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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.database.DataSetObserver;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ArrayAdapterTest {
    private static final int INVALID_ID = -1;
    private static final String STR1 = "string1";
    private static final String STR2 = "string2";
    private static final String STR3 = "string3";

    private ArrayAdapter<String> mArrayAdapter;
    private Context mContext;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule =
            new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mArrayAdapter = new ArrayAdapter<>(mContext, R.layout.simple_dropdown_item_1line);
    }

    @Test
    public void testConstructor() {
        new ArrayAdapter<String>(mContext, R.layout.simple_dropdown_item_1line);
        new ArrayAdapter<String>(mContext, INVALID_ID); // invalid resource id

        new ArrayAdapter<String>(mContext, R.layout.simple_dropdown_item_1line, R.id.text1);
        new ArrayAdapter<String>(mContext, R.layout.simple_dropdown_item_1line, INVALID_ID);

        new ArrayAdapter<>(mContext, R.layout.simple_dropdown_item_1line,
                new String[] {"str1", "str2"});

        new ArrayAdapter<>(mContext, R.layout.simple_dropdown_item_1line, R.id.text1,
                new String[] {"str1", "str2"});

        List<String> list = new ArrayList<>();
        list.add(STR1);
        list.add(STR2);

        new ArrayAdapter<>(mContext, R.layout.simple_dropdown_item_1line, list);

        new ArrayAdapter<>(mContext, R.layout.simple_dropdown_item_1line, R.id.text1, list);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext() {
        new ArrayAdapter<String>(null, R.layout.simple_dropdown_item_1line);
    }

    @Test
    public void testDataChangeEvent() {
        final DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        mArrayAdapter.registerDataSetObserver(mockDataSetObserver);

        // enable automatically notifying.
        mArrayAdapter.setNotifyOnChange(true);
        verifyZeroInteractions(mockDataSetObserver);
        mArrayAdapter.add(STR1);
        assertEquals(1, mArrayAdapter.getCount());
        verify(mockDataSetObserver, times(1)).onChanged();
        mArrayAdapter.add(STR2);
        assertEquals(2, mArrayAdapter.getCount());
        verify(mockDataSetObserver, times(2)).onChanged();

        // reset data
        mArrayAdapter.clear();
        // clear notify changed
        verify(mockDataSetObserver, times(3)).onChanged();
        assertEquals(0, mArrayAdapter.getCount());
        // if empty before, clear also notify changed
        mArrayAdapter.clear();
        verify(mockDataSetObserver, times(4)).onChanged();

        reset(mockDataSetObserver);

        // disable auto notify
        mArrayAdapter.setNotifyOnChange(false);

        mArrayAdapter.add(STR3);
        assertEquals(1, mArrayAdapter.getCount());
        verifyZeroInteractions(mockDataSetObserver);

        // manually notify
        mArrayAdapter.notifyDataSetChanged();
        verify(mockDataSetObserver, times(1)).onChanged();
        // no data changed, but force notify
        mArrayAdapter.notifyDataSetChanged();
        verify(mockDataSetObserver, times(2)).onChanged();
        // once called notify, auto notify enabled
        mArrayAdapter.add(STR3);
        verify(mockDataSetObserver, times(3)).onChanged();
    }

    @UiThreadTest
    @Test
    public void testAccessView() {
        final TextView textView = new TextView(mContext);
        textView.setText(STR3);

        assertNotNull(mArrayAdapter.getContext());

        assertEquals(0, mArrayAdapter.getCount());

        mArrayAdapter.add(STR1);
        mArrayAdapter.add(STR2);
        mArrayAdapter.add(STR3);

        assertEquals(3, mArrayAdapter.getCount());

        assertEquals(STR1, ((TextView) mArrayAdapter.getView(0, null, null)).getText());
        assertEquals(STR2, ((TextView) mArrayAdapter.getView(1, null, null)).getText());
        assertEquals(STR3, ((TextView) mArrayAdapter.getDropDownView(2, null, null)).getText());

        assertEquals(STR3, textView.getText());
        assertSame(textView, mArrayAdapter.getView(0, textView, null));
        assertSame(textView, mArrayAdapter.getDropDownView(0, textView, null));
        assertEquals(STR1, textView.getText());
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetViewOutOfBoundsLow() {
        final TextView textView = new TextView(mContext);
        mArrayAdapter.getView(-1, textView, null);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testDropDownGetViewOutOfBoundsLow() {
        final TextView textView = new TextView(mContext);
        mArrayAdapter.getDropDownView(-1, textView, null);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetViewOutOfBoundsHigh() {
        final TextView textView = new TextView(mContext);
        mArrayAdapter.getView(mArrayAdapter.getCount(), textView, null);
    }

    @UiThreadTest
    @Test(expected=IndexOutOfBoundsException.class)
    public void testDropDownGetViewOutOfBoundsHigh() {
        final TextView textView = new TextView(mContext);
        mArrayAdapter.getDropDownView(mArrayAdapter.getCount(), textView, null);
    }

    @Test
    public void testGetFilter() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            Filter filter = mArrayAdapter.getFilter();

            assertNotNull(mArrayAdapter.getFilter());
            assertSame(filter, mArrayAdapter.getFilter());
        });
    }

    /**
     * Just simple change the resource id from which the drop view inflate from
     * we set a xml that not contain a textview, so exception should throw to lete us know
     * sucessfully change the dropdown xml, but should not affect the normal view by getview
     */
    @UiThreadTest
    @Test
    public void testSetDropDownViewResource() {
        mArrayAdapter.add(STR1);

        mArrayAdapter.getDropDownView(0, null, null);

        mArrayAdapter.setDropDownViewResource(R.layout.tabhost_layout);
        // getview is ok
        mArrayAdapter.getView(0, null, null);

        mArrayAdapter.setDropDownViewResource(INVALID_ID);
    }

    @UiThreadTest
    @Test(expected=IllegalStateException.class)
    public void testSetDropDownViewResourceIllegal() {
        mArrayAdapter.add(STR1);
        mArrayAdapter.setDropDownViewResource(R.layout.tabhost_layout);
        mArrayAdapter.getDropDownView(0, null, null);
    }

    @Test
    public void testAccessDropDownViewTheme() {
        Theme theme = mContext.getResources().newTheme();
        mArrayAdapter.setDropDownViewTheme(theme);
        assertSame(theme, mArrayAdapter.getDropDownViewTheme());
    }

    /**
     * insert the item to the specific position, notify data changed
     * check -1, normal, > count
     */
    @Test
    public void testInsert() {
        mArrayAdapter.setNotifyOnChange(true);
        final DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        mArrayAdapter.registerDataSetObserver(mockDataSetObserver);

        mArrayAdapter.insert(STR1, 0);
        assertEquals(1, mArrayAdapter.getCount());
        assertEquals(0, mArrayAdapter.getPosition(STR1));
        verify(mockDataSetObserver, times(1)).onChanged();

        mArrayAdapter.insert(STR2, 0);
        assertEquals(2, mArrayAdapter.getCount());
        assertEquals(1, mArrayAdapter.getPosition(STR1));
        assertEquals(0, mArrayAdapter.getPosition(STR2));

        mArrayAdapter.insert(STR3, mArrayAdapter.getCount());
        assertEquals(mArrayAdapter.getCount() - 1, mArrayAdapter.getPosition(STR3));

        mArrayAdapter.insert(null, 0);
        assertEquals(0, mArrayAdapter.getPosition(null));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testInsertOutOfBoundsLow() {
        mArrayAdapter.insert(STR1, 0);
        mArrayAdapter.insert(STR2, 0);
        mArrayAdapter.insert(null, 0);

        mArrayAdapter.insert(STR1, -1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testInsertOutOfBoundsHigh() {
        mArrayAdapter.insert(STR1, 0);
        mArrayAdapter.insert(STR2, 0);
        mArrayAdapter.insert(null, 0);

        mArrayAdapter.insert(STR1, mArrayAdapter.getCount() + 1);
    }

    /**
     * return the given position obj
     * test range: -1, normal, > count
     */
    @Test
    public void testGetItem() {
        mArrayAdapter.add(STR1);
        mArrayAdapter.add(STR2);
        mArrayAdapter.add(STR3);

        assertSame(STR1, mArrayAdapter.getItem(0));
        assertSame(STR2, mArrayAdapter.getItem(1));
        assertSame(STR3, mArrayAdapter.getItem(2));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetItemOutOfBoundsLow() {
        mArrayAdapter.add(STR1);
        mArrayAdapter.add(STR2);
        mArrayAdapter.add(STR3);

        mArrayAdapter.getItem(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetItemOutOfBoundsHigh() {
        mArrayAdapter.add(STR1);
        mArrayAdapter.add(STR2);
        mArrayAdapter.add(STR3);

        mArrayAdapter.getItem(mArrayAdapter.getCount());
    }

    /**
     * just return the given position
     */
    @Test
    public void testGetItemId() {
        mArrayAdapter.add(STR1);
        mArrayAdapter.add(STR2);
        mArrayAdapter.add(STR3);

        assertEquals(0, mArrayAdapter.getItemId(0));
        assertEquals(1, mArrayAdapter.getItemId(1));
        assertEquals(2, mArrayAdapter.getItemId(2));

        // test invalid input
        assertEquals(-1, mArrayAdapter.getItemId(-1));
        assertEquals(mArrayAdapter.getCount(), mArrayAdapter.getItemId(mArrayAdapter.getCount()));
    }

    /*
     * return the obj position that in the array, if there are same objs, return the first one
     */
    @Test
    public void testGetPosition() {
        mArrayAdapter.add(STR1);
        mArrayAdapter.add(STR2);
        mArrayAdapter.add(STR1);

        assertEquals(0, mArrayAdapter.getPosition(STR1));
        assertEquals(1, mArrayAdapter.getPosition(STR2));
        // return the first one if same obj exsit
        assertEquals(0, mArrayAdapter.getPosition(STR1));

        assertEquals(-1, mArrayAdapter.getPosition(STR3));

        // test invalid input
        assertEquals(-1, mArrayAdapter.getPosition(null));
    }

    /**
     * Removes the specified object from the array. notify data changed
     * remove first one if duplicated string in the array
     */
    @Test
    public void testRemove() {
        final DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        mArrayAdapter.registerDataSetObserver(mockDataSetObserver);
        mArrayAdapter.setNotifyOnChange(true);

        // remove the not exist one
        assertEquals(0, mArrayAdapter.getCount());
        verifyZeroInteractions(mockDataSetObserver);
        // remove the item not exist also notify change
        mArrayAdapter.remove(STR1);
        verify(mockDataSetObserver, times(1)).onChanged();

        mArrayAdapter.add(STR1);
        mArrayAdapter.add(STR2);
        mArrayAdapter.add(STR3);
        mArrayAdapter.add(STR2);
        reset(mockDataSetObserver);
        assertEquals(4, mArrayAdapter.getCount());

        mArrayAdapter.remove(STR1);
        assertEquals(3, mArrayAdapter.getCount());
        assertEquals(-1, mArrayAdapter.getPosition(STR1));
        assertEquals(0, mArrayAdapter.getPosition(STR2));
        assertEquals(1, mArrayAdapter.getPosition(STR3));
        verify(mockDataSetObserver, times(1)).onChanged();

        mArrayAdapter.remove(STR2);
        assertEquals(2, mArrayAdapter.getCount());
        // remove the first one if duplicated
        assertEquals(1, mArrayAdapter.getPosition(STR2));
        assertEquals(0, mArrayAdapter.getPosition(STR3));

        mArrayAdapter.remove(STR2);
        assertEquals(1, mArrayAdapter.getCount());
        assertEquals(-1, mArrayAdapter.getPosition(STR2));
        assertEquals(0, mArrayAdapter.getPosition(STR3));
    }

    /*
     * Creates a new ArrayAdapter from external resources. The content of the array is
     * obtained through {@link android.content.res.Resources#getTextArray(int)}.
     */
    @Test
    public void testCreateFromResource() {
        final ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mContext,
                R.array.string, R.layout.simple_spinner_item);
        final CharSequence[] staticOptions = adapter.getAutofillOptions();
        assertEquals(3, staticOptions.length);
        assertEquals("Test String 1", staticOptions[0]);
        assertEquals("Test String 2", staticOptions[1]);
        assertEquals("Test String 3", staticOptions[2]);

        // Make sure values set dynamically wins.
        adapter.setAutofillOptions("Dynamic", "am I");
        final CharSequence[] dynamicOptions = adapter.getAutofillOptions();
        assertEquals(2, dynamicOptions.length);
        assertEquals("Dynamic", dynamicOptions[0]);
        assertEquals("am I", dynamicOptions[1]);

        ArrayAdapter.createFromResource(mContext, R.array.string, INVALID_ID);
    }

    @Test(expected=NullPointerException.class)
    public void testCreateFromResourceWithNullContext() {
        ArrayAdapter.createFromResource(null, R.array.string, R.layout.simple_spinner_item);
    }

    @Test(expected=Resources.NotFoundException.class)
    public void testCreateFromResourceWithInvalidId() {
        ArrayAdapter.createFromResource(mContext, INVALID_ID, R.layout.simple_spinner_item);
    }

    @Test
    public void testSort() {
        final DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        mArrayAdapter.registerDataSetObserver(mockDataSetObserver);
        mArrayAdapter.setNotifyOnChange(true);
        verifyZeroInteractions(mockDataSetObserver);

        mArrayAdapter.sort((String o1, String o2) -> 0);
        verify(mockDataSetObserver, times(1)).onChanged();

        mArrayAdapter.sort(null);
        verify(mockDataSetObserver, times(2)).onChanged();
    }

    /**
     * insert multiple items via add, notify data changed
     * check count and content
     */
    @Test
    public void testAdd() {
        mArrayAdapter.setNotifyOnChange(true);
        final DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        mArrayAdapter.registerDataSetObserver(mockDataSetObserver);

        mArrayAdapter.clear();
        assertEquals(mArrayAdapter.getCount(), 0);

        mArrayAdapter.add("testing");
        mArrayAdapter.add("android");
        assertEquals(mArrayAdapter.getCount(), 2);
        assertEquals(mArrayAdapter.getItem(0), "testing");
        assertEquals(mArrayAdapter.getItem(1), "android");
    }

    /**
     * insert multiple items via addAll, notify data changed
     * check count and content
     */
    @Test
    public void testAddAllCollection() {
        mArrayAdapter.setNotifyOnChange(true);
        final DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        mArrayAdapter.registerDataSetObserver(mockDataSetObserver);

        List<String> list = new ArrayList<>();
        list.add("");
        list.add("hello");
        list.add("android");
        list.add("!");

        mArrayAdapter.clear();
        assertEquals(mArrayAdapter.getCount(), 0);

        mArrayAdapter.addAll(list);
        assertEquals(mArrayAdapter.getCount(), list.size());

        assertEquals(mArrayAdapter.getItem(0), list.get(0));
        assertEquals(mArrayAdapter.getItem(1), list.get(1));
        assertEquals(mArrayAdapter.getItem(2), list.get(2));
        assertEquals(mArrayAdapter.getItem(3), list.get(3));
    }

    /**
     * insert multiple items via addAll, notify data changed
     * check count and content
     */
    @Test
    public void testAddAllParams() {
        mArrayAdapter.setNotifyOnChange(true);
        final DataSetObserver mockDataSetObserver = mock(DataSetObserver.class);
        mArrayAdapter.registerDataSetObserver(mockDataSetObserver);

        mArrayAdapter.clear();
        assertEquals(mArrayAdapter.getCount(), 0);

        mArrayAdapter.addAll("this", "is", "a", "unit", "test");
        assertEquals(mArrayAdapter.getCount(), 5);
        assertEquals(mArrayAdapter.getItem(0), "this");
        assertEquals(mArrayAdapter.getItem(1), "is");
        assertEquals(mArrayAdapter.getItem(2), "a");
        assertEquals(mArrayAdapter.getItem(3), "unit");
        assertEquals(mArrayAdapter.getItem(4), "test");
    }
}
