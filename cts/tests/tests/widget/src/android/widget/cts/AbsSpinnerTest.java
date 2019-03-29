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

import android.app.Activity;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.os.Parcelable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsSpinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AbsSpinnerTest {
    private Activity mActivity;
    private AbsSpinner mAbsSpinner;

    @Rule
    public ActivityTestRule<RelativeLayoutCtsActivity> mActivityRule =
            new ActivityTestRule<>(RelativeLayoutCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mAbsSpinner = (AbsSpinner) mActivity.findViewById(R.id.spinner1);
    }

    @Test
    public void testConstructor() {
        new Spinner(mActivity);

        new Spinner(mActivity, null);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle);

        new Gallery(mActivity);
        new Gallery(mActivity, null);
        new Gallery(mActivity, null, 0);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.gallery_test);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new Gallery(mActivity, attrs);
        new Gallery(mActivity, attrs, 0);
    }

    /**
     * Check points:
     * 1. Jump to the specific item.
     */
    @UiThreadTest
    @Test
    public void testSetSelectionIntBoolean() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAbsSpinner.setAdapter(adapter);
        assertEquals(0, mAbsSpinner.getSelectedItemPosition());

        mAbsSpinner.setSelection(1, true);
        assertEquals(1, mAbsSpinner.getSelectedItemPosition());

        mAbsSpinner.setSelection(mAbsSpinner.getCount() - 1, false);
        assertEquals(mAbsSpinner.getCount() - 1, mAbsSpinner.getSelectedItemPosition());

        // The animation effect depends on implementation in AbsSpinner's subClass.
        // It is not meaningful to check it.
    }

    /**
     * Check points:
     * 1. the currently selected item should be the one which set using this method.
     */
    @UiThreadTest
    @Test
    public void testSetSelectionInt() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAbsSpinner.setAdapter(adapter);
        assertEquals(0, mAbsSpinner.getSelectedItemPosition());

        mAbsSpinner.setSelection(1);
        assertEquals(1, mAbsSpinner.getSelectedItemPosition());

        mAbsSpinner.setSelection(mAbsSpinner.getCount() - 1);
        assertEquals(mAbsSpinner.getCount() - 1, mAbsSpinner.getSelectedItemPosition());
    }

    /**
     * Check points:
     * 1. the adapter returned from getAdapter() should be the one specified using setAdapter().
     * 2. the adapter provides methods to transform spinner items based on their position
     * relative to the selected item.
     */
    @UiThreadTest
    @Test
    public void testAccessAdapter() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mAbsSpinner.setAdapter(adapter);
        assertSame(adapter, mAbsSpinner.getAdapter());
        assertEquals(adapter.getCount(), mAbsSpinner.getCount());
        assertEquals(0, mAbsSpinner.getSelectedItemPosition());
        assertEquals(adapter.getItemId(0), mAbsSpinner.getSelectedItemId());
        mAbsSpinner.setSelection(1);
        assertEquals(1, mAbsSpinner.getSelectedItemPosition());
        assertEquals(adapter.getItemId(1), mAbsSpinner.getSelectedItemId());
    }

    @Test
    public void testRequestLayout() {
        AbsSpinner absSpinner = new Spinner(mActivity);
        absSpinner.layout(0, 0, 200, 300);
        assertFalse(absSpinner.isLayoutRequested());

        absSpinner.requestLayout();
        assertTrue(absSpinner.isLayoutRequested());
    }

    /**
     * Check points:
     * 1. The value returned from getCount() equals the count of Adapter associated with
     * this AdapterView.
     */
    @UiThreadTest
    @Test
    public void testGetCount() {
        ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);

        mAbsSpinner.setAdapter(adapter1);
        assertEquals(adapter1.getCount(), mAbsSpinner.getCount());

        CharSequence anotherStringArray[] = { "another array string 1", "another array string 2" };
        ArrayAdapter<CharSequence> adapter2 = new ArrayAdapter<>(mActivity,
                android.R.layout.simple_spinner_item, anotherStringArray);

        mAbsSpinner.setAdapter(adapter2);
        assertEquals(anotherStringArray.length, mAbsSpinner.getCount());
    }

    /**
     * Check points:
     * 1. Should return the position of the item which contains the specified point.
     * 2. Should return INVALID_POSITION if the point does not intersect an item
     */
    @Test
    public void testPointToPosition() {
        AbsSpinner absSpinner = new Gallery(mActivity);
        MockSpinnerAdapter adapter = new MockSpinnerAdapter();
        assertEquals(AdapterView.INVALID_POSITION, absSpinner.pointToPosition(10, 10));

        adapter.setCount(3);
        absSpinner.setAdapter(adapter);
        absSpinner.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        Rect rc = new Rect(0, 0, 100, 100);
        Rect rcChild0 = new Rect(0, 0, 20, rc.bottom);
        Rect rcChild1 = new Rect(rcChild0.right, 0, 70, rc.bottom);
        Rect rcChild2 = new Rect(rcChild1.right, 0, rc.right, rc.bottom);
        absSpinner.layout(rc.left, rc.top, rc.right, rc.bottom);
        absSpinner.getChildAt(0).layout(rcChild0.left, rcChild0.top,
                rcChild0.right, rcChild0.bottom);
        absSpinner.getChildAt(1).layout(rcChild1.left, rcChild1.top,
                rcChild1.right, rcChild1.bottom);
        absSpinner.getChildAt(2).layout(rcChild2.left, rcChild2.top,
                rcChild2.right, rcChild2.bottom);

        assertEquals(AdapterView.INVALID_POSITION, absSpinner.pointToPosition(-1, -1));
        assertEquals(0, absSpinner.pointToPosition(rcChild0.left + 1, rc.bottom - 1));
        assertEquals(1, absSpinner.pointToPosition(rcChild1.left + 1, rc.bottom - 1));
        assertEquals(2, absSpinner.pointToPosition(rcChild2.left + 1, rc.bottom - 1));
        assertEquals(AdapterView.INVALID_POSITION,
                absSpinner.pointToPosition(rc.right + 1, rc.bottom - 1));

    }

    /**
     * Check points:
     * 1. Should return the view corresponding to the currently selected item.
     * 2. Should return null if nothing is selected.
     */
    @Test
    public void testGetSelectedView() {
        AbsSpinner absSpinner = new Gallery(mActivity);
        MockSpinnerAdapter adapter = new MockSpinnerAdapter();
        assertNull(absSpinner.getSelectedView());

        absSpinner.setAdapter(adapter);
        absSpinner.layout(0, 0, 20, 20);
        assertSame(absSpinner.getChildAt(0), absSpinner.getSelectedView());

        absSpinner.setSelection(1, true);
        assertSame(absSpinner.getChildAt(1), absSpinner.getSelectedView());
    }

    /**
     * Check points:
     * 1. the view's current state saved by onSaveInstanceState() should be correctly restored
     * after onRestoreInstanceState().
     */
    @UiThreadTest
    @Test
    public void testOnSaveAndRestoreInstanceState() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAbsSpinner.setAdapter(adapter);
        assertEquals(0, mAbsSpinner.getSelectedItemPosition());
        assertEquals(adapter.getItemId(0), mAbsSpinner.getSelectedItemId());
        Parcelable parcelable = mAbsSpinner.onSaveInstanceState();

        mAbsSpinner.setSelection(1);
        assertEquals(1, mAbsSpinner.getSelectedItemPosition());
        assertEquals(adapter.getItemId(1), mAbsSpinner.getSelectedItemId());

        mAbsSpinner.onRestoreInstanceState(parcelable);
        mAbsSpinner.measure(View.MeasureSpec.EXACTLY, View.MeasureSpec.EXACTLY);
        mAbsSpinner.layout(mAbsSpinner.getLeft(), mAbsSpinner.getTop(),
                mAbsSpinner.getRight(), mAbsSpinner.getBottom());
        assertEquals(0, mAbsSpinner.getSelectedItemPosition());
        assertEquals(adapter.getItemId(0), mAbsSpinner.getSelectedItemId());
    }

    /*
     * The Mock class of SpinnerAdapter to be used in test.
     */
    private class MockSpinnerAdapter implements SpinnerAdapter {
        public static final int DEFAULT_COUNT = 1;
        private int mCount = DEFAULT_COUNT;

        public View getDropDownView(int position, View convertView,
                ViewGroup parent) {
            return null;
        }

        public int getCount() {
            return mCount;
        }

        public void setCount(int count) {
            mCount = count;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return new ImageView(mActivity);
        }

        public int getViewTypeCount() {
            return 0;
        }

        public boolean hasStableIds() {
            return false;
        }

        public boolean isEmpty() {
            return false;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
        }
    }
}
