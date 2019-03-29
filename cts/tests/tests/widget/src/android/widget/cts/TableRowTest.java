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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
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
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link TableRow}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TableRowTest {
    private Activity mActivity;

    @Rule
    public ActivityTestRule<TableCtsActivity> mActivityRule =
            new ActivityTestRule<>(TableCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new TableRow(mActivity);

        new TableRow(mActivity, null);
    }

    @UiThreadTest
    @Test
    public void testSetOnHierarchyChangeListener() {
        TableRow tableRow = new TableRow(mActivity);

        ViewGroup.OnHierarchyChangeListener mockHierarchyChangeListener =
                mock(ViewGroup.OnHierarchyChangeListener.class);
        tableRow.setOnHierarchyChangeListener(mockHierarchyChangeListener);

        View toAdd = new TextView(mActivity);
        tableRow.addView(toAdd);
        verify(mockHierarchyChangeListener, times(1)).onChildViewAdded(tableRow, toAdd);
        tableRow.removeViewAt(0);
        verify(mockHierarchyChangeListener, times(1)).onChildViewRemoved(tableRow, toAdd);
        verifyNoMoreInteractions(mockHierarchyChangeListener);

        tableRow.setOnHierarchyChangeListener(null);
        tableRow.addView(new TextView(mActivity));
        tableRow.removeViewAt(0);
        verifyNoMoreInteractions(mockHierarchyChangeListener);
    }

    @UiThreadTest
    @Test
    public void testGetVirtualChildAt() {
        mActivity.setContentView(android.widget.cts.R.layout.table_layout_1);
        TableLayout tableLayout = (TableLayout) mActivity
                .findViewById(android.widget.cts.R.id.table1);

        TableRow tableRow = (TableRow) tableLayout.getChildAt(0);
        Resources resources = mActivity.getResources();
        assertEquals(resources.getString(R.string.table_layout_first),
                ((TextView) tableRow.getVirtualChildAt(0)).getText().toString());
        assertEquals(resources.getString(R.string.table_layout_second),
                ((TextView) tableRow.getVirtualChildAt(1)).getText().toString());
        assertEquals(resources.getString(R.string.table_layout_third),
                ((TextView) tableRow.getVirtualChildAt(2)).getText().toString());

        mActivity.setContentView(android.widget.cts.R.layout.table_layout_2);
        tableLayout = (TableLayout) mActivity.findViewById(android.widget.cts.R.id.table2);

        tableRow = (TableRow) tableLayout.getChildAt(0);
        assertNull(tableRow.getVirtualChildAt(0));
        assertEquals(resources.getString(R.string.table_layout_long),
                ((TextView) tableRow.getVirtualChildAt(1)).getText().toString());
        assertEquals(resources.getString(R.string.table_layout_second),
                ((TextView) tableRow.getVirtualChildAt(2)).getText().toString());
        assertEquals(resources.getString(R.string.table_layout_second),
                ((TextView) tableRow.getVirtualChildAt(3)).getText().toString());
        assertEquals(resources.getString(R.string.table_layout_third),
                ((TextView) tableRow.getVirtualChildAt(4)).getText().toString());
    }

    @UiThreadTest
    @Test
    public void testGetVirtualChildCount() {
        mActivity.setContentView(android.widget.cts.R.layout.table_layout_1);
        TableLayout tableLayout = (TableLayout) mActivity
                .findViewById(android.widget.cts.R.id.table1);

        TableRow tableRow = (TableRow) tableLayout.getChildAt(0);
        assertEquals(3, tableRow.getVirtualChildCount());

        mActivity.setContentView(android.widget.cts.R.layout.table_layout_2);
        tableLayout = (TableLayout) mActivity.findViewById(android.widget.cts.R.id.table2);

        tableRow = (TableRow) tableLayout.getChildAt(0);
        assertEquals(5, tableRow.getVirtualChildCount());
    }

    @Test
    public void testGenerateLayoutParamsFromAttributeSet() {
        TableRow tableRow = new TableRow(mActivity);

        Resources resources = mActivity.getResources();
        XmlResourceParser parser = resources.getLayout(R.layout.table_layout_1);
        AttributeSet attr = Xml.asAttributeSet(parser);

        assertNotNull(tableRow.generateLayoutParams(attr));

        assertNotNull(tableRow.generateLayoutParams((AttributeSet) null));
    }

    @Test
    public void testCheckLayoutParams() {
        MockTableRow mockTableRow = new MockTableRow(mActivity);

        assertTrue(mockTableRow.checkLayoutParams(new TableRow.LayoutParams(200, 300)));

        assertFalse(mockTableRow.checkLayoutParams(new ViewGroup.LayoutParams(200, 300)));

        assertFalse(mockTableRow.checkLayoutParams(new RelativeLayout.LayoutParams(200, 300)));

        assertFalse(mockTableRow.checkLayoutParams(null));
    }

    @Test
    public void testGenerateDefaultLayoutParams() {
        MockTableRow mockTableRow = new MockTableRow(mActivity);

        LinearLayout.LayoutParams layoutParams = mockTableRow.generateDefaultLayoutParams();
        assertNotNull(layoutParams);
        assertTrue(layoutParams instanceof TableRow.LayoutParams);
    }

    @Test
    public void testGenerateLayoutParamsFromLayoutParams() {
        MockTableRow mockTableRow = new MockTableRow(mActivity);

        LinearLayout.LayoutParams layoutParams = mockTableRow.generateLayoutParams(
                new ViewGroup.LayoutParams(200, 300));
        assertNotNull(layoutParams);
        assertEquals(200, layoutParams.width);
        assertEquals(300, layoutParams.height);
        assertTrue(layoutParams instanceof TableRow.LayoutParams);
    }

    @Test(expected=NullPointerException.class)
    public void testGenerateLayoutParamsFromLayoutParamsNull() {
        MockTableRow mockTableRow = new MockTableRow(mActivity);

        mockTableRow.generateLayoutParams((ViewGroup.LayoutParams) null);
    }

    @Test
    public void testOnLayout() {
        MockTableRow mockTableRow = new MockTableRow(mActivity);

        mockTableRow.onLayout(false, 0, 0, 200, 300);
    }

    @Test
    public void testOnMeasure() {
        MockTableRow mockTableRow = new MockTableRow(mActivity);

        mockTableRow.onMeasure(MeasureSpec.EXACTLY, MeasureSpec.EXACTLY);
    }

    /*
     * Mock class for TableRow to test protected methods
     */
    private class MockTableRow extends TableRow {
        public MockTableRow(Context context) {
            super(context);
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
