/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.format.DateUtils;
import com.android.storagemanager.R;
import com.android.storagemanager.deletionhelper.AppsAsyncLoader.PackageInfo;
import java.util.concurrent.TimeUnit;

/**
 * Preference item for an app with a switch to signify if it should be uninstalled. This shows the
 * name and icon of the app along with the days since its last use.
 */
public class AppDeletionPreference extends NestedDeletionPreference {
    private PackageInfo mApp;
    private Context mContext;

    public AppDeletionPreference(Context context, PackageInfo item) {
        super(context);
        mApp = item;
        mContext = context;
        setIcon(item.icon);
        setTitle(item.label);
        setItemSize(mApp.size);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
    }

    /**
     * Returns the package name for the app that these preference represents.
     */
    public String getPackageName() {
        return mApp.packageName;
    }

    public void updateSummary() {
        if (mApp == null) return;
        if (mApp.daysSinceLastUse == AppsAsyncLoader.NEVER_USED) {
            setSummary(mContext.getString(R.string.deletion_helper_app_summary_never_used));
        } else if (mApp.daysSinceLastUse == AppsAsyncLoader.UNKNOWN_LAST_USE) {
            setSummary(mContext.getString(R.string.deletion_helper_app_summary_unknown_used));
        } else {
            // Use the formatter for the "yesterday" and "today" cases.
            if (mApp.daysSinceLastUse <= 1) {
                final long now = System.currentTimeMillis();
                final long lastUse = now - TimeUnit.DAYS.toMillis(mApp.daysSinceLastUse);
                setSummary(
                        DateUtils.getRelativeTimeSpanString(
                                lastUse, now, DateUtils.DAY_IN_MILLIS, 0));
            } else {
                setSummary(
                        mContext.getString(
                                R.string.deletion_helper_app_summary, mApp.daysSinceLastUse));
            }
        }
    }

}
