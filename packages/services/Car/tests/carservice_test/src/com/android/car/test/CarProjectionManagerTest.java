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
import android.car.CarProjectionManager;
import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.view.KeyEvent;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarProjectionManagerTest extends MockedCarTestBase {
    private static final String TAG = CarProjectionManagerTest.class.getSimpleName();

    private final Semaphore mLongAvailable = new Semaphore(0);
    private final Semaphore mAvailable = new Semaphore(0);

    private final CarProjectionManager.CarProjectionListener mListener =
            new CarProjectionManager.CarProjectionListener() {
                @Override
                public void onVoiceAssistantRequest(boolean fromLongPress) {
                    if (fromLongPress) {
                        mLongAvailable.release();
                    } else {
                        mAvailable.release();
                    }
                }
            };

    private CarProjectionManager mManager;

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.HW_KEY_INPUT, new PropertyHandler())
                .setAccess(VehiclePropertyAccess.READ);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mManager = (CarProjectionManager) getCar().getCarManager(Car.PROJECTION_SERVICE);
    }

    public void testShortPressListener() throws Exception {
        mManager.registerProjectionListener(
                mListener,
                CarProjectionManager.PROJECTION_VOICE_SEARCH);
        assertEquals(0, mAvailable.availablePermits());
        assertEquals(0, mLongAvailable.availablePermits());
        sendVoiceKey(false);
        assertTrue(mAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(0, mLongAvailable.availablePermits());
    }

    public void testLongPressListener() throws Exception {
        mManager.registerProjectionListener(
                mListener,
                CarProjectionManager.PROJECTION_LONG_PRESS_VOICE_SEARCH);
        assertEquals(0, mLongAvailable.availablePermits());
        assertEquals(0, mAvailable.availablePermits());
        sendVoiceKey(true);
        assertTrue(mLongAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(0, mAvailable.availablePermits());
    }

    public void testMixedPressListener() throws Exception {
        mManager.registerProjectionListener(
                mListener,
                CarProjectionManager.PROJECTION_LONG_PRESS_VOICE_SEARCH
                | CarProjectionManager.PROJECTION_VOICE_SEARCH);
        assertEquals(0, mLongAvailable.availablePermits());
        assertEquals(0, mAvailable.availablePermits());
        sendVoiceKey(true);
        assertTrue(mLongAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(0, mAvailable.availablePermits());

        assertEquals(0, mLongAvailable.availablePermits());
        assertEquals(0, mAvailable.availablePermits());
        sendVoiceKey(false);
        assertTrue(mAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(0, mLongAvailable.availablePermits());
    }

    public void sendVoiceKey(boolean isLong) throws InterruptedException {
        int[] values = {VehicleHwKeyInputAction.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, 0, 0};

        VehiclePropValue injectValue =
                VehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .addIntValue(values)
                        .build();

        getMockedVehicleHal().injectEvent(injectValue);

        if (isLong) {
            Thread.sleep(1200); // Long press is > 1s.
        }

        int[] upValues = {VehicleHwKeyInputAction.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST, 0, 0 };

        injectValue = VehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .addIntValue(upValues)
                .build();

        getMockedVehicleHal().injectEvent(injectValue);
    }


    private class PropertyHandler implements VehicleHalPropertyHandler {
        HashMap<Integer, VehiclePropValue> mMap = new HashMap<>();

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            Log.d(TAG, "onPropertySet:" + value);
            mMap.put(value.prop, value);
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            Log.d(TAG, "onPropertyGet:" + value);
            VehiclePropValue currentValue = mMap.get(value.prop);
            // VNS will call get method when subscribe is called, just return empty value.
            return currentValue != null ? currentValue : value;
        }

        @Override
        public synchronized void onPropertySubscribe(int property, int zones, float sampleRate) {
            Log.d(TAG, "onPropertySubscribe property " + property + " sampleRate " + sampleRate);
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
            Log.d(TAG, "onPropertyUnSubscribe property " + property);
        }
    }
}
