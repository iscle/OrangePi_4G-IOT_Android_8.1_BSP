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
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Looper;
import com.android.settingslib.applications.ApplicationsState;
import com.android.storagemanager.testing.TestingConstants;
import com.android.storagemanager.deletionhelper.AppStateUsageStatsBridge.UsageStatsState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.res.builder.RobolectricPackageManager;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=TestingConstants.MANIFEST, sdk=23)
public class AppStateUsageStatsBridgeTest {

    public static final String PACKAGE_SYSTEM = "package.system";
    private static final long STARTING_TIME = TimeUnit.DAYS.toMillis(1000);
    private static final String PACKAGE_NAME = "package.mcpackageface";
    public static final String PACKAGE_CLEARABLE = "package.clearable";
    public static final String PACKAGE_TOO_NEW_TO_DELETE = "package.tooNewToDelete";

    @Mock private ApplicationsState mState;
    @Mock private ApplicationsState.Session mSession;
    @Mock private UsageStatsManager mUsageStatsManager;
    @Mock private AppStateUsageStatsBridge.Clock mClock;
    private AppStateUsageStatsBridge mBridge;
    private ArrayList<ApplicationsState.AppEntry> mApps;
    private HashMap<String, UsageStats> mUsageStats;
    private RobolectricPackageManager mPm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Set up the application state.
        when(mState.newSession(any(ApplicationsState.Callbacks.class))).thenReturn(mSession);
        when(mState.getBackgroundLooper()).thenReturn(Looper.getMainLooper());

        // Set up the ApplicationState's session to return our fake list of apps.
        mApps = new ArrayList<>();
        when(mSession.getAllApps()).thenReturn(mApps);

        // Set up our mock usage stats service.
        ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.application);
        mPm = RuntimeEnvironment.getRobolectricPackageManager();
        app.setSystemService(Context.USAGE_STATS_SERVICE, mUsageStatsManager);

        // Set up the AppStateUsageStatsBridge with a fake clock for us to manipulate the time.
        when(mClock.getCurrentTime()).thenReturn(STARTING_TIME);
        mBridge = new AppStateUsageStatsBridge(RuntimeEnvironment.application, mState, null);
        mBridge.mClock = mClock;
        AppStateUsageStatsBridge.FILTER_USAGE_STATS.init();

        // Set up our fake usage stats.
        mUsageStats = new HashMap<>();
        when(mUsageStatsManager.queryAndAggregateUsageStats(anyLong(),
                anyLong())).thenReturn(mUsageStats);
    }

    @Test
    public void test_appInstalledSameDayNeverUsed_isInvalid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(1000));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(0);
        assertThat(stats.daysSinceLastUse).isEqualTo(AppStateUsageStatsBridge.NEVER_USED);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_appInstalledSameDayNeverUsed_isValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(1000));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(0);
        assertThat(stats.daysSinceLastUse).isEqualTo(AppStateUsageStatsBridge.NEVER_USED);
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_unusedApp_isValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(910));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(90);
        assertThat(stats.daysSinceLastUse).isEqualTo(AppStateUsageStatsBridge.NEVER_USED);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isTrue();
    }

    @Test
    public void test_noThresholdFilter_unusedApp_isValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(910));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(90);
        assertThat(stats.daysSinceLastUse).isEqualTo(AppStateUsageStatsBridge.NEVER_USED);
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_unknownLastUse_isFilteredOut() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(910));
        registerLastUse(PACKAGE_NAME, -1);

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(90);
        assertThat(stats.daysSinceLastUse).isEqualTo(AppStateUsageStatsBridge.UNKNOWN_LAST_USE);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_unknownLastUse_isFilteredOut() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(910));
        registerLastUse(PACKAGE_NAME, -1);

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(90);
        assertThat(stats.daysSinceLastUse).isEqualTo(AppStateUsageStatsBridge.UNKNOWN_LAST_USE);
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(app)).isFalse();
    }

    @Test
    public void test_oldAppRecentlyUsed_isNotValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(999));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(1);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_oldAppRecentlyUsed_isValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(999));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(1);
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_oldUnusedApp_isValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(801));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(199);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isTrue();
    }

    @Test
    public void test_noThresholdFilter_oldUnusedApp_isValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(801));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(199);
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(app)).isTrue();
    }

    @Test
    public void test_systemApps_areInvalid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        app.info.flags = ApplicationInfo.FLAG_SYSTEM;

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(200);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_systemApps_areInvalid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        app.info.flags = ApplicationInfo.FLAG_SYSTEM;

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(200);
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(app)).isFalse();
    }

    @Test
    public void test_persistentProcessApps_areInvalid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        app.info.flags = ApplicationInfo.FLAG_PERSISTENT;

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(200);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_persistentProcessApps_areInvalid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        app.info.flags = ApplicationInfo.FLAG_PERSISTENT;

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(200);
        assertThat(stats.daysSinceLastUse).isEqualTo(200);
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(app)).isFalse();
    }

    @Test
    public void test_multipleApps_processCorrectly() {
        ApplicationsState.AppEntry clearable =
                addPackageToPackageManager(PACKAGE_CLEARABLE, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_CLEARABLE, TimeUnit.DAYS.toMillis(800));
        mApps.add(clearable);
        ApplicationsState.AppEntry tooNewtoDelete =
                addPackageToPackageManager(PACKAGE_TOO_NEW_TO_DELETE, TimeUnit.DAYS.toMillis(1000));
        registerLastUse(PACKAGE_TOO_NEW_TO_DELETE, TimeUnit.DAYS.toMillis(1000));
        mApps.add(tooNewtoDelete);
        ApplicationsState.AppEntry systemApp =
                addPackageToPackageManager(PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800));
        systemApp.info.flags = ApplicationInfo.FLAG_SYSTEM;
        mApps.add(systemApp);
        ApplicationsState.AppEntry persistentApp =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        persistentApp.info.flags = ApplicationInfo.FLAG_PERSISTENT;
        mApps.add(persistentApp);

        mBridge.loadAllExtraInfo();

        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(clearable)).isTrue();
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(tooNewtoDelete)).isFalse();
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(systemApp)).isFalse();
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(persistentApp)).isFalse();
    }

    @Test
    public void test_noThresholdFilter_ignoresUsageForFiltering() {
        ApplicationsState.AppEntry clearable =
                addPackageToPackageManager(PACKAGE_CLEARABLE, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_CLEARABLE, TimeUnit.DAYS.toMillis(800));
        mApps.add(clearable);
        ApplicationsState.AppEntry tooNewtoDelete =
                addPackageToPackageManager(PACKAGE_TOO_NEW_TO_DELETE, TimeUnit.DAYS.toMillis(1000));
        registerLastUse(PACKAGE_TOO_NEW_TO_DELETE, TimeUnit.DAYS.toMillis(1000));
        mApps.add(tooNewtoDelete);
        ApplicationsState.AppEntry systemApp =
                addPackageToPackageManager(PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_SYSTEM, TimeUnit.DAYS.toMillis(800));
        systemApp.info.flags = ApplicationInfo.FLAG_SYSTEM;
        mApps.add(systemApp);
        ApplicationsState.AppEntry persistentApp =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(800));
        persistentApp.info.flags = ApplicationInfo.FLAG_PERSISTENT;
        mApps.add(persistentApp);

        mBridge.loadAllExtraInfo();

        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(clearable)).isTrue();
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(tooNewtoDelete)).isTrue();
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(systemApp)).isFalse();
        assertThat(AppStateUsageStatsBridge.FILTER_NO_THRESHOLD.filterApp(persistentApp)).isFalse();
    }

    @Test
    public void testAppUsedOverOneYearAgoIsValid() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(600));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(1000 - 366));

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);
        UsageStatsState stats = (UsageStatsState) app.extraInfo;

        assertThat(app.extraInfo).isNotNull();
        assertThat(stats.daysSinceFirstInstall).isEqualTo(400);
        assertThat(stats.daysSinceLastUse).isEqualTo(AppStateUsageStatsBridge.NEVER_USED);
        assertThat(AppStateUsageStatsBridge.FILTER_USAGE_STATS.filterApp(app)).isTrue();
    }

    @Test
    public void testStartEndIsBeforeEndTimeInQuery() {
        ApplicationsState.AppEntry app =
                addPackageToPackageManager(PACKAGE_NAME, TimeUnit.DAYS.toMillis(600));
        registerLastUse(PACKAGE_NAME, TimeUnit.DAYS.toMillis(1000 - 366));
        ArgumentCaptor<Long> startTimeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endTimeCaptor = ArgumentCaptor.forClass(Long.class);

        mBridge.updateExtraInfo(app, PACKAGE_NAME, 0);

        verify(mUsageStatsManager, atLeastOnce())
                .queryAndAggregateUsageStats(startTimeCaptor.capture(), endTimeCaptor.capture());
        assertThat(startTimeCaptor.getValue()).isLessThan(endTimeCaptor.getValue());
    }

    private ApplicationsState.AppEntry addPackageToPackageManager(String packageName,
            long installTime) {
        PackageInfo testPackage = new PackageInfo();
        testPackage.packageName = packageName;
        testPackage.firstInstallTime = installTime;
        mPm.addPackage(testPackage);
        ApplicationsState.AppEntry app = mock(ApplicationsState.AppEntry.class);
        ApplicationInfo info = mock(ApplicationInfo.class);
        info.packageName = packageName;
        app.info = info;
        return app;
    }

    private void registerLastUse(String packageName, long time) {
        UsageStats usageStats = mock(UsageStats.class);
        when(usageStats.getPackageName()).thenReturn(packageName);
        when(usageStats.getLastTimeUsed()).thenReturn(time);
        mUsageStats.put(packageName, usageStats);
    }

    private class FakeClock extends AppStateUsageStatsBridge.Clock {
        public long time;

        @Override
        public long getCurrentTime() {
            return time;
        }
    }
}
