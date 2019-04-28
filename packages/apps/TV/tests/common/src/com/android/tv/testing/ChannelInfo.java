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

package com.android.tv.testing;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import java.util.Objects;

/**
 * Channel Information.
 */
public final class ChannelInfo {
    private static final SparseArray<String> VIDEO_HEIGHT_TO_FORMAT_MAP = new SparseArray<>();
    static {
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(480, TvContract.Channels.VIDEO_FORMAT_480P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(576, TvContract.Channels.VIDEO_FORMAT_576P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(720, TvContract.Channels.VIDEO_FORMAT_720P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(1080, TvContract.Channels.VIDEO_FORMAT_1080P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(2160, TvContract.Channels.VIDEO_FORMAT_2160P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(4320, TvContract.Channels.VIDEO_FORMAT_4320P);
    }

    public static final String[] PROJECTION = {
            TvContract.Channels.COLUMN_DISPLAY_NUMBER,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID,
    };

    public final String number;
    public final String name;
    public final String logoUrl;
    public final int originalNetworkId;
    public final int videoWidth;
    public final int videoHeight;
    public final float videoPixelAspectRatio;
    public final int audioChannel;
    public final int audioLanguageCount;
    public final boolean hasClosedCaption;
    public final ProgramInfo program;
    public final String appLinkText;
    public final int appLinkColor;
    public final String appLinkIconUri;
    public final String appLinkPosterArtUri;
    public final String appLinkIntentUri;

    /**
     * Create a channel info for TVTestInput.
     *
     * @param context a context to insert logo. It can be null if logo isn't needed.
     * @param channelNumber a channel number to be use as an identifier.
     *                      {@link #originalNetworkId} will be assigned the same value, too.
     */
    public static ChannelInfo create(@Nullable Context context, int channelNumber) {
        Builder builder = new Builder()
                .setNumber(String.valueOf(channelNumber))
                .setName("Channel " + channelNumber)
                .setOriginalNetworkId(channelNumber);
        if (context != null) {
            // tests/input/tools/get_test_logos.sh only stores 1000 logos.
            builder.setLogoUrl(getUriStringForChannelLogo(context, channelNumber));
        }
        return builder.build();
    }

    public static String getUriStringForChannelLogo(Context context, int logoIndex) {
        int index = (logoIndex % 1000) + 1;
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.getPackageName())
                .path("drawable")
                .appendPath("ch_" + index + "_logo").build().toString();
    }

    public static ChannelInfo fromCursor(Cursor c) {
        // TODO: Fill other fields.
        Builder builder = new Builder();
        int index = c.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        if (index >= 0) {
            builder.setNumber(c.getString(index));
        }
        index = c.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
        if (index >= 0) {
            builder.setName(c.getString(index));
        }
        index = c.getColumnIndex(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID);
        if (index >= 0) {
            builder.setOriginalNetworkId(c.getInt(index));
        }
        return builder.build();
    }

    private ChannelInfo(String number, String name, String logoUrl, int originalNetworkId,
            int videoWidth, int videoHeight, float videoPixelAspectRatio, int audioChannel,
            int audioLanguageCount, boolean hasClosedCaption, ProgramInfo program,
            String appLinkText, int appLinkColor, String appLinkIconUri, String appLinkPosterArtUri,
            String appLinkIntentUri) {
        this.number = number;
        this.name = name;
        this.logoUrl = logoUrl;
        this.originalNetworkId = originalNetworkId;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoPixelAspectRatio = videoPixelAspectRatio;
        this.audioChannel = audioChannel;
        this.audioLanguageCount = audioLanguageCount;
        this.hasClosedCaption = hasClosedCaption;
        this.program = program;
        this.appLinkText = appLinkText;
        this.appLinkColor = appLinkColor;
        this.appLinkIconUri = appLinkIconUri;
        this.appLinkPosterArtUri = appLinkPosterArtUri;
        this.appLinkIntentUri = appLinkIntentUri;
    }

    public String getVideoFormat() {
        return VIDEO_HEIGHT_TO_FORMAT_MAP.get(videoHeight);
    }

    @Override
    public String toString() {
        return "Channel{"
                + "number=" + number
                + ", name=" + name
                + ", logoUri=" + logoUrl
                + ", originalNetworkId=" + originalNetworkId
                + ", videoWidth=" + videoWidth
                + ", videoHeight=" + videoHeight
                + ", audioChannel=" + audioChannel
                + ", audioLanguageCount=" + audioLanguageCount
                + ", hasClosedCaption=" + hasClosedCaption
                + ", appLinkText=" + appLinkText
                + ", appLinkColor=" + appLinkColor
                + ", appLinkIconUri=" + appLinkIconUri
                + ", appLinkPosterArtUri=" + appLinkPosterArtUri
                + ", appLinkIntentUri=" + appLinkIntentUri + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChannelInfo that = (ChannelInfo) o;
        return Objects.equals(originalNetworkId, that.originalNetworkId) &&
                Objects.equals(videoWidth, that.videoWidth) &&
                Objects.equals(videoHeight, that.videoHeight) &&
                Objects.equals(audioChannel, that.audioChannel) &&
                Objects.equals(audioLanguageCount, that.audioLanguageCount) &&
                Objects.equals(hasClosedCaption, that.hasClosedCaption) &&
                Objects.equals(appLinkColor, that.appLinkColor) &&
                Objects.equals(number, that.number) &&
                Objects.equals(name, that.name) &&
                Objects.equals(logoUrl, that.logoUrl) &&
                Objects.equals(program, that.program) &&
                Objects.equals(appLinkText, that.appLinkText) &&
                Objects.equals(appLinkIconUri, that.appLinkIconUri) &&
                Objects.equals(appLinkPosterArtUri, that.appLinkPosterArtUri) &&
                Objects.equals(appLinkIntentUri, that.appLinkIntentUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, name, originalNetworkId);
    }

    /**
     * Builder class for {@code ChannelInfo}.
     */
    public static class Builder {
        private String mNumber;
        private String mName;
        private String mLogoUrl = null;
        private int mOriginalNetworkId;
        private int mVideoWidth = 1920;  // Width for HD video.
        private int mVideoHeight = 1080;  // Height for HD video.
        private float mVideoPixelAspectRatio = 1.0f; //default value
        private int mAudioChannel;
        private int mAudioLanguageCount;
        private boolean mHasClosedCaption;
        private ProgramInfo mProgram;
        private String mAppLinkText;
        private int mAppLinkColor;
        private String mAppLinkIconUri;
        private String mAppLinkPosterArtUri;
        private String mAppLinkIntentUri;

        public Builder() {
        }

        public Builder(ChannelInfo other) {
            mNumber = other.number;
            mName = other.name;
            mLogoUrl = other.name;
            mOriginalNetworkId = other.originalNetworkId;
            mVideoWidth = other.videoWidth;
            mVideoHeight = other.videoHeight;
            mVideoPixelAspectRatio = other.videoPixelAspectRatio;
            mAudioChannel = other.audioChannel;
            mAudioLanguageCount = other.audioLanguageCount;
            mHasClosedCaption = other.hasClosedCaption;
            mProgram = other.program;
        }

        public Builder setName(String name) {
            mName = name;
            return this;
        }

        public Builder setNumber(String number) {
            mNumber = number;
            return this;
        }

        public Builder setLogoUrl(String logoUrl) {
            mLogoUrl = logoUrl;
            return this;
        }

        public Builder setOriginalNetworkId(int originalNetworkId) {
            mOriginalNetworkId = originalNetworkId;
            return this;
        }

        public Builder setVideoWidth(int videoWidth) {
            mVideoWidth = videoWidth;
            return this;
        }

        public Builder setVideoHeight(int videoHeight) {
            mVideoHeight = videoHeight;
            return this;
        }

        public Builder setVideoPixelAspectRatio(float videoPixelAspectRatio) {
            mVideoPixelAspectRatio = videoPixelAspectRatio;
            return this;
        }

        public Builder setAudioChannel(int audioChannel) {
            mAudioChannel = audioChannel;
            return this;
        }

        public Builder setAudioLanguageCount(int audioLanguageCount) {
            mAudioLanguageCount = audioLanguageCount;
            return this;
        }

        public Builder setHasClosedCaption(boolean hasClosedCaption) {
            mHasClosedCaption = hasClosedCaption;
            return this;
        }

        public Builder setProgram(ProgramInfo program) {
            mProgram = program;
            return this;
        }

        public Builder setAppLinkText(String appLinkText) {
            mAppLinkText = appLinkText;
            return this;
        }

        public Builder setAppLinkColor(int appLinkColor) {
            mAppLinkColor = appLinkColor;
            return this;
        }

        public Builder setAppLinkIconUri(String appLinkIconUri) {
            mAppLinkIconUri = appLinkIconUri;
            return this;
        }

        public Builder setAppLinkPosterArtUri(String appLinkPosterArtUri) {
            mAppLinkPosterArtUri = appLinkPosterArtUri;
            return this;
        }

        public Builder setAppLinkIntentUri(String appLinkIntentUri) {
            mAppLinkIntentUri = appLinkIntentUri;
            return this;
        }

        public ChannelInfo build() {
            return new ChannelInfo(mNumber, mName, mLogoUrl, mOriginalNetworkId,
                    mVideoWidth, mVideoHeight, mVideoPixelAspectRatio, mAudioChannel,
                    mAudioLanguageCount, mHasClosedCaption, mProgram, mAppLinkText, mAppLinkColor,
                    mAppLinkIconUri, mAppLinkPosterArtUri, mAppLinkIntentUri);

        }
    }
}
