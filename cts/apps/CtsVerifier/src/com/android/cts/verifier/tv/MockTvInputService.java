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

package com.android.cts.verifier.tv;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("NewApi")
public class MockTvInputService extends TvInputService {
    private static final String TAG = "MockTvInputService";

    private static final String BROADCAST_ACTION = "action";
    private static final String SELECT_TRACK_TYPE = "type";
    private static final String SELECT_TRACK_ID = "id";
    private static final String CAPTION_ENABLED = "enabled";
    private static final String PAUSE_CALLED = "pause_called";
    private static final float DISPLAY_RATIO_EPSILON = 0.01f;

    private static final Object sLock = new Object();
    private static Callback sTuneCallback = null;
    private static Callback sOverlayViewCallback = null;
    private static Callback sBroadcastCallback = null;
    private static Callback sUnblockContentCallback = null;
    private static Callback sSelectTrackCallback = null;
    private static Callback sSetCaptionEnabledCallback = null;
    // Callbacks for time shift.
    private static Callback sResumeAfterPauseCallback = null;
    private static Callback sPositionTrackingCallback = null;
    private static Callback sRewindCallback = null;
    private static Callback sFastForwardCallback = null;
    private static Callback sSeekToPreviousCallback = null;
    private static Callback sSeekToNextCallback = null;
    private static Callback sOverlayViewSizeChangedCallback = null;

    private static TvContentRating sRating = null;

    static final TvTrackInfo sEngAudioTrack =
            new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, "audio_eng")
            .setAudioChannelCount(2)
            .setAudioSampleRate(48000)
            .setLanguage("eng")
            .build();
    static final TvTrackInfo sSpaAudioTrack =
            new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, "audio_spa")
            .setAudioChannelCount(2)
            .setAudioSampleRate(48000)
            .setLanguage("spa")
            .build();
    static final TvTrackInfo sEngSubtitleTrack =
            new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "subtitle_eng")
            .setLanguage("eng")
            .build();
    static final TvTrackInfo sKorSubtitleTrack =
            new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "subtitle_kor")
            .setLanguage("kor")
            .build();
    // These parameters make the display aspect ratio of sDummyVideoTrack be 4:3,
    // which is one of common standards.
    static final TvTrackInfo sDummyVideoTrack =
            new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, "video_dummy")
            .setVideoWidth(704)
            .setVideoHeight(480)
            .setVideoPixelAspectRatio(0.909f)
            .setVideoFrameRate(60)
            .build();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (sLock) {
                if (sBroadcastCallback != null) {
                    String expectedAction =
                            sBroadcastCallback.getBundle().getString(BROADCAST_ACTION);
                    if (intent.getAction().equals(expectedAction)) {
                        sBroadcastCallback.post();
                        sBroadcastCallback = null;
                    }
                }
            }
        }
    };

    static void expectTune(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sTuneCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectBroadcast(View postTarget, String action, Runnable successCallback) {
        synchronized (sLock) {
            sBroadcastCallback = new Callback(postTarget, successCallback);
            sBroadcastCallback.getBundle().putString(BROADCAST_ACTION, action);
        }
    }

    static void expectUnblockContent(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sUnblockContentCallback = new Callback(postTarget, successCallback);
        }
    }

    static void setBlockRating(TvContentRating rating) {
        synchronized (sLock) {
            sRating = rating;
        }
    }

    static void expectOverlayView(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sOverlayViewCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectSelectTrack(int type, String id, View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sSelectTrackCallback = new Callback(postTarget, successCallback);
            sSelectTrackCallback.getBundle().putInt(SELECT_TRACK_TYPE, type);
            sSelectTrackCallback.getBundle().putString(SELECT_TRACK_ID, id);
        }
    }

    static void expectSetCaptionEnabled(boolean enabled, View postTarget,
            Runnable successCallback) {
        synchronized (sLock) {
            sSetCaptionEnabledCallback = new Callback(postTarget, successCallback);
            sSetCaptionEnabledCallback.getBundle().putBoolean(CAPTION_ENABLED, enabled);
        }
    }

    static void expectResumeAfterPause(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sResumeAfterPauseCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectPositionTracking(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sPositionTrackingCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectRewind(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sRewindCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectFastForward(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sFastForwardCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectSeekToPrevious(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sSeekToPreviousCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectSeekToNext(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sSeekToNextCallback = new Callback(postTarget, successCallback);
        }
    }

    static void expectedVideoAspectRatio(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sOverlayViewSizeChangedCallback = new Callback(postTarget, successCallback);
        }
    }

    static String getInputId(Context context) {
        return TvContract.buildInputId(new ComponentName(context,
                        MockTvInputService.class.getName()));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
        intentFilter.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    public Session onCreateSession(String inputId) {
        Session session = new MockSessionImpl(this);
        session.setOverlayViewEnabled(true);
        return session;
    }

    private static class MockSessionImpl extends Session {
        private static final int MSG_SEEK = 1000;
        private static final int SEEK_DELAY_MS = 300;

        private final Context mContext;
        private Surface mSurface = null;
        private List<TvTrackInfo> mTracks = new ArrayList<>();

        private long mRecordStartTimeMs;
        private long mPausedTimeMs;
        // The time in milliseconds when the current position is lastly updated.
        private long mLastCurrentPositionUpdateTimeMs;
        // The current playback position.
        private long mCurrentPositionMs;
        // The current playback speed rate.
        private float mSpeed;

        private final Handler mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_SEEK) {
                    // Actually, this input doesn't play any videos, it just shows the image.
                    // So we should simulate the playback here by changing the current playback
                    // position periodically in order to test the time shift.
                    // If the playback is paused, the current playback position doesn't need to be
                    // changed.
                    if (mPausedTimeMs == 0) {
                        long currentTimeMs = System.currentTimeMillis();
                        mCurrentPositionMs += (long) ((currentTimeMs
                                - mLastCurrentPositionUpdateTimeMs) * mSpeed);
                        mCurrentPositionMs = Math.max(mRecordStartTimeMs,
                                Math.min(mCurrentPositionMs, currentTimeMs));
                        mLastCurrentPositionUpdateTimeMs = currentTimeMs;
                    }
                    sendEmptyMessageDelayed(MSG_SEEK, SEEK_DELAY_MS);
                }
                super.handleMessage(msg);
            }
        };

        private MockSessionImpl(Context context) {
            super(context);
            mContext = context;
            mTracks.add(sEngAudioTrack);
            mTracks.add(sSpaAudioTrack);
            mTracks.add(sEngSubtitleTrack);
            mTracks.add(sKorSubtitleTrack);
            mTracks.add(sDummyVideoTrack);
        }

        @Override
        public void onRelease() {
        }

        private void draw() {
            Surface surface = mSurface;
            if (surface == null) return;
            if (!surface.isValid()) return;

            Canvas c = surface.lockCanvas(null);
            if (c == null) return;
            try {
                Bitmap b = BitmapFactory.decodeResource(
                        mContext.getResources(), R.drawable.icon);
                int srcWidth = b.getWidth();
                int srcHeight = b.getHeight();
                int dstWidth = c.getWidth();
                int dstHeight = c.getHeight();
                c.drawColor(Color.BLACK);
                c.drawBitmap(b, new Rect(0, 0, srcWidth, srcHeight),
                        new Rect(10, 10, dstWidth - 10, dstHeight - 10), null);
            } finally {
                surface.unlockCanvasAndPost(c);
            }
        }

        @Override
        public View onCreateOverlayView() {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                    LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.tv_overlay, null);
            TextView textView = (TextView) view.findViewById(R.id.overlay_view_text);
            textView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    Callback overlayViewCallback = null;
                    synchronized (sLock) {
                        overlayViewCallback = sOverlayViewCallback;
                        sOverlayViewCallback = null;
                    }
                    if (overlayViewCallback != null) {
                        overlayViewCallback.post();
                    }
                }
            });
            return view;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            mSurface = surface;
            if (surface != null) {
                draw();
            }
            return true;
        }

        @Override
        public void onSetStreamVolume(float volume) {
        }

        @Override
        public boolean onTune(Uri channelUri) {
            synchronized (sLock) {
                if (sRating != null) {
                    notifyContentBlocked(sRating);
                }
                if (sTuneCallback != null) {
                    sTuneCallback.post();
                    sTuneCallback = null;
                }
                if (sRating == null) {
                    notifyContentAllowed();
                }
            }
            notifyVideoAvailable();
            notifyTracksChanged(mTracks);
            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, sEngAudioTrack.getId());
            notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, null);
            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, sDummyVideoTrack.getId());

            notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            mRecordStartTimeMs = mCurrentPositionMs = mLastCurrentPositionUpdateTimeMs
                    = System.currentTimeMillis();
            mPausedTimeMs = 0;
            mHandler.sendEmptyMessageDelayed(MSG_SEEK, SEEK_DELAY_MS);
            mSpeed = 1;
            return true;
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            synchronized (sLock) {
                if (sSelectTrackCallback != null) {
                    Bundle bundle = sSelectTrackCallback.getBundle();
                    if (bundle.getInt(SELECT_TRACK_TYPE) == type
                            && bundle.getString(SELECT_TRACK_ID).equals(trackId)) {
                        sSelectTrackCallback.post();
                        sSelectTrackCallback = null;
                    }
                }
            }
            notifyTrackSelected(type, trackId);
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            synchronized (sLock) {
                if (sSetCaptionEnabledCallback != null) {
                    Bundle bundle = sSetCaptionEnabledCallback.getBundle();
                    if (bundle.getBoolean(CAPTION_ENABLED) == enabled) {
                        sSetCaptionEnabledCallback.post();
                        sSetCaptionEnabledCallback = null;
                    }
                }
            }
        }

        @Override
        public void onUnblockContent(TvContentRating unblockedRating) {
            synchronized (sLock) {
                if (sRating != null && sRating.equals(unblockedRating)) {
                    sUnblockContentCallback.post();
                    sRating = null;
                    notifyContentAllowed();
                }
            }
        }

        @Override
        public long onTimeShiftGetCurrentPosition() {
            synchronized (sLock) {
                if (sPositionTrackingCallback != null) {
                    sPositionTrackingCallback.post();
                    sPositionTrackingCallback = null;
                }
            }
            Log.d(TAG, "currentPositionMs=" + mCurrentPositionMs);
            return mCurrentPositionMs;
        }

        @Override
        public long onTimeShiftGetStartPosition() {
            return mRecordStartTimeMs;
        }

        @Override
        public void onTimeShiftPause() {
            synchronized (sLock) {
                if (sResumeAfterPauseCallback != null) {
                    sResumeAfterPauseCallback.mBundle.putBoolean(PAUSE_CALLED, true);
                }
            }
            mCurrentPositionMs = mPausedTimeMs = mLastCurrentPositionUpdateTimeMs
                    = System.currentTimeMillis();
        }

        @Override
        public void onTimeShiftResume() {
            synchronized (sLock) {
                if (sResumeAfterPauseCallback != null
                        && sResumeAfterPauseCallback.mBundle.getBoolean(PAUSE_CALLED)) {
                    sResumeAfterPauseCallback.post();
                    sResumeAfterPauseCallback = null;
                }
            }
            mSpeed = 1;
            mPausedTimeMs = 0;
            mLastCurrentPositionUpdateTimeMs = System.currentTimeMillis();
        }

        @Override
        public void onTimeShiftSeekTo(long timeMs) {
            synchronized (sLock) {
                if (mCurrentPositionMs > timeMs) {
                    if (sSeekToPreviousCallback != null) {
                        sSeekToPreviousCallback.post();
                        sSeekToPreviousCallback = null;
                    }
                } else if (mCurrentPositionMs < timeMs) {
                    if (sSeekToNextCallback != null) {
                        sSeekToNextCallback.post();
                        sSeekToNextCallback = null;
                    }
                }
            }
            mLastCurrentPositionUpdateTimeMs = System.currentTimeMillis();
            mCurrentPositionMs = Math.max(mRecordStartTimeMs,
                    Math.min(timeMs, mLastCurrentPositionUpdateTimeMs));
        }

        @Override
        public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
            synchronized(sLock) {
                if (params != null) {
                    if (params.getSpeed() > 1) {
                        if (sFastForwardCallback != null) {
                            sFastForwardCallback.post();
                            sFastForwardCallback = null;
                        }
                    } else if (params.getSpeed() < 1) {
                        if (sRewindCallback != null) {
                            sRewindCallback.post();
                            sRewindCallback = null;
                        }
                    }
                }
            }
            mSpeed = params.getSpeed();
        }

        @Override
        public void onOverlayViewSizeChanged(int width, int height) {
            synchronized(sLock) {
                draw();
                if (sOverlayViewSizeChangedCallback != null) {
                    if (sDummyVideoTrack.getVideoHeight() <= 0
                            || sDummyVideoTrack.getVideoWidth() <= 0) {
                        Log.w(TAG,
                                "The width or height of the selected video track is invalid.");
                    } else if (height <= 0 || width <= 0) {
                        Log.w(TAG, "The width or height of the OverlayView is incorrect.");
                    } else if (Math.abs((float)width / height
                            - (float)sDummyVideoTrack.getVideoWidth()
                            * sDummyVideoTrack.getVideoPixelAspectRatio()
                            / sDummyVideoTrack.getVideoHeight()) < DISPLAY_RATIO_EPSILON) {
                        // Verify the video display aspect ratio is correct
                        // and setVideoPixelAspectRatio() works for the view size.
                        sOverlayViewSizeChangedCallback.post();
                        sOverlayViewSizeChangedCallback = null;
                    }
                }
            }
        }
    }

    private static class Callback {
        private final View mPostTarget;
        private final Runnable mAction;
        private final Bundle mBundle = new Bundle();

        Callback(View postTarget, Runnable action) {
            mPostTarget = postTarget;
            mAction = action;
        }

        public void post() {
            mPostTarget.post(mAction);
        }

        public Bundle getBundle() {
            return mBundle;
        }
    }
}
