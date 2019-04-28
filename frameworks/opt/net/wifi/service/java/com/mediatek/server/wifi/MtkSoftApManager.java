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

package com.mediatek.server.wifi;

import static com.android.server.wifi.util.ApConfigUtil.ERROR_GENERIC;
import static com.android.server.wifi.util.ApConfigUtil.ERROR_NO_CHANNEL;
import static com.android.server.wifi.util.ApConfigUtil.SUCCESS;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.InterfaceConfiguration;
import android.net.wifi.IApInterface;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.net.BaseNetworkObserver;
import com.android.server.wifi.SoftApManager;
import com.android.server.wifi.StateMachineDeathRecipient;
import com.android.server.wifi.WifiApConfigStore;
import com.android.server.wifi.WifiMetrics;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.util.ApConfigUtil;
import com.mediatek.provider.MtkSettingsExt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import mediatek.net.wifi.HotspotClient;
import mediatek.net.wifi.WifiHotspotManager;

/**
 * Manage WiFi in AP mode.
 * The internal state machine runs under "WifiStateMachine" thread context.
 */
public class MtkSoftApManager extends SoftApManager {
    private static final String TAG = "MtkSoftApManager";

    private final WifiNative mWifiNative;

    private final String mCountryCode;

    private final SoftApStateMachine mStateMachine;

    private final Listener mListener;

    private final IApInterface mApInterface;

    private final INetworkManagementService mNwService;
    private final WifiApConfigStore mWifiApConfigStore;

    private final WifiMetrics mWifiMetrics;

    private WifiConfiguration mApConfig;

    /// M: Hotspot manager implementation @{
    private static final String HOSTAPD_CONFIG_FILE =
            Environment.getDataDirectory() + "/misc/wifi/hostapd.conf";
    private static final String ALLOWED_LIST_FILE =
            Environment.getDataDirectory() + "/misc/wifi/allowed_list.conf";
    private static final String ACCEPT_MAC_UPDATE_FILE =
            Environment.getDataDirectory() + "/misc/wifi/accept_mac_update.conf";

    static final int BASE = Protocol.BASE_WIFI;
    public static final int M_CMD_BLOCK_CLIENT                 = BASE + 300;
    public static final int M_CMD_UNBLOCK_CLIENT               = BASE + 301;
    public static final int M_CMD_GET_CLIENTS_LIST             = BASE + 302;
    public static final int M_CMD_START_AP_WPS                 = BASE + 303;
    public static final int M_CMD_IS_ALL_DEVICES_ALLOWED       = BASE + 304;
    public static final int M_CMD_SET_ALL_DEVICES_ALLOWED      = BASE + 305;
    public static final int M_CMD_ALLOW_DEVICE                 = BASE + 306;
    public static final int M_CMD_DISALLOW_DEVICE              = BASE + 307;
    public static final int M_CMD_GET_ALLOWED_DEVICES          = BASE + 308;

    private final Context mContext;
    private final WifiApInjector mWifiApInjector;
    private final WifiApNative mWifiApNative;
    private final WifiApMonitor mWifiApMonitor;

    private HashMap<String, HotspotClient> mHotspotClients =
                                                    new HashMap<String, HotspotClient>();
    private static LinkedHashMap<String, HotspotClient> sAllowedDevices;
    /// @}

    /// M: Hotspot manager implementation
    public MtkSoftApManager(Context context,
                            Looper looper,
                            WifiNative wifiNative,
                            String countryCode,
                            Listener listener,
                            IApInterface apInterface,
                            INetworkManagementService nms,
                            WifiApConfigStore wifiApConfigStore,
                            WifiConfiguration config,
                            WifiMetrics wifiMetrics) {
        /// M: Hotspot manager implementation @{
        super(looper, wifiNative, countryCode, listener, apInterface, nms, wifiApConfigStore,
                config, wifiMetrics);
        mContext = context;
        if (WifiApInjector.getInstance() == null) {
            mWifiApInjector = new WifiApInjector();
        } else {
            mWifiApInjector = WifiApInjector.getInstance();
        }
        mWifiApNative = mWifiApInjector.getWifiApNative();
        mWifiApMonitor = mWifiApInjector.getWifiApMonitor();
        mApInterface = apInterface;
        /// @}

        mStateMachine = new SoftApStateMachine(looper);

        mWifiNative = wifiNative;
        mCountryCode = countryCode;
        mListener = listener;
        mNwService = nms;
        mWifiApConfigStore = wifiApConfigStore;
        if (config == null) {
            mApConfig = mWifiApConfigStore.getApConfiguration();
        } else {
            mApConfig = config;
        }
        mWifiMetrics = wifiMetrics;
    }

    /**
     * Start soft AP with the supplied config.
     */
    public void start() {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_START, mApConfig);
    }

    /**
     * Stop soft AP.
     */
    public void stop() {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_STOP);
    }

    /// M: Hotspot manager implementation @{
    public void startApWpsCommand(Message message) {
        mStateMachine.sendMessage(message);
    }

    public List<HotspotClient> getHotspotClientsList() {
        List<HotspotClient> clients = new ArrayList<HotspotClient>();
        synchronized (mHotspotClients) {
            for (HotspotClient client : mHotspotClients.values()) {
                clients.add(new HotspotClient(client));
            }
        }
        return clients;
    }

    public void syncBlockClient(Message message) {
        mStateMachine.sendMessage(message);
    }

    public void syncUnblockClient(Message message) {
        mStateMachine.sendMessage(message);
    }

    static private void initAllowedListIfNecessary() {
        if (sAllowedDevices == null) {
            sAllowedDevices = new LinkedHashMap<String, HotspotClient>();

            try {
                BufferedReader br = new BufferedReader(new FileReader(ALLOWED_LIST_FILE));
                String line = br.readLine();
                while (line != null) {
                    String[] result = line.split("\t");
                    if (result == null) {
                        continue;
                    }
                    String address = result[0];
                    boolean blocked = result[1].equals("1") ? true : false;
                    String name = result.length == 3 ? result[2] : "";
                    sAllowedDevices.put(address, new HotspotClient(address, blocked, name));
                    line = br.readLine();
                }
                br.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString(), new Throwable("initAllowedListIfNecessary"));
            }
        }
    }

    static private void writeAllowedList() {
        String content = "";
        for (HotspotClient device : sAllowedDevices.values()) {
            String blocked = device.isBlocked == true ? "1" : "0";
            if (device.name != null) {
                content += device.deviceAddress + "\t" + blocked + "\t" + device.name + "\n";
            } else {
                content += device.deviceAddress + "\t" + blocked + "\n";
            }
        }

        Log.d(TAG, "writeAllowedLis content = " + content);
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(ALLOWED_LIST_FILE));
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString(), new Throwable("writeAllowedList"));
        }
    }

    static public boolean isAllDevicesAllowed(Context context) {
        boolean result = (Settings.System.getInt(context.getContentResolver(),
            MtkSettingsExt.System.WIFI_HOTSPOT_IS_ALL_DEVICES_ALLOWED, 1) == 1);
        return result;
    }

    static public void writeAllDevicesAllowed(Context context, boolean enabled) {
        Settings.System.putInt(context.getContentResolver(),
            MtkSettingsExt.System.WIFI_HOTSPOT_IS_ALL_DEVICES_ALLOWED, enabled ? 1 : 0);
    }

    static public void addDeviceToAllowedList(HotspotClient device) {
        Log.d(TAG, "addDeviceToAllowedList device = " + device +
            ", is name null?" + (device.name == null));
        initAllowedListIfNecessary();
        if (!sAllowedDevices.containsKey(device.deviceAddress)) {
            sAllowedDevices.put(device.deviceAddress, device);
        }
        writeAllowedList();
    }

    static public void removeDeviceFromAllowedList(String address) {
        Log.d(TAG, "removeDeviceFromAllowedList address = " + address);
        initAllowedListIfNecessary();
        sAllowedDevices.remove(address);
        writeAllowedList();
    }

    private void initAcceptMacFile() {
        initAllowedListIfNecessary();

        String content = "";
        for (HotspotClient device : sAllowedDevices.values()) {
            String prefix = device.isBlocked ? "-" : "";
            content += prefix + device.deviceAddress + "\n";
        }

        try {
            // Change mode to allow hostapd to read the file
            Runtime.getRuntime().exec("chmod 604 " + ACCEPT_MAC_UPDATE_FILE);

            BufferedWriter bw = new BufferedWriter(new FileWriter(ACCEPT_MAC_UPDATE_FILE));
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString(), new Throwable("writeAllowedList"));
        }
    }

    private void updateAcceptMacFile(String content) {
        Log.d(TAG, "updateAllowedList content = " + content);
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(ACCEPT_MAC_UPDATE_FILE));
            bw.write(content);
            bw.close();
            mWifiApNative.updateAllowedListCommand(ACCEPT_MAC_UPDATE_FILE);
        } catch (IOException e) {
            Log.e(TAG, e.toString(), new Throwable("updateAcceptMacFile"));
        }
    }

    static public List<HotspotClient> getAllowedDevices() {
        Log.d(TAG, "getAllowedDevices");
        initAllowedListIfNecessary();
        List<HotspotClient> devices = new ArrayList<HotspotClient>();
        for (HotspotClient device : sAllowedDevices.values()) {
            devices.add(new HotspotClient(device));
            Log.d(TAG, "device = " + device);
        }
        return devices;
    }

    public void syncAllowDevice(String address) {
        updateAcceptMacFile(address);
    }

    public void syncDisallowDevice(String address) {
        updateAcceptMacFile("-" + address);
    }

    public void syncSetAllDevicesAllowed(boolean enabled, boolean allowAllConnectedDevices) {
        if (!enabled) {
            initAllowedListIfNecessary();
            if (allowAllConnectedDevices && mHotspotClients.size() > 0) {
                String content = "";
                for (HotspotClient client : mHotspotClients.values()) {
                    if (!client.isBlocked && !sAllowedDevices.containsKey(client.deviceAddress)) {
                        sAllowedDevices.put(client.deviceAddress, new HotspotClient(client));
                        content += client.deviceAddress + "\n";
                    }
                }

                if (!content.equals("")) {
                    writeAllowedList();
                    updateAcceptMacFile(content);
                }
            }
        }

        mWifiApNative.setAllDevicesAllowedCommand(enabled);
    }
    /// @}

    /**
     * Update AP state.
     * @param state new AP state
     * @param reason Failure reason if the new AP state is in failure state
     */
    private void updateApState(int state, int reason) {
        if (mListener != null) {
            mListener.onStateChanged(state, reason);
        }
    }

    /**
     * Start a soft AP instance with the given configuration.
     * @param config AP configuration
     * @return integer result code
     */
    private int startSoftAp(WifiConfiguration config) {
        if (config == null || config.SSID == null) {
            Log.e(TAG, "Unable to start soft AP without valid configuration");
            return ERROR_GENERIC;
        }

        // Make a copy of configuration for updating AP band and channel.
        WifiConfiguration localConfig = new WifiConfiguration(config);

        int result = ApConfigUtil.updateApChannelConfig(
                mWifiNative, mCountryCode,
                mWifiApConfigStore.getAllowed2GChannel(), localConfig);
        if (result != SUCCESS) {
            Log.e(TAG, "Failed to update AP band and channel");
            return result;
        }

        // Setup country code if it is provided.
        if (mCountryCode != null) {
            // Country code is mandatory for 5GHz band, return an error if failed to set
            // country code when AP is configured for 5GHz band.
            if (!mWifiNative.setCountryCodeHal(mCountryCode.toUpperCase(Locale.ROOT))
                    && config.apBand == WifiConfiguration.AP_BAND_5GHZ) {
                Log.e(TAG, "Failed to set country code, required for setting up "
                        + "soft ap in 5GHz");
                return ERROR_GENERIC;
            }
        }

        int encryptionType = getIApInterfaceEncryptionType(localConfig);

        if (localConfig.hiddenSSID) {
            Log.d(TAG, "SoftAP is a hidden network");
        }

        try {
            /// M: Fix channel for testing
            String fixChannelString = SystemProperties.get("wifi.tethering.channel");
            int fixChannel = -1;
            if (fixChannelString != null && fixChannelString.length() > 0) {
                fixChannel = Integer.parseInt(fixChannelString);
                if (fixChannel >= 0) {
                    localConfig.apChannel = fixChannel;
                }
            }
            // Note that localConfig.SSID is intended to be either a hex string or "double quoted".
            // However, it seems that whatever is handing us these configurations does not obey
            // this convention.
            boolean success = mApInterface.writeHostapdConfig(
                    localConfig.SSID.getBytes(StandardCharsets.UTF_8), localConfig.hiddenSSID,
                    localConfig.apChannel, encryptionType,
                    (localConfig.preSharedKey != null)
                            ? localConfig.preSharedKey.getBytes(StandardCharsets.UTF_8)
                            : new byte[0]);
            if (!success) {
                Log.e(TAG, "Failed to write hostapd configuration");
                return ERROR_GENERIC;
            }
            /// M: Hotspot manager implementation @{
            int maxNumSta = Settings.System.getInt(
                    mContext.getContentResolver(),
                    MtkSettingsExt.System.WIFI_HOTSPOT_MAX_CLIENT_NUM,
                    6);
            String isWhiteListEnabled = (Settings.System.getInt(mContext.getContentResolver(),
                MtkSettingsExt.System.WIFI_HOTSPOT_IS_ALL_DEVICES_ALLOWED, 1) == 1) ? "0" : "1";
            try {
                BufferedReader br = new BufferedReader(new FileReader(HOSTAPD_CONFIG_FILE));
                StringBuffer configContent = new StringBuffer();
                String line;
                while ((line = br.readLine()) != null) {
                    configContent.append(line + "\n");
                }
                br.close();

                configContent.append("max_num_sta=" + maxNumSta + "\n" +
                                     "eap_server=1\n" +
                                     "wps_state=2\n" +
                                     "config_methods=display physical_display push_button\n" +
                                     "device_name=AndroidAP\n" +
                                     "manufacturer=MediaTek Inc.\n" +
                                     "model_name=MTK Wireless Model\n" +
                                     "model_number=66xx\n" +
                                     "serial_number=1.0\n" +
                                     "device_type=10-0050F204-5\n" +
                                     "macaddr_acl=" + isWhiteListEnabled + "\n" +
                                     "accept_mac_file=" + ACCEPT_MAC_UPDATE_FILE + "\n");
                Log.e(TAG, "HOSTAPD_CONFIG_FILE: " + configContent.toString());

                BufferedWriter bw = new BufferedWriter(new FileWriter(HOSTAPD_CONFIG_FILE));
                bw.write(configContent.toString());
                bw.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString(), new Throwable("Fail to write HOSTAPD_CONFIG_FILE"));
            }

            initAcceptMacFile();
            /// @}

            success = mApInterface.startHostapd();
            if (!success) {
                Log.e(TAG, "Failed to start hostapd.");
                return ERROR_GENERIC;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in starting soft AP: " + e);
        }

        Log.d(TAG, "Soft AP is started");

        return SUCCESS;
    }

    private static int getIApInterfaceEncryptionType(WifiConfiguration localConfig) {
        int encryptionType;
        switch (localConfig.getAuthType()) {
            case KeyMgmt.NONE:
                encryptionType = IApInterface.ENCRYPTION_TYPE_NONE;
                break;
            case KeyMgmt.WPA_PSK:
                encryptionType = IApInterface.ENCRYPTION_TYPE_WPA;
                break;
            case KeyMgmt.WPA2_PSK:
                encryptionType = IApInterface.ENCRYPTION_TYPE_WPA2;
                break;
            default:
                // We really shouldn't default to None, but this was how NetworkManagementService
                // used to do this.
                encryptionType = IApInterface.ENCRYPTION_TYPE_NONE;
                break;
        }
        return encryptionType;
    }

    /**
     * Teardown soft AP.
     */
    private void stopSoftAp() {
        try {
            mApInterface.stopHostapd();
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in stopping soft AP: " + e);
            return;
        }
        Log.d(TAG, "Soft AP is stopped");
    }

    private class SoftApStateMachine extends StateMachine {
        // Commands for the state machine.
        public static final int CMD_START = 0;
        public static final int CMD_STOP = 1;
        public static final int CMD_AP_INTERFACE_BINDER_DEATH = 2;
        public static final int CMD_INTERFACE_STATUS_CHANGED = 3;
        /// M: Hotspot manager implementation @{
        public static final int CMD_POLL_IP_ADDRESS = 4;

        /* Should be the same with WifiStateMachine */
        private static final int Wifi_SUCCESS = 1;
        private static final int Wifi_FAILURE = -1;

        private static final int POLL_IP_ADDRESS_INTERVAL_MSECS = 2000;
        private static final int POLL_IP_TIMES = 15;
        /// @}

        private final State mIdleState = new IdleState();
        private final State mStartedState = new StartedState();

        private final StateMachineDeathRecipient mDeathRecipient =
                new StateMachineDeathRecipient(this, CMD_AP_INTERFACE_BINDER_DEATH);

        private NetworkObserver mNetworkObserver;

        private class NetworkObserver extends BaseNetworkObserver {
            private final String mIfaceName;

            NetworkObserver(String ifaceName) {
                mIfaceName = ifaceName;
            }

            @Override
            public void interfaceLinkStateChanged(String iface, boolean up) {
                if (mIfaceName.equals(iface)) {
                    SoftApStateMachine.this.sendMessage(
                            CMD_INTERFACE_STATUS_CHANGED, up ? 1 : 0, 0, this);
                }
            }
        }

        /// M: Hotspot manager implementation @{
        private final WifiManager mWifiManager;
        private String mMonitorInterfaceName;
        private boolean mIsMonitoring = false;
        private boolean mStartApWps = false;
        private int mClientNum = 0;
        /* M: Channel for sending replies. */
        private AsyncChannel mReplyChannel = new AsyncChannel();

        /* M: For hotspot auto stop */
        private static final String ACTION_STOP_HOTSPOT =
                                            "com.android.server.WifiManager.action.STOP_HOTSPOT";
        private BroadcastReceiver mHotspotStopReceiver;
        private PendingIntent mIntentStopHotspot;
        private static final int STOP_HOTSPOT_REQUEST = 2;
        private static final long HOTSPOT_DISABLE_MS = 5 * 60 * 1000;
        private int mDuration = MtkSettingsExt.System.WIFI_HOTSPOT_AUTO_DISABLE_FOR_FIVE_MINS;
        private HandlerThread mSoftApHandlerThread;
        private HotspotAutoDisableObserver mHotspotAutoDisableObserver;
        private AlarmManager mAlarmManager;

        /* M: HotspotAutoDisableObserver for watching the setting change */
        private class HotspotAutoDisableObserver extends ContentObserver {
            public HotspotAutoDisableObserver(Handler handler) {
                super(handler);
                mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    MtkSettingsExt.System.WIFI_HOTSPOT_AUTO_DISABLE), false, this);
            }

            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mDuration = Settings.System.getInt(
                                    mContext.getContentResolver(),
                                    MtkSettingsExt.System.WIFI_HOTSPOT_AUTO_DISABLE,
                                    MtkSettingsExt.System.WIFI_HOTSPOT_AUTO_DISABLE_FOR_FIVE_MINS);
                if (mDuration != 0) {
                    if (mClientNum == 0 &&
                        getCurrentState() == mStartedState) {
                        mAlarmManager.cancel(mIntentStopHotspot);
                        Log.d(TAG, "Set alarm for setting changed, mDuration:" + mDuration);
                        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            android.os.SystemClock.elapsedRealtime() +
                            mDuration * HOTSPOT_DISABLE_MS, mIntentStopHotspot);
                    }
                } else {
                    mAlarmManager.cancel(mIntentStopHotspot);
                }
            }
        }
        /// @}

        SoftApStateMachine(Looper looper) {
            super(TAG, looper);

            addState(mIdleState);
            addState(mStartedState);

            setInitialState(mIdleState);
            start();

            /// M: Hotspot manager implementation @{
            mHotspotStopReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.e(TAG, "onReceive : ACTION_STOP_HOTSPOT");
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE);
                    cm.stopTethering(ConnectivityManager.TETHERING_WIFI);
                }
            };

            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

            try {
                mMonitorInterfaceName = mApInterface.getInterfaceName();
            } catch (RemoteException e) {
                Log.e(TAG, "Exception in SoftApStateMachine constructor: " + e);
            }
            Log.d(TAG, "mMonitorInterfaceName = " + mMonitorInterfaceName);

            mWifiApMonitor.registerHandler(
                    mMonitorInterfaceName,
                    WifiApMonitor.WPS_OVERLAP_EVENT,
                    getHandler());
            mWifiApMonitor.registerHandler(
                    mMonitorInterfaceName,
                    WifiApMonitor.AP_STA_CONNECTED_EVENT,
                    getHandler());
            mWifiApMonitor.registerHandler(
                    mMonitorInterfaceName,
                    WifiApMonitor.AP_STA_DISCONNECTED_EVENT,
                    getHandler());
            mWifiApMonitor.registerHandler(
                    mMonitorInterfaceName,
                    WifiApMonitor.SUP_DISCONNECTION_EVENT,
                    getHandler());
            /// @}
        }

        private class IdleState extends State {
            @Override
            public void enter() {
                Log.d(TAG, getName());

                mDeathRecipient.unlinkToDeath();
                unregisterObserver();
            }

            @Override
            public boolean processMessage(Message message) {
                switch (message.what) {
                    case CMD_START:
                        updateApState(WifiManager.WIFI_AP_STATE_ENABLING, 0);
                        if (!mDeathRecipient.linkToDeath(mApInterface.asBinder())) {
                            mDeathRecipient.unlinkToDeath();
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                    WifiManager.SAP_START_FAILURE_GENERAL);
                            mWifiMetrics.incrementSoftApStartResult(
                                    false, WifiManager.SAP_START_FAILURE_GENERAL);
                            break;
                        }

                        try {
                            mNetworkObserver = new NetworkObserver(mApInterface.getInterfaceName());
                            mNwService.registerObserver(mNetworkObserver);
                        } catch (RemoteException e) {
                            mDeathRecipient.unlinkToDeath();
                            unregisterObserver();
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                          WifiManager.SAP_START_FAILURE_GENERAL);
                            mWifiMetrics.incrementSoftApStartResult(
                                    false, WifiManager.SAP_START_FAILURE_GENERAL);
                            break;
                        }

                        int result = startSoftAp((WifiConfiguration) message.obj);
                        if (result != SUCCESS) {
                            int failureReason = WifiManager.SAP_START_FAILURE_GENERAL;
                            if (result == ERROR_NO_CHANNEL) {
                                failureReason = WifiManager.SAP_START_FAILURE_NO_CHANNEL;
                            }
                            mDeathRecipient.unlinkToDeath();
                            unregisterObserver();
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED, failureReason);
                            mWifiMetrics.incrementSoftApStartResult(false, failureReason);
                            break;
                        }

                        /// M: Hotspot manager implementation @{
                        mWifiApMonitor.startMonitoring(mMonitorInterfaceName);
                        mIsMonitoring = true;
                        /// @}

                        transitionTo(mStartedState);
                        break;
                    /// M: Hotspot manager implementation @{
                    case WifiApMonitor.SUP_DISCONNECTION_EVENT:
                        if (mIsMonitoring) {
                            // If the disconnection event is from driver, close the connection.
                            mIsMonitoring = false;
                            mWifiApNative.closeHostapdConnection();
                            mWifiApMonitor.stopMonitoring(mMonitorInterfaceName);
                            mWifiApMonitor.unregisterAllHandler();
                            Log.d(TAG, "ap0 TEMINATING event");
                        } else {
                            // If the disconnection event is from calling stopMonitoring, ignore it
                            Log.d(TAG, "ap0 is stopped already");
                        }
                        break;
                    /// @}
                    default:
                        // Ignore all other commands.
                        break;
                }

                return HANDLED;
            }

            private void unregisterObserver() {
                if (mNetworkObserver == null) {
                    return;
                }
                try {
                    mNwService.unregisterObserver(mNetworkObserver);
                } catch (RemoteException e) { }
                mNetworkObserver = null;
            }
        }

        private class StartedState extends State {
            private boolean mIfaceIsUp;

            private void onUpChanged(boolean isUp) {
                if (isUp == mIfaceIsUp) {
                    return;  // no change
                }
                mIfaceIsUp = isUp;
                if (isUp) {
                    Log.d(TAG, "SoftAp is ready for use");
                    updateApState(WifiManager.WIFI_AP_STATE_ENABLED, 0);
                    mWifiMetrics.incrementSoftApStartResult(true, 0);
                } else {
                    // TODO: handle the case where the interface was up, but goes down
                }
            }

            /// M: Hotspot manager implementation @{
            private void sendClientsChangedBroadcast() {
                Intent intent = new Intent(WifiHotspotManager.WIFI_HOTSPOT_CLIENTS_CHANGED_ACTION);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }

            private void sendClientsIpReadyBroadcast(String mac, String ip, String deviceName) {
                Intent intent = new Intent("android.net.wifi.WIFI_HOTSPOT_CLIENTS_IP_READY");
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                intent.putExtra(WifiHotspotManager.EXTRA_DEVICE_ADDRESS, mac);
                intent.putExtra(WifiHotspotManager.EXTRA_IP_ADDRESS, ip);
                intent.putExtra(WifiHotspotManager.EXTRA_DEVICE_NAME, deviceName);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
            /// @}

            @Override
            public void enter() {
                Log.d(TAG, getName());

                mIfaceIsUp = false;
                InterfaceConfiguration config = null;
                try {
                    config = mNwService.getInterfaceConfig(mApInterface.getInterfaceName());
                } catch (RemoteException e) {
                }
                if (config != null) {
                    onUpChanged(config.isUp());
                }

                /// M: Hotspot manager implementation @{
                /* M: For hotspot auto stop */
                mContext.registerReceiver(
                        mHotspotStopReceiver, new IntentFilter(ACTION_STOP_HOTSPOT));

                mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

                mSoftApHandlerThread = new HandlerThread("softApThreadForObserver");
                mSoftApHandlerThread.start();
                mHotspotAutoDisableObserver =
                        new HotspotAutoDisableObserver(
                                new Handler(mSoftApHandlerThread.getLooper()));
                Intent stopHotspotIntent = new Intent(ACTION_STOP_HOTSPOT);
                mIntentStopHotspot = PendingIntent.getBroadcast(
                                                        mContext,
                                                        STOP_HOTSPOT_REQUEST,
                                                        stopHotspotIntent,
                                                        0);
                mDuration = Settings.System.getInt(
                                    mContext.getContentResolver(),
                                    MtkSettingsExt.System.WIFI_HOTSPOT_AUTO_DISABLE,
                                    MtkSettingsExt.System.WIFI_HOTSPOT_AUTO_DISABLE_FOR_FIVE_MINS);
                if (mDuration != 0) {
                    mAlarmManager.cancel(mIntentStopHotspot);
                    Log.d(TAG, "Set alarm for enter StartedState, mDuration:" + mDuration);
                    mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() +
                        mDuration * HOTSPOT_DISABLE_MS, mIntentStopHotspot);
                }
                /// @}
            }

            /// M: Hotspot manager implementation @{
            @Override
            public void exit() {
                mSoftApHandlerThread.quit();
                mContext.getContentResolver().unregisterContentObserver(
                        mHotspotAutoDisableObserver);
                mContext.unregisterReceiver(mHotspotStopReceiver);

                if (mDuration != 0) {
                    mAlarmManager.cancel(mIntentStopHotspot);
                }
                synchronized (mHotspotClients) {
                    mHotspotClients.clear();
                }
                sendClientsChangedBroadcast();
            }
            /// @}

            @Override
            public boolean processMessage(Message message) {
                switch (message.what) {
                    case CMD_INTERFACE_STATUS_CHANGED:
                        if (message.obj != mNetworkObserver) {
                            // This is from some time before the most recent configuration.
                            break;
                        }
                        boolean isUp = message.arg1 == 1;
                        onUpChanged(isUp);
                        break;
                    case CMD_START:
                        // Already started, ignore this command.
                        break;
                    case CMD_AP_INTERFACE_BINDER_DEATH:
                    case CMD_STOP:
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING, 0);
                        stopSoftAp();
                        if (message.what == CMD_AP_INTERFACE_BINDER_DEATH) {
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                    WifiManager.SAP_START_FAILURE_GENERAL);
                        } else {
                            updateApState(WifiManager.WIFI_AP_STATE_DISABLED, 0);
                        }
                        transitionTo(mIdleState);
                        break;
                    /// M: Hotspot manager implementation @{
                    case CMD_POLL_IP_ADDRESS:
                        String deviceAddress = (String) message.obj;
                        int count = message.arg1;
                        String ipAddress =
                            mWifiManager.getWifiHotspotManager().getClientIp(deviceAddress);
                        String deviceName =
                            mWifiManager.getWifiHotspotManager().getClientDeviceName(deviceAddress);
                        Log.d(TAG, "CMD_POLL_IP_ADDRESS ,deviceAddress = " +
                              message.obj + " ipAddress = " + ipAddress + ", count = " + count);
                        if (ipAddress == null && count < POLL_IP_TIMES) {
                            sendMessageDelayed(CMD_POLL_IP_ADDRESS, ++count, 0, deviceAddress,
                                               POLL_IP_ADDRESS_INTERVAL_MSECS);
                        } else if (ipAddress != null) {
                            sendClientsIpReadyBroadcast(deviceAddress, ipAddress, deviceName);
                        }
                        break;
                    case WifiApMonitor.AP_STA_CONNECTED_EVENT:
                        Log.d(TAG, "AP STA CONNECTED:" + message.obj);
                        ++mClientNum;
                        String address = (String) message.obj;
                        synchronized (mHotspotClients) {
                            if (!mHotspotClients.containsKey(address)) {
                                mHotspotClients.put(address, new HotspotClient(address, false));
                            }
                        }

                        // Hotspot auto disable
                        if (mDuration != 0 && mClientNum == 1) {
                            mAlarmManager.cancel(mIntentStopHotspot);
                        }
                        int start = 1;
                        sendMessageDelayed(CMD_POLL_IP_ADDRESS, start, 0, address,
                                           POLL_IP_ADDRESS_INTERVAL_MSECS);

                        sendClientsChangedBroadcast();
                        break;
                    case WifiApMonitor.AP_STA_DISCONNECTED_EVENT:
                        Log.d(TAG, "AP STA DISCONNECTED:" + message.obj);
                        --mClientNum;
                        address = (String) message.obj;
                        synchronized (mHotspotClients) {
                            HotspotClient client = mHotspotClients.get(address);
                            if (client != null && !client.isBlocked) {
                                mHotspotClients.remove(address);
                            }
                        }

                        // Hotspot auto disable
                        if (mDuration != 0 && mClientNum == 0) {
                            Log.d(TAG, "Set alarm for no client, mDuration:" + mDuration);
                            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                android.os.SystemClock.elapsedRealtime() +
                                mDuration * HOTSPOT_DISABLE_MS, mIntentStopHotspot);
                        }

                        sendClientsChangedBroadcast();
                        break;
                    case M_CMD_BLOCK_CLIENT:
                        boolean result = mWifiApNative.blockClientCommand(
                                                       ((HotspotClient) message.obj).deviceAddress);
                        if (result) {
                            synchronized (mHotspotClients) {
                                HotspotClient client =
                                   mHotspotClients.get(((HotspotClient) message.obj).deviceAddress);
                                if (client != null) {
                                    client.isBlocked = true;
                                } else {
                                    Log.e(TAG, "Failed to get " +
                                                ((HotspotClient) message.obj).deviceAddress);
                                }
                            }
                            sendClientsChangedBroadcast();
                        } else {
                            Log.e(
                                TAG,
                                "Failed to block " + ((HotspotClient) message.obj).deviceAddress);
                        }
                        mReplyChannel.replyToMessage(
                                                message,
                                                message.what,
                                                result ? Wifi_SUCCESS : Wifi_FAILURE);
                        break;
                    case M_CMD_UNBLOCK_CLIENT:
                        result = mWifiApNative.unblockClientCommand(
                                                    ((HotspotClient) message.obj).deviceAddress);
                        if (result) {
                            synchronized (mHotspotClients) {
                                mHotspotClients.remove(((HotspotClient) message.obj).deviceAddress);
                            }
                            sendClientsChangedBroadcast();
                        } else {
                            Log.e(
                                TAG,
                                "Failed to unblock " + ((HotspotClient) message.obj).deviceAddress);
                        }
                        mReplyChannel.replyToMessage(
                                                message,
                                                message.what,
                                                result ? Wifi_SUCCESS : Wifi_FAILURE);
                        break;
                    case M_CMD_START_AP_WPS:
                        WpsInfo wpsConfig = (WpsInfo) message.obj;
                        switch (wpsConfig.setup) {
                            case WpsInfo.PBC:
                                mStartApWps = true;
                                mWifiApNative.startApWpsPbcCommand();
                                break;
                            case WpsInfo.DISPLAY:
                                String pin =
                                    mWifiApNative.startApWpsCheckPinCommand(wpsConfig.pin);
                                Log.d(TAG, "Check pin result:" + pin);
                                if (pin != null && !pin.equals("FAIL-CHECKSUM\n")) {
                                    mWifiApNative.startApWpsWithPinFromDeviceCommand(pin);
                                } else {
                                    Intent intent = new Intent(
                                            WifiHotspotManager.WIFI_WPS_CHECK_PIN_FAIL_ACTION);
                                    intent.addFlags(
                                            Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                                }
                                break;
                            default:
                                Log.e(TAG, "Invalid setup for WPS!");
                                break;
                        }
                        break;
                    case WifiApMonitor.WPS_OVERLAP_EVENT:
                        if (mStartApWps) {
                            Intent intent =
                                new Intent(WifiHotspotManager.WIFI_HOTSPOT_OVERLAP_ACTION);
                            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                            mStartApWps = false;
                        }
                        break;
                    /// @}
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

    }
}
