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

import static android.car.CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarNotConnectedException;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import org.junit.Assert;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarAppFocusManagerTest extends CarApiTestBase {
    private static final String TAG = CarAppFocusManagerTest.class.getSimpleName();
    private CarAppFocusManager mManager;

    private final LooperThread mEventThread = new LooperThread();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mManager = (CarAppFocusManager) getCar().getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(mManager);

        // Request all application focuses and abandon them to ensure no active context is present
        // when test starts.
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        mManager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, owner);
        mManager.requestAppFocus(APP_FOCUS_TYPE_VOICE_COMMAND, owner);
        mManager.abandonAppFocus(owner);

        mEventThread.start();
        mEventThread.waitForReadyState();
    }

    public void testSetActiveNullListener() throws Exception {
        try {
            mManager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, null);
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
        FocusChangedListener listener = new FocusChangedListener();
        FocusChangedListener listener2 = new FocusChangedListener();
        mManager.addFocusListener(listener, 1);
        mManager.addFocusListener(listener2, 1);
        mManager.removeFocusListener(listener);
        mManager.removeFocusListener(listener2);
        mManager.removeFocusListener(listener2);  // Double-unregister is OK
    }

    public void testRegisterUnregisterSpecificApp() throws Exception {
        FocusChangedListener listener1 = new FocusChangedListener();
        FocusChangedListener listener2 = new FocusChangedListener();

        CarAppFocusManager manager = createManager();
        manager.addFocusListener(listener1, APP_FOCUS_TYPE_NAVIGATION);
        manager.addFocusListener(listener2, APP_FOCUS_TYPE_NAVIGATION);
        manager.addFocusListener(listener2, APP_FOCUS_TYPE_VOICE_COMMAND);

        manager.removeFocusListener(listener1, APP_FOCUS_TYPE_NAVIGATION);

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, new FocusOwnershipCallback()));

        // Unregistred from nav app, no events expected.
        assertFalse(listener1.waitForFocusChangeAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(listener2.waitForFocusChangeAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION, true));

        manager.removeFocusListener(listener2, APP_FOCUS_TYPE_NAVIGATION);
        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, new FocusOwnershipCallback()));
        assertFalse(listener2.waitForFocusChangeAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION, true));
        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager.requestAppFocus(APP_FOCUS_TYPE_VOICE_COMMAND, new FocusOwnershipCallback()));
        assertTrue(listener2.waitForFocusChangeAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_VOICE_COMMAND, true));

        manager.removeFocusListener(listener2, 2);
        manager.removeFocusListener(listener2, 2);    // Double-unregister is OK
    }

    public void testFocusChange() throws Exception {
        CarAppFocusManager manager1 = createManager();
        CarAppFocusManager manager2 = createManager();
        assertNotNull(manager2);
        final int[] emptyFocus = new int[0];

        Assert.assertArrayEquals(emptyFocus, manager1.getActiveAppTypes());
        FocusChangedListener change1 = new FocusChangedListener();
        FocusChangedListener change2 = new FocusChangedListener();
        FocusOwnershipCallback owner1 = new FocusOwnershipCallback();
        FocusOwnershipCallback owner2 = new FocusOwnershipCallback();
        manager1.addFocusListener(change1, APP_FOCUS_TYPE_NAVIGATION);
        manager1.addFocusListener(change1, APP_FOCUS_TYPE_VOICE_COMMAND);
        manager2.addFocusListener(change2, APP_FOCUS_TYPE_NAVIGATION);
        manager2.addFocusListener(change2, APP_FOCUS_TYPE_VOICE_COMMAND);


        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager1.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, owner1));
        assertTrue(owner1.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));
        int[] expectedFocuses = new int[] {APP_FOCUS_TYPE_NAVIGATION};
        Assert.assertArrayEquals(expectedFocuses, manager1.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_VOICE_COMMAND));
        assertFalse(manager2.isOwningFocus(owner2, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(change1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager1.requestAppFocus(APP_FOCUS_TYPE_VOICE_COMMAND, owner1));
        assertTrue(owner1.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_VOICE_COMMAND));
        expectedFocuses = new int[] {
                APP_FOCUS_TYPE_NAVIGATION,
                APP_FOCUS_TYPE_VOICE_COMMAND };
        assertTrue(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_NAVIGATION));
        assertTrue(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_VOICE_COMMAND));
        assertFalse(manager2.isOwningFocus(owner2, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                APP_FOCUS_TYPE_VOICE_COMMAND));
        Assert.assertArrayEquals(expectedFocuses, manager1.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertTrue(change1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, true));

        // this should be no-op
        change1.reset();
        change2.reset();
        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager1.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, owner1));
        assertTrue(owner1.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        Assert.assertArrayEquals(expectedFocuses, manager1.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertFalse(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));
        assertFalse(change1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));

        assertEquals(CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED,
                manager2.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, owner2));
        assertTrue(owner2.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        assertFalse(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_NAVIGATION));
        assertTrue(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(manager2.isOwningFocus(owner2, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                APP_FOCUS_TYPE_VOICE_COMMAND));
        Assert.assertArrayEquals(expectedFocuses, manager1.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(owner1.waitForOwnershipLossAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION));

        // no-op as it is not owning it
        change1.reset();
        change2.reset();
        manager1.abandonAppFocus(owner1, APP_FOCUS_TYPE_NAVIGATION);
        assertFalse(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_NAVIGATION));
        assertTrue(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(manager2.isOwningFocus(owner2, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                APP_FOCUS_TYPE_VOICE_COMMAND));
        Assert.assertArrayEquals(expectedFocuses, manager1.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());

        change1.reset();
        change2.reset();
        manager1.abandonAppFocus(owner1, APP_FOCUS_TYPE_VOICE_COMMAND);
        assertFalse(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_VOICE_COMMAND));
        assertTrue(manager2.isOwningFocus(owner2, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                APP_FOCUS_TYPE_VOICE_COMMAND));
        expectedFocuses = new int[] {APP_FOCUS_TYPE_NAVIGATION};
        Assert.assertArrayEquals(expectedFocuses, manager1.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(change2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertTrue(change1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, false));

        change1.reset();
        change2.reset();
        manager2.abandonAppFocus(owner2, APP_FOCUS_TYPE_NAVIGATION);
        assertFalse(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager1.isOwningFocus(owner1, APP_FOCUS_TYPE_VOICE_COMMAND));
        assertFalse(manager2.isOwningFocus(owner2, APP_FOCUS_TYPE_NAVIGATION));
        assertFalse(manager2.isOwningFocus(owner2,
                APP_FOCUS_TYPE_VOICE_COMMAND));
        expectedFocuses = emptyFocus;
        Assert.assertArrayEquals(expectedFocuses, manager1.getActiveAppTypes());
        Assert.assertArrayEquals(expectedFocuses, manager2.getActiveAppTypes());
        assertTrue(change1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, false));

        manager1.removeFocusListener(change1);
        manager2.removeFocusListener(change2);
    }

    public void testFilter() throws Exception {
        CarAppFocusManager manager1 = createManager(getContext(), mEventThread);
        CarAppFocusManager manager2 = createManager(getContext(), mEventThread);

        Assert.assertArrayEquals(new int[0], manager1.getActiveAppTypes());
        Assert.assertArrayEquals(new int[0], manager2.getActiveAppTypes());

        FocusChangedListener listener1 = new FocusChangedListener();
        FocusChangedListener listener2 = new FocusChangedListener();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        manager1.addFocusListener(listener1, APP_FOCUS_TYPE_NAVIGATION);
        manager1.addFocusListener(listener1, APP_FOCUS_TYPE_VOICE_COMMAND);
        manager2.addFocusListener(listener2, APP_FOCUS_TYPE_NAVIGATION);

        assertEquals(APP_FOCUS_REQUEST_SUCCEEDED,
                manager1.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        assertTrue(listener1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));

        listener1.reset();
        listener2.reset();
        assertEquals(APP_FOCUS_REQUEST_SUCCEEDED,
                manager1.requestAppFocus(APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_VOICE_COMMAND));

        assertTrue(listener1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, true));

        listener1.reset();
        listener2.reset();
        manager1.abandonAppFocus(owner, APP_FOCUS_TYPE_VOICE_COMMAND);
        assertTrue(listener1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, false));

        listener1.reset();
        listener2.reset();
        manager1.abandonAppFocus(owner, APP_FOCUS_TYPE_NAVIGATION);
        assertTrue(listener1.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, false));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, false));
    }

    private CarAppFocusManager createManager()
            throws CarNotConnectedException, InterruptedException {
        return createManager(getContext(), mEventThread);
    }

    private static CarAppFocusManager createManager(Context context,
            LooperThread eventThread) throws InterruptedException, CarNotConnectedException {
        Car car = createCar(context, eventThread);
        CarAppFocusManager manager = (CarAppFocusManager) car.getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(manager);
        return manager;
    }

    private static Car createCar(Context context, LooperThread eventThread)
            throws InterruptedException {
        DefaultServiceConnectionListener connectionListener =
                new DefaultServiceConnectionListener();
        Car car = Car.createCar(context, connectionListener, eventThread.mHandler);
        assertNotNull(car);
        car.connect();
        connectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        return car;
    }

    public void testMultipleChangeListenersPerManager() throws Exception {
        CarAppFocusManager manager = createManager();
        FocusChangedListener listener = new FocusChangedListener();
        FocusChangedListener listener2 = new FocusChangedListener();
        FocusOwnershipCallback owner = new FocusOwnershipCallback();
        manager.addFocusListener(listener, APP_FOCUS_TYPE_NAVIGATION);
        manager.addFocusListener(listener, APP_FOCUS_TYPE_VOICE_COMMAND);
        manager.addFocusListener(listener2, APP_FOCUS_TYPE_NAVIGATION);

        assertEquals(APP_FOCUS_REQUEST_SUCCEEDED,
                manager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_NAVIGATION));

        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, true));

        listener.reset();
        listener2.reset();
        assertEquals(APP_FOCUS_REQUEST_SUCCEEDED,
                manager.requestAppFocus(APP_FOCUS_TYPE_VOICE_COMMAND, owner));
        assertTrue(owner.waitForOwnershipGrantAndAssert(
                DEFAULT_WAIT_TIMEOUT_MS, APP_FOCUS_TYPE_VOICE_COMMAND));

        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, true));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, true));

        listener.reset();
        listener2.reset();
        manager.abandonAppFocus(owner, APP_FOCUS_TYPE_VOICE_COMMAND);
        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, false));
        assertFalse(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_VOICE_COMMAND, false));

        listener.reset();
        listener2.reset();
        manager.abandonAppFocus(owner, APP_FOCUS_TYPE_NAVIGATION);
        assertTrue(listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, false));
        assertTrue(listener2.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                APP_FOCUS_TYPE_NAVIGATION, false));
    }

    private class FocusChangedListener implements CarAppFocusManager.OnAppFocusChangedListener {
        private volatile int mLastChangeAppType;
        private volatile boolean mLastChangeAppActive;
        private volatile Semaphore mChangeWait = new Semaphore(0);

        boolean waitForFocusChangeAndAssert(long timeoutMs, int expectedAppType,
                boolean expectedAppActive) throws Exception {

            if (!mChangeWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }

            assertEquals(expectedAppType, mLastChangeAppType);
            assertEquals(expectedAppActive, mLastChangeAppActive);
            return true;
        }

        void reset() throws InterruptedException {
            mLastChangeAppType = 0;
            mLastChangeAppActive = false;
            mChangeWait.drainPermits();
        }

        @Override
        public void onAppFocusChanged(int appType, boolean active) {
            assertEventThread();
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

        boolean waitForOwnershipLossAndAssert(long timeoutMs, int expectedAppType)
                throws Exception {
            if (!mLossEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastLossEvent);
            return true;
        }

        boolean waitForOwnershipGrantAndAssert(long timeoutMs, int expectedAppType)
                throws Exception {
            if (!mGrantEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastGrantEvent);
            return true;
        }

        @Override
        public void onAppFocusOwnershipLost(int appType) {
            Log.i(TAG, "onAppFocusOwnershipLost " + appType);
            assertEventThread();
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

    private void assertEventThread() {
        assertEquals(mEventThread, Thread.currentThread());
    }

    private static class LooperThread extends Thread {

        private final Object mReadySync = new Object();

        volatile Handler mHandler;

        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler();

            synchronized (mReadySync) {
                mReadySync.notifyAll();
            }

            Looper.loop();
        }

        void waitForReadyState() throws InterruptedException {
            synchronized (mReadySync) {
                mReadySync.wait(DEFAULT_WAIT_TIMEOUT_MS);
            }
        }
    }
}
