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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.ColorInt;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ViewAsserts;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsoluteLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link LinearLayout}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class LinearLayoutTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<LinearLayoutCtsActivity> mActivityRule =
            new ActivityTestRule<>(LinearLayoutCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new LinearLayout(mActivity);

        new LinearLayout(mActivity, null);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.linearlayout_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new LinearLayout(mActivity, attrs);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext() {
        new LinearLayout(null, null);
    }

    @UiThreadTest
    @Test
    public void testAccessBaselineAligned() {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_empty);
        parent.setBaselineAligned(true);
        assertTrue(parent.isBaselineAligned());

        parent.setBaselineAligned(false);
        assertFalse(parent.isBaselineAligned());

        // android:baselineAligned="false" in LinearLayout weightsum
        parent = (LinearLayout) mActivity.findViewById(R.id.linear_weightsum);
        assertFalse(parent.isBaselineAligned());

        // default mBaselineAligned is true.
        parent = (LinearLayout) mActivity.findViewById(R.id.linear_horizontal);
        assertTrue(parent.isBaselineAligned());

        // default mBaselineAligned is true.
        // Only applicable if {@link #mOrientation} is horizontal
        parent = (LinearLayout) mActivity.findViewById(R.id.linear_vertical);
        assertTrue(parent.isBaselineAligned());
    }

    @UiThreadTest
    @Test
    public void testGetBaseline() {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_empty);

        ListView lv1 = new ListView(mActivity);
        parent.addView(lv1);
        assertEquals(-1, parent.getBaseline());

        ListView lv2 = new ListView(mActivity);
        parent.addView(lv2);
        parent.setBaselineAlignedChildIndex(1);
        try {
            parent.getBaseline();
            fail("LinearLayout.getBaseline() should throw exception here.");
        } catch (RuntimeException e) {
        }

        ListView lv3 = new MockListView(mActivity);
        parent.addView(lv3);
        parent.setBaselineAlignedChildIndex(2);
        assertEquals(lv3.getBaseline(), parent.getBaseline());
    }

    @UiThreadTest
    @Test
    public void testAccessBaselineAlignedChildIndex() {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_empty);

        // set BaselineAlignedChildIndex
        ListView lv1 = new ListView(mActivity);
        ListView lv2 = new ListView(mActivity);
        ListView lv3 = new ListView(mActivity);
        parent.addView(lv1);
        parent.addView(lv2);
        parent.addView(lv3);
        parent.setBaselineAlignedChildIndex(1);
        assertEquals(1, parent.getBaselineAlignedChildIndex());

        parent.setBaselineAlignedChildIndex(2);
        assertEquals(2, parent.getBaselineAlignedChildIndex());

        try {
            parent.setBaselineAlignedChildIndex(-1);
            fail("LinearLayout should throw IllegalArgumentException here.");
        } catch (IllegalArgumentException e) {
        }
        try {
            parent.setBaselineAlignedChildIndex(3);
            fail("LinearLayout should throw IllegalArgumentException here.");
        } catch (IllegalArgumentException e) {
        }

        parent = (LinearLayout) mActivity.findViewById(R.id.linear_baseline_aligned_child_index);
        assertEquals(1, parent.getBaselineAlignedChildIndex());
    }

    /**
     * weightsum is a horizontal LinearLayout. There are three children in it.
     */
    @Test
    public void testAccessWeightSum() {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_weightsum);
        TextView weight02 = (TextView) parent.findViewById(R.id.weight_0_2);
        TextView weight05 = (TextView) parent.findViewById(R.id.weight_0_5);
        TextView weight03 = (TextView) parent.findViewById(R.id.weight_0_3);

        assertNotNull(parent);
        assertNotNull(weight02);
        assertNotNull(weight05);
        assertNotNull(weight03);

        assertEquals(mActivity.getResources().getString(R.string.horizontal_text_1),
                weight02.getText().toString());
        assertEquals(mActivity.getResources().getString(R.string.horizontal_text_2),
                weight05.getText().toString());
        assertEquals(mActivity.getResources().getString(R.string.horizontal_text_3),
                weight03.getText().toString());

        assertEquals(LinearLayout.HORIZONTAL, parent.getOrientation());
        assertEquals(1.0f, parent.getWeightSum(), 0.0f);

        int parentWidth = parent.getWidth();
        assertEquals(Math.ceil(parentWidth * 0.2), weight02.getWidth(), 1.0);
        assertEquals(Math.ceil(parentWidth * 0.5), weight05.getWidth(), 1.0);
        assertEquals(Math.ceil(parentWidth * 0.3), weight03.getWidth(), 1.0);
    }

    @UiThreadTest
    @Test
    public void testWeightDistribution() {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_empty);

        for (int i = 0; i < 3; i++) {
            parent.addView(new View(mActivity), new LayoutParams(0, 0, 1));
        }

        int size = 100;
        int spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);

        for (int i = 0; i < 3; i++) {
            View child = parent.getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.height = 0;
            lp.width = LayoutParams.MATCH_PARENT;
            child.setLayoutParams(lp);
        }
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.measure(spec, spec);
        parent.layout(0, 0, size, size);
        assertEquals(100, parent.getWidth());
        assertEquals(100, parent.getChildAt(0).getWidth());
        assertEquals(100, parent.getChildAt(1).getWidth());
        assertEquals(100, parent.getChildAt(2).getWidth());
        assertEquals(100, parent.getHeight());
        assertEquals(33, parent.getChildAt(0).getHeight());
        assertEquals(33, parent.getChildAt(1).getHeight());
        assertEquals(34, parent.getChildAt(2).getHeight());

        for (int i = 0; i < 3; i++) {
            View child = parent.getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.height = LayoutParams.MATCH_PARENT;
            lp.width = 0;
            child.setLayoutParams(lp);
        }
        parent.setOrientation(LinearLayout.HORIZONTAL);
        parent.measure(spec, spec);
        parent.layout(0, 0, size, size);
        assertEquals(100, parent.getWidth());
        assertEquals(33, parent.getChildAt(0).getWidth());
        assertEquals(33, parent.getChildAt(1).getWidth());
        assertEquals(34, parent.getChildAt(2).getWidth());
        assertEquals(100, parent.getHeight());
        assertEquals(100, parent.getChildAt(0).getHeight());
        assertEquals(100, parent.getChildAt(1).getHeight());
        assertEquals(100, parent.getChildAt(2).getHeight());
    }

    @UiThreadTest
    @Test
    public void testGenerateLayoutParams() {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(320, 240);
        MockLinearLayout parent = (MockLinearLayout) mActivity.findViewById(R.id.linear_custom);
        LayoutParams layoutParams1 = parent.generateLayoutParams(lp);
        assertEquals(320, layoutParams1.width);
        assertEquals(240, layoutParams1.height);
    }

    @UiThreadTest
    @Test
    public void testCheckLayoutParams() {
        MockLinearLayout parent = (MockLinearLayout) mActivity.findViewById(R.id.linear_custom);

        ViewGroup.LayoutParams params = new AbsoluteLayout.LayoutParams(240, 320, 0, 0);
        assertFalse(parent.checkLayoutParams(params));

        params = new LinearLayout.LayoutParams(240, 320);
        assertTrue(parent.checkLayoutParams(params));
    }

    @UiThreadTest
    @Test
    public void testGenerateDefaultLayoutParams() {
        MockLinearLayout parent = (MockLinearLayout) mActivity.findViewById(R.id.linear_custom);

        parent.setOrientation(LinearLayout.HORIZONTAL);
        ViewGroup.LayoutParams param = parent.generateDefaultLayoutParams();
        assertNotNull(param);
        assertTrue(param instanceof LinearLayout.LayoutParams);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, param.width);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, param.height);

        parent.setOrientation(LinearLayout.VERTICAL);
        param = parent.generateDefaultLayoutParams();
        assertNotNull(param);
        assertTrue(param instanceof LinearLayout.LayoutParams);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, param.width);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, param.height);

        parent.setOrientation(-1);
        assertNull(parent.generateDefaultLayoutParams());
    }

    @UiThreadTest
    @Test
    public void testGenerateLayoutParamsFromMarginParams() {
        MockLinearLayout parent = (MockLinearLayout) mActivity.findViewById(R.id.linear_custom);

        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(3, 5);
        lp.leftMargin = 1;
        lp.topMargin = 2;
        lp.rightMargin = 3;
        lp.bottomMargin = 4;
        LinearLayout.LayoutParams generated = parent.generateLayoutParams(lp);
        assertNotNull(generated);
        assertEquals(3, generated.width);
        assertEquals(5, generated.height);

        assertEquals(1, generated.leftMargin);
        assertEquals(2, generated.topMargin);
        assertEquals(3, generated.rightMargin);
        assertEquals(4, generated.bottomMargin);
    }

    /**
     * layout of horizontal LinearLayout.
     * ----------------------------------------------------
     * | ------------ |                 |                 |
     * | | top view | | --------------- |                 |
     * | |          | | | center view | | --------------- |
     * | ------------ | |             | | | bottom view | |
     * |              | --------------- | |             | |
     * |     parent   |                 | --------------- |
     * ----------------------------------------------------
     */
    @Test
    public void testLayoutHorizontal() {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_horizontal);
        TextView topView = (TextView) mActivity.findViewById(R.id.gravity_top);
        TextView centerView = (TextView) mActivity.findViewById(R.id.gravity_center_vertical);
        TextView bottomView = (TextView) mActivity.findViewById(R.id.gravity_bottom);

        assertNotNull(parent);
        assertNotNull(topView);
        assertNotNull(centerView);
        assertNotNull(bottomView);

        assertEquals(mActivity.getResources().getString(R.string.horizontal_text_1),
                topView.getText().toString());
        assertEquals(mActivity.getResources().getString(R.string.horizontal_text_2),
                centerView.getText().toString());
        assertEquals(mActivity.getResources().getString(R.string.horizontal_text_3),
                bottomView.getText().toString());

        assertEquals(LinearLayout.HORIZONTAL, parent.getOrientation());

        ViewAsserts.assertTopAligned(parent, topView);
        ViewAsserts.assertVerticalCenterAligned(parent, centerView);
        ViewAsserts.assertBottomAligned(parent, bottomView);

        assertEquals(0, topView.getTop());
        assertEquals(topView.getHeight(), topView.getBottom());
        assertEquals(0, topView.getLeft());
        assertEquals(centerView.getLeft(), topView.getRight());

        int offset = (parent.getHeight() - centerView.getHeight()) / 2;
        assertEquals(offset, centerView.getTop());
        assertEquals(offset + centerView.getHeight(), centerView.getBottom());
        assertEquals(topView.getRight(), centerView.getLeft());
        assertEquals(bottomView.getLeft(), centerView.getRight());

        assertEquals(parent.getHeight() - bottomView.getHeight(), bottomView.getTop());
        assertEquals(parent.getHeight(), bottomView.getBottom());
        assertEquals(centerView.getRight(), bottomView.getLeft());
        assertEquals(parent.getWidth(), bottomView.getRight());
    }

    /**
     * layout of vertical LinearLayout.
     * -----------------------------------
     * | -------------                   |
     * | | left view |                   |
     * | -------------                   |
     * | - - - - - - - - - - - - - - - - |
     * |        ---------------          |
     * |        | center view |          |
     * |        ---------------          |
     * | - - - - - - - - - - - - - - - - |
     * |                  -------------- |
     * | parent           | right view | |
     * |                  -------------- |
     * -----------------------------------
     */
    @Test
    public void testLayoutVertical() {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_vertical);
        TextView leftView = (TextView) mActivity.findViewById(R.id.gravity_left);
        TextView centerView = (TextView) mActivity.findViewById(R.id.gravity_center_horizontal);
        TextView rightView = (TextView) mActivity.findViewById(R.id.gravity_right);

        assertNotNull(parent);
        assertNotNull(leftView);
        assertNotNull(centerView);
        assertNotNull(rightView);

        assertEquals(mActivity.getResources().getString(R.string.vertical_text_1),
                leftView.getText().toString());
        assertEquals(mActivity.getResources().getString(R.string.vertical_text_2),
                centerView.getText().toString());
        assertEquals(mActivity.getResources().getString(R.string.vertical_text_3),
                rightView.getText().toString());

        assertEquals(LinearLayout.VERTICAL, parent.getOrientation());

        ViewAsserts.assertLeftAligned(parent, leftView);
        ViewAsserts.assertHorizontalCenterAligned(parent, centerView);
        ViewAsserts.assertRightAligned(parent, rightView);

        assertEquals(0, leftView.getTop());
        assertEquals(centerView.getTop(), leftView.getBottom());
        assertEquals(0, leftView.getLeft());
        assertEquals(leftView.getWidth(), leftView.getRight());

        int offset = (parent.getWidth() - centerView.getWidth()) / 2;
        assertEquals(leftView.getBottom(), centerView.getTop());
        assertEquals(rightView.getTop(), centerView.getBottom());
        assertEquals(offset, centerView.getLeft());
        assertEquals(offset + centerView.getWidth(), centerView.getRight());

        assertEquals(centerView.getBottom(), rightView.getTop());
        assertEquals(parent.getHeight(), rightView.getBottom());
        assertEquals(parent.getWidth() - rightView.getWidth(), rightView.getLeft());
        assertEquals(parent.getWidth(), rightView.getRight());
    }

    @Test
    public void testVerticalCenterGravityOnHorizontalLayout() throws Throwable {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_weightsum);
        TextView leftView = (TextView) parent.findViewById(R.id.weight_0_2);
        TextView centerView = (TextView) parent.findViewById(R.id.weight_0_5);
        TextView rightView = (TextView) parent.findViewById(R.id.weight_0_3);

        mActivityRule.runOnUiThread(() -> parent.setLayoutDirection(View.LAYOUT_DIRECTION_LTR));
        mInstrumentation.waitForIdleSync();

        int originalLeftViewLeft = leftView.getLeft();
        int originalLeftViewRight = leftView.getRight();
        int originalCenterViewLeft = centerView.getLeft();
        int originalCenterViewRight = centerView.getRight();
        int originalRightViewLeft = rightView.getLeft();
        int originalRightViewRight = rightView.getRight();

        mActivityRule.runOnUiThread(() -> parent.setVerticalGravity(Gravity.CENTER_VERTICAL));
        mInstrumentation.waitForIdleSync();

        assertEquals(Gravity.CENTER_VERTICAL, parent.getGravity() & Gravity.VERTICAL_GRAVITY_MASK);

        ViewAsserts.assertVerticalCenterAligned(parent, leftView);
        ViewAsserts.assertVerticalCenterAligned(parent, centerView);
        ViewAsserts.assertVerticalCenterAligned(parent, rightView);

        final int parentHeight = parent.getHeight();

        int verticalOffset = (parentHeight - leftView.getHeight()) / 2;
        assertEquals(verticalOffset, leftView.getTop());
        assertEquals(verticalOffset + leftView.getHeight(), leftView.getBottom());
        assertEquals(originalLeftViewLeft, leftView.getLeft());
        assertEquals(originalLeftViewRight, leftView.getRight());

        verticalOffset = (parentHeight - centerView.getHeight()) / 2;
        assertEquals(verticalOffset, centerView.getTop());
        assertEquals(verticalOffset + centerView.getHeight(), centerView.getBottom());
        assertEquals(originalCenterViewLeft, centerView.getLeft());
        assertEquals(originalCenterViewRight, centerView.getRight());

        verticalOffset = (parentHeight - rightView.getHeight()) / 2;
        assertEquals(verticalOffset, rightView.getTop());
        assertEquals(verticalOffset + rightView.getHeight(), rightView.getBottom());
        assertEquals(originalRightViewLeft, rightView.getLeft());
        assertEquals(originalRightViewRight, rightView.getRight());
    }

    @Test
    public void testBottomGravityOnHorizontalLayout() throws Throwable {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_weightsum);
        TextView leftView = (TextView) parent.findViewById(R.id.weight_0_2);
        TextView centerView = (TextView) parent.findViewById(R.id.weight_0_5);
        TextView rightView = (TextView) parent.findViewById(R.id.weight_0_3);

        mActivityRule.runOnUiThread(() -> parent.setLayoutDirection(View.LAYOUT_DIRECTION_LTR));
        mInstrumentation.waitForIdleSync();

        int originalLeftViewLeft = leftView.getLeft();
        int originalLeftViewRight = leftView.getRight();
        int originalCenterViewLeft = centerView.getLeft();
        int originalCenterViewRight = centerView.getRight();
        int originalRightViewLeft = rightView.getLeft();
        int originalRightViewRight = rightView.getRight();

        mActivityRule.runOnUiThread(() -> parent.setVerticalGravity(Gravity.BOTTOM));
        mInstrumentation.waitForIdleSync();

        assertEquals(Gravity.BOTTOM, parent.getGravity() & Gravity.VERTICAL_GRAVITY_MASK);

        ViewAsserts.assertBottomAligned(parent, leftView);
        ViewAsserts.assertBottomAligned(parent, centerView);
        ViewAsserts.assertBottomAligned(parent, rightView);

        final int parentHeight = parent.getHeight();

        assertEquals(parentHeight - leftView.getHeight(), leftView.getTop());
        assertEquals(parentHeight, leftView.getBottom());
        assertEquals(originalLeftViewLeft, leftView.getLeft());
        assertEquals(originalLeftViewRight, leftView.getRight());

        assertEquals(parentHeight - centerView.getHeight(), centerView.getTop());
        assertEquals(parentHeight, centerView.getBottom());
        assertEquals(originalCenterViewLeft, centerView.getLeft());
        assertEquals(originalCenterViewRight, centerView.getRight());

        assertEquals(parentHeight - rightView.getHeight(), rightView.getTop());
        assertEquals(parentHeight, rightView.getBottom());
        assertEquals(originalRightViewLeft, rightView.getLeft());
        assertEquals(originalRightViewRight, rightView.getRight());
    }

    @Test
    public void testHorizontalCenterGravityOnVerticalLayout() throws Throwable {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_weightsum_vertical);
        TextView topView = (TextView) parent.findViewById(R.id.weight_0_1);
        TextView centerView = (TextView) parent.findViewById(R.id.weight_0_4);
        TextView bottomView = (TextView) parent.findViewById(R.id.weight_0_5);

        mActivityRule.runOnUiThread(() -> parent.setLayoutDirection(View.LAYOUT_DIRECTION_LTR));
        mInstrumentation.waitForIdleSync();

        final int parentWidth = parent.getHeight();

        int originalTopViewTop = topView.getTop();
        int originalTopViewBottom = topView.getBottom();
        int originalCenterViewTop = centerView.getTop();
        int originalCenterViewBottom = centerView.getBottom();
        int originalBottomViewTop = bottomView.getTop();
        int originalBottomViewBottom = bottomView.getBottom();

        mActivityRule.runOnUiThread(
                () -> parent.setHorizontalGravity(Gravity.CENTER_HORIZONTAL));
        mInstrumentation.waitForIdleSync();

        assertEquals(Gravity.CENTER_HORIZONTAL,
                parent.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);

        ViewAsserts.assertHorizontalCenterAligned(parent, topView);
        ViewAsserts.assertHorizontalCenterAligned(parent, centerView);
        ViewAsserts.assertHorizontalCenterAligned(parent, bottomView);

        int horizontalOffset = (parentWidth - topView.getWidth()) / 2;
        assertEquals(originalTopViewTop, topView.getTop());
        assertEquals(originalTopViewBottom, topView.getBottom());
        assertEquals(horizontalOffset, topView.getLeft());
        assertEquals(horizontalOffset + topView.getWidth(), topView.getRight());

        horizontalOffset = (parentWidth - centerView.getWidth()) / 2;
        assertEquals(originalCenterViewTop, centerView.getTop());
        assertEquals(originalCenterViewBottom, centerView.getBottom());
        assertEquals(horizontalOffset, centerView.getLeft());
        assertEquals(horizontalOffset + centerView.getWidth(), centerView.getRight());

        horizontalOffset = (parentWidth - bottomView.getWidth()) / 2;
        assertEquals(originalBottomViewTop, bottomView.getTop());
        assertEquals(originalBottomViewBottom, bottomView.getBottom());
        assertEquals(horizontalOffset, bottomView.getLeft());
        assertEquals(horizontalOffset + bottomView.getWidth(), bottomView.getRight());
    }

    @Test
    public void testRightGravityOnVerticalLayout() throws Throwable {
        LinearLayout parent = (LinearLayout) mActivity.findViewById(R.id.linear_weightsum_vertical);
        TextView topView = (TextView) parent.findViewById(R.id.weight_0_1);
        TextView centerView = (TextView) parent.findViewById(R.id.weight_0_4);
        TextView bottomView = (TextView) parent.findViewById(R.id.weight_0_5);

        mActivityRule.runOnUiThread(() -> parent.setLayoutDirection(View.LAYOUT_DIRECTION_LTR));
        mInstrumentation.waitForIdleSync();

        final int parentWidth = parent.getHeight();

        int originalTopViewTop = topView.getTop();
        int originalTopViewBottom = topView.getBottom();
        int originalCenterViewTop = centerView.getTop();
        int originalCenterViewBottom = centerView.getBottom();
        int originalBottomViewTop = bottomView.getTop();
        int originalBottomViewBottom = bottomView.getBottom();

        mActivityRule.runOnUiThread(() -> parent.setHorizontalGravity(Gravity.RIGHT));
        mInstrumentation.waitForIdleSync();

        assertEquals(Gravity.RIGHT, parent.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);

        ViewAsserts.assertRightAligned(parent, topView);
        ViewAsserts.assertRightAligned(parent, centerView);
        ViewAsserts.assertRightAligned(parent, bottomView);

        assertEquals(originalTopViewTop, topView.getTop());
        assertEquals(originalTopViewBottom, topView.getBottom());
        assertEquals(parentWidth - topView.getWidth(), topView.getLeft());
        assertEquals(parentWidth, topView.getRight());

        assertEquals(originalCenterViewTop, centerView.getTop());
        assertEquals(originalCenterViewBottom, centerView.getBottom());
        assertEquals(parentWidth - centerView.getWidth(), centerView.getLeft());
        assertEquals(parentWidth, centerView.getRight());

        assertEquals(originalBottomViewTop, bottomView.getTop());
        assertEquals(originalBottomViewBottom, bottomView.getBottom());
        assertEquals(parentWidth - bottomView.getWidth(), bottomView.getLeft());
        assertEquals(parentWidth, bottomView.getRight());
    }

    private void verifyBounds(final ViewGroup viewGroup, final View view,
            final CountDownLatch countDownLatch, final int left, final int top,
            final int width, final int height) {
        viewGroup.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        assertEquals(left, view.getLeft());
                        assertEquals(top, view.getTop());
                        assertEquals(width, view.getWidth());
                        assertEquals(height, view.getHeight());
                        countDownLatch.countDown();
                        viewGroup.getViewTreeObserver().removeOnPreDrawListener(this);
                        return true;
                    }
                });
    }

    @Test
    public void testVisibilityAffectsLayout() throws Throwable {
        // Toggling view visibility between GONE/VISIBLE can affect the position of
        // other children in that container. This test verifies that these changes
        // on the first child of a LinearLayout affects the position of a second child
        final int childWidth = 100;
        final int childHeight = 200;
        final LinearLayout parent = new LinearLayout(mActivity);
        ViewGroup.LayoutParams parentParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        parent.setLayoutParams(parentParams);
        final View child1 = new View(mActivity);
        child1.setBackgroundColor(Color.GREEN);
        ViewGroup.LayoutParams childParams = new ViewGroup.LayoutParams(childWidth, childHeight);
        child1.setLayoutParams(childParams);
        final View child2 = new View(mActivity);
        child2.setBackgroundColor(Color.RED);
        childParams = new ViewGroup.LayoutParams(childWidth, childHeight);
        child2.setLayoutParams(childParams);
        final ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.linearlayout_root);

        final CountDownLatch countDownLatch1 = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            viewGroup.removeAllViews();
            viewGroup.addView(parent);
            parent.addView(child1);
            parent.addView(child2);
            verifyBounds(viewGroup, child1, countDownLatch1, 0, 0, childWidth, childHeight);
            verifyBounds(viewGroup, child2, countDownLatch1,
                    childWidth, 0, childWidth, childHeight);
        });
        try {
            assertTrue(countDownLatch1.await(500, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        final CountDownLatch countDownLatch2 = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            child1.setVisibility(View.GONE);
            verifyBounds(viewGroup, child2, countDownLatch2, 0, 0, childWidth, childHeight);
        });
        try {
            assertTrue(countDownLatch2.await(500, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        final CountDownLatch countDownLatch3 = new CountDownLatch(2);
        mActivityRule.runOnUiThread(() -> {
            child1.setVisibility(View.VISIBLE);
            verifyBounds(viewGroup, child1, countDownLatch3, 0, 0, childWidth, childHeight);
            verifyBounds(viewGroup, child2, countDownLatch3,
                    childWidth, 0, childWidth, childHeight);
        });
        try {
            assertTrue(countDownLatch3.await(500, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }
    }

    private void verifyVisualsOfVerticalLayoutWithDivider(LinearLayout parent,
            int expectedDividerPositionMask,
            int expectedDividerSize, @ColorInt int expectedDividerColor,
            int expectedDividerPadding) {
        final int parentWidth = parent.getWidth();
        final int parentHeight = parent.getHeight();

        final boolean expectingTopDivider =
                (expectedDividerPositionMask & LinearLayout.SHOW_DIVIDER_BEGINNING) != 0;
        final boolean expectingMiddleDivider =
                (expectedDividerPositionMask & LinearLayout.SHOW_DIVIDER_MIDDLE) != 0;
        final boolean expectingBottomDivider =
                (expectedDividerPositionMask & LinearLayout.SHOW_DIVIDER_END) != 0;
        final int expectedDividerCount = (expectingTopDivider ? 1 : 0)
                + (expectingMiddleDivider ? 1 : 0) + (expectingBottomDivider ? 1 : 0);

        final int expectedChildHeight =
                (parentHeight - expectedDividerCount * expectedDividerSize) / 2;

        final int expectedTopChildTop = expectingTopDivider ? expectedDividerSize : 0;
        TestUtils.assertRegionPixelsOfColor("Region of first child is blue", parent,
                new Rect(0, expectedTopChildTop, parentWidth,
                        expectedTopChildTop + expectedChildHeight),
                Color.BLUE, 1, true);

        final int expectedBottomChildBottom =
                expectingBottomDivider ? parentHeight - expectedDividerSize : parentHeight;
        TestUtils.assertRegionPixelsOfColor("Region of second child is green", parent,
                new Rect(0, expectedBottomChildBottom - expectedChildHeight, parentWidth,
                        expectedBottomChildBottom),
                Color.GREEN, 1, true);

        if (expectedDividerSize == 0) {
            return;
        }

        // Do we expect top divider?
        if (expectingTopDivider) {
            TestUtils.assertRegionPixelsOfColor(
                    "Region of top divider is " + TestUtils.formatColorToHex(expectedDividerColor),
                    parent,
                    new Rect(expectedDividerPadding, 0, parentWidth - expectedDividerPadding,
                            expectedDividerSize),
                    expectedDividerColor, 1, true);
            TestUtils.assertRegionPixelsOfColor("Region of left padding of top divider is yellow",
                    parent,
                    new Rect(0, 0, expectedDividerPadding, expectedDividerSize),
                    Color.YELLOW, 1, true);
            TestUtils.assertRegionPixelsOfColor("Region of right padding of top divider is yellow",
                    parent,
                    new Rect(parentWidth - expectedDividerPadding, 0, parentWidth,
                            expectedDividerSize),
                    Color.YELLOW, 1, true);
        }

        // Do we expect middle divider?
        if (expectingMiddleDivider) {
            final int expectedMiddleDividerTop = expectedTopChildTop + expectedChildHeight;
            TestUtils.assertRegionPixelsOfColor(
                    "Region of middle divider is " +
                            TestUtils.formatColorToHex(expectedDividerColor),
                    parent,
                    new Rect(expectedDividerPadding, expectedMiddleDividerTop,
                            parentWidth - expectedDividerPadding,
                            expectedMiddleDividerTop + expectedDividerSize),
                    expectedDividerColor, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of left padding of middle divider is yellow",
                    parent,
                    new Rect(0, expectedMiddleDividerTop, expectedDividerPadding,
                            expectedMiddleDividerTop + expectedDividerSize),
                    Color.YELLOW, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of right padding of middle divider is yellow",
                    parent,
                    new Rect(parentWidth - expectedDividerPadding, expectedMiddleDividerTop,
                            parentWidth, expectedMiddleDividerTop + expectedDividerSize),
                    Color.YELLOW, 1, true);
        }

        // Do we expect bottom divider?
        if (expectingBottomDivider) {
            final int expectedBottomDividerTop = expectedBottomChildBottom;
            TestUtils.assertRegionPixelsOfColor(
                    "Region of bottom divider is " +
                            TestUtils.formatColorToHex(expectedDividerColor),
                    parent,
                    new Rect(expectedDividerPadding, expectedBottomDividerTop,
                            parentWidth - expectedDividerPadding,
                            expectedBottomDividerTop + expectedDividerSize),
                    expectedDividerColor, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of left padding of bottom divider is yellow",
                    parent,
                    new Rect(0, expectedBottomDividerTop, expectedDividerPadding,
                            expectedBottomDividerTop + expectedDividerSize),
                    Color.YELLOW, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of right padding of bottom divider is yellow",
                    parent,
                    new Rect(parentWidth - expectedDividerPadding, expectedBottomDividerTop,
                            parentWidth, expectedBottomDividerTop + expectedDividerSize),
                    Color.YELLOW, 1, true);
        }
    }

    /**
     * layout of vertical LinearLayout.
     * -----------------------------------
     * | ------------------------------- |
     * | |            child1           | |
     * | ------------------------------- |
     * | - - - - - - divider - - - - - - |
     * | ------------------------------- |
     * | |            child2           | |
     * | ------------------------------- |
     * -----------------------------------
     *
     * Parent is filled with yellow color. Child 1 is filled with green and child 2 is filled
     * with blue. Divider is red at the beginning. Throughout this method we reconfigure the
     * visibility, drawable and paddings of the divider and verify the overall visuals of the
     * container.
     */
    @Test
    public void testDividersInVerticalLayout() throws Throwable {
        final LinearLayout parent =
                (LinearLayout) mActivity.findViewById(R.id.linear_vertical_with_divider);

        final Resources res = mActivity.getResources();
        final int dividerSize = res.getDimensionPixelSize(R.dimen.linear_layout_divider_size);
        final int dividerPadding = res.getDimensionPixelSize(R.dimen.linear_layout_divider_padding);

        assertEquals(LinearLayout.SHOW_DIVIDER_MIDDLE, parent.getShowDividers());
        assertEquals(dividerPadding, parent.getDividerPadding());
        final Drawable dividerDrawable = parent.getDividerDrawable();
        TestUtils.assertAllPixelsOfColor("Divider is red", dividerDrawable,
                dividerDrawable.getIntrinsicWidth(), dividerDrawable.getIntrinsicHeight(),
                false, Color.RED, 1, true);

        // Test the initial visuals of the entire parent
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Change the divider to magenta
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerDrawable(
                        mActivity.getDrawable(R.drawable.linear_layout_divider_magenta)));
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.MAGENTA, dividerPadding);

        // Change the divider to null (no divider effectively)
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerDrawable(null));
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                0, Color.TRANSPARENT, 0);

        // Change the divider back to red
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerDrawable(
                        mActivity.getDrawable(R.drawable.linear_layout_divider_red)));
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Change the padding to half the original size
        final int halfPadding = dividerPadding / 2;
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerPadding(halfPadding));
        assertEquals(halfPadding, parent.getDividerPadding());
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, halfPadding);

        // Change the padding to twice the original size
        final int doublePadding = dividerPadding * 2;
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerPadding(doublePadding));
        assertEquals(doublePadding, parent.getDividerPadding());
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, doublePadding);

        // And back to the original padding
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerPadding(dividerPadding));
        assertEquals(dividerPadding, parent.getDividerPadding());
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Set show dividers to NONE (no divider effectively)
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE));
        assertEquals(LinearLayout.SHOW_DIVIDER_NONE, parent.getShowDividers());
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_NONE,
                0, Color.TRANSPARENT, 0);

        // Show only top divider
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(LinearLayout.SHOW_DIVIDER_BEGINNING));
        assertEquals(LinearLayout.SHOW_DIVIDER_BEGINNING, parent.getShowDividers());
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_BEGINNING,
                dividerSize, Color.RED, dividerPadding);

        // Show only bottom divider
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(LinearLayout.SHOW_DIVIDER_END));
        assertEquals(LinearLayout.SHOW_DIVIDER_END, parent.getShowDividers());
        verifyVisualsOfVerticalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);

        // Show top and bottom dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_END));
        assertEquals(LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_END,
                parent.getShowDividers());
        verifyVisualsOfVerticalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);

        // Show top and middle dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE));
        assertEquals(LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE,
                parent.getShowDividers());
        verifyVisualsOfVerticalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Show middle and bottom dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END));
        assertEquals(LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END,
                parent.getShowDividers());
        verifyVisualsOfVerticalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);

        // Show top, middle and bottom dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE
                                | LinearLayout.SHOW_DIVIDER_END));
        assertEquals(
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE
                        | LinearLayout.SHOW_DIVIDER_END,
                parent.getShowDividers());
        verifyVisualsOfVerticalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE
                        | LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);
    }

    private void verifyVisualsOfHorizontalLayoutWithDivider(LinearLayout parent,
            int expectedDividerPositionMask,
            int expectedDividerSize, @ColorInt int expectedDividerColor,
            int expectedDividerPadding) {
        final int parentWidth = parent.getWidth();
        final int parentHeight = parent.getHeight();

        final boolean expectingLeftDivider =
                (expectedDividerPositionMask & LinearLayout.SHOW_DIVIDER_BEGINNING) != 0;
        final boolean expectingMiddleDivider =
                (expectedDividerPositionMask & LinearLayout.SHOW_DIVIDER_MIDDLE) != 0;
        final boolean expectingRightDivider =
                (expectedDividerPositionMask & LinearLayout.SHOW_DIVIDER_END) != 0;
        final int expectedDividerCount = (expectingLeftDivider ? 1 : 0)
                + (expectingMiddleDivider ? 1 : 0) + (expectingRightDivider ? 1 : 0);

        final int expectedChildWidth =
                (parentWidth - expectedDividerCount * expectedDividerSize) / 2;

        final int expectedLeftChildLeft = expectingLeftDivider ? expectedDividerSize : 0;
        TestUtils.assertRegionPixelsOfColor("Region of first child is blue", parent,
                new Rect(expectedLeftChildLeft, 0,
                        expectedLeftChildLeft + expectedChildWidth, parentHeight),
                Color.BLUE, 1, true);

        final int expectedRightChildRight =
                expectingRightDivider ? parentWidth - expectedDividerSize : parentWidth;
        TestUtils.assertRegionPixelsOfColor("Region of second child is green", parent,
                new Rect(expectedRightChildRight - expectedChildWidth, 0, expectedRightChildRight,
                        parentHeight),
                Color.GREEN, 1, true);

        if (expectedDividerSize == 0) {
            return;
        }

        // Do we expect left divider?
        if (expectingLeftDivider) {
            TestUtils.assertRegionPixelsOfColor(
                    "Region of left divider is " + TestUtils.formatColorToHex(expectedDividerColor),
                    parent,
                    new Rect(0, expectedDividerPadding, expectedDividerSize,
                            parentHeight - expectedDividerPadding),
                    expectedDividerColor, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of top padding of left divider is yellow",
                    parent,
                    new Rect(0, 0, expectedDividerSize, expectedDividerPadding),
                    Color.YELLOW, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of bottom padding of left divider is yellow",
                    parent,
                    new Rect(0, parentHeight - expectedDividerPadding, expectedDividerSize,
                            parentHeight),
                    Color.YELLOW, 1, true);
        }

        // Do we expect middle divider?
        if (expectingMiddleDivider) {
            final int expectedMiddleDividerLeft = expectedLeftChildLeft + expectedChildWidth;
            TestUtils.assertRegionPixelsOfColor(
                    "Region of middle divider is " +
                            TestUtils.formatColorToHex(expectedDividerColor),
                    parent,
                    new Rect(expectedMiddleDividerLeft, expectedDividerPadding,
                            expectedMiddleDividerLeft + expectedDividerSize,
                            parentHeight - expectedDividerPadding),
                    expectedDividerColor, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of top padding of middle divider is yellow",
                    parent,
                    new Rect(expectedMiddleDividerLeft, 0,
                            expectedMiddleDividerLeft + expectedDividerSize,
                            expectedDividerPadding),
                    Color.YELLOW, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of bottom padding of middle divider is yellow",
                    parent,
                    new Rect(expectedMiddleDividerLeft, parentHeight - expectedDividerPadding,
                            expectedMiddleDividerLeft + expectedDividerSize, parentHeight),
                    Color.YELLOW, 1, true);
        }

        // Do we expect right divider?
        if (expectingRightDivider) {
            final int expectedRightDividerLeft = expectedRightChildRight;
            TestUtils.assertRegionPixelsOfColor(
                    "Region of right divider is " +
                            TestUtils.formatColorToHex(expectedDividerColor),
                    parent,
                    new Rect(expectedRightDividerLeft, expectedDividerPadding,
                            expectedRightDividerLeft + expectedDividerSize,
                            parentHeight - expectedDividerPadding),
                    expectedDividerColor, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of top padding of right divider is yellow",
                    parent,
                    new Rect(expectedRightDividerLeft, 0,
                            expectedRightDividerLeft + expectedDividerSize,
                            expectedDividerPadding),
                    Color.YELLOW, 1, true);
            TestUtils.assertRegionPixelsOfColor(
                    "Region of bottom padding of right divider is yellow",
                    parent,
                    new Rect(expectedRightDividerLeft, parentHeight - expectedDividerPadding,
                            expectedRightDividerLeft + expectedDividerSize, parentHeight),
                    Color.YELLOW, 1, true);
        }
    }

    /**
     * layout of horizontal LinearLayout.
     * -----------------------------------
     * | ------------  |  -------------  |
     * | |          |     |           |  |
     * | |          |  d  |           |  |
     * | |          |  i  |           |  |
     * | |          |  v  |           |  |
     * | |  child1  |  i  |  child2   |  |
     * | |          |  d  |           |  |
     * | |          |  e  |           |  |
     * | |          |  r  |           |  |
     * | |          |     |           |  |
     * | ------------  |  -------------  |
     * -----------------------------------
     *
     * Parent is filled with yellow color. Child 1 is filled with green and child 2 is filled
     * with blue. Divider is red at the beginning. Throughout this method we reconfigure the
     * visibility, drawable and paddings of the divider and verify the overall visuals of the
     * container.
     */
    @Test
    public void testDividersInHorizontalLayout() throws Throwable {
        final LinearLayout parent =
                (LinearLayout) mActivity.findViewById(R.id.linear_horizontal_with_divider);

        mActivityRule.runOnUiThread(() -> parent.setLayoutDirection(View.LAYOUT_DIRECTION_LTR));
        mInstrumentation.waitForIdleSync();

        final Resources res = mActivity.getResources();
        final int dividerSize = res.getDimensionPixelSize(R.dimen.linear_layout_divider_size);
        final int dividerPadding = res.getDimensionPixelSize(R.dimen.linear_layout_divider_padding);

        assertEquals(LinearLayout.SHOW_DIVIDER_MIDDLE, parent.getShowDividers());
        assertEquals(dividerPadding, parent.getDividerPadding());
        final Drawable dividerDrawable = parent.getDividerDrawable();
        TestUtils.assertAllPixelsOfColor("Divider is red", dividerDrawable,
                dividerDrawable.getIntrinsicWidth(), dividerDrawable.getIntrinsicHeight(),
                false, Color.RED, 1, true);

        // Test the initial visuals of the entire parent
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Change the divider to magenta
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerDrawable(
                        mActivity.getDrawable(R.drawable.linear_layout_divider_magenta)));
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.MAGENTA, dividerPadding);

        // Change the divider to null (no divider effectively)
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerDrawable(null));
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                0, Color.TRANSPARENT, 0);

        // Change the divider back to red
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerDrawable(
                        mActivity.getDrawable(R.drawable.linear_layout_divider_red)));
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Change the padding to half the original size
        final int halfPadding = dividerPadding / 2;
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerPadding(halfPadding));
        assertEquals(halfPadding, parent.getDividerPadding());
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, halfPadding);

        // Change the padding to twice the original size
        final int doublePadding = dividerPadding * 2;
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerPadding(doublePadding));
        assertEquals(doublePadding, parent.getDividerPadding());
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, doublePadding);

        // And back to the original padding
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setDividerPadding(dividerPadding));
        assertEquals(dividerPadding, parent.getDividerPadding());
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Set show dividers to NONE (no divider effectively)
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE));
        assertEquals(LinearLayout.SHOW_DIVIDER_NONE, parent.getShowDividers());
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_NONE,
                0, Color.TRANSPARENT, 0);

        // Show only left divider
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(LinearLayout.SHOW_DIVIDER_BEGINNING));
        assertEquals(LinearLayout.SHOW_DIVIDER_BEGINNING, parent.getShowDividers());
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_BEGINNING,
                dividerSize, Color.RED, dividerPadding);

        // Show only right divider
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(LinearLayout.SHOW_DIVIDER_END));
        assertEquals(LinearLayout.SHOW_DIVIDER_END, parent.getShowDividers());
        verifyVisualsOfHorizontalLayoutWithDivider(parent, LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);

        // Show left and right dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_END));
        assertEquals(LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_END,
                parent.getShowDividers());
        verifyVisualsOfHorizontalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);

        // Show left and middle dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE));
        assertEquals(LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE,
                parent.getShowDividers());
        verifyVisualsOfHorizontalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE,
                dividerSize, Color.RED, dividerPadding);

        // Show middle and right dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END));
        assertEquals(LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END,
                parent.getShowDividers());
        verifyVisualsOfHorizontalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);

        // Show left, middle and right dividers
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, parent,
                () -> parent.setShowDividers(
                        LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE
                                | LinearLayout.SHOW_DIVIDER_END));
        assertEquals(
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE
                        | LinearLayout.SHOW_DIVIDER_END,
                parent.getShowDividers());
        verifyVisualsOfHorizontalLayoutWithDivider(parent,
                LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE
                        | LinearLayout.SHOW_DIVIDER_END,
                dividerSize, Color.RED, dividerPadding);
    }

    private class MockListView extends ListView {
        private final static int DEFAULT_CHILD_BASE_LINE = 1;

        public MockListView(Context context) {
            super(context);
        }

        public int getBaseline() {
            return DEFAULT_CHILD_BASE_LINE;
        }
    }

    /**
     * Add MockLinearLayout to help for testing protected methods in LinearLayout.
     * Because we can not access protected methods in LinearLayout directly, we have to
     * extends from it and override protected methods so that we can access them in
     * our test codes.
     */
    public static class MockLinearLayout extends LinearLayout {
        public MockLinearLayout(Context c) {
            super(c);
        }

        public MockLinearLayout(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
            return super.checkLayoutParams(p);
        }

        @Override
        protected LinearLayout.LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }

        @Override
        protected LinearLayout.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
            return super.generateLayoutParams(p);
        }
    }
}
