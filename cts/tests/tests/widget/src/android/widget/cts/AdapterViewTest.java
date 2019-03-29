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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AdapterViewTest {

    private final static int INVALID_ID = -1;

    private final static int LAYOUT_WIDTH = 200;
    private final static int LAYOUT_HEIGHT = 200;

    final String[] FRUIT = { "1", "2", "3", "4", "5", "6", "7", "8" };

    private AdapterViewCtsActivity mActivity;
    private AdapterView<ListAdapter> mAdapterView;

    @Rule
    public ActivityTestRule<AdapterViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(AdapterViewCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mAdapterView = new ListView(mActivity);
    }

    @Test
    public void testConstructor() {
        XmlPullParser parser = mActivity.getResources().getXml(R.layout.adapterview_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        new MockAdapterView(mActivity);

        new MockAdapterView(mActivity, attrs);

        new MockAdapterView(mActivity, attrs, 0);

        new MockAdapterView(mActivity, null, INVALID_ID);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext() {
        new MockAdapterView(null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testAddView1() {
        ListView subView = new ListView(mActivity);
        mAdapterView.addView(subView);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testAddView2() {
        ListView subView = new ListView(mActivity);
        mAdapterView.addView(subView, 0);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testAddView3() {
        ListView subView = new ListView(mActivity);
        mAdapterView.addView(subView, (ViewGroup.LayoutParams) null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testAddView4() {
        ListView subView = new ListView(mActivity);
        mAdapterView.addView(subView, 0, (ViewGroup.LayoutParams) null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testRemoveView1() {
        mAdapterView.removeViewAt(0);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testRemoveView2() {
        ListView subView = new ListView(mActivity);
        mAdapterView.removeView(subView);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testRemoveAllViews() {
        mAdapterView.removeAllViews();
    }

    @Test(expected=RuntimeException.class)
    public void testSetOnClickListener() {
        mAdapterView.setOnClickListener((View v) -> {});
    }

    @Test
    public void testGetCount() {
        // Before setAdapter, the count should be zero.
        assertEquals(0, mAdapterView.getCount());

        setArrayAdapter(mAdapterView);

        // After setAdapter, the count should be the value return by adapter.
        assertEquals(FRUIT.length, mAdapterView.getCount());
    }

    @Test
    public void testAccessEmptyView() {
        ImageView emptyView = new ImageView(mActivity);

        // If there is no adapter has been set, emptyView hasn't been set, there will be no
        // emptyView return by getEmptyView().
        assertEquals(null, mAdapterView.getEmptyView());

        // If the adapter is 0 count, emptyView has been set, the emptyView should be returned by
        // getEmptyView. EmptyView will be set to Visible.
        mAdapterView.setAdapter(new ArrayAdapter<String>(
                mActivity, R.layout.adapterview_layout, new String[]{}));
        emptyView.setVisibility(View.INVISIBLE);
        assertEquals(View.INVISIBLE, emptyView.getVisibility());

        // set empty view, for no item added, empty set to visible
        mAdapterView.setEmptyView(emptyView);
        assertSame(emptyView, mAdapterView.getEmptyView());
        assertEquals(View.VISIBLE, emptyView.getVisibility());

        // If the adapter is not empty, the emptyView should also be returned by
        // getEmptyView. EmptyView will be set to Gone.
        setArrayAdapter(mAdapterView);
        emptyView = new ImageView(mActivity);

        assertEquals(View.VISIBLE, emptyView.getVisibility());
        mAdapterView.setEmptyView(emptyView);
        // for item added, emptyview is set to gone
        assertEquals(emptyView, mAdapterView.getEmptyView());
        assertEquals(View.GONE, emptyView.getVisibility());

        // null adapter should also show empty view
        mAdapterView.setAdapter(null);
        emptyView = new ImageView(mActivity);
        emptyView.setVisibility(View.INVISIBLE);
        assertEquals(View.INVISIBLE, emptyView.getVisibility());
        // set empty view
        mAdapterView.setEmptyView(emptyView);
        assertEquals(emptyView, mAdapterView.getEmptyView());
        assertEquals(View.VISIBLE, emptyView.getVisibility());
    }

    @Test
    public void testAccessVisiblePosition() {
        assertEquals(0, mAdapterView.getFirstVisiblePosition());
        // If no adapter has been set, the value should be -1;
        assertEquals(-1, mAdapterView.getLastVisiblePosition());

        setArrayAdapter(mAdapterView);

        // LastVisiblePosition should be adapter's getCount - 1,by mocking method
        float fontScale = Settings.System.getFloat(
                mActivity.getContentResolver(), Settings.System.FONT_SCALE, 1);
        if (fontScale < 1) {
            fontScale = 1;
        }
        float density = mActivity.getResources().getDisplayMetrics().density;
        int bottom = (int) (LAYOUT_HEIGHT * density * fontScale);
        mAdapterView.layout(0, 0, LAYOUT_WIDTH, bottom);
        assertEquals(FRUIT.length - 1, mAdapterView.getLastVisiblePosition());
    }

    @Test
    public void testItemOrItemIdAtPosition() {
        // no adapter set
        assertNull(mAdapterView.getItemAtPosition(0));
        assertEquals(AdapterView.INVALID_ROW_ID, mAdapterView.getItemIdAtPosition(1));

        // after adapter set
        setArrayAdapter(mAdapterView);
        int count = mAdapterView.getAdapter().getCount();

        for (int i = 0; i < count; i++) {
            assertEquals(FRUIT[i], mAdapterView.getItemAtPosition(i));
        }
        assertNull(mAdapterView.getItemAtPosition(-1));

        for (int i = 0; i < count; i++) {
            assertEquals(i, mAdapterView.getItemIdAtPosition(i));
        }
        assertEquals(AdapterView.INVALID_ROW_ID, mAdapterView.getItemIdAtPosition(-1));
        assertEquals(FRUIT.length, mAdapterView.getItemIdAtPosition(FRUIT.length));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testItemAtPositionInvalidIndex() {
        setArrayAdapter(mAdapterView);
        mAdapterView.getItemAtPosition(FRUIT.length);
    }

    @Test
    public void testAccessOnItemClickAndLongClickListener() {
        AdapterView.OnItemClickListener mockClickListener =
                mock(AdapterView.OnItemClickListener.class);
        AdapterView.OnItemLongClickListener mockLongClickListener =
                mock(AdapterView.OnItemLongClickListener.class);
        when(mockLongClickListener.onItemLongClick(
                any(AdapterView.class), any(View.class), anyInt(), anyLong())).thenReturn(true);

        assertNull(mAdapterView.getOnItemLongClickListener());
        assertNull(mAdapterView.getOnItemClickListener());

        assertFalse(mAdapterView.performItemClick(null, 0, 0));

        mAdapterView.setOnItemClickListener(mockClickListener);
        mAdapterView.setOnItemLongClickListener(mockLongClickListener);
        assertEquals(mockLongClickListener, mAdapterView.getOnItemLongClickListener());

        verifyZeroInteractions(mockClickListener);
        assertTrue(mAdapterView.performItemClick(null, 0, 0));
        verify(mockClickListener, times(1)).onItemClick(eq(mAdapterView), any(),
                eq(0), eq(0L));

        setArrayAdapter(mAdapterView);
        verifyZeroInteractions(mockLongClickListener);
        mAdapterView.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT);
        assertTrue(mAdapterView.showContextMenuForChild(mAdapterView.getChildAt(0)));
        verify(mockLongClickListener, times(1)).onItemLongClick(eq(mAdapterView), any(View.class),
                eq(0), eq(0L));
    }

    @Test
    public void testAccessOnItemSelectedListener() throws Throwable {
        mAdapterView = mActivity.getListView();
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, mAdapterView,
                () -> mAdapterView.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)), true);

        mActivityRule.runOnUiThread(() -> setArrayAdapter(mAdapterView));
        // Wait for the UI to "settle down" since selection is fired asynchronously
        // on the next layout pass, and we don't want to trigger the listener too early
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        AdapterView.OnItemSelectedListener mockSelectedListener =
                mock(AdapterView.OnItemSelectedListener.class);
        mActivityRule.runOnUiThread(() ->
                mAdapterView.setOnItemSelectedListener(mockSelectedListener));
        assertEquals(mockSelectedListener, mAdapterView.getOnItemSelectedListener());

        verifyZeroInteractions(mockSelectedListener);

        // Select item #1 and verify that the listener has been notified
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mAdapterView,
                () -> mAdapterView.setSelection(1));
        verify(mockSelectedListener, times(1)).onItemSelected(eq(mAdapterView), any(View.class),
                eq(1), eq(1L));
        verifyNoMoreInteractions(mockSelectedListener);

        // Select last item and verify that the listener has been notified
        reset(mockSelectedListener);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mAdapterView,
                () -> mAdapterView.setSelection(FRUIT.length - 1));
        verify(mockSelectedListener, times(1)).onItemSelected(
                eq(mAdapterView), any(View.class), eq(FRUIT.length - 1),
                eq((long) FRUIT.length - 1));
        verifyNoMoreInteractions(mockSelectedListener);
    }

    /*
     * Get the position within the adapter's data set for the view, where view is a an adapter item
     * or a descendant of an adapter item.
     * when scroll down the list, the item's position may be 5 or 6 be on the screen
     * but to the layout parent ,it may still be the 1, 2 child for there always has 3,4 views there
     * it's hard to scroll the list in unit test, so we just test without scrolling
     * this means the position of item is same as position of the children in parent layout
     */
    @Test
    public void testGetPositionForView() {
        setArrayAdapter(mAdapterView);
        mAdapterView.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT);

        int count = mAdapterView.getChildCount();
        for (int i = 0; i < count; i++) {
            assertEquals(i, mAdapterView.getPositionForView(mAdapterView.getChildAt(i)));
        }

        assertEquals(AdapterView.INVALID_POSITION,
                mAdapterView.getPositionForView(new ImageView(mActivity)));
    }

    @Test(expected=NullPointerException.class)
    public void testGetPositionForNull() {
        setArrayAdapter(mAdapterView);
        mAdapterView.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT);
        mAdapterView.getPositionForView(null);
    }

    @Test
    public void testChangeFocusable() {
        assertFalse(mAdapterView.isFocusable());
        assertFalse(mAdapterView.isFocusableInTouchMode());

        // no item added will never focusable
        assertNull(mAdapterView.getAdapter());
        mAdapterView.setFocusable(true);
        assertFalse(mAdapterView.isFocusable());
        assertFalse(mAdapterView.isFocusableInTouchMode());

        // only focusable with children added
        setArrayAdapter(mAdapterView);
        assertTrue(mAdapterView.getAdapter().getCount() > 0);
        mAdapterView.setFocusable(true);
        assertTrue(mAdapterView.isFocusable());
        assertTrue(mAdapterView.isFocusableInTouchMode());

        // FOCUSABLE_AUTO should also work with children added (AbsListView is clickable)
        mAdapterView.setFocusable(View.FOCUSABLE_AUTO);
        assertTrue(mAdapterView.isFocusable());
        assertTrue(mAdapterView.isFocusableInTouchMode());

        mAdapterView.setFocusable(false);
        assertFalse(mAdapterView.isFocusable());
        assertFalse(mAdapterView.isFocusableInTouchMode());
    }

    /*
     * set and get the selected id, position and item.
     * values will not change if invalid id given.
     */
    @Test
    public void testGetSelected() {
        assertEquals(AdapterView.INVALID_ROW_ID, mAdapterView.getSelectedItemId());
        assertEquals(AdapterView.INVALID_POSITION, mAdapterView.getSelectedItemPosition());
        assertEquals(null, mAdapterView.getSelectedItem());

        // set adapter, 0 selected by default
        setArrayAdapter(mAdapterView);
        assertEquals(0, mAdapterView.getSelectedItemId());
        assertEquals(0, mAdapterView.getSelectedItemPosition());
        assertEquals(FRUIT[0], mAdapterView.getSelectedItem());

        int expectedId = 1;
        mAdapterView.setSelection(expectedId);
        assertEquals(expectedId, mAdapterView.getSelectedItemId());
        assertEquals(expectedId, mAdapterView.getSelectedItemPosition());
        assertEquals(FRUIT[expectedId], mAdapterView.getSelectedItem());

        // invalid id will be ignored
        expectedId = -1;
        mAdapterView.setSelection(expectedId);
        assertEquals(1, mAdapterView.getSelectedItemId());
        assertEquals(1, mAdapterView.getSelectedItemPosition());
        assertEquals(FRUIT[1], mAdapterView.getSelectedItem());

        expectedId = mAdapterView.getCount();
        mAdapterView.setSelection(expectedId);
        assertEquals(1, mAdapterView.getSelectedItemId());
        assertEquals(1, mAdapterView.getSelectedItemPosition());
        assertEquals(FRUIT[1], mAdapterView.getSelectedItem());
    }

    /*
     * not update this test until the ViewGroup's test finish.
     */
    @Test
    public void testDispatchSaveInstanceState() {
        MockAdapterView adapterView = new MockAdapterView(mActivity);
        adapterView.setSaveEnabled(true);
        adapterView.setId(1);
        SparseArray<Parcelable> sa = new SparseArray<Parcelable>();
        adapterView.dispatchSaveInstanceState(sa);
        assertTrue(sa.size() > 0);
    }

    /*
     * not update this test until the ViewGroup's test finish.
     */
    @Test
    public void testDispatchRestoreInstanceState() {
        MockAdapterView adapterView = new MockAdapterView(mActivity);
        adapterView.setSaveEnabled(true);
        adapterView.setId(1);
        SparseArray<Parcelable> sparseArray = new SparseArray<Parcelable>();
        adapterView.dispatchRestoreInstanceState(sparseArray);
    }

    /*
     * whether this view can has animation layout
     * if no child added, it always return false
     * this method is protected, so we involve the mock
     */
    @Test
    public void testCanAnimate() {
        MockAdapterView adapterView = new MockAdapterView(mActivity);
        LayoutAnimationController lAC = new LayoutAnimationController(new AnimationSet(true));

        // no child added, always false
        assertNull(adapterView.getAdapter());
        adapterView.setLayoutAnimation(lAC);
        assertFalse(adapterView.canAnimate());

        setArrayAdapter(adapterView);

        assertTrue(adapterView.getAdapter().getCount() > 0);
        assertTrue(adapterView.canAnimate());
    }

    private static class MockAdapterView extends ListView{

        public MockAdapterView(Context context) {
            super(context);
        }

        public MockAdapterView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MockAdapterView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
            super.dispatchRestoreInstanceState(container);
        }

        @Override
        protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
            super.dispatchSaveInstanceState(container);
        }

        @Override
        protected boolean canAnimate() {
            return super.canAnimate();
        }
    }

    private void setArrayAdapter(AdapterView<ListAdapter> adapterView) {
        adapterView.setAdapter(new ArrayAdapter<>(
                mActivity, R.layout.adapterview_layout, FRUIT));
    }
}
