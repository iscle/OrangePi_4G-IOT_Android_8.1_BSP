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
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Keep;
import android.support.v14.preference.SwitchPreference;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.android.settingslib.accessibility.AccessibilityUtils;
import com.android.tv.settings.R;

import java.util.List;
import java.util.Set;

/**
 * Fragment for Accessibility settings
 */
@Keep
public class AccessibilityFragment extends LeanbackPreferenceFragment {
    private static final String TOGGLE_HIGH_TEXT_CONTRAST_KEY = "toggle_high_text_contrast";
    private static final String ACCESSIBILITY_SERVICES_KEY = "system_accessibility_services";

    private PreferenceGroup mServicesPref;

    /**
     * Create a new instance of the fragment
     * @return New fragment instance
     */
    public static AccessibilityFragment newInstance() {
        return new AccessibilityFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mServicesPref != null) {
            refreshServices(mServicesPref);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.accessibility, null);

        final TwoStatePreference highContrastPreference =
                (TwoStatePreference) findPreference(TOGGLE_HIGH_TEXT_CONTRAST_KEY);
        highContrastPreference.setChecked(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED, 0) == 1);

        mServicesPref = (PreferenceGroup) findPreference(ACCESSIBILITY_SERVICES_KEY);
        refreshServices(mServicesPref);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), TOGGLE_HIGH_TEXT_CONTRAST_KEY)) {
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED,
                    (((SwitchPreference) preference).isChecked() ? 1 : 0));
            return true;
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }

    private void refreshServices(PreferenceGroup group) {
        final List<AccessibilityServiceInfo> installedServiceInfos =
                getActivity().getSystemService(AccessibilityManager.class)
                        .getInstalledAccessibilityServiceList();
        final Set<ComponentName> enabledServices =
                AccessibilityUtils.getEnabledServicesFromSettings(getActivity());
        final boolean accessibilityEnabled = Settings.Secure.getInt(
                getActivity().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;

        for (final AccessibilityServiceInfo accInfo : installedServiceInfos) {
            final ServiceInfo serviceInfo = accInfo.getResolveInfo().serviceInfo;
            final ComponentName componentName = new ComponentName(serviceInfo.packageName,
                    serviceInfo.name);

            final boolean serviceEnabled = accessibilityEnabled
                    && enabledServices.contains(componentName);

            final String title = accInfo.getResolveInfo()
                    .loadLabel(getActivity().getPackageManager()).toString();

            final String key = "ServicePref:" + componentName.flattenToString();
            Preference servicePref = findPreference(key);
            if (servicePref == null) {
                servicePref = new Preference(group.getContext());
                servicePref.setKey(key);
            }
            servicePref.setTitle(title);
            servicePref.setSummary(serviceEnabled ? R.string.settings_on : R.string.settings_off);
            servicePref.setFragment(AccessibilityServiceFragment.class.getName());
            AccessibilityServiceFragment.prepareArgs(servicePref.getExtras(),
                    serviceInfo.packageName,
                    serviceInfo.name,
                    accInfo.getSettingsActivityName(),
                    title);
            group.addPreference(servicePref);
        }
    }

}
