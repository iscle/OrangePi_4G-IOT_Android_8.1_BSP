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
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.StateSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CheckedTextViewTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private ListView mListView;
    private CheckedTextView mCheckedTextView;

    @Rule
    public ActivityTestRule<CheckedTextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(CheckedTextViewCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mListView = (ListView) mActivity.findViewById(R.id.checkedtextview_listview);
        mCheckedTextView = (CheckedTextView) mActivity.findViewById(R.id.checkedtextview_test);
    }

    @Test
    public void testConstructor() {
        new CheckedTextView(mActivity);
        new CheckedTextView(mActivity, null);
        new CheckedTextView(mActivity, null, android.R.attr.checkedTextViewStyle);
        new CheckedTextView(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_CheckedTextView);
        new CheckedTextView(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_CheckedTextView);
        new CheckedTextView(mActivity, null, 0,
                android.R.style.Widget_Material_CheckedTextView);
        new CheckedTextView(mActivity, null, 0,
                android.R.style.Widget_Material_Light_CheckedTextView);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new CheckedTextView(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new CheckedTextView(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new CheckedTextView(null, null, -1);
    }

    @Test
    public void testChecked() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mListView.setAdapter(new CheckedTextViewAdapter());

            mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            mListView.setItemChecked(1, true);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(1, mListView.getCheckedItemPosition());
        assertTrue(mListView.isItemChecked(1));
        assertFalse(mListView.isItemChecked(0));

        final ListAdapter adapter = mListView.getAdapter();
        final CheckedTextView view0 = (CheckedTextView) adapter.getView(0, null, null);
        final CheckedTextView view1 = (CheckedTextView) adapter.getView(1, null, null);
        final CheckedTextView view2 = (CheckedTextView) adapter.getView(2, null, null);
        assertFalse(view0.isChecked());
        assertTrue(view1.isChecked());
        assertFalse(view2.isChecked());

        mActivityRule.runOnUiThread(() -> {
            mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            mListView.setItemChecked(2, true);
        });
        mInstrumentation.waitForIdleSync();
        assertFalse(view0.isChecked());
        assertTrue(view1.isChecked());
        assertTrue(view2.isChecked());

        view0.setChecked(true);
        view1.setChecked(false);
        view2.setChecked(false);
        assertTrue(view0.isChecked());
        assertFalse(view1.isChecked());
        assertFalse(view2.isChecked());
    }

    @UiThreadTest
    @Test
    public void testToggle() {
        assertFalse(mCheckedTextView.isChecked());

        mCheckedTextView.toggle();
        assertTrue(mCheckedTextView.isChecked());

        mCheckedTextView.toggle();
        assertFalse(mCheckedTextView.isChecked());

        mCheckedTextView.setChecked(true);
        mCheckedTextView.toggle();
        assertFalse(mCheckedTextView.isChecked());
    }

    @Test
    public void testDrawableStateChanged() {
        MockCheckedTextView checkedTextView = new MockCheckedTextView(mActivity);

        assertFalse(checkedTextView.hasDrawableStateChanged());
        checkedTextView.refreshDrawableState();
        assertTrue(checkedTextView.hasDrawableStateChanged());
    }

    @Test
    public void testSetPadding() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mListView.setPadding(1, 2, 3, 4);
            mListView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();

        final int origTop = mListView.getPaddingTop();
        final int origBottom = mListView.getPaddingBottom();
        final int origLeft = mListView.getPaddingLeft();
        final int origRight = mListView.getPaddingRight();

        mActivityRule.runOnUiThread(() -> {
            mListView.setPadding(10, 20, 30, 40);
            mListView.requestLayout();
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(origTop < mListView.getPaddingTop());
        assertTrue(origBottom < mListView.getPaddingBottom());
        assertTrue(origLeft < mListView.getPaddingLeft());
        assertTrue(origRight < mListView.getPaddingRight());
    }

    private void cleanUpForceLayoutFlags(View view) {
        if (view != null) {
            view.layout(0, 0, 0, 0);
            assertFalse(view.isLayoutRequested());
        }
    }

    @UiThreadTest
    @Test
    public void testSetCheckMarkDrawableByDrawable() {
        int basePaddingRight = 10;

        // set drawable when checkedTextView is GONE
        mCheckedTextView.setVisibility(View.GONE);
        final Drawable firstDrawable = mActivity.getDrawable(R.drawable.scenery);
        firstDrawable.setVisible(true, false);
        assertEquals(StateSet.WILD_CARD, firstDrawable.getState());
        cleanUpForceLayoutFlags(mCheckedTextView);

        mCheckedTextView.setCheckMarkDrawable(firstDrawable);
        assertEquals(firstDrawable.getIntrinsicWidth(), mCheckedTextView.getPaddingRight());
        assertFalse(firstDrawable.isVisible());
        assertTrue(Arrays.equals(mCheckedTextView.getDrawableState(), firstDrawable.getState()));
        assertTrue(mCheckedTextView.isLayoutRequested());

        mCheckedTextView.setCheckMarkDrawable(null);

        // update drawable when checkedTextView is VISIBLE
        mCheckedTextView.setVisibility(View.VISIBLE);
        mCheckedTextView.setPadding(0, 0, basePaddingRight, 0);
        final Drawable secondDrawable = mActivity.getDrawable(R.drawable.pass);
        secondDrawable.setVisible(true, false);
        assertEquals(StateSet.WILD_CARD, secondDrawable.getState());
        cleanUpForceLayoutFlags(mCheckedTextView);

        mCheckedTextView.setCheckMarkDrawable(secondDrawable);
        assertEquals(secondDrawable.getIntrinsicWidth() + basePaddingRight,
                mCheckedTextView.getPaddingRight());
        assertTrue(secondDrawable.isVisible());
        assertTrue(Arrays.equals(mCheckedTextView.getDrawableState(), secondDrawable.getState()));
        assertTrue(mCheckedTextView.isLayoutRequested());

        cleanUpForceLayoutFlags(mCheckedTextView);
        mCheckedTextView.setCheckMarkDrawable(null);
        assertEquals(basePaddingRight, mCheckedTextView.getPaddingRight());
        assertTrue(mCheckedTextView.isLayoutRequested());
    }

    @UiThreadTest
    @Test
    public void testSetCheckMarkDrawableById() {
        int basePaddingRight = 10;

        // set drawable
        mCheckedTextView.setPadding(0, 0, basePaddingRight, 0);
        Drawable firstDrawable = mActivity.getDrawable(R.drawable.scenery);
        cleanUpForceLayoutFlags(mCheckedTextView);

        mCheckedTextView.setCheckMarkDrawable(R.drawable.scenery);
        assertEquals(firstDrawable.getIntrinsicWidth() + basePaddingRight,
                mCheckedTextView.getPaddingRight());
        assertTrue(mCheckedTextView.isLayoutRequested());

        // set the same drawable again
        cleanUpForceLayoutFlags(mCheckedTextView);
        mCheckedTextView.setCheckMarkDrawable(R.drawable.scenery);
        assertEquals(firstDrawable.getIntrinsicWidth() + basePaddingRight,
                mCheckedTextView.getPaddingRight());
        assertFalse(mCheckedTextView.isLayoutRequested());

        // update drawable
        final Drawable secondDrawable = mActivity.getDrawable(R.drawable.pass);
        mCheckedTextView.setCheckMarkDrawable(secondDrawable);
        assertEquals(secondDrawable.getIntrinsicWidth() + basePaddingRight,
                mCheckedTextView.getPaddingRight());
        assertTrue(mCheckedTextView.isLayoutRequested());

        mCheckedTextView.setCheckMarkDrawable(null);

        // resId is 0
        mCheckedTextView.setPadding(0, 0, basePaddingRight, 0);
        cleanUpForceLayoutFlags(mCheckedTextView);

        mCheckedTextView.setCheckMarkDrawable(0);
        assertEquals(basePaddingRight, mCheckedTextView.getPaddingRight());
        assertFalse(mCheckedTextView.isLayoutRequested());
    }

    @UiThreadTest
    @Test
    public void testSetCheckMarkByMixedTypes() {
        cleanUpForceLayoutFlags(mCheckedTextView);

        // Specifically test for b/22626247 (AOSP issue 180455).
        mCheckedTextView.setCheckMarkDrawable(R.drawable.scenery);
        mCheckedTextView.setCheckMarkDrawable(null);
        mCheckedTextView.setCheckMarkDrawable(R.drawable.scenery);
        assertNotNull(mCheckedTextView.getCheckMarkDrawable());
    }

    @UiThreadTest
    @Test
    public void testAccessInstanceState() {
        assertFalse(mCheckedTextView.isChecked());
        assertFalse(mCheckedTextView.getFreezesText());

        final Parcelable state = mCheckedTextView.onSaveInstanceState();
        assertNotNull(state);
        assertFalse(mCheckedTextView.getFreezesText());

        mCheckedTextView.setChecked(true);

        mCheckedTextView.onRestoreInstanceState(state);
        assertFalse(mCheckedTextView.isChecked());
        assertTrue(mCheckedTextView.isLayoutRequested());
    }

    @Test
    public void testCheckMarkTinting() throws Throwable {
        mActivityRule.runOnUiThread(() -> mCheckedTextView.setChecked(true));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mCheckedTextView,
                () -> mCheckedTextView.setCheckMarkDrawable(R.drawable.icon_red));

        Drawable checkMark = mCheckedTextView.getCheckMarkDrawable();
        TestUtils.assertAllPixelsOfColor("Initial state is red", checkMark,
                checkMark.getBounds().width(), checkMark.getBounds().height(), false,
                Color.RED, 1, true);

        // With SRC_IN we're expecting the translucent tint color to "take over" the
        // original red checkmark.
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mCheckedTextView, () -> {
            mCheckedTextView.setCheckMarkTintMode(PorterDuff.Mode.SRC_IN);
            mCheckedTextView.setCheckMarkTintList(ColorStateList.valueOf(0x8000FF00));
        });

        assertEquals(PorterDuff.Mode.SRC_IN, mCheckedTextView.getCheckMarkTintMode());
        assertEquals(0x8000FF00, mCheckedTextView.getCheckMarkTintList().getDefaultColor());
        checkMark = mCheckedTextView.getCheckMarkDrawable();
        TestUtils.assertAllPixelsOfColor("Expected 50% green", checkMark,
                checkMark.getIntrinsicWidth(), checkMark.getIntrinsicHeight(), false,
                0x8000FF00, 1, true);

        // With SRC_OVER we're expecting the translucent tint color to be drawn on top
        // of the original red checkmark, creating a composite color fill as the result.
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mCheckedTextView,
                () -> mCheckedTextView.setCheckMarkTintMode(PorterDuff.Mode.SRC_OVER));

        assertEquals(PorterDuff.Mode.SRC_OVER, mCheckedTextView.getCheckMarkTintMode());
        assertEquals(0x8000FF00, mCheckedTextView.getCheckMarkTintList().getDefaultColor());
        checkMark = mCheckedTextView.getCheckMarkDrawable();
        TestUtils.assertAllPixelsOfColor("Expected 50% green over full red", checkMark,
                checkMark.getIntrinsicWidth(), checkMark.getIntrinsicHeight(), false,
                TestUtils.compositeColors(0x8000FF00, Color.RED), 1, true);

        // Switch to a different color for the underlying checkmark and verify that the
        // currently configured tinting (50% green overlay) is still respected
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mCheckedTextView,
                () -> mCheckedTextView.setCheckMarkDrawable(R.drawable.icon_yellow));
        assertEquals(PorterDuff.Mode.SRC_OVER, mCheckedTextView.getCheckMarkTintMode());
        assertEquals(0x8000FF00, mCheckedTextView.getCheckMarkTintList().getDefaultColor());
        checkMark = mCheckedTextView.getCheckMarkDrawable();
        TestUtils.assertAllPixelsOfColor("Expected 50% green over full yellow", checkMark,
                checkMark.getIntrinsicWidth(), checkMark.getIntrinsicHeight(), false,
                TestUtils.compositeColors(0x8000FF00, Color.YELLOW), 1, true);
    }

    private static final class MockCheckedTextView extends CheckedTextView {
        private boolean mHasDrawableStateChanged = false;

        public MockCheckedTextView(Context context) {
            super(context);
        }

        public MockCheckedTextView(Context context, AttributeSet attrs) {
            super(context, attrs, 0);
        }

        public MockCheckedTextView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            mHasDrawableStateChanged = true;
        }

        @Override
        protected int[] onCreateDrawableState(int extraSpace) {
            return super.onCreateDrawableState(extraSpace);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        public boolean hasDrawableStateChanged() {
            return mHasDrawableStateChanged;
        }
    }

    private class CheckedTextViewAdapter extends BaseAdapter {
        private CheckedTextView[] mCheckedTextViews = new CheckedTextView[]{
                new MockCheckedTextView(mActivity),
                new MockCheckedTextView(mActivity),
                new MockCheckedTextView(mActivity),
        };

        public int getCount() {
            return mCheckedTextViews.length;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return mCheckedTextViews[position];
        }
    }
}
