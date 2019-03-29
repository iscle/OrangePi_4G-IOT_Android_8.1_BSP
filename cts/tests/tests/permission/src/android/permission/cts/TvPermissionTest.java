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

package android.permission.cts;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.net.Uri;
import android.test.AndroidTestCase;

/**
 * Tests for TV API related permissions.
 */
public class TvPermissionTest extends AndroidTestCase {
    private static final String DUMMY_INPUT_ID = "dummy";

    private boolean mHasTvInputFramework;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHasTvInputFramework = getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LIVE_TV);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void verifyInsert(Uri uri, String tableName) throws Exception {
        try {
            ContentValues values = new ContentValues();
            getContext().getContentResolver().insert(uri, values);
            fail("Accessing " + tableName + " table should require WRITE_EPG_DATA permission.");
        } catch (SecurityException e) {
            // Expected exception
        }
    }

    public void verifyUpdate(Uri uri, String tableName) throws Exception {
        try {
            ContentValues values = new ContentValues();
            getContext().getContentResolver().update(uri, values, null, null);
            fail("Accessing " + tableName + " table should require WRITE_EPG_DATA permission.");
        } catch (SecurityException e) {
            // Expected exception
        }
    }

    public void verifyDelete(Uri uri, String tableName) throws Exception {
        try {
            getContext().getContentResolver().delete(uri, null, null);
            fail("Accessing " + tableName + " table should require WRITE_EPG_DATA permission.");
        } catch (SecurityException e) {
            // Expected exception
        }
    }

    public void testInsertChannels() throws Exception {
        if (!mHasTvInputFramework) return;
        verifyInsert(TvContract.Channels.CONTENT_URI, "channels");
    }

    public void testUpdateChannels() throws Exception {
        if (!mHasTvInputFramework) return;
        verifyUpdate(TvContract.Channels.CONTENT_URI, "channels");
    }

    public void testDeleteChannels() throws Exception {
        if (!mHasTvInputFramework) return;
        verifyDelete(TvContract.Channels.CONTENT_URI, "channels");
    }

    public void testInsertPrograms() throws Exception {
        if (!mHasTvInputFramework) return;
        verifyInsert(TvContract.Programs.CONTENT_URI, "programs");
    }

    public void testUpdatePrograms() throws Exception {
        if (!mHasTvInputFramework) return;
        verifyUpdate(TvContract.Programs.CONTENT_URI, "programs");
    }

    public void testDeletePrograms() throws Exception {
        if (!mHasTvInputFramework) return;
        verifyDelete(TvContract.Programs.CONTENT_URI, "programs");
    }
}
