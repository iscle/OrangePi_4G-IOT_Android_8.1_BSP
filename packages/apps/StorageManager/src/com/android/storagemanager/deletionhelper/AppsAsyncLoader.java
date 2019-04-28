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

package com.android.storagemanager.deletionhelper;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.support.annotation.VisibleForTesting;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import com.android.settingslib.applications.PackageManagerWrapper;
import com.android.settingslib.applications.StorageStatsSource;
import com.android.settingslib.applications.StorageStatsSource.AppStorageStats;
import com.android.storagemanager.deletionhelper.AppsAsyncLoader.PackageInfo;
import com.android.storagemanager.utils.AsyncLoader;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * AppsAsyncLoader is a Loader which loads app storage information and categories it by the app's
 * specified categorization.
 */
public class AppsAsyncLoader extends AsyncLoader<List<PackageInfo>> {
    private static final String TAG = "AppsAsyncLoader";

    public static final long NEVER_USED = Long.MAX_VALUE;
    public static final long UNKNOWN_LAST_USE = -1;
    public static final long UNUSED_DAYS_DELETION_THRESHOLD = 90;
    public static final long MIN_DELETION_THRESHOLD = Long.MIN_VALUE;
    public static final int NORMAL_THRESHOLD = 0;
    public static final int SIZE_UNKNOWN = -1;
    public static final int SIZE_INVALID = -2;
    public static final int NO_THRESHOLD = 1;
    private static final String DEBUG_APP_UNUSED_OVERRIDE = "debug.asm.app_unused_limit";
    private static final long DAYS_IN_A_TYPICAL_YEAR = 365;

    protected Clock mClock;
    protected AppsAsyncLoader.AppFilter mFilter;
    private int mUserId;
    private String mUuid;
    private StorageStatsSource mStatsManager;
    private PackageManagerWrapper mPackageManager;

    private UsageStatsManager mUsageStatsManager;

    private AppsAsyncLoader(
            Context context,
            int userId,
            String uuid,
            StorageStatsSource source,
            PackageManagerWrapper pm,
            UsageStatsManager um,
            AppsAsyncLoader.AppFilter filter) {
        super(context);
        mUserId = userId;
        mUuid = uuid;
        mStatsManager = source;
        mPackageManager = pm;
        mUsageStatsManager = um;
        mClock = new Clock();
        mFilter = filter;
    }

    @Override
    public List<PackageInfo> loadInBackground() {
        return loadApps();
    }

    private List<PackageInfo> loadApps() {
        ArraySet<Integer> seenUid = new ArraySet<>(); // some apps share a uid

        long now = mClock.getCurrentTime();
        long startTime = now - DateUtils.YEAR_IN_MILLIS;
        final Map<String, UsageStats> map =
                mUsageStatsManager.queryAndAggregateUsageStats(startTime, now);
        final Map<String, UsageStats> alternateMap =
                getLatestUsageStatsByPackageName(startTime, now);

        List<ApplicationInfo> applicationInfos =
                mPackageManager.getInstalledApplicationsAsUser(0, mUserId);
        List<PackageInfo> stats = new ArrayList<>();
        int size = applicationInfos.size();
        mFilter.init();
        for (int i = 0; i < size; i++) {
            ApplicationInfo app = applicationInfos.get(i);
            if (seenUid.contains(app.uid)) {
                continue;
            }

            UsageStats usageStats = map.get(app.packageName);
            UsageStats alternateUsageStats = alternateMap.get(app.packageName);

            final AppStorageStats appSpace;
            try {
                appSpace = mStatsManager.getStatsForUid(app.volumeUuid, app.uid);
            } catch (IOException e) {
                Log.w(TAG, e);
                continue;
            }

            PackageInfo extraInfo =
                    new PackageInfo.Builder()
                            .setDaysSinceLastUse(
                                    getDaysSinceLastUse(
                                            getGreaterUsageStats(
                                                    app.packageName,
                                                    usageStats,
                                                    alternateUsageStats)))
                            .setDaysSinceFirstInstall(getDaysSinceInstalled(app.packageName))
                            .setUserId(UserHandle.getUserId(app.uid))
                            .setPackageName(app.packageName)
                            .setSize(appSpace.getTotalBytes())
                            .setFlags(app.flags)
                            .setIcon(mPackageManager.getUserBadgedIcon(app))
                            .setLabel(mPackageManager.loadLabel(app))
                            .build();
            seenUid.add(app.uid);
            if (mFilter.filterApp(extraInfo) && !isDefaultLauncher(mPackageManager, extraInfo)) {
                stats.add(extraInfo);
            }
        }
        stats.sort(PACKAGE_INFO_COMPARATOR);
        return stats;
    }

    @VisibleForTesting
    UsageStats getGreaterUsageStats(String packageName, UsageStats primary, UsageStats alternate) {
        long primaryLastUsed = primary != null ? primary.getLastTimeUsed() : 0;
        long alternateLastUsed = alternate != null ? alternate.getLastTimeUsed() : 0;

        if (primaryLastUsed != alternateLastUsed) {
            Log.w(
                    TAG,
                    new StringBuilder("Usage stats mismatch for ")
                            .append(packageName)
                            .append(" ")
                            .append(primaryLastUsed)
                            .append(" ")
                            .append(alternateLastUsed)
                            .toString());
        }

        return (primaryLastUsed > alternateLastUsed) ? primary : alternate;
    }

    private Map<String, UsageStats> getLatestUsageStatsByPackageName(long startTime, long endTime) {
        List<UsageStats> usageStats =
                mUsageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_YEARLY, startTime, endTime);
        Map<String, List<UsageStats>> groupedByPackageName =
                usageStats.stream().collect(Collectors.groupingBy(UsageStats::getPackageName));

        ArrayMap<String, UsageStats> latestStatsByPackageName = new ArrayMap<>();
        groupedByPackageName
                .entrySet()
                .stream()
                .forEach(
                        // Flattens the list of UsageStats to only have the latest by
                        // getLastTimeUsed, retaining the package name as the key.
                        (Map.Entry<String, List<UsageStats>> item) -> {
                            latestStatsByPackageName.put(
                                    item.getKey(),
                                    Collections.max(
                                            item.getValue(),
                                            (UsageStats o1, UsageStats o2) ->
                                                    Long.compare(
                                                            o1.getLastTimeUsed(),
                                                            o2.getLastTimeUsed())));
                        });

        return latestStatsByPackageName;
    }

    @Override
    protected void onDiscardResult(List<PackageInfo> result) {}

    private static boolean isDefaultLauncher(
            PackageManagerWrapper packageManager, PackageInfo info) {
        if (packageManager == null) {
            return false;
        }

        final List<ResolveInfo> homeActivities = new ArrayList<>();
        ComponentName defaultActivity = packageManager.getHomeActivities(homeActivities);
        if (defaultActivity != null) {
            String packageName = defaultActivity.getPackageName();
            return packageName == null
                    ? false
                    : defaultActivity.getPackageName().equals(info.packageName);
        }

        return false;
    }

    public static class Builder {
        private Context mContext;
        private int mUid;
        private String mUuid;
        private StorageStatsSource mStorageStatsSource;
        private PackageManagerWrapper mPackageManager;
        private UsageStatsManager mUsageStatsManager;
        private AppsAsyncLoader.AppFilter mFilter;

        public Builder(Context context) {
            mContext = context;
        }

        public Builder setUid(int uid) {
            mUid = uid;
            return this;
        }

        public Builder setUuid(String uuid) {
            this.mUuid = uuid;
            return this;
        }

        public Builder setStorageStatsSource(StorageStatsSource storageStatsSource) {
            this.mStorageStatsSource = storageStatsSource;
            return this;
        }

        public Builder setPackageManager(PackageManagerWrapper packageManager) {
            this.mPackageManager = packageManager;
            return this;
        }

        public Builder setUsageStatsManager(UsageStatsManager usageStatsManager) {
            this.mUsageStatsManager = usageStatsManager;
            return this;
        }

        public Builder setFilter(AppFilter filter) {
            this.mFilter = filter;
            return this;
        }

        public AppsAsyncLoader build() {
            return new AppsAsyncLoader(
                    mContext,
                    mUid,
                    mUuid,
                    mStorageStatsSource,
                    mPackageManager,
                    mUsageStatsManager,
                    mFilter);
        }
    }

    /**
     * Comparator that checks PackageInfo to see if it describes the same app based on the name and
     * user it belongs to. This comparator does NOT fulfill the standard java equality contract
     * because it only checks a few fields.
     */
    public static final Comparator<PackageInfo> PACKAGE_INFO_COMPARATOR =
            new Comparator<PackageInfo>() {
                private final Collator sCollator = Collator.getInstance();

                @Override
                public int compare(PackageInfo object1, PackageInfo object2) {
                    if (object1.size < object2.size) return 1;
                    if (object1.size > object2.size) return -1;
                    int compareResult = sCollator.compare(object1.label, object2.label);
                    if (compareResult != 0) {
                        return compareResult;
                    }
                    compareResult = sCollator.compare(object1.packageName, object2.packageName);
                    if (compareResult != 0) {
                        return compareResult;
                    }
                    return object1.userId - object2.userId;
                }
            };

    public static final AppFilter FILTER_NO_THRESHOLD =
            new AppFilter() {
                @Override
                public void init() {}

                @Override
                public boolean filterApp(PackageInfo info) {
                    if (info == null) {
                        return false;
                    }
                    return !isBundled(info)
                            && !isPersistentProcess(info)
                            && isExtraInfoValid(info, MIN_DELETION_THRESHOLD);
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
                public boolean filterApp(PackageInfo info) {
                    if (info == null) {
                        return false;
                    }
                    return !isBundled(info)
                            && !isPersistentProcess(info)
                            && isExtraInfoValid(info, mUnusedDaysThreshold);
                }
            };

    private static boolean isBundled(PackageInfo info) {
        return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isPersistentProcess(PackageInfo info) {
        return (info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
    }

    private static boolean isExtraInfoValid(Object extraInfo, long unusedDaysThreshold) {
        if (extraInfo == null || !(extraInfo instanceof PackageInfo)) {
            return false;
        }

        PackageInfo state = (PackageInfo) extraInfo;

        // If we are missing information, let's be conservative and not show it.
        if (state.daysSinceFirstInstall == UNKNOWN_LAST_USE
                || state.daysSinceLastUse == UNKNOWN_LAST_USE) {
            Log.w(TAG, "Missing information. Skipping app");
            return false;
        }

        // If the app has never been used, daysSinceLastUse is Long.MAX_VALUE, so the first
        // install is always the most recent use.
        long mostRecentUse = Math.min(state.daysSinceFirstInstall, state.daysSinceLastUse);
        if (mostRecentUse >= unusedDaysThreshold) {
            Log.i(TAG, "Accepting " + state.packageName + " with a minimum of " + mostRecentUse);
        }
        return mostRecentUse >= unusedDaysThreshold;
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
        android.content.pm.PackageInfo pi = null;
        try {
            pi = mPackageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, packageName + " was not found.");
        }

        if (pi == null) {
            return UNKNOWN_LAST_USE;
        }
        return (TimeUnit.MILLISECONDS.toDays(mClock.getCurrentTime() - pi.firstInstallTime));
    }

    public interface AppFilter {

        /**
         * Note: This method must be manually called before using an app filter. It does not get
         * called on construction.
         */
        void init();

        default void init(Context context) {
            init();
        }

        /**
         * Returns true or false depending on whether the app should be filtered or not.
         *
         * @param info the PackageInfo for the app in question.
         * @return true if the app should be included, false if it should be filtered out.
         */
        boolean filterApp(PackageInfo info);
    }

    /** PackageInfo contains all the information needed to present apps for deletion to users. */
    public static class PackageInfo {

        public long daysSinceLastUse;
        public long daysSinceFirstInstall;
        public int userId;
        public String packageName;
        public long size;
        public Drawable icon;
        public CharSequence label;
        /**
         * Flags from {@link ApplicationInfo} that set whether the app is a regular app or something
         * special like a system app.
         */
        public int flags;

        private PackageInfo(
                long daysSinceLastUse,
                long daysSinceFirstInstall,
                int userId,
                String packageName,
                long size,
                int flags,
                Drawable icon,
                CharSequence label) {
            this.daysSinceLastUse = daysSinceLastUse;
            this.daysSinceFirstInstall = daysSinceFirstInstall;
            this.userId = userId;
            this.packageName = packageName;
            this.size = size;
            this.flags = flags;
            this.icon = icon;
            this.label = label;
        }

        public static class Builder {
            private long mDaysSinceLastUse;
            private long mDaysSinceFirstInstall;
            private int mUserId;
            private String mPackageName;
            private long mSize;
            private int mFlags;
            private Drawable mIcon;
            private CharSequence mLabel;

            public Builder setDaysSinceLastUse(long daysSinceLastUse) {
                this.mDaysSinceLastUse = daysSinceLastUse;
                return this;
            }

            public Builder setDaysSinceFirstInstall(long daysSinceFirstInstall) {
                this.mDaysSinceFirstInstall = daysSinceFirstInstall;
                return this;
            }

            public Builder setUserId(int userId) {
                this.mUserId = userId;
                return this;
            }

            public Builder setPackageName(String packageName) {
                this.mPackageName = packageName;
                return this;
            }

            public Builder setSize(long size) {
                this.mSize = size;
                return this;
            }

            public Builder setFlags(int flags) {
                this.mFlags = flags;
                return this;
            }

            public Builder setIcon(Drawable icon) {
                this.mIcon = icon;
                return this;
            }

            public Builder setLabel(CharSequence label) {
                this.mLabel = label;
                return this;
            }

            public PackageInfo build() {
                return new PackageInfo(
                        mDaysSinceLastUse,
                        mDaysSinceFirstInstall,
                        mUserId,
                        mPackageName,
                        mSize,
                        mFlags,
                        mIcon,
                        mLabel);
            }
        }
    }

    /** Clock provides the current time. */
    static class Clock {
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }
    }
}
