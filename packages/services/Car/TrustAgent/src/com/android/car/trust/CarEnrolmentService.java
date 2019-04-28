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
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.car.trust.comms.SimpleBleServer;

import java.util.HashSet;
import java.util.UUID;

/**
 * A service that receives escrow token enrollment requests from remote devices.
 */
public class CarEnrolmentService extends SimpleBleServer {
    private static final String TAG = "CarEnrolmentService";

    public interface EnrolmentCallback {
        void onEnrolmentDataReceived(byte[] token);
    }

    private BluetoothGattService mEnrolmentService;
    private BluetoothGattCharacteristic mEnrolmentEscrowToken;
    private BluetoothGattCharacteristic mEnrolmentTokenHandle;

    private HashSet<EnrolmentCallback> mCallbacks;

    private final IBinder mBinder = new EnrolmentServiceBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        mCallbacks = new HashSet<>();
        setupEnrolmentService();
    }

    public void start() {
        ParcelUuid uuid = new ParcelUuid(
                UUID.fromString(getString(R.string.enrollment_service_uuid)));
        start(uuid, mEnrolmentService);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCharacteristicWrite(BluetoothDevice device,
            int requestId, BluetoothGattCharacteristic characteristic,
            boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        if (characteristic.getUuid().equals(mEnrolmentEscrowToken.getUuid())) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Enrolment token received, value: " + Utils.getLong(value));
            }

            for (EnrolmentCallback callback : mCallbacks) {
                callback.onEnrolmentDataReceived(value);
            }
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothDevice device,
            int requestId, int offset, BluetoothGattCharacteristic characteristic) {
        //Enrolment service should not have any read requests.
    }

    public void addEnrolmentCallback(EnrolmentCallback callback) {
        mCallbacks.add(callback);
    }

    public void sendHandle(long handle, BluetoothDevice device) {
        mEnrolmentTokenHandle.setValue(Utils.getBytes(handle));

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Sending notification for EscrowToken Handle");
        }
        mGattServer.notifyCharacteristicChanged(device,
                mEnrolmentTokenHandle, false /* confirm */);
    }

    public class EnrolmentServiceBinder extends Binder {
        public CarEnrolmentService getService() {
            return CarEnrolmentService.this;
        }
    }

    // Create services and characteristics for enrolling new unlocking escrow tokens
    private void setupEnrolmentService() {
        mEnrolmentService = new BluetoothGattService(
                UUID.fromString(getString(R.string.enrollment_service_uuid)),
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Characteristic to describe the escrow token being used for unlock
        mEnrolmentEscrowToken = new BluetoothGattCharacteristic(
                UUID.fromString(getString(R.string.enrollment_token_uuid)),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        // Characteristic to describe the handle being used for this escrow token
        mEnrolmentTokenHandle = new BluetoothGattCharacteristic(
                UUID.fromString(getString(R.string.enrollment_handle_uuid)),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        mEnrolmentService.addCharacteristic(mEnrolmentEscrowToken);
        mEnrolmentService.addCharacteristic(mEnrolmentTokenHandle);
    }
}
