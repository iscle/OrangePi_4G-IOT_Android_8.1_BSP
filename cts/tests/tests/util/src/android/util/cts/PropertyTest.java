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

package android.util.cts;

import android.graphics.Point;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.util.Property;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PropertyTest {
    private float mFloatValue = -1;
    private int mIntValue = -2;
    private Point mPointValue = new Point(-3, -4);

    @Test
    public void testProperty() throws Exception {
        float testFloatValue = 5;
        Point testPointValue = new Point(10, 20);

        assertFalse(getFloatProp() == testFloatValue);
        assertFalse(getPointProp().equals(testPointValue));
        assertEquals(getFloatProp(), RAW_FLOAT_PROP.get(this), 0f);
        assertEquals(getPointProp(), RAW_POINT_PROP.get(this));

        RAW_FLOAT_PROP.set(this, testFloatValue);
        assertEquals(mFloatValue, RAW_FLOAT_PROP.get(this), 0f);

        RAW_POINT_PROP.set(this, testPointValue);
        assertEquals(testPointValue, RAW_POINT_PROP.get(this));
    }

    @Test
    public void testFloatProperty() throws Exception {
        assertFalse(getFloatProp() == 5);
        assertEquals(getFloatProp(), FLOAT_PROP.get(this), 0f);

        FLOAT_PROP.set(this, 5f);
        assertEquals(5f, FLOAT_PROP.get(this), 0f);

        FLOAT_PROP.setValue(this, 10);
        assertEquals(10f, FLOAT_PROP.get(this), 0f);
    }

    @Test
    public void testIntProperty() throws Exception {
        assertFalse(getIntProp() == 5);
        assertEquals(getIntProp(), INT_PROP.get(this).intValue());

        INT_PROP.set(this, 5);
        assertEquals(5, INT_PROP.get(this).intValue());

        INT_PROP.setValue(this, 10);
        assertEquals(10, INT_PROP.get(this).intValue());
    }

    // Utility methods to get/set instance values. Used by Property classes below.

    private void setFloatProp(float value) {
        mFloatValue = value;
    }

    private float getFloatProp() {
        return mFloatValue;
    }

    private void setIntProp(int value) {
        mIntValue = value;
    }

    private int getIntProp() {
        return mIntValue;
    }

    private void setPointProp(Point value) {
        mPointValue = value;
    }

    private Point getPointProp() {
        return mPointValue;
    }

    // Properties. RAW subclass from the generic Property class, the others subclass from
    // the primitive-friendly IntProperty and FloatProperty subclasses.

    private static final Property<PropertyTest, Point> RAW_POINT_PROP =
            new Property<PropertyTest, Point>(Point.class, "rawPoint") {
                @Override
                public void set(PropertyTest object, Point value) {
                    object.setPointProp(value);
                }

                @Override
                public Point get(PropertyTest object) {
                    return object.getPointProp();
                }
            };

    private static final Property<PropertyTest, Float> RAW_FLOAT_PROP =
            new Property<PropertyTest, Float>(Float.class, "rawFloat") {
                @Override
                public void set(PropertyTest object, Float value) {
                    object.setFloatProp(value);
                }

                @Override
                public Float get(PropertyTest object) {
                    return object.getFloatProp();
                }
            };

    private static final FloatProperty<PropertyTest> FLOAT_PROP =
            new FloatProperty<PropertyTest>("float") {

                @Override
                public void setValue(PropertyTest object, float value) {
                    object.setFloatProp(value);
                }

                @Override
                public Float get(PropertyTest object) {
                    return object.getFloatProp();
                }
            };

    private static final IntProperty<PropertyTest> INT_PROP =
            new IntProperty<PropertyTest>("int") {

                @Override
                public void setValue(PropertyTest object, int value) {
                    object.setIntProp(value);
                }

                @Override
                public Integer get(PropertyTest object) {
                    return object.getIntProp();
                }
            };
}
