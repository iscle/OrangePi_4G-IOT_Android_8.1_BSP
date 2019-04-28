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

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.format.Formatter;
import android.util.AttributeSet;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.storagemanager.R;
import com.android.storagemanager.utils.PreferenceListCache;
import java.util.List;

/**
 * AppDeletionPreferenceGroup is a collapsible checkbox preference group which contains many
 * apps to be cleared in the Deletion Helper.
 */
public class AppDeletionPreferenceGroup extends CollapsibleCheckboxPreferenceGroup
        implements AppDeletionType.AppListener, Preference.OnPreferenceChangeListener {
    private static final int ORDER_OFFSET = 100;
    private AppDeletionType mBackend;

    @VisibleForTesting PreferenceScreen mScreen;

    public AppDeletionPreferenceGroup(Context context) {
        this(context, null);
    }

    public AppDeletionPreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnPreferenceChangeListener(this);
        updateText();
    }

    @Override
    public void onAppRebuild(List<AppsAsyncLoader.PackageInfo> apps) {
        int appCount = apps.size();
        int currentUserId = getContext().getUserId();
        PreferenceListCache cache = new PreferenceListCache(this);
        for (int i = 0; i < appCount; i++) {
            AppsAsyncLoader.PackageInfo app = apps.get(i);

            if (app.userId != currentUserId) {
                continue;
            }

            final String packageName = app.packageName;
            AppDeletionPreference preference =
                    (AppDeletionPreference) cache.getCachedPreference(packageName);
            if (preference == null) {
                preference = new AppDeletionPreference(getContext(), app);
                preference.setKey(packageName);
                preference.setOnPreferenceChangeListener(this);
            }
            addThresholdDependentPreference(preference, isNoThreshold());
            preference.setChecked(mBackend.isChecked(packageName));
            preference.setOrder(i + ORDER_OFFSET);
            preference.updateSummary();
        }
        cache.removeCachedPrefs();
        updateText();
    }

    private void addThresholdDependentPreference(
            AppDeletionPreference preference, boolean isThresholded) {
        if (isNoThreshold()) {
            addPreferenceToScreen(preference);
        } else {
            addPreference(preference);
        }
    }

    private boolean isNoThreshold() {
        return mBackend.getDeletionThreshold() == 0;
    }

    @VisibleForTesting
    void addPreferenceToScreen(AppDeletionPreference preference) {
        if (mScreen == null) {
            mScreen = getPreferenceManager().getPreferenceScreen();
        }
        mScreen.addPreference(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean isChecked = (boolean) newValue;

        // If we have no AppDeletionType, we have no apps to toggle.
        if (mBackend == null) {
            return true;
        }

        if (preference == this) {
            for (int i = 0; i < getPreferenceCount(); i++) {
                AppDeletionPreference p = (AppDeletionPreference) getPreference(i);
                p.setOnPreferenceChangeListener(null);
                p.setChecked(isChecked);
                mBackend.setChecked(p.getPackageName(), isChecked);
                p.setOnPreferenceChangeListener(this);
            }
            updateText();
            MetricsLogger.action(getContext(), MetricsEvent.ACTION_DELETION_SELECTION_ALL_APPS,
                    isChecked);
            return true;
        }

        // If a single preference changed, we need to toggle just itself.
        AppDeletionPreference p = (AppDeletionPreference) preference;
        mBackend.setChecked(p.getPackageName(), isChecked);
        logAppToggle(isChecked, p.getPackageName());
        updateText();
        return true;
    }

    @Override
    public void onClick() {
        super.onClick();
        MetricsLogger.action(
                getContext(), MetricsEvent.ACTION_DELETION_APPS_COLLAPSED, isCollapsed());
    }

    /**
     * Initializes the PreferenceGroup with a source of apps to list.
     *
     * @param type The AppDeletionType which provides the app list.
     */
    public void setDeletionType(AppDeletionType type) {
        mBackend = type;
    }

    private void updateText() {
        long freeableBytes = 0;
        long deletionThreshold = AppsAsyncLoader.UNUSED_DAYS_DELETION_THRESHOLD;
        if (mBackend != null) {
            freeableBytes =
                    mBackend.getTotalAppsFreeableSpace(DeletionHelperSettings.COUNT_UNCHECKED);
            deletionThreshold = mBackend.getDeletionThreshold();
            switchSpinnerToCheckboxOrDisablePreference(freeableBytes, mBackend.getLoadingStatus());
        }
        Context app = getContext();
        setTitle(app.getString(R.string.deletion_helper_apps_group_title));
        setSummary(
                app.getString(
                        R.string.deletion_helper_apps_group_summary,
                        Formatter.formatFileSize(app, freeableBytes),
                        deletionThreshold));
    }

    private void logAppToggle(boolean checked, String packageName) {
        if (checked) {
            MetricsLogger.action(
                    getContext(), MetricsEvent.ACTION_DELETION_SELECTION_APP_ON, packageName);
        } else {
            MetricsLogger.action(getContext(), MetricsEvent.ACTION_DELETION_SELECTION_APP_OFF,
                    packageName);
        }
    }
}
