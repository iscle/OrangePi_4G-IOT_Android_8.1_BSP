/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
*/

package com.mediatek.server.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.Process;
import android.os.RemoteException;
import android.security.KeyStore;
import android.util.Log;
import android.util.MutableBoolean;

import com.android.server.wifi.SupplicantStaIfaceHal;
import com.android.server.wifi.SupplicantStaNetworkHal;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.util.InformationElementUtil;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import vendor.mediatek.hardware.wifi.supplicant.V1_1.IMtkSupplicantStaIface;
import vendor.mediatek.hardware.wifi.supplicant.V1_1.IMtkSupplicantStaNetwork;

public class MtkWapi {
    private static final String TAG = "MtkWapi";
    private static final int WAPI_VERSION = 1;
    private static final int WAPI_AUTH_KEY_MGMT_WAI = 0x01721400;
    private static final int WAPI_AUTH_KEY_MGMT_PSK = 0x02721400;

    /* Definition of WAPI information element ID */
    public static final int EID_WAPI = 68;

    /* Singleton MtkWapi */
    private static MtkWapi sMtkWapi = null;

    /* This flag indicates latest aliases we got from KeyStore, and used it
           to determine need to update or not*/
    public static String[] mAliasesCache;

    /* Use this flag to on/off WAPI feature in fwk*/
    public static boolean mIsSystemSupportWapi = false;

    /* Use this flag to check whether we have inquired system support WAPI */
    public static boolean mIsCheckedSupport = false;

    /**
     * Parse WAPI element
     */
    public static String parseWapiElement(InformationElement ie) {
        if (!mIsCheckedSupport) {
            if(checkSupportWapi()) {
                init();
            }
        }

        if (!mIsSystemSupportWapi) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.wrap(ie.bytes).order(ByteOrder.LITTLE_ENDIAN);
        String TAG = "InformationElementUtil.WAPI";
        Log.d(TAG, "parseWapiElement start");

        try {
            // version
            if (buf.getShort() != WAPI_VERSION) {
                // incorrect version
                Log.e(TAG, "incorrect WAPI version");
                return null;
            }

            // count
            int count = buf.getShort();
            if (count != 1) {
                Log.e(TAG, "WAPI IE invalid AKM count: " + count);
            }

            // found the WAPI IE, hence start building the capability string
            String security = "[WAPI";

            // keyMgmt
            int keyMgmt = buf.getInt();

            // judge keyMgmt is WAPI-PSK or WAPI-CERT
            if (keyMgmt == WAPI_AUTH_KEY_MGMT_WAI) {
                security += "-CERT";
            } else if (keyMgmt == WAPI_AUTH_KEY_MGMT_PSK) {
                security += "-PSK";
            }
            // we parsed what we want at this point
            security += "]";
            return security;
        } catch (BufferUnderflowException e) {
            Log.e("IE_Capabilities", "Couldn't parse WAPI element, buffer underflow");
            return null;
        }
    }

    /**
     * Check whether need to update aliases to supplicant
     */
    public static boolean updateAliases(WifiConfiguration config) {
        if (isWapiConfiguration(config) &&
                config.getAuthType() == WifiConfiguration.KeyMgmt.WAPI_CERT &&
                config.mAliases == null) {
            /// M: List all wapi aliases in keystore
            String[] aliases = KeyStore.getInstance().list("WAPI_CACERT_", Process.WIFI_UID);
            Arrays.sort(aliases);
            StringBuilder sortedAliases = new StringBuilder();
            for (String alias : aliases) {
                sortedAliases.append(alias).append(";");
            }
            if (isAliasesChanged(aliases) && !setWapiCertAliasList(sortedAliases.toString())) {
                Log.e(TAG, "failed to set alias list: " + sortedAliases.toString());
                return false;
            }
        }
        return true;
    }

    /**
     * Compare newAliases with mAliasesCache to determine whether need to update
     * aliases to supplicant
     */
    public static boolean isAliasesChanged(String[] newAliases) {
        if (mAliasesCache == null || !Arrays.equals(mAliasesCache, newAliases)) {
            mAliasesCache = newAliases;
            return true;
        }
        return false;
    }

    /**
     * Check whether configuration changes, and might need to update to supplicant
     */
    public static boolean hasAliasesChanged(WifiConfiguration config,
            WifiConfiguration config1) {
        if (!mIsSystemSupportWapi) {
            return false;
        }
        if (!(isWapiConfiguration(config) && isWapiConfiguration(config1))) {
            return false;
        }
        if (config.getAuthType() == KeyMgmt.WAPI_PSK && config1.getAuthType() == KeyMgmt.WAPI_PSK) {
            return false;
        }
        if (config.mAliases == null) {
            if (config1.mAliases == null) {
                return false;
            } else {
                return true;
            }
        }
        if (!config.mAliases.equals(config1.mAliases)) {
            return true;
        }
        return false;
    }

    public static MtkWapi getInstance() {
        if (sMtkWapi == null) {
            synchronized (TAG) {
                sMtkWapi = new MtkWapi();
            }
        }
        return sMtkWapi;
    }

    /**
     * Check whether the given config is WAPI security
     */
    public static boolean isWapiConfiguration(WifiConfiguration config) {
        if (config == null) return false;
        boolean isWapi = false;
        for (int p = 0; p < config.allowedProtocols.size(); p++) {
            if (config.allowedProtocols.get(p)) {
                if (p == WifiConfiguration.Protocol.WAPI) {
                    isWapi = true;
                    break;
                }
            }
        }
        return isWapi;
    }

    /**
     * Build the ScanResult.capabilities String.
     */
    public static String generateCapabilitiesString(InformationElement[] ies,
            BitSet beaconCap, String capabilities) {
        String isWAPI = null;
        if (ies == null || beaconCap == null) {
            return capabilities;
        }
        // Check whether its WAPI security or not from ies.
        for (InformationElement ie : ies) {
            if (ie.id == EID_WAPI) {
                isWAPI = parseWapiElement(ie);
            }
        }
        if (isWAPI != null) {
            capabilities += isWAPI;
            if (capabilities.contains("[WEP]")) {
                return capabilities.replace("[WEP]", "");
            }
        }
        return capabilities;
    }

    /**
     * Helper method to check if the provided |scanResult| corresponds to a WAPI network or not.
     * This checks if the provided capabilities string contains WAPI encryption type or not.
     */
    public static boolean isScanResultForWapiNetwork(ScanResult scanResult) {
        return scanResult.capabilities.contains("WAPI");
    }

    /**
     * Helper method to check if the provided |config| corresponds to a WAPI network or not.
     */
    public static boolean isConfigForWapiNetwork(WifiConfiguration config) {
        return config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WAPI_PSK)
                || config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WAPI_CERT);
    }

    /**
     * Check whether support WAPI or not
     *
     * @param aliases String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    private static boolean checkSupportWapi() {
        SupplicantStaIfaceHal supplicant = WifiInjector.getInstance().getSupplicantStaIfaceHal();
        synchronized (getLock(supplicant)) {
            final String methodStr = "getMtkFeatureMask";
            if (!checkSupplicantStaIfaceAndLogFailure(supplicant, methodStr)) return false;
            try {
                if (getISupplicantStaIface(supplicant) instanceof IMtkSupplicantStaIface) {
                    IMtkSupplicantStaIface mtkIface =
                            (IMtkSupplicantStaIface) getISupplicantStaIface(supplicant);
                    MutableBoolean statusOk = new MutableBoolean(false);
                    mtkIface.getMtkFeatureMask((SupplicantStatus status, int maskValue) -> {
                        statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                        if (statusOk.value) {
                            mIsSystemSupportWapi =
                                    ((maskValue & IMtkSupplicantStaIface.MtkFeatureMask.WAPI) ==
                                            IMtkSupplicantStaIface.MtkFeatureMask.WAPI);
                            mIsCheckedSupport = true;
                        }
                        checkStatusAndLogFailure(supplicant, status, methodStr);
                    });
                    return statusOk.value;
                } else {
                    Log.e(TAG, "getMtkFeatureMask" +
                            " mISupplicantStaIface is not IMtkSupplicantStaIface");
                    return false;
                }
            } catch (RemoteException e) {
                handleRemoteException(supplicant, e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set all WAPI aliases to supplicant
     *
     * @param aliases String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public static boolean setWapiCertAliasList(String aliases) {
        SupplicantStaIfaceHal supplicant = WifiInjector.getInstance().getSupplicantStaIfaceHal();
        synchronized (getLock(supplicant)) {
            final String methodStr = "setWapiCertAliasList";
            if (!checkSupplicantStaIfaceAndLogFailure(supplicant, methodStr)) return false;
            try {
                if (getISupplicantStaIface(supplicant) instanceof IMtkSupplicantStaIface) {
                    IMtkSupplicantStaIface mtkIface =
                            (IMtkSupplicantStaIface) getISupplicantStaIface(supplicant);
                    SupplicantStatus status = mtkIface.setWapiCertAliasList(aliases);
                    return checkStatusAndLogFailure(supplicant, status, methodStr);
                } else {
                    Log.e(TAG, "setWapiCertAliasList" +
                            " mISupplicantStaIface is not IMtkSupplicantStaIface");
                    return false;
                }
            } catch (RemoteException e) {
                handleRemoteException(supplicant, e, methodStr);
                return false;
            }
        }
    }

    private static Object getLock(SupplicantStaIfaceHal supplicant) {
        Object lock;
        try {
            Field lockField = supplicant.getClass().getDeclaredField("mLock");
            lockField.setAccessible(true);
            lock = lockField.get(supplicant);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            lock = new Object();
        }
        return lock;
    }

    private static boolean checkSupplicantStaIfaceAndLogFailure(
            SupplicantStaIfaceHal supplicant, final String methodStr) {
        try {
            Method method = supplicant.getClass().getDeclaredMethod(
                    "checkSupplicantStaIfaceAndLogFailure", String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(supplicant, methodStr);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static ISupplicantStaIface getISupplicantStaIface(
            SupplicantStaIfaceHal supplicant) {
        try {
            Field field = supplicant.getClass().getDeclaredField("mISupplicantStaIface");
            field.setAccessible(true);
            return (ISupplicantStaIface) field.get(supplicant);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean checkStatusAndLogFailure(SupplicantStaIfaceHal supplicant,
            SupplicantStatus status, final String methodStr) {
        try {
            Method method = supplicant.getClass().getDeclaredMethod(
                    "checkStatusAndLogFailure", SupplicantStatus.class, String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(supplicant, status, methodStr);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void handleRemoteException(SupplicantStaIfaceHal supplicant,
            RemoteException re, String methodStr) {
        try {
            Method method = supplicant.getClass().getDeclaredMethod(
                    "handleRemoteException", RemoteException.class, String.class);
            method.setAccessible(true);
            method.invoke(supplicant, re, methodStr);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    public static boolean setWapiCertAlias(SupplicantStaNetworkHal network, String alias) {
        SupplicantStaIfaceHal supplicant = WifiInjector.getInstance().getSupplicantStaIfaceHal();
        //SupplicantStaNetworkHal network = getSupplicantStaNetworkHal(supplicant);
        if (network == null) return false;
        synchronized (getLock(network)) {
            final String methodStr = "setWapiCertAlias";
            if (!checkISupplicantStaNetworkAndLogFailure(network, methodStr)) return false;
            try {
                if (getISupplicantStaNetwork(network) instanceof IMtkSupplicantStaNetwork) {
                    IMtkSupplicantStaNetwork mtkIface =
                            (IMtkSupplicantStaNetwork) getISupplicantStaNetwork(network);
                    SupplicantStatus status =  mtkIface.setWapiCertAlias(
                        alias == null ? WifiEnterpriseConfig.EMPTY_VALUE : alias);
                    return checkStatusAndLogFailure(network, status, methodStr);
                } else {
                    Log.e(TAG, "setWapiCertAlias" +
                            " mISupplicantStaNetwork is not IMtkSupplicantStaNetwork");
                    return false;
                }
            } catch (RemoteException e) {
                handleRemoteException(network, e, methodStr);
                return false;
            }
        }
    }

    private static SupplicantStaNetworkHal getSupplicantStaNetworkHal(
            SupplicantStaIfaceHal supplicant) {
        try {
            Field field = supplicant.getClass().getDeclaredField("mCurrentNetworkRemoteHandle");
            field.setAccessible(true);
            return (SupplicantStaNetworkHal) field.get(supplicant);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Object getLock(SupplicantStaNetworkHal network) {
        Object lock;
        try {
            Field field = network.getClass().getDeclaredField("mLock");
            field.setAccessible(true);
            lock = field.get(network);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            lock = new Object();
        }
        return lock;
    }

    private static boolean checkISupplicantStaNetworkAndLogFailure(
            SupplicantStaNetworkHal network, final String methodStr) {
        try {
            Method method = network.getClass().getDeclaredMethod(
                    "checkISupplicantStaNetworkAndLogFailure", String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(network, methodStr);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static ISupplicantStaNetwork getISupplicantStaNetwork(
            SupplicantStaNetworkHal network) {
        try {
            Field field = network.getClass().getDeclaredField("mISupplicantStaNetwork");
            field.setAccessible(true);
            return (ISupplicantStaNetwork) field.get(network);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean checkStatusAndLogFailure(SupplicantStaNetworkHal network,
            SupplicantStatus status, final String methodStr) {
        try {
            Method method = network.getClass().getDeclaredMethod(
                    "checkStatusAndLogFailure", SupplicantStatus.class, String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(network, status, methodStr);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void handleRemoteException(SupplicantStaNetworkHal network,
            RemoteException re, String methodStr) {
        try {
            Method method = network.getClass().getDeclaredMethod(
                    "handleRemoteException", RemoteException.class, String.class);
            method.setAccessible(true);
            method.invoke(network, re, methodStr);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    private static Context getContext() {
    WifiInjector wi = WifiInjector.getInstance();
        try {
            Field field = wi.getClass().getDeclaredField("mContext");
            field.setAccessible(true);
            return (Context) field.get(wi);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void init() {
        Context context = getContext();
        if (context == null) return;

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        context.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                        int state = intent.getIntExtra(
                                WifiManager.EXTRA_WIFI_STATE,
                                WifiManager.WIFI_STATE_UNKNOWN);
                        Log.d(TAG, "onReceive WIFI_STATE_CHANGED_ACTION state --> " + state);
                        if (state == WifiManager.WIFI_STATE_DISABLED) {
                            mAliasesCache = null;
                        }
                    }
                }
            },
            new IntentFilter(filter));
    }
}
