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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.cts.util.DrawingUtils;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewGroupOverlayTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private ViewGroup mViewGroupWithOverlay;
    private ViewGroupOverlay mViewGroupOverlay;

    @Rule
    public ActivityTestRule<ViewGroupOverlayCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewGroupOverlayCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mViewGroupWithOverlay = (ViewGroup) mActivity.findViewById(R.id.viewgroup_with_overlay);
        mViewGroupOverlay = mViewGroupWithOverlay.getOverlay();
    }

    @Presubmit
    @Test
    public void testBasics() {
        DrawingUtils.assertAllPixelsOfColor("Default fill", mViewGroupWithOverlay,
                Color.WHITE, null);
        assertNotNull("Overlay is not null", mViewGroupOverlay);
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddNullView() {
        mViewGroupOverlay.add((View) null);
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testRemoveNullView() {
         mViewGroupOverlay.remove((View) null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithOneView() {
        // Add one colored view to the overlay
        final View redView = new View(mActivity);
        redView.setBackgroundColor(Color.RED);
        redView.layout(10, 20, 30, 40);

        mViewGroupOverlay.add(redView);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one red view",
                mViewGroupWithOverlay, Color.WHITE, colorRectangles);

        // Now remove that view from the overlay and test that we're back to pure white fill
        mViewGroupOverlay.remove(redView);
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewGroupWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithNonOverlappingViews() {
        // Add three views to the overlay
        final View redView = new View(mActivity);
        redView.setBackgroundColor(Color.RED);
        redView.layout(10, 20, 30, 40);
        final View greenView = new View(mActivity);
        greenView.setBackgroundColor(Color.GREEN);
        greenView.layout(60, 30, 90, 50);
        final View blueView = new View(mActivity);
        blueView.setBackgroundColor(Color.BLUE);
        blueView.layout(40, 60, 80, 90);

        mViewGroupOverlay.add(redView);
        mViewGroupOverlay.add(greenView);
        mViewGroupOverlay.add(blueView);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(60, 30, 90, 50), Color.GREEN));
        colorRectangles.add(new Pair<>(new Rect(40, 60, 80, 90), Color.BLUE));
        DrawingUtils.assertAllPixelsOfColor("Overlay with three views", mViewGroupWithOverlay,
                Color.WHITE, colorRectangles);

        // Remove one of the views from the overlay
        mViewGroupOverlay.remove(greenView);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(40, 60, 80, 90), Color.BLUE));
        DrawingUtils.assertAllPixelsOfColor("Overlay with two views", mViewGroupWithOverlay,
                Color.WHITE, colorRectangles);

        // Clear all views from the overlay and test that we're back to pure white fill
        mViewGroupOverlay.clear();
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewGroupWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithNonOverlappingViewAndDrawable() {
        // Add one view and one drawable to the overlay
        final View redView = new View(mActivity);
        redView.setBackgroundColor(Color.RED);
        redView.layout(10, 20, 30, 40);
        final Drawable greenDrawable = new ColorDrawable(Color.GREEN);
        greenDrawable.setBounds(60, 30, 90, 50);

        mViewGroupOverlay.add(redView);
        mViewGroupOverlay.add(greenDrawable);

        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(60, 30, 90, 50), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one view and one drawable",
                mViewGroupWithOverlay, Color.WHITE, colorRectangles);

        // Remove the view from the overlay
        mViewGroupOverlay.remove(redView);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(60, 30, 90, 50), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one drawable", mViewGroupWithOverlay,
                Color.WHITE, colorRectangles);

        // Clear everything from the overlay and test that we're back to pure white fill
        mViewGroupOverlay.clear();
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewGroupWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithOverlappingViews() {
        // Add two overlapping colored views to the overlay
        final View redView = new View(mActivity);
        redView.setBackgroundColor(Color.RED);
        redView.layout(10, 20, 60, 40);
        final View greenView = new View(mActivity);
        greenView.setBackgroundColor(Color.GREEN);
        greenView.layout(30, 20, 80, 40);

        mViewGroupOverlay.add(redView);
        mViewGroupOverlay.add(greenView);

        // Our overlay views overlap in horizontal 30-60 range. Here we test that the
        // second view is the one that is drawn last in that range.
        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 30, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(30, 20, 80, 40), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with two drawables", mViewGroupWithOverlay,
                Color.WHITE, colorRectangles);

        // Remove the second view from the overlay
        mViewGroupOverlay.remove(greenView);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 60, 40), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one drawable", mViewGroupWithOverlay,
                Color.WHITE, colorRectangles);

        // Clear all views from the overlay and test that we're back to pure white fill
        mViewGroupOverlay.clear();
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewGroupWithOverlay,
                Color.WHITE, null);
    }

    @UiThreadTest
    @Test
    public void testOverlayWithOverlappingViewAndDrawable() {
        // Add two overlapping colored views to the overlay
        final Drawable redDrawable = new ColorDrawable(Color.RED);
        redDrawable.setBounds(10, 20, 60, 40);
        final View greenView = new View(mActivity);
        greenView.setBackgroundColor(Color.GREEN);
        greenView.layout(30, 20, 80, 40);

        mViewGroupOverlay.add(redDrawable);
        mViewGroupOverlay.add(greenView);

        // Our overlay views overlap in horizontal 30-60 range. Even though the green view was
        // added after the red drawable, *all* overlay drawables are drawn after the overlay views.
        // So in the overlap range we expect color red
        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(10, 20, 60, 40), Color.RED));
        colorRectangles.add(new Pair<>(new Rect(60, 20, 80, 40), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one view and one drawable",
                mViewGroupWithOverlay, Color.WHITE, colorRectangles);

        // Remove the drawable from the overlay
        mViewGroupOverlay.remove(redDrawable);
        colorRectangles.clear();
        colorRectangles.add(new Pair<>(new Rect(30, 20, 80, 40), Color.GREEN));
        DrawingUtils.assertAllPixelsOfColor("Overlay with one view", mViewGroupWithOverlay,
                Color.WHITE, colorRectangles);

        // Clear all views from the overlay and test that we're back to pure white fill
        mViewGroupOverlay.clear();
        DrawingUtils.assertAllPixelsOfColor("Back to default fill", mViewGroupWithOverlay,
                Color.WHITE, null);
    }

    @Test
    public void testOverlayViewNoClicks() throws Throwable {
        // Add one colored view with mock click listener to the overlay
        final View redView = new View(mActivity);
        redView.setBackgroundColor(Color.RED);
        final View.OnClickListener mockClickListener = mock(View.OnClickListener.class);
        redView.setOnClickListener(mockClickListener);
        redView.layout(10, 20, 30, 40);

        mActivityRule.runOnUiThread(() -> mViewGroupOverlay.add(redView));

        // Emulate a tap in the center of the view we've added to the overlay
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mViewGroupWithOverlay);

        // Verify that our mock listener hasn't been called
        verify(mockClickListener, never()).onClick(any(View.class));
    }

    @UiThreadTest
    @Test
    public void testOverlayReparenting() {
        // Find the view that we're about to add to our overlay
        final View level2View = mViewGroupWithOverlay.findViewById(R.id.level2);
        final View level3View = level2View.findViewById(R.id.level3);

        assertTrue(level2View == level3View.getParent());

        // Set the fill of this view to red
        level3View.setBackgroundColor(Color.RED);
        mViewGroupOverlay.add(level3View);

        // At this point we expect the view that was added to the overlay to have been removed
        // from its original parent
        assertFalse(level2View == level3View.getParent());

        // Check the expected visual appearance of our view group. We expect that the view that
        // was added to the overlay to maintain its relative location inside the activity.
        final List<Pair<Rect, Integer>> colorRectangles = new ArrayList<>();
        colorRectangles.add(new Pair<>(new Rect(65, 60, 85, 90), Color.RED));
        DrawingUtils.assertAllPixelsOfColor("Empty overlay before adding grandchild",
                mViewGroupWithOverlay, Color.WHITE, colorRectangles);

    }
}
