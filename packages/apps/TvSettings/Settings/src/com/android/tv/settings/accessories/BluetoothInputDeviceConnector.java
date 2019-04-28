/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.util.Log;
import android.view.InputDevice;

/**
 * Manages process of pairing and connecting of input devices.
 */
public class BluetoothInputDeviceConnector implements BluetoothDevicePairer.BluetoothConnector {

    public static final String TAG = "BtInputDeviceConnector";

    private static final boolean DEBUG = false;

    private static final String[] INVALID_INPUT_KEYBOARD_DEVICE_NAMES = {
        "gpio-keypad", "cec_keyboard", "Virtual", "athome_remote"
    };

    private BluetoothProfile.ServiceListener mServiceConnection =
            new BluetoothProfile.ServiceListener() {

        @Override
        public void onServiceDisconnected(int profile) {
            Log.w(TAG, "Service disconnected, perhaps unexpectedly");
            unregisterInputMethodMonitor();
            closeInputProfileProxy();
            mOpenConnectionCallback.failed();
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DEBUG) {
                Log.d(TAG, "Connection made to bluetooth proxy.");
            }
            mInputProxy = (BluetoothInputDevice) proxy;
            if (mTarget != null) {
                registerInputMethodMonitor();
                if (DEBUG) {
                    Log.d(TAG, "Connecting to target: " + mTarget.getAddress());
                }
                // TODO need to start a timer, otherwise if the connection fails we might be
                // stuck here forever
                mInputProxy.connect(mTarget);

                // must set PRIORITY_AUTO_CONNECT or auto-connection will not
                // occur, however this setting does not appear to be sticky
                // across a reboot
                mInputProxy.setPriority(mTarget, BluetoothProfile.PRIORITY_AUTO_CONNECT);
            }
        }
    };

    private BluetoothInputDevice mInputProxy;
    private boolean mInputMethodMonitorRegistered = false;

    private BluetoothDevice mTarget;
    private Context mContext;
    private Handler mHandler;
    private BluetoothDevicePairer.OpenConnectionCallback mOpenConnectionCallback;

    private void registerInputMethodMonitor() {
        InputManager inputManager = (InputManager) mContext.getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(mInputListener, mHandler);

        // TO DO: The line below is a workaround for an issue in InputManager.
        // The manager doesn't actually registers itself with the InputService
        // unless we query it for input devices. We should remove this once
        // the problem is fixed in InputManager.
        // Reference bug in Frameworks: b/10415556
        int[] inputDevices = inputManager.getInputDeviceIds();

        mInputMethodMonitorRegistered = true;
    }

    private InputManager.InputDeviceListener mInputListener =
            new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceRemoved(int deviceId) {
            // ignored
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            // ignored
        }

        @Override
        public void onInputDeviceAdded(int deviceId) {
           if (BluetoothDevicePairer.hasValidInputDevice(mContext, new int[] {deviceId})) {
               onInputAdded();
           }
        }
    };

    private void onInputAdded() {
        unregisterInputMethodMonitor();
        closeInputProfileProxy();
        mOpenConnectionCallback.succeeded();
    }

    private void unregisterInputMethodMonitor() {
        if (mInputMethodMonitorRegistered) {
            InputManager inputManager = (InputManager) mContext.getSystemService(Context.INPUT_SERVICE);
            inputManager.unregisterInputDeviceListener(mInputListener);
            mInputMethodMonitorRegistered = false;
        }
    }

    private void closeInputProfileProxy() {
        if (mInputProxy != null) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                adapter.closeProfileProxy(BluetoothProfile.INPUT_DEVICE, mInputProxy);
                mInputProxy = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up input profile proxy", t);
            }
        }
    }

    private BluetoothInputDeviceConnector() {
    }

    public BluetoothInputDeviceConnector(Context context, BluetoothDevice target, Handler handler,
                                         BluetoothDevicePairer.OpenConnectionCallback callback) {
        mContext = context;
        mTarget = target;
        mHandler = handler;
        mOpenConnectionCallback = callback;
    }

    @Override
    public void openConnection(BluetoothAdapter adapter) {
        if (!adapter.getProfileProxy(mContext, mServiceConnection, BluetoothProfile.INPUT_DEVICE)) {
            mOpenConnectionCallback.failed();
        }
    }
}
