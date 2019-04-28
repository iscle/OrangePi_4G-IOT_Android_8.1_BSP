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

package com.android.tv.dvr.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import com.android.tv.data.BaseProgram;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.provider.DvrContract.SeriesRecordings;
import com.android.tv.util.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * Schedules the recording of a Series of Programs.
 *
 * <p>
 * Contains the data needed to create new ScheduleRecordings as the programs become available in
 * the EPG.
 */
public class SeriesRecording implements Parcelable {
    /**
     * Indicates that the ID is not assigned yet.
     */
    public static final long ID_NOT_SET = 0;

    /**
     * The default priority of this recording.
     */
    public static final long DEFAULT_PRIORITY = Long.MAX_VALUE >> 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {OPTION_CHANNEL_ONE, OPTION_CHANNEL_ALL})
    public @interface ChannelOption {}
    /**
     * An option which indicates that the episodes in one channel are recorded.
     */
    public static final int OPTION_CHANNEL_ONE = 0;
    /**
     * An option which indicates that the episodes in all the channels are recorded.
     */
    public static final int OPTION_CHANNEL_ALL = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {STATE_SERIES_NORMAL, STATE_SERIES_STOPPED})
    public @interface SeriesState {}

    /**
     * The state indicates that the series recording is a normal one.
     */
    public static final int STATE_SERIES_NORMAL = 0;

    /**
     * The state indicates that the series recording is stopped.
     */
    public static final int STATE_SERIES_STOPPED = 1;

    /**
     * Compare priority in descending order.
     */
    public static final Comparator<SeriesRecording> PRIORITY_COMPARATOR =
            new Comparator<SeriesRecording>() {
        @Override
        public int compare(SeriesRecording lhs, SeriesRecording rhs) {
            int value = Long.compare(rhs.mPriority, lhs.mPriority);
            if (value == 0) {
                // New recording has the higher priority.
                value = Long.compare(rhs.mId, lhs.mId);
            }
            return value;
        }
    };

    /**
     * Compare ID in ascending order.
     */
    public static final Comparator<SeriesRecording> ID_COMPARATOR =
            new Comparator<SeriesRecording>() {
                @Override
                public int compare(SeriesRecording lhs, SeriesRecording rhs) {
                    return Long.compare(lhs.mId, rhs.mId);
                }
            };

    /**
     * Creates a new Builder with the values set from the series information of {@link BaseProgram}.
     */
    public static Builder builder(String inputId, BaseProgram p) {
        return new Builder()
                .setInputId(inputId)
                .setSeriesId(p.getSeriesId())
                .setChannelId(p.getChannelId())
                .setTitle(p.getTitle())
                .setDescription(p.getDescription())
                .setLongDescription(p.getLongDescription())
                .setCanonicalGenreIds(p.getCanonicalGenreIds())
                .setPosterUri(p.getPosterArtUri())
                .setPhotoUri(p.getThumbnailUri());
    }

    /**
     * Creates a new Builder with the values set from an existing {@link SeriesRecording}.
     */
    public static Builder buildFrom(SeriesRecording r) {
        return new Builder()
                .setId(r.mId)
                .setInputId(r.getInputId())
                .setChannelId(r.getChannelId())
                .setPriority(r.getPriority())
                .setTitle(r.getTitle())
                .setDescription(r.getDescription())
                .setLongDescription(r.getLongDescription())
                .setSeriesId(r.getSeriesId())
                .setStartFromEpisode(r.getStartFromEpisode())
                .setStartFromSeason(r.getStartFromSeason())
                .setChannelOption(r.getChannelOption())
                .setCanonicalGenreIds(r.getCanonicalGenreIds())
                .setPosterUri(r.getPosterUri())
                .setPhotoUri(r.getPhotoUri())
                .setState(r.getState());
    }

    /**
     * Use this projection if you want to create {@link SeriesRecording} object using
     * {@link #fromCursor}.
     */
    public static final String[] PROJECTION = {
            // Columns must match what is read in fromCursor()
            SeriesRecordings._ID,
            SeriesRecordings.COLUMN_INPUT_ID,
            SeriesRecordings.COLUMN_CHANNEL_ID,
            SeriesRecordings.COLUMN_PRIORITY,
            SeriesRecordings.COLUMN_TITLE,
            SeriesRecordings.COLUMN_SHORT_DESCRIPTION,
            SeriesRecordings.COLUMN_LONG_DESCRIPTION,
            SeriesRecordings.COLUMN_SERIES_ID,
            SeriesRecordings.COLUMN_START_FROM_EPISODE,
            SeriesRecordings.COLUMN_START_FROM_SEASON,
            SeriesRecordings.COLUMN_CHANNEL_OPTION,
            SeriesRecordings.COLUMN_CANONICAL_GENRE,
            SeriesRecordings.COLUMN_POSTER_URI,
            SeriesRecordings.COLUMN_PHOTO_URI,
            SeriesRecordings.COLUMN_STATE
    };
    /**
     * Creates {@link SeriesRecording} object from the given {@link Cursor}.
     */
    public static SeriesRecording fromCursor(Cursor c) {
        int index = -1;
        return new Builder()
                .setId(c.getLong(++index))
                .setInputId(c.getString(++index))
                .setChannelId(c.getLong(++index))
                .setPriority(c.getLong(++index))
                .setTitle(c.getString(++index))
                .setDescription(c.getString(++index))
                .setLongDescription(c.getString(++index))
                .setSeriesId(c.getString(++index))
                .setStartFromEpisode(c.getInt(++index))
                .setStartFromSeason(c.getInt(++index))
                .setChannelOption(channelOption(c.getString(++index)))
                .setCanonicalGenreIds(c.getString(++index))
                .setPosterUri(c.getString(++index))
                .setPhotoUri(c.getString(++index))
                .setState(seriesRecordingState(c.getString(++index)))
                .build();
    }

    /**
     * Returns the ContentValues with keys as the columns specified in {@link SeriesRecordings}
     * and the values from {@code r}.
     */
    public static ContentValues toContentValues(SeriesRecording r) {
        ContentValues values = new ContentValues();
        if (r.getId() != ID_NOT_SET) {
            values.put(SeriesRecordings._ID, r.getId());
        } else {
            values.putNull(SeriesRecordings._ID);
        }
        values.put(SeriesRecordings.COLUMN_INPUT_ID, r.getInputId());
        values.put(SeriesRecordings.COLUMN_CHANNEL_ID, r.getChannelId());
        values.put(SeriesRecordings.COLUMN_PRIORITY, r.getPriority());
        values.put(SeriesRecordings.COLUMN_TITLE, r.getTitle());
        values.put(SeriesRecordings.COLUMN_SHORT_DESCRIPTION, r.getDescription());
        values.put(SeriesRecordings.COLUMN_LONG_DESCRIPTION, r.getLongDescription());
        values.put(SeriesRecordings.COLUMN_SERIES_ID, r.getSeriesId());
        values.put(SeriesRecordings.COLUMN_START_FROM_EPISODE, r.getStartFromEpisode());
        values.put(SeriesRecordings.COLUMN_START_FROM_SEASON, r.getStartFromSeason());
        values.put(SeriesRecordings.COLUMN_CHANNEL_OPTION,
                channelOption(r.getChannelOption()));
        values.put(SeriesRecordings.COLUMN_CANONICAL_GENRE,
                Utils.getCanonicalGenre(r.getCanonicalGenreIds()));
        values.put(SeriesRecordings.COLUMN_POSTER_URI, r.getPosterUri());
        values.put(SeriesRecordings.COLUMN_PHOTO_URI, r.getPhotoUri());
        values.put(SeriesRecordings.COLUMN_STATE, seriesRecordingState(r.getState()));
        return values;
    }

    private static String channelOption(@ChannelOption int option) {
        switch (option) {
            case OPTION_CHANNEL_ONE:
                return SeriesRecordings.OPTION_CHANNEL_ONE;
            case OPTION_CHANNEL_ALL:
                return SeriesRecordings.OPTION_CHANNEL_ALL;
        }
        return SeriesRecordings.OPTION_CHANNEL_ONE;
    }

    @ChannelOption private static int channelOption(String option) {
        switch (option) {
            case SeriesRecordings.OPTION_CHANNEL_ONE:
                return OPTION_CHANNEL_ONE;
            case SeriesRecordings.OPTION_CHANNEL_ALL:
                return OPTION_CHANNEL_ALL;
        }
        return OPTION_CHANNEL_ONE;
    }

    private static String seriesRecordingState(@SeriesState int state) {
        switch (state) {
            case STATE_SERIES_NORMAL:
                return SeriesRecordings.STATE_SERIES_NORMAL;
            case STATE_SERIES_STOPPED:
                return SeriesRecordings.STATE_SERIES_STOPPED;
        }
        return SeriesRecordings.STATE_SERIES_NORMAL;
    }

    @SeriesState private static int seriesRecordingState(String state) {
        switch (state) {
            case SeriesRecordings.STATE_SERIES_NORMAL:
                return STATE_SERIES_NORMAL;
            case SeriesRecordings.STATE_SERIES_STOPPED:
                return STATE_SERIES_STOPPED;
        }
        return STATE_SERIES_NORMAL;
    }

    /**
     * Builder for {@link SeriesRecording}.
     */
    public static class Builder {
        private long mId = ID_NOT_SET;
        private long mPriority = DvrScheduleManager.DEFAULT_SERIES_PRIORITY;
        private String mTitle;
        private String mDescription;
        private String mLongDescription;
        private String mInputId;
        private long mChannelId;
        private String mSeriesId;
        private int mStartFromSeason = SeriesRecordings.THE_BEGINNING;
        private int mStartFromEpisode = SeriesRecordings.THE_BEGINNING;
        private int mChannelOption = OPTION_CHANNEL_ONE;
        private int[] mCanonicalGenreIds;
        private String mPosterUri;
        private String mPhotoUri;
        private int mState = SeriesRecording.STATE_SERIES_NORMAL;

        /**
         * @see #getId()
         */
        public Builder setId(long id) {
            mId = id;
            return this;
        }

        /**
         * @see #getPriority() ()
         */
        public Builder setPriority(long priority) {
            mPriority = priority;
            return this;
        }

        /**
         * @see #getTitle()
         */
        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        /**
         * @see #getDescription()
         */
        public Builder setDescription(String description) {
            mDescription = description;
            return this;
        }

        /**
         * @see #getLongDescription()
         */
        public Builder setLongDescription(String longDescription) {
            mLongDescription = longDescription;
            return this;
        }

        /**
         * @see #getInputId()
         */
        public Builder setInputId(String inputId) {
            mInputId = inputId;
            return this;
        }

        /**
         * @see #getChannelId()
         */
        public Builder setChannelId(long channelId) {
            mChannelId = channelId;
            return this;
        }

        /**
         * @see #getSeriesId()
         */
        public Builder setSeriesId(String seriesId) {
            mSeriesId = seriesId;
            return this;
        }

        /**
         * @see #getStartFromSeason()
         */
        public Builder setStartFromSeason(int startFromSeason) {
            mStartFromSeason = startFromSeason;
            return this;
        }

        /**
         * @see #getChannelOption()
         */
        public Builder setChannelOption(@ChannelOption int option) {
            mChannelOption = option;
            return this;
        }

        /**
         * @see #getStartFromEpisode()
         */
        public Builder setStartFromEpisode(int startFromEpisode) {
            mStartFromEpisode = startFromEpisode;
            return this;
        }

        /**
         * @see #getCanonicalGenreIds()
         */
        public Builder setCanonicalGenreIds(String genres) {
            mCanonicalGenreIds = Utils.getCanonicalGenreIds(genres);
            return this;
        }

        /**
         * @see #getCanonicalGenreIds()
         */
        public Builder setCanonicalGenreIds(int[] canonicalGenreIds) {
            mCanonicalGenreIds = canonicalGenreIds;
            return this;
        }

        /**
         * @see #getPosterUri()
         */
        public Builder setPosterUri(String posterUri) {
            mPosterUri = posterUri;
            return this;
        }

        /**
         * @see #getPhotoUri()
         */
        public Builder setPhotoUri(String photoUri) {
            mPhotoUri = photoUri;
            return this;
        }

        /**
         * @see #getState()
         */
        public Builder setState(@SeriesState int state) {
            mState = state;
            return this;
        }

        /**
         * Creates a new {@link SeriesRecording}.
         */
        public SeriesRecording build() {
            return new SeriesRecording(mId, mPriority, mTitle, mDescription, mLongDescription,
                    mInputId, mChannelId, mSeriesId, mStartFromSeason, mStartFromEpisode,
                    mChannelOption, mCanonicalGenreIds, mPosterUri, mPhotoUri, mState);
        }
    }

    public static SeriesRecording fromParcel(Parcel in) {
        return new Builder()
                .setId(in.readLong())
                .setPriority(in.readLong())
                .setTitle(in.readString())
                .setDescription(in.readString())
                .setLongDescription(in.readString())
                .setInputId(in.readString())
                .setChannelId(in.readLong())
                .setSeriesId(in.readString())
                .setStartFromSeason(in.readInt())
                .setStartFromEpisode(in.readInt())
                .setChannelOption(in.readInt())
                .setCanonicalGenreIds(in.createIntArray())
                .setPosterUri(in.readString())
                .setPhotoUri(in.readString())
                .setState(in.readInt())
                .build();
    }

    public static final Parcelable.Creator<SeriesRecording> CREATOR =
            new Parcelable.Creator<SeriesRecording>() {
        @Override
        public SeriesRecording createFromParcel(Parcel in) {
          return SeriesRecording.fromParcel(in);
        }

        @Override
        public SeriesRecording[] newArray(int size) {
          return new SeriesRecording[size];
        }
    };

    private long mId;
    private final long mPriority;
    private final String mTitle;
    private final String mDescription;
    private final String mLongDescription;
    private final String mInputId;
    private final long mChannelId;
    private final String mSeriesId;
    private final int mStartFromSeason;
    private final int mStartFromEpisode;
    @ChannelOption private final int mChannelOption;
    private final int[] mCanonicalGenreIds;
    private final String mPosterUri;
    private final String mPhotoUri;
    @SeriesState private int mState;

    /**
     * The input id of this SeriesRecording.
     */
    public String getInputId() {
        return mInputId;
    }

    /**
     * The channelId to match. The channel ID might not be valid when the channel option is "ALL".
     */
    public long getChannelId() {
        return mChannelId;
    }

    /**
     * The id of this SeriesRecording.
     */
    public long getId() {
        return mId;
    }

    /**
     * Sets the ID.
     */
    public void setId(long id) {
        mId = id;
    }

    /**
     * The priority of this recording.
     *
     * <p> The highest number is recorded first. If there is a tie in mPriority then the higher mId
     * wins.
     */
    public long getPriority() {
        return mPriority;
    }

    /**
     * The series title.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * The series description.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * The long series description.
     */
    public String getLongDescription() {
        return mLongDescription;
    }

    /**
     * SeriesId when not null is used to match programs instead of using title and channelId.
     *
     * <p>SeriesId is an opaque but stable string.
     */
    public String getSeriesId() {
        return mSeriesId;
    }

    /**
     * If not == {@link SeriesRecordings#THE_BEGINNING} and seasonNumber == startFromSeason then
     * only record episodes with a episodeNumber >= this
     */
    public int getStartFromEpisode() {
        return mStartFromEpisode;
    }

    /**
     * If not == {@link SeriesRecordings#THE_BEGINNING} then only record episodes with a
     * seasonNumber >= this
     */
    public int getStartFromSeason() {
        return mStartFromSeason;
    }

    /**
     * Returns the channel recording option.
     */
    @ChannelOption public int getChannelOption() {
        return mChannelOption;
    }

    /**
     * Returns the canonical genre ID's.
     */
    public int[] getCanonicalGenreIds() {
        return mCanonicalGenreIds;
    }

    /**
     * Returns the poster URI.
     */
    public String getPosterUri() {
        return mPosterUri;
    }

    /**
     * Returns the photo URI.
     */
    public String getPhotoUri() {
        return mPhotoUri;
    }

    /**
     * Returns the state of series recording.
     */
    @SeriesState public int getState() {
        return mState;
    }

    /**
     * Checks whether the series recording is stopped or not.
     */
    public boolean isStopped() {
        return mState == STATE_SERIES_STOPPED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeriesRecording)) return false;
        SeriesRecording that = (SeriesRecording) o;
        return mPriority == that.mPriority
                && mChannelId == that.mChannelId
                && mStartFromSeason == that.mStartFromSeason
                && mStartFromEpisode == that.mStartFromEpisode
                && Objects.equals(mId, that.mId)
                && Objects.equals(mTitle, that.mTitle)
                && Objects.equals(mDescription, that.mDescription)
                && Objects.equals(mLongDescription, that.mLongDescription)
                && Objects.equals(mSeriesId, that.mSeriesId)
                && mChannelOption == that.mChannelOption
                && Arrays.equals(mCanonicalGenreIds, that.mCanonicalGenreIds)
                && Objects.equals(mPosterUri, that.mPosterUri)
                && Objects.equals(mPhotoUri, that.mPhotoUri)
                && mState == that.mState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPriority, mChannelId, mStartFromSeason, mStartFromEpisode, mId,
                mTitle, mDescription, mLongDescription, mSeriesId, mChannelOption,
                mCanonicalGenreIds, mPosterUri, mPhotoUri, mState);
    }

    @Override
    public String toString() {
        return "SeriesRecording{" +
                "inputId=" + mInputId +
                ", channelId=" + mChannelId +
                ", id='" + mId + '\'' +
                ", priority=" + mPriority +
                ", title='" + mTitle + '\'' +
                ", description='" + mDescription + '\'' +
                ", longDescription='" + mLongDescription + '\'' +
                ", startFromSeason=" + mStartFromSeason +
                ", startFromEpisode=" + mStartFromEpisode +
                ", channelOption=" + mChannelOption +
                ", canonicalGenreIds=" + Arrays.toString(mCanonicalGenreIds) +
                ", posterUri=" + mPosterUri +
                ", photoUri=" + mPhotoUri +
                ", state=" + mState +
                '}';
    }

    private SeriesRecording(long id, long priority, String title, String description,
            String longDescription, String inputId, long channelId, String seriesId,
            int startFromSeason, int startFromEpisode, int channelOption, int[] canonicalGenreIds,
            String posterUri, String photoUri, int state) {
        this.mId = id;
        this.mPriority = priority;
        this.mTitle = title;
        this.mDescription = description;
        this.mLongDescription = longDescription;
        this.mInputId = inputId;
        this.mChannelId = channelId;
        this.mSeriesId = seriesId;
        this.mStartFromSeason = startFromSeason;
        this.mStartFromEpisode = startFromEpisode;
        this.mChannelOption = channelOption;
        this.mCanonicalGenreIds = canonicalGenreIds;
        this.mPosterUri = posterUri;
        this.mPhotoUri = photoUri;
        this.mState = state;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int paramInt) {
        out.writeLong(mId);
        out.writeLong(mPriority);
        out.writeString(mTitle);
        out.writeString(mDescription);
        out.writeString(mLongDescription);
        out.writeString(mInputId);
        out.writeLong(mChannelId);
        out.writeString(mSeriesId);
        out.writeInt(mStartFromSeason);
        out.writeInt(mStartFromEpisode);
        out.writeInt(mChannelOption);
        out.writeIntArray(mCanonicalGenreIds);
        out.writeString(mPosterUri);
        out.writeString(mPhotoUri);
        out.writeInt(mState);
    }

    /**
     * Returns an array containing all of the elements in the list.
     */
    public static SeriesRecording[] toArray(Collection<SeriesRecording> series) {
        return series.toArray(new SeriesRecording[series.size()]);
    }

    /**
     * Returns {@code true} if the {@code program} is part of the series and meets the season and
     * episode constraints.
     */
    public boolean matchProgram(Program program) {
        return matchProgram(program, mChannelOption);
    }

    /**
     * Returns {@code true} if the {@code program} is part of the series and meets the season and
     * episode constraints. It checks the channel option only if {@code checkChannelOption} is
     * {@code true}.
     */
    public boolean matchProgram(Program program, @ChannelOption int channelOption) {
        String seriesId = program.getSeriesId();
        long channelId = program.getChannelId();
        String seasonNumber = program.getSeasonNumber();
        String episodeNumber = program.getEpisodeNumber();
        if (!mSeriesId.equals(seriesId) || (channelOption == SeriesRecording.OPTION_CHANNEL_ONE
                && mChannelId != channelId)) {
            return false;
        }
        // Season number and episode number matches if
        // start_season_number < program_season_number
        // || (start_season_number == program_season_number
        // && start_episode_number <= program_episode_number).
        if (mStartFromSeason == SeriesRecordings.THE_BEGINNING
                || TextUtils.isEmpty(seasonNumber)) {
            return true;
        } else {
            int intSeasonNumber;
            try {
                intSeasonNumber = Integer.valueOf(seasonNumber);
            } catch (NumberFormatException e) {
                return true;
            }
            if (intSeasonNumber > mStartFromSeason) {
                return true;
            } else if (intSeasonNumber < mStartFromSeason) {
                return false;
            }
        }
        if (mStartFromEpisode == SeriesRecordings.THE_BEGINNING
                || TextUtils.isEmpty(episodeNumber)) {
            return true;
        } else {
            int intEpisodeNumber;
            try {
                intEpisodeNumber = Integer.valueOf(episodeNumber);
            } catch (NumberFormatException e) {
                return true;
            }
            return intEpisodeNumber >= mStartFromEpisode;
        }
    }
}
