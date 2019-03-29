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

package android.content.cts;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.util.MathUtils;

import javax.annotation.Nullable;

/**
 * A stub data paging provider used for testing of paging support.
 * Ignores client supplied projections.
 */
public final class TestPagingContentProvider extends ContentProvider {

    static final String AUTHORITY = "android.content.cts.testpagingprovider";

    static final Uri PAGED_DATA_URI = Uri.parse("content://" + AUTHORITY + "/paged/");
    static final Uri UNPAGED_DATA_URI = Uri.parse("content://" + AUTHORITY + "/un-paged/");

    /** Required queryArgument specifying corpus size. */
    static final String RECORD_COUNT = "test-record-count";
    static final String COLUMN_POS = "ColumnPos";
    static final String COLUMN_A = "ColumnA";
    static final String COLUMN_B = "ColumnB";
    static final String COLUMN_C = "ColumnC";
    static final String COLUMN_D = "ColumnD";
    static final String[] PROJECTION = {
        COLUMN_POS,
        COLUMN_A,
        COLUMN_B,
        COLUMN_C,
        COLUMN_D
    };

    private static final String TAG = "TestPagingContentProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] ignored, Bundle queryArgs,
            CancellationSignal cancellationSignal) {

        queryArgs = queryArgs != null ? queryArgs : Bundle.EMPTY;

        int recordCount = queryArgs.getInt(RECORD_COUNT, Integer.MIN_VALUE);
        if (recordCount == Integer.MIN_VALUE) {
            throw new RuntimeException("Recordset size must be specified.");
        }

        if (recordCount < 0) {
            throw new RuntimeException("Recordset size must be >= 0");
        }

        Cursor cursor = null;
        if (PAGED_DATA_URI.equals(uri)) {
            cursor = buildPagedResults(queryArgs, recordCount);
        } else if (UNPAGED_DATA_URI.equals(uri)) {
            cursor = buildUnpagedResults(recordCount);
        }

        if (cursor == null) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        Log.v(TAG, "Final cursor contains " + cursor.getCount() + " rows.");
        return cursor;
    }

    private Cursor buildPagedResults(Bundle queryArgs, int recordsetSize) {

        int offset = queryArgs.getInt(ContentResolver.QUERY_ARG_OFFSET, 0);
        int limit = queryArgs.getInt(ContentResolver.QUERY_ARG_LIMIT, Integer.MIN_VALUE);

        Log.v(TAG, "Building paged results. {"
                + "recordsetSize=" + recordsetSize
                + ", offset=" + offset
                + ", limit=" + limit + "}");

        MatrixCursor c = createCursor();
        Bundle extras = c.getExtras();

        // Calculate the number of items to include in the cursor.
        int numItems = MathUtils.constrain(recordsetSize - offset, 0, limit);

        // Build the paged result set.
        for (int i = offset; i < offset + numItems; i++) {
            fillRow(c.newRow(), i);
        }

        extras.putStringArray(ContentResolver.EXTRA_HONORED_ARGS, new String[] {
            ContentResolver.QUERY_ARG_OFFSET,
            ContentResolver.QUERY_ARG_LIMIT
        });
        extras.putInt(ContentResolver.EXTRA_TOTAL_COUNT, recordsetSize);
        return c;
    }

    private Cursor buildUnpagedResults(int recordsetSize) {

        Log.v(TAG, "Building un-paged results. {" + "recordsetSize=" + recordsetSize + "}");

        MatrixCursor c = createCursor();

        // Build the unpaged result set.
        for (int i = 0; i < recordsetSize; i++) {
            fillRow(c.newRow(), i);
        }

        return c;
    }

    private MatrixCursor createCursor() {
        MatrixCursor c = new MatrixCursor(PROJECTION);
        Bundle extras = new Bundle();
        c.setExtras(extras);
        return c;
    }

    private void fillRow(RowBuilder row, int pos) {
        row.add(COLUMN_POS, pos);
        row.add(COLUMN_A, "--aaa--" + pos);
        row.add(COLUMN_B, "**bbb**" + pos);
        row.add(COLUMN_C, "^^ccc^^" + pos);
        row.add(COLUMN_D, "##ddd##" + pos);
    }

    @Override
    public Cursor query(
            Uri uri, @Nullable String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException("Call query w/ Bundle args");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
