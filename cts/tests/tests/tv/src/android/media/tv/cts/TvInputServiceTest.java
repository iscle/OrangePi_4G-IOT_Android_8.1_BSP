/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvRecordingClient;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.cts.TvInputServiceTest.CountingTvInputService.CountingSession;
import android.media.tv.cts.TvInputServiceTest.CountingTvInputService.CountingRecordingSession;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;

import android.tv.cts.R;

import com.android.compatibility.common.util.PollingCheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * Test {@link android.media.tv.TvInputService}.
 */
public class TvInputServiceTest extends ActivityInstrumentationTestCase2<TvViewStubActivity> {
    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT = 15000L;
    private static final String DUMMT_TRACK_ID = "dummyTrackId";
    private static final TvTrackInfo DUMMY_TRACK =
            new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, DUMMT_TRACK_ID)
            .setVideoWidth(1920).setVideoHeight(1080).setLanguage("und").build();
    private static Bundle sDummyBundle;

    private TvView mTvView;
    private TvRecordingClient mTvRecordingClient;
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private TvInputManager mManager;
    private TvInputInfo mStubInfo;
    private TvInputInfo mFaultyStubInfo;
    private final StubCallback mCallback = new StubCallback();
    private final StubTimeShiftPositionCallback mTimeShiftPositionCallback =
            new StubTimeShiftPositionCallback();
    private final StubRecordingCallback mRecordingCallback = new StubRecordingCallback();

    private static class StubCallback extends TvView.TvInputCallback {
        private int mChannelRetunedCount;
        private int mVideoAvailableCount;
        private int mVideoUnavailableCount;
        private int mTrackSelectedCount;
        private int mTrackChangedCount;
        private int mVideoSizeChanged;
        private int mContentAllowedCount;
        private int mContentBlockedCount;
        private int mTimeShiftStatusChangedCount;

        private Uri mChannelRetunedUri;
        private Integer mVideoUnavailableReason;
        private Integer mTrackSelectedType;
        private String mTrackSelectedTrackId;
        private List<TvTrackInfo> mTracksChangedTrackList;
        private TvContentRating mContentBlockedRating;
        private Integer mTimeShiftStatusChangedStatus;

        @Override
        public void onChannelRetuned(String inputId, Uri channelUri) {
            mChannelRetunedCount++;
            mChannelRetunedUri = channelUri;
        }

        @Override
        public void onVideoAvailable(String inputId) {
            mVideoAvailableCount++;
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            mVideoUnavailableCount++;
            mVideoUnavailableReason = reason;
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            mTrackSelectedCount++;
            mTrackSelectedType = type;
            mTrackSelectedTrackId = trackId;
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> trackList) {
            mTrackChangedCount++;
            mTracksChangedTrackList = trackList;
        }

        @Override
        public void onVideoSizeChanged(String inputId, int width, int height) {
            mVideoSizeChanged++;
        }

        @Override
        public void onContentAllowed(String inputId) {
            mContentAllowedCount++;
        }

        @Override
        public void onContentBlocked(String inputId, TvContentRating rating) {
            mContentBlockedCount++;
            mContentBlockedRating = rating;
        }

        @Override
        public void onTimeShiftStatusChanged(String inputId, int status) {
            mTimeShiftStatusChangedCount++;
            mTimeShiftStatusChangedStatus = status;
        }

        public void resetCounts() {
            mChannelRetunedCount = 0;
            mVideoAvailableCount = 0;
            mVideoUnavailableCount = 0;
            mTrackSelectedCount = 0;
            mTrackChangedCount = 0;
            mContentAllowedCount = 0;
            mContentBlockedCount = 0;
            mTimeShiftStatusChangedCount = 0;
        }

        public void resetPassedValues() {
            mChannelRetunedUri = null;
            mVideoUnavailableReason = null;
            mTrackSelectedType = null;
            mTrackSelectedTrackId = null;
            mTracksChangedTrackList = null;
            mContentBlockedRating = null;
            mTimeShiftStatusChangedStatus = null;
        }
    }

    private static class StubTimeShiftPositionCallback extends TvView.TimeShiftPositionCallback {
        private int mTimeShiftStartPositionChanged;
        private int mTimeShiftCurrentPositionChanged;

        @Override
        public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
            mTimeShiftStartPositionChanged++;
        }

        @Override
        public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
            mTimeShiftCurrentPositionChanged++;
        }

        public void resetCounts() {
            mTimeShiftStartPositionChanged = 0;
            mTimeShiftCurrentPositionChanged = 0;
        }
    }

    public TvInputServiceTest() {
        super(TvViewStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        mActivity = getActivity();
        mInstrumentation = getInstrumentation();
        mTvView = (TvView) mActivity.findViewById(R.id.tvview);
        mTvRecordingClient = new TvRecordingClient(mActivity, "TvInputServiceTest",
                mRecordingCallback, null);
        mManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : mManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(CountingTvInputService.class.getName())) {
                mStubInfo = info;
            }
            if (info.getServiceInfo().name.equals(FaultyTvInputService.class.getName())) {
                mFaultyStubInfo = info;
            }
            if (mStubInfo != null && mFaultyStubInfo != null) {
                break;
            }
        }
        assertNotNull(mStubInfo);
        mTvView.setCallback(mCallback);

        CountingTvInputService.sSession = null;
    }

    public void testTvInputServiceSession() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        initDummyBundle();
        verifyCommandTune();
        verifyCommandTuneWithBundle();
        verifyCommandSendAppPrivateCommand();
        verifyCommandSetStreamVolume();
        verifyCommandSetCaptionEnabled();
        verifyCommandSelectTrack();
        verifyCommandDispatchKeyDown();
        verifyCommandDispatchKeyMultiple();
        verifyCommandDispatchKeyUp();
        verifyCommandDispatchTouchEvent();
        verifyCommandDispatchTrackballEvent();
        verifyCommandDispatchGenericMotionEvent();
        verifyCommandTimeShiftPause();
        verifyCommandTimeShiftResume();
        verifyCommandTimeShiftSeekTo();
        verifyCommandTimeShiftSetPlaybackParams();
        verifyCommandTimeShiftPlay();
        verifyCommandSetTimeShiftPositionCallback();
        verifyCommandOverlayViewSizeChanged();
        verifyCallbackChannelRetuned();
        verifyCallbackVideoAvailable();
        verifyCallbackVideoUnavailable();
        verifyCallbackTracksChanged();
        verifyCallbackTrackSelected();
        verifyCallbackVideoSizeChanged();
        verifyCallbackContentAllowed();
        verifyCallbackContentBlocked();
        verifyCallbackTimeShiftStatusChanged();
        verifyCallbackLayoutSurface();

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testTvInputServiceRecordingSession() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        initDummyBundle();
        verifyCommandTuneForRecording();
        verifyCallbackConnectionFailed();
        verifyCommandTuneForRecordingWithBundle();
        verifyCallbackTuned();
        verifyCommandStartRecording();
        verifyCommandStopRecording();
        verifyCommandSendAppPrivateCommandForRecording();
        verifyCallbackRecordingStopped();
        verifyCallbackError();
        verifyCommandRelease();
        verifyCallbackDisconnected();
    }

    public void verifyCommandTuneForRecording() {
        resetCounts();
        resetPassedValues();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune(mStubInfo.getId(), fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null && session.mTuneCount > 0
                        && Objects.equals(session.mTunedChannelUri, fakeChannelUri);
            }
        }.run();
    }

    public void verifyCommandTuneForRecordingWithBundle() {
        resetCounts();
        resetPassedValues();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune(mStubInfo.getId(), fakeChannelUri, sDummyBundle);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null
                        && session.mTuneCount > 0
                        && session.mTuneWithBundleCount > 0
                        && Objects.equals(session.mTunedChannelUri, fakeChannelUri)
                        && bundleEquals(session.mTuneWithBundleData, sDummyBundle);
            }
        }.run();
    }

    public void verifyCommandRelease() {
        resetCounts();
        mTvRecordingClient.release();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null && session.mReleaseCount > 0;
            }
        }.run();
    }

    public void verifyCommandStartRecording() {
        resetCounts();
        resetPassedValues();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.startRecording(fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null
                        && session.mStartRecordingCount > 0
                        && Objects.equals(session.mProgramHint, fakeChannelUri);
            }
        }.run();
    }

    public void verifyCommandStopRecording() {
        resetCounts();
        mTvRecordingClient.stopRecording();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null && session.mStopRecordingCount > 0;
            }
        }.run();
    }

    public void verifyCommandSendAppPrivateCommandForRecording() {
        resetCounts();
        resetPassedValues();
        final String action = "android.media.tv.cts.TvInputServiceTest.privateCommand";
        mTvRecordingClient.sendAppPrivateCommand(action, sDummyBundle);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null
                        && session.mAppPrivateCommandCount > 0
                        && bundleEquals(session.mAppPrivateCommandData, sDummyBundle)
                        && TextUtils.equals(session.mAppPrivateCommandAction, action);
            }
        }.run();
    }

    public void verifyCallbackTuned() {
        resetCounts();
        resetPassedValues();
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        assertNotNull(session);
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        session.notifyTuned(fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mTunedCount > 0
                        && Objects.equals(mRecordingCallback.mTunedChannelUri, fakeChannelUri);
            }
        }.run();
    }

    public void verifyCallbackError() {
        resetCounts();
        resetPassedValues();
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        assertNotNull(session);
        final int error = TvInputManager.RECORDING_ERROR_UNKNOWN;
        session.notifyError(error);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mErrorCount > 0
                        && mRecordingCallback.mError == error;
            }
        }.run();
    }

    public void verifyCallbackRecordingStopped() {
        resetCounts();
        resetPassedValues();
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        assertNotNull(session);
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        session.notifyRecordingStopped(fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mRecordingStoppedCount > 0
                        && Objects.equals(mRecordingCallback.mRecordedProgramUri, fakeChannelUri);
            }
        }.run();
    }

    public void verifyCallbackConnectionFailed() {
        resetCounts();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune("invalid_input_id", fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mConnectionFailedCount > 0;
            }
        }.run();
    }

    public void verifyCallbackDisconnected() {
        resetCounts();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune(mFaultyStubInfo.getId(), fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mDisconnectedCount > 0;
            }
        }.run();
    }

    public void verifyCommandTune() {
        resetCounts();
        resetPassedValues();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvView.tune(mStubInfo.getId(), fakeChannelUri);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mTuneCount > 0
                        && session.mCreateOverlayView > 0
                        && Objects.equals(session.mTunedChannelUri, fakeChannelUri);
            }
        }.run();
    }

    public void verifyCommandTuneWithBundle() {
        resetCounts();
        resetPassedValues();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvView.tune(mStubInfo.getId(), fakeChannelUri, sDummyBundle);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mTuneCount > 0
                        && session.mTuneWithBundleCount > 0
                        && Objects.equals(session.mTunedChannelUri, fakeChannelUri)
                        && bundleEquals(session.mTuneWithBundleData, sDummyBundle);
            }
        }.run();
    }

    public void verifyCommandSetStreamVolume() {
        resetCounts();
        resetPassedValues();
        final float volume = 0.8f;
        mTvView.setStreamVolume(volume);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mSetStreamVolumeCount > 0
                        && session.mStreamVolume == volume;
            }
        }.run();
    }

    public void verifyCommandSetCaptionEnabled() {
        resetCounts();
        resetPassedValues();
        final boolean enable = true;
        mTvView.setCaptionEnabled(enable);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mSetCaptionEnabledCount > 0
                        && session.mCaptionEnabled == enable;
            }
        }.run();
    }

    public void verifyCommandSelectTrack() {
        resetCounts();
        resetPassedValues();
        verifyCallbackTracksChanged();
        final int dummyTrackType = DUMMY_TRACK.getType();
        final String dummyTrackId = DUMMY_TRACK.getId();
        mTvView.selectTrack(dummyTrackType, dummyTrackId);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mSelectTrackCount > 0
                        && session.mSelectTrackType == dummyTrackType
                        && TextUtils.equals(session.mSelectTrackId, dummyTrackId);
            }
        }.run();
    }

    public void verifyCommandDispatchKeyDown() {
        resetCounts();
        resetPassedValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mTvView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mKeyDownCount > 0
                        && session.mKeyDownCode == keyCode
                        && keyEventEquals(event, session.mKeyDownEvent);
            }
        }.run();
    }

    public void verifyCommandDispatchKeyMultiple() {
        resetCounts();
        resetPassedValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_MULTIPLE, keyCode);
        mTvView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mKeyMultipleCount > 0
                        && session.mKeyMultipleCode == keyCode
                        && keyEventEquals(event, session.mKeyMultipleEvent)
                        && session.mKeyMultipleNumber == event.getRepeatCount();
            }
        }.run();
    }

    public void verifyCommandDispatchKeyUp() {
        resetCounts();
        resetPassedValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        mTvView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mKeyUpCount > 0
                        && session.mKeyUpCode == keyCode
                        && keyEventEquals(event, session.mKeyUpEvent);
            }
        }.run();
    }

    public void verifyCommandDispatchTouchEvent() {
        resetCounts();
        resetPassedValues();
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1.0f, 1.0f,
                1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mTvView.dispatchTouchEvent(event);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mTouchEventCount > 0
                        && motionEventEquals(session.mTouchEvent, event);
            }
        }.run();
    }

    public void verifyCommandDispatchTrackballEvent() {
        resetCounts();
        resetPassedValues();
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1.0f, 1.0f,
                1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(InputDevice.SOURCE_TRACKBALL);
        mTvView.dispatchTouchEvent(event);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mTrackballEventCount > 0
                        && motionEventEquals(session.mTrackballEvent, event);
            }
        }.run();
    }

    public void verifyCommandDispatchGenericMotionEvent() {
        resetCounts();
        resetPassedValues();
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1.0f, 1.0f,
                1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        mTvView.dispatchGenericMotionEvent(event);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mGenricMotionEventCount > 0
                        && motionEventEquals(session.mGenricMotionEvent, event);
            }
        }.run();
    }

    public void verifyCommandTimeShiftPause() {
        resetCounts();
        mTvView.timeShiftPause();
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftPauseCount > 0;
            }
        }.run();
    }

    public void verifyCommandTimeShiftResume() {
        resetCounts();
        mTvView.timeShiftResume();
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftResumeCount > 0;
            }
        }.run();
    }

    public void verifyCommandTimeShiftSeekTo() {
        resetCounts();
        resetPassedValues();
        final long timeMs = 0;
        mTvView.timeShiftSeekTo(timeMs);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftSeekToCount > 0
                        && session.mTimeShiftSeekTo == timeMs;
            }
        }.run();
    }

    public void verifyCommandTimeShiftSetPlaybackParams() {
        resetCounts();
        resetPassedValues();
        final PlaybackParams param = new PlaybackParams().setSpeed(2.0f)
                .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT);
        mTvView.timeShiftSetPlaybackParams(param);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftSetPlaybackParamsCount > 0
                        && playbackParamsEquals(session.mTimeShiftSetPlaybackParams, param);
            }
        }.run();
    }

    public void verifyCommandTimeShiftPlay() {
        resetCounts();
        resetPassedValues();
        final Uri fakeRecordedProgramUri = TvContract.buildRecordedProgramUri(0);
        mTvView.timeShiftPlay(mStubInfo.getId(), fakeRecordedProgramUri);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftPlayCount > 0
                        && Objects.equals(session.mRecordedProgramUri, fakeRecordedProgramUri);
            }
        }.run();
    }

    public void verifyCommandSetTimeShiftPositionCallback() {
        resetCounts();
        mTvView.setTimeShiftPositionCallback(mTimeShiftPositionCallback);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mTimeShiftPositionCallback.mTimeShiftCurrentPositionChanged > 0
                        && mTimeShiftPositionCallback.mTimeShiftStartPositionChanged > 0;
            }
        }.run();
    }

    public void verifyCommandOverlayViewSizeChanged() {
        resetCounts();
        resetPassedValues();
        final int width = 10;
        final int height = 20;
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mTvView.setLayoutParams(new LinearLayout.LayoutParams(width, height));
            }
        });
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mOverlayViewSizeChangedCount > 0
                        && session.mOverlayViewSizeChangedWidth == width
                        && session.mOverlayViewSizeChangedHeight == height;
            }
        }.run();
    }

    public void verifyCommandSendAppPrivateCommand() {
        resetCounts();
        final String action = "android.media.tv.cts.TvInputServiceTest.privateCommand";
        mTvView.sendAppPrivateCommand(action, sDummyBundle);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mAppPrivateCommandCount > 0
                        && bundleEquals(session.mAppPrivateCommandData, sDummyBundle)
                        && TextUtils.equals(session.mAppPrivateCommandAction, action);
            }
        }.run();
    }

    public void verifyCallbackChannelRetuned() {
        resetCounts();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        session.notifyChannelRetuned(fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mChannelRetunedCount > 0
                        && Objects.equals(mCallback.mChannelRetunedUri, fakeChannelUri);
            }
        }.run();
    }

    public void verifyCallbackVideoAvailable() {
        resetCounts();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        session.notifyVideoAvailable();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mVideoAvailableCount > 0;
            }
        }.run();
    }

    public void verifyCallbackVideoUnavailable() {
        resetCounts();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        final int reason = TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING;
        session.notifyVideoUnavailable(reason);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mVideoUnavailableCount > 0
                        && mCallback.mVideoUnavailableReason == reason;
            }
        }.run();
    }

    public void verifyCallbackTracksChanged() {
        resetCounts();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        ArrayList<TvTrackInfo> tracks = new ArrayList<>();
        tracks.add(DUMMY_TRACK);
        session.notifyTracksChanged(tracks);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mTrackChangedCount > 0
                        && Objects.equals(mCallback.mTracksChangedTrackList, tracks);
            }
        }.run();
    }

    public void verifyCallbackVideoSizeChanged() {
        resetCounts();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        ArrayList<TvTrackInfo> tracks = new ArrayList<>();
        tracks.add(DUMMY_TRACK);
        session.notifyTracksChanged(tracks);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mVideoSizeChanged > 0;
            }
        }.run();
    }

    public void verifyCallbackTrackSelected() {
        resetCounts();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        assertNotNull(DUMMY_TRACK);
        session.notifyTrackSelected(DUMMY_TRACK.getType(), DUMMY_TRACK.getId());
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mTrackSelectedCount > 0
                        && mCallback.mTrackSelectedType == DUMMY_TRACK.getType()
                        && TextUtils.equals(DUMMY_TRACK.getId(), mCallback.mTrackSelectedTrackId);
            }
        }.run();
    }

    public void verifyCallbackContentAllowed() {
        resetCounts();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        session.notifyContentAllowed();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mContentAllowedCount > 0;
            }
        }.run();
    }

    public void verifyCallbackContentBlocked() {
        resetCounts();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        final TvContentRating rating = TvContentRating.createRating("android.media.tv", "US_TVPG",
                "US_TVPG_TV_MA", "US_TVPG_S", "US_TVPG_V");
        session.notifyContentBlocked(rating);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mContentBlockedCount > 0
                        && Objects.equals(mCallback.mContentBlockedRating, rating);
            }
        }.run();
    }

    public void verifyCallbackTimeShiftStatusChanged() {
        resetCounts();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        final int status = TvInputManager.TIME_SHIFT_STATUS_AVAILABLE;
        session.notifyTimeShiftStatusChanged(status);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mTimeShiftStatusChangedCount > 0
                        && mCallback.mTimeShiftStatusChangedStatus == status;
            }
        }.run();
    }

    public void verifyCallbackLayoutSurface() {
        resetCounts();
        final int left = 10;
        final int top = 20;
        final int right = 30;
        final int bottom = 40;
        final CountingSession session = CountingTvInputService.sSession;
        assertNotNull(session);
        session.layoutSurface(left, top, right, bottom);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                int childCount = mTvView.getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    View v = mTvView.getChildAt(i);
                    if (v instanceof SurfaceView) {
                        return v.getLeft() == left && v.getTop() == top && v.getRight() == right
                                && v.getBottom() == bottom;
                    }
                }
                return false;
            }
        }.run();
    }

    public static boolean keyEventEquals(KeyEvent event, KeyEvent other) {
        if (event == other) return true;
        if (event == null || other == null) return false;
        return event.getDownTime() == other.getDownTime()
                && event.getEventTime() == other.getEventTime()
                && event.getAction() == other.getAction()
                && event.getKeyCode() == other.getKeyCode()
                && event.getRepeatCount() == other.getRepeatCount()
                && event.getMetaState() == other.getMetaState()
                && event.getDeviceId() == other.getDeviceId()
                && event.getScanCode() == other.getScanCode()
                && event.getFlags() == other.getFlags()
                && event.getSource() == other.getSource()
                && TextUtils.equals(event.getCharacters(), other.getCharacters());
    }

    public static boolean motionEventEquals(MotionEvent event, MotionEvent other) {
        if (event == other) return true;
        if (event == null || other == null) return false;
        return event.getDownTime() == other.getDownTime()
                && event.getEventTime() == other.getEventTime()
                && event.getAction() == other.getAction()
                && event.getX() == other.getX()
                && event.getY() == other.getY()
                && event.getPressure() == other.getPressure()
                && event.getSize() == other.getSize()
                && event.getMetaState() == other.getMetaState()
                && event.getXPrecision() == other.getXPrecision()
                && event.getYPrecision() == other.getYPrecision()
                && event.getDeviceId() == other.getDeviceId()
                && event.getEdgeFlags() == other.getEdgeFlags()
                && event.getSource() == other.getSource();
    }

    public static boolean playbackParamsEquals(PlaybackParams param, PlaybackParams other) {
        if (param == other) return true;
        if (param == null || other == null) return false;
        return param.getAudioFallbackMode() == other.getAudioFallbackMode()
                && param.getSpeed() == other.getSpeed();
    }

    public static boolean bundleEquals(Bundle b, Bundle other) {
        if (b == other) return true;
        if (b == null || other == null) return false;
        if (b.size() != other.size()) return false;

        Set<String> keys = b.keySet();
        for (String key : keys) {
            if (!other.containsKey(key)) return false;
            Object objOne = b.get(key);
            Object objTwo = other.get(key);
            if (!Objects.equals(objOne, objTwo)) {
                return false;
            }
        }
        return true;
    }

    public void initDummyBundle() {
        sDummyBundle = new Bundle();
        sDummyBundle.putString("stringKey", new String("Test String"));
    }

    private void resetCounts() {
        if (CountingTvInputService.sSession != null) {
            CountingTvInputService.sSession.resetCounts();
        }
        if (CountingTvInputService.sRecordingSession != null) {
            CountingTvInputService.sRecordingSession.resetCounts();
        }
        mCallback.resetCounts();
        mTimeShiftPositionCallback.resetCounts();
        mRecordingCallback.resetCounts();
    }

    private void resetPassedValues() {
        if (CountingTvInputService.sSession != null) {
            CountingTvInputService.sSession.resetPassedValues();
        }
        if (CountingTvInputService.sRecordingSession != null) {
            CountingTvInputService.sRecordingSession.resetPassedValues();
        }
        mCallback.resetPassedValues();
        mRecordingCallback.resetPassedValues();
    }

    public static class CountingTvInputService extends StubTvInputService {
        static CountingSession sSession;
        static CountingRecordingSession sRecordingSession;

        @Override
        public Session onCreateSession(String inputId) {
            sSession = new CountingSession(this);
            sSession.setOverlayViewEnabled(true);
            return sSession;
        }

        @Override
        public RecordingSession onCreateRecordingSession(String inputId) {
            sRecordingSession = new CountingRecordingSession(this);
            return sRecordingSession;
        }

        public static class CountingSession extends Session {
            public volatile int mTuneCount;
            public volatile int mTuneWithBundleCount;
            public volatile int mSetStreamVolumeCount;
            public volatile int mSetCaptionEnabledCount;
            public volatile int mSelectTrackCount;
            public volatile int mCreateOverlayView;
            public volatile int mKeyDownCount;
            public volatile int mKeyLongPressCount;
            public volatile int mKeyMultipleCount;
            public volatile int mKeyUpCount;
            public volatile int mTouchEventCount;
            public volatile int mTrackballEventCount;
            public volatile int mGenricMotionEventCount;
            public volatile int mOverlayViewSizeChangedCount;
            public volatile int mTimeShiftPauseCount;
            public volatile int mTimeShiftResumeCount;
            public volatile int mTimeShiftSeekToCount;
            public volatile int mTimeShiftSetPlaybackParamsCount;
            public volatile int mTimeShiftPlayCount;
            public volatile long mTimeShiftGetCurrentPositionCount;
            public volatile long mTimeShiftGetStartPositionCount;
            public volatile int mAppPrivateCommandCount;

            public volatile String mAppPrivateCommandAction;
            public volatile Bundle mAppPrivateCommandData;
            public volatile Uri mTunedChannelUri;
            public volatile Bundle mTuneWithBundleData;
            public volatile Float mStreamVolume;
            public volatile Boolean mCaptionEnabled;
            public volatile Integer mSelectTrackType;
            public volatile String mSelectTrackId;
            public volatile Integer mKeyDownCode;
            public volatile KeyEvent mKeyDownEvent;
            public volatile Integer mKeyLongPressCode;
            public volatile KeyEvent mKeyLongPressEvent;
            public volatile Integer mKeyMultipleCode;
            public volatile Integer mKeyMultipleNumber;
            public volatile KeyEvent mKeyMultipleEvent;
            public volatile Integer mKeyUpCode;
            public volatile KeyEvent mKeyUpEvent;
            public volatile MotionEvent mTouchEvent;
            public volatile MotionEvent mTrackballEvent;
            public volatile MotionEvent mGenricMotionEvent;
            public volatile Long mTimeShiftSeekTo;
            public volatile PlaybackParams mTimeShiftSetPlaybackParams;
            public volatile Uri mRecordedProgramUri;
            public volatile Integer mOverlayViewSizeChangedWidth;
            public volatile Integer mOverlayViewSizeChangedHeight;

            CountingSession(Context context) {
                super(context);
            }

            public void resetCounts() {
                mTuneCount = 0;
                mTuneWithBundleCount = 0;
                mSetStreamVolumeCount = 0;
                mSetCaptionEnabledCount = 0;
                mSelectTrackCount = 0;
                mCreateOverlayView = 0;
                mKeyDownCount = 0;
                mKeyLongPressCount = 0;
                mKeyMultipleCount = 0;
                mKeyUpCount = 0;
                mTouchEventCount = 0;
                mTrackballEventCount = 0;
                mGenricMotionEventCount = 0;
                mOverlayViewSizeChangedCount = 0;
                mTimeShiftPauseCount = 0;
                mTimeShiftResumeCount = 0;
                mTimeShiftSeekToCount = 0;
                mTimeShiftSetPlaybackParamsCount = 0;
                mTimeShiftPlayCount = 0;
                mTimeShiftGetCurrentPositionCount = 0;
                mTimeShiftGetStartPositionCount = 0;
                mAppPrivateCommandCount = 0;
            }

            public void resetPassedValues() {
                mAppPrivateCommandAction = null;
                mAppPrivateCommandData = null;
                mTunedChannelUri = null;
                mTuneWithBundleData = null;
                mStreamVolume = null;
                mCaptionEnabled = null;
                mSelectTrackType = null;
                mSelectTrackId = null;
                mKeyDownCode = null;
                mKeyDownEvent = null;
                mKeyLongPressCode = null;
                mKeyLongPressEvent = null;
                mKeyMultipleCode = null;
                mKeyMultipleNumber = null;
                mKeyMultipleEvent = null;
                mKeyUpCode = null;
                mKeyUpEvent = null;
                mTouchEvent = null;
                mTrackballEvent = null;
                mGenricMotionEvent = null;
                mTimeShiftSeekTo = null;
                mTimeShiftSetPlaybackParams = null;
                mRecordedProgramUri = null;
                mOverlayViewSizeChangedWidth = null;
                mOverlayViewSizeChangedHeight = null;
            }

            @Override
            public void onAppPrivateCommand(String action, Bundle data) {
                mAppPrivateCommandCount++;
                mAppPrivateCommandAction = action;
                mAppPrivateCommandData = data;
            }

            @Override
            public void onRelease() {
            }

            @Override
            public boolean onSetSurface(Surface surface) {
                return false;
            }

            @Override
            public boolean onTune(Uri channelUri) {
                mTuneCount++;
                mTunedChannelUri = channelUri;
                return false;
            }

            @Override
            public boolean onTune(Uri channelUri, Bundle data) {
                mTuneWithBundleCount++;
                mTuneWithBundleData = data;
                // Also calls {@link #onTune(Uri)} since it will never be called if the
                // implementation overrides {@link #onTune(Uri, Bundle)}.
                onTune(channelUri);
                return false;
            }

            @Override
            public void onSetStreamVolume(float volume) {
                mSetStreamVolumeCount++;
                mStreamVolume = volume;
            }

            @Override
            public void onSetCaptionEnabled(boolean enabled) {
                mSetCaptionEnabledCount++;
                mCaptionEnabled = enabled;
            }

            @Override
            public boolean onSelectTrack(int type, String id) {
                mSelectTrackCount++;
                mSelectTrackType = type;
                mSelectTrackId = id;
                return false;
            }

            @Override
            public View onCreateOverlayView() {
                mCreateOverlayView++;
                return null;
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                mKeyDownCount++;
                mKeyDownCode = keyCode;
                mKeyDownEvent = event;
                return false;
            }

            @Override
            public boolean onKeyLongPress(int keyCode, KeyEvent event) {
                mKeyLongPressCount++;
                mKeyLongPressCode = keyCode;
                mKeyLongPressEvent = event;
                return false;
            }

            @Override
            public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
                mKeyMultipleCount++;
                mKeyMultipleCode = keyCode;
                mKeyMultipleNumber = count;
                mKeyMultipleEvent = event;
                return false;
            }

            @Override
            public boolean onKeyUp(int keyCode, KeyEvent event) {
                mKeyUpCount++;
                mKeyUpCode = keyCode;
                mKeyUpEvent = event;
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                mTouchEventCount++;
                mTouchEvent = event;
                return false;
            }

            @Override
            public boolean onTrackballEvent(MotionEvent event) {
                mTrackballEventCount++;
                mTrackballEvent = event;
                return false;
            }

            @Override
            public boolean onGenericMotionEvent(MotionEvent event) {
                mGenricMotionEventCount++;
                mGenricMotionEvent = event;
                return false;
            }

            @Override
            public void onTimeShiftPause() {
                mTimeShiftPauseCount++;
            }

            @Override
            public void onTimeShiftResume() {
                mTimeShiftResumeCount++;
            }

            @Override
            public void onTimeShiftSeekTo(long timeMs) {
                mTimeShiftSeekToCount++;
                mTimeShiftSeekTo = timeMs;
            }

            @Override
            public void onTimeShiftSetPlaybackParams(PlaybackParams param) {
                mTimeShiftSetPlaybackParamsCount++;
                mTimeShiftSetPlaybackParams = param;
            }

            @Override
            public void onTimeShiftPlay(Uri recordedProgramUri) {
                mTimeShiftPlayCount++;
                mRecordedProgramUri = recordedProgramUri;
            }

            @Override
            public long onTimeShiftGetCurrentPosition() {
                return ++mTimeShiftGetCurrentPositionCount;
            }

            @Override
            public long onTimeShiftGetStartPosition() {
                return ++mTimeShiftGetStartPositionCount;
            }

            @Override
            public void onOverlayViewSizeChanged(int width, int height) {
                mOverlayViewSizeChangedCount++;
                mOverlayViewSizeChangedWidth = width;
                mOverlayViewSizeChangedHeight = height;
            }
        }

        public static class CountingRecordingSession extends RecordingSession {
            public volatile int mTuneCount;
            public volatile int mTuneWithBundleCount;
            public volatile int mReleaseCount;
            public volatile int mStartRecordingCount;
            public volatile int mStopRecordingCount;
            public volatile int mAppPrivateCommandCount;

            public volatile Uri mTunedChannelUri;
            public volatile Bundle mTuneWithBundleData;
            public volatile Uri mProgramHint;
            public volatile String mAppPrivateCommandAction;
            public volatile Bundle mAppPrivateCommandData;

            CountingRecordingSession(Context context) {
                super(context);
            }

            public void resetCounts() {
                mTuneCount = 0;
                mTuneWithBundleCount = 0;
                mReleaseCount = 0;
                mStartRecordingCount = 0;
                mStopRecordingCount = 0;
                mAppPrivateCommandCount = 0;
            }

            public void resetPassedValues() {
                mTunedChannelUri = null;
                mTuneWithBundleData = null;
                mProgramHint = null;
                mAppPrivateCommandAction = null;
                mAppPrivateCommandData = null;
            }

            @Override
            public void onTune(Uri channelUri) {
                mTuneCount++;
                mTunedChannelUri = channelUri;
            }

            @Override
            public void onTune(Uri channelUri, Bundle data) {
                mTuneWithBundleCount++;
                mTuneWithBundleData = data;
                // Also calls {@link #onTune(Uri)} since it will never be called if the
                // implementation overrides {@link #onTune(Uri, Bundle)}.
                onTune(channelUri);
            }

            @Override
            public void onRelease() {
                mReleaseCount++;
            }

            @Override
            public void onStartRecording(Uri programHint) {
                mStartRecordingCount++;
                mProgramHint = programHint;
            }

            @Override
            public void onStopRecording() {
                mStopRecordingCount++;
            }

            @Override
            public void onAppPrivateCommand(String action, Bundle data) {
                mAppPrivateCommandCount++;
                mAppPrivateCommandAction = action;
                mAppPrivateCommandData = data;
            }
        }
    }

    private static class StubRecordingCallback extends TvRecordingClient.RecordingCallback {
        private int mTunedCount;
        private int mRecordingStoppedCount;
        private int mErrorCount;
        private int mConnectionFailedCount;
        private int mDisconnectedCount;

        private Uri mTunedChannelUri;
        private Uri mRecordedProgramUri;
        private Integer mError;

        @Override
        public void onTuned(Uri channelUri) {
            mTunedCount++;
            mTunedChannelUri = channelUri;
        }

        @Override
        public void onRecordingStopped(Uri recordedProgramUri) {
            mRecordingStoppedCount++;
            mRecordedProgramUri = recordedProgramUri;
        }

        @Override
        public void onError(int error) {
            mErrorCount++;
            mError = error;
        }

        @Override
        public void onConnectionFailed(String inputId) {
            mConnectionFailedCount++;
        }

        @Override
        public void onDisconnected(String inputId) {
            mDisconnectedCount++;
        }

        public void resetCounts() {
            mTunedCount = 0;
            mRecordingStoppedCount = 0;
            mErrorCount = 0;
            mConnectionFailedCount = 0;
            mDisconnectedCount = 0;
        }

        public void resetPassedValues() {
            mTunedChannelUri = null;
            mRecordedProgramUri = null;
            mError = null;
        }
    }
}
