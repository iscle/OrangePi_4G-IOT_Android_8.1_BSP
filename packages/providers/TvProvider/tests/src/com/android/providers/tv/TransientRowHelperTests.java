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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.PreviewPrograms;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TransientRowHelperTests extends AndroidTestCase {
    private static final String FAKE_INPUT_ID = "TransientRowHelperTests";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;
    private RebootSimulatingTransientRowHelper mTransientRowHelper;

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
        mTransientRowHelper = new RebootSimulatingTransientRowHelper(getContext());
        mProvider.setTransientRowHelper(mTransientRowHelper);
        Utils.clearTvProvider(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    private static class PreviewProgram {
        long id;
        final boolean isTransient;

        PreviewProgram(boolean isTransient) {
            this(-1, isTransient);
        }

        PreviewProgram(long id, boolean isTransient) {
            this.id = id;
            this.isTransient = isTransient;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PreviewProgram)) {
                return false;
            }
            PreviewProgram that = (PreviewProgram) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(isTransient, that.isTransient);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, isTransient);
        }

        @Override
        public String toString() {
            return "PreviewProgram(id=" + id + ",isTransient=" + isTransient + ")";
        }
    }

    private long insertChannel(boolean isTransient) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        values.put(Channels.COLUMN_TRANSIENT, isTransient ? 1 : 0);
        Uri uri = mResolver.insert(Channels.CONTENT_URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    private void insertPreviewPrograms(long channelId, PreviewProgram... programs) {
        insertPreviewPrograms(channelId, Arrays.asList(programs));
    }

    private void insertPreviewPrograms(long channelId, Collection<PreviewProgram> programs) {
        ContentValues values = new ContentValues();
        values.put(PreviewPrograms.COLUMN_CHANNEL_ID, channelId);
        for (PreviewProgram program : programs) {
            values.put(PreviewPrograms.COLUMN_TYPE, PreviewPrograms.TYPE_MOVIE);
            values.put(PreviewPrograms.COLUMN_TRANSIENT, program.isTransient ? 1 : 0);
            Uri uri = mResolver.insert(PreviewPrograms.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    private Set<PreviewProgram> queryPreviewPrograms() {
        String[] projection = new String[] {
            PreviewPrograms._ID,
            PreviewPrograms.COLUMN_TRANSIENT,
        };

        Cursor cursor = mResolver.query(PreviewPrograms.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<PreviewProgram> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new PreviewProgram(cursor.getLong(0), cursor.getInt(1) == 1));
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

    public void testTransientRowsAreDeletedAfterReboot() {
        PreviewProgram transientProgramInTransientChannel =
                new PreviewProgram(true /* transient */);
        PreviewProgram permanentProgramInTransientChannel =
                new PreviewProgram(false /* transient */);
        PreviewProgram transientProgramInPermanentChannel =
                new PreviewProgram(true /* transient */);
        PreviewProgram permanentProgramInPermanentChannel =
                new PreviewProgram(false /* transient */);
        long transientChannelId = insertChannel(true /* transient */);
        long permanentChannelId = insertChannel(false /* transient */);
        insertPreviewPrograms(transientChannelId, transientProgramInTransientChannel);
        insertPreviewPrograms(transientChannelId, permanentProgramInTransientChannel);
        insertPreviewPrograms(permanentChannelId, transientProgramInPermanentChannel);
        insertPreviewPrograms(permanentChannelId, permanentProgramInPermanentChannel);

        assertEquals("Before reboot all the programs inserted should exist.",
                Sets.newHashSet(transientProgramInTransientChannel,
                        permanentProgramInTransientChannel, transientProgramInPermanentChannel,
                        permanentProgramInPermanentChannel),
                queryPreviewPrograms());
        assertEquals("Before reboot the channels inserted should exist.",
                2, getChannelCount());

        mTransientRowHelper.simulateReboot();
        assertEquals("Transient program and programs in transient channel should be removed.",
                Sets.newHashSet(permanentProgramInPermanentChannel), queryPreviewPrograms());
        assertEquals("Transient channel should not be removed.",
                1, getChannelCount());
    }

    private class RebootSimulatingTransientRowHelper extends TransientRowHelper {
        private int mLastDeletionBootCount;
        private int mBootCount = 1;

        private RebootSimulatingTransientRowHelper(Context context) {
            super(context);
        }

        @Override
        protected int getBootCount() {
            return mBootCount;
        }

        @Override
        protected int getLastDeletionBootCount() {
            return mLastDeletionBootCount;
        }

        @Override
        protected void setLastDeletionBootCount() {
            mLastDeletionBootCount = mBootCount;
        }

        private void simulateReboot() {
            mTransientRowsDeleted = false;
            mBootCount++;
        }
    }
}
