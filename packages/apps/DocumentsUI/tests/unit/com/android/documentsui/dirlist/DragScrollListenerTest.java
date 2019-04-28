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

import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.DragEvent;
import android.view.View;

import com.android.documentsui.ItemDragListener;
import com.android.documentsui.testing.DragEvents;
import com.android.documentsui.testing.TestTimer;
import com.android.documentsui.testing.Views;
import com.android.documentsui.ui.ViewAutoScroller.ScrollActionDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Timer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DragScrollListenerTest {

    private static final int VIEW_HEIGHT = 100;
    private static final int TOP_Y_POINT = 0;
    private static final int BOTTOM_Y_POINT = VIEW_HEIGHT;

    private View mTestView;
    private TestDragHost mTestDragHost;
    private TestDragHandler mDragHandler;
    private TestScrollActionDelegate mActionDelegate = new TestScrollActionDelegate();
    private DragHoverListener mListener;
    private boolean mCanScrollUp;
    private boolean mCanScrollDown;

    @Before
    public void setUp() {
        mTestView = Views.createTestView(0, 0);
        mTestDragHost = new TestDragHost();
        mDragHandler = new TestDragHandler(mTestDragHost);
        mListener = new DragHoverListener(
                mDragHandler,
                () -> VIEW_HEIGHT,
                view -> (view == mTestView),
                () -> mCanScrollUp,
                () -> mCanScrollDown,
                mActionDelegate);
        mCanScrollUp = true;
        mCanScrollDown = true;
    }

    @Test
    public void testDragEvent_DelegateToHandler() {
        triggerDragEvent(DragEvent.ACTION_DRAG_STARTED);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_STARTED);

        triggerDragEvent(DragEvent.ACTION_DROP);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DROP);

        triggerDragEvent(DragEvent.ACTION_DRAG_ENDED);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_ENDED);

        triggerDragEvent(DragEvent.ACTION_DRAG_EXITED);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_EXITED);
    }

    @Test
    public void testDragLocationEvent_DelegateToHandler() {
        triggerDragEvent(DragEvent.ACTION_DRAG_STARTED);

        // Not in hotspot
        triggerDragLocationEvent(0, VIEW_HEIGHT / 2);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_LOCATION);

        // Can't scroll up
        mCanScrollUp = false;
        mCanScrollDown = true;
        triggerDragLocationEvent(0, TOP_Y_POINT);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_LOCATION);

        // Can't scroll Down
        mCanScrollDown = false;
        mCanScrollUp = true;
        triggerDragLocationEvent(0, BOTTOM_Y_POINT);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_LOCATION);

        triggerDragLocationEvent(0, TOP_Y_POINT);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_LOCATION);

        triggerDragLocationEvent(0, BOTTOM_Y_POINT);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_LOCATION);

        triggerDragEvent(DragEvent.ACTION_DRAG_ENDED);
    }

    @Test
    public void testDragEnterEvent_DelegateToHandler() {
        triggerDragEvent(DragEvent.ACTION_DRAG_STARTED);

        // Location Event always precedes Entered event
        triggerDragLocationEvent(0, 50);
        // If not in the hotspot, we don't want to trap the event
        triggerDragEvent(DragEvent.ACTION_DRAG_ENTERED);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_ENTERED);

        // Can't scroll up
        mCanScrollUp = false;
        mCanScrollDown = true;
        triggerDragLocationEvent(0, TOP_Y_POINT);
        triggerDragEvent(DragEvent.ACTION_DRAG_ENTERED);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_ENTERED);

        // Can't scroll Down
        mCanScrollDown = false;
        mCanScrollUp = true;
        triggerDragLocationEvent(0, BOTTOM_Y_POINT);
        triggerDragEvent(DragEvent.ACTION_DRAG_ENTERED);
        assertTrue(mDragHandler.mLastDropEvent.getAction() == DragEvent.ACTION_DRAG_ENTERED);

        triggerDragEvent(DragEvent.ACTION_DRAG_ENDED);
    }

    // A correct Auto-scroll happens in the sequence of:
    // Started -> LocationChanged -> Scroll -> Enter -> Exit
    // This test to make sure scroll actually happens in the right direction given correct sequence
    @Test
    public void testActualDragScrolldEvents() {
        triggerDragEvent(DragEvent.ACTION_DRAG_STARTED);

        triggerDragLocationEvent(0, TOP_Y_POINT);
        triggerDragEvent(DragEvent.ACTION_DRAG_ENTERED);
        mActionDelegate.assertScrollNegative();

        triggerDragLocationEvent(0, BOTTOM_Y_POINT);
        triggerDragEvent(DragEvent.ACTION_DRAG_ENTERED);

        triggerDragEvent(DragEvent.ACTION_DRAG_ENDED);
        mActionDelegate.assertScrollPositive();
    }

    protected boolean triggerDragEvent(int actionId) {
        final DragEvent testEvent = DragEvents.createTestDragEvent(actionId);

        return mListener.onDrag(mTestView, testEvent);
    }

    protected boolean triggerDragLocationEvent(float x, float y) {
        final DragEvent testEvent = DragEvents.createTestLocationEvent(x, y);

        return mListener.onDrag(mTestView, testEvent);
    }

    private static class TestDragHandler extends ItemDragListener<TestDragHost> {

        private DragEvent mLastDropEvent;

        protected TestDragHandler(TestDragHost dragHost) {
            super(dragHost);
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            mLastDropEvent = event;
            return true;
        }
    }

    private static class TestDragHost implements ItemDragListener.DragHost {

        @Override
        public void setDropTargetHighlight(View v, boolean highlight) {}

        @Override
        public void runOnUiThread(Runnable runnable) {}

        @Override
        public void onViewHovered(View v) {}

        @Override
        public void onDragEntered(View v) {}

        @Override
        public void onDragExited(View v) {}

        @Override
        public void onDragEnded() {}
    }

    private class TestScrollActionDelegate implements ScrollActionDelegate {

        private int mDy;

        @Override
        public void scrollBy(int dy) {
            mDy = dy;
        }

        @Override
        public void runAtNextFrame(Runnable r) {
        }

        @Override
        public void removeCallback(Runnable r) {
        }

        public void assertScrollPositive() {
            assertTrue("actual: " + mDy, mDy > 0);
        }

        public void assertScrollNegative() {
            assertTrue("actual: " + mDy, mDy < 0);
        }
    };
}
