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

import android.util.Log;

import com.android.car.hal.PowerHalService;
import com.android.car.hal.VehicleHal;

import java.util.LinkedList;

public class MockedPowerHalService extends PowerHalService {
    private static final String TAG = MockedPowerHalService.class.getSimpleName();

    private final boolean mIsPowerStateSupported;
    private final boolean mIsDeepSleepAllowed;
    private final boolean mIsTimedWakeupAllowed;
    private PowerState mCurrentPowerState = new PowerState(PowerHalService.STATE_ON_FULL, 0);
    private PowerEventListener mListener;

    private final LinkedList<int[]> mSentStates = new LinkedList<>();

    public MockedPowerHalService(boolean isPowerStateSupported, boolean isDeepSleepAllowed,
            boolean isTimedWakeupAllowed) {
        super(new VehicleHal(null, null, null, null, null, null, null, null, null));
        mIsPowerStateSupported = isPowerStateSupported;
        mIsDeepSleepAllowed = isDeepSleepAllowed;
        mIsTimedWakeupAllowed = isTimedWakeupAllowed;
    }

    @Override
    public synchronized void setListener(PowerEventListener listener) {
        mListener = listener;
    }

    @Override
    public void sendBootComplete() {
        Log.i(TAG, "sendBootComplete");
        doSendState(SET_BOOT_COMPLETE, 0);
    }

    @Override
    public void sendSleepEntry() {
        Log.i(TAG, "sendSleepEntry");
        doSendState(SET_DEEP_SLEEP_ENTRY, 0);
    }

    @Override
    public void sendSleepExit() {
        Log.i(TAG, "sendSleepExit");
        doSendState(SET_DEEP_SLEEP_EXIT, 0);
    }

    @Override
    public void sendShutdownPostpone(int postponeTimeMs) {
        Log.i(TAG, "sendShutdownPostpone");
        doSendState(SET_SHUTDOWN_POSTPONE, postponeTimeMs);
    }

    @Override
    public void sendShutdownStart(int wakeupTimeSec) {
        Log.i(TAG, "sendShutdownStart");
        doSendState(SET_SHUTDOWN_START, wakeupTimeSec);
    }

    @Override
    public void sendDisplayOn() {
        Log.i(TAG, "sendDisplayOn");
        doSendState(SET_DISPLAY_ON, 0);
    }

    @Override
    public void sendDisplayOff() {
        Log.i(TAG, "sendDisplayOff");
        doSendState(SET_DISPLAY_OFF, 0);
    }

    public synchronized int[] waitForSend(long timeoutMs) throws Exception {
        if (mSentStates.size() == 0) {
            wait(timeoutMs);
        }
        return mSentStates.removeFirst();
    }

    private synchronized void doSendState(int state, int param) {
        int[] toSend = new int[] {state, param};
        mSentStates.addLast(toSend);
        notifyAll();
    }

    @Override
    public boolean isPowerStateSupported() {
        return mIsPowerStateSupported;
    }

    @Override
    public boolean isDeepSleepAllowed() {
        return mIsDeepSleepAllowed;
    }

    @Override
    public boolean isTimedWakeupAllowed() {
        return mIsTimedWakeupAllowed;
    }

    @Override
    public synchronized PowerState getCurrentPowerState() {
        return mCurrentPowerState;
    }

    public void setCurrentPowerState(PowerState state) {
        setCurrentPowerState(state, true);
    }

    public void setCurrentPowerState(PowerState state, boolean notify) {
        PowerEventListener listener;
        synchronized (this) {
            mCurrentPowerState = state;
            listener = mListener;
        }
        if (listener != null && notify) {
            listener.onApPowerStateChange(state);
        }
    }
}
