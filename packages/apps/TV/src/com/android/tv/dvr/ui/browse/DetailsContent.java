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

package com.android.tv.dvr.ui.browse;

import android.content.Context;
import android.media.tv.TvContract;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.ui.DvrUiHelper;

/**
 * A class for details content.
 */
class DetailsContent {
    /** Constant for invalid time. */
    public static final long INVALID_TIME = -1;

    private CharSequence mTitle;
    private long mStartTimeUtcMillis;
    private long mEndTimeUtcMillis;
    private String mDescription;
    private String mLogoImageUri;
    private String mBackgroundImageUri;
    private boolean mUsingChannelLogo;

    static DetailsContent createFromRecordedProgram(Context context,
            RecordedProgram recordedProgram) {
        return new DetailsContent.Builder()
                .setChannelId(recordedProgram.getChannelId())
                .setProgramTitle(recordedProgram.getTitle())
                .setSeasonNumber(recordedProgram.getSeasonNumber())
                .setEpisodeNumber(recordedProgram.getEpisodeNumber())
                .setStartTimeUtcMillis(recordedProgram.getStartTimeUtcMillis())
                .setEndTimeUtcMillis(recordedProgram.getEndTimeUtcMillis())
                .setDescription(TextUtils.isEmpty(recordedProgram.getLongDescription())
                        ? recordedProgram.getDescription() : recordedProgram.getLongDescription())
                .setPosterArtUri(recordedProgram.getPosterArtUri())
                .setThumbnailUri(recordedProgram.getThumbnailUri())
                .build(context);
    }

    static DetailsContent createFromSeriesRecording(Context context,
            SeriesRecording seriesRecording) {
        return new DetailsContent.Builder()
                .setChannelId(seriesRecording.getChannelId())
                .setTitle(seriesRecording.getTitle())
                .setDescription(TextUtils.isEmpty(seriesRecording.getLongDescription())
                        ? seriesRecording.getDescription() : seriesRecording.getLongDescription())
                .setPosterArtUri(seriesRecording.getPosterUri())
                .setThumbnailUri(seriesRecording.getPhotoUri())
                .build(context);
    }

    static DetailsContent createFromScheduledRecording(Context context,
            ScheduledRecording scheduledRecording) {
        Channel channel = TvApplication.getSingletons(context).getChannelDataManager()
                .getChannel(scheduledRecording.getChannelId());
        String description = !TextUtils.isEmpty(scheduledRecording.getProgramDescription()) ?
                scheduledRecording.getProgramDescription()
                : scheduledRecording.getProgramLongDescription();
        if (TextUtils.isEmpty(description)) {
            description = channel != null ? channel.getDescription() : null;
        }
        return new DetailsContent.Builder()
                .setChannelId(scheduledRecording.getChannelId())
                .setProgramTitle(scheduledRecording.getProgramTitle())
                .setSeasonNumber(scheduledRecording.getSeasonNumber())
                .setEpisodeNumber(scheduledRecording.getEpisodeNumber())
                .setStartTimeUtcMillis(scheduledRecording.getStartTimeMs())
                .setEndTimeUtcMillis(scheduledRecording.getEndTimeMs())
                .setDescription(description)
                .setPosterArtUri(scheduledRecording.getProgramPosterArtUri())
                .setThumbnailUri(scheduledRecording.getProgramThumbnailUri())
                .build(context);
    }

    private DetailsContent() { }

    /**
     * Returns title.
     */
    public CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Returns start time.
     */
    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    /**
     * Returns end time.
     */
    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    /**
     * Returns description.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Returns Logo image URI as a String.
     */
    public String getLogoImageUri() {
        return mLogoImageUri;
    }

    /**
     * Returns background image URI as a String.
     */
    public String getBackgroundImageUri() {
        return mBackgroundImageUri;
    }

    /**
     * Returns if image URIs are from its channels' logo.
     */
    public boolean isUsingChannelLogo() {
        return mUsingChannelLogo;
    }

    /**
     * Copies other details content.
     */
    public void copyFrom(DetailsContent other) {
        if (this == other) {
            return;
        }
        mTitle = other.mTitle;
        mStartTimeUtcMillis = other.mStartTimeUtcMillis;
        mEndTimeUtcMillis = other.mEndTimeUtcMillis;
        mDescription = other.mDescription;
        mLogoImageUri = other.mLogoImageUri;
        mBackgroundImageUri = other.mBackgroundImageUri;
        mUsingChannelLogo = other.mUsingChannelLogo;
    }

    /**
     * A class for building details content.
     */
    public static final class Builder {
        private final DetailsContent mDetailsContent;

        private long mChannelId;
        private String mProgramTitle;
        private String mSeasonNumber;
        private String mEpisodeNumber;
        private String mPosterArtUri;
        private String mThumbnailUri;

        public Builder() {
            mDetailsContent = new DetailsContent();
            mDetailsContent.mStartTimeUtcMillis = INVALID_TIME;
            mDetailsContent.mEndTimeUtcMillis = INVALID_TIME;
        }

        /**
         * Sets title.
         */
        public Builder setTitle(CharSequence title) {
            mDetailsContent.mTitle = title;
            return this;
        }

        /**
         * Sets start time.
         */
        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mDetailsContent.mStartTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        /**
         * Sets end time.
         */
        public Builder setEndTimeUtcMillis(long endTimeUtcMillis) {
            mDetailsContent.mEndTimeUtcMillis = endTimeUtcMillis;
            return this;
        }

        /**
         * Sets description.
         */
        public Builder setDescription(String description) {
            mDetailsContent.mDescription = description;
            return this;
        }

        /**
         * Sets logo image URI as a String.
         */
        public Builder setLogoImageUri(String logoImageUri) {
            mDetailsContent.mLogoImageUri = logoImageUri;
            return this;
        }

        /**
         * Sets background image URI as a String.
         */
        public Builder setBackgroundImageUri(String backgroundImageUri) {
            mDetailsContent.mBackgroundImageUri = backgroundImageUri;
            return this;
        }

        private Builder setProgramTitle(String programTitle) {
            mProgramTitle = programTitle;
            return this;
        }

        private Builder setSeasonNumber(String seasonNumber) {
            mSeasonNumber = seasonNumber;
            return this;
        }

        private Builder setEpisodeNumber(String episodeNumber) {
            mEpisodeNumber = episodeNumber;
            return this;
        }

        private Builder setChannelId(long channelId) {
            mChannelId = channelId;
            return this;
        }

        private Builder setPosterArtUri(String posterArtUri) {
            mPosterArtUri = posterArtUri;
            return this;
        }

        private Builder setThumbnailUri(String thumbnailUri) {
            mThumbnailUri = thumbnailUri;
            return this;
        }

        private void createStyledTitle(Context context, Channel channel) {
            CharSequence title = DvrUiHelper.getStyledTitleWithEpisodeNumber(context,
                    mProgramTitle, mSeasonNumber, mEpisodeNumber,
                    R.style.text_appearance_card_view_episode_number);
            if (TextUtils.isEmpty(title)) {
                mDetailsContent.mTitle = channel != null ? channel.getDisplayName()
                        : context.getResources().getString(R.string.no_program_information);
            } else {
                mDetailsContent.mTitle = title;
            }
        }

        private void createImageUris(@Nullable Channel channel) {
            mDetailsContent.mLogoImageUri = null;
            mDetailsContent.mBackgroundImageUri = null;
            mDetailsContent.mUsingChannelLogo = false;
            if (!TextUtils.isEmpty(mPosterArtUri) && !TextUtils.isEmpty(mThumbnailUri)) {
                mDetailsContent.mLogoImageUri = mPosterArtUri;
                mDetailsContent.mBackgroundImageUri = mThumbnailUri;
            } else if (!TextUtils.isEmpty(mPosterArtUri)) {
                // thumbnailUri is empty
                mDetailsContent.mLogoImageUri = mPosterArtUri;
                mDetailsContent.mBackgroundImageUri = mPosterArtUri;
            } else if (!TextUtils.isEmpty(mThumbnailUri)) {
                // posterArtUri is empty
                mDetailsContent.mLogoImageUri = mThumbnailUri;
                mDetailsContent.mBackgroundImageUri = mThumbnailUri;
            }
            if (TextUtils.isEmpty(mDetailsContent.mLogoImageUri) && channel != null) {
                String channelLogoUri = TvContract.buildChannelLogoUri(channel.getId())
                        .toString();
                mDetailsContent.mLogoImageUri = channelLogoUri;
                mDetailsContent.mBackgroundImageUri = channelLogoUri;
                mDetailsContent.mUsingChannelLogo = true;
            }
        }

        /**
         * Builds details content.
         */
        public DetailsContent build(Context context) {
            Channel channel = TvApplication.getSingletons(context).getChannelDataManager()
                    .getChannel(mChannelId);
            if (mDetailsContent.mTitle == null) {
                createStyledTitle(context, channel);
            }
            if (mDetailsContent.mBackgroundImageUri == null
                    && mDetailsContent.mLogoImageUri == null) {
                createImageUris(channel);
            }
            DetailsContent detailsContent = new DetailsContent();
            detailsContent.copyFrom(mDetailsContent);
            return detailsContent;
        }
    }
}