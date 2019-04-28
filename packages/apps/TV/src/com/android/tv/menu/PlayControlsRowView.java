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

package com.android.tv.menu;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TimeShiftManager;
import com.android.tv.TimeShiftManager.TimeShiftActionId;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dialog.HalfSizedDialogFragment;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrDataManager.OnDvrScheduleLoadFinishedListener;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrStopRecordingFragment;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.menu.Menu.MenuShowReason;
import com.android.tv.ui.TunableTvView;

public class PlayControlsRowView extends MenuRowView {
    private static final int NORMAL_WIDTH_MAX_BUTTON_COUNT = 5;
    // Dimensions
    private final int mTimeIndicatorLeftMargin;
    private final int mTimeTextLeftMargin;
    private final int mTimelineWidth;
    // Views
    private TextView mBackgroundView;
    private View mTimeIndicator;
    private TextView mTimeText;
    private PlaybackProgressBar mProgress;
    private PlayControlsButton mJumpPreviousButton;
    private PlayControlsButton mRewindButton;
    private PlayControlsButton mPlayPauseButton;
    private PlayControlsButton mFastForwardButton;
    private PlayControlsButton mJumpNextButton;
    private PlayControlsButton mRecordButton;
    private TextView mProgramStartTimeText;
    private TextView mProgramEndTimeText;
    private TunableTvView mTvView;
    private TimeShiftManager mTimeShiftManager;
    private final DvrDataManager mDvrDataManager;
    private final DvrManager mDvrManager;
    private final MainActivity mMainActivity;

    private final java.text.DateFormat mTimeFormat;
    private long mProgramStartTimeMs;
    private long mProgramEndTimeMs;
    private boolean mUseCompactLayout;
    private final int mNormalButtonMargin;
    private final int mCompactButtonMargin;

    private final String mUnavailableMessage;

    private final ScheduledRecordingListener mScheduledRecordingListener
            = new ScheduledRecordingListener() {
        @Override
        public void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) { }

        @Override
        public void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) { }

        @Override
        public void onScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings) {
            Channel currentChannel = mMainActivity.getCurrentChannel();
            if (currentChannel != null && isShown()) {
                for (ScheduledRecording schedule : scheduledRecordings) {
                    if (schedule.getChannelId() == currentChannel.getId()) {
                        updateRecordButton();
                        break;
                    }
                }
            }
        }
    };

    public PlayControlsRowView(Context context) {
        this(context, null);
    }

    public PlayControlsRowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayControlsRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PlayControlsRowView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        Resources res = context.getResources();
        mTimeIndicatorLeftMargin =
                - res.getDimensionPixelSize(R.dimen.play_controls_time_indicator_width) / 2;
        mTimeTextLeftMargin =
                - res.getDimensionPixelOffset(R.dimen.play_controls_time_width) / 2;
        mTimelineWidth = res.getDimensionPixelSize(R.dimen.play_controls_width);
        mTimeFormat = DateFormat.getTimeFormat(context);
        mNormalButtonMargin = res.getDimensionPixelSize(R.dimen.play_controls_button_normal_margin);
        mCompactButtonMargin =
                res.getDimensionPixelSize(R.dimen.play_controls_button_compact_margin);
        if (CommonFeatures.DVR.isEnabled(context)) {
            mDvrDataManager = TvApplication.getSingletons(context).getDvrDataManager();
            mDvrManager = TvApplication.getSingletons(context).getDvrManager();
        } else {
            mDvrDataManager = null;
            mDvrManager = null;
        }
        mMainActivity = (MainActivity) context;
        mUnavailableMessage = res.getString(R.string.play_controls_unavailable);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDvrDataManager != null) {
            mDvrDataManager.addScheduledRecordingListener(mScheduledRecordingListener);
            if (!mDvrDataManager.isDvrScheduleLoadFinished()) {
                mDvrDataManager.addDvrScheduleLoadFinishedListener(
                        new OnDvrScheduleLoadFinishedListener() {
                            @Override
                            public void onDvrScheduleLoadFinished() {
                                mDvrDataManager.removeDvrScheduleLoadFinishedListener(this);
                                if (isShown()) {
                                    updateRecordButton();
                                }
                            }
                        });
            }

        }
    }

    @Override
    protected int getContentsViewId() {
        return R.id.play_controls;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Clip the ViewGroup(body) to the rounded rectangle of outline.
        findViewById(R.id.body).setClipToOutline(true);
        mBackgroundView = (TextView) findViewById(R.id.background);
        mTimeIndicator = findViewById(R.id.time_indicator);
        mTimeText = (TextView) findViewById(R.id.time_text);
        mProgress = (PlaybackProgressBar) findViewById(R.id.progress);
        mJumpPreviousButton = (PlayControlsButton) findViewById(R.id.jump_previous);
        mRewindButton = (PlayControlsButton) findViewById(R.id.rewind);
        mPlayPauseButton = (PlayControlsButton) findViewById(R.id.play_pause);
        mFastForwardButton = (PlayControlsButton) findViewById(R.id.fast_forward);
        mJumpNextButton = (PlayControlsButton) findViewById(R.id.jump_next);
        mRecordButton = (PlayControlsButton) findViewById(R.id.record);
        mProgramStartTimeText = (TextView) findViewById(R.id.program_start_time);
        mProgramEndTimeText = (TextView) findViewById(R.id.program_end_time);

        initializeButton(mJumpPreviousButton, R.drawable.lb_ic_skip_previous,
                R.string.play_controls_description_skip_previous, null, new Runnable() {
            @Override
            public void run() {
                if (mTimeShiftManager.isAvailable()) {
                    mTimeShiftManager.jumpToPrevious();
                    updateControls(true);
                }
            }
        });
        initializeButton(mRewindButton, R.drawable.lb_ic_fast_rewind,
                R.string.play_controls_description_fast_rewind, null, new Runnable() {
            @Override
            public void run() {
                if (mTimeShiftManager.isAvailable()) {
                    mTimeShiftManager.rewind();
                    updateButtons();
                }
            }
        });
        initializeButton(mPlayPauseButton, R.drawable.lb_ic_play,
                R.string.play_controls_description_play_pause, null, new Runnable() {
            @Override
            public void run() {
                if (mTimeShiftManager.isAvailable()) {
                    mTimeShiftManager.togglePlayPause();
                    updateButtons();
                }
            }
        });
        initializeButton(mFastForwardButton, R.drawable.lb_ic_fast_forward,
                R.string.play_controls_description_fast_forward, null, new Runnable() {
            @Override
            public void run() {
                if (mTimeShiftManager.isAvailable()) {
                    mTimeShiftManager.fastForward();
                    updateButtons();
                }
            }
        });
        initializeButton(mJumpNextButton, R.drawable.lb_ic_skip_next,
                R.string.play_controls_description_skip_next, null, new Runnable() {
            @Override
            public void run() {
                if (mTimeShiftManager.isAvailable()) {
                    mTimeShiftManager.jumpToNext();
                    updateControls(true);
                }
            }
        });
        int color = getResources().getColor(R.color.play_controls_recording_icon_color_on_focus,
                null);
        initializeButton(mRecordButton, R.drawable.ic_record_start, R.string
                .channels_item_record_start, color, new Runnable() {
            @Override
            public void run() {
                onRecordButtonClicked();
            }
        });
    }

    private boolean isCurrentChannelRecording() {
        Channel currentChannel = mMainActivity.getCurrentChannel();
        return currentChannel != null && mDvrManager != null
                && mDvrManager.getCurrentRecording(currentChannel.getId()) != null;
    }

    private void onRecordButtonClicked() {
        boolean isRecording = isCurrentChannelRecording();
        Channel currentChannel = mMainActivity.getCurrentChannel();
        TvApplication.getSingletons(getContext()).getTracker().sendMenuClicked(isRecording ?
                R.string.channels_item_record_start : R.string.channels_item_record_stop);
        if (!isRecording) {
            if (!(mDvrManager != null && mDvrManager.isChannelRecordable(currentChannel))) {
                Toast.makeText(mMainActivity, R.string.dvr_msg_cannot_record_channel,
                        Toast.LENGTH_SHORT).show();
            } else {
                Program program = TvApplication.getSingletons(mMainActivity).getProgramDataManager()
                        .getCurrentProgram(currentChannel.getId());
                DvrUiHelper.checkStorageStatusAndShowErrorMessage(mMainActivity,
                        currentChannel.getInputId(), new Runnable() {
                            @Override
                            public void run() {
                                DvrUiHelper.requestRecordingCurrentProgram(mMainActivity,
                                        currentChannel, program, true);
                            }
                        });
            }
        } else if (currentChannel != null) {
            DvrUiHelper.showStopRecordingDialog(mMainActivity, currentChannel.getId(),
                    DvrStopRecordingFragment.REASON_USER_STOP,
                    new HalfSizedDialogFragment.OnActionClickListener() {
                        @Override
                        public void onActionClick(long actionId) {
                            if (actionId == DvrStopRecordingFragment.ACTION_STOP) {
                                ScheduledRecording currentRecording =
                                        mDvrManager.getCurrentRecording(
                                                currentChannel.getId());
                                if (currentRecording != null) {
                                    mDvrManager.stopRecording(currentRecording);
                                }
                            }
                        }
                    });
        }
    }

    private void initializeButton(PlayControlsButton button, int imageResId,
            int descriptionId, Integer focusedIconColor, Runnable clickAction) {
        button.setImageResId(imageResId);
        button.setAction(clickAction);
        if (focusedIconColor != null) {
            button.setFocusedIconColor(focusedIconColor);
        }
        button.findViewById(R.id.button)
                .setContentDescription(getResources().getString(descriptionId));
    }

    @Override
    public void onBind(MenuRow row) {
        super.onBind(row);
        PlayControlsRow playControlsRow = (PlayControlsRow) row;
        mTvView = playControlsRow.getTvView();
        mTimeShiftManager = playControlsRow.getTimeShiftManager();
        mTimeShiftManager.setListener(new TimeShiftManager.Listener() {
            @Override
            public void onAvailabilityChanged() {
                updateMenuVisibility();
                PlayControlsRowView.this.updateAll(false);
            }

            @Override
            public void onPlayStatusChanged(int status) {
                updateMenuVisibility();
                if (mTimeShiftManager.isAvailable()) {
                    updateControls(false);
                }
            }

            @Override
            public void onRecordTimeRangeChanged() {
                if (mTimeShiftManager.isAvailable()) {
                    updateControls(false);
                }
            }

            @Override
            public void onCurrentPositionChanged() {
                if (mTimeShiftManager.isAvailable()) {
                    initializeTimeline();
                    updateControls(false);
                }
            }

            @Override
            public void onProgramInfoChanged() {
                if (mTimeShiftManager.isAvailable()) {
                    initializeTimeline();
                    updateControls(false);
                }
            }

            @Override
            public void onActionEnabledChanged(@TimeShiftActionId int actionId, boolean enabled) {
                // Move focus to the play/pause button when the PREVIOUS, NEXT, REWIND or
                // FAST_FORWARD button is clicked and the button becomes disabled.
                // No need to update the UI here because the UI will be updated by other callbacks.
                if (!enabled &&
                        ((actionId == TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS
                                && mJumpPreviousButton.hasFocus())
                        || (actionId == TimeShiftManager.TIME_SHIFT_ACTION_ID_REWIND
                                && mRewindButton.hasFocus())
                        || (actionId == TimeShiftManager.TIME_SHIFT_ACTION_ID_FAST_FORWARD
                                && mFastForwardButton.hasFocus())
                        || (actionId == TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT
                                && mJumpNextButton.hasFocus()))) {
                    mPlayPauseButton.requestFocus();
                }
            }
        });
        // force update to initialize everything
        updateAll(true);
    }

    private void initializeTimeline() {
        Program program = mTimeShiftManager.getProgramAt(
                mTimeShiftManager.getCurrentPositionMs());
        mProgramStartTimeMs = program.getStartTimeUtcMillis();
        mProgramEndTimeMs = program.getEndTimeUtcMillis();
        mProgress.setMax(mProgramEndTimeMs - mProgramStartTimeMs);
        updateRecTimeText();
        SoftPreconditions.checkArgument(mProgramStartTimeMs <= mProgramEndTimeMs);
    }

    private void updateMenuVisibility() {
        boolean keepMenuVisible =
                mTimeShiftManager.isAvailable() && !mTimeShiftManager.isNormalPlaying();
        getMenu().setKeepVisible(keepMenuVisible);
    }

    public void onPreselected() {
        updateControls(true);
    }

    @Override
    public void onSelected(boolean showTitle) {
        super.onSelected(showTitle);
        postHideRippleAnimation();
    }

    @Override
    public void initialize(@MenuShowReason int reason) {
        super.initialize(reason);
        switch (reason) {
            case Menu.REASON_PLAY_CONTROLS_JUMP_TO_PREVIOUS:
                if (mTimeShiftManager.isActionEnabled(
                        TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS)) {
                    setInitialFocusView(mJumpPreviousButton);
                } else {
                    setInitialFocusView(mPlayPauseButton);
                }
                break;
            case Menu.REASON_PLAY_CONTROLS_REWIND:
                if (mTimeShiftManager.isActionEnabled(
                        TimeShiftManager.TIME_SHIFT_ACTION_ID_REWIND)) {
                    setInitialFocusView(mRewindButton);
                } else {
                    setInitialFocusView(mPlayPauseButton);
                }
                break;
            case Menu.REASON_PLAY_CONTROLS_FAST_FORWARD:
                if (mTimeShiftManager.isActionEnabled(
                        TimeShiftManager.TIME_SHIFT_ACTION_ID_FAST_FORWARD)) {
                    setInitialFocusView(mFastForwardButton);
                } else {
                    setInitialFocusView(mPlayPauseButton);
                }
                break;
            case Menu.REASON_PLAY_CONTROLS_JUMP_TO_NEXT:
                if (mTimeShiftManager.isActionEnabled(
                        TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT)) {
                    setInitialFocusView(mJumpNextButton);
                } else {
                    setInitialFocusView(mPlayPauseButton);
                }
                break;
            case Menu.REASON_PLAY_CONTROLS_PLAY_PAUSE:
            case Menu.REASON_PLAY_CONTROLS_PLAY:
            case Menu.REASON_PLAY_CONTROLS_PAUSE:
            default:
                setInitialFocusView(mPlayPauseButton);
                break;
        }
        postHideRippleAnimation();
    }

    private void postHideRippleAnimation() {
        // Focus may be changed in another message if requestFocus is called in this message.
        // After the focus is actually changed, hideRippleAnimation should run
        // to reflect the result of the focus change. To be sure, hideRippleAnimation is posted.
        post(new Runnable() {
            @Override
            public void run() {
                mJumpPreviousButton.hideRippleAnimation();
                mRewindButton.hideRippleAnimation();
                mPlayPauseButton.hideRippleAnimation();
                mFastForwardButton.hideRippleAnimation();
                mJumpNextButton.hideRippleAnimation();
            }
        });
    }

    @Override
    protected void onChildFocusChange(View v, boolean hasFocus) {
        super.onChildFocusChange(v, hasFocus);
        if ((v.getParent().equals(mRewindButton) || v.getParent().equals(mFastForwardButton))
                && !hasFocus) {
            if (mTimeShiftManager.getPlayStatus() == TimeShiftManager.PLAY_STATUS_PLAYING) {
                mTimeShiftManager.play();
                updateButtons();
            }
        }
    }

    /**
     * Updates the view contents. It is called from the PlayControlsRow.
     */
    public void update() {
        updateAll(false);
    }

    private void updateAll(boolean forceUpdate) {
        if (mTimeShiftManager.isAvailable() && !mTvView.isScreenBlocked()) {
            setEnabled(true);
            initializeTimeline();
            mBackgroundView.setEnabled(true);
            setTextIfNeeded(mBackgroundView, null);
        } else {
            setEnabled(false);
            mBackgroundView.setEnabled(false);
            setTextIfNeeded(mBackgroundView, mUnavailableMessage);
        }
        // force the controls be updated no matter it's visible or not.
        updateControls(forceUpdate);
    }

    private void updateControls(boolean forceUpdate) {
        if (forceUpdate || getContentsView().isShown()) {
            updateTime();
            updateProgress();
            updateButtons();
            updateRecordButton();
            updateButtonMargin();
        }
    }

    private void updateTime() {
        if (isEnabled()) {
            mTimeText.setVisibility(View.VISIBLE);
            mTimeIndicator.setVisibility(View.VISIBLE);
        } else {
            mTimeText.setVisibility(View.INVISIBLE);
            mTimeIndicator.setVisibility(View.GONE);
            return;
        }
        long currentPositionMs = mTimeShiftManager.getCurrentPositionMs();
        int currentTimePositionPixel =
                convertDurationToPixel(currentPositionMs - mProgramStartTimeMs);
        mTimeText.setTranslationX(currentTimePositionPixel + mTimeTextLeftMargin);
        setTextIfNeeded(mTimeText, getTimeString(currentPositionMs));
        mTimeIndicator.setTranslationX(currentTimePositionPixel + mTimeIndicatorLeftMargin);
    }

    private void updateProgress() {
        if (isEnabled()) {
            long progressStartTimeMs = Math.min(mProgramEndTimeMs,
                    Math.max(mProgramStartTimeMs, mTimeShiftManager.getRecordStartTimeMs()));
            long currentPlayingTimeMs = Math.min(mProgramEndTimeMs,
                    Math.max(mProgramStartTimeMs, mTimeShiftManager.getCurrentPositionMs()));
            long progressEndTimeMs = Math.min(mProgramEndTimeMs,
                    Math.max(mProgramStartTimeMs, mTimeShiftManager.getRecordEndTimeMs()));
            mProgress.setProgressRange(progressStartTimeMs - mProgramStartTimeMs,
                    progressEndTimeMs - mProgramStartTimeMs);
            mProgress.setProgress(currentPlayingTimeMs - mProgramStartTimeMs);
        } else {
            mProgress.setProgressRange(0, 0);
        }
    }

    private void updateRecTimeText() {
        if (isEnabled()) {
            mProgramStartTimeText.setVisibility(View.VISIBLE);
            setTextIfNeeded(mProgramStartTimeText, getTimeString(mProgramStartTimeMs));
            mProgramEndTimeText.setVisibility(View.VISIBLE);
            setTextIfNeeded(mProgramEndTimeText, getTimeString(mProgramEndTimeMs));
        } else {
            mProgramStartTimeText.setVisibility(View.GONE);
            mProgramEndTimeText.setVisibility(View.GONE);
        }
    }

    private void updateButtons() {
        if (isEnabled()) {
            mPlayPauseButton.setVisibility(View.VISIBLE);
            mJumpPreviousButton.setVisibility(View.VISIBLE);
            mJumpNextButton.setVisibility(View.VISIBLE);
            mRewindButton.setVisibility(View.VISIBLE);
            mFastForwardButton.setVisibility(View.VISIBLE);
        } else {
            mPlayPauseButton.setVisibility(View.GONE);
            mJumpPreviousButton.setVisibility(View.GONE);
            mJumpNextButton.setVisibility(View.GONE);
            mRewindButton.setVisibility(View.GONE);
            mFastForwardButton.setVisibility(View.GONE);
            return;
        }

        if (mTimeShiftManager.getPlayStatus() == TimeShiftManager.PLAY_STATUS_PAUSED) {
            mPlayPauseButton.setImageResId(R.drawable.lb_ic_play);
            mPlayPauseButton.setEnabled(mTimeShiftManager.isActionEnabled(
                    TimeShiftManager.TIME_SHIFT_ACTION_ID_PLAY));
        } else {
            mPlayPauseButton.setImageResId(R.drawable.lb_ic_pause);
            mPlayPauseButton.setEnabled(mTimeShiftManager.isActionEnabled(
                    TimeShiftManager.TIME_SHIFT_ACTION_ID_PAUSE));
        }
        mJumpPreviousButton.setEnabled(mTimeShiftManager.isActionEnabled(
                TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS));
        mRewindButton.setEnabled(mTimeShiftManager.isActionEnabled(
                TimeShiftManager.TIME_SHIFT_ACTION_ID_REWIND));
        mFastForwardButton.setEnabled(mTimeShiftManager.isActionEnabled(
                TimeShiftManager.TIME_SHIFT_ACTION_ID_FAST_FORWARD));
        mJumpNextButton.setEnabled(mTimeShiftManager.isActionEnabled(
                TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT));
        mJumpPreviousButton.setVisibility(VISIBLE);
        mJumpNextButton.setVisibility(VISIBLE);
        updateButtonMargin();

        PlayControlsButton button;
        if (mTimeShiftManager.getPlayDirection() == TimeShiftManager.PLAY_DIRECTION_FORWARD) {
            mRewindButton.setLabel(null);
            button = mFastForwardButton;
        } else {
            mFastForwardButton.setLabel(null);
            button = mRewindButton;
        }
        if (mTimeShiftManager.getDisplayedPlaySpeed() == TimeShiftManager.PLAY_SPEED_1X) {
            button.setLabel(null);
        } else {
            button.setLabel(getResources().getString(R.string.play_controls_speed,
                    mTimeShiftManager.getDisplayedPlaySpeed()));
        }
    }

    private void updateRecordButton() {
        if (isEnabled()) {
            mRecordButton.setVisibility(VISIBLE);
        } else {
            mRecordButton.setVisibility(GONE);
            return;
        }
        if (!(mDvrManager != null
                && mDvrManager.isChannelRecordable(mMainActivity.getCurrentChannel()))) {
            mRecordButton.setVisibility(View.GONE);
            updateButtonMargin();
            return;
        }
        mRecordButton.setVisibility(View.VISIBLE);
        updateButtonMargin();
        if (isCurrentChannelRecording()) {
            mRecordButton.setImageResId(R.drawable.ic_record_stop);
        } else {
            mRecordButton.setImageResId(R.drawable.ic_record_start);
        }
    }

    private void updateButtonMargin() {
        int numOfVisibleButtons = (mJumpPreviousButton.getVisibility() == View.VISIBLE ? 1 : 0)
                + (mRewindButton.getVisibility() == View.VISIBLE ? 1 : 0)
                + (mPlayPauseButton.getVisibility() == View.VISIBLE ? 1 : 0)
                + (mFastForwardButton.getVisibility() == View.VISIBLE ? 1 : 0)
                + (mJumpNextButton.getVisibility() == View.VISIBLE ? 1 : 0)
                + (mRecordButton.getVisibility() == View.VISIBLE ? 1 : 0);
        boolean useCompactLayout = numOfVisibleButtons > NORMAL_WIDTH_MAX_BUTTON_COUNT;
        if (mUseCompactLayout == useCompactLayout) {
            return;
        }
        mUseCompactLayout = useCompactLayout;
        int margin = mUseCompactLayout ? mCompactButtonMargin : mNormalButtonMargin;
        updateButtonMargin(mJumpPreviousButton, margin);
        updateButtonMargin(mRewindButton, margin);
        updateButtonMargin(mPlayPauseButton, margin);
        updateButtonMargin(mFastForwardButton, margin);
        updateButtonMargin(mJumpNextButton, margin);
        updateButtonMargin(mRecordButton, margin);
    }

    private void updateButtonMargin(PlayControlsButton button, int margin) {
        MarginLayoutParams params = (MarginLayoutParams) button.getLayoutParams();
        params.setMargins(margin, 0, margin, 0);
        button.setLayoutParams(params);
    }

    private String getTimeString(long timeMs) {
        return mTimeFormat.format(timeMs);
    }

    private int convertDurationToPixel(long duration) {
        if (mProgramEndTimeMs <= mProgramStartTimeMs) {
            return 0;
        }
        return (int) (duration * mTimelineWidth / (mProgramEndTimeMs - mProgramStartTimeMs));
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDvrDataManager != null) {
            mDvrDataManager.removeScheduledRecordingListener(mScheduledRecordingListener);
        }
    }

    private void setTextIfNeeded(TextView textView, String text) {
        if (!TextUtils.equals(textView.getText(), text)) {
            textView.setText(text);
        }
    }
}
