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
import android.content.res.XmlResourceParser;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ViewAsserts;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.RelativeLayout;
import android.widget.cts.util.XmlUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Test {@link RelativeLayout}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class RelativeLayoutTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<RelativeLayoutCtsActivity> mActivityRule =
            new ActivityTestRule<>(RelativeLayoutCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new RelativeLayout(mActivity);

        new RelativeLayout(mActivity, null);

        new RelativeLayout(mActivity, null, 0);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.relative_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new RelativeLayout(mActivity, attrs);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext() {
        new RelativeLayout(null, null);
    }

    @Test
    public void testSetIgnoreGravity() throws Throwable {
        // Initial gravity for this RelativeLayout is Gravity.Right.
        final RelativeLayout relativeLayout = (RelativeLayout) mActivity.findViewById(
                R.id.relative_sublayout_ignore_gravity);

        View view12 = mActivity.findViewById(R.id.relative_view12);
        View view13 = mActivity.findViewById(R.id.relative_view13);

        // set in xml, android:ignoreGravity="@id/relative_view12"
        ViewAsserts.assertLeftAligned(relativeLayout, view12);
        ViewAsserts.assertRightAligned(relativeLayout, view13);

        relativeLayout.setIgnoreGravity(R.id.relative_view13);
        mActivityRule.runOnUiThread(relativeLayout::requestLayout);
        mInstrumentation.waitForIdleSync();
        ViewAsserts.assertRightAligned(relativeLayout, view12);
        ViewAsserts.assertLeftAligned(relativeLayout, view13);

        relativeLayout.setIgnoreGravity(0);
        mActivityRule.runOnUiThread(relativeLayout::requestLayout);
        mInstrumentation.waitForIdleSync();
        ViewAsserts.assertRightAligned(relativeLayout, view12);
        ViewAsserts.assertRightAligned(relativeLayout, view13);
    }

    @Test
    public void testAccessGravity() throws Throwable {
        final RelativeLayout relativeLayout = (RelativeLayout) mActivity.findViewById(
                R.id.relative_sublayout_gravity);

        View view10 = mActivity.findViewById(R.id.relative_view10);
        View view11 = mActivity.findViewById(R.id.relative_view11);

        // Default: -- LEFT & TOP
        ViewAsserts.assertLeftAligned(relativeLayout, view10);
        ViewAsserts.assertTopAligned(relativeLayout, view10);
        ViewAsserts.assertLeftAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());

        // -- BOTTOM && RIGHT
        mActivityRule.runOnUiThread(
                () -> relativeLayout.setGravity(Gravity.BOTTOM | Gravity.RIGHT));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.BOTTOM | Gravity.RIGHT, relativeLayout.getGravity());
        ViewAsserts.assertRightAligned(relativeLayout, view10);
        assertEquals(view11.getTop(), view10.getBottom());
        ViewAsserts.assertRightAligned(relativeLayout, view11);
        ViewAsserts.assertBottomAligned(relativeLayout, view11);

        // -- BOTTOM
        mActivityRule.runOnUiThread(() -> relativeLayout.setGravity(Gravity.BOTTOM));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.BOTTOM | Gravity.START, relativeLayout.getGravity());
        ViewAsserts.assertLeftAligned(relativeLayout, view10);
        assertEquals(view11.getTop(), view10.getBottom());
        ViewAsserts.assertLeftAligned(relativeLayout, view11);
        ViewAsserts.assertBottomAligned(relativeLayout, view11);

        // CENTER_HORIZONTAL
        mActivityRule.runOnUiThread(() -> relativeLayout.setGravity(Gravity.CENTER_HORIZONTAL));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.CENTER_HORIZONTAL | Gravity.TOP,
                relativeLayout.getGravity());
        ViewAsserts.assertHorizontalCenterAligned(relativeLayout, view10);
        ViewAsserts.assertTopAligned(relativeLayout, view10);
        ViewAsserts.assertHorizontalCenterAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());

        // CENTER_VERTICAL
        mActivityRule.runOnUiThread(() -> relativeLayout.setGravity(Gravity.CENTER_VERTICAL));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.CENTER_VERTICAL | Gravity.START, relativeLayout.getGravity());
        ViewAsserts.assertLeftAligned(relativeLayout, view10);
        int topSpace = view10.getTop();
        int bottomSpace = relativeLayout.getHeight() - view11.getBottom();
        assertTrue(Math.abs(bottomSpace - topSpace) <= 1);
        ViewAsserts.assertLeftAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());
    }

    @Test
    public void testSetHorizontalGravity() throws Throwable {
        final RelativeLayout relativeLayout = (RelativeLayout) mActivity.findViewById(
                R.id.relative_sublayout_gravity);

        View view10 = mActivity.findViewById(R.id.relative_view10);
        View view11 = mActivity.findViewById(R.id.relative_view11);

        // Default: -- LEFT
        ViewAsserts.assertLeftAligned(relativeLayout, view10);
        ViewAsserts.assertTopAligned(relativeLayout, view10);
        ViewAsserts.assertLeftAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());

        // RIGHT
        mActivityRule.runOnUiThread(() -> relativeLayout.setHorizontalGravity(Gravity.RIGHT));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.RIGHT, Gravity.HORIZONTAL_GRAVITY_MASK & relativeLayout.getGravity());
        ViewAsserts.assertRightAligned(relativeLayout, view10);
        ViewAsserts.assertTopAligned(relativeLayout, view10);
        ViewAsserts.assertRightAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());

        // CENTER_HORIZONTAL
        mActivityRule.runOnUiThread(
                () -> relativeLayout.setHorizontalGravity(Gravity.CENTER_HORIZONTAL));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.CENTER_HORIZONTAL,
                Gravity.HORIZONTAL_GRAVITY_MASK & relativeLayout.getGravity());
        ViewAsserts.assertHorizontalCenterAligned(relativeLayout, view10);
        ViewAsserts.assertTopAligned(relativeLayout, view10);
        ViewAsserts.assertHorizontalCenterAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());
    }

    @Test
    public void testSetVerticalGravity() throws Throwable {
        final RelativeLayout relativeLayout = (RelativeLayout) mActivity.findViewById(
                R.id.relative_sublayout_gravity);

        View view10 = mActivity.findViewById(R.id.relative_view10);
        View view11 = mActivity.findViewById(R.id.relative_view11);

        // Default: -- TOP
        ViewAsserts.assertLeftAligned(relativeLayout, view10);
        ViewAsserts.assertTopAligned(relativeLayout, view10);
        ViewAsserts.assertLeftAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());

        // BOTTOM
        mActivityRule.runOnUiThread(() -> relativeLayout.setVerticalGravity(Gravity.BOTTOM));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.BOTTOM, Gravity.VERTICAL_GRAVITY_MASK & relativeLayout.getGravity());
        ViewAsserts.assertLeftAligned(relativeLayout, view10);
        assertEquals(view11.getTop(), view10.getBottom());
        ViewAsserts.assertLeftAligned(relativeLayout, view11);
        ViewAsserts.assertBottomAligned(relativeLayout, view11);

        // CENTER_VERTICAL
        mActivityRule.runOnUiThread(
                () -> relativeLayout.setVerticalGravity(Gravity.CENTER_VERTICAL));
        mInstrumentation.waitForIdleSync();
        assertEquals(Gravity.CENTER_VERTICAL,
                Gravity.VERTICAL_GRAVITY_MASK & relativeLayout.getGravity());
        ViewAsserts.assertLeftAligned(relativeLayout, view10);
        int topSpace = view10.getTop();
        int bottomSpace = relativeLayout.getHeight() - view11.getBottom();
        assertTrue(Math.abs(bottomSpace - topSpace) <= 1);
        ViewAsserts.assertLeftAligned(relativeLayout, view11);
        assertEquals(view11.getTop(), view10.getBottom());
    }

    @Test
    public void testGetBaseline() {
        RelativeLayout relativeLayout = new RelativeLayout(mActivity);
        assertEquals(-1, relativeLayout.getBaseline());

        relativeLayout = (RelativeLayout) mActivity.findViewById(R.id.relative_sublayout_attrs);
        View view = mActivity.findViewById(R.id.relative_view1);
        assertEquals(view.getBaseline(), relativeLayout.getBaseline());
    }

    @Test
    public void testGenerateLayoutParams1() throws XmlPullParserException, IOException {
        RelativeLayout relativeLayout = new RelativeLayout(mActivity);

        // normal value
        XmlResourceParser parser = mActivity.getResources().getLayout(R.layout.relative_layout);
        XmlUtils.beginDocument(parser, "RelativeLayout");
        LayoutParams layoutParams = relativeLayout.generateLayoutParams(parser);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.width);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.height);
    }

    @Test
    public void testGenerateLayoutParams2() {
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(200, 300);

        MyRelativeLayout myRelativeLayout = new MyRelativeLayout(mActivity);

        // normal value
         RelativeLayout.LayoutParams layoutParams =
                 (RelativeLayout.LayoutParams) myRelativeLayout.generateLayoutParams(p);
         assertEquals(200, layoutParams.width);
         assertEquals(300, layoutParams.height);
    }

    @Test(expected=NullPointerException.class)
    public void testGenerateLayoutParamsFromNull() {
        MyRelativeLayout myRelativeLayout = new MyRelativeLayout(mActivity);
        myRelativeLayout.generateLayoutParams((ViewGroup.LayoutParams) null);
    }

    @Test
    public void testGenerateDefaultLayoutParams() {
        MyRelativeLayout myRelativeLayout = new MyRelativeLayout(mActivity);

        ViewGroup.LayoutParams layoutParams = myRelativeLayout.generateDefaultLayoutParams();
        assertTrue(layoutParams instanceof RelativeLayout.LayoutParams);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, layoutParams.width);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, layoutParams.height);
    }

    @Test
    public void testGenerateLayoutParamsFromMarginParams() {
        MyRelativeLayout layout = new MyRelativeLayout(mActivity);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(3, 5);
        lp.leftMargin = 1;
        lp.topMargin = 2;
        lp.rightMargin = 3;
        lp.bottomMargin = 4;
        RelativeLayout.LayoutParams generated = (RelativeLayout.LayoutParams)
                layout.generateLayoutParams(lp);
        assertNotNull(generated);
        assertEquals(3, generated.width);
        assertEquals(5, generated.height);

        assertEquals(1, generated.leftMargin);
        assertEquals(2, generated.topMargin);
        assertEquals(3, generated.rightMargin);
        assertEquals(4, generated.bottomMargin);
    }

    @Test
    public void testCheckLayoutParams() {
        MyRelativeLayout myRelativeLayout = new MyRelativeLayout(mActivity);

        ViewGroup.LayoutParams p1 = new ViewGroup.LayoutParams(200, 300);
        assertFalse(myRelativeLayout.checkLayoutParams(p1));

        RelativeLayout.LayoutParams p2 = new RelativeLayout.LayoutParams(200, 300);
        assertTrue(myRelativeLayout.checkLayoutParams(p2));

        AbsListView.LayoutParams p3 = new AbsListView.LayoutParams(200, 300);
        assertFalse(myRelativeLayout.checkLayoutParams(p3));
    }

    @Test
    public void testGetRule() {
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(0, 0);
        p.addRule(RelativeLayout.LEFT_OF, R.id.abslistview_root);
        p.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

        assertEquals("Get resource ID rule", R.id.abslistview_root,
                p.getRule(RelativeLayout.LEFT_OF));
        assertEquals("Get boolean rule", RelativeLayout.TRUE,
                p.getRule(RelativeLayout.CENTER_IN_PARENT));
        assertEquals("Get missing rule", 0, p.getRule(RelativeLayout.ABOVE));
    }

    /**
     * Tests to prevent regressions in baseline alignment.
     */
    @Test
    public void testBaselineAlignment() throws Throwable {
        mActivityRule.runOnUiThread(
                () -> mActivity.setContentView(R.layout.relative_layout_baseline));
        mInstrumentation.waitForIdleSync();

        View button = mActivity.findViewById(R.id.button1);
        assertTrue(button.getHeight() > 0);

        button = mActivity.findViewById(R.id.button2);
        assertTrue(button.getHeight() > 0);

        button = mActivity.findViewById(R.id.button3);
        assertTrue(button.getHeight() > 0);

        button = mActivity.findViewById(R.id.button4);
        assertTrue(button.getHeight() > 0);
    }

    @Test
    public void testBidiWidth() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(R.layout.relative_layout_bidi);
            mActivity.findViewById(R.id.relative_sublayout_bidi)
                     .setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        });
        mInstrumentation.waitForIdleSync();

        final View ltrLayout = mActivity.findViewById(R.id.relative_sublayout_bidi);
        assertNotNull(ltrLayout);
        final int ltrWidth = ltrLayout.getWidth();

        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(R.layout.relative_layout_bidi);
            mActivity.findViewById(R.id.relative_sublayout_bidi)
                     .setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        });
        mInstrumentation.waitForIdleSync();

        final View rtlLayout = mActivity.findViewById(R.id.relative_sublayout_bidi);
        assertNotNull(rtlLayout);
        final int rtlWidth = rtlLayout.getWidth();

        assertEquals(ltrWidth, rtlWidth);
    }

    private class MyRelativeLayout extends RelativeLayout {
        public MyRelativeLayout(Context context) {
            super(context);
        }

        @Override
        protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
            return super.checkLayoutParams(p);
        }

        @Override
        protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }
        @Override
        protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
            return super.generateLayoutParams(p);
        }
    }
}
