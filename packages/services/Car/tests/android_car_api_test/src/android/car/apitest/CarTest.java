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

package android.car.apitest;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.ICar;
import android.car.hardware.CarSensorManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
public class CarTest extends AndroidTestCase {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;

    private final Semaphore mConnectionWait = new Semaphore(0);

    private ICar mICar;

    private final ServiceConnection mConnectionListener = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            assertMainThread();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            assertMainThread();
            mICar = ICar.Stub.asInterface(service);
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
        Car car = Car.createCar(getContext(), mConnectionListener);
        assertFalse(car.isConnected());
        assertFalse(car.isConnecting());
        car.connect();
        // TODO fix race here
        // assertTrue(car.isConnecting()); // This makes test flaky.
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
        Car car = Car.createCar(getContext(), mConnectionListener);
        assertFalse(car.isConnected());
        assertFalse(car.isConnecting());
        car.connect();
        try {
            car.connect();
            fail("dobule connect should throw");
        } catch (IllegalStateException e) {
            // expected
        }
        car.disconnect();
    }

    public void testConstructorWithICar() throws Exception {
        Car car = Car.createCar(getContext(), mConnectionListener);
        car.connect();
        waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        assertNotNull(mICar);
        Car car2 = new Car(getContext(), mICar, null);
        assertTrue(car2.isConnected());
    }

    public void testCheckCarNotConnectedExceptionFromCarService() throws Exception {
        try {
            Car.checkCarNotConnectedExceptionFromCarService(new IllegalStateException());
            fail();
        } catch(IllegalStateException e) {
            // expected
        }
        try {
            Car.checkCarNotConnectedExceptionFromCarService(
                    new IllegalStateException(Car.CAR_NOT_CONNECTED_EXCEPTION_MSG));
            fail();
        } catch(CarNotConnectedException e) {
            // expected
        }
    }
}
