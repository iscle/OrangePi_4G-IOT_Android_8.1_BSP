/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2015. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.server.wifi;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanSettings;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.Protocol;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.*;
import com.mediatek.common.util.OperatorCustomizationFactoryLoader;
import com.mediatek.common.util.OperatorCustomizationFactoryLoader.OperatorFactoryInfo;
import com.mediatek.common.wifi.IWifiFwkExt;
import com.mediatek.provider.MtkSettingsExt;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_FAILED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLING;
import static android.net.wifi.WifiManager.WIFI_STATE_UNKNOWN;
import static com.android.internal.util.StateMachine.HANDLED;
import static com.android.internal.util.StateMachine.NOT_HANDLED;

public final class MtkOpFwkExtManager {
    private static final String TAG = "MtkOpExtManager";
    public static final String SYSUI_PACKAGE_NAME = "com.android.systemui";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static Context sContext = null;
    private static IWifiFwkExt sExt = null;
    private static AutoConnectManager sACM = null;
    private static final List<OperatorFactoryInfo> sFactoryInfoList
        = new ArrayList<OperatorFactoryInfo>();

    static {
        sFactoryInfoList.add(
            new OperatorFactoryInfo(
            "Op01WifiService.apk",
            "com.mediatek.op.wifi.Op01WifiOperatorFactory",
            "com.mediatek.server.wifi.op01",
            "OP01"
        ));
        sFactoryInfoList.add(
            new OperatorFactoryInfo(
            "Op09WifiService.apk",
            "com.mediatek.op.wifi.Op09WifiOperatorFactory",
            "com.mediatek.server.wifi.op09",
            "OP09"
        ));
    }

    public static synchronized IWifiFwkExt getOpExt() {
        if (sExt == null) {
            WifiOperatorFactoryBase factory =
                (WifiOperatorFactoryBase) OperatorCustomizationFactoryLoader
                                              .loadFactory(sContext, sFactoryInfoList);
            if (factory == null) {
                factory = new WifiOperatorFactoryBase();
            }
            MtkOpFwkExtManager.log("Factory is : " + factory.getClass());
            sExt = factory.createWifiFwkExt(sContext);
            sExt.init();
        }
        return sExt;
    }

    public static class WifiOperatorFactoryBase {
        public IWifiFwkExt createWifiFwkExt(Context context) {
            return new DefaultWifiOpFwkExt(context);
        }
    }

    private static BroadcastReceiver sOperatorReceiver = new MtkWifiOpReceiver();
    public static class MtkWifiOpReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            MtkOpFwkExtManager.log("sOperatorReceiver.onReceive: " + action);
            if (action.equals(IWifiFwkExt.AUTOCONNECT_SETTINGS_CHANGE)) {
                MtkOpFwkExtManager.getACM()
                                  .updateAutoConnectSettings(sAdapter.getLastNetworkId());
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                final int previousState = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, -1);
                final int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);
                MtkOpFwkExtManager.log("previous state: " + previousState);
                MtkOpFwkExtManager.log("previous state: " + state);

                AutoConnectManager acm = MtkOpFwkExtManager.getACM();
                acm.setWaitForScanResult(false);
                acm.setShowReselectDialog(false);
                if (state == WIFI_STATE_ENABLING) {
                    if (MtkOpFwkExtManager.getOpExt().hasCustomizedAutoConnect()) {
                        MtkOpFwkExtManager.getACM().resetDisconnectNetworkStates();
                    }
                }

                MtkOpFwkExtManager.updateWifiState(previousState, state);
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = (NetworkInfo) intent.getExtra(WifiManager.EXTRA_NETWORK_INFO);
                DetailedState detailedState = info.getDetailedState();
                MtkOpFwkExtManager.log("detailed state: " + detailedState);

                AutoConnectManager acm = MtkOpFwkExtManager.getACM();
                if (detailedState == DetailedState.CONNECTED) {
                    acm.setWaitForScanResult(false);
                } else if (detailedState == DetailedState.DISCONNECTED) {
                    acm.handleNetworkDisconnect();
                    acm.setShowReselectDialog(false);
                    if (acm.getNetworkState() == DetailedState.CONNECTED) {
                        acm.setWaitForScanResult(false);
                        acm.handleWifiDisconnect();
                    }
                }
                // update states
                acm.setNetworkState(detailedState);
                acm.setLastNetworkId(sAdapter.getLastNetworkId());
            } else if (action.equals(IWifiFwkExt.ACTION_SUSPEND_NOTIFICATION)) {
                int type = intent.getIntExtra(IWifiFwkExt.EXTRA_SUSPEND_TYPE, -1);
                MtkOpFwkExtManager.getOpExt().suspendNotification(type);
            }
        }
    };

    private static class WifiStateTracker {
        int previousState;
        int currentState;
    }
    private static WifiStateTracker sWifiStateTracker = new WifiStateTracker();
    private static void updateWifiState(int current, int previous) {
        sWifiStateTracker.previousState = previous;
        sWifiStateTracker.currentState = current;
    }

    public static synchronized AutoConnectManager getACM() {
        if (sACM == null) {
            sACM = new AutoConnectManager(sContext, MtkOpFwkExtManager.getOpExt());
        }
        return sACM;
    }

    public static class AutoConnectManager {
        private static final int MIN_RSSI = -200;
        private static final int MAX_RSSI = 256;

        private List<Integer> mDisconnectNetworks = new ArrayList<Integer>();
        private int mSystemUiUid = -1;
        // whether we should show reselect dialog, this flag stores this info across related calling
        // stacks
        private boolean mShowReselectDialog = false;
        // this flag indicates this scan is triggered by bad RSSI/passive disconnection, it's for
        // network reselection
        private boolean mScanForWeakSignal = false;
        // record contains the last disconnected network (passive disconnection/disconnection due to
        // bad RSSI)
        private int mDisconnectNetworkId = INVALID_NETWORK_ID;
        // indicate whether supplicant is connecting, it's updated in handleSupplicantStateChange()
        private boolean mIsConnecting = false;
        // indicates whether the last disconnection is an active disconnection, will be reset to false
        // when:
        // * wifi is re-enabled
        // * a network is connected
        // * it is consumed in handleNetworkDisconnect()'s customized auto connect codes
        private boolean mDisconnectOperation = false;
        private boolean mWaitForScanResult = false;
        // log last weak signal checking time to prevent excessive scan trigger by weak signal handle
        // codes
        private long mLastCheckWeakSignalTime = 0;
        private Context mContext;
        private IWifiFwkExt mExt;
        private DetailedState mNetworkState = DetailedState.IDLE;
        private int mLastNetworkId = INVALID_NETWORK_ID;

        public AutoConnectManager(Context context, IWifiFwkExt ext) {
            mContext = context;
            mExt = ext;
            MtkOpFwkExtManager.log("AutoConnectManager: mExt is " + mExt.getClass());
            try {
                mSystemUiUid = context.getPackageManager().getPackageUidAsUser(
                    SYSUI_PACKAGE_NAME,
                    PackageManager.MATCH_SYSTEM_ONLY,
                    UserHandle.USER_SYSTEM);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Unable to resolve SystemUI's UID.");
            }
        }

        public void setLastNetworkId(int id) {
            mLastNetworkId = id;
        }

        public void setNetworkState(DetailedState state) {
            mNetworkState = state;
        }

        public DetailedState getNetworkState() {
            return mNetworkState;
        }

        public void addDisconnectNetwork(int netId) {
            log("addDisconnectNetwork: " + netId);
            synchronized (mDisconnectNetworks) {
                mDisconnectNetworks.add(netId);
            }
        }

        public void removeDisconnectNetwork(int netId) {
            log("removeDisconnectNetwork: " + netId);
            synchronized (mDisconnectNetworks) {
                mDisconnectNetworks.remove((Integer) netId);
            }
        }

        public void clearDisconnectNetworks() {
            log("clearDisconnectNetworks");
            synchronized (mDisconnectNetworks) {
                mDisconnectNetworks.clear();
            }
        }

        public List<Integer> getDisconnectNetworks() {
            List<Integer> networks = new ArrayList<Integer>();
            synchronized (mDisconnectNetworks) {
                for (Integer netId : mDisconnectNetworks) {
                    networks.add(netId);
                }
            }
            return networks;
        }

        public boolean disableNetwork(int networkId) {
            WifiConfigManager configManager = WifiInjector.getInstance().getWifiConfigManager();
            return configManager.disableNetwork(networkId, mSystemUiUid);
        }

        public boolean enableNetwork(int networkId) {
            WifiConfigManager configManager = WifiInjector.getInstance().getWifiConfigManager();
            return configManager.enableNetwork(networkId, false, mSystemUiUid);
        }

        public boolean getShowReselectDialog() {
            return mShowReselectDialog;
        }

        public void setShowReselectDialog(boolean value) {
            mShowReselectDialog = value;
        }

        public boolean getScanForWeakSignal() {
            return mScanForWeakSignal;
        }

        public void setScanForWeakSignal(boolean value) {
            mScanForWeakSignal = value;
        }

        public void setWaitForScanResult(boolean value) {
            mWaitForScanResult = value;
        }

        public boolean getWaitForScanResult() {
            return mWaitForScanResult;
        }

        public void showReselectionDialog() {
            setScanForWeakSignal(false);
            Log.d(TAG, "showReselectionDialog mDisconnectNetworkId:" + mDisconnectNetworkId);
            int networkId = getHighPriorityNetworkId();
            if (networkId == INVALID_NETWORK_ID) {
                return;
            }
            if (mExt.shouldAutoConnect()) {
                if (!mIsConnecting &&
                    !"WpsRunningState".equals(sAdapter.getCurrentState().getName())) {
                    sAdapter.sendMessage(
                        sAdapter.obtainMessage(
                            WifiStateMachineAdapter.CMD_ENABLE_NETWORK,
                            networkId,
                            1));
                } else {
                    Log.d(TAG, "WiFi is connecting!");
                }
            } else {
                setShowReselectDialog(mExt.handleNetworkReselection());
            }
        }

        private int getHighPriorityNetworkId() {
            int networkId = INVALID_NETWORK_ID;
            int priority = -1;
            int rssi = MIN_RSSI;
            String ssid = null;
            WifiConfigManager wcm = WifiInjector.getInstance().getWifiConfigManager();
            List<WifiConfiguration> networks = wcm.getSavedNetworks();
            if (networks == null || networks.size() == 0) {
                Log.d(TAG, "No configured networks, ignore!");
                return networkId;
            }
            HashMap<Integer, Integer> foundNetworks = new HashMap<Integer, Integer>();
            List<ScanResult> scanResults = sAdapter.syncGetScanResultsList();
            if (scanResults != null) {
                for (WifiConfiguration network : networks) {
                    if (network.networkId != mDisconnectNetworkId) {
                        for (ScanResult scanResult : scanResults) {
                            if ((network.SSID != null) &&
                                (scanResult.SSID != null) &&
                                network.SSID.equals("\"" + scanResult.SSID + "\"") &&
                                mExt.getSecurity(network) == mExt.getSecurity(scanResult) &&
                                scanResult.level > IWifiFwkExt.BEST_SIGNAL_THRESHOLD) {
                                foundNetworks.put(network.priority, scanResult.level);
                            }
                        }
                    }
                }
            }
            if (foundNetworks.size() < IWifiFwkExt.MIN_NETWORKS_NUM) {
                Log.d(TAG, "Configured networks number less than two, ignore!");
                return networkId;
            }
            Object[] keys = foundNetworks.keySet().toArray();
            Arrays.sort(keys, new Comparator<Object>() {
                public int compare(Object obj1, Object obj2) {
                    return (Integer) obj2 - (Integer) obj1;
                }
            });
            /*for (Object key : keys) {
                Log.d(TAG, "Priority:" + key + ", rssi:" + foundNetworks.get(key));
            }*/
            priority = (Integer) keys[0];
            for (WifiConfiguration network : networks) {
                if (network.priority == priority) {
                    networkId = network.networkId;
                    ssid = network.SSID;
                    rssi = foundNetworks.get(priority);
                    break;
                }
            }
            Log.d(TAG, "Found the highest priority AP, networkId:" + networkId
                + ", priority:" + priority + ", rssi:" + rssi + ", ssid:" + ssid);
            return networkId;
        }

        public void clearDisconnectNetworkId() {
            mDisconnectNetworkId = INVALID_NETWORK_ID;
        }

        public int getDisconnectNetworkId() {
            return mDisconnectNetworkId;
        }

        public void setDisconnectNetworkId(int id) {
            mDisconnectNetworkId = id;
        }

        public void handleScanResults(List<ScanDetail> availableNetworks) {
            MtkOpFwkExtManager.log("ACM: handleScanResults enter");
            if (mExt.hasCustomizedAutoConnect()) {
                if (availableNetworks.isEmpty()) {
                    if (getWaitForScanResult()) {
                        showSwitchDialog();
                    }
                } else {
                    if (isWifiConnecting()) {
                        return;
                    } else {
                        if (getWaitForScanResult()) {
                            showSwitchDialog();
                        }
                    }
                }
            }
            MtkOpFwkExtManager.log("ACM: handleScanResults exit");
        }

        private boolean isDataAvailable() {
            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
                TelephonyManager tm =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (phone == null || !phone.isRadioOn(mContext.getPackageName()) || tm == null) {
                    return false;
                }

                boolean isSim1Insert = tm.hasIccCard(PhoneConstants.SIM_ID_1);
                boolean isSim2Insert = false;
                if (tm.getDefault().getPhoneCount() >= 2) {
                    isSim2Insert = tm.hasIccCard(PhoneConstants.SIM_ID_2);
                }
                if (!isSim1Insert && !isSim2Insert) {
                return false;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        private void showSwitchDialog() {
            setWaitForScanResult(false);
            if (!getShowReselectDialog() && isDataAvailable()) {
                sendWifiFailoverGprsDialog();
            }
        }

        private boolean isWifiConnecting() {
            WifiInfo wifiInfo = sAdapter.getWifiInfo();
            int networkId = mIsConnecting ? wifiInfo.getNetworkId() : INVALID_NETWORK_ID;
            return (mExt.isWifiConnecting(networkId, getDisconnectNetworks()) ||
                    "WpsRunningState".equals(sAdapter.getCurrentState().getName()));
        }

        public void setIsConnecting(boolean value) {
            mIsConnecting = value;
        }

        public boolean getIsConnecting() {
            return mIsConnecting;
        }

        public void resetStates() {
            mDisconnectOperation = false;
            mScanForWeakSignal = false;
            mShowReselectDialog = false;
            mWaitForScanResult = false;
            mLastCheckWeakSignalTime = 0;
            mIsConnecting = false;
            resetDisconnectNetworkStates();
        }

        private void resetDisconnectNetworkStates() {
            Log.d(TAG, "resetDisconnectNetworkStates");
            if (!mExt.shouldAutoConnect()) {
                // disable all networks to prevent auto connection in manual/ask mode
                disableAllNetworks(false);
            } else {
                enableNetworks(getDisconnectNetworks());
            }
            clearDisconnectNetworks();
        }

        private void enableNetworks(List<Integer> networks) {
            if (networks != null) {
                for (int netId : networks) {
                    log("enableNetwork: " + netId);
                    boolean succeeded = enableNetwork(netId);
                    if (!succeeded) {
                        Log.e(TAG, "enableNetworks: failed to enable network " + netId);
                    }
                }
            }
        }

        private void disableAllNetworks(boolean exceptLastNetwork) {
            Log.d(TAG, "disableAllNetworks, exceptLastNetwork:" + exceptLastNetwork);
            WifiConfigManager wcm = WifiInjector.getInstance().getWifiConfigManager();
            List<WifiConfiguration> networks = wcm.getSavedNetworks();
            if (exceptLastNetwork) {
                if (null != networks) {
                    for (WifiConfiguration network : networks) {
                        int lastNetworkId = sAdapter.getLastNetworkId();
                        if (network.networkId != lastNetworkId &&
                            network.status != WifiConfiguration.Status.DISABLED) {
                            disableNetwork(network.networkId);
                        }
                    }
                }
            } else {
                if (null != networks) {
                    for (WifiConfiguration network : networks) {
                        if (network.status != WifiConfiguration.Status.DISABLED) {
                            disableNetwork(network.networkId);
                        }
                    }
                }
            }
        }
        public void updateRSSI(Integer newRssi, int ipAddr, int lastNetworkId) {
            if (mExt.hasCustomizedAutoConnect()) {
                if (newRssi != null && newRssi < IWifiFwkExt.WEAK_SIGNAL_THRESHOLD) {
                    long time = android.os.SystemClock.elapsedRealtime();
                    boolean autoConnect = mExt.shouldAutoConnect();
                    Log.d(TAG, "fetchRssi, ip:" + ipAddr
                            + ", mDisconnectOperation:" + mDisconnectOperation
                            + ", time:" + time + ", lasttime:" + mLastCheckWeakSignalTime);
                    final long lastCheckInterval = time - mLastCheckWeakSignalTime;
                    if ((ipAddr != 0 &&
                            !mDisconnectOperation &&
                            lastCheckInterval > IWifiFwkExt.MIN_INTERVAL_CHECK_WEAK_SIGNAL_MS) ||
                        (autoConnect &&
                            lastCheckInterval > IWifiFwkExt.MIN_INTERVAL_SCAN_SUPRESSION_MS)) {
                        Log.d(TAG, "Rssi < -85, scan to check signal!");
                        mLastCheckWeakSignalTime = time;
                        mDisconnectNetworkId = lastNetworkId;
                        mScanForWeakSignal = true;
                        sAdapter.startScan(-1 /*UNKNOWN_SCAN_SOURCE */, 0, null, null);
                    }
                }
            }
        }

        public void handleNetworkDisconnect() {
            if (mExt.hasCustomizedAutoConnect()) {
                MtkOpFwkExtManager.log("handleNetworkDisconnect, oldState:" +
                    mNetworkState + ", mDisconnectOperation:" + mDisconnectOperation);
                if (mNetworkState == DetailedState.CONNECTED) {
                    // record the disconnected network
                    mDisconnectNetworkId = mLastNetworkId;
                    if (!mDisconnectOperation) {
                        // this is not an active disconnection, treat this as a weak signal case too
                        mScanForWeakSignal = true;
                        sAdapter.startScan(-1 /*UNKNOWN_SCAN_SOURCE */, 0, null, null);
                    }
                }

                // in manual/ask mode, all network should be disabled except the current connected
                // one.
                // disable the disconnected network to prevent auto reconnecting to it
                if (!mExt.shouldAutoConnect()) {
                    disableNetwork(mLastNetworkId);
                }

                // consume this flag
                mDisconnectOperation = false;
                // reset this timestamp
                mLastCheckWeakSignalTime = 0;
            }
        }

        private boolean isPsDataAvailable() {
            // Check SIM ready
            TelephonyManager telMgr = (TelephonyManager) mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);
            if (telMgr == null) {
                MtkOpFwkExtManager.log("TelephonyManager is null");
                return false;
            }

            boolean isSIMReady = false;
            int i = 0;
            int n = telMgr.getSimCount();
            for (i = 0; i < n; i++) {
                if (telMgr.getSimState(i) == TelephonyManager.SIM_STATE_READY) {
                    isSIMReady = true;
                    break;
                }
            }

            MtkOpFwkExtManager.log("isSIMReady: " + isSIMReady);
            if (!isSIMReady) {
                return false;
            }

            // check radio on
            ITelephony iTel = ITelephony.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE));
            if (iTel == null) {
                MtkOpFwkExtManager.log("ITelephony is null");
                return false;
            }

            SubscriptionManager subMgr = SubscriptionManager.from(mContext);
            if (subMgr == null) {
                MtkOpFwkExtManager.log("SubscriptionManager is null");
                return false;
            }

            int[] subIdList = subMgr.getActiveSubscriptionIdList();
            n = 0;
            if (subIdList != null) {
                n = subIdList.length;
            }

            boolean isRadioOn = false;
            for (i = 0; i < n; i++) {
                try {
                    isRadioOn = iTel.isRadioOnForSubscriber(
                            subIdList[i],
                            mContext.getPackageName());
                    if (isRadioOn) {
                        break;
                    }
                } catch (RemoteException e) {
                    MtkOpFwkExtManager.log("isRadioOnForSubscriber RemoteException");
                    isRadioOn = false;
                }
            }
            if (!isRadioOn) {
                MtkOpFwkExtManager.log("All sub Radio OFF");
                return false;
            }

            // Check flight mode
            int airplanMode = Settings.System.getInt(
                    mContext.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0);
            MtkOpFwkExtManager.log("airplanMode:" + airplanMode);
            if (airplanMode == 1) {
                return false;
            }

            return true;
        }

        public void handleWifiDisconnect() {
            if (mExt.hasCustomizedAutoConnect()) {
                MtkOpFwkExtManager.log("handleWifiDisconnect");

                if (SystemProperties.get("ro.op01_compatible").equals("1")) {
                    MtkOpFwkExtManager.log("skip DataDialog, no datadialog");
                    return;
                }

                int isAsking = Settings.System.getInt(mContext.getContentResolver(),
                        MtkSettingsExt.System.WIFI_CONNECT_REMINDER,
                        IWifiFwkExt.WIFI_CONNECT_REMINDER_ALWAYS);
                if (isAsking != IWifiFwkExt.WIFI_CONNECT_REMINDER_ALWAYS) {
                    MtkOpFwkExtManager.log("Not ask mode");
                    return;
                }

                boolean dataAvailable = isPsDataAvailable();
                MtkOpFwkExtManager.log("dataAvailable: " + dataAvailable);
                if (!dataAvailable) {
                    return;
                }

                turnOffDataConnection();
                if (!hasConnectableAp()) {
                    sendWifiFailoverGprsDialog();
                }
            }
        }

        private void sendWifiFailoverGprsDialog() {
            Intent intent = new Intent(IWifiFwkExt.ACTION_WIFI_FAILOVER_GPRS_DIALOG);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.setClassName("com.mediatek.server.wifi.op01", "com.mediatek.op.wifi.DataConnectionReceiver");
            sContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            MtkOpFwkExtManager.log("ACTION_WIFI_FAILOVER_GPRS_DIALOG sent");
        }

        public void updateAutoConnectSettings(int lastConnectId) {
            boolean isConnecting = (mIsConnecting ||
                                    "WpsRunningState".equals(sAdapter.getCurrentState().getName()));
            Log.d(TAG, "updateAutoConnectSettings, isConnecting:" + isConnecting);
            List<WifiConfiguration> networks = WifiInjector.getInstance()
                                                           .getWifiConfigManager()
                                                           .getSavedNetworks();
            if (null != networks) {
                if (mExt.shouldAutoConnect()) {
                    if (!isConnecting) {
                        Collections.sort(networks, new Comparator<WifiConfiguration>() {
                            public int compare(WifiConfiguration obj1, WifiConfiguration obj2) {
                                return obj2.priority - obj1.priority;
                            }
                        });
                        for (WifiConfiguration network : networks) {
                            if (network.networkId != lastConnectId) {
                                enableNetwork(network.networkId);
                            }
                        }
                    }
                } else {
                    if (!isConnecting) {
                        for (WifiConfiguration network : networks) {
                            if (network.networkId != lastConnectId
                                && network.status != WifiConfiguration.Status.DISABLED) {
                                disableNetwork(network.networkId);
                            }
                        }
                    }
                }
            }
        }

        public boolean preProcessMessage(State state, Message msg) {
            log("preProcessMessage(" + state.getName() + ", " + msg.what + ")");
            int netId;
            switch (state.getName()) {
            case "ConnectModeState":
                switch(msg.what) {
                case WifiStateMachineAdapter.CMD_REMOVE_NETWORK:
                    if (mExt.hasCustomizedAutoConnect()) {
                        netId = msg.arg1;
                        removeDisconnectNetwork(netId);
                        if (netId == sAdapter.getWifiInfo().getNetworkId()) {
                            mDisconnectOperation = true;
                            mScanForWeakSignal = false;
                        }
                    }
                    break;
                case WifiStateMachineAdapter.CMD_ENABLE_NETWORK:
                    if (mExt.hasCustomizedAutoConnect()) {
                        netId = msg.arg1;
                        boolean disableOthers = msg.arg2 == 1;
                        if (!disableOthers && !mExt.shouldAutoConnect()) {
                            Log.d(TAG,
                                "Shouldn't auto connect, ignore the enable network operation!");
                            sAdapter.replyToMessage(
                                msg,
                                msg.what,
                                WifiStateMachineAdapter.SUCCESS);
                            return HANDLED;
                        }
                    }
                    break;
                default:
                    return NOT_HANDLED;
                }
                return NOT_HANDLED;
            default:
                log("State " + state.getName() + " NOT_HANDLED");
                return NOT_HANDLED;
            }
        }

        public boolean postProcessMessage(State state, Message msg, Object... args) {
            log("postProcessMessage(" + state.getName() + ", " + msg.what + ", " + args + ")");
            int netId;
            switch (state.getName()) {
            case "ConnectModeState":
                switch(msg.what) {
                case WifiStateMachineAdapter.CMD_ENABLE_NETWORK:
                    if (mExt.hasCustomizedAutoConnect()) {
                        netId = msg.arg1;
                        boolean disableOthers = msg.arg2 == 1;
                        boolean ok = (Boolean) args[0];
                        if (disableOthers && ok) {
                            removeDisconnectNetwork(netId);
                            mDisconnectOperation = true;
                            mScanForWeakSignal = false;
                        }
                    }
                    break;
                case WifiManager.DISABLE_NETWORK:
                    if (mExt.hasCustomizedAutoConnect()) {
                        // check whether it's succefully disabled
                        netId = msg.arg1;
                        WifiConfiguration config = WifiInjector.getInstance()
                                                               .getWifiConfigManager()
                                                               .getConfiguredNetwork(netId);
                        // we need to ensure this network was successfully disabled
                        if (config != null &&
                            config.getNetworkSelectionStatus() != null &&
                            !config.getNetworkSelectionStatus().isNetworkEnabled()) {
                            addDisconnectNetwork(netId);
                            if (netId == sAdapter.getWifiInfo().getNetworkId()) {
                                mDisconnectOperation = true;
                                mScanForWeakSignal = false;
                            }
                        }
                    }
                    break;
                case WifiManager.CONNECT_NETWORK:
                    if (mExt.hasCustomizedAutoConnect()) {
                        netId = msg.arg1;
                        NetworkUpdateResult result = (NetworkUpdateResult) args[1];
                        boolean hasCredentialChanged = false;
                        if (result != null) {
                            netId = result.getNetworkId();
                            hasCredentialChanged = result.hasCredentialChanged();
                        }
                        if (sAdapter.getWifiInfo().getNetworkId() != netId ||
                            hasCredentialChanged) {
                            // set this flag because we will disconnect current network due to
                            // either user specified a different network or the credential change
                            // forced us to do a reconnect
                            mDisconnectOperation = true;
                        }
                        mScanForWeakSignal = false;
                        removeDisconnectNetwork(netId);
                    }
                    break;
                case WifiManager.FORGET_NETWORK:
                    // check whether the network has been deleted
                    if (mExt.hasCustomizedAutoConnect()) {
                        netId = msg.arg1;
                        WifiConfiguration config = WifiInjector.getInstance()
                                                               .getWifiConfigManager()
                                                               .getConfiguredNetwork(netId);
                        if (config == null) {
                            removeDisconnectNetwork(netId);
                            if (netId == sAdapter.getWifiInfo().getNetworkId()) {
                                mDisconnectOperation = true;
                                mScanForWeakSignal = false;
                            }
                        }
                    }
                    break;
                default:
                    return NOT_HANDLED;
                }
                return NOT_HANDLED;
            default:
                log("State " + state.getName() + " NOT_HANDLED");
                return NOT_HANDLED;
            }
        }

        public void turnOffDataConnection() {
            TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            // Remember last status on(1) or off(-1)
            Settings.System.putLong(mContext.getContentResolver(),
                                MtkSettingsExt.System.LAST_SIMID_BEFORE_WIFI_DISCONNECTED,
                                tm.getDataEnabled() ? 1 : -1);
            if (tm != null) {
                tm.setDataEnabled(false);
            }
        }

        public boolean hasConnectableAp() {
            if (MtkOpFwkExtManager.hasConnectableAp() && mExt.hasConnectableAp()) {
                mWaitForScanResult = true;
                return true;
            }
            return false;
        }
    }

    // enable access to various internal WifiStateMachine members
    private static WifiStateMachineAdapter sAdapter = null;
    public static class WifiStateMachineAdapter {
        // NOTE: keep these values sync with WifiStateMachine
        static final int BASE = Protocol.BASE_WIFI;
        static final int CMD_REMOVE_NETWORK                                 = BASE + 53;
        static final int CMD_ENABLE_NETWORK                                 = BASE + 54;

        static final int SUCCESS = 1;
        static final int FAILURE = -1;

        private final WifiStateMachine mWsm;
        private final Class<?> mWsmCls;

        public WifiStateMachineAdapter(WifiStateMachine wsm) {
            mWsm = wsm;
            mWsmCls = wsm.getClass();
        }

        public void replyToMessage(Message msg, int what) {
            try {
                Method method = mWsmCls.getDeclaredMethod(
                    "replyToMessage",
                    Message.class,
                    Integer.class);
                method.setAccessible(true);
                method.invoke(mWsm, msg, what);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void replyToMessage(Message msg, int what, int arg1) {
            try {
                Method method = mWsmCls.getDeclaredMethod(
                    "replyToMessage",
                    Message.class,
                    Integer.class,
                    Integer.class);
                method.setAccessible(true);
                method.invoke(mWsm, msg, what, arg1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void replyToMessage(Message msg, int what, Object obj) {
            try {
                Method method = mWsmCls.getDeclaredMethod(
                    "replyToMessage",
                    Message.class,
                    Integer.class,
                    Object.class);
                method.setAccessible(true);
                method.invoke(mWsm, msg, what, obj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public WifiInfo getWifiInfo() {
            return mWsm.getWifiInfo();
        }

        public int getLastNetworkId() {
            int id = INVALID_NETWORK_ID;
            try {
                Field field = mWsmCls.getDeclaredField("mLastNetworkId");
                field.setAccessible(true);
                id = (Integer) field.get(mWsm);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                return id;
            }
        }

        public IState getCurrentState() {
            State state = null;
            try {
                Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
                method.setAccessible(true);
                state = (State) method.invoke(mWsm);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                return state;
            }
        }

        public void startScan(int callingUid,
                              int scanCounter,
                              ScanSettings settings,
                              WorkSource workSource) {
            mWsm.startScan(callingUid, scanCounter, settings, workSource);
        }

        public void sendMessage(int what) {
            mWsm.sendMessage(what);
        }

        public void sendMessage(Message message) {
            mWsm.sendMessage(message);
        }

        public Message obtainMessage(int what, int arg1, int arg2) {
            return mWsm.obtainMessage(what, arg1, arg2);
        }

        public List<ScanResult> syncGetScanResultsList() {
            return mWsm.syncGetScanResultsList();
        }
    }

    // NOTE: this method should be invoked in an looper thread
    public static void initialize(Context context) {
        Log.d(TAG, "initialize: " + context);
        sContext = context;
        // 0. make an adapter

        // 1. register OP network evaluator
        IWifiFwkExt ext = MtkOpFwkExtManager.getOpExt();
        WifiInjector injector = WifiInjector.getInstance();
        if (ext.hasNetworkSelection() == IWifiFwkExt.OP_01) {
            WifiConfigManager ctm = injector.getWifiConfigManager();
            try {
                Field field = WifiInjector.class
                                  .getDeclaredField("mWifiNetworkSelector");
                field.setAccessible(true);
                WifiNetworkSelector selector = (WifiNetworkSelector) field.get(injector);
                selector.registerNetworkEvaluator(
                    new MtkOpNetworkEvaluator(context, ctm),
                    0 /* before any AOSP evaluators */);
            } catch (Exception e) {
                // simply ignore all reflection exceptions
                e.printStackTrace();
            }
        }

        // 2. modify PERIODIC_SCAN_INTERVAL_MS
        final int interval = ext.defaultFrameworkScanIntervalMs();
        MtkOpFwkExtManager.log(
            "defaultFrameworkScanIntervalMs: " + interval);
        MtkOpFwkExtManager.log(
            "PERIODIC_SCAN_INTERVAL_MS: " + WifiConnectivityManager.PERIODIC_SCAN_INTERVAL_MS);
        try {
            Field nameField = WifiConnectivityManager.class
                                  .getDeclaredField("PERIODIC_SCAN_INTERVAL_MS");
            // remove final modifier
            Field modifiersField = Field.class.getDeclaredField("accessFlags");
            modifiersField.setAccessible(true);
            MtkOpFwkExtManager.log("accessFlags: " + modifiersField.getInt(nameField));
            modifiersField.setInt(nameField, (nameField.getModifiers() & ~Modifier.FINAL));
            MtkOpFwkExtManager.log("accessFlags: " + modifiersField.getInt(nameField));
            MtkOpFwkExtManager.log(
                "old: " + nameField.getInt(null));
            nameField.setInt(null, interval);
            MtkOpFwkExtManager.log(
                "new: " + nameField.getInt(null));
            // restore final modifier
            // modifiersField.setInt(nameField, (nameField.getModifiers() | Modifier.FINAL));
        } catch (Exception e) {
            // simply ignore all reflection exceptions
            e.printStackTrace();
        }
        MtkOpFwkExtManager.log(
            "PERIODIC_SCAN_INTERVAL_MS: " + WifiConnectivityManager.PERIODIC_SCAN_INTERVAL_MS);
        // check if we succeeded
        if (WifiConnectivityManager.PERIODIC_SCAN_INTERVAL_MS != interval) {
            Log.e(TAG, "Failed to modify PERIODIC_SCAN_INTERVAL_MS");
        }

        // 3. register receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(IWifiFwkExt.AUTOCONNECT_SETTINGS_CHANGE);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(IWifiFwkExt.ACTION_SUSPEND_NOTIFICATION);
        sContext.registerReceiver(sOperatorReceiver, intentFilter);

        // 4. register supplicant state handler
        Handler handler = new Handler() {
            @Override
            public final void handleMessage(Message msg) {
                Log.d(TAG, "Supplicant message: " + msg.what);
                switch (msg.what) {
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                    StateChangeResult stateChangeResult = (StateChangeResult) msg.obj;
                    SupplicantState state = null;
                    try {
                        Field field = StateChangeResult.class.getDeclaredField("state");
                        field.setAccessible(true);
                        state = (SupplicantState) field.get(stateChangeResult);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    MtkOpFwkExtManager.getACM()
                                        .setIsConnecting(SupplicantState.isConnecting(state));
                    break;
                case WifiMonitor.SUP_CONNECTION_EVENT:
                    MtkOpFwkExtManager.getACM().resetStates();
                    break;
                default:
                    Log.e(TAG, "Invalid message: " + msg.what);
                }
            }
        };
        String interfaceName = injector.getWifiNative().getInterfaceName();
        WifiMonitor monitor = injector.getWifiMonitor();
        monitor.registerHandler(
            interfaceName,
            WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT,
            handler);
        monitor.registerHandler(
            interfaceName,
            WifiMonitor.SUP_CONNECTION_EVENT,
            handler);

        // 5. make an adapter of WSM
        sAdapter = new WifiStateMachineAdapter(injector.getWifiStateMachine());
        Log.d(TAG, "initialize done");
    }

    // NOTE: keep these values in-sync with WifiSettingsStore
    private static final int WIFI_DISABLED = 0;
    private static final int WIFI_DISABLED_AIRPLANE_ON = 3;
    private static int getPersistedWifiState() {
        final ContentResolver cr = sContext.getContentResolver();
        try {
            return Settings.Global.getInt(cr, Settings.Global.WIFI_ON);
        } catch (Settings.SettingNotFoundException e) {
            Settings.Global.putInt(cr, Settings.Global.WIFI_ON, WIFI_DISABLED);
            return WIFI_DISABLED;
        }
    }

    public static boolean hasConnectableAp() {
        int persistedWifiState = getPersistedWifiState();
        return !(persistedWifiState == WIFI_DISABLED ||
                 persistedWifiState == WIFI_DISABLED_AIRPLANE_ON);
    }

    public static void log(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }
}
