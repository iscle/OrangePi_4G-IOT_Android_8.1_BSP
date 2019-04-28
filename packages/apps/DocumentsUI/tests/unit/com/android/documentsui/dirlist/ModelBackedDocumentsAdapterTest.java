/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import android.app.PendingIntent;
import android.content.Context;
import android.database.Cursor;
import android.support.test.filters.MediumTest;
import android.test.AndroidTestCase;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.Model;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.State;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFileTypeLookup;

@MediumTest
public class ModelBackedDocumentsAdapterTest extends AndroidTestCase {

    private static final String AUTHORITY = "test_authority";

    private TestEnv mEnv;
    private ActionHandler mActionHandler;
    private ModelBackedDocumentsAdapter mAdapter;

    public void setUp() {

        final Context testContext = TestContext.createStorageTestContext(getContext(), AUTHORITY);
        mEnv = TestEnv.create(AUTHORITY);
        mActionHandler = new TestActionHandler();

        DocumentsAdapter.Environment env = new TestEnvironment(testContext);

        mAdapter = new ModelBackedDocumentsAdapter(
                env, new IconHelper(testContext, State.MODE_GRID), new TestFileTypeLookup());
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
    }

    // Tests that the item count is correct.
    public void testItemCount() {
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    private final class TestEnvironment implements DocumentsAdapter.Environment {
        private final Context testContext;

        @Override
        public Features getFeatures() {
            return mEnv.features;
        }

        @Override
        public ActionHandler getActionHandler() { return mActionHandler; }

        private TestEnvironment(Context testContext) {
            this.testContext = testContext;
        }

        @Override
        public boolean isSelected(String id) {
            return false;
        }

        @Override
        public boolean isDocumentEnabled(String mimeType, int flags) {
            return true;
        }

        @Override
        public void initDocumentHolder(DocumentHolder holder) {}

        @Override
        public Model getModel() {
            return mEnv.model;
        }

        @Override
        public State getDisplayState() {
            return null;
        }

        @Override
        public boolean isInSearchMode() {
            return false;
        }

        @Override
        public Context getContext() {
            return testContext;
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public void onBindDocumentHolder(DocumentHolder holder, Cursor cursor) {}
    }
}
