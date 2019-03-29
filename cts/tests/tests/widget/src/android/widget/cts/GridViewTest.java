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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ViewAsserts;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.GridLayoutAnimationController.AnimationParameters;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link GridView}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class GridViewTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private GridView mGridView;

    @Rule
    public ActivityTestRule<GridViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(GridViewCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mGridView = (GridView) mActivity.findViewById(R.id.gridview);

        PollingCheck.waitFor(mActivity::hasWindowFocus);
    }

    @Test
    public void testConstructor() {
        new GridView(mActivity);

        new GridView(mActivity, null);

        new GridView(mActivity, null, android.R.attr.gridViewStyle);

        new GridView(mActivity, null, 0, android.R.style.Widget_DeviceDefault_GridView);

        new GridView(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_GridView);

        new GridView(mActivity, null, 0, android.R.style.Widget_Material_GridView);

        new GridView(mActivity, null, 0, android.R.style.Widget_Material_Light_GridView);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.gridview_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new GridView(mActivity, attrs);
        new GridView(mActivity, attrs, 0);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext1() {
        new GridView(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext2() {
        new GridView(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext3() {
        new GridView(null, null, 0);
    }

    @UiThreadTest
    @Test
    public void testAccessAdapter() {
        // set Adapter
        ImageAdapter adapter = new ImageAdapter(mActivity);
        mGridView.setAdapter(adapter);
        assertSame(adapter, mGridView.getAdapter());

        mGridView.setAdapter(null);
        assertNull(mGridView.getAdapter());
    }

    @UiThreadTest
    @Test
    public void testSetSelection() {
        mGridView.setSelection(0);
        assertEquals(0, mGridView.getSelectedItemPosition());

        mGridView.setSelection(-1);
        assertEquals(-1, mGridView.getSelectedItemPosition());

        mGridView.setSelection(mGridView.getCount());
        assertEquals(mGridView.getCount(), mGridView.getSelectedItemPosition());
    }

    @Test
    public void testPressKey() throws Throwable {
        final int NUM_COLUMNS = 3;

        GridView.OnItemClickListener mockItemClickListener =
                mock(GridView.OnItemClickListener.class);
        mGridView.setOnItemClickListener(mockItemClickListener);

        // this test case can not be ran in UI thread.
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new ImageAdapter(mActivity));
            mGridView.setNumColumns(NUM_COLUMNS);
            mGridView.invalidate();
            mGridView.requestLayout();
            mGridView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(0, mGridView.getSelectedItemPosition());
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
        mInstrumentation.sendKeySync(event);
        assertEquals(1, mGridView.getSelectedItemPosition());

        event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT);
        mInstrumentation.sendKeySync(event);
        assertEquals(0, mGridView.getSelectedItemPosition());

        assertEquals(0, mGridView.getSelectedItemPosition());
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, mGridView.getSelectedItemPosition());

        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, mGridView.getSelectedItemPosition());

        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        assertEquals(1, mGridView.getSelectedItemPosition());

        event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0,
                KeyEvent.META_SHIFT_LEFT_ON);
        mInstrumentation.sendKeySync(event);
        event = new KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0,
                KeyEvent.META_SHIFT_LEFT_ON);
        mInstrumentation.sendKeySync(event);
        assertEquals(0, mGridView.getSelectedItemPosition());

        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(NUM_COLUMNS, mGridView.getSelectedItemPosition());

        verify(mockItemClickListener, never()).onItemClick(any(AdapterView.class), any(View.class),
                anyInt(), anyLong());
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        verify(mockItemClickListener, times(1)).onItemClick(eq(mGridView), any(View.class),
                eq(NUM_COLUMNS), eq((long) NUM_COLUMNS));

        reset(mockItemClickListener);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        verify(mockItemClickListener, times(1)).onItemClick(eq(mGridView), any(View.class),
                eq(NUM_COLUMNS), eq((long) NUM_COLUMNS));
    }

    @Test
    public void testSetGravity() throws Throwable {
        final int NUM_COLUMNS = 1;
        // this test case can not be ran in UI thread.
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new ImageAdapter(mActivity));
            mGridView.setNumColumns(NUM_COLUMNS);
            mGridView.setHorizontalSpacing(0);
            mGridView.setVerticalSpacing(0);
        });
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
            mGridView.setGravity(Gravity.CENTER_HORIZONTAL);
            mGridView.invalidate();
            mGridView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(Gravity.CENTER_HORIZONTAL, mGridView.getGravity());
        ViewAsserts.assertHorizontalCenterAligned(mGridView, mGridView.getChildAt(0));

        mActivityRule.runOnUiThread(() -> {
            mGridView.setGravity(Gravity.LEFT);
            mGridView.invalidate();
            mGridView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(Gravity.LEFT, mGridView.getGravity());
        ViewAsserts.assertLeftAligned(mGridView, mGridView.getChildAt(0),
                mGridView.getListPaddingLeft());

        mActivityRule.runOnUiThread(() -> {
            mGridView.setGravity(Gravity.RIGHT);
            mGridView.invalidate();
            mGridView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(Gravity.RIGHT, mGridView.getGravity());
        ViewAsserts.assertRightAligned(mGridView, mGridView.getChildAt(0),
                mGridView.getListPaddingRight());
    }

    @Test
    public void testAccessHorizontalSpacing() throws Throwable {
        verifyAccessHorizontalSpacing(View.LAYOUT_DIRECTION_LTR);
    }

    @Test
    public void testAccessHorizontalSpacingRTL() throws Throwable {
        verifyAccessHorizontalSpacing(View.LAYOUT_DIRECTION_RTL);
    }

    private void verifyAccessHorizontalSpacing(final int layoutDir) throws Throwable {
        mActivityRule.runOnUiThread(() -> mGridView.setLayoutDirection(layoutDir));
        mGridView.setStretchMode(GridView.NO_STRETCH);
        // Number of columns should be big enough, otherwise the
        // horizontal spacing cannot be correctly verified.
        mGridView.setNumColumns(28);

        mActivityRule.runOnUiThread(() ->  {
            mGridView.setAdapter(new MockGridViewAdapter(3));
            mGridView.setHorizontalSpacing(0);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(0, mGridView.getRequestedHorizontalSpacing());
        assertEquals(0, mGridView.getHorizontalSpacing());
        View child0 = mGridView.getChildAt(0);
        View child1 = mGridView.getChildAt(1);
        if (layoutDir == View.LAYOUT_DIRECTION_LTR) {
            assertEquals(0, child1.getLeft() - child0.getRight());
        } else {
            assertEquals(0, child0.getLeft() - child1.getRight());
        }

        mActivityRule.runOnUiThread(() -> mGridView.setHorizontalSpacing(5));
        mInstrumentation.waitForIdleSync();

        assertEquals(5, mGridView.getRequestedHorizontalSpacing());
        assertEquals(5, mGridView.getHorizontalSpacing());
        child0 = mGridView.getChildAt(0);
        child1 = mGridView.getChildAt(1);
        if (layoutDir == View.LAYOUT_DIRECTION_LTR) {
            assertEquals(5, child1.getLeft() - child0.getRight());
        } else {
            assertEquals(5, child0.getLeft() - child1.getRight());
        }
    }

    @Test
    public void testAccessVerticalSpacing() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new MockGridViewAdapter(3));
            mGridView.setVerticalSpacing(0);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(0, mGridView.getVerticalSpacing());
        View child0 = mGridView.getChildAt(0);
        View child1 = mGridView.getChildAt(1);
        assertEquals(0, child1.getTop() - child0.getBottom());

        mActivityRule.runOnUiThread(() -> mGridView.setVerticalSpacing(5));
        mInstrumentation.waitForIdleSync();

        assertEquals(5, mGridView.getVerticalSpacing());
        child0 = mGridView.getChildAt(0);
        child1 = mGridView.getChildAt(1);
        assertEquals(5, child1.getTop() - child0.getBottom());
    }

    @Test
    public void testAccessStretchMode() throws Throwable {
        View child;

        final int NUM_COLUMNS = 8;
        // this test case can not be ran in UI thread.
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new ImageAdapter(mActivity));
            mGridView.setColumnWidth(10);
            mGridView.setNumColumns(NUM_COLUMNS);
            mGridView.setHorizontalSpacing(0);
            mGridView.setVerticalSpacing(0);
            mGridView.invalidate();
            mGridView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();

        int[][] childRight = new int[3][3];
        int STRETCH_SPACING = 0;
        int STRETCH_COLUMN_WIDTH = 1;
        int STRETCH_SPACING_UNIFORM = 2;
        int INDEX_RIGHTMOST = 0;
        int INDEX_0 = 1;
        int INDEX_1 = 2;

        mActivityRule.runOnUiThread(() -> {
            mGridView.setColumnWidth(15);
            mGridView.setStretchMode(GridView.STRETCH_SPACING);
            mGridView.invalidate();
            mGridView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(GridView.STRETCH_SPACING, mGridView.getStretchMode());
        child = mGridView.getChildAt(NUM_COLUMNS - 1); // get the rightmost view at the first line.
        childRight[STRETCH_SPACING][INDEX_RIGHTMOST] = child.getRight();

        child = mGridView.getChildAt(0);
        childRight[STRETCH_SPACING][INDEX_0] = child.getRight();

        child = mGridView.getChildAt(1);
        childRight[STRETCH_SPACING][INDEX_1] = child.getRight();

        mActivityRule.runOnUiThread(() -> {
            mGridView.setColumnWidth(15);
            mGridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            mGridView.invalidate();
            mGridView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(GridView.STRETCH_COLUMN_WIDTH, mGridView.getStretchMode());
        child = mGridView.getChildAt(NUM_COLUMNS - 1); // get the rightmost view at the first line.
        childRight[STRETCH_COLUMN_WIDTH][INDEX_RIGHTMOST] = child.getRight();

        child = mGridView.getChildAt(0);
        childRight[STRETCH_COLUMN_WIDTH][INDEX_0] = child.getRight();

        child = mGridView.getChildAt(1);
        childRight[STRETCH_COLUMN_WIDTH][INDEX_1] = child.getRight();

        mActivityRule.runOnUiThread(() -> {
            mGridView.setColumnWidth(15);
            mGridView.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);
            mGridView.invalidate();
            mGridView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(GridView.STRETCH_SPACING_UNIFORM, mGridView.getStretchMode());
        child = mGridView.getChildAt(NUM_COLUMNS - 1); // get the rightmost view at the first line.
        childRight[STRETCH_SPACING_UNIFORM][INDEX_RIGHTMOST] = child.getRight();

        child = mGridView.getChildAt(0);
        childRight[STRETCH_SPACING_UNIFORM][INDEX_0] = child.getRight();

        child = mGridView.getChildAt(1);
        childRight[STRETCH_SPACING_UNIFORM][INDEX_1] = child.getRight();

        assertTrue(childRight[STRETCH_SPACING][INDEX_RIGHTMOST]
                > childRight[STRETCH_COLUMN_WIDTH][INDEX_RIGHTMOST]);
        assertTrue(childRight[STRETCH_SPACING][INDEX_RIGHTMOST]
                > childRight[STRETCH_SPACING_UNIFORM][INDEX_RIGHTMOST]);
        assertTrue(childRight[STRETCH_SPACING][INDEX_0]
                == childRight[STRETCH_COLUMN_WIDTH][INDEX_0]);
        assertTrue(childRight[STRETCH_SPACING][INDEX_0]
                < childRight[STRETCH_SPACING_UNIFORM][INDEX_0]);
        assertTrue(childRight[STRETCH_SPACING][INDEX_1]
                > childRight[STRETCH_COLUMN_WIDTH][INDEX_1]);
        assertTrue(childRight[STRETCH_SPACING][INDEX_1]
                < childRight[STRETCH_SPACING_UNIFORM][INDEX_1]);
    }

    @Test
    public void testSetNumColumns() throws Throwable {
        // this test case can not be ran in UI thread.
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new MockGridViewAdapter(10));
            mGridView.setHorizontalSpacing(0);
            mGridView.setVerticalSpacing(0);
            mGridView.setNumColumns(10);
        });
        mInstrumentation.waitForIdleSync();

        View child0 = mGridView.getChildAt(0);
        View child9 = mGridView.getChildAt(9);
        assertEquals(child0.getBottom(), child9.getBottom());

        mActivityRule.runOnUiThread(() -> mGridView.setNumColumns(9));
        mInstrumentation.waitForIdleSync();

        child0 = mGridView.getChildAt(0);
        child9 = mGridView.getChildAt(9);
        assertEquals(child0.getBottom(), child9.getTop());
        assertEquals(child0.getLeft(), child9.getLeft());

        mActivityRule.runOnUiThread(() -> mGridView.setNumColumns(1));
        mInstrumentation.waitForIdleSync();

        for (int i = 0; i < mGridView.getChildCount(); i++) {
            View child = mGridView.getChildAt(i);
            assertEquals(0, child.getLeft() - mGridView.getListPaddingLeft());
        }
    }

    @Test
    public void testDefaultNumColumns() {
        final GridView gridView = new GridView(mActivity);
        assertEquals(gridView.getNumColumns(), GridView.AUTO_FIT);
    }

    @Test
    public void testGetNumColumns() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new MockGridViewAdapter(10));
            mGridView.setNumColumns(10);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(mGridView.getNumColumns(), 10);

        mActivityRule.runOnUiThread(() -> mGridView.setNumColumns(1));
        mInstrumentation.waitForIdleSync();

        assertEquals(mGridView.getNumColumns(), 1);

        mActivityRule.runOnUiThread(() -> mGridView.setNumColumns(0));
        mInstrumentation.waitForIdleSync();

        //although setNumColumns(0) was called, the number of columns should be 1
        assertEquals(mGridView.getNumColumns(), 1);
    }

    @Test
    public void testAttachLayoutAnimationParameters() {
        MockGridView mockGridView = new MockGridView(mActivity);
        ViewGroup.LayoutParams p = new ViewGroup.LayoutParams(320, 480);
        mockGridView.attachLayoutAnimationParameters(null, p, 1, 2);
        AnimationParameters animationParams = (AnimationParameters) p.layoutAnimationParameters;
        assertEquals(1, animationParams.index);
        assertEquals(2, animationParams.count);
    }

    @Test
    public void testLayoutChildren() {
        MockGridView mockGridView = new MockGridView(mActivity);
        mockGridView.layoutChildren();
    }

    @UiThreadTest
    @Test
    public void testOnFocusChanged() {
        final MockGridView mockGridView = new MockGridView(mActivity);

        assertFalse(mockGridView.hasCalledOnFocusChanged());
        mockGridView.setAdapter(new MockGridViewAdapter(10));
        mockGridView.setFocusable(true);
        mockGridView.requestFocus();

        assertTrue(mockGridView.hasCalledOnFocusChanged());
        mockGridView.reset();
        assertFalse(mockGridView.hasCalledOnFocusChanged());

        mockGridView.clearFocus();

        assertTrue(mockGridView.hasCalledOnFocusChanged());
    }

    @Test
    public void testAccessColumnWidth() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new MockGridViewAdapter(10));
            mGridView.setNumColumns(GridView.AUTO_FIT);
            mGridView.setHorizontalSpacing(0);
            mGridView.setVerticalSpacing(0);
            mGridView.setColumnWidth(0);
        });
        mInstrumentation.waitForIdleSync();

        // Verify whether column number equals 2.
        assertEquals(0, mGridView.getRequestedColumnWidth());
        assertEquals(mGridView.getWidth() / 2, mGridView.getColumnWidth());
        View child0 = mGridView.getChildAt(0);
        View child1 = mGridView.getChildAt(1);
        View child2 = mGridView.getChildAt(2);
        assertEquals(child0.getBottom(), child1.getBottom());
        assertEquals(child0.getLeft(), child2.getLeft());

        mActivityRule.runOnUiThread(() -> {
            mGridView.setNumColumns(GridView.AUTO_FIT);
            mGridView.setColumnWidth(Integer.MAX_VALUE);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(Integer.MAX_VALUE, mGridView.getRequestedColumnWidth());
        assertEquals(mGridView.getWidth(), mGridView.getColumnWidth());
        child0 = mGridView.getChildAt(0);
        child1 = mGridView.getChildAt(1);
        assertEquals(child0.getBottom(), child1.getTop());
        assertEquals(child0.getLeft(), child1.getLeft());
    }

    @MediumTest
    @Test
    public void testFullyDetachUnusedViewOnScroll() throws Throwable {
        final AttachDetachAwareView theView = new AttachDetachAwareView(mActivity);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, () -> {
            mGridView.setAdapter(new DummyAdapter(1000, theView));
        });
        assertEquals("test sanity", 1, theView.mOnAttachCount);
        assertEquals("test sanity", 0, theView.mOnDetachCount);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, () -> {
            mGridView.scrollListBy(mGridView.getHeight() * 2);
        });
        assertNull("test sanity, unused view should be removed", theView.getParent());
        assertEquals("unused view should be detached", 1, theView.mOnDetachCount);
        assertFalse(theView.isTemporarilyDetached());
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, () -> {
            mGridView.scrollListBy(-mGridView.getHeight() * 2);
            // listview limits scroll to 1 page which is why we call it twice here.
            mGridView.scrollListBy(-mGridView.getHeight() * 2);
        });
        assertNotNull("test sanity, view should be re-added", theView.getParent());
        assertEquals("view should receive another attach call", 2, theView.mOnAttachCount);
        assertEquals("view should not receive a detach call", 1, theView.mOnDetachCount);
        assertFalse(theView.isTemporarilyDetached());
    }

    @MediumTest
    @Test
    public void testFullyDetachUnusedViewOnReLayout() throws Throwable {
        final AttachDetachAwareView theView = new AttachDetachAwareView(mActivity);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, () -> {
            mGridView.setAdapter(new DummyAdapter(1000, theView));
        });
        assertEquals("test sanity", 1, theView.mOnAttachCount);
        assertEquals("test sanity", 0, theView.mOnDetachCount);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, () -> {
            mGridView.setSelection(800);
        });
        assertNull("test sanity, unused view should be removed", theView.getParent());
        assertEquals("unused view should be detached", 1, theView.mOnDetachCount);
        assertFalse(theView.isTemporarilyDetached());
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, () -> {
            mGridView.setSelection(0);
        });
        assertNotNull("test sanity, view should be re-added", theView.getParent());
        assertEquals("view should receive another attach call", 2, theView.mOnAttachCount);
        assertEquals("view should not receive a detach call", 1, theView.mOnDetachCount);
        assertFalse(theView.isTemporarilyDetached());
    }

    @MediumTest
    @Test
    public void testFullyDetachUnusedViewOnScrollForFocus() throws Throwable {
        final AttachDetachAwareView theView = new AttachDetachAwareView(mActivity);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, () -> {
            mGridView.setAdapter(new DummyAdapter(1000, theView));
        });
        assertEquals("test sanity", 1, theView.mOnAttachCount);
        assertEquals("test sanity", 0, theView.mOnDetachCount);
        while(theView.getParent() != null) {
            assertEquals("the view should NOT be detached", 0, theView.mOnDetachCount);
            CtsKeyEventUtil.sendKeys(mInstrumentation, mGridView, KeyEvent.KEYCODE_DPAD_DOWN);
            WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, null);
        }
        assertEquals("the view should be detached", 1, theView.mOnDetachCount);
        assertFalse(theView.isTemporarilyDetached());
        while(theView.getParent() == null) {
            CtsKeyEventUtil.sendKeys(mInstrumentation, mGridView, KeyEvent.KEYCODE_DPAD_UP);
            WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mGridView, null);
        }
        assertEquals("the view should be re-attached", 2, theView.mOnAttachCount);
        assertEquals("the view should not receive another detach", 1, theView.mOnDetachCount);
        assertFalse(theView.isTemporarilyDetached());
    }

    @LargeTest
    @Test
    public void testSmoothScrollByOffset() throws Throwable {
        final int itemCount = 300;
        mActivityRule.runOnUiThread(() -> {
            mGridView.setAdapter(new MockGridViewAdapter(itemCount));
            mGridView.setNumColumns(GridView.AUTO_FIT);
            mGridView.setHorizontalSpacing(0);
            mGridView.setVerticalSpacing(0);
            mGridView.setColumnWidth(Integer.MAX_VALUE);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(0, mGridView.getFirstVisiblePosition());

        // Register a scroll listener on our GridView. The listener will notify our latch
        // when the "target" item comes into view. If that never happens, the latch will
        // time out and fail the test.
        final CountDownLatch latch = new CountDownLatch(1);
        final int positionToScrollTo = itemCount - 10;
        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
                if ((positionToScrollTo >= firstVisibleItem) &&
                        (positionToScrollTo <= (firstVisibleItem + visibleItemCount))) {
                    latch.countDown();
                }
            }
        });
        int offset = positionToScrollTo - mGridView.getLastVisiblePosition();
        mActivityRule.runOnUiThread(() -> mGridView.smoothScrollByOffset(offset));

        boolean result = false;
        try {
            result = latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
        assertTrue("Timed out while waiting for the target view to be scrolled into view", result);
    }

    private static class MockGridView extends GridView {
        private boolean mCalledOnFocusChanged = false;

        public boolean hasCalledOnFocusChanged() {
            return mCalledOnFocusChanged;
        }

        public void reset() {
            mCalledOnFocusChanged = false;
        }

        public MockGridView(Context context) {
            super(context);
        }

        public MockGridView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MockGridView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public void attachLayoutAnimationParameters(View child,
                ViewGroup.LayoutParams params, int index, int count) {
            super.attachLayoutAnimationParameters(child, params, index, count);
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction,
                Rect previouslyFocusedRect) {
            mCalledOnFocusChanged = true;
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        }
    }

    private class MockGridViewAdapter implements ListAdapter, Filterable {
        private final int mCount;

        MockGridViewAdapter(int count) {
            mCount = count;
        }

        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean isEnabled(int position) {
            return true;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
        }

        public int getCount() {
            return mCount;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public boolean hasStableIds() {
            return false;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if ((convertView != null) && (convertView instanceof ImageView)) {
                ((ImageView) convertView).setImageResource(R.drawable.size_48x48);
                return convertView;
            }

            ImageView newView = new ImageView(mActivity);
            AbsListView.LayoutParams params = new AbsListView.LayoutParams(
                                                  AbsListView.LayoutParams.WRAP_CONTENT,
                                                  AbsListView.LayoutParams.WRAP_CONTENT);
            newView.setLayoutParams(params);
            newView.setImageResource(R.drawable.size_48x48);
            return newView;
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public boolean isEmpty() {
            return false;
        }

        public Filter getFilter() {
            return new FilterTest();
        }
    }

    private static class FilterTest extends Filter {
        @Override
        protected Filter.FilterResults performFiltering(CharSequence constraint) {
            return null;
        }

        @Override
        protected void publishResults(CharSequence constraint, Filter.FilterResults results) {
        }
    }

    private class ImageAdapter implements ListAdapter {
        public ImageAdapter(Context c) {
            mContext = c;
        }

        public int getCount() {
            return mThumbIds.length;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {
                imageView = new ImageView(mContext);
                int layoutSize = (int)(50 * mContext.getResources().getDisplayMetrics().density);
                imageView.setLayoutParams(new GridView.LayoutParams(layoutSize, layoutSize));
                imageView.setAdjustViewBounds(false);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setPadding(0, 0, 0, 0);
            } else {
                imageView = (ImageView) convertView;
            }

            imageView.setImageResource(mThumbIds[position]);

            return imageView;
        }

        private Context mContext;

        private Integer[] mThumbIds = {
                R.drawable.failed, R.drawable.pass,
                R.drawable.animated, R.drawable.black,
                R.drawable.blue, R.drawable.red,
                R.drawable.animated, R.drawable.black,
                R.drawable.blue, R.drawable.failed,
                R.drawable.pass, R.drawable.red,
        };

        private final DataSetObservable mDataSetObservable = new DataSetObservable();

        public boolean hasStableIds() {
            return false;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mDataSetObservable.registerObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mDataSetObservable.unregisterObserver(observer);
        }

        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean isEnabled(int position) {
            return true;
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public boolean isEmpty() {
            return getCount() == 0;
        }
    }
}
