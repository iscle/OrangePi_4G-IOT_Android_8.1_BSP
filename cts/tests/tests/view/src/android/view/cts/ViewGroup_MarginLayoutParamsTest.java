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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.cts.util.XmlUtils;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ViewGroup_MarginLayoutParamsTest {
    private Context mContext;
    private ViewGroup.MarginLayoutParams mMarginLayoutParams;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testConstructor() {
        // create a new MarginLayoutParams instance
        XmlResourceParser p = mContext.getResources().getLayout(
                R.layout.viewgroup_margin_layout);
        try {
            XmlUtils.beginDocument(p, "LinearLayout");
        } catch (Exception e) {
            fail("Fail in preparing AttibuteSet.");
        }
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(mContext, p);
        assertNotNull(mMarginLayoutParams);

        mMarginLayoutParams = null;
        // create a new MarginLayoutParams instance
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(320, 480);
        assertNotNull(mMarginLayoutParams);

        mMarginLayoutParams = null;
        // create a new MarginLayoutParams instance
        MarginLayoutParams temp = new ViewGroup.MarginLayoutParams(320, 480);
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(temp);
        assertNotNull(mMarginLayoutParams);

        mMarginLayoutParams = null;
        // create a new MarginLayoutParams instance
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(320, 480);
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(lp);
        assertNotNull(mMarginLayoutParams);
    }

    @Test
    public void testSetMargins() {
        // create a new MarginLayoutParams instance
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(320, 480);
        mMarginLayoutParams.setMargins(20, 30, 120, 140);
        assertEquals(20, mMarginLayoutParams.leftMargin);
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.rightMargin);
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(20, mMarginLayoutParams.getMarginStart());
        assertEquals(120, mMarginLayoutParams.getMarginEnd());

        assertEquals(false, mMarginLayoutParams.isMarginRelative());
    }

    @Test
    public void testSetMarginsRelative() {
        // create a new MarginLayoutParams instance
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(320, 480);
        mMarginLayoutParams.setMargins(0, 30, 0, 140);
        mMarginLayoutParams.setMarginStart(20);
        mMarginLayoutParams.setMarginEnd(120);
        assertEquals(20, mMarginLayoutParams.getMarginStart());
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.getMarginEnd());
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(0, mMarginLayoutParams.leftMargin);
        assertEquals(0, mMarginLayoutParams.rightMargin);

        assertEquals(true, mMarginLayoutParams.isMarginRelative());
    }

    @Test
    public void testResolveMarginsRelative() {
        ViewGroup vg = new LinearLayout(mContext);

        // LTR / normal margin case
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(320, 480);
        mMarginLayoutParams.setMargins(20, 30, 120, 140);
        vg.setLayoutParams(mMarginLayoutParams);

        assertEquals(20, mMarginLayoutParams.leftMargin);
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.rightMargin);
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(20, mMarginLayoutParams.getMarginStart());
        assertEquals(120, mMarginLayoutParams.getMarginEnd());

        assertEquals(false, mMarginLayoutParams.isMarginRelative());

        // LTR / relative margin case
        mMarginLayoutParams.setMargins(0, 30, 0, 140);
        mMarginLayoutParams.setMarginStart(20);
        mMarginLayoutParams.setMarginEnd(120);
        vg.setLayoutParams(mMarginLayoutParams);

        assertEquals(20, mMarginLayoutParams.getMarginStart());
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.getMarginEnd());
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(20, mMarginLayoutParams.leftMargin);
        assertEquals(120, mMarginLayoutParams.rightMargin);

        assertEquals(true, mMarginLayoutParams.isMarginRelative());

        // RTL / normal margin case
        vg.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(320, 480);
        mMarginLayoutParams.setMargins(20, 30, 120, 140);
        vg.setLayoutParams(mMarginLayoutParams);

        assertEquals(20, mMarginLayoutParams.leftMargin);
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.rightMargin);
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(120, mMarginLayoutParams.getMarginStart());
        assertEquals(20, mMarginLayoutParams.getMarginEnd());

        assertEquals(false, mMarginLayoutParams.isMarginRelative());

        // RTL / relative margin case
        mMarginLayoutParams.setMargins(0, 30, 0, 140);
        mMarginLayoutParams.setMarginStart(20);
        mMarginLayoutParams.setMarginEnd(120);
        vg.setLayoutParams(mMarginLayoutParams);

        assertEquals(20, mMarginLayoutParams.getMarginStart());
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.getMarginEnd());
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(120, mMarginLayoutParams.leftMargin);
        assertEquals(20, mMarginLayoutParams.rightMargin);

        assertEquals(true, mMarginLayoutParams.isMarginRelative());
    }

    @Test
    public void testResolveMarginsExplicit() {
        // LTR / relative margin case
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(320, 480);
        mMarginLayoutParams.setMargins(0, 30, 0, 140);
        mMarginLayoutParams.setMarginStart(20);
        mMarginLayoutParams.setMarginEnd(120);
        mMarginLayoutParams.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        assertEquals(20, mMarginLayoutParams.getMarginStart());
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.getMarginEnd());
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(20, mMarginLayoutParams.leftMargin);
        assertEquals(120, mMarginLayoutParams.rightMargin);

        assertEquals(true, mMarginLayoutParams.isMarginRelative());

        // RTL / relative margin case
        mMarginLayoutParams = new ViewGroup.MarginLayoutParams(320, 480);
        mMarginLayoutParams.setMargins(0, 30, 0, 140);
        mMarginLayoutParams.setMarginStart(20);
        mMarginLayoutParams.setMarginEnd(120);
        mMarginLayoutParams.resolveLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        assertEquals(20, mMarginLayoutParams.getMarginStart());
        assertEquals(30, mMarginLayoutParams.topMargin);
        assertEquals(120, mMarginLayoutParams.getMarginEnd());
        assertEquals(140, mMarginLayoutParams.bottomMargin);

        assertEquals(120, mMarginLayoutParams.leftMargin);
        assertEquals(20, mMarginLayoutParams.rightMargin);

        assertEquals(true, mMarginLayoutParams.isMarginRelative());
    }

    @Test
    public void testVerticalHorizontalMargins() {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        LinearLayout viewGroup = (LinearLayout)
                inflater.inflate(R.layout.viewgroup_margin_layout_verticalhorizontal, null);
        int measureSpec = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY);
        viewGroup.measure(measureSpec, measureSpec);
        viewGroup.layout(0, 0, 1000, 1000);

        View view1 = viewGroup.findViewById(R.id.view1);
        View view2 = viewGroup.findViewById(R.id.view2);
        View view3 = viewGroup.findViewById(R.id.view3);
        View view4 = viewGroup.findViewById(R.id.view4);
        View view5 = viewGroup.findViewById(R.id.view5);
        View view6 = viewGroup.findViewById(R.id.view6);
        View view7 = viewGroup.findViewById(R.id.view7);

        Rect defaultBounds = new Rect(view1.getLeft(), view1.getTop(), view1.getRight(),
                view1.getBottom());
        int marginStart = mContext.getResources().getDimensionPixelSize(R.dimen.marginStart);
        int marginEnd = mContext.getResources().getDimensionPixelSize(R.dimen.marginEnd);
        int marginAll = mContext.getResources().getDimensionPixelSize(R.dimen.marginAll);
        int marginHorizontal =
                mContext.getResources().getDimensionPixelSize(R.dimen.marginHorizontal);
        int marginVertical = mContext.getResources().getDimensionPixelSize(R.dimen.marginVertical);

        checkBounds(view2, defaultBounds, marginHorizontal, 0, marginHorizontal, 0);
        checkBounds(view3, defaultBounds, 0, marginVertical, 0, marginVertical);
        checkBounds(view4, defaultBounds, marginHorizontal, marginVertical,
                marginHorizontal, marginVertical);
        checkBounds(view5, defaultBounds, marginHorizontal, marginVertical,
                marginHorizontal, marginVertical);
        checkBounds(view6, defaultBounds, marginStart, marginVertical, marginEnd, marginVertical);
        checkBounds(view7, defaultBounds, marginAll, marginAll, marginAll, marginAll);
    }

    private void checkBounds(View view, Rect defaultBounds,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        assertEquals("Left", defaultBounds.left + insetLeft, view.getLeft());
        assertEquals("Top", defaultBounds.top + insetTop, view.getTop());
        assertEquals("Right", defaultBounds.right - insetRight, view.getRight());
        assertEquals("Bottom", defaultBounds.bottom - insetBottom, view.getBottom());
    }
}
