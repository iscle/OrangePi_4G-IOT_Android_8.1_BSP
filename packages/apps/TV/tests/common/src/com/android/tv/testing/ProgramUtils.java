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

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.util.Log;

import com.android.tv.common.TvContentRatingCache;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ProgramUtils {
    private static final String TAG = "ProgramUtils";
    private static final boolean DEBUG = false;

    // Populate program data for a week.
    private static final long PROGRAM_INSERT_DURATION_MS = TimeUnit.DAYS.toMillis(7);
    private static final int MAX_DB_INSERT_COUNT_AT_ONCE = 500;

    /**
     * Populate programs by repeating given program information.
     * This method will populate programs without any gap nor overlapping
     * starting from the current time.
     */
    public static void populatePrograms(Context context, Uri channelUri, ProgramInfo program) {
        ContentValues values = new ContentValues();
        long channelId = ContentUris.parseId(channelUri);

        values.put(Programs.COLUMN_CHANNEL_ID, channelId);
        values.put(Programs.COLUMN_SHORT_DESCRIPTION, program.description);
        values.put(Programs.COLUMN_CONTENT_RATING,
                TvContentRatingCache.contentRatingsToString(program.contentRatings));

        long currentTimeMs = System.currentTimeMillis();
        long targetEndTimeMs = currentTimeMs + PROGRAM_INSERT_DURATION_MS;
        long timeMs = getLastProgramEndTimeMs(context, channelUri, currentTimeMs, targetEndTimeMs);
        if (timeMs <= 0) {
            timeMs = currentTimeMs;
        }
        int index = program.getIndex(timeMs, channelId);
        timeMs = program.getStartTimeMs(index, channelId);

        ArrayList<ContentValues> list = new ArrayList<>();
        while (timeMs < targetEndTimeMs) {
            ProgramInfo programAt = program.build(context, index++);
            values.put(Programs.COLUMN_TITLE, programAt.title);
            values.put(Programs.COLUMN_EPISODE_TITLE, programAt.episode);
            if (programAt.seasonNumber != 0) {
                values.put(Programs.COLUMN_SEASON_NUMBER, programAt.seasonNumber);
            }
            if (programAt.episodeNumber != 0) {
                values.put(Programs.COLUMN_EPISODE_NUMBER, programAt.episodeNumber);
            }
            values.put(Programs.COLUMN_POSTER_ART_URI, programAt.posterArtUri);
            values.put(Programs.COLUMN_START_TIME_UTC_MILLIS, timeMs);
            values.put(Programs.COLUMN_END_TIME_UTC_MILLIS, timeMs + programAt.durationMs);
            values.put(Programs.COLUMN_CANONICAL_GENRE, programAt.genre);
            values.put(Programs.COLUMN_POSTER_ART_URI, programAt.posterArtUri);
            list.add(new ContentValues(values));
            timeMs += programAt.durationMs;

            if (list.size() >= MAX_DB_INSERT_COUNT_AT_ONCE
                    || timeMs >= targetEndTimeMs) {
                try {
                    context.getContentResolver().bulkInsert(Programs.CONTENT_URI,
                            list.toArray(new ContentValues[list.size()]));
                } catch (SQLiteException e) {
                    Log.e(TAG, "Can't insert EPG.", e);
                    return;
                }
                if (DEBUG) Log.d(TAG, "Inserted " + list.size() + " programs for " + channelUri);
                list.clear();
            }
        }
    }

    private static long getLastProgramEndTimeMs(
            Context context, Uri channelUri, long startTimeMs, long endTimeMs) {
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri, startTimeMs, endTimeMs);
        String[] projection = {Programs.COLUMN_END_TIME_UTC_MILLIS};
        try (Cursor cursor =
                context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToLast()) {
                return cursor.getLong(0);
            }
        }
        return 0;
    }

    private ProgramUtils() {}
}
