/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.dvr.recorder;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Looper;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;

import com.android.tv.InputSessionManager;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.feature.TestableFeature;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.DvrDataManagerInMemoryImpl;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.testing.FakeClock;
import com.android.tv.testing.dvr.RecordingTestUtils;
import com.android.tv.util.TvInputManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link RecordingScheduler}.
 */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class SchedulerTest {
    private static final String INPUT_ID = "input_id";
    private static final int CHANNEL_ID = 273;

    private FakeClock mFakeClock;
    private DvrDataManagerInMemoryImpl mDataManager;
    private RecordingScheduler mScheduler;
    @Mock DvrManager mDvrManager;
    @Mock InputSessionManager mSessionManager;
    @Mock AlarmManager mMockAlarmManager;
    @Mock ChannelDataManager mChannelDataManager;
    @Mock TvInputManagerHelper mInputManager;
    private final TestableFeature mDvrFeature = CommonFeatures.DVR;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDvrFeature.enableForTest();
        mFakeClock = FakeClock.createWithCurrentTime();
        mDataManager = new DvrDataManagerInMemoryImpl(getTargetContext(), mFakeClock);
        Mockito.when(mChannelDataManager.isDbLoadFinished()).thenReturn(true);
        mScheduler = new RecordingScheduler(Looper.myLooper(), mDvrManager, mSessionManager, mDataManager,
                mChannelDataManager, mInputManager, getTargetContext(), mFakeClock,
                mMockAlarmManager);
    }

    @After
    public void tearDown() {
        mDvrFeature.resetForTests();
    }

    @Test
    public void testUpdate_none() {
        mScheduler.updateAndStartServiceIfNeeded();
        verifyZeroInteractions(mMockAlarmManager);
    }

    @Test
    public void testUpdate_nextIn12Hours() {
        long now = mFakeClock.currentTimeMillis();
        long startTime = now + TimeUnit.HOURS.toMillis(12);
        ScheduledRecording r = RecordingTestUtils
                .createTestRecordingWithPeriod(INPUT_ID, CHANNEL_ID, startTime,
                startTime + TimeUnit.HOURS.toMillis(1));
        mDataManager.addScheduledRecording(r);
        verify(mMockAlarmManager).setExactAndAllowWhileIdle(
                eq(AlarmManager.RTC_WAKEUP),
                eq(startTime - RecordingScheduler.MS_TO_WAKE_BEFORE_START),
                any(PendingIntent.class));
        Mockito.reset(mMockAlarmManager);
        mScheduler.updateAndStartServiceIfNeeded();
        verify(mMockAlarmManager).setExactAndAllowWhileIdle(
                eq(AlarmManager.RTC_WAKEUP),
                eq(startTime - RecordingScheduler.MS_TO_WAKE_BEFORE_START),
                any(PendingIntent.class));
    }

    @Test
    public void testStartsWithin() {
        long now = mFakeClock.currentTimeMillis();
        long startTime = now + 3;
        ScheduledRecording r = RecordingTestUtils
                .createTestRecordingWithPeriod(INPUT_ID, CHANNEL_ID, startTime, startTime + 100);
        assertFalse(mScheduler.startsWithin(r, 2));
        assertTrue(mScheduler.startsWithin(r, 3));
    }
}