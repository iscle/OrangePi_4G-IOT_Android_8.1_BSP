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

package com.android.documentsui.dirlist;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.content.ClipData;
import android.database.Cursor;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.DragEvent;
import android.view.View;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.files.TestActivity;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.testing.ClipDatas;
import com.android.documentsui.testing.DragEvents;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestDragAndDropManager;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.Views;
import com.android.documentsui.ui.TestDialogController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DragHostTest {
    private static final List<String> ITEMS = TestData.create(100);

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestActionHandler mActionHandler;
    private TestDialogController mDialogs;
    private DragHost<?> dragHost;
    private TestDragAndDropManager mDragAndDropManager;
    private SelectionManager mSelectionMgr;
    private boolean mIsDocumentView;
    private DocumentHolder mNextDocumentHolder;
    private DocumentInfo mNextDocumentInfo;

    @Before
    public void setUp() throws Exception {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mDialogs = new TestDialogController();
        mDragAndDropManager = new TestDragAndDropManager();
        mSelectionMgr = SelectionManagers.createTestInstance(ITEMS);
        mActionHandler = new TestActionHandler();
        dragHost = new DragHost<>(
                mActivity,
                mDragAndDropManager,
                mSelectionMgr,
                mActionHandler,
                mEnv.state,
                mDialogs,
                (View v) -> mIsDocumentView,
                (View v) -> mNextDocumentHolder,
                (View v) -> mNextDocumentInfo
        );
    }
    @Test
    public void testHandleDrop_onValidView() {
        final ClipData data = ClipDatas.createTestClipData();
        final DragEvent dropEvent = DragEvents.createTestDropEvent(data);
        final View view = Views.createTestView();
        mNextDocumentInfo = TestEnv.FOLDER_0;
        mDragAndDropManager.dropOnDocumentHandler.nextReturn(true);

        assertTrue(dragHost.handleDropEvent(view, dropEvent));
        mDragAndDropManager.dropOnDocumentHandler.assertCalled();
    }

    @Test
    public void testHandleDrop_notOnValidView() {
        final ClipData data = ClipDatas.createTestClipData();
        final DragEvent dropEvent = DragEvents.createTestDropEvent(data);
        final View view = Views.createTestView();

        assertFalse(dragHost.handleDropEvent(view, dropEvent));
        mDragAndDropManager.dropOnDocumentHandler.assertNotCalled();
    }
}
