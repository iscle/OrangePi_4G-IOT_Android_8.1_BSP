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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.view.animation.cts.AnimationTestUtils;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link View}.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class View_AnimationTest {

    private static final int TIME_OUT = 5000;
    private static final int DURATION = 2000;

    private Instrumentation mInstrumentation;
    private Activity mActivity;

    private TranslateAnimation mAnimation;

    @Rule
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mAnimation = new TranslateAnimation(0.0f, 10.0f, 0.0f, 10.0f);
        mAnimation.setDuration(DURATION);
    }

    @Test
    public void testAnimation() throws Throwable {
        final View view = mActivity.findViewById(R.id.mock_view);
        // set null animation
        view.setAnimation(null);
        assertNull(view.getAnimation());

        view.setAnimation(mAnimation);
        mActivityRule.runOnUiThread(view::invalidate);

        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, view, mAnimation,
                TIME_OUT);
    }

    @Test(expected=NullPointerException.class)
    public void testStartAnimationNull() {
        final View view = mActivity.findViewById(R.id.mock_view);
        view.startAnimation(null);
    }

    @Test
    public void testStartAnimation() throws Throwable {
        final View view = mActivity.findViewById(R.id.mock_view);

        mActivityRule.runOnUiThread(() -> view.startAnimation(mAnimation));

        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, view, mAnimation,
                TIME_OUT);
    }

    @Test
    public void testClearBeforeAnimation() throws Throwable {
        final View view = mActivity.findViewById(R.id.mock_view);
        assertFalse(mAnimation.hasStarted());

        view.setAnimation(mAnimation);

        assertSame(mAnimation, view.getAnimation());

        mActivityRule.runOnUiThread(() -> {
            view.clearAnimation();
            view.invalidate();
        });

        SystemClock.sleep(TIME_OUT);
        assertFalse(mAnimation.hasStarted());
        assertNull(view.getAnimation());
    }

    @Test
    public void testClearDuringAnimation() throws Throwable {
        final View view = mActivity.findViewById(R.id.mock_view);
        mActivityRule.runOnUiThread(() -> {
            view.startAnimation(mAnimation);
            assertNotNull(view.getAnimation());
        });

        PollingCheck.waitFor(TIME_OUT, mAnimation::hasStarted);

        mActivityRule.runOnUiThread(view::clearAnimation);
        SystemClock.sleep(TIME_OUT);
        assertTrue(mAnimation.hasStarted());
        assertTrue(mAnimation.hasEnded());
        assertNull(view.getAnimation());
    }
}
