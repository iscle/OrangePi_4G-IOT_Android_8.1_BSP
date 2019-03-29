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
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


@SmallTest
@RequiresDevice
public class CarTest extends AndroidTestCase {

    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2000;

    private Car mCar;
    private DefaultServiceConnectionListener mServiceConnectionListener;

    public void testConnection() throws Exception {
        mServiceConnectionListener = new DefaultServiceConnectionListener();
        mCar = Car.createCar(getContext(), mServiceConnectionListener);
        assertFalse(mCar.isConnected());
        assertFalse(mCar.isConnecting());
        mCar.connect();
        mServiceConnectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        assertTrue(mServiceConnectionListener.isConnected());
        assertEquals(Car.CONNECTION_TYPE_EMBEDDED, mCar.getCarConnectionType());
        mCar.disconnect();
        assertFalse(mCar.isConnected());
        assertFalse(mCar.isConnecting());
    }

    protected class DefaultServiceConnectionListener implements ServiceConnection {
        private final Semaphore mConnectionWait = new Semaphore(0);

        private boolean mIsconnected = false;

        public synchronized boolean isConnected() {
            return mIsconnected;
        }

        public void waitForConnection(long timeoutMs) throws InterruptedException {
            mConnectionWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (this) {
                mIsconnected = false;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (this) {
                mIsconnected = true;
            }
            mConnectionWait.release();
        }
    }
}
