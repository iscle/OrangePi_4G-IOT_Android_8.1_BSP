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

package com.android.documentsui;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.documentsui.base.Features;
import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.testing.TestModel;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestRecyclerView;

import java.util.ArrayList;
import java.util.List;

@SmallTest
public class FocusManagerTest extends AndroidTestCase {

    private static final String TEST_AUTHORITY = "test_authority";

    private static final List<String> ITEMS = TestData.create(10);

    private FocusManager mManager;
    private TestRecyclerView mView;
    private SelectionManager mSelectionMgr;
    private TestFeatures mFeatures;

    @Override
    public void setUp() throws Exception {
        mView = TestRecyclerView.create(ITEMS);
        mSelectionMgr = SelectionManagers.createTestInstance(ITEMS);
        mFeatures = new TestFeatures();
        mManager = new FocusManager(mFeatures, mSelectionMgr, null, null, 0)
                .reset(mView, new TestModel(TEST_AUTHORITY, mFeatures));
    }

    public void testFocus() {
        mManager.focusDocument(Integer.toString(3));
        mView.assertItemViewFocused(3);
     }

    public void testPendingFocus() {
       mManager.focusDocument(Integer.toString(10));
       List<String> mutableItems = TestData.create(11);
       mView.setItems(mutableItems);
       mManager.onLayoutCompleted();
       // Should only be called once
       mView.assertItemViewFocused(10);
    }

    public void testFocusDirectoryList_noItemsToFocus() {
        mView = TestRecyclerView.create(new ArrayList<>());
        mManager = new FocusManager(
                mFeatures, SelectionManagers.createTestInstance(), null, null, 0)
                .reset(mView, new TestModel(TEST_AUTHORITY, mFeatures));
        assertFalse(mManager.focusDirectoryList());
    }

    public void testFocusDirectoryList_hasSelection() {
        mSelectionMgr.toggleSelection("0");
        assertFalse(mManager.focusDirectoryList());
    }
}
