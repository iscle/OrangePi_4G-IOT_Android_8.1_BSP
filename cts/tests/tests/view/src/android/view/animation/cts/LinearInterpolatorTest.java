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

package android.view.animation.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import android.view.cts.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link LinearInterpolator}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class LinearInterpolatorTest {
    private static final float ALPHA_DELTA = 0.001f;

    /** It is defined in R.anim.alpha */
    private static final long LINEAR_ALPHA_DURATION = 500;
    private static final long LINEAR_ALPHA_TIME_STEP = LINEAR_ALPHA_DURATION / 5;

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
        new LinearInterpolator();
        new LinearInterpolator(mActivity, null);
    }

    @Test
    public void testGetInterpolation() {
        LinearInterpolator interpolator = new LinearInterpolator();
        final float delta1 = interpolator.getInterpolation(0.1f)
                - interpolator.getInterpolation(0.0f);
        final float delta2 = interpolator.getInterpolation(0.2f)
                - interpolator.getInterpolation(0.1f);
        final float delta3 = interpolator.getInterpolation(0.3f)
                - interpolator.getInterpolation(0.2f);
        assertEquals(delta1, delta2, ALPHA_DELTA);
        assertEquals(delta2, delta3, ALPHA_DELTA);
    }

    @Test
    public void testLinearInterpolator() throws Throwable {
        final View animWindow = mActivity.findViewById(R.id.anim_window);
        final Animation anim = AnimationUtils.loadAnimation(mActivity, R.anim.alpha);
        assertEquals(LINEAR_ALPHA_DURATION, anim.getDuration());
        assertTrue(anim instanceof AlphaAnimation);

        Interpolator interpolator = new LinearInterpolator();
        anim.setInterpolator(interpolator);
        assertFalse(anim.hasStarted());

        AnimationTestUtils.assertRunAnimation(mInstrumentation, mActivityRule, animWindow, anim);

        Transformation transformation = new Transformation();
        final long startTime = anim.getStartTime();
        anim.getTransformation(startTime, transformation);
        final float alpha1 = transformation.getAlpha();
        assertEquals(0.0f, alpha1, ALPHA_DELTA);

        anim.getTransformation(startTime + LINEAR_ALPHA_TIME_STEP, transformation);
        final float alpha2 = transformation.getAlpha();

        anim.getTransformation(startTime + LINEAR_ALPHA_TIME_STEP * 2, transformation);
        final float alpha3 = transformation.getAlpha();

        anim.getTransformation(startTime + LINEAR_ALPHA_TIME_STEP * 3, transformation);
        final float alpha4 = transformation.getAlpha();

        anim.getTransformation(startTime + LINEAR_ALPHA_TIME_STEP * 4, transformation);
        final float alpha5 = transformation.getAlpha();

        anim.getTransformation(startTime + LINEAR_ALPHA_DURATION, transformation);
        final float alpha6 = transformation.getAlpha();
        assertEquals(1.0f, alpha6, ALPHA_DELTA);

        final float delta1 = alpha2 - alpha1;
        final float delta2 = alpha3 - alpha2;
        final float delta3 = alpha4 - alpha3;
        final float delta4 = alpha5 - alpha4;
        final float delta5 = alpha6 - alpha5;
        assertEquals(delta1, delta2, ALPHA_DELTA);
        assertEquals(delta2, delta3, ALPHA_DELTA);
        assertEquals(delta3, delta4, ALPHA_DELTA);
        assertEquals(delta4, delta5, ALPHA_DELTA);
    }
}
