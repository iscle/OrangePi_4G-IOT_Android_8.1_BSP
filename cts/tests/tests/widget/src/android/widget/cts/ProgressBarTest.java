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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.cts.util.TestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ProgressBarTest {
    private ProgressBarCtsActivity mActivity;
    private ProgressBar mProgressBar;
    private ProgressBar mProgressBarHorizontal;

    @Rule
    public ActivityTestRule<ProgressBarCtsActivity> mActivityRule =
            new ActivityTestRule<>(ProgressBarCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mProgressBar = (ProgressBar) mActivity.findViewById(R.id.progress);
        mProgressBarHorizontal = (ProgressBar) mActivity.findViewById(R.id.progress_horizontal);
    }

    @Test
    public void testConstructor() {
        new ProgressBar(mActivity);

        new ProgressBar(mActivity, null);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyle);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleHorizontal);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleInverse);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleLarge);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleLargeInverse);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleSmall);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleSmallInverse);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleSmallTitle);

        new ProgressBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_ProgressBar);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Horizontal);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Large);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Large_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Small);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Small_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Small_Title);

        new ProgressBar(mActivity, null, 0, android.R.style.Widget_Material_Light_ProgressBar);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Horizontal);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Large);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Large_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Small);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Small_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Small_Title);
    }

    @UiThreadTest
    @Test
    public void testSetIndeterminate() {
        assertTrue(mProgressBar.isIndeterminate());

        mProgressBar.setIndeterminate(true);
        assertTrue(mProgressBar.isIndeterminate());

        mProgressBar.setIndeterminate(false);
        // because default is Indeterminate only progressBar, can't change the status
        assertTrue(mProgressBar.isIndeterminate());

        assertFalse(mProgressBarHorizontal.isIndeterminate());

        mProgressBarHorizontal.setIndeterminate(true);
        assertTrue(mProgressBarHorizontal.isIndeterminate());

        mProgressBarHorizontal.setIndeterminate(false);
        assertFalse(mProgressBarHorizontal.isIndeterminate());
    }

    @UiThreadTest
    @Test
    public void testQueryAnimationState() {
        assertTrue(mProgressBar.isAnimating());
        assertFalse(mProgressBarHorizontal.isAnimating());

        mProgressBarHorizontal.setIndeterminate(true);
        assertTrue(mProgressBarHorizontal.isAnimating());

        mProgressBarHorizontal.setIndeterminate(false);
        assertFalse(mProgressBarHorizontal.isAnimating());

        mProgressBar.setVisibility(View.GONE);
        assertFalse(mProgressBar.isAnimating());
        mProgressBar.setVisibility(View.INVISIBLE);
        assertFalse(mProgressBar.isAnimating());
        mProgressBar.setVisibility(View.VISIBLE);
        assertTrue(mProgressBar.isAnimating());
    }

    @UiThreadTest
    @Test
    public void testAccessIndeterminateDrawable() {
        // set IndeterminateDrawable
        // normal value
        Drawable mockProgressDrawable = spy(new ColorDrawable(Color.YELLOW));
        mProgressBar.setIndeterminateDrawable(mockProgressDrawable);
        assertSame(mockProgressDrawable, mProgressBar.getIndeterminateDrawable());
        verify(mockProgressDrawable, never()).draw(any(Canvas.class));
        mProgressBar.draw(new Canvas());
        verify(mockProgressDrawable, atLeastOnce()).draw(any(Canvas.class));

        // exceptional value
        mProgressBar.setIndeterminateDrawable(null);
        assertNull(mProgressBar.getIndeterminateDrawable());
    }

    @UiThreadTest
    @Test
    public void testAccessProgressDrawable() {
        // set ProgressDrawable
        // normal value
        Drawable mockProgressDrawable = spy(new ColorDrawable(Color.BLUE));
        mProgressBarHorizontal.setProgressDrawable(mockProgressDrawable);
        assertSame(mockProgressDrawable, mProgressBarHorizontal.getProgressDrawable());
        verify(mockProgressDrawable, never()).draw(any(Canvas.class));
        mProgressBarHorizontal.draw(new Canvas());
        verify(mockProgressDrawable, atLeastOnce()).draw(any(Canvas.class));

        // exceptional value
        mProgressBarHorizontal.setProgressDrawable(null);
        assertNull(mProgressBarHorizontal.getProgressDrawable());
    }

    @UiThreadTest
    @Test
    public void testAccessProgress() {
        assertEquals(0, mProgressBarHorizontal.getProgress());

        final int maxProgress = mProgressBarHorizontal.getMax();
        // set Progress
        // normal value
        mProgressBarHorizontal.setProgress(maxProgress >> 1);
        assertEquals(maxProgress >> 1, mProgressBarHorizontal.getProgress());

        // exceptional values
        mProgressBarHorizontal.setProgress(-1);
        assertEquals(0, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(maxProgress + 1);
        assertEquals(maxProgress, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(Integer.MAX_VALUE);
        assertEquals(maxProgress, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(0, true);
        assertEquals(0, mProgressBarHorizontal.getProgress());

        // when in indeterminate mode
        mProgressBarHorizontal.setIndeterminate(true);
        mProgressBarHorizontal.setProgress(maxProgress >> 1);
        assertEquals(0, mProgressBarHorizontal.getProgress());
    }

    @UiThreadTest
    @Test
    public void testAccessSecondaryProgress() {
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());

        final int maxProgress = mProgressBarHorizontal.getMax();
        // set SecondaryProgress
        // normal value
        mProgressBarHorizontal.setSecondaryProgress(maxProgress >> 1);
        assertEquals(maxProgress >> 1, mProgressBarHorizontal.getSecondaryProgress());

        // exceptional value
        mProgressBarHorizontal.setSecondaryProgress(-1);
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.setSecondaryProgress(maxProgress + 1);
        assertEquals(maxProgress, mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.setSecondaryProgress(Integer.MAX_VALUE);
        assertEquals(maxProgress, mProgressBarHorizontal.getSecondaryProgress());

        // when in indeterminate mode
        mProgressBarHorizontal.setIndeterminate(true);
        mProgressBarHorizontal.setSecondaryProgress(maxProgress >> 1);
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());
    }

    @UiThreadTest
    @Test
    public void testIncrementProgressBy() {
        // normal value
        int increment = 1;
        int oldProgress = mProgressBarHorizontal.getProgress();
        mProgressBarHorizontal.incrementProgressBy(increment);
        assertEquals(oldProgress + increment, mProgressBarHorizontal.getProgress());

        increment = mProgressBarHorizontal.getMax() >> 1;
        oldProgress = mProgressBarHorizontal.getProgress();
        mProgressBarHorizontal.incrementProgressBy(increment);
        assertEquals(oldProgress + increment, mProgressBarHorizontal.getProgress());

        // exceptional values
        mProgressBarHorizontal.setProgress(0);
        mProgressBarHorizontal.incrementProgressBy(Integer.MAX_VALUE);
        assertEquals(mProgressBarHorizontal.getMax(), mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(0);
        mProgressBarHorizontal.incrementProgressBy(Integer.MIN_VALUE);
        assertEquals(0, mProgressBarHorizontal.getProgress());
    }

    @UiThreadTest
    @Test
    public void testIncrementSecondaryProgressBy() {
        // normal value
        int increment = 1;
        int oldSecondaryProgress = mProgressBarHorizontal.getSecondaryProgress();
        mProgressBarHorizontal.incrementSecondaryProgressBy(increment);
        assertEquals(oldSecondaryProgress + increment,
                mProgressBarHorizontal.getSecondaryProgress());

        increment = mProgressBarHorizontal.getMax() >> 1;
        oldSecondaryProgress = mProgressBarHorizontal.getSecondaryProgress();
        mProgressBarHorizontal.incrementSecondaryProgressBy(increment);
        assertEquals(oldSecondaryProgress + increment,
                mProgressBarHorizontal.getSecondaryProgress());

        // exceptional values
        mProgressBarHorizontal.setSecondaryProgress(0);
        mProgressBarHorizontal.incrementSecondaryProgressBy(Integer.MAX_VALUE);
        assertEquals(mProgressBarHorizontal.getMax(),
                mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.setSecondaryProgress(0);
        mProgressBarHorizontal.incrementSecondaryProgressBy(Integer.MIN_VALUE);
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());
    }

    @UiThreadTest
    @Test
    public void testAccessInterpolator() {
        // default should be LinearInterpolator
        assertTrue(mProgressBar.getInterpolator() instanceof LinearInterpolator);

        Interpolator interpolator = new AccelerateDecelerateInterpolator();
        mProgressBar.setInterpolator(interpolator);
        assertEquals(interpolator, mProgressBar.getInterpolator());

        mProgressBar.setInterpolator(mActivity, android.R.anim.accelerate_interpolator);
        assertTrue(mProgressBar.getInterpolator() instanceof AccelerateInterpolator);
    }

    @UiThreadTest
    @Test
    public void testSetVisibility() {
        // set visibility
        // normal value
        int visibility = View.VISIBLE;
        mProgressBarHorizontal.setVisibility(visibility);
        assertEquals(visibility, mProgressBarHorizontal.getVisibility());

        visibility = View.GONE;
        mProgressBarHorizontal.setVisibility(visibility);
        assertEquals(visibility, mProgressBarHorizontal.getVisibility());

        // exceptional value
        visibility = 0xfffffff5; // -11
        int mask = 0x0000000C; // View.VISIBILITY_MASK
        int expected = (mProgressBarHorizontal.getVisibility() & ~mask) | (visibility & mask);
        mProgressBarHorizontal.setVisibility(visibility);
        assertEquals(expected, mProgressBarHorizontal.getVisibility());

        visibility = 0x7fffffff; // Integer.MAX_VALUE;
        expected = (mProgressBarHorizontal.getVisibility() & ~mask) | (visibility & mask);
        mProgressBarHorizontal.setVisibility(Integer.MAX_VALUE);
        assertEquals(expected, mProgressBarHorizontal.getVisibility());
    }

    @UiThreadTest
    @Test
    public void testInvalidateDrawable() {
        ProgressBar mockProgressBar = spy(new ProgressBar(mActivity));

        Drawable mockDrawable1 = spy(new ColorDrawable(Color.RED));
        Drawable mockDrawable2 = spy(new ColorDrawable(Color.GREEN));
        mockProgressBar.setBackgroundDrawable(mockDrawable1);

        mockProgressBar.invalidateDrawable(mockDrawable1);
        verify(mockProgressBar, atLeastOnce()).invalidate(anyInt(), anyInt(), anyInt(), anyInt());

        reset(mockProgressBar);
        mockProgressBar.invalidateDrawable(mockDrawable2);
        verify(mockProgressBar, never()).invalidate(anyInt(), anyInt(), anyInt(), anyInt());

        mockProgressBar.setIndeterminateDrawable(mockDrawable1);
        mockProgressBar.setProgressDrawable(mockDrawable2);
    }

    @UiThreadTest
    @Test
    public void testPostInvalidate() {
        mProgressBarHorizontal.postInvalidate();
    }

    @UiThreadTest
    @Test
    public void testAccessMax() {
        // set Progress
        int progress = 10;
        mProgressBarHorizontal.setProgress(progress);

        // normal value
        int max = progress + 1;
        mProgressBarHorizontal.setMax(max);
        assertEquals(max, mProgressBarHorizontal.getMax());
        assertEquals(progress, mProgressBarHorizontal.getProgress());

        max = progress - 1;
        mProgressBarHorizontal.setMax(max);
        assertEquals(max, mProgressBarHorizontal.getMax());
        assertEquals(max, mProgressBarHorizontal.getProgress());

        // exceptional values
        mProgressBarHorizontal.setMax(-1);
        assertEquals(0, mProgressBarHorizontal.getMax());
        assertEquals(0, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setMax(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, mProgressBarHorizontal.getMax());
        assertEquals(0, mProgressBarHorizontal.getProgress());
    }

    @UiThreadTest
    @Test
    public void testProgressTint() {
        ProgressBar tintedProgressBar = (ProgressBar) mActivity.findViewById(R.id.progress_tint);

        assertEquals("Progress tint inflated correctly",
                Color.WHITE, tintedProgressBar.getProgressTintList().getDefaultColor());
        assertEquals("Progress tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getProgressTintMode());

        assertEquals("Progress background tint inflated correctly",
                Color.WHITE, tintedProgressBar.getProgressBackgroundTintList().getDefaultColor());
        assertEquals("Progress background tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getProgressBackgroundTintMode());

        assertEquals("Secondary progress tint inflated correctly",
                Color.WHITE, tintedProgressBar.getSecondaryProgressTintList().getDefaultColor());
        assertEquals("Secondary progress tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getSecondaryProgressTintMode());

        Drawable mockProgressDrawable = spy(new ColorDrawable(Color.BLACK));

        mProgressBar.setProgressDrawable(mockProgressDrawable);
        // No progress tint applied by default
        verify(mockProgressDrawable, never()).setTintList(any(ColorStateList.class));

        mProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        // Progress background tint not applied when layer missing
        verify(mockProgressDrawable, never()).setTintList(any(ColorStateList.class));

        mProgressBar.setSecondaryProgressTintList(ColorStateList.valueOf(Color.WHITE));
        // Secondary progress tint not applied when layer missing
        verify(mockProgressDrawable, never()).setTintList(any(ColorStateList.class));

        mProgressBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        // Progress tint applied when setProgressTintList() called after setProgress()
        verify(mockProgressDrawable, times(1)).setTintList(TestUtils.colorStateListOf(Color.WHITE));

        mProgressBar.setProgressBackgroundTintMode(PorterDuff.Mode.DST_OVER);
        assertEquals(PorterDuff.Mode.DST_OVER, mProgressBar.getProgressBackgroundTintMode());

        mProgressBar.setSecondaryProgressTintMode(PorterDuff.Mode.DST_IN);
        assertEquals(PorterDuff.Mode.DST_IN, mProgressBar.getSecondaryProgressTintMode());

        mProgressBar.setProgressTintMode(PorterDuff.Mode.DST_ATOP);
        assertEquals(PorterDuff.Mode.DST_ATOP, mProgressBar.getProgressTintMode());

        reset(mockProgressDrawable);
        mProgressBar.setProgressDrawable(null);
        mProgressBar.setProgressDrawable(mockProgressDrawable);
        // Progress tint applied when setProgressTintList() called before setProgress()
        verify(mockProgressDrawable, times(1)).setTintList(TestUtils.colorStateListOf(Color.WHITE));
    }

    @UiThreadTest
    @Test
    public void testIndeterminateTint() {
        ProgressBar tintedProgressBar =
                (ProgressBar) mActivity.findViewById(R.id.indeterminate_tint);

        assertEquals("Indeterminate tint inflated correctly",
                Color.WHITE, tintedProgressBar.getIndeterminateTintList().getDefaultColor());
        assertEquals("Indeterminate tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getIndeterminateTintMode());

        Drawable mockIndeterminateDrawable = spy(new ColorDrawable(Color.MAGENTA));

        mProgressBar.setIndeterminateDrawable(mockIndeterminateDrawable);
        // No indeterminate tint applied by default
        verify(mockIndeterminateDrawable, never()).setTintList(any(ColorStateList.class));

        mProgressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.RED));
        // Indeterminate tint applied when setIndeterminateTintList() called after
        // setIndeterminate()
        verify(mockIndeterminateDrawable, times(1)).setTintList(
                TestUtils.colorStateListOf(Color.RED));

        mProgressBar.setIndeterminateTintMode(PorterDuff.Mode.LIGHTEN);
        assertEquals(PorterDuff.Mode.LIGHTEN, mProgressBar.getIndeterminateTintMode());

        reset(mockIndeterminateDrawable);
        mProgressBar.setIndeterminateDrawable(null);
        mProgressBar.setIndeterminateDrawable(mockIndeterminateDrawable);
        // Indeterminate tint applied when setIndeterminateTintList() called before
        // setIndeterminate()
        verify(mockIndeterminateDrawable, times(1)).setTintList(
                TestUtils.colorStateListOf(Color.RED));
    }

    @UiThreadTest
    @Test
    public void testVerifyDrawable() {
        MockProgressBar mockProgressBar =
                (MockProgressBar) mActivity.findViewById(R.id.progress_custom);

        Drawable d1 = mActivity.getDrawable(R.drawable.blue);
        Drawable d2 = mActivity.getDrawable(R.drawable.red);
        Drawable d3 = mActivity.getDrawable(R.drawable.yellow);

        mockProgressBar.setBackgroundDrawable(d1);
        assertTrue(mockProgressBar.verifyDrawable(d1));
        assertFalse(mockProgressBar.verifyDrawable(d2));
        assertFalse(mockProgressBar.verifyDrawable(d3));

        mockProgressBar.setIndeterminateDrawable(d2);
        assertTrue(mockProgressBar.verifyDrawable(d1));
        assertTrue(mockProgressBar.verifyDrawable(d2));
        assertFalse(mockProgressBar.verifyDrawable(d3));

        mockProgressBar.setProgressDrawable(d3);
        assertTrue(mockProgressBar.verifyDrawable(d1));
        assertTrue(mockProgressBar.verifyDrawable(d2));
        assertTrue(mockProgressBar.verifyDrawable(d3));
    }

    @UiThreadTest
    @Test
    public void testOnSaveAndRestoreInstanceState() {
        int oldProgress = 1;
        int oldSecondaryProgress = mProgressBarHorizontal.getMax() - 1;
        mProgressBarHorizontal.setProgress(oldProgress);
        mProgressBarHorizontal.setSecondaryProgress(oldSecondaryProgress);
        assertEquals(oldProgress, mProgressBarHorizontal.getProgress());
        assertEquals(oldSecondaryProgress, mProgressBarHorizontal.getSecondaryProgress());

        Parcelable state = mProgressBarHorizontal.onSaveInstanceState();

        int newProgress = 2;
        int newSecondaryProgress = mProgressBarHorizontal.getMax() - 2;
        mProgressBarHorizontal.setProgress(newProgress);
        mProgressBarHorizontal.setSecondaryProgress(newSecondaryProgress);
        assertEquals(newProgress, mProgressBarHorizontal.getProgress());
        assertEquals(newSecondaryProgress, mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.onRestoreInstanceState(state);
        assertEquals(oldProgress, mProgressBarHorizontal.getProgress());
        assertEquals(oldSecondaryProgress, mProgressBarHorizontal.getSecondaryProgress());
    }

    /*
     * Mock class for ProgressBar to test protected methods
     */
    public static class MockProgressBar extends ProgressBar {
        public MockProgressBar(Context context) {
            super(context);
        }

        public MockProgressBar(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return super.verifyDrawable(who);
        }
    }
}
