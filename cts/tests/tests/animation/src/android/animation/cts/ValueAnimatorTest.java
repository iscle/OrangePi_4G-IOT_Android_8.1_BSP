/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.animation.cts;

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ValueAnimatorTest {
    private static final float EPSILON = 0.0001f;
    private static float sPreviousAnimatorScale = 1.0f;

    private AnimationActivity mActivity;
    private ValueAnimator mValueAnimator;
    private final long mDuration = 2000;

    @Rule
    public ActivityTestRule<AnimationActivity> mActivityRule =
            new ActivityTestRule<>(AnimationActivity.class);

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        mActivity = mActivityRule.getActivity();
        mValueAnimator = mActivity.createAnimatorWithDuration(mDuration);
    }

    @BeforeClass
    public static void beforeClass() {
        sPreviousAnimatorScale = ValueAnimator.getDurationScale();
        ValueAnimator.setDurationScale(1.0f);
    }

    @AfterClass
    public static void afterClass() {
        ValueAnimator.setDurationScale(sPreviousAnimatorScale);
    }

    @Test
    public void testDuration() throws Throwable {
        ValueAnimator valueAnimatorLocal = mActivity.createAnimatorWithDuration(mDuration);
        startAnimation(valueAnimatorLocal);
        assertEquals(mDuration, valueAnimatorLocal.getDuration());
    }

    @Test
    public void testIsRunning() throws Throwable {
        assertFalse(mValueAnimator.isRunning());
        startAnimation(mValueAnimator);
        ValueAnimator valueAnimatorReturned = mActivity.view.bounceYAnimator;
        assertTrue(valueAnimatorReturned.isRunning());
    }

    @Test
    public void testIsStarted() throws Throwable {
        assertFalse(mValueAnimator.isRunning());
        assertFalse(mValueAnimator.isStarted());
        long startDelay = 10000;
        mValueAnimator.setStartDelay(startDelay);
        startAnimation(mValueAnimator);
        assertFalse(mValueAnimator.isRunning());
        assertTrue(mValueAnimator.isStarted());
    }

    @Test
    public void testRepeatMode() throws Throwable {
        ValueAnimator mValueAnimator = mActivity.createAnimatorWithRepeatMode(
            ValueAnimator.RESTART);
        startAnimation(mValueAnimator);
        assertEquals(ValueAnimator.RESTART, mValueAnimator.getRepeatMode());
    }

    @Test
    public void testRepeatCount() throws Throwable {
        int repeatCount = 2;
        ValueAnimator mValueAnimator = mActivity.createAnimatorWithRepeatCount(repeatCount);
        startAnimation(mValueAnimator);
        assertEquals(repeatCount, mValueAnimator.getRepeatCount());
    }

    @Test
    public void testStartDelay() {
        long startDelay = 1000;
        mValueAnimator.setStartDelay(startDelay);
        assertEquals(startDelay, mValueAnimator.getStartDelay());
    }

    /**
     * Verify that an animator with start delay will have its listener's onAnimationStart(...)
     * and onAnimationEnd(...) called at the right time.
     */
    @Test
    public void testListenerCallbackWithStartDelay() throws Throwable {
        final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setStartDelay(300);
        anim.setDuration(300);
        AnimatorListener listener = mock(AnimatorListenerAdapter.class);
        anim.addListener(listener);
        mActivityRule.runOnUiThread(() -> {
            anim.start();
        });

        verify(listener, timeout(450).times(1)).onAnimationStart(anim, false);
        verify(listener, timeout(450).times(1)).onAnimationEnd(anim, false);
    }

    @Test
    public void testGetCurrentPlayTime() throws Throwable {
        startAnimation(mValueAnimator);
        SystemClock.sleep(100);
        long currentPlayTime = mValueAnimator.getCurrentPlayTime();
        assertTrue(currentPlayTime  >  0);
    }

    @Test
    public void testSetCurrentPlayTime() throws Throwable {
        final ValueAnimator anim = ValueAnimator.ofFloat(0, 100).setDuration(mDuration);
        final ValueAnimator delayedAnim = ValueAnimator.ofFloat(0, 100).setDuration(mDuration);
        delayedAnim.setStartDelay(mDuration);
        final long proposedCurrentPlayTime = mDuration / 2;
        mActivityRule.runOnUiThread(() -> {
            anim.setCurrentPlayTime(mDuration / 2);
            long currentPlayTime = anim.getCurrentPlayTime();
            float currentFraction = anim.getAnimatedFraction();
            float currentValue = (Float) anim.getAnimatedValue();
            assertEquals(proposedCurrentPlayTime, currentPlayTime);
            assertEquals(.5f, currentFraction, EPSILON);
            assertEquals(50, currentValue, EPSILON);

            delayedAnim.setCurrentPlayTime(mDuration / 2);
            currentPlayTime = delayedAnim.getCurrentPlayTime();
            currentFraction = delayedAnim.getAnimatedFraction();
            currentValue = (Float) delayedAnim.getAnimatedValue();
            assertEquals(proposedCurrentPlayTime, currentPlayTime);
            assertEquals(.5f, currentFraction, EPSILON);
            assertEquals(50, currentValue, EPSILON);
        });
        // Now make sure that it's still true a little later, to test that we're
        // getting a result based on the seek time, not the wall clock time
        SystemClock.sleep(100);
        long currentPlayTime = anim.getCurrentPlayTime();
        float currentFraction = anim.getAnimatedFraction();
        float currentValue = (Float) anim.getAnimatedValue();
        assertEquals(proposedCurrentPlayTime, currentPlayTime);
        assertEquals(.5f, currentFraction, EPSILON);
        assertEquals(50, currentValue, EPSILON);

        currentPlayTime = delayedAnim.getCurrentPlayTime();
        currentFraction = delayedAnim.getAnimatedFraction();
        currentValue = (Float) delayedAnim.getAnimatedValue();
        assertEquals(proposedCurrentPlayTime, currentPlayTime);
        assertEquals(.5f, currentFraction, EPSILON);
        assertEquals(50, currentValue, EPSILON);

        // Finally, start() the delayed animation and check that the play time was
        // not affected by playing during the delay
        mActivityRule.runOnUiThread(() -> {
            delayedAnim.start();
            assertEquals(proposedCurrentPlayTime, delayedAnim.getCurrentPlayTime());
            assertEquals(.5f, delayedAnim.getAnimatedFraction(), EPSILON);
            assertEquals(50, (float) delayedAnim.getAnimatedValue(), EPSILON);
        });

        SystemClock.sleep(100);
        currentPlayTime = delayedAnim.getCurrentPlayTime();
        currentFraction = delayedAnim.getAnimatedFraction();
        currentValue = (Float) delayedAnim.getAnimatedValue();
        assertTrue(currentPlayTime > proposedCurrentPlayTime);
        assertTrue(currentFraction > 0.5f);
        assertTrue(currentValue > 50);

        mActivityRule.runOnUiThread(delayedAnim::cancel);
    }

    @Test
    public void testPauseListener() throws Throwable {
        // Adds two pause listeners to the animator, and remove one after the animator is paused.
        Animator.AnimatorPauseListener l1 = mock(Animator.AnimatorPauseListener.class);
        Animator.AnimatorPauseListener l2 = mock(Animator.AnimatorPauseListener.class);
        ValueAnimator a1 = ValueAnimator.ofFloat(0, 1f);
        a1.addPauseListener(l1);
        a1.addPauseListener(l2);
        mActivityRule.runOnUiThread(() -> {
            a1.start();
            a1.pause();
            verify(l1, times(1)).onAnimationPause(a1);
            verify(l2, times(1)).onAnimationPause(a1);
            a1.removePauseListener(l2);
            a1.resume();
        });

        // Check that the pause listener that is removed doesn't have resume called.
        verify(l1, times(1)).onAnimationResume(a1);
        verify(l2, times(0)).onAnimationResume(a1);
    }

    @Test
    public void testSetCurrentPlayTimeAfterStart() throws Throwable {
        // This test sets current play time right after start() is called on a non-delayed animation
        final long duration = 100;
        final float seekFraction = 0.2f;
        final CountDownLatch frameUpdateLatch = new CountDownLatch(1);

        final AnimatorSetTest.MyListener myListener = new AnimatorSetTest.MyListener();
        final ValueAnimator anim  = ValueAnimator.ofFloat(0, 1).setDuration(duration);
        anim.setInterpolator(null);
        final Animator.AnimatorListener listener = mock(Animator.AnimatorListener.class);
        anim.addListener(listener);
        anim.addListener(myListener);
        mActivityRule.runOnUiThread(() -> {
            anim.start();
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                float fractionOnFirstFrame = -1f;

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (fractionOnFirstFrame < 0) {
                        // First frame:
                        fractionOnFirstFrame = animation.getAnimatedFraction();
                        assertEquals(seekFraction, fractionOnFirstFrame, EPSILON);
                        frameUpdateLatch.countDown();
                    } else {
                        assertTrue(animation.getAnimatedFraction() >= fractionOnFirstFrame);
                    }
                }
            });
            long currentPlayTime = (long) (seekFraction * (float) duration);
            anim.setCurrentPlayTime(currentPlayTime);
        });
        assertTrue(frameUpdateLatch.await(100, TimeUnit.MILLISECONDS));
        verify(listener, within(200)).onAnimationEnd(anim, false);
        // Also make sure the onAnimationEnd(anim) is called.
        assertTrue(myListener.mEndIsCalled);
    }

    @Test
    public void testSetCurrentFraction() throws Throwable {
        final ValueAnimator anim = ValueAnimator.ofFloat(0, 100).setDuration(mDuration);
        final long proposedCurrentPlayTime = mDuration / 2;
        mActivityRule.runOnUiThread(() -> {
            anim.setCurrentFraction(.5f);
            long currentPlayTime = anim.getCurrentPlayTime();
            float currentFraction = anim.getAnimatedFraction();
            float currentValue = (Float) anim.getAnimatedValue();
            assertEquals(proposedCurrentPlayTime, currentPlayTime);
            assertEquals(.5f, currentFraction, EPSILON);
            assertEquals(50, currentValue, EPSILON);
        });
        // Now make sure that it's still true a little later, to test that we're
        // getting a result based on the seek time, not the wall clock time
        SystemClock.sleep(100);
        long currentPlayTime = anim.getCurrentPlayTime();
        float currentFraction = anim.getAnimatedFraction();
        float currentValue = (Float) anim.getAnimatedValue();
        assertEquals(proposedCurrentPlayTime, currentPlayTime);
        assertEquals(.5f, currentFraction, EPSILON);
        assertEquals(50, currentValue, EPSILON);
    }

    @UiThreadTest
    @Test
    public void testReverseRightAfterStart() {
        // Reverse() right after start() should trigger immediate end() at fraction 0.
        final ValueAnimator anim = ValueAnimator.ofFloat(0, 100).setDuration(mDuration);
        anim.start();
        assertTrue(anim.isStarted());
        anim.reverse();
        assertFalse(anim.isStarted());
        assertEquals(0f, anim.getAnimatedFraction(), 0.0f);
    }

    @Test
    public void testGetFrameDelay() throws Throwable {
        final long frameDelay = 10;
        mActivityRule.runOnUiThread(() -> mValueAnimator.setFrameDelay(frameDelay));
        startAnimation(mValueAnimator);
        SystemClock.sleep(100);
        mActivityRule.runOnUiThread(() -> {
            long actualFrameDelay = mValueAnimator.getFrameDelay();
            assertEquals(frameDelay, actualFrameDelay);
        });
    }

    @Test
    public void testUpdateListeners() throws Throwable {
        final AnimatorSetTest.MyListener myListener = new AnimatorSetTest.MyListener();
        ValueAnimator.AnimatorUpdateListener l1 = mock(ValueAnimator.AnimatorUpdateListener.class);
        ValueAnimator.AnimatorUpdateListener l2 = mock(ValueAnimator.AnimatorUpdateListener.class);
        ValueAnimator.AnimatorUpdateListener l3 = mock(ValueAnimator.AnimatorUpdateListener.class);
        ValueAnimator.AnimatorUpdateListener l4 = mock(ValueAnimator.AnimatorUpdateListener.class);

        AnimatorListenerAdapter listener = mock(AnimatorListenerAdapter.class);

        ValueAnimator a1 = ValueAnimator.ofFloat(0, 1f);
        a1.setDuration(50);
        a1.addUpdateListener(l1);
        a1.addUpdateListener(l2);
        a1.removeAllUpdateListeners();

        a1.addUpdateListener(l3);
        a1.addUpdateListener(l4);
        a1.removeUpdateListener(l3);

        a1.addListener(listener);
        a1.addListener(myListener);

        mActivityRule.runOnUiThread(() -> {
            a1.start();
        });

        // Wait for the anim to finish.
        verify(listener, within(200)).onAnimationEnd(a1, false);
        // Also make sure the onAnimationEnd(anim) is called.
        assertTrue(myListener.mEndIsCalled);

        verify(l1, times(0)).onAnimationUpdate(a1);
        verify(l2, times(0)).onAnimationUpdate(a1);
        verify(l3, times(0)).onAnimationUpdate(a1);
        verify(l4, atLeast(1)).onAnimationUpdate(a1);
    }

    @Test
    public void testValuesSetterAndGetter() throws Throwable {

        final AnimatorSetTest.MyListener myListener = new AnimatorSetTest.MyListener();
        ValueAnimator a2 = ValueAnimator.ofPropertyValuesHolder();
        PropertyValuesHolder p1 = PropertyValuesHolder.ofFloat("scaleX", 0f, 1f);
        PropertyValuesHolder p2 = PropertyValuesHolder.ofFloat("scaleY", 1f, 2f);
        a2.setValues(p1, p2);
        PropertyValuesHolder[] holders = a2.getValues();
        assertEquals(2, holders.length);

        // Use the PropertyValueHolders returned from the getter to initialize the animator, in
        // order to test the getter.
        ValueAnimator a1 = ValueAnimator.ofPropertyValuesHolder(holders);
        a1.setDuration(50);
        a1.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float scaleX = (Float) animation.getAnimatedValue("scaleX");
                float scaleY = (Float) animation.getAnimatedValue("scaleY");
                assertTrue(scaleX >= 0f && scaleX <= 1f);
                assertTrue(scaleY >= 1f && scaleY <= 2f);
            }
        });
        AnimatorListenerAdapter l1 = mock(AnimatorListenerAdapter.class);
        a1.addListener(l1);
        a1.addListener(myListener);

        mActivityRule.runOnUiThread(() -> {
            a1.start();
        });

        verify(l1, within(200)).onAnimationEnd(a1, false);
        // Also make sure the onAnimationEnd(anim) is called.
        assertTrue(myListener.mEndIsCalled);
    }

    @Test
    public void testSetObjectValues() throws Throwable {
        TypeEvaluator<PointF> eval = new TypeEvaluator<PointF>() {
            PointF tmpValue = new PointF();
            @Override
            public PointF evaluate(float fraction, PointF startValue, PointF endValue) {
                tmpValue.x = fraction * startValue.x + (1f - fraction) * endValue.x;
                tmpValue.y = fraction * startValue.y + (1f - fraction) * endValue.y;
                return tmpValue;
            }
        };

        final AnimatorSetTest.MyListener myListener = new AnimatorSetTest.MyListener();
        ValueAnimator a1 = new ValueAnimator();
        a1.setDuration(50);
        a1.setObjectValues(new PointF(0, 0), new PointF(1, 1));
        a1.setEvaluator(eval);
        a1.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                PointF point = (PointF) animation.getAnimatedValue();
                assertTrue(point.x >= 0f && point.x <= 1f);
                assertTrue(point.y >= 0f && point.y <= 1f);
            }
        });
        AnimatorListenerAdapter l1 = mock(AnimatorListenerAdapter.class);
        a1.addListener(l1);
        a1.addListener(myListener);
        mActivityRule.runOnUiThread(() -> {
            a1.start();
        });

        verify(l1, within(200)).onAnimationEnd(a1, false);
        // Also make sure the onAnimationEnd(anim) is called.
        assertTrue(myListener.mEndIsCalled);
    }

    @Test
    public void testSetInterpolator() throws Throwable {
        AccelerateInterpolator interpolator = new AccelerateInterpolator();
        ValueAnimator mValueAnimator = mActivity.createAnimatorWithInterpolator(interpolator);
        startAnimation(mValueAnimator);
        assertTrue(interpolator.equals(mValueAnimator.getInterpolator()));
    }

    @Test
    public void testCancel() throws Throwable {
        startAnimation(mValueAnimator);
        SystemClock.sleep(100);
        cancelAnimation(mValueAnimator);
        assertFalse(mValueAnimator.isRunning());
    }

    @Test
    public void testEnd() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        ObjectAnimator objAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        startAnimation(objAnimator);
        SystemClock.sleep(100);
        endAnimation(objAnimator);
        float y = mActivity.view.newBall.getY();
        assertEquals(y, endY, 0.0f);
    }

    @Test
    public void testGetAnimatedFraction() throws Throwable {
        ValueAnimator objAnimator = getAnimator();
        startAnimation(objAnimator);
        assertNotNull(objAnimator);
        float[] fractions = getValue(objAnimator, 10, "getAnimatedFraction()", 200l, null);
        for(int j = 0; j < 9; j++){
            assertTrue(fractions[j] >= 0.0);
            assertTrue(fractions[j] <= 1.0);
            assertTrue(errorMessage(fractions), fractions[j + 1] >= fractions[j]);
        }
    }

    @Test
    public void testGetAnimatedValue() throws Throwable {
        ValueAnimator objAnimator = getAnimator();
        startAnimation(objAnimator);
        assertNotNull(objAnimator);
        float[] animatedValues = getValue(objAnimator, 10, "getAnimatedValue()", 200l, null);

        for(int j = 0; j < 9; j++){
            assertTrue(errorMessage(animatedValues), animatedValues[j + 1] >= animatedValues[j]);
        }
    }

    @Test
    public void testGetAnimatedValue_PropertyName() throws Throwable {
        String property = "y";

        ValueAnimator objAnimator = getAnimator();
        startAnimation(objAnimator);
        assertNotNull(objAnimator);
        float[] animatedValues = getValue(objAnimator, 10, "getAnimatedValue(property)", 200l,
            property);
        for(int j = 0; j < 9; j++){
            assertTrue(errorMessage(animatedValues), animatedValues[j + 1] >= animatedValues[j]);
        }
    }

    @Test
    public void testOfFloat() throws Throwable {
        float start = 0.0f;
        float end = 1.0f;
        float[] values = {start, end};
        final ValueAnimator valueAnimatorLocal = ValueAnimator.ofFloat(values);
        valueAnimatorLocal.setDuration(mDuration);
        valueAnimatorLocal.setRepeatCount(ValueAnimator.INFINITE);
        valueAnimatorLocal.setInterpolator(new AccelerateInterpolator());
        valueAnimatorLocal.setRepeatMode(ValueAnimator.RESTART);

        mActivityRule.runOnUiThread(valueAnimatorLocal::start);
        SystemClock.sleep(100);
        boolean isRunning = valueAnimatorLocal.isRunning();
        assertTrue(isRunning);

        Float animatedValue = (Float) valueAnimatorLocal.getAnimatedValue();
        assertTrue(animatedValue >= start);
        assertTrue(animatedValue <= end);
    }

    @Test
    public void testOfInt() throws Throwable {
        int start = 0;
        int end = 10;
        int[] values = {start, end};
        final ValueAnimator valueAnimatorLocal = ValueAnimator.ofInt(values);
        valueAnimatorLocal.setDuration(mDuration);
        valueAnimatorLocal.setRepeatCount(ValueAnimator.INFINITE);
        valueAnimatorLocal.setInterpolator(new AccelerateInterpolator());
        valueAnimatorLocal.setRepeatMode(ValueAnimator.RESTART);

        mActivityRule.runOnUiThread(valueAnimatorLocal::start);
        SystemClock.sleep(100);
        boolean isRunning = valueAnimatorLocal.isRunning();
        assertTrue(isRunning);

        Integer animatedValue = (Integer) valueAnimatorLocal.getAnimatedValue();
        assertTrue(animatedValue >= start);
        assertTrue(animatedValue <= end);
    }

    @Test
    public void testOfArgb() throws Throwable {
        int start = 0xffff0000;
        int end = 0xff0000ff;
        int[] values = {start, end};
        int startRed = Color.red(start);
        int startBlue = Color.blue(start);
        int endRed = Color.red(end);
        int endBlue = Color.blue(end);
        final ValueAnimator valueAnimatorLocal = ValueAnimator.ofArgb(values);
        valueAnimatorLocal.setDuration(mDuration);

        final CountDownLatch latch = new CountDownLatch(1);
        valueAnimatorLocal.addUpdateListener((ValueAnimator animation) -> {
            if (animation.getAnimatedFraction() > .05f) {
                latch.countDown();
            }
        });

        mActivityRule.runOnUiThread(valueAnimatorLocal::start);
        boolean isRunning = valueAnimatorLocal.isRunning();
        assertTrue(isRunning);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));

        Integer animatedValue = (Integer) valueAnimatorLocal.getAnimatedValue();
        int alpha = Color.alpha(animatedValue);
        int red = Color.red(animatedValue);
        int green = Color.green(animatedValue);
        int blue = Color.blue(animatedValue);
        assertTrue(red < startRed);
        assertTrue(red > endRed);
        assertTrue(blue > startBlue);
        assertTrue(blue < endBlue);
        assertEquals(255, alpha);
        assertEquals(0, green);

        mActivityRule.runOnUiThread(valueAnimatorLocal::cancel);
    }

    @Test
    public void testNoDelayOnSeekAnimation() throws Throwable {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setInterpolator(new LinearInterpolator());
        animator.setStartDelay(1000);
        animator.setDuration(300);
        animator.setCurrentPlayTime(150);
        final Animator.AnimatorListener watcher = mock(Animator.AnimatorListener.class);
        animator.addListener(watcher);
        mActivityRule.runOnUiThread(animator::start);
        verify(watcher, times(1)).onAnimationStart(animator, false);
        assertTrue(((Float)animator.getAnimatedValue()) >= 0.5f);
        assertTrue(animator.getAnimatedFraction() >= 0.5f);
        mActivityRule.runOnUiThread(animator::cancel);
    }

    @Test
    public void testNotifiesAfterEnd() throws Throwable {
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                assertTrue(animation.isStarted());
                assertTrue(animation.isRunning());
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                assertFalse(animation.isRunning());
                assertFalse(animation.isStarted());
                super.onAnimationEnd(animation);
            }
        });
        mActivityRule.runOnUiThread(() -> {
            animator.start();
            animator.end();
        });
    }

    @Test
    public void testAnimatorsEnabled() throws Throwable {
        float currentDurationScale = ValueAnimator.getDurationScale();
        try {
            testAnimatorsEnabledImpl(true);
            testAnimatorsEnabledImpl(false);
        } finally {
            // restore scale value to avoid messing up future tests
            ValueAnimator.setDurationScale(currentDurationScale);
        }
    }

    private void testAnimatorsEnabledImpl(boolean enabled) throws Throwable {
        final CountDownLatch endLatch = new CountDownLatch(1);
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });
        mActivityRule.runOnUiThread(() -> {
            animator.start();
        });

        float durationScale = enabled ? 1 : 0;
        ValueAnimator.setDurationScale(durationScale);

        if (enabled) {
            assertTrue("Animators not enabled with duration scale 1",
                    ValueAnimator.areAnimatorsEnabled());
            assertFalse("Animator ended too early when animators enabled = ",
                    endLatch.await(50, TimeUnit.MILLISECONDS));
        } else {
            assertFalse("Animators enabled with duration scale 0",
                    ValueAnimator.areAnimatorsEnabled());
            assertTrue("Animator did not end when animators enabled = ",
                    endLatch.await(50, TimeUnit.MILLISECONDS));
        }
        mActivityRule.runOnUiThread(() -> {
            animator.end();
        });
    }

    private ValueAnimator getAnimator() {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        ValueAnimator objAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        return objAnimator;
    }

    private float[] getValue(ValueAnimator animator, int n, String methodName,
            long sleepTime, String property) throws InterruptedException {
        float[] values = new float[n];
        for(int i = 0; i < n; i++){
            SystemClock.sleep(sleepTime);
            float value = 0.0f;
            if(methodName.equals("getAnimatedFraction()")) {
                value = animator.getAnimatedFraction();
            }else if(methodName.equals("getAnimatedValue()")) {
              value = ((Float)animator.getAnimatedValue()).floatValue();
            }else if(methodName.equals("getAnimatedValue(property)")) {
              value = ((Float)animator.getAnimatedValue(property)).floatValue();
            }
            values[i] = value;
        }
        return values;
    }

    private void startAnimation(final ValueAnimator animator) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startAnimation(animator));
    }

    private void endAnimation(final ValueAnimator animator) throws Throwable {
        mActivityRule.runOnUiThread(animator::end);
    }

    private void cancelAnimation(final ValueAnimator animator) throws Throwable {
        mActivityRule.runOnUiThread(animator::cancel);
    }

    private String errorMessage(float[] values) {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            message.append(values[i]).append(" ");
        }
        return message.toString();
    }
}
