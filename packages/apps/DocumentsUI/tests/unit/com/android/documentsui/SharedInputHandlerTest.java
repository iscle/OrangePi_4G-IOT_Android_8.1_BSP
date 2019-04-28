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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.documentsui.base.Procedure;
import com.android.documentsui.dirlist.TestFocusHandler;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestFeatures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SharedInputHandlerTest {

    private SharedInputHandler mSharedInputHandler;
    private SelectionManager mSelectionMgr = SelectionManagers.createTestInstance();
    private TestFeatures mFeatures = new TestFeatures();
    private TestFocusHandler mFocusHandler = new TestFocusHandler();
    private boolean mDirPopHappened;
    private boolean mCanceledSearch;
    private Procedure mDirPopper = new Procedure() {
        @Override
        public boolean run() {
            mDirPopHappened = true;
            return true;
        }
    };

    @Before
    public void setUp() {
        mDirPopHappened = false;
        mSharedInputHandler = new SharedInputHandler(
                mFocusHandler,
                mSelectionMgr,
                () -> {
                    return false;
                },
                mDirPopper,
                mFeatures);
    }

    @Test
    public void testUnrelatedButton_DoesNothing() {
        KeyEvent event =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0);
        assertFalse(mSharedInputHandler.onKeyDown(event.getKeyCode(), event));
    }

    @Test
    public void testBackButton_CancelsSearch() {
        mSelectionMgr.toggleSelection("1");
        mSharedInputHandler = new SharedInputHandler(
                new TestFocusHandler(),
                SelectionManagers.createTestInstance(),
                () -> {
                        mCanceledSearch = true;
                        return true;
                },
                mDirPopper,
                new TestFeatures());
        KeyEvent backEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(backEvent.getKeyCode(), backEvent));

        assertTrue(mCanceledSearch);
        assertEquals(mSelectionMgr.getSelection().size(), 1);
        assertFalse(mDirPopHappened);
    }

    @Test
    public void testBackButton_ClearsSelection() {
        mSelectionMgr.toggleSelection("1");
        assertEquals(mSelectionMgr.getSelection().size(), 1);
        KeyEvent backEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(backEvent.getKeyCode(), backEvent));

        assertFalse(mCanceledSearch);
        assertEquals(mSelectionMgr.getSelection().size(), 0);
        assertFalse(mDirPopHappened);
    }

    @Test
    public void testBackButton_PopsDirectory() {
        KeyEvent backEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(backEvent.getKeyCode(), backEvent));

        assertFalse(mCanceledSearch);
        assertEquals(mSelectionMgr.getSelection().size(), 0);
        assertTrue(mDirPopHappened);
    }

    @Test
    public void testEscButton_CancelsSearch() {
        mSelectionMgr.toggleSelection("1");
        mSharedInputHandler = new SharedInputHandler(
                new TestFocusHandler(),
                SelectionManagers.createTestInstance(),
                () -> {
                        mCanceledSearch = true;
                        return true;
                },
                mDirPopper,
                new TestFeatures());
        KeyEvent escapeEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(escapeEvent.getKeyCode(), escapeEvent));

        assertTrue(mCanceledSearch);
        assertEquals(mSelectionMgr.getSelection().size(), 1);
        assertFalse(mDirPopHappened);
    }

    @Test
    public void testEscButton_ClearsSelection() {
        mSelectionMgr.toggleSelection("1");
        assertEquals(mSelectionMgr.getSelection().size(), 1);
        KeyEvent escapeEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(escapeEvent.getKeyCode(), escapeEvent));

        assertFalse(mCanceledSearch);
        assertEquals(mSelectionMgr.getSelection().size(), 0);
        assertFalse(mDirPopHappened);
    }

    @Test
    public void testEscButton_DoesNotPopDirectory() {
        KeyEvent escapeEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(escapeEvent.getKeyCode(), escapeEvent));

        assertFalse(mCanceledSearch);
        assertEquals(mSelectionMgr.getSelection().size(), 0);
        assertFalse(mDirPopHappened);
    }

    @Test
    public void testDeleteButton_PopsDirectory() {
        KeyEvent delEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(delEvent.getKeyCode(), delEvent));

        assertTrue(mDirPopHappened);
    }

    @Test
    public void testTab_AdvancesFocusArea() {
        mFeatures.systemKeyboardNavigation = false;
        KeyEvent tabEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(tabEvent.getKeyCode(), tabEvent));

        assertTrue(mFocusHandler.advanceFocusAreaCalled);
    }

    @Test
    public void testNavKey_FocusesDirectory() {
        mFeatures.systemKeyboardNavigation = false;
        KeyEvent navEvent =
                new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0, 0);
        assertTrue(mSharedInputHandler.onKeyDown(navEvent.getKeyCode(), navEvent));

        assertTrue(mFocusHandler.focusDirectoryCalled);
    }
}
