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

package com.android.documentsui;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.database.Cursor;
import android.provider.DocumentsContract.Document;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.testing.ActivityManagers;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestFileTypeLookup;
import com.android.documentsui.testing.TestImmediateExecutor;
import com.android.documentsui.testing.TestProvidersAccess;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class RecentsLoaderTests {

    private TestEnv mEnv;
    private TestActivity mActivity;
    private RecentsLoader mLoader;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mActivity.activityManager = ActivityManagers.create(false);

        mEnv.state.action = State.ACTION_BROWSE;
        mEnv.state.acceptMimes = new String[] { "*/*" };

        mLoader = new RecentsLoader(mActivity, mEnv.providers, mEnv.state, mEnv.features,
                TestImmediateExecutor.createLookup(), new TestFileTypeLookup());
    }

    @Test
    public void testDocumentsNotMovable() {
        final DocumentInfo doc = mEnv.model.createFile("freddy.jpg",
                Document.FLAG_SUPPORTS_MOVE
                        | Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_REMOVE);
        doc.lastModified = System.currentTimeMillis();
        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextRecentDocumentsReturns(doc);

        final DirectoryResult result = mLoader.loadInBackground();

        final Cursor c = result.cursor;
        assertEquals(1, c.getCount());
        for (int i = 0; i < c.getCount(); ++i) {
            c.moveToNext();
            final int flags = c.getInt(c.getColumnIndex(Document.COLUMN_FLAGS));
            assertEquals(0, flags & Document.FLAG_SUPPORTS_DELETE);
            assertEquals(0, flags & Document.FLAG_SUPPORTS_REMOVE);
            assertEquals(0, flags & Document.FLAG_SUPPORTS_MOVE);
        }
    }
}
