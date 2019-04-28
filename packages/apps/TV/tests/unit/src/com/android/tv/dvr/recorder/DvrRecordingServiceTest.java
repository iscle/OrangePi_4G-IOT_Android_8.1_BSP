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

import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.Build;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.test.ServiceTestCase;

import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.feature.TestableFeature;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DvrRecordingService}.
 */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class DvrRecordingServiceTest
        extends ServiceTestCase<DvrRecordingServiceTest.MockDvrRecordingService> {
    private final TestableFeature mDvrFeature = CommonFeatures.DVR;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDvrFeature.enableForTest();
        MockitoAnnotations.initMocks(this);
        setupService();
    }

    @Override
    protected void tearDown() throws Exception {
        mDvrFeature.resetForTests();
        super.tearDown();
    }

    public DvrRecordingServiceTest() {
        super(MockDvrRecordingService.class);
    }

    public void testStartService_null() throws Exception {
        // Not recording
        startService(null);
        assertFalse(getService().mInForeground);

        // Recording
        getService().startRecording();
        startService(null);
        assertTrue(getService().mInForeground);
        assertTrue(getService().mIsRecording);
        getService().reset();
    }

    public void testStartService_noUpcomingRecording() throws Exception {
        Intent intent = new Intent(getContext(), DvrRecordingServiceTest.class);
        intent.putExtra(DvrRecordingService.EXTRA_START_FOR_RECORDING, false);

        // Not recording
        startService(intent);
        assertTrue(getService().mInForeground);
        assertFalse(getService().mForegroundForUpcomingRecording);
        getService().stopForegroundIfNotRecordingInternal();
        assertFalse(getService().mInForeground);

        // Recording, ended quickly
        getService().startRecording();
        startService(intent);
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertTrue(getService().mIsRecording);
        getService().stopRecording();
        assertFalse(getService().mInForeground);
        assertFalse(getService().mIsRecording);
        getService().stopForegroundIfNotRecordingInternal();
        assertFalse(getService().mInForeground);
        assertFalse(getService().mIsRecording);
        getService().reset();

        // Recording, ended later
        getService().startRecording();
        startService(intent);
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertTrue(getService().mIsRecording);
        getService().stopForegroundIfNotRecordingInternal();
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertTrue(getService().mIsRecording);
        getService().stopRecording();
        assertFalse(getService().mInForeground);
        assertFalse(getService().mIsRecording);
        getService().reset();
    }

    public void testStartService_hasUpcomingRecording() throws Exception {
        Intent intent = new Intent(getContext(), DvrRecordingServiceTest.class);
        intent.putExtra(DvrRecordingService.EXTRA_START_FOR_RECORDING, true);

        // Not recording
        startService(intent);
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertFalse(getService().mIsRecording);
        getService().startRecording();
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertTrue(getService().mIsRecording);
        getService().stopRecording();
        assertFalse(getService().mInForeground);
        assertFalse(getService().mIsRecording);
        getService().reset();

        // Recording
        getService().startRecording();
        startService(intent);
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertTrue(getService().mIsRecording);
        getService().startRecording();
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertTrue(getService().mIsRecording);
        getService().stopRecording();
        assertTrue(getService().mInForeground);
        assertTrue(getService().mForegroundForUpcomingRecording);
        assertTrue(getService().mIsRecording);
        getService().stopRecording();
        assertFalse(getService().mInForeground);
        assertFalse(getService().mIsRecording);
        getService().reset();
    }

    public static class MockDvrRecordingService extends DvrRecordingService {
        private int mRecordingCount = 0;
        private boolean mInForeground;
        private boolean mForegroundForUpcomingRecording;

        @Override
        protected void startForegroundInternal(boolean hasUpcomingRecording) {
            mForegroundForUpcomingRecording = hasUpcomingRecording;
            mInForeground = true;
        }

        @Override
        protected void stopForegroundInternal() {
            mInForeground = false;
        }

        private void startRecording() {
            mOnRecordingSessionChangeListener.onRecordingSessionChange(true, ++mRecordingCount);
        }

        private void stopRecording() {
            mOnRecordingSessionChangeListener.onRecordingSessionChange(false, --mRecordingCount);
        }

        private void reset() {
            mRecordingCount = 0;
            mInForeground = false;
            mIsRecording = false;
        }
    }
}