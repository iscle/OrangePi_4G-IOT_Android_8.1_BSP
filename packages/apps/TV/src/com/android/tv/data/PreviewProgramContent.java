/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.data;

import android.content.Context;
import android.media.tv.TvContract;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import com.android.tv.TvApplication;
import com.android.tv.dvr.data.RecordedProgram;

import java.util.Objects;

/**
 * A class to store the content of preview programs.
 */
public class PreviewProgramContent {
    private final static String PARAM_INPUT = "input";

    private long mId;
    private long mPreviewChannelId;
    private int mType;
    private boolean mLive;
    private String mTitle;
    private String mDescription;
    private Uri mPosterArtUri;
    private Uri mIntentUri;
    private Uri mPreviewVideoUri;

    /**
     * Create preview program content from {@link Program}
     */
    public static PreviewProgramContent createFromProgram(Context context,
            long previewChannelId, Program program) {
        Channel channel = TvApplication.getSingletons(context).getChannelDataManager()
                .getChannel(program.getChannelId());
        if (channel == null) {
            return null;
        }
        String channelDisplayName = channel.getDisplayName();
        return new PreviewProgramContent.Builder()
                .setId(program.getId())
                .setPreviewChannelId(previewChannelId)
                .setType(TvContract.PreviewPrograms.TYPE_CHANNEL)
                .setLive(true)
                .setTitle(program.getTitle())
                .setDescription(!TextUtils.isEmpty(channelDisplayName)
                        ? channelDisplayName : channel.getDisplayNumber())
                .setPosterArtUri(Uri.parse(program.getPosterArtUri()))
                .setIntentUri(channel.getUri())
                .setPreviewVideoUri(PreviewDataManager.PreviewDataUtils.addQueryParamToUri(
                        channel.getUri(), new Pair<>(PARAM_INPUT, channel.getInputId())))
                .build();
    }

    /**
     * Create preview program content from {@link RecordedProgram}
     */
    public static PreviewProgramContent createFromRecordedProgram(
            Context context, long previewChannelId, RecordedProgram recordedProgram) {
        Channel channel = TvApplication.getSingletons(context).getChannelDataManager()
                .getChannel(recordedProgram.getChannelId());
        String channelDisplayName = null;
        if (channel != null) {
            channelDisplayName = channel.getDisplayName();
        }
        Uri recordedProgramUri = TvContract.buildRecordedProgramUri(recordedProgram.getId());
        return new PreviewProgramContent.Builder()
                .setId(recordedProgram.getId())
                .setPreviewChannelId(previewChannelId)
                .setType(TvContract.PreviewPrograms.TYPE_CLIP)
                .setTitle(recordedProgram.getTitle())
                .setDescription(channelDisplayName != null ? channelDisplayName : "")
                .setPosterArtUri(Uri.parse(recordedProgram.getPosterArtUri()))
                .setIntentUri(recordedProgramUri)
                .setPreviewVideoUri(PreviewDataManager.PreviewDataUtils.addQueryParamToUri(
                        recordedProgramUri, new Pair<>(PARAM_INPUT, recordedProgram.getInputId())))
                .build();
    }

    private PreviewProgramContent() { }

    public void copyFrom(PreviewProgramContent other) {
        if (this == other) {
            return;
        }
        mId = other.mId;
        mPreviewChannelId = other.mPreviewChannelId;
        mType = other.mType;
        mLive = other.mLive;
        mTitle = other.mTitle;
        mDescription = other.mDescription;
        mPosterArtUri = other.mPosterArtUri;
        mIntentUri = other.mIntentUri;
        mPreviewVideoUri = other.mPreviewVideoUri;
    }

    /**
     * Returns the id, which is an identification. It usually comes from the original data which
     * create the {@PreviewProgramContent}.
     */
    public long getId() {
        return mId;
    }

    /**
     * Returns the preview channel id which the preview program belongs to.
     */
    public long getPreviewChannelId() {
        return mPreviewChannelId;
    }

    /**
     * Returns the type of the preview program.
     */
    public int getType() {
        return mType;
    }

    /**
     * Returns whether the preview program is live or not.
     */
    public boolean getLive() {
        return mLive;
    }

    /**
     * Returns the title of the preview program.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Returns the description of the preview program.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Returns the poster art uri of the preview program.
     */
    public Uri getPosterArtUri() {
        return mPosterArtUri;
    }

    /**
     * Returns the intent uri of the preview program.
     */
    public Uri getIntentUri() {
        return mIntentUri;
    }

    /**
     * Returns the preview video uri of the preview program.
     */
    public Uri getPreviewVideoUri() {
        return mPreviewVideoUri;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PreviewProgramContent)) {
            return false;
        }
        PreviewProgramContent previewProgramContent = (PreviewProgramContent) other;
        return previewProgramContent.mId == mId
                && previewProgramContent.mPreviewChannelId == mPreviewChannelId
                && previewProgramContent.mType == mType
                && previewProgramContent.mLive == mLive
                && Objects.equals(previewProgramContent.mTitle, mTitle)
                && Objects.equals(previewProgramContent.mDescription, mDescription)
                && Objects.equals(previewProgramContent.mPosterArtUri, mPosterArtUri)
                && Objects.equals(previewProgramContent.mIntentUri, mIntentUri)
                && Objects.equals(previewProgramContent.mPreviewVideoUri, mPreviewVideoUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mPreviewChannelId, mType, mLive, mTitle, mDescription,
                mPosterArtUri, mIntentUri, mPreviewVideoUri);
    }

    public static final class Builder {
        private final PreviewProgramContent mPreviewProgramContent;

        public Builder() {
            mPreviewProgramContent = new PreviewProgramContent();
        }

        public Builder setId(long id) {
            mPreviewProgramContent.mId = id;
            return this;
        }

        public Builder setPreviewChannelId(long previewChannelId) {
            mPreviewProgramContent.mPreviewChannelId = previewChannelId;
            return this;
        }

        public Builder setType(int type) {
            mPreviewProgramContent.mType = type;
            return this;
        }

        public Builder setLive(boolean live) {
            mPreviewProgramContent.mLive = live;
            return this;
        }

        public Builder setTitle(String title) {
            mPreviewProgramContent.mTitle = title;
            return this;
        }

        public Builder setDescription(String description) {
            mPreviewProgramContent.mDescription = description;
            return this;
        }

        public Builder setPosterArtUri(Uri posterArtUri) {
            mPreviewProgramContent.mPosterArtUri = posterArtUri;
            return this;
        }

        public Builder setIntentUri(Uri intentUri) {
            mPreviewProgramContent.mIntentUri = intentUri;
            return this;
        }

        public Builder setPreviewVideoUri(Uri previewVideoUri) {
            mPreviewProgramContent.mPreviewVideoUri = previewVideoUri;
            return this;
        }

        public PreviewProgramContent build() {
            PreviewProgramContent previewProgramContent = new PreviewProgramContent();
            previewProgramContent.copyFrom(mPreviewProgramContent);
            return previewProgramContent;
        }
    }
}
