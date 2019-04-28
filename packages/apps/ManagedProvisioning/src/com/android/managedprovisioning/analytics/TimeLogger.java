/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.analytics;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_COPY_ACCOUNT_TASK_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_CREATE_PROFILE_TASK_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_DOWNLOAD_PACKAGE_TASK_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ENCRYPT_DEVICE_ACTIVITY_TIME_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_INSTALL_PACKAGE_TASK_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_START_PROFILE_TASK_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_WEB_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.IntDef;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Utility class to log time.
 */
public class TimeLogger {

    private final int mCategory;
    private final Context mContext;
    private final MetricsLoggerWrapper mMetricsLoggerWrapper;
    private final AnalyticsUtils mAnalyticsUtils;
    private Long mStartTime;

    @IntDef({
            PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS,
            PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS,
            PROVISIONING_ENCRYPT_DEVICE_ACTIVITY_TIME_MS,
            PROVISIONING_WEB_ACTIVITY_TIME_MS,
            PROVISIONING_COPY_ACCOUNT_TASK_MS,
            PROVISIONING_CREATE_PROFILE_TASK_MS,
            PROVISIONING_START_PROFILE_TASK_MS,
            PROVISIONING_DOWNLOAD_PACKAGE_TASK_MS,
            PROVISIONING_INSTALL_PACKAGE_TASK_MS})
    public @interface TimeCategory {}

    public TimeLogger(Context context, @TimeCategory int category) {
        this(context, category, new MetricsLoggerWrapper(), new AnalyticsUtils());
    }

    @VisibleForTesting
    TimeLogger(
            Context context,
            int category,
            MetricsLoggerWrapper metricsLoggerWrapper,
            AnalyticsUtils analyticsUtils) {
        mContext = checkNotNull(context);
        mCategory = checkNotNull(category);
        mMetricsLoggerWrapper = checkNotNull(metricsLoggerWrapper);
        mAnalyticsUtils = checkNotNull(analyticsUtils);
    }

    /**
     * Notifies start time to logger.
     */
    public void start() {
        mStartTime = mAnalyticsUtils.elapsedRealTime();
    }

    /**
     * Notifies stop time to logger. Call is ignored if there is no start time.
     */
    public void stop() {
        // Ignore logging time if we couldn't find start time.
        if (mStartTime != null) {
            // Provisioning wouldn't run for 25 days, so int should be fine.
            final int time = (int) (mAnalyticsUtils.elapsedRealTime() - mStartTime);
            // Clear stored start time, we shouldn't log total time twice for same start time.
            mStartTime = null;
            mMetricsLoggerWrapper.logAction(mContext, mCategory, time);
        }
    }
}
