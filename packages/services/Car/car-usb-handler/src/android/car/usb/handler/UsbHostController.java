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
package android.car.usb.handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
 * TODO: Support handling multiple new USB devices at the same time.
 */
public final class UsbHostController
        implements UsbDeviceHandlerResolver.UsbDeviceHandlerResolverCallback {

    /**
     * Callbacks for controller
     */
    public interface UsbHostControllerCallbacks {
        /** Host controller ready for shutdown */
        void shutdown();
        /** Change of processing state */
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
    private final UsbDeviceHandlerResolver mUsbResolver;
    private final UsbHostControllerHandler mHandler;

    private final BroadcastReceiver mUsbBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice device = intent.<UsbDevice>getParcelableExtra(UsbManager.EXTRA_DEVICE);
                unsetActiveDeviceIfMatch(device);
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                UsbDevice device = intent.<UsbDevice>getParcelableExtra(UsbManager.EXTRA_DEVICE);
                setActiveDeviceIfMatch(device);
            }
        }
    };

    @GuardedBy("this")
    private UsbDevice mActiveDevice;

    public UsbHostController(Context context, UsbHostControllerCallbacks callbacks) {
        mContext = context;
        mCallback = callbacks;
        mHandler = new UsbHostControllerHandler(Looper.myLooper());
        mUsbSettingsStorage = new UsbSettingsStorage(context);
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mUsbResolver = new UsbDeviceHandlerResolver(mUsbManager, mContext, this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsbBroadcastReceiver, filter);

    }

    private synchronized void setActiveDeviceIfMatch(UsbDevice device) {
        if (mActiveDevice != null && device != null
                && UsbUtil.isDevicesMatching(device, mActiveDevice)) {
            mActiveDevice = device;
        }
    }

    private synchronized void unsetActiveDeviceIfMatch(UsbDevice device) {
        mHandler.requestDeviceRemoved();
        if (mActiveDevice != null && device != null
                && UsbUtil.isDevicesMatching(device, mActiveDevice)) {
            mActiveDevice = null;
        }
    }

    private synchronized boolean startDeviceProcessingIfNull(UsbDevice device) {
        if (mActiveDevice == null) {
            mActiveDevice = device;
            return true;
        }
        return false;
    }

    private synchronized void stopDeviceProcessing() {
        mActiveDevice = null;
    }

    private synchronized UsbDevice getActiveDevice() {
        return mActiveDevice;
    }

    private boolean deviceMatchedActiveDevice(UsbDevice device) {
        UsbDevice activeDevice = getActiveDevice();
        return activeDevice != null && UsbUtil.isDevicesMatching(activeDevice, device);
    }

    private String generateTitle() {
        String manufacturer = mActiveDevice.getManufacturerName();
        String product = mActiveDevice.getProductName();
        if (manufacturer == null && product == null) {
            return mContext.getString(R.string.usb_unknown_device);
        }
        if (manufacturer != null && product != null) {
            return manufacturer + " " + product;
        }
        if (manufacturer != null) {
            return manufacturer;
        }
        return product;
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

        UsbDeviceSettings settings = mUsbSettingsStorage.getSettings(device);
        if (settings != null && mUsbResolver.dispatch(
                    mActiveDevice, settings.getHandler(), settings.getAoap())) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Usb Device: " + device + " was sent to component: "
                        + settings.getHandler());
            }
            return;
        }
        mCallback.titleChanged(generateTitle());
        mUsbResolver.resolve(device);
    }

    /**
     * Applies device settings.
     */
    public void applyDeviceSettings(UsbDeviceSettings settings) {
        mUsbSettingsStorage.saveSettings(settings);
        mUsbResolver.dispatch(getActiveDevice(), settings.getHandler(), settings.getAoap());
    }

    /**
     * Release object.
     */
    public void release() {
        mContext.unregisterReceiver(mUsbBroadcastReceiver);
        mUsbResolver.release();
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
            } else if (handlers.size() == 1) {
                applyDeviceSettings(handlers.get(0));
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
