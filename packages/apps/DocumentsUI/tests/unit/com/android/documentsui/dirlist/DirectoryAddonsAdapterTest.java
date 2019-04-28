/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.test.filters.MediumTest;
import android.support.v7.widget.RecyclerView;
import android.test.AndroidTestCase;
import android.view.ViewGroup;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.Model;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.State;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFileTypeLookup;

@MediumTest
public class DirectoryAddonsAdapterTest extends AndroidTestCase {

    private static final String AUTHORITY = "test_authority";

    private TestEnv mEnv;
    private DirectoryAddonsAdapter mAdapter;
    private ActionHandler mActionHandler;

    public void setUp() {

        mEnv = TestEnv.create(AUTHORITY);
        mActionHandler = new TestActionHandler();
        mEnv.clear();

        final Context testContext = TestContext.createStorageTestContext(getContext(), AUTHORITY);
        DocumentsAdapter.Environment env = new TestEnvironment(testContext);

        mAdapter = new DirectoryAddonsAdapter(
            env,
            new ModelBackedDocumentsAdapter(
                    env, new IconHelper(testContext, State.MODE_GRID), new TestFileTypeLookup()));

        mEnv.model.addUpdateListener(mAdapter.getModelUpdateListener());
    }

    // Tests that the item count is correct for a directory containing files and subdirs.
    public void testItemCount_mixed() {
        mEnv.reset();  // creates a mix of folders and files for us.

        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
    }

    // Tests that the item count is correct for a directory containing only subdirs.
    public void testItemCount_allDirs() {
        String[] names = {"Trader Joe's", "Alphabeta", "Lucky", "Vons", "Gelson's"};
        for (String name : names) {
            mEnv.model.createFolder(name);
        }
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    // Tests that the item count is correct for a directory containing only files.
    public void testItemCount_allFiles() {
        String[] names = {"123.txt", "234.jpg", "abc.pdf"};
        for (String name : names) {
            mEnv.model.createFile(name);
        }
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    public void testAddsInfoMessage_WithDirectoryChildren() {
        String[] names = {"123.txt", "234.jpg", "abc.pdf"};
        for (String name : names) {
            mEnv.model.createFile(name);
        }
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_INFO, "some info");
        mEnv.model.setCursorExtras(bundle);
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    public void testItemCount_none() {
        mEnv.model.update();
        assertEquals(1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE);
    }

    public void testAddsInfoMessage_WithNoItem() {
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_INFO, "some info");
        mEnv.model.setCursorExtras(bundle);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    public void testAddsErrorMessage_WithNoItem() {
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_ERROR, "some error");
        mEnv.model.setCursorExtras(bundle);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    private void assertHolderType(int index, int type) {
        assertTrue(mAdapter.getItemViewType(index) == type);
    }

    private final class TestEnvironment implements DocumentsAdapter.Environment {
        private final Context testContext;

        private TestEnvironment(Context testContext) {
            this.testContext = testContext;
        }

        @Override
        public Features getFeatures() {
            return mEnv.features;
        }

        @Override
        public ActionHandler getActionHandler() { return mActionHandler; }

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

    private static class DummyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        public int getItemCount() { return 0; }
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {}
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return null;
        }
    }
}
