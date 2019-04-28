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

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.format.DateUtils;
import android.util.Log;
import com.android.storagemanager.deletionhelper.AppStateBaseBridge.Callback;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.ApplicationsState.AppFilter;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Connects data from the UsageStatsManager to the ApplicationsState.
 */
public class AppStateUsageStatsBridge extends AppStateBaseBridge {
    private static final String TAG = "AppStateUsageStatsBridge";

    private static final String DEBUG_APP_UNUSED_OVERRIDE = "debug.asm.app_unused_limit";
    public static final long NEVER_USED = Long.MAX_VALUE;
    public static final long UNKNOWN_LAST_USE = -1;
    public static final long UNUSED_DAYS_DELETION_THRESHOLD = 90;
    private static final long DAYS_IN_A_TYPICAL_YEAR = 365;
    public static final long MIN_DELETION_THRESHOLD = Long.MIN_VALUE;
    public static final int NORMAL_THRESHOLD = 0;
    public static final int NO_THRESHOLD = 1;

    private UsageStatsManager mUsageStatsManager;
    private PackageManager mPm;
    // This clock is used to provide the time. By default, it uses the system clock, but can be
    // replaced for test purposes.
    protected Clock mClock;

    public AppStateUsageStatsBridge(Context context, ApplicationsState appState,
            Callback callback) {
        super(appState, callback);
        mUsageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        mPm = context.getPackageManager();
        mClock = new Clock();
    }

    @Override
    protected void loadAllExtraInfo() {
        ArrayList<AppEntry> apps = mAppSession.getAllApps();
        if (apps == null) return;

        final Map<String, UsageStats> map = getAggregatedUsageStats();
        for (AppEntry entry : apps) {
            UsageStats usageStats = map.get(entry.info.packageName);
            entry.extraInfo = new UsageStatsState(getDaysSinceLastUse(usageStats),
                    getDaysSinceInstalled(entry.info.packageName),
                    UserHandle.getUserId(entry.info.uid));
        }
    }

    @Override
    protected void updateExtraInfo(AppEntry app, String pkg, int uid) {
        Map<String, UsageStats> map = getAggregatedUsageStats();
        UsageStats usageStats = map.get(app.info.packageName);
        app.extraInfo = new UsageStatsState(getDaysSinceLastUse(usageStats),
                getDaysSinceInstalled(app.info.packageName),
                UserHandle.getUserId(app.info.uid));
    }

    private long getDaysSinceLastUse(UsageStats stats) {
        if (stats == null) {
            return NEVER_USED;
        }
        long lastUsed = stats.getLastTimeUsed();
        // Sometimes, a usage is recorded without a time and we don't know when the use was.
        if (lastUsed <= 0) {
            return UNKNOWN_LAST_USE;
        }

        // Theoretically, this should be impossible, but UsageStatsService, uh, finds a way.
        long days = (TimeUnit.MILLISECONDS.toDays(mClock.getCurrentTime() - lastUsed));
        if (days > DAYS_IN_A_TYPICAL_YEAR) {
            return NEVER_USED;
        }
        return days;
    }

    private long getDaysSinceInstalled(String packageName) {
        PackageInfo pi = null;
        try {
            pi = mPm.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, packageName + " was not found.");
        }

        if (pi == null) {
            return UNKNOWN_LAST_USE;
        }
        return (TimeUnit.MILLISECONDS.toDays(mClock.getCurrentTime() - pi.firstInstallTime));
    }

    private Map<String, UsageStats> getAggregatedUsageStats() {
        long now = mClock.getCurrentTime();
        long startTime = now - DateUtils.YEAR_IN_MILLIS;
        return mUsageStatsManager.queryAndAggregateUsageStats(startTime, now);
    }

    private static boolean isBundled(AppEntry info) {
        return (info.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isPersistentProcess(AppEntry info) {
        return (info.info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
    }

    private static boolean isExtraInfoValid(Object extraInfo, long unusedDaysThreshold) {
        if (extraInfo == null || !(extraInfo instanceof UsageStatsState)) {
            return false;
        }

        UsageStatsState state = (UsageStatsState) extraInfo;

        // If we are missing information, let's be conservative and not show it.
        if (state.daysSinceFirstInstall == UNKNOWN_LAST_USE
                || state.daysSinceLastUse == UNKNOWN_LAST_USE) {
            Log.w(TAG, "Missing information. Skipping app");
            return false;
        }

        // If the app has never been used, daysSinceLastUse is Long.MAX_VALUE, so the first
        // install is always the most recent use.
        long mostRecentUse = Math.min(state.daysSinceFirstInstall, state.daysSinceLastUse);
        return mostRecentUse >= unusedDaysThreshold;
    }

    public static final AppFilter FILTER_NO_THRESHOLD =
            new AppFilter() {
                @Override
                public void init() {}

                @Override
                public boolean filterApp(AppEntry info) {
                    if (info == null) {
                        return false;
                    }
                    return isExtraInfoValid(info.extraInfo, MIN_DELETION_THRESHOLD)
                            && !isBundled(info)
                            && !isPersistentProcess(info);
                }
            };

    /**
     * Filters only non-system apps which haven't been used in the last 60 days. If an app's last
     * usage is unknown, it is skipped.
     */
    public static final AppFilter FILTER_USAGE_STATS =
            new AppFilter() {
                private long mUnusedDaysThreshold;

                @Override
                public void init() {
                    mUnusedDaysThreshold =
                            SystemProperties.getLong(
                                    DEBUG_APP_UNUSED_OVERRIDE, UNUSED_DAYS_DELETION_THRESHOLD);
                }

                @Override
                public boolean filterApp(AppEntry info) {
                    if (info == null) {
                        return false;
                    }
                    return isExtraInfoValid(info.extraInfo, mUnusedDaysThreshold)
                            && !isBundled(info)
                            && !isPersistentProcess(info);
                }
            };

    /**
     * UsageStatsState contains the days since the last use and first install of a given app.
     */
    public static class UsageStatsState {
        public long daysSinceLastUse;
        public long daysSinceFirstInstall;
        public int userId;

        public UsageStatsState(long daysSinceLastUse, long daysSinceFirstInstall, int userId) {
            this.daysSinceLastUse = daysSinceLastUse;
            this.daysSinceFirstInstall = daysSinceFirstInstall;
            this.userId = userId;
        }
    }

    /**
     * Clock provides the current time.
     */
    static class Clock {
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }
    }
}
