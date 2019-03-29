/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.transition.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.animation.Animator;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ChangeBoundsTest extends BaseTransitionTest {
    private static final int SMALL_SQUARE_SIZE_DP = 10;
    private static final int LARGE_SQUARE_SIZE_DP = 30;
    private static final int SMALL_OFFSET_DP = 2;

    ChangeBounds mChangeBounds;
    ValidateBoundsListener mBoundsChangeListener;

    @Override
    @Before
    public void setup() {
        super.setup();
        resetChangeBoundsTransition();
        mBoundsChangeListener = null;
    }

    private void resetChangeBoundsTransition() {
        mListener = mock(Transition.TransitionListener.class);
        mChangeBounds = new MyChangeBounds();
        mChangeBounds.setDuration(400);
        mChangeBounds.addListener(mListener);
        mChangeBounds.setInterpolator(new LinearInterpolator());
        mTransition = mChangeBounds;
    }

    @Test
    public void testBasicChangeBounds() throws Throwable {
        enterScene(R.layout.scene1);

        validateInScene1();

        mBoundsChangeListener = new ValidateBoundsListener(true);

        startTransition(R.layout.scene6);
        // The update listener will validate that it is changing throughout the animation
        waitForEnd(800);

        validateInScene6();
    }

    @Test
    public void testResizeClip() throws Throwable {
        assertEquals(false, mChangeBounds.getResizeClip());
        mChangeBounds.setResizeClip(true);
        assertEquals(true, mChangeBounds.getResizeClip());
        enterScene(R.layout.scene1);

        validateInScene1();

        mBoundsChangeListener = new ValidateBoundsListener(true);

        startTransition(R.layout.scene6);

        // The update listener will validate that it is changing throughout the animation
        waitForEnd(800);

        validateInScene6();
    }

    @Test
    public void testResizeClipSmaller() throws Throwable {
        mChangeBounds.setResizeClip(true);
        enterScene(R.layout.scene6);

        validateInScene6();

        mBoundsChangeListener = new ValidateBoundsListener(false);
        startTransition(R.layout.scene1);

        // The update listener will validate that it is changing throughout the animation
        waitForEnd(800);

        validateInScene1();
    }

    @Test
    public void testInterruptSameDestination() throws Throwable {
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before interrupting the transition
        Thread.sleep(150);
        resetChangeBoundsTransition();
        startTransition(R.layout.scene6);

        assertFalse(isRestartingAnimation());
        waitForEnd(1000);
        validateInScene6();
    }

    @Test
    public void testInterruptSameDestinationResizeClip() throws Throwable {
        mChangeBounds.setResizeClip(true);
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before interrupting the transition
        Thread.sleep(150);

        resetChangeBoundsTransition();
        mChangeBounds.setResizeClip(true);
        startTransition(R.layout.scene6);

        assertFalse(isRestartingAnimation());
        assertFalse(isRestartingClip());
        waitForEnd(1000);
        validateInScene6();
    }

    @Test
    public void testInterruptWithReverse() throws Throwable {
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before reversing
        Thread.sleep(150);
        // reverse the transition back to scene1
        resetChangeBoundsTransition();
        startTransition(R.layout.scene1);

        assertFalse(isRestartingAnimation());
        waitForEnd(1000);
        validateInScene1();
    }

    @Test
    public void testInterruptWithReverseResizeClip() throws Throwable {
        mChangeBounds.setResizeClip(true);
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before reversing
        Thread.sleep(150);

        // reverse the transition back to scene1
        resetChangeBoundsTransition();
        mChangeBounds.setResizeClip(true);
        startTransition(R.layout.scene1);

        assertFalse(isRestartingAnimation());
        assertFalse(isRestartingClip());
        waitForEnd(1000);
        validateInScene1();
    }

    private boolean isRestartingAnimation() {
        View red = mActivity.findViewById(R.id.redSquare);
        View green = mActivity.findViewById(R.id.greenSquare);
        Resources resources = mActivity.getResources();
        float closestDistance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SMALL_OFFSET_DP, resources.getDisplayMetrics());
        return red.getTop() < closestDistance || green.getTop() < closestDistance;
    }

    private boolean isRestartingClip() {
        Resources resources = mActivity.getResources();
        float smallDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SMALL_SQUARE_SIZE_DP + SMALL_OFFSET_DP, resources.getDisplayMetrics());
        float largeDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LARGE_SQUARE_SIZE_DP - SMALL_OFFSET_DP, resources.getDisplayMetrics());

        View red = mActivity.findViewById(R.id.redSquare);
        Rect redClip = red.getClipBounds();
        View green = mActivity.findViewById(R.id.greenSquare);
        Rect greenClip = green.getClipBounds();
        return redClip == null || redClip.width() < smallDim || redClip.width() > largeDim ||
                greenClip == null || greenClip.width() < smallDim || greenClip.width() > largeDim;
    }

    private void validateInScene1() {
        validateViewPlacement(R.id.redSquare, R.id.greenSquare, SMALL_SQUARE_SIZE_DP);
    }

    private void validateInScene6() {
        validateViewPlacement(R.id.greenSquare, R.id.redSquare, LARGE_SQUARE_SIZE_DP);
    }

    private void validateViewPlacement(int topViewResource, int bottomViewResource, int dim) {
        Resources resources = mActivity.getResources();
        float expectedDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dim,
                resources.getDisplayMetrics());
        View aboveSquare = mActivity.findViewById(topViewResource);
        assertEquals(0, aboveSquare.getLeft());
        assertEquals(0, aboveSquare.getTop());
        assertTrue(aboveSquare.getRight() != 0);
        final int aboveSquareBottom = aboveSquare.getBottom();
        assertTrue(aboveSquareBottom != 0);

        View belowSquare = mActivity.findViewById(bottomViewResource);
        assertEquals(0, belowSquare.getLeft());
        assertWithinAPixel(aboveSquareBottom, belowSquare.getTop());
        assertWithinAPixel(aboveSquareBottom + aboveSquare.getHeight(),
                belowSquare.getBottom());
        assertWithinAPixel(aboveSquare.getRight(), belowSquare.getRight());

        assertWithinAPixel(expectedDim, aboveSquare.getHeight());
        assertWithinAPixel(expectedDim, aboveSquare.getWidth());
        assertWithinAPixel(expectedDim, belowSquare.getHeight());
        assertWithinAPixel(expectedDim, belowSquare.getWidth());

        assertNull(aboveSquare.getClipBounds());
        assertNull(belowSquare.getClipBounds());
    }

    private static boolean isWithinAPixel(float expectedDim, int dim) {
        return (Math.abs(dim - expectedDim) <= 1);
    }

    private static void assertWithinAPixel(float expectedDim, int dim) {
        assertTrue("Expected dimension to be within one pixel of "
                + expectedDim + ", but was " + dim, isWithinAPixel(expectedDim, dim));
    }

    private static void assertNotWithinAPixel(float expectedDim, int dim) {
        assertTrue("Expected dimension to not be within one pixel of "
                + expectedDim + ", but was " + dim, !isWithinAPixel(expectedDim, dim));
    }

    private class MyChangeBounds extends ChangeBounds {
        private static final String PROPNAME_BOUNDS = "android:changeBounds:bounds";
        @Override
        public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
                TransitionValues endValues) {
            Animator animator = super.createAnimator(sceneRoot, startValues, endValues);
            if (animator != null && mBoundsChangeListener != null) {
                animator.addListener(mBoundsChangeListener);
                Rect startBounds = (Rect) startValues.values.get(PROPNAME_BOUNDS);
                Rect endBounds = (Rect) endValues.values.get(PROPNAME_BOUNDS);
            }
            return animator;
        }
    }

    private class ValidateBoundsListener implements ViewTreeObserver.OnDrawListener,
            Animator.AnimatorListener {
        final boolean mGrow;
        final int mMin;
        final int mMax;

        final Point mRedDimensions = new Point(-1, -1);
        final Point mGreenDimensions = new Point(-1, -1);

        View mRedSquare;
        View mGreenSquare;

        boolean mDidChangeSize;

        private ValidateBoundsListener(boolean grow) {
            mGrow = grow;

            Resources resources = mActivity.getResources();
            mMin = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    SMALL_SQUARE_SIZE_DP, resources.getDisplayMetrics()));
            mMax = (int) Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    LARGE_SQUARE_SIZE_DP, resources.getDisplayMetrics()));
        }

        public void validateView(View view, Point dimensions) {
            final String name = view.getTransitionName();
            final boolean clipped = mChangeBounds.getResizeClip();
            assertEquals(clipped, view.getClipBounds() != null);

            final int width;
            final int height;
            if (clipped) {
                width = view.getClipBounds().width();
                height = view.getClipBounds().height();
            } else {
                width = view.getWidth();
                height = view.getHeight();
            }
            validateDim(name, "width", dimensions.x, width);
            validateDim(name, "height", dimensions.y, height);
            dimensions.set(width, height);
        }

        private void validateDim(String name, String dimen, int lastDim, int newDim) {
            if (lastDim != -1) {
                if (mGrow) {
                    assertTrue(name + " new " + dimen + " " + newDim
                                    + " is less than previous " + lastDim,
                            newDim >= lastDim);
                } else {
                    assertTrue(name + " new " + dimen + " " + newDim
                                    + " is more than previous " + lastDim,
                            newDim <= lastDim);
                }
                if (newDim != lastDim) {
                    mDidChangeSize = true;
                }
            }
            assertTrue(name + " " + dimen + " " + newDim + " must be <= " + mMax,
                    newDim <= mMax);
            assertTrue(name + " " + dimen + " " + newDim + " must be >= " + mMin,
                    newDim >= mMin);
        }

        @Override
        public void onDraw() {
            if (mRedSquare == null) {
                mRedSquare = mActivity.findViewById(R.id.redSquare);
                mGreenSquare = mActivity.findViewById(R.id.greenSquare);
            }
            validateView(mRedSquare, mRedDimensions);
            validateView(mGreenSquare, mGreenDimensions);
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mActivity.getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(this);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mActivity.getWindow().getDecorView().getViewTreeObserver().removeOnDrawListener(this);
            assertTrue(mDidChangeSize);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }
    }
}

