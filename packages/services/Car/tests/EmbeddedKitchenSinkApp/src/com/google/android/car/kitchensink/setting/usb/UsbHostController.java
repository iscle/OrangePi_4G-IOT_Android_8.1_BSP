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
package com.google.android.car.kitchensink.setting.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller used to handle USB device connections.
 */
public final class UsbHostController
        implements UsbDeviceHandlerResolver.UsbDeviceHandlerResolverCallback {

    /**
     * Callbacks for controller
     */
    public interface UsbHostControllerCallbacks {
        /** Host controller ready for shutdown*/
        void shutdown();
        /** Change of processing state*/
        void processingStateChanged(boolean processing);
        /** Title of processing changed */
        void titleChanged(String title);
        /** Options for USB device changed */
        void optionsUpdated(List<UsbDeviceSettings> options);
    }

    private static final String TAG = UsbHostController.class.getSimpleName();
    private static final boolean LOCAL_LOGD = true;
    private static final boolean LOCAL_LOGV = true;


    private final List<UsbDeviceSettings> mEmptyList = new ArrayList<>();
    private final Context mContext;
    private final UsbHostControllerCallbacks mCallback;
    private final UsbSettingsStorage mUsbSettingsStorage;
    private final UsbManager mUsbManager;
    private final PackageManager mPackageManager;
    private final UsbDeviceHandlerResolver mUsbReslover;
    private final UsbHostControllerHandler mHandler;

    private final BroadcastReceiver mUsbBroadcastReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice device = intent.<UsbDevice>getParcelableExtra(UsbManager.EXTRA_DEVICE);
                unsetActiveDeviceIfSerialMatch(device);
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                UsbDevice device = intent.<UsbDevice>getParcelableExtra(UsbManager.EXTRA_DEVICE);
                setActiveDeviceIfSerialMatch(device);
            }
        }
    };

    @GuardedBy("this")
    private UsbDevice mActiveDevice;

    @GuardedBy("this")
    private String mProcessingDeviceSerial;

    public UsbHostController(Context context, UsbHostControllerCallbacks callbacks) {
        mContext = context;
        mCallback = callbacks;
        mHandler = new UsbHostControllerHandler(Looper.myLooper());
        mUsbSettingsStorage = new UsbSettingsStorage(context);
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mPackageManager = context.getPackageManager();
        mUsbReslover = new UsbDeviceHandlerResolver(mUsbManager, mContext, this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsbBroadcastReceiver, filter);

    }

    private synchronized void setActiveDeviceIfSerialMatch(UsbDevice device) {
        if (device != null && device.getSerialNumber() != null
                && device.getSerialNumber().equals(mProcessingDeviceSerial)) {
            mActiveDevice = device;
        }
    }

    private synchronized void unsetActiveDeviceIfSerialMatch(UsbDevice device) {
        mHandler.requestDeviceRemoved();
        if (mActiveDevice != null && mActiveDevice.getSerialNumber() != null
                && mActiveDevice.getSerialNumber().equals(device.getSerialNumber())) {
            mActiveDevice = null;
        }
    }

    private synchronized boolean startDeviceProcessingIfNull(UsbDevice device) {
        if (mActiveDevice == null) {
            mActiveDevice = device;
            mProcessingDeviceSerial = device.getSerialNumber();
            return true;
        }
        return false;
    }

    private synchronized void stopDeviceProcessing() {
        mActiveDevice = null;
        mProcessingDeviceSerial = null;
    }

    private synchronized UsbDevice getActiveDevice() {
        return mActiveDevice;
    }

    private boolean deviceMatchedActiveDevice(UsbDevice device) {
        UsbDevice activeDevice = getActiveDevice();
        return activeDevice != null && activeDevice.getSerialNumber() != null
                && activeDevice.getSerialNumber().equals(device.getSerialNumber());
    }

    /**
     * Processes device new device.
     * <p>
     * It will load existing settings or resolve supported handlers.
     */
    public void processDevice(UsbDevice device) {
        if (!startDeviceProcessingIfNull(device)) {
            Log.w(TAG, "Currently, other device is being processed");
        }
        mCallback.optionsUpdated(mEmptyList);
        mCallback.processingStateChanged(true);

        UsbDeviceSettings settings = mUsbSettingsStorage.getSettings(
                device.getSerialNumber(), device.getVendorId(), device.getProductId());
        if (settings != null && mUsbReslover.dispatch(
                    mActiveDevice, settings.getHandler(), settings.getAoap())) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Usb Device: " + device + " was sent to component: "
                        + settings.getHandler());
            }
            return;
        }
        mCallback.titleChanged(device.getManufacturerName() + " " + device.getProductName());
        mUsbReslover.resolve(device);
    }

    /**
     * Applies device settings.
     */
    public void applyDeviceSettings(UsbDeviceSettings settings) {
        mUsbSettingsStorage.saveSettings(settings);
        mUsbReslover.dispatch(getActiveDevice(), settings.getHandler(), settings.getAoap());
    }

    /**
     * Release object.
     */
    public void release() {
        mContext.unregisterReceiver(mUsbBroadcastReceiver);
        mUsbReslover.release();
    }

    @Override
    public void onHandlersResolveCompleted(
            UsbDevice device, List<UsbDeviceSettings> handlers) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "onHandlersResolveComplete: " + device);
        }
        if (deviceMatchedActiveDevice(device)) {
            mCallback.processingStateChanged(false);
            if (handlers.isEmpty()) {
                onDeviceDispatched();
            } else {
                mCallback.optionsUpdated(handlers);
            }
        } else {
            Log.w(TAG, "Handlers ignored as they came for inactive device");
        }
    }

    @Override
    public void onDeviceDispatched() {
        stopDeviceProcessing();
        mCallback.shutdown();
    }

    void doHandleDeviceRemoved() {
        if (getActiveDevice() == null) {
            if (LOCAL_LOGD) {
                Log.d(TAG, "USB device detached");
            }
            stopDeviceProcessing();
            mCallback.shutdown();
        }
    }

    private static Intent createDeviceAttachedIntent(UsbDevice device) {
        Intent intent = new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intent.putExtra(UsbManager.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private class UsbHostControllerHandler extends Handler {
        private static final int MSG_DEVICE_REMOVED = 1;

        private static final int DEVICE_REMOVE_TIMEOUT_MS = 500;

        private UsbHostControllerHandler(Looper looper) {
            super(looper);
        }

        private void requestDeviceRemoved() {
            sendEmptyMessageDelayed(MSG_DEVICE_REMOVED, DEVICE_REMOVE_TIMEOUT_MS);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DEVICE_REMOVED:
                    doHandleDeviceRemoved();
                    break;
                default:
                    Log.w(TAG, "Unhandled message: " + msg);
                    super.handleMessage(msg);
            }
        }
    }


}
