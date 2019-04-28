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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.dvr.ui.list.SchedulesHeaderRow.SeriesRecordingHeaderRow;

/**
 * A base class for RowPresenter for {@link SchedulesHeaderRow}
 */
abstract class SchedulesHeaderRowPresenter extends RowPresenter {
    private Context mContext;

    public SchedulesHeaderRowPresenter(Context context) {
        setHeaderPresenter(null);
        setSelectEffectEnabled(false);
        mContext = context;
    }

    /**
     * Returns the context.
     */
    Context getContext() {
        return mContext;
    }

    /**
     * A ViewHolder for {@link SchedulesHeaderRow}.
     */
    public static class SchedulesHeaderRowViewHolder extends RowPresenter.ViewHolder {
        private TextView mTitle;
        private TextView mDescription;

        public SchedulesHeaderRowViewHolder(Context context, ViewGroup parent) {
            super(LayoutInflater.from(context).inflate(R.layout.dvr_schedules_header, parent,
                    false));
            mTitle = (TextView) view.findViewById(R.id.header_title);
            mDescription = (TextView) view.findViewById(R.id.header_description);
        }
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder viewHolder, Object item) {
        super.onBindRowViewHolder(viewHolder, item);
        SchedulesHeaderRowViewHolder headerViewHolder = (SchedulesHeaderRowViewHolder) viewHolder;
        SchedulesHeaderRow header = (SchedulesHeaderRow) item;
        headerViewHolder.mTitle.setText(header.getTitle());
        headerViewHolder.mDescription.setText(header.getDescription());
    }

    /**
     * A presenter for {@link SchedulesHeaderRow.DateHeaderRow}.
     */
    public static class DateHeaderRowPresenter extends SchedulesHeaderRowPresenter {
        public DateHeaderRowPresenter(Context context) {
            super(context);
        }

        @Override
        protected ViewHolder createRowViewHolder(ViewGroup parent) {
            return new DateHeaderRowViewHolder(getContext(), parent);
        }

        /**
         * A ViewHolder for
         * {@link SchedulesHeaderRow.DateHeaderRow}.
         */
        public static class DateHeaderRowViewHolder extends SchedulesHeaderRowViewHolder {
            public DateHeaderRowViewHolder(Context context, ViewGroup parent) {
                super(context, parent);
            }
        }
    }

    /**
     * A presenter for {@link SeriesRecordingHeaderRow}.
     */
    public static class SeriesRecordingHeaderRowPresenter extends SchedulesHeaderRowPresenter {
        private final boolean mLtr;
        private final Drawable mSettingsDrawable;
        private final Drawable mCancelDrawable;
        private final Drawable mResumeDrawable;

        private final String mSettingsInfo;
        private final String mCancelAllInfo;
        private final String mResumeInfo;

        public SeriesRecordingHeaderRowPresenter(Context context) {
            super(context);
            mLtr = context.getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_LTR;
            mSettingsDrawable = context.getDrawable(R.drawable.ic_settings);
            mCancelDrawable = context.getDrawable(R.drawable.ic_dvr_cancel_large);
            mResumeDrawable = context.getDrawable(R.drawable.ic_record_start);
            mSettingsInfo = context.getString(R.string.dvr_series_schedules_settings);
            mCancelAllInfo = context.getString(R.string.dvr_series_schedules_stop);
            mResumeInfo = context.getString(R.string.dvr_series_schedules_start);
        }

        @Override
        protected ViewHolder createRowViewHolder(ViewGroup parent) {
            return new SeriesHeaderRowViewHolder(getContext(), parent);
        }

        @Override
        protected void onBindRowViewHolder(RowPresenter.ViewHolder viewHolder, Object item) {
            super.onBindRowViewHolder(viewHolder, item);
            SeriesHeaderRowViewHolder headerViewHolder =
                    (SeriesHeaderRowViewHolder) viewHolder;
            SeriesRecordingHeaderRow header = (SeriesRecordingHeaderRow) item;
            headerViewHolder.mSeriesSettingsButton.setVisibility(
                    header.getSeriesRecording().isStopped() ? View.INVISIBLE : View.VISIBLE);
            headerViewHolder.mSeriesSettingsButton.setText(mSettingsInfo);
            setTextDrawable(headerViewHolder.mSeriesSettingsButton, mSettingsDrawable);
            if (header.getSeriesRecording().isStopped()) {
                headerViewHolder.mToggleStartStopButton.setText(mResumeInfo);
                setTextDrawable(headerViewHolder.mToggleStartStopButton, mResumeDrawable);
            } else {
                headerViewHolder.mToggleStartStopButton.setText(mCancelAllInfo);
                setTextDrawable(headerViewHolder.mToggleStartStopButton, mCancelDrawable);
            }
            headerViewHolder.mSeriesSettingsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    DvrUiHelper.startSeriesSettingsActivity(getContext(),
                            header.getSeriesRecording().getId(),
                            header.getPrograms(), false, false, false, null);
                }
            });
            headerViewHolder.mToggleStartStopButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (header.getSeriesRecording().isStopped()) {
                        // Reset priority to the highest.
                        SeriesRecording seriesRecording = SeriesRecording
                                .buildFrom(header.getSeriesRecording())
                                .setPriority(TvApplication.getSingletons(getContext())
                                        .getDvrScheduleManager().suggestNewSeriesPriority())
                                .build();
                        TvApplication.getSingletons(getContext()).getDvrManager()
                                .updateSeriesRecording(seriesRecording);
                        DvrUiHelper.startSeriesSettingsActivity(getContext(),
                                header.getSeriesRecording().getId(),
                                header.getPrograms(), false, false, false, null);
                    } else {
                        DvrUiHelper.showCancelAllSeriesRecordingDialog(
                                (DvrSchedulesActivity) view.getContext(),
                                header.getSeriesRecording());
                    }
                }
            });
        }

        private void setTextDrawable(TextView textView, Drawable drawableStart) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableStart, null, null,
                    null);
        }

        /**
         * A ViewHolder for {@link SeriesRecordingHeaderRow}.
         */
        public static class SeriesHeaderRowViewHolder extends SchedulesHeaderRowViewHolder {
            private final TextView mSeriesSettingsButton;
            private final TextView mToggleStartStopButton;
            private final boolean mLtr;

            private final View mSelector;

            private View mLastFocusedView;
            public SeriesHeaderRowViewHolder(Context context, ViewGroup parent) {
                super(context, parent);
                mLtr = context.getResources().getConfiguration().getLayoutDirection()
                        == View.LAYOUT_DIRECTION_LTR;
                view.findViewById(R.id.button_container).setVisibility(View.VISIBLE);
                mSeriesSettingsButton = (TextView) view.findViewById(R.id.series_settings);
                mToggleStartStopButton =
                        (TextView) view.findViewById(R.id.series_toggle_start_stop);
                mSelector = view.findViewById(R.id.selector);
                OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean focused) {
                        view.post(new Runnable() {
                            @Override
                            public void run() {
                                updateSelector(view);
                            }
                        });
                    }
                };
                mSeriesSettingsButton.setOnFocusChangeListener(onFocusChangeListener);
                mToggleStartStopButton.setOnFocusChangeListener(onFocusChangeListener);
            }

            private void updateSelector(View focusedView) {
                int animationDuration = mSelector.getContext().getResources()
                        .getInteger(android.R.integer.config_shortAnimTime);
                DecelerateInterpolator interpolator = new DecelerateInterpolator();

                if (focusedView.hasFocus()) {
                    ViewGroup.LayoutParams lp = mSelector.getLayoutParams();
                    final int targetWidth = focusedView.getWidth();
                    float targetTranslationX;
                    if (mLtr) {
                        targetTranslationX = focusedView.getLeft() - mSelector.getLeft();
                    } else {
                        targetTranslationX = focusedView.getRight() - mSelector.getRight();
                    }

                    // if the selector is invisible, set the width and translation X directly -
                    // don't animate.
                    if (mSelector.getAlpha() == 0) {
                        mSelector.setTranslationX(targetTranslationX);
                        lp.width = targetWidth;
                        mSelector.requestLayout();
                    }

                    // animate the selector in and to the proper width and translation X.
                    final float deltaWidth = lp.width - targetWidth;
                    mSelector.animate().cancel();
                    mSelector.animate().translationX(targetTranslationX).alpha(1f)
                            .setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    // Set width to the proper width for this animation step.
                                    lp.width = targetWidth + Math.round(
                                            deltaWidth * (1f - animation.getAnimatedFraction()));
                                    mSelector.requestLayout();
                                }
                            }).setDuration(animationDuration).setInterpolator(interpolator).start();
                    mLastFocusedView = focusedView;
                } else if (mLastFocusedView == focusedView) {
                    mSelector.animate().setUpdateListener(null).cancel();
                    mSelector.animate().alpha(0f).setDuration(animationDuration)
                            .setInterpolator(interpolator).start();
                    mLastFocusedView = null;
                }
            }
        }
    }
}
