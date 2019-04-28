/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarDiagnosticManagerTest extends AndroidTestCase {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 5000;

    private final Semaphore mConnectionWait = new Semaphore(0);

    private Car mCar;
    private CarDiagnosticManager mCarDiagnosticManager;

    private final ServiceConnection mConnectionListener =
            new ServiceConnection() {

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    assertMainThread();
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
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
        mCar = Car.createCar(getContext(), mConnectionListener);
        mCar.connect();
        waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        mCarDiagnosticManager = (CarDiagnosticManager) mCar.getCarManager(Car.DIAGNOSTIC_SERVICE);
        assertNotNull(mCarDiagnosticManager);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mCar.disconnect();
    }

    /**
     * Test that we can read live frame data over the diagnostic manager
     *
     * @throws Exception
     */
    public void testLiveFrame() throws Exception {
        CarDiagnosticEvent liveFrame = mCarDiagnosticManager.getLatestLiveFrame();
        if (null != liveFrame) {
            assertTrue(liveFrame.isLiveFrame());
            assertFalse(liveFrame.isEmptyFrame());
        }
    }

    /**
     * Test that we can read well-formed freeze frame data over the diagnostic manager
     *
     * @throws Exception
     */
    public void testFreezeFrames() throws Exception {
        long[] timestamps = mCarDiagnosticManager.getFreezeFrameTimestamps();
        if (null != timestamps) {
            for (long timestamp : timestamps) {
                CarDiagnosticEvent freezeFrame = mCarDiagnosticManager.getFreezeFrame(timestamp);
                assertNotNull(freezeFrame);
                assertEquals(timestamp, freezeFrame.timestamp);
                assertTrue(freezeFrame.isFreezeFrame());
                assertFalse(freezeFrame.isEmptyFrame());
                assertNotSame("", freezeFrame.dtc);
            }
        }
    }
}
