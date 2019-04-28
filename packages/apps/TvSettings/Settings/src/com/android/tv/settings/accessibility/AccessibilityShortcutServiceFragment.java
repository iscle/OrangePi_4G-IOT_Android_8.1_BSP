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
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Keep;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v17.preference.LeanbackSettingsFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;

import java.util.List;

/**
 * Fragment imitating a single-selection list for picking the accessibility shortcut service
 */
@Keep
public class AccessibilityShortcutServiceFragment extends LeanbackPreferenceFragment implements
        AccessibilityServiceConfirmationFragment.OnAccessibilityServiceConfirmedListener {
    private static final String SERVICE_RADIO_GROUP = "service_group";

    private final Preference.OnPreferenceChangeListener mPreferenceChangeListener =
            (preference, newValue) -> {
                final String newCompString = preference.getKey();
                final String currentService =
                        AccessibilityShortcutFragment.getCurrentService(getContext());
                if ((Boolean) newValue && !TextUtils.equals(newCompString, currentService)) {
                    final ComponentName cn = ComponentName.unflattenFromString(newCompString);
                    final CharSequence label = preference.getTitle();
                    final Fragment confirmFragment =
                            AccessibilityServiceConfirmationFragment.newInstance(cn, label, true);
                    confirmFragment.setTargetFragment(AccessibilityShortcutServiceFragment.this, 0);

                    final Fragment settingsFragment = getCallbackFragment();
                    if (settingsFragment instanceof LeanbackSettingsFragment) {
                        ((LeanbackSettingsFragment) settingsFragment)
                                .startImmersiveFragment(confirmFragment);
                    } else {
                        throw new IllegalStateException("Not attached to settings fragment??");
                    }
                }
                return false;
            };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.accessibility_shortcut_service, null);

        final PreferenceScreen screen = getPreferenceScreen();
        final Context themedContext = getPreferenceManager().getContext();

        final List<AccessibilityServiceInfo> installedServices = getContext()
                .getSystemService(AccessibilityManager.class)
                .getInstalledAccessibilityServiceList();
        final PackageManager packageManager = getContext().getPackageManager();
        final String currentService = AccessibilityShortcutFragment.getCurrentService(getContext());
        for (AccessibilityServiceInfo service : installedServices) {
            final RadioPreference preference = new RadioPreference(themedContext);
            preference.setPersistent(false);
            preference.setRadioGroup(SERVICE_RADIO_GROUP);
            preference.setOnPreferenceChangeListener(mPreferenceChangeListener);

            final String serviceString = service.getComponentName().flattenToString();
            if (TextUtils.equals(currentService, serviceString)) {
                preference.setChecked(true);
            }
            preference.setKey(serviceString);
            preference.setTitle(service.getResolveInfo().loadLabel(packageManager));

            screen.addPreference(preference);
        }
    }

    @Override
    public void onAccessibilityServiceConfirmed(ComponentName componentName, boolean enabling) {
        final String componentString = componentName.flattenToString();
        Settings.Secure.putString(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,
                componentString);
        getFragmentManager().popBackStack();
    }
}
