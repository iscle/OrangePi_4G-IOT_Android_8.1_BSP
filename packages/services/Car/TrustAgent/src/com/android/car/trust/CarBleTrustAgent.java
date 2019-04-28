/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.car.trust;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.trust.TrustAgentService;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.android.car.trust.comms.SimpleBleServer;

import java.util.concurrent.TimeUnit;

/**
 * A sample trust agent that demonstrates how to use the escrow token unlock APIs. </p>
 *
 * This trust agent runs during direct boot and binds to a BLE service that listens for remote
 * devices to trigger an unlock. <p/>
 *
 * The permissions for this agent must be enabled as priv-app permissions for it to start.
 */
public class CarBleTrustAgent extends TrustAgentService {
    public static final String ACTION_REVOKE_TRUST = "revoke-trust-action";
    public static final String ACTION_ADD_TOKEN = "add-token-action";
    public static final String ACTION_IS_TOKEN_ACTIVE = "is-token-active-action";
    public static final String ACTION_REMOVE_TOKEN = "remove-token-action";
    public static final String ACTION_UNLOCK_DEVICE = "unlock-device-action";

    public static final String ACTION_TOKEN_STATUS_RESULT = "token-status-result-action";
    public static final String ACTION_ADD_TOKEN_RESULT = "add-token-result-action";

    public static final String INTENT_EXTRA_ESCROW_TOKEN = "extra-escrow-token";
    public static final String INTENT_EXTRA_TOKEN_HANDLE = "extra-token-handle";
    public static final String INTENT_EXTRA_TOKEN_STATUS = "extra-token-status";


    private static final String TAG = "CarBleTrustAgent";

    private static final long TRUST_DURATION_MS = TimeUnit.MINUTES.toMicros(5);
    private static final long BLE_RETRY_MS = TimeUnit.SECONDS.toMillis(1);

    private CarUnlockService mCarUnlockService;
    private LocalBroadcastManager mLocalBroadcastManager;

    private boolean mBleServiceBound;

    // We cannot directly bind to TrustAgentService since the onBind method is final.
    // As a result, we communicate with the various UI components using a LocalBroadcastManager.
    private final BroadcastReceiver mTrustEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Received broadcast: " + action);
            }
            if (ACTION_REVOKE_TRUST.equals(action)) {
                revokeTrust();
            } else if (ACTION_ADD_TOKEN.equals(action)) {
                byte[] token = intent.getByteArrayExtra(INTENT_EXTRA_ESCROW_TOKEN);
                addEscrowToken(token, getCurrentUserHandle());
            } else if (ACTION_IS_TOKEN_ACTIVE.equals(action)) {
                long handle = intent.getLongExtra(INTENT_EXTRA_TOKEN_HANDLE, -1);
                isEscrowTokenActive(handle, getCurrentUserHandle());
            } else if (ACTION_REMOVE_TOKEN.equals(action)) {
                long handle = intent.getLongExtra(INTENT_EXTRA_TOKEN_HANDLE, -1);
                removeEscrowToken(handle, getCurrentUserHandle());
            }
        }
    };

    @Override
    public void onTrustTimeout() {
        super.onTrustTimeout();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onTrustTimeout(): timeout expired");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Bluetooth trust agent starting up");
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REVOKE_TRUST);
        filter.addAction(ACTION_ADD_TOKEN);
        filter.addAction(ACTION_IS_TOKEN_ACTIVE);
        filter.addAction(ACTION_REMOVE_TOKEN);

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this /* context */);
        mLocalBroadcastManager.registerReceiver(mTrustEventReceiver, filter);

        // If the user is already unlocked, don't bother starting the BLE service.
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        if (!um.isUserUnlocked()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "User locked, will now bind CarUnlockService");
            }
            Intent intent = new Intent(this, CarUnlockService.class);

            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            setManagingTrust(true);
        }
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Car Trust agent shutting down");
        }
        mLocalBroadcastManager.unregisterReceiver(mTrustEventReceiver);

        // Unbind the service to avoid leaks from BLE stack.
        if (mBleServiceBound) {
            unbindService(mServiceConnection);
        }
        super.onDestroy();
    }

    private SimpleBleServer.ConnectionListener mConnectionListener
            = new SimpleBleServer.ConnectionListener() {
        @Override
        public void onServerStarted() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "BLE server started");
            }
        }

        @Override
        public void onServerStartFailed(int errorCode) {
            Log.w(TAG, "BLE server failed to start. Error Code: " + errorCode);
        }

        @Override
        public void onDeviceConnected(BluetoothDevice device) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "BLE device connected. Name: " + device.getName()
                        + " Address: " + device.getAddress());
            }
        }
    };

    private CarUnlockService.UnlockServiceCallback mUnlockCallback
            = new CarUnlockService.UnlockServiceCallback() {
        @Override
        public void unlockDevice(byte[] token, long handle) {
            unlock(token, handle);
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "CarUnlockService connected");
            }

            mBleServiceBound = true;
            CarUnlockService.UnlockServiceBinder binder
                    = (CarUnlockService.UnlockServiceBinder) service;
            mCarUnlockService = binder.getService();
            mCarUnlockService.addUnlockServiceCallback(mUnlockCallback);
            mCarUnlockService.addConnectionListener(mConnectionListener);
            maybeStartBleUnlockService();
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mCarUnlockService = null;
            mBleServiceBound = false;
        }

    };

    private void maybeStartBleUnlockService() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Trying to open a Ble GATT server");
        }

        BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothGattServer mGattServer
                = btManager.openGattServer(this, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
            }
        });

        // The BLE stack is started up before the trust agent service, however Gatt capabilities
        // might not be ready just yet. Keep trying until a GattServer can open up before proceeding
        // to start the rest of the BLE services.
        if (mGattServer == null) {
            Log.e(TAG, "Gatt not available, will try again...in " + BLE_RETRY_MS + "ms");

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    maybeStartBleUnlockService();
                }
            }, BLE_RETRY_MS);
        } else {
            mGattServer.close();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "GATT available, starting up UnlockService");
            }
            mCarUnlockService.start();
        }
    }

    private void unlock(byte[] token, long handle) {
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "About to unlock user. Current handle: " + handle
                    + " Time: " + System.currentTimeMillis());
        }
        unlockUserWithToken(handle, token, getCurrentUserHandle());

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Attempted to unlock user, is user unlocked? " + um.isUserUnlocked()
                    + " Time: " + System.currentTimeMillis());
        }
        setManagingTrust(true);

        if (um.isUserUnlocked()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, getString(R.string.trust_granted_explanation));
            }
            grantTrust("Granting trust from escrow token",
                    TRUST_DURATION_MS, FLAG_GRANT_TRUST_DISMISS_KEYGUARD);
            // Trust has been granted, disable the BLE server. This trust agent service does
            // not need to receive additional BLE data.
            unbindService(mServiceConnection);
        }
    }

    @Override
    public void onEscrowTokenRemoved(long handle, boolean successful) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onEscrowTokenRemoved. Handle: " + handle + " successful? " + successful);
        }
    }

    @Override
    public void onEscrowTokenStateReceived(long handle, int tokenState) {
        boolean isActive = tokenState == TOKEN_STATE_ACTIVE;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Token handle: " + handle + " isActive: " + isActive);
        }

        Intent intent = new Intent();
        intent.setAction(ACTION_TOKEN_STATUS_RESULT);
        intent.putExtra(INTENT_EXTRA_TOKEN_STATUS, isActive);

        mLocalBroadcastManager.sendBroadcast(intent);
    }

    @Override
    public void onEscrowTokenAdded(byte[] token, long handle, UserHandle user) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onEscrowTokenAdded, handle: " + handle);
        }

        Intent intent = new Intent();
        intent.setAction(ACTION_ADD_TOKEN_RESULT);
        intent.putExtra(INTENT_EXTRA_TOKEN_HANDLE, handle);

        mLocalBroadcastManager.sendBroadcast(intent);
    }

    private UserHandle getCurrentUserHandle() {
        return UserHandle.of(UserHandle.myUserId());
    }
}
