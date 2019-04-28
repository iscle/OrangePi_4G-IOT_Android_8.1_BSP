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
package com.android.emergency.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.edit.EditInfoActivity;
import com.android.emergency.preferences.EmergencyContactsPreference;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Collections;

/** Utility methods for dealing with preferences. */
public class PreferenceUtils {
    @VisibleForTesting
    public static final String SETTINGS_SUGGESTION_ACTIVITY_ALIAS = ".edit.EditInfoSuggestion";

    /** Returns true if there is at least one preference set. */
    public static boolean hasAtLeastOnePreferenceSet(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (String key : PreferenceKeys.KEYS_VIEW_EMERGENCY_INFO) {
            if (!TextUtils.isEmpty(prefs.getString(key, ""))) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if there is at least one valid (still existing) emergency contact. */
    public static boolean hasAtLeastOneEmergencyContact(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String emergencyContactsString = "";
        try {
            emergencyContactsString = prefs.getString(PreferenceKeys.KEY_EMERGENCY_CONTACTS, "");
        } catch (ClassCastException e) {
            // Protect against b/28194605: We used to store the contacts using a string set.
            // If it is a string set, ignore its value. If it is not a string set it will throw
            // a ClassCastException
            prefs.getStringSet(
                    PreferenceKeys.KEY_EMERGENCY_CONTACTS,
                    Collections.<String>emptySet());
        }

        return !EmergencyContactsPreference.deserializeAndFilter(
                PreferenceKeys.KEY_EMERGENCY_CONTACTS,
                context,
                emergencyContactsString).isEmpty();
    }

    /**
     * Enables or disables the settings suggestion for this application, depending on whether any
     * emergency settings exist.
     */
    public static void updateSettingsSuggestionState(Context context) {
        int state = hasAtLeastOnePreferenceOrContactSet(context) ?
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED :
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        setSettingsSuggestionState(context, state);
    }

    /** Enables the settings suggestion for this application. */
    public static void enableSettingsSuggestion(Context context) {
        setSettingsSuggestionState(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    private static boolean hasAtLeastOnePreferenceOrContactSet(Context context) {
        return hasAtLeastOnePreferenceSet(context) || hasAtLeastOneEmergencyContact(context);
    }

    private static void setSettingsSuggestionState(Context context, int state) {
        String packageName = context.getPackageName();
        String targetClass = packageName + SETTINGS_SUGGESTION_ACTIVITY_ALIAS;
        ComponentName name = new ComponentName(packageName, targetClass);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP);
    }
}
