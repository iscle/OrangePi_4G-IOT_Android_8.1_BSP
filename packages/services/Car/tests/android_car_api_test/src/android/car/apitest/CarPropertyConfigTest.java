/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.car.apitest;

import android.car.hardware.CarPropertyConfig;
import android.graphics.Point;
import android.test.suitebuilder.annotation.MediumTest;

/**
 * Unit tests for {@link CarPropertyConfig}
 */
@MediumTest
public class CarPropertyConfigTest extends CarPropertyTestBase {

    public void testCarPropertyConfigBuilder() {
        createFloatPropertyConfig();
    }

    private CarPropertyConfig<Float> createFloatPropertyConfig() {
        CarPropertyConfig<Float> config = CarPropertyConfig
                .newBuilder(Float.class, PROPERTY_ID, CAR_AREA_TYPE)
                .addArea(WINDOW_DRIVER)
                .addAreaConfig(WINDOW_PASSENGER, 10f, 20f)
                .build();

        assertEquals(PROPERTY_ID, config.getPropertyId());
        assertEquals(CAR_AREA_TYPE, config.getAreaType());
        assertEquals(Float.class, config.getPropertyType());
        assertEquals(2, config.getAreaCount());

        // We didn't assign any restrictions to WINDOW_DRIVER area.
        assertNull(config.getMinValue(WINDOW_DRIVER));
        assertNull(config.getMaxValue(WINDOW_DRIVER));

        assertEquals(10f, config.getMinValue(WINDOW_PASSENGER));
        assertEquals(20f, config.getMaxValue(WINDOW_PASSENGER));

        return config;
    }

    public void testWriteReadFloat() {
        CarPropertyConfig<Float> config = createFloatPropertyConfig();

        writeToParcel(config);
        CarPropertyConfig<Float> configRead = readFromParcel();

        assertEquals(PROPERTY_ID, configRead.getPropertyId());
        assertEquals(CAR_AREA_TYPE, configRead.getAreaType());
        assertEquals(Float.class, configRead.getPropertyType());
        assertEquals(2, configRead.getAreaCount());

        // We didn't assign any restrictions to WINDOW_DRIVER area.
        assertNull(configRead.getMinValue(WINDOW_DRIVER));
        assertNull(configRead.getMaxValue(WINDOW_DRIVER));

        assertEquals(10f, configRead.getMinValue(WINDOW_PASSENGER));
        assertEquals(20f, configRead.getMaxValue(WINDOW_PASSENGER));
    }

    public void testWriteReadIntegerArray() {
        CarPropertyConfig<Integer[]> config = CarPropertyConfig
                .newBuilder(Integer[].class, PROPERTY_ID, CAR_AREA_TYPE)
                // We can specify min/max values per each element.
                .addAreaConfig(WINDOW_DRIVER, new Integer[] {10, 20, 30}, new Integer[0])
                .addArea(WINDOW_PASSENGER)
                .build();

        writeToParcel(config);
        CarPropertyConfig<Integer[]> configRead = readFromParcel();

        assertEquals(PROPERTY_ID, configRead.getPropertyId());
        assertEquals(CAR_AREA_TYPE, configRead.getAreaType());
        assertEquals(Integer[].class, configRead.getPropertyType());
        assertEquals(2, configRead.getAreaCount());

        // We didn't assign any restrictions to WINDOW_DRIVER area.
        assertNull(configRead.getMinValue(WINDOW_PASSENGER));
        assertNull(configRead.getMaxValue(WINDOW_PASSENGER));

        assertEquals(3, configRead.getMinValue(WINDOW_DRIVER).length);
        assertEquals(20, (int) configRead.getMinValue(WINDOW_DRIVER)[1]);
        assertEquals(0, configRead.getMaxValue(WINDOW_DRIVER).length);
    }

    public void testWriteReadUnexpectedType() {
        CarPropertyConfig<Float> config = createFloatPropertyConfig();

        writeToParcel(config);

        try {
            CarPropertyConfig<Integer> integerConfig = readFromParcel();
            Integer value = integerConfig.getMinValue(WINDOW_PASSENGER);
            fail(String.valueOf(value));
        } catch (ClassCastException expected) {
            // Expected. Wrote float, attempted to read integer.
        }

        // Type casting from raw CarPropertyConfig should be fine, just sanity check.
        CarPropertyConfig rawTypeConfig = readFromParcel();
        assertEquals(10f, rawTypeConfig.getMinValue(WINDOW_PASSENGER));

        try {
            int intValue = (Integer) rawTypeConfig.getMinValue(WINDOW_PASSENGER);
            fail(String.valueOf(intValue));
        } catch (ClassCastException expected) {
            // Expected. Wrote float, attempted to read integer.
        }
    }

    public void testWriteReadArbitraryParcelable() {
        Point maxPoint = new Point(10, 20);
        CarPropertyConfig<Point> config = CarPropertyConfig
                .newBuilder(Point.class, PROPERTY_ID, CAR_AREA_TYPE)
                .addAreaConfig(WINDOW_DRIVER, null, maxPoint)
                .build();

        assertNull(config.getMinValue());
        assertNotNull(config.toString(), config.getMaxValue());
        assertEquals("Value: " + config.toString(), 10, config.getMaxValue().x);

        writeToParcel(config);

        CarPropertyConfig<Point> configRead = readFromParcel();
        assertNotNull(configRead);
        assertEquals(Point.class, configRead.getPropertyType());
        assertNull(configRead.getMinValue());
        assertEquals(10, configRead.getMaxValue().x);
    }
}
