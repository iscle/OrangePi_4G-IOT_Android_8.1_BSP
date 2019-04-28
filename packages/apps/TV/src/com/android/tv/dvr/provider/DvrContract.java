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

package com.android.tv.dvr.provider;

import android.provider.BaseColumns;

/**
 * The contract between the DVR provider and applications. Contains definitions for the supported
 * columns. It's for the internal use in Live TV.
 */
public final class DvrContract {
    /** Column definition for Schedules table. */
    public static final class Schedules implements BaseColumns {
        /** The table name. */
        public static final String TABLE_NAME = "schedules";

        /** The recording type for program recording. */
        public static final String TYPE_PROGRAM = "TYPE_PROGRAM";

        /** The recording type for timed recording. */
        public static final String TYPE_TIMED = "TYPE_TIMED";

        /** The recording has not been started yet. */
        public static final String STATE_RECORDING_NOT_STARTED = "STATE_RECORDING_NOT_STARTED";

        /** The recording is in progress. */
        public static final String STATE_RECORDING_IN_PROGRESS = "STATE_RECORDING_IN_PROGRESS";

        /** The recording is finished. */
        public static final String STATE_RECORDING_FINISHED = "STATE_RECORDING_FINISHED";

        /** The recording failed. */
        public static final String STATE_RECORDING_FAILED = "STATE_RECORDING_FAILED";

        /** The recording finished and clipping. */
        public static final String STATE_RECORDING_CLIPPED = "STATE_RECORDING_CLIPPED";

        /** The recording marked as deleted. */
        public static final String STATE_RECORDING_DELETED = "STATE_RECORDING_DELETED";

        /** The recording marked as canceled. */
        public static final String STATE_RECORDING_CANCELED = "STATE_RECORDING_CANCELED";

        /**
         * The priority of this recording.
         *
         * <p> The lowest number is recorded first. If there is a tie in priority then the lower id
         * wins.  Defaults to {@value Long#MAX_VALUE}
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_PRIORITY = "priority";

        /**
         * The type of this recording.
         *
         * <p>This value should be one of the followings: {@link #TYPE_PROGRAM} and
         * {@link #TYPE_TIMED}.
         *
         * <p>This is a required field.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_TYPE = "type";

        /**
         * The input id of recording.
         *
         * <p>This is a required field.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_INPUT_ID = "input_id";

        /**
         * The ID of the channel for recording.
         *
         * <p>This is a required field.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_CHANNEL_ID = "channel_id";

        /**
         * The ID of the associated program for recording.
         *
         * <p>This is an optional field.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_PROGRAM_ID = "program_id";

        /**
         * The title of the associated program for recording.
         *
         * <p>This is an optional field.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_PROGRAM_TITLE = "program_title";

        /**
         * The start time of this recording, in milliseconds since the epoch.
         *
         * <p>This is a required field.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";

        /**
         * The end time of this recording, in milliseconds since the epoch.
         *
         * <p>This is a required field.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";

        /**
         * The season number of this program for episodic TV shows.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_SEASON_NUMBER = "season_number";

        /**
         * The episode number of this program for episodic TV shows.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_EPISODE_NUMBER = "episode_number";

        /**
         * The episode title of this program for episodic TV shows.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_EPISODE_TITLE = "episode_title";

        /**
         * The description of program.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_PROGRAM_DESCRIPTION = "program_description";

        /**
         * The long description of program.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_PROGRAM_LONG_DESCRIPTION = "program_long_description";

        /**
         * The poster art uri of program.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_PROGRAM_POST_ART_URI = "program_poster_art_uri";

        /**
         * The thumbnail uri of program.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_PROGRAM_THUMBNAIL_URI = "program_thumbnail_uri";

        /**
         * The state of this recording.
         *
         * <p>This value should be one of the followings: {@link #STATE_RECORDING_NOT_STARTED},
         * {@link #STATE_RECORDING_IN_PROGRESS}, {@link #STATE_RECORDING_FINISHED},
         * {@link #STATE_RECORDING_FAILED}, {@link #STATE_RECORDING_CLIPPED} and
         * {@link #STATE_RECORDING_DELETED}.
         *
         * <p>This is a required field.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_STATE = "state";

        /**
         * The ID of the parent series recording.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_SERIES_RECORDING_ID = "series_recording_id";

        private Schedules() { }
    }

    /** Column definition for Recording table. */
    public static final class SeriesRecordings implements BaseColumns {
        /** The table name. */
        public static final String TABLE_NAME = "series_recording";

        /**
         * This value is used for {@link #COLUMN_START_FROM_SEASON} and
         * {@link #COLUMN_START_FROM_EPISODE} to mean record all seasons or episodes.
         */
        public static final int THE_BEGINNING = -1;

        /**
         * The series recording option which indicates that the episodes in one channel are
         * recorded.
         */
        public static final String OPTION_CHANNEL_ONE = "OPTION_CHANNEL_ONE";

        /**
         * The series recording option which indicates that the episodes in all the channels are
         * recorded.
         */
        public static final String OPTION_CHANNEL_ALL = "OPTION_CHANNEL_ALL";

        /**
         * The state indicates that it is a normal one.
         */
        public static final String STATE_SERIES_NORMAL = "STATE_SERIES_NORMAL";

        /**
         * The state indicates that it is stopped.
         */
        public static final String STATE_SERIES_STOPPED = "STATE_SERIES_STOPPED";

        /**
         * The priority of this recording.
         *
         * <p> The lowest number is recorded first. If there is a tie in priority then the lower id
         * wins.  Defaults to {@value Long#MAX_VALUE}
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_PRIORITY = "priority";

        /**
         * The input id of recording.
         *
         * <p>This is a required field.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_INPUT_ID = "input_id";

        /**
         * The ID of the channel for recording.
         *
         * <p>This is a required field.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_CHANNEL_ID = "channel_id";

        /**
         * The  ID of the associated series to record.
         *
         * <p>The id is an opaque but stable string.
         *
         * <p>This is an optional field.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_SERIES_ID = "series_id";

        /**
         * The title of the series.
         *
         * <p>This is a required field.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_TITLE = "title";

        /**
         * The short description of the series.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_SHORT_DESCRIPTION = "short_description";

        /**
         * The long description of the series.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_LONG_DESCRIPTION = "long_description";

        /**
         * The number of the earliest season to record. The
         * value {@link #THE_BEGINNING} means record all seasons.
         *
         * <p>Default value is {@value #THE_BEGINNING} {@link #THE_BEGINNING}.
         *
         * <p>Type: INTEGER (int)
         */
        public static final String COLUMN_START_FROM_SEASON = "start_from_season";

        /**
         * The number of the earliest episode to record in {@link #COLUMN_START_FROM_SEASON}.  The
         * value {@link #THE_BEGINNING} means record all episodes.
         *
         * <p>Default value is {@value #THE_BEGINNING} {@link #THE_BEGINNING}.
         *
         * <p>Type: INTEGER (int)
         */
        public static final String COLUMN_START_FROM_EPISODE = "start_from_episode";

        /**
         * The series recording option which indicates the channels to record.
         *
         * <p>This value should be one of the followings: {@link #OPTION_CHANNEL_ONE} and
         * {@link #OPTION_CHANNEL_ALL}. The default value is OPTION_CHANNEL_ONE.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_CHANNEL_OPTION = "channel_option";

        /**
         * The comma-separated canonical genre string of this series.
         *
         * <p>Canonical genres are defined in {@link android.media.tv.TvContract.Programs.Genres}.
         * Use {@link android.media.tv.TvContract.Programs.Genres#encode} to create a text that can
         * be stored in this column. Use {@link android.media.tv.TvContract.Programs.Genres#decode}
         * to get the canonical genre strings from the text stored in the column.
         *
         * <p>Type: TEXT
         * @see android.media.tv.TvContract.Programs.Genres
         * @see android.media.tv.TvContract.Programs.Genres#encode
         * @see android.media.tv.TvContract.Programs.Genres#decode
         */
        public static final String COLUMN_CANONICAL_GENRE = "canonical_genre";

        /**
         * The URI for the poster of this TV series.
         *
         * <p>The data in the column must be a URL, or a URI in one of the following formats:
         *
         * <ul>
         * <li>content ({@link android.content.ContentResolver#SCHEME_CONTENT})</li>
         * <li>android.resource ({@link android.content.ContentResolver#SCHEME_ANDROID_RESOURCE})
         * </li>
         * <li>file ({@link android.content.ContentResolver#SCHEME_FILE})</li>
         * </ul>
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_POSTER_URI = "poster_uri";

        /**
         * The URI for the photo of this TV program.
         *
         * <p>The data in the column must be a URL, or a URI in one of the following formats:
         *
         * <ul>
         * <li>content ({@link android.content.ContentResolver#SCHEME_CONTENT})</li>
         * <li>android.resource ({@link android.content.ContentResolver#SCHEME_ANDROID_RESOURCE})
         * </li>
         * <li>file ({@link android.content.ContentResolver#SCHEME_FILE})</li>
         * </ul>
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_PHOTO_URI = "photo_uri";

        /**
         * The state of whether the series recording be canceled or not.
         *
         * <p>This value should be one of the followings: {@link #STATE_SERIES_NORMAL} and
         * {@link #STATE_SERIES_STOPPED}. The default value is STATE_SERIES_NORMAL.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_STATE = "state";

        private SeriesRecordings() { }
    }

    private DvrContract() { }
}
