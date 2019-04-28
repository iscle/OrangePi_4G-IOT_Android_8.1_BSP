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
package com.android.car.test;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class AppFocusTest extends MockedCarTestBase {
    private static final String TAG = AppFocusTest.class.getSimpleName();
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 1000;

    public void testFocusChange() throws Exception {
        CarAppFocusManager manager = (CarAppFocusManager) getCar().getCarManager(
                Car.APP_FOCUS_SERVICE);
        FocusChangedListener listener = new FocusChangedListener();
        FocusOwnershipCallback ownershipListener = new FocusOwnershipCallback();
        manager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        manager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        manager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, ownershipListener);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true);
        manager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, ownershipListener);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true);
        manager.abandonAppFocus(ownershipListener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false);
        manager.abandonAppFocus(ownershipListener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false);
        manager.removeFocusListener(listener);
    }

    private class FocusChangedListener implements CarAppFocusManager.OnAppFocusChangedListener {
        private int mLastChangeAppType;
        private boolean mLastChangeAppActive;
        private final Semaphore mChangeWait = new Semaphore(0);

        public boolean waitForFocusChangeAndAssert(long timeoutMs, int expectedAppType,
                boolean expectedAppActive) throws Exception {
            if (!mChangeWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastChangeAppType);
            assertEquals(expectedAppActive, mLastChangeAppActive);
            return true;
        }

        @Override
        public void onAppFocusChanged(int appType, boolean active) {
            Log.i(TAG, "onAppFocusChanged appType=" + appType + " active=" + active);
            mLastChangeAppType = appType;
            mLastChangeAppActive = active;
            mChangeWait.release();
        }
    }

    private class FocusOwnershipCallback
            implements CarAppFocusManager.OnAppFocusOwnershipCallback {
        private int mLastLossEvent;
        private final Semaphore mLossEventWait = new Semaphore(0);

        private int mLastGrantEvent;
        private final Semaphore mGrantEventWait = new Semaphore(0);

        public boolean waitForOwnershipLossAndAssert(long timeoutMs, int expectedLossAppType)
                throws Exception {
            if (!mLossEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedLossAppType, mLastLossEvent);
            return true;
        }

        public boolean waitForOwnershipGrantAndAssert(long timeoutMs, int expectedGrantAppType)
                throws Exception {
            if (!mGrantEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedGrantAppType, mLastGrantEvent);
            return true;
        }

        @Override
        public void onAppFocusOwnershipLost(int appType) {
            Log.i(TAG, "onAppFocusOwnershipLost " + appType);
            mLastLossEvent = appType;
            mLossEventWait.release();
        }

        @Override
        public void onAppFocusOwnershipGranted(int appType) {
            Log.i(TAG, "onAppFocusOwnershipGranted " + appType);
            mLastGrantEvent = appType;
            mGrantEventWait.release();
        }
    }
}
