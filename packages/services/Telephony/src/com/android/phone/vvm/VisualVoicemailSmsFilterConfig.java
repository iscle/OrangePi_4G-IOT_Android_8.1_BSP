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
package com.android.phone.vvm;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.util.ArraySet;

import com.android.phone.vvm.RemoteVvmTaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stores the config values needed for visual voicemail sms filtering. The values from
 * OmtpVvmCarrierConfigHelper are stored here during activation instead. These values are read and
 * written through TelephonyManager.
 */
public class VisualVoicemailSmsFilterConfig {

    private static final String VVM_SMS_FILTER_COFIG_SHARED_PREFS_KEY_PREFIX =
            "vvm_sms_filter_config_";
    private static final String ENABLED_KEY = "_enabled";
    private static final String PREFIX_KEY = "_prefix";
    private static final String ORIGINATING_NUMBERS_KEY = "_originating_numbers";
    private static final String DESTINATION_PORT_KEY = "_destination_port";
    private static final String DEFAULT_PACKAGE = "com.android.phone";

    public static void enableVisualVoicemailSmsFilter(Context context, String callingPackage,
            int subId,
            VisualVoicemailSmsFilterSettings settings) {
        new Editor(context, callingPackage, subId)
                .setBoolean(ENABLED_KEY, true)
                .setString(PREFIX_KEY, settings.clientPrefix)
                .setStringList(ORIGINATING_NUMBERS_KEY, settings.originatingNumbers)
                .setInt(DESTINATION_PORT_KEY, settings.destinationPort)
                .apply();
    }

    public static void disableVisualVoicemailSmsFilter(Context context, String callingPackage,
            int subId) {
        new Editor(context, callingPackage, subId)
                .setBoolean(ENABLED_KEY, false)
                .apply();
    }

    public static VisualVoicemailSmsFilterSettings getActiveVisualVoicemailSmsFilterSettings(
            Context context, int subId) {
        ComponentName componentName = RemoteVvmTaskManager.getRemotePackage(context, subId);
        String packageName;
        if (componentName == null) {
            packageName = DEFAULT_PACKAGE;
        } else {
            packageName = componentName.getPackageName();
        }
        return getVisualVoicemailSmsFilterSettings(
                context,
                packageName,
                subId);
    }

    @Nullable
    public static VisualVoicemailSmsFilterSettings getVisualVoicemailSmsFilterSettings(
            Context context,
            String packageName, int subId) {
        Reader reader = new Reader(context, packageName, subId);
        if (!reader.getBoolean(ENABLED_KEY, false)) {
            return null;
        }
        return new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix(reader.getString(PREFIX_KEY,
                        VisualVoicemailSmsFilterSettings.DEFAULT_CLIENT_PREFIX))
                .setOriginatingNumbers(reader.getStringSet(ORIGINATING_NUMBERS_KEY,
                        VisualVoicemailSmsFilterSettings.DEFAULT_ORIGINATING_NUMBERS))
                .setDestinationPort(reader.getInt(DESTINATION_PORT_KEY,
                        VisualVoicemailSmsFilterSettings.DEFAULT_DESTINATION_PORT))
                .build();
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context.createDeviceProtectedStorageContext());
    }

    private static String makePerPhoneAccountKeyPrefix(String packageName, int subId) {
        // subId is persistent across reboot and upgrade, but not across devices.
        // ICC id is better as a key but it involves more trouble to get one as subId is more
        // commonly passed around.
        return VVM_SMS_FILTER_COFIG_SHARED_PREFS_KEY_PREFIX + packageName + "_"
                + subId;
    }

    private static class Editor {

        private final SharedPreferences.Editor mPrefsEditor;
        private final String mKeyPrefix;

        public Editor(Context context, String packageName, int subId) {
            mPrefsEditor = getSharedPreferences(context).edit();
            mKeyPrefix = makePerPhoneAccountKeyPrefix(packageName, subId);
        }

        private Editor setInt(String key, int value) {
            mPrefsEditor.putInt(makeKey(key), value);
            return this;
        }

        private Editor setString(String key, String value) {
            mPrefsEditor.putString(makeKey(key), value);
            return this;
        }

        private Editor setBoolean(String key, boolean value) {
            mPrefsEditor.putBoolean(makeKey(key), value);
            return this;
        }

        private Editor setStringList(String key, List<String> value) {
            mPrefsEditor.putStringSet(makeKey(key), new ArraySet(value));
            return this;
        }

        public void apply() {
            mPrefsEditor.apply();
        }

        private String makeKey(String key) {
            return mKeyPrefix + key;
        }
    }


    private static class Reader {

        private final SharedPreferences mPrefs;
        private final String mKeyPrefix;

        public Reader(Context context, String packageName, int subId) {
            mPrefs = getSharedPreferences(context);
            mKeyPrefix = makePerPhoneAccountKeyPrefix(packageName, subId);
        }

        private int getInt(String key, int defaultValue) {
            return mPrefs.getInt(makeKey(key), defaultValue);
        }

        private String getString(String key, String defaultValue) {
            return mPrefs.getString(makeKey(key), defaultValue);
        }

        private boolean getBoolean(String key, boolean defaultValue) {
            return mPrefs.getBoolean(makeKey(key), defaultValue);
        }

        private List<String> getStringSet(String key, List<String> defaultValue) {
            Set<String> result = mPrefs.getStringSet(makeKey(key), null);
            if (result == null) {
                return defaultValue;
            }
            return new ArrayList<>(result);
        }

        private String makeKey(String key) {
            return mKeyPrefix + key;
        }
    }
}
