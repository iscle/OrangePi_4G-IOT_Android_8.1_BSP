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

package com.android.tv.data.epg;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.data.Program;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The helper class for {@link com.android.tv.data.epg.EpgFetcher} */
class EpgFetchHelper {
    private static final String TAG = "EpgFetchHelper";
    private static final boolean DEBUG = false;

    private static final long PROGRAM_QUERY_DURATION_MS = TimeUnit.DAYS.toMillis(30);
    private static final int BATCH_OPERATION_COUNT = 100;

    // Value: Long
    private static final String KEY_LAST_UPDATED_EPG_TIMESTAMP =
            "com.android.tv.data.epg.EpgFetcher.LastUpdatedEpgTimestamp";
    // Value: String
    private static final String KEY_LAST_LINEUP_ID =
            "com.android.tv.data.epg.EpgFetcher.LastLineupId";

    private static long sLastEpgUpdatedTimestamp = -1;
    private static String sLastLineupId;

    private EpgFetchHelper() { }

    /**
     * Updates newly fetched EPG data for the given channel to local providers. The method will
     * compare the broadcasting time and try to match each newly fetched program with old programs
     * of that channel in the database one by one. It will update the matched old program, or insert
     * the new program if there is no matching program can be found in the database and at the same
     * time remove those old programs which conflicts with the inserted one.

     * @param channelId the target channel ID.
     * @param fetchedPrograms the newly fetched program data.
     * @return {@code true} if new program data are successfully updated. Otherwise {@code false}.
     */
    static boolean updateEpgData(Context context, long channelId, List<Program> fetchedPrograms) {
        final int fetchedProgramsCount = fetchedPrograms.size();
        if (fetchedProgramsCount == 0) {
            return false;
        }
        boolean updated = false;
        long startTimeMs = System.currentTimeMillis();
        long endTimeMs = startTimeMs + PROGRAM_QUERY_DURATION_MS;
        List<Program> oldPrograms = queryPrograms(context, channelId, startTimeMs, endTimeMs);
        int oldProgramsIndex = 0;
        int newProgramsIndex = 0;

        // Compare the new programs with old programs one by one and update/delete the old one
        // or insert new program if there is no matching program in the database.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        while (newProgramsIndex < fetchedProgramsCount) {
            Program oldProgram = oldProgramsIndex < oldPrograms.size()
                    ? oldPrograms.get(oldProgramsIndex) : null;
            Program newProgram = fetchedPrograms.get(newProgramsIndex);
            boolean addNewProgram = false;
            if (oldProgram != null) {
                if (oldProgram.equals(newProgram)) {
                    // Exact match. No need to update. Move on to the next programs.
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (hasSameTitleAndOverlap(oldProgram, newProgram)) {
                    // Partial match. Update the old program with the new one.
                    // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                    // could be application specific settings which belong to the old program.
                    ops.add(ContentProviderOperation.newUpdate(
                            TvContract.buildProgramUri(oldProgram.getId()))
                            .withValues(Program.toContentValues(newProgram))
                            .build());
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (oldProgram.getEndTimeUtcMillis() < newProgram.getEndTimeUtcMillis()) {
                    // No match. Remove the old program first to see if the next program in
                    // {@code oldPrograms} partially matches the new program.
                    ops.add(ContentProviderOperation.newDelete(
                            TvContract.buildProgramUri(oldProgram.getId()))
                            .build());
                    oldProgramsIndex++;
                } else {
                    // No match. The new program does not match any of the old programs. Insert
                    // it as a new program.
                    addNewProgram = true;
                    newProgramsIndex++;
                }
            } else {
                // No old programs. Just insert new programs.
                addNewProgram = true;
                newProgramsIndex++;
            }
            if (addNewProgram) {
                ops.add(ContentProviderOperation
                        .newInsert(Programs.CONTENT_URI)
                        .withValues(Program.toContentValues(newProgram))
                        .build());
            }
            // Throttle the batch operation not to cause TransactionTooLargeException.
            if (ops.size() > BATCH_OPERATION_COUNT || newProgramsIndex >= fetchedProgramsCount) {
                try {
                    if (DEBUG) {
                        int size = ops.size();
                        Log.d(TAG, "Running " + size + " operations for channel " + channelId);
                        for (int i = 0; i < size; ++i) {
                            Log.d(TAG, "Operation(" + i + "): " + ops.get(i));
                        }
                    }
                    context.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                    updated = true;
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "Failed to insert programs.", e);
                    return updated;
                }
                ops.clear();
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Updated " + fetchedProgramsCount + " programs for channel " + channelId);
        }
        return updated;
    }

    private static List<Program> queryPrograms(Context context, long channelId,
            long startTimeMs, long endTimeMs) {
        try (Cursor c = context.getContentResolver().query(
                TvContract.buildProgramsUriForChannel(channelId, startTimeMs, endTimeMs),
                Program.PROJECTION, null, null, Programs.COLUMN_START_TIME_UTC_MILLIS)) {
            if (c == null) {
                return Collections.emptyList();
            }
            ArrayList<Program> programs = new ArrayList<>();
            while (c.moveToNext()) {
                programs.add(Program.fromCursor(c));
            }
            return programs;
        }
    }

    /**
     * Returns {@code true} if the {@code oldProgram} needs to be updated with the
     * {@code newProgram}.
     */
    private static boolean hasSameTitleAndOverlap(Program oldProgram, Program newProgram) {
        // NOTE: Here, we update the old program if it has the same title and overlaps with the
        // new program. The test logic is just an example and you can modify this. E.g. check
        // whether the both programs have the same program ID if your EPG supports any ID for
        // the programs.
        return TextUtils.equals(oldProgram.getTitle(), newProgram.getTitle())
                && oldProgram.getStartTimeUtcMillis() <= newProgram.getEndTimeUtcMillis()
                && newProgram.getStartTimeUtcMillis() <= oldProgram.getEndTimeUtcMillis();
    }

    /**
     * Sets the last known lineup ID into shared preferences for future usage. If channels are not
     * re-scanned, EPG fetcher can directly use this value instead of checking the correct lineup ID
     * every time when it needs to fetch EPG data.
     */
    @WorkerThread
    synchronized static void setLastLineupId(Context context, String lineupId) {
        if (DEBUG) {
            if (lineupId == null) {
                Log.d(TAG, "Clear stored lineup id: " + sLastLineupId);
            }
        }
        sLastLineupId = lineupId;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(KEY_LAST_LINEUP_ID, lineupId).apply();
    }

    /**
     * Gets the last known lineup ID from shared preferences.
     */
    synchronized static String getLastLineupId(Context context) {
        if (sLastLineupId == null) {
            sLastLineupId = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(KEY_LAST_LINEUP_ID, null);
        }
        if (DEBUG) Log.d(TAG, "Last lineup is " + sLastLineupId);
        return sLastLineupId;
    }

    /**
     * Sets the last updated timestamp of EPG data into shared preferences. If the EPG data is not
     * out-dated, it's not necessary for EPG fetcher to fetch EPG again.
     */
    @WorkerThread
    synchronized static void setLastEpgUpdatedTimestamp(Context context, long timestamp) {
        sLastEpgUpdatedTimestamp = timestamp;
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(
                KEY_LAST_UPDATED_EPG_TIMESTAMP, timestamp).apply();
    }

    /**
     * Gets the last updated timestamp of EPG data.
     */
    synchronized static long getLastEpgUpdatedTimestamp(Context context) {
        if (sLastEpgUpdatedTimestamp < 0) {
            sLastEpgUpdatedTimestamp = PreferenceManager.getDefaultSharedPreferences(context)
                    .getLong(KEY_LAST_UPDATED_EPG_TIMESTAMP, 0);
        }
        return sLastEpgUpdatedTimestamp;
    }
}