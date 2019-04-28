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

import android.support.car.Car;
import android.support.car.CarAppFocusManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarAppFocusManagerTest extends CarApiTestBase {
    private static final String TAG = CarAppFocusManagerTest.class.getSimpleName();
    private CarAppFocusManager mManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mManager = (CarAppFocusManager) getCar().getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(mManager);

        // Request all application focuses and abandon them to ensure no active context is present
        // when test starts.
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner);
        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner);
        mManager.abandonAppFocus(owner);
    }

    public void testSetActiveNullListener() throws Exception {
        try {
            mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testRegisterNull() throws Exception {
        try {
            mManager.addFocusListener(null, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testRegisterUnregister() throws Exception {
        FocusChangedListener listener = new FocusChangedListener();
        FocusChangedListener listener2 = new FocusChangedListener();
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        mManager.addFocusListener(listener2, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        mManager.removeFocusListener(listener);
        mManager.removeFocusListener(listener2);
    }

    public void testFocusChange() throws Exception {
        DefaultCarConnectionCallback connectionCallbacks =
                new DefaultCarConnectionCallback();
        Car car2 = Car.createCar(getContext(), connectionCallbacks, null);
        car2.connect();
        connectionCallbacks.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        CarAppFocusManager manager2 = (CarAppFocusManager)
                car2.getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(manager2);
        final int[] emptyFocus = new int[0];

        FocusChangedListener change = new FocusChangedListener();
        FocusChangedListener change2 = new FocusChangedListener();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        FocusOwnershipCallback owner2 = new FocusOwnershipCallback();
        mManager.addFocusListener(change, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mManager.addFocusListener(change, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        manager2.addFocusListener(change2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        manager2.addFocusListener(change2, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);

        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner);
        assertTrue(owner.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        assertTrue(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertFalse(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner2));

        assertTrue(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(change.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner);
        assertTrue(owner.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND,
                owner2));
        assertTrue(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertTrue(change.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));

        // this should be no-op
        change.reset();
        change2.reset();
        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner);
        assertTrue(owner.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        assertFalse(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertFalse(change.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        manager2.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2);
        assertTrue(owner2.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        assertFalse(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner2));
        assertTrue(owner.waitForOwnershipLossAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        // no-op as it is not owning it
        change.reset();
        change2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertFalse(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner2));

        change.reset();
        change2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        assertFalse(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertFalse(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner2));
        assertTrue(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertTrue(change.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));

        change.reset();
        change2.reset();
        manager2.abandonAppFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertFalse(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertFalse(mManager.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertFalse(manager2.isOwningFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner2));
        assertTrue(change.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
        mManager.removeFocusListener(change);
        manager2.removeFocusListener(change2);
    }

    public void testFilter() throws Exception {
        DefaultCarConnectionCallback connectionCallbacks =
                new DefaultCarConnectionCallback();
        Car car2 = Car.createCar(getContext(), connectionCallbacks);
        car2.connect();
        connectionCallbacks.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        CarAppFocusManager manager2 = (CarAppFocusManager)
                car2.getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(manager2);

        FocusChangedListener listener = new FocusChangedListener();
        FocusChangedListener listener2 = new FocusChangedListener();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        manager2.addFocusListener(listener2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);

        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner);
        assertTrue(owner.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        listener.reset();
        listener2.reset();
        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner);
        assertTrue(owner.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));

        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
    }

    public void testMultipleChangeListenersPerManager() throws Exception {
        FocusChangedListener listener = new FocusChangedListener();
        FocusChangedListener listener2 = new FocusChangedListener();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        mManager.addFocusListener(listener2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);

        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner);
        assertTrue(owner.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        listener.reset();
        listener2.reset();
        mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner);
        assertTrue(owner.waitForOwnershipGrantAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));

        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
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

        public void reset() {
            mLastChangeAppType = 0;
            mLastChangeAppActive = false;
        }

        @Override
        public void onAppFocusChanged(CarAppFocusManager manager, int appType, boolean active) {
            Log.i(TAG, "onAppFocusChanged appType=" + appType + " active=" + active);
            assertMainThread();
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

        public boolean waitForOwnershipLossAndAssert(long timeoutMs, int expectedAppType)
                throws Exception {
            if (!mLossEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastLossEvent);
            return true;
        }

        public boolean waitForOwnershipGrantAndAssert(long timeoutMs, int expectedAppType)
                throws Exception {
            if (!mGrantEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastGrantEvent);
            return true;
        }

        @Override
        public void onAppFocusOwnershipLost(CarAppFocusManager manager, int appType) {
            Log.i(TAG, "onAppFocusOwnershipLost " + appType);
            assertMainThread();
            mLastLossEvent = appType;
            mLossEventWait.release();
        }

        @Override
        public void onAppFocusOwnershipGranted(CarAppFocusManager manager, int appType) {
            Log.i(TAG, "onAppFocusOwnershipGranted " + appType);
            assertMainThread();
            mLastGrantEvent = appType;
            mGrantEventWait.release();
        }
    }
}
