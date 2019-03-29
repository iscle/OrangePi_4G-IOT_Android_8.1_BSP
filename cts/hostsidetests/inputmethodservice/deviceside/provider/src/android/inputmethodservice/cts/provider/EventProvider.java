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

package android.inputmethodservice.cts.provider;

import static android.inputmethodservice.cts.common.EventProviderConstants.AUTHORITY;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.inputmethodservice.cts.common.EventProviderConstants.EventTableConstants;
import android.inputmethodservice.cts.db.Database;
import android.inputmethodservice.cts.db.Table;
import android.inputmethodservice.cts.DeviceEvent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * IME event content provider.
 */
public final class EventProvider extends ContentProvider {

    private static final String TAG = EventProvider.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String DB_NAME = "database";
    private static final int DB_VERSION = 1;

    private UriHelper.Factory mUriFactory;
    private Database mDatabase;

    @Override
    public boolean onCreate() {
        mUriFactory = UriHelper.Factory.builder()
                .addUri(AUTHORITY, EventTableConstants.NAME, EventTableConstants.TYPE_DIR)
                .addUri(AUTHORITY, EventTableConstants.NAME + "/#", EventTableConstants.TYPE_ITEM)
                .build();
        mDatabase = new Database(getContext(), DB_NAME, DB_VERSION) {
            @Override
            @NonNull
            protected List<Table> getTables() {
                return Collections.singletonList(DeviceEvent.TABLE);
            }
        };
        return true;
    }

    @Override
    public Cursor query(@NonNull final Uri uri, @Nullable final String[] projection,
            final @Nullable String selection, @Nullable final String[] selectionArgs,
            @Nullable final String orderBy) {
        final UriHelper uriHelper = mUriFactory.newInstance(uri);
        if (DEBUG) {
            Log.d(TAG, "query:"
                    + " uri=" + uri
                    + " projection=" + Arrays.toString(projection)
                    + " selection=" + uriHelper.buildSelection(selection)
                    + " selectionArgs=" + Arrays.toString(
                            uriHelper.buildSelectionArgs(selectionArgs))
                    + " orderBy=" + orderBy);
        }
        final Cursor cursor = mDatabase.query(
                uriHelper.table, projection, uriHelper.buildSelection(selection),
                uriHelper.buildSelectionArgs(selectionArgs), orderBy);
        if (DEBUG) {
            Log.d(TAG, "  query.count=" + cursor.getCount());
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public Uri insert(@NonNull final Uri uri, @Nullable final ContentValues values) {
        final UriHelper uriHelper = mUriFactory.newInstance(uri);
        if (DEBUG) {
            Log.d(TAG, "insert: uri=" + uri + " values={" + values + "}");
        }
        final long rowId = mDatabase.insert(uriHelper.table, values);
        final Uri insertedUri = ContentUris.withAppendedId(uri, rowId);
        if (DEBUG) {
            Log.d(TAG, "  insert.uri=" + insertedUri);
        }
        getContext().getContentResolver().notifyChange(insertedUri, null);
        return insertedUri;
    }

    @Override
    public int delete(@NonNull final Uri uri, @Nullable final String selection,
            @Nullable final String[] selectionArgs) {
        final UriHelper uriHelper = mUriFactory.newInstance(uri);
        if (DEBUG) {
            Log.d(TAG, "delete:"
                    + " uri=" + uri
                    + " selection=" + uriHelper.buildSelection(selection)
                    + " selectionArgs=" + Arrays.toString(
                            uriHelper.buildSelectionArgs(selectionArgs)));
        }
        final int count = mDatabase.delete(uriHelper.table, uriHelper.buildSelection(selection),
                uriHelper.buildSelectionArgs(selectionArgs));
        if (DEBUG) {
            Log.d(TAG, "  delete.count=" + count);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(@NonNull final Uri uri, @Nullable final ContentValues values,
            final @Nullable String selection, @Nullable final String[] selectionArgs) {
        final UriHelper uriHelper = mUriFactory.newInstance(uri);
        if (DEBUG) {
            Log.d(TAG, "update:"
                    + " uri=" + uri
                    + " values={" + values + "}"
                    + " selection=" + uriHelper.buildSelection(selection)
                    + " selectionArgs=" + Arrays.toString(
                            uriHelper.buildSelectionArgs(selectionArgs)));
        }
        final int count = mDatabase.update(uriHelper.table, values,
                uriHelper.buildSelection(selection), uriHelper.buildSelectionArgs(selectionArgs));
        if (DEBUG) {
            Log.d(TAG, "  update.count=" + count);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    @Nullable
    public String getType(@NonNull final Uri uri) {
        return mUriFactory.getTypeOf(uri);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        mDatabase.close();
        mDatabase = null;
        mUriFactory = null;
    }
}
