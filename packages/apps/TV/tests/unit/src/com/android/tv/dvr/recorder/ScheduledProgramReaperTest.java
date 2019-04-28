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

package com.android.tv.dvr.recorder;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.test.MoreAsserts;

import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.feature.TestableFeature;
import com.android.tv.dvr.DvrDataManagerInMemoryImpl;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.testing.FakeClock;
import com.android.tv.testing.dvr.RecordingTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link ScheduledProgramReaper}.
 */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class ScheduledProgramReaperTest {
    private static final String INPUT_ID = "input_id";
    private static final int CHANNEL_ID = 273;
    private static final long DURATION = TimeUnit.HOURS.toMillis(1);

    private ScheduledProgramReaper mReaper;
    private FakeClock mFakeClock;
    private DvrDataManagerInMemoryImpl mDvrDataManager;
    @Mock private DvrManager mDvrManager;
    private final TestableFeature mDvrFeature = CommonFeatures.DVR;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDvrFeature.enableForTest();
        mFakeClock = FakeClock.createWithTimeOne();
        mDvrDataManager = new DvrDataManagerInMemoryImpl(getContext(), mFakeClock);
        mReaper = new ScheduledProgramReaper(mDvrDataManager, mFakeClock);
    }

    @After
    public void tearDown() {
        mDvrFeature.resetForTests();
    }

    @Test
    public void testRun_noRecordings() {
        assertTrue(mDvrDataManager.getAllScheduledRecordings().isEmpty());
        mReaper.run();
        assertTrue(mDvrDataManager.getAllScheduledRecordings().isEmpty());
    }

    @Test
    public void testRun_oneRecordingsTomorrow() {
        ScheduledRecording recording = addNewScheduledRecordingForTomorrow();
        MoreAsserts
                .assertContentsInAnyOrder(mDvrDataManager.getAllScheduledRecordings(), recording);
        mReaper.run();
        MoreAsserts
                .assertContentsInAnyOrder(mDvrDataManager.getAllScheduledRecordings(), recording);
    }

    @Test
    public void testRun_oneRecordingsStarted() {
        ScheduledRecording recording = addNewScheduledRecordingForTomorrow();
        MoreAsserts
                .assertContentsInAnyOrder(mDvrDataManager.getAllScheduledRecordings(), recording);
        mFakeClock.increment(TimeUnit.DAYS);
        mReaper.run();
        MoreAsserts
                .assertContentsInAnyOrder(mDvrDataManager.getAllScheduledRecordings(), recording);
    }

    @Test
    public void testRun_oneRecordingsFinished() {
        ScheduledRecording recording = addNewScheduledRecordingForTomorrow();
        MoreAsserts
                .assertContentsInAnyOrder(mDvrDataManager.getAllScheduledRecordings(), recording);
        mFakeClock.increment(TimeUnit.DAYS);
        mFakeClock.increment(TimeUnit.MINUTES, 2);
        mReaper.run();
        MoreAsserts
                .assertContentsInAnyOrder(mDvrDataManager.getAllScheduledRecordings(), recording);
    }

    @Test
    public void testRun_oneRecordingsExpired() {
        ScheduledRecording recording = addNewScheduledRecordingForTomorrow();
        MoreAsserts
                .assertContentsInAnyOrder(mDvrDataManager.getAllScheduledRecordings(), recording);
        mFakeClock.increment(TimeUnit.DAYS, 1 + ScheduledProgramReaper.DAYS);
        mFakeClock.increment(TimeUnit.MILLISECONDS, DURATION);
        // After the cutoff and enough so we can see on the clock
        mFakeClock.increment(TimeUnit.SECONDS, 1);

        mReaper.run();
        assertTrue("Recordings after reaper at " + com.android.tv.util.Utils
                        .toIsoDateTimeString(mFakeClock.currentTimeMillis()),
                mDvrDataManager.getAllScheduledRecordings().isEmpty());
    }

    private ScheduledRecording addNewScheduledRecordingForTomorrow() {
        long startTime = mFakeClock.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        ScheduledRecording recording = RecordingTestUtils.createTestRecordingWithPeriod(INPUT_ID,
                CHANNEL_ID, startTime, startTime + DURATION);
        return mDvrDataManager.addScheduledRecordingInternal(
                ScheduledRecording.buildFrom(recording)
                        .setState(ScheduledRecording.STATE_RECORDING_FINISHED).build());
    }
}
