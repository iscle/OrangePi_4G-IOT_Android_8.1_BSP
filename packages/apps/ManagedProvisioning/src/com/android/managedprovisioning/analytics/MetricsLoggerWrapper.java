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

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.VIEW_UNKNOWN;

import android.content.Context;

import com.android.internal.logging.MetricsLogger;
import com.android.managedprovisioning.common.ProvisionLogger;

/**
 * Utility class to log metrics using MetricsLogger.
 */
public class MetricsLoggerWrapper {

    public static final boolean LOG_ENABLED = false;

    public MetricsLoggerWrapper() {}

    /**
     * Wrapper to log action with string values.
     *
     * @param context Context passed to MetricsLogger.
     * @param category Metrics category to be logged.
     * @param value String value to be logged
     */
    public void logAction(Context context, int category, String value) {
        logd("MetricsLoggerWrapper, category:" + category + ", value: " + value);
        if (category != VIEW_UNKNOWN) {
            MetricsLogger.action(context, category, value);
        }
    }

    /**
     * Wrapper to log action with integer values.
     *
     * @param context Context passed to MetricsLogger.
     * @param category Metrics category to be logged.
     * @param value Int value to be logged.
     */
    public void logAction(Context context, int category, int value) {
        logd("MetricsLoggerWrapper, category:" + category + ", value: " + value);
        if (category != VIEW_UNKNOWN) {
            MetricsLogger.action(context, category, value);
        }
    }

    /**
     * Wrapper to log action.
     *
     * @param context Context passed to MetricsLogger.
     * @param category Metrics category to be logged.
     */
    public void logAction(Context context, int category) {
        logd("MetricsLoggerWrapper, category:" + category);
        if (category != VIEW_UNKNOWN) {
            MetricsLogger.action(context, category);
        }
    }

    private void logd(String logText) {
        if (LOG_ENABLED) {
            ProvisionLogger.logd(logText);
        }
    }
}
