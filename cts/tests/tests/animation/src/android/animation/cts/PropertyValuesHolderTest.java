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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TypeConverter;
import android.animation.ValueAnimator;
import android.app.Instrumentation;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.drawable.ShapeDrawable;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.FloatProperty;
import android.util.Property;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class PropertyValuesHolderTest {
    private static final float LINE1_START = -32f;
    private static final float LINE1_END = -2f;
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
    private long mDuration = 1000;
    private float mStartY;
    private float mEndY;
    private Object mObject;
    private String mProperty;

    @Rule
    public ActivityTestRule<AnimationActivity> mActivityRule =
            new ActivityTestRule<>(AnimationActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.setInTouchMode(false);
        mActivity = mActivityRule.getActivity();
        mProperty = "y";
        mStartY = mActivity.mStartY;
        mEndY = mActivity.mStartY + mActivity.mDeltaY;
        mObject = mActivity.view.newBall;
    }

    @Test
    public void testGetPropertyName() {
        float[] values = {mStartY, mEndY};
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofFloat(mProperty, values);
        assertEquals(mProperty, pVHolder.getPropertyName());
    }

    @Test
    public void testSetPropertyName() {
        float[] values = {mStartY, mEndY};
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofFloat("", values);
        pVHolder.setPropertyName(mProperty);
        assertEquals(mProperty, pVHolder.getPropertyName());
    }

    @Test
    public void testClone() {
        float[] values = {mStartY, mEndY};
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofFloat(mProperty, values);
        PropertyValuesHolder cloneHolder = pVHolder.clone();
        assertEquals(pVHolder.getPropertyName(), cloneHolder.getPropertyName());
    }

    @Test
    public void testSetValues() throws Throwable {
        float[] dummyValues = {100, 150};
        float[] values = {mStartY, mEndY};
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofFloat(mProperty, dummyValues);
        pVHolder.setFloatValues(values);

        ObjectAnimator objAnimator = ObjectAnimator.ofPropertyValuesHolder(mObject,pVHolder);
        assertTrue(objAnimator != null);
        setAnimatorProperties(objAnimator);

        startAnimation(objAnimator);
        assertTrue(objAnimator != null);
        float[] yArray = getYPosition();
        assertResults(yArray, mStartY, mEndY);
    }

    private ObjectAnimator createAnimator(Keyframe... keyframes) {
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofKeyframe(mProperty, keyframes);
        ObjectAnimator objAnimator = ObjectAnimator.ofPropertyValuesHolder(mObject,pVHolder);
        objAnimator.setDuration(mDuration);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        return objAnimator;
    }

    private void waitUntilFinished(ObjectAnimator objectAnimator, long timeoutMilliseconds)
            throws InterruptedException {
        final Animator.AnimatorListener listener = mock(Animator.AnimatorListener.class);
        objectAnimator.addListener(listener);
        verify(listener, within(timeoutMilliseconds)).onAnimationEnd(objectAnimator, false);
        mInstrumentation.waitForIdleSync();
    }

    private void setTarget(final Animator animator, final Object target) throws Throwable {
        mActivityRule.runOnUiThread(() -> animator.setTarget(target));
    }

    private void startSingleAnimation(final Animator animator) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startSingleAnimation(animator));
    }

    @Test
    public void testResetValues() throws Throwable {
        final float initialY = mActivity.view.newBall.getY();
        Keyframe emptyKeyframe1 = Keyframe.ofFloat(.0f);
        ObjectAnimator objAnimator1 = createAnimator(emptyKeyframe1, Keyframe.ofFloat(1f, 100f));
        startSingleAnimation(objAnimator1);
        assertTrue("Keyframe should be assigned a value", emptyKeyframe1.hasValue());
        assertEquals("Keyframe should get the value from the target",
                (float) emptyKeyframe1.getValue(), initialY, 0.0f);
        waitUntilFinished(objAnimator1, mDuration * 2);
        assertEquals(100f, mActivity.view.newBall.getY(), 0.0f);
        startSingleAnimation(objAnimator1);
        waitUntilFinished(objAnimator1, mDuration * 2);

        // run another ObjectAnimator that will move the Y value to something else
        Keyframe emptyKeyframe2 = Keyframe.ofFloat(.0f);
        ObjectAnimator objAnimator2 = createAnimator(emptyKeyframe2, Keyframe.ofFloat(1f, 200f));
        startSingleAnimation(objAnimator2);
        assertTrue("Keyframe should be assigned a value", emptyKeyframe2.hasValue());
        assertEquals("Keyframe should get the value from the target",
                (float) emptyKeyframe2.getValue(), 100f, 0.0f);
        waitUntilFinished(objAnimator2, mDuration * 2);
        assertEquals(200f, mActivity.view.newBall.getY(), 0.0f);

        // re-run first object animator. since its target did not change, it should have the same
        // start value for kf1
        startSingleAnimation(objAnimator1);
        assertEquals((float) emptyKeyframe1.getValue(), initialY, 0.0f);
        waitUntilFinished(objAnimator1, mDuration * 2);

        Keyframe fullKeyframe = Keyframe.ofFloat(.0f, 333f);
        ObjectAnimator objAnimator3 = createAnimator(fullKeyframe, Keyframe.ofFloat(1f, 500f));
        startSingleAnimation(objAnimator3);
        assertEquals("When keyframe has value, should not be assigned from the target object",
                (float) fullKeyframe.getValue(), 333f, 0.0f);
        waitUntilFinished(objAnimator3, mDuration * 2);

        // now, null out the target of the first animator
        float updatedY = mActivity.view.newBall.getY();
        setTarget(objAnimator1, null);
        startSingleAnimation(objAnimator1);
        assertTrue("Keyframe should get a value", emptyKeyframe1.hasValue());
        assertEquals("Keyframe should get the updated Y value",
                (float) emptyKeyframe1.getValue(), updatedY, 0.0f);
        waitUntilFinished(objAnimator1, mDuration * 2);
        assertEquals("Animation should run as expected", 100f, mActivity.view.newBall.getY(), 0.0f);

        // now, reset the target of the fully defined animation.
        setTarget(objAnimator3, null);
        startSingleAnimation(objAnimator3);
        assertEquals("When keyframe is fully defined, its value should not change when target is"
                + " reset", (float) fullKeyframe.getValue(), 333f, 0.0f);
        waitUntilFinished(objAnimator3, mDuration * 2);

        // run the other one to change Y value
        startSingleAnimation(objAnimator2);
        waitUntilFinished(objAnimator2, mDuration * 2);
        // now, set another target w/ the same View type. it should still reset
        ShapeHolder view = new ShapeHolder(new ShapeDrawable());
        updatedY = mActivity.view.newBall.getY();
        setTarget(objAnimator1, view);
        startSingleAnimation(objAnimator1);
        assertTrue("Keyframe should get a value when target is set to another view of the same"
                + " class", emptyKeyframe1.hasValue());
        assertEquals("Keyframe should get the updated Y value when target is set to another view"
                + " of the same class", (float) emptyKeyframe1.getValue(), updatedY, 0.0f);
        waitUntilFinished(objAnimator1, mDuration * 2);
        assertEquals("Animation should run as expected", 100f, mActivity.view.newBall.getY(), 0.0f);
    }

    @Test
    public void testOfFloat() throws Throwable {
        float[] values = {mStartY, mEndY};
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofFloat(mProperty, values);
        assertNotNull(pVHolder);
        ObjectAnimator objAnimator = ObjectAnimator.ofPropertyValuesHolder(mObject,pVHolder);
        assertTrue(objAnimator != null);

        setAnimatorProperties(objAnimator);
        startAnimation(objAnimator);
        assertTrue(objAnimator != null);
        float[] yArray = getYPosition();
        assertResults(yArray, mStartY, mEndY);
    }

    @Test
    public void testOfFloat_Property() throws Throwable {
        float[] values = {mStartY, mEndY};
        ShapeHolderYProperty property=new ShapeHolderYProperty(ShapeHolder.class,"y");
        property.setObject(mObject);
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofFloat(property, values);
        assertNotNull(pVHolder);
        ObjectAnimator objAnimator = ObjectAnimator.ofPropertyValuesHolder(mObject,pVHolder);
        assertTrue(objAnimator != null);

        setAnimatorProperties(objAnimator);
        startAnimation(objAnimator);
        assertTrue(objAnimator != null);
        float[] yArray = getYPosition();
        assertResults(yArray, mStartY, mEndY);
    }

    @Test
    public void testOfInt() throws Throwable {
        int start = 0;
        int end = 10;
        int[] values = {start, end};
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofInt(mProperty, values);
        assertNotNull(pVHolder);
        final ObjectAnimator objAnimator = ObjectAnimator.ofPropertyValuesHolder(mObject,pVHolder);
        assertTrue(objAnimator != null);
        setAnimatorProperties(objAnimator);
        mActivityRule.runOnUiThread(objAnimator::start);
        SystemClock.sleep(1000);
        assertTrue(objAnimator.isRunning());
        Integer animatedValue = (Integer) objAnimator.getAnimatedValue();
        assertTrue(animatedValue >= start);
        assertTrue(animatedValue <= end);
    }

    @Test
    public void testOfInt_Property() throws Throwable{
        Object object = mActivity.view;
        String property = "backgroundColor";
        int startColor = mActivity.view.RED;
        int endColor = mActivity.view.BLUE;
        int values[] = {startColor, endColor};

        ViewColorProperty colorProperty=new ViewColorProperty(Integer.class,property);
        colorProperty.setObject(object);
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofInt(colorProperty, values);
        assertNotNull(pVHolder);

        ObjectAnimator colorAnimator = ObjectAnimator.ofPropertyValuesHolder(object,pVHolder);
        colorAnimator.setDuration(1000);
        colorAnimator.setEvaluator(new ArgbEvaluator());
        colorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimator.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator objectAnimator = (ObjectAnimator) mActivity.createAnimatorWithDuration(
            mDuration);
        startAnimation(objectAnimator, colorAnimator);
        SystemClock.sleep(1000);
        Integer animatedValue = (Integer) colorAnimator.getAnimatedValue();
        int redMin = Math.min(Color.red(startColor), Color.red(endColor));
        int redMax = Math.max(Color.red(startColor), Color.red(endColor));
        int blueMin = Math.min(Color.blue(startColor), Color.blue(endColor));
        int blueMax = Math.max(Color.blue(startColor), Color.blue(endColor));
        assertTrue(Color.red(animatedValue) >= redMin);
        assertTrue(Color.red(animatedValue) <= redMax);
        assertTrue(Color.blue(animatedValue) >= blueMin);
        assertTrue(Color.blue(animatedValue) <= blueMax);
    }

    @Test
    public void testOfMultiFloat_Path() throws Throwable {
        // Test for PropertyValuesHolder.ofMultiFloat(String, Path);
        // Create a quadratic bezier curve that are symmetric about the vertical line (x = 50).
        // Expect when fraction < 0.5, x < 50, otherwise, x >= 50.
        Path path = new Path();
        path.moveTo(QUADRATIC_CTRL_PT1_X, QUADRATIC_CTRL_PT1_Y);
        path.quadTo(QUADRATIC_CTRL_PT2_X, QUADRATIC_CTRL_PT2_Y,
                QUADRATIC_CTRL_PT3_X, QUADRATIC_CTRL_PT3_Y);

        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiFloat("position", path);
        final ValueAnimator anim = ValueAnimator.ofPropertyValuesHolder(pvh);

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
    public void testOfMultiFloat_Array() throws Throwable {
        // Test for PropertyValuesHolder.ofMultiFloat(String, float[][]);
        final float[][] data = new float[10][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new float[3];
            data[i][0] = i;
            data[i][1] = i * 2;
            data[i][2] = 0f;
        }
        final CountDownLatch endLatch = new CountDownLatch(1);
        final PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiFloat("position", data);

        final ValueAnimator anim = ValueAnimator.ofPropertyValuesHolder(pvh);
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
            assertEquals(0.0f, values[2], 0.0f);
        });

        mActivityRule.runOnUiThread(anim::start);
        assertTrue(endLatch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testOfMultiInt_Path() throws Throwable {
        // Test for PropertyValuesHolder.ofMultiInt(String, Path);
        // Create a quadratic bezier curve that are symmetric about the vertical line (x = 50).
        // Expect when fraction < 0.5, x < 50, otherwise, x >= 50.
        Path path = new Path();
        path.moveTo(QUADRATIC_CTRL_PT1_X, QUADRATIC_CTRL_PT1_Y);
        path.quadTo(QUADRATIC_CTRL_PT2_X, QUADRATIC_CTRL_PT2_Y,
                QUADRATIC_CTRL_PT3_X, QUADRATIC_CTRL_PT3_Y);

        final PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiInt("position", path);
        final ValueAnimator anim = ValueAnimator.ofPropertyValuesHolder(pvh);
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
    public void testOfMultiInt_Array() throws Throwable {
        // Test for PropertyValuesHolder.ofMultiFloat(String, int[][]);
        final int[][] data = new int[10][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new int[3];
            data[i][0] = i;
            data[i][1] = i * 2;
            data[i][2] = 0;
        }

        final CountDownLatch endLatch = new CountDownLatch(1);
        final PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiInt("position", data);
        final ValueAnimator anim = ValueAnimator.ofPropertyValuesHolder(pvh);
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
        // Test for PropertyValuesHolder.ofObject(String, TypeConverter<T, V>, Path)
        // and for PropertyValuesHolder.ofObject(Property, TypeConverter<T, V>, Path)
        // Create a path that contains two disconnected line segments. Check that the animated
        // property x and property y always stay on the line segments.
        Path path = new Path();
        path.moveTo(LINE1_START, -LINE1_START);
        path.lineTo(LINE1_END, -LINE1_END);
        path.moveTo(LINE2_START, LINE2_START);
        path.lineTo(LINE2_END, LINE2_END);
        TypeConverter<PointF, Float> converter = new TypeConverter<PointF, Float>(
                PointF.class, Float.class) {
            @Override
            public Float convert(PointF value) {
                return (float) Math.sqrt(value.x * value.x + value.y * value.y);
            }
        };
        final CountDownLatch endLatch = new CountDownLatch(3);

        // Create three animators. The first one use a converter that converts the point to distance
        // to  origin. The second one does not have a type converter. The third animator uses a
        // converter to changes sign of the x, y value of the input pointF.
        FloatProperty property = new FloatProperty("distance") {
            @Override
            public void setValue(Object object, float value) {
            }

            @Override
            public Object get(Object object) {
                return null;
            }
        };
        final PropertyValuesHolder pvh1 =
                PropertyValuesHolder.ofObject(property, converter, path);
        final ValueAnimator anim1 = ValueAnimator.ofPropertyValuesHolder(pvh1);
        anim1.setDuration(100);
        anim1.setInterpolator(null);
        anim1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        final PropertyValuesHolder pvh2 =
                PropertyValuesHolder.ofObject("position", null, path);
        final ValueAnimator anim2 = ValueAnimator.ofPropertyValuesHolder(pvh2);
        anim2.setDuration(100);
        anim2.setInterpolator(null);
        anim2.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        TypeConverter<PointF, PointF> converter3 = new TypeConverter<PointF, PointF>(
                PointF.class, PointF.class) {
            PointF mValue = new PointF();
            @Override
            public PointF convert(PointF value) {
                mValue.x = -value.x;
                mValue.y = -value.y;
                return mValue;
            }
        };
        final PropertyValuesHolder pvh3 =
                PropertyValuesHolder.ofObject("position", converter3, path);
        final ValueAnimator anim3 = ValueAnimator.ofPropertyValuesHolder(pvh3);
        anim3.setDuration(100);
        anim3.setInterpolator(null);
        anim3.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        anim3.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            // Set the initial value of the distance to the distance between the first point on
            // the path to the origin.
            float mLastDistance = (float) (32 * Math.sqrt(2));
            float mLastFraction = 0f;
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = anim1.getAnimatedFraction();
                assertEquals(fraction, anim2.getAnimatedFraction(), 0.0f);
                assertEquals(fraction, anim3.getAnimatedFraction(), 0.0f);
                float distance = (Float) anim1.getAnimatedValue();
                PointF position = (PointF) anim2.getAnimatedValue();
                PointF positionReverseSign = (PointF) anim3.getAnimatedValue();
                assertEquals(position.x, -positionReverseSign.x, 0.0f);
                assertEquals(position.y, -positionReverseSign.y, 0.0f);

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
            anim3.start();
        });

        // Wait until both of the animations finish
        assertTrue(endLatch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSetConverter() throws Throwable {
        // Test for PropertyValuesHolder.setConverter()
        PropertyValuesHolder pvh = PropertyValuesHolder.ofObject("", null, 0f, 1f);
        // Reverse the sign of the float in the converter, and use that value as the new type
        // PointF's x value.
        pvh.setConverter(new TypeConverter<Float, PointF>(Float.class, PointF.class) {
            PointF mValue = new PointF();
            @Override
            public PointF convert(Float value) {
                mValue.x = value * (-1f);
                mValue.y = 0f;
                return mValue;
            }
        });
        final CountDownLatch endLatch = new CountDownLatch(2);

        final ValueAnimator anim1 = ValueAnimator.ofPropertyValuesHolder(pvh);
        anim1.setInterpolator(null);
        anim1.setDuration(100);
        anim1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        final ValueAnimator anim2 = ValueAnimator.ofFloat(0f, 1f);
        anim2.setInterpolator(null);
        anim2.addUpdateListener((ValueAnimator animation) -> {
            assertEquals(anim1.getAnimatedFraction(), anim2.getAnimatedFraction(), 0.0f);
            // Check that the pvh with type converter did reverse the sign of float, and set
            // the x value of the PointF with it.
            PointF value1 = (PointF) anim1.getAnimatedValue();
            float value2 = (Float) anim2.getAnimatedValue();
            assertEquals(value2, -value1.x, 0.0f);
            assertEquals(0f, value1.y, 0.0f);
        });
        anim2.setDuration(100);
        anim2.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endLatch.countDown();
            }
        });

        mActivityRule.runOnUiThread(() -> {
            anim1.start();
            anim2.start();
        });

        // Wait until both of the animations finish
        assertTrue(endLatch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSetProperty() throws Throwable {
        float[] values = {mStartY, mEndY};
        ShapeHolderYProperty property=new ShapeHolderYProperty(ShapeHolder.class,"y");
        property.setObject(mObject);
        PropertyValuesHolder pVHolder = PropertyValuesHolder.ofFloat("", values);
        pVHolder.setProperty(property);
        ObjectAnimator objAnimator = ObjectAnimator.ofPropertyValuesHolder(mObject,pVHolder);
        setAnimatorProperties(objAnimator);
        startAnimation(objAnimator);
        assertTrue(objAnimator != null);
        float[] yArray = getYPosition();
        assertResults(yArray, mStartY, mEndY);
    }

    class ShapeHolderYProperty extends Property {
        private ShapeHolder shapeHolder ;
        private Class type = Float.class;
        private String name = "y";
        @SuppressWarnings("unchecked")
        public ShapeHolderYProperty(Class type, String name) throws Exception {
            super(Float.class, name );
            if(!( type.equals(this.type) || ( name.equals(this.name))) ){
                throw new Exception("Type or name provided does not match with " +
                        this.type.getName() + " or " + this.name);
            }
        }

        public void setObject(Object object){
            shapeHolder = (ShapeHolder) object;
        }

        @Override
        public Object get(Object object) {
            return shapeHolder;
        }

        @Override
        public String getName() {
            return "y";
        }

        @Override
        public Class getType() {
            return super.getType();
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public void set(Object object, Object value) {
            shapeHolder.setY((Float)value);
        }

    }

    class ViewColorProperty extends Property {
        private View view ;
        private Class type = Integer.class;
        private String name = "backgroundColor";
        @SuppressWarnings("unchecked")
        public ViewColorProperty(Class type, String name) throws Exception {
            super(Integer.class, name );
            if(!( type.equals(this.type) || ( name.equals(this.name))) ){
                throw new Exception("Type or name provided does not match with " +
                        this.type.getName() + " or " + this.name);
            }
        }

        public void setObject(Object object){
            view = (View) object;
        }

        @Override
        public Object get(Object object) {
            return view;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class getType() {
            return super.getType();
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public void set(Object object, Object value) {
            view.setBackgroundColor((Integer)value);
        }
    }

    private void setAnimatorProperties(ObjectAnimator objAnimator) {
        objAnimator.setDuration(mDuration);
        objAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objAnimator.setInterpolator(new AccelerateInterpolator());
        objAnimator.setRepeatMode(ValueAnimator.REVERSE);
    }

    public float[] getYPosition() throws Throwable{
        float[] yArray = new float[3];
        for(int i = 0; i < 3; i++) {
            float y = mActivity.view.newBall.getY();
            yArray[i] = y;
            SystemClock.sleep(300);
        }
        return yArray;
    }

    public void assertResults(float[] yArray,float startY, float endY) {
        for(int i = 0; i < 3; i++){
            float y = yArray[i];
            assertTrue(y >= startY);
            assertTrue(y <= endY);
            if(i < 2) {
                float yNext = yArray[i+1];
                assertTrue(y != yNext);
            }
        }
    }

    private void startAnimation(final Animator animator) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startAnimation(animator));
    }

    private void startAnimation(final ObjectAnimator mObjectAnimator,
            final ObjectAnimator colorAnimator) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startAnimation(mObjectAnimator, colorAnimator));
    }
}

