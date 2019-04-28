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
 * limitations under the License.
 */
package com.mediatek.server.wifi;

import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.HwRemoteBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.server.wifi.util.NativeUtil;

import java.util.ArrayList;
import javax.annotation.concurrent.ThreadSafe;

import vendor.mediatek.hardware.wifi.hostapd.V1_0.HostapdStatus;
import vendor.mediatek.hardware.wifi.hostapd.V1_0.HostapdStatusCode;
import vendor.mediatek.hardware.wifi.hostapd.V1_0.IHostapd;
import vendor.mediatek.hardware.wifi.hostapd.V1_0.IHostapdIface;
import vendor.mediatek.hardware.wifi.hostapd.V1_0.IHostapdIfaceCallback;

/**
 * M: WiFi hotspot manager
 * Hal calls for bring up/shut down of the hostapd daemon and for
 * sending requests to the hostapd daemon
 * To maintain thread-safety, the locking protocol is that every non-static method (regardless of
 * access level) acquires mLock.
 */
@ThreadSafe
public class HostapdIfaceHal {
    private static final String TAG = "HostapdIfaceHal";

    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = true;

    // Hostapd HAL interface objects
    private IServiceManager mIServiceManager = null;
    private IHostapd mIHostapd;
    private IHostapdIface mIHostapdIface;
    private IHostapdIfaceCallback mIHostapdIfaceCallback;
    private final IServiceNotification mServiceNotificationCallback =
            new IServiceNotification.Stub() {
        public void onRegistration(String fqName, String name, boolean preexisting) {
            synchronized (mLock) {
                if (mVerboseLoggingEnabled) {
                    Log.i(TAG, "IServiceNotification.onRegistration for: " + fqName
                            + ", " + name + " preexisting=" + preexisting);
                }
                if (!initHostapdService() || !initHostapdIface()) {
                    Log.e(TAG, "initalizing IHostapdIfaces failed.");
                    hostapdServiceDiedHandler();
                } else {
                    Log.i(TAG, "Completed initialization of IHostapd interfaces.");
                }
            }
        }
    };
    private final HwRemoteBinder.DeathRecipient mServiceManagerDeathRecipient =
            cookie -> {
                synchronized (mLock) {
                    Log.w(TAG, "IServiceManager died: cookie=" + cookie);
                    hostapdServiceDiedHandler();
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                }
            };
    private final HwRemoteBinder.DeathRecipient mHostapdDeathRecipient =
            cookie -> {
                synchronized (mLock) {
                    Log.w(TAG, "IHostapd/IHostapdStaIface died: cookie=" + cookie);
                    hostapdServiceDiedHandler();
                }
            };

    private String mIfaceName;
    private final WifiApMonitor mWifiApMonitor;

    public HostapdIfaceHal(WifiApMonitor monitor) {
        mWifiApMonitor = monitor;
        mIHostapdIfaceCallback = new HostapdIfaceHalCallback();
    }

    /**
     * Enable/Disable verbose logging.
     *
     * @param enable true to enable, false to disable.
     */
    void enableVerboseLogging(boolean enable) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = enable;
        }
    }

    private boolean linkToServiceManagerDeath() {
        synchronized (mLock) {
            if (mIServiceManager == null) return false;
            try {
                if (!mIServiceManager.linkToDeath(mServiceManagerDeathRecipient, 0)) {
                    Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                    hostapdServiceDiedHandler();
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IServiceManager.linkToDeath exception", e);
                return false;
            }
            return true;
        }
    }

    /**
     * Registers a service notification for the IHostapd service, which triggers intialization of
     * the IHostapdIface
     * @return true if the service notification was successfully registered
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Registering IHostapd service ready callback.");
            }
            mIHostapd = null;
            mIHostapdIface = null;
            if (mIServiceManager != null) {
                // Already have an IServiceManager and serviceNotification registered, don't
                // don't register another.
                return true;
            }
            try {
                mIServiceManager = getServiceManagerMockable();
                if (mIServiceManager == null) {
                    Log.e(TAG, "Failed to get HIDL Service Manager");
                    return false;
                }
                if (!linkToServiceManagerDeath()) {
                    return false;
                }
                /* TODO(b/33639391) : Use the new IHostapd.registerForNotifications() once it
                   exists */
                if (!mIServiceManager.registerForNotifications(
                        IHostapd.kInterfaceName, "", mServiceNotificationCallback)) {
                    Log.e(TAG, "Failed to register for notifications to "
                            + IHostapd.kInterfaceName);
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while trying to register a listener for IHostapd service: "
                        + e);
                hostapdServiceDiedHandler();
            }
            return true;
        }
    }

    private boolean linkToHostapdDeath() {
        synchronized (mLock) {
            if (mIHostapd == null) return false;
            try {
                if (!mIHostapd.linkToDeath(mHostapdDeathRecipient, 0)) {
                    Log.wtf(TAG, "Error on linkToDeath on IHostapd");
                    hostapdServiceDiedHandler();
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IHostapd.linkToDeath exception", e);
                return false;
            }
            return true;
        }
    }

    private boolean initHostapdService() {
        synchronized (mLock) {
            try {
                mIHostapd = getHostapdMockable();
            } catch (RemoteException e) {
                Log.e(TAG, "IHostapd.getService exception: " + e);
                return false;
            }
            if (mIHostapd == null) {
                Log.e(TAG, "Got null IHostapd service. Stopping hostapd HIDL startup");
                return false;
            }
            if (!linkToHostapdDeath()) {
                return false;
            }
        }
        return true;
    }

    private boolean linkToHostapdIfaceDeath() {
        synchronized (mLock) {
            if (mIHostapdIface == null) return false;
            try {
                if (!mIHostapdIface.linkToDeath(mHostapdDeathRecipient, 0)) {
                    Log.wtf(TAG, "Error on linkToDeath on IHostapdIface");
                    hostapdServiceDiedHandler();
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IHostapdIface.linkToDeath exception", e);
                return false;
            }
            return true;
        }
    }

    private boolean initHostapdIface() {
        synchronized (mLock) {
            /** List all hostapd Ifaces */
            final ArrayList<String> hostapdIfaces = new ArrayList<>();
            try {
                mIHostapd.listInterfaces((HostapdStatus status,
                        ArrayList<String> ifaces) -> {
                    if (status.code != HostapdStatusCode.SUCCESS) {
                        Log.e(TAG, "Getting Hostapd Interfaces failed: " + status.code);
                        return;
                    }
                    hostapdIfaces.addAll(ifaces);
                });
            } catch (RemoteException e) {
                Log.e(TAG, "IHostapd.listInterfaces exception: " + e);
                return false;
            }
            if (hostapdIfaces.size() == 0) {
                Log.e(TAG, "Got zero HIDL hostapd ifaces. Stopping hostapd HIDL startup.");
                return false;
            }
            Mutable<IHostapdIface> hostapdIface = new Mutable<>();
            Mutable<String> ifaceName = new Mutable<>();
            for (String ifaceInfo : hostapdIfaces) {
                try {
                    mIHostapd.getInterface(ifaceInfo,
                            (HostapdStatus status, IHostapdIface iface) -> {
                            if (status.code != HostapdStatusCode.SUCCESS) {
                                Log.e(TAG, "Failed to get IHostapdIface " + status.code);
                                return;
                            }
                            hostapdIface.value = iface;
                        });
                } catch (RemoteException e) {
                    Log.e(TAG, "IHostapd.getInterface exception: " + e);
                    return false;
                }
                ifaceName.value = ifaceInfo;
                break;
            }
            if (hostapdIface.value == null) {
                Log.e(TAG, "initHostapdIface got null iface");
                return false;
            }
            mIHostapdIface = getApIfaceMockable(hostapdIface.value);
            mIfaceName = ifaceName.value;
            if (!linkToHostapdIfaceDeath()) {
                return false;
            }
            if (!registerCallback(mIHostapdIfaceCallback)) {
                return false;
            }
            return true;
        }
    }

    private void hostapdServiceDiedHandler() {
        synchronized (mLock) {
            mIHostapd = null;
            mIHostapdIface = null;
            mWifiApMonitor.broadcastSupplicantDisconnectionEvent(mIfaceName);
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mIServiceManager != null;
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mIHostapdIface != null;
        }
    }

    /**
     * Wrapper functions to access static HAL methods, created to be mockable in unit tests
     */
    protected IServiceManager getServiceManagerMockable() throws RemoteException {
        synchronized (mLock) {
            return IServiceManager.getService();
        }
    }

    protected IHostapd getHostapdMockable() throws RemoteException {
        synchronized (mLock) {
            return IHostapd.getService();
        }
    }

    protected IHostapdIface getApIfaceMockable(IHostapdIface iface) {
        synchronized (mLock) {
            return IHostapdIface.asInterface(iface.asBinder());
        }
    }

    /** See IHostapdIface.hal for documentation */
    private boolean registerCallback(IHostapdIfaceCallback callback) {
        synchronized (mLock) {
            final String methodStr = "registerCallback";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status =  mIHostapdIface.registerCallback(callback);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPbc() {
        synchronized (mLock) {
            final String methodStr = "startWpsPbc";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapdIface.startWpsPbc();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Start WPS pin keypad operation with the specified pin.
     *
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPinKeypad(String pin) {
        if (TextUtils.isEmpty(pin)) return false;
        synchronized (mLock) {
            final String methodStr = "startWpsPinKeypad";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapdIface.startWpsPinKeypad(pin);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Start WPS pin check.
     *
     * @param pin Pin to be checked.
     * @return valid pin on success, null otherwise.
     */
    public String startWpsCheckPin(String pin) {
        if (TextUtils.isEmpty(pin)) return null;
        synchronized (mLock) {
            final String methodStr = "startWpsCheckPin";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return null;
            final Mutable<String> gotPin = new Mutable<>();
            try {
                mIHostapdIface.startWpsCheckPin(pin,
                        (HostapdStatus status, String validPin) -> {
                            if (checkStatusAndLogFailure(status, methodStr)) {
                                gotPin.value = validPin;
                            }
                        });
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
            return gotPin.value;
        }
    }

    /**
     * Block client.
     *
     * @param deviceAddress MAC address of client to be blocked.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean blockClient(String deviceAddress) {
        if (TextUtils.isEmpty(deviceAddress)) return false;
        synchronized (mLock) {
            final String methodStr = "blockClient";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapdIface.blockClient(deviceAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Unblock client.
     *
     * @param deviceAddress MAC address of client to be unblocked.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean unblockClient(String deviceAddress) {
        if (TextUtils.isEmpty(deviceAddress)) return false;
        synchronized (mLock) {
            final String methodStr = "unblockClient";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapdIface.unblockClient(deviceAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Update allowed list.
     *
     * @param filePath File path of allowed list which wanna update.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean updateAllowedList(String filePath) {
        if (TextUtils.isEmpty(filePath)) return false;
        synchronized (mLock) {
            final String methodStr = "updateAllowedList";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapdIface.updateAllowedList(filePath);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set all devices allowed.
     *
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setAllDevicesAllowed(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setAllDevicesAllowed";
            if (!checkHostapdIfaceAndLogFailure(methodStr)) return false;
            try {
                HostapdStatus status = mIHostapdIface.setAllDevicesAllowed(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Returns false if HostapdIface is null, and logs failure to call methodStr
     */
    private boolean checkHostapdIfaceAndLogFailure(final String methodStr) {
        synchronized (mLock) {
            if (mIHostapdIface == null) {
                Log.e(TAG, "Can't call " + methodStr + ", IHostapdIface is null");
                return false;
            }
            return true;
        }
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    private boolean checkStatusAndLogFailure(HostapdStatus status,
            final String methodStr) {
        synchronized (mLock) {
            if (status.code != HostapdStatusCode.SUCCESS) {
                Log.e(TAG, "IHostapdIface." + methodStr + " failed: "
                        + hostapdStatusCodeToString(status.code) + ", " + status.debugMessage);
                return false;
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "IHostapdIface." + methodStr + " succeeded");
                }
                return true;
            }
        }
    }

    /**
     * Helper function to log callbacks.
     */
    private void logCallback(final String methodStr) {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "IHostapdIfaceCallback." + methodStr + " received");
            }
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            hostapdServiceDiedHandler();
            Log.e(TAG, "IHostapdIfaceIface." + methodStr + " failed with exception", e);
        }
    }

    /**
     * Converts HostapdStatus code values to strings for debug logging
     * TODO(b/34811152) Remove this, or make it more break resistance
     */
    public static String hostapdStatusCodeToString(int code) {
        switch (code) {
            case 0:
                return "SUCCESS";
            case 1:
                return "FAILURE_UNKNOWN";
            case 2:
                return "FAILURE_ARGS_INVALID";
            case 3:
                return "FAILURE_IFACE_INVALID";
            case 4:
                return "FAILURE_IFACE_UNKNOWN";
            case 5:
                return "FAILURE_IFACE_EXISTS";
            case 6:
                return "FAILURE_IFACE_DISABLED";
            case 7:
                return "FAILURE_IFACE_NOT_DISCONNECTED";
            case 8:
                return "NOT_SUPPORTED";
            default:
                return "??? UNKNOWN_CODE";
        }
    }

    private static class Mutable<E> {
        public E value;

        Mutable() {
            value = null;
        }

        Mutable(E value) {
            this.value = value;
        }
    }

    private class HostapdIfaceHalCallback extends IHostapdIfaceCallback.Stub {
        @Override
        public void onWpsEventPbcOverlap() {
            synchronized (mLock) {
                logCallback("onWpsEventPbcOverlap");
                mWifiApMonitor.broadcastWpsOverlapEvent(mIfaceName);
            }
        }

        /**
         * Used to indicate when a STA device is connected to this device.
         *
         * @param srcAddress MAC address of the device that was authorized.
         */
        public void onStaAuthorized(byte[] staAddress) {
            logd("STA authorized on " + mIfaceName);
            String macString;
            try {
                macString = NativeUtil.macAddressFromByteArray(staAddress);
            } catch (Exception e) {
                Log.e(TAG, "Could not decode MAC address.", e);
                return;
            }
            mWifiApMonitor.broadcastApStaConnected(mIfaceName, macString);
        }

        /**
         * Used to indicate when a STA device is disconnected from this device.
         *
         * @param srcAddress MAC address of the device that was deauthorized.
         */
        public void onStaDeauthorized(byte[] staAddress) {
            logd("STA deauthorized on " + mIfaceName);
            String macString;
            try {
                macString = NativeUtil.macAddressFromByteArray(staAddress);
            } catch (Exception e) {
                Log.e(TAG, "Could not decode MAC address.", e);
                return;
            }
            mWifiApMonitor.broadcastApStaDisconnected(mIfaceName, macString);
        }
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private static void logi(String s) {
        Log.i(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }
}
