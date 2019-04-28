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

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
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

/**
 * UserInputHandler / MultiSelectManager integration test covering the shared
 * responsibility of range selection.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_RangeTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler<TestEvent> mInputHandler;
    private TestActionHandler mActionHandler;

    private SelectionProbe mSelection;
    private TestFocusHandler mFocusHandler;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestEventHandler<InputEvent> mRightClickHandler;
    private TestEventHandler<InputEvent> mDragAndDropHandler;
    private TestEventHandler<InputEvent> mGestureSelectHandler;
    private TestEventHandler<Void> mPerformHapticFeedback;
    private Builder mEvent;

    @Before
    public void setUp() {

        SelectionManager selectionMgr = SelectionManagers.createTestInstance(ITEMS);
        mActionHandler = new TestActionHandler();
        mFocusHandler = new TestFocusHandler();
        mSelection = new SelectionProbe(selectionMgr);
        mCanSelect = new TestPredicate<>();
        mRightClickHandler = new TestEventHandler<>();
        mDragAndDropHandler = new TestEventHandler<>();
        mGestureSelectHandler = new TestEventHandler<>();

        mInputHandler = new UserInputHandler<>(
                mActionHandler,
                mFocusHandler,
                selectionMgr,
                (MotionEvent event) -> {
                    throw new UnsupportedOperationException("Not exercised in tests.");
                },
                mCanSelect,
                mRightClickHandler::accept,
                mDragAndDropHandler::accept,
                mGestureSelectHandler::accept,
                () -> mPerformHapticFeedback.accept(null));

        mEvent = TestEvent.builder().mouse().overDocIcon();
    }

    @Test
    public void testExtendRange() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());
        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertRangeSelection(7, 11);
    }

    @Test
    public void testExtendRangeContinues() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());
        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mInputHandler.onSingleTapUp(mEvent.at(21).shift().build());
        mSelection.assertRangeSelection(7, 21);
    }

    @Test
    public void testMultipleContiguousRanges() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());
        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());

        // click without shift sets a new range start point.
        mInputHandler.onSingleTapUp(mEvent.at(20).unshift().notOverDocIcon().build());
        mInputHandler.onSingleTapConfirmed(mEvent.at(20).notOverDocIcon().build());
        mFocusHandler.focusPos = 20;
        mInputHandler.onSingleTapUp(mEvent.at(25).shift().notOverDocIcon().build());
        mInputHandler.onSingleTapConfirmed(mEvent.at(25).shift().notOverDocIcon().build());

        mSelection.assertRangeNotSelected(7, 11);
        mSelection.assertRangeSelected(20, 25);
        mSelection.assertSelectionSize(6);
    }

    @Test
    public void testReducesSelectionRange() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());
        mInputHandler.onSingleTapUp(mEvent.at(17).shift().build());
        mInputHandler.onSingleTapUp(mEvent.at(10).shift().build());
        mSelection.assertRangeSelection(7, 10);
    }

    @Test
    public void testReducesSelectionRange_Reverse() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(17).build());
        mInputHandler.onSingleTapUp(mEvent.at(7).shift().build());
        mInputHandler.onSingleTapUp(mEvent.at(14).shift().build());
        mSelection.assertRangeSelection(14, 17);
    }

    @Test
    public void testExtendsRange_Reverse() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(12).build());
        mInputHandler.onSingleTapUp(mEvent.at(5).shift().build());
        mSelection.assertRangeSelection(5, 12);
    }

    @Test
    public void testExtendsRange_ReversesAfterForwardClick() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());
        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mInputHandler.onSingleTapUp(mEvent.at(0).shift().build());
        mSelection.assertRangeSelection(0, 7);
    }

    @Test
    public void testRightClickEstablishesRange() {

        TestEvent fistClick = mEvent.at(7).secondary().build();
        mInputHandler.onDown(fistClick);
        // This next method call simulates the behavior of the system event dispatch code.
        // UserInputHandler depends on a specific sequence of events for internal
        // state to remain valid. It's not an awesome arrangement, but it is currently
        // necessary.
        //
        // See: UserInputHandler.MouseDelegate#mHandledOnDown;
        mInputHandler.onSingleTapUp(fistClick);

        // Now we can send a subsequent event that should extend selection.
        TestEvent secondClick = mEvent.at(11).primary().shift().build();
        mInputHandler.onDown(secondClick);
        mInputHandler.onSingleTapUp(secondClick);

        mSelection.assertRangeSelection(7, 11);
    }
}
