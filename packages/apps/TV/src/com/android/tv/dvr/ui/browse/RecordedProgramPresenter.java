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
 * limitations under the License.
 */

package com.android.tv.dvr.ui.browse;

import android.content.Context;
import android.media.tv.TvInputManager;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.DvrWatchedPositionManager.WatchedPositionChangedListener;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.util.Utils;

/**
 * Presents a {@link RecordedProgram} in the {@link DvrBrowseFragment}.
 */
public class RecordedProgramPresenter extends DvrItemPresenter<RecordedProgram> {
    private final DvrWatchedPositionManager mDvrWatchedPositionManager;
    private String mTodayString;
    private String mYesterdayString;
    private final int mProgressBarColor;
    private final boolean mShowEpisodeTitle;
    private final boolean mExpandTitleWhenFocused;

    protected final class RecordedProgramViewHolder extends DvrItemViewHolder
            implements WatchedPositionChangedListener {
        private RecordedProgram mProgram;
        private boolean mShowProgress;

        public RecordedProgramViewHolder(RecordingCardView view, Integer progressColor) {
            super(view);
            if (progressColor == null) {
                mShowProgress = false;
            } else {
                mShowProgress = true;
                view.setProgressBarColor(progressColor);
            }
        }

        private void setProgressBar(long watchedPositionMs) {
            ((RecordingCardView) view).setProgressBar(
                    (watchedPositionMs == TvInputManager.TIME_SHIFT_INVALID_TIME) ? null
                            : Math.min(100, (int) (100.0f * watchedPositionMs
                                    / mProgram.getDurationMillis())));
        }

        @Override
        public void onWatchedPositionChanged(long programId, long positionMs) {
            if (programId == mProgram.getId()) {
                setProgressBar(positionMs);
            }
        }

        @Override
        protected void onBound(RecordedProgram program) {
            mProgram = program;
            if (mShowProgress) {
                mDvrWatchedPositionManager.addListener(this, program.getId());
                setProgressBar(mDvrWatchedPositionManager.getWatchedPosition(program.getId()));
            } else {
                getView().setProgressBar(null);
            }
        }

        @Override
        protected void onUnbound() {
            if (mShowProgress) {
                mDvrWatchedPositionManager.removeListener(this, mProgram.getId());
            }
            getView().reset();
        }
    }

    RecordedProgramPresenter(Context context, boolean showEpisodeTitle,
            boolean expandTitleWhenFocused) {
        super(context);
        mTodayString = mContext.getString(R.string.dvr_date_today);
        mYesterdayString = mContext.getString(R.string.dvr_date_yesterday);
        mDvrWatchedPositionManager =
                TvApplication.getSingletons(mContext).getDvrWatchedPositionManager();
        mProgressBarColor = mContext.getResources()
                .getColor(R.color.play_controls_progress_bar_watched);
        mShowEpisodeTitle = showEpisodeTitle;
        mExpandTitleWhenFocused = expandTitleWhenFocused;
    }

    public RecordedProgramPresenter(Context context) {
        this(context, false, false);
    }

    @Override
    public DvrItemViewHolder onCreateDvrItemViewHolder() {
        return new RecordedProgramViewHolder(
                new RecordingCardView(mContext, mExpandTitleWhenFocused), mProgressBarColor);
    }

    @Override
    public void onBindDvrItemViewHolder(DvrItemViewHolder baseHolder, RecordedProgram program) {
        final RecordedProgramViewHolder viewHolder = (RecordedProgramViewHolder) baseHolder;
        final RecordingCardView cardView = viewHolder.getView();
        DetailsContent details = DetailsContent.createFromRecordedProgram(mContext, program);
        cardView.setTitle(mShowEpisodeTitle ?
                program.getEpisodeDisplayTitle(mContext) : details.getTitle());
        cardView.setImageUri(details.getLogoImageUri(), details.isUsingChannelLogo());
        cardView.setContent(generateMajorContent(program), generateMinorContent(program));
        cardView.setDetailBackgroundImageUri(details.getBackgroundImageUri());
    }

    private String generateMajorContent(RecordedProgram program) {
        int dateDifference = Utils.computeDateDifference(program.getStartTimeUtcMillis(),
                System.currentTimeMillis());
        if (dateDifference == 0) {
            return mTodayString;
        } else if (dateDifference == 1) {
            return mYesterdayString;
        } else {
            return Utils.getDurationString(mContext, program.getStartTimeUtcMillis(),
                    program.getStartTimeUtcMillis(), false, true, false, 0);
        }
    }

    private String generateMinorContent(RecordedProgram program) {
        int durationMinutes = Math.max(1, Utils.getRoundOffMinsFromMs(program.getDurationMillis()));
        return mContext.getResources().getQuantityString(
                R.plurals.dvr_program_duration, durationMinutes, durationMinutes);
    }
}
