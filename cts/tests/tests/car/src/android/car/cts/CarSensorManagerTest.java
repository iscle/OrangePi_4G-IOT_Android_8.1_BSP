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

package android.car.cts;

import android.car.Car;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
@RequiresDevice
public class CarSensorManagerTest extends CarApiTestBase {

    private CarSensorManager mCarSensorManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarSensorManager = (CarSensorManager) getCar().getCarManager(Car.SENSOR_SERVICE);
    }

    public void testDrivingPolicy() throws Exception {
        int[] supportedSensors = mCarSensorManager.getSupportedSensors();
        assertNotNull(supportedSensors);
        boolean found = false;
        for (int sensor: supportedSensors) {
            if (sensor == CarSensorManager.SENSOR_TYPE_DRIVING_STATUS) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        assertTrue(mCarSensorManager.isSensorSupported(
                CarSensorManager.SENSOR_TYPE_DRIVING_STATUS));
        assertTrue(CarSensorManager.isSensorSupported(supportedSensors,
                CarSensorManager.SENSOR_TYPE_DRIVING_STATUS));
        CarSensorEvent lastEvent = mCarSensorManager.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_DRIVING_STATUS);
        assertNotNull(lastEvent);
    }
}
