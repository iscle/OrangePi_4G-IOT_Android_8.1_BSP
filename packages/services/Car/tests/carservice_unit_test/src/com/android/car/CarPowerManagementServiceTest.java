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

package com.android.car;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.car.CarPowerManagementService.PowerEventProcessingHandler;
import com.android.car.CarPowerManagementService.PowerServiceEventListener;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.PowerHalService.PowerState;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
public class CarPowerManagementServiceTest extends AndroidTestCase {
    private static final String TAG = CarPowerManagementServiceTest.class.getSimpleName();
    private static final long WAIT_TIMEOUT_MS = 2000;
    private static final long WAIT_TIMEOUT_LONG_MS = 5000;

    private MockedPowerHalService mPowerHal;
    private SystemInterfaceImpl mSystemInterface;
    private CarPowerManagementService mService;
    private final PowerEventListener mPowerEventListener = new PowerEventListener();
    private PowerEventProcessingHandlerImpl mPowerEventProcessingHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPowerHal = new MockedPowerHalService(true /*isPowerStateSupported*/,
                true /*isDeepSleepAllowed*/, true /*isTimedWakeupAllowed*/);
        mSystemInterface = new SystemInterfaceImpl();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mService != null) {
            mService.release();
        }
    }

    public void testBootComplete() throws Exception {
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(0, 0);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
    }

    public void testDisplayOff() throws Exception {
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(0, 0);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
        // it will call display on for initial state
        assertTrue(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
        mPowerHal.setCurrentPowerState(new PowerState(PowerHalService.STATE_ON_DISP_OFF, 0));
        assertFalse(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
    }

    public void testDisplayOn() throws Exception {
        // start with display off
        mSystemInterface.setDisplayState(false);
        mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS);
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(0, 0);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);

        // display should be turned on as it started with off state.
        assertTrue(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
    }

    public void testShutdown() throws Exception {
        final int wakeupTime = 100;
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(0, wakeupTime);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
        assertTrue(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));

        mPowerHal.setCurrentPowerState(new PowerState(PowerHalService.STATE_SHUTDOWN_PREPARE,
                PowerHalService.FLAG_SHUTDOWN_IMMEDIATELY));
        assertStateReceived(PowerHalService.SET_SHUTDOWN_START, wakeupTime);
        assertFalse(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
        mPowerEventListener.waitForShutdown(WAIT_TIMEOUT_MS);
        mSystemInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    public void testShutdownWithProcessing() throws Exception {
        final long processingTimeMs = 3000;
        final int wakeupTime = 100;
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(processingTimeMs,
                wakeupTime);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
        assertTrue(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));

        mPowerHal.setCurrentPowerState(new PowerState(PowerHalService.STATE_SHUTDOWN_PREPARE, 0));
        mPowerEventProcessingHandler.waitForPrepareShutdown(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START,
                WAIT_TIMEOUT_LONG_MS, wakeupTime);
        assertFalse(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
        mPowerEventListener.waitForShutdown(WAIT_TIMEOUT_MS);
        mSystemInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    public void testSleepEntryAndWakeup() throws Exception {
        final int wakeupTime = 100;
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(0, wakeupTime);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
        assertTrue(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));

        mPowerHal.setCurrentPowerState(new PowerState(PowerHalService.STATE_SHUTDOWN_PREPARE,
                PowerHalService.FLAG_SHUTDOWN_PARAM_CAN_SLEEP));
        assertFalse(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        mPowerEventListener.waitForSleepEntry(WAIT_TIMEOUT_MS);
        int wakeupTimeReceived = mSystemInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertEquals(wakeupTime, wakeupTimeReceived);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerEventListener.waitForSleepExit(WAIT_TIMEOUT_MS);

    }

    public void testSleepEntryAndPowerOnWithProcessing() throws Exception {
        final long processingTimeMs = 3000;
        final int wakeupTime = 100;
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(processingTimeMs,
                wakeupTime);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
        assertTrue(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));

        mPowerHal.setCurrentPowerState(new PowerState(PowerHalService.STATE_SHUTDOWN_PREPARE,
                PowerHalService.FLAG_SHUTDOWN_PARAM_CAN_SLEEP));
        mPowerEventProcessingHandler.waitForPrepareShutdown(WAIT_TIMEOUT_MS);
        assertFalse(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY,
                WAIT_TIMEOUT_LONG_MS, 0);
        mPowerEventListener.waitForSleepEntry(WAIT_TIMEOUT_MS);
        // set power on here without notification. PowerManager should check the state after sleep
        // exit
        mPowerHal.setCurrentPowerState(new PowerState(PowerHalService.STATE_ON_DISP_OFF, 0), false);
        int wakeupTimeReceived = mSystemInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertEquals(wakeupTime, wakeupTimeReceived);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerEventListener.waitForSleepExit(WAIT_TIMEOUT_MS);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
    }

    public void testSleepEntryAndWakeUpForProcessing() throws Exception {
        final long processingTimeMs = 3000;
        final int wakeupTime = 100;
        mService = new CarPowerManagementService(mPowerHal, mSystemInterface);
        mService.init();
        mService.registerPowerEventListener(mPowerEventListener);
        mPowerEventProcessingHandler = new PowerEventProcessingHandlerImpl(processingTimeMs,
                wakeupTime);
        mService.registerPowerEventProcessingHandler(mPowerEventProcessingHandler);
        assertStateReceived(MockedPowerHalService.SET_BOOT_COMPLETE, 0);
        mPowerEventProcessingHandler.waitForPowerOn(WAIT_TIMEOUT_MS);
        assertTrue(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));

        mPowerHal.setCurrentPowerState(new PowerState(PowerHalService.STATE_SHUTDOWN_PREPARE,
                PowerHalService.FLAG_SHUTDOWN_PARAM_CAN_SLEEP));
        mPowerEventProcessingHandler.waitForPrepareShutdown(WAIT_TIMEOUT_MS);
        assertFalse(mSystemInterface.waitForDisplayStateChange(WAIT_TIMEOUT_MS));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY,
                WAIT_TIMEOUT_LONG_MS, 0);
        mPowerEventListener.waitForSleepEntry(WAIT_TIMEOUT_MS);
        mSystemInterface.setWakeupCausedByTimer(true);
        int wakeupTimeReceived = mSystemInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertEquals(wakeupTime, wakeupTimeReceived);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerEventListener.waitForSleepExit(WAIT_TIMEOUT_MS);
        // second processing after wakeup
        assertFalse(mSystemInterface.getDisplayState());
        mPowerEventProcessingHandler.waitForPrepareShutdown(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY,
                WAIT_TIMEOUT_LONG_MS, 0);
        mPowerEventListener.waitForSleepEntry(WAIT_TIMEOUT_MS);
        // PM will shutdown system as it was not woken-up due to timer and it is not power on.
        mSystemInterface.setWakeupCausedByTimer(false);
        wakeupTimeReceived = mSystemInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertEquals(wakeupTime, wakeupTimeReceived);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        assertStateReceived(PowerHalService.SET_SHUTDOWN_START, wakeupTime);
        mPowerEventListener.waitForShutdown(WAIT_TIMEOUT_MS);
        mSystemInterface.waitForShutdown(WAIT_TIMEOUT_MS);
        assertFalse(mSystemInterface.getDisplayState());
    }

    private void assertStateReceived(int expectedState, int expectedParam) throws Exception {
        int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_MS);
        assertEquals(expectedState, state[0]);
        assertEquals(expectedParam, state[1]);
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(int lastState, long timeoutMs,
            int expectedParamForShutdown) throws Exception {
        while (true) {
            int[] state = mPowerHal.waitForSend(timeoutMs);
            if (state[0] == PowerHalService.SET_SHUTDOWN_POSTPONE) {
                continue;
            }
            if (state[0] == lastState) {
                assertEquals(expectedParamForShutdown, state[1]);
                return;
            }
        }
    }

    private static void waitForSemaphore(Semaphore semaphore, long timeoutMs)
            throws InterruptedException {
        if (!semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("timeout");
        }
    }

    private static class SystemInterfaceImpl extends SystemInterface {

        private boolean mDisplayOn = true;
        private final Semaphore mDisplayStateWait = new Semaphore(0);
        private final Semaphore mShutdownWait = new Semaphore(0);
        private final Semaphore mSleepWait = new Semaphore(0);
        private final Semaphore mSleepExitWait = new Semaphore(0);
        private int mWakeupTime;
        private boolean mWakeupCausedByTimer = false;

        @Override
        public synchronized void setDisplayState(boolean on) {
            mDisplayOn = on;
            mDisplayStateWait.release();
        }

        public synchronized boolean getDisplayState() {
            return mDisplayOn;
        }

        public boolean waitForDisplayStateChange(long timeoutMs) throws Exception {
            waitForSemaphore(mDisplayStateWait, timeoutMs);
            return mDisplayOn;
        }

        @Override
        public void releaseAllWakeLocks() {
        }

        @Override
        public void shutdown() {
            mShutdownWait.release();
        }

        public void waitForShutdown(long timeoutMs) throws Exception {
            waitForSemaphore(mShutdownWait, timeoutMs);
        }

        @Override
        public void enterDeepSleep(int wakeupTimeSec) {
            mWakeupTime = wakeupTimeSec;
            mSleepWait.release();
            try {
                mSleepExitWait.acquire();
            } catch (InterruptedException e) {
            }
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            return true;
        }

        public int waitForSleepEntryAndWakeup(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepWait, timeoutMs);
            mSleepExitWait.release();
            return mWakeupTime;
        }

        @Override
        public void switchToPartialWakeLock() {
        }

        @Override
        public void switchToFullWakeLock() {
        }

        @Override
        public void startDisplayStateMonitoring(CarPowerManagementService service) {
        }

        @Override
        public void stopDisplayStateMonitoring() {
        }

        @Override
        public synchronized boolean isWakeupCausedByTimer() {
            Log.i(TAG, "isWakeupCausedByTimer:" + mWakeupCausedByTimer);
            return mWakeupCausedByTimer;
        }

        public synchronized void setWakeupCausedByTimer(boolean set) {
            mWakeupCausedByTimer = set;
        }
    }

    private class PowerEventListener implements PowerServiceEventListener {
        private final Semaphore mShutdownWait = new Semaphore(0);
        private final Semaphore mSleepEntryWait = new Semaphore(0);
        private final Semaphore mSleepExitWait = new Semaphore(0);

        @Override
        public void onShutdown() {
            mShutdownWait.release();
        }

        public void waitForShutdown(long timeoutMs) throws Exception {
            waitForSemaphore(mShutdownWait, timeoutMs);
        }

        @Override
        public void onSleepEntry() {
            mSleepEntryWait.release();
        }

        public void waitForSleepEntry(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepEntryWait, timeoutMs);
        }

        @Override
        public void onSleepExit() {
            mSleepExitWait.release();
        }

        public void waitForSleepExit(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepExitWait, timeoutMs);
        }
    }

    private class PowerEventProcessingHandlerImpl implements PowerEventProcessingHandler {
        private final long mProcessingTime;
        private final int mWakeupTime;
        private final Semaphore mPrepareShutdownWait = new Semaphore(0);
        private final Semaphore mOnPowerOnWait = new Semaphore(0);
        private boolean mShuttingDown;
        private boolean mDisplayOn;

        private PowerEventProcessingHandlerImpl(long processingTime, int wakeupTime) {
            mProcessingTime = processingTime;
            mWakeupTime = wakeupTime;
        }

        @Override
        public long onPrepareShutdown(boolean shuttingDown) {
            mShuttingDown = shuttingDown;
            mPrepareShutdownWait.release();
            return mProcessingTime;
        }
        public boolean waitForPrepareShutdown(long timeoutMs) throws Exception {
            waitForSemaphore(mPrepareShutdownWait, timeoutMs);
            return mShuttingDown;
        }

        @Override
        public void onPowerOn(boolean displayOn) {
            mDisplayOn = displayOn;
            mOnPowerOnWait.release();
        }

        public boolean waitForPowerOn(long timeoutMs) throws Exception {
            waitForSemaphore(mOnPowerOnWait, timeoutMs);
            return mDisplayOn;
        }

        @Override
        public int getWakeupTime() {
            return mWakeupTime;
        }
    }
}
