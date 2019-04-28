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

package com.android.providers.tv;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

public class ColumnFilterTest extends AndroidTestCase {
    private static final String FAKE_INPUT_ID = "ColumnFilterTest";
    private static final String NON_CONTRACTED_COLUMN_NAME = "non_contracted_column";
    private static final String FAKE_FIELD_CONTENT = "FakeFieldContent";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = new MockContentResolver();
        mResolver.addProvider(Settings.AUTHORITY, new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return new Bundle();
            }
        });

        mProvider = new TvProviderForTesting();
        mResolver.addProvider(TvContract.AUTHORITY, mProvider);

        setContext(new MockTvProviderContext(mResolver, getContext()));

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);
        Utils.clearTvProvider(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    private long insertChannel(boolean testNonContractedColumn) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        if (testNonContractedColumn) {
            values.put(NON_CONTRACTED_COLUMN_NAME, FAKE_FIELD_CONTENT);
        }
        Uri uri = mResolver.insert(Channels.CONTENT_URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    public void testQueryChannel() {
        long channelId = insertChannel(false);
        String[] projection = new String[]{
                Channels.COLUMN_INPUT_ID,
                NON_CONTRACTED_COLUMN_NAME,
                Channels._ID
        };
        Cursor cursor = mResolver.query(Channels.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        cursor.moveToNext();
        assertEquals(FAKE_INPUT_ID, cursor.getString(0));
        assertNull(cursor.getString(1));
        assertEquals(channelId, cursor.getLong(2));
    }

    public void testQueryChannelWithNullProjection() {
        long channelId = insertChannel(false);
        Cursor cursor = mResolver.query(Channels.CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
    }

    public void testQueryChannelWithNoValidColumn() {
        long channelId = insertChannel(false);
        String[] projection = new String[] {
                NON_CONTRACTED_COLUMN_NAME,
        };
        Cursor cursor = mResolver.query(Channels.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        cursor.moveToNext();
        assertNull(cursor.getString(0));
        assertEquals(0, cursor.getInt(0));
    }

    public void testInsertAndQueryChannel() {
        long channelId = insertChannel(true);
        String[] projection = new String[] {
                Channels.COLUMN_INPUT_ID,
                NON_CONTRACTED_COLUMN_NAME,
                Channels._ID
        };
        Cursor cursor = mResolver.query(Channels.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        cursor.moveToNext();
        assertEquals(FAKE_INPUT_ID, cursor.getString(0));
        assertNull(cursor.getString(1));
        assertEquals(channelId, cursor.getLong(2));
    }

    public void testUpdateChannel() {
        long channelId = insertChannel(false);
        ContentValues values = new ContentValues();
        values.put(NON_CONTRACTED_COLUMN_NAME, FAKE_FIELD_CONTENT);
        mResolver.update(Channels.CONTENT_URI, values, null, null);
        String[] projection = new String[] {
                Channels.COLUMN_INPUT_ID,
                NON_CONTRACTED_COLUMN_NAME,
                Channels._ID
        };
        Cursor cursor = mResolver.query(Channels.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        cursor.moveToNext();
        assertEquals(FAKE_INPUT_ID, cursor.getString(0));
        assertNull(cursor.getString(1));
        assertEquals(channelId, cursor.getLong(2));
    }
}
