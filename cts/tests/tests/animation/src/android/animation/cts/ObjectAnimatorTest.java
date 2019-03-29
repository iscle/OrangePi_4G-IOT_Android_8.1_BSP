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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TypeConverter;
import android.animation.ValueAnimator;
import android.app.Instrumentation;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Property;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ObjectAnimatorTest {
    private static final float LINE1_START = -32f;
    private static final float LINE1_END = -2f;
    private static final float LINE1_Y = 0f;
    private static final float LINE2_START = 2f;
    private static final float LINE2_END = 12f;
    private static final float QUADRATIC_CTRL_PT1_X = 0f;
    private static final float QUADRATIC_CTRL_PT1_Y = 0f;
    private static final float QUADRATIC_CTRL_PT2_X = 50f;
    private static final float QUADRATIC_CTRL_PT2_Y = 20f;
    private static final float QUADRATIC_CTRL_PT3_X = 100f;
    private static final float QUADRATIC_CTRL_PT3_Y = 0f;
    private static final float EPSILON = .001f;

    private Instrumentation mInstrumentation;
    private AnimationActivity mActivity;
    private ObjectAnimator mObjectAnimator;
    private long mDuration = 1000;

    @Rule
    public ActivityTestRule<AnimationActivity> mActivityRule =
            new ActivityTestRule<>(AnimationActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.setInTouchMode(false);
        mActivity = mActivityRule.getActivity();
        mObjectAnimator = (ObjectAnimator) mActivity.createAnimatorWithDuration(mDuration);
    }

    @Test
    public void testDuration() throws Throwable {
        final long duration = 2000;
        ObjectAnimator objectAnimatorLocal = (ObjectAnimator) mActivity.createAnimatorWithDuration(
            duration);
        startAnimation(objectAnimatorLocal);
        assertEquals(duration, objectAnimatorLocal.getDuration());
    }

    @Test
    public void testOfFloat() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        ObjectAnimator objAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
        assertTrue(objAnimator != null);

        ValueAnimator.AnimatorUpdateListener updateListener = ((animator) -> {
            float y = (Float) animator.getAnimatedValue();
            assertTrue(y >= startY);
            assertTrue(y <= endY);
        });
        ValueAnimator.AnimatorUpdateListener mockListener =
                mock(ValueAnimator.AnimatorUpdateListener.class);
        objAnimator.addUpdateListener(mockListener);
        objAnimator.addUpdateListener(updateListener);
        objAnimator.setDuration(200);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mActivityRule.runOnUiThread(objAnimator::start);
        assertTrue(objAnimator != null);

        verify(mockListener, timeout(2000).atLeast(20)).onAnimationUpdate(objAnimator);
        mActivityRule.runOnUiThread(objAnimator::cancel);
    }

    @Test
    public void testOfFloatBase() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        ObjectAnimator animator = ObjectAnimator.ofFloat(object, property, startY, endY);
        ObjectAnimator objAnimator = new ObjectAnimator();
        objAnimator.setTarget(object);
        objAnimator.setPropertyName(property);
        assertEquals(animator.getTarget(), objAnimator.getTarget());
        assertEquals(animator.getPropertyName(), objAnimator.getPropertyName());
    }

    @Test
    public void testOfInt() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "scrollY";

        final ObjectAnimator intAnimator = ObjectAnimator.ofInt(object, property, 200, 0);
        ValueAnimator.AnimatorUpdateListener updateListener = ((animator) -> {
            int value = (Integer) intAnimator.getAnimatedValue();
            assertTrue(value <= 200);
            assertTrue(value >= 0);
        });
        final Animator.AnimatorListener mockListener = mock(Animator.AnimatorListener.class);
        intAnimator.addListener(mockListener);

        intAnimator.addUpdateListener(updateListener);
        intAnimator.setDuration(200);
        intAnimator.setRepeatCount(1);
        intAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mActivityRule.runOnUiThread(intAnimator::start);

        verify(mockListener, timeout(400)).onAnimationRepeat(intAnimator);
        verify(mockListener, timeout(400)).onAnimationEnd(intAnimator, false);
    }

    @Test
    public void testOfObject() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "backgroundColor";
        int startColor = 0xFFFF8080;
        int endColor = 0xFF8080FF;

        Object[] values = {new Integer(startColor), new Integer(endColor)};
        ArgbEvaluator evaluator = new ArgbEvaluator();
        final ObjectAnimator colorAnimator = ObjectAnimator.ofObject(object, property,
                evaluator, values);
        ValueAnimator.AnimatorUpdateListener updateListener = ((animator) -> {
            int color = (Integer) colorAnimator.getAnimatedValue();
            // Check that channel is interpolated separately.
            assertEquals(0xFF, Color.alpha(color));
            assertTrue(Color.red(color) <= Color.red(startColor));
            assertTrue(Color.red(color) >= Color.red(endColor));
            assertEquals(0x80, Color.green(color));
            assertTrue(Color.blue(color) >= Color.blue(startColor));
            assertTrue(Color.blue(color) <= Color.blue(endColor));
        });
        final Animator.AnimatorListener mockListener = mock(Animator.AnimatorListener.class);
        colorAnimator.addListener(mockListener);

        colorAnimator.addUpdateListener(updateListener);
        colorAnimator.setDuration(200);
        colorAnimator.setRepeatCount(1);
        colorAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mActivityRule.runOnUiThread(colorAnimator::start);

        verify(mockListener, timeout(400)).onAnimationRepeat(colorAnimator);
        verify(mockListener, timeout(400)).onAnimationEnd(colorAnimator, false);
    }

    @Test
    public void testOfPropertyValuesHolder() throws Throwable {
        Object object = mActivity.view.newBall;
        String propertyName = "scrollX";
        int startValue = 200;
        int endValue = 0;
        int[] values = {startValue, endValue};
        PropertyValuesHolder propertyValuesHolder = PropertyValuesHolder.ofInt(propertyName, values);
        final ObjectAnimator intAnimator = ObjectAnimator.ofPropertyValuesHolder(object,
            propertyValuesHolder);

        ValueAnimator.AnimatorUpdateListener updateListener = ((animator) -> {
            int value = (Integer) intAnimator.getAnimatedValue();
            // Check that each channel is interpolated separately.
            assertTrue(value <= 200);
            assertTrue(value >= 0);
        });
        final Animator.AnimatorListener mockListener = mock(Animator.AnimatorListener.class);
        intAnimator.addListener(mockListener);

        intAnimator.addUpdateListener(updateListener);
        intAnimator.setDuration(200);
        mActivityRule.runOnUiThread(intAnimator::start);

        verify(mockListener, timeout(400)).onAnimationEnd(intAnimator, false);
    }

    @Test
    public void testOfArgb() throws Throwable {
        Object object = mActivity.view;
        String property = "backgroundColor";
        int start = 0xffff0000;
        int end = 0xff0000ff;
        int[] values = {start, end};
        int startRed = Color.red(start);
        int startBlue = Color.blue(start);
        int endRed = Color.red(end);
        int endBlue = Color.blue(end);

        ValueAnimator.AnimatorUpdateListener updateListener = ((anim) -> {
            Integer animatedValue = (Integer) anim.getAnimatedValue();
            int alpha = Color.alpha(animatedValue);
            int red = Color.red(animatedValue);
            int green = Color.green(animatedValue);
            int blue = Color.blue(animatedValue);
            assertTrue(red <= startRed);
            assertTrue(red >= endRed);
            assertTrue(blue >= startBlue);
            assertTrue(blue <= endBlue);
            assertEquals(255, alpha);
            assertEquals(0, green);

        });

        final Animator.AnimatorListener mockListener = mock(Animator.AnimatorListener.class);
        final ObjectAnimator animator = ObjectAnimator.ofArgb(object, property, start, end);
        animator.setDuration(200);
        animator.addListener(mockListener);
        animator.addUpdateListener(updateListener);

        mActivityRule.runOnUiThread(animator::start);
        assertTrue(animator.isRunning());

        verify(mockListener, timeout(400)).onAnimationEnd(animator, false);
    }

    @Test
    public void testNullObject() throws Throwable {
        final ObjectAnimator anim = ObjectAnimator.ofFloat(null, "dummyValue", 0f, 1f);
        anim.setDuration(300);
        final ValueAnimator.AnimatorUpdateListener updateListener =
                mock(ValueAnimator.AnimatorUpdateListener.class);
        anim.addUpdateListener(updateListener);
        final Animator.AnimatorListener listener = mock(Animator.AnimatorListener.class);
        anim.addListener(listener);

        mActivityRule.runOnUiThread(anim::start);
        verify(listener, within(500)).onAnimationEnd(anim, false);
        // Verify that null target ObjectAnimator didn't get canceled.
        verify(listener, times(0)).onAnimationCancel(anim);
        // Verify that the update listeners gets called a few times.
        verify(updateListener, atLeast(8)).onAnimationUpdate(anim);
    }

    @Test
    public void testGetPropertyName() throws Throwable {
        Object object = mActivity.view.newBall;
        String propertyName = "backgroundColor";
        int startColor = mActivity.view.RED;
        int endColor = mActivity.view.BLUE;
        Object[] values = {new Integer(startColor), new Integer(endColor)};
        ArgbEvaluator evaluator = new ArgbEvaluator();
        ObjectAnimator colorAnimator = ObjectAnimator.ofObject(object, propertyName,
                evaluator, values);
        String actualPropertyName = colorAnimator.getPropertyName();
        assertEquals(propertyName, actualPropertyName);
    }

    @Test
    public void testSetFloatValues() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        float[] values = {startY, endY};
        ObjectAnimator objAnimator = new ObjectAnimator();
        ValueAnimator.AnimatorUpdateListener updateListener = ((animator) -> {
            float y = (Float) animator.getAnimatedValue();
            assertTrue(y >= startY);
            assertTrue(y <= endY);
        });
        ValueAnimator.AnimatorUpdateListener mockListener =
                mock(ValueAnimator.AnimatorUpdateListener.class);
        objAnimator.addUpdateListener(mockListener);
        objAnimator.addUpdateListener(updateListener);
        objAnimator.setTarget(object);
        objAnimator.setPropertyName(property);
        objAnimator.setFloatValues(values);
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mActivityRule.runOnUiThread(objAnimator::start);

        verify(mockListener, timeout(2000).atLeast(20)).onAnimationUpdate(objAnimator);
        mActivityRule.runOnUiThread(objAnimator::cancel);
    }

    @Test
    public void testGetTarget() throws Throwable {
        Object object = mActivity.view.newBall;
        String propertyName = "backgroundColor";
        int startColor = mActivity.view.RED;
        int endColor = mActivity.view.BLUE;
        Object[] values = {new Integer(startColor), new Integer(endColor)};
        ArgbEvaluator evaluator = new ArgbEvaluator();
        ObjectAnimator colorAnimator = ObjectAnimator.ofObject(object, propertyName,
                evaluator, values);
        Object target = colorAnimator.getTarget();
        assertEquals(object, target);
    }

    @Test
    public void testClone() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        Interpolator interpolator = new AccelerateInterpolator();
        ObjectAnimator objAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(interpolator);
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator cloneAnimator = objAnimator.clone();

        assertEquals(mDuration, cloneAnimator.getDuration());
        assertEquals(ValueAnimator.INFINITE, cloneAnimator.getRepeatCount());
        assertEquals(ValueAnimator.REVERSE, cloneAnimator.getRepeatMode());
        assertEquals(object, cloneAnimator.getTarget());
        assertEquals(property, cloneAnimator.getPropertyName());
        assertEquals(interpolator, cloneAnimator.getInterpolator());
    }

    @Test
    public void testOfFloat_Path() throws Throwable {
        // Test for ObjectAnimator.ofFloat(Object, String, String, Path)
        // Create a path that contains two disconnected line segments. Check that the animated
        // property x and property y always stay on the line segments.
        Path path = new Path();
        path.moveTo(LINE1_START, LINE1_Y);
        path.lineTo(LINE1_END, LINE1_Y);
        path.moveTo(LINE2_START, LINE2_START);
        path.lineTo(LINE2_END, LINE2_END);
        final double totalLength = (LINE1_END - LINE1_START) + Math.sqrt(
                (LINE2_END - LINE2_START) * (LINE2_END - LINE2_START) +
                (LINE2_END - LINE2_START) * (LINE2_END - LINE2_START));
        final double firstSegEndFraction = (LINE1_END - LINE1_START) / totalLength;
        final float delta = 0.01f;

        Object target = new Object() {
            public void setX(float x) {
            }

            public void setY(float y) {
            }
        };

        final ObjectAnimator anim = ObjectAnimator.ofFloat(target, "x", "y", path);
        anim.setDuration(200);
        // Linear interpolator
        anim.setInterpolator(null);
        anim.addUpdateListener((ValueAnimator animation) -> {
            float fraction = animation.getAnimatedFraction();
            float x = (Float) animation.getAnimatedValue("x");
            float y = (Float) animation.getAnimatedValue("y");

            // Check that the point is on the path.
            if (x <= 0) {
                // First line segment is a horizontal line.
                assertTrue(x >= LINE1_START);
                assertTrue(x <= LINE1_END);
                assertEquals(LINE1_Y, y, 0.0f);

                // Check that the time animation stays on the first segment is proportional to
                // the length of the first line segment.
                assertTrue(fraction < firstSegEndFraction + delta);
            } else {
                assertTrue(x >= LINE2_START);
                assertTrue(x <= LINE2_END);
                assertEquals(x, y, 0.0f);

                // Check that the time animation stays on the second segment is proportional to
                // the length of the second line segment.
                assertTrue(fraction > firstSegEndFraction - delta);
            }
        });
        final Animator.AnimatorListener listener = mock(Animator.AnimatorListener.class);
        anim.addListener(listener);
        mActivityRule.runOnUiThread(anim::start);
        verify(listener, within(400)).onAnimationEnd(anim, false);
    }

    @Test
    public void testOfInt_Path() throws Throwable {
        // Test for ObjectAnimator.ofInt(Object, String, String, Path)
        // Create a path that contains two disconnected line segments. Check that the animated
        // property x and property y always stay on the line segments.
        Path path = new Path();
        path.moveTo(LINE1_START, -LINE1_START);
        path.lineTo(LINE1_END, -LINE1_END);
        path.moveTo(LINE2_START, LINE2_START);
        path.lineTo(LINE2_END, LINE2_END);

        Object target = new Object() {
            public void setX(float x) {
            }

            public void setY(float y) {
            }
        };
        final CountDownLatch endLatch = new CountDownLatch(1);
        final ObjectAnimator anim = ObjectAnimator.ofInt(target, "x", "y", path);
        anim.setDuration(200);

        // Linear interpolator
        anim.setInterpolator(null);
        anim.addUpdateListener((ValueAnimator animation) -> {
            float fraction = animation.getAnimatedFraction();
            int x = (Integer) animation.getAnimatedValue("x");
            int y = (Integer) animation.getAnimatedValue("y");

            // Check that the point is on the path.
            if (x <= 0) {
                // Check that the time animation stays on the first segment is proportional to
                // the length of the first line segment.
                assertTrue(x >= LINE1_START);
                assertTrue(x <= LINE1_END);
                assertEquals(x, -y);

                // First line segment is 3 times as long as the second line segment, so the
                // 3/4 of the animation duration will be spent on the first line segment.
                assertTrue(fraction <= 0.75f);
            } else {
                // Check that the time animation stays on the second segment is proportional to
                // the length of the second line segment.
                assertTrue(x >= LINE2_START);
                assertTrue(x <= LINE2_END);
                assertEquals(x, y);

                assertTrue(fraction >= 0.75f);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });
        mActivityRule.runOnUiThread(anim::start);
        assertTrue(endLatch.await(400, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testOfMultiFloat_Path() throws Throwable {
        // Test for ObjectAnimator.ofMultiFloat(Object, String, Path);
        // Create a quadratic bezier curve that are symmetric about the vertical line (x = 50).
        // Expect when fraction < 0.5, x < 50, otherwise, x >= 50.
        Path path = new Path();
        path.moveTo(QUADRATIC_CTRL_PT1_X, QUADRATIC_CTRL_PT1_Y);
        path.quadTo(QUADRATIC_CTRL_PT2_X, QUADRATIC_CTRL_PT2_Y,
                QUADRATIC_CTRL_PT3_X, QUADRATIC_CTRL_PT3_Y);

        Object target = new Object() {
            public void setPosition(float x, float y) {
            }
        };

        final ObjectAnimator anim = ObjectAnimator.ofMultiFloat(target, "position", path);
        // Linear interpolator
        anim.setInterpolator(null);
        anim.setDuration(200);

        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            float lastFraction = 0;
            float lastX = 0;
            float lastY = 0;
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float[] values = (float[]) animation.getAnimatedValue();
                assertEquals(2, values.length);
                float x = values[0];
                float y = values[1];
                float fraction = animation.getAnimatedFraction();
                // Given that the curve is symmetric about the line (x = 50), x should be less than
                // 50 for half of the animation duration.
                if (fraction < 0.5) {
                    assertTrue(x < QUADRATIC_CTRL_PT2_X);
                } else {
                    assertTrue(x >= QUADRATIC_CTRL_PT2_X);
                }

                if (lastFraction > 0.5) {
                    // x should be increasing, y should be decreasing
                    assertTrue(x >= lastX);
                    assertTrue(y <= lastY);
                } else if (fraction <= 0.5) {
                    // when fraction <= 0.5, both x, y should be increasing
                    assertTrue(x >= lastX);
                    assertTrue(y >= lastY);
                }
                lastX = x;
                lastY = y;
                lastFraction = fraction;
            }
        });
        final Animator.AnimatorListener listener = mock(Animator.AnimatorListener.class);
        anim.addListener(listener);
        mActivityRule.runOnUiThread(anim::start);
        verify(listener, within(400)).onAnimationEnd(anim, false);
    }

    @Test
    public void testOfMultiFloat() throws Throwable {
        // Test for ObjectAnimator.ofMultiFloat(Object, String, float[][]);
        final float[][] data = new float[10][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new float[3];
            data[i][0] = i;
            data[i][1] = i * 2;
            data[i][2] = 0f;
        }

        Object target = new Object() {
            public void setPosition(float x, float y, float z) {
            }
        };
        final CountDownLatch endLatch = new CountDownLatch(1);
        final ObjectAnimator anim = ObjectAnimator.ofMultiFloat(target, "position", data);
        anim.setInterpolator(null);
        anim.setDuration(60);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        anim.addUpdateListener((ValueAnimator animation) -> {
            float fraction = animation.getAnimatedFraction();
            float[] values = (float[]) animation.getAnimatedValue();
            assertEquals(3, values.length);

            float expectedX = fraction * (data.length - 1);

            assertEquals(expectedX, values[0], EPSILON);
            assertEquals(expectedX * 2, values[1], EPSILON);
            assertEquals(0f, values[2], 0.0f);
        });

        mActivityRule.runOnUiThread(anim::start);
        assertTrue(endLatch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testOfMultiInt_Path() throws Throwable {
        // Test for ObjectAnimator.ofMultiInt(Object, String, Path);
        // Create a quadratic bezier curve that are symmetric about the vertical line (x = 50).
        // Expect when fraction < 0.5, x < 50, otherwise, x >= 50.
        Path path = new Path();
        path.moveTo(QUADRATIC_CTRL_PT1_X, QUADRATIC_CTRL_PT1_Y);
        path.quadTo(QUADRATIC_CTRL_PT2_X, QUADRATIC_CTRL_PT2_Y,
                QUADRATIC_CTRL_PT3_X, QUADRATIC_CTRL_PT3_Y);

        Object target = new Object() {
            public void setPosition(int x, int y) {
            }
        };

        final ObjectAnimator anim = ObjectAnimator.ofMultiInt(target, "position", path);
        // Linear interpolator
        anim.setInterpolator(null);
        anim.setDuration(200);

        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            float lastFraction = 0;
            int lastX = 0;
            int lastY = 0;
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int[] values = (int[]) animation.getAnimatedValue();
                assertEquals(2, values.length);
                int x = values[0];
                int y = values[1];
                float fraction = animation.getAnimatedFraction();
                // Given that the curve is symmetric about the line (x = 50), x should be less than
                // 50 for half of the animation duration.
                if (fraction < 0.5) {
                    assertTrue(x < QUADRATIC_CTRL_PT2_X);
                } else {
                    assertTrue(x >= QUADRATIC_CTRL_PT2_X);
                }

                if (lastFraction > 0.5) {
                    // x should be increasing, y should be decreasing
                    assertTrue(x >= lastX);
                    assertTrue(y <= lastY);
                } else if (fraction <= 0.5) {
                    // when fraction <= 0.5, both x, y should be increasing
                    assertTrue(x >= lastX);
                    assertTrue(y >= lastY);
                }
                lastX = x;
                lastY = y;
                lastFraction = fraction;
            }
        });
        final Animator.AnimatorListener listener = mock(Animator.AnimatorListener.class);
        anim.addListener(listener);
        mActivityRule.runOnUiThread(anim::start);
        verify(listener, within(400)).onAnimationEnd(anim, false);
    }

    @Test
    public void testOfMultiInt() throws Throwable {
        // Test for ObjectAnimator.ofMultiFloat(Object, String, int[][]);
        final int[][] data = new int[10][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new int[3];
            data[i][0] = i;
            data[i][1] = i * 2;
            data[i][2] = 0;
        }

        Object target = new Object() {
            public void setPosition(int x, int y, int z) {
            }
        };
        final CountDownLatch endLatch = new CountDownLatch(1);
        final ObjectAnimator anim = ObjectAnimator.ofMultiInt(target, "position", data);
        anim.setInterpolator(null);
        anim.setDuration(60);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        anim.addUpdateListener((ValueAnimator animation) -> {
            float fraction = animation.getAnimatedFraction();
            int[] values = (int[]) animation.getAnimatedValue();
            assertEquals(3, values.length);

            int expectedX = Math.round(fraction * (data.length - 1));
            int expectedY = Math.round(fraction * (data.length - 1) * 2);

            // Allow a delta of 1 for rounding errors.
            assertEquals(expectedX, values[0], 1);
            assertEquals(expectedY, values[1], 1);
            assertEquals(0, values[2]);
        });

        mActivityRule.runOnUiThread(anim::start);
        assertTrue(endLatch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testOfObject_Converter() throws Throwable {
        // Test for ObjectAnimator.ofObject(Object, String, TypeConverter<T, V>, Path)
        // Create a path that contains two disconnected line segments. Check that the animated
        // property x and property y always stay on the line segments.
        Path path = new Path();
        path.moveTo(LINE1_START, -LINE1_START);
        path.lineTo(LINE1_END, -LINE1_END);
        path.moveTo(LINE2_START, LINE2_START);
        path.lineTo(LINE2_END, LINE2_END);

        Object target1 = new Object() {
            public void setDistance(float distance) {
            }
        };
        Object target2 = new Object() {
            public void setPosition(PointF pos) {
            }
        };
        TypeConverter<PointF, Float> converter = new TypeConverter<PointF, Float>(
                PointF.class, Float.class) {
            @Override
            public Float convert(PointF value) {
                return (float) Math.sqrt(value.x * value.x + value.y * value.y);
            }
        };
        final CountDownLatch endLatch = new CountDownLatch(2);

        // Create two animators. One use a converter that converts the point to distance to origin.
        // The other one does not have a type converter.
        final ObjectAnimator anim1 = ObjectAnimator.ofObject(target1, "distance", converter, path);
        anim1.setDuration(100);
        anim1.setInterpolator(null);
        anim1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        final ObjectAnimator anim2 = ObjectAnimator.ofObject(target2, "position", null, path);
        anim2.setDuration(100);
        anim2.setInterpolator(null);
        anim2.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });
        anim2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            // Set the initial value of the distance to the distance between the first point on
            // the path to the origin.
            float mLastDistance = (float) (32 * Math.sqrt(2));
            float mLastFraction = 0f;
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = anim1.getAnimatedFraction();
                assertEquals(fraction, anim2.getAnimatedFraction(), 0.0f);
                float distance = (Float) anim1.getAnimatedValue();
                PointF position = (PointF) anim2.getAnimatedValue();

                // Manually calculate the distance for the animator that doesn't have a
                // TypeConverter, and expect the result to be the same as the animation value from
                // the type converter.
                float distanceFromPosition = (float) Math.sqrt(
                        position.x * position.x + position.y * position.y);
                assertEquals(distance, distanceFromPosition, 0.0001f);

                if (mLastFraction > 0.75) {
                    // In the 2nd line segment of the path, distance to origin should be increasing.
                    assertTrue(distance >= mLastDistance);
                } else if (fraction < 0.75) {
                    assertTrue(distance <= mLastDistance);
                }
                mLastDistance = distance;
                mLastFraction = fraction;
            }
        });

        mActivityRule.runOnUiThread(() -> {
            anim1.start();
            anim2.start();
        });

        // Wait until both of the animations finish
        assertTrue(endLatch.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testIsStarted() throws Throwable {
        Object object = mActivity.view.newBall;
        String property = "y";
        float startY = mActivity.mStartY;
        float endY = mActivity.mStartY + mActivity.mDeltaY;
        Interpolator interpolator = new AccelerateInterpolator();
        ObjectAnimator objAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(interpolator);
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
        startAnimation(objAnimator);
        SystemClock.sleep(100);
        assertTrue(objAnimator.isStarted());
        SystemClock.sleep(100);
    }

    @Test
    public void testSetStartEndValues() throws Throwable {
        final float startValue = 100, endValue = 500;
        final AnimTarget target = new AnimTarget();
        final ObjectAnimator anim1 = ObjectAnimator.ofFloat(target, "testValue", 0);
        target.setTestValue(startValue);
        anim1.setupStartValues();
        target.setTestValue(endValue);
        anim1.setupEndValues();
        mActivityRule.runOnUiThread(() -> {
            anim1.start();
            assertEquals(startValue, (float) anim1.getAnimatedValue(), 0.0f);
            anim1.setCurrentFraction(1);
            assertEquals(endValue, (float) anim1.getAnimatedValue(), 0.0f);
            anim1.cancel();
        });

        final Property property = AnimTarget.TEST_VALUE;
        final ObjectAnimator anim2 = ObjectAnimator.ofFloat(target, AnimTarget.TEST_VALUE, 0);
        target.setTestValue(startValue);
        final float startValueExpected = (Float) property.get(target);
        anim2.setupStartValues();
        target.setTestValue(endValue);
        final float endValueExpected = (Float) property.get(target);
        anim2.setupEndValues();
        mActivityRule.runOnUiThread(() -> {
            anim2.start();
            assertEquals(startValueExpected, (float) anim2.getAnimatedValue(), 0.0f);
            anim2.setCurrentFraction(1);
            assertEquals(endValueExpected, (float) anim2.getAnimatedValue(), 0.0f);
            anim2.cancel();
        });

        // This is a test that ensures that the values set on a Property-based animator
        // are determined by the property, not by the setter/getter of the target object
        final Property doubler = AnimTarget.TEST_DOUBLING_VALUE;
        final ObjectAnimator anim3 = ObjectAnimator.ofFloat(target,
                doubler, 0);
        target.setTestValue(startValue);
        final float startValueExpected3 = (Float) doubler.get(target);
        anim3.setupStartValues();
        target.setTestValue(endValue);
        final float endValueExpected3 = (Float) doubler.get(target);
        anim3.setupEndValues();
        mActivityRule.runOnUiThread(() -> {
            anim3.start();
            assertEquals(startValueExpected3, (float) anim3.getAnimatedValue(), 0.0f);
            anim3.setCurrentFraction(1);
            assertEquals(endValueExpected3, (float) anim3.getAnimatedValue(), 0.0f);
            anim3.cancel();
        });
    }

    @Test
    public void testCachedValues() throws Throwable {
        final AnimTarget target = new AnimTarget();
        final ObjectAnimator anim = ObjectAnimator.ofFloat(target, "testValue", 100);
        anim.setDuration(200);
        final CountDownLatch twoFramesLatch = new CountDownLatch(2);
        mActivityRule.runOnUiThread(() -> {
            anim.start();
            final View decor = mActivity.getWindow().getDecorView();
            decor.postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    if (twoFramesLatch.getCount() > 0) {
                        twoFramesLatch.countDown();
                        decor.postOnAnimation(this);
                    }
                }
            });
        });

        assertTrue("Animation didn't start in a reasonable time",
                twoFramesLatch.await(100, TimeUnit.MILLISECONDS));

        mActivityRule.runOnUiThread(() -> {
            assertTrue("Start value should readjust to current position",
                    target.getTestValue() != 0);
            anim.cancel();
            anim.setupStartValues();
            anim.start();
            assertTrue("Start value should readjust to current position",
                    target.getTestValue() != 0);
            anim.cancel();
        });
    }

    static class AnimTarget {
        private float mTestValue = 0;

        public void setTestValue(float value) {
            mTestValue = value;
        }

        public float getTestValue() {
            return mTestValue;
        }

        public static final Property<AnimTarget, Float> TEST_VALUE =
                new Property<AnimTarget, Float>(Float.class, "testValue") {
                    @Override
                    public void set(AnimTarget object, Float value) {
                        object.setTestValue(value);
                    }

                    @Override
                    public Float get(AnimTarget object) {
                        return object.getTestValue();
                    }
                };
        public static final Property<AnimTarget, Float> TEST_DOUBLING_VALUE =
                new Property<AnimTarget, Float>(Float.class, "testValue") {
                    @Override
                    public void set(AnimTarget object, Float value) {
                        object.setTestValue(value);
                    }

                    @Override
                    public Float get(AnimTarget object) {
                        // purposely different from getTestValue, to verify that properties
                        // are independent of setters/getters
                        return object.getTestValue() * 2;
                    }
                };
    }

    private void startAnimation(final ObjectAnimator mObjectAnimator) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startAnimation(mObjectAnimator));
    }

    private void startAnimation(final ObjectAnimator mObjectAnimator, final
            ObjectAnimator colorAnimator) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startAnimation(mObjectAnimator, colorAnimator));
    }
}
