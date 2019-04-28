/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.services.telephony.sip;

import android.content.Context;
import android.content.Intent;
import android.net.sip.SipManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

/**
 * Wrapper for SIP's preferences.
 */
public class SipPreferences {
    private static final String PREFIX = "[SipPreferences] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */

    // Used to clear out old SharedPreferences file during SipProfile Database Migration
    private static final String SIP_SHARED_PREFERENCES = "SIP_PREFERENCES";

    private Context mContext;

    public SipPreferences(Context context) {
        mContext = context;
    }

    public void setSipCallOption(String option) {
        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.SIP_CALL_OPTIONS, option);

        // Notify SipAccountRegistry in the telephony layer that the configuration has changed.
        // This causes the SIP PhoneAccounts to be re-registered.  This ensures the supported URI
        // schemes for the SIP PhoneAccounts matches the new SIP_CALL_OPTIONS setting.
        Intent intent = new Intent(SipManager.ACTION_SIP_CALL_OPTION_CHANGED);
        mContext.sendBroadcast(intent);
    }

    public String getSipCallOption() {
        String option = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.SIP_CALL_OPTIONS);
        return (option != null) ? option
                                : mContext.getString(R.string.sip_address_only);
    }

    public void setReceivingCallsEnabled(boolean enabled) {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SIP_RECEIVE_CALLS, (enabled ? 1 : 0));
    }

    public boolean isReceivingCallsEnabled() {
        try {
            return (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SIP_RECEIVE_CALLS) != 0);
        } catch (SettingNotFoundException e) {
            log("isReceivingCallsEnabled, option not set; use default value, exception: " + e);
            return false;
        }
    }

    /**
     * Remove obsolete SharedPreferences File upon upgrade from M->N.
     */
    public void clearSharedPreferences() {
        mContext.deleteSharedPreferences(SIP_SHARED_PREFERENCES);
    }

    // TODO: back up to Android Backup

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
