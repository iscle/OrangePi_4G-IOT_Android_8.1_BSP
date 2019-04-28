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

package com.android.tv.dvr.data;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.RecordedPrograms;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.tv.common.R;
import com.android.tv.common.TvContentRatingCache;
import com.android.tv.data.BaseProgram;
import com.android.tv.data.GenreItems;
import com.android.tv.data.InternalDataUtils;
import com.android.tv.util.Utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Immutable instance of {@link android.media.tv.TvContract.RecordedPrograms}.
 */
@TargetApi(Build.VERSION_CODES.N)
public class RecordedProgram extends BaseProgram {
    public static final int ID_NOT_SET = -1;

    public final static String[] PROJECTION = {
            // These are in exactly the order listed in RecordedPrograms
            RecordedPrograms._ID,
            RecordedPrograms.COLUMN_PACKAGE_NAME,
            RecordedPrograms.COLUMN_INPUT_ID,
            RecordedPrograms.COLUMN_CHANNEL_ID,
            RecordedPrograms.COLUMN_TITLE,
            RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            RecordedPrograms.COLUMN_SEASON_TITLE,
            RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            RecordedPrograms.COLUMN_EPISODE_TITLE,
            RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS,
            RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS,
            RecordedPrograms.COLUMN_BROADCAST_GENRE,
            RecordedPrograms.COLUMN_CANONICAL_GENRE,
            RecordedPrograms.COLUMN_SHORT_DESCRIPTION,
            RecordedPrograms.COLUMN_LONG_DESCRIPTION,
            RecordedPrograms.COLUMN_VIDEO_WIDTH,
            RecordedPrograms.COLUMN_VIDEO_HEIGHT,
            RecordedPrograms.COLUMN_AUDIO_LANGUAGE,
            RecordedPrograms.COLUMN_CONTENT_RATING,
            RecordedPrograms.COLUMN_POSTER_ART_URI,
            RecordedPrograms.COLUMN_THUMBNAIL_URI,
            RecordedPrograms.COLUMN_SEARCHABLE,
            RecordedPrograms.COLUMN_RECORDING_DATA_URI,
            RecordedPrograms.COLUMN_RECORDING_DATA_BYTES,
            RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
            RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS,
            RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG1,
            RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG2,
            RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG3,
            RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG4,
            RecordedPrograms.COLUMN_VERSION_NUMBER,
            RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA,
    };

    public static RecordedProgram fromCursor(Cursor cursor) {
        int index = 0;
        Builder builder = builder()
                .setId(cursor.getLong(index++))
                .setPackageName(cursor.getString(index++))
                .setInputId(cursor.getString(index++))
                .setChannelId(cursor.getLong(index++))
                .setTitle(cursor.getString(index++))
                .setSeasonNumber(cursor.getString(index++))
                .setSeasonTitle(cursor.getString(index++))
                .setEpisodeNumber(cursor.getString(index++))
                .setEpisodeTitle(cursor.getString(index++))
                .setStartTimeUtcMillis(cursor.getLong(index++))
                .setEndTimeUtcMillis(cursor.getLong(index++))
                .setBroadcastGenres(cursor.getString(index++))
                .setCanonicalGenres(cursor.getString(index++))
                .setShortDescription(cursor.getString(index++))
                .setLongDescription(cursor.getString(index++))
                .setVideoWidth(cursor.getInt(index++))
                .setVideoHeight(cursor.getInt(index++))
                .setAudioLanguage(cursor.getString(index++))
                .setContentRatings(
                        TvContentRatingCache.getInstance().getRatings(cursor.getString(index++)))
                .setPosterArtUri(cursor.getString(index++))
                .setThumbnailUri(cursor.getString(index++))
                .setSearchable(cursor.getInt(index++) == 1)
                .setDataUri(cursor.getString(index++))
                .setDataBytes(cursor.getLong(index++))
                .setDurationMillis(cursor.getLong(index++))
                .setExpireTimeUtcMillis(cursor.getLong(index++))
                .setInternalProviderFlag1(cursor.getInt(index++))
                .setInternalProviderFlag2(cursor.getInt(index++))
                .setInternalProviderFlag3(cursor.getInt(index++))
                .setInternalProviderFlag4(cursor.getInt(index++))
                .setVersionNumber(cursor.getInt(index++));
        if (Utils.isInBundledPackageSet(builder.mPackageName)) {
            InternalDataUtils.deserializeInternalProviderData(cursor.getBlob(index), builder);
        }
        return builder.build();
    }

    public static ContentValues toValues(RecordedProgram recordedProgram) {
        ContentValues values = new ContentValues();
        if (recordedProgram.mId != ID_NOT_SET) {
            values.put(RecordedPrograms._ID, recordedProgram.mId);
        }
        values.put(RecordedPrograms.COLUMN_INPUT_ID, recordedProgram.mInputId);
        values.put(RecordedPrograms.COLUMN_CHANNEL_ID, recordedProgram.mChannelId);
        values.put(RecordedPrograms.COLUMN_TITLE, recordedProgram.mTitle);
        values.put(RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER, recordedProgram.mSeasonNumber);
        values.put(RecordedPrograms.COLUMN_SEASON_TITLE, recordedProgram.mSeasonTitle);
        values.put(RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, recordedProgram.mEpisodeNumber);
        values.put(RecordedPrograms.COLUMN_EPISODE_TITLE, recordedProgram.mTitle);
        values.put(RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS,
                recordedProgram.mStartTimeUtcMillis);
        values.put(RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, recordedProgram.mEndTimeUtcMillis);
        values.put(RecordedPrograms.COLUMN_BROADCAST_GENRE,
                safeEncode(recordedProgram.mBroadcastGenres));
        values.put(RecordedPrograms.COLUMN_CANONICAL_GENRE,
                safeEncode(recordedProgram.mCanonicalGenres));
        values.put(RecordedPrograms.COLUMN_SHORT_DESCRIPTION, recordedProgram.mShortDescription);
        values.put(RecordedPrograms.COLUMN_LONG_DESCRIPTION, recordedProgram.mLongDescription);
        if (recordedProgram.mVideoWidth == 0) {
            values.putNull(RecordedPrograms.COLUMN_VIDEO_WIDTH);
        } else {
            values.put(RecordedPrograms.COLUMN_VIDEO_WIDTH, recordedProgram.mVideoWidth);
        }
        if (recordedProgram.mVideoHeight == 0) {
            values.putNull(RecordedPrograms.COLUMN_VIDEO_HEIGHT);
        } else {
            values.put(RecordedPrograms.COLUMN_VIDEO_HEIGHT, recordedProgram.mVideoHeight);
        }
        values.put(RecordedPrograms.COLUMN_AUDIO_LANGUAGE, recordedProgram.mAudioLanguage);
        values.put(RecordedPrograms.COLUMN_CONTENT_RATING,
                TvContentRatingCache.contentRatingsToString(recordedProgram.mContentRatings));
        values.put(RecordedPrograms.COLUMN_POSTER_ART_URI, recordedProgram.mPosterArtUri);
        values.put(RecordedPrograms.COLUMN_THUMBNAIL_URI, recordedProgram.mThumbnailUri);
        values.put(RecordedPrograms.COLUMN_SEARCHABLE, recordedProgram.mSearchable ? 1 : 0);
        values.put(RecordedPrograms.COLUMN_RECORDING_DATA_URI,
                safeToString(recordedProgram.mDataUri));
        values.put(RecordedPrograms.COLUMN_RECORDING_DATA_BYTES, recordedProgram.mDataBytes);
        values.put(RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
                recordedProgram.mDurationMillis);
        values.put(RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS,
                recordedProgram.mExpireTimeUtcMillis);
        values.put(RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA,
                InternalDataUtils.serializeInternalProviderData(recordedProgram));
        values.put(RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG1,
                recordedProgram.mInternalProviderFlag1);
        values.put(RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG2,
                recordedProgram.mInternalProviderFlag2);
        values.put(RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG3,
                recordedProgram.mInternalProviderFlag3);
        values.put(RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG4,
                recordedProgram.mInternalProviderFlag4);
        values.put(RecordedPrograms.COLUMN_VERSION_NUMBER, recordedProgram.mVersionNumber);
        return values;
    }

    public static class Builder{
        private long mId = ID_NOT_SET;
        private String mPackageName;
        private String mInputId;
        private long mChannelId;
        private String mTitle;
        private String mSeriesId;
        private String mSeasonNumber;
        private String mSeasonTitle;
        private String mEpisodeNumber;
        private String mEpisodeTitle;
        private long mStartTimeUtcMillis;
        private long mEndTimeUtcMillis;
        private String[] mBroadcastGenres;
        private String[] mCanonicalGenres;
        private String mShortDescription;
        private String mLongDescription;
        private int mVideoWidth;
        private int mVideoHeight;
        private String mAudioLanguage;
        private TvContentRating[] mContentRatings;
        private String mPosterArtUri;
        private String mThumbnailUri;
        private boolean mSearchable = true;
        private Uri mDataUri;
        private long mDataBytes;
        private long mDurationMillis;
        private long mExpireTimeUtcMillis;
        private int mInternalProviderFlag1;
        private int mInternalProviderFlag2;
        private int mInternalProviderFlag3;
        private int mInternalProviderFlag4;
        private int mVersionNumber;

        public Builder setId(long id) {
            mId = id;
            return this;
        }

        public Builder setPackageName(String packageName) {
            mPackageName = packageName;
            return this;
        }

        public Builder setInputId(String inputId) {
            mInputId = inputId;
            return this;
        }

        public Builder setChannelId(long channelId) {
            mChannelId = channelId;
            return this;
        }

        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        public Builder setSeriesId(String seriesId) {
            mSeriesId = seriesId;
            return this;
        }

        public Builder setSeasonNumber(String seasonNumber) {
            mSeasonNumber = seasonNumber;
            return this;
        }

        public Builder setSeasonTitle(String seasonTitle) {
            mSeasonTitle = seasonTitle;
            return this;
        }

        public Builder setEpisodeNumber(String episodeNumber) {
            mEpisodeNumber = episodeNumber;
            return this;
        }

        public Builder setEpisodeTitle(String episodeTitle) {
            mEpisodeTitle = episodeTitle;
            return this;
        }

        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mStartTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        public Builder setEndTimeUtcMillis(long endTimeUtcMillis) {
            mEndTimeUtcMillis = endTimeUtcMillis;
            return this;
        }

        public Builder setBroadcastGenres(String broadcastGenres) {
            if (TextUtils.isEmpty(broadcastGenres)) {
                mBroadcastGenres = null;
                return this;
            }
            return setBroadcastGenres(TvContract.Programs.Genres.decode(broadcastGenres));
        }

        private Builder setBroadcastGenres(String[] broadcastGenres) {
            mBroadcastGenres = broadcastGenres;
            return this;
        }

        public Builder setCanonicalGenres(String canonicalGenres) {
            if (TextUtils.isEmpty(canonicalGenres)) {
                mCanonicalGenres = null;
                return this;
            }
            return setCanonicalGenres(TvContract.Programs.Genres.decode(canonicalGenres));
        }

        private Builder setCanonicalGenres(String[] canonicalGenres) {
            mCanonicalGenres = canonicalGenres;
            return this;
        }

        public Builder setShortDescription(String shortDescription) {
            mShortDescription = shortDescription;
            return this;
        }

        public Builder setLongDescription(String longDescription) {
            mLongDescription = longDescription;
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

        public Builder setAudioLanguage(String audioLanguage) {
            mAudioLanguage = audioLanguage;
            return this;
        }

        public Builder setContentRatings(TvContentRating[] contentRatings) {
            mContentRatings = contentRatings;
            return this;
        }

        private Uri toUri(String uriString) {
            try {
                return uriString == null ? null : Uri.parse(uriString);
            } catch (Exception e) {
                return null;
            }
        }

        public Builder setPosterArtUri(String posterArtUri) {
            mPosterArtUri = posterArtUri;
            return this;
        }

        public Builder setThumbnailUri(String thumbnailUri) {
            mThumbnailUri = thumbnailUri;
            return this;
        }

        public Builder setSearchable(boolean searchable) {
            mSearchable = searchable;
            return this;
        }

        public Builder setDataUri(String dataUri) {
            return setDataUri(toUri(dataUri));
        }

        public Builder setDataUri(Uri dataUri) {
            mDataUri = dataUri;
            return this;
        }

        public Builder setDataBytes(long dataBytes) {
            mDataBytes = dataBytes;
            return this;
        }

        public Builder setDurationMillis(long durationMillis) {
            mDurationMillis = durationMillis;
            return this;
        }

        public Builder setExpireTimeUtcMillis(long expireTimeUtcMillis) {
            mExpireTimeUtcMillis = expireTimeUtcMillis;
            return this;
        }

        public Builder setInternalProviderFlag1(int internalProviderFlag1) {
            mInternalProviderFlag1 = internalProviderFlag1;
            return this;
        }

        public Builder setInternalProviderFlag2(int internalProviderFlag2) {
            mInternalProviderFlag2 = internalProviderFlag2;
            return this;
        }

        public Builder setInternalProviderFlag3(int internalProviderFlag3) {
            mInternalProviderFlag3 = internalProviderFlag3;
            return this;
        }

        public Builder setInternalProviderFlag4(int internalProviderFlag4) {
            mInternalProviderFlag4 = internalProviderFlag4;
            return this;
        }

        public Builder setVersionNumber(int versionNumber) {
            mVersionNumber = versionNumber;
            return this;
        }

        public RecordedProgram build() {
            if (TextUtils.isEmpty(mTitle)) {
                // If title is null, series cannot be generated for this program.
                setSeriesId(null);
            } else if (TextUtils.isEmpty(mSeriesId) && !TextUtils.isEmpty(mEpisodeNumber)) {
                // If series ID is not set, generate it for the episodic program of other TV input.
                setSeriesId(BaseProgram.generateSeriesId(mPackageName, mTitle));
            }
            return new RecordedProgram(mId, mPackageName, mInputId, mChannelId, mTitle, mSeriesId,
                    mSeasonNumber, mSeasonTitle, mEpisodeNumber, mEpisodeTitle, mStartTimeUtcMillis,
                    mEndTimeUtcMillis, mBroadcastGenres, mCanonicalGenres, mShortDescription,
                    mLongDescription, mVideoWidth, mVideoHeight, mAudioLanguage, mContentRatings,
                    mPosterArtUri, mThumbnailUri, mSearchable, mDataUri, mDataBytes,
                    mDurationMillis, mExpireTimeUtcMillis, mInternalProviderFlag1,
                    mInternalProviderFlag2, mInternalProviderFlag3, mInternalProviderFlag4,
                    mVersionNumber);
        }
    }

    public static Builder builder() { return new Builder(); }

    public static Builder buildFrom(RecordedProgram orig) {
        return builder()
                .setId(orig.getId())
                .setPackageName(orig.getPackageName())
                .setInputId(orig.getInputId())
                .setChannelId(orig.getChannelId())
                .setTitle(orig.getTitle())
                .setSeriesId(orig.getSeriesId())
                .setSeasonNumber(orig.getSeasonNumber())
                .setSeasonTitle(orig.getSeasonTitle())
                .setEpisodeNumber(orig.getEpisodeNumber())
                .setEpisodeTitle(orig.getEpisodeTitle())
                .setStartTimeUtcMillis(orig.getStartTimeUtcMillis())
                .setEndTimeUtcMillis(orig.getEndTimeUtcMillis())
                .setBroadcastGenres(orig.getBroadcastGenres())
                .setCanonicalGenres(orig.getCanonicalGenres())
                .setShortDescription(orig.getDescription())
                .setLongDescription(orig.getLongDescription())
                .setVideoWidth(orig.getVideoWidth())
                .setVideoHeight(orig.getVideoHeight())
                .setAudioLanguage(orig.getAudioLanguage())
                .setContentRatings(orig.getContentRatings())
                .setPosterArtUri(orig.getPosterArtUri())
                .setThumbnailUri(orig.getThumbnailUri())
                .setSearchable(orig.isSearchable())
                .setInternalProviderFlag1(orig.getInternalProviderFlag1())
                .setInternalProviderFlag2(orig.getInternalProviderFlag2())
                .setInternalProviderFlag3(orig.getInternalProviderFlag3())
                .setInternalProviderFlag4(orig.getInternalProviderFlag4())
                .setVersionNumber(orig.getVersionNumber());
    }

    public static final Comparator<RecordedProgram> START_TIME_THEN_ID_COMPARATOR =
            new Comparator<RecordedProgram>() {
                @Override
                public int compare(RecordedProgram lhs, RecordedProgram rhs) {
                    int res =
                            Long.compare(lhs.getStartTimeUtcMillis(), rhs.getStartTimeUtcMillis());
                    if (res != 0) {
                        return res;
                    }
                    return Long.compare(lhs.mId, rhs.mId);
                }
    };

    private static final long CLIPPED_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5);

    private final long mId;
    private final String mPackageName;
    private final String mInputId;
    private final long mChannelId;
    private final String mTitle;
    private final String mSeriesId;
    private final String mSeasonNumber;
    private final String mSeasonTitle;
    private final String mEpisodeNumber;
    private final String mEpisodeTitle;
    private final long mStartTimeUtcMillis;
    private final long mEndTimeUtcMillis;
    private final String[] mBroadcastGenres;
    private final String[] mCanonicalGenres;
    private final String mShortDescription;
    private final String mLongDescription;
    private final int mVideoWidth;
    private final int mVideoHeight;
    private final String mAudioLanguage;
    private final TvContentRating[] mContentRatings;
    private final String mPosterArtUri;
    private final String mThumbnailUri;
    private final boolean mSearchable;
    private final Uri mDataUri;
    private final long mDataBytes;
    private final long mDurationMillis;
    private final long mExpireTimeUtcMillis;
    private final int mInternalProviderFlag1;
    private final int mInternalProviderFlag2;
    private final int mInternalProviderFlag3;
    private final int mInternalProviderFlag4;
    private final int mVersionNumber;

    private RecordedProgram(long id, String packageName, String inputId, long channelId,
            String title, String seriesId, String seasonNumber, String seasonTitle,
            String episodeNumber, String episodeTitle, long startTimeUtcMillis,
            long endTimeUtcMillis, String[] broadcastGenres, String[] canonicalGenres,
            String shortDescription, String longDescription, int videoWidth, int videoHeight,
            String audioLanguage, TvContentRating[] contentRatings, String posterArtUri,
            String thumbnailUri, boolean searchable, Uri dataUri, long dataBytes,
            long durationMillis, long expireTimeUtcMillis, int internalProviderFlag1,
            int internalProviderFlag2, int internalProviderFlag3, int internalProviderFlag4,
            int versionNumber) {
        mId = id;
        mPackageName = packageName;
        mInputId = inputId;
        mChannelId = channelId;
        mTitle = title;
        mSeriesId = seriesId;
        mSeasonNumber = seasonNumber;
        mSeasonTitle = seasonTitle;
        mEpisodeNumber = episodeNumber;
        mEpisodeTitle = episodeTitle;
        mStartTimeUtcMillis = startTimeUtcMillis;
        mEndTimeUtcMillis = endTimeUtcMillis;
        mBroadcastGenres = broadcastGenres;
        mCanonicalGenres = canonicalGenres;
        mShortDescription = shortDescription;
        mLongDescription = longDescription;
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;

        mAudioLanguage = audioLanguage;
        mContentRatings = contentRatings;
        mPosterArtUri = posterArtUri;
        mThumbnailUri = thumbnailUri;
        mSearchable = searchable;
        mDataUri = dataUri;
        mDataBytes = dataBytes;
        mDurationMillis = durationMillis;
        mExpireTimeUtcMillis = expireTimeUtcMillis;
        mInternalProviderFlag1 = internalProviderFlag1;
        mInternalProviderFlag2 = internalProviderFlag2;
        mInternalProviderFlag3 = internalProviderFlag3;
        mInternalProviderFlag4 = internalProviderFlag4;
        mVersionNumber = versionNumber;
    }

    public String getAudioLanguage() {
        return mAudioLanguage;
    }

    public String[] getBroadcastGenres() {
        return mBroadcastGenres;
    }

    public String[] getCanonicalGenres() {
        return mCanonicalGenres;
    }

    /**
     * Returns array of canonical genre ID's for this recorded program.
     */
    @Override
    public int[] getCanonicalGenreIds() {
        if (mCanonicalGenres == null) {
            return null;
        }
        int[] genreIds = new int[mCanonicalGenres.length];
        for (int i = 0; i < mCanonicalGenres.length; i++) {
            genreIds[i] = GenreItems.getId(mCanonicalGenres[i]);
        }
        return genreIds;
    }

    @Override
    public long getChannelId() {
        return mChannelId;
    }

    @Nullable
    @Override
    public TvContentRating[] getContentRatings() {
        return mContentRatings;
    }

    public Uri getDataUri() {
        return mDataUri;
    }

    public long getDataBytes() {
        return mDataBytes;
    }

    @Override
    public long getDurationMillis() {
        return mDurationMillis;
    }

    @Override
    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    @Override
    public String getEpisodeNumber() {
        return mEpisodeNumber;
    }

    @Override
    public String getEpisodeTitle() {
        return mEpisodeTitle;
    }

    @Nullable
    public String getEpisodeDisplayNumber(Context context) {
        if (!TextUtils.isEmpty(mEpisodeNumber)) {
            if (TextUtils.equals(mSeasonNumber, "0")) {
                // Do not show "S0: ".
                return String.format(context.getResources().getString(
                        R.string.display_episode_number_format_no_season_number), mEpisodeNumber);
            } else {
                return String.format(context.getResources().getString(
                        R.string.display_episode_number_format), mSeasonNumber, mEpisodeNumber);
            }
        }
        return null;
    }

    public long getExpireTimeUtcMillis() {
        return mExpireTimeUtcMillis;
    }

    public long getId() {
        return mId;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getInputId() {
        return mInputId;
    }

    public int getInternalProviderFlag1() {
        return mInternalProviderFlag1;
    }

    public int getInternalProviderFlag2() {
        return mInternalProviderFlag2;
    }

    public int getInternalProviderFlag3() {
        return mInternalProviderFlag3;
    }

    public int getInternalProviderFlag4() {
        return mInternalProviderFlag4;
    }

    @Override
    public String getDescription() {
        return mShortDescription;
    }

    @Override
    public String getLongDescription() {
        return mLongDescription;
    }

    @Override
    public String getPosterArtUri() {
        return mPosterArtUri;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public boolean isSearchable() {
        return mSearchable;
    }

    @Override
    public String getSeriesId() {
        return mSeriesId;
    }

    @Override
    public String getSeasonNumber() {
        return mSeasonNumber;
    }

    public String getSeasonTitle() {
        return mSeasonTitle;
    }

    @Override
    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    @Override
    public String getThumbnailUri() {
        return mThumbnailUri;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    public Uri getUri() {
        return ContentUris.withAppendedId(RecordedPrograms.CONTENT_URI, mId);
    }

    public int getVersionNumber() {
        return mVersionNumber;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    /**
     * Checks whether the recording has been clipped or not.
     */
    public boolean isClipped() {
        return mEndTimeUtcMillis - mStartTimeUtcMillis - mDurationMillis > CLIPPED_THRESHOLD_MS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordedProgram that = (RecordedProgram) o;
        return Objects.equals(mId, that.mId) &&
                Objects.equals(mChannelId, that.mChannelId) &&
                Objects.equals(mSeriesId, that.mSeriesId) &&
                Objects.equals(mSeasonNumber, that.mSeasonNumber) &&
                Objects.equals(mSeasonTitle, that.mSeasonTitle) &&
                Objects.equals(mEpisodeNumber, that.mEpisodeNumber) &&
                Objects.equals(mStartTimeUtcMillis, that.mStartTimeUtcMillis) &&
                Objects.equals(mEndTimeUtcMillis, that.mEndTimeUtcMillis) &&
                Objects.equals(mVideoWidth, that.mVideoWidth) &&
                Objects.equals(mVideoHeight, that.mVideoHeight) &&
                Objects.equals(mSearchable, that.mSearchable) &&
                Objects.equals(mDataBytes, that.mDataBytes) &&
                Objects.equals(mDurationMillis, that.mDurationMillis) &&
                Objects.equals(mExpireTimeUtcMillis, that.mExpireTimeUtcMillis) &&
                Objects.equals(mInternalProviderFlag1, that.mInternalProviderFlag1) &&
                Objects.equals(mInternalProviderFlag2, that.mInternalProviderFlag2) &&
                Objects.equals(mInternalProviderFlag3, that.mInternalProviderFlag3) &&
                Objects.equals(mInternalProviderFlag4, that.mInternalProviderFlag4) &&
                Objects.equals(mVersionNumber, that.mVersionNumber) &&
                Objects.equals(mTitle, that.mTitle) &&
                Objects.equals(mEpisodeTitle, that.mEpisodeTitle) &&
                Arrays.equals(mBroadcastGenres, that.mBroadcastGenres) &&
                Arrays.equals(mCanonicalGenres, that.mCanonicalGenres) &&
                Objects.equals(mShortDescription, that.mShortDescription) &&
                Objects.equals(mLongDescription, that.mLongDescription) &&
                Objects.equals(mAudioLanguage, that.mAudioLanguage) &&
                Arrays.equals(mContentRatings, that.mContentRatings) &&
                Objects.equals(mPosterArtUri, that.mPosterArtUri) &&
                Objects.equals(mThumbnailUri, that.mThumbnailUri);
    }

    /**
     * Hashes based on the ID.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mId);
    }

    @Override
    public String toString() {
        return "RecordedProgram"
                + "[" +  mId +
                "]{ mPackageName=" + mPackageName +
                ", mInputId='" + mInputId + '\'' +
                ", mChannelId='" + mChannelId + '\'' +
                ", mTitle='" + mTitle + '\'' +
                ", mSeriesId='" + mSeriesId + '\'' +
                ", mEpisodeNumber=" + mEpisodeNumber +
                ", mEpisodeTitle='" + mEpisodeTitle + '\'' +
                ", mStartTimeUtcMillis=" + mStartTimeUtcMillis +
                ", mEndTimeUtcMillis=" + mEndTimeUtcMillis +
                ", mBroadcastGenres=" +
                        (mBroadcastGenres != null ? Arrays.toString(mBroadcastGenres) : "null") +
                ", mCanonicalGenres=" +
                        (mCanonicalGenres != null ? Arrays.toString(mCanonicalGenres) : "null") +
                ", mShortDescription='" + mShortDescription + '\'' +
                ", mLongDescription='" + mLongDescription + '\'' +
                ", mVideoHeight=" + mVideoHeight +
                ", mVideoWidth=" + mVideoWidth +
                ", mAudioLanguage='" + mAudioLanguage + '\'' +
                ", mContentRatings='" +
                        TvContentRatingCache.contentRatingsToString(mContentRatings) + '\'' +
                ", mPosterArtUri=" + mPosterArtUri +
                ", mThumbnailUri=" + mThumbnailUri +
                ", mSearchable=" + mSearchable +
                ", mDataUri=" + mDataUri +
                ", mDataBytes=" + mDataBytes +
                ", mDurationMillis=" + mDurationMillis +
                ", mExpireTimeUtcMillis=" + mExpireTimeUtcMillis +
                ", mInternalProviderFlag1=" + mInternalProviderFlag1 +
                ", mInternalProviderFlag2=" + mInternalProviderFlag2 +
                ", mInternalProviderFlag3=" + mInternalProviderFlag3 +
                ", mInternalProviderFlag4=" + mInternalProviderFlag4 +
                ", mSeasonNumber=" + mSeasonNumber +
                ", mSeasonTitle=" + mSeasonTitle +
                ", mVersionNumber=" + mVersionNumber +
                '}';
    }

    @Nullable
    private static String safeToString(@Nullable Object o) {
        return o == null ? null : o.toString();
    }

    @Nullable
    private static String safeEncode(@Nullable String[] genres) {
        return genres == null ? null : TvContract.Programs.Genres.encode(genres);
    }

    /**
     * Returns an array containing all of the elements in the list.
     */
    public static RecordedProgram[] toArray(Collection<RecordedProgram> recordedPrograms) {
        return recordedPrograms.toArray(new RecordedProgram[recordedPrograms.size()]);
    }
}
