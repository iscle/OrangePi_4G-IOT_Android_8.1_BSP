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

import static android.car.CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;


@SmallTest
@RequiresDevice
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
            mManager.addFocusListener(null, 0);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testRegisterUnregister() throws Exception {
        FocusChangedListerner listener = new FocusChangedListerner();
        FocusChangedListerner listener2 = new FocusChangedListerner();
        mManager.addFocusListener(listener, 1);
        mManager.addFocusListener(listener2, 1);
        mManager.removeFocusListener(listener);
        mManager.removeFocusListener(listener2);
    }

    public void testFocusChange() throws Exception {
        DefaultServiceConnectionListener connectionListener =
                new DefaultServiceConnectionListener();
        Car car2 = Car.createCar(getContext(), connectionListener, null);
        car2.connect();
        connectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        CarAppFocusManager manager2 = (CarAppFocusManager)
                car2.getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(manager2);
        final int[] emptyFocus = new int[0];

        Assert.assertArrayEquals(emptyFocus, mManager.getActiveAppTypes());
        FocusChangedListerner change = new FocusChangedListerner();
        FocusChangedListerner change2 = new FocusChangedListerner();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        FocusOwnershipCallback owner2 = new FocusOwnershipCallback();
        mManager.addFocusListener(change, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mManager.addFocusListener(change, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        manager2.addFocusListener(change2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        manager2.addFocusListener(change2, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));
        int[] expectedFocuses = new int[] {CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION};
        Assert.assertArrayEquals(expectedFocuses, mManager.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertFalse(manager2.isOwningFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(change2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(change.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_VOICE_COMMAND));
        expectedFocuses = new int[] {
            CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION,
            CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND };
        assertTrue(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertTrue(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertFalse(manager2.isOwningFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        Assert.assertArrayEquals(expectedFocuses, mManager.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(change2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertTrue(change.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));

        // this should be no-op
        change.reset();
        change2.reset();
        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        Assert.assertArrayEquals(expectedFocuses, mManager.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertFalse(change2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertFalse(change.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager2.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertTrue(owner2.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        assertFalse(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertTrue(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(manager2.isOwningFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
              CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        Assert.assertArrayEquals(expectedFocuses, mManager.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(owner.waitForOwnershipLossAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        // no-op as it is not owning it
        change.reset();
        change2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertFalse(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertTrue(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(manager2.isOwningFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        Assert.assertArrayEquals(expectedFocuses, mManager.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());

        change.reset();
        change2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        assertFalse(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(manager2.isOwningFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        expectedFocuses = new int[] {CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION};
        Assert.assertArrayEquals(expectedFocuses, mManager.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(change2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertTrue(change.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));

        change.reset();
        change2.reset();
        manager2.abandonAppFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertFalse(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(mManager.isOwningFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        assertFalse(manager2.isOwningFocus(owner2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND));
        expectedFocuses = emptyFocus;
        Assert.assertArrayEquals(expectedFocuses, mManager.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(change.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
        mManager.removeFocusListener(change);
        manager2.removeFocusListener(change2);
    }

    public void testFilter() throws Exception {
        DefaultServiceConnectionListener connectionListener =
                new DefaultServiceConnectionListener();
        Car car2 = Car.createCar(getContext(), connectionListener);
        car2.connect();
        connectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        CarAppFocusManager manager2 = (CarAppFocusManager)
                car2.getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(manager2);

        Assert.assertArrayEquals(new int[0], mManager.getActiveAppTypes());

        FocusChangedListerner listener = new FocusChangedListerner();
        FocusChangedListerner listener2 = new FocusChangedListerner();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        manager2.addFocusListener(listener2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        listener.reset();
        listener2.reset();
        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_VOICE_COMMAND));

        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertFalse(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertFalse(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
        assertTrue(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
    }

    public void testMultipleChangeListenersPerManager() throws Exception {
        FocusChangedListerner listener = new FocusChangedListerner();
        FocusChangedListerner listener2 = new FocusChangedListerner();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mManager.addFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        mManager.addFocusListener(listener2, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                 CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true));

        listener.reset();
        listener2.reset();
        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                mManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_VOICE_COMMAND));

        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertFalse(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertFalse(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false));

        listener.reset();
        listener2.reset();
        mManager.abandonAppFocus(owner, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        assertTrue(listener.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
        assertTrue(listener2.waitForFocusChangedAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false));
    }

    private class FocusChangedListerner implements CarAppFocusManager.OnAppFocusChangedListener {
        private int mLastChangeAppType;
        private boolean mLastChangeAppActive;
        private final Semaphore mChangeWait = new Semaphore(0);

        public boolean waitForFocusChangedAndAssert(long timeoutMs, int expectedAppType,
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
        public void onAppFocusChanged(int appType, boolean active) {
            Log.i(TAG, "onAppFocusChange appType=" + appType + " active=" + active);
            assertMainThread();
            mLastChangeAppType = appType;
            mLastChangeAppActive = active;
            mChangeWait.release();
        }
    }

    private class FocusOwnershipCallback
            implements CarAppFocusManager.OnAppFocusOwnershipCallback {
        private volatile int mLastLossEvent;
        private final Semaphore mLossEventWait = new Semaphore(0);

        private volatile int mLastGrantEvent;
        private final Semaphore mGrantEventWait = new Semaphore(0);

        public boolean waitForOwnershipGrantAndAssert(long timeoutMs, int expectedAppType)
                throws Exception {
            if (!mGrantEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastGrantEvent);
            return true;
        }

        public boolean waitForOwnershipLossAndAssert(long timeoutMs, int expectedAppType)
                throws Exception {
            if (!mLossEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastLossEvent);
            return true;
        }

        @Override
        public void onAppFocusOwnershipLost(int appType) {
            Log.i(TAG, "onAppFocusOwnershipLoss " + appType);
            assertMainThread();
            mLastLossEvent = appType;
            mLossEventWait.release();
        }

        @Override
        public void onAppFocusOwnershipGranted(int appType) {
            Log.i(TAG, "onAppFocusOwnershipGranted " + appType);
            assertMainThread();
            mLastGrantEvent = appType;
            mGrantEventWait.release();
        }
    }
}
