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
import android.os.Handler;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.util.Utils;

import java.util.concurrent.TimeUnit;

/**
 * Presents a {@link ScheduledRecording} in the {@link DvrBrowseFragment}.
 */
class ScheduledRecordingPresenter extends DvrItemPresenter<ScheduledRecording> {
    private static final long PROGRESS_UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5);

    private final DvrManager mDvrManager;
    private final int mProgressBarColor;

    private final class ScheduledRecordingViewHolder extends DvrItemViewHolder {
        private final Handler mHandler = new Handler();
        private ScheduledRecording mScheduledRecording;
        private final Runnable mProgressBarUpdater = new Runnable() {
            @Override
            public void run() {
                updateProgressBar();
                mHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
            }
        };

        ScheduledRecordingViewHolder(RecordingCardView view, int progressBarColor) {
            super(view);
            view.setProgressBarColor(progressBarColor);
        }

        @Override
        protected void onBound(ScheduledRecording recording) {
            mScheduledRecording = recording;
            updateProgressBar();
            startUpdateProgressBar();
        }

        @Override
        protected void onUnbound() {
            stopUpdateProgressBar();
            mScheduledRecording = null;
            getView().reset();
        }

        private void updateProgressBar() {
            if (mScheduledRecording == null) {
                return;
            }
            int recordingState = mScheduledRecording.getState();
            RecordingCardView cardView = (RecordingCardView) view;
            if (recordingState == ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                cardView.setProgressBar(Math.max(0, Math.min((int) (100 *
                        (System.currentTimeMillis() - mScheduledRecording.getStartTimeMs())
                        / mScheduledRecording.getDuration()), 100)));
            } else if (recordingState == ScheduledRecording.STATE_RECORDING_FINISHED) {
                cardView.setProgressBar(100);
            } else {
                // Hides progress bar.
                cardView.setProgressBar(null);
            }
        }

        private void startUpdateProgressBar() {
            mHandler.post(mProgressBarUpdater);
        }

        private void stopUpdateProgressBar() {
            mHandler.removeCallbacks(mProgressBarUpdater);
        }
    }

    public ScheduledRecordingPresenter(Context context) {
        super(context);
        mDvrManager = TvApplication.getSingletons(mContext).getDvrManager();
        mProgressBarColor = mContext.getResources()
                .getColor(R.color.play_controls_recording_icon_color_on_focus);
    }

    @Override
    public DvrItemViewHolder onCreateDvrItemViewHolder() {
        return new ScheduledRecordingViewHolder(new RecordingCardView(mContext), mProgressBarColor);
    }

    @Override
    public void onBindDvrItemViewHolder(DvrItemViewHolder baseHolder,
            ScheduledRecording recording) {
        final ScheduledRecordingViewHolder viewHolder = (ScheduledRecordingViewHolder) baseHolder;
        final RecordingCardView cardView = viewHolder.getView();
        DetailsContent details = DetailsContent.createFromScheduledRecording(mContext, recording);
        cardView.setTitle(details.getTitle());
        cardView.setImageUri(details.getLogoImageUri(), details.isUsingChannelLogo());
        cardView.setAffiliatedIcon(mDvrManager.isConflicting(recording) ?
                R.drawable.ic_warning_white_32dp : 0);
        cardView.setContent(generateMajorContent(recording), null);
        cardView.setDetailBackgroundImageUri(details.getBackgroundImageUri());
    }

    private String generateMajorContent(ScheduledRecording recording) {
        int dateDifference = Utils.computeDateDifference(System.currentTimeMillis(),
                recording.getStartTimeMs());
        if (dateDifference <= 0) {
            return mContext.getString(R.string.dvr_date_today_time,
                    Utils.getDurationString(mContext, recording.getStartTimeMs(),
                            recording.getEndTimeMs(), false, false, true, 0));
        } else if (dateDifference == 1) {
            return mContext.getString(R.string.dvr_date_tomorrow_time,
                    Utils.getDurationString(mContext, recording.getStartTimeMs(),
                            recording.getEndTimeMs(), false, false, true, 0));
        } else {
            return Utils.getDurationString(mContext, recording.getStartTimeMs(),
                    recording.getStartTimeMs(), false, true, false, 0);
        }
    }
}