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
import static org.junit.Assert.assertNull;
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
import android.widget.BaseAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link BaseAdapter}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BaseAdapterTest {
    @Test
    public void testHasStableIds() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        assertFalse(baseAdapter.hasStableIds());
    }

    @Test
    public void testDataSetObserver() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);

        verifyZeroInteractions(mockDataSetObserver);
        baseAdapter.notifyDataSetChanged();
        verifyZeroInteractions(mockDataSetObserver);

        baseAdapter.registerDataSetObserver(mockDataSetObserver);
        baseAdapter.notifyDataSetChanged();
        verify(mockDataSetObserver, times(1)).onChanged();

        reset(mockDataSetObserver);
        verifyZeroInteractions(mockDataSetObserver);
        baseAdapter.unregisterDataSetObserver(mockDataSetObserver);
        baseAdapter.notifyDataSetChanged();
        verifyZeroInteractions(mockDataSetObserver);
    }

    @Test
    public void testNotifyDataSetInvalidated() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);

        verifyZeroInteractions(mockDataSetObserver);
        baseAdapter.notifyDataSetInvalidated();
        verifyZeroInteractions(mockDataSetObserver);

        baseAdapter.registerDataSetObserver(mockDataSetObserver);
        baseAdapter.notifyDataSetInvalidated();
        verify(mockDataSetObserver, times(1)).onInvalidated();
    }

    @Test
    public void testAreAllItemsEnabled() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        assertTrue(baseAdapter.areAllItemsEnabled());
    }

    @Test
    public void testIsEnabled() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        assertTrue(baseAdapter.isEnabled(0));
    }

    @Test
    public void testGetDropDownView() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        assertNull(baseAdapter.getDropDownView(0, null, null));
    }

    @Test
    public void testGetItemViewType() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        assertEquals(0, baseAdapter.getItemViewType(0));
    }

    @Test
    public void testGetViewTypeCount() {
        BaseAdapter baseAdapter = new MockBaseAdapter();
        assertEquals(1, baseAdapter.getViewTypeCount());
    }

    @Test
    public void testIsEmpty() {
        MockBaseAdapter baseAdapter = new MockBaseAdapter();

        baseAdapter.setCount(0);
        assertTrue(baseAdapter.isEmpty());

        baseAdapter.setCount(1);
        assertFalse(baseAdapter.isEmpty());
    }

    @Test
    public void testGetAutofillOptions() {
        MockBaseAdapter baseAdapter = new MockBaseAdapter();
        assertNull(baseAdapter.getAutofillOptions());

        baseAdapter.setAutofillOptions("single");
        CharSequence[] single = baseAdapter.getAutofillOptions();
        assertEquals(1, single.length);
        assertEquals("single", single[0]);

        baseAdapter.setAutofillOptions("mult1", "mult2");
        CharSequence[] multiple = baseAdapter.getAutofillOptions();
        assertEquals(2, multiple.length);
        assertEquals("mult1", multiple[0]);
        assertEquals("mult2", multiple[1]);
    }

    private static class MockBaseAdapter extends BaseAdapter {
        private int mCount = 0;

        public void setCount(int count) {
            mCount = count;
        }

        public int getCount() {
            return mCount;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return null;
        }
    }
}
