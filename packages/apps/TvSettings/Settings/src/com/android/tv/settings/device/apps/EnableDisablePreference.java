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

package com.android.tv.settings.device.apps;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.util.Log;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class EnableDisablePreference extends AppActionPreference {
    private static final String TAG = "EnableDisablePreference";

    private static Signature sSystemSignature;

    private final PackageManager mPackageManager;

    public EnableDisablePreference(Context context,
            ApplicationsState.AppEntry entry) {
        super(context, entry);
        mPackageManager = context.getPackageManager();
        refresh();
    }

    public void refresh() {
        if (!UninstallPreference.canUninstall(mEntry) && canDisable()) {
            setVisible(true);
            if (mEntry.info.enabled) {
                setTitle(R.string.device_apps_app_management_disable);
                ConfirmationFragment.prepareArgs(getExtras(), mEntry.info.packageName, false);
            } else {
                setTitle(R.string.device_apps_app_management_enable);
                ConfirmationFragment.prepareArgs(getExtras(), mEntry.info.packageName, true);
            }
        } else {
            setVisible(false);
        }
    }

    private boolean canDisable() {
        final HashSet<String> homePackages = getHomePackages();
        PackageInfo packageInfo;
        try {
            packageInfo = mPackageManager.getPackageInfo(mEntry.info.packageName,
                    PackageManager.GET_DISABLED_COMPONENTS |
                            PackageManager.GET_UNINSTALLED_PACKAGES |
                            PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return !(homePackages.contains(mEntry.info.packageName) ||
                isSystemPackage(mPackageManager, packageInfo));
    }

    private HashSet<String> getHomePackages() {
        HashSet<String> homePackages = new HashSet<>();
        // Get list of "home" apps and trace through any meta-data references
        List<ResolveInfo> homeActivities = new ArrayList<>();
        mPackageManager.getHomeActivities(homeActivities);
        for (ResolveInfo ri : homeActivities) {
            final String activityPkg = ri.activityInfo.packageName;
            homePackages.add(activityPkg);
            // Also make sure to include anything proxying for the home app
            final Bundle metadata = ri.activityInfo.metaData;
            if (metadata != null) {
                final String metaPkg = metadata.getString(ActivityManager.META_HOME_ALTERNATE);
                if (signaturesMatch(mPackageManager, metaPkg, activityPkg)) {
                    homePackages.add(metaPkg);
                }
            }
        }
        return homePackages;
    }

    private static boolean signaturesMatch(PackageManager pm, String pkg1, String pkg2) {
        if (pkg1 != null && pkg2 != null) {
            try {
                final int match = pm.checkSignatures(pkg1, pkg2);
                if (match >= PackageManager.SIGNATURE_MATCH) {
                    return true;
                }
            } catch (Exception e) {
                // e.g. named alternate package not found during lookup;
                // this is an expected case sometimes
            }
        }
        return false;
    }

    /**
     * Determine whether a package is a "system package", in which case certain things (like
     * disabling notifications or disabling the package altogether) should be disallowed.
     */
    private static boolean isSystemPackage(PackageManager pm, PackageInfo pkg) {
        if (sSystemSignature == null) {
            try {
                final PackageInfo sys = pm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
                sSystemSignature = getFirstSignature(sys);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Could not look up system signature", e);
            }
        }
        return sSystemSignature != null && sSystemSignature.equals(getFirstSignature(pkg));
    }

    private static Signature getFirstSignature(PackageInfo pkg) {
        if (pkg != null && pkg.signatures != null && pkg.signatures.length > 0) {
            return pkg.signatures[0];
        }
        return null;
    }

    @Override
    public String getFragment() {
        return ConfirmationFragment.class.getName();
    }

    public static class ConfirmationFragment extends AppActionPreference.ConfirmationFragment {
        private static final String ARG_PACKAGE_NAME = "packageName";
        private static final String ARG_ENABLE = "enable";

        private static void prepareArgs(@NonNull Bundle args, String packageName, boolean enable) {
            args.putString(ARG_PACKAGE_NAME, packageName);
            args.putBoolean(ARG_ENABLE, enable);
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            final AppManagementFragment fragment = (AppManagementFragment) getTargetFragment();
            final Boolean enable = getArguments().getBoolean(ARG_ENABLE);
            return new GuidanceStylist.Guidance(
                    getString(enable ? R.string.device_apps_app_management_enable :
                            R.string.device_apps_app_management_disable),
                    getString(enable ? R.string.device_apps_app_management_enable_desc :
                            R.string.device_apps_app_management_disable_desc),
                    fragment.getAppName(),
                    fragment.getAppIcon());
        }

        @Override
        public void onOk() {
            getActivity().getPackageManager().setApplicationEnabledSetting(
                    getArguments().getString(ARG_PACKAGE_NAME),
                    getArguments().getBoolean(ARG_ENABLE) ?
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT :
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    0);
        }
    }
}
