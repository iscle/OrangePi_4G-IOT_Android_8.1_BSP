/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.system.development;

import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.android.tv.settings.R;

import java.util.List;

@Keep
public class InactiveApps extends LeanbackPreferenceFragment implements
        Preference.OnPreferenceClickListener {

    private UsageStatsManager mUsageStats;

    @Override
    public void onCreate(Bundle icicle) {
        mUsageStats = getActivity().getSystemService(UsageStatsManager.class);
        super.onCreate(icicle);
    }

    @Override
    public void onResume() {
        super.onResume();
        init();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context themedContext = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager()
                .createPreferenceScreen(themedContext);
        screen.setTitle(R.string.inactive_apps_title);
        setPreferenceScreen(screen);
    }

    private void init() {
        final Context themedContext = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        screen.setOrderingAsAdded(false);
        final PackageManager pm = getActivity().getPackageManager();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);
        for (ResolveInfo app : apps) {
            String packageName = app.activityInfo.applicationInfo.packageName;
            Preference p = new Preference(themedContext);
            p.setTitle(app.loadLabel(pm));
            p.setIcon(app.loadIcon(pm));
            p.setKey(packageName);
            updateSummary(p);
            p.setOnPreferenceClickListener(this);

            screen.addPreference(p);
        }
    }

    private void updateSummary(Preference p) {
        boolean inactive = mUsageStats.isAppInactive(p.getKey());
        p.setSummary(inactive
                ? R.string.inactive_app_inactive_summary
                : R.string.inactive_app_active_summary);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String packageName = preference.getKey();
        mUsageStats.setAppInactive(packageName, !mUsageStats.isAppInactive(packageName));
        updateSummary(preference);
        return false;
    }

}
