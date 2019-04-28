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

import com.google.android.collect.Sets;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public class PackageRemovedReceiverTest extends AndroidTestCase {
    private static final String FAKE_INPUT_ID = "PackageRemovedReceiverTest";

    private static final String FAKE_PACKAGE_NAME_1 = "package.removed.receiver.Test1";
    private static final String FAKE_PACKAGE_NAME_2 = "package.removed.receiver.Test2";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;
    private PackageRemovedReceiver mReceiver;

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

        mReceiver = new PackageRemovedReceiver();
        Utils.clearTvProvider(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    private static class Program {
        long id;
        final String packageName;

        Program(String pkgName) {
            this(-1, pkgName);
        }

        Program(long id, String pkgName) {
            this.id = id;
            this.packageName = pkgName;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Program)) {
                return false;
            }
            Program that = (Program) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(packageName, that.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, packageName);
        }

        @Override
        public String toString() {
            return "Program(id=" + id + ",packageName=" + packageName + ")";
        }
    }

    private long insertChannel() {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        Uri uri = mResolver.insert(Channels.CONTENT_URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    private void insertPrograms(long channelId, Program... programs) {
        insertPrograms(channelId, Arrays.asList(programs));
    }

    private void insertPrograms(long channelId, Collection<Program> programs) {
        ContentValues values = new ContentValues();
        values.put(Programs.COLUMN_CHANNEL_ID, channelId);
        for (Program program : programs) {
            Uri uri = mResolver.insert(Programs.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    private Set<Program> queryPrograms() {
        String[] projection = new String[] {
                Programs._ID,
                TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
        };

        Cursor cursor = mResolver.query(Programs.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<Program> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new Program(cursor.getLong(0), cursor.getString(1)));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    private long getChannelCount() {
        String[] projection = new String[] {
                Channels._ID,
        };

        Cursor cursor = mResolver.query(Channels.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    public void testPackageRemoved() {
        Program programInPackage1 = new Program(FAKE_PACKAGE_NAME_1);
        Program programInPackage2 = new Program(FAKE_PACKAGE_NAME_2);
        mProvider.callingPackage = FAKE_PACKAGE_NAME_1;
        long channelInPackage1Id = insertChannel();
        insertPrograms(channelInPackage1Id, programInPackage1);
        mProvider.callingPackage = FAKE_PACKAGE_NAME_2;
        long channelInPackage2ID = insertChannel();
        insertPrograms(channelInPackage2ID, programInPackage2);

        assertEquals(Sets.newHashSet(programInPackage1, programInPackage2), queryPrograms());
        assertEquals(2, getChannelCount());

        mReceiver.onReceive(getContext(), new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED,
                Uri.parse("package:" + FAKE_PACKAGE_NAME_1)));

        assertEquals("Program should be removed if its package is removed.",
                Sets.newHashSet(programInPackage2), queryPrograms());
        assertEquals("Channel should be removed if its package is removed.", 1, getChannelCount());

        mReceiver.onReceive(getContext(), new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED,
                Uri.parse("package:" + FAKE_PACKAGE_NAME_2)));

        assertTrue("Program should be removed if its package is removed.",
                queryPrograms().isEmpty());
        assertEquals("Channel should be removed if its package is removed.", 0, getChannelCount());
    }
}
