/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream.telecom;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamProducer;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads recent calls from the call log and produces a {@link StreamCard} for each entry.
 */
public class RecentCallStreamProducer extends StreamProducer
        implements Loader.OnLoadCompleteListener<Cursor> {
    private static final String TAG = "RecentCallProducer";
    private static final long RECENT_CALL_TIME_RANGE = 6 * DateUtils.HOUR_IN_MILLIS;

    /** Number of call log items to query for */
    private static final int CALL_LOG_QUERY_LIMIT = 1;
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private CursorLoader mCursorLoader;
    private StreamCard mCurrentStreamCard;
    private long mCurrentNumber;
    private RecentCallConverter mConverter = new RecentCallConverter();

    public RecentCallStreamProducer(Context context) {
        super(context);
        mCursorLoader = createCallLogLoader();
    }

    @Override
    public void start() {
        super.start();
        if (!hasReadCallLogPermission()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Could not onStart RecentCallStreamProducer, permissions not granted");
            }
            return;
        }

        if (!mCursorLoader.isStarted()) {
            mCursorLoader.startLoading();
        }
    }

    @Override
    public void stop() {
        if (mCursorLoader.isStarted()) {
            mCursorLoader.stopLoading();
            removeCard(mCurrentStreamCard);
            mCurrentStreamCard = null;
            mCurrentNumber = 0;
        }
        super.stop();
    }

    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null || !cursor.moveToFirst()) {
            return;
        }

        int column = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        String number = cursor.getString(column);
        column = cursor.getColumnIndex(CallLog.Calls.DATE);
        long callTimeMs = cursor.getLong(column);
        // Display if we have a phone number, and the call was within 6hours.
        number = number.replaceAll("[^0-9]", "");
        long timestamp = System.currentTimeMillis();
        long digits = Long.parseLong(number);

        if (!TextUtils.isEmpty(number) &&
                (timestamp - callTimeMs) < RECENT_CALL_TIME_RANGE) {
            if (mCurrentStreamCard == null || mCurrentNumber != digits) {
                removeCard(mCurrentStreamCard);
                mCurrentStreamCard = mConverter.createStreamCard(mContext, number, timestamp);
                mCurrentNumber = digits;
                postCard(mCurrentStreamCard);
            }
        }
    }

    private boolean hasReadCallLogPermission() {
        return mContext.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Creates a CursorLoader for Call data.
     * Note: NOT to be used with LoaderManagers.
     */
    private CursorLoader createCallLogLoader() {
        // We need to check for NULL explicitly otherwise entries with where READ is NULL
        // may not match either the query or its negation.
        // We consider the calls that are not yet consumed (i.e. IS_READ = 0) as "new".
        StringBuilder where = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();

        String selection = where.length() > 0 ? where.toString() : null;
        Uri uri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY,
                        Integer.toString(CALL_LOG_QUERY_LIMIT))
                .build();
        CursorLoader loader = new CursorLoader(mContext, uri, null, selection,
                selectionArgs.toArray(EMPTY_STRING_ARRAY), CallLog.Calls.DEFAULT_SORT_ORDER);
        loader.registerListener(0, this /* OnLoadCompleteListener */);
        return loader;
    }

}
