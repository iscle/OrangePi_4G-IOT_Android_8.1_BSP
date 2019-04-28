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

import android.car.Car;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.CarPropertyConfig;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@MediumTest
public class CarCabinManagerTest extends CarApiTestBase {
    private static final String TAG = CarCabinManagerTest.class.getSimpleName();

    private CarCabinManager mCabinManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCabinManager = (CarCabinManager) getCar().getCarManager(Car.CABIN_SERVICE);
        assertNotNull(mCabinManager);
    }

    public void testAllCabinProperties() throws Exception {
        List<CarPropertyConfig> properties = mCabinManager.getPropertyList();
        Set<Class> supportedTypes = new HashSet<>(Arrays.asList(
                new Class[] { Integer.class, Boolean.class }));

        for (CarPropertyConfig property : properties) {
            if (supportedTypes.contains(property.getPropertyType())) {
                assertTypeAndZone(property);
            } else {
                fail("Type is not supported for " + property);
            }
        }
    }

    private void assertTypeAndZone(CarPropertyConfig property) {
        int propId = property.getPropertyId();
        switch (propId) {
            // Zoned boolean properties
            case CarCabinManager.ID_DOOR_LOCK:
            case CarCabinManager.ID_MIRROR_LOCK:
            case CarCabinManager.ID_MIRROR_FOLD:
            case CarCabinManager.ID_SEAT_BELT_BUCKLED:
            case CarCabinManager.ID_WINDOW_LOCK:
                assertEquals(Boolean.class, property.getPropertyType());
                assertFalse(property.isGlobalProperty());
                break;

            // Zoned integer properties
            case CarCabinManager.ID_DOOR_POS:
            case CarCabinManager.ID_DOOR_MOVE:
            case CarCabinManager.ID_MIRROR_Z_POS:
            case CarCabinManager.ID_MIRROR_Z_MOVE:
            case CarCabinManager.ID_MIRROR_Y_POS:
            case CarCabinManager.ID_MIRROR_Y_MOVE:
            case CarCabinManager.ID_SEAT_MEMORY_SELECT:
            case CarCabinManager.ID_SEAT_MEMORY_SET:
            case CarCabinManager.ID_SEAT_BELT_HEIGHT_POS:
            case CarCabinManager.ID_SEAT_BELT_HEIGHT_MOVE:
            case CarCabinManager.ID_SEAT_FORE_AFT_POS:
            case CarCabinManager.ID_SEAT_FORE_AFT_MOVE:
            case CarCabinManager.ID_SEAT_BACKREST_ANGLE_1_POS:
            case CarCabinManager.ID_SEAT_BACKREST_ANGLE_1_MOVE:
            case CarCabinManager.ID_SEAT_BACKREST_ANGLE_2_POS:
            case CarCabinManager.ID_SEAT_BACKREST_ANGLE_2_MOVE:
            case CarCabinManager.ID_SEAT_HEIGHT_POS:
            case CarCabinManager.ID_SEAT_HEIGHT_MOVE:
            case CarCabinManager.ID_SEAT_DEPTH_POS:
            case CarCabinManager.ID_SEAT_DEPTH_MOVE:
            case CarCabinManager.ID_SEAT_TILT_POS:
            case CarCabinManager.ID_SEAT_TILT_MOVE:
            case CarCabinManager.ID_SEAT_LUMBAR_FORE_AFT_POS:
            case CarCabinManager.ID_SEAT_LUMBAR_FORE_AFT_MOVE:
            case CarCabinManager.ID_SEAT_LUMBAR_SIDE_SUPPORT_POS:
            case CarCabinManager.ID_SEAT_LUMBAR_SIDE_SUPPORT_MOVE:
            case CarCabinManager.ID_SEAT_HEADREST_HEIGHT_POS:
            case CarCabinManager.ID_SEAT_HEADREST_HEIGHT_MOVE:
            case CarCabinManager.ID_SEAT_HEADREST_ANGLE_POS:
            case CarCabinManager.ID_SEAT_HEADREST_ANGLE_MOVE:
            case CarCabinManager.ID_SEAT_HEADREST_FORE_AFT_POS:
            case CarCabinManager.ID_SEAT_HEADREST_FORE_AFT_MOVE:
            case CarCabinManager.ID_WINDOW_POS:
            case CarCabinManager.ID_WINDOW_MOVE:
            case CarCabinManager.ID_WINDOW_VENT_POS:
            case CarCabinManager.ID_WINDOW_VENT_MOVE:
                assertEquals(Integer.class, property.getPropertyType());
                assertFalse(property.isGlobalProperty());
                checkIntMinMax(property);
                break;
            default:
                Log.e(TAG, "Property ID not handled: " + propId);
                assertTrue(false);
                break;
        }
    }

    private void checkIntMinMax(CarPropertyConfig<Integer> property) {
        Log.i(TAG, "checkIntMinMax property:" + property);
        if (!property.isGlobalProperty()) {
            int[] areaIds = property.getAreaIds();
            assertTrue(areaIds.length > 0);
            assertEquals(areaIds.length, property.getAreaCount());

            for (int areId : areaIds) {
                assertTrue(property.hasArea(areId));
                int min = property.getMinValue(areId);
                int max = property.getMaxValue(areId);
                assertTrue(min <= max);
            }
        } else {
            int min = property.getMinValue();
            int max = property.getMaxValue();
            assertTrue(min <= max);
            for (int i = 0; i < 32; i++) {
                assertFalse(property.hasArea(0x1 << i));
                assertNull(property.getMinValue(0x1 << i));
                assertNull(property.getMaxValue(0x1 << i));
            }
        }
    }
}
