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
import android.support.car.hardware.CarSensorManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarTest extends AndroidTestCase {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;

    private final Semaphore mConnectionWait = new Semaphore(0);

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

    public void testCarConnection() throws Exception {
        Car car = Car.createCar(getContext(), mConnectionCallbacks);
        assertFalse(car.isConnected());
        assertFalse(car.isConnecting());
        car.connect();
        //TODO fix race here
        assertTrue(car.isConnecting());
        waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        assertTrue(car.isConnected());
        assertFalse(car.isConnecting());
        CarSensorManager carSensorManager =
                (CarSensorManager) car.getCarManager(Car.SENSOR_SERVICE);
        assertNotNull(carSensorManager);
        CarSensorManager carSensorManager2 =
                (CarSensorManager) car.getCarManager(Car.SENSOR_SERVICE);
        assertEquals(carSensorManager, carSensorManager2);
        Object noSuchService = car.getCarManager("No such service");
        assertNull(noSuchService);
        // double disconnect should be safe.
        car.disconnect();
        car.disconnect();
        assertFalse(car.isConnected());
        assertFalse(car.isConnecting());
    }

    public void testDoubleConnect() throws Exception {
        Car car = Car.createCar(getContext(), mConnectionCallbacks);
        assertFalse(car.isConnected());
        assertFalse(car.isConnecting());
        car.connect();
        try {
            car.connect();
            fail("double connect should throw an exception");
        } catch (IllegalStateException e) {
            // expected
        }
        car.disconnect();
    }
}
