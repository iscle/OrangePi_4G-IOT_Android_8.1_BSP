/*
 * Copyright 2013 The Android Open Source Project
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

package com.android.phone;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.CarrierConfigManager;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.phone.PhoneGlobals;

public class ImsUtil {
    private static final String LOG_TAG = ImsUtil.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static boolean sImsPhoneSupported = false;

    private ImsUtil() {
    }

    static {
        PhoneGlobals app = PhoneGlobals.getInstance();
        sImsPhoneSupported = true;
    }

    /**
     * @return {@code true} if this device supports voice calls using the built-in SIP stack.
     */
    static boolean isImsPhoneSupported() {
        return sImsPhoneSupported;

    }

    /**
     * @return {@code true} if WFC is supported by the platform and has been enabled by the user.
     */
    public static boolean isWfcEnabled(Context context) {
        boolean isEnabledByPlatform = ImsManager.isWfcEnabledByPlatform(context);
        boolean isEnabledByUser = ImsManager.isWfcEnabledByUser(context);
        if (DBG) Log.d(LOG_TAG, "isWfcEnabled :: isEnabledByPlatform=" + isEnabledByPlatform);
        if (DBG) Log.d(LOG_TAG, "isWfcEnabled :: isEnabledByUser=" + isEnabledByUser);
        return isEnabledByPlatform && isEnabledByUser;
    }

    /**
     * @return {@code true} if the device is configured to use "Wi-Fi only" mode. If WFC is not
     * enabled, this will return {@code false}.
     */
    public static boolean isWfcModeWifiOnly(Context context) {
        boolean isWifiOnlyMode =
                ImsManager.getWfcMode(context) == ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY;
        if (DBG) Log.d(LOG_TAG, "isWfcModeWifiOnly :: isWifiOnlyMode" + isWifiOnlyMode);
        return isWfcEnabled(context) && isWifiOnlyMode;
    }

    /**
     * When a call cannot be placed, determines if the use of WFC should be promoted, per the
     * carrier config.  Use of WFC is promoted to the user if the device is connected to a WIFI
     * network, WFC is disabled but provisioned, and the carrier config indicates that the
     * features should be promoted.
     *
     * @return {@code true} if use of WFC should be promoted, {@code false} otherwise.
     */
    public static boolean shouldPromoteWfc(Context context) {
        CarrierConfigManager cfgManager = (CarrierConfigManager) context
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager == null || !cfgManager.getConfig()
                .getBoolean(CarrierConfigManager.KEY_CARRIER_PROMOTE_WFC_ON_CALL_FAIL_BOOL)) {
            return false;
        }

        if (!ImsManager.isWfcProvisionedOnDevice(context)) {
            return false;
        }

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                return ni.getType() == ConnectivityManager.TYPE_WIFI && !isWfcEnabled(context);
            }
        }
        return false;
    }
}
