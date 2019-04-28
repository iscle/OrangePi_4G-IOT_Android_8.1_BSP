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

package com.android.storagemanager.automatic;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.Utils;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.deviceinfo.StorageVolumeProvider;
import com.android.storagemanager.overlay.FeatureFactory;
import com.android.storagemanager.overlay.StorageManagementJobProvider;

/**
 * {@link JobService} class to start automatic storage clearing jobs to free up space. The job only
 * starts if the device is under a certain percent of free storage.
 */
public class AutomaticStorageManagementJobService extends JobService {
    private static final String TAG = "AsmJobService";

    private static final long DEFAULT_LOW_FREE_PERCENT = 15;

    private StorageManagementJobProvider mProvider;
    private StorageVolumeProvider mVolumeProvider;
    private Clock mClock;

    @Override
    public boolean onStartJob(JobParameters args) {
        // We need to double-check the preconditions here because they are not enforced for a
        // periodic job.
        if (!preconditionsFulfilled()) {
            // By telling the system to re-schedule the job, it will attempt to execute again at a
            // later idle window -- possibly one where we are charging.
            jobFinished(args, true);
            return false;
        }

        mProvider = FeatureFactory.getFactory(this).getStorageManagementJobProvider();
        if (maybeDisableDueToPolicy(mProvider, getContentResolver(), getClock())) {
            jobFinished(args, false);
            return false;
        }

        if (!volumeNeedsManagement()) {
            Log.i(TAG, "Skipping automatic storage management.");
            Settings.Secure.putLong(getContentResolver(),
                    Settings.Secure.AUTOMATIC_STORAGE_MANAGER_LAST_RUN,
                    System.currentTimeMillis());
            jobFinished(args, false);
            return false;
        }

        boolean isEnabled =
                Settings.Secure.getInt(getContentResolver(),
                        Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED, 0) != 0;
        if (!isEnabled) {
            Intent maybeShowNotificationIntent =
                    new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION);
            maybeShowNotificationIntent.setClass(getApplicationContext(),
                    NotificationController.class);
            getApplicationContext().sendBroadcast(maybeShowNotificationIntent);
            jobFinished(args, false);
            return false;
        }

        if (mProvider != null) {
            return mProvider.onStartJob(this, args, getDaysToRetain());
        }

        jobFinished(args, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters args) {
        if (mProvider != null) {
            return mProvider.onStopJob(this, args);
        }

        return false;
    }

    void setStorageVolumeProvider(StorageVolumeProvider storageProvider) {
        mVolumeProvider = storageProvider;
    }

    private int getDaysToRetain() {
        return Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.AUTOMATIC_STORAGE_MANAGER_DAYS_TO_RETAIN,
                Utils.getDefaultStorageManagerDaysToRetain(getResources()));
    }

    private boolean volumeNeedsManagement() {
        if (mVolumeProvider == null) {
            mVolumeProvider = new StorageManagerVolumeProvider(
                    getSystemService(StorageManager.class));
        }

        PrivateStorageInfo info = PrivateStorageInfo.getPrivateStorageInfo(mVolumeProvider);

        long lowStorageThreshold = (info.totalBytes * DEFAULT_LOW_FREE_PERCENT) / 100;
        return info.freeBytes < lowStorageThreshold;
    }

    private boolean preconditionsFulfilled() {
        // NOTE: We don't check the idle state here because this job should be running in idle
        // maintenance windows. During the idle maintenance window, the device is -technically- not
        // idle. For more information, see PowerManager.isDeviceIdleMode().
        Context context = getApplicationContext();
        return JobPreconditions.isCharging(context);
    }

    /** Returns if ASM was disabled due to policy. * */
    @VisibleForTesting
    static boolean maybeDisableDueToPolicy(
            StorageManagementJobProvider provider, ContentResolver cr, Clock clock) {
        if (provider == null || cr == null) {
            return false;
        }

        final long disabledThresholdMillis = provider.getDisableThresholdMillis(cr);
        final long currentTime = clock.currentTimeMillis();
        final boolean disabledByPolicyInThePast =
                Settings.Secure.getInt(
                                cr,
                                Settings.Secure.AUTOMATIC_STORAGE_MANAGER_TURNED_OFF_BY_POLICY,
                                0)
                        != 0;
        if (currentTime > disabledThresholdMillis && !disabledByPolicyInThePast) {
            Settings.Secure.putInt(
                    cr, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_TURNED_OFF_BY_POLICY, 1);
            Settings.Secure.putInt(cr, Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED, 0);
            return true;
        }

        return false;
    }

    private Clock getClock() {
        if (mClock == null) {
            mClock = new Clock();
        }
        return mClock;
    }

    @VisibleForTesting
    void setClock(Clock clock) {
        mClock = clock;
    }

    /** Clock provides the current time. */
    protected static class Clock {
        /** Returns the current time in milliseconds. */
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}
