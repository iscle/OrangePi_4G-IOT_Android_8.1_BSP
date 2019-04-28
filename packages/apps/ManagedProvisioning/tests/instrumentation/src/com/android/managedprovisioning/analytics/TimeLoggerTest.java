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

package com.android.managedprovisioning.analytics;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link TimeLogger}.
 */
@SmallTest
public class TimeLoggerTest extends AndroidTestCase {

    private static final int CATEGORY = 1;
    private static final long START_TIME_MS = 1500;
    private static final long STOP_TIME_MS = 2500;

    private TimeLogger mTimeLogger;

    @Mock private Context mContext;
    @Mock private MetricsLoggerWrapper mMetricsLoggerWrapper;
    @Mock private AnalyticsUtils mAnalyticsUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        mTimeLogger = new TimeLogger(mContext, CATEGORY, mMetricsLoggerWrapper, mAnalyticsUtils);
    }

    @SmallTest
    public void testTimeLogger_withStartTime() {
        // GIVEN that START_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(START_TIME_MS);
        // WHEN logging time starts.
        mTimeLogger.start();

        // GIVEN that STOP_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(STOP_TIME_MS);
        // WHEN logging time stops.
        mTimeLogger.stop();

        // THEN time taken should be logged and the value should be stop time - start time.
        verify(mMetricsLoggerWrapper).logAction(mContext, CATEGORY,
                (int) (STOP_TIME_MS - START_TIME_MS));
    }

    @SmallTest
    public void testTimeLogger_withStartTime_stopsTwice() {
        // GIVEN that START_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(START_TIME_MS);
        // WHEN logging time starts.
        mTimeLogger.start();

        // GIVEN that STOP_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(STOP_TIME_MS);
        // WHEN logging time stops.
        mTimeLogger.stop();

        // THEN time taken should be logged and the value should be stop time - start time.
        verify(mMetricsLoggerWrapper).logAction(mContext, CATEGORY,
                (int) (STOP_TIME_MS - START_TIME_MS));

        // WHEN logging time stops.
        mTimeLogger.stop();
        // THEN nothing should be logged.
        verifyNoMoreInteractions(mMetricsLoggerWrapper);
    }

    @SmallTest
    public void testTimeLogger_withoutStartTime() {
        // GIVEN there is no start time.
        // WHEN logging time stops.
        mTimeLogger.stop();
        // THEN nothing should be logged.
        verifyZeroInteractions(mMetricsLoggerWrapper);
    }
}
