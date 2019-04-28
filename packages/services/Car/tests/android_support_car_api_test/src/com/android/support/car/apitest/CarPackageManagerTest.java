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
import android.support.car.content.pm.CarPackageManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarPackageManagerTest extends AndroidTestCase {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;

    private final Semaphore mConnectionWait = new Semaphore(0);

    private Car mCar;
    private CarPackageManager mCarPackageManager;

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
        mCarPackageManager = (CarPackageManager) mCar.getCarManager(Car.PACKAGE_SERVICE);
        assertNotNull(mCarPackageManager);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mCar.disconnect();
    }

    public void testCreate() throws Exception {
        //nothing to do for now
    }
}
