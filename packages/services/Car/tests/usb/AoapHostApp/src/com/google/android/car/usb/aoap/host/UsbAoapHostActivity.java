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
package com.google.android.car.usb.aoap.host;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Host activity for AOAP test app.
 */
public class UsbAoapHostActivity extends Activity
        implements SpeedMeasurementController.SpeedMeasurementControllerCallback {

    private static final String TAG = UsbAoapHostActivity.class.getSimpleName();

    private final List<String> mLogMessages = new ArrayList<>();

    private UsbManager mUsbManager;
    private UsbStateReceiver mReceiver;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbConnection;
    private TextView mLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.host);
        mLog = (TextView) findViewById(R.id.usb_log);
        mLog.setMovementMethod(new ScrollingMovementMethod());


        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mReceiver = new UsbStateReceiver();
        registerReceiver(mReceiver, filter);

        Intent intent = getIntent();
        if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            mUsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            mUsbConnection = mUsbManager.openDevice(mUsbDevice);
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SpeedMeasurementController testController =
                new SpeedMeasurementController(this, mUsbDevice, mUsbConnection, this);
        testController.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        if (mUsbConnection != null) {
            mUsbConnection.close();
        }
    }

    private String getTestModeString(int mode) {
        if (mode == SpeedMeasurementController.TEST_MODE_SYNC) {
            return "Sync";
        } else if (mode == SpeedMeasurementController.TEST_MODE_ASYNC) {
            return "Async";
        } else {
            return "Unknown mode: " + mode;
        }
    }

    private synchronized void addLog(String message) {
        mLogMessages.add(message);
        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLog.setText(TextUtils.join("\n", mLogMessages));
                }
            });
    }

    @Override
    public void testStarted(int mode, int bufferSize) {
        addLog("Starting " + getTestModeString(mode) + " mode test with buffer size " + bufferSize
                + " bytes");
    }

    @Override
    public void testFinished(int mode, int bufferSize) {
        addLog("Completed " + getTestModeString(mode) + " mode test with buffer size " + bufferSize
                + " bytes");
    }

    @Override
    public void testResult(int mode, String update) {
        Log.i(TAG, "Test result " + mode + " update: " + update);
        addLog("Test result for " + getTestModeString(mode) + " mode: " + update);
    }

    @Override
    public void testSuiteFinished() {
        Log.i(TAG, "All tests finished");
        addLog("All tests are completed");
    }

    private static boolean isDevicesMatching(UsbDevice l, UsbDevice r) {
        if (l.getVendorId() == r.getVendorId() && l.getProductId() == r.getProductId()
                && TextUtils.equals(l.getSerialNumber(), r.getSerialNumber())) {
            return true;
        }
        return false;
    }

    private class UsbStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (isDevicesMatching(mUsbDevice, device)) {
                    finish();
                }
            }
        }
    }
}
