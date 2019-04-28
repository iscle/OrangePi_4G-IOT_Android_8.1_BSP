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
 * limitations under the License.
 */

package com.android.tv.testinput;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.PlaybackParams;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvContract.RecordedPrograms;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;

import com.android.tv.input.TunerHelper;
import com.android.tv.testing.ChannelInfo;
import com.android.tv.testing.testinput.ChannelState;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Simple TV input service which provides test channels.
 */
public class TestTvInputService extends TvInputService {
    private static final String TAG = "TestTvInputService";
    private static final int REFRESH_DELAY_MS = 1000 / 5;
    private static final boolean DEBUG = false;

    // Consider the command delivering time from Live TV.
    private static final long MAX_COMMAND_DELAY = TimeUnit.SECONDS.toMillis(3);

    private final TestInputControl mBackend = TestInputControl.getInstance();

    private TunerHelper mTunerHelper;

    public static String buildInputId(Context context) {
        return TvContract.buildInputId(new ComponentName(context, TestTvInputService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBackend.init(this, buildInputId(this));
        mTunerHelper = new TunerHelper(getResources().getInteger(R.integer.tuner_count));
    }

    @Override
    public Session onCreateSession(String inputId) {
        Log.v(TAG, "Creating session for " + inputId);
        // onCreateSession always succeeds because this session can be used to play the recorded
        // program.
        return new SimpleSessionImpl(this);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public RecordingSession onCreateRecordingSession(String inputId) {
        Log.v(TAG, "Creating recording session for " + inputId);
        if (!mTunerHelper.tunerAvailableForRecording()) {
            return null;
        }
        return new SimpleRecordingSessionImpl(this, inputId);
    }

    /**
     * Simple session implementation that just display some text.
     */
    private class SimpleSessionImpl extends Session {
        private static final int MSG_SEEK = 1000;
        private static final int SEEK_DELAY_MS = 300;

        private final Paint mTextPaint = new Paint();
        private final DrawRunnable mDrawRunnable = new DrawRunnable();
        private Surface mSurface = null;
        private Uri mChannelUri = null;
        private ChannelInfo mChannel = null;
        private ChannelState mCurrentState = null;
        private String mCurrentVideoTrackId = null;
        private String mCurrentAudioTrackId = null;

        private long mRecordStartTimeMs;
        private long mPausedTimeMs;
        // The time in milliseconds when the current position is lastly updated.
        private long mLastCurrentPositionUpdateTimeMs;
        // The current playback position.
        private long mCurrentPositionMs;
        // The current playback speed rate.
        private float mSpeed;

        private final Handler mHandler = new Handler(Looper.myLooper()) {
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

        SimpleSessionImpl(Context context) {
            super(context);
            mTextPaint.setColor(Color.BLACK);
            mTextPaint.setTextSize(150);
            mHandler.post(mDrawRunnable);
            if (DEBUG) {
                Log.v(TAG, "Created session " + this);
            }
        }

        private void setAudioTrack(String selectedAudioTrackId) {
            Log.i(TAG, "Set audio track to " + selectedAudioTrackId);
            mCurrentAudioTrackId = selectedAudioTrackId;
            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, mCurrentAudioTrackId);
        }

        private void setVideoTrack(String selectedVideoTrackId) {
            Log.i(TAG, "Set video track to " + selectedVideoTrackId);
            mCurrentVideoTrackId = selectedVideoTrackId;
            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, mCurrentVideoTrackId);
        }

        @Override
        public void onRelease() {
            if (DEBUG) {
                Log.v(TAG, "Releasing session " + this);
            }
            mTunerHelper.stopTune(mChannelUri);
            mDrawRunnable.cancel();
            mHandler.removeCallbacks(mDrawRunnable);
            mSurface = null;
            mChannelUri = null;
            mChannel = null;
            mCurrentState = null;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            synchronized (mDrawRunnable) {
                mSurface = surface;
            }
            if (surface != null) {
                if (DEBUG) {
                    Log.v(TAG, "Surface set");
                }
            } else {
                if (DEBUG) {
                    Log.v(TAG, "Surface unset");
                }
            }

            return true;
        }

        @Override
        public void onSurfaceChanged(int format, int width, int height) {
            super.onSurfaceChanged(format, width, height);
            Log.d(TAG, "format=" + format + " width=" + width + " height=" + height);
        }

        @Override
        public void onSetStreamVolume(float volume) {
            // No-op
        }

        @Override
        public boolean onTune(Uri channelUri) {
            Log.i(TAG, "Tune to " + channelUri);
            mTunerHelper.stopTune(mChannelUri);
            mChannelUri = channelUri;
            ChannelInfo info = mBackend.getChannelInfo(channelUri);
            synchronized (mDrawRunnable) {
                if (info == null || mChannel == null
                        || mChannel.originalNetworkId != info.originalNetworkId) {
                    mCurrentState = null;
                }
                mChannel = info;
                mCurrentVideoTrackId = null;
                mCurrentAudioTrackId = null;
            }
            if (mChannel == null) {
                Log.i(TAG, "Channel not found for " + channelUri);
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
            } else if (!mTunerHelper.tune(channelUri, false)) {
                Log.i(TAG, "No available tuner for " + channelUri);
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
            } else {
                Log.i(TAG, "Tuning to " + mChannel);
            }
            notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            mRecordStartTimeMs = mCurrentPositionMs = mLastCurrentPositionUpdateTimeMs
                    = System.currentTimeMillis();
            mPausedTimeMs = 0;
            mHandler.sendEmptyMessageDelayed(MSG_SEEK, SEEK_DELAY_MS);
            mSpeed = 1;
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            // No-op
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            Log.d(TAG, "onKeyDown (keyCode=" + keyCode + ", event=" + event + ")");
            return true;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            Log.d(TAG, "onKeyUp (keyCode=" + keyCode + ", event=" + event + ")");
            return true;
        }

        @Override
        public long onTimeShiftGetCurrentPosition() {
            Log.d(TAG, "currentPositionMs=" + mCurrentPositionMs);
            return mCurrentPositionMs;
        }

        @Override
        public long onTimeShiftGetStartPosition() {
            return mRecordStartTimeMs;
        }

        @Override
        public void onTimeShiftPause() {
            mCurrentPositionMs = mPausedTimeMs = mLastCurrentPositionUpdateTimeMs
                    = System.currentTimeMillis();
        }

        @Override
        public void onTimeShiftResume() {
            mSpeed = 1;
            mPausedTimeMs = 0;
            mLastCurrentPositionUpdateTimeMs = System.currentTimeMillis();
        }

        @Override
        public void onTimeShiftSeekTo(long timeMs) {
            mLastCurrentPositionUpdateTimeMs = System.currentTimeMillis();
            mCurrentPositionMs = Math.max(mRecordStartTimeMs,
                    Math.min(timeMs, mLastCurrentPositionUpdateTimeMs));
        }

        @Override
        public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
            mSpeed = params.getSpeed();
        }

        private final class DrawRunnable implements Runnable {
            private volatile boolean mIsCanceled = false;

            @Override
            public void run() {
                if (mIsCanceled) {
                    return;
                }
                if (DEBUG) {
                    Log.v(TAG, "Draw task running");
                }
                boolean updatedState = false;
                ChannelState oldState;
                ChannelState newState = null;
                Surface currentSurface;
                ChannelInfo currentChannel;

                synchronized (this) {
                    oldState = mCurrentState;
                    currentSurface = mSurface;
                    currentChannel = mChannel;
                    if (currentChannel != null) {
                        newState = mBackend.getChannelState(currentChannel.originalNetworkId);
                        if (oldState == null || newState.getVersion() > oldState.getVersion()) {
                            mCurrentState = newState;
                            updatedState = true;
                        }
                    } else {
                        mCurrentState = null;
                    }

                    if (currentSurface != null) {
                        String now = new Date(mCurrentPositionMs).toString();
                        String name = currentChannel == null ? "Null" : currentChannel.name;
                        try {
                            Canvas c = currentSurface.lockCanvas(null);
                            c.drawColor(0xFF888888);
                            c.drawText(name, 100f, 200f, mTextPaint);
                            c.drawText(now, 100f, 400f, mTextPaint);
                            // Assuming c.drawXXX will never fail.
                            currentSurface.unlockCanvasAndPost(c);
                        } catch (IllegalArgumentException e) {
                            // The surface might have been abandoned. Ignore the exception.
                        }
                        if (DEBUG) {
                            Log.v(TAG, "Post to canvas");
                        }
                    } else {
                        if (DEBUG) {
                            Log.v(TAG, "No surface");
                        }
                    }
                }
                if (updatedState) {
                    update(oldState, newState, currentChannel);
                }

                if (!mIsCanceled) {
                    mHandler.postDelayed(this, REFRESH_DELAY_MS);
                }
            }

            private void update(ChannelState oldState, ChannelState newState,
                    ChannelInfo currentChannel) {
                Log.i(TAG, "Updating channel " + currentChannel.number + " state to " + newState);
                notifyTracksChanged(newState.getTrackInfoList());
                if (oldState == null || oldState.getTuneStatus() != newState.getTuneStatus()) {
                    if (newState.getTuneStatus() == ChannelState.TUNE_STATUS_VIDEO_AVAILABLE) {
                        notifyVideoAvailable();
                        //TODO handle parental controls.
                        notifyContentAllowed();
                        setAudioTrack(newState.getSelectedAudioTrackId());
                        setVideoTrack(newState.getSelectedVideoTrackId());
                    } else {
                        notifyVideoUnavailable(newState.getTuneStatus());
                    }
                }
            }

            public void cancel() {
                mIsCanceled = true;
            }
        }
    }

    private class SimpleRecordingSessionImpl extends RecordingSession {
        private final String[] PROGRAM_PROJECTION = {
                Programs.COLUMN_TITLE,
                Programs.COLUMN_EPISODE_TITLE,
                Programs.COLUMN_SHORT_DESCRIPTION,
                Programs.COLUMN_POSTER_ART_URI,
                Programs.COLUMN_THUMBNAIL_URI,
                Programs.COLUMN_CANONICAL_GENRE,
                Programs.COLUMN_CONTENT_RATING,
                Programs.COLUMN_START_TIME_UTC_MILLIS,
                Programs.COLUMN_END_TIME_UTC_MILLIS,
                Programs.COLUMN_VIDEO_WIDTH,
                Programs.COLUMN_VIDEO_HEIGHT,
                Programs.COLUMN_SEASON_DISPLAY_NUMBER,
                Programs.COLUMN_SEASON_TITLE,
                Programs.COLUMN_EPISODE_DISPLAY_NUMBER,
        };

        private final String mInputId;
        private long mStartTime;
        private long mEndTime;
        private Uri mChannelUri;
        private Uri mProgramHintUri;

        public SimpleRecordingSessionImpl(Context context, String inputId) {
            super(context);
            mInputId = inputId;
        }

        @Override
        public void onTune(Uri uri) {
            Log.i(TAG, "SimpleReccordingSesesionImpl: onTune()");
            mTunerHelper.stopRecording(mChannelUri);
            mChannelUri = uri;
            ChannelInfo channel = mBackend.getChannelInfo(uri);
            if (channel == null) {
                notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            } else if (!mTunerHelper.tune(uri, true)) {
                notifyError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY);
            } else {
                notifyTuned(uri);
            }
        }

        @Override
        public void onStartRecording(Uri programHintUri) {
            Log.i(TAG, "SimpleReccordingSesesionImpl: onStartRecording()");
            mStartTime = System.currentTimeMillis();
            mProgramHintUri = programHintUri;
        }

        @Override
        public void onStopRecording() {
            Log.i(TAG, "SimpleReccordingSesesionImpl: onStopRecording()");
            mEndTime = System.currentTimeMillis();
            final long startTime = mStartTime;
            final long endTime = mEndTime;
            final Uri programHintUri = mProgramHintUri;
            final Uri channelUri = mChannelUri;
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... arg0) {
                    long time = System.currentTimeMillis();
                    if (programHintUri != null) {
                        // Retrieves program info from mProgramHintUri
                        try (Cursor c = getContentResolver().query(programHintUri,
                                PROGRAM_PROJECTION, null, null, null)) {
                            if (c != null && c.getCount() > 0) {
                                storeRecordedProgram(c, startTime, endTime);
                                return null;
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error querying " + this, e);
                        }
                    }
                    // Retrieves the current program
                    try (Cursor c = getContentResolver().query(
                            TvContract.buildProgramsUriForChannel(channelUri, startTime,
                                    endTime - startTime < MAX_COMMAND_DELAY ? startTime :
                                            endTime - MAX_COMMAND_DELAY),
                            PROGRAM_PROJECTION, null, null, null)) {
                        if (c != null && c.getCount() == 1) {
                            storeRecordedProgram(c, startTime, endTime);
                            return null;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error querying " + this, e);
                    }
                    storeRecordedProgram(null, startTime, endTime);
                    return null;
                }

                private void storeRecordedProgram(Cursor c, long startTime, long endTime) {
                    ContentValues values = new ContentValues();
                    values.put(RecordedPrograms.COLUMN_INPUT_ID, mInputId);
                    values.put(RecordedPrograms.COLUMN_CHANNEL_ID,
                            ContentUris.parseId(channelUri));
                    values.put(RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
                            endTime - startTime);
                    if (c != null) {
                        int index = 0;
                        c.moveToNext();
                        values.put(Programs.COLUMN_TITLE, c.getString(index++));
                        values.put(Programs.COLUMN_EPISODE_TITLE, c.getString(index++));
                        values.put(Programs.COLUMN_SHORT_DESCRIPTION, c.getString(index++));
                        values.put(Programs.COLUMN_POSTER_ART_URI, c.getString(index++));
                        values.put(Programs.COLUMN_THUMBNAIL_URI, c.getString(index++));
                        values.put(Programs.COLUMN_CANONICAL_GENRE, c.getString(index++));
                        values.put(Programs.COLUMN_CONTENT_RATING, c.getString(index++));
                        values.put(Programs.COLUMN_START_TIME_UTC_MILLIS, c.getLong(index++));
                        values.put(Programs.COLUMN_END_TIME_UTC_MILLIS, c.getLong(index++));
                        values.put(Programs.COLUMN_VIDEO_WIDTH, c.getLong(index++));
                        values.put(Programs.COLUMN_VIDEO_HEIGHT, c.getLong(index++));
                        values.put(Programs.COLUMN_SEASON_DISPLAY_NUMBER, c.getString(index++));
                        values.put(Programs.COLUMN_SEASON_TITLE, c.getString(index++));
                        values.put(Programs.COLUMN_EPISODE_DISPLAY_NUMBER,
                                c.getString(index++));
                    } else {
                        values.put(RecordedPrograms.COLUMN_TITLE, "No program info");
                        values.put(RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS, startTime);
                        values.put(RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, endTime);
                    }
                    Uri uri = getContentResolver()
                            .insert(TvContract.RecordedPrograms.CONTENT_URI, values);
                    notifyRecordingStopped(uri);
                }
            }.execute();
        }

        @Override
        public void onRelease() {
            Log.i(TAG, "SimpleReccordingSesesionImpl: onRelease()");
            mTunerHelper.stopRecording(mChannelUri);
            mChannelUri = null;
        }
    }
}
