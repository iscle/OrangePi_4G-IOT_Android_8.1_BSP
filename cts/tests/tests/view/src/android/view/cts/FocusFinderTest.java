/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.FocusFinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FocusFinderTest {
    private FocusFinder mFocusFinder;
    private ViewGroup mLayout;
    private Button mTopLeft;
    private Button mTopRight;
    private Button mBottomLeft;
    private Button mBottomRight;

    @Rule
    public ActivityTestRule<FocusFinderCtsActivity> mActivityRule =
            new ActivityTestRule<>(FocusFinderCtsActivity.class);

    @Before
    public void setup() {
        FocusFinderCtsActivity activity = mActivityRule.getActivity();

        mFocusFinder = FocusFinder.getInstance();
        mLayout = activity.layout;
        mTopLeft = activity.topLeftButton;
        mTopRight = activity.topRightButton;
        mBottomLeft = activity.bottomLeftButton;
        mBottomRight = activity.bottomRightButton;
        mTopLeft.setNextFocusLeftId(View.NO_ID);
        mTopRight.setNextFocusLeftId(View.NO_ID);
        mBottomLeft.setNextFocusLeftId(View.NO_ID);
        mBottomRight.setNextFocusLeftId(View.NO_ID);
    }

    @Test
    public void testGetInstance() {
        assertNotNull(mFocusFinder);
    }

    @Test
    public void testFindNextFocus() throws Throwable {
        /*
         * Go clockwise around the buttons from the top left searching for focus.
         *
         * +---+---+
         * | 1 | 2 |
         * +---+---+
         * | 3 | 4 |
         * +---+---+
         */
        verifyNextFocus(mTopLeft, View.FOCUS_RIGHT, mTopRight);
        verifyNextFocus(mTopRight, View.FOCUS_DOWN, mBottomRight);
        verifyNextFocus(mBottomRight, View.FOCUS_LEFT, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_UP, mTopLeft);

        verifyNextFocus(null, View.FOCUS_RIGHT, mTopLeft);
        verifyNextFocus(null, View.FOCUS_DOWN, mTopLeft);
        verifyNextFocus(null, View.FOCUS_LEFT, mBottomRight);
        verifyNextFocus(null, View.FOCUS_UP, mBottomRight);

        // Check that left/right traversal works when top/bottom borders are equal.
        verifyNextFocus(mTopRight, View.FOCUS_LEFT, mTopLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_RIGHT, mBottomRight);

        // Edge-case where root has focus
        mActivityRule.runOnUiThread(() -> {
            mLayout.setFocusableInTouchMode(true);
            verifyNextFocus(mLayout, View.FOCUS_FORWARD, mTopLeft);
        });
    }

    private void verifyNextFocus(View currentFocus, int direction, View expectedNextFocus) {
        View actualNextFocus = mFocusFinder.findNextFocus(mLayout, currentFocus, direction);
        assertEquals(expectedNextFocus, actualNextFocus);
    }

    @Test
    public void testFindNextFocusFromRect() {
        /*
         * Create a small rectangle on the border between the top left and top right buttons.
         *
         * +---+---+
         * |  [ ]  |
         * +---+---+
         * |   |   |
         * +---+---+
         */
        Rect rect = new Rect();
        mTopLeft.getDrawingRect(rect);
        rect.offset(mTopLeft.getWidth() / 2, 0);
        rect.inset(mTopLeft.getWidth() / 4, mTopLeft.getHeight() / 4);

        verifytNextFocusFromRect(rect, View.FOCUS_LEFT, mTopLeft);
        verifytNextFocusFromRect(rect, View.FOCUS_RIGHT, mTopRight);

        /*
         * Create a small rectangle on the border between the top left and bottom left buttons.
         *
         * +---+---+
         * |   |   |
         * +[ ]+---+
         * |   |   |
         * +---+---+
         */
        mTopLeft.getDrawingRect(rect);
        rect.offset(0, mTopRight.getHeight() / 2);
        rect.inset(mTopLeft.getWidth() / 4, mTopLeft.getHeight() / 4);

        verifytNextFocusFromRect(rect, View.FOCUS_UP, mTopLeft);
        verifytNextFocusFromRect(rect, View.FOCUS_DOWN, mBottomLeft);
    }

    private void verifytNextFocusFromRect(Rect rect, int direction, View expectedNextFocus) {
        View actualNextFocus = mFocusFinder.findNextFocusFromRect(mLayout, rect, direction);
        assertEquals(expectedNextFocus, actualNextFocus);
    }

    @Test
    public void testFindNearestTouchable() {
        /*
         * Table layout with two rows and coordinates are relative to those parent rows.
         * Lines outside the box signify touch points used in the tests.
         *      |
         *   +---+---+
         *   | 1 | 2 |--
         *   +---+---+
         * --| 3 | 4 |
         *   +---+---+
         *         |
         */

        // 1
        int x = mTopLeft.getWidth() / 2 - 5;
        int y = 0;
        int[] deltas = new int[2];
        View view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_DOWN, deltas);
        assertEquals(mTopLeft, view);
        assertEquals(0, deltas[0]);
        assertEquals(0, deltas[1]);

        // 2
        deltas = new int[2];
        x = mTopRight.getRight();
        y = mTopRight.getBottom() / 2;
        view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_LEFT, deltas);
        assertEquals(mTopRight, view);
        assertEquals(-1, deltas[0]);
        assertEquals(0, deltas[1]);

        // 3
        deltas = new int[2];
        x = 0;
        y = mTopLeft.getBottom() + mBottomLeft.getHeight() / 2;
        view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_RIGHT, deltas);
        assertEquals(mBottomLeft, view);
        assertEquals(0, deltas[0]);
        assertEquals(0, deltas[1]);

        // 4
        deltas = new int[2];
        x = mBottomRight.getRight();
        y = mTopRight.getBottom() + mBottomRight.getBottom();
        view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_UP, deltas);
        assertEquals(mBottomRight, view);
        assertEquals(0, deltas[0]);
        assertEquals(-1, deltas[1]);
    }

    @Test
    public void testFindNextAndPrevFocusAvoidingChain() {
        mBottomRight.setNextFocusForwardId(mBottomLeft.getId());
        mBottomLeft.setNextFocusForwardId(mTopRight.getId());
        // Follow the chain
        verifyNextFocus(mBottomRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mTopRight, View.FOCUS_BACKWARD, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_BACKWARD, mBottomRight);

        // Now go to the one not in the chain
        verifyNextFocus(mTopRight, View.FOCUS_FORWARD, mTopLeft);
        verifyNextFocus(mBottomRight, View.FOCUS_BACKWARD, mTopLeft);

        // Now go back to the top of the chain
        verifyNextFocus(mTopLeft, View.FOCUS_FORWARD, mBottomRight);
        verifyNextFocus(mTopLeft, View.FOCUS_BACKWARD, mTopRight);

        // Now make the chain a circle -- this is the pathological case
        mTopRight.setNextFocusForwardId(mBottomRight.getId());
        // Fall back to the next one in a chain.
        verifyNextFocus(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mTopLeft, View.FOCUS_BACKWARD, mBottomRight);

        //Now do branching focus changes
        mTopRight.setNextFocusForwardId(View.NO_ID);
        mBottomRight.setNextFocusForwardId(mTopRight.getId());
        verifyNextFocus(mBottomRight, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mBottomLeft, View.FOCUS_FORWARD, mTopRight);
        // From the tail, it jumps out of the chain
        verifyNextFocus(mTopRight, View.FOCUS_FORWARD, mTopLeft);

        // Back from the head of a tree goes out of the tree
        // We don't know which is the head of the focus chain since it is branching.
        View prevFocus1 = mFocusFinder.findNextFocus(mLayout, mBottomLeft, View.FOCUS_BACKWARD);
        View prevFocus2 = mFocusFinder.findNextFocus(mLayout, mBottomRight, View.FOCUS_BACKWARD);
        assertTrue(prevFocus1 == mTopLeft || prevFocus2 == mTopLeft);

        // From outside, it chooses an arbitrary head of the chain
        View nextFocus = mFocusFinder.findNextFocus(mLayout, mTopLeft, View.FOCUS_FORWARD);
        assertTrue(nextFocus == mBottomRight || nextFocus == mBottomLeft);

        // Going back from the tail of the split chain, it chooses an arbitrary head
        nextFocus = mFocusFinder.findNextFocus(mLayout, mTopRight, View.FOCUS_BACKWARD);
        assertTrue(nextFocus == mBottomRight || nextFocus == mBottomLeft);
    }

    @Test(timeout = 500)
    public void testChainVisibility() {
        mBottomRight.setNextFocusForwardId(mBottomLeft.getId());
        mBottomLeft.setNextFocusForwardId(mTopRight.getId());
        mBottomLeft.setVisibility(View.INVISIBLE);
        View next = mFocusFinder.findNextFocus(mLayout, mBottomRight, View.FOCUS_FORWARD);
        assertSame(mTopRight, next);

        mBottomLeft.setNextFocusForwardId(View.NO_ID);
        next = mFocusFinder.findNextFocus(mLayout, mBottomRight, View.FOCUS_FORWARD);
        assertSame(mTopLeft, next);

        // This shouldn't go into an infinite loop
        mBottomRight.setNextFocusForwardId(mTopRight.getId());
        mTopLeft.setNextFocusForwardId(mTopRight.getId());
        mTopRight.setNextFocusForwardId(mBottomLeft.getId());
        mBottomLeft.setNextFocusForwardId(mTopLeft.getId());
        mActivityRule.getActivity().runOnUiThread(() -> {
            mTopLeft.setVisibility(View.INVISIBLE);
            mTopRight.setVisibility(View.INVISIBLE);
            mBottomLeft.setVisibility(View.INVISIBLE);
            mBottomRight.setVisibility(View.INVISIBLE);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFocusFinder.findNextFocus(mLayout, mBottomRight, View.FOCUS_FORWARD);
    }

    private void verifyNextCluster(View currentCluster, int direction, View expectedNextCluster) {
        View actualNextCluster = mFocusFinder.findNextKeyboardNavigationCluster(
                mLayout, currentCluster, direction);
        assertEquals(expectedNextCluster, actualNextCluster);
    }

    private void verifyNextClusterView(View currentCluster, int direction, View expectedNextView) {
        View actualNextView = mFocusFinder.findNextKeyboardNavigationCluster(
                mLayout, currentCluster, direction);
        if (actualNextView == mLayout) {
            actualNextView =
                    mFocusFinder.findNextKeyboardNavigationCluster(mLayout, null, direction);
        }
        assertEquals(expectedNextView, actualNextView);
    }

    @Test
    public void testNoClusters() {
        // No views are marked as clusters, so next cluster is always null.
        verifyNextCluster(mTopRight, View.FOCUS_FORWARD, null);
        verifyNextCluster(mTopRight, View.FOCUS_BACKWARD, null);
    }

    @Test
    public void testFindNextCluster() {
        // Cluster navigation from all possible starting points in all directions.
        mTopLeft.setKeyboardNavigationCluster(true);
        mTopRight.setKeyboardNavigationCluster(true);
        mBottomLeft.setKeyboardNavigationCluster(true);

        verifyNextCluster(null, View.FOCUS_FORWARD, mTopLeft);
        verifyNextCluster(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextCluster(mTopRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextCluster(mBottomLeft, View.FOCUS_FORWARD, mLayout);
        verifyNextCluster(mBottomRight, View.FOCUS_FORWARD, mLayout);

        verifyNextCluster(null, View.FOCUS_BACKWARD, mBottomLeft);
        verifyNextCluster(mTopLeft, View.FOCUS_BACKWARD, mLayout);
        verifyNextCluster(mTopRight, View.FOCUS_BACKWARD, mTopLeft);
        verifyNextCluster(mBottomLeft, View.FOCUS_BACKWARD, mTopRight);
        verifyNextCluster(mBottomRight, View.FOCUS_BACKWARD, mLayout);
    }

    @Test
    public void testFindNextAndPrevClusterAvoidingChain() {
        // Basically a duplicate of normal focus test above. The same logic should be used for both.
        mTopLeft.setKeyboardNavigationCluster(true);
        mTopRight.setKeyboardNavigationCluster(true);
        mBottomLeft.setKeyboardNavigationCluster(true);
        mBottomRight.setKeyboardNavigationCluster(true);
        mBottomRight.setNextClusterForwardId(mBottomLeft.getId());
        mBottomLeft.setNextClusterForwardId(mTopRight.getId());
        // Follow the chain
        verifyNextCluster(mBottomRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextCluster(mBottomLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextCluster(mTopRight, View.FOCUS_BACKWARD, mBottomLeft);
        verifyNextCluster(mBottomLeft, View.FOCUS_BACKWARD, mBottomRight);

        // Now go to the one not in the chain
        verifyNextClusterView(mTopRight, View.FOCUS_FORWARD, mTopLeft);
        verifyNextClusterView(mBottomRight, View.FOCUS_BACKWARD, mTopLeft);

        // Now go back to the top of the chain
        verifyNextClusterView(mTopLeft, View.FOCUS_FORWARD, mBottomRight);
        verifyNextClusterView(mTopLeft, View.FOCUS_BACKWARD, mTopRight);

        // Now make the chain a circle -- this is the pathological case
        mTopRight.setNextClusterForwardId(mBottomRight.getId());
        // Fall back to the next one in a chain.
        verifyNextClusterView(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextClusterView(mTopLeft, View.FOCUS_BACKWARD, mBottomRight);

        //Now do branching focus changes
        mTopRight.setNextClusterForwardId(View.NO_ID);
        mBottomRight.setNextClusterForwardId(mTopRight.getId());
        assertEquals(mBottomRight.getNextClusterForwardId(), mTopRight.getId());
        verifyNextClusterView(mBottomRight, View.FOCUS_FORWARD, mTopRight);
        verifyNextClusterView(mBottomLeft, View.FOCUS_FORWARD, mTopRight);
        // From the tail, it jumps out of the chain
        verifyNextClusterView(mTopRight, View.FOCUS_FORWARD, mTopLeft);

        // Back from the head of a tree goes out of the tree
        // We don't know which is the head of the focus chain since it is branching.
        View prevFocus1 = mFocusFinder.findNextKeyboardNavigationCluster(mLayout, mBottomLeft,
                View.FOCUS_BACKWARD);
        View prevFocus2 = mFocusFinder.findNextKeyboardNavigationCluster(mLayout, mBottomRight,
                View.FOCUS_BACKWARD);
        assertTrue(prevFocus1 == mTopLeft || prevFocus2 == mTopLeft);

        // From outside, it chooses an arbitrary head of the chain
        View nextFocus = mFocusFinder.findNextKeyboardNavigationCluster(mLayout, mTopLeft,
                View.FOCUS_FORWARD);
        assertTrue(nextFocus == mBottomRight || nextFocus == mBottomLeft);

        // Going back from the tail of the split chain, it chooses an arbitrary head
        nextFocus = mFocusFinder.findNextKeyboardNavigationCluster(mLayout, mTopRight,
                View.FOCUS_BACKWARD);
        assertTrue(nextFocus == mBottomRight || nextFocus == mBottomLeft);
    }

    @Test
    public void testDuplicateId() throws Throwable {
        LayoutInflater inflater = mActivityRule.getActivity().getLayoutInflater();
        mLayout = (ViewGroup) mActivityRule.getActivity().findViewById(R.id.inflate_layout);
        View[] buttons = new View[3];
        View[] boxes = new View[3];
        mActivityRule.runOnUiThread(() -> {
            for (int i = 0; i < 3; ++i) {
                View item = inflater.inflate(R.layout.focus_finder_sublayout, mLayout, false);
                buttons[i] = item.findViewById(R.id.itembutton);
                boxes[i] = item.findViewById(R.id.itembox);
                mLayout.addView(item);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verifyNextFocus(buttons[0], View.FOCUS_FORWARD, boxes[0]);
        verifyNextFocus(boxes[0], View.FOCUS_FORWARD, buttons[1]);
        verifyNextFocus(buttons[1], View.FOCUS_FORWARD, boxes[1]);
        verifyNextFocus(boxes[1], View.FOCUS_FORWARD, buttons[2]);
    }

    @Test
    public void testBasicFocusOrder() {
        // Sanity check to make sure sorter is behaving
        FrameLayout layout = new FrameLayout(mLayout.getContext());
        Button button1 = new Button(mLayout.getContext());
        Button button2 = new Button(mLayout.getContext());
        setViewBox(button1, 0, 0, 10, 10);
        setViewBox(button2, 0, 0, 10, 10);
        layout.addView(button1);
        layout.addView(button2);
        View[] views = new View[]{button2, button1};
        // empty shouldn't crash or anything
        FocusFinder.sort(views, 0, 0, layout, false);
        // one view should work
        FocusFinder.sort(views, 0, 1, layout, false);
        assertEquals(button2, views[0]);
        // exactly overlapping views should remain in original order
        FocusFinder.sort(views, 0, 2, layout, false);
        assertEquals(button2, views[0]);
        assertEquals(button1, views[1]);
        // make sure it will actually mutate input array.
        setViewBox(button2, 20, 0, 30, 10);
        FocusFinder.sort(views, 0, 2, layout, false);
        assertEquals(button1, views[0]);
        assertEquals(button2, views[1]);

        // While we don't want to test details, we should at least verify basic correctness
        // like "left-to-right" ordering in well-behaved layouts
        assertEquals(mLayout.findFocus(), mTopLeft);
        verifyNextFocus(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mTopRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_FORWARD, mBottomRight);

        // Should still work intuitively even if some views are slightly shorter.
        mBottomLeft.setBottom(mBottomLeft.getBottom() - 3);
        mBottomLeft.offsetTopAndBottom(3);
        verifyNextFocus(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mTopRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_FORWARD, mBottomRight);

        // RTL layout should work right-to-left
        mActivityRule.getActivity().runOnUiThread(
                () -> mLayout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verifyNextFocus(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mTopRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_FORWARD, mBottomRight);
    }

    private void setViewBox(View view, int left, int top, int right, int bottom) {
        view.setLeft(left);
        view.setTop(top);
        view.setRight(right);
        view.setBottom(bottom);
    }
}
