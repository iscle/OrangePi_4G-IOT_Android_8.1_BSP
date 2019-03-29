/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ViewOutlineProviderTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @UiThreadTest
    @Test
    public void testBackground() {
        View view = new View(mContext);
        view.setLeftTopRightBottom(100, 200, 300, 400);

        Outline outline = new Outline();
        outline.setAlpha(1.0f);
        Rect queryRect = new Rect();

        // No background - outline is 0 alpha, width x height rect
        ViewOutlineProvider.BACKGROUND.getOutline(view, outline);
        outline.getRect(queryRect);
        assertEquals(new Rect(0, 0, 200, 200), queryRect);
        assertEquals(0f, outline.getAlpha(), 0f);

        // With background - outline is passed directly from background
        view.setBackground(new ColorDrawable(Color.BLACK) {
            @Override
            public void getOutline(@NonNull Outline outline) {
                outline.setRect(1, 2, 3, 4);
                outline.setAlpha(0.123f);
            }
        });
        ViewOutlineProvider.BACKGROUND.getOutline(view, outline);
        outline.getRect(queryRect);
        assertEquals(new Rect(1, 2, 3, 4), queryRect);
        assertEquals(0.123f, outline.getAlpha(), 0f);
    }

    @UiThreadTest
    @Test
    public void testBounds() {
        View view = new View(mContext);

        Outline outline = new Outline();
        Rect queryRect = new Rect();
        outline.setAlpha(0.123f);

        view.setLeftTopRightBottom(1, 2, 3, 4);
        ViewOutlineProvider.BOUNDS.getOutline(view, outline);
        outline.getRect(queryRect);
        assertEquals(new Rect(0, 0, 2, 2), queryRect); // local width/height
        assertEquals(0.123f, outline.getAlpha(), 0f); // alpha not changed

        view.setLeftTopRightBottom(100, 200, 300, 400);
        ViewOutlineProvider.BOUNDS.getOutline(view, outline);
        outline.getRect(queryRect);
        assertEquals(new Rect(0, 0, 200, 200), queryRect); // local width/height
        assertEquals(0.123f, outline.getAlpha(), 0f); // alpha not changed
    }

    @UiThreadTest
    @Test
    public void testPaddedBounds() {
        View view = new View(mContext);

        Outline outline = new Outline();
        Rect queryRect = new Rect();
        outline.setAlpha(0.123f);

        view.setLeftTopRightBottom(10, 20, 30, 40);
        view.setPadding(0, 0, 0, 0);
        ViewOutlineProvider.PADDED_BOUNDS.getOutline(view, outline);
        outline.getRect(queryRect);
        assertEquals(new Rect(0, 0, 20, 20), queryRect); // local width/height
        assertEquals(0.123f, outline.getAlpha(), 0f); // alpha not changed

        view.setPadding(5, 5, 5, 5);
        ViewOutlineProvider.PADDED_BOUNDS.getOutline(view, outline);
        outline.getRect(queryRect);
        assertEquals(new Rect(5, 5, 15, 15), queryRect); // local width/height, inset by 5
        assertEquals(0.123f, outline.getAlpha(), 0f); // alpha not changed
    }
}
