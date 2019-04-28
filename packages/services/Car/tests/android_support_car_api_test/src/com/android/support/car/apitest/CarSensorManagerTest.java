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

package com.android.support.car.apitest;

import android.os.Looper;
import android.support.car.Car;
import android.support.car.CarConnectionCallback;
import android.support.car.hardware.CarSensorEvent;
import android.support.car.hardware.CarSensorManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarSensorManagerTest extends AndroidTestCase {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;

    private final Semaphore mConnectionWait = new Semaphore(0);

    private Car mCar;
    private CarSensorManager mCarSensorManager;

    private final CarConnectionCallback mConnectionCallbacks =
            new CarConnectionCallback() {
        @Override
        public void onDisconnected(Car car) {
            assertMainThread();
        }

        @Override
        public void onConnected(Car car) {
            assertMainThread();
            mConnectionWait.release();
        }
    };

    private void assertMainThread() {
        assertTrue(Looper.getMainLooper().isCurrentThread());
    }
    private void waitForConnection(long timeoutMs) throws InterruptedException {
        mConnectionWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCar = Car.createCar(getContext(), mConnectionCallbacks);
        mCar.connect();
        waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        mCarSensorManager =
                (CarSensorManager) mCar.getCarManager(Car.SENSOR_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mCar.disconnect();
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
        CarSensorEvent lastEvent = mCarSensorManager.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_DRIVING_STATUS);
        assertNotNull(lastEvent);
    }

    public void testSensorType() throws Exception {
        List<Field> androidCarAllIntMembers = TestUtil.getAllPublicStaticFinalMembers(
                android.car.hardware.CarSensorManager.class, int.class);
        List<Field> supportCarAllIntMembers = TestUtil.getAllPublicStaticFinalMembers(
                android.support.car.hardware.CarSensorManager.class, int.class);
        List<Field> androidCarSensorTypes = filterSensorTypes(androidCarAllIntMembers);
        List<Field> supportCarSensorTypes = filterSensorTypes(supportCarAllIntMembers);
        Map<Integer, Field> androidCarSensorTypeToField = new HashMap<>();
        for (Field androidCarSensorType : androidCarSensorTypes) {
            androidCarSensorTypeToField.put(androidCarSensorType.getInt(null),
                    androidCarSensorType);
        }
        StringBuilder builder = new StringBuilder();
        boolean failed = false;
        for (Field supportCarSensorType : supportCarSensorTypes) {
            Field androidCarSensorType = androidCarSensorTypeToField.get(
                    supportCarSensorType.getInt(null));
            assertNotNull("Sensor type:" + supportCarSensorType.getName() +
                    " not defined in android.car", androidCarSensorType);
            if (supportCarSensorType.getName().equals(androidCarSensorType.getName())) {
                // match ok
            } else if (androidCarSensorType.getName().startsWith("SENSOR_TYPE_RESERVED")) {
                // not used in android.car, ok
            } else if (supportCarSensorType.getName().startsWith("SENSOR_TYPE_RESERVED")) {
                // used in android.car but reserved in support.car
            } else {
                failed = true;
                builder.append("android.support sensor has name:" + supportCarSensorType.getName() +
                        " while android.car sensor has name:" + androidCarSensorType.getName() +
                        "\n");
            }
            androidCarSensorTypeToField.remove(supportCarSensorType.getInt(null));
        }
        assertFalse(builder.toString(), failed);
        assertTrue("android Car sensor has additional types defined:" + androidCarSensorTypeToField,
                androidCarSensorTypeToField.size() == 0);
    }

    public void testSensorRate() throws Exception {
        List<Field> androidCarAllIntMembers = TestUtil.getAllPublicStaticFinalMembers(
                android.car.hardware.CarSensorManager.class, int.class);
        List<Field> supportCarAllIntMembers = TestUtil.getAllPublicStaticFinalMembers(
                android.support.car.hardware.CarSensorManager.class, int.class);
        List<Field> androidCarSensorRates = filterSensorRates(androidCarAllIntMembers);
        List<Field> supportCarSensorRates = filterSensorRates(supportCarAllIntMembers);
        Map<Integer, Field> androidCarSensorRateToField = new HashMap<>();
        for (Field androidCarSensorRate : androidCarSensorRates) {
            androidCarSensorRateToField.put(androidCarSensorRate.getInt(null),
                    androidCarSensorRate);
        }
        StringBuilder builder = new StringBuilder();
        boolean failed = false;
        for (Field supprotCarSensorRate : supportCarSensorRates) {
            Field androidCarSensorRate = androidCarSensorRateToField.get(
                    supprotCarSensorRate.getInt(null));
            assertNotNull("Sensor rate:" + supprotCarSensorRate.getName() +
                    " not defined in android.car", androidCarSensorRate);
            if (supprotCarSensorRate.getName().equals(androidCarSensorRate.getName())) {
                // match ok
            } else {
                failed = true;
                builder.append("android.support sensor rate has name:" +
                        supprotCarSensorRate.getName() +
                        " while android.car sensor rate has name:" +
                        androidCarSensorRate.getName());
            }
            androidCarSensorRateToField.remove(supprotCarSensorRate.getInt(null));
        }
        assertFalse(builder.toString(), failed);
        assertTrue("android Car sensor has additional rates defined:" + androidCarSensorRateToField,
                androidCarSensorRateToField.size() == 0);
    }

    private List<Field> filterSensorTypes(List<Field> fields) {
        return filterFields(fields, "SENSOR_TYPE_");
    }

    private List<Field> filterSensorRates(List<Field> fields) {
        return filterFields(fields, "SENSOR_RATE_");
    }

    private List<Field> filterFields(List<Field> fields, String prefix) {
        List<Field> result = new LinkedList<>();
        for (Field f : fields) {
            if (f.getName().startsWith(prefix)) {
                result.add(f);
            }
        }
        return result;
    }
}
