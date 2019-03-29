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
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.test.AndroidTestCase;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CarApiTestBase extends AndroidTestCase {
    protected static final long DEFAULT_WAIT_TIMEOUT_MS = 1000;

    private Car mCar;

    private final DefaultServiceConnectionListener mConnectionListener =
            new DefaultServiceConnectionListener();

    protected void assertMainThread() {
        assertTrue(Looper.getMainLooper().isCurrentThread());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCar = Car.createCar(getContext(), mConnectionListener, null);
        mCar.connect();
        mConnectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mCar.disconnect();
    }

    protected synchronized Car getCar() {
        return mCar;
    }

    protected class DefaultServiceConnectionListener implements ServiceConnection {
        private final Semaphore mConnectionWait = new Semaphore(0);

        public void waitForConnection(long timeoutMs) throws InterruptedException {
            mConnectionWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            assertMainThread();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            assertMainThread();
            mConnectionWait.release();
        }
    }
}
