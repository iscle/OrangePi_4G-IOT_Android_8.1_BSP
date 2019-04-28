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

import android.app.Service;
import android.car.Car;
import android.car.CarProjectionManager;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.test.suitebuilder.annotation.MediumTest;

@MediumTest
public class CarProjectionManagerTest extends CarApiTestBase {
    private static final String TAG = CarProjectionManagerTest.class.getSimpleName();

    private final CarProjectionManager.CarProjectionListener mListener =
            new CarProjectionManager.CarProjectionListener() {
                @Override
                public void onVoiceAssistantRequest(boolean fromLongPress) {
                    //void
                }
            };

    private CarProjectionManager mManager;

    public static class TestService extends Service {
        public static Object mLock = new Object();
        private static boolean sBound;
        private final Binder mBinder = new Binder() {};

        private static synchronized void setBound(boolean bound) {
            sBound = bound;
        }

        public static synchronized boolean getBound() {
            return sBound;
        }

        @Override
        public IBinder onBind(Intent intent) {
            setBound(true);
            synchronized (mLock) {
                mLock.notify();
            }
            return mBinder;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mManager = (CarProjectionManager) getCar().getCarManager(Car.PROJECTION_SERVICE);
        assertNotNull(mManager);
    }

    public void testSetUnsetListeners() throws Exception {
        mManager.registerProjectionListener(
                mListener, CarProjectionManager.PROJECTION_VOICE_SEARCH);
        mManager.unregisterProjectionListener();
    }

    public void testRegisterListenersHandleBadInput() throws Exception {
        try {
            mManager.registerProjectionListener(null, CarProjectionManager.PROJECTION_VOICE_SEARCH);
            fail();
        } catch (IllegalArgumentException e) {
            // expected.
        }
    }

    public void testRegisterProjectionRunner() throws Exception {
        Intent intent = new Intent(getContext(), TestService.class);
        assertFalse(TestService.getBound());
        mManager.registerProjectionRunner(intent);
        synchronized (TestService.mLock) {
            try {
                TestService.mLock.wait(1000);
            } catch (InterruptedException e) {
                // Do nothing
            }
        }
        assertTrue(TestService.getBound());
        mManager.unregisterProjectionRunner(intent);
    }
}
