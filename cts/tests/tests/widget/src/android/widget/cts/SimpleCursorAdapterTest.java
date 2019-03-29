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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Test {@link SimpleCursorAdapter}.
 * The simple cursor adapter's cursor will be set to
 * {@link SimpleCursorAdapterTest#mCursor} It will use internal
 * R.layout.simple_list_item_1.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SimpleCursorAdapterTest {
    private static final int ADAPTER_ROW_COUNT = 20;

    private static final int DEFAULT_COLUMN_COUNT = 2;

    private static final int[] VIEWS_TO = new int[] { R.id.cursorAdapter_item0 };

    private static final String[] COLUMNS_FROM = new String[] { "column1" };

    private static final String SAMPLE_IMAGE_NAME = "testimage.jpg";

    private Context mContext;

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
     * It has 2 columns and 20 rows
     */
    private Cursor mCursor;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();

        mCursor = createTestCursor(DEFAULT_COLUMN_COUNT, ADAPTER_ROW_COUNT);
    }

    private SimpleCursorAdapter makeSimpleCursorAdapter() {
        return new SimpleCursorAdapter(
                mContext, R.layout.cursoradapter_item0, mCursor, COLUMNS_FROM, VIEWS_TO);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new SimpleCursorAdapter(mContext, R.layout.cursoradapter_item0,
                createTestCursor(DEFAULT_COLUMN_COUNT, ADAPTER_ROW_COUNT),
                COLUMNS_FROM, VIEWS_TO);
    }

    @UiThreadTest
    @Test
    public void testBindView() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        TextView listItem = (TextView) simpleCursorAdapter.newView(mContext, null, null);

        listItem.setText("");
        mCursor.moveToFirst();
        simpleCursorAdapter.bindView(listItem, null, mCursor);
        assertEquals("01", listItem.getText().toString());

        mCursor.moveToLast();
        simpleCursorAdapter.bindView(listItem, null, mCursor);
        assertEquals("191", listItem.getText().toString());

        // the binder take care of binding
        listItem.setText("");
        SimpleCursorAdapter.ViewBinder binder = mock(SimpleCursorAdapter.ViewBinder.class);
        doReturn(true).when(binder).setViewValue(any(View.class), any(Cursor.class), anyInt());
        simpleCursorAdapter.setViewBinder(binder);
        mCursor.moveToFirst();
        simpleCursorAdapter.bindView(listItem, null, mCursor);
        verify(binder, times(1)).setViewValue(any(View.class), eq(mCursor), eq(1));
        assertEquals("", listItem.getText().toString());

        // the binder try to bind but fail
        doReturn(false).when(binder).setViewValue(any(View.class), any(Cursor.class), anyInt());
        reset(binder);
        mCursor.moveToLast();
        simpleCursorAdapter.bindView(listItem, null, mCursor);
        verify(binder, times(1)).setViewValue(any(View.class), eq(mCursor), eq(1));
        assertEquals("191", listItem.getText().toString());

        final int [] to = { R.id.cursorAdapter_host };
        simpleCursorAdapter = new SimpleCursorAdapter(mContext, R.layout.cursoradapter_host,
                mCursor, COLUMNS_FROM, to);
        LinearLayout illegalView = (LinearLayout)simpleCursorAdapter.newView(mContext, null, null);
        try {
            // The IllegalStateException already gets thrown in the line above.
            simpleCursorAdapter.bindView(illegalView, null, mCursor);
            fail("Should throw IllegalStateException if the view is not TextView or ImageView");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @UiThreadTest
    @Test
    public void testAccessViewBinder() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        assertNull(simpleCursorAdapter.getViewBinder());

        SimpleCursorAdapter.ViewBinder binder = mock(SimpleCursorAdapter.ViewBinder.class);
        doReturn(true).when(binder).setViewValue(any(View.class), any(Cursor.class), anyInt());
        simpleCursorAdapter.setViewBinder(binder);
        assertSame(binder, simpleCursorAdapter.getViewBinder());

        doReturn(false).when(binder).setViewValue(any(View.class), any(Cursor.class), anyInt());
        simpleCursorAdapter.setViewBinder(binder);
        assertSame(binder, simpleCursorAdapter.getViewBinder());

        simpleCursorAdapter.setViewBinder(null);
        assertNull(simpleCursorAdapter.getViewBinder());
    }

    @UiThreadTest
    @Test
    public void testSetViewText() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        TextView view = new TextView(mContext);
        simpleCursorAdapter.setViewText(view, "expected");
        assertEquals("expected", view.getText().toString());

        simpleCursorAdapter.setViewText(view, null);
        assertEquals("", view.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testSetViewImage() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        // resId
        int sceneryImgResId = R.drawable.scenery;
        ImageView view = new ImageView(mContext);
        assertNull(view.getDrawable());
        simpleCursorAdapter.setViewImage(view, String.valueOf(sceneryImgResId));
        assertNotNull(view.getDrawable());
        BitmapDrawable d = (BitmapDrawable) mContext.getResources().getDrawable(
                sceneryImgResId);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) view.getDrawable()).getBitmap());

        // blank
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        simpleCursorAdapter.setViewImage(view, "");
        assertNull(view.getDrawable());

        // null
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        try {
            // Should declare NullPoinertException if the uri or value is null
            simpleCursorAdapter.setViewImage(view, null);
            fail("Should throw NullPointerException if the uri or value is null");
        } catch (NullPointerException e) {
            // expected
        }

        // uri
        view = new ImageView(mContext);
        assertNull(view.getDrawable());
        try {
            int testimgRawId = R.raw.testimage;
            simpleCursorAdapter.setViewImage(view,
                    createTestImage(mContext, SAMPLE_IMAGE_NAME, testimgRawId));
            assertNotNull(view.getDrawable());
            Bitmap actualBitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
            Bitmap testBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mContext.getResources(), testimgRawId, actualBitmap.getConfig());
            WidgetTestUtils.assertEquals(testBitmap, actualBitmap);
        } finally {
            destroyTestImage(mContext, SAMPLE_IMAGE_NAME);
        }
    }

    @UiThreadTest
    @Test
    public void testAccessStringConversionColumn() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        // default is -1
        assertEquals(-1, simpleCursorAdapter.getStringConversionColumn());

        simpleCursorAdapter.setStringConversionColumn(1);
        assertEquals(1, simpleCursorAdapter.getStringConversionColumn());

        // Should check whether the column index is out of bounds
        simpleCursorAdapter.setStringConversionColumn(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, simpleCursorAdapter.getStringConversionColumn());

        // Should check whether the column index is out of bounds
        simpleCursorAdapter.setStringConversionColumn(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, simpleCursorAdapter.getStringConversionColumn());
    }

    @UiThreadTest
    @Test
    public void testAccessCursorToStringConverter() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        // default is null
        assertNull(simpleCursorAdapter.getCursorToStringConverter());

        SimpleCursorAdapter.CursorToStringConverter converter =
                mock(SimpleCursorAdapter.CursorToStringConverter.class);
        simpleCursorAdapter.setCursorToStringConverter(converter);
        assertSame(converter, simpleCursorAdapter.getCursorToStringConverter());

        simpleCursorAdapter.setCursorToStringConverter(null);
        assertNull(simpleCursorAdapter.getCursorToStringConverter());
    }

    @UiThreadTest
    @Test
    public void testChangeCursor() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        // have "column1"
        Cursor curWith3Columns = createTestCursor(3, ADAPTER_ROW_COUNT);
        simpleCursorAdapter.changeCursor(curWith3Columns);
        assertSame(curWith3Columns, simpleCursorAdapter.getCursor());

        // does not have "column1"
        Cursor curWith1Column = createTestCursor(1, ADAPTER_ROW_COUNT);
        try {
            simpleCursorAdapter.changeCursor(curWith1Column);
            fail("Should throw exception if the cursor does not have the "
                    + "original column passed in the constructor");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @UiThreadTest
    @Test
    public void testConvertToString() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        mCursor.moveToFirst();
        assertEquals("", simpleCursorAdapter.convertToString(null));

        // converter is null, StringConversionColumn is set to negative
        simpleCursorAdapter.setStringConversionColumn(Integer.MIN_VALUE);
        assertEquals(mCursor.toString(), simpleCursorAdapter.convertToString(mCursor));

        // converter is null, StringConversionColumn is set to 1
        simpleCursorAdapter.setStringConversionColumn(1);
        assertEquals("01", simpleCursorAdapter.convertToString(mCursor));

        // converter is null, StringConversionColumn is set to 3 (larger than columns count)
        // the cursor has 3 columns including column0, column1 and _id which is added automatically
        simpleCursorAdapter.setStringConversionColumn(DEFAULT_COLUMN_COUNT + 1);
        try {
            simpleCursorAdapter.convertToString(mCursor);
            fail("Should throw IndexOutOfBoundsException if index is beyond the columns count");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        Cursor curWith3Columns = createTestCursor(DEFAULT_COLUMN_COUNT + 1, ADAPTER_ROW_COUNT);
        curWith3Columns.moveToFirst();

        // converter is null, StringConversionColumn is set to 3
        // and covert with a cursor which has 4 columns
        simpleCursorAdapter.setStringConversionColumn(2);
        assertEquals("02", simpleCursorAdapter.convertToString(curWith3Columns));

        // converter is not null, StringConversionColumn is 1
        SimpleCursorAdapter.CursorToStringConverter converter =
                mock(SimpleCursorAdapter.CursorToStringConverter.class);
        simpleCursorAdapter.setCursorToStringConverter(converter);
        simpleCursorAdapter.setStringConversionColumn(1);
        simpleCursorAdapter.convertToString(curWith3Columns);
        verify(converter, times(1)).convertToString(curWith3Columns);
    }

    @UiThreadTest
    @Test
    public void testNewView() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup viewGroup = (ViewGroup) layoutInflater.inflate(
                R.layout.cursoradapter_host, null);
        View result = simpleCursorAdapter.newView(mContext, null, viewGroup);
        assertNotNull(result);
        assertEquals(R.id.cursorAdapter_item0, result.getId());

        result = simpleCursorAdapter.newView(mContext, null, null);
        assertNotNull(result);
        assertEquals(R.id.cursorAdapter_item0, result.getId());
    }

    @UiThreadTest
    @Test
    public void testNewDropDownView() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup viewGroup = (ViewGroup) layoutInflater.inflate(
                R.layout.cursoradapter_host, null);
        View result = simpleCursorAdapter.newDropDownView(null, null, viewGroup);
        assertNotNull(result);
        assertEquals(R.id.cursorAdapter_item0, result.getId());
    }

    @UiThreadTest
    @Test
    public void testChangeCursorAndColumns() {
        SimpleCursorAdapter simpleCursorAdapter = makeSimpleCursorAdapter();
        assertSame(mCursor, simpleCursorAdapter.getCursor());

        TextView listItem = (TextView) simpleCursorAdapter.newView(mContext, null, null);

        mCursor.moveToFirst();
        simpleCursorAdapter.bindView(listItem, null, mCursor);
        assertEquals("01", listItem.getText().toString());

        mCursor.moveToLast();
        simpleCursorAdapter.bindView(listItem, null, mCursor);
        assertEquals("191", listItem.getText().toString());

        Cursor newCursor = createTestCursor(3, ADAPTER_ROW_COUNT);
        final String[] from = new String[] { "column2" };
        simpleCursorAdapter.changeCursorAndColumns(newCursor, from, VIEWS_TO);
        assertSame(newCursor, simpleCursorAdapter.getCursor());
        newCursor.moveToFirst();
        simpleCursorAdapter.bindView(listItem, null, newCursor);
        assertEquals("02", listItem.getText().toString());

        newCursor.moveToLast();
        simpleCursorAdapter.bindView(listItem, null, newCursor);
        assertEquals("192", listItem.getText().toString());

        simpleCursorAdapter.changeCursorAndColumns(null, null, null);
        assertNull(simpleCursorAdapter.getCursor());
    }

    /**
     * Creates the test cursor.
     *
     * @param colCount the column count
     * @param rowCount the row count
     * @return the cursor
     */
    @SuppressWarnings("unchecked")
    private Cursor createTestCursor(int colCount, int rowCount) {
        String[] columns = new String[colCount + 1];
        for (int i = 0; i < colCount; i++) {
            columns[i] = "column" + i;
        }
        columns[colCount] = "_id";

        MatrixCursor cursor = new MatrixCursor(columns, rowCount);
        Object[] row = new Object[colCount + 1];
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < colCount; j++) {
                row[j] = "" + i + "" + j;
            }
            row[colCount] = i;
            cursor.addRow(row);
        }
        return cursor;
    }

    public static String createTestImage(Context context, String fileName, int resId) {
        try (InputStream source = context.getResources().openRawResource(resId);
             OutputStream target = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        } catch (IOException e) {
            fail(e.getMessage());
        }

        return context.getFileStreamPath(fileName).getAbsolutePath();
    }

    public static void destroyTestImage(Context context, String fileName) {
        context.deleteFile(fileName);
    }
}
