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
package com.google.android.car.usb.aoap.companion;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import libcore.io.IoUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Activity for AOAP phone test app. */
public class AoapPhoneCompanionActivity extends Activity {
    private static final String TAG = AoapPhoneCompanionActivity.class.getSimpleName();
    private static final boolean DBG = true;
    private static final ByteOrder ORDER = ByteOrder.BIG_ENDIAN;

    private static final String ACTION_USB_ACCESSORY_PERMISSION =
            "com.google.android.car.usb.aoap.companion.ACTION_USB_ACCESSORY_PERMISSION";

    private UsbManager mUsbManager;
    private AccessoryReceiver mReceiver;
    private ParcelFileDescriptor mFd;
    private ProcessorThread mProcessorThread;
    private UsbAccessory mAccessory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.device);
        Button exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        configureReceiver();
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent.getAction().equals(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)) {
            UsbAccessory accessory =
                    (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (accessory != null) {
                onAccessoryAttached(accessory);
            } else {
                throw new RuntimeException("USB accessory is null.");
            }
        } else {
            finish();
        }
    }

    private void configureReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction(ACTION_USB_ACCESSORY_PERMISSION);
        mReceiver = new AccessoryReceiver();
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        IoUtils.closeQuietly(mFd);
        if (mProcessorThread != null) {
            mProcessorThread.requestToQuit();
            try {
                mProcessorThread.join(1000);
            } catch (InterruptedException e) {
            }
            if (mProcessorThread.isAlive()) { // reader thread stuck
                Log.w(TAG, "ProcessorThread still alive");
            }
        }
    }

    private void onAccessoryAttached(UsbAccessory accessory) {
        Log.i(TAG, "Starting AOAP discovery protocol, accessory attached: " + accessory);
        // Check whether we have permission to access the accessory.
        if (!mUsbManager.hasPermission(accessory)) {
            Log.i(TAG, "Prompting the user for access to the accessory.");
            Intent intent = new Intent(ACTION_USB_ACCESSORY_PERMISSION);
            intent.setPackage(getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
            mUsbManager.requestPermission(accessory, pendingIntent);
            return;
        }
        mFd = mUsbManager.openAccessory(accessory);
        if (mFd == null) {
            Log.e(TAG, "UsbManager.openAccessory returned null");
            finish();
            return;
        }
        mAccessory = accessory;
        mProcessorThread = new ProcessorThread(mFd);
        mProcessorThread.start();
    }

    private void onAccessoryDetached(UsbAccessory accessory) {
        Log.i(TAG, "Accessory detached: " + accessory);
        finish();
    }

    private class ProcessorThread extends Thread {
        private boolean mShouldQuit = false;
        private final FileInputStream mInputStream;
        private final FileOutputStream mOutputStream;
        private final byte[] mBuffer = new byte[16384];

        private ProcessorThread(ParcelFileDescriptor fd) {
            super("AOAP");
            mInputStream = new FileInputStream(fd.getFileDescriptor());
            mOutputStream = new FileOutputStream(fd.getFileDescriptor());
        }

        private synchronized void requestToQuit() {
            mShouldQuit = true;
        }

        private synchronized boolean shouldQuit() {
            return mShouldQuit;
        }

        protected int byteToInt(byte[] buffer) {
            return ByteBuffer.wrap(buffer).order(ORDER).getInt();
        }

        @Override
        public void run() {
            while (!shouldQuit()) {
                int readBufferSize = 0;
                while (!shouldQuit()) {
                    try {
                        int read = mInputStream.read(mBuffer);
                        if (read == 4 && readBufferSize == 0) {
                            readBufferSize = byteToInt(mBuffer);
                            continue;
                        }
                        Log.d(TAG, "Read " + read + " bytes");
                        if (read < readBufferSize) {
                            break;
                        }
                    } catch (IOException e) {
                        Log.i(TAG, "ProcessorThread IOException", e);
                        // AOAP App should release FD when IOException happens.
                        // If FD is kept, device will not behave nicely on reset and multiple reset
                        // can be required.
                        finish();
                        return;
                    }
                }
                if (!shouldQuit()) {
                    byte[] outBuffer = "DONE".getBytes();
                    try {
                        mOutputStream.write(outBuffer);
                    } catch (IOException e) {
                        Log.i(TAG, "ProcessorThread IOException", e);
                        finish();
                        return;
                    }
                }
            }
        }
    }

    private class AccessoryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (accessory != null) {
                String action = intent.getAction();
                if (action.equals(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)) {
                    onAccessoryAttached(accessory);
                } else if (action.equals(UsbManager.ACTION_USB_ACCESSORY_DETACHED)) {
                    if (mAccessory != null && mAccessory.equals(accessory)) {
                        onAccessoryDetached(accessory);
                    }
                } else if (action.equals(ACTION_USB_ACCESSORY_PERMISSION)) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(TAG, "Accessory permission granted: " + accessory);
                        onAccessoryAttached(accessory);
                    } else {
                        Log.e(TAG, "Accessory permission denied: " + accessory);
                        finish();
                    }
                }
            }
        }
    }
}
