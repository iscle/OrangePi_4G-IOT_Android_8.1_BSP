/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.animation.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.XmlResourceParser;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.view.cts.R;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link Animation}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class AnimationTest {
    private static final float COMPARISON_DELTA = 0.001f;

    /** It is defined in R.anim.accelerate_alpha */
    private static final int ACCELERATE_ALPHA_DURATION = 1000;

    /** It is defined in R.anim.decelerate_alpha */
    private static final int DECELERATE_ALPHA_DURATION = 2000;

    private static final int CANCELATION_TIMEOUT = 5000;

    private Instrumentation mInstrumentation;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<AnimationTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(AnimationTestCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        XmlResourceParser parser = mActivity.getResources().getAnimation(R.anim.alpha);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new Animation(mActivity, attrs) {
        };

        new Animation() {
        };
    }

    @Test
    public void testAccessInterpolator() {
        // check default interpolator
        MyAnimation myAnimation = new MyAnimation();
        Interpolator interpolator = myAnimation.getInterpolator();
        assertTrue(interpolator instanceof AccelerateDecelerateInterpolator); // issue 1561186.

        myAnimation.ensureInterpolator();
        assertSame(interpolator, myAnimation.getInterpolator());

        myAnimation.setInterpolator(null);
        assertNull(myAnimation.getInterpolator());
        myAnimation.ensureInterpolator();
        interpolator = myAnimation.getInterpolator();
        assertTrue(interpolator instanceof AccelerateDecelerateInterpolator);

        Animation animation = AnimationUtils.loadAnimation(mActivity, R.anim.decelerate_alpha);
        interpolator = animation.getInterpolator();
        assertNotNull(interpolator);
        assertTrue(interpolator instanceof DecelerateInterpolator);

        animation = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        interpolator = animation.getInterpolator();
        assertNotNull(interpolator);
        assertTrue(interpolator instanceof AccelerateInterpolator);
    }

    @Test
    public void testDefaultFill() {
        Animation animation = new Animation() {
        };
        assertTrue(animation.getFillBefore());
        assertFalse(animation.getFillAfter());
    }

    @Test
    public void testAccessFill() throws Throwable {
        View animWindow = mActivity.findViewById(R.id.anim_window);
        // XML file of R.anim.accelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //     android:interpolator="@android:anim/accelerate_interpolator"
        //     android:fromAlpha="0.1"
        //     android:toAlpha="0.9"
        //     android:duration="1000" />
        Animation animation = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        assertFalse(animation.isFillEnabled());
        assertTrue(animation.getFillBefore());
        assertFalse(animation.getFillAfter());

        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow,
                animation);

        // fillBefore and fillAfter are ignored when fillEnabled is false
        Transformation transformation = new Transformation();
        // check alpha before start
        animation.getTransformation(animation.getStartTime() - 1, transformation);
        float alpha = transformation.getAlpha();
        assertEquals(0.1f, alpha, COMPARISON_DELTA);  // issue 1698355

        transformation = new Transformation();
        // check alpha after the end
        animation.getTransformation(animation.getStartTime() + animation.getDuration() + 1,
                transformation);
        alpha = transformation.getAlpha();
        assertEquals(0.9f, alpha, COMPARISON_DELTA);  // issue 1698355

        animation.setFillEnabled(true);
        animation.setFillBefore(false);
        assertTrue(animation.isFillEnabled());
        assertFalse(animation.getFillBefore());
        assertFalse(animation.getFillAfter());
        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow,
                animation);

        transformation = new Transformation();
        animation.getTransformation(animation.getStartTime() - 1, transformation);
        alpha = transformation.getAlpha();
        assertEquals(1.0f, alpha, COMPARISON_DELTA);

        transformation = new Transformation();
        animation.getTransformation(animation.getStartTime() + animation.getDuration() + 1,
                transformation);
        alpha = transformation.getAlpha();
        assertEquals(1.0f, alpha, COMPARISON_DELTA);

        animation.setFillBefore(true);
        animation.setFillAfter(true);
        assertTrue(animation.isFillEnabled());
        assertTrue(animation.getFillBefore());
        assertTrue(animation.getFillAfter());
        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow,
                animation);

        transformation = new Transformation();
        animation.getTransformation(animation.getStartTime() - 1, transformation);
        alpha = transformation.getAlpha();
        assertEquals(0.1f, alpha, COMPARISON_DELTA);

        transformation = new Transformation();
        animation.getTransformation(animation.getStartTime() + animation.getDuration() + 1,
                transformation);
        alpha = transformation.getAlpha();
        assertEquals(0.9f, alpha, COMPARISON_DELTA);
    }

    @Test
    public void testComputeDurationHint() {
        // start offset is 0, duration is 2000, repeat count is 0.
        Animation animation = AnimationUtils.loadAnimation(mActivity, R.anim.decelerate_alpha);
        assertEquals(2000, animation.computeDurationHint());

        // start offset is 0, duration is 2000, repeat count is 2.
        animation.setRepeatCount(2);
        assertEquals(6000, animation.computeDurationHint());

        // start offset is 800, duration is 2000, repeat count is 2.
        animation.setStartOffset(800);
        assertEquals(8400, animation.computeDurationHint());
    }

    @Test
    public void testRepeatAnimation() throws Throwable {
        // check default repeatMode
        Animation animation = new Animation() {
        };
        assertEquals(Animation.RESTART, animation.getRepeatMode());

        final View animWindow = mActivity.findViewById(R.id.anim_window);

        // XML file of R.anim.decelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/decelerate_interpolator"
        //      android:fromAlpha="0.0"
        //      android:toAlpha="1.0"
        //      android:duration="2000" />
        final Animation anim = AnimationUtils.loadAnimation(mActivity, R.anim.decelerate_alpha);
        assertEquals(Animation.RESTART, animation.getRepeatMode());
        long duration = anim.getDuration();
        assertEquals(DECELERATE_ALPHA_DURATION, duration);
        // repeat count is 0, repeat mode does not make sense.
        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow, anim);

        // test repeat mode REVERSE
        anim.setRepeatCount(1);
        anim.setRepeatMode(Animation.REVERSE);
        // we have to PollingCheck the animation status on test thread,
        // it cannot be done on UI thread, so we invoke runOnUiThread method here.
        mActivityRule.runOnUiThread(() -> animWindow.startAnimation(anim));

        // check whether animation has started
        PollingCheck.waitFor(anim::hasStarted);

        Transformation transformation = new Transformation();
        long startTime = anim.getStartTime();
        anim.getTransformation(startTime, transformation);
        float alpha1 = transformation.getAlpha();
        assertEquals(0.0f, alpha1, COMPARISON_DELTA);

        anim.getTransformation(startTime + 1000, transformation);
        float alpha2 = transformation.getAlpha();

        anim.getTransformation(startTime + 2000, transformation);
        float alpha3 = transformation.getAlpha();
        assertEquals(1.0f, alpha3, COMPARISON_DELTA);

        // wait for animation has ended.
        // timeout is larger than duration, in case the system is sluggish
        PollingCheck.waitFor(duration * 2 + 1000, anim::hasEnded);

        // get start time of reversing.
        startTime = anim.getStartTime();
        anim.getTransformation(startTime + 3000, transformation);
        float alpha4 = transformation.getAlpha();

        anim.getTransformation(startTime + 4000, transformation);
        float alpha5 = transformation.getAlpha();
        assertEquals(0.0f, alpha5, COMPARISON_DELTA);

        // check decelerating delta alpha when reverse. alpha should change form 0.0f to 1.0f
        // and then from 1.0f to 0.0f
        float delta1 = alpha2 - alpha1;
        float delta2 = alpha3 - alpha2;
        // the animation plays backward
        float delta3 = alpha3 - alpha4;
        float delta4 = alpha4 - alpha5;
        assertTrue(delta1 > delta2);
        assertTrue(delta3 > delta4);

        // test repeat mode RESTART
        anim.setRepeatMode(Animation.RESTART);
        // we have to PollingCheck the animation status on test thread,
        // it cannot be done on UI thread, so we invoke runOnUiThread method here.
        mActivityRule.runOnUiThread(() -> animWindow.startAnimation(anim));

        // check whether animation has started
        PollingCheck.waitFor(anim::hasStarted);

        transformation = new Transformation();
        startTime = anim.getStartTime();
        anim.getTransformation(startTime, transformation);
        alpha1 = transformation.getAlpha();
        assertEquals(0.0f, alpha1, COMPARISON_DELTA);

        anim.getTransformation(startTime + 1000, transformation);
        alpha2 = transformation.getAlpha();

        anim.getTransformation(startTime + 2000, transformation);
        alpha3 = transformation.getAlpha();
        assertEquals(1.0f, alpha3, COMPARISON_DELTA);

        // wait for animation has ended.
        // timeout is larger than duration, in case the system is sluggish
        PollingCheck.waitFor(duration * 2 + 1000, anim::hasEnded);

        // get start time of restarting.
        startTime = anim.getStartTime();
        anim.getTransformation(startTime + 3000, transformation);
        alpha4 = transformation.getAlpha();

        anim.getTransformation(startTime + 4000, transformation);
        alpha5 = transformation.getAlpha();
        assertEquals(1.0f, alpha5, COMPARISON_DELTA);

        // check decelerating delta alpha when restart. alpha should change form 0.0f to 1.0f
        // and then from 0.0f to 1.0f again
        delta1 = alpha2 - 0.0f;
        delta2 = alpha3 - alpha2;
        // the animation restarts from the beginning
        delta3 = alpha4 - 0.0f;
        delta4 = alpha5 - alpha4;
        assertTrue(delta1 > delta2);
        assertTrue(delta3 > delta4);
    }

    @Test
    public void testAccessStartOffset() throws Throwable {
        final long startOffset = 800;
        // check default startOffset
        Animation animation = new Animation() {
        };
        assertEquals(0, animation.getStartOffset());

        View animWindow = mActivity.findViewById(R.id.anim_window);
        // XML file of R.anim.accelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/accelerate_interpolator"
        //      android:fromAlpha="0.1"
        //      android:toAlpha="0.9"
        //      android:duration="1000" />
        animation = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        animation.setStartOffset(startOffset);
        assertEquals(startOffset, animation.getStartOffset());

        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow,
                animation, ACCELERATE_ALPHA_DURATION + startOffset);

        Transformation transformation = new Transformation();
        long startTime = animation.getStartTime();
        animation.getTransformation(startTime, transformation);
        float alpha1 = transformation.getAlpha();
        assertEquals(0.1f, alpha1, COMPARISON_DELTA);

        animation.getTransformation(startTime + 400, transformation);
        float alpha2 = transformation.getAlpha();
        // alpha is 0.1f during start offset
        assertEquals(0.1f, alpha2, COMPARISON_DELTA);

        animation.getTransformation(startTime + startOffset, transformation);
        float alpha3 = transformation.getAlpha();
        // alpha is 0.1f during start offset
        assertEquals(0.1f, alpha3, COMPARISON_DELTA);

        animation.getTransformation(startTime + startOffset + 1, transformation);
        float alpha4 = transformation.getAlpha();
        // alpha is lager than 0.1f after start offset
        assertTrue(alpha4 > 0.1f);
    }

    @Test
    public void testRunAccelerateAlpha() throws Throwable {
        // check default startTime
        Animation animation = new Animation() {
        };
        assertEquals(Animation.START_ON_FIRST_FRAME, animation.getStartTime());

        long currentTime = AnimationUtils.currentAnimationTimeMillis();
        animation.setStartTime(currentTime);
        assertEquals(currentTime, animation.getStartTime());

        View animWindow = mActivity.findViewById(R.id.anim_window);

        // XML file of R.anim.accelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/accelerate_interpolator"
        //      android:fromAlpha="0.1"
        //      android:toAlpha="0.9"
        //      android:duration="1000" />
        Animation anim = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        assertEquals(Animation.START_ON_FIRST_FRAME, anim.getStartTime());
        assertFalse(anim.hasStarted());
        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow, anim);
    }

    @Test
    public void testGetTransformation() throws Throwable {
        final View animWindow = mActivity.findViewById(R.id.anim_window);

        // XML file of R.anim.accelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/accelerate_interpolator"
        //      android:fromAlpha="0.1"
        //      android:toAlpha="0.9"
        //      android:duration="1000" />
        final Animation anim = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        assertFalse(anim.hasStarted());

        // we have to PollingCheck the animation status on test thread,
        // it cannot be done on UI thread, so we invoke runOnUiThread method here.
        mActivityRule.runOnUiThread(() -> animWindow.startAnimation(anim));

        // check whether animation has started
        PollingCheck.waitFor(anim::hasStarted);

        // check transformation objects that is provided by the
        // caller and will be filled in by the animation.
        Transformation transformation = new Transformation();
        long startTime = anim.getStartTime();
        assertTrue(anim.getTransformation(startTime, transformation));
        float alpha1 = transformation.getAlpha();
        assertEquals(0.1f, alpha1, COMPARISON_DELTA);

        assertTrue(anim.getTransformation(startTime + 250, transformation));
        float alpha2 = transformation.getAlpha();

        assertTrue(anim.getTransformation(startTime + 500, transformation));
        float alpha3 = transformation.getAlpha();

        assertTrue(anim.getTransformation(startTime + 750, transformation));
        float alpha4 = transformation.getAlpha();

        // wait for animation has ended.
        // timeout is larger than duration, in case the system is sluggish
        PollingCheck.waitFor(2000, anim::hasEnded);

        assertFalse(anim.getTransformation(startTime + 1000, transformation));
        float alpha5 = transformation.getAlpha();
        assertEquals(0.9f, alpha5, COMPARISON_DELTA);

        // check decelerating delta alpha
        float delta1 = alpha2 - alpha1;
        float delta2 = alpha3 - alpha2;
        float delta3 = alpha4 - alpha3;
        float delta4 = alpha5 - alpha4;
        assertTrue(delta1 < delta2);
        assertTrue(delta2 < delta3);
        assertTrue(delta3 < delta4);
    }

    @Test
    public void testAccessZAdjustment() {
        // check default zAdjustment
        Animation animation = new Animation() {
        };
        assertEquals(Animation.ZORDER_NORMAL, animation.getZAdjustment());

        animation = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        assertEquals(Animation.ZORDER_NORMAL, animation.getZAdjustment());

        animation.setZAdjustment(Animation.ZORDER_TOP);
        assertEquals(Animation.ZORDER_TOP, animation.getZAdjustment());

        animation.setZAdjustment(Animation.ZORDER_BOTTOM);
        assertEquals(Animation.ZORDER_BOTTOM, animation.getZAdjustment());
    }

    @Test
    public void testInitialize() {
        Animation animation = new Animation() {
        };

        assertFalse(animation.isInitialized());
        animation.initialize(320, 480, 320, 480);
        assertTrue(animation.isInitialized());
    }

    @Test
    public void testResolveSize() {
        MyAnimation myAnimation = new MyAnimation();

        assertEquals(1.0f, myAnimation.resolveSize(Animation.ABSOLUTE, 1.0f, 0, 0),
                COMPARISON_DELTA);
        assertEquals(2.0f, myAnimation.resolveSize(Animation.ABSOLUTE, 2.0f, 0, 0),
                COMPARISON_DELTA);

        assertEquals(6.0f, myAnimation.resolveSize(Animation.RELATIVE_TO_SELF, 3.0f, 2, 0),
                COMPARISON_DELTA);
        assertEquals(9.0f, myAnimation.resolveSize(Animation.RELATIVE_TO_SELF, 3.0f, 3, 0),
                COMPARISON_DELTA);

        assertEquals(18.0f, myAnimation.resolveSize(Animation.RELATIVE_TO_PARENT, 3.0f, 0, 6),
                COMPARISON_DELTA);
        assertEquals(12.0f, myAnimation.resolveSize(Animation.RELATIVE_TO_PARENT, 3.0f, 0, 4),
                COMPARISON_DELTA);

        int unknownType = 7;
        assertEquals(8.0f, myAnimation.resolveSize(unknownType, 8.0f, 3, 4), COMPARISON_DELTA);
        assertEquals(10.0f, myAnimation.resolveSize(unknownType, 10.0f, 3, 4), COMPARISON_DELTA);
    }

    @Test
    public void testRestrictDuration() {
        Animation animation = new Animation() {
        };

        animation.setStartOffset(1000);
        animation.restrictDuration(500);
        assertEquals(500, animation.getStartOffset());
        assertEquals(0, animation.getDuration());
        assertEquals(0, animation.getRepeatCount());

        animation.setStartOffset(1000);
        animation.setDuration(1000);
        animation.restrictDuration(1500);
        assertEquals(500, animation.getDuration());

        animation.setStartOffset(1000);
        animation.setDuration(1000);
        animation.setRepeatCount(3);
        animation.restrictDuration(4500);
        assertEquals(1, animation.getRepeatCount());
    }

    @Test
    public void testScaleCurrentDuration() {
        Animation animation = new Animation() {
        };

        animation.setDuration(10);
        animation.scaleCurrentDuration(0);
        assertEquals(0, animation.getDuration());

        animation.setDuration(10);
        animation.scaleCurrentDuration(2);
        assertEquals(20, animation.getDuration());

        animation.setDuration(10);
        animation.scaleCurrentDuration(-1);
        assertEquals(-10, animation.getDuration());
    }

    @LargeTest
    @Test
    public void testSetAnimationListener() throws Throwable {
        final View animWindow = mActivity.findViewById(R.id.anim_window);

        // XML file of R.anim.accelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/accelerate_interpolator"
        //      android:fromAlpha="0.1"
        //      android:toAlpha="0.9"
        //      android:duration="1000" />
        final Animation anim = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        final AnimationListener listener = mock(AnimationListener.class);
        anim.setAnimationListener(listener);
        verifyZeroInteractions(listener);

        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow, anim);
        verify(listener, times(1)).onAnimationStart(anim);
        verify(listener, times(1)).onAnimationEnd(anim);
        verify(listener, never()).onAnimationRepeat(anim);

        reset(listener);
        anim.setRepeatCount(2);
        anim.setRepeatMode(Animation.REVERSE);

        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow, anim,
                3000);
        verify(listener, times(1)).onAnimationStart(anim);
        verify(listener, times(2)).onAnimationRepeat(anim);
        verify(listener, times(1)).onAnimationEnd(anim);

        reset(listener);
        // onAnimationEnd will not be invoked and animation should not end
        anim.setRepeatCount(Animation.INFINITE);

        mActivityRule.runOnUiThread(() -> animWindow.startAnimation(anim));
        // Verify that our animation doesn't call listener's onAnimationEnd even after a long
        // period of time. We just sleep and then verify what's happened with the listener.
        SystemClock.sleep(4 * ACCELERATE_ALPHA_DURATION);

        verify(listener, times(1)).onAnimationStart(anim);
        verify(listener, atLeastOnce()).onAnimationRepeat(anim);
        verify(listener, never()).onAnimationEnd(anim);
    }

    @Test
    public void testStart() {
        Animation animation = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        animation.setStartTime(0);

        animation.start();
        assertEquals(Animation.START_ON_FIRST_FRAME, animation.getStartTime());
    }

    @Test
    public void testStartNow() {
        Animation animation = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);
        animation.setStartTime(0);

        long currentTime = AnimationUtils.currentAnimationTimeMillis();
        animation.startNow();
        assertEquals(currentTime, animation.getStartTime(), 100);
    }

    @Test
    public void testWillChangeBounds() {
        Animation animation = new Animation() {
        };

        assertTrue(animation.willChangeBounds());
    }

    @Test
    public void testWillChangeTransformationMatrix() {
        Animation animation = new Animation() {
        };

        assertTrue(animation.willChangeTransformationMatrix());
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        MyAnimation myAnimation = new MyAnimation();
        myAnimation.setDuration(3000);
        myAnimation.setFillAfter(true);
        myAnimation.setFillBefore(false);
        myAnimation.setFillEnabled(true);
        myAnimation.setStartTime(1000);
        myAnimation.setRepeatCount(10);
        myAnimation.setRepeatMode(Animation.REVERSE);

        Animation cloneAnimation = myAnimation.clone();
        assertNotSame(myAnimation, cloneAnimation);
        assertEquals(myAnimation.getDuration(), cloneAnimation.getDuration());
        assertEquals(myAnimation.getFillAfter(), cloneAnimation.getFillAfter());
        assertEquals(myAnimation.getFillBefore(), cloneAnimation.getFillBefore());
        assertEquals(myAnimation.isFillEnabled(), cloneAnimation.isFillEnabled());
        assertEquals(myAnimation.getStartOffset(), cloneAnimation.getStartOffset());
        assertEquals(myAnimation.getRepeatCount(), cloneAnimation.getRepeatCount());
        assertEquals(myAnimation.getRepeatMode(), cloneAnimation.getRepeatMode());
    }

    @Test
    public void testCancelImmediately() throws Throwable {
        MyAnimation anim = new MyAnimation();
        final CountDownLatch latch1 = new CountDownLatch(1);
        runCanceledAnimation(anim, latch1, false, false);
        assertTrue(latch1.await(CANCELATION_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anim.isStillAnimating());
    }

    @Test
    public void testRepeatingCancelImmediately() throws Throwable {
        MyAnimation anim = new MyAnimation();
        final CountDownLatch latch2 = new CountDownLatch(1);
        runCanceledAnimation(anim, latch2, true, false);
        assertTrue(latch2.await(CANCELATION_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anim.isStillAnimating());
    }

    @Test
    public void testCancelDelayed() throws Throwable {
        MyAnimation anim = new MyAnimation();
        final CountDownLatch latch3 = new CountDownLatch(1);
        runCanceledAnimation(anim, latch3, false, true);
        assertTrue(latch3.await(CANCELATION_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anim.isStillAnimating());
    }

    @Test
    public void testRepeatingCancelDelayed() throws Throwable {
        MyAnimation anim = new MyAnimation();
        final CountDownLatch latch4 = new CountDownLatch(1);
        runCanceledAnimation(anim, latch4, true, true);
        assertTrue(latch4.await(CANCELATION_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anim.isStillAnimating());
    }

    private void runCanceledAnimation(final MyAnimation anim, final CountDownLatch latch,
            final boolean repeating, final boolean delayed) throws Throwable {
        // The idea behind this test is that canceling an Animation should result in
        // it ending, which means not having its getTransformation() method called
        // anymore. The trick is that cancel() will still allow one more frame to run,
        // so we have to insert some delay between when we cancel and when we can check
        // whether it is still animating.
        final View view = mActivity.findViewById(R.id.anim_window);
        mActivityRule.runOnUiThread(() -> {
            anim.setDuration(delayed ? 150 : 100);
            if (repeating) {
                anim.setRepeatCount(Animation.INFINITE);
            }
            view.startAnimation(anim);
            if (!delayed) {
                anim.cancel();
            } else {
                view.postDelayed(anim::cancel, 50);
            }
            view.postDelayed(() -> {
                anim.setStillAnimating(false);
                view.postDelayed(latch::countDown, 50);
            }, delayed ? 100 : 50);
        });
    }

    private class MyAnimation extends Animation {
        boolean mStillAnimating = false;

        @Override
        protected void ensureInterpolator() {
            super.ensureInterpolator();
        }

        @Override
        protected float resolveSize(int type, float value, int size, int parentSize) {
            return super.resolveSize(type, value, size, parentSize);
        }

        @Override
        protected Animation clone() throws CloneNotSupportedException {
            return super.clone();
        }

        public void setStillAnimating(boolean value) {
            mStillAnimating = value;
        }

        public boolean isStillAnimating() {
            return mStillAnimating;
        }

        @Override
        public boolean getTransformation(long currentTime, Transformation outTransformation) {
            mStillAnimating = true;
            return super.getTransformation(currentTime, outTransformation);
        }
    }
}
