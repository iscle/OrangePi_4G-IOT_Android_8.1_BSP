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

package com.android.tv.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ApplicationErrorReport;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.TvView.OnUnhandledInputEventListener;
import android.media.tv.TvView.TvInputCallback;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.tv.ApplicationSingletons;
import com.android.tv.Features;
import com.android.tv.InputSessionManager;
import com.android.tv.InputSessionManager.TvViewSession;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Program;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.util.DurationTimer;
import com.android.tv.util.Debug;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.BuildConfig;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.data.Channel;
import com.android.tv.data.StreamInfo;
import com.android.tv.data.WatchedHistoryManager;
import com.android.tv.parental.ContentRatingsManager;
import com.android.tv.recommendation.NotificationService;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.NetworkUtils;
import com.android.tv.util.PermissionUtils;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public class TunableTvView extends FrameLayout implements StreamInfo {
    private static final boolean DEBUG = false;
    private static final String TAG = "TunableTvView";

    public static final int VIDEO_UNAVAILABLE_REASON_NOT_TUNED = -1;
    public static final int VIDEO_UNAVAILABLE_REASON_NO_RESOURCE = -2;
    public static final int VIDEO_UNAVAILABLE_REASON_SCREEN_BLOCKED = -3;
    public static final int VIDEO_UNAVAILABLE_REASON_NONE = -100;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BLOCK_SCREEN_TYPE_NO_UI, BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW, BLOCK_SCREEN_TYPE_NORMAL})
    public @interface BlockScreenType {}
    public static final int BLOCK_SCREEN_TYPE_NO_UI = 0;
    public static final int BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW = 1;
    public static final int BLOCK_SCREEN_TYPE_NORMAL = 2;

    private static final String PERMISSION_RECEIVE_INPUT_EVENT =
            "com.android.tv.permission.RECEIVE_INPUT_EVENT";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ TIME_SHIFT_STATE_NONE, TIME_SHIFT_STATE_PLAY, TIME_SHIFT_STATE_PAUSE,
            TIME_SHIFT_STATE_REWIND, TIME_SHIFT_STATE_FAST_FORWARD })
    private @interface TimeShiftState {}
    private static final int TIME_SHIFT_STATE_NONE = 0;
    private static final int TIME_SHIFT_STATE_PLAY = 1;
    private static final int TIME_SHIFT_STATE_PAUSE = 2;
    private static final int TIME_SHIFT_STATE_REWIND = 3;
    private static final int TIME_SHIFT_STATE_FAST_FORWARD = 4;

    private static final int FADED_IN = 0;
    private static final int FADED_OUT = 1;
    private static final int FADING_IN = 2;
    private static final int FADING_OUT = 3;

    private AppLayerTvView mTvView;
    private TvViewSession mTvViewSession;
    private Channel mCurrentChannel;
    private TvInputManagerHelper mInputManagerHelper;
    private ContentRatingsManager mContentRatingsManager;
    private ParentalControlSettings mParentalControlSettings;
    private ProgramDataManager mProgramDataManager;
    @Nullable
    private WatchedHistoryManager mWatchedHistoryManager;
    private boolean mStarted;
    private String mTagetInputId;
    private TvInputInfo mInputInfo;
    private OnTuneListener mOnTuneListener;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoFormat = StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
    private float mVideoFrameRate;
    private float mVideoDisplayAspectRatio;
    private int mAudioChannelCount = StreamInfo.AUDIO_CHANNEL_COUNT_UNKNOWN;
    private boolean mHasClosedCaption = false;
    private boolean mScreenBlocked;
    private OnScreenBlockingChangedListener mOnScreenBlockedListener;
    private TvContentRating mBlockedContentRating;
    private int mVideoUnavailableReason = VIDEO_UNAVAILABLE_REASON_NOT_TUNED;
    private boolean mCanReceiveInputEvent;
    private boolean mIsMuted;
    private float mVolume;
    private boolean mParentControlEnabled;
    private int mFixedSurfaceWidth;
    private int mFixedSurfaceHeight;
    private final boolean mCanModifyParentalControls;
    private boolean mIsUnderShrunken;

    @TimeShiftState private int mTimeShiftState = TIME_SHIFT_STATE_NONE;
    private TimeShiftListener mTimeShiftListener;
    private boolean mTimeShiftAvailable;
    private long mTimeShiftCurrentPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;

    private final Tracker mTracker;
    private final DurationTimer mChannelViewTimer = new DurationTimer();
    private InternetCheckTask mInternetCheckTask;

    // A block screen view to hide the real TV view underlying. It may be used to enforce parental
    // control, or hide screen when there's no video available and show appropriate information.
    private final BlockScreenView mBlockScreenView;
    private final int mTuningImageColorFilter;

    // A spinner view to show buffering status.
    private final View mBufferingSpinnerView;

    private final View mDimScreenView;

    private int mFadeState = FADED_IN;
    private Runnable mActionAfterFade;

    @BlockScreenType private int mBlockScreenType;

    private final TvInputManagerHelper mInputManager;
    private final ConnectivityManager mConnectivityManager;
    private final InputSessionManager mInputSessionManager;

    private final TvInputCallback mCallback = new TvInputCallback() {
        @Override
        public void onConnectionFailed(String inputId) {
            Log.w(TAG, "Failed to bind an input");
            mTracker.sendInputConnectionFailure(inputId);
            Channel channel = mCurrentChannel;
            mCurrentChannel = null;
            mInputInfo = null;
            mCanReceiveInputEvent = false;
            if (mOnTuneListener != null) {
                // If tune is called inside onTuneFailed, mOnTuneListener will be set to
                // a new instance. In order to avoid to clear the new mOnTuneListener,
                // we copy mOnTuneListener to l and clear mOnTuneListener before
                // calling onTuneFailed.
                OnTuneListener listener = mOnTuneListener;
                mOnTuneListener = null;
                listener.onTuneFailed(channel);
            }
        }

        @Override
        public void onDisconnected(String inputId) {
            Log.w(TAG, "Session is released by crash");
            mTracker.sendInputDisconnected(inputId);
            Channel channel = mCurrentChannel;
            mCurrentChannel = null;
            mInputInfo = null;
            mCanReceiveInputEvent = false;
            if (mOnTuneListener != null) {
                OnTuneListener listener = mOnTuneListener;
                mOnTuneListener = null;
                listener.onUnexpectedStop(channel);
            }
        }

        @Override
        public void onChannelRetuned(String inputId, Uri channelUri) {
            if (DEBUG) {
                Log.d(TAG, "onChannelRetuned(inputId=" + inputId + ", channelUri="
                        + channelUri + ")");
            }
            if (mOnTuneListener != null) {
                mOnTuneListener.onChannelRetuned(channelUri);
            }
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
            mHasClosedCaption = false;
            for (TvTrackInfo track : tracks) {
                if (track.getType() == TvTrackInfo.TYPE_SUBTITLE) {
                    mHasClosedCaption = true;
                    break;
                }
            }
            if (mOnTuneListener != null) {
                mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
            }
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            if (trackId == null) {
                // A track is unselected.
                if (type == TvTrackInfo.TYPE_VIDEO) {
                    mVideoWidth = 0;
                    mVideoHeight = 0;
                    mVideoFormat = StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
                    mVideoFrameRate = 0f;
                    mVideoDisplayAspectRatio = 0f;
                } else if (type == TvTrackInfo.TYPE_AUDIO) {
                    mAudioChannelCount = StreamInfo.AUDIO_CHANNEL_COUNT_UNKNOWN;
                }
            } else {
                List<TvTrackInfo> tracks = getTracks(type);
                boolean trackFound = false;
                if (tracks != null) {
                    for (TvTrackInfo track : tracks) {
                        if (track.getId().equals(trackId)) {
                            if (type == TvTrackInfo.TYPE_VIDEO) {
                                mVideoWidth = track.getVideoWidth();
                                mVideoHeight = track.getVideoHeight();
                                mVideoFormat = Utils.getVideoDefinitionLevelFromSize(
                                        mVideoWidth, mVideoHeight);
                                mVideoFrameRate = track.getVideoFrameRate();
                                if (mVideoWidth <= 0 || mVideoHeight <= 0) {
                                    mVideoDisplayAspectRatio = 0.0f;
                                } else {
                                    float VideoPixelAspectRatio =
                                            track.getVideoPixelAspectRatio();
                                    mVideoDisplayAspectRatio = VideoPixelAspectRatio
                                            * mVideoWidth / mVideoHeight;
                                }
                            } else if (type == TvTrackInfo.TYPE_AUDIO) {
                                mAudioChannelCount = track.getAudioChannelCount();
                            }
                            trackFound = true;
                            break;
                        }
                    }
                }
                if (!trackFound) {
                    Log.w(TAG, "Invalid track ID: " + trackId);
                }
            }
            if (mOnTuneListener != null) {
                mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
            }
        }

        @Override
        public void onVideoAvailable(String inputId) {
            if (DEBUG) Log.d(TAG, "onVideoAvailable: {inputId=" + inputId + "}");
            Debug.getTimer(Debug.TAG_START_UP_TIMER).log("Start up of Live TV ends," +
                    " TunableTvView.onVideoAvailable resets timer");
            long startUpDurationTime = Debug.getTimer(Debug.TAG_START_UP_TIMER).reset();
            Debug.removeTimer(Debug.TAG_START_UP_TIMER);
            if (BuildConfig.ENG && startUpDurationTime > Debug.TIME_START_UP_DURATION_THRESHOLD) {
                showAlertDialogForLongStartUp();
            }
            mVideoUnavailableReason = VIDEO_UNAVAILABLE_REASON_NONE;
            updateBlockScreenAndMuting();
            if (mOnTuneListener != null) {
                mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
            }
        }

        private void showAlertDialogForLongStartUp() {
            new AlertDialog.Builder(getContext()).setTitle(
                    getContext().getString(R.string.settings_send_feedback))
                    .setMessage("Because the start up time of Live channels is too long," +
                            " please send feedback")
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(Intent.ACTION_APP_ERROR);
                                    ApplicationErrorReport report = new ApplicationErrorReport();
                                    report.packageName = report.processName = getContext()
                                            .getApplicationContext().getPackageName();
                                    report.time = System.currentTimeMillis();
                                    report.type = ApplicationErrorReport.TYPE_CRASH;

                                    // Add the crash info to add title of feedback automatically.
                                    ApplicationErrorReport.CrashInfo crash = new
                                            ApplicationErrorReport.CrashInfo();
                                    crash.exceptionClassName =
                                            "Live TV start up takes long time";
                                    crash.exceptionMessage =
                                            "The start up time of Live TV is too long";
                                    report.crashInfo = crash;

                                    intent.putExtra(Intent.EXTRA_BUG_REPORT, report);
                                    getContext().startActivity(intent);
                                }
                            })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            if (reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                    && reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) {
                Debug.getTimer(Debug.TAG_START_UP_TIMER).log(
                        "TunableTvView.onVideoUnAvailable reason = (" + reason
                                + ") and removes timer");
                Debug.removeTimer(Debug.TAG_START_UP_TIMER);
            } else {
                Debug.getTimer(Debug.TAG_START_UP_TIMER).log(
                        "TunableTvView.onVideoUnAvailable reason = (" + reason + ")");
            }
            mVideoUnavailableReason = reason;
            if (closePipIfNeeded()) {
                return;
            }
            updateBlockScreenAndMuting();
            if (mOnTuneListener != null) {
                mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
            }
            switch (reason) {
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN:
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING:
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL:
                    mTracker.sendChannelVideoUnavailable(mCurrentChannel, reason);
                default:
                    // do nothing
            }
        }

        @Override
        public void onContentAllowed(String inputId) {
            mBlockedContentRating = null;
            updateBlockScreenAndMuting();
            if (mOnTuneListener != null) {
                mOnTuneListener.onContentAllowed();
            }
        }

        @Override
        public void onContentBlocked(String inputId, TvContentRating rating) {
            if (rating != null && rating.equals(mBlockedContentRating)) {
                return;
            }
            mBlockedContentRating = rating;
            if (closePipIfNeeded()) {
                return;
            }
            updateBlockScreenAndMuting();
            if (mOnTuneListener != null) {
                mOnTuneListener.onContentBlocked();
            }
        }

        @Override
        public void onTimeShiftStatusChanged(String inputId, int status) {
            if (DEBUG) {
                Log.d(TAG, "onTimeShiftStatusChanged: {inputId=" + inputId + ", status=" + status +
                        "}");
            }
            boolean available = status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE;
            setTimeShiftAvailable(available);
        }
    };

    public TunableTvView(Context context) {
        this(context, null);
    }

    public TunableTvView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        inflate(getContext(), R.layout.tunable_tv_view, this);

        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        if (CommonFeatures.DVR.isEnabled(context)) {
            mInputSessionManager = appSingletons.getInputSessionManager();
        } else {
            mInputSessionManager = null;
        }
        mInputManager = appSingletons.getTvInputManagerHelper();
        mConnectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        mCanModifyParentalControls = PermissionUtils.hasModifyParentalControls(context);
        mTracker = appSingletons.getTracker();
        mBlockScreenType = BLOCK_SCREEN_TYPE_NORMAL;
        mBlockScreenView = (BlockScreenView) findViewById(R.id.block_screen);
        mBlockScreenView.addInfoFadeInAnimationListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                adjustBlockScreenSpacingAndText();
            }
        });

        mBufferingSpinnerView = findViewById(R.id.buffering_spinner);
        mTuningImageColorFilter = getResources()
                .getColor(R.color.tvview_block_image_color_filter, null);
        mDimScreenView = findViewById(R.id.dim_screen);
        mDimScreenView.animate().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mActionAfterFade != null) {
                    mActionAfterFade.run();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (mActionAfterFade != null) {
                    mActionAfterFade.run();
                }
            }
        });
    }

    public void initialize(ProgramDataManager programDataManager,
            TvInputManagerHelper tvInputManagerHelper) {
        mTvView = (AppLayerTvView) findViewById(R.id.tv_view);
        mProgramDataManager = programDataManager;
        mInputManagerHelper = tvInputManagerHelper;
        mContentRatingsManager = tvInputManagerHelper.getContentRatingsManager();
        mParentalControlSettings = tvInputManagerHelper.getParentalControlSettings();
        if (mInputSessionManager != null) {
            mTvViewSession = mInputSessionManager.createTvViewSession(mTvView, this, mCallback);
        } else {
            mTvView.setCallback(mCallback);
        }
    }

    public void start() {
        mStarted = true;
    }

    /**
     * Warms up the input to reduce the start time.
     */
    public void warmUpInput(String inputId, Uri channelUri) {
        if (!mStarted && inputId != null && channelUri != null) {
            if (mTvViewSession != null) {
                mTvViewSession.tune(inputId, channelUri);
            } else {
                mTvView.tune(inputId, channelUri);
            }
            mVideoUnavailableReason = TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING;
            updateBlockScreenAndMuting();
        }
    }

    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        if (mCurrentChannel != null) {
            long duration = mChannelViewTimer.reset();
            mTracker.sendChannelViewStop(mCurrentChannel, duration);
            if (mWatchedHistoryManager != null && !mCurrentChannel.isPassthrough()) {
                mWatchedHistoryManager.logChannelViewStop(mCurrentChannel,
                        System.currentTimeMillis(), duration);
            }
        }
        reset();
    }

    /**
     * Releases the resources.
     */
    public void release() {
        if (mInputSessionManager != null) {
            mInputSessionManager.releaseTvViewSession(mTvViewSession);
            mTvViewSession = null;
        }
    }

    /**
     * Resets TV view.
     */
    public void reset() {
        resetInternal();
        mVideoUnavailableReason = VIDEO_UNAVAILABLE_REASON_NOT_TUNED;
        updateBlockScreenAndMuting();
    }

    /**
     * Resets TV view to acquire the recording session.
     */
    public void resetByRecording() {
        resetInternal();
    }

    private void resetInternal() {
        if (mTvViewSession != null) {
            mTvViewSession.reset();
        } else {
            mTvView.reset();
        }
        mCurrentChannel = null;
        mInputInfo = null;
        mCanReceiveInputEvent = false;
        mOnTuneListener = null;
        setTimeShiftAvailable(false);
    }

    public void setMain() {
        mTvView.setMain();
    }

    public void setWatchedHistoryManager(WatchedHistoryManager watchedHistoryManager) {
        mWatchedHistoryManager = watchedHistoryManager;
    }

    /**
     * Sets if the TunableTvView is under shrunken.
     */
    public void setIsUnderShrunken(boolean isUnderShrunken) {
        mIsUnderShrunken = isUnderShrunken;
    }

    public boolean isPlaying() {
        return mStarted;
    }

    /**
     * Called when parental control is changed.
     */
    public void onParentalControlChanged(boolean enabled) {
        mParentControlEnabled = enabled;
        if (!enabled) {
            // Unblock screen immediately if parental control is turned off
            updateBlockScreenAndMuting();
        }
    }

    /**
     * Tunes to a channel with the {@code channelId}.
     *
     * @param params extra data to send it to TIS and store the data in TIMS.
     * @return false, if the TV input is not a proper state to tune to a channel. For example,
     *         if the state is disconnected or channelId doesn't exist, it returns false.
     */
    public boolean tuneTo(Channel channel, Bundle params, OnTuneListener listener) {
        Debug.getTimer(Debug.TAG_START_UP_TIMER).log("TunableTvView.tuneTo");
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG) Log.d(TAG, "tuneTo " + channel);
        TvInputInfo inputInfo = mInputManagerHelper.getTvInputInfo(channel.getInputId());
        if (inputInfo == null) {
            return false;
        }
        if (mCurrentChannel != null) {
            long duration = mChannelViewTimer.reset();
            mTracker.sendChannelViewStop(mCurrentChannel, duration);
            if (mWatchedHistoryManager != null && !mCurrentChannel.isPassthrough()) {
                mWatchedHistoryManager.logChannelViewStop(mCurrentChannel,
                        System.currentTimeMillis(), duration);
            }
        }
        mOnTuneListener = listener;
        mCurrentChannel = channel;
        boolean tunedByRecommendation = params != null
                && params.getString(NotificationService.TUNE_PARAMS_RECOMMENDATION_TYPE) != null;
        boolean needSurfaceSizeUpdate = false;
        if (!inputInfo.equals(mInputInfo)) {
            mTagetInputId = inputInfo.getId();
            mInputInfo = inputInfo;
            mCanReceiveInputEvent = getContext().getPackageManager().checkPermission(
                    PERMISSION_RECEIVE_INPUT_EVENT, mInputInfo.getServiceInfo().packageName)
                            == PackageManager.PERMISSION_GRANTED;
            if (DEBUG) {
                Log.d(TAG, "Input \'" + mInputInfo.getId() + "\' can receive input event: "
                        + mCanReceiveInputEvent);
            }
            needSurfaceSizeUpdate = true;
        }
        mTracker.sendChannelViewStart(mCurrentChannel, tunedByRecommendation);
        mChannelViewTimer.start();
        mVideoWidth = 0;
        mVideoHeight = 0;
        mVideoFormat = StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
        mVideoFrameRate = 0f;
        mVideoDisplayAspectRatio = 0f;
        mAudioChannelCount = StreamInfo.AUDIO_CHANNEL_COUNT_UNKNOWN;
        mHasClosedCaption = false;
        mBlockedContentRating = null;
        mTimeShiftCurrentPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;
        // To reduce the IPCs, unregister the callback here and register it when necessary.
        mTvView.setTimeShiftPositionCallback(null);
        setTimeShiftAvailable(false);
        if (needSurfaceSizeUpdate && mFixedSurfaceWidth > 0 && mFixedSurfaceHeight > 0) {
            // When the input is changed, TvView recreates its SurfaceView internally.
            // So we need to call SurfaceHolder.setFixedSize for the new SurfaceView.
            getSurfaceView().getHolder().setFixedSize(mFixedSurfaceWidth, mFixedSurfaceHeight);
        }
        mVideoUnavailableReason = TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING;
        if (mTvViewSession != null) {
            mTvViewSession.tune(channel, params, listener);
        } else {
            mTvView.tune(mInputInfo.getId(), mCurrentChannel.getUri(), params);
        }
        updateBlockScreenAndMuting();
        if (mOnTuneListener != null) {
            mOnTuneListener.onStreamInfoChanged(this);
        }
        return true;
    }

    @Override
    public Channel getCurrentChannel() {
        return mCurrentChannel;
    }

    /**
     * Sets the current channel. Call this method only when setting the current channel without
     * actually tuning to it.
     *
     * @param currentChannel The new current channel to set to.
     */
    public void setCurrentChannel(Channel currentChannel) {
        mCurrentChannel = currentChannel;
    }

    public void setStreamVolume(float volume) {
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG) Log.d(TAG, "setStreamVolume " + volume);
        mVolume = volume;
        if (!mIsMuted) {
            mTvView.setStreamVolume(volume);
        }
    }

    /**
     * Sets fixed size for the internal {@link android.view.Surface} of
     * {@link android.media.tv.TvView}. If either {@code width} or {@code height} is non positive,
     * the {@link android.view.Surface}'s size will be matched to the layout.
     *
     * Note: Once {@link android.view.SurfaceHolder#setFixedSize} is called,
     * {@link android.view.SurfaceView} and its underlying window can be misaligned, when the size
     * of {@link android.view.SurfaceView} is changed without changing either left position or top
     * position. For detail, please refer the codes of android.view.SurfaceView.updateWindow().
     */
    public void setFixedSurfaceSize(int width, int height) {
        mFixedSurfaceWidth = width;
        mFixedSurfaceHeight = height;
        if (mFixedSurfaceWidth > 0 && mFixedSurfaceHeight > 0) {
            // When the input is changed, TvView recreates its SurfaceView internally.
            // So we need to call SurfaceHolder.setFixedSize for the new SurfaceView.
            SurfaceView surfaceView = (SurfaceView) mTvView.getChildAt(0);
            surfaceView.getHolder().setFixedSize(mFixedSurfaceWidth, mFixedSurfaceHeight);
        } else {
            SurfaceView surfaceView = (SurfaceView) mTvView.getChildAt(0);
            surfaceView.getHolder().setSizeFromLayout();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchTrackballEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchGenericMotionEvent(event);
    }

    public interface OnTuneListener {
        void onTuneFailed(Channel channel);
        void onUnexpectedStop(Channel channel);
        void onStreamInfoChanged(StreamInfo info);
        void onChannelRetuned(Uri channel);
        void onContentBlocked();
        void onContentAllowed();
    }

    public void unblockContent(TvContentRating rating) {
        mTvView.unblockContent(rating);
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight;
    }

    @Override
    public int getVideoDefinitionLevel() {
        return mVideoFormat;
    }

    @Override
    public float getVideoFrameRate() {
        return mVideoFrameRate;
    }

    /**
     * Returns displayed aspect ratio (video width / video height * pixel ratio).
     */
    @Override
    public float getVideoDisplayAspectRatio() {
        return mVideoDisplayAspectRatio;
    }

    @Override
    public int getAudioChannelCount() {
        return mAudioChannelCount;
    }

    @Override
    public boolean hasClosedCaption() {
        return mHasClosedCaption;
    }

    @Override
    public boolean isVideoAvailable() {
        return mVideoUnavailableReason == VIDEO_UNAVAILABLE_REASON_NONE;
    }

    @Override
    public boolean isVideoOrAudioAvailable() {
        return mVideoUnavailableReason == VIDEO_UNAVAILABLE_REASON_NONE
                || mVideoUnavailableReason == TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY;
    }

    @Override
    public int getVideoUnavailableReason() {
        return mVideoUnavailableReason;
    }

    /**
     * Returns the {@link android.view.SurfaceView} of the {@link android.media.tv.TvView}.
     */
    private SurfaceView getSurfaceView() {
        return (SurfaceView) mTvView.getChildAt(0);
    }

    public void setOnUnhandledInputEventListener(OnUnhandledInputEventListener listener) {
        mTvView.setOnUnhandledInputEventListener(listener);
    }

    public void setClosedCaptionEnabled(boolean enabled) {
        mTvView.setCaptionEnabled(enabled);
    }

    public List<TvTrackInfo> getTracks(int type) {
        return mTvView.getTracks(type);
    }

    public String getSelectedTrack(int type) {
        return mTvView.getSelectedTrack(type);
    }

    public void selectTrack(int type, String trackId) {
        mTvView.selectTrack(type, trackId);
    }

    /**
     * Gets {@link android.view.ViewGroup.MarginLayoutParams} of the underlying
     * {@link TvView}, which is the actual view to play live TV videos.
     */
    public MarginLayoutParams getTvViewLayoutParams() {
        return (MarginLayoutParams) mTvView.getLayoutParams();
    }

    /**
     * Sets {@link android.view.ViewGroup.MarginLayoutParams} of the underlying
     * {@link TvView}, which is the actual view to play live TV videos.
     */
    public void setTvViewLayoutParams(MarginLayoutParams layoutParams) {
        mTvView.setLayoutParams(layoutParams);
    }

    /**
     * Gets the underlying {@link AppLayerTvView}, which is the actual view to play live TV videos.
     */
    public TvView getTvView() {
        return mTvView;
    }

    /**
     * Returns if the screen is blocked, either by {@link #blockOrUnblockScreen(boolean)} or because
     * the content is blocked.
     */
    public boolean isBlocked() {
        return isScreenBlocked() || isContentBlocked();
    }

    /**
     * Returns if the screen is blocked by {@link #blockOrUnblockScreen(boolean)}.
     */
    public boolean isScreenBlocked() {
        return mScreenBlocked;
    }

    /**
     * Returns {@code true} if the content is blocked, otherwise {@code false}.
     */
    public boolean isContentBlocked() {
        return mBlockedContentRating != null;
    }

    public void setOnScreenBlockedListener(OnScreenBlockingChangedListener listener) {
        mOnScreenBlockedListener = listener;
    }

    /**
     * Returns currently blocked content rating. {@code null} if it's not blocked.
     */
    @Override
    public TvContentRating getBlockedContentRating() {
        return mBlockedContentRating;
    }

    /**
     * Blocks/unblocks current TV screen and mutes.
     * There would be black screen with lock icon in order to show that
     * screen block is intended and not an error.
     *
     * @param blockOrUnblock {@code true} to block the screen, or {@code false} to unblock.
     */
    public void blockOrUnblockScreen(boolean blockOrUnblock) {
        if (mScreenBlocked == blockOrUnblock) {
            return;
        }
        mScreenBlocked = blockOrUnblock;
        if (closePipIfNeeded()) {
            return;
        }
        updateBlockScreenAndMuting();
        if (mOnScreenBlockedListener != null) {
            mOnScreenBlockedListener.onScreenBlockingChanged(blockOrUnblock);
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mTvView != null) {
            mTvView.setVisibility(visibility);
        }
    }

    /**
     * Set the type of block screen. If {@code type} is set to {@code BLOCK_SCREEN_TYPE_NO_UI}, the
     * block screen will not show any description such as a lock icon and a text for the blocked
     * reason, if {@code type} is set to {@code BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW}, the block screen
     * will show the description for shrunken tv view (Small icon and short text), and if
     * {@code type} is set to {@code BLOCK_SCREEN_TYPE_NORMAL}, the block screen will show the
     * description for normal tv view (Big icon and long text).
     *
     * @param type The type of block screen to set.
     */
    public void setBlockScreenType(@BlockScreenType int type) {
        if (mBlockScreenType != type) {
            mBlockScreenType = type;
            updateBlockScreen(true);
        }
    }

    private void updateBlockScreen(boolean animation) {
        mBlockScreenView.endAnimations();
        int blockReason = (mScreenBlocked || mBlockedContentRating != null)
                && mParentControlEnabled ? VIDEO_UNAVAILABLE_REASON_SCREEN_BLOCKED
                        : mVideoUnavailableReason;
        if (blockReason != VIDEO_UNAVAILABLE_REASON_NONE) {
            mBufferingSpinnerView.setVisibility(
                    blockReason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING
                            || blockReason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ?
                            VISIBLE : GONE);
            if (!animation) {
                adjustBlockScreenSpacingAndText();
            }
            if (blockReason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) {
                return;
            }
            mBlockScreenView.setVisibility(VISIBLE);
            mBlockScreenView.setBackgroundImage(null);
            if (blockReason == VIDEO_UNAVAILABLE_REASON_SCREEN_BLOCKED) {
                mBlockScreenView.setIconVisibility(true);
                if (!mCanModifyParentalControls) {
                    mBlockScreenView.setIconImage(R.drawable.ic_message_lock_no_permission);
                    mBlockScreenView.setIconScaleType(ImageView.ScaleType.CENTER);
                } else {
                    mBlockScreenView.setIconImage(R.drawable.ic_message_lock);
                    mBlockScreenView.setIconScaleType(ImageView.ScaleType.FIT_CENTER);
                }
            } else {
                if (mInternetCheckTask != null) {
                    mInternetCheckTask.cancel(true);
                    mInternetCheckTask = null;
                }
                mBlockScreenView.setIconVisibility(false);
                if (blockReason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING) {
                    showImageForTuningIfNeeded();
                } else if (blockReason == TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN
                        && mCurrentChannel != null && !mCurrentChannel.isPhysicalTunerChannel()) {
                    mInternetCheckTask = new InternetCheckTask();
                    mInternetCheckTask.execute();
                }
            }
            mBlockScreenView.onBlockStatusChanged(mBlockScreenType, animation);
        } else {
            mBufferingSpinnerView.setVisibility(GONE);
            if (mBlockScreenView.getVisibility() == VISIBLE) {
                mBlockScreenView.fadeOut();
            }
        }
    }

    private void adjustBlockScreenSpacingAndText() {
        mBlockScreenView.setSpacing(mBlockScreenType);
        String text = getBlockScreenText();
        if (text != null) {
            mBlockScreenView.setInfoText(text);
        }
    }

    /**
     * Returns the block screen text corresponding to the current status.
     * Note that returning {@code null} value means that the current text should not be changed.
     */
    private String getBlockScreenText() {
        // TODO: add a test for this method
        Resources res = getResources();
        if (mScreenBlocked && mParentControlEnabled) {
            switch (mBlockScreenType) {
                case BLOCK_SCREEN_TYPE_NO_UI:
                case BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW:
                    return "";
                case BLOCK_SCREEN_TYPE_NORMAL:
                    if (mCanModifyParentalControls) {
                        return res.getString(R.string.tvview_channel_locked);
                    } else {
                        return res.getString(R.string.tvview_channel_locked_no_permission);
                    }
            }
        } else if (mBlockedContentRating != null && mParentControlEnabled) {
            String name = mContentRatingsManager.getDisplayNameForRating(mBlockedContentRating);
            switch (mBlockScreenType) {
                case BLOCK_SCREEN_TYPE_NO_UI:
                    return "";
                case BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW:
                    if (TextUtils.isEmpty(name)) {
                        return res.getString(R.string.shrunken_tvview_content_locked);
                    } else if (name.equals(res.getString(R.string.unrated_rating_name))) {
                        return res.getString(R.string.shrunken_tvview_content_locked_unrated);
                    } else {
                        return res.getString(R.string.shrunken_tvview_content_locked_format, name);
                    }
                case BLOCK_SCREEN_TYPE_NORMAL:
                    if (TextUtils.isEmpty(name)) {
                        if (mCanModifyParentalControls) {
                            return res.getString(R.string.tvview_content_locked);
                        } else {
                            return res.getString(R.string.tvview_content_locked_no_permission);
                        }
                    } else {
                        if (mCanModifyParentalControls) {
                            return name.equals(res.getString(R.string.unrated_rating_name))
                                    ? res.getString(R.string.tvview_content_locked_unrated)
                                    : res.getString(R.string.tvview_content_locked_format, name);
                        } else {
                            return name.equals(res.getString(R.string.unrated_rating_name))
                                    ? res.getString(
                                            R.string.tvview_content_locked_unrated_no_permission)
                                    : res.getString(
                                            R.string.tvview_content_locked_format_no_permission,
                                            name);
                        }
                    }
            }
        } else if (mVideoUnavailableReason != VIDEO_UNAVAILABLE_REASON_NONE) {
            switch (mVideoUnavailableReason) {
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY:
                    return res.getString(R.string.tvview_msg_audio_only);
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL:
                    return res.getString(R.string.tvview_msg_weak_signal);
                case VIDEO_UNAVAILABLE_REASON_NO_RESOURCE:
                    return getTuneConflictMessage();
                default:
                    return "";
            }
        }
        return null;
    }

    private boolean closePipIfNeeded() {
        if (Features.PICTURE_IN_PICTURE.isEnabled(getContext())
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && ((Activity) getContext()).isInPictureInPictureMode()
                && (mScreenBlocked
                        || mBlockedContentRating != null
                        || mVideoUnavailableReason
                                == TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)) {
            ((Activity) getContext()).finish();
            return true;
        }
        return false;
    }

    private void updateBlockScreenAndMuting() {
        updateBlockScreen(false);
        updateMuteStatus();
    }

    private boolean shouldShowImageForTuning() {
        if (mVideoUnavailableReason != TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                || mScreenBlocked || mBlockedContentRating != null || mCurrentChannel == null
                || mIsUnderShrunken || getWidth() == 0 || getWidth() == 0 || !isBundledInput()) {
            return false;
        }
        Program currentProgram = mProgramDataManager.getCurrentProgram(mCurrentChannel.getId());
        if (currentProgram == null) {
            return false;
        }
        TvContentRating rating =
                mParentalControlSettings.getBlockedRating(currentProgram.getContentRatings());
        return !(mParentControlEnabled && rating != null);
    }

    private void showImageForTuningIfNeeded() {
        if (shouldShowImageForTuning()) {
            if (mCurrentChannel == null) {
                return;
            }
            Program currentProgram = mProgramDataManager.getCurrentProgram(mCurrentChannel.getId());
            if (currentProgram != null) {
                currentProgram.loadPosterArt(getContext(), getWidth(), getHeight(),
                        createProgramPosterArtCallback(mCurrentChannel.getId()));
            }
        }
    }

    private String getTuneConflictMessage() {
        if (mTagetInputId != null) {
            TvInputInfo input = mInputManager.getTvInputInfo(mTagetInputId);
            Long timeMs = mInputSessionManager.getEarliestRecordingSessionEndTimeMs(mTagetInputId);
            if (timeMs != null) {
                return getResources().getQuantityString(R.plurals.tvview_msg_input_no_resource,
                        input.getTunerCount(),
                        DateUtils.formatDateTime(getContext(), timeMs, DateUtils.FORMAT_SHOW_TIME));
            }
        }
        return null;
    }

    private void updateMuteStatus() {
        // Workaround: TunerTvInputService uses AC3 pass-through implementation, which disables
        // audio tracks to enforce the mute request. We don't want to send mute request if we are
        // not going to block the screen to prevent the video jankiness resulted by disabling audio
        // track before the playback is started. In other way, we should send unmute request before
        // the playback is started, because TunerTvInput will remember the muted state and mute
        // itself right way when the playback is going to be started, which results the initial
        // jankiness, too.
        boolean isBundledInput = isBundledInput();
        if ((isBundledInput || isVideoOrAudioAvailable()) && !mScreenBlocked
                && mBlockedContentRating == null) {
            if (mIsMuted) {
                mIsMuted = false;
                mTvView.setStreamVolume(mVolume);
            }
        } else {
            if (!mIsMuted) {
                if ((mInputInfo == null || isBundledInput)
                        && !mScreenBlocked && mBlockedContentRating == null) {
                    return;
                }
                mIsMuted = true;
                mTvView.setStreamVolume(0);
            }
        }
    }

    private boolean isBundledInput() {
        return mInputInfo != null && mInputInfo.getType() == TvInputInfo.TYPE_TUNER
                && Utils.isBundledInput(mInputInfo.getId());
    }

    /** Returns true if this view is faded out. */
    public boolean isFadedOut() {
        return mFadeState == FADED_OUT;
    }

    /** Fade out this TunableTvView. Fade out by increasing the dimming. */
    public void fadeOut(int durationMillis, TimeInterpolator interpolator,
            final Runnable actionAfterFade) {
        mDimScreenView.setAlpha(0f);
        mDimScreenView.setVisibility(View.VISIBLE);
        mDimScreenView.animate()
                .alpha(1f)
                .setDuration(durationMillis)
                .setInterpolator(interpolator)
                .withStartAction(new Runnable() {
                    @Override
                    public void run() {
                        mFadeState = FADING_OUT;
                        mActionAfterFade = actionAfterFade;
                    }
                })
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mFadeState = FADED_OUT;
                    }
                });
    }

    /** Fade in this TunableTvView. Fade in by decreasing the dimming. */
    public void fadeIn(int durationMillis, TimeInterpolator interpolator,
            final Runnable actionAfterFade) {
        mDimScreenView.setAlpha(1f);
        mDimScreenView.setVisibility(View.VISIBLE);
        mDimScreenView.animate()
                .alpha(0f)
                .setDuration(durationMillis)
                .setInterpolator(interpolator)
                .withStartAction(new Runnable() {
                    @Override
                    public void run() {
                        mFadeState = FADING_IN;
                        mActionAfterFade = actionAfterFade;
                    }
                })
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mFadeState = FADED_IN;
                        mDimScreenView.setVisibility(View.GONE);
                    }
                });
    }

    /** Remove the fade effect. */
    public void removeFadeEffect() {
        mDimScreenView.animate().cancel();
        mDimScreenView.setVisibility(View.GONE);
        mFadeState = FADED_IN;
    }

    /**
     * Sets the TimeShiftListener
     *
     * @param listener The instance of {@link TimeShiftListener}.
     */
    public void setTimeShiftListener(TimeShiftListener listener) {
        mTimeShiftListener = listener;
    }

    private void setTimeShiftAvailable(boolean isTimeShiftAvailable) {
        if (mTimeShiftAvailable == isTimeShiftAvailable) {
            return;
        }
        mTimeShiftAvailable = isTimeShiftAvailable;
        if (isTimeShiftAvailable) {
            mTvView.setTimeShiftPositionCallback(new TvView.TimeShiftPositionCallback() {
                @Override
                public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
                    if (mTimeShiftListener != null && mCurrentChannel != null
                            && mCurrentChannel.getInputId().equals(inputId)) {
                        mTimeShiftListener.onRecordStartTimeChanged(timeMs);
                    }
                }

                @Override
                public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
                    mTimeShiftCurrentPositionMs = timeMs;
                }
            });
        } else {
            mTvView.setTimeShiftPositionCallback(null);
        }
        if (mTimeShiftListener != null) {
            mTimeShiftListener.onAvailabilityChanged();
        }
    }

    /**
     * Returns if the time shift is available for the current channel.
     */
    public boolean isTimeShiftAvailable() {
        return mTimeShiftAvailable;
    }

    /**
     * Plays the media, if the current input supports time-shifting.
     */
    public void timeshiftPlay() {
        if (!isTimeShiftAvailable()) {
            throw new IllegalStateException("Time-shift is not supported for the current channel");
        }
        if (mTimeShiftState == TIME_SHIFT_STATE_PLAY) {
            return;
        }
        mTvView.timeShiftResume();
    }

    /**
     * Pauses the media, if the current input supports time-shifting.
     */
    public void timeshiftPause() {
        if (!isTimeShiftAvailable()) {
            throw new IllegalStateException("Time-shift is not supported for the current channel");
        }
        if (mTimeShiftState == TIME_SHIFT_STATE_PAUSE) {
            return;
        }
        mTvView.timeShiftPause();
    }

    /**
     * Rewinds the media with the given speed, if the current input supports time-shifting.
     *
     * @param speed The speed to rewind the media. e.g. 2 for 2x, 3 for 3x and 4 for 4x.
     */
    public void timeshiftRewind(int speed) {
        if (!isTimeShiftAvailable()) {
            throw new IllegalStateException("Time-shift is not supported for the current channel");
        } else {
            if (speed <= 0) {
                throw new IllegalArgumentException("The speed should be a positive integer.");
            }
            mTimeShiftState = TIME_SHIFT_STATE_REWIND;
            PlaybackParams params = new PlaybackParams();
            params.setSpeed(speed * -1);
            mTvView.timeShiftSetPlaybackParams(params);
        }
    }

    /**
     * Fast-forwards the media with the given speed, if the current input supports time-shifting.
     *
     * @param speed The speed to forward the media. e.g. 2 for 2x, 3 for 3x and 4 for 4x.
     */
    public void timeshiftFastForward(int speed) {
        if (!isTimeShiftAvailable()) {
            throw new IllegalStateException("Time-shift is not supported for the current channel");
        } else {
            if (speed <= 0) {
                throw new IllegalArgumentException("The speed should be a positive integer.");
            }
            mTimeShiftState = TIME_SHIFT_STATE_FAST_FORWARD;
            PlaybackParams params = new PlaybackParams();
            params.setSpeed(speed);
            mTvView.timeShiftSetPlaybackParams(params);
        }
    }

    /**
     * Seek to the given time position.
     *
     * @param timeMs The time in milliseconds to seek to.
     */
    public void timeshiftSeekTo(long timeMs) {
        if (!isTimeShiftAvailable()) {
            throw new IllegalStateException("Time-shift is not supported for the current channel");
        }
        mTvView.timeShiftSeekTo(timeMs);
    }

    /**
     * Returns the current playback position in milliseconds.
     */
    public long timeshiftGetCurrentPositionMs() {
        if (!isTimeShiftAvailable()) {
            throw new IllegalStateException("Time-shift is not supported for the current channel");
        }
        if (DEBUG) {
            Log.d(TAG, "timeshiftGetCurrentPositionMs: current position ="
                    + Utils.toTimeString(mTimeShiftCurrentPositionMs));
        }
        return mTimeShiftCurrentPositionMs;
    }

    private ImageLoader.ImageLoaderCallback<BlockScreenView> createProgramPosterArtCallback(
            final long channelId) {
        return new ImageLoader.ImageLoaderCallback<BlockScreenView>(mBlockScreenView) {
            @Override
            public void onBitmapLoaded(BlockScreenView view, @Nullable Bitmap posterArt) {
                if (posterArt == null || getCurrentChannel() == null
                        || channelId != getCurrentChannel().getId()
                        || !shouldShowImageForTuning()) {
                    return;
                }
                Drawable drawablePosterArt = new BitmapDrawable(view.getResources(), posterArt);
                drawablePosterArt.mutate().setColorFilter(
                        mTuningImageColorFilter, PorterDuff.Mode.SRC_OVER);
                view.setBackgroundImage(drawablePosterArt);
            }
        };
    }

    /**
     * Used to receive the time-shift events.
     */
    public static abstract class TimeShiftListener {
        /**
         * Called when the availability of the time-shift for the current channel has been changed.
         * It should be guaranteed that this is called only when the availability is really changed.
         */
        public abstract void onAvailabilityChanged();

        /**
         * Called when the record start time has been changed.
         * This is not called when the recorded programs is played.
         */
        public abstract void onRecordStartTimeChanged(long recordStartTimeMs);
    }

    /**
     * A listener which receives the notification when the screen is blocked/unblocked.
     */
    public static abstract class OnScreenBlockingChangedListener {
        /**
         * Called when the screen is blocked/unblocked.
         */
        public abstract void onScreenBlockingChanged(boolean blocked);
    }

    private class InternetCheckTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            return NetworkUtils.isNetworkAvailable(mConnectivityManager);
        }

        @Override
        protected void onPostExecute(Boolean networkAvailable) {
            mInternetCheckTask = null;
            if (!networkAvailable && isAttachedToWindow()
                    && !mScreenBlocked && mBlockedContentRating == null
                    && mVideoUnavailableReason == TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN) {
                mBlockScreenView.setIconVisibility(true);
                mBlockScreenView.setIconImage(R.drawable.ic_sad_cloud);
                mBlockScreenView.setInfoText(R.string.tvview_msg_no_internet_connection);
            }
        }
    }
}
