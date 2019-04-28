/**
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.phone.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.phone.PhoneGlobals;

public class VoicemailProviderSettingsUtil {
    private static final String LOG_TAG = VoicemailProviderSettingsUtil.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final String VM_NUMBERS_SHARED_PREFERENCES_NAME = "vm_numbers";

    // Suffix appended to provider key for storing vm number
    private static final String VM_NUMBER_TAG = "#VMNumber";
    // Suffix appended to forward settings key for storing an individual setting
    private static final String FWD_SETTING_TAG = "#Setting";
    // Suffix appended to provider key for storing forwarding settings
    private static final String FWD_SETTINGS_TAG = "#FWDSettings";
    // Suffix appended to forward settings key for storing length of settings array
    private static final String FWD_SETTINGS_LENGTH_TAG = "#Length";

    // Suffixes appended to forward setting key for storing an individual setting properties
    private static final String FWD_SETTING_STATUS = "#Status";
    private static final String FWD_SETTING_REASON = "#Reason";
    private static final String FWD_SETTING_NUMBER = "#Number";
    private static final String FWD_SETTING_TIME = "#Time";

    /**
     * Returns settings previously stored for the currently selected voice mail provider. If no
     * setting is stored for the voice mail provider, return null.
     */
    public static VoicemailProviderSettings load(Context context, String key) {
        SharedPreferences prefs = getPrefs(context);

        String vmNumberSetting = prefs.getString(key + VM_NUMBER_TAG, null);
        if (vmNumberSetting == null) {
            Log.w(LOG_TAG, "VoiceMailProvider settings for the key \"" + key + "\""
                    + " were not found. Returning null.");
            return null;
        }

        CallForwardInfo[] cfi = VoicemailProviderSettings.NO_FORWARDING;
        String fwdKey = key + FWD_SETTINGS_TAG;
        int fwdLen = prefs.getInt(fwdKey + FWD_SETTINGS_LENGTH_TAG, 0);
        if (fwdLen > 0) {
            cfi = new CallForwardInfo[fwdLen];
            for (int i = 0; i < cfi.length; i++) {
                String settingKey = fwdKey + FWD_SETTING_TAG + String.valueOf(i);
                cfi[i] = new CallForwardInfo();
                cfi[i].status = prefs.getInt(settingKey + FWD_SETTING_STATUS, 0);
                cfi[i].reason = prefs.getInt(
                        settingKey + FWD_SETTING_REASON,
                        CommandsInterface.CF_REASON_ALL_CONDITIONAL);
                cfi[i].serviceClass = CommandsInterface.SERVICE_CLASS_VOICE;
                cfi[i].toa = PhoneNumberUtils.TOA_International;
                cfi[i].number = prefs.getString(settingKey + FWD_SETTING_NUMBER, "");
                cfi[i].timeSeconds = prefs.getInt(settingKey + FWD_SETTING_TIME, 20);
            }
        }

        VoicemailProviderSettings settings = new VoicemailProviderSettings(vmNumberSetting, cfi);
        if (DBG) log("Loaded settings for " + key + ": " + settings.toString());
        return settings;
    }

    /**
     * Saves new VM provider settings and associates them with the currently selected provider if
     * the settings are different than the ones already stored for this provider.
     *
     * These will be used later when the user switches a provider.
     */
    public static void save(Context context, String key, VoicemailProviderSettings newSettings) {
        VoicemailProviderSettings curSettings = load(context, key);
        if (newSettings.equals(curSettings)) {
            if (DBG) log("save: Not saving setting for " + key + " since they have not changed");
            return;
        }

        if (DBG) log("Saving settings for " + key + ": " + newSettings.toString());

        SharedPreferences prefs = getPrefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key + VM_NUMBER_TAG, newSettings.getVoicemailNumber());
        String fwdKey = key + FWD_SETTINGS_TAG;

        CallForwardInfo[] s = newSettings.getForwardingSettings();
        if (s != VoicemailProviderSettings.NO_FORWARDING) {
            editor.putInt(fwdKey + FWD_SETTINGS_LENGTH_TAG, s.length);
            for (int i = 0; i < s.length; i++) {
                String settingKey = fwdKey + FWD_SETTING_TAG + String.valueOf(i);
                CallForwardInfo fi = s[i];
                editor.putInt(settingKey + FWD_SETTING_STATUS, fi.status);
                editor.putInt(settingKey + FWD_SETTING_REASON, fi.reason);
                editor.putString(settingKey + FWD_SETTING_NUMBER, fi.number);
                editor.putInt(settingKey + FWD_SETTING_TIME, fi.timeSeconds);
            }
        } else {
            editor.putInt(fwdKey + FWD_SETTINGS_LENGTH_TAG, 0);
        }

        editor.apply();
    }

    /**
     * Deletes settings for the provider identified by this key.
     */
    public static void delete(Context context, String key) {
        if (DBG) log("Deleting settings for" + key);

        if (TextUtils.isEmpty(key)) {
            return;
        }

        SharedPreferences prefs = getPrefs(context);
        prefs.edit()
                .putString(key + VM_NUMBER_TAG, null)
                .putInt(key + FWD_SETTINGS_TAG + FWD_SETTINGS_LENGTH_TAG, 0)
                .commit();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(
                VM_NUMBERS_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
