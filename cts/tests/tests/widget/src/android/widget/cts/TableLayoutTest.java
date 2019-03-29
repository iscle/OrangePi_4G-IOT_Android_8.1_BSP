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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link TableLayout}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TableLayoutTest {
    private TableCtsActivity mActivity;
    private TableLayout mTableDefault;
    private TableLayout mTableEmpty;
    private MockTableLayout mTableCustomEmpty;

    @Rule
    public ActivityTestRule<TableCtsActivity> mActivityRule =
            new ActivityTestRule<>(TableCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mTableDefault = (TableLayout) mActivity.findViewById(R.id.table1);
        mTableEmpty = (TableLayout) mActivity.findViewById(R.id.table_empty);
        mTableCustomEmpty = (MockTableLayout) mActivity.findViewById(R.id.table_custom_empty);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new TableLayout(mActivity);

        new TableLayout(mActivity, null);

        assertTrue(mTableDefault.isColumnCollapsed(0));
        assertTrue(mTableDefault.isColumnStretchable(2));

        mActivity.setContentView(R.layout.table_layout_2);
        TableLayout tableLayout = (TableLayout) mActivity.findViewById(R.id.table2);
        assertTrue(tableLayout.isColumnShrinkable(1));
    }

    @UiThreadTest
    @Test
    public void testSetOnHierarchyChangeListener() {
        ViewGroup.OnHierarchyChangeListener mockHierarchyChangeListener =
                mock(ViewGroup.OnHierarchyChangeListener.class);
        mTableEmpty.setOnHierarchyChangeListener(mockHierarchyChangeListener);

        View toAdd = new TextView(mActivity);
        mTableEmpty.addView(toAdd);
        verify(mockHierarchyChangeListener, times(1)).onChildViewAdded(mTableEmpty, toAdd);
        mTableEmpty.removeViewAt(0);
        verify(mockHierarchyChangeListener, times(1)).onChildViewRemoved(mTableEmpty, toAdd);
        verifyNoMoreInteractions(mockHierarchyChangeListener);

        mTableEmpty.setOnHierarchyChangeListener(null);
        mTableEmpty.addView(new TextView(mActivity));
        mTableEmpty.removeViewAt(0);
        verifyNoMoreInteractions(mockHierarchyChangeListener);
    }

    @UiThreadTest
    @Test
    public void testRequestLayout() {
        mTableEmpty.addView(new TextView(mActivity));
        mTableEmpty.addView(new ListView(mActivity));
        mTableEmpty.layout(0, 0, 200, 300);
        assertFalse(mTableEmpty.isLayoutRequested());
        assertFalse(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertFalse(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.requestLayout();
        assertTrue(mTableEmpty.isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());
    }

    @UiThreadTest
    @Test
    public void testAccessShrinkAllColumns() {
        assertFalse(mTableEmpty.isShrinkAllColumns());

        mTableEmpty.setShrinkAllColumns(true);
        assertTrue(mTableEmpty.isShrinkAllColumns());
        mTableEmpty.setShrinkAllColumns(false);
        assertFalse(mTableEmpty.isShrinkAllColumns());
    }

    @UiThreadTest
    @Test
    public void testAccessStretchAllColumns() {
        assertFalse(mTableEmpty.isStretchAllColumns());

        mTableEmpty.setStretchAllColumns(true);
        assertTrue(mTableEmpty.isStretchAllColumns());
        mTableEmpty.setStretchAllColumns(false);
        assertFalse(mTableEmpty.isStretchAllColumns());
    }

    @UiThreadTest
    @Test
    public void testAccessColumnCollapsed() {
        mTableEmpty.addView(new TextView(mActivity));
        mTableEmpty.addView(new TextView(mActivity));
        assertFalse(mTableEmpty.isColumnCollapsed(0));
        assertFalse(mTableEmpty.isColumnCollapsed(1));

        mTableEmpty.layout(0, 0, 200, 300);
        assertFalse(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertFalse(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.setColumnCollapsed(0, true);
        assertTrue(mTableEmpty.isColumnCollapsed(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnCollapsed(1, true);
        assertTrue(mTableEmpty.isColumnCollapsed(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnCollapsed(0, false);
        assertFalse(mTableEmpty.isColumnCollapsed(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnCollapsed(1, false);
        assertFalse(mTableEmpty.isColumnCollapsed(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());
    }

    @UiThreadTest
    @Test
    public void testAccessColumnStretchable() {
        mTableEmpty.addView(new TableRow(mActivity));
        mTableEmpty.addView(new TableRow(mActivity));
        assertFalse(mTableEmpty.isColumnStretchable(0));
        assertFalse(mTableEmpty.isColumnStretchable(1));

        mTableEmpty.layout(0, 0, 200, 300);
        assertFalse(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertFalse(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.setColumnStretchable(0, true);
        assertTrue(mTableEmpty.isColumnStretchable(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnStretchable(1, true);
        assertTrue(mTableEmpty.isColumnStretchable(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnStretchable(0, false);
        assertFalse(mTableEmpty.isColumnStretchable(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnStretchable(1, false);
        assertFalse(mTableEmpty.isColumnStretchable(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

    }

    @Test
    public void testColumnStretchableEffect() throws Throwable {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        // Preparation: remove Collapsed mark for column 0.
        mActivityRule.runOnUiThread(() -> mTableDefault.setColumnCollapsed(0, false));
        instrumentation.waitForIdleSync();
        assertFalse(mTableDefault.isColumnStretchable(0));
        assertFalse(mTableDefault.isColumnStretchable(1));
        assertTrue(mTableDefault.isColumnStretchable(2));

        TextView column0 = (TextView) ((TableRow) mTableDefault.getChildAt(0)).getChildAt(0);
        TextView column1 = (TextView) ((TableRow) mTableDefault.getChildAt(0)).getChildAt(1);
        TextView column2 = (TextView) ((TableRow) mTableDefault.getChildAt(0)).getChildAt(2);
        int oldWidth0 = column0.getWidth();
        int oldWidth1 = column1.getWidth();
        int oldWidth2 = column2.getWidth();
        column0.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.EXACTLY);
        int orignalWidth0 = column0.getMeasuredWidth();
        column1.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.EXACTLY);
        int orignalWidth1 = column1.getMeasuredWidth();
        column2.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.EXACTLY);
        int orignalWidth2 = column2.getMeasuredWidth();
        int totalSpace = mTableDefault.getWidth() - orignalWidth0
                - orignalWidth1 - orignalWidth2;

        // Test: set column 1 is able to be stretched.
        mActivityRule.runOnUiThread(() -> mTableDefault.setColumnStretchable(1, true));
        instrumentation.waitForIdleSync();
        assertEquals(oldWidth0, column0.getWidth());
        assertTrue(oldWidth1 < column1.getWidth());
        assertTrue(oldWidth2 > column2.getWidth());
        int extraSpace = totalSpace / 2;
        assertEquals(orignalWidth0, column0.getWidth());
        assertEquals(orignalWidth1 + extraSpace, column1.getWidth());
        assertEquals(orignalWidth2 + extraSpace, column2.getWidth());
        oldWidth0 = column0.getWidth();
        oldWidth1 = column1.getWidth();
        oldWidth2 = column2.getWidth();

        // Test: set column 0 is able to be stretched.
        mActivityRule.runOnUiThread(() -> mTableDefault.setColumnStretchable(0, true));
        instrumentation.waitForIdleSync();
        assertTrue(oldWidth0 < column0.getWidth());
        assertTrue(oldWidth1 > column1.getWidth());
        assertTrue(oldWidth2 > column2.getWidth());
        extraSpace = totalSpace / 3;
        assertEquals(orignalWidth0 + extraSpace, column0.getWidth());
        assertEquals(orignalWidth1 + extraSpace, column1.getWidth());
        assertEquals(orignalWidth2 + extraSpace, column2.getWidth());
        oldWidth0 = column0.getWidth();
        oldWidth1 = column1.getWidth();
        oldWidth2 = column2.getWidth();

        // Test: set column 2 is unable to be stretched.
        mActivityRule.runOnUiThread(() -> mTableDefault.setColumnStretchable(2, false));
        instrumentation.waitForIdleSync();
        // assertTrue(oldWidth0 < column0.getWidth());
        // assertTrue(oldWidth1 < column1.getWidth());
        assertEquals(oldWidth0, column0.getWidth());
        assertEquals(oldWidth1, column1.getWidth());
        assertTrue(oldWidth2 > column2.getWidth());
        // extraSpace = totalSpace / 2;
        extraSpace = totalSpace / 3;
        assertEquals(orignalWidth0 + extraSpace, column0.getWidth());
        assertEquals(orignalWidth1 + extraSpace, column1.getWidth());
        assertEquals(orignalWidth2, column2.getWidth());
        oldWidth0 = column0.getWidth();
        oldWidth1 = column1.getWidth();
        oldWidth2 = column2.getWidth();

        // Test: mark all columns are able to be stretched.
        mActivityRule.runOnUiThread(() -> {
            mTableDefault.setStretchAllColumns(true);
            mTableDefault.requestLayout();
        });
        instrumentation.waitForIdleSync();
        // assertTrue(oldWidth0 > column0.getWidth());
        // assertTrue(oldWidth1 > column1.getWidth());
        assertEquals(oldWidth0, column0.getWidth());
        assertEquals(oldWidth1, column1.getWidth());
        assertTrue(oldWidth2 < column2.getWidth());
        extraSpace = totalSpace / 3;
        assertEquals(orignalWidth0 + extraSpace, column0.getWidth());
        assertEquals(orignalWidth1 + extraSpace, column1.getWidth());
        assertEquals(orignalWidth2 + extraSpace, column2.getWidth());
        oldWidth0 = column0.getWidth();
        oldWidth1 = column1.getWidth();
        oldWidth2 = column2.getWidth();

        // Test: Remove the mark for all columns are able to be stretched.
        mActivityRule.runOnUiThread(() -> {
            mTableDefault.setStretchAllColumns(false);
            mTableDefault.requestLayout();
        });
        instrumentation.waitForIdleSync();
        // assertTrue(oldWidth0 > column0.getWidth());
        // assertTrue(oldWidth1 > column1.getWidth());
        assertEquals(oldWidth0, column0.getWidth());
        assertEquals(oldWidth1, column1.getWidth());
        assertTrue(oldWidth2 > column2.getWidth());
        assertEquals(orignalWidth0 + extraSpace, column0.getWidth());
        assertEquals(orignalWidth1 + extraSpace, column1.getWidth());
        assertEquals(orignalWidth2, column2.getWidth());
    }

    @UiThreadTest
    @Test
    public void testAccessColumnShrinkable() {
        mTableEmpty.addView(new TableRow(mActivity));
        mTableEmpty.addView(new TableRow(mActivity));
        assertFalse(mTableEmpty.isColumnShrinkable(0));
        assertFalse(mTableEmpty.isColumnShrinkable(1));

        mTableEmpty.layout(0, 0, 200, 300);
        assertFalse(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertFalse(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.setColumnShrinkable(0, true);
        assertTrue(mTableEmpty.isColumnShrinkable(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnShrinkable(1, true);
        assertTrue(mTableEmpty.isColumnShrinkable(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnShrinkable(0, false);
        assertFalse(mTableEmpty.isColumnShrinkable(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        mTableEmpty.setColumnShrinkable(1, false);
        assertFalse(mTableEmpty.isColumnShrinkable(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());
    }

    @UiThreadTest
    @Test
    public void testAddView() {
        View child1 = new TextView(mActivity);
        mTableEmpty.addView(child1);
        assertSame(child1, mTableEmpty.getChildAt(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        View child2 = new RelativeLayout(mActivity);
        mTableEmpty.addView(child2);
        assertSame(child1, mTableEmpty.getChildAt(0));
        assertSame(child2, mTableEmpty.getChildAt(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        View child3 = new ListView(mActivity);
        mTableEmpty.addView(child3);
        assertSame(child1, mTableEmpty.getChildAt(0));
        assertSame(child2, mTableEmpty.getChildAt(1));
        assertSame(child3, mTableEmpty.getChildAt(2));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(2).isLayoutRequested());
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddViewNull() {
        mTableEmpty.addView(null);
    }

    @UiThreadTest
    @Test
    public void testAddViewAtIndex() {
        View child1 = new TextView(mActivity);
        mTableEmpty.addView(child1, 0);
        assertSame(child1, mTableEmpty.getChildAt(0));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        View child2 = new RelativeLayout(mActivity);
        mTableEmpty.addView(child2, 0);
        assertSame(child2, mTableEmpty.getChildAt(0));
        assertSame(child1, mTableEmpty.getChildAt(1));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        View child3 = new ListView(mActivity);
        mTableEmpty.addView(child3, -1);
        assertSame(child2, mTableEmpty.getChildAt(0));
        assertSame(child1, mTableEmpty.getChildAt(1));
        assertSame(child3, mTableEmpty.getChildAt(2));
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(2).isLayoutRequested());
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddViewAtIndexTooLow() {
        mTableEmpty.addView(null, -1);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testAddViewAtIndexTooHigh() {
        mTableEmpty.addView(new ListView(mActivity), Integer.MAX_VALUE);
    }

    @UiThreadTest
    @Test
    public void testAddViewWithLayoutParams() {
        View child1 = new TextView(mActivity);
        assertNull(child1.getLayoutParams());
        mTableEmpty.addView(child1, new ViewGroup.LayoutParams(100, 200));
        assertSame(child1, mTableEmpty.getChildAt(0));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(0).getLayoutParams().width);
        assertEquals(200, mTableEmpty.getChildAt(0).getLayoutParams().height);
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        View child2 = new TableRow(mActivity);
        assertNull(child2.getLayoutParams());
        mTableEmpty.addView(child2, new TableRow.LayoutParams(200, 300, 1));
        assertSame(child1, mTableEmpty.getChildAt(0));
        assertSame(child2, mTableEmpty.getChildAt(1));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(0).getLayoutParams().width);
        assertEquals(200, mTableEmpty.getChildAt(0).getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(1).getLayoutParams().width);
        assertEquals(300, mTableEmpty.getChildAt(1).getLayoutParams().height);
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddViewWithLayoutParamsNullView() {
        mTableEmpty.addView(null, new TableLayout.LayoutParams(200, 300));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testAddViewWithLayoutParamsNullLayoutParams() {
        mTableEmpty.addView(new ListView(mActivity), null);
    }

    @UiThreadTest
    @Test
    public void testAddViewAtIndexWithLayoutParams() {
        View child1 = new TextView(mActivity);
        assertNull(child1.getLayoutParams());
        mTableEmpty.addView(child1, 0, new ViewGroup.LayoutParams(100, 200));
        assertSame(child1, mTableEmpty.getChildAt(0));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(0).getLayoutParams().width);
        assertEquals(200, mTableEmpty.getChildAt(0).getLayoutParams().height);
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        View child2 = new TableRow(mActivity);
        assertNull(child2.getLayoutParams());
        mTableEmpty.addView(child2, 0, new TableRow.LayoutParams(200, 300, 1));
        assertSame(child2, mTableEmpty.getChildAt(0));
        assertSame(child1, mTableEmpty.getChildAt(1));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(0).getLayoutParams().width);
        assertEquals(300, mTableEmpty.getChildAt(0).getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(1).getLayoutParams().width);
        assertEquals(200, mTableEmpty.getChildAt(1).getLayoutParams().height);
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());

        mTableEmpty.layout(0, 0, 200, 300);

        View child3 = new ListView(mActivity);
        assertNull(child3.getLayoutParams());
        mTableEmpty.addView(child3, -1, new ListView.LayoutParams(300, 400));
        assertSame(child2, mTableEmpty.getChildAt(0));
        assertSame(child1, mTableEmpty.getChildAt(1));
        assertSame(child3, mTableEmpty.getChildAt(2));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(0).getLayoutParams().width);
        assertEquals(300, mTableEmpty.getChildAt(0).getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(1).getLayoutParams().width);
        assertEquals(200, mTableEmpty.getChildAt(1).getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                mTableEmpty.getChildAt(2).getLayoutParams().width);
        assertEquals(400, mTableEmpty.getChildAt(2).getLayoutParams().height);
        assertTrue(mTableEmpty.getChildAt(0).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(1).isLayoutRequested());
        assertTrue(mTableEmpty.getChildAt(2).isLayoutRequested());
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testAddViewAtIndexWithLayoutParamsIndexTooHigh() {
        mTableEmpty.addView(new ListView(mActivity), Integer.MAX_VALUE,
                new TableLayout.LayoutParams(200, 300));
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddViewAtIndexWithLayoutParamsNullView() {
        mTableEmpty.addView(null, -1, new TableLayout.LayoutParams(200, 300));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testAddViewAtIndexWithLayoutParamsNullLayoutParams() {
        mTableEmpty.addView(new ListView(mActivity), -1, null);
    }

    @UiThreadTest
    @Test
    public void testGenerateLayoutParamsFromAttributeSet() {
        XmlResourceParser parser = mActivity.getResources().getLayout(R.layout.table_layout_1);
        AttributeSet attr = Xml.asAttributeSet(parser);

        assertNotNull(mTableEmpty.generateLayoutParams(attr));

        assertNotNull(mTableEmpty.generateLayoutParams((AttributeSet) null));
    }

    @UiThreadTest
    @Test
    public void testCheckLayoutParams() {
        assertTrue(mTableCustomEmpty.checkLayoutParams(new TableLayout.LayoutParams(200, 300)));

        assertFalse(mTableCustomEmpty.checkLayoutParams(new ViewGroup.LayoutParams(200, 300)));

        assertFalse(mTableCustomEmpty.checkLayoutParams(new RelativeLayout.LayoutParams(200, 300)));

        assertFalse(mTableCustomEmpty.checkLayoutParams(null));
    }

    @UiThreadTest
    @Test
    public void testGenerateDefaultLayoutParams() {
        LinearLayout.LayoutParams layoutParams = mTableCustomEmpty.generateDefaultLayoutParams();
        assertNotNull(layoutParams);
        assertTrue(layoutParams instanceof TableLayout.LayoutParams);
    }

    @UiThreadTest
    @Test
    public void testGenerateLayoutParamsFromLayoutParams() {
        LinearLayout.LayoutParams layoutParams = mTableCustomEmpty.generateLayoutParams(
                new ViewGroup.LayoutParams(200, 300));
        assertNotNull(layoutParams);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, layoutParams.width);
        assertEquals(300, layoutParams.height);
        assertTrue(layoutParams instanceof TableLayout.LayoutParams);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testGenerateLayoutParamsFromLayoutParamsNull() {
        mTableCustomEmpty.generateLayoutParams((ViewGroup.LayoutParams) null);
    }

    @UiThreadTest
    @Test
    public void testOnLayout() {
        mTableCustomEmpty.onLayout(false, 0, 0, 20, 20);
    }

    @UiThreadTest
    @Test
    public void testOnMeasure() {
        mTableCustomEmpty.onMeasure(MeasureSpec.EXACTLY, MeasureSpec.EXACTLY);
    }

    /*
     * Mock class for TableLayout to test protected methods
     */
    public static class MockTableLayout extends TableLayout {
        public MockTableLayout(Context context) {
            super(context);
        }

        public MockTableLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
            return super.checkLayoutParams(p);
        }

        @Override
        protected LinearLayout.LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }

        @Override
        protected LinearLayout.LayoutParams generateLayoutParams(
                ViewGroup.LayoutParams p) {
            return super.generateLayoutParams(p);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}

