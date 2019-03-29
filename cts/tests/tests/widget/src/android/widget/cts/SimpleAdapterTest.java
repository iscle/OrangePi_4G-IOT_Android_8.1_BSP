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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Test {@link SimpleAdapter}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SimpleAdapterTest {
    private static final int DEFAULT_ROW_COUNT = 20;

    private static final int DEFAULT_COLUMN_COUNT = 2;

    private static final int[] VIEWS_TO = new int[] { android.R.id.text1 };

    private static final String[] COLUMNS_FROM = new String[] { "column1" };

    /**
     * The original cursor and its content will be set to:
     * <TABLE>
     * <TR>
     * <TH>Column0</TH>
     * <TH>Column1</TH>
     * </TR>
     * <TR>
     * <TD>00</TD>
     * <TD>01</TD>
     * </TR>
     * <TR>
     * <TD>10</TD>
     * <TD>11</TD>
     * </TR>
     * <TR>
     * <TD>...</TD>
     * <TD>...</TD>
     * </TR>
     * <TR>
     * <TD>190</TD>
     * <TD>191</TD>
     * </TR>
     * </TABLE>
     * It has 2 columns and 20 rows. The default layout for item
     * is R.layout.simple_list_item_1
     */
    private SimpleAdapter mSimpleAdapter;

    private Context mContext;

    private LinearLayout mAdapterHost;

    private LayoutInflater mInflater;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mAdapterHost = (LinearLayout) mInflater.inflate(
                R.layout.cursoradapter_host, null);

        // new the SimpleAdapter instance
        mSimpleAdapter = new SimpleAdapter(mContext,
                createTestList(DEFAULT_COLUMN_COUNT, DEFAULT_ROW_COUNT),
                android.R.layout.simple_list_item_1, COLUMNS_FROM, VIEWS_TO);
    }

    @Test
    public void testConstructor() {
        new SimpleAdapter(mContext, createTestList(DEFAULT_COLUMN_COUNT, DEFAULT_ROW_COUNT),
                android.R.layout.simple_list_item_1, COLUMNS_FROM, VIEWS_TO);
    }

    @Test
    public void testGetCount() {
        mSimpleAdapter = new SimpleAdapter(mContext,
                createTestList(DEFAULT_COLUMN_COUNT, DEFAULT_ROW_COUNT),
                android.R.layout.simple_list_item_1, COLUMNS_FROM, VIEWS_TO);
        assertEquals(20, mSimpleAdapter.getCount());

        mSimpleAdapter = new SimpleAdapter(mContext, createTestList(DEFAULT_COLUMN_COUNT, 10),
                android.R.layout.simple_list_item_1, COLUMNS_FROM, VIEWS_TO);
        assertEquals(10, mSimpleAdapter.getCount());
    }

    @Test
    public void testGetItem() {
        assertEquals("01", ((Map<?, ?>) mSimpleAdapter.getItem(0)).get("column1"));
        assertEquals("191", ((Map<?, ?>) mSimpleAdapter.getItem(19)).get("column1"));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetItemIndexTooLow() {
        mSimpleAdapter.getItem(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetItemIndexTooHigh() {
        mSimpleAdapter.getItem(20);
    }

    @Test
    public void testGetItemId() {
        assertEquals(0, mSimpleAdapter.getItemId(0));

        assertEquals(19, mSimpleAdapter.getItemId(19));

        // are the following behaviors correct?
        assertEquals(-1, mSimpleAdapter.getItemId(-1));

        assertEquals(20, mSimpleAdapter.getItemId(20));
    }

    @UiThreadTest
    @Test
    public void testGetView() {
        // use the layout passed in to constructor
        View result = mSimpleAdapter.getView(0, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("01", ((TextView) result).getText().toString());

        result = mSimpleAdapter.getView(19, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("191", ((TextView) result).getText().toString());

        // use the previous result as the convert view
        // the param ViewGroup is never read
        TextView convertView = (TextView) result;
        result = mSimpleAdapter.getView(0, convertView, mAdapterHost);
        assertEquals("01", ((TextView) result).getText().toString());
        assertSame(convertView, result);

        // parent can be null
        result = mSimpleAdapter.getView(10, convertView, null);
        assertEquals("101", ((TextView) result).getText().toString());

        // the binder takes care of binding, the param ViewGroup is never read
        SimpleAdapter.ViewBinder binder = mock(SimpleAdapter.ViewBinder.class);
        doReturn(true).when(binder).setViewValue(any(View.class), any(Object.class), anyString());
        mSimpleAdapter.setViewBinder(binder);
        mSimpleAdapter.getView(0, null, mAdapterHost);
        verify(binder, times(1)).setViewValue(any(View.class), eq("01"), anyString());

        // binder try binding but fail
        doReturn(false).when(binder).setViewValue(any(View.class), any(Object.class), anyString());
        reset(binder);
        result = mSimpleAdapter.getView(0, null, mAdapterHost);
        verify(binder, times(1)).setViewValue(any(View.class), eq("01"), anyString());
        assertEquals("01", ((TextView) result).getText().toString());
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetViewIndexTooLow() {
        View result = mSimpleAdapter.getView(0, null, mAdapterHost);
        mSimpleAdapter.getView(-1, result, null);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetViewIndexTooHigh() {
        View result = mSimpleAdapter.getView(0, null, mAdapterHost);
        mSimpleAdapter.getView(20, result, null);
    }

    @UiThreadTest
    @Test
    public void testSetDropDownViewResource() {
        mSimpleAdapter.setDropDownViewResource(android.R.layout.simple_list_item_2);
        View result = mSimpleAdapter.getDropDownView(0, null, mAdapterHost);
        assertTrue(result instanceof TwoLineListItem);
        assertEquals("01",
                ((TextView) result.findViewById(android.R.id.text1)).getText().toString());

        result = mSimpleAdapter.getDropDownView(19, null, mAdapterHost);
        assertTrue(result instanceof TwoLineListItem);
        assertEquals("191",
                ((TextView) result.findViewById(android.R.id.text1)).getText().toString());

        mSimpleAdapter.setDropDownViewResource(android.R.layout.simple_list_item_1);
        result = mSimpleAdapter.getDropDownView(0, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("01", ((TextView) result).getText().toString());

        result = mSimpleAdapter.getDropDownView(19, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("191", ((TextView) result).getText().toString());
    }

    @UiThreadTest
    @Test
    public void testGetDropDownView() {
        View result = mSimpleAdapter.getDropDownView(0, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("01", ((TextView) result).getText().toString());

        result = mSimpleAdapter.getDropDownView(19, null, mAdapterHost);
        assertTrue(result instanceof TextView);
        assertEquals("191", ((TextView) result).getText().toString());

        // use the previous result as the convert view
        TextView convertView = (TextView) result;
        // the param ViewGroup is never read
        result = mSimpleAdapter.getDropDownView(0, convertView, mAdapterHost);
        assertEquals("01", convertView.getText().toString());
        assertSame(convertView, result);

        // The parent can be null
        result = mSimpleAdapter.getDropDownView(10, convertView, null);
        assertEquals("101", ((TextView) result).getText().toString());

        // the binder takes care of binding, the param ViewGroup is never read
        SimpleAdapter.ViewBinder binder = mock(SimpleAdapter.ViewBinder.class);
        doReturn(true).when(binder).setViewValue(any(View.class), any(Object.class), anyString());
        mSimpleAdapter.setViewBinder(binder);
        mSimpleAdapter.getDropDownView(19, null, mAdapterHost);
        verify(binder, times(1)).setViewValue(any(View.class), eq("191"), anyString());

        // binder try binding but fail
        doReturn(false).when(binder).setViewValue(any(View.class), any(Object.class), anyString());
        reset(binder);
        result = mSimpleAdapter.getDropDownView(19, null, mAdapterHost);
        verify(binder, times(1)).setViewValue(any(View.class), eq("191"), anyString());
        assertEquals("191", ((TextView) result).getText().toString());
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetDropDownViewIndexTooLow() {
        View result = mSimpleAdapter.getDropDownView(0, null, mAdapterHost);
        mSimpleAdapter.getDropDownView(-1, result, null);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetDropDownViewIndexTooHigh() {
        View result = mSimpleAdapter.getDropDownView(0, null, mAdapterHost);
        mSimpleAdapter.getDropDownView(20, result, null);
    }

    @Test
    public void testAccessDropDownViewTheme() {
        Theme theme = mContext.getResources().newTheme();
        mSimpleAdapter.setDropDownViewTheme(theme);
        assertSame(theme, mSimpleAdapter.getDropDownViewTheme());
    }

    @Test
    public void testAccessViewBinder() {
        // no binder default
        assertNull(mSimpleAdapter.getViewBinder());

        // binder takes care of binding
        SimpleAdapter.ViewBinder binder = mock(SimpleAdapter.ViewBinder.class);
        doReturn(true).when(binder).setViewValue(any(View.class), any(Object.class), anyString());
        mSimpleAdapter.setViewBinder(binder);
        assertSame(binder, mSimpleAdapter.getViewBinder());

        // binder try binding but fail
        doReturn(false).when(binder).setViewValue(any(View.class), any(Object.class), anyString());
        mSimpleAdapter.setViewBinder(binder);
        assertSame(binder, mSimpleAdapter.getViewBinder());

        mSimpleAdapter.setViewBinder(null);
        assertNull(mSimpleAdapter.getViewBinder());
    }

    @Test
    public void testSetViewImage() {
        // String represents resId
        ImageView view = new ImageView(mContext);
        assertNull(view.getDrawable());
        mSimpleAdapter.setViewImage(view, String.valueOf(R.drawable.scenery));
        BitmapDrawable d = (BitmapDrawable) mContext.getDrawable(R.drawable.scenery);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) view.getDrawable()).getBitmap());

        // blank
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        mSimpleAdapter.setViewImage(view, "");
        assertNull(view.getDrawable());

        // null
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        try {
            // Should declare NullPoinertException if the uri or value is null
            mSimpleAdapter.setViewImage(view, null);
            fail("Should throw NullPointerException if the uri or value is null");
        } catch (NullPointerException e) {
        }

        // resId
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        mSimpleAdapter.setViewImage(view, R.drawable.scenery);
        d = (BitmapDrawable) mContext.getDrawable(R.drawable.scenery);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) view.getDrawable()).getBitmap());

        // illegal resid
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        mSimpleAdapter.setViewImage(view, Integer.MAX_VALUE);
        assertNull(view.getDrawable());

        // uri
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        try {
            mSimpleAdapter.setViewImage(view, SimpleCursorAdapterTest.createTestImage(mContext,
                    "testimage", R.raw.testimage));
            assertNotNull(view.getDrawable());
            Bitmap actualBitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
            Bitmap testBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mContext.getResources(), R.raw.testimage, actualBitmap.getConfig());
            WidgetTestUtils.assertEquals(testBitmap, actualBitmap);
        } finally {
            SimpleCursorAdapterTest.destroyTestImage(mContext,"testimage");
        }
    }

    @UiThreadTest
    @Test
    public void testSetViewText() {
        TextView view = new TextView(mContext);
        mSimpleAdapter.setViewText(view, "expected");
        assertEquals("expected", view.getText().toString());

        mSimpleAdapter.setViewText(view, null);
        assertEquals("", view.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testGetFilter() {
        assertNotNull(mSimpleAdapter.getFilter());
    }

    /**
     * Creates the test list.
     *
     * @param colCount the column count
     * @param rowCount the row count
     * @return the array list< hash map< string, string>>, Example:if the
     *         colCount = 2 and rowCount = 3, the list will be { {column0=>00,
     *         column1=>01}, {column0=>10, column1=>11}, {column0=>20,
     *         column1=>21} }
     */
    private ArrayList<HashMap<String, String>> createTestList(int colCount, int rowCount) {
        ArrayList<HashMap<String, String>> list = new ArrayList<>();
        String[] columns = new String[colCount];
        for (int i = 0; i < colCount; i++) {
            columns[i] = "column" + i;
        }

        for (int i = 0; i < rowCount; i++) {
            HashMap<String, String> row = new HashMap<>();
            for (int j = 0; j < colCount; j++) {
                row.put(columns[j], "" + i + "" + j);
            }
            list.add(row);
        }

        return list;
    }
}
