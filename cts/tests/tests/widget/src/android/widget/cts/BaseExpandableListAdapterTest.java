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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.database.DataSetObserver;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link BaseExpandableListAdapter}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BaseExpandableListAdapterTest {
    @Test
    public void testDefaults() {
        // Test child type / group type APIs for the default values documented in the method
        // Javadocs
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        assertEquals(1, adapter.getGroupTypeCount());
        assertEquals(0, adapter.getGroupType(0));
        assertEquals(1, adapter.getChildTypeCount());
        assertEquals(0, adapter.getChildType(0, 0));
    }

    @Test
    public void testAreAllItemsEnabled() {
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        assertTrue(adapter.areAllItemsEnabled());
    }

    @Test
    public void testGetCombinedId() {
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();

        long childID = adapter.getCombinedChildId(10, 100);
        long groupID = adapter.getCombinedGroupId(10);

        // there should be no clash in group and child IDs
        assertTrue(childID != groupID);

        childID = adapter.getCombinedChildId(0, 0);
        groupID = adapter.getCombinedGroupId(0);
        assertTrue(childID != groupID);
    }

    @Test
    public void testIsEmpty() {
        MockBaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        assertTrue(adapter.isEmpty());
        adapter.setGroupCount(10);
        assertFalse(adapter.isEmpty());
    }

    @Test
    public void testNotifyDataSetChanged() {
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        adapter.registerDataSetObserver(mockDataSetObserver);

        verifyZeroInteractions(mockDataSetObserver);
        adapter.notifyDataSetChanged();
        verify(mockDataSetObserver, times(1)).onChanged();
    }

    @Test
    public void testNotifyDataSetInvalidated() {
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        adapter.registerDataSetObserver(mockDataSetObserver);

        verifyZeroInteractions(mockDataSetObserver);
        adapter.notifyDataSetInvalidated();
        verify(mockDataSetObserver, times(1)).onInvalidated();
    }

    @Test
    public void testOnGroupCollapsed() {
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        // this function is non-operation.
        adapter.onGroupCollapsed(0);
    }

    @Test
    public void testOnGroupExpanded() {
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        // this function is non-operation.
        adapter.onGroupExpanded(0);
    }

    @Test
    public void testDataSetObserver() {
        BaseExpandableListAdapter adapter = new MockBaseExpandableListAdapter();
        DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        adapter.registerDataSetObserver(mockDataSetObserver);

        verifyZeroInteractions(mockDataSetObserver);
        adapter.notifyDataSetChanged();
        verify(mockDataSetObserver, times(1)).onChanged();

        reset(mockDataSetObserver);
        verifyZeroInteractions(mockDataSetObserver);
        adapter.unregisterDataSetObserver(mockDataSetObserver);
        adapter.notifyDataSetChanged();
        verifyZeroInteractions(mockDataSetObserver);
    }

    private class MockBaseExpandableListAdapter extends BaseExpandableListAdapter {
        private int mGroupCount;

        public Object getChild(int groupPosition, int childPosition) {
            return null;
        }

        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        public View getChildView(int groupPosition, int childPosition,
                boolean isLastChild, View convertView, ViewGroup parent) {
            return null;
        }

        public int getChildrenCount(int groupPosition) {
            return 0;
        }

        public Object getGroup(int groupPosition) {
            return null;
        }

        public int getGroupCount() {
            return mGroupCount;
        }

        public void setGroupCount(int count) {
            mGroupCount = count;
        }

        public long getGroupId(int groupPosition) {
            return 0;
        }

        public View getGroupView(int groupPosition, boolean isExpanded,
                View convertView, ViewGroup parent) {
            return null;
        }

        public boolean hasStableIds() {
            return false;
        }

        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }
}
