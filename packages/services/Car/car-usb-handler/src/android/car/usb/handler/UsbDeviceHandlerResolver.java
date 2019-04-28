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
import android.hardware.usb.UsbDeviceConnection;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.xmlpull.v1.XmlPullParser;

/** Resolves supported handlers for USB device. */
public final class UsbDeviceHandlerResolver {
    private static final String TAG = UsbDeviceHandlerResolver.class.getSimpleName();
    private static final boolean LOCAL_LOGD = true;

    /**
     * Callbacks for device resolver.
     */
    public interface UsbDeviceHandlerResolverCallback {
        /** Handlers are resolved */
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

    private class DeviceContext {
        public final UsbDevice usbDevice;
        public final UsbDeviceConnection connection;
        public final UsbDeviceSettings settings;
        public final List<UsbDeviceSettings> activeDeviceSettings;
        public final Queue<Pair<ResolveInfo, DeviceFilter>> mActiveDeviceOptions =
                new LinkedList<>();

        private volatile IUsbAoapSupportCheckService mUsbAoapSupportCheckService;
        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.i(TAG, "onServiceConnected: " + className);
                mUsbAoapSupportCheckService = IUsbAoapSupportCheckService.Stub.asInterface(service);
                mHandler.requestOnServiceConnectionStateChanged(DeviceContext.this);
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                Log.i(TAG, "onServiceDisconnected: " + className);
                mUsbAoapSupportCheckService = null;
                mHandler.requestOnServiceConnectionStateChanged(DeviceContext.this);
            }
        };

        public DeviceContext(UsbDevice usbDevice, UsbDeviceSettings settings,
                List<UsbDeviceSettings> activeDeviceSettings) {
            this.usbDevice = usbDevice;
            this.settings = settings;
            this.activeDeviceSettings = activeDeviceSettings;
            connection = UsbUtil.openConnection(mUsbManager, usbDevice);
        }
    }

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
        // USB device in AOAP mode model
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

        public static DeviceFilter read(XmlPullParser parser, boolean aoapData) {
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

    public UsbDeviceHandlerResolver(UsbManager manager, Context context,
            UsbDeviceHandlerResolverCallback deviceListener) {
        mUsbManager = manager;
        mContext = context;
        mDeviceCallback = deviceListener;
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new UsbDeviceResolverHandler(mHandlerThread.getLooper());
        mPackageManager = context.getPackageManager();
    }

    /**
     * Releases current object.
     */
    public void release() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
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
            Log.d(TAG, "dispatch: " + device + " component: " + component + " inAoap: " + inAoap);
        }

        ActivityInfo activityInfo;
        try {
            activityInfo = mPackageManager.getActivityInfo(component, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Activity not found: " + component);
            return false;
        }

        Intent intent = createDeviceAttachedIntent(device);
        if (inAoap) {
            if (AoapInterface.isDeviceInAoapMode(device)) {
                mDeviceCallback.onDeviceDispatched();
            } else {
                DeviceFilter filter =
                        packageMatches(activityInfo, intent.getAction(), device, true);
                if (filter != null) {
                    requestAoapSwitch(device, filter);
                    return true;
                }
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

    private void doHandleResolveHandlers(UsbDevice device) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "doHandleResolveHandlers: " + device);
        }

        Intent intent = createDeviceAttachedIntent(device);
        List<Pair<ResolveInfo, DeviceFilter>> matches = getDeviceMatches(device, intent, false);
        if (LOCAL_LOGD) {
            Log.d(TAG, "matches size: " + matches.size());
        }
        List<UsbDeviceSettings> settings = new ArrayList<>(matches.size());
        for (Pair<ResolveInfo, DeviceFilter> info : matches) {
            UsbDeviceSettings setting = UsbDeviceSettings.constructSettings(device);
            setting.setHandler(
                    new ComponentName(
                            info.first.activityInfo.packageName, info.first.activityInfo.name));
            settings.add(setting);
        }
        DeviceContext deviceContext =
                new DeviceContext(device, UsbDeviceSettings.constructSettings(device), settings);
        if (AoapInterface.isSupported(deviceContext.connection)) {
            deviceContext.mActiveDeviceOptions.addAll(getDeviceMatches(device, intent, true));
            queryNextAoapHandler(deviceContext);
        } else {
            deviceProbingComplete(deviceContext);
        }
    }

    private void queryNextAoapHandler(DeviceContext context) {
        Pair<ResolveInfo, DeviceFilter> option = context.mActiveDeviceOptions.peek();
        if (option == null) {
            Log.w(TAG, "No more options left.");
            deviceProbingComplete(context);
            return;
        }
        Intent serviceIntent = new Intent();
        serviceIntent.setComponent(ComponentName.unflattenFromString(option.second.mAoapService));
        boolean bound = mContext.bindService(serviceIntent, context.mServiceConnection,
                Context.BIND_AUTO_CREATE);
        if (bound) {
            mHandler.requestServiceConnectionTimeout();
        } else {
            if (LOCAL_LOGD) {
                Log.d(TAG, "Failed to bind to the service");
            }
            context.mActiveDeviceOptions.poll();
            queryNextAoapHandler(context);
        }
    }

    private void requestAoapSwitch(UsbDevice device, DeviceFilter filter) {
        UsbDeviceConnection connection = UsbUtil.openConnection(mUsbManager, device);
        try {
            UsbUtil.sendAoapAccessoryStart(
                    connection,
                    filter.mAoapManufacturer,
                    filter.mAoapModel,
                    filter.mAoapDescription,
                    filter.mAoapVersion,
                    filter.mAoapUri,
                    filter.mAoapSerial);
        } catch (IOException e) {
            Log.w(TAG, "Failed to switch device into AOAP mode", e);
        }
        connection.close();
    }

    private void deviceProbingComplete(DeviceContext context) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "deviceProbingComplete");
        }
        mDeviceCallback.onHandlersResolveCompleted(context.usbDevice, context.activeDeviceSettings);
    }

    private void doHandleServiceConnectionStateChanged(DeviceContext context) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "doHandleServiceConnectionStateChanged: "
                    + context.mUsbAoapSupportCheckService);
        }
        if (context.mUsbAoapSupportCheckService != null) {
            boolean deviceSupported = false;
            try {
                deviceSupported =
                        context.mUsbAoapSupportCheckService.isDeviceSupported(context.usbDevice);
            } catch (RemoteException e) {
                Log.e(TAG, "Call to remote service failed", e);
            }
            if (deviceSupported) {
                Pair<ResolveInfo, DeviceFilter> option = context.mActiveDeviceOptions.peek();

                UsbDeviceSettings setting = UsbDeviceSettings.constructSettings(context.settings);
                setting.setHandler(
                        new ComponentName(
                            option.first.activityInfo.packageName, option.first.activityInfo.name));
                setting.setAoap(true);
                context.activeDeviceSettings.add(setting);
            }
            mContext.unbindService(context.mServiceConnection);
        }
        context.mActiveDeviceOptions.poll();
        queryNextAoapHandler(context);
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

    private class UsbDeviceResolverHandler extends Handler {
        private static final int MSG_RESOLVE_HANDLERS = 0;
        private static final int MSG_SERVICE_CONNECTION_STATE_CHANGE = 1;
        private static final int MSG_SERVICE_CONNECTION_TIMEOUT = 2;
        private static final int MSG_COMPLETE_DISPATCH = 3;

        private static final long CONNECT_TIMEOUT_MS = 5000;

        private UsbDeviceResolverHandler(Looper looper) {
            super(looper);
        }

        public void requestResolveHandlers(UsbDevice device) {
            Message msg = obtainMessage(MSG_RESOLVE_HANDLERS, device);
            sendMessage(msg);
        }

        public void requestOnServiceConnectionStateChanged(DeviceContext deviceContext) {
            sendMessage(obtainMessage(MSG_SERVICE_CONNECTION_STATE_CHANGE, deviceContext));
        }

        public void requestServiceConnectionTimeout() {
            sendEmptyMessageDelayed(MSG_SERVICE_CONNECTION_TIMEOUT, CONNECT_TIMEOUT_MS);
        }

        public void requestCompleteDeviceDispatch() {
            sendEmptyMessage(MSG_COMPLETE_DISPATCH);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RESOLVE_HANDLERS:
                    doHandleResolveHandlers((UsbDevice) msg.obj);
                    break;
                case MSG_SERVICE_CONNECTION_STATE_CHANGE:
                    removeMessages(MSG_SERVICE_CONNECTION_TIMEOUT);
                    doHandleServiceConnectionStateChanged((DeviceContext) msg.obj);
                    break;
                case MSG_SERVICE_CONNECTION_TIMEOUT:
                    Log.i(TAG, "Service connection timeout");
                    doHandleServiceConnectionStateChanged(null);
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
