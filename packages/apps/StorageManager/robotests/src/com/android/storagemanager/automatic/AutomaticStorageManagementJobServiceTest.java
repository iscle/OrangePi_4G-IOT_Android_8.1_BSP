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

package com.android.storagemanager.automatic;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.usage.StorageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.Settings;

import com.android.settingslib.deviceinfo.StorageVolumeProvider;
import com.android.storagemanager.overlay.FeatureFactory;
import com.android.storagemanager.overlay.StorageManagementJobProvider;
import com.android.storagemanager.testing.TestingConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=TestingConstants.MANIFEST, sdk=TestingConstants.SDK_VERSION)
public class AutomaticStorageManagementJobServiceTest {
    @Mock private BatteryManager mBatteryManager;
    @Mock private NotificationManager mNotificationManager;
    @Mock private VolumeInfo mVolumeInfo;
    @Mock private File mFile;
    @Mock private JobParameters mJobParameters;
    @Mock private StorageManagementJobProvider mStorageManagementJobProvider;
    @Mock private FeatureFactory mFeatureFactory;
    @Mock private StorageVolumeProvider mStorageVolumeProvider;
    @Mock private AutomaticStorageManagementJobService.Clock mClock;
    private AutomaticStorageManagementJobService mJobService;
    private ShadowApplication mApplication;
    private List<VolumeInfo> mVolumes;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mJobParameters.getJobId()).thenReturn(0);

        // Let's set up our system services to act like a device that has the following conditions:
        // 1. We're plugged in and charging.
        // 2. We have a completely full device.
        // 3. ASM is disabled.
        when(mBatteryManager.isCharging()).thenReturn(true);
        mVolumes = new ArrayList<>();
        when(mVolumeInfo.getPath()).thenReturn(mFile);
        when(mVolumeInfo.getType()).thenReturn(VolumeInfo.TYPE_PRIVATE);
        when(mVolumeInfo.getFsUuid()).thenReturn(StorageManager.UUID_PRIMARY_PHYSICAL);
        when(mVolumeInfo.isMountedReadable()).thenReturn(true);
        mVolumes.add(mVolumeInfo);
        when(mStorageVolumeProvider.getPrimaryStorageSize()).thenReturn(100L);
        when(mStorageVolumeProvider.getVolumes()).thenReturn(mVolumes);
        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(mVolumeInfo)))
                .thenReturn(0L);
        when(mStorageVolumeProvider.getTotalBytes(
                        nullable(StorageStatsManager.class), eq(mVolumeInfo)))
                .thenReturn(100L);

        mApplication = ShadowApplication.getInstance();
        mApplication.setSystemService(Context.BATTERY_SERVICE, mBatteryManager);
        mApplication.setSystemService(Context.NOTIFICATION_SERVICE, mNotificationManager);

        // This is a hack-y injection of our own FeatureFactory.
        // By default, the Storage Manager has a FeatureFactory which returns null for all features.
        // Using reflection, we can inject our own FeatureFactory which returns a mock for the
        // StorageManagementJobProvider feature. This lets us observe when the ASMJobService
        // actually tries to run the job.
        when(mFeatureFactory.getStorageManagementJobProvider())
                .thenReturn(mStorageManagementJobProvider);
        when(mStorageManagementJobProvider.onStartJob(
                        nullable(Context.class), nullable(JobParameters.class), anyInt()))
                .thenReturn(false);
        ReflectionHelpers.setStaticField(FeatureFactory.class, "sFactory", mFeatureFactory);

        // And we can't forget to initialize the actual job service.
        mJobService = spy(Robolectric.setupService(AutomaticStorageManagementJobService.class));
        mJobService.setStorageVolumeProvider(mStorageVolumeProvider);
        mJobService.setClock(mClock);

        Resources fakeResources = mock(Resources.class);
        when(fakeResources.getInteger(
                        com.android.internal.R.integer.config_storageManagerDaystoRetainDefault))
                .thenReturn(90);

        when(mJobService.getResources()).thenReturn(fakeResources);
    }

    @Test
    public void testJobRequiresCharging() {
        when(mBatteryManager.isCharging()).thenReturn(false);
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        // The job should report that it needs to be retried, if not charging.
        assertJobFinished(true);

        when(mBatteryManager.isCharging()).thenReturn(true);
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertJobFinished(false);
    }

    @Test
    public void testStartJobTriesUpsellWhenASMDisabled() {
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertJobFinished(false);
        mApplication.runBackgroundTasks();

        List<Intent> broadcastedIntents = mApplication.getBroadcastIntents();
        assertThat(broadcastedIntents.size()).isEqualTo(1);

        Intent lastIntent = broadcastedIntents.get(0);
        assertThat(lastIntent.getAction())
                .isEqualTo(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION);
        assertThat(lastIntent.getComponent().getClassName())
                .isEqualTo(NotificationController.class.getCanonicalName());

        assertStorageManagerJobDidNotRun();
    }

    @Test
    public void testASMJobRunsWithValidConditions() {
        activateASM();
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertStorageManagerJobRan();
    }

    @Test
    public void testJobDoesntRunIfStorageNotFull() throws Exception {
        activateASM();
        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(mVolumeInfo)))
                .thenReturn(100L);
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertStorageManagerJobDidNotRun();
    }

    @Test
    public void testJobOnlyRunsIfFreeStorageIsUnder15Percent() throws Exception {
        activateASM();
        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(mVolumeInfo)))
                .thenReturn(15L);
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertStorageManagerJobDidNotRun();

        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(mVolumeInfo)))
                .thenReturn(14L);
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertStorageManagerJobRan();
    }

    @Test
    public void testNonDefaultDaysToRetain() {
        ContentResolver resolver = mApplication.getApplicationContext().getContentResolver();
        Settings.Secure.putInt(resolver, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_DAYS_TO_RETAIN,
                30);
        activateASM();
        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertStorageManagerJobRan(30);
    }

    @Test
    public void testNonPrivateDrivesIgnoredForFreeSpaceCalculation() throws Exception {
        File notPrivate = mock(File.class);
        VolumeInfo nonPrivateVolume = mock(VolumeInfo.class);
        when(nonPrivateVolume.getPath()).thenReturn(notPrivate);
        when(nonPrivateVolume.getType()).thenReturn(VolumeInfo.TYPE_PUBLIC);
        mVolumes.add(nonPrivateVolume);
        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(nonPrivateVolume)))
                .thenReturn(0L);
        when(mStorageVolumeProvider.getTotalBytes(
                        nullable(StorageStatsManager.class), eq(nonPrivateVolume)))
                .thenReturn(100L);
        activateASM();
        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(mVolumeInfo)))
                .thenReturn(15L);

        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertStorageManagerJobDidNotRun();
    }

    @Test
    public void testMultiplePrivateVolumesCountedForASMActivationThreshold() throws Exception {
        File privateVolume = mock(File.class);
        VolumeInfo privateVolumeInfo = mock(VolumeInfo.class);
        when(privateVolumeInfo.getPath()).thenReturn(privateVolume);
        when(privateVolumeInfo.getType()).thenReturn(VolumeInfo.TYPE_PRIVATE);
        when(privateVolumeInfo.isMountedReadable()).thenReturn(true);
        when(privateVolumeInfo.getFsUuid()).thenReturn(StorageManager.UUID_PRIVATE_INTERNAL);
        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(privateVolumeInfo)))
                .thenReturn(0L);
        when(mStorageVolumeProvider.getTotalBytes(
                        nullable(StorageStatsManager.class), eq(privateVolumeInfo)))
                .thenReturn(100L);
        mVolumes.add(privateVolumeInfo);
        activateASM();
        when(mStorageVolumeProvider.getFreeBytes(
                        nullable(StorageStatsManager.class), eq(mVolumeInfo)))
                .thenReturn(15L);

        assertThat(mJobService.onStartJob(mJobParameters)).isFalse();
        assertStorageManagerJobRan();
    }

    @Test
    public void disableSmartStorageIfPastThreshold() throws Exception {
        ContentResolver resolver = mApplication.getApplicationContext().getContentResolver();
        activateASM();

        AutomaticStorageManagementJobService.Clock fakeClock =
                mock(AutomaticStorageManagementJobService.Clock.class);
        when(fakeClock.currentTimeMillis()).thenReturn(1001L);
        when(mStorageManagementJobProvider.getDisableThresholdMillis(any(ContentResolver.class)))
                .thenReturn(1000L);
        AutomaticStorageManagementJobService.maybeDisableDueToPolicy(
                mStorageManagementJobProvider, resolver, fakeClock);

        assertThat(
                        Settings.Secure.getInt(
                                resolver, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED))
                .isEqualTo(0);
    }

    @Test
    public void dontDisableSmartStorageIfPastThresholdAndDisabledInThePast() throws Exception {
        ContentResolver resolver = mApplication.getApplicationContext().getContentResolver();
        activateASM();
        Settings.Secure.putInt(
                resolver, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_TURNED_OFF_BY_POLICY, 1);

        AutomaticStorageManagementJobService.Clock fakeClock =
                mock(AutomaticStorageManagementJobService.Clock.class);
        when(fakeClock.currentTimeMillis()).thenReturn(1001L);
        when(mStorageManagementJobProvider.getDisableThresholdMillis(any(ContentResolver.class)))
                .thenReturn(1000L);
        AutomaticStorageManagementJobService.maybeDisableDueToPolicy(
                mStorageManagementJobProvider, resolver, fakeClock);

        assertThat(
                        Settings.Secure.getInt(
                                resolver, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED))
                .isNotEqualTo(0);
    }

    @Test
    public void logDisabledByPolicyIfPastThreshold() throws Exception {
        ContentResolver resolver = mApplication.getApplicationContext().getContentResolver();
        activateASM();

        AutomaticStorageManagementJobService.Clock fakeClock =
                mock(AutomaticStorageManagementJobService.Clock.class);
        when(fakeClock.currentTimeMillis()).thenReturn(1001L);
        when(mStorageManagementJobProvider.getDisableThresholdMillis(any(ContentResolver.class)))
                .thenReturn(1000L);
        AutomaticStorageManagementJobService.maybeDisableDueToPolicy(
                mStorageManagementJobProvider, resolver, fakeClock);

        assertThat(
                        Settings.Secure.getInt(
                                resolver,
                                Settings.Secure.AUTOMATIC_STORAGE_MANAGER_TURNED_OFF_BY_POLICY))
                .isGreaterThan(0);
    }

    @Test
    public void dontDisableSmartStorageIfNotPastThreshold() throws Exception {
        ContentResolver resolver = mApplication.getApplicationContext().getContentResolver();
        activateASM();

        AutomaticStorageManagementJobService.Clock fakeClock =
                mock(AutomaticStorageManagementJobService.Clock.class);
        when(fakeClock.currentTimeMillis()).thenReturn(999L);
        when(mStorageManagementJobProvider.getDisableThresholdMillis(any(ContentResolver.class)))
                .thenReturn(1000L);
        AutomaticStorageManagementJobService.maybeDisableDueToPolicy(
                mStorageManagementJobProvider, resolver, fakeClock);

        assertThat(
                        Settings.Secure.getInt(
                                resolver, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED))
                .isNotEqualTo(0);
    }

    private void assertJobFinished(boolean retryNeeded) {
        verify(mJobService).jobFinished(nullable(JobParameters.class), eq(retryNeeded));
    }

    private void assertStorageManagerJobRan() {
        assertStorageManagerJobRan(
                Settings.Secure.AUTOMATIC_STORAGE_MANAGER_DAYS_TO_RETAIN_DEFAULT);
    }

    private void assertStorageManagerJobRan(int daysToRetain) {
        verify(mStorageManagementJobProvider).onStartJob(eq(mJobService), eq(mJobParameters),
                eq(daysToRetain));
    }

    private void assertStorageManagerJobDidNotRun() {
        verify(mStorageManagementJobProvider, never())
                .onStartJob(any(Context.class), any(JobParameters.class), anyInt());
    }

    private void activateASM() {
        ContentResolver resolver = mApplication.getApplicationContext().getContentResolver();
        Settings.Secure.putInt(resolver, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED, 1);
    }
}
