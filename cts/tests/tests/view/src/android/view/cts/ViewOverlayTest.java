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

import static org.junit.Assert.assertNotNull;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;
import android.view.View;
import android.view.ViewOverlay;
import android.view.cts.util.DrawingUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewOverlayTest {
    private View mViewWithOverlay;
    private ViewOverlay mViewOverlay;

    @Rule
    public ActivityTestRule<ViewOverlayCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewOverlayCtsActivity.class);

    @Before
    public void setup() {
        mViewWithOverlay = mActivityRule.getActivity().findViewById(R.id.view_with_overlay);
        mViewOverlay = mViewWithOverlay.getOverlay();
    }

    @Test
    public void testBasics() {
        DrawingUtils.assertAllPixelsOfColor("Default fill", mViewWithOverlay,
                Color.WHITE, null);
        assertNotNull("Overlay is not null", mViewOverlay);
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddNullDrawable() {
         mViewOverlay.add(null);
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testRemoveNullDrawable() {
        mViewOverlay.remove(null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithOneDrawable() {
        // Add one colored drawable to the overlay
        final Drawable redDrawable = new ColorDrawable(Color.RED);
        redDrawable.setBounds(20, 30, 40, 50);
        mViewOverlay.add(redDrawable);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(20, 30, 40, 50), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one red drawable", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Now remove that drawable from the overlay and test that we're back to pure white fill
        mViewOverlay.remove(redDrawable);
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testAddTheSameDrawableTwice() {
        final Drawable redDrawable = new ColorDrawable(Color.RED);
        redDrawable.setBounds(20, 30, 40, 50);
        // Add the same drawable twice
        mViewOverlay.add(redDrawable);
        mViewOverlay.add(redDrawable);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(20, 30, 40, 50), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one red drawable", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Now remove that drawable from the overlay and test that we're back to pure white fill
        mViewOverlay.remove(redDrawable);
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testRemoveTheSameDrawableTwice() {
        // Add one colored drawable to the overlay
        final Drawable redDrawable = new ColorDrawable(Color.RED);
        redDrawable.setBounds(20, 30, 40, 50);
        mViewOverlay.add(redDrawable);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(20, 30, 40, 50), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one red drawable", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Now remove that drawable from the overlay and test that we're back to pure white fill.
        // Remove the drawable twice. The second should be a no-op
        mViewOverlay.remove(redDrawable);
        mViewOverlay.remove(redDrawable);
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithNonOverlappingDrawables() {
        // Add three color drawables to the overlay
        final Drawable redDrawable = new ColorDrawable(Color.RED);
        redDrawable.setBounds(10, 20, 30, 40);
        final Drawable greenDrawable = new ColorDrawable(Color.GREEN);
        greenDrawable.setBounds(60, 30, 90, 50);
        final Drawable blueDrawable = new ColorDrawable(Color.BLUE);
        blueDrawable.setBounds(40, 60, 80, 90);

        mViewOverlay.add(redDrawable);
        mViewOverlay.add(greenDrawable);
        mViewOverlay.add(blueDrawable);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(60, 30, 90, 50), Color.GREEN));
        colorRectangles.add(new Pair<>(new Rect(40, 60, 80, 90), Color.BLUE));
        DrawingUtils.assertAllPixelsOfColor("Overlay with three drawables", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Remove one of the drawables from the overlay
        mViewOverlay.remove(greenDrawable);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(40, 60, 80, 90), Color.BLUE));
        DrawingUtils.assertAllPixelsOfColor("Overlay with two drawables", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Clear all drawables from the overlay and test that we're back to pure white fill
        mViewOverlay.clear();
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithOverlappingDrawables() {
        // Add two overlapping color drawables to the overlay
        final Drawable redDrawable = new ColorDrawable(Color.RED);
        redDrawable.setBounds(10, 20, 60, 40);
        final Drawable greenDrawable = new ColorDrawable(Color.GREEN);
        greenDrawable.setBounds(30, 20, 80, 40);

        mViewOverlay.add(redDrawable);
        mViewOverlay.add(greenDrawable);

        // Our overlay drawables overlap in horizontal 30-60 range. Here we test that the
        // second drawable is the one that is drawn last in that range.
        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(30, 20, 80, 40), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with two drawables", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Remove the second from the overlay
        mViewOverlay.remove(greenDrawable);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 60, 40), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one drawable", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Clear all drawables from the overlay and test that we're back to pure white fill
        mViewOverlay.clear();
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testOverlayDynamicChangesToDrawable() {
        // Add one colored drawable to the overlay
        final ColorDrawable drawable = new ColorDrawable(Color.RED);
        drawable.setBounds(20, 30, 40, 50);
        mViewOverlay.add(drawable);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(20, 30, 40, 50), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one red drawable", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Update the bounds of our red drawable. Note that ideally we want to verify that
        // ViewOverlay's internal implementation tracks the changes to the drawables and kicks
        // off a redraw pass at some point. Here we are testing a subset of that - that the
        // next time a redraw of View / ViewOverlay happens, it catches the new state of our
        // original drawable.
        drawable.setBounds(50, 10, 80, 90);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(50, 10, 80, 90), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Red drawable moved", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Update the color of our drawable. Same (partial) testing as before.
        drawable.setColor(Color.GREEN);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(50, 10, 80, 90), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Drawable is green now", mViewWithOverlay,
                Color.WHITE, colorRectangles);
    }

    @UiThreadTest
    @Test
    public void testOverlayDynamicChangesToOverlappingDrawables() {
        // Add two overlapping color drawables to the overlay
        final ColorDrawable redDrawable = new ColorDrawable(Color.RED);
        redDrawable.setBounds(10, 20, 60, 40);
        final ColorDrawable greenDrawable = new ColorDrawable(Color.GREEN);
        greenDrawable.setBounds(30, 20, 80, 40);

        mViewOverlay.add(redDrawable);
        mViewOverlay.add(greenDrawable);

        // Our overlay drawables overlap in horizontal 30-60 range. This is the same test as
        // in testOverlayWithOverlappingDrawables
        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(30, 20, 80, 40), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with two drawables", mViewWithOverlay,
                Color.WHITE, colorRectangles);

        // Now change the color of the first drawable and verify that it didn't "bump" it up
        // in the drawing order.
        redDrawable.setColor(Color.BLUE);
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.BLUE));
        colorRectangles.add(new Pair<>(new Rect(30, 20, 80, 40), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with two drawables", mViewWithOverlay,
                Color.WHITE, colorRectangles);
    }
}