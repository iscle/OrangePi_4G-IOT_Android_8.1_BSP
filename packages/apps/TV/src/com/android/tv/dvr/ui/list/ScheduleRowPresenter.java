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
 * limitations under the License
 */

package com.android.tv.dvr.ui.list;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.dialog.HalfSizedDialogFragment;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrStopRecordingFragment;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.util.ToastUtils;
import com.android.tv.util.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * A RowPresenter for {@link ScheduleRow}.
 */
@TargetApi(Build.VERSION_CODES.N)
class ScheduleRowPresenter extends RowPresenter {
    private static final String TAG = "ScheduleRowPresenter";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ACTION_START_RECORDING, ACTION_STOP_RECORDING, ACTION_CREATE_SCHEDULE,
            ACTION_REMOVE_SCHEDULE})
    public @interface ScheduleRowAction {}
    /** An action to start recording. */
    public static final int ACTION_START_RECORDING = 1;
    /** An action to stop recording. */
    public static final int ACTION_STOP_RECORDING = 2;
    /** An action to create schedule for the row. */
    public static final int ACTION_CREATE_SCHEDULE = 3;
    /** An action to remove the schedule. */
    public static final int ACTION_REMOVE_SCHEDULE = 4;

    private final Context mContext;
    private final DvrManager mDvrManager;
    private final DvrScheduleManager mDvrScheduleManager;

    private final String mTunerConflictWillNotBeRecordedInfo;
    private final String mTunerConflictWillBePartiallyRecordedInfo;
    private final int mAnimationDuration;

    private int mLastFocusedViewId;

    /**
     * A ViewHolder for {@link ScheduleRow}
     */
    public static class ScheduleRowViewHolder extends RowPresenter.ViewHolder {
        private ScheduleRowPresenter mPresenter;
        @ScheduleRowAction private int[] mActions;
        private boolean mLtr;
        private LinearLayout mInfoContainer;
        // The first action is on the right of the second action.
        private RelativeLayout mSecondActionContainer;
        private RelativeLayout mFirstActionContainer;
        private View mSelectorView;
        private TextView mTimeView;
        private TextView mProgramTitleView;
        private TextView mInfoSeparatorView;
        private TextView mChannelNameView;
        private TextView mConflictInfoView;
        private ImageView mSecondActionView;
        private ImageView mFirstActionView;

        private Runnable mPendingAnimationRunnable;

        private final int mSelectorTranslationDelta;
        private final int mSelectorWidthDelta;
        private final int mInfoContainerTargetWidthWithNoAction;
        private final int mInfoContainerTargetWidthWithOneAction;
        private final int mInfoContainerTargetWidthWithTwoAction;
        private final int mRoundRectRadius;

        private final OnFocusChangeListener mOnFocusChangeListener =
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean focused) {
                        view.post(new Runnable() {
                            @Override
                            public void run() {
                                if (view.isFocused()) {
                                    mPresenter.mLastFocusedViewId = view.getId();
                                }
                                updateSelector();
                            }
                        });
                    }
                };

        public ScheduleRowViewHolder(View view, ScheduleRowPresenter presenter) {
            super(view);
            mPresenter = presenter;
            mLtr = view.getContext().getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_LTR;
            mInfoContainer = (LinearLayout) view.findViewById(R.id.info_container);
            mSecondActionContainer = (RelativeLayout) view.findViewById(
                    R.id.action_second_container);
            mSecondActionView = (ImageView) view.findViewById(R.id.action_second);
            mFirstActionContainer = (RelativeLayout) view.findViewById(
                    R.id.action_first_container);
            mFirstActionView = (ImageView) view.findViewById(R.id.action_first);
            mSelectorView = view.findViewById(R.id.selector);
            mTimeView = (TextView) view.findViewById(R.id.time);
            mProgramTitleView = (TextView) view.findViewById(R.id.program_title);
            mInfoSeparatorView = (TextView) view.findViewById(R.id.info_separator);
            mChannelNameView = (TextView) view.findViewById(R.id.channel_name);
            mConflictInfoView = (TextView) view.findViewById(R.id.conflict_info);
            Resources res = view.getResources();
            mSelectorTranslationDelta =
                    res.getDimensionPixelSize(R.dimen.dvr_schedules_item_section_margin)
                    - res.getDimensionPixelSize(R.dimen.dvr_schedules_item_focus_translation_delta);
            mSelectorWidthDelta = res.getDimensionPixelSize(
                    R.dimen.dvr_schedules_item_focus_width_delta);
            mRoundRectRadius = res.getDimensionPixelSize(R.dimen.dvr_schedules_selector_radius);
            int fullWidth = res.getDimensionPixelSize(
                    R.dimen.dvr_schedules_item_width)
                    - 2 * res.getDimensionPixelSize(R.dimen.dvr_schedules_layout_padding);
            mInfoContainerTargetWidthWithNoAction = fullWidth + 2 * mRoundRectRadius;
            mInfoContainerTargetWidthWithOneAction = fullWidth
                    - res.getDimensionPixelSize(R.dimen.dvr_schedules_item_section_margin)
                    - res.getDimensionPixelSize(R.dimen.dvr_schedules_item_delete_width)
                    + mRoundRectRadius + mSelectorWidthDelta;
            mInfoContainerTargetWidthWithTwoAction = mInfoContainerTargetWidthWithOneAction
                    - res.getDimensionPixelSize(R.dimen.dvr_schedules_item_section_margin)
                    - res.getDimensionPixelSize(R.dimen.dvr_schedules_item_icon_size);

            mInfoContainer.setOnFocusChangeListener(mOnFocusChangeListener);
            mFirstActionContainer.setOnFocusChangeListener(mOnFocusChangeListener);
            mSecondActionContainer.setOnFocusChangeListener(mOnFocusChangeListener);
        }

        /**
         * Returns time view.
         */
        public TextView getTimeView() {
            return mTimeView;
        }

        /**
         * Returns title view.
         */
        public TextView getProgramTitleView() {
            return mProgramTitleView;
        }

        private void updateSelector() {
            int animationDuration = mSelectorView.getResources().getInteger(
                    android.R.integer.config_shortAnimTime);
            DecelerateInterpolator interpolator = new DecelerateInterpolator();

            if (mInfoContainer.isFocused() || mSecondActionContainer.isFocused()
                    || mFirstActionContainer.isFocused()) {
                final ViewGroup.LayoutParams lp = mSelectorView.getLayoutParams();
                final int targetWidth;
                if (mInfoContainer.isFocused()) {
                    // Use actions to check the visibility of the actions instead of calling
                    // View.getVisibility() because the view could be on the hiding animation.
                    if (mActions == null || mActions.length == 0) {
                        targetWidth = mInfoContainerTargetWidthWithNoAction;
                    } else if (mActions.length == 1) {
                        targetWidth = mInfoContainerTargetWidthWithOneAction;
                    } else {
                        targetWidth = mInfoContainerTargetWidthWithTwoAction;
                    }
                } else if (mSecondActionContainer.isFocused()) {
                    targetWidth = Math.max(mSecondActionContainer.getWidth(), 2 * mRoundRectRadius);
                } else {
                    targetWidth = mFirstActionContainer.getWidth() + mRoundRectRadius
                            + mSelectorTranslationDelta;
                }

                float targetTranslationX;
                if (mInfoContainer.isFocused()) {
                    targetTranslationX = mLtr ? mInfoContainer.getLeft() - mRoundRectRadius
                            - mSelectorView.getLeft() :
                            mInfoContainer.getRight() + mRoundRectRadius - mSelectorView.getRight();
                } else if (mSecondActionContainer.isFocused()) {
                    if (mSecondActionContainer.getWidth() > 2 * mRoundRectRadius) {
                        targetTranslationX = mLtr ? mSecondActionContainer.getLeft() -
                                mSelectorView.getLeft()
                                : mSecondActionContainer.getRight() - mSelectorView.getRight();
                    } else {
                        targetTranslationX = mLtr ? mSecondActionContainer.getLeft() -
                                (mRoundRectRadius - mSecondActionContainer.getWidth() / 2) -
                                mSelectorView.getLeft()
                                : mSecondActionContainer.getRight() +
                                (mRoundRectRadius - mSecondActionContainer.getWidth() / 2) -
                                mSelectorView.getRight();
                    }
                } else {
                    targetTranslationX = mLtr ? mFirstActionContainer.getLeft()
                            - mSelectorTranslationDelta - mSelectorView.getLeft()
                            : mFirstActionContainer.getRight() + mSelectorTranslationDelta
                            - mSelectorView.getRight();
                }

                if (mSelectorView.getAlpha() == 0) {
                    mSelectorView.setTranslationX(targetTranslationX);
                    lp.width = targetWidth;
                    mSelectorView.requestLayout();
                }

                // animate the selector in and to the proper width and translation X.
                final float deltaWidth = lp.width - targetWidth;
                mSelectorView.animate().cancel();
                mSelectorView.animate().translationX(targetTranslationX).alpha(1f)
                        .setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                // Set width to the proper width for this animation step.
                                lp.width = targetWidth + Math.round(
                                        deltaWidth * (1f - animation.getAnimatedFraction()));
                                mSelectorView.requestLayout();
                            }
                        }).setDuration(animationDuration).setInterpolator(interpolator).start();
                if (mPendingAnimationRunnable != null) {
                    mPendingAnimationRunnable.run();
                    mPendingAnimationRunnable = null;
                }
            } else {
                mSelectorView.animate().cancel();
                mSelectorView.animate().alpha(0f).setDuration(animationDuration)
                        .setInterpolator(interpolator).setUpdateListener(null).start();
            }
        }

        /**
         * Grey out the information body.
         */
        public void greyOutInfo() {
            mTimeView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info_grey, null));
            mProgramTitleView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info_grey, null));
            mInfoSeparatorView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info_grey, null));
            mChannelNameView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info_grey, null));
            mConflictInfoView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info_grey, null));
        }

        /**
         * Reverse grey out operation.
         */
        public void whiteBackInfo() {
            mTimeView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info, null));
            mProgramTitleView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_main, null));
            mInfoSeparatorView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info, null));
            mChannelNameView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info, null));
            mConflictInfoView.setTextColor(mInfoContainer.getResources().getColor(R.color
                    .dvr_schedules_item_info, null));
        }
    }

    public ScheduleRowPresenter(Context context) {
        setHeaderPresenter(null);
        setSelectEffectEnabled(false);
        mContext = context;
        mDvrManager = TvApplication.getSingletons(context).getDvrManager();
        mDvrScheduleManager = TvApplication.getSingletons(context).getDvrScheduleManager();
        mTunerConflictWillNotBeRecordedInfo = mContext.getString(
                R.string.dvr_schedules_tuner_conflict_will_not_be_recorded_info);
        mTunerConflictWillBePartiallyRecordedInfo = mContext.getString(
                R.string.dvr_schedules_tuner_conflict_will_be_partially_recorded);
        mAnimationDuration = mContext.getResources().getInteger(
                android.R.integer.config_shortAnimTime);
    }

    @Override
    public ViewHolder createRowViewHolder(ViewGroup parent) {
        return onGetScheduleRowViewHolder(LayoutInflater.from(mContext)
                .inflate(R.layout.dvr_schedules_item, parent, false));
    }

    /**
     * Returns context.
     */
    protected Context getContext() {
        return mContext;
    }

    /**
     * Returns DVR manager.
     */
    protected DvrManager getDvrManager() {
        return mDvrManager;
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);
        ScheduleRowViewHolder viewHolder = (ScheduleRowViewHolder) vh;
        ScheduleRow row = (ScheduleRow) item;
        @ScheduleRowAction int[] actions = getAvailableActions(row);
        viewHolder.mActions = actions;
        viewHolder.mInfoContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isInfoClickable(row)) {
                    onInfoClicked(row);
                }
            }
        });

        viewHolder.mFirstActionContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onActionClicked(actions[0], row);
            }
        });

        viewHolder.mSecondActionContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onActionClicked(actions[1], row);
            }
        });

        viewHolder.mTimeView.setText(onGetRecordingTimeText(row));
        String programInfoText = onGetProgramInfoText(row);
        if (TextUtils.isEmpty(programInfoText)) {
            int durationMins = Math.max(1, Utils.getRoundOffMinsFromMs(row.getDuration()));
            programInfoText = mContext.getResources().getQuantityString(
                    R.plurals.dvr_schedules_recording_duration, durationMins, durationMins);
        }
        String channelName = getChannelNameText(row);
        viewHolder.mProgramTitleView.setText(programInfoText);
        viewHolder.mInfoSeparatorView.setVisibility((!TextUtils.isEmpty(programInfoText)
                && !TextUtils.isEmpty(channelName)) ? View.VISIBLE : View.GONE);
        viewHolder.mChannelNameView.setText(channelName);
        if (actions != null) {
            switch (actions.length) {
                case 2:
                    viewHolder.mSecondActionView.setImageResource(getImageForAction(actions[1]));
                    // pass through
                case 1:
                    viewHolder.mFirstActionView.setImageResource(getImageForAction(actions[0]));
                    break;
            }
        }
        if (mDvrManager.isConflicting(row.getSchedule())) {
            String conflictInfo;
            if (mDvrScheduleManager.isPartiallyConflicting(row.getSchedule())) {
                conflictInfo = mTunerConflictWillBePartiallyRecordedInfo;
            } else {
                conflictInfo = mTunerConflictWillNotBeRecordedInfo;
            }
            viewHolder.mConflictInfoView.setText(conflictInfo);
            viewHolder.mConflictInfoView.setVisibility(View.VISIBLE);
        } else {
            viewHolder.mConflictInfoView.setVisibility(View.GONE);
        }
        if (shouldBeGrayedOut(row)) {
            viewHolder.greyOutInfo();
        } else {
            viewHolder.whiteBackInfo();
        }
        viewHolder.mInfoContainer.setFocusable(isInfoClickable(row));
        updateActionContainer(viewHolder, viewHolder.isSelected());
    }

    private int getImageForAction(@ScheduleRowAction int action) {
        switch (action) {
            case ACTION_START_RECORDING:
                return R.drawable.ic_record_start;
            case ACTION_STOP_RECORDING:
                return R.drawable.ic_record_stop;
            case ACTION_CREATE_SCHEDULE:
                return R.drawable.ic_scheduled_recording;
            case ACTION_REMOVE_SCHEDULE:
                return R.drawable.ic_dvr_cancel;
            default:
                return 0;
        }
    }

    /**
     * Returns view holder for schedule row.
     */
    protected ScheduleRowViewHolder onGetScheduleRowViewHolder(View view) {
        return new ScheduleRowViewHolder(view, this);
    }

    /**
     * Returns time text for time view from scheduled recording.
     */
    protected String onGetRecordingTimeText(ScheduleRow row) {
        return Utils.getDurationString(mContext, row.getStartTimeMs(), row.getEndTimeMs(), true,
                false, true, 0);
    }

    /**
     * Returns program info text for program title view.
     */
    protected String onGetProgramInfoText(ScheduleRow row) {
        return row.getProgramTitleWithEpisodeNumber(mContext);
    }

    private String getChannelNameText(ScheduleRow row) {
        Channel channel = TvApplication.getSingletons(mContext).getChannelDataManager()
                .getChannel(row.getChannelId());
        return channel == null ? null :
                TextUtils.isEmpty(channel.getDisplayName()) ? channel.getDisplayNumber() :
                        channel.getDisplayName().trim() + " " + channel.getDisplayNumber();
    }

    /**
     * Called when user click Info in {@link ScheduleRow}.
     */
    protected void onInfoClicked(ScheduleRow row) {
        DvrUiHelper.startDetailsActivity((Activity) mContext, row.getSchedule(), null, true);
    }

    private boolean isInfoClickable(ScheduleRow row) {
        return row.getSchedule() != null
                && (row.getSchedule().isNotStarted() || row.getSchedule().isInProgress());
    }

    /**
     * Called when the button in a row is clicked.
     */
    protected void onActionClicked(@ScheduleRowAction final int action, ScheduleRow row) {
        switch (action) {
            case ACTION_START_RECORDING:
                onStartRecording(row);
                break;
            case ACTION_STOP_RECORDING:
                onStopRecording(row);
                break;
            case ACTION_CREATE_SCHEDULE:
                onCreateSchedule(row);
                break;
            case ACTION_REMOVE_SCHEDULE:
                onRemoveSchedule(row);
                break;
        }
    }

    /**
     * Action handler for {@link #ACTION_START_RECORDING}.
     */
    protected void onStartRecording(ScheduleRow row) {
        ScheduledRecording schedule = row.getSchedule();
        if (schedule == null) {
            // This row has been deleted.
            return;
        }
        // Checks if there are current recordings that will be stopped by schedule this program.
        // If so, shows confirmation dialog to users.
        List<ScheduledRecording> conflictSchedules = mDvrScheduleManager.getConflictingSchedules(
                schedule.getChannelId(), System.currentTimeMillis(), schedule.getEndTimeMs());
        for (int i = conflictSchedules.size() - 1; i >= 0; i--) {
            ScheduledRecording conflictSchedule = conflictSchedules.get(i);
            if (conflictSchedule.isInProgress()) {
                DvrUiHelper.showStopRecordingDialog((Activity) mContext,
                        conflictSchedule.getChannelId(),
                        DvrStopRecordingFragment.REASON_ON_CONFLICT,
                        new HalfSizedDialogFragment.OnActionClickListener() {
                            @Override
                            public void onActionClick(long actionId) {
                                if (actionId == DvrStopRecordingFragment.ACTION_STOP) {
                                    onStartRecordingInternal(row);
                                }
                            }
                        });
                return;
            }
        }
        onStartRecordingInternal(row);
    }

    private void onStartRecordingInternal(ScheduleRow row) {
        if (row.isOnAir() && !row.isRecordingInProgress() && !row.isStartRecordingRequested()) {
            row.setStartRecordingRequested(true);
            if (row.isRecordingNotStarted()) {
                mDvrManager.setHighestPriority(row.getSchedule());
            } else if (row.isRecordingFinished()) {
                mDvrManager.addSchedule(ScheduledRecording.buildFrom(row.getSchedule())
                        .setId(ScheduledRecording.ID_NOT_SET)
                        .setState(ScheduledRecording.STATE_RECORDING_NOT_STARTED)
                        .setPriority(mDvrManager.suggestHighestPriority(row.getSchedule()))
                        .build());
            } else {
                SoftPreconditions.checkState(false, TAG, "Invalid row state to start recording: "
                        + row);
                return;
            }
            String msg = mContext.getString(R.string.dvr_msg_current_program_scheduled,
                    row.getSchedule().getProgramTitle(),
                    Utils.toTimeString(row.getEndTimeMs(), false));
            ToastUtils.show(mContext, msg, Toast.LENGTH_SHORT);
        }
    }

    /**
     * Action handler for {@link #ACTION_STOP_RECORDING}.
     */
    protected void onStopRecording(ScheduleRow row) {
        if (row.getSchedule() == null) {
            // This row has been deleted.
            return;
        }
        if (row.isRecordingInProgress() && !row.isStopRecordingRequested()) {
            row.setStopRecordingRequested(true);
            mDvrManager.stopRecording(row.getSchedule());
            CharSequence deletedInfo = onGetProgramInfoText(row);
            if (TextUtils.isEmpty(deletedInfo)) {
                deletedInfo = getChannelNameText(row);
            }
            ToastUtils.show(mContext, mContext.getResources()
                    .getString(R.string.dvr_schedules_deletion_info, deletedInfo),
                    Toast.LENGTH_SHORT);
        }
    }

    /**
     * Action handler for {@link #ACTION_CREATE_SCHEDULE}.
     */
    protected void onCreateSchedule(ScheduleRow row) {
        if (row.getSchedule() == null) {
            // This row has been deleted.
            return;
        }
        if (!row.isOnAir()) {
            if (row.isScheduleCanceled()) {
                mDvrManager.updateScheduledRecording(ScheduledRecording.buildFrom(row.getSchedule())
                        .setState(ScheduledRecording.STATE_RECORDING_NOT_STARTED)
                        .setPriority(mDvrManager.suggestHighestPriority(row.getSchedule()))
                        .build());
                String msg = mContext.getString(R.string.dvr_msg_program_scheduled,
                        row.getSchedule().getProgramTitle());
                ToastUtils.show(mContext, msg, Toast.LENGTH_SHORT);
            } else if (mDvrManager.isConflicting(row.getSchedule())) {
                mDvrManager.setHighestPriority(row.getSchedule());
            }
        }
    }

    /**
     * Action handler for {@link #ACTION_REMOVE_SCHEDULE}.
     */
    protected void onRemoveSchedule(ScheduleRow row) {
        if (row.getSchedule() == null) {
            // This row has been deleted.
            return;
        }
        CharSequence deletedInfo = null;
        if (row.isOnAir()) {
            if (row.isRecordingNotStarted()) {
                deletedInfo = getDeletedInfo(row);
                mDvrManager.removeScheduledRecording(row.getSchedule());
            }
        } else {
            if (mDvrManager.isConflicting(row.getSchedule())
                    && !shouldKeepScheduleAfterRemoving()) {
                deletedInfo = getDeletedInfo(row);
                mDvrManager.removeScheduledRecording(row.getSchedule());
            } else if (row.isRecordingNotStarted()) {
                deletedInfo = getDeletedInfo(row);
                mDvrManager.updateScheduledRecording(ScheduledRecording.buildFrom(row.getSchedule())
                        .setState(ScheduledRecording.STATE_RECORDING_CANCELED)
                        .build());
            }
        }
        if (deletedInfo != null) {
            ToastUtils.show(mContext, mContext.getResources()
                            .getString(R.string.dvr_schedules_deletion_info, deletedInfo),
                    Toast.LENGTH_SHORT);
        }
    }

    private CharSequence getDeletedInfo(ScheduleRow row) {
        CharSequence deletedInfo = onGetProgramInfoText(row);
        if (TextUtils.isEmpty(deletedInfo)) {
            return getChannelNameText(row);
        }
        return deletedInfo;
    }

    @Override
    protected void onRowViewSelected(ViewHolder vh, boolean selected) {
        super.onRowViewSelected(vh, selected);
        updateActionContainer(vh, selected);
    }

    /**
     * Internal method for onRowViewSelected, can be customized by subclass.
     */
    private void updateActionContainer(ViewHolder vh, boolean selected) {
        ScheduleRowViewHolder viewHolder = (ScheduleRowViewHolder) vh;
        viewHolder.mSecondActionContainer.animate().setListener(null).cancel();
        viewHolder.mFirstActionContainer.animate().setListener(null).cancel();
        if (selected && viewHolder.mActions != null) {
            switch (viewHolder.mActions.length) {
                case 2:
                    prepareShowActionView(viewHolder.mSecondActionContainer);
                    prepareShowActionView(viewHolder.mFirstActionContainer);
                    viewHolder.mPendingAnimationRunnable = new Runnable() {
                        @Override
                        public void run() {
                            showActionView(viewHolder.mSecondActionContainer);
                            showActionView(viewHolder.mFirstActionContainer);
                        }
                    };
                    break;
                case 1:
                    prepareShowActionView(viewHolder.mFirstActionContainer);
                    viewHolder.mPendingAnimationRunnable = new Runnable() {
                        @Override
                        public void run() {
                            hideActionView(viewHolder.mSecondActionContainer, View.GONE);
                            showActionView(viewHolder.mFirstActionContainer);
                        }
                    };
                    if (mLastFocusedViewId == R.id.action_second_container) {
                        mLastFocusedViewId = R.id.info_container;
                    }
                    break;
                case 0:
                default:
                    viewHolder.mPendingAnimationRunnable = new Runnable() {
                        @Override
                        public void run() {
                            hideActionView(viewHolder.mSecondActionContainer, View.GONE);
                            hideActionView(viewHolder.mFirstActionContainer, View.GONE);
                        }
                    };
                    mLastFocusedViewId = R.id.info_container;
                    SoftPreconditions.checkState(viewHolder.mInfoContainer.isFocusable(), TAG,
                            "No focusable view in this row: " + viewHolder);
                    break;
            }
            View view = viewHolder.view.findViewById(mLastFocusedViewId);
            if (view != null && view.getVisibility() == View.VISIBLE) {
                // When the row is selected, information container gets the initial focus.
                // To give the focus to the same control as the previous row, we need to call
                // requestFocus() explicitly.
                if (view.hasFocus()) {
                    viewHolder.mPendingAnimationRunnable.run();
                } else if (view.isFocusable()){
                    view.requestFocus();
                } else {
                    viewHolder.view.requestFocus();
                }
            }
        } else {
            viewHolder.mPendingAnimationRunnable = null;
            hideActionView(viewHolder.mFirstActionContainer, View.GONE);
            hideActionView(viewHolder.mSecondActionContainer, View.GONE);
        }
    }

    private void prepareShowActionView(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0.0f);
        }
        view.setVisibility(View.VISIBLE);
    }

    /**
     * Add animation when view is visible.
     */
    private void showActionView(View view) {
        view.animate().alpha(1.0f).setInterpolator(new DecelerateInterpolator())
                .setDuration(mAnimationDuration).start();
    }

    /**
     * Add animation when view change to invisible.
     */
    private void hideActionView(View view, int visibility) {
        if (view.getVisibility() != View.VISIBLE) {
            if (view.getVisibility() != visibility) {
                view.setVisibility(visibility);
            }
            return;
        }
        view.animate().alpha(0.0f).setInterpolator(new DecelerateInterpolator())
                .setDuration(mAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(visibility);
                        view.animate().setListener(null);
                    }
                }).start();
    }

    /**
     * Returns the available actions according to the row's state. It should be the reverse order
     * with that in the screen.
     */
    @ScheduleRowAction
    protected int[] getAvailableActions(ScheduleRow row) {
        if (row.getSchedule() != null) {
            if (row.isRecordingInProgress()) {
                return new int[]{ACTION_STOP_RECORDING};
            } else if (row.isOnAir()) {
                if (row.isRecordingNotStarted()) {
                    if (canResolveConflict()) {
                        // The "START" action can change the conflict states.
                        return new int[] {ACTION_REMOVE_SCHEDULE, ACTION_START_RECORDING};
                    } else {
                        return new int[] {ACTION_REMOVE_SCHEDULE};
                    }
                } else if (row.isRecordingFinished()) {
                    return new int[] {ACTION_START_RECORDING};
                } else {
                    SoftPreconditions.checkState(false, TAG, "Invalid row state in checking the"
                            + " available actions(on air): " + row);
                }
            } else {
                if (row.isScheduleCanceled()) {
                    return new int[] {ACTION_CREATE_SCHEDULE};
                } else if (mDvrManager.isConflicting(row.getSchedule()) && canResolveConflict()) {
                    return new int[] {ACTION_REMOVE_SCHEDULE, ACTION_CREATE_SCHEDULE};
                } else if (row.isRecordingNotStarted()) {
                    return new int[] {ACTION_REMOVE_SCHEDULE};
                } else {
                    SoftPreconditions.checkState(false, TAG, "Invalid row state in checking the"
                            + " available actions(future schedule): " + row);
                }
            }
        }
        return null;
    }

    /**
     * Check if the conflict can be resolved in this screen.
     */
    protected boolean canResolveConflict() {
        return true;
    }

    /**
     * Check if the schedule should be kept after removing it.
     */
    protected boolean shouldKeepScheduleAfterRemoving() {
        return false;
    }

    /**
     * Checks if the row should be grayed out.
     */
    protected boolean shouldBeGrayedOut(ScheduleRow row) {
        return row.getSchedule() == null
                || (row.isOnAir() && !row.isRecordingInProgress())
                || mDvrManager.isConflicting(row.getSchedule())
                || row.isScheduleCanceled();
    }
}
