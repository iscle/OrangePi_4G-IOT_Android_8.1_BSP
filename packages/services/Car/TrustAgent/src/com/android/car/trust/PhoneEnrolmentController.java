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

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;

/**
 * A controller that sets up a {@link SimpleBleClient} to connect to the BLE enrollment service.
 * It also binds the UI components to control the enrollment process.
 */
public class PhoneEnrolmentController {
    private static final String TAG = "PhoneEnrolmentCtlr";
    private String mTokenHandleKey;
    private String mEscrowTokenKey;

    // BLE characteristics associated with the enrollment/add escrow token service.
    private BluetoothGattCharacteristic mEnrolmentTokenHandle;
    private BluetoothGattCharacteristic mEnrolmentEscrowToken;

    private ParcelUuid mEnrolmentServiceUuid;

    private SimpleBleClient mClient;
    private Context mContext;

    private TextView mTextView;
    private Handler mHandler;

    private Button mScanButton;
    private Button mEnrolButton;

    public PhoneEnrolmentController(Context context) {
        mContext = context;

        mTokenHandleKey = context.getString(R.string.pref_key_token_handle);
        mEscrowTokenKey = context.getString(R.string.pref_key_escrow_token);

        mClient = new SimpleBleClient(context);
        mEnrolmentServiceUuid = new ParcelUuid(
                UUID.fromString(mContext.getString(R.string.enrollment_service_uuid)));
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
        mEnrolButton = enrolButton;

        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClient.start(mEnrolmentServiceUuid);
            }
        });

        mEnrolButton.setEnabled(false);
        mEnrolButton.setAlpha(0.3f);
        mEnrolButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendOutputText("Sending new escrow token to remote device");

                byte[] token = generateEscrowToken();
                sendEnrolmentRequest(token);

                // WARNING: Store the token so it can be used later for unlocking. This token
                // should NEVER be stored on the device that is being unlocked. It should
                // always be securely stored on a remote device that will trigger the unlock.
                storeToken(token);
            }
        });
    }

    /**
     * @return A random byte array that is used as the escrow token for remote device unlock.
     */
    private byte[] generateEscrowToken() {
        Random random = new Random();
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
        buffer.putLong(0, random.nextLong());
        return buffer.array();
    }

    private void sendEnrolmentRequest(byte[] token) {
        mEnrolmentEscrowToken.setValue(token);
        mClient.writeCharacteristic(mEnrolmentEscrowToken);
        storeToken(token);
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

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCharacteristicChanged: " + Utils.getLong(characteristic.getValue()));
            }

            if (characteristic.getUuid().equals(mEnrolmentTokenHandle.getUuid())) {
                // Store the new token handle that the BLE server is sending us. This required
                // to unlock the device.
                long handle = Utils.getLong(characteristic.getValue());
                storeHandle(handle);
                appendOutputText("Token handle received: " + handle);
            }
        }

        @Override
        public void onServiceDiscovered(BluetoothGattService service) {
            if (!service.getUuid().equals(mEnrolmentServiceUuid.getUuid())) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Service UUID: " + service.getUuid()
                            + " does not match Enrolment UUID " + mEnrolmentServiceUuid.getUuid());
                }
                return;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Enrolment Service # characteristics: "
                        + service.getCharacteristics().size());
            }
            mEnrolmentEscrowToken
                    = Utils.getCharacteristic(R.string.enrollment_token_uuid, service, mContext);
            mEnrolmentTokenHandle
                    = Utils.getCharacteristic(R.string.enrollment_handle_uuid, service, mContext);
            mClient.setCharacteristicNotification(mEnrolmentTokenHandle, true /* enable */);
            appendOutputText("Enrolment BLE client successfully connected");

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Services are now set up, allow users to enrol new escrow tokens.
                    mEnrolButton.setEnabled(true);
                    mEnrolButton.setAlpha(1.0f);
                }
            });
        }
    };

    private void storeHandle(long handle) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putLong(mTokenHandleKey, handle).apply();
    }

    private void storeToken(byte[] token) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String byteArray = Base64.encodeToString(token, Base64.DEFAULT);
        prefs.edit().putString(mEscrowTokenKey, byteArray).apply();
    }

    private void appendOutputText(final String text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTextView.append("\n" + text);
            }
        });
    }
}
