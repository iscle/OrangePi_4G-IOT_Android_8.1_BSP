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

package com.android.tv.settings.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.annotation.Keep;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.android.settingslib.accessibility.AccessibilityUtils;
import com.android.tv.settings.R;

import java.util.List;

/**
 * Fragment for configuring the accessibility shortcut
 */
@Keep
public class AccessibilityShortcutFragment extends LeanbackPreferenceFragment {
    private static final String KEY_ENABLE = "enable";
    private static final String KEY_SERVICE = "service";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.accessibility_shortcut, null);

        final TwoStatePreference enablePref = (TwoStatePreference) findPreference(KEY_ENABLE);
        enablePref.setOnPreferenceChangeListener((preference, newValue) -> {
            setAccessibilityShortcutEnabled((Boolean) newValue);
            return true;
        });

        boolean shortcutEnabled = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SHORTCUT_ENABLED, 1) == 1;

        enablePref.setChecked(shortcutEnabled);
    }

    @Override
    public void onResume() {
        super.onResume();
        final Preference servicePref = findPreference(KEY_SERVICE);
        final List<AccessibilityServiceInfo> installedServices = getContext()
                .getSystemService(AccessibilityManager.class)
                .getInstalledAccessibilityServiceList();
        final PackageManager packageManager = getContext().getPackageManager();
        final String currentService = getCurrentService(getContext());
        for (AccessibilityServiceInfo service : installedServices) {
            final String serviceString = service.getComponentName().flattenToString();
            if (TextUtils.equals(currentService, serviceString)) {
                servicePref.setSummary(service.getResolveInfo().loadLabel(packageManager));
            }
        }
    }

    private void setAccessibilityShortcutEnabled(boolean enabled) {
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SHORTCUT_ENABLED, enabled ? 1 : 0);
        final Preference servicePref = findPreference(KEY_SERVICE);
        servicePref.setEnabled(enabled);
    }

    static String getCurrentService(Context context) {
        String shortcutServiceString = AccessibilityUtils
                .getShortcutTargetServiceComponentNameString(context, UserHandle.myUserId());
        if (shortcutServiceString != null) {
            ComponentName shortcutName = ComponentName.unflattenFromString(shortcutServiceString);
            if (shortcutName != null) {
                return shortcutName.flattenToString();
            }
        }
        return null;
    }
}
