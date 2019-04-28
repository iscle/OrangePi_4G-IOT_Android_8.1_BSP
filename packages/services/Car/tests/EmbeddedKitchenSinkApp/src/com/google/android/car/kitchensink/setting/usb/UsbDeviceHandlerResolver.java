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

import android.car.IUsbAoapSupportCheckService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Resolves supported handlers for USB device.
 */
public final class UsbDeviceHandlerResolver
        implements UsbDeviceStateController.UsbDeviceStateListener {
    private static final String TAG = UsbDeviceHandlerResolver.class.getSimpleName();
    private static final boolean LOCAL_LOGD = true;

    private static final int MODE_OFF = 0;
    private static final int MODE_PROBE = 1;
    private static final int MODE_PROBE_AOAP = 2;
    private static final int MODE_DISPATCH = 3;

    /**
     * Callbacks for device reolver.
     */
    public interface UsbDeviceHandlerResolverCallback {
        /** Handlers are reolved */
        void onHandlersResolveCompleted(
                UsbDevice device, List<UsbDeviceSettings> availableSettings);
        /** Device was dispatched */
        void onDeviceDispatched();
    }

    private final UsbManager mUsbManager;
    private final PackageManager mPackageManager;
    private final UsbDeviceHandlerResolverCallback mDeviceCallback;
    private final Context mContext;
    private final HandlerThread mHandlerThread;
    private final UsbDeviceResolverHandler mHandler;
    private final UsbDeviceStateController mStateController;
    private final Queue<Pair<ResolveInfo, DeviceFilter>> mActiveDeviceOptions = new LinkedList<>();
    private final List<UsbDeviceSettings> mActiveDeviceSettings = new ArrayList<>();

    private String mActiveDeviceSerial;
    private UsbDevice mActiveUsbDevice;
    private UsbDeviceSettings mBaseSettings;
    private int mDeviceMode = MODE_OFF;
    private Intent mDispatchIntent;
    private int mDispatchUid;
    private IUsbAoapSupportCheckService mIUsbAoapSupportCheckService;
    private boolean mBound;

    // This class is used to describe a USB device.
    // When used in HashMaps all values must be specified,
    // but wildcards can be used for any of the fields in
    // the package meta-data.
    private static class DeviceFilter {
        // USB Vendor ID (or -1 for unspecified)
        public final int mVendorId;
        // USB Product ID (or -1 for unspecified)
        public final int mProductId;
        // USB device or interface class (or -1 for unspecified)
        public final int mClass;
        // USB device subclass (or -1 for unspecified)
        public final int mSubclass;
        // USB device protocol (or -1 for unspecified)
        public final int mProtocol;
        // USB device manufacturer name string (or null for unspecified)
        public final String mManufacturerName;
        // USB device product name string (or null for unspecified)
        public final String mProductName;
        // USB device serial number string (or null for unspecified)
        public final String mSerialNumber;

        // USB device in AOAP mode manufacturer
        public final String mAoapManufacturer;
        // USB device in AOAP mode modeal
        public final String mAoapModel;
        // USB device in AOAP mode description string
        public final String mAoapDescription;
        // USB device in AOAP mode version
        public final String mAoapVersion;
        // USB device in AOAP mode URI
        public final String mAoapUri;
        // USB device in AOAP mode serial
        public final String mAoapSerial;
        // USB device in AOAP mode verification service
        public final String mAoapService;

        DeviceFilter(int vid, int pid, int clasz, int subclass, int protocol,
                            String manufacturer, String product, String serialnum,
                            String aoapManufacturer, String aoapModel, String aoapDescription,
                            String aoapVersion, String aoapUri, String aoapSerial,
                            String aoapService) {
            mVendorId = vid;
            mProductId = pid;
            mClass = clasz;
            mSubclass = subclass;
            mProtocol = protocol;
            mManufacturerName = manufacturer;
            mProductName = product;
            mSerialNumber = serialnum;

            mAoapManufacturer = aoapManufacturer;
            mAoapModel = aoapModel;
            mAoapDescription = aoapDescription;
            mAoapVersion = aoapVersion;
            mAoapUri = aoapUri;
            mAoapSerial = aoapSerial;
            mAoapService = aoapService;
        }

        DeviceFilter(UsbDevice device) {
            mVendorId = device.getVendorId();
            mProductId = device.getProductId();
            mClass = device.getDeviceClass();
            mSubclass = device.getDeviceSubclass();
            mProtocol = device.getDeviceProtocol();
            mManufacturerName = device.getManufacturerName();
            mProductName = device.getProductName();
            mSerialNumber = device.getSerialNumber();
            mAoapManufacturer = null;
            mAoapModel = null;
            mAoapDescription = null;
            mAoapVersion = null;
            mAoapUri = null;
            mAoapSerial = null;
            mAoapService = null;
        }

        public static DeviceFilter read(XmlPullParser parser, boolean aoapData)
                throws XmlPullParserException, IOException {
            int vendorId = -1;
            int productId = -1;
            int deviceClass = -1;
            int deviceSubclass = -1;
            int deviceProtocol = -1;
            String manufacturerName = null;
            String productName = null;
            String serialNumber = null;

            String aoapManufacturer = null;
            String aoapModel = null;
            String aoapDescription = null;
            String aoapVersion = null;
            String aoapUri = null;
            String aoapSerial = null;
            String aoapService = null;

            int count = parser.getAttributeCount();
            for (int i = 0; i < count; i++) {
                String name = parser.getAttributeName(i);
                String value = parser.getAttributeValue(i);
                // Attribute values are ints or strings
                if (!aoapData && "manufacturer-name".equals(name)) {
                    manufacturerName = value;
                } else if (!aoapData && "product-name".equals(name)) {
                    productName = value;
                } else if (!aoapData && "serial-number".equals(name)) {
                    serialNumber = value;
                } else if (aoapData && "manufacturer".equals(name)) {
                    aoapManufacturer = value;
                } else if (aoapData && "model".equals(name)) {
                    aoapModel = value;
                } else if (aoapData && "description".equals(name)) {
                    aoapDescription = value;
                } else if (aoapData && "version".equals(name)) {
                    aoapVersion = value;
                } else if (aoapData && "uri".equals(name)) {
                    aoapUri = value;
                } else if (aoapData && "serial".equals(name)) {
                    aoapSerial = value;
                } else if (aoapData && "service".equals(name)) {
                    aoapService = value;
                } else if (!aoapData) {
                    int intValue = -1;
                    int radix = 10;
                    if (value != null && value.length() > 2 && value.charAt(0) == '0'
                            && (value.charAt(1) == 'x' || value.charAt(1) == 'X')) {
                        // allow hex values starting with 0x or 0X
                        radix = 16;
                        value = value.substring(2);
                    }
                    try {
                        intValue = Integer.parseInt(value, radix);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "invalid number for field " + name, e);
                        continue;
                    }
                    if ("vendor-id".equals(name)) {
                        vendorId = intValue;
                    } else if ("product-id".equals(name)) {
                        productId = intValue;
                    } else if ("class".equals(name)) {
                        deviceClass = intValue;
                    } else if ("subclass".equals(name)) {
                        deviceSubclass = intValue;
                    } else if ("protocol".equals(name)) {
                        deviceProtocol = intValue;
                    }
                }
            }
            return new DeviceFilter(vendorId, productId,
                                    deviceClass, deviceSubclass, deviceProtocol,
                                    manufacturerName, productName, serialNumber, aoapManufacturer,
                                    aoapModel, aoapDescription, aoapVersion, aoapUri, aoapSerial,
                                    aoapService);
        }

        private boolean matches(int clasz, int subclass, int protocol) {
            return ((mClass == -1 || clasz == mClass)
                    && (mSubclass == -1 || subclass == mSubclass)
                    && (mProtocol == -1 || protocol == mProtocol));
        }

        public boolean isAoap() {
            return (mVendorId == AoapInterface.USB_ACCESSORY_VENDOR_ID
                    && mProductId == AoapInterface.USB_ACCESSORY_PRODUCT_ID);
        }

        public boolean matches(UsbDevice device) {
            if (mVendorId != -1 && device.getVendorId() != mVendorId) {
                return false;
            }
            if (mProductId != -1 && device.getProductId() != mProductId) {
                return false;
            }
            if (mManufacturerName != null && device.getManufacturerName() == null) {
                return false;
            }
            if (mProductName != null && device.getProductName() == null) {
                return false;
            }
            if (mSerialNumber != null && device.getSerialNumber() == null) {
                return false;
            }
            if (mManufacturerName != null && device.getManufacturerName() != null
                    && !mManufacturerName.equals(device.getManufacturerName())) {
                return false;
            }
            if (mProductName != null && device.getProductName() != null
                    && !mProductName.equals(device.getProductName())) {
                return false;
            }
            if (mSerialNumber != null && device.getSerialNumber() != null
                    && !mSerialNumber.equals(device.getSerialNumber())) {
                return false;
            }

            // check device class/subclass/protocol
            if (matches(device.getDeviceClass(), device.getDeviceSubclass(),
                        device.getDeviceProtocol())) {
                return true;
            }

            // if device doesn't match, check the interfaces
            int count = device.getInterfaceCount();
            for (int i = 0; i < count; i++) {
                UsbInterface intf = device.getInterface(i);
                if (matches(intf.getInterfaceClass(), intf.getInterfaceSubclass(),
                            intf.getInterfaceProtocol())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean equals(Object obj) {
            // can't compare if we have wildcard strings
            if (mVendorId == -1 || mProductId == -1
                    || mClass == -1 || mSubclass == -1 || mProtocol == -1) {
                return false;
            }
            if (obj instanceof DeviceFilter) {
                DeviceFilter filter = (DeviceFilter) obj;

                if (filter.mVendorId != mVendorId
                        || filter.mProductId != mProductId
                        || filter.mClass != mClass
                        || filter.mSubclass != mSubclass
                        || filter.mProtocol != mProtocol) {
                    return false;
                }
                if ((filter.mManufacturerName != null && mManufacturerName == null)
                        || (filter.mManufacturerName == null && mManufacturerName != null)
                        || (filter.mProductName != null && mProductName == null)
                        || (filter.mProductName == null && mProductName != null)
                        || (filter.mSerialNumber != null && mSerialNumber == null)
                        || (filter.mSerialNumber == null && mSerialNumber != null)) {
                    return false;
                }
                if  ((filter.mManufacturerName != null && mManufacturerName != null
                          && !mManufacturerName.equals(filter.mManufacturerName))
                          || (filter.mProductName != null && mProductName != null
                          && !mProductName.equals(filter.mProductName))
                          || (filter.mSerialNumber != null && mSerialNumber != null
                          && !mSerialNumber.equals(filter.mSerialNumber))) {
                    return false;
                }
                return true;
            }
            if (obj instanceof UsbDevice) {
                UsbDevice device = (UsbDevice) obj;
                if (device.getVendorId() != mVendorId
                        || device.getProductId() != mProductId
                        || device.getDeviceClass() != mClass
                        || device.getDeviceSubclass() != mSubclass
                        || device.getDeviceProtocol() != mProtocol) {
                    return false;
                }
                if ((mManufacturerName != null && device.getManufacturerName() == null)
                        || (mManufacturerName == null && device.getManufacturerName() != null)
                        || (mProductName != null && device.getProductName() == null)
                        || (mProductName == null && device.getProductName() != null)
                        || (mSerialNumber != null && device.getSerialNumber() == null)
                        || (mSerialNumber == null && device.getSerialNumber() != null)) {
                    return false;
                }
                if ((device.getManufacturerName() != null
                        && !mManufacturerName.equals(device.getManufacturerName()))
                        || (device.getProductName() != null
                        && !mProductName.equals(device.getProductName()))
                        || (device.getSerialNumber() != null
                        && !mSerialNumber.equals(device.getSerialNumber()))) {
                    return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (((mVendorId << 16) | mProductId)
                    ^ ((mClass << 16) | (mSubclass << 8) | mProtocol));
        }

        @Override
        public String toString() {
            return "DeviceFilter[mVendorId=" + mVendorId + ",mProductId=" + mProductId
                    + ",mClass=" + mClass + ",mSubclass=" + mSubclass
                    + ",mProtocol=" + mProtocol + ",mManufacturerName=" + mManufacturerName
                    + ",mProductName=" + mProductName + ",mSerialNumber=" + mSerialNumber + "]";
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected: " + className);
            mHandler.requestOnServiceConnectionStateChanged(
                    IUsbAoapSupportCheckService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceDisconnected: " + className);
            mHandler.requestOnServiceConnectionStateChanged(null);
        }
    };

    public UsbDeviceHandlerResolver(UsbManager manager, Context context,
            UsbDeviceHandlerResolverCallback deviceListener) {
        mUsbManager = manager;
        mContext = context;
        mDeviceCallback = deviceListener;
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new UsbDeviceResolverHandler(mHandlerThread.getLooper());
        mPackageManager = context.getPackageManager();
        mStateController = new UsbDeviceStateController(context, this, manager);
        mStateController.init();
    }

    /**
     * Releases current object.
     */
    public void release() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
        if (mStateController != null) {
            mStateController.release();
        }
    }

    /**
     * Resolves handlers for USB device.
     */
    public void resolve(UsbDevice device) {
        mHandler.requestResolveHandlers(device);
    }

    /**
     * Dispatches device to component.
     */
    public boolean dispatch(UsbDevice device, ComponentName component, boolean inAoap) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "dispatch: " + device + " component: " + component + " inAoap:" + inAoap);
        }

        mActiveUsbDevice = device;
        mDeviceMode = MODE_DISPATCH;
        ActivityInfo activityInfo;
        try {
            activityInfo = mPackageManager.getActivityInfo(component, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Activity not found: " + component);
            return false;
        }

        Intent intent = createDeviceAttachedIntent(device);
        if (inAoap) {
            DeviceFilter filter = packageMatches(activityInfo, intent.getAction(), device, true);
            intent.setComponent(component);
            mDispatchIntent = intent;
            mDispatchUid = activityInfo.applicationInfo.uid;
            if (filter != null) {
                requestAoapSwitch(filter);
                return true;
            }
        }

        intent.setComponent(component);
        mUsbManager.grantPermission(device, activityInfo.applicationInfo.uid);

        mContext.startActivity(intent);
        mHandler.requestCompleteDeviceDispatch();
        return true;
    }

    private static Intent createDeviceAttachedIntent(UsbDevice device) {
        Intent intent = new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intent.putExtra(UsbManager.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void doHandleResolveHandlers(
            UsbDevice device) {
        mActiveDeviceSettings.clear();
        mActiveDeviceOptions.clear();
        mActiveUsbDevice = device;
        mDeviceMode = MODE_PROBE;

        if (LOCAL_LOGD) {
            Log.d(TAG, "doHandleResolveHandlers: " + device);
        }
        boolean maySupportAoap = UsbUtil.possiblyAndroid(device);
        boolean isInAoap = AoapInterface.isDeviceInAoapMode(device);

        Intent intent = createDeviceAttachedIntent(device);
        List<Pair<ResolveInfo, DeviceFilter>> matches = getDeviceMatches(device, intent, false);
        if (LOCAL_LOGD) {
            Log.d(TAG, "matches size: " + matches.size());
        }
        for (Pair<ResolveInfo, DeviceFilter> info : matches) {
            UsbDeviceSettings setting = UsbDeviceSettings.constructSettings(mActiveUsbDevice);
            setting.setHandler(
                    new ComponentName(
                            info.first.activityInfo.packageName, info.first.activityInfo.name));
            mActiveDeviceSettings.add(setting);
        }
        mBaseSettings = UsbDeviceSettings.constructSettings(mActiveUsbDevice);
        if (!AoapInterface.isDeviceInAoapMode(device) && maySupportAoap) {
            mActiveDeviceOptions.addAll(getDeviceMatches(device, intent, true));
            handleTryNextAoapMode();
        } else {
            doHandleCompleteDeviceProbing();
        }
    }

    private void handleTryNextAoapMode() {
        if (LOCAL_LOGD) {
            Log.d(TAG, "handleTryNextAoapMode");
        }
        Pair<ResolveInfo, DeviceFilter> option = mActiveDeviceOptions.peek();
        if (option == null) {
            mHandler.requestCompleteDeviceProbing();
            return;
        }
        requestAoapSwitch(option.second);
    }

    private void requestAoapSwitch(DeviceFilter filter) {
        UsbDeviceStateController.AoapSwitchRequest request =
                new UsbDeviceStateController.AoapSwitchRequest(
                        mActiveUsbDevice,
                        filter.mAoapManufacturer,
                        filter.mAoapModel,
                        filter.mAoapDescription,
                        filter.mAoapVersion,
                        filter.mAoapUri,
                        filter.mAoapSerial);
        mStateController.startAoap(request);
    }

    private void doHandleCompleteDeviceProbing() {
        if (LOCAL_LOGD) {
            Log.d(TAG, "doHandleCompleteDeviceProbing");
        }
        mDeviceCallback.onHandlersResolveCompleted(mActiveUsbDevice, mActiveDeviceSettings);
        stopDeviceProcessing(mActiveUsbDevice);
        mActiveUsbDevice = null;
        mBaseSettings = null;
        mDeviceMode = MODE_OFF;
    }

    private void doHandleAoapStartComplete(UsbDevice device) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "doHandleAoapStartComplete:" + device + " mode: " + MODE_DISPATCH);
        }
        if (mDeviceMode == MODE_DISPATCH && mDispatchIntent != null) {
            mDispatchIntent.putExtra(UsbManager.EXTRA_DEVICE, device);
            mUsbManager.grantPermission(device, mDispatchUid);
            mContext.startActivity(mDispatchIntent);
            mDispatchIntent = null;
            mDispatchUid = 0;
            mDeviceMode = MODE_OFF;
            mDeviceCallback.onDeviceDispatched();
            return;
        }
        mActiveUsbDevice = device;
        mDeviceMode = MODE_PROBE_AOAP;
        if (device == null) {
            mActiveDeviceOptions.poll();
            handleTryNextAoapMode();
        }

        Pair<ResolveInfo, DeviceFilter> option = mActiveDeviceOptions.peek();
        if (option == null) {
            Log.w(TAG, "No more options left.");
            mStateController.startDeviceReset(mActiveUsbDevice);
            return;
        }
        DeviceFilter filter = option.second;
        Intent serviceIntent = new Intent();
        serviceIntent.setComponent(ComponentName.unflattenFromString(option.second.mAoapService));
        boolean bound = mContext.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
        if (bound) {
            mHandler.requestServiceConnectionTimeout();
        } else {
            if (LOCAL_LOGD) {
                Log.d(TAG, "Failed to bind to the service");
            }
            mStateController.startDeviceReset(mActiveUsbDevice);
        }
    }

    private void doHandleDeviceResetComplete(UsbDevice device) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "doHandleDeviceResetComplete:" + device);
        }
        mActiveDeviceOptions.poll();
        mActiveUsbDevice = device;
        mDeviceMode = MODE_PROBE;
        handleTryNextAoapMode();
    }

    private void doHandleServiceConnectionStateChanged(IUsbAoapSupportCheckService service) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "doHandleServiceConnectionStateChanged: " + service);
        }
        mBound = service != null;
        mIUsbAoapSupportCheckService = service;
        if (mBound && mActiveUsbDevice != null && mDeviceMode == MODE_PROBE_AOAP) {
            boolean deviceSupported = false;
            try {
                deviceSupported =
                        mIUsbAoapSupportCheckService.isDeviceSupported(mActiveUsbDevice);
            } catch (RemoteException e) {
                Log.e(TAG, "Call to remote service failed", e);
            }
            if (deviceSupported) {
                Pair<ResolveInfo, DeviceFilter> option = mActiveDeviceOptions.peek();

                UsbDeviceSettings setting = UsbDeviceSettings.constructSettings(mBaseSettings);
                setting.setHandler(
                        new ComponentName(
                            option.first.activityInfo.packageName, option.first.activityInfo.name));
                setting.setAoap(true);
                mActiveDeviceSettings.add(setting);
            }
            mContext.unbindService(mConnection);
            mBound = false;
            mIUsbAoapSupportCheckService = null;
            mStateController.startDeviceReset(mActiveUsbDevice);
        } else if (mActiveUsbDevice != null && mDeviceMode == MODE_PROBE_AOAP) {
            mStateController.startDeviceReset(mActiveUsbDevice);
        } else {
            handleTryNextAoapMode();
        }
    }

    private List<Pair<ResolveInfo, DeviceFilter>> getDeviceMatches(
            UsbDevice device, Intent intent, boolean forAoap) {
        List<Pair<ResolveInfo, DeviceFilter>> matches = new ArrayList<>();
        List<ResolveInfo> resolveInfos =
                mPackageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA);
        for (ResolveInfo resolveInfo : resolveInfos) {
            DeviceFilter filter = packageMatches(resolveInfo.activityInfo,
                    intent.getAction(), device, forAoap);
            if (filter != null) {
                matches.add(Pair.create(resolveInfo, filter));
            }
        }
        return matches;
    }

    private DeviceFilter packageMatches(ActivityInfo ai, String metaDataName, UsbDevice device,
            boolean forAoap) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "packageMatches ai: " + ai + "metaDataName: " + metaDataName + " forAoap: "
                    + forAoap);
        }
        String filterTagName = forAoap ? "usb-aoap-accessory" : "usb-device";
        XmlResourceParser parser = null;
        try {
            parser = ai.loadXmlMetaData(mPackageManager, metaDataName);
            if (parser == null) {
                Log.w(TAG, "no meta-data for " + ai);
                return null;
            }

            XmlUtils.nextElement(parser);
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                if (device != null && filterTagName.equals(tagName)) {
                    DeviceFilter filter = DeviceFilter.read(parser, forAoap);
                    if (forAoap || filter.matches(device)) {
                        return filter;
                    }
                }
                XmlUtils.nextElement(parser);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to load component info " + ai.toString(), e);
        } finally {
            if (parser != null) parser.close();
        }
        return null;
    }

    @Override
    public void onDeviceResetComplete(UsbDevice device) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "onDeviceResetComplete: " + device);
        }
        mHandler.requestOnDeviceResetComplete(device);
    }

    @Override
    public void onAoapStartComplete(UsbDevice device) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "onAoapStartComplete: " + device);
        }
        mHandler.requestOnAoapStartComplete(device);
    }

    @Override
    public void onAoapStartFailed(UsbDevice device) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "onAoapStartFailed: " + device);
        }
        mActiveDeviceOptions.poll();
        handleTryNextAoapMode();
    }

    private boolean isDeviceProcessing(UsbDevice device) {
        return mActiveDeviceSerial != null
                && mActiveDeviceSerial.equals(device.getSerialNumber());
    }

    private boolean stopDeviceProcessing(UsbDevice device) {
        if (device == null || device.getSerialNumber().equals(mActiveDeviceSerial)) {
            mActiveDeviceSerial = null;
            return true;
        }
        return false;
    }

    private boolean startDeviceProcessing(UsbDevice device) {
        if (mActiveDeviceSerial != null) {
            return false;
        } else {
            mActiveDeviceSerial = device.getSerialNumber();
            return true;
        }
    }

    private class UsbDeviceResolverHandler extends Handler {
        private static final int MSG_RESOLVE_HANDLERS = 0;
        private static final int MSG_DEVICE_RESET_COMPLETE = 1;
        private static final int MSG_AOAP_START_COMPLETE = 2;
        private static final int MSG_SERVICE_CONNECTION_STATE_CHANGE = 3;
        private static final int MSG_SERVICE_CONNECTION_TIMEOUT = 4;
        private static final int MSG_COMPLETE_PROBING = 5;
        private static final int MSG_COMPLETE_DISPATCH = 6;

        private static final long RESCHEDULE_TIMEOUT_MS = 100;
        private static final long CONNECT_TIMEOUT_MS = 5000;
        private static final long FINISH_PROBING_TIMEOUT_MS = 200;

        private UsbDeviceResolverHandler(Looper looper) {
            super(looper);
        }

        public void requestResolveHandlers(UsbDevice device) {
            Message msg = obtainMessage(MSG_RESOLVE_HANDLERS, device);
            sendMessage(msg);
        }

        public void requestOnAoapStartComplete(UsbDevice device) {
            sendMessage(obtainMessage(MSG_AOAP_START_COMPLETE, device));
        }

        public void requestOnDeviceResetComplete(UsbDevice device) {
            sendMessage(obtainMessage(MSG_DEVICE_RESET_COMPLETE, device));
        }

        public void requestOnServiceConnectionStateChanged(IUsbAoapSupportCheckService service) {
            sendMessage(obtainMessage(MSG_SERVICE_CONNECTION_STATE_CHANGE, service));
        }

        public void requestServiceConnectionTimeout() {
            sendEmptyMessageDelayed(MSG_SERVICE_CONNECTION_TIMEOUT, CONNECT_TIMEOUT_MS);
        }

        public void requestCompleteDeviceProbing() {
            sendEmptyMessageDelayed(MSG_COMPLETE_PROBING, FINISH_PROBING_TIMEOUT_MS);
        }

        public void requestCompleteDeviceDispatch() {
            sendEmptyMessage(MSG_COMPLETE_DISPATCH);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RESOLVE_HANDLERS:
                    UsbDevice device = (UsbDevice) msg.obj;
                    // if this device is already being processed - drop the request.
                    if (!isDeviceProcessing(device)) {
                        if (startDeviceProcessing(device)) {
                            doHandleResolveHandlers(device);
                        } else {
                            // Reschedule this device for processing at later time.
                            sendMessageDelayed(msg, RESCHEDULE_TIMEOUT_MS);
                        }
                    } else {
                        Log.i(TAG, "Device is already being processed: " + device);
                    }
                    break;
                case MSG_AOAP_START_COMPLETE:
                    doHandleAoapStartComplete((UsbDevice) msg.obj);
                    break;
                case MSG_DEVICE_RESET_COMPLETE:
                    doHandleDeviceResetComplete((UsbDevice) msg.obj);
                    break;
                case MSG_SERVICE_CONNECTION_STATE_CHANGE:
                    removeMessages(MSG_SERVICE_CONNECTION_TIMEOUT);
                    doHandleServiceConnectionStateChanged((IUsbAoapSupportCheckService) msg.obj);
                    break;
                case MSG_SERVICE_CONNECTION_TIMEOUT:
                    Log.i(TAG, "Service connection timeout");
                    doHandleServiceConnectionStateChanged(null);
                    break;
                case MSG_COMPLETE_PROBING:
                    doHandleCompleteDeviceProbing();
                    break;
                case MSG_COMPLETE_DISPATCH:
                    mDeviceCallback.onDeviceDispatched();
                    break;
                default:
                    Log.w(TAG, "Unsupported message: " + msg);
            }
        }
    }
}
