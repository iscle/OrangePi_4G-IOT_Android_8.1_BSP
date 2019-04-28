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

import android.hardware.automotive.vehicle.V2_0.VehicleApPowerBootupReason;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerSetState;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerState;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateConfigFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;

import com.google.android.collect.Lists;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarPowerManagementTest extends MockedCarTestBase {

    private final PowerStatePropertyHandler mPowerStateHandler = new PowerStatePropertyHandler();

    private void setupPowerPropertyAndStart(boolean allowSleep) {
        addProperty(VehicleProperty.AP_POWER_STATE, mPowerStateHandler)
                .setConfigArray(Lists.newArrayList(
                        allowSleep ? VehicleApPowerStateConfigFlag.ENABLE_DEEP_SLEEP_FLAG : 0));

        addStaticProperty(VehicleProperty.AP_POWER_BOOTUP_REASON,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_BOOTUP_REASON)
                        .addIntValue(VehicleApPowerBootupReason.USER_POWER_ON)
                        .build());

        reinitializeMockedHal();
    }

    public void testImmediateShutdown() throws Exception {
        setupPowerPropertyAndStart(true);
        assertBootComplete();
        mPowerStateHandler.sendPowerState(
                VehicleApPowerState.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
        mPowerStateHandler.waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS,
                VehicleApPowerSetState.SHUTDOWN_START);
        mPowerStateHandler.sendPowerState(VehicleApPowerState.ON_FULL, 0);
    }

    public void testDisplayOnOff() throws Exception {
        setupPowerPropertyAndStart(true);
        assertBootComplete();
        for (int i = 0; i < 2; i++) {
            mPowerStateHandler.sendPowerState(VehicleApPowerState.ON_DISP_OFF, 0);
            waitForFakeDisplayState(false);
            mPowerStateHandler.sendPowerState(VehicleApPowerState.ON_FULL, 0);
            waitForFakeDisplayState(true);
        }
    }

    /* TODO make deep sleep work to test this
    public void testSleepEntry() throws Exception {
        assertBootComplete();
        mPowerStateHandler.sendPowerState(
                VehicleApPowerState.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        assertResponse(VehicleApPowerSetState.DEEP_SLEEP_ENTRY, 0);
        assertResponse(VehicleApPowerSetState.DEEP_SLEEP_EXIT, 0);
        mPowerStateHandler.sendPowerState(
                VehicleApPowerState.ON_FULL,
                0);
    }*/

    private void assertResponse(int expectedResponseState, int expectedResponseParam)
            throws Exception {
        LinkedList<int[]> setEvents = mPowerStateHandler.waitForStateSetAndGetAll(
                DEFAULT_WAIT_TIMEOUT_MS, expectedResponseState);
        int[] last = setEvents.getLast();
        assertEquals(expectedResponseState, last[0]);
        assertEquals(expectedResponseParam, last[1]);
    }

    private void assertBootComplete() throws Exception {
        mPowerStateHandler.waitForSubscription(DEFAULT_WAIT_TIMEOUT_MS);
        LinkedList<int[]> setEvents = mPowerStateHandler.waitForStateSetAndGetAll(
                DEFAULT_WAIT_TIMEOUT_MS, VehicleApPowerSetState.BOOT_COMPLETE);
        int[] first = setEvents.getFirst();
        assertEquals(VehicleApPowerSetState.BOOT_COMPLETE, first[0]);
        assertEquals(0, first[1]);
    }

    private class PowerStatePropertyHandler implements VehicleHalPropertyHandler {

        private int mPowerState = VehicleApPowerState.ON_FULL;
        private int mPowerParam = 0;

        private final Semaphore mSubscriptionWaitSemaphore = new Semaphore(0);
        private final Semaphore mSetWaitSemaphore = new Semaphore(0);
        private LinkedList<int[]> mSetStates = new LinkedList<>();

        @Override
        public void onPropertySet(VehiclePropValue value) {
            ArrayList<Integer> v = value.value.int32Values;
            synchronized (this) {
                mSetStates.add(new int[] {
                        v.get(VehicleApPowerStateIndex.STATE),
                        v.get(VehicleApPowerStateIndex.ADDITIONAL)
                });
            }
            mSetWaitSemaphore.release();
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_STATE)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(mPowerState, mPowerParam)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, int zones, float sampleRate) {
            mSubscriptionWaitSemaphore.release();
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            //ignore
        }

        private synchronized void setCurrentState(int state, int param) {
            mPowerState = state;
            mPowerParam = param;
        }

        private void waitForSubscription(long timeoutMs) throws Exception {
            if (!mSubscriptionWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("waitForSubscription timeout");
            }
        }

        private LinkedList<int[]> waitForStateSetAndGetAll(long timeoutMs, int expectedSet)
                throws Exception {
            while (true) {
                if (!mSetWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                    fail("waitForStateSetAndGetAll timeout");
                }
                synchronized (this) {
                    boolean found = false;
                    for (int[] state : mSetStates) {
                        if (state[0] == expectedSet) {
                            found = true;
                        }
                    }
                    if (found) {
                        LinkedList<int[]> res = mSetStates;
                        mSetStates = new LinkedList<>();
                        return res;
                    }
                }
            }
        }

        private void sendPowerState(int state, int param) {
            getMockedVehicleHal().injectEvent(
                    VehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_STATE)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(state, param)
                            .build());
        }
    }
}
