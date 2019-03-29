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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.database.DataSetObserver;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Test {@link HeaderViewListAdapter}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HeaderViewListAdapterTest {
    private Context mContext;
    private HeaderViewFullAdapter mFullAdapter;
    private HeaderViewEmptyAdapter mEmptyAdapter;

    @UiThreadTest
    @Before
    public void setup() throws Throwable {
        mContext = InstrumentationRegistry.getTargetContext();
        mFullAdapter = new HeaderViewFullAdapter();
        mEmptyAdapter = new HeaderViewEmptyAdapter();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<ListView.FixedViewInfo>();
        ArrayList<ListView.FixedViewInfo> footer = new ArrayList<ListView.FixedViewInfo>(5);
        new HeaderViewListAdapter(header, footer, null);

        new HeaderViewListAdapter(header, footer, mEmptyAdapter);
    }

    @Test
    public void testGetHeadersCount() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertEquals(0, headerViewListAdapter.getHeadersCount());

        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<ListView.FixedViewInfo>(4);
        header.add(lv.new FixedViewInfo());
        headerViewListAdapter = new HeaderViewListAdapter(header, null, null);
        assertEquals(1, headerViewListAdapter.getHeadersCount());
    }

    @Test
    public void testGetFootersCount() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertEquals(0, headerViewListAdapter.getFootersCount());

        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> footer = new ArrayList<ListView.FixedViewInfo>(4);
        footer.add(lv.new FixedViewInfo());
        headerViewListAdapter = new HeaderViewListAdapter(null, footer, null);
        assertEquals(1, headerViewListAdapter.getFootersCount());
    }

    @UiThreadTest
    @Test
    public void testIsEmpty() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertTrue(headerViewListAdapter.isEmpty());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mEmptyAdapter);
        assertTrue(headerViewListAdapter.isEmpty());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertFalse(headerViewListAdapter.isEmpty());
    }

    @Test
    public void testRemoveHeader() {
        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<ListView.FixedViewInfo>(4);
        ListView lv1 = new ListView(mContext);
        ListView lv2 = new ListView(mContext);
        ListView.FixedViewInfo info1 = lv.new FixedViewInfo();
        info1.view = lv1;
        ListView.FixedViewInfo info2 = lv.new FixedViewInfo();
        info2.view = lv2;
        header.add(info1);
        header.add(info2);
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(header, null, null);
        assertEquals(2, headerViewListAdapter.getHeadersCount());
        assertFalse(headerViewListAdapter.removeHeader(new ListView(mContext)));
        assertTrue(headerViewListAdapter.removeHeader(lv1));
        assertEquals(1, headerViewListAdapter.getHeadersCount());

        headerViewListAdapter.removeHeader(null);
    }

    @Test
    public void testRemoveFooter() {
        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> footer = new ArrayList<ListView.FixedViewInfo>(4);
        ListView lv1 = new ListView(mContext);
        ListView lv2 = new ListView(mContext);
        ListView.FixedViewInfo info1 = lv.new FixedViewInfo();
        info1.view = lv1;
        ListView.FixedViewInfo info2 = lv.new FixedViewInfo();
        info2.view = lv2;
        footer.add(info1);
        footer.add(info2);
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, footer, null);
        assertEquals(2, headerViewListAdapter.getFootersCount());
        assertFalse(headerViewListAdapter.removeFooter(new ListView(mContext)));
        assertTrue(headerViewListAdapter.removeFooter(lv1));
        assertEquals(1, headerViewListAdapter.getFootersCount());

        headerViewListAdapter.removeFooter(null);
    }

    @UiThreadTest
    @Test
    public void testGetCount() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertEquals(0, headerViewListAdapter.getCount());

        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<ListView.FixedViewInfo>(4);
        Object data1 = new Object();
        Object data2 = new Object();
        ListView.FixedViewInfo info1 = lv.new FixedViewInfo();
        info1.data = data1;
        ListView.FixedViewInfo info2 = lv.new FixedViewInfo();
        info2.data = data2;
        header.add(info1);
        header.add(info2);
        ArrayList<ListView.FixedViewInfo> footer = new ArrayList<ListView.FixedViewInfo>(4);
        Object data3 = new Object();
        Object data4 = new Object();
        ListView.FixedViewInfo info3 = lv.new FixedViewInfo();
        info3.data = data3;
        ListView.FixedViewInfo info4 = lv.new FixedViewInfo();
        info4.data = data4;
        footer.add(info3);
        footer.add(info4);

        headerViewListAdapter = new HeaderViewListAdapter(header, footer, mEmptyAdapter);
        // 4 is header's count + footer's count + emptyAdapter's count
        assertEquals(4, headerViewListAdapter.getCount());

        headerViewListAdapter = new HeaderViewListAdapter(header, footer, mFullAdapter);
        // 5 is header's count + footer's count + fullAdapter's count
        assertEquals(5, headerViewListAdapter.getCount());
    }

    @Test
    public void testAreAllItemsEnabled() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertTrue(headerViewListAdapter.areAllItemsEnabled());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertTrue(headerViewListAdapter.areAllItemsEnabled());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mEmptyAdapter);
        assertFalse(headerViewListAdapter.areAllItemsEnabled());
    }

    @Test
    public void testIsEnabled() {
        HeaderViewListAdapter headerViewListAdapter =
            new HeaderViewListAdapter(null, null, mFullAdapter);
        assertTrue(headerViewListAdapter.isEnabled(0));

        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<ListView.FixedViewInfo>(4);
        header.add(lv.new FixedViewInfo());
        headerViewListAdapter = new HeaderViewListAdapter(header, null, mFullAdapter);
        assertFalse(headerViewListAdapter.isEnabled(0));
        assertTrue(headerViewListAdapter.isEnabled(1));

        ArrayList<ListView.FixedViewInfo> footer = new ArrayList<ListView.FixedViewInfo>(4);
        footer.add(lv.new FixedViewInfo());
        footer.add(lv.new FixedViewInfo());
        headerViewListAdapter = new HeaderViewListAdapter(header, footer, mFullAdapter);
        assertFalse(headerViewListAdapter.isEnabled(0));
        assertTrue(headerViewListAdapter.isEnabled(1));
        assertFalse(headerViewListAdapter.isEnabled(2));
        assertFalse(headerViewListAdapter.isEnabled(3));

        headerViewListAdapter = new HeaderViewListAdapter(null, footer, mFullAdapter);
        assertTrue(headerViewListAdapter.isEnabled(0));
        assertFalse(headerViewListAdapter.isEnabled(1));
        assertFalse(headerViewListAdapter.isEnabled(2));
    }

    @Test
    public void testGetItem() {
        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<ListView.FixedViewInfo>(4);
        Object data1 = new Object();
        Object data2 = new Object();
        ListView.FixedViewInfo info1 = lv.new FixedViewInfo();
        info1.data = data1;
        ListView.FixedViewInfo info2 = lv.new FixedViewInfo();
        info2.data = data2;
        header.add(info1);
        header.add(info2);
        ArrayList<ListView.FixedViewInfo> footer = new ArrayList<ListView.FixedViewInfo>(4);
        Object data3 = new Object();
        Object data4 = new Object();
        ListView.FixedViewInfo info3 = lv.new FixedViewInfo();
        info3.data = data3;
        ListView.FixedViewInfo info4 = lv.new FixedViewInfo();
        info4.data = data4;
        footer.add(info3);
        footer.add(info4);

        HeaderViewListAdapter headerViewListAdapter =
            new HeaderViewListAdapter(header, footer, mFullAdapter);
        assertSame(data1, headerViewListAdapter.getItem(0));
        assertSame(data2, headerViewListAdapter.getItem(1));
        assertSame(mFullAdapter.getItem(0), headerViewListAdapter.getItem(2));
        assertSame(data3, headerViewListAdapter.getItem(3));
        assertSame(data4, headerViewListAdapter.getItem(4));
    }

    @Test
    public void testGetItemId() {
        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<ListView.FixedViewInfo>(4);
        ListView lv1 = new ListView(mContext);
        ListView lv2 = new ListView(mContext);
        ListView.FixedViewInfo info1 = lv.new FixedViewInfo();
        info1.view = lv1;
        ListView.FixedViewInfo info2 = lv.new FixedViewInfo();
        info2.view = lv2;
        header.add(info1);
        header.add(info2);

        HeaderViewListAdapter headerViewListAdapter =
            new HeaderViewListAdapter(header, null, mFullAdapter);
        assertEquals(-1, headerViewListAdapter.getItemId(0));
        assertEquals(mFullAdapter.getItemId(0), headerViewListAdapter.getItemId(2));
    }

    @Test
    public void testHasStableIds() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertFalse(headerViewListAdapter.hasStableIds());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertTrue(headerViewListAdapter.hasStableIds());
    }

    @Test
    public void testGetView() {
        ListView lv = new ListView(mContext);
        ArrayList<ListView.FixedViewInfo> header = new ArrayList<>(4);
        ListView lv1 = new ListView(mContext);
        ListView lv2 = new ListView(mContext);
        ListView.FixedViewInfo info1 = lv.new FixedViewInfo();
        info1.view = lv1;
        ListView.FixedViewInfo info2 = lv.new FixedViewInfo();
        info2.view = lv2;
        header.add(info1);
        header.add(info2);

        // No adapter, just header
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(header, null, null);
        assertSame(lv1, headerViewListAdapter.getView(0, null, null));
        assertSame(lv2, headerViewListAdapter.getView(1, null, null));

        // Adapter only
        View expected = mFullAdapter.getView(0, null, null);
        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertSame(expected, headerViewListAdapter.getView(0, null, null));

        // Header and adapter
        headerViewListAdapter = new HeaderViewListAdapter(header, null, mFullAdapter);
        assertSame(lv1, headerViewListAdapter.getView(0, null, null));
        assertSame(lv2, headerViewListAdapter.getView(1, null, null));
        assertSame(expected, headerViewListAdapter.getView(2, null, null));
    }

    @Test
    public void testGetItemViewType() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertEquals(AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER,
                headerViewListAdapter.getItemViewType(0));

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertEquals(AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER,
                headerViewListAdapter.getItemViewType(-1));
        assertEquals(0, headerViewListAdapter.getItemViewType(0));
        assertEquals(AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER,
                headerViewListAdapter.getItemViewType(2));
    }

    @Test
    public void testGetViewTypeCount() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertEquals(1, headerViewListAdapter.getViewTypeCount());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertEquals(mFullAdapter.getViewTypeCount(), headerViewListAdapter.getViewTypeCount());
    }

    @Test
    public void testRegisterDataSetObserver() {
        HeaderViewListAdapter headerViewListAdapter =
            new HeaderViewListAdapter(null, null, mFullAdapter);
        DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        headerViewListAdapter.registerDataSetObserver(mockDataSetObserver);
        assertSame(mockDataSetObserver, mFullAdapter.getDataSetObserver());
    }

    @Test
    public void testUnregisterDataSetObserver() {
        HeaderViewListAdapter headerViewListAdapter =
            new HeaderViewListAdapter(null, null, mFullAdapter);
        DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        headerViewListAdapter.registerDataSetObserver(mockDataSetObserver);

        headerViewListAdapter.unregisterDataSetObserver(null);
        assertSame(mockDataSetObserver, mFullAdapter.getDataSetObserver());
        headerViewListAdapter.unregisterDataSetObserver(mockDataSetObserver);
        assertNull(mFullAdapter.getDataSetObserver());
    }

    @UiThreadTest
    @Test
    public void testGetFilter() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertNull(headerViewListAdapter.getFilter());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertNull(headerViewListAdapter.getFilter());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mEmptyAdapter);
        assertSame(mEmptyAdapter.getFilter(), headerViewListAdapter.getFilter());
    }

    @Test
    public void testGetWrappedAdapter() {
        HeaderViewListAdapter headerViewListAdapter = new HeaderViewListAdapter(null, null, null);
        assertNull(headerViewListAdapter.getWrappedAdapter());

        headerViewListAdapter = new HeaderViewListAdapter(null, null, mFullAdapter);
        assertSame(mFullAdapter, headerViewListAdapter.getWrappedAdapter());
    }

    private class HeaderViewEmptyAdapter implements ListAdapter, Filterable {
        private final HeaderViewFilterTest mFilter;

        public HeaderViewEmptyAdapter() {
            mFilter = new HeaderViewFilterTest();
        }

        public boolean areAllItemsEnabled() {
            return false;
        }

        public boolean isEnabled(int position) {
            return true;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
        }

        public int getCount() {
            return 0;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        public boolean hasStableIds() {
            return false;
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            return null;
        }

        public int getItemViewType(int position) {
            return 0;
        }
        public int getViewTypeCount() {
            return 1;
        }

        public boolean isEmpty() {
            return true;
        }

        public Filter getFilter() {
            return mFilter;
        }
    }

    private class HeaderViewFullAdapter implements ListAdapter {
        private DataSetObserver mObserver;
        private Object mItem;
        private final View mView = new View(mContext);

        public DataSetObserver getDataSetObserver() {
            return mObserver;
        }

        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean isEnabled(int position) {
            return true;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mObserver = observer;
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (mObserver == observer) {
                mObserver = null;
            }
        }

        public int getCount() {
            return 1;
        }

        public Object getItem(int position) {
            if (mItem == null) {
                mItem = new Object();
            }
            return mItem;
        }

        public long getItemId(int position) {
            return position;
        }

        public boolean hasStableIds() {
            return true;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return mView;
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
    }

    private static class HeaderViewFilterTest extends Filter {
        @Override
        protected Filter.FilterResults performFiltering(CharSequence constraint) {
            return null;
        }

        @Override
        protected void publishResults(CharSequence constraint, Filter.FilterResults results) {
        }
    }
}
