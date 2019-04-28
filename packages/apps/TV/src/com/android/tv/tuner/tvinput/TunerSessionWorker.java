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

package com.android.tv.tuner.tvinput;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaFormat;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Surface;
import android.view.accessibility.CaptioningManager;

import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.ExoPlayer;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.TvContentRatingCache;
import com.android.tv.customization.TvCustomizationManager;
import com.android.tv.customization.TvCustomizationManager.TRICKPLAY_MODE;
import com.android.tv.tuner.TunerPreferences;
import com.android.tv.tuner.TunerPreferences.TrickplaySetting;
import com.android.tv.tuner.data.Cea708Data;
import com.android.tv.tuner.data.PsipData.EitItem;
import com.android.tv.tuner.data.PsipData.TvTracksInterface;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.data.nano.Channel;
import com.android.tv.tuner.data.nano.Track.AtscAudioTrack;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.android.tv.tuner.exoplayer.MpegTsRendererBuilder;
import com.android.tv.tuner.exoplayer.buffer.BufferManager;
import com.android.tv.tuner.exoplayer.buffer.BufferManager.StorageManager;
import com.android.tv.tuner.exoplayer.buffer.DvrStorageManager;
import com.android.tv.tuner.exoplayer.MpegTsPlayer;
import com.android.tv.tuner.exoplayer.buffer.TrickplayStorageManager;
import com.android.tv.tuner.exoplayer.ffmpeg.FfmpegDecoderClient;
import com.android.tv.tuner.source.TsDataSource;
import com.android.tv.tuner.source.TsDataSourceManager;
import com.android.tv.tuner.util.StatusTextUtils;
import com.android.tv.tuner.util.SystemPropertiesProxy;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * {@link TunerSessionWorker} implements a handler thread which processes TV input jobs
 * such as handling {@link ExoPlayer}, managing a tuner device, trickplay, and so on.
 */
@WorkerThread
public class TunerSessionWorker implements PlaybackBufferListener,
        MpegTsPlayer.VideoEventListener, MpegTsPlayer.Listener, EventDetector.EventListener,
        ChannelDataManager.ProgramInfoListener, Handler.Callback {
    private static final String TAG = "TunerSessionWorker";
    private static final boolean DEBUG = false;
    private static final boolean ENABLE_PROFILER = true;
    private static final String PLAY_FROM_CHANNEL = "channel";
    private static final String MAX_BUFFER_SIZE_KEY = "tv.tuner.buffersize_mbytes";
    private static final int MAX_BUFFER_SIZE_DEF = 2 * 1024;  // 2GB
    private static final int MIN_BUFFER_SIZE_DEF = 256;  // 256MB

    // Public messages
    public static final int MSG_SELECT_TRACK = 1;
    public static final int MSG_UPDATE_CAPTION_TRACK = 2;
    public static final int MSG_SET_STREAM_VOLUME = 3;
    public static final int MSG_TIMESHIFT_PAUSE = 4;
    public static final int MSG_TIMESHIFT_RESUME = 5;
    public static final int MSG_TIMESHIFT_SEEK_TO = 6;
    public static final int MSG_TIMESHIFT_SET_PLAYBACKPARAMS = 7;
    public static final int MSG_AUDIO_CAPABILITIES_CHANGED = 8;
    public static final int MSG_UNBLOCKED_RATING = 9;
    public static final int MSG_TUNER_PREFERENCES_CHANGED = 10;

    // Private messages
    private static final int MSG_TUNE = 1000;
    private static final int MSG_RELEASE = 1001;
    private static final int MSG_RETRY_PLAYBACK = 1002;
    private static final int MSG_START_PLAYBACK = 1003;
    private static final int MSG_UPDATE_PROGRAM = 1008;
    private static final int MSG_SCHEDULE_OF_PROGRAMS = 1009;
    private static final int MSG_UPDATE_CHANNEL_INFO = 1010;
    private static final int MSG_TRICKPLAY_BY_SEEK = 1011;
    private static final int MSG_SMOOTH_TRICKPLAY_MONITOR = 1012;
    private static final int MSG_PARENTAL_CONTROLS = 1015;
    private static final int MSG_RESCHEDULE_PROGRAMS = 1016;
    private static final int MSG_BUFFER_START_TIME_CHANGED = 1017;
    private static final int MSG_CHECK_SIGNAL = 1018;
    private static final int MSG_DISCOVER_CAPTION_SERVICE_NUMBER = 1019;
    private static final int MSG_RESET_PLAYBACK = 1020;
    private static final int MSG_BUFFER_STATE_CHANGED = 1021;
    private static final int MSG_PROGRAM_DATA_RESULT = 1022;
    private static final int MSG_STOP_TUNE = 1023;
    private static final int MSG_SET_SURFACE = 1024;
    private static final int MSG_NOTIFY_AUDIO_TRACK_UPDATED = 1025;

    private static final int TS_PACKET_SIZE = 188;
    private static final int CHECK_NO_SIGNAL_INITIAL_DELAY_MS = 4000;
    private static final int CHECK_NO_SIGNAL_PERIOD_MS = 500;
    private static final int RECOVER_STOPPED_PLAYBACK_PERIOD_MS = 2500;
    private static final int PARENTAL_CONTROLS_INTERVAL_MS = 5000;
    private static final int RESCHEDULE_PROGRAMS_INITIAL_DELAY_MS = 4000;
    private static final int RESCHEDULE_PROGRAMS_INTERVAL_MS = 10000;
    private static final int RESCHEDULE_PROGRAMS_TOLERANCE_MS = 2000;
    // The following 3s is defined empirically. This should be larger than 2s considering video
    // key frame interval in the TS stream.
    private static final int PLAYBACK_STATE_CHANGED_WAITING_THRESHOLD_MS = 3000;
    private static final int PLAYBACK_RETRY_DELAY_MS = 5000;
    private static final int MAX_IMMEDIATE_RETRY_COUNT = 5;
    private static final long INVALID_TIME = -1;

    // Some examples of the track ids of the audio tracks, "a0", "a1", "a2".
    // The number after prefix is being used for indicating a index of the given audio track.
    private static final String AUDIO_TRACK_PREFIX = "a";

    // Some examples of the tracks id of the caption tracks, "s1", "s2", "s3".
    // The number after prefix is being used for indicating a index of a caption service number
    // of the given caption track.
    private static final String SUBTITLE_TRACK_PREFIX = "s";
    private static final int TRACK_PREFIX_SIZE = 1;
    private static final String VIDEO_TRACK_ID = "v";
    private static final long BUFFER_UNDERFLOW_BUFFER_MS = 5000;

    // Actual interval would be divided by the speed.
    private static final int EXPECTED_KEY_FRAME_INTERVAL_MS = 500;
    private static final int MIN_TRICKPLAY_SEEK_INTERVAL_MS = 20;
    private static final int TRICKPLAY_MONITOR_INTERVAL_MS = 250;
    private static final int RELEASE_WAIT_INTERVAL_MS = 50;
    private static final long TRICKPLAY_OFF_DURATION_MS = TimeUnit.DAYS.toMillis(14);

    // Since release() is done asynchronously, synchronization between multiple TunerSessionWorker
    // creation/release is required.
    // This is used to guarantee that at most one active TunerSessionWorker exists at any give time.
    private static Semaphore sActiveSessionSemaphore = new Semaphore(1);

    private final Context mContext;
    private final ChannelDataManager mChannelDataManager;
    private final TsDataSourceManager mSourceManager;
    private final int mMaxTrickplayBufferSizeMb;
    private final File mTrickplayBufferDir;
    private final @TRICKPLAY_MODE int mTrickplayModeCustomization;
    private volatile Surface mSurface;
    private volatile float mVolume = 1.0f;
    private volatile boolean mCaptionEnabled;
    private volatile MpegTsPlayer mPlayer;
    private volatile TunerChannel mChannel;
    private volatile Long mRecordingDuration;
    private volatile long mRecordStartTimeMs;
    private volatile long mBufferStartTimeMs;
    private volatile boolean mTrickplayDisabledByStorageIssue;
    private @TrickplaySetting int mTrickplaySetting;
    private long mTrickplayExpiredMs;
    private String mRecordingId;
    private final Handler mHandler;
    private int mRetryCount;
    private final ArrayList<TvTrackInfo> mTvTracks;
    private final SparseArray<AtscAudioTrack> mAudioTrackMap;
    private final SparseArray<AtscCaptionTrack> mCaptionTrackMap;
    private AtscCaptionTrack mCaptionTrack;
    private PlaybackParams mPlaybackParams = new PlaybackParams();
    private boolean mPlayerStarted = false;
    private boolean mReportedDrawnToSurface = false;
    private boolean mReportedWeakSignal = false;
    private EitItem mProgram;
    private List<EitItem> mPrograms;
    private final TvInputManager mTvInputManager;
    private boolean mChannelBlocked;
    private TvContentRating mUnblockedContentRating;
    private long mLastPositionMs;
    private AudioCapabilities mAudioCapabilities;
    private long mLastLimitInBytes;
    private final TvContentRatingCache mTvContentRatingCache = TvContentRatingCache.getInstance();
    private final TunerSession mSession;
    private final boolean mHasSoftwareAudioDecoder;
    private int mPlayerState = ExoPlayer.STATE_IDLE;
    private long mPreparingStartTimeMs;
    private long mBufferingStartTimeMs;
    private long mReadyStartTimeMs;
    private boolean mIsActiveSession;
    private boolean mReleaseRequested; // Guarded by mReleaseLock
    private final Object mReleaseLock = new Object();

    public TunerSessionWorker(Context context, ChannelDataManager channelDataManager,
                TunerSession tunerSession) {
        if (DEBUG) Log.d(TAG, "TunerSessionWorker created");
        mContext = context;

        // HandlerThread should be set up before it is registered as a listener in the all other
        // components.
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper(), this);
        mSession = tunerSession;
        mChannelDataManager = channelDataManager;
        mChannelDataManager.setListener(this);
        mChannelDataManager.checkDataVersion(mContext);
        mSourceManager = TsDataSourceManager.createSourceManager(false);
        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        mTvTracks = new ArrayList<>();
        mAudioTrackMap = new SparseArray<>();
        mCaptionTrackMap = new SparseArray<>();
        CaptioningManager captioningManager =
                (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
        mCaptionEnabled = captioningManager.isEnabled();
        mPlaybackParams.setSpeed(1.0f);
        mMaxTrickplayBufferSizeMb =
                SystemPropertiesProxy.getInt(MAX_BUFFER_SIZE_KEY, MAX_BUFFER_SIZE_DEF);
        mTrickplayModeCustomization = TvCustomizationManager.getTrickplayMode(context);
        if (mTrickplayModeCustomization ==
                TvCustomizationManager.TRICKPLAY_MODE_USE_EXTERNAL_STORAGE) {
            boolean useExternalStorage =
                    Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) &&
                    Environment.isExternalStorageRemovable();
            mTrickplayBufferDir = useExternalStorage ? context.getExternalCacheDir() : null;
        } else if (mTrickplayModeCustomization == TvCustomizationManager.TRICKPLAY_MODE_ENABLED) {
            mTrickplayBufferDir = context.getCacheDir();
        } else {
            mTrickplayBufferDir = null;
        }
        mTrickplayDisabledByStorageIssue = mTrickplayBufferDir == null;
        mTrickplaySetting = TunerPreferences.getTrickplaySetting(context);
        if (mTrickplaySetting != TunerPreferences.TRICKPLAY_SETTING_NOT_SET
                && mTrickplayModeCustomization
                        == TvCustomizationManager.TRICKPLAY_MODE_USE_EXTERNAL_STORAGE) {
            // Consider the case of Customization package updates the value of trickplay mode
            // to TRICKPLAY_MODE_USE_EXTERNAL_STORAGE after install.
            mTrickplaySetting = TunerPreferences.TRICKPLAY_SETTING_NOT_SET;
            TunerPreferences.setTrickplaySetting(context, mTrickplaySetting);
            TunerPreferences.setTrickplayExpiredMs(context, 0);
        }
        mTrickplayExpiredMs = TunerPreferences.getTrickplayExpiredMs(context);
        mPreparingStartTimeMs = INVALID_TIME;
        mBufferingStartTimeMs = INVALID_TIME;
        mReadyStartTimeMs = INVALID_TIME;
        // NOTE: We assume that TunerSessionWorker instance will be at most one.
        // Only one TunerSessionWorker can be connected to FfmpegDecoderClient at any given time.
        // connect() will return false, if there is a connected TunerSessionWorker already.
        mHasSoftwareAudioDecoder = FfmpegDecoderClient.connect(context);
    }

    // Public methods
    @MainThread
    public void tune(Uri channelUri) {
        mHandler.removeCallbacksAndMessages(null);
        mSourceManager.setHasPendingTune();
        sendMessage(MSG_TUNE, channelUri);
    }

    @MainThread
    public void stopTune() {
        mHandler.removeCallbacksAndMessages(null);
        sendMessage(MSG_STOP_TUNE);
    }

    /**
     * Sets {@link Surface}.
     */
    @MainThread
    public void setSurface(Surface surface) {
        if (surface != null && !surface.isValid()) {
            Log.w(TAG, "Ignoring invalid surface.");
            return;
        }
        // mSurface is kept even when tune is called right after. But, messages can be deleted by
        // tune or updateChannelBlockStatus. So mSurface should be stored here, not through message.
        mSurface = surface;
        mHandler.sendEmptyMessage(MSG_SET_SURFACE);
    }

    /**
     * Sets volume.
     */
    @MainThread
    public void setStreamVolume(float volume) {
        // mVolume is kept even when tune is called right after. But, messages can be deleted by
        // tune or updateChannelBlockStatus. So mVolume is stored here and mPlayer.setVolume will be
        // called in MSG_SET_STREAM_VOLUME.
        mVolume = volume;
        mHandler.sendEmptyMessage(MSG_SET_STREAM_VOLUME);
    }

    /**
     * Sets if caption is enabled or disabled.
     */
    @MainThread
    public void setCaptionEnabled(boolean captionEnabled) {
        // mCaptionEnabled is kept even when tune is called right after. But, messages can be
        // deleted by tune or updateChannelBlockStatus. So mCaptionEnabled is stored here and
        // start/stopCaptionTrack will be called in MSG_UPDATE_CAPTION_STATUS.
        mCaptionEnabled = captionEnabled;
        mHandler.sendEmptyMessage(MSG_UPDATE_CAPTION_TRACK);
    }

    public TunerChannel getCurrentChannel() {
        return mChannel;
    }

    @MainThread
    public long getStartPosition() {
        return mBufferStartTimeMs;
    }


    private String getRecordingPath() {
        return Uri.parse(mRecordingId).getPath();
    }

    private Long getDurationForRecording(String recordingId) {
        DvrStorageManager storageManager =
                    new DvrStorageManager(new File(getRecordingPath()), false);
        List<BufferManager.TrackFormat> trackFormatList =
                    storageManager.readTrackInfoFiles(false);
        if (trackFormatList.isEmpty()) {
                trackFormatList = storageManager.readTrackInfoFiles(true);
        }
        if (!trackFormatList.isEmpty()) {
            BufferManager.TrackFormat trackFormat = trackFormatList.get(0);
            Long durationUs = trackFormat.format.getLong(MediaFormat.KEY_DURATION);
            // we need duration by milli for trickplay notification.
            return durationUs != null ? durationUs / 1000 : null;
        }
        Log.e(TAG, "meta file for recording was not found: " + recordingId);
        return null;
    }

    @MainThread
    public long getCurrentPosition() {
        // TODO: More precise time may be necessary.
        MpegTsPlayer mpegTsPlayer = mPlayer;
        long currentTime = mpegTsPlayer != null
                ? mRecordStartTimeMs + mpegTsPlayer.getCurrentPosition() : mRecordStartTimeMs;
        if (mChannel == null && mPlayerState == ExoPlayer.STATE_ENDED) {
            currentTime = mRecordingDuration + mRecordStartTimeMs;
        }
        if (DEBUG) {
            long systemCurrentTime = System.currentTimeMillis();
            Log.d(TAG, "currentTime = " + currentTime
                    + " ; System.currentTimeMillis() = " + systemCurrentTime
                    + " ; diff = " + (currentTime - systemCurrentTime));
        }
        return currentTime;
    }

    @AnyThread
    public void sendMessage(int messageType) {
        mHandler.sendEmptyMessage(messageType);
    }

    @AnyThread
    public void sendMessage(int messageType, Object object) {
        mHandler.obtainMessage(messageType, object).sendToTarget();
    }

    @AnyThread
    public void sendMessage(int messageType, int arg1, int arg2, Object object) {
        mHandler.obtainMessage(messageType, arg1, arg2, object).sendToTarget();
    }

    @MainThread
    public void release() {
        if (DEBUG) Log.d(TAG, "release()");
        synchronized (mReleaseLock) {
            mReleaseRequested = true;
        }
        if (mHasSoftwareAudioDecoder) {
            FfmpegDecoderClient.disconnect(mContext);
        }
        mChannelDataManager.setListener(null);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendEmptyMessage(MSG_RELEASE);
    }

    // MpegTsPlayer.Listener
    // Called in the same thread as mHandler.
    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (DEBUG) Log.d(TAG, "ExoPlayer state change: " + playbackState + " " + playWhenReady);
        if (playbackState == mPlayerState) {
            return;
        }
        mReadyStartTimeMs = INVALID_TIME;
        mPreparingStartTimeMs = INVALID_TIME;
        mBufferingStartTimeMs = INVALID_TIME;
        if (playbackState == ExoPlayer.STATE_READY) {
            if (DEBUG) Log.d(TAG, "ExoPlayer ready");
            if (!mPlayerStarted) {
                sendMessage(MSG_START_PLAYBACK, System.identityHashCode(mPlayer));
            }
            mReadyStartTimeMs = SystemClock.elapsedRealtime();
        } else if (playbackState == ExoPlayer.STATE_PREPARING) {
            mPreparingStartTimeMs = SystemClock.elapsedRealtime();
        } else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            mBufferingStartTimeMs = SystemClock.elapsedRealtime();
        } else if (playbackState == ExoPlayer.STATE_ENDED) {
            // Final status
            // notification of STATE_ENDED from MpegTsPlayer will be ignored afterwards.
            Log.i(TAG, "Player ended: end of stream");
            if (mChannel != null) {
                sendMessage(MSG_RETRY_PLAYBACK, System.identityHashCode(mPlayer));
            }
        }
        mPlayerState = playbackState;
    }

    @Override
    public void onError(Exception e) {
        if (TunerPreferences.getStoreTsStream(mContext)) {
            // Crash intentionally to capture the error causing TS file.
            Log.e(TAG, "Crash intentionally to capture the error causing TS file. "
                    + e.getMessage());
            SoftPreconditions.checkState(false);
        }
        // There maybe some errors that finally raise ExoPlaybackException and will be handled here.
        // If we are playing live stream, retrying playback maybe helpful. But for recorded stream,
        // retrying playback is not helpful.
        if (mChannel != null) {
            mHandler.obtainMessage(MSG_RETRY_PLAYBACK, System.identityHashCode(mPlayer))
                    .sendToTarget();
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, float pixelWidthHeight) {
        if (mChannel != null && mChannel.hasVideo()) {
            updateVideoTrack(width, height);
        }
        if (mRecordingId != null) {
            updateVideoTrack(width, height);
        }
    }

    @Override
    public void onDrawnToSurface(MpegTsPlayer player, Surface surface) {
        if (mSurface != null && mPlayerStarted) {
            if (DEBUG) Log.d(TAG, "MSG_DRAWN_TO_SURFACE");
            if (mRecordingId != null) {
                // Workaround of b/33298048: set it to 1 instead of 0.
                mBufferStartTimeMs = mRecordStartTimeMs = 1;
            } else {
                mBufferStartTimeMs = mRecordStartTimeMs = System.currentTimeMillis();
            }
            notifyVideoAvailable();
            mReportedDrawnToSurface = true;

            // If surface is drawn successfully, it means that the playback was brought back
            // to normal and therefore, the playback recovery status will be reset through
            // setting a zero value to the retry count.
            // TODO: Consider audio only channels for detecting playback status changes to
            //       be normal.
            mRetryCount = 0;
            if (mCaptionEnabled && mCaptionTrack != null) {
                startCaptionTrack();
            } else {
                stopCaptionTrack();
            }
            mHandler.sendEmptyMessage(MSG_NOTIFY_AUDIO_TRACK_UPDATED);
        }
    }

    @Override
    public void onSmoothTrickplayForceStopped() {
        if (mPlayer == null || !mHandler.hasMessages(MSG_SMOOTH_TRICKPLAY_MONITOR)) {
            return;
        }
        mHandler.removeMessages(MSG_SMOOTH_TRICKPLAY_MONITOR);
        doTrickplayBySeek((int) mPlayer.getCurrentPosition());
    }

    @Override
    public void onAudioUnplayable() {
        if (mPlayer == null) {
            return;
        }
        Log.i(TAG, "AC3 audio cannot be played due to device limitation");
        mSession.sendUiMessage(
                TunerSession.MSG_UI_SHOW_AUDIO_UNPLAYABLE);
    }

    // MpegTsPlayer.VideoEventListener
    @Override
    public void onEmitCaptionEvent(Cea708Data.CaptionEvent event) {
        mSession.sendUiMessage(TunerSession.MSG_UI_PROCESS_CAPTION_TRACK, event);
    }

    @Override
    public void onClearCaptionEvent() {
        mSession.sendUiMessage(TunerSession.MSG_UI_CLEAR_CAPTION_RENDERER);
    }

    @Override
    public void onDiscoverCaptionServiceNumber(int serviceNumber) {
        sendMessage(MSG_DISCOVER_CAPTION_SERVICE_NUMBER, serviceNumber);
    }

    // ChannelDataManager.ProgramInfoListener
    @Override
    public void onProgramsArrived(TunerChannel channel, List<EitItem> programs) {
        sendMessage(MSG_SCHEDULE_OF_PROGRAMS, new Pair<>(channel, programs));
    }

    @Override
    public void onChannelArrived(TunerChannel channel) {
        sendMessage(MSG_UPDATE_CHANNEL_INFO, channel);
    }

    @Override
    public void onRescanNeeded() {
        mSession.sendUiMessage(TunerSession.MSG_UI_TOAST_RESCAN_NEEDED);
    }

    @Override
    public void onRequestProgramsResponse(TunerChannel channel, List<EitItem> programs) {
        sendMessage(MSG_PROGRAM_DATA_RESULT, new Pair<>(channel, programs));
    }

    // PlaybackBufferListener
    @Override
    public void onBufferStartTimeChanged(long startTimeMs) {
        sendMessage(MSG_BUFFER_START_TIME_CHANGED, startTimeMs);
    }

    @Override
    public void onBufferStateChanged(boolean available) {
        sendMessage(MSG_BUFFER_STATE_CHANGED, available);
    }

    @Override
    public void onDiskTooSlow() {
        mTrickplayDisabledByStorageIssue = true;
        sendMessage(MSG_RETRY_PLAYBACK, System.identityHashCode(mPlayer));
    }

    // EventDetector.EventListener
    @Override
    public void onChannelDetected(TunerChannel channel, boolean channelArrivedAtFirstTime) {
        mChannelDataManager.notifyChannelDetected(channel, channelArrivedAtFirstTime);
    }

    @Override
    public void onEventDetected(TunerChannel channel, List<EitItem> items) {
        mChannelDataManager.notifyEventDetected(channel, items);
    }

    @Override
    public void onChannelScanDone() {
        // do nothing.
    }

    private long parseChannel(Uri uri) {
        try {
            List<String> paths = uri.getPathSegments();
            if (paths.size() > 1 && paths.get(0).equals(PLAY_FROM_CHANNEL)) {
                return ContentUris.parseId(uri);
            }
        } catch (UnsupportedOperationException | NumberFormatException e) {
        }
        return -1;
    }

    private static class RecordedProgram {
        private final long mChannelId;
        private final String mDataUri;

        private static final String[] PROJECTION = {
            TvContract.Programs.COLUMN_CHANNEL_ID,
            TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI,
        };

        public RecordedProgram(Cursor cursor) {
            int index = 0;
            mChannelId = cursor.getLong(index++);
            mDataUri = cursor.getString(index++);
        }

        public RecordedProgram(long channelId, String dataUri) {
            mChannelId = channelId;
            mDataUri = dataUri;
        }

        public static RecordedProgram onQuery(Cursor c) {
            RecordedProgram recording = null;
            if (c != null && c.moveToNext()) {
                recording = new RecordedProgram(c);
            }
            return recording;
        }

        public String getDataUri() {
            return mDataUri;
        }
    }

    private RecordedProgram getRecordedProgram(Uri recordedUri) {
        ContentResolver resolver = mContext.getContentResolver();
        try(Cursor c = resolver.query(recordedUri, RecordedProgram.PROJECTION, null, null, null)) {
            if (c != null) {
                RecordedProgram result = RecordedProgram.onQuery(c);
                if (DEBUG) {
                    Log.d(TAG, "Finished query for " + this);
                }
                return result;
            } else {
                if (c == null) {
                    Log.e(TAG, "Unknown query error for " + this);
                } else {
                    if (DEBUG) Log.d(TAG, "Canceled query for " + this);
                }
                return null;
            }
        }
    }

    private String parseRecording(Uri uri) {
        RecordedProgram recording = getRecordedProgram(uri);
        if (recording != null) {
            return recording.getDataUri();
        }
        return null;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_TUNE: {
                if (DEBUG) Log.d(TAG, "MSG_TUNE");

                // When sequential tuning messages arrived, it skips middle tuning messages in order
                // to change to the last requested channel quickly.
                if (mHandler.hasMessages(MSG_TUNE)) {
                    return true;
                }
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
                if (!mIsActiveSession) {
                    // Wait until release is finished if there is a pending release.
                    try {
                        while (!sActiveSessionSemaphore.tryAcquire(
                                RELEASE_WAIT_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                            synchronized (mReleaseLock) {
                                if (mReleaseRequested) {
                                    return true;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    synchronized (mReleaseLock) {
                        if (mReleaseRequested) {
                            sActiveSessionSemaphore.release();
                            return true;
                        }
                    }
                    mIsActiveSession = true;
                }
                Uri channelUri = (Uri) msg.obj;
                String recording = null;
                long channelId = parseChannel(channelUri);
                TunerChannel channel = (channelId == -1) ? null
                        : mChannelDataManager.getChannel(channelId);
                if (channelId == -1) {
                    recording = parseRecording(channelUri);
                }
                if (channel == null && recording == null) {
                    Log.w(TAG, "onTune() is failed. Can't find channel for " + channelUri);
                    stopTune();
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                    return true;
                }
                clearCallbacksAndMessagesSafely();
                mChannelDataManager.removeAllCallbacksAndMessages();
                if (channel != null) {
                    mChannelDataManager.requestProgramsData(channel);
                }
                prepareTune(channel, recording);
                // TODO: Need to refactor. notifyContentAllowed() should not be called if parental
                // control is turned on.
                mSession.notifyContentAllowed();
                resetTvTracks();
                resetPlayback();
                mHandler.sendEmptyMessageDelayed(MSG_RESCHEDULE_PROGRAMS,
                        RESCHEDULE_PROGRAMS_INITIAL_DELAY_MS);
                return true;
            }
            case MSG_STOP_TUNE: {
                if (DEBUG) Log.d(TAG, "MSG_STOP_TUNE");
                mChannel = null;
                stopPlayback(true);
                stopCaptionTrack();
                resetTvTracks();
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                return true;
            }
            case MSG_RELEASE: {
                if (DEBUG) Log.d(TAG, "MSG_RELEASE");
                mHandler.removeCallbacksAndMessages(null);
                stopPlayback(true);
                stopCaptionTrack();
                mSourceManager.release();
                mHandler.getLooper().quitSafely();
                if (mIsActiveSession) {
                    sActiveSessionSemaphore.release();
                }
                return true;
            }
            case MSG_RETRY_PLAYBACK: {
                if (System.identityHashCode(mPlayer) == (int) msg.obj) {
                    Log.i(TAG, "Retrying the playback for channel: " + mChannel);
                    mHandler.removeMessages(MSG_RETRY_PLAYBACK);
                    // When there is a request of retrying playback, don't reuse TunerHal.
                    mSourceManager.setKeepTuneStatus(false);
                    mRetryCount++;
                    if (DEBUG) {
                        Log.d(TAG, "MSG_RETRY_PLAYBACK " + mRetryCount);
                    }
                    mChannelDataManager.removeAllCallbacksAndMessages();
                    if (mRetryCount <= MAX_IMMEDIATE_RETRY_COUNT) {
                        resetPlayback();
                    } else {
                        // When it reaches this point, it may be due to an error that occurred in
                        // the tuner device. Calling stopPlayback() resets the tuner device
                        // to recover from the error.
                        stopPlayback(false);
                        stopCaptionTrack();

                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL);
                        Log.i(TAG, "Notify weak signal since fail to retry playback");

                        // After MAX_IMMEDIATE_RETRY_COUNT, give some delay of an empirically chosen
                        // value before recovering the playback.
                        mHandler.sendEmptyMessageDelayed(MSG_RESET_PLAYBACK,
                                RECOVER_STOPPED_PLAYBACK_PERIOD_MS);
                    }
                }
                return true;
            }
            case MSG_RESET_PLAYBACK: {
                if (DEBUG) Log.d(TAG, "MSG_RESET_PLAYBACK");
                mChannelDataManager.removeAllCallbacksAndMessages();
                resetPlayback();
                return true;
            }
            case MSG_START_PLAYBACK: {
                if (DEBUG) Log.d(TAG, "MSG_START_PLAYBACK");
                if (mChannel != null || mRecordingId != null) {
                    startPlayback((int) msg.obj);
                }
                return true;
            }
            case MSG_UPDATE_PROGRAM: {
                if (mChannel != null) {
                    EitItem program = (EitItem) msg.obj;
                    updateTvTracks(program, false);
                    mHandler.sendEmptyMessage(MSG_PARENTAL_CONTROLS);
                }
                return true;
            }
            case MSG_SCHEDULE_OF_PROGRAMS: {
                mHandler.removeMessages(MSG_UPDATE_PROGRAM);
                Pair<TunerChannel, List<EitItem>> pair =
                        (Pair<TunerChannel, List<EitItem>>) msg.obj;
                TunerChannel channel = pair.first;
                if (mChannel == null) {
                    return true;
                }
                if (mChannel != null && mChannel.compareTo(channel) != 0) {
                    return true;
                }
                mPrograms = pair.second;
                EitItem currentProgram = getCurrentProgram();
                if (currentProgram == null) {
                    mProgram = null;
                }
                long currentTimeMs = getCurrentPosition();
                if (mPrograms != null) {
                    for (EitItem item : mPrograms) {
                        if (currentProgram != null && currentProgram.compareTo(item) == 0) {
                            if (DEBUG) {
                                Log.d(TAG, "Update current TvTracks " + item);
                            }
                            if (mProgram != null && mProgram.compareTo(item) == 0) {
                                continue;
                            }
                            mProgram = item;
                            updateTvTracks(item, false);
                        } else if (item.getStartTimeUtcMillis() > currentTimeMs) {
                            if (DEBUG) {
                                Log.d(TAG, "Update next TvTracks " + item + " "
                                        + (item.getStartTimeUtcMillis() - currentTimeMs));
                            }
                            mHandler.sendMessageDelayed(
                                    mHandler.obtainMessage(MSG_UPDATE_PROGRAM, item),
                                    item.getStartTimeUtcMillis() - currentTimeMs);
                        }
                    }
                }
                mHandler.sendEmptyMessage(MSG_PARENTAL_CONTROLS);
                return true;
            }
            case MSG_UPDATE_CHANNEL_INFO: {
                TunerChannel channel = (TunerChannel) msg.obj;
                if (mChannel != null && mChannel.compareTo(channel) == 0) {
                    updateChannelInfo(channel);
                }
                return true;
            }
            case MSG_PROGRAM_DATA_RESULT: {
                TunerChannel channel = (TunerChannel) ((Pair) msg.obj).first;

                // If there already exists, skip it since real-time data is a top priority,
                if (mChannel != null && mChannel.compareTo(channel) == 0
                        && mPrograms == null && mProgram == null) {
                    sendMessage(MSG_SCHEDULE_OF_PROGRAMS, msg.obj);
                }
                return true;
            }
            case MSG_TRICKPLAY_BY_SEEK: {
                if (mPlayer == null) {
                    return true;
                }
                doTrickplayBySeek(msg.arg1);
                return true;
            }
            case MSG_SMOOTH_TRICKPLAY_MONITOR: {
                if (mPlayer == null) {
                    return true;
                }
                long systemCurrentTime = System.currentTimeMillis();
                long position = getCurrentPosition();
                if (mRecordingId == null) {
                    // Checks if the position exceeds the upper bound when forwarding,
                    // or exceed the lower bound when rewinding.
                    // If the direction is not checked, there can be some issues.
                    // (See b/29939781 for more details.)
                    if ((position > systemCurrentTime && mPlaybackParams.getSpeed() > 0L)
                            || (position < mBufferStartTimeMs && mPlaybackParams.getSpeed() < 0L)) {
                        doTimeShiftResume();
                        return true;
                    }
                } else {
                    if (position > mRecordingDuration || position < 0) {
                        doTimeShiftPause();
                        return true;
                    }
                }
                mHandler.sendEmptyMessageDelayed(MSG_SMOOTH_TRICKPLAY_MONITOR,
                        TRICKPLAY_MONITOR_INTERVAL_MS);
                return true;
            }
            case MSG_RESCHEDULE_PROGRAMS: {
                if (mHandler.hasMessages(MSG_SCHEDULE_OF_PROGRAMS)) {
                    mHandler.sendEmptyMessage(MSG_RESCHEDULE_PROGRAMS);
                } else {
                    doReschedulePrograms();
                }
                return true;
            }
            case MSG_PARENTAL_CONTROLS: {
                doParentalControls();
                mHandler.removeMessages(MSG_PARENTAL_CONTROLS);
                mHandler.sendEmptyMessageDelayed(MSG_PARENTAL_CONTROLS,
                        PARENTAL_CONTROLS_INTERVAL_MS);
                return true;
            }
            case MSG_UNBLOCKED_RATING: {
                mUnblockedContentRating = (TvContentRating) msg.obj;
                doParentalControls();
                mHandler.removeMessages(MSG_PARENTAL_CONTROLS);
                mHandler.sendEmptyMessageDelayed(MSG_PARENTAL_CONTROLS,
                        PARENTAL_CONTROLS_INTERVAL_MS);
                return true;
            }
            case MSG_DISCOVER_CAPTION_SERVICE_NUMBER: {
                int serviceNumber = (int) msg.obj;
                doDiscoverCaptionServiceNumber(serviceNumber);
                return true;
            }
            case MSG_SELECT_TRACK: {
                if (mChannel != null || mRecordingId != null) {
                    doSelectTrack(msg.arg1, (String) msg.obj);
                }
                return true;
            }
            case MSG_UPDATE_CAPTION_TRACK: {
                if (mCaptionEnabled) {
                    startCaptionTrack();
                } else {
                    stopCaptionTrack();
                }
                return true;
            }
            case MSG_TIMESHIFT_PAUSE: {
                if (DEBUG) Log.d(TAG, "MSG_TIMESHIFT_PAUSE");
                if (mPlayer == null) {
                    return true;
                }
                setTrickplayEnabledIfNeeded();
                doTimeShiftPause();
                return true;
            }
            case MSG_TIMESHIFT_RESUME: {
                if (DEBUG) Log.d(TAG, "MSG_TIMESHIFT_RESUME");
                if (mPlayer == null) {
                    return true;
                }
                setTrickplayEnabledIfNeeded();
                doTimeShiftResume();
                return true;
            }
            case MSG_TIMESHIFT_SEEK_TO: {
                long position = (long) msg.obj;
                if (DEBUG) Log.d(TAG, "MSG_TIMESHIFT_SEEK_TO (position=" + position + ")");
                if (mPlayer == null) {
                    return true;
                }
                setTrickplayEnabledIfNeeded();
                doTimeShiftSeekTo(position);
                return true;
            }
            case MSG_TIMESHIFT_SET_PLAYBACKPARAMS: {
                if (mPlayer == null) {
                    return true;
                }
                setTrickplayEnabledIfNeeded();
                doTimeShiftSetPlaybackParams((PlaybackParams) msg.obj);
                return true;
            }
            case MSG_AUDIO_CAPABILITIES_CHANGED: {
                AudioCapabilities capabilities = (AudioCapabilities) msg.obj;
                if (DEBUG) {
                    Log.d(TAG, "MSG_AUDIO_CAPABILITIES_CHANGED " + capabilities);
                }
                if (capabilities == null) {
                    return true;
                }
                if (!capabilities.equals(mAudioCapabilities)) {
                    // HDMI supported encodings are changed. restart player.
                    mAudioCapabilities = capabilities;
                    resetPlayback();
                }
                return true;
            }
            case MSG_SET_STREAM_VOLUME: {
                if (mPlayer != null && mPlayer.isPlaying()) {
                    mPlayer.setVolume(mVolume);
                }
                return true;
            }
            case MSG_TUNER_PREFERENCES_CHANGED: {
                mHandler.removeMessages(MSG_TUNER_PREFERENCES_CHANGED);
                @TrickplaySetting int trickplaySetting =
                        TunerPreferences.getTrickplaySetting(mContext);
                if (trickplaySetting != mTrickplaySetting) {
                    boolean wasTrcikplayEnabled =
                        mTrickplaySetting != TunerPreferences.TRICKPLAY_SETTING_DISABLED;
                    boolean isTrickplayEnabled =
                        trickplaySetting != TunerPreferences.TRICKPLAY_SETTING_DISABLED;
                    mTrickplaySetting = trickplaySetting;
                    if (isTrickplayEnabled != wasTrcikplayEnabled) {
                        sendMessage(MSG_RESET_PLAYBACK, System.identityHashCode(mPlayer));
                    }
                }
                return true;
            }
            case MSG_BUFFER_START_TIME_CHANGED: {
                if (mPlayer == null) {
                    return true;
                }
                mBufferStartTimeMs = (long) msg.obj;
                if (!hasEnoughBackwardBuffer()
                        && (!mPlayer.isPlaying() || mPlaybackParams.getSpeed() < 1.0f)) {
                    mPlayer.setPlayWhenReady(true);
                    mPlayer.setAudioTrackAndClosedCaption(true);
                    mPlaybackParams.setSpeed(1.0f);
                }
                return true;
            }
            case MSG_BUFFER_STATE_CHANGED: {
                boolean available = (boolean) msg.obj;
                mSession.notifyTimeShiftStatusChanged(available
                        ? TvInputManager.TIME_SHIFT_STATUS_AVAILABLE
                        : TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
                return true;
            }
            case MSG_CHECK_SIGNAL: {
                if (mChannel == null || mPlayer == null) {
                    return true;
                }
                TsDataSource source = mPlayer.getDataSource();
                long limitInBytes = source != null ? source.getBufferedPosition() : 0L;
                if (TunerDebug.ENABLED) {
                    TunerDebug.calculateDiff();
                    mSession.sendUiMessage(TunerSession.MSG_UI_SET_STATUS_TEXT,
                            Html.fromHtml(
                                    StatusTextUtils.getStatusWarningInHTML(
                                            (limitInBytes - mLastLimitInBytes)
                                                    / TS_PACKET_SIZE,
                                            TunerDebug.getVideoFrameDrop(),
                                            TunerDebug.getBytesInQueue(),
                                            TunerDebug.getAudioPositionUs(),
                                            TunerDebug.getAudioPositionUsRate(),
                                            TunerDebug.getAudioPtsUs(),
                                            TunerDebug.getAudioPtsUsRate(),
                                            TunerDebug.getVideoPtsUs(),
                                            TunerDebug.getVideoPtsUsRate()
                                    )));
                }
                mSession.sendUiMessage(TunerSession.MSG_UI_HIDE_MESSAGE);
                long currentTime = SystemClock.elapsedRealtime();
                long bufferingTimeMs = mBufferingStartTimeMs != INVALID_TIME
                        ? currentTime - mBufferingStartTimeMs : mBufferingStartTimeMs;
                long preparingTimeMs = mPreparingStartTimeMs != INVALID_TIME
                        ? currentTime - mPreparingStartTimeMs : mPreparingStartTimeMs;
                boolean isBufferingTooLong =
                        bufferingTimeMs > PLAYBACK_STATE_CHANGED_WAITING_THRESHOLD_MS;
                boolean isPreparingTooLong =
                        preparingTimeMs > PLAYBACK_STATE_CHANGED_WAITING_THRESHOLD_MS;
                boolean isWeakSignal = source != null
                        && mChannel.getType() != Channel.TYPE_FILE
                        && (isBufferingTooLong || isPreparingTooLong);
                if (isWeakSignal && !mReportedWeakSignal) {
                    if (!mHandler.hasMessages(MSG_RETRY_PLAYBACK)) {
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RETRY_PLAYBACK,
                                System.identityHashCode(mPlayer)), PLAYBACK_RETRY_DELAY_MS);
                    }
                    if (mPlayer != null) {
                        mPlayer.setAudioTrackAndClosedCaption(false);
                    }
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL);
                    Log.i(TAG, "Notify weak signal due to signal check, " + String.format(
                            "packetsPerSec:%d, bufferingTimeMs:%d, preparingTimeMs:%d, " +
                                    "videoFrameDrop:%d",
                            (limitInBytes - mLastLimitInBytes) / TS_PACKET_SIZE,
                            bufferingTimeMs,
                            preparingTimeMs,
                            TunerDebug.getVideoFrameDrop()
                    ));
                } else if (!isWeakSignal && mReportedWeakSignal) {
                    boolean isPlaybackStable = mReadyStartTimeMs != INVALID_TIME
                            && currentTime - mReadyStartTimeMs
                                    > PLAYBACK_STATE_CHANGED_WAITING_THRESHOLD_MS;
                    if (!isPlaybackStable) {
                        // Wait until playback becomes stable.
                    } else if (mReportedDrawnToSurface) {
                        mHandler.removeMessages(MSG_RETRY_PLAYBACK);
                        notifyVideoAvailable();
                        mPlayer.setAudioTrackAndClosedCaption(true);
                    }
                }
                mLastLimitInBytes = limitInBytes;
                mHandler.sendEmptyMessageDelayed(MSG_CHECK_SIGNAL, CHECK_NO_SIGNAL_PERIOD_MS);
                return true;
            }
            case MSG_SET_SURFACE: {
                if (mPlayer != null) {
                    mPlayer.setSurface(mSurface);
                } else {
                    // TODO: Since surface is dynamically set, we can remove the dependency of
                    // playback start on mSurface nullity.
                    resetPlayback();
                }
                return true;
            }
            case MSG_NOTIFY_AUDIO_TRACK_UPDATED: {
                notifyAudioTracksUpdated();
                return true;
            }
            default: {
                Log.w(TAG, "Unhandled message code: " + msg.what);
                return false;
            }
        }
    }

    // Private methods
    private void doSelectTrack(int type, String trackId) {
        int numTrackId = trackId != null
                ? Integer.parseInt(trackId.substring(TRACK_PREFIX_SIZE)) : -1;
        if (type == TvTrackInfo.TYPE_AUDIO) {
            if (trackId == null) {
                return;
            }
            if (numTrackId != mPlayer.getSelectedTrack(MpegTsPlayer.TRACK_TYPE_AUDIO)) {
                mPlayer.setSelectedTrack(MpegTsPlayer.TRACK_TYPE_AUDIO, numTrackId);
            }
            mSession.notifyTrackSelected(type, trackId);
        } else if (type == TvTrackInfo.TYPE_SUBTITLE) {
            if (trackId == null) {
                mSession.notifyTrackSelected(type, null);
                mCaptionTrack = null;
                stopCaptionTrack();
                return;
            }
            for (TvTrackInfo track : mTvTracks) {
                if (track.getId().equals(trackId)) {
                    // The service number of the caption service is used for track id of a
                    // subtitle track. Passes the following track id on to TsParser.
                    mSession.notifyTrackSelected(type, trackId);
                    mCaptionTrack = mCaptionTrackMap.get(numTrackId);
                    startCaptionTrack();
                    return;
                }
            }
        }
    }

    private void setTrickplayEnabledIfNeeded() {
        if (mChannel == null ||
                mTrickplayModeCustomization != TvCustomizationManager.TRICKPLAY_MODE_ENABLED) {
            return;
        }
        if (mTrickplaySetting == TunerPreferences.TRICKPLAY_SETTING_NOT_SET) {
            mTrickplaySetting = TunerPreferences.TRICKPLAY_SETTING_ENABLED;
            TunerPreferences.setTrickplaySetting(
                    mContext, mTrickplaySetting);
        }
    }

    private MpegTsPlayer createPlayer(AudioCapabilities capabilities) {
        if (capabilities == null) {
            Log.w(TAG, "No Audio Capabilities");
        }
        long now = System.currentTimeMillis();
        if (mTrickplayModeCustomization == TvCustomizationManager.TRICKPLAY_MODE_ENABLED
                && mTrickplaySetting == TunerPreferences.TRICKPLAY_SETTING_NOT_SET) {
            if (mTrickplayExpiredMs == 0) {
                mTrickplayExpiredMs = now + TRICKPLAY_OFF_DURATION_MS;
                TunerPreferences.setTrickplayExpiredMs(mContext, mTrickplayExpiredMs);
            } else {
                if (mTrickplayExpiredMs < now) {
                    mTrickplaySetting = TunerPreferences.TRICKPLAY_SETTING_DISABLED;
                    TunerPreferences.setTrickplaySetting(mContext, mTrickplaySetting);
                }
            }
        }
        BufferManager bufferManager = null;
        if (mRecordingId != null) {
            StorageManager storageManager =
                    new DvrStorageManager(new File(getRecordingPath()), false);
            bufferManager = new BufferManager(storageManager);
            updateCaptionTracks(((DvrStorageManager)storageManager).readCaptionInfoFiles());
        } else if (!mTrickplayDisabledByStorageIssue
                && mTrickplaySetting != TunerPreferences.TRICKPLAY_SETTING_DISABLED
                && mMaxTrickplayBufferSizeMb >= MIN_BUFFER_SIZE_DEF) {
            bufferManager = new BufferManager(new TrickplayStorageManager(mContext,
                    mTrickplayBufferDir, 1024L * 1024 * mMaxTrickplayBufferSizeMb));
        } else {
            Log.w(TAG, "Trickplay is disabled.");
        }
        MpegTsPlayer player = new MpegTsPlayer(
                new MpegTsRendererBuilder(mContext, bufferManager, this),
                mHandler, mSourceManager, capabilities, this);
        Log.i(TAG, "Passthrough AC3 renderer");
        if (DEBUG) Log.d(TAG, "ExoPlayer created");
        return player;
    }

    private void startCaptionTrack() {
        if (mCaptionEnabled && mCaptionTrack != null) {
            mSession.sendUiMessage(
                    TunerSession.MSG_UI_START_CAPTION_TRACK, mCaptionTrack);
            if (mPlayer != null) {
                mPlayer.setCaptionServiceNumber(mCaptionTrack.serviceNumber);
            }
        }
    }

    private void stopCaptionTrack() {
        if (mPlayer != null) {
            mPlayer.setCaptionServiceNumber(Cea708Data.EMPTY_SERVICE_NUMBER);
        }
        mSession.sendUiMessage(TunerSession.MSG_UI_STOP_CAPTION_TRACK);
    }

    private void resetTvTracks() {
        mTvTracks.clear();
        mAudioTrackMap.clear();
        mCaptionTrackMap.clear();
        mSession.sendUiMessage(TunerSession.MSG_UI_RESET_CAPTION_TRACK);
        mSession.notifyTracksChanged(mTvTracks);
    }

    private void updateTvTracks(TvTracksInterface tvTracksInterface, boolean fromPmt) {
        synchronized (tvTracksInterface) {
            if (DEBUG) {
                Log.d(TAG, "UpdateTvTracks " + tvTracksInterface);
            }
            List<AtscAudioTrack> audioTracks = tvTracksInterface.getAudioTracks();
            List<AtscCaptionTrack> captionTracks = tvTracksInterface.getCaptionTracks();
            // According to ATSC A/69 chapter 6.9, both PMT and EIT should have descriptors for audio
            // tracks, but in real world, we see some bogus audio track info in EIT, so, we trust audio
            // track info in PMT more and use info in EIT only when we have nothing.
            if (audioTracks != null && !audioTracks.isEmpty()
                    && (mChannel == null || mChannel.getAudioTracks() == null || fromPmt)) {
                updateAudioTracks(audioTracks);
            }
            if (captionTracks == null || captionTracks.isEmpty()) {
                if (tvTracksInterface.hasCaptionTrack()) {
                    updateCaptionTracks(captionTracks);
                }
            } else {
                updateCaptionTracks(captionTracks);
            }
        }
    }

    private void removeTvTracks(int trackType) {
        Iterator<TvTrackInfo> iterator = mTvTracks.iterator();
        while (iterator.hasNext()) {
            TvTrackInfo tvTrackInfo = iterator.next();
            if (tvTrackInfo.getType() == trackType) {
                iterator.remove();
            }
        }
    }

    private void updateVideoTrack(int width, int height) {
        removeTvTracks(TvTrackInfo.TYPE_VIDEO);
        mTvTracks.add(new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, VIDEO_TRACK_ID)
                .setVideoWidth(width).setVideoHeight(height).build());
        mSession.notifyTracksChanged(mTvTracks);
        mSession.notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, VIDEO_TRACK_ID);
    }

    private void updateAudioTracks(List<AtscAudioTrack> audioTracks) {
        if (DEBUG) {
            Log.d(TAG, "Update AudioTracks " + audioTracks);
        }
        mAudioTrackMap.clear();
        if (audioTracks != null) {
            int index = 0;
            for (AtscAudioTrack audioTrack : audioTracks) {
                audioTrack.index = index;
                mAudioTrackMap.put(index, audioTrack);
                ++index;
            }
        }
        mHandler.sendEmptyMessage(MSG_NOTIFY_AUDIO_TRACK_UPDATED);
    }

    private void notifyAudioTracksUpdated() {
        if (mPlayer == null) {
            // Audio tracks will be updated later once player initialization is done.
            return;
        }
        int audioTrackCount = mPlayer.getTrackCount(MpegTsPlayer.TRACK_TYPE_AUDIO);
        removeTvTracks(TvTrackInfo.TYPE_AUDIO);
        for (int i = 0; i < audioTrackCount; i++) {
            // We use language information from EIT/VCT only when the player does not provide
            // languages.
            com.google.android.exoplayer.MediaFormat infoFromPlayer =
                    mPlayer.getTrackFormat(MpegTsPlayer.TRACK_TYPE_AUDIO, i);
            AtscAudioTrack infoFromEit = mAudioTrackMap.get(i);
            AtscAudioTrack infoFromVct = (mChannel != null
                    && mChannel.getAudioTracks().size() == mAudioTrackMap.size()
                    && i < mChannel.getAudioTracks().size())
                    ? mChannel.getAudioTracks().get(i) : null;
            String language = !TextUtils.isEmpty(infoFromPlayer.language) ? infoFromPlayer.language
                    : (infoFromEit != null && infoFromEit.language != null) ? infoFromEit.language
                            : (infoFromVct != null && infoFromVct.language != null)
                                    ? infoFromVct.language : null;
            TvTrackInfo.Builder builder = new TvTrackInfo.Builder(
                    TvTrackInfo.TYPE_AUDIO, AUDIO_TRACK_PREFIX + i);
            builder.setLanguage(language);
            builder.setAudioChannelCount(infoFromPlayer.channelCount);
            builder.setAudioSampleRate(infoFromPlayer.sampleRate);
            TvTrackInfo track = builder.build();
            mTvTracks.add(track);
        }
        mSession.notifyTracksChanged(mTvTracks);
    }

    private void updateCaptionTracks(List<AtscCaptionTrack> captionTracks) {
        if (DEBUG) {
            Log.d(TAG, "Update CaptionTrack " + captionTracks);
        }
        removeTvTracks(TvTrackInfo.TYPE_SUBTITLE);
        mCaptionTrackMap.clear();
        if (captionTracks != null) {
            for (AtscCaptionTrack captionTrack : captionTracks) {
                if (mCaptionTrackMap.indexOfKey(captionTrack.serviceNumber) >= 0) {
                    continue;
                }
                String language = captionTrack.language;

                // The service number of the caption service is used for track id of a subtitle.
                // Later, when a subtitle is chosen, track id will be passed on to TsParser.
                TvTrackInfo.Builder builder =
                        new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE,
                                SUBTITLE_TRACK_PREFIX + captionTrack.serviceNumber);
                builder.setLanguage(language);
                mTvTracks.add(builder.build());
                mCaptionTrackMap.put(captionTrack.serviceNumber, captionTrack);
            }
        }
        mSession.notifyTracksChanged(mTvTracks);
    }

    private void updateChannelInfo(TunerChannel channel) {
        if (DEBUG) {
            Log.d(TAG, String.format("Channel Info (old) videoPid: %d audioPid: %d " +
                    "audioSize: %d", mChannel.getVideoPid(), mChannel.getAudioPid(),
                    mChannel.getAudioPids().size()));
        }

        // The list of the audio tracks resided in a channel is often changed depending on a
        // program being on the air. So, we should update the streaming PIDs and types of the
        // tuned channel according to the newly received channel data.
        int oldVideoPid = mChannel.getVideoPid();
        int oldAudioPid = mChannel.getAudioPid();
        List<Integer> audioPids = channel.getAudioPids();
        List<Integer> audioStreamTypes = channel.getAudioStreamTypes();
        int size = audioPids.size();
        mChannel.setVideoPid(channel.getVideoPid());
        mChannel.setAudioPids(audioPids);
        mChannel.setAudioStreamTypes(audioStreamTypes);
        updateTvTracks(channel, true);
        int index = audioPids.isEmpty() ? -1 : 0;
        for (int i = 0; i < size; ++i) {
            if (audioPids.get(i) == oldAudioPid) {
                index = i;
                break;
            }
        }
        mChannel.selectAudioTrack(index);
        mSession.notifyTrackSelected(TvTrackInfo.TYPE_AUDIO,
                index == -1 ? null : AUDIO_TRACK_PREFIX + index);

        // Reset playback if there is a change in the listening streaming PIDs.
        if (oldVideoPid != mChannel.getVideoPid()
                || oldAudioPid != mChannel.getAudioPid()) {
            // TODO: Implement a switching between tracks more smoothly.
            resetPlayback();
        }
        if (DEBUG) {
            Log.d(TAG, String.format("Channel Info (new) videoPid: %d audioPid: %d " +
                    " audioSize: %d", mChannel.getVideoPid(), mChannel.getAudioPid(),
                    mChannel.getAudioPids().size()));
        }
    }

    private void stopPlayback(boolean removeChannelDataCallbacks) {
        if (removeChannelDataCallbacks) {
            mChannelDataManager.removeAllCallbacksAndMessages();
        }
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(false);
            mPlayer.release();
            mPlayer = null;
            mPlayerState = ExoPlayer.STATE_IDLE;
            mPlaybackParams.setSpeed(1.0f);
            mPlayerStarted = false;
            mReportedDrawnToSurface = false;
            mPreparingStartTimeMs = INVALID_TIME;
            mBufferingStartTimeMs = INVALID_TIME;
            mReadyStartTimeMs = INVALID_TIME;
            mLastLimitInBytes = 0L;
            mSession.sendUiMessage(TunerSession.MSG_UI_HIDE_AUDIO_UNPLAYABLE);
            mSession.notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_UNAVAILABLE);
        }
    }

    private void startPlayback(int playerHashCode) {
        // TODO: provide hasAudio()/hasVideo() for play recordings.
        if (mPlayer == null || System.identityHashCode(mPlayer) != playerHashCode) {
            return;
        }
        if (mChannel != null && !mChannel.hasAudio()) {
            if (DEBUG) Log.d(TAG, "Channel " + mChannel + " does not have audio.");
            // Playbacks with video-only stream have not been tested yet.
            // No video-only channel has been found.
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
            return;
        }
        if (mChannel != null && ((mChannel.hasAudio() && !mPlayer.hasAudio())
                || (mChannel.hasVideo() && !mPlayer.hasVideo()))
                && mChannel.getType() != Channel.TYPE_NETWORK) {
            // If the channel is from network, skip this part since the video and audio tracks
            // information for channels from network are more reliable in the extractor. Otherwise,
            // tracks haven't been detected in the extractor. Try again.
            sendMessage(MSG_RETRY_PLAYBACK, System.identityHashCode(mPlayer));
            return;
        }
        // Since mSurface is volatile, we define a local variable surface to keep the same value
        // inside this method.
        Surface surface = mSurface;
        if (surface != null && !mPlayerStarted) {
            mPlayer.setSurface(surface);
            mPlayer.setPlayWhenReady(true);
            mPlayer.setVolume(mVolume);
            if (mChannel != null && mPlayer.hasAudio() && !mPlayer.hasVideo()) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY);
            } else if (!mReportedWeakSignal) {
                // Doesn't show buffering during weak signal.
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
            }
            mSession.sendUiMessage(TunerSession.MSG_UI_HIDE_MESSAGE);
            mPlayerStarted = true;
        }
    }

    private void preparePlayback() {
        SoftPreconditions.checkState(mPlayer == null);
        if (mChannel == null && mRecordingId == null) {
            return;
        }
        mSourceManager.setKeepTuneStatus(true);
        MpegTsPlayer player = createPlayer(mAudioCapabilities);
        player.setCaptionServiceNumber(Cea708Data.EMPTY_SERVICE_NUMBER);
        player.setVideoEventListener(this);
        player.setCaptionServiceNumber(mCaptionTrack != null ?
                mCaptionTrack.serviceNumber : Cea708Data.EMPTY_SERVICE_NUMBER);
        if (!player.prepare(mContext, mChannel, mHasSoftwareAudioDecoder, this)) {
            mSourceManager.setKeepTuneStatus(false);
            player.release();
            if (!mHandler.hasMessages(MSG_TUNE)) {
                // When prepare failed, there may be some errors related to hardware. In that
                // case, retry playback immediately may not help.
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL);
                Log.i(TAG, "Notify weak signal due to player preparation failure");
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RETRY_PLAYBACK,
                        System.identityHashCode(mPlayer)), PLAYBACK_RETRY_DELAY_MS);
            }
        } else {
            mPlayer = player;
            mPlayerStarted = false;
            mHandler.removeMessages(MSG_CHECK_SIGNAL);
            mHandler.sendEmptyMessageDelayed(MSG_CHECK_SIGNAL, CHECK_NO_SIGNAL_INITIAL_DELAY_MS);
        }
    }

    private void resetPlayback() {
        long timestamp, oldTimestamp;
        timestamp = SystemClock.elapsedRealtime();
        stopPlayback(false);
        stopCaptionTrack();
        if (ENABLE_PROFILER) {
            oldTimestamp = timestamp;
            timestamp = SystemClock.elapsedRealtime();
            Log.i(TAG, "[Profiler] stopPlayback() takes " + (timestamp - oldTimestamp) + " ms");
        }
        if (mChannelBlocked || mSurface == null) {
            return;
        }
        preparePlayback();
    }

    private void prepareTune(TunerChannel channel, String recording) {
        mChannelBlocked = false;
        mUnblockedContentRating = null;
        mRetryCount = 0;
        mChannel = channel;
        mRecordingId = recording;
        mRecordingDuration = recording != null ? getDurationForRecording(recording) : null;
        mProgram = null;
        mPrograms = null;
        if (mRecordingId != null) {
            // Workaround of b/33298048: set it to 1 instead of 0.
            mBufferStartTimeMs = mRecordStartTimeMs = 1;
        } else {
            mBufferStartTimeMs = mRecordStartTimeMs = System.currentTimeMillis();
        }
        mLastPositionMs = 0;
        mCaptionTrack = null;
        mHandler.sendEmptyMessage(MSG_PARENTAL_CONTROLS);
    }

    private void doReschedulePrograms() {
        long currentPositionMs = getCurrentPosition();
        long forwardDifference = Math.abs(currentPositionMs - mLastPositionMs
                - RESCHEDULE_PROGRAMS_INTERVAL_MS);
        mLastPositionMs = currentPositionMs;

        // A gap is measured as the time difference between previous and next current position
        // periodically. If the gap has a significant difference with an interval of a period,
        // this means that there is a change of playback status and the programs of the current
        // channel should be rescheduled to new playback timeline.
        if (forwardDifference > RESCHEDULE_PROGRAMS_TOLERANCE_MS) {
            if (DEBUG) {
                Log.d(TAG, "reschedule programs size:"
                        + (mPrograms != null ? mPrograms.size() : 0) + " current program: "
                        + getCurrentProgram());
            }
            mHandler.obtainMessage(MSG_SCHEDULE_OF_PROGRAMS, new Pair<>(mChannel, mPrograms))
                    .sendToTarget();
        }
        mHandler.removeMessages(MSG_RESCHEDULE_PROGRAMS);
        mHandler.sendEmptyMessageDelayed(MSG_RESCHEDULE_PROGRAMS,
                RESCHEDULE_PROGRAMS_INTERVAL_MS);
    }

    private int getTrickPlaySeekIntervalMs() {
        return Math.max(EXPECTED_KEY_FRAME_INTERVAL_MS / (int) Math.abs(mPlaybackParams.getSpeed()),
                MIN_TRICKPLAY_SEEK_INTERVAL_MS);
    }

    private void doTrickplayBySeek(int seekPositionMs) {
        mHandler.removeMessages(MSG_TRICKPLAY_BY_SEEK);
        if (mPlaybackParams.getSpeed() == 1.0f || !mPlayer.isPrepared()) {
            return;
        }
        if (seekPositionMs < mBufferStartTimeMs - mRecordStartTimeMs) {
            if (mPlaybackParams.getSpeed() > 1.0f) {
                // If fast forwarding, the seekPositionMs can be out of the buffered range
                // because of chuck evictions.
                seekPositionMs = (int) (mBufferStartTimeMs - mRecordStartTimeMs);
            } else {
                mPlayer.seekTo(mBufferStartTimeMs - mRecordStartTimeMs);
                mPlaybackParams.setSpeed(1.0f);
                mPlayer.setAudioTrackAndClosedCaption(true);
                return;
            }
        } else if (seekPositionMs > System.currentTimeMillis() - mRecordStartTimeMs) {
            // Stops trickplay when FF requested the position later than current position.
            // If RW trickplay requested the position later than current position,
            // continue trickplay.
            if (mPlaybackParams.getSpeed() > 0.0f) {
                mPlayer.seekTo(System.currentTimeMillis() - mRecordStartTimeMs);
                mPlaybackParams.setSpeed(1.0f);
                mPlayer.setAudioTrackAndClosedCaption(true);
                return;
            }
        }

        long delayForNextSeek = getTrickPlaySeekIntervalMs();
        if (!mPlayer.isBuffering()) {
            mPlayer.seekTo(seekPositionMs);
        } else {
            delayForNextSeek = MIN_TRICKPLAY_SEEK_INTERVAL_MS;
        }
        seekPositionMs += mPlaybackParams.getSpeed() * delayForNextSeek;
        mHandler.sendMessageDelayed(mHandler.obtainMessage(
                MSG_TRICKPLAY_BY_SEEK, seekPositionMs, 0), delayForNextSeek);
    }

    private void doTimeShiftPause() {
        mHandler.removeMessages(MSG_SMOOTH_TRICKPLAY_MONITOR);
        mHandler.removeMessages(MSG_TRICKPLAY_BY_SEEK);
        if (!hasEnoughBackwardBuffer()) {
            return;
        }
        mPlaybackParams.setSpeed(1.0f);
        mPlayer.setPlayWhenReady(false);
        mPlayer.setAudioTrackAndClosedCaption(true);
    }

    private void doTimeShiftResume() {
        mHandler.removeMessages(MSG_SMOOTH_TRICKPLAY_MONITOR);
        mHandler.removeMessages(MSG_TRICKPLAY_BY_SEEK);
        mPlaybackParams.setSpeed(1.0f);
        mPlayer.setPlayWhenReady(true);
        mPlayer.setAudioTrackAndClosedCaption(true);
    }

    private void doTimeShiftSeekTo(long timeMs) {
        mHandler.removeMessages(MSG_SMOOTH_TRICKPLAY_MONITOR);
        mHandler.removeMessages(MSG_TRICKPLAY_BY_SEEK);
        mPlayer.seekTo((int) (timeMs - mRecordStartTimeMs));
    }

    private void doTimeShiftSetPlaybackParams(PlaybackParams params) {
        if (!hasEnoughBackwardBuffer() && params.getSpeed() < 1.0f) {
            return;
        }
        mPlaybackParams = params;
        float speed = mPlaybackParams.getSpeed();
        if (speed == 1.0f) {
            mHandler.removeMessages(MSG_SMOOTH_TRICKPLAY_MONITOR);
            mHandler.removeMessages(MSG_TRICKPLAY_BY_SEEK);
            doTimeShiftResume();
        } else if (mPlayer.supportSmoothTrickPlay(speed)) {
            mHandler.removeMessages(MSG_TRICKPLAY_BY_SEEK);
            mPlayer.setAudioTrackAndClosedCaption(false);
            mPlayer.startSmoothTrickplay(mPlaybackParams);
            mHandler.sendEmptyMessageDelayed(MSG_SMOOTH_TRICKPLAY_MONITOR,
                    TRICKPLAY_MONITOR_INTERVAL_MS);
        } else {
            mHandler.removeMessages(MSG_SMOOTH_TRICKPLAY_MONITOR);
            if (!mHandler.hasMessages(MSG_TRICKPLAY_BY_SEEK)) {
                mPlayer.setAudioTrackAndClosedCaption(false);
                mPlayer.setPlayWhenReady(false);
                // Initiate trickplay
                mHandler.sendMessage(mHandler.obtainMessage(MSG_TRICKPLAY_BY_SEEK,
                        (int) (mPlayer.getCurrentPosition()
                                + speed * getTrickPlaySeekIntervalMs()), 0));
            }
        }
    }

    private EitItem getCurrentProgram() {
        if (mPrograms == null || mPrograms.isEmpty()) {
            return null;
        }
        if (mChannel.getType() == Channel.TYPE_FILE) {
            // For the playback from the local file, we use the first one from the given program.
            EitItem first = mPrograms.get(0);
            if (first != null && (mProgram == null
                    || first.getStartTimeUtcMillis() < mProgram.getStartTimeUtcMillis())) {
                return first;
            }
            return null;
        }
        long currentTimeMs = getCurrentPosition();
        for (EitItem item : mPrograms) {
            if (item.getStartTimeUtcMillis() <= currentTimeMs
                    && item.getEndTimeUtcMillis() >= currentTimeMs) {
                return item;
            }
        }
        return null;
    }

    private void doParentalControls() {
        boolean isParentalControlsEnabled = mTvInputManager.isParentalControlsEnabled();
        if (isParentalControlsEnabled) {
            TvContentRating blockContentRating = getContentRatingOfCurrentProgramBlocked();
            if (DEBUG) {
                if (blockContentRating != null) {
                    Log.d(TAG, "Check parental controls: blocked by content rating - "
                            + blockContentRating);
                } else {
                    Log.d(TAG, "Check parental controls: available");
                }
            }
            updateChannelBlockStatus(blockContentRating != null, blockContentRating);
        } else {
            if (DEBUG) {
                Log.d(TAG, "Check parental controls: available");
            }
            updateChannelBlockStatus(false, null);
        }
    }

    private void doDiscoverCaptionServiceNumber(int serviceNumber) {
        int index = mCaptionTrackMap.indexOfKey(serviceNumber);
        if (index < 0) {
            AtscCaptionTrack captionTrack = new AtscCaptionTrack();
            captionTrack.serviceNumber = serviceNumber;
            captionTrack.wideAspectRatio = false;
            captionTrack.easyReader = false;
            mCaptionTrackMap.put(serviceNumber, captionTrack);
            mTvTracks.add(new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE,
                    SUBTITLE_TRACK_PREFIX + serviceNumber).build());
            mSession.notifyTracksChanged(mTvTracks);
        }
    }

    private TvContentRating getContentRatingOfCurrentProgramBlocked() {
        EitItem currentProgram = getCurrentProgram();
        if (currentProgram == null) {
            return null;
        }
        TvContentRating[] ratings = mTvContentRatingCache
                .getRatings(currentProgram.getContentRating());
        if (ratings == null || ratings.length == 0) {
            ratings = new TvContentRating[] {TvContentRating.UNRATED};
        }
        for (TvContentRating rating : ratings) {
            if (!Objects.equals(mUnblockedContentRating, rating) && mTvInputManager
                    .isRatingBlocked(rating)) {
                return rating;
            }
        }
        return null;
    }

    private void updateChannelBlockStatus(boolean channelBlocked,
            TvContentRating contentRating) {
        if (mChannelBlocked == channelBlocked) {
            return;
        }
        mChannelBlocked = channelBlocked;
        if (mChannelBlocked) {
            clearCallbacksAndMessagesSafely();
            stopPlayback(true);
            resetTvTracks();
            if (contentRating != null) {
                mSession.notifyContentBlocked(contentRating);
            }
            mHandler.sendEmptyMessageDelayed(MSG_PARENTAL_CONTROLS, PARENTAL_CONTROLS_INTERVAL_MS);
        } else {
            clearCallbacksAndMessagesSafely();
            resetPlayback();
            mSession.notifyContentAllowed();
            mHandler.sendEmptyMessageDelayed(MSG_RESCHEDULE_PROGRAMS,
                    RESCHEDULE_PROGRAMS_INITIAL_DELAY_MS);
            mHandler.removeMessages(MSG_CHECK_SIGNAL);
            mHandler.sendEmptyMessageDelayed(MSG_CHECK_SIGNAL, CHECK_NO_SIGNAL_INITIAL_DELAY_MS);
        }
    }

    @WorkerThread
    private void clearCallbacksAndMessagesSafely() {
        // If MSG_RELEASE is removed, TunerSessionWorker will hang forever.
        // Do not remove messages, after release is requested from MainThread.
        synchronized (mReleaseLock) {
            if (!mReleaseRequested) {
                mHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    private boolean hasEnoughBackwardBuffer() {
        return mPlayer.getCurrentPosition() + BUFFER_UNDERFLOW_BUFFER_MS
                >= mBufferStartTimeMs - mRecordStartTimeMs;
    }

    private void notifyVideoUnavailable(final int reason) {
        mReportedWeakSignal = (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL);
        if (mSession != null) {
            mSession.notifyVideoUnavailable(reason);
        }
    }

    private void notifyVideoAvailable() {
        mReportedWeakSignal = false;
        if (mSession != null) {
            mSession.notifyVideoAvailable();
        }
    }
}
