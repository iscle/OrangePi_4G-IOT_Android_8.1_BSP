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

import android.car.hardware.CarPropertyValue;
import android.graphics.Point;
import android.test.suitebuilder.annotation.MediumTest;

/**
 * Unit tests for {@link CarPropertyValue}
 */
@MediumTest
public class CarPropertyValueTest extends CarPropertyConfigTest {

    public void testSimpleFloatValue() {
        CarPropertyValue<Float> floatValue =
                new CarPropertyValue<>(PROPERTY_ID, WINDOW_DRIVER, 10f);

        writeToParcel(floatValue);

        CarPropertyValue<Float> valueRead = readFromParcel();
        assertEquals(10f, valueRead.getValue());
    }

    public void testCarAreaArbitraryParcelable() {
        CarPropertyValue<Point> pointValue =
                new CarPropertyValue<>(PROPERTY_ID, WINDOW_DRIVER, new Point(30, 40));

        writeToParcel(pointValue);
        CarPropertyValue<Point> pointValueRead = readFromParcel();

        assertNotNull(pointValueRead);
        assertEquals(30, pointValueRead.getValue().x);
        assertEquals(40, pointValueRead.getValue().y);
    }
}
