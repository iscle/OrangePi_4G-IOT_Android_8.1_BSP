/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
*/

package com.mediatek.server.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;

import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.server.wifi.WifiConfigManager;
import com.android.server.wifi.WifiCountryCode;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiStateMachine;
import com.android.server.wifi.util.TelephonyUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

public class MtkEapSimUtility {
    private static final String TAG = "MtkEapSimUtility";
    private static boolean mVerboseLoggingEnabled = false;
    public static final int GET_SUBID_NULL_ERROR = -1;
    private static TelephonyManager mTelephonyManager;
    private static WifiConfigManager mWifiConfigManager;
    private static WifiNative mWifiNative;
    private static WifiStateMachine mWifiStateMachine;
    private static WifiCountryCode mCountryCode;
    //M: ALPS03503585 For Auto connect EAP-SIM AP
    private static String mSim1IccState = IccCardConstants.INTENT_VALUE_ICC_UNKNOWN;
    private static String mSim2IccState = IccCardConstants.INTENT_VALUE_ICC_UNKNOWN;
    private static boolean mSim1Present = false;
    private static boolean mSim2Present = false;

    public static void init() {
        if (mTelephonyManager == null) {
            mTelephonyManager = WifiInjector.getInstance().makeTelephonyManager();
        }
        mWifiConfigManager = WifiInjector.getInstance().getWifiConfigManager();
        mWifiNative = WifiInjector.getInstance().getWifiNative();
        mWifiStateMachine = WifiInjector.getInstance().getWifiStateMachine();
        mCountryCode = WifiInjector.getInstance().getWifiCountryCode();
    }

    ///M: ALPS03503585. EAP-SIM for extending to dual sim
    public static boolean setSimSlot(int networkId, String slotId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Set network sim slot " + slotId + " for netId " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.simSlot = WifiInfo.removeDoubleQuotes(slotId);

        mWifiConfigManager.saveToStore(true);
        return true;
    }

    /**
     * Resets all sim networks state.
     * M: ALPS03503585 EAP-SIM for extending to dual sim
     */
    public static void resetSimNetworks(boolean simPresent, int simSlot) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "resetSimNetworks, simPresent: " + simPresent + ", simSlot: " + simSlot);
        }
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (TelephonyUtil.isSimConfig(config) && getIntSimSlot(config) == simSlot) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Reset SSID " + config.SSID + " with simSlot " + simSlot);
                }
                String currentIdentity = null;
                if (simPresent) {
                    currentIdentity = getSimIdentity(mTelephonyManager, config);
                }
                // Update the loaded config
                config.enterpriseConfig.setIdentity(currentIdentity);
                if (config.enterpriseConfig.getEapMethod() != WifiEnterpriseConfig.Eap.PEAP) {
                    config.enterpriseConfig.setAnonymousIdentity("");
                }
            }
        }
        if (simSlot == 0 || simSlot == -1) {
            mSim1Present = simPresent;
        } else if (simSlot == 1) {
            mSim2Present = simPresent;
        }
        //Enable sim config when it is ready
        if (!simPresent) return;
        List<WifiConfiguration> networks = mWifiConfigManager.getSavedNetworks();
        if (null != networks) {
            for (WifiConfiguration network : networks) {
                if (TelephonyUtil.isSimConfig(network) && getIntSimSlot(network) == simSlot) {
                    Log.d(TAG, "Enable sim config, SSID " + network.SSID + ", slot=" + simSlot);
                    mWifiConfigManager.updateNetworkSelectionStatus(network.networkId,
                            WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLE);
                }
            }
        }
    }

    public static boolean isSim1Present() { return mSim1Present; }
    public static boolean isSim2Present() { return mSim2Present; }
    /**
     * M: ALPS03503585 EAP-SIM for extending to dual sim
     * Convert simSlot from string to integer
     */
    public static int getIntSimSlot(WifiConfiguration config) {
        int slotId = 0;
        String simSlot = config.simSlot;
        if (simSlot != null) {
            String[] simSlots = simSlot.split("\"");
            //simSlot = "\"1\"";
            if (simSlots.length > 1) {
                slotId = Integer.parseInt(simSlots[1]);
                //simSlot = "1";
            } else if (simSlots.length == 1) {
                if (simSlots[0].length() > 0) {
                    slotId = Integer.parseInt(simSlots[0]);
                }
            }
        }
        return slotId;
    }

    /**
     * Get the identity for the current SIM or null if the SIM is not available
     * M: ALPS03503585. EAP-SIM for extending to dual sim
     *
     * @param tm TelephonyManager instance
     * @param config WifiConfiguration that indicates what sort of authentication is necessary
     * @param slotId slot num in phone. 0 for card 1, 1 for card 2, -1 for unspecified card
     * @return String with the identity or none if the SIM is not available or config is invalid
     */
    public static String getSimIdentity(TelephonyManager tm, WifiConfiguration config) {
        if (tm == null) {
            Log.e(TAG, "No valid TelephonyManager");
            return null;
        }
        int slotId = getIntSimSlot(config);
        int subId = getSubId(slotId);
        if (tm.getDefault().getPhoneCount() >= 2 && subId != GET_SUBID_NULL_ERROR) {
            String imsi = tm.getSubscriberId(subId);
            String mccMnc = "";
            if (tm.getSimState(slotId) == TelephonyManager.SIM_STATE_READY) {
                // Returns the Service Provider Name (SPN). SIM state must be SIM_STATE_READY
                mccMnc = tm.getSimOperator(subId);
            }
            return buildIdentity(getSimMethodForConfig(config), imsi, mccMnc);
        } else { ///M: getPhoneCount() < 2 or subId == GET_SUBID_NULL_ERROR
            return TelephonyUtil.getSimIdentity(tm, config);
        }
    }

    ///M: ALPS03503585. GET_SUBID_NULL_ERROR means phone doesn't insert any card
    public static int getSubId(int simSlot) {
        int[] subIds = SubscriptionManager.getSubId(simSlot);
        if (subIds != null) {
            return subIds[0];
        } else {
            return GET_SUBID_NULL_ERROR;
        }
    }

    ///M: ALPS03503585. EAP-SIM for extending to dual sim
    public static int getDefaultSim() {
        return SubscriptionManager.getSlotIndex(SubscriptionManager.getDefaultSubscriptionId());
    }

    ///M: ALPS03503585. EAP-SIM for extending to dual sim
    public static String getIccAuthentication(int appType, int authType, String base64Challenge) {
        String tmResponse = null;
        int slotId = getIntSimSlot(getTargetWificonfiguration());
        if (slotId != GET_SUBID_NULL_ERROR) {
            if (mTelephonyManager.getDefault().getPhoneCount() >= 2) {
                int subId = getSubId(slotId);
                if (subId != GET_SUBID_NULL_ERROR) {
                    Log.d(TAG, "subId: " + subId + ", appType: " + appType
                            + ", authType: " + authType + ", challenge: " + base64Challenge);
                    Log.d(TAG, "getIccAuthentication for specified subId");
                    tmResponse = mTelephonyManager.getIccAuthentication(subId, appType,
                            authType, base64Challenge);
                    return tmResponse;
                }
            }
        }
        Log.d(TAG, "getIccAuthentication for the default subscription");
        tmResponse = mTelephonyManager.getIccAuthentication(appType, authType, base64Challenge);
        return tmResponse;
    }

    public static boolean isConfigSimCardLoaded(WifiConfiguration config) {
        int simSlot = getIntSimSlot(config);
        // If simSlot is unspecified (-1)
        if (simSlot == GET_SUBID_NULL_ERROR) {
            Log.d(TAG, "simSlot is unspecified, check loaded for iccState: ("
                    + mSim1IccState + ", " + mSim2IccState + ")");
            if (mSim1IccState.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)
                    || mSim2IccState.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                return true;
            } else {
                return false;
            }
        }
        String iccState = (simSlot == 0) ? mSim1IccState : mSim2IccState;
        return iccState.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED);
    }

    public static void setSim1IccState(String iccState) {
        mSim1IccState = iccState;
    }

    public static void setSim2IccState(String iccState) {
        mSim2IccState = iccState;
    }

    public static String getSim1IccState() { return mSim1IccState; }

    public static String getSim2IccState() { return mSim2IccState; }

    public static boolean isDualSimAllAbsent() {
        return mSim1IccState == IccCardConstants.INTENT_VALUE_ICC_ABSENT
                && mSim2IccState == IccCardConstants.INTENT_VALUE_ICC_ABSENT;
    }

    private static int lookupFrameworkNetworkId(int supplicantNetworkId) {
        return mWifiNative.getFrameworkNetworkId(supplicantNetworkId);
    }

    //M: If slotId is unspecified(-1), set default sim from TelepohonyManager
    public static void setDefaultSimToUnspecifiedSimSlot() {
        WifiConfiguration targetWificonfiguration = getTargetWificonfiguration();
        int slotId = getIntSimSlot(targetWificonfiguration);
        int subId = getSubId(slotId);
        if (subId == GET_SUBID_NULL_ERROR) {
            Log.d(TAG, "config.simSlot is unspecified(-1), so it should be changed"
                    + "to default sim slot selected by telephony manager");
            int defaultSim = getDefaultSim();
            // Update config's simSlot
            targetWificonfiguration.simSlot = "\"" + defaultSim + "\"";
            if (setSimSlot(targetWificonfiguration.networkId, targetWificonfiguration.simSlot)) {
                Log.d(TAG, "config.simSlot is changed to " + targetWificonfiguration.simSlot);
            }
        }
    }

    ///M: Disable EAP-SIM AP if modem is not ready
    // Airplane mode off and wifi on, since modem is not ready, disable network
    public static void disableSimConfigWhenSimNotLoaded() {
        if (!mSim1IccState.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)
                || !mSim2IccState.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
            Log.d(TAG, "iccState: (" + mSim1IccState + "," + mSim2IccState + ")"
                    + " Check EAP-SIM state when supplicant connection finish");
            List<WifiConfiguration> networks = mWifiConfigManager.getSavedNetworks();
            if (null != networks) {
                for (WifiConfiguration network : networks) {
                    if (TelephonyUtil.isSimConfig(network) && !isConfigSimCardLoaded(network)) {
                        Log.d(TAG, "Disable EAP SIM/AKA network for WNS, "
                                + "netId: " + network.networkId);
                        mWifiConfigManager.updateNetworkSelectionStatus(network.networkId,
                                WifiConfiguration.NetworkSelectionStatus
                                        .DISABLED_AUTHENTICATION_SIM_CARD_ABSENT);
                    }
                }
            } else {
                Log.d(TAG, "Check for EAP_SIM_AKA, but networks is null!");
            }
        }
    }

    public static boolean disableSimConfigIfSimNotLoaded(WifiConfiguration config) {
        if (TelephonyUtil.isSimConfig(config)) {
            if (!isConfigSimCardLoaded(config)) {
                Log.e(TAG, "START_CONNECT EAP-SIM AP, iccState: "
                        + "(" + mSim1IccState + ", " + mSim2IccState + "), set networkStatus to "
                        + "DISABLED_AUTHENTICATION_SIM_CARD_ABSENT, drop this connect");
                mWifiConfigManager.updateNetworkSelectionStatus(config.networkId,
                        WifiConfiguration.NetworkSelectionStatus
                                .DISABLED_AUTHENTICATION_SIM_CARD_ABSENT);
                return true;
            }
        }
        return false;
    }

    /// get function or variable by reflection @{
    private static WifiConfiguration getTargetWificonfiguration() {
        try {
            Field field = mWifiStateMachine.getClass()
                    .getDeclaredField("targetWificonfiguration");
            field.setAccessible(true);
            return (WifiConfiguration) field.get(mWifiStateMachine);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Collection<WifiConfiguration> getInternalConfiguredNetworks() {
        try {
            Method method = mWifiConfigManager.getClass()
                    .getDeclaredMethod("getInternalConfiguredNetworks");
            method.setAccessible(true);
            return (Collection<WifiConfiguration>) method.invoke(mWifiConfigManager);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static WifiConfiguration getInternalConfiguredNetwork(int networkId) {
        try {
            Method method = mWifiConfigManager.getClass()
                    .getDeclaredMethod("getInternalConfiguredNetwork", int.class);
            method.setAccessible(true);
            return (WifiConfiguration) method.invoke(mWifiConfigManager, networkId);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String buildIdentity(int eapMethod, String imsi, String mccMnc) {
        try {
            Method method = TelephonyUtil.class.getDeclaredMethod(
                    "buildIdentity", int.class, String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, eapMethod, imsi, mccMnc);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static int getSimMethodForConfig(WifiConfiguration config) {
        try {
            Method method = TelephonyUtil.class.getDeclaredMethod(
                    "getSimMethodForConfig", WifiConfiguration.class);
            method.setAccessible(true);
            return (Integer) method.invoke(null, config);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return -1;
        }
    }
    /// }@

    public static void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            mVerboseLoggingEnabled = true;
        } else {
            mVerboseLoggingEnabled = false;
        }
    }

    public static class MtkSimBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            ///M: ALPS03503585 EAP-SIM for extending to dual sim
            int simSlot = intent.getIntExtra(PhoneConstants.SLOT_KEY, -1);
            Log.d(TAG, "onReceive ACTION_SIM_STATE_CHANGED iccState: " + state
                    + ", simSlot: " + simSlot);
            int CMD_RESET_SIM_NETWORKS = 0;
            try {
                Field field = mWifiStateMachine.getClass()
                        .getDeclaredField("CMD_RESET_SIM_NETWORKS");
                field.setAccessible(true);
                CMD_RESET_SIM_NETWORKS = (Integer) field.get(mWifiStateMachine);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(state)) {
                if (0 == simSlot || -1 == simSlot) { /// simSlot = -1 means single sim
                    setSim1IccState(IccCardConstants.INTENT_VALUE_ICC_ABSENT);
                } else if (1 == simSlot) {
                    setSim2IccState(IccCardConstants.INTENT_VALUE_ICC_ABSENT);
                }
                Log.d(TAG, "resetting networks because SIM" + simSlot + " was removed");
                mWifiStateMachine.sendMessage(CMD_RESET_SIM_NETWORKS, 0, simSlot);
                if (isDualSimAllAbsent()) {
                    Log.d(TAG, "All sim card is absent, "
                            + "resetting country code because SIM is removed");
                    mCountryCode.simCardRemoved();
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(state)) {
                if (0 == simSlot || -1 == simSlot) { /// simSlot = -1 means single sim
                    setSim1IccState(IccCardConstants.INTENT_VALUE_ICC_LOADED);
                } else if (1 == simSlot) {
                    setSim2IccState(IccCardConstants.INTENT_VALUE_ICC_LOADED);
                }
                Log.d(TAG, "resetting networks because SIM" + simSlot + " was loaded");
                mWifiStateMachine.sendMessage(CMD_RESET_SIM_NETWORKS, 1, simSlot);
            }
        }
    }
}
