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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.test.annotation.UiThreadTest;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Test {@link SimpleExpandableListAdapter}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SimpleExpandableListAdapterTest {
    private static final int EXPANDED_GROUP_LAYOUT = android.R.layout.simple_expandable_list_item_2;

    private static final int LAST_CHILD_LAYOUT = android.R.layout.simple_list_item_2;

    private static final int CHILD_LAYOUT = android.R.layout.simple_list_item_1;

    private static final int GROUP_LAYOUT = android.R.layout.simple_expandable_list_item_1;

    private static final int[] VIEWS_GROUP_TO = new int[] {
            android.R.id.text1
    };

    private static final int[] VIEWS_CHILD_TO = new int[] {
            android.R.id.text1
    };

    private static final String[] COLUMNS_GROUP_FROM = new String[] {
        "column0"
    };

    private static final String[] COLUMNS_CHILD_FROM = new String[] {
        "column0"
    };

    private SimpleExpandableListAdapter mSimpleExpandableListAdapter;

    private Context mContext;

    /**
     * The child list.Each data are prefixed with "child". The content will be
     * set to 4 sub lists. Each sub list has 1 column. The first sub list has 1
     * row, the second has 2 rows, the third has 3 rows and the last has 4 rows
     */
    private ArrayList<ArrayList<HashMap<String, String>>> mChildList;

    /**
     * The group list. Each data are prefixed with "group". The content will be
     * set to 1 column and 4 rows
     */
    private ArrayList<HashMap<String, String>> mGroupList;

    private LinearLayout mAdapterHost;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSimpleExpandableListAdapter = null;
        mGroupList = createTestList(1, 4, "group");
        mChildList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ArrayList<HashMap<String, String>> l = createTestList(1, i + 1, "child");
            mChildList.add(l);
        }

        mSimpleExpandableListAdapter = new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);

        mAdapterHost = (LinearLayout) ((LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(
                R.layout.cursoradapter_host, null);
    }

    @Test
    public void testConstructor() {
        new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);

        new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, EXPANDED_GROUP_LAYOUT,
                COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);

        new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, EXPANDED_GROUP_LAYOUT,
                COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, LAST_CHILD_LAYOUT,
                COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);
    }

    @Test
    public void testGetChild() {
        HashMap<String, String> expected = new HashMap<>();
        expected.put("column0", "child00");
        assertEquals(expected, mSimpleExpandableListAdapter.getChild(0, 0));

        expected = new HashMap<String, String>();
        expected.put("column0", "child30");
        assertEquals(expected, mSimpleExpandableListAdapter.getChild(3, 3));
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildGroupPositionTooLow() {
        mSimpleExpandableListAdapter.getChild(-1, 0);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildGroupPositionTooHigh() {
        mSimpleExpandableListAdapter.getChild(4, 0);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildChildPositionTooLow() {
        mSimpleExpandableListAdapter.getChild(0, -1);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildChildPositionTooHigh() {
        mSimpleExpandableListAdapter.getChild(0, 1);
    }

    @Test
    public void testGetChildId() {
        assertEquals(0, mSimpleExpandableListAdapter.getChildId(0, 0));
        assertEquals(3, mSimpleExpandableListAdapter.getChildId(3, 3));

        // the following indexes are out of bounds
        assertEquals(0, mSimpleExpandableListAdapter.getChildId(-1, 0));
        assertEquals(-1, mSimpleExpandableListAdapter.getChildId(0, -1));
        assertEquals(0, mSimpleExpandableListAdapter.getChildId(4, 0));
        assertEquals(4, mSimpleExpandableListAdapter.getChildId(0, 4));
    }

    @UiThreadTest
    @Test
    public void testGetChildView() {
        // the normal and last use same layout
        View result = mSimpleExpandableListAdapter.getChildView(0, 0, false, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("child00", ((TextView) result).getText().toString());

        result = mSimpleExpandableListAdapter.getChildView(3, 3, true, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("child30", ((TextView) result).getText().toString());

        // the normal and last use different layouts
        mSimpleExpandableListAdapter = new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, EXPANDED_GROUP_LAYOUT,
                COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, LAST_CHILD_LAYOUT,
                COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);

        result = mSimpleExpandableListAdapter.getChildView(0, 0, false, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("child00", ((TextView) result).getText().toString());

        result = mSimpleExpandableListAdapter.getChildView(3, 3, true, null, mAdapterHost);
        assertTrue(result instanceof TwoLineListItem);
        assertEquals("child30",
                ((TextView) result.findViewById(android.R.id.text1)).getText().toString());

        // use convert view
        View convertView = new TextView(mContext);
        convertView.setId(android.R.id.text1);
        result = mSimpleExpandableListAdapter.getChildView(2, 2, false, convertView, mAdapterHost);
        assertSame(convertView, result);
        assertEquals("child20", ((TextView) result).getText().toString());

        // the parent can be null
        convertView = new TextView(mContext);
        convertView.setId(android.R.id.text1);
        result = mSimpleExpandableListAdapter.getChildView(1, 1, false, convertView, null);
        assertSame(convertView, result);
        assertEquals("child10", ((TextView) result).getText().toString());
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildViewGroupPositionTooLow() {
        mSimpleExpandableListAdapter.getChildView(-1, 0, false, null, mAdapterHost);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildViewGroupPositionTooHigh() {
        mSimpleExpandableListAdapter.getChildView(4, 0, false, null, mAdapterHost);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildViewChildPositionTooLow() {
        mSimpleExpandableListAdapter.getChildView(0, -1, false, null, mAdapterHost);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildViewChildPositionTooHigh() {
        mSimpleExpandableListAdapter.getChildView(0, 1, false, null, mAdapterHost);
    }

    @UiThreadTest
    @Test
    public void testNewChildView() {
        // the normal and last use same layout
        View result = mSimpleExpandableListAdapter.newChildView(false, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertNotNull(result.findViewById(android.R.id.text1));

        result = mSimpleExpandableListAdapter.newChildView(true, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertNotNull(result.findViewById(android.R.id.text1));

        // the normal and last use different layouts
        mSimpleExpandableListAdapter = new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, EXPANDED_GROUP_LAYOUT,
                COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, LAST_CHILD_LAYOUT,
                COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);

        result = mSimpleExpandableListAdapter.newChildView(false, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertNotNull(result.findViewById(android.R.id.text1));

        result = mSimpleExpandableListAdapter.newChildView(true, mAdapterHost);
        assertTrue(result instanceof TwoLineListItem);
        assertNotNull(result.findViewById(android.R.id.text1));
    }

    @Test
    public void testGetChildrenCount() {
        assertEquals(1, mSimpleExpandableListAdapter.getChildrenCount(0));
        assertEquals(2, mSimpleExpandableListAdapter.getChildrenCount(1));
        assertEquals(3, mSimpleExpandableListAdapter.getChildrenCount(2));
        assertEquals(4, mSimpleExpandableListAdapter.getChildrenCount(3));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildrenCountGroupPositionTooLow() {
        mSimpleExpandableListAdapter.getChildrenCount(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetChildrenCountGroupPositionTooHigh() {
        mSimpleExpandableListAdapter.getChildrenCount(4);
    }

    @Test
    public void testGetGroup() {
        HashMap<String, String> expected = new HashMap<>();
        expected.put("column0", "group00");
        assertEquals(expected, mSimpleExpandableListAdapter.getGroup(0));

        expected = new HashMap<>();
        expected.put("column0", "group30");
        assertEquals(expected, mSimpleExpandableListAdapter.getGroup(3));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetGroupGroupPositionTooLow() {
        mSimpleExpandableListAdapter.getGroup(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetGroupGroupPositionTooHigh() {
        mSimpleExpandableListAdapter.getGroup(4);
    }

    @Test
    public void testGetGroupCount() {
        assertEquals(4, mSimpleExpandableListAdapter.getGroupCount());

        mSimpleExpandableListAdapter = new SimpleExpandableListAdapter(mContext,
                createTestList(1, 9, ""), GROUP_LAYOUT, COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);
        assertEquals(9, mSimpleExpandableListAdapter.getGroupCount());
    }

    @Test
    public void testGetGroupId() {
        assertEquals(0, mSimpleExpandableListAdapter.getGroupId(0));
        assertEquals(3, mSimpleExpandableListAdapter.getGroupId(3));

        // following indexes are out of bounds
        assertEquals(-1, mSimpleExpandableListAdapter.getGroupId(-1));
        assertEquals(4, mSimpleExpandableListAdapter.getGroupId(4));
    }

    @UiThreadTest
    @Test
    public void testGetGroupView() {
        // the collapsed and expanded use same layout
        View result = mSimpleExpandableListAdapter.getGroupView(0, false, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("group00", ((TextView) result).getText().toString());

        result = mSimpleExpandableListAdapter.getGroupView(3, true, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("group30", ((TextView) result).getText().toString());

        // the collapsed and expanded use different layouts
        mSimpleExpandableListAdapter = new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, EXPANDED_GROUP_LAYOUT,
                COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, LAST_CHILD_LAYOUT,
                COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);

        result = mSimpleExpandableListAdapter.getGroupView(0, true, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("group00", ((TextView) result).getText().toString());

        result = mSimpleExpandableListAdapter.getGroupView(3, false, null, mAdapterHost);
        assertTrue(result instanceof TwoLineListItem);
        assertEquals("group30",
                ((TextView) result.findViewById(android.R.id.text1)).getText().toString());

        // use convert view
        View convertView = new TextView(mContext);
        convertView.setId(android.R.id.text1);
        result = mSimpleExpandableListAdapter.getGroupView(2, false, convertView, mAdapterHost);
        assertSame(convertView, result);
        assertEquals("group20", ((TextView) result).getText().toString());

        // the parent can be null
        convertView = new TextView(mContext);
        convertView.setId(android.R.id.text1);
        result = mSimpleExpandableListAdapter.getGroupView(1, false, convertView, null);
        assertSame(convertView, result);
        assertEquals("group10", ((TextView) result).getText().toString());
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetGroupViewGroupPositionTooLow() {
        mSimpleExpandableListAdapter.getGroupView(-1, false, null, mAdapterHost);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetGroupViewGroupPositionTooHigh() {
        mSimpleExpandableListAdapter.getGroupView(4, false, null, mAdapterHost);
    }

    @UiThreadTest
    @Test
    public void testNewGroupView() {
        // the collapsed and expanded use same layout
        View result = mSimpleExpandableListAdapter.newGroupView(false, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertNotNull(result.findViewById(android.R.id.text1));

        result = mSimpleExpandableListAdapter.newGroupView(true, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertNotNull(result.findViewById(android.R.id.text1));

        // the collapsed and expanded use different layouts
        mSimpleExpandableListAdapter = new SimpleExpandableListAdapter(mContext,
                mGroupList, GROUP_LAYOUT, EXPANDED_GROUP_LAYOUT,
                COLUMNS_GROUP_FROM, VIEWS_GROUP_TO,
                mChildList, CHILD_LAYOUT, LAST_CHILD_LAYOUT,
                COLUMNS_CHILD_FROM, VIEWS_CHILD_TO);

        result = mSimpleExpandableListAdapter.newGroupView(true, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertNotNull(result.findViewById(android.R.id.text1));

        result = mSimpleExpandableListAdapter.newGroupView(false, mAdapterHost);
        assertTrue(result instanceof TwoLineListItem);
        assertNotNull(result.findViewById(android.R.id.text1));
    }

    @Test
    public void testIsChildSelectable() {
        assertTrue(mSimpleExpandableListAdapter.isChildSelectable(0, 0));
        assertTrue(mSimpleExpandableListAdapter.isChildSelectable(3, 3));

        // following indexes are out of bounds
        assertTrue(mSimpleExpandableListAdapter.isChildSelectable(-1, 0));
        assertTrue(mSimpleExpandableListAdapter.isChildSelectable(0, -1));
        assertTrue(mSimpleExpandableListAdapter.isChildSelectable(4, 0));
        assertTrue(mSimpleExpandableListAdapter.isChildSelectable(0, 1));
    }

    @Test
    public void testHasStableIds() {
        assertTrue(mSimpleExpandableListAdapter.hasStableIds());
    }

    /**
     * Creates the test list.
     *
     * @param colCount the column count
     * @param rowCount the row count
     * @param prefix the prefix
     * @return the array list< hash map< string, string>>
     */
    private ArrayList<HashMap<String, String>> createTestList(int colCount, int rowCount,
            String prefix) {
        ArrayList<HashMap<String, String>> list = new ArrayList<>();
        String[] columns = new String[colCount];
        for (int i = 0; i < colCount; i++) {
            columns[i] = "column" + i;
        }

        for (int i = 0; i < rowCount; i++) {
            HashMap<String, String> row = new HashMap<>();
            for (int j = 0; j < colCount; j++) {
                row.put(columns[j], prefix + i + "" + j);
            }
            list.add(row);
        }

        return list;
    }
}
