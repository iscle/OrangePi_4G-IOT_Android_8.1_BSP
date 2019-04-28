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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.fail;

import android.provider.DocumentsContract;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;
import android.view.View;

import com.android.documentsui.MenuManager;
import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.DragStartListener.ActiveListener;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.testing.TestDragAndDropManager;
import com.android.documentsui.testing.TestEvent;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestSelectionDetails;
import com.android.documentsui.testing.Views;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DragStartListenerTest {

    private ActiveListener mListener;
    private TestEvent.Builder mEvent;
    private SelectionManager mMultiSelectManager;
    private SelectionDetails mSelectionDetails;
    private String mViewModelId;
    private TestDragAndDropManager mManager;

    @Before
    public void setUp() throws Exception {
        mMultiSelectManager = SelectionManagers.createTestInstance();
        mManager = new TestDragAndDropManager();
        mSelectionDetails = new TestSelectionDetails();

        DocumentInfo doc = new DocumentInfo();
        doc.authority = Providers.AUTHORITY_STORAGE;
        doc.documentId = "id";
        doc.derivedUri = DocumentsContract.buildDocumentUri(doc.authority, doc.documentId);

        State state = new State();
        state.stack.push(doc);
        mListener = new DragStartListener.ActiveListener(
                null, // icon helper
                state,
                mMultiSelectManager,
                mSelectionDetails,
                // view finder
                (float x, float y) -> {
                    return Views.createTestView(x, y);
                },
                // model id finder
                (View view) -> {
                    return mViewModelId;
                },
                // docInfo Converter
                (Selection selection) -> {
                    return new ArrayList<DocumentInfo>();
                },
                mManager);

        mViewModelId = "1234";

        mEvent = TestEvent.builder()
                .action(MotionEvent.ACTION_MOVE)
                .mouse()
                .at(1)
                .inDragHotspot()
                .primary();
    }

    @Test
    public void testDragStarted_OnMouseMove() {
        assertTrue(mListener.onMouseDragEvent(mEvent.build()));
        mManager.startDragHandler.assertCalled();
    }

    @Test
    public void testDragNotStarted_NonModelBackedView() {
        mViewModelId = null;
        assertFalse(mListener.onMouseDragEvent(mEvent.build()));
        mManager.startDragHandler.assertNotCalled();
    }

    @Test
    public void testThrows_OnNonMouseMove() {
        TestEvent e = TestEvent.builder()
                .at(1)
                .action(MotionEvent.ACTION_MOVE).build();
        assertThrows(e);
    }

    @Test
    public void testThrows_OnNonPrimaryMove() {
        assertThrows(mEvent.pressButton(MotionEvent.BUTTON_PRIMARY).build());
    }

    @Test
    public void testThrows_OnNonMove() {
        assertThrows(mEvent.action(MotionEvent.ACTION_UP).build());
    }

    @Test
    public void testThrows_WhenNotOnItem() {
        assertThrows(mEvent.at(-1).build());
    }

    @Test
    public void testDragStart_nonSelectedItem() {
        Selection selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).build());
        assertTrue(selection.size() == 1);
        assertTrue(selection.contains("1234"));
    }

    @Test
    public void testDragStart_selectedItem() {
        Selection selection = new Selection();
        selection.add("1234");
        selection.add("5678");
        mMultiSelectManager.replaceSelection(selection);

        selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).build());
        assertTrue(selection.size() == 2);
        assertTrue(selection.contains("1234"));
        assertTrue(selection.contains("5678"));
    }

    @Test
    public void testDragStart_newNonSelectedItem() {
        Selection selection = new Selection();
        selection.add("5678");
        mMultiSelectManager.replaceSelection(selection);

        selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).build());
        assertTrue(selection.size() == 1);
        assertTrue(selection.contains("1234"));
        // After this, selection should be cleared
        assertFalse(mMultiSelectManager.hasSelection());
    }

    @Test
    public void testCtrlDragStart_newNonSelectedItem() {
        Selection selection = new Selection();
        selection.add("5678");
        mMultiSelectManager.replaceSelection(selection);

        selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).ctrl().build());
        assertTrue(selection.size() == 2);
        assertTrue(selection.contains("1234"));
        assertTrue(selection.contains("5678"));
    }

    private void assertThrows(InputEvent e) {
        try {
            assertFalse(mListener.onMouseDragEvent(e));
            fail();
        } catch (AssertionError expected) {}
    }
}
