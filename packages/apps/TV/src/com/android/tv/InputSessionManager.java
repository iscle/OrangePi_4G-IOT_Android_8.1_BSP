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

package com.android.tv;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputInfo;
import android.media.tv.TvRecordingClient;
import android.media.tv.TvRecordingClient.RecordingCallback;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.TvView.TvInputCallback;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.data.Channel;
import com.android.tv.ui.TunableTvView;
import com.android.tv.ui.TunableTvView.OnTuneListener;
import com.android.tv.util.TvInputManagerHelper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manages input sessions.
 * Responsible for:
 * <ul>
 *     <li>Manage {@link TvView} sessions and recording sessions</li>
 *     <li>Manage capabilities (conflict)</li>
 * </ul>
 * <p>
 * As TvView's methods should be called on the main thread and the {@link RecordingSession} should
 * look at the state of the {@link TvViewSession} when it calls the framework methods, the framework
 * calls in RecordingSession are made on the main thread not to introduce the multi-thread problems.
 */
@TargetApi(Build.VERSION_CODES.N)
public class InputSessionManager {
    private static final String TAG = "InputSessionManager";
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final TvInputManagerHelper mInputManager;
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final Set<TvViewSession> mTvViewSessions = new ArraySet<>();
    private final Set<RecordingSession> mRecordingSessions =
            Collections.synchronizedSet(new ArraySet<>());
    private final Set<OnTvViewChannelChangeListener> mOnTvViewChannelChangeListeners =
            new ArraySet<>();
    private final Set<OnRecordingSessionChangeListener> mOnRecordingSessionChangeListeners =
            new ArraySet<>();

    public InputSessionManager(Context context) {
        mContext = context.getApplicationContext();
        mInputManager = TvApplication.getSingletons(context).getTvInputManagerHelper();
    }

    /**
     * Creates the session for {@link TvView}.
     * <p>
     * Do not call {@link TvView#setCallback} after the session is created.
     */
    @MainThread
    @NonNull
    public TvViewSession createTvViewSession(TvView tvView, TunableTvView tunableTvView,
            TvInputCallback callback) {
        TvViewSession session = new TvViewSession(tvView, tunableTvView, callback);
        mTvViewSessions.add(session);
        if (DEBUG) Log.d(TAG, "TvView session created: " + session);
        return session;
    }

    /**
     * Releases the {@link TvView} session.
     */
    @MainThread
    public void releaseTvViewSession(TvViewSession session) {
        mTvViewSessions.remove(session);
        session.reset();
        if (DEBUG) Log.d(TAG, "TvView session released: " + session);
    }

    /**
     * Creates the session for recording.
     */
    @NonNull
    public RecordingSession createRecordingSession(String inputId, String tag,
            RecordingCallback callback, Handler handler, long endTimeMs) {
        RecordingSession session = new RecordingSession(inputId, tag, callback, handler, endTimeMs);
        mRecordingSessions.add(session);
        if (DEBUG) Log.d(TAG, "Recording session created: " + session);
        for (OnRecordingSessionChangeListener listener : mOnRecordingSessionChangeListeners) {
            listener.onRecordingSessionChange(true, mRecordingSessions.size());
        }
        return session;
    }

    /**
     * Releases the recording session.
     */
    public void releaseRecordingSession(RecordingSession session) {
        mRecordingSessions.remove(session);
        session.release();
        if (DEBUG) Log.d(TAG, "Recording session released: " + session);
        for (OnRecordingSessionChangeListener listener : mOnRecordingSessionChangeListeners) {
            listener.onRecordingSessionChange(false, mRecordingSessions.size());
        }
    }

    /**
     * Adds the {@link OnTvViewChannelChangeListener}.
     */
    @MainThread
    public void addOnTvViewChannelChangeListener(OnTvViewChannelChangeListener listener) {
        mOnTvViewChannelChangeListeners.add(listener);
    }

    /**
     * Removes the {@link OnTvViewChannelChangeListener}.
     */
    @MainThread
    public void removeOnTvViewChannelChangeListener(OnTvViewChannelChangeListener listener) {
        mOnTvViewChannelChangeListeners.remove(listener);
    }

    @MainThread
    void notifyTvViewChannelChange(Uri channelUri) {
        for (OnTvViewChannelChangeListener l : mOnTvViewChannelChangeListeners) {
            l.onTvViewChannelChange(channelUri);
        }
    }

    /** Adds the {@link OnRecordingSessionChangeListener}. */
    public void addOnRecordingSessionChangeListener(OnRecordingSessionChangeListener listener) {
        mOnRecordingSessionChangeListeners.add(listener);
    }

    /** Removes the {@link OnRecordingSessionChangeListener}. */
    public void removeRecordingSessionChangeListener(OnRecordingSessionChangeListener listener) {
        mOnRecordingSessionChangeListeners.remove(listener);
    }

    /** Returns the current {@link TvView} channel. */
    @MainThread
    public Uri getCurrentTvViewChannelUri() {
        for (TvViewSession session : mTvViewSessions) {
            if (session.mTuned) {
                return session.mChannelUri;
            }
        }
        return null;
    }

    /**
     * Retruns the earliest end time of recording sessions in progress of the certain TV input.
     */
    @MainThread
    public Long getEarliestRecordingSessionEndTimeMs(String inputId) {
        long timeMs = Long.MAX_VALUE;
        synchronized (mRecordingSessions) {
            for (RecordingSession session : mRecordingSessions) {
                if (session.mTuned && TextUtils.equals(inputId, session.mInputId)) {
                    if (session.mEndTimeMs < timeMs) {
                        timeMs = session.mEndTimeMs;
                    }
                }
            }
        }
        return timeMs == Long.MAX_VALUE ? null : timeMs;
    }

    @MainThread
    int getTunedTvViewSessionCount(String inputId) {
        int tunedCount = 0;
        for (TvViewSession session : mTvViewSessions) {
            if (session.mTuned && Objects.equals(inputId, session.mInputId)) {
                ++tunedCount;
            }
        }
        return tunedCount;
    }

    @MainThread
    boolean isTunedForTvView(Uri channelUri) {
        for (TvViewSession session : mTvViewSessions) {
            if (session.mTuned && Objects.equals(channelUri, session.mChannelUri)) {
                return true;
            }
        }
        return false;
    }

    int getTunedRecordingSessionCount(String inputId) {
        synchronized (mRecordingSessions) {
            int tunedCount = 0;
            for (RecordingSession session : mRecordingSessions) {
                if (session.mTuned && Objects.equals(inputId, session.mInputId)) {
                    ++tunedCount;
                }
            }
            return tunedCount;
        }
    }

    boolean isTunedForRecording(Uri channelUri) {
        synchronized (mRecordingSessions) {
            for (RecordingSession session : mRecordingSessions) {
                if (session.mTuned && Objects.equals(channelUri, session.mChannelUri)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The session for {@link TvView}.
     * <p>
     * The methods which create or release session for the TV input should be called through this
     * session.
     */
    @MainThread
    public class TvViewSession {
        private final TvView mTvView;
        private final TunableTvView mTunableTvView;
        private final TvInputCallback mCallback;
        private Channel mChannel;
        private String mInputId;
        private Uri mChannelUri;
        private Bundle mParams;
        private OnTuneListener mOnTuneListener;
        private boolean mTuned;
        private boolean mNeedToBeRetuned;

        TvViewSession(TvView tvView, TunableTvView tunableTvView, TvInputCallback callback) {
            mTvView = tvView;
            mTunableTvView = tunableTvView;
            mCallback = callback;
            mTvView.setCallback(new DelegateTvInputCallback(mCallback) {
                @Override
                public void onConnectionFailed(String inputId) {
                    if (DEBUG) Log.d(TAG, "TvViewSession: connection failed");
                    mTuned = false;
                    mNeedToBeRetuned = false;
                    super.onConnectionFailed(inputId);
                    notifyTvViewChannelChange(null);
                }

                @Override
                public void onDisconnected(String inputId) {
                    if (DEBUG) Log.d(TAG, "TvViewSession: disconnected");
                    mTuned = false;
                    mNeedToBeRetuned = false;
                    super.onDisconnected(inputId);
                    notifyTvViewChannelChange(null);
                }
            });
        }

        /**
         * Tunes to the channel.
         * <p>
         * As this is called only for the warming up, there's no need to be retuned.
         */
        public void tune(String inputId, Uri channelUri) {
            if (DEBUG) {
                Log.d(TAG, "warm-up tune: {input=" + inputId + ", channelUri=" + channelUri + "}");
            }
            mInputId = inputId;
            mChannelUri = channelUri;
            mTuned = true;
            mNeedToBeRetuned = false;
            mTvView.tune(inputId, channelUri);
            notifyTvViewChannelChange(channelUri);
        }

        /**
         * Tunes to the channel.
         */
        public void tune(Channel channel, Bundle params, OnTuneListener listener) {
            if (DEBUG) {
                Log.d(TAG, "tune: {session=" + this + ", channel=" + channel + ", params=" + params
                        + ", listener=" + listener + ", mTuned=" + mTuned + "}");
            }
            mChannel = channel;
            mInputId = channel.getInputId();
            mChannelUri = channel.getUri();
            mParams = params;
            mOnTuneListener = listener;
            TvInputInfo input = mInputManager.getTvInputInfo(mInputId);
            if (input == null || (input.canRecord() && !isTunedForRecording(mChannelUri)
                    && getTunedRecordingSessionCount(mInputId) >= input.getTunerCount())) {
                if (DEBUG) {
                    if (input == null) {
                        Log.d(TAG, "Can't find input for input ID: " + mInputId);
                    } else {
                        Log.d(TAG, "No more tuners to tune for input: " + input);
                    }
                }
                mCallback.onConnectionFailed(mInputId);
                // Release the previous session to not to hold the unnecessary session.
                resetByRecording();
                return;
            }
            mTuned = true;
            mNeedToBeRetuned = false;
            mTvView.tune(mInputId, mChannelUri, params);
            notifyTvViewChannelChange(mChannelUri);
        }

        void retune() {
            if (DEBUG) Log.d(TAG, "Retune requested.");
            if (mNeedToBeRetuned) {
                if (DEBUG) Log.d(TAG, "Retuning: {channel=" + mChannel + "}");
                mTunableTvView.tuneTo(mChannel, mParams, mOnTuneListener);
                mNeedToBeRetuned = false;
            }
        }

        /**
         * Plays a given recorded TV program.
         *
         * @see TvView#timeShiftPlay
         */
        public void timeShiftPlay(String inputId, Uri recordedProgramUri) {
            mTuned = false;
            mNeedToBeRetuned = false;
            mTvView.timeShiftPlay(inputId, recordedProgramUri);
            notifyTvViewChannelChange(null);
        }

        /**
         * Resets this TvView.
         */
        public void reset() {
            if (DEBUG) Log.d(TAG, "Reset TvView session");
            mTuned = false;
            mTvView.reset();
            mNeedToBeRetuned = false;
            notifyTvViewChannelChange(null);
        }

        void resetByRecording() {
            mCallback.onVideoUnavailable(mInputId,
                    TunableTvView.VIDEO_UNAVAILABLE_REASON_NO_RESOURCE);
            if (mTuned) {
                if (DEBUG) Log.d(TAG, "Reset TvView session by recording");
                mTunableTvView.resetByRecording();
                reset();
            }
            mNeedToBeRetuned = true;
        }
    }

    /**
     * The session for recording.
     * <p>
     * The caller is responsible for releasing the session when the error occurs.
     */
    public class RecordingSession {
        private final String mInputId;
        private Uri mChannelUri;
        private final RecordingCallback mCallback;
        private final Handler mHandler;
        private volatile long mEndTimeMs;
        private TvRecordingClient mClient;
        private boolean mTuned;

        RecordingSession(String inputId, String tag, RecordingCallback callback,
                Handler handler, long endTimeMs) {
            mInputId = inputId;
            mCallback = callback;
            mHandler = handler;
            mClient = new TvRecordingClient(mContext, tag, callback, handler);
            mEndTimeMs = endTimeMs;
        }

        void release() {
            if (DEBUG) Log.d(TAG, "Release of recording session requested.");
            runOnHandler(mMainThreadHandler, new Runnable() {
                @Override
                public void run() {
                    if (DEBUG) Log.d(TAG, "Releasing of recording session.");
                    mTuned = false;
                    mClient.release();
                    mClient = null;
                    for (TvViewSession session : mTvViewSessions) {
                        if (DEBUG) {
                            Log.d(TAG, "Finding TvView sessions for retune: {tuned="
                                    + session.mTuned + ", inputId=" + session.mInputId
                                    + ", session=" + session + "}");
                        }
                        if (!session.mTuned && Objects.equals(session.mInputId, mInputId)) {
                            session.retune();
                            break;
                        }
                    }
                }
            });
        }

        /**
         * Tunes to the channel for recording.
         */
        public void tune(String inputId, Uri channelUri) {
            runOnHandler(mMainThreadHandler, new Runnable() {
                @Override
                public void run() {
                    int tunedRecordingSessionCount = getTunedRecordingSessionCount(inputId);
                    TvInputInfo input = mInputManager.getTvInputInfo(inputId);
                    if (input == null || !input.canRecord()
                            || input.getTunerCount() <= tunedRecordingSessionCount) {
                        runOnHandler(mHandler, new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onConnectionFailed(inputId);
                            }
                        });
                        return;
                    }
                    mTuned = true;
                    int tunedTuneSessionCount = getTunedTvViewSessionCount(inputId);
                    if (!isTunedForTvView(channelUri) && tunedTuneSessionCount > 0
                            && tunedRecordingSessionCount + tunedTuneSessionCount
                                    >= input.getTunerCount()) {
                        for (TvViewSession session : mTvViewSessions) {
                            if (session.mTuned && Objects.equals(session.mInputId, inputId)
                                    && !isTunedForRecording(session.mChannelUri)) {
                                session.resetByRecording();
                                break;
                            }
                        }
                    }
                    mChannelUri = channelUri;
                    mClient.tune(inputId, channelUri);
                }
            });
        }

        /**
         * Starts recording.
         */
        public void startRecording(Uri programHintUri) {
            mClient.startRecording(programHintUri);
        }

        /**
         * Stops recording.
         */
        public void stopRecording() {
            mClient.stopRecording();
        }

        /**
         * Sets recording session's ending time.
         */
        public void setEndTimeMs(long endTimeMs) {
            mEndTimeMs = endTimeMs;
        }

        private void runOnHandler(Handler handler, Runnable runnable) {
            if (Looper.myLooper() == handler.getLooper()) {
                runnable.run();
            } else {
                handler.post(runnable);
            }
        }
    }

    private static class DelegateTvInputCallback extends TvInputCallback {
        private final TvInputCallback mDelegate;

        DelegateTvInputCallback(TvInputCallback delegate) {
            mDelegate = delegate;
        }

        @Override
        public void onConnectionFailed(String inputId) {
            mDelegate.onConnectionFailed(inputId);
        }

        @Override
        public void onDisconnected(String inputId) {
            mDelegate.onDisconnected(inputId);
        }

        @Override
        public void onChannelRetuned(String inputId, Uri channelUri) {
            mDelegate.onChannelRetuned(inputId, channelUri);
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
            mDelegate.onTracksChanged(inputId, tracks);
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            mDelegate.onTrackSelected(inputId, type, trackId);
        }

        @Override
        public void onVideoSizeChanged(String inputId, int width, int height) {
            mDelegate.onVideoSizeChanged(inputId, width, height);
        }

        @Override
        public void onVideoAvailable(String inputId) {
            mDelegate.onVideoAvailable(inputId);
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            mDelegate.onVideoUnavailable(inputId, reason);
        }

        @Override
        public void onContentAllowed(String inputId) {
            mDelegate.onContentAllowed(inputId);
        }

        @Override
        public void onContentBlocked(String inputId, TvContentRating rating) {
            mDelegate.onContentBlocked(inputId, rating);
        }

        @Override
        public void onTimeShiftStatusChanged(String inputId, int status) {
            mDelegate.onTimeShiftStatusChanged(inputId, status);
        }
    }

    /**
     * Called when the {@link TvView} channel is changed.
     */
    public interface OnTvViewChannelChangeListener {
        void onTvViewChannelChange(@Nullable Uri channelUri);
    }

    /** Called when recording session is created or destroyed. */
    public interface OnRecordingSessionChangeListener {
        void onRecordingSessionChange(boolean create, int count);
    }
}
