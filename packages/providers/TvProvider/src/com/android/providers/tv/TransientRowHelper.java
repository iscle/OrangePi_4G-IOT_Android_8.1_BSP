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
 * limitations under the License
 */

package com.android.providers.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.PreviewPrograms;
import android.media.tv.TvContract.WatchNextPrograms;
import android.preference.PreferenceManager;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;
import com.android.providers.tv.TvProvider.DatabaseHelper;

/**
 * Convenient class for deleting transient rows. This ensures that the clean up job is done only
 * once after boot.
 */
public class TransientRowHelper {
    private static final String PREF_KEY_LAST_DELETION_BOOT_COUNT =
            "pref_key_last_deletion_boot_count";
    private static TransientRowHelper sInstance;

    private Context mContext;
    private DatabaseHelper mDatabaseHelper;
    @VisibleForTesting
    protected boolean mTransientRowsDeleted;

    /**
     * Returns the singleton TransientRowHelper instance.
     *
     * @param context The application context.
     */
    public static TransientRowHelper getInstance(Context context) {
        synchronized (TransientRowHelper.class) {
            if (sInstance == null) {
                sInstance = new TransientRowHelper(context);
            }
        }
        return sInstance;
    }

    @VisibleForTesting
    TransientRowHelper(Context context) {
        mContext = context;
        mDatabaseHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Ensures that transient rows, inserted previously before current boot, are deleted.
     */
    public synchronized void ensureOldTransientRowsDeleted() {
        if (mTransientRowsDeleted) {
            return;
        }
        mTransientRowsDeleted = true;
        if (getLastDeletionBootCount() >= getBootCount()) {
            // This can be the second execution of TvProvider after boot since system kills
            // TvProvider in low memory conditions. If this is the case, we shouldn't delete
            // transient rows.
            return;
        }
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        // Delete all the transient programs and channels.
        db.delete(TvProvider.PREVIEW_PROGRAMS_TABLE, PreviewPrograms.COLUMN_TRANSIENT + "=1", null);
        db.delete(TvProvider.CHANNELS_TABLE, Channels.COLUMN_TRANSIENT + "=1", null);
        db.delete(TvProvider.WATCH_NEXT_PROGRAMS_TABLE, WatchNextPrograms.COLUMN_TRANSIENT + "=1",
                null);
        setLastDeletionBootCount();
    }

    @VisibleForTesting
    protected int getBootCount() {
        return Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BOOT_COUNT,
                -1);
    }

    @VisibleForTesting
    protected int getLastDeletionBootCount() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getInt(PREF_KEY_LAST_DELETION_BOOT_COUNT, -1);
    }

    @VisibleForTesting
    protected void setLastDeletionBootCount() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit();
        editor.putInt(PREF_KEY_LAST_DELETION_BOOT_COUNT, getBootCount());
        editor.apply();
    }
}
