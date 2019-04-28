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
import android.car.hardware.radio.CarRadioEvent;
import android.car.hardware.radio.CarRadioManager;
import android.car.hardware.radio.CarRadioManager.CarRadioEventListener;
import android.car.hardware.radio.CarRadioPreset;
import android.hardware.radio.RadioManager;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.google.android.collect.Lists;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarRadioManagerTest extends MockedCarTestBase {

    private static final String TAG = CarRadioManagerTest.class.getSimpleName();

    // Use this semaphore to block until the callback is heard of.
    private Semaphore mAvailable;

    private static final int NUM_PRESETS = 2;
    private final HashMap<Integer, CarRadioPreset> mRadioPresets = new HashMap<>();

    private CarRadioManager mCarRadioManager;

    private class RadioPresetPropertyHandler implements VehicleHalPropertyHandler {
        public RadioPresetPropertyHandler() { }

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            assertEquals(value.prop, VehicleProperty.RADIO_PRESET);

            Integer[] valueList = new Integer[4];
            value.value.int32Values.toArray(valueList);
            assertFalse(
                "Index out of range: " + valueList[0] + " (0, " + NUM_PRESETS + ")",
                valueList[0] < 1);
            assertFalse(
                "Index out of range: " + valueList[0] + " (0, " + NUM_PRESETS + ")",
                valueList[0] > NUM_PRESETS);

            CarRadioPreset preset =
                new CarRadioPreset(valueList[0], valueList[1], valueList[2], valueList[3]);
            mRadioPresets.put(valueList[0], preset);

            // The test case must be waiting for the semaphore, if not we should throw exception.
            if (mAvailable.availablePermits() != 0) {
                Log.d(TAG, "Lock was free, should have been locked.");
            }
            mAvailable.release();
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertEquals(value.prop, VehicleProperty.RADIO_PRESET);

            Integer[] valueList = new Integer[4];
            value.value.int32Values.toArray(valueList);

            // Get the actual preset.
            if (valueList[0] < 1 || valueList[0] > NUM_PRESETS) {
                // VNS will call get method when subscribe is called, just return an empty
                // value.
                return value;
            }
            CarRadioPreset preset = mRadioPresets.get(valueList[0]);
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.RADIO_PRESET)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(
                            preset.getPresetNumber(),
                            preset.getBand(),
                            preset.getChannel(),
                            preset.getSubChannel())
                    .build();
        }

        @Override
        public synchronized void onPropertySubscribe(int property, int zones, float sampleRate) {
            Log.d(TAG, "onPropertySubscribe property: " + property + " rate: " + sampleRate);
            if (mAvailable.availablePermits() != 0) {
                Log.d(TAG, "Lock was free, should have been locked.");
                return;
            }
            mAvailable.release();
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
        }
    }

    private class EventListener implements CarRadioEventListener {
        public EventListener() { }

        @Override
        public void onEvent(CarRadioEvent event) {
            // Print the event and release the lock.
            Log.d(TAG, event.toString());
            if (mAvailable.availablePermits() != 0) {
                Log.e(TAG, "Lock should be taken.");
                // Let the timeout fail the test here.
                return;
            }
            mAvailable.release();
        }
    }

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.RADIO_PRESET, new RadioPresetPropertyHandler())
                .setConfigArray(Lists.newArrayList(NUM_PRESETS));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAvailable = new Semaphore(0);
        mCarRadioManager = (CarRadioManager) getCar().getCarManager(Car.RADIO_SERVICE);
    }

    public void testPresetCount() throws Exception {
        int presetCount = mCarRadioManager.getPresetCount();
        assertEquals("Preset count not same.", NUM_PRESETS, presetCount);
    }

    public void testSetAndGetPreset() throws Exception {
        // Create a preset.
        CarRadioPreset preset = new CarRadioPreset(1, RadioManager.BAND_FM, 1234, -1);
        assertEquals("Lock should be freed by now.", 0, mAvailable.availablePermits());
        // mAvailable.acquire(1);
        mCarRadioManager.setPreset(preset);

        // Wait for acquire to be available again, fail if timeout.
        boolean success = mAvailable.tryAcquire(5L, TimeUnit.SECONDS);
        assertEquals("Could not finish setting, timeout!", true, success);

        // Test that get preset gives you the same element.
        assertEquals(preset, mCarRadioManager.getPreset(1));
    }

    public void testSubscribe() throws Exception {
        EventListener listener = new EventListener();
        assertEquals("Lock should be freed by now.", 0, mAvailable.availablePermits());
        mCarRadioManager.registerListener(listener);

        // Wait for acquire to be available again, fail if timeout.
        boolean success = mAvailable.tryAcquire(5L, TimeUnit.SECONDS);
        assertEquals("addListener timeout", true, success);

        // Inject an event and wait for its callback in onPropertySet.
        CarRadioPreset preset = new CarRadioPreset(2, RadioManager.BAND_AM, 4321, -1);

        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.RADIO_PRESET)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .addIntValue(
                        preset.getPresetNumber(),
                        preset.getBand(),
                        preset.getChannel(),
                        preset.getSubChannel())
                .build();
        getMockedVehicleHal().injectEvent(v);

        success = mAvailable.tryAcquire(5L, TimeUnit.SECONDS);
        assertEquals("injectEvent, onEvent timeout!", true, success);
    }
}
