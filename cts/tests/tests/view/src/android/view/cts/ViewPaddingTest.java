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

import android.content.Context;
import android.graphics.Rect;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ViewPaddingTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testPadding() {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        LinearLayout viewGroup = (LinearLayout)
                inflater.inflate(R.layout.view_padding, null);
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
        View view8 = viewGroup.findViewById(R.id.view8);
        View view9 = viewGroup.findViewById(R.id.view9);
        View view10 = viewGroup.findViewById(R.id.view10);
        View view11 = viewGroup.findViewById(R.id.view11);
        View view12 = viewGroup.findViewById(R.id.view12);

        Rect defaultBounds = new Rect(view1.getLeft(), view1.getTop(), view1.getRight(),
                view1.getBottom());
        int insetLeft = mContext.getResources().getDimensionPixelSize(R.dimen.insetLeft);
        int insetRight = mContext.getResources().getDimensionPixelSize(R.dimen.insetRight);
        int insetTop = mContext.getResources().getDimensionPixelSize(R.dimen.insetTop);
        int insetBottom = mContext.getResources().getDimensionPixelSize(R.dimen.insetBottom);
        int insetStart = mContext.getResources().getDimensionPixelSize(R.dimen.insetStart);
        int insetEnd = mContext.getResources().getDimensionPixelSize(R.dimen.insetEnd);
        int insetAll = mContext.getResources().getDimensionPixelSize(R.dimen.insetAll);
        int insetHorizontal =
                mContext.getResources().getDimensionPixelSize(R.dimen.insetHorizontal);
        int insetVertical = mContext.getResources().getDimensionPixelSize(R.dimen.insetVertical);

        checkBounds(view2, defaultBounds, insetAll, insetAll, insetAll, insetAll);
        checkBounds(view3, defaultBounds, insetLeft, insetTop, 0, 0);
        checkBounds(view4, defaultBounds, 0, 0, insetRight, insetBottom);
        checkBounds(view5, defaultBounds, insetLeft, insetTop, insetRight, insetBottom);
        checkBounds(view6, defaultBounds, insetHorizontal, 0, insetHorizontal, 0);
        checkBounds(view7, defaultBounds, 0, insetVertical, 0, insetVertical);
        checkBounds(view8, defaultBounds, insetHorizontal, insetVertical, insetHorizontal,
                insetVertical);
        checkBounds(view9, defaultBounds, insetHorizontal, insetVertical, insetHorizontal,
                insetVertical);
        checkBounds(view10, defaultBounds, insetAll, insetAll, insetAll, insetAll);
        checkBounds(view11, defaultBounds, insetStart, insetAll, insetEnd, insetAll);
        checkBounds(view12, defaultBounds, insetStart, 0, insetEnd, 0);
    }

    private void checkBounds(View view, Rect defaultBounds,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        assertEquals("Left", defaultBounds.left + insetLeft, view.getLeft());
        assertEquals("Top", defaultBounds.top + insetTop, view.getTop());
        assertEquals("Right", defaultBounds.right - insetRight, view.getRight());
        assertEquals("Bottom", defaultBounds.bottom - insetBottom, view.getBottom());
    }
}
