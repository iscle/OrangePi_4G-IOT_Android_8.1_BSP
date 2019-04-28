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
import android.view.KeyEvent;
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
public final class UserInputHandler_KeyboardTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler<TestEvent> mInputHandler;
    private TestActionHandler mActionHandler;
    private TestFocusHandler mFocusHandler;
    private SelectionProbe mSelection;

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
        mSelection = new SelectionProbe(selectionMgr);
        mFocusHandler = new TestFocusHandler();
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
    public void testArrowKey_nonShiftClearsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).build());
        mSelection.assertSelection(11);

        mFocusHandler.handleKey = true;
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP);
        mInputHandler.onKey(null, event.getKeyCode(), event);

        mSelection.assertNoSelection();
    }
}