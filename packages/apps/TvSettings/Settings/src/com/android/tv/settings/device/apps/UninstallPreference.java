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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

public class UninstallPreference extends AppActionPreference {

    public UninstallPreference(Context context,
            ApplicationsState.AppEntry entry) {
        super(context, entry);
        refresh();
    }

    public void refresh() {
        if (canUninstall()) {
            setVisible(true);
            setTitle(R.string.device_apps_app_management_uninstall);
        } else if (canUninstallUpdates()) {
            setVisible(true);
            setTitle(R.string.device_apps_app_management_uninstall_updates);
        } else {
            setVisible(false);
        }
    }

    public boolean canUninstall() {
        return canUninstall(mEntry);
    }

    public static boolean canUninstall(ApplicationsState.AppEntry entry) {
        return (entry.info.flags &
                (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP|ApplicationInfo.FLAG_SYSTEM)) == 0;
    }

    public boolean canUninstallUpdates() {
        return (mEntry.info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    @Override
    public Intent getIntent() {
        final Uri packageURI = Uri.parse("package:" + mEntry.info.packageName);
        final Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI);
        uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true);
        uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        return uninstallIntent;
    }
}
