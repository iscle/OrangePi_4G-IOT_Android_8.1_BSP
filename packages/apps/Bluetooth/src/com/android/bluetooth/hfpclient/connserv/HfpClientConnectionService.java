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
 * limitations under the License.
 */
package com.android.bluetooth.hfpclient.connserv;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import com.android.bluetooth.hfpclient.HeadsetClientService;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class HfpClientConnectionService extends ConnectionService {
    private static final String TAG = "HfpClientConnService";
    private static final boolean DBG = true;

    public static final String HFP_SCHEME = "hfpc";

    private BluetoothAdapter mAdapter;

    // BluetoothHeadset proxy.
    private BluetoothHeadsetClient mHeadsetProfile;
    private TelecomManager mTelecomManager;

    private final Map<BluetoothDevice, HfpClientDeviceBlock> mDeviceBlocks =
        new HashMap<>();

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) {
                Log.d(TAG, "onReceive " + intent);
            }
            String action = intent != null ? intent.getAction() : null;

            if (BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int newState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (DBG) {
                        Log.d(TAG, "Established connection with " + device);
                    }

                    HfpClientDeviceBlock block = null;
                    if ((block = createBlockForDevice(device)) == null) {
                        Log.w(TAG, "Block already exists for device " + device + " ignoring.");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (DBG) {
                        Log.d(TAG, "Disconnecting from " + device);
                    }

                    // Disconnect any inflight calls from the connection service.
                    synchronized (HfpClientConnectionService.this) {
                        HfpClientDeviceBlock block = mDeviceBlocks.remove(device);
                        if (block == null) {
                            Log.w(TAG, "Disconnect for device but no block " + device);
                            return;
                        }
                        block.cleanup();
                        // Block should be subsequently garbage collected
                        block = null;
                    }
                }
            } else if (BluetoothHeadsetClient.ACTION_CALL_CHANGED.equals(action)) {
                BluetoothHeadsetClientCall call =
                    intent.getParcelableExtra(BluetoothHeadsetClient.EXTRA_CALL);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                HfpClientDeviceBlock block = findBlockForDevice(call.getDevice());
                if (block == null) {
                    Log.w(TAG, "Call changed but no block for device " + device);
                    return;
                }

                // If we are not connected, then when we actually do get connected --
                // the calls should
                // be added (see ACTION_CONNECTION_STATE_CHANGED intent above).
                block.handleCall(call);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) {
            Log.d(TAG, "onCreate");
        }
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTelecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        mAdapter.getProfileProxy(this, mServiceListener, BluetoothProfile.HEADSET_CLIENT);
    }

    @Override
    public void onDestroy() {
        if (DBG) {
            Log.d(TAG, "onDestroy called");
        }
        // Close the profile.
        if (mHeadsetProfile != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT, mHeadsetProfile);
        }

        // Unregister the broadcast receiver.
        try {
            unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Receiver was not registered.");
        }

        // Unregister the phone account. This should ideally happen when disconnection ensues but in
        // case the service crashes we may need to force clean.
        disconnectAll();
    }

    private synchronized void disconnectAll() {
        for (Iterator<Map.Entry<BluetoothDevice, HfpClientDeviceBlock>> it =
                mDeviceBlocks.entrySet().iterator(); it.hasNext();) {
            it.next().getValue().cleanup();
            it.remove();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) {
            Log.d(TAG, "onStartCommand " + intent);
        }
        // In order to make sure that the service is sticky (recovers from errors when HFP
        // connection is still active) and to stop it we need a special intent since stopService
        // only recreates it.
        if (intent != null &&
            intent.getBooleanExtra(HeadsetClientService.HFP_CLIENT_STOP_TAG, false)) {
            // Stop the service.
            stopSelf();
            return 0;
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothHeadsetClient.ACTION_CALL_CHANGED);
            registerReceiver(mBroadcastReceiver, filter);
            return START_STICKY;
        }
    }

    // This method is called whenever there is a new incoming call (or right after BT connection).
    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (DBG) {
            Log.d(TAG, "onCreateIncomingConnection " + connectionManagerAccount +
                " req: " + request);
        }

        HfpClientDeviceBlock block = findBlockForHandle(connectionManagerAccount);
        if (block == null) {
            Log.w(TAG, "HfpClient does not support having a connection manager");
            return null;
        }

        // We should already have a connection by this time.
        BluetoothHeadsetClientCall call =
            request.getExtras().getParcelable(
                TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        return block.onCreateIncomingConnection(call);
    }

    // This method is called *only if* Dialer UI is used to place an outgoing call.
    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (DBG) {
            Log.d(TAG, "onCreateOutgoingConnection " + connectionManagerAccount);
        }
        HfpClientDeviceBlock block = findBlockForHandle(connectionManagerAccount);
        if (block == null) {
            Log.w(TAG, "HfpClient does not support having a connection manager");
            return null;
        }

        return block.onCreateOutgoingConnection(request.getAddress());
    }

    // This method is called when:
    // 1. Outgoing call created from the AG.
    // 2. Call transfer from AG -> HF (on connection when existed call present).
    @Override
    public Connection onCreateUnknownConnection(
            PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (DBG) {
            Log.d(TAG, "onCreateUnknownConnection " + connectionManagerAccount);
        }
        HfpClientDeviceBlock block = findBlockForHandle(connectionManagerAccount);
        if (block == null) {
            Log.w(TAG, "HfpClient does not support having a connection manager");
            return null;
        }

        // We should already have a connection by this time.
        BluetoothHeadsetClientCall call =
            request.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
        return block.onCreateUnknownConnection(call);
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        if (DBG) {
            Log.d(TAG, "onConference " + connection1 + " " + connection2);
        }

        BluetoothDevice bd1 = ((HfpClientConnection) connection1).getDevice();
        BluetoothDevice bd2 = ((HfpClientConnection) connection2).getDevice();
        // We can only conference two connections on same device
        if (!Objects.equals(bd1, bd2)) {
            Log.e(TAG, "Cannot conference calls from two different devices "
                            + "bd1 " + bd1 + " bd2 " + bd2 + " conn1 " + connection1
                            + "connection2 " + connection2);
            return;
        }

        HfpClientDeviceBlock block = findBlockForDevice(bd1);
        block.onConference(connection1, connection2);
    }

    private BluetoothDevice getDevice(PhoneAccountHandle handle) {
        PhoneAccount account = mTelecomManager.getPhoneAccount(handle);
        String btAddr = account.getAddress().getSchemeSpecificPart();
        return mAdapter.getRemoteDevice(btAddr);
    }

    BluetoothProfile.ServiceListener mServiceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DBG) {
                Log.d(TAG, "onServiceConnected");
            }
            mHeadsetProfile = (BluetoothHeadsetClient) proxy;

            List<BluetoothDevice> devices = mHeadsetProfile.getConnectedDevices();
            if (devices == null) {
                Log.w(TAG, "No connected or more than one connected devices found." + devices);
                return;
            }
            for (BluetoothDevice device : devices) {
                if (DBG) {
                    Log.d(TAG, "Creating phone account for device " + device);
                }

                // Creation of the block takes care of initializing the phone account and
                // calls.
                HfpClientDeviceBlock block = createBlockForDevice(device);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (DBG) {
                Log.d(TAG, "onServiceDisconnected " + profile);
            }
            mHeadsetProfile = null;
            disconnectAll();
        }
    };

    // Block management functions
    synchronized HfpClientDeviceBlock createBlockForDevice(BluetoothDevice device) {
        Log.d(TAG, "Creating block for device " + device);
        if (mDeviceBlocks.containsKey(device)) {
            Log.e(TAG, "Device already exists " + device + " blocks " + mDeviceBlocks);
            return null;
        }

        HfpClientDeviceBlock block = new HfpClientDeviceBlock(this, device, mHeadsetProfile);
        mDeviceBlocks.put(device, block);
        return block;
    }

    synchronized HfpClientDeviceBlock findBlockForDevice(BluetoothDevice device) {
        Log.d(TAG, "Finding block for device " + device + " blocks " + mDeviceBlocks);
        return mDeviceBlocks.get(device);
    }

    synchronized HfpClientDeviceBlock findBlockForHandle(PhoneAccountHandle handle) {
        PhoneAccount account = mTelecomManager.getPhoneAccount(handle);
        String btAddr = account.getAddress().getSchemeSpecificPart();
        BluetoothDevice device = mAdapter.getRemoteDevice(btAddr);
        Log.d(TAG, "Finding block for handle " + handle + " device " + btAddr);
        return mDeviceBlocks.get(device);
    }

    // Util functions that may be used by various classes
    public static PhoneAccount createAccount(Context context, BluetoothDevice device) {
        Uri addr = Uri.fromParts(HfpClientConnectionService.HFP_SCHEME, device.getAddress(), null);
        PhoneAccountHandle handle = new PhoneAccountHandle(
            new ComponentName(context, HfpClientConnectionService.class), device.getAddress());
        PhoneAccount account =
                new PhoneAccount.Builder(handle, "HFP " + device.toString())
                    .setAddress(addr)
                    .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                    .build();
        if (DBG) {
            Log.d(TAG, "phoneaccount: " + account);
        }
        return account;
    }

    public static boolean hasHfpClientEcc(BluetoothHeadsetClient client, BluetoothDevice device) {
        Bundle features = client.getCurrentAgEvents(device);
        return features == null ? false :
                features.getBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_ECC, false);
    }
}
