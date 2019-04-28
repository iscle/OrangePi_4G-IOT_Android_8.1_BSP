/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.deletionhelper;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;
import com.android.settingslib.applications.PackageManagerWrapper;
import com.android.settingslib.applications.StorageStatsSource;
import com.android.settingslib.applications.StorageStatsSource.AppStorageStats;
import com.android.storagemanager.deletionhelper.AppsAsyncLoader.PackageInfo;
import com.android.storagemanager.testing.StorageManagerRobolectricTestRunner;
import com.android.storagemanager.testing.TestingConstants;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(StorageManagerRobolectricTestRunner.class)
@Config(manifest = TestingConstants.MANIFEST, sdk = 23)
public class AppsAsyncLoaderTest {

    public static final String PACKAGE_SYSTEM = "package.system";
    private static final long STARTING_TIME = TimeUnit.DAYS.toMillis(1000);
    private static final String PACKAGE_NAME = "package.mcpackageface";
    public static final String PACKAGE_CLEARABLE = "package.clearable";
    public static final String PACKAGE_TOO_NEW_TO_DELETE = "package.tooNewToDelete";
    public static final String PACKAGE_DEFAULT_LAUNCHER = "package.launcherface";

    @Mock private UsageStatsManager mUsageStatsManager;
    @Mock private StorageStatsSource mStorageStatsSource;
    @Mock private AppsAsyncLoader.Clock mClock;
    @Mock private PackageManagerWrapper mPackageManager;
    @Mock private AppStorageStats mAppStorageStats;
    private AppsAsyncLoader mLoader;
    private HashMap<String, UsageStats> mUsageStats;
    private ArrayList<ApplicationInfo> mInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Set up our mock usage app service.
        ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.application);
        app.setSystemService(Context.USAGE_STATS_SERVICE, mUsageStatsManager);

        // Initialize filters and loader with mock objects
        AppsAsyncLoader.FILTER_NO_THRESHOLD.init();
        AppsAsyncLoader.FILTER_USAGE_STATS.init();

        // Set up the AppsAsyncLoader with a fake clock for us to manipulate the time.
        when(mClock.getCurrentTime()).thenReturn(STARTING_TIME);

        // Set up the loader to return our fake list of apps.
        mInfo = new ArrayList<>();
        when(mPackageManager.getInstalledApplicationsAsUser(anyInt(), anyInt())).thenReturn(mInfo);
        when(mPackageManager.getHomeActivities(any(List.class)))
                .thenReturn(new ComponentName(PACKAGE_DEFAULT_LAUNCHER, ""));

        AppsAsyncLoader.FILTER_USAGE_STATS.init();

        // Set up our fake usage app.
        mUsageStats = new HashMap<>();
        when(mUsageStatsManager.queryAndAggregateUsageStats(anyLong(), anyLong()))
                .thenReturn(mUsageStats);
        when(mStorageStatsSource.getStatsForUid(any(), anyInt())).thenReturn(mAppStorageStats);

        mLoader =
                new AppsAsyncLoader.Builder(RuntimeEnvironment.application)
                        .setUid(0)
                        .setUuid(VolumeInfo.ID_PRIVATE_INTERNAL)
                        .setStorageStatsSource(mStorageStatsSource)
                        .setPackageManager(mPackageManager)
                        .setUsageStatsManager(mUsageStatsManager)
                        .setFilter(AppsAsyncLoader.FILTER_NO_THRESHOLD)
                        .build();
        mLoader.mClock = mClock;
    }

    @Test
    public void test_appInstalledSameDayNeverUsed_isInvalid() {
        AppsAsyncLoader.PackageInfo app =
                createPackage(PACKAGE_NAME, AppsAsyncLoader.NEVER_USED, 0);

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_appInstalledSameDayNeverUsed_isValid() {
        AppsAsyncLoader.PackageInfo app =
                createPackage(PACKAGE_NAME, AppsAsyncLoader.NEVER_USED, 0);

        assertThat(AppsAsyncLoader.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_unusedApp_isValid() {
        AppsAsyncLoader.PackageInfo app =
                createPackage(PACKAGE_NAME, AppsAsyncLoader.NEVER_USED, 90);

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isTrue();
    }

    @Test
    public void test_noThresholdFilter_unusedApp_isValid() {
        AppsAsyncLoader.PackageInfo app =
                createPackage(PACKAGE_NAME, AppsAsyncLoader.NEVER_USED, 90);

        assertThat(AppsAsyncLoader.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_unknownLastUse_isFilteredOut() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, -1, 90);

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_unknownLastUse_isFilteredOut() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, -1, 90);

        assertThat(AppsAsyncLoader.FILTER_NO_THRESHOLD.filterApp(app)).isFalse();
    }

    @Test
    public void test_oldAppRecentlyUsed_isNotValid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 1, 200);

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_oldAppRecentlyUsed_isValid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 1, 200);

        assertThat(AppsAsyncLoader.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_oldUnusedApp_isValid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 199, 200);

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isTrue();
    }

    @Test
    public void test_noThresholdFilter_oldUnusedApp_isValid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 199, 200);

        assertThat(AppsAsyncLoader.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_systemApps_areInvalid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 200, 200);
        app.flags = ApplicationInfo.FLAG_SYSTEM;

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_systemApps_areInvalid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 200, 200);
        app.flags = ApplicationInfo.FLAG_SYSTEM;

        assertThat(AppsAsyncLoader.FILTER_NO_THRESHOLD.filterApp(app)).isFalse();
    }

    @Test
    public void test_persistentProcessApps_areInvalid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 200, 200);
        app.flags = ApplicationInfo.FLAG_PERSISTENT;

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_persistentProcessApps_areInvalid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 200, 200);
        app.flags = ApplicationInfo.FLAG_PERSISTENT;

        assertThat(AppsAsyncLoader.FILTER_NO_THRESHOLD.filterApp(app)).isFalse();
    }

    @Test
    public void test_multipleApps_processCorrectly() {
        mLoader.mFilter = AppsAsyncLoader.FILTER_USAGE_STATS;
        mLoader.mFilter.init();
        AppsAsyncLoader.PackageInfo clearable =
                createPackage(
                        PACKAGE_CLEARABLE,
                        TimeUnit.DAYS.toMillis(800),
                        TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_CLEARABLE, TimeUnit.DAYS.toMillis(800));
        registerApp(clearable, 0, TimeUnit.DAYS.toMillis(800));
        AppsAsyncLoader.PackageInfo tooNewtoDelete =
                createPackage(
                        PACKAGE_TOO_NEW_TO_DELETE,
                        TimeUnit.DAYS.toMillis(1000),
                        TimeUnit.DAYS.toMillis(1000));
        registerLastUse(PACKAGE_TOO_NEW_TO_DELETE, TimeUnit.DAYS.toMillis(1000));
        registerApp(tooNewtoDelete, 1, TimeUnit.DAYS.toMillis(1000));
        AppsAsyncLoader.PackageInfo systemApp =
                createPackage(
                        PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800), TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800));
        systemApp.flags = ApplicationInfo.FLAG_SYSTEM;
        registerApp(systemApp, 2, TimeUnit.DAYS.toMillis(800));
        AppsAsyncLoader.PackageInfo persistentApp =
                createPackage(
                        PACKAGE_NAME, TimeUnit.DAYS.toMillis(800), TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        persistentApp.flags = ApplicationInfo.FLAG_PERSISTENT;
        registerApp(persistentApp, 3, TimeUnit.DAYS.toMillis(800));
        List<AppsAsyncLoader.PackageInfo> infos = mLoader.loadInBackground();

        assertThat(containsPackage(infos, PACKAGE_CLEARABLE)).isTrue();
        assertThat(containsPackage(infos, PACKAGE_TOO_NEW_TO_DELETE)).isFalse();
        assertThat(containsPackage(infos, PACKAGE_NAME)).isFalse();
        assertThat(containsPackage(infos, PACKAGE_SYSTEM)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_ignoresUsageForFiltering() {
        mLoader.mFilter = AppsAsyncLoader.FILTER_NO_THRESHOLD;
        mLoader.mFilter.init();
        AppsAsyncLoader.PackageInfo clearable =
                createPackage(
                        PACKAGE_CLEARABLE,
                        TimeUnit.DAYS.toMillis(800),
                        TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_CLEARABLE, TimeUnit.DAYS.toMillis(800));
        registerApp(clearable, 0, TimeUnit.DAYS.toMillis(800));
        AppsAsyncLoader.PackageInfo tooNewtoDelete =
                createPackage(
                        PACKAGE_TOO_NEW_TO_DELETE,
                        TimeUnit.DAYS.toMillis(1000),
                        TimeUnit.DAYS.toMillis(1000));
        registerLastUse(PACKAGE_TOO_NEW_TO_DELETE, TimeUnit.DAYS.toMillis(1000));
        registerApp(tooNewtoDelete, 1, TimeUnit.DAYS.toMillis(1000));
        AppsAsyncLoader.PackageInfo systemApp =
                createPackage(
                        PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800), TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800));
        systemApp.flags = ApplicationInfo.FLAG_SYSTEM;
        registerApp(systemApp, 2, TimeUnit.DAYS.toMillis(800));
        AppsAsyncLoader.PackageInfo persistentApp =
                createPackage(
                        PACKAGE_NAME, TimeUnit.DAYS.toMillis(800), TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        persistentApp.flags = ApplicationInfo.FLAG_PERSISTENT;
        registerApp(persistentApp, 3, TimeUnit.DAYS.toMillis(800));
        List<AppsAsyncLoader.PackageInfo> infos = mLoader.loadInBackground();

        assertThat(containsPackage(infos, PACKAGE_CLEARABLE)).isTrue();
        assertThat(containsPackage(infos, PACKAGE_TOO_NEW_TO_DELETE)).isTrue();
        assertThat(containsPackage(infos, PACKAGE_NAME)).isFalse();
        assertThat(containsPackage(infos, PACKAGE_SYSTEM)).isFalse();
    }

    @Test
    public void testAppUsedOverOneYearAgoIsValid() {
        AppsAsyncLoader.PackageInfo app = createPackage(PACKAGE_NAME, 1000 - 366, 400);

        assertThat(AppsAsyncLoader.FILTER_USAGE_STATS.filterApp(app)).isTrue();
    }

    @Test
    public void test_getGreaterUsageStats_primaryIsNull() {
        UsageStats secondary = mock(UsageStats.class);
        when(secondary.getLastTimeUsed()).thenReturn(1000L);
        assertThat(mLoader.getGreaterUsageStats(PACKAGE_NAME, null, secondary))
                .isEqualTo(secondary);
    }

    @Test
    public void test_getGreaterUsageStats_secondaryIsNull() {
        UsageStats primary = mock(UsageStats.class);
        when(primary.getLastTimeUsed()).thenReturn(1000L);
        assertThat(mLoader.getGreaterUsageStats(PACKAGE_NAME, primary, null)).isEqualTo(primary);
    }

    @Test
    public void test_getGreaterUsageStats_primaryIsGreater() {
        UsageStats primary = mock(UsageStats.class);
        when(primary.getLastTimeUsed()).thenReturn(1000L);
        UsageStats secondary = mock(UsageStats.class);
        when(secondary.getLastTimeUsed()).thenReturn(900L);
        assertThat(mLoader.getGreaterUsageStats(PACKAGE_NAME, primary, secondary))
                .isEqualTo(primary);
    }

    @Test
    public void test_getGreaterUsageStats_secondaryIsGreater() {
        UsageStats primary = mock(UsageStats.class);
        when(primary.getLastTimeUsed()).thenReturn(900L);
        UsageStats secondary = mock(UsageStats.class);
        when(secondary.getLastTimeUsed()).thenReturn(1000L);
        assertThat(mLoader.getGreaterUsageStats(PACKAGE_NAME, primary, secondary))
                .isEqualTo(secondary);
    }

    @Test
    public void test_defaultLauncherDisallowedFromDeletion() {
        mLoader.mFilter = AppsAsyncLoader.FILTER_USAGE_STATS;
        mLoader.mFilter.init();
        AppsAsyncLoader.PackageInfo defaultLauncher =
                createPackage(
                        PACKAGE_DEFAULT_LAUNCHER,
                        TimeUnit.DAYS.toMillis(800),
                        TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_DEFAULT_LAUNCHER, TimeUnit.DAYS.toMillis(800));
        registerApp(defaultLauncher, 0, TimeUnit.DAYS.toMillis(800));
        List<AppsAsyncLoader.PackageInfo> infos = mLoader.loadInBackground();

        assertThat(containsPackage(infos, PACKAGE_DEFAULT_LAUNCHER)).isFalse();
    }

    private AppsAsyncLoader.PackageInfo createPackage(
            String packageName, long lastUse, long installTime) {
        AppsAsyncLoader.PackageInfo app =
                new AppsAsyncLoader.PackageInfo.Builder()
                        .setDaysSinceLastUse(lastUse)
                        .setDaysSinceFirstInstall(installTime)
                        .setPackageName(packageName)
                        .setLabel("")
                        .build();
        app.packageName = packageName;
        app.label = packageName;
        return app;
    }

    private void registerApp(AppsAsyncLoader.PackageInfo info, int uid, long installed) {
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = uid;
        applicationInfo.packageName = info.packageName;
        applicationInfo.flags = info.flags;
        mInfo.add(applicationInfo);
        android.content.pm.PackageInfo packageInfo = mock(android.content.pm.PackageInfo.class);
        packageInfo.firstInstallTime = installed;
        try {
            when(mPackageManager.getPackageInfo(eq(info.packageName), anyInt()))
                    .thenReturn(packageInfo);
            when(mPackageManager.loadLabel(eq(applicationInfo)))
                    .thenReturn(applicationInfo.packageName);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void registerLastUse(String packageName, long time) {
        UsageStats usageStats = mock(UsageStats.class);
        when(usageStats.getPackageName()).thenReturn(packageName);
        when(usageStats.getLastTimeUsed()).thenReturn(time);
        mUsageStats.put(packageName, usageStats);
    }

    private boolean containsPackage(List<PackageInfo> infos, String expectedPackage) {
        for (PackageInfo info : infos) {
            if (TextUtils.equals(info.packageName, expectedPackage)) {
                return true;
            }
        }
        return false;
    }
}
