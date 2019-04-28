/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.providers.tv;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvContract.WatchedPrograms;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.TimeUnit;

/**
 * A service that cleans up EPG data.
 */
public class EpgDataCleanupService extends IntentService {
    private static final boolean DEBUG = true;
    private static final String TAG = "EpgDataCleanupService";

    static final String ACTION_CLEAN_UP_EPG_DATA =
            "com.android.providers.tv.intent.CLEAN_UP_EPG_DATA";

    public EpgDataCleanupService() {
        super("EpgDataCleanupService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "Received intent: " + intent);
        }
        final String action = intent.getAction();
        if (!ACTION_CLEAN_UP_EPG_DATA.equals(action)) {
            return;
        }

        long nowMillis = System.currentTimeMillis();

        int maxProgramAgeInDays = getResources().getInteger(R.integer.max_program_age_in_days);
        if (maxProgramAgeInDays > 0) {
            clearOldPrograms(nowMillis - TimeUnit.DAYS.toMillis(maxProgramAgeInDays));
        }

        int maxWatchedProgramAgeInDays =
                getResources().getInteger(R.integer.max_watched_program_age_in_days);
        if (maxWatchedProgramAgeInDays > 0) {
            clearOldWatchHistory(nowMillis - TimeUnit.DAYS.toMillis(maxWatchedProgramAgeInDays));
        }

        int maxWatchedProgramEntryCount =
                getResources().getInteger(R.integer.max_watched_program_entry_count);
        if (maxWatchedProgramEntryCount > 0) {
            clearOverflowWatchHistory(maxWatchedProgramEntryCount);
        }
    }

    /**
     * Clear program info that ended before {@code maxEndTimeMillis}.
     */
    @VisibleForTesting
    void clearOldPrograms(long maxEndTimeMillis) {
        int deleteCount = getContentResolver().delete(
                Programs.CONTENT_URI,
                Programs.COLUMN_END_TIME_UTC_MILLIS + "<?",
                new String[] { String.valueOf(maxEndTimeMillis) });
        if (DEBUG && deleteCount > 0) {
            Log.d(TAG, "Deleted " + deleteCount + " programs"
                  + " (reason: ended before "
                  + DateUtils.getRelativeTimeSpanString(this, maxEndTimeMillis) + ")");
        }
    }

    /**
     * Clear watch history whose watch started before {@code maxStartTimeMillis}.
     * In theory, history entry for currently watching program can be deleted
     * (e.g., have been watching since before {@code maxStartTimeMillis}).
     */
    @VisibleForTesting
    void clearOldWatchHistory(long maxStartTimeMillis) {
        int deleteCount = getContentResolver().delete(
                WatchedPrograms.CONTENT_URI,
                WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS + "<?",
                new String[] { String.valueOf(maxStartTimeMillis) });
        if (DEBUG && deleteCount > 0) {
            Log.d(TAG, "Deleted " + deleteCount + " watched programs"
                  + " (reason: started before "
                  + DateUtils.getRelativeTimeSpanString(this, maxStartTimeMillis) + ")");
        }
    }

    /**
     * Clear watch history except last {@code maxEntryCount} entries.
     * "Last" here is based on watch start time, and so, in theory, history entry for program
     * that user was watching until recent reboot can be deleted earlier than other entries
     * which ended before.
     */
    @VisibleForTesting
    void clearOverflowWatchHistory(int maxEntryCount) {
        Cursor cursor = getContentResolver().query(
                WatchedPrograms.CONTENT_URI,
                new String[] { WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS }, null, null,
                WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS);
        if (cursor == null) {
            Log.e(TAG, "Failed to query watched program");
            return;
        }
        int totalCount;
        long maxStartTimeMillis;
        try {
            totalCount = cursor.getCount();
            int overflowCount = totalCount - maxEntryCount;
            if (overflowCount <= 0) {
                return;
            }
            if (!cursor.moveToPosition(overflowCount - 1)) {
                Log.e(TAG, "Failed to query watched program");
                return;
            }
            maxStartTimeMillis = cursor.getLong(0);
        } finally {
            cursor.close();
        }

        int deleteCount = getContentResolver().delete(
                WatchedPrograms.CONTENT_URI,
                WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS + "<?",
                new String[] { String.valueOf(maxStartTimeMillis + 1) });
        if (DEBUG && deleteCount > 0) {
            Log.d(TAG, "Deleted " + deleteCount + " of " + totalCount + " watched programs"
                  + " (reason: entry count > " + maxEntryCount + ")");
        }
    }
}
