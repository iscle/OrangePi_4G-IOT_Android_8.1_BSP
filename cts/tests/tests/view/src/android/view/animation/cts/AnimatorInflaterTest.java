/*
* Copyright (C) 2014 The Android Open Source Project
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.cts.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AnimatorInflaterTest {
    private static final String TAG = "AnimatorInflaterTest";

    private Instrumentation mInstrumentation;
    private AnimationTestCtsActivity mActivity;
    private View mTestView;

    Set<Integer> identityHashes = new HashSet<>();

    @Rule
    public ActivityTestRule<AnimationTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(AnimationTestCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mTestView = mActivity.findViewById(R.id.anim_window);
    }

    private void assertUnique(Object object) {
        assertUnique(object, "");
    }

    private void assertUnique(Object object, String msg) {
        final int code = System.identityHashCode(object);
        assertTrue("object should be unique " + msg + ", obj:" + object, identityHashes.add(code));
    }

    @Test
    public void testLoadAnimatorWithDifferentInterpolators() throws Throwable {
        Animator anim1 = AnimatorInflater .loadAnimator(mActivity, R.anim.changing_test_animator);
        if (!rotate()) {
            return;//cancel test
        }
        Animator anim2 = AnimatorInflater .loadAnimator(mActivity, R.anim.changing_test_animator);
        assertNotSame(anim1, anim2);
        assertNotSame("interpolater is orientation dependent, should change",
                anim1.getInterpolator(), anim2.getInterpolator());
    }

    /**
     * Tests animators with dimension references.
     */
    @Test
    public void testLoadAnimator() throws Throwable {
        // to identify objects
        Animator anim1 = AnimatorInflater.loadAnimator(mActivity, R.anim.test_animator);
        Animator anim2 = AnimatorInflater.loadAnimator(mActivity, R.anim.test_animator);
        assertNotSame("a different animation should be returned", anim1, anim2);
        assertSame("interpolator should be shallow cloned", anim1.getInterpolator(),
                anim2.getInterpolator());
        for (int i = 0; i < 2; i++) {
            float targetX = mActivity.getResources()
                    .getDimension(R.dimen.test_animator_target_x);
            // y value changes in landscape orientation
            float targetY = mActivity.getResources()
                    .getDimension(R.dimen.test_animator_target_y);
            for (Animator anim : new Animator[]{anim1, anim2}) {
                assertTrue(anim instanceof AnimatorSet);
                assertUnique(anim);
                AnimatorSet set = (AnimatorSet) anim;
                assertEquals("should have 3 sub animations", 3, set.getChildAnimations().size());
                for (Animator subAnim : set.getChildAnimations()) {
                    assertUnique(subAnim);
                    assertTrue(subAnim instanceof ObjectAnimator);
                }
                final ObjectAnimator child1 = (ObjectAnimator) set.getChildAnimations().get(0);
                final ObjectAnimator child2 = (ObjectAnimator) set.getChildAnimations().get(1);
                final DummyObject dummyObject = new DummyObject();
                mActivityRule.runOnUiThread(() -> {
                    for (ObjectAnimator animator : new ObjectAnimator[]{child1, child2}) {
                        animator.setTarget(dummyObject);
                        animator.setupStartValues();
                        animator.start();
                        animator.end();
                    }
                });
                assertEquals(targetX, dummyObject.x, 0.0f);
                assertEquals(targetY, dummyObject.y, 0.0f);
            }
            if (i == 0) {
                if (!rotate()) {
                    return;//cancel test
                }
            }
            anim1 = AnimatorInflater.loadAnimator(mActivity, R.anim.test_animator);
            anim2 = AnimatorInflater.loadAnimator(mActivity, R.anim.test_animator);

        }
    }

    private boolean rotate() throws Throwable {
        WindowManager mWindowManager = (WindowManager) mActivity
                .getSystemService(Context.WINDOW_SERVICE);
        Display display = mWindowManager.getDefaultDisplay();
        int orientation = mActivity.getResources().getConfiguration().orientation;

        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                mActivity.getClass().getName(), null, false);
        mInstrumentation.addMonitor(monitor);
        int nextRotation = 0;
        switch (display.getRotation()) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                nextRotation = UiAutomation.ROTATION_FREEZE_90;
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                nextRotation = UiAutomation.ROTATION_FREEZE_0;
                break;
            default:
                Log.e(TAG, "Cannot get rotation, test is canceled");
                return false;
        }
        boolean rotated = mInstrumentation.getUiAutomation().setRotation(nextRotation);
        Thread.sleep(500);
        if (!rotated) {
            Log.e(TAG, "Rotation failed, test is canceled");
        }
        mInstrumentation.waitForIdleSync();
        if (!mActivity.waitUntilVisible()) {
            Log.e(TAG, "Activity failed to complete rotation, canceling test");
            return false;
        }
        if (mActivity.getWindowManager().getDefaultDisplay().getRotation() != nextRotation) {
            Log.e(TAG, "New activity orientation does not match. Canceling test");
            return false;
        }
        if (mActivity.getResources().getConfiguration().orientation == orientation) {
            Log.e(TAG, "Screen orientation didn't change, test is canceled");
            return false;
        }
        return true;
    }

    /**
     * Simple state list animator test that checks for cloning
     */
    @Test
    public void testLoadStateListAnimator() {
        StateListAnimator sla1 = AnimatorInflater.loadStateListAnimator(mActivity,
                R.anim.test_state_list_animator);
        StateListAnimator sla2 = AnimatorInflater.loadStateListAnimator(mActivity,
                R.anim.test_state_list_animator);
        assertUnique(sla1);
        assertUnique(sla2);
    }

    /**
     * Tests a state list animator which has an @anim reference that has different xmls per
     * orientation
     */
    @Test
    public void testLoadStateListAnimatorWithChangingResetState() throws Throwable {
        loadStateListAnimatorWithChangingResetStateTest();
        if (!rotate()) {
            return;//cancel test
        }

        loadStateListAnimatorWithChangingResetStateTest();
    }

    private void loadStateListAnimatorWithChangingResetStateTest() throws Throwable {
        final StateListAnimator sla = AnimatorInflater.loadStateListAnimator(mActivity,
                R.anim.test_state_list_animator_2);
        mActivityRule.runOnUiThread(() -> {
            mTestView.setStateListAnimator(sla);
            mTestView.jumpDrawablesToCurrentState();
        });
        float resetValue = mActivity.getResources().getDimension(R.dimen.reset_state_value);
        mInstrumentation.waitForIdleSync();
        assertEquals(resetValue, mTestView.getX(), 0.0f);
        assertEquals(resetValue, mTestView.getY(), 0.0f);
        assertEquals(resetValue, mTestView.getZ(), 0.0f);
    }

    /**
     * Tests a state list animator which has different xml descriptions per orientation.
     */
    @Test
    public void testLoadChangingStateListAnimator() throws Throwable {
        loadChangingStateListAnimatorTest();
        if (!rotate()) {
            return;//cancel test
        }
        loadChangingStateListAnimatorTest();
    }

    private void loadChangingStateListAnimatorTest() throws Throwable {
        final StateListAnimator sla = AnimatorInflater.loadStateListAnimator(mActivity,
                R.anim.changing_state_list_animator);
        mActivityRule.runOnUiThread(() -> {
            mTestView.setStateListAnimator(sla);
            mTestView.jumpDrawablesToCurrentState();
        });
        float targetValue = mActivity.getResources()
                .getDimension(R.dimen.changing_state_list_anim_target_x_value);
        mInstrumentation.waitForIdleSync();
        assertEquals(targetValue, mTestView.getX(), 0.0f);
    }

    /**
     * Tests that makes sure that reloaded animator is not affected by previous changes
     */
    @Test
    public void testReloadedAnimatorIsNotModified() throws Throwable {
        final Animator anim1 = AnimatorInflater.loadAnimator(mActivity, R.anim.test_animator);
        final CountDownLatch mStarted = new CountDownLatch(1);
        final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mStarted.countDown();
            }
        };
        mActivityRule.runOnUiThread(() -> {
            anim1.setTarget(mTestView);
            anim1.addListener(listener);
            anim1.start();
        });
        Animator anim2 = AnimatorInflater.loadAnimator(mActivity, R.anim.test_animator);
        assertTrue(anim1.isStarted());
        assertFalse(anim2.isStarted());
        assertFalse("anim2 should not include the listener",
                anim2.getListeners() != null && anim2.getListeners().contains(listener));
        assertTrue("animator should start", mStarted.await(10, TimeUnit.SECONDS));
        assertFalse(anim2.isRunning());

    }

    class DummyObject {

        float x;
        float y;

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }
    }
}

