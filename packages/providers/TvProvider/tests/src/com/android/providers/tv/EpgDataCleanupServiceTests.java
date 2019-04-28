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

import com.google.android.collect.Sets;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvContract.WatchedPrograms;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.suitebuilder.annotation.Suppress;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.ServiceTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EpgDataCleanupServiceTests extends ServiceTestCase<EpgDataCleanupService> {
    private static final String FAKE_INPUT_ID = "EpgDataCleanupServiceTests";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;

    public EpgDataCleanupServiceTests() {
        super(EpgDataCleanupService.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = new MockContentResolver();
        // DateUtils tries to access Settings provider to get date format string.
        mResolver.addProvider(Settings.AUTHORITY, new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return new Bundle();
            }
        });

        mProvider = new TvProviderForTesting();
        mResolver.addProvider(TvContract.AUTHORITY, mProvider);

        setContext(new MockTvProviderContext(mResolver, getSystemContext()));

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);

        Utils.clearTvProvider(mResolver);
        startService(new Intent(getContext(), EpgDataCleanupService.class));
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    private static class Program {
        long id;
        final long startTime;
        final long endTime;

        Program(long startTime, long endTime) {
            this(-1, startTime, endTime);
        }

        Program(long id, long startTime, long endTime) {
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Program)) {
                return false;
            }
            Program that = (Program) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(startTime, that.startTime)
                    && Objects.equals(endTime, that.endTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, startTime, endTime);
        }

        @Override
        public String toString() {
            return "Program(id=" + id + ",start=" + startTime + ",end=" + endTime + ")";
        }
    }

    private long insertChannel() {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        Uri uri = mResolver.insert(Channels.CONTENT_URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    private void insertPrograms(Program... programs) {
        insertPrograms(Arrays.asList(programs));
    }

    private void insertPrograms(Collection<Program> programs) {
        long channelId = insertChannel();

        ContentValues values = new ContentValues();
        values.put(Programs.COLUMN_CHANNEL_ID, channelId);
        for (Program program : programs) {
            values.put(Programs.COLUMN_START_TIME_UTC_MILLIS, program.startTime);
            values.put(Programs.COLUMN_END_TIME_UTC_MILLIS, program.endTime);
            Uri uri = mResolver.insert(Programs.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    private Set<Program> queryPrograms() {
        String[] projection = new String[] {
            Programs._ID,
            Programs.COLUMN_START_TIME_UTC_MILLIS,
            Programs.COLUMN_END_TIME_UTC_MILLIS,
        };

        Cursor cursor = mResolver.query(Programs.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<Program> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new Program(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2)));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    private void insertWatchedPrograms(Program... programs) {
        insertWatchedPrograms(Arrays.asList(programs));
    }

    private void insertWatchedPrograms(Collection<Program> programs) {
        long channelId = insertChannel();

        ContentValues values = new ContentValues();
        values.put(WatchedPrograms.COLUMN_PACKAGE_NAME, getContext().getPackageName());
        values.put(WatchedPrograms.COLUMN_CHANNEL_ID, channelId);
        for (Program program : programs) {
            values.put(WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS, program.startTime);
            values.put(WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS, program.endTime);
            Uri uri = mProvider.insertWatchedProgramSync(values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    private Set<Program> queryWatchedPrograms() {
        String[] projection = new String[] {
            WatchedPrograms._ID,
            WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS,
            WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS,
        };

        Cursor cursor = mResolver.query(WatchedPrograms.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<Program> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new Program(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2)));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    @Override
    public void testServiceTestCaseSetUpProperly() throws Exception {
        assertNotNull(getService());
    }

    public void testClearOldPrograms() {
        Program program = new Program(1, 2);
        insertPrograms(program);

        getService().clearOldPrograms(2);
        assertEquals("Program should NOT be deleted if it ended at given time.",
                Sets.newHashSet(program), queryPrograms());

        getService().clearOldPrograms(3);
        assertTrue("Program should be deleted if it ended before given time.",
                queryPrograms().isEmpty());

        ArrayList<Program> programs = new ArrayList<Program>();
        for (int i = 0; i < 10; i++) {
            programs.add(new Program(999 + i, 1000 + i));
        }
        insertPrograms(programs);

        getService().clearOldPrograms(1005);
        assertEquals("Program should be deleted if and only if it ended before given time.",
                new HashSet<Program>(programs.subList(5, 10)), queryPrograms());
    }

    // Disable temporarily since it's not trivial to fix due to asynchronous implementation of
    // watch history management.
    public void testClearOldWatchedPrograms() {
        Program program = new Program(1, 2);
        insertWatchedPrograms(program);

        getService().clearOldWatchHistory(1);
        assertEquals("Watch history should NOT be deleted if watch started at given time.",
                Sets.newHashSet(program), queryWatchedPrograms());

        getService().clearOldWatchHistory(2);
        assertTrue("Watch history shuold be deleted if watch started before given time.",
                queryWatchedPrograms().isEmpty());

        ArrayList<Program> programs = new ArrayList<Program>();
        for (int i = 0; i < 10; i++) {
            programs.add(new Program(1000 + i, 1001 + i));
        }
        insertWatchedPrograms(programs);

        getService().clearOldWatchHistory(1005);
        assertEquals("Watch history should be deleted if and only if it started before given time.",
                new HashSet<Program>(programs.subList(5, 10)), queryWatchedPrograms());
    }

    // Disable temporarily since it's not trivial to fix due to asynchronous implementation of
    // watch history management.
    public void testClearOverflowWatchHistory() {
        ArrayList<Program> programs = new ArrayList<Program>();
        for (int i = 0; i < 10; i++) {
            programs.add(new Program(1000 + i, 1001 + i));
        }
        insertWatchedPrograms(programs);

        getService().clearOverflowWatchHistory(5);
        assertEquals("Watch history should be deleted in watch start time order.",
                new HashSet<Program>(programs.subList(5, 10)), queryWatchedPrograms());

        getService().clearOverflowWatchHistory(0);
        assertTrue("All history should be deleted.", queryWatchedPrograms().isEmpty());
    }
}
