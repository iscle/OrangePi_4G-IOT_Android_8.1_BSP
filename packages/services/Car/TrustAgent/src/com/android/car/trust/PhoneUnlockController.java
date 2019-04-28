/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.android.car.trust.comms.SimpleBleClient;

import java.util.UUID;

/**
 * A controller that sets up a {@link SimpleBleClient} to connect to the BLE unlock service.
 */
public class PhoneUnlockController {
    private static final String TAG = "PhoneUnlockController";

    private String mTokenHandleKey;
    private String mEscrowTokenKey;

    // BLE characteristics associated with the enrolment/add escrow token service.
    private BluetoothGattCharacteristic mUnlockTokenHandle;
    private BluetoothGattCharacteristic mUnlockEscrowToken;

    private ParcelUuid mUnlockServiceUuid;

    private SimpleBleClient mClient;
    private Context mContext;

    private TextView mTextView;
    private Handler mHandler;

    private Button mScanButton;
    private Button mUnlockButton;

    public PhoneUnlockController(Context context) {
        mContext = context;

        mTokenHandleKey = context.getString(R.string.pref_key_token_handle);
        mEscrowTokenKey = context.getString(R.string.pref_key_escrow_token);

        mClient = new SimpleBleClient(context);
        mUnlockServiceUuid = new ParcelUuid(
                UUID.fromString(mContext.getString(R.string.unlock_service_uuid)));
        mClient.addCallback(mCallback /* callback */);

        mHandler = new Handler(mContext.getMainLooper());
    }

    /**
     * Binds the views to the actions that can be performed by this controller.
     *
     * @param textView    A text view used to display results from various BLE actions
     * @param scanButton  Button used to start scanning for available BLE devices.
     * @param enrolButton Button used to send new escrow token to remote device.
     */
    public void bind(TextView textView, Button scanButton, Button enrolButton) {
        mTextView = textView;
        mScanButton = scanButton;
        mUnlockButton = enrolButton;

        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClient.start(mUnlockServiceUuid);
            }
        });

        mUnlockButton.setEnabled(false);
        mUnlockButton.setAlpha(0.3f);
        mUnlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendOutputText("Sending unlock token and handle to remote device");
                sendUnlockRequest();
            }
        });
    }

    private void sendUnlockRequest() {
        // Retrieve stored token and handle and write to remote device.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        long handle = prefs.getLong(mTokenHandleKey, -1);
        byte[] token = Base64.decode(prefs.getString(mEscrowTokenKey, null), Base64.DEFAULT);

        mUnlockEscrowToken.setValue(token);
        mUnlockTokenHandle.setValue(Utils.getBytes(handle));

        mClient.writeCharacteristic(mUnlockEscrowToken);
        mClient.writeCharacteristic(mUnlockTokenHandle);
    }

    private SimpleBleClient.ClientCallback mCallback = new SimpleBleClient.ClientCallback() {
        @Override
        public void onDeviceConnected(BluetoothDevice device) {
            appendOutputText("Device connected: " + device.getName()
                    + " addr: " + device.getAddress());
        }

        @Override
        public void onDeviceDisconnected() {
            appendOutputText("Device disconnected");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic) {
            // Not expecting any characteristics changes for the unlocking client.
        }

        @Override
        public void onServiceDiscovered(BluetoothGattService service) {
            if (!service.getUuid().equals(mUnlockServiceUuid.getUuid())) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Service UUID: " + service.getUuid()
                        + " does not match Enrolment UUID " + mUnlockServiceUuid.getUuid());
                }
                return;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unlock Service # characteristics: "
                        + service.getCharacteristics().size());
            }
            mUnlockEscrowToken
                    = Utils.getCharacteristic(R.string.unlock_escrow_token_uiid, service, mContext);
            mUnlockTokenHandle
                    = Utils.getCharacteristic(R.string.unlock_handle_uiid, service, mContext);
            appendOutputText("Unlock BLE client successfully connected");

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Services are now set up, allow users to enrol new escrow tokens.
                    mUnlockButton.setEnabled(true);
                    mUnlockButton.setAlpha(1.0f);
                }
            });
        }
    };

    private void appendOutputText(final String text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTextView.append("\n" + text);
            }
        });
    }
}
