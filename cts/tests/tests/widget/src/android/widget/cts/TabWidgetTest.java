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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.cts.util.TestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link TabWidget}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TabWidgetTest {
    private TabHostCtsActivity mActivity;
    private TabWidget mTabWidget;
    private Instrumentation mInstrumentation;

    @Rule
    public ActivityTestRule<TabHostCtsActivity> mActivityRule =
            new ActivityTestRule<>(TabHostCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mTabWidget = mActivity.getTabWidget();
    }

    @Test
    public void testConstructor() {
        new TabWidget(mActivity);

        new TabWidget(mActivity, null);

        new TabWidget(mActivity, null, 0);
    }

    @Test
    public void testConstructorWithStyle() {
        TabWidget tabWidget = new TabWidget(mActivity, null, 0, R.style.TabWidgetCustomStyle);

        assertFalse(tabWidget.isStripEnabled());

        Drawable leftStripDrawable = tabWidget.getLeftStripDrawable();
        assertNotNull(leftStripDrawable);
        TestUtils.assertAllPixelsOfColor("Left strip green", leftStripDrawable,
                leftStripDrawable.getIntrinsicWidth(), leftStripDrawable.getIntrinsicHeight(),
                true, 0xFF00FF00, 1, false);

        Drawable rightStripDrawable = tabWidget.getRightStripDrawable();
        assertNotNull(rightStripDrawable);
        TestUtils.assertAllPixelsOfColor("Right strip red", rightStripDrawable,
                rightStripDrawable.getIntrinsicWidth(), rightStripDrawable.getIntrinsicHeight(),
                true, 0xFFFF0000, 1, false);
    }

    @Test
    public void testInflateFromXml() {
        LayoutInflater inflater = LayoutInflater.from(mActivity);
        TabWidget tabWidget = (TabWidget) inflater.inflate(R.layout.tabhost_custom, null, false);

        assertFalse(tabWidget.isStripEnabled());

        Drawable leftStripDrawable = tabWidget.getLeftStripDrawable();
        assertNotNull(leftStripDrawable);
        TestUtils.assertAllPixelsOfColor("Left strip red", leftStripDrawable,
                leftStripDrawable.getIntrinsicWidth(), leftStripDrawable.getIntrinsicHeight(),
                true, 0xFFFF0000, 1, false);

        Drawable rightStripDrawable = tabWidget.getRightStripDrawable();
        assertNotNull(rightStripDrawable);
        TestUtils.assertAllPixelsOfColor("Right strip green", rightStripDrawable,
                rightStripDrawable.getIntrinsicWidth(), rightStripDrawable.getIntrinsicHeight(),
                true, 0xFF00FF00, 1, false);
    }

    @UiThreadTest
    @Test
    public void testTabCount() {
        // We have one tab added in onCreate() of our activity
        assertEquals(1, mTabWidget.getTabCount());

        for (int i = 1; i < 10; i++) {
            mTabWidget.addView(new TextView(mActivity));
            assertEquals(i + 1, mTabWidget.getTabCount());
        }
    }

    @UiThreadTest
    @Test
    public void testTabViews() {
        // We have one tab added in onCreate() of our activity. We "reach" into the default tab
        // indicator layout in the same way we do in TabHost_TabSpecTest tests.
        TextView tab0 = (TextView) mTabWidget.getChildTabViewAt(0).findViewById(android.R.id.title);
        assertNotNull(tab0);
        assertEquals(TabHostCtsActivity.INITIAL_TAB_LABEL, tab0.getText());

        for (int i = 1; i < 10; i++) {
            TextView toAdd = new TextView(mActivity);
            toAdd.setText("Tab #" + i);
            mTabWidget.addView(toAdd);
            assertEquals(toAdd, mTabWidget.getChildTabViewAt(i));
        }
    }

    @UiThreadTest
    @Test
    public void testChildDrawableStateChanged() {
        MockTabWidget mockTabWidget = new MockTabWidget(mActivity);
        TextView tv0 = new TextView(mActivity);
        TextView tv1 = new TextView(mActivity);
        mockTabWidget.addView(tv0);
        mockTabWidget.addView(tv1);
        mockTabWidget.setCurrentTab(1);
        mockTabWidget.reset();
        mockTabWidget.childDrawableStateChanged(tv0);
        assertFalse(mockTabWidget.hasCalledInvalidate());

        mockTabWidget.reset();
        mockTabWidget.childDrawableStateChanged(tv1);
        assertTrue(mockTabWidget.hasCalledInvalidate());

        mockTabWidget.reset();
        mockTabWidget.childDrawableStateChanged(null);
        assertFalse(mockTabWidget.hasCalledInvalidate());
    }

    @UiThreadTest
    @Test
    public void testSetCurrentTab() {
        mTabWidget.addView(new TextView(mActivity));

        assertTrue(mTabWidget.getChildAt(0).isSelected());
        assertFalse(mTabWidget.getChildAt(1).isSelected());
        assertTrue(mTabWidget.getChildAt(0).isFocused());
        assertFalse(mTabWidget.getChildAt(1).isFocused());

        mTabWidget.setCurrentTab(1);
        assertFalse(mTabWidget.getChildAt(0).isSelected());
        assertTrue(mTabWidget.getChildAt(1).isSelected());
        assertTrue(mTabWidget.getChildAt(0).isFocused());
        assertFalse(mTabWidget.getChildAt(1).isFocused());
    }

    @UiThreadTest
    @Test
    public void testFocusCurrentTab() {
        mTabWidget.addView(new TextView(mActivity));

        assertTrue(mTabWidget.getChildAt(0).isSelected());
        assertFalse(mTabWidget.getChildAt(1).isSelected());
        assertEquals(mTabWidget.getChildAt(0), mTabWidget.getFocusedChild());
        assertTrue(mTabWidget.getChildAt(0).isFocused());
        assertFalse(mTabWidget.getChildAt(1).isFocused());

        // normal
        mTabWidget.focusCurrentTab(1);
        assertFalse(mTabWidget.getChildAt(0).isSelected());
        assertTrue(mTabWidget.getChildAt(1).isSelected());
        assertEquals(mTabWidget.getChildAt(1), mTabWidget.getFocusedChild());
        assertFalse(mTabWidget.getChildAt(0).isFocused());
        assertTrue(mTabWidget.getChildAt(1).isFocused());

        mTabWidget.focusCurrentTab(0);
        assertTrue(mTabWidget.getChildAt(0).isSelected());
        assertFalse(mTabWidget.getChildAt(1).isSelected());
        assertEquals(mTabWidget.getChildAt(0), mTabWidget.getFocusedChild());
        assertTrue(mTabWidget.getChildAt(0).isFocused());
        assertFalse(mTabWidget.getChildAt(1).isFocused());
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testFocusCurrentTabIndexTooLow() {
        mTabWidget.focusCurrentTab(-1);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testFocusCurrentTabIndexTooHigh() {
        mTabWidget.focusCurrentTab(mTabWidget.getChildCount() + 1);
    }

    @UiThreadTest
    @Test
    public void testSetEnabled() {
        mTabWidget.addView(new TextView(mActivity));
        mTabWidget.addView(new TextView(mActivity));
        assertTrue(mTabWidget.isEnabled());
        assertTrue(mTabWidget.getChildAt(0).isEnabled());
        assertTrue(mTabWidget.getChildAt(1).isEnabled());

        mTabWidget.setEnabled(false);
        assertFalse(mTabWidget.isEnabled());
        assertFalse(mTabWidget.getChildAt(0).isEnabled());
        assertFalse(mTabWidget.getChildAt(1).isEnabled());

        mTabWidget.setEnabled(true);
        assertTrue(mTabWidget.isEnabled());
        assertTrue(mTabWidget.getChildAt(0).isEnabled());
        assertTrue(mTabWidget.getChildAt(1).isEnabled());
    }

    @UiThreadTest
    @Test
    public void testAddView() {
        MockTabWidget mockTabWidget = new MockTabWidget(mActivity);

        // normal value
        View view1 = new TextView(mActivity);
        mockTabWidget.addView(view1);
        assertSame(view1, mockTabWidget.getChildAt(0));
        LayoutParams defaultLayoutParam = mockTabWidget.generateDefaultLayoutParams();
        if (mockTabWidget.getOrientation() == LinearLayout.VERTICAL) {
            assertEquals(defaultLayoutParam.height, LayoutParams.WRAP_CONTENT);
            assertEquals(defaultLayoutParam.width, LayoutParams.MATCH_PARENT);
        } else if (mockTabWidget.getOrientation() == LinearLayout.HORIZONTAL) {
            assertEquals(defaultLayoutParam.height, LayoutParams.WRAP_CONTENT);
            assertEquals(defaultLayoutParam.width, LayoutParams.WRAP_CONTENT);
        } else {
            assertNull(defaultLayoutParam);
        }

        View view2 = new RelativeLayout(mActivity);
        mockTabWidget.addView(view2);
        assertSame(view2, mockTabWidget.getChildAt(1));
    }

    @Test(expected=RuntimeException.class)
    public void testAddAdapterView() {
        MockTabWidget mockTabWidget = new MockTabWidget(mActivity);
        // Since TabWidget registers a click listener on each child, this is expected
        // to fail with anything that extends AdapterView
        mockTabWidget.addView(new ListView(mActivity));
    }

    @Test(expected=NullPointerException.class)
    public void testAddNullView() {
        MockTabWidget mockTabWidget = new MockTabWidget(mActivity);
        // Since TabWidget registers a click listener on each child, this is expected
        // to fail with anything that extends AdapterView
        mockTabWidget.addView(null);
    }

    @UiThreadTest
    @Test
    public void testStripEnabled() {
        mTabWidget.setStripEnabled(true);
        assertTrue(mTabWidget.isStripEnabled());

        mTabWidget.setStripEnabled(false);
        assertFalse(mTabWidget.isStripEnabled());
    }

    @Test
    public void testStripDrawables() throws Throwable {
        mTabWidget.setStripEnabled(true);

        // Test setting left strip drawable
        mActivityRule.runOnUiThread(() -> mTabWidget.setLeftStripDrawable(R.drawable.icon_green));
        Drawable leftStripDrawable = mTabWidget.getLeftStripDrawable();
        assertNotNull(leftStripDrawable);
        TestUtils.assertAllPixelsOfColor("Left strip green", leftStripDrawable,
                leftStripDrawable.getIntrinsicWidth(), leftStripDrawable.getIntrinsicHeight(),
                true, 0xFF00FF00, 1, false);

        mActivityRule.runOnUiThread(() -> mTabWidget.setLeftStripDrawable(
                mActivity.getDrawable(R.drawable.icon_red)));
        leftStripDrawable = mTabWidget.getLeftStripDrawable();
        assertNotNull(leftStripDrawable);
        TestUtils.assertAllPixelsOfColor("Left strip red", leftStripDrawable,
                leftStripDrawable.getIntrinsicWidth(), leftStripDrawable.getIntrinsicHeight(),
                true, 0xFFFF0000, 1, false);

        mActivityRule.runOnUiThread(() -> mTabWidget.setLeftStripDrawable(null));
        leftStripDrawable = mTabWidget.getLeftStripDrawable();
        assertNull(leftStripDrawable);

        // Wait for draw.
        mInstrumentation.waitForIdleSync();

        // Test setting right strip drawable
        mActivityRule.runOnUiThread(() -> mTabWidget.setRightStripDrawable(R.drawable.icon_red));
        Drawable rightStripDrawable = mTabWidget.getRightStripDrawable();
        assertNotNull(rightStripDrawable);
        TestUtils.assertAllPixelsOfColor("Right strip red", rightStripDrawable,
                rightStripDrawable.getIntrinsicWidth(), rightStripDrawable.getIntrinsicHeight(),
                true, 0xFFFF0000, 1, false);

        mActivityRule.runOnUiThread(() -> mTabWidget.setRightStripDrawable(
                mActivity.getDrawable(R.drawable.icon_green)));
        rightStripDrawable = mTabWidget.getRightStripDrawable();
        assertNotNull(rightStripDrawable);
        TestUtils.assertAllPixelsOfColor("Left strip green", rightStripDrawable,
                rightStripDrawable.getIntrinsicWidth(), rightStripDrawable.getIntrinsicHeight(),
                true, 0xFF00FF00, 1, false);

        mActivityRule.runOnUiThread(() -> mTabWidget.setRightStripDrawable(null));
        rightStripDrawable = mTabWidget.getRightStripDrawable();
        assertNull(rightStripDrawable);

        // Wait for draw.
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testDividerDrawables() {
        mTabWidget.setDividerDrawable(R.drawable.icon_blue);
        Drawable dividerDrawable = mTabWidget.getDividerDrawable();
        assertNotNull(dividerDrawable);
        TestUtils.assertAllPixelsOfColor("Divider blue", dividerDrawable,
                dividerDrawable.getIntrinsicWidth(), dividerDrawable.getIntrinsicHeight(),
                true, 0xFF0000FF, 1, false);

        mTabWidget.setDividerDrawable(mActivity.getDrawable(R.drawable.icon_yellow));
        dividerDrawable = mTabWidget.getDividerDrawable();
        assertNotNull(dividerDrawable);
        TestUtils.assertAllPixelsOfColor("Divider yellow", dividerDrawable,
                dividerDrawable.getIntrinsicWidth(), dividerDrawable.getIntrinsicHeight(),
                true, 0xFFFFFF00, 1, false);

    }

    /*
     * Mock class for TabWidget to be used in test cases.
     */
    private class MockTabWidget extends TabWidget {
        private boolean mCalledInvalidate = false;

        public MockTabWidget(Context context) {
            super(context);
        }

        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }

        @Override
        public void invalidate() {
            super.invalidate();
            mCalledInvalidate = true;
        }

        public boolean hasCalledInvalidate() {
            return mCalledInvalidate;
        }

        public void reset() {
            mCalledInvalidate = false;
        }
    }
}
