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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionProbe;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEvent;
import com.android.documentsui.testing.TestEvent.Builder;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestPredicate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_MouseTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler<TestEvent> mInputHandler;
    private TestActionHandler mActionHandler;
    private TestFocusHandler mFocusHandler;
    private SelectionProbe mSelection;
    private SelectionManager mSelectionMgr;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestEventHandler<InputEvent> mContextMenuClickHandler;
    private TestEventHandler<InputEvent> mDragAndDropHandler;
    private TestEventHandler<InputEvent> mGestureSelectHandler;
    private TestEventHandler<Void> mPerformHapticFeedback;

    private Builder mEvent;

    @Before
    public void setUp() {

        mSelectionMgr = SelectionManagers.createTestInstance(ITEMS);
        mActionHandler = new TestActionHandler();

        mSelection = new SelectionProbe(mSelectionMgr);
        mCanSelect = new TestPredicate<>();
        mContextMenuClickHandler = new TestEventHandler<>();
        mDragAndDropHandler = new TestEventHandler<>();
        mGestureSelectHandler = new TestEventHandler<>();
        mFocusHandler = new TestFocusHandler();

        mInputHandler = new UserInputHandler<>(
                mActionHandler,
                mFocusHandler,
                mSelectionMgr,
                (MotionEvent event) -> {
                    throw new UnsupportedOperationException("Not exercised in tests.");
                },
                mCanSelect,
                mContextMenuClickHandler::accept,
                mDragAndDropHandler::accept,
                mGestureSelectHandler::accept,
                () -> mPerformHapticFeedback.accept(null));

        mEvent = TestEvent.builder().mouse().overDocIcon();
    }

    @Test
    public void testConfirmedClick_StartsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).build());
        mSelection.assertSelection(11);
    }

    @Test
    public void testClickOnIconWithExistingSelection_AddsToSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).build());
        mInputHandler.onSingleTapUp(mEvent.at(10).build());
        mSelection.assertSelected(10, 11);
    }

    @Test
    public void testClickOnIconOfSelectedItem_RemovesFromSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(8).build());
        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertSelected(8, 9, 10, 11);

        mInputHandler.onSingleTapUp(mEvent.at(9).unshift().build());
        mSelection.assertSelected(8, 10, 11);
    }

    @Test
    public void testRightClickDown_StartsContextMenu() {
        mInputHandler.onDown(mEvent.secondary().build());
        mContextMenuClickHandler.assertLastArgument(mEvent.secondary().build());
    }

    @Test
    public void testAltClickDown_StartsContextMenu() {
        mInputHandler.onDown(mEvent.primary().alt().build());
        mContextMenuClickHandler.assertLastArgument(mEvent.primary().alt().build());
    }

    @Test
    public void testScroll_shouldTrap() {
        assertTrue(mInputHandler.onScroll(mEvent.at(0).action(MotionEvent.ACTION_MOVE).primary().build()));
    }

    @Test
    public void testScroll_NoTrapForTwoFinger() {
        assertFalse(mInputHandler.onScroll(mEvent.at(0).action(MotionEvent.ACTION_MOVE).build()));
    }

    @Test
    public void testUnconfirmedCtrlClick_AddsToExistingSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).ctrl().build());
        mSelection.assertSelection(7, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_ExtendsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testConfirmedShiftClick_ExtendsSelectionFromOriginFocus() {
        mFocusHandler.focusPos = 7;
        mFocusHandler.focusModelId = "7";
        // This is a hack-y test, since the real FocusManager would've set range begin itself.
        mSelectionMgr.setSelectionRangeBegin(7);
        mSelection.assertNoSelection();

        mInputHandler.onSingleTapConfirmed(mEvent.at(11).shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_RotatesAroundOrigin() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);

        mInputHandler.onSingleTapUp(mEvent.at(5).shift().build());
        mSelection.assertSelection(5, 6, 7);
        mSelection.assertNotSelected(8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftCtrlClick_Combination() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);

        mInputHandler.onSingleTapUp(mEvent.at(5).unshift().ctrl().build());

        mSelection.assertSelection(5, 7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftCtrlClick_ShiftTakesPriority() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).ctrl().shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    // TODO: Add testSpaceBar_Previews, but we need to set a system property
    // to have a deterministic state.

    @Test
    public void testDoubleClick_Opens() {
        mInputHandler.onDoubleTap(mEvent.at(11).build());
        mActionHandler.open.assertLastArgument(mEvent.build().getDocumentDetails());
    }

    @Test
    public void testMiddleClick_DoesNothing() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).tertiary().build());
        mSelection.assertNoSelection();
    }

    @Test
    public void testClickOff_ClearsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).build());
        mInputHandler.onSingleTapUp(mEvent.at(RecyclerView.NO_POSITION).build());
        mSelection.assertNoSelection();
    }

    @Test
    public void testClick_Focuses() {
        int id = 11;
        mInputHandler.onSingleTapConfirmed(mEvent.at(id).notOverDocIcon().build());
        assertTrue(mFocusHandler.getFocusModelId().equals(Integer.toString(id)));
    }

    @Test
    public void testClickOff_ClearsFocus() {
        int id = 11;
        mInputHandler.onSingleTapConfirmed(mEvent.at(id).notOverDocIcon().build());
        assertTrue(mFocusHandler.hasFocusedItem());
        mInputHandler.onSingleTapUp(mEvent.at(RecyclerView.NO_POSITION).build());
        assertFalse(mFocusHandler.hasFocusedItem());
    }

    @Test
    public void testClickOffSelection_RemovesSelectionAndFocuses() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(1).build());
        mInputHandler.onSingleTapUp(mEvent.at(5).shift().build());
        mSelection.assertSelection(1, 2, 3, 4, 5);

        int id = 11;
        mInputHandler.onSingleTapUp(mEvent.at(id).unshift().notOverDocIcon().build());
        assertTrue(mFocusHandler.getFocusModelId().equals(Integer.toString(id)));
        mSelection.assertNoSelection();
    }
}
