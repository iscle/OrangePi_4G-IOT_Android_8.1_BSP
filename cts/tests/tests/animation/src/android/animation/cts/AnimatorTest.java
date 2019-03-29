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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.animation.AccelerateInterpolator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AnimatorTest {
    private AnimationActivity mActivity;
    private Animator mAnimator;
    private long mDuration = 1000;

    @Rule
    public ActivityTestRule<AnimationActivity> mActivityRule =
            new ActivityTestRule<>(AnimationActivity.class);

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        mActivity = mActivityRule.getActivity();
        mAnimator = mActivity.createAnimatorWithDuration(mDuration);
    }

    @Test
    public void testConstructor() {
        mAnimator = new ValueAnimator();
        assertNotNull(mAnimator);
    }

    @Test
    public void testClone() {
        Animator animatorClone = mAnimator.clone();
        assertEquals(mAnimator.getDuration(), animatorClone.getDuration());
    }

    @Test
    public void testStartDelay() {
        long startDelay = 1000;
        mAnimator.setStartDelay(startDelay);
        assertEquals(startDelay, mAnimator.getStartDelay());
    }

    @UiThreadTest
    @Test
    public void testStart() throws Exception {
        mAnimator.start();
        assertTrue(mAnimator.isRunning());
        assertTrue(mAnimator.isStarted());
    }

    @Test
    public void testGetDuration() throws Throwable {
        final long duration = 2000;
        Animator animatorLocal = mActivity.createAnimatorWithDuration(duration);
        startAnimation(animatorLocal);
        assertEquals(duration, animatorLocal.getDuration());
    }

    @Test
    public void testIsRunning() throws Throwable {
        assertFalse(mAnimator.isRunning());
        startAnimation(mAnimator);
        assertTrue(mAnimator.isRunning());
    }

    @Test
    public void testIsStarted() throws Throwable {
        assertFalse(mAnimator.isRunning());
        assertFalse(mAnimator.isStarted());
        long startDelay = 10000;
        mAnimator.setStartDelay(startDelay);
        startAnimation(mAnimator);
        assertFalse(mAnimator.isRunning());
        assertTrue(mAnimator.isStarted());
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
        startAnimation(mAnimator);
        SystemClock.sleep(100);
        mActivityRule.runOnUiThread(mAnimator::cancel);
        assertFalse(mAnimator.isRunning());
    }

    @Test
    public void testEnd() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        Animator animator = ObjectAnimator.ofFloat(object, property, startY, endY);
        animator.setDuration(mDuration);
        ((ObjectAnimator)animator).setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateInterpolator());
        ((ObjectAnimator)animator).setRepeatMode(ValueAnimator.REVERSE);
        startAnimation(animator);
        SystemClock.sleep(100);
        endAnimation(animator);
        float y = mActivity.view.newBall.getY();
        assertEquals(y, endY, 0.0f);
    }

    @Test
    public void testSetListener() throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        List<Animator.AnimatorListener> listListeners = mAnimator.getListeners();
        assertNull(listListeners);
        MyListener listener = new MyListener();
        assertFalse(listener.mStart);
        assertFalse(listener.mEnd);
        assertEquals(listener.mRepeat, 0);
        mAnimator.addListener(listener);
        mAnimator.setDuration(100l);
        startAnimation(mAnimator);
        SystemClock.sleep(200);

        assertTrue(listener.mStart);
        assertFalse(listener.mEnd);
        assertTrue(listener.mRepeat >= 0);

        mActivityRule.runOnUiThread(mAnimator::cancel);
        instrumentation.waitForIdleSync();
        assertTrue(listener.mCancel);

        mActivityRule.runOnUiThread(mAnimator::end);
        instrumentation.waitForIdleSync();
        assertTrue(listener.mEnd);
    }

    @Test
    public void testRemoveListener() throws Throwable {
        List<Animator.AnimatorListener> listListenersOne = mAnimator.getListeners();
        assertNull(listListenersOne);
        MyListener listener = new MyListener();
        mAnimator.addListener(listener);

        List<Animator.AnimatorListener> listListenersTwo = mAnimator.getListeners();
        assertEquals(listListenersTwo.size(), 1);
        mAnimator.removeListener(listener);

        List<Animator.AnimatorListener> listListenersThree = mAnimator.getListeners();
        assertNull(listListenersThree);
    }

    @Test
    public void testRemoveAllListenerers() throws Throwable {
        MyListener listener1 = new MyListener();
        MyListener listener2 = new MyListener();
        mAnimator.addListener(listener1);
        mAnimator.addListener(listener2);

        List<Animator.AnimatorListener> listListenersOne = mAnimator.getListeners();
        assertEquals(listListenersOne.size(), 2);
        mAnimator.removeAllListeners();

        List<Animator.AnimatorListener> listListenersTwo = mAnimator.getListeners();
        assertNull(listListenersTwo);
    }

    @Test
    public void testNullObjectAnimator() throws Throwable {
        Object object = mActivity.view.newBall;
        final ObjectAnimator animator = ObjectAnimator.ofFloat(object, "y", 0, 100);
        MyListener listener = new MyListener();
        animator.addListener(listener);
        mActivity.view.newBall.setY(0);
        startAnimation(animator);
        int sleepCount = 0;
        while (mActivity.view.newBall.getY() == 0 && sleepCount++ < 50) {
            SystemClock.sleep(1);
        }
        assertNotSame(0, mActivity.view.newBall.getY());
        mActivityRule.runOnUiThread(() -> animator.setTarget(null));
        assertTrue(listener.mCancel);
    }

    class MyListener implements Animator.AnimatorListener{
        boolean mStart = false;
        boolean mEnd = false;
        boolean mCancel = false;
        int mRepeat = 0;

        public void onAnimationCancel(Animator animation) {
            mCancel = true;
        }

        public void onAnimationEnd(Animator animation) {
            mEnd = true;
        }

        public void onAnimationRepeat(Animator animation) {
            mRepeat++;
        }

        public void onAnimationStart(Animator animation) {
            mStart = true;
        }
    }
    private void startAnimation(final Animator animator) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startAnimation(animator));
    }

    private void endAnimation(final Animator animator) throws Throwable {
        mActivityRule.runOnUiThread(animator::end);
    }
}

