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
import android.car.CarNotConnectedException;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.hardware.automotive.vehicle.V2_0.VehicleGear;
import android.hardware.automotive.vehicle.V2_0.VehicleIgnitionState;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.car.vehiclehal.VehiclePropValueBuilder;

/**
 * Test the public entry points for the CarSensorManager
 */
@MediumTest
public class CarSensorManagerTest extends MockedCarTestBase {
    private static final String TAG = CarSensorManagerTest.class.getSimpleName();

    private CarSensorManager mCarSensorManager;

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.NIGHT_MODE,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                        .addIntValue(0)
                        .build());
        addProperty(VehicleProperty.PERF_VEHICLE_SPEED,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                        .addFloatValue(0f)
                        .build());
        addProperty(VehicleProperty.FUEL_LEVEL_LOW,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.FUEL_LEVEL_LOW)
                        .setBooleanValue(false)
                        .build());
        addProperty(VehicleProperty.PARKING_BRAKE_ON,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.PARKING_BRAKE_ON)
                        .setBooleanValue(true)
                        .build());
        addProperty(VehicleProperty.CURRENT_GEAR,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.CURRENT_GEAR)
                        .addIntValue(0)
                        .build());
        addProperty(VehicleProperty.GEAR_SELECTION,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValue(0)
                        .build());
        addProperty(VehicleProperty.DRIVING_STATUS,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.DRIVING_STATUS)
                        .addIntValue(0)
                        .build());
        addProperty(VehicleProperty.IGNITION_STATE,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.IGNITION_STATE)
                        .addIntValue(CarSensorEvent.IGNITION_STATE_ACC)
                        .build());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Start the HAL layer and set up the sensor manager service
        mCarSensorManager = (CarSensorManager) getCar().getCarManager(Car.SENSOR_SERVICE);
    }

    /**
     * Test single sensor availability entry point
     * @throws Exception
     */
    public void testSensorAvailability() throws Exception {
        // NOTE:  Update this test if/when the reserved values put into use.  For now, we
        //        expect them to never be supported.
        assertFalse(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_RESERVED1));
        assertFalse(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_RESERVED13));
        assertFalse(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_RESERVED21));

        // We expect these sensors to always be available
        assertTrue(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_CAR_SPEED));
        assertTrue(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_FUEL_LEVEL));
        assertTrue(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_PARKING_BRAKE));
        assertTrue(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_GEAR));
        assertTrue(mCarSensorManager.isSensorSupported(CarSensorManager.SENSOR_TYPE_NIGHT));
        assertTrue(mCarSensorManager.isSensorSupported(
                CarSensorManager.SENSOR_TYPE_DRIVING_STATUS));
        assertTrue(mCarSensorManager.isSensorSupported(
                CarSensorManager.SENSOR_TYPE_IGNITION_STATE));
    }

    /**
     * Test sensor enumeration entry point
     * @throws Exception
     */
    public void testSensorEnumeration() throws Exception {
        int[] supportedSensors = mCarSensorManager.getSupportedSensors();
        assertNotNull(supportedSensors);

        Log.i(TAG, "Found " + supportedSensors.length + " supported sensors.");

        // Unfortunately, we don't have a definitive range for legal sensor values,
        // so we have set a "reasonable" range here.  The ending value, in particular,
        // will need to be updated if/when new sensor types are allowed.
        // Here we are ensuring that all the enumerated sensors also return supported.
        for (int candidate = 0; candidate <= CarSensorManager.SENSOR_TYPE_RESERVED21; ++candidate) {
            boolean supported = mCarSensorManager.isSensorSupported(candidate);
            boolean found = false;
            for (int sensor : supportedSensors) {
                if (candidate == sensor) {
                    found = true;
                    Log.i(TAG, "Sensor type " + sensor + " is supported.");
                    break;
                }
            }

            // Make sure the individual query on a sensor type is consistent
            assertEquals(found, supported);
        }

        // Here we simply ensure that one specific expected sensor is always available to help
        // ensure we don't have a trivially broken test finding nothing.
        boolean found = false;
        for (int sensor : supportedSensors) {
            if (sensor == CarSensorManager.SENSOR_TYPE_DRIVING_STATUS) {
                found = true;
                break;
            }
        }
        assertTrue("We expect at least DRIVING_STATUS to be available", found);
    }

    /**
     * Test senor notification registration, delivery, and unregistration
     * @throws Exception
     */
    public void testEvents() throws Exception {
        // Set up our listener callback
        SensorListener listener = new SensorListener();
        mCarSensorManager.registerListener(listener,
                CarSensorManager.SENSOR_TYPE_NIGHT,
                CarSensorManager.SENSOR_RATE_NORMAL);

        VehiclePropValue value;
        CarSensorEvent event;
        CarSensorEvent.NightData data = null;

        listener.reset();

        // Set the value TRUE and wait for the event to arrive
        getMockedVehicleHal().injectEvent(
                VehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                        .setBooleanValue(true)
                        .setTimestamp(1L)
                        .build());
        assertTrue(listener.waitForSensorChange(1L));

        // Ensure we got the expected event
        assertEquals(listener.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);

        // Ensure we got the expected value in our callback
        data = listener.getLastEvent().getNightData(data);
        Log.d(TAG, "NightMode " + data.isNightMode + " at " + data.timestamp);
        assertTrue(data.isNightMode);

        // Ensure we have the expected value in the sensor manager's cache
        event = mCarSensorManager.getLatestSensorEvent(CarSensorManager.SENSOR_TYPE_NIGHT);
        assertNotNull(event);
        data = event.getNightData(data);
        assertEquals("Unexpected event timestamp", data.timestamp, 1);
        assertTrue("Unexpected value", data.isNightMode);

        listener.reset();
        // Set the value FALSE
        getMockedVehicleHal().injectEvent(
                VehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                        .setTimestamp(1001)
                        .setBooleanValue(false)
                        .build());
        assertTrue(listener.waitForSensorChange(1001));

        // Ensure we got the expected event
        assertEquals(listener.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);

        // Ensure we got the expected value in our callback
        data = listener.getLastEvent().getNightData(data);
        assertEquals("Unexpected event timestamp", 1001, data.timestamp);
        assertFalse("Unexpected value", data.isNightMode);

        // Ensure we have the expected value in the sensor manager's cache
        event = mCarSensorManager.getLatestSensorEvent(CarSensorManager.SENSOR_TYPE_NIGHT);
        assertNotNull(event);
        data = event.getNightData(data);
        assertFalse(data.isNightMode);

        // Unregister our handler (from all sensor types)
        mCarSensorManager.unregisterListener(listener);

        listener.reset();
        // Set the value TRUE again
        value = VehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                .setTimestamp(2001)
                .setBooleanValue(true)
                .build();
        getMockedVehicleHal().injectEvent(value);

        // Ensure we did not get a callback (should timeout)
        Log.i(TAG, "waiting for unexpected callback -- should timeout.");
        assertFalse(listener.waitForSensorChange(2001));

        // Despite us not having a callback registered, the Sensor Manager should see the update
        event = mCarSensorManager.getLatestSensorEvent(CarSensorManager.SENSOR_TYPE_NIGHT);
        assertNotNull(event);
        data = event.getNightData(data);
        assertEquals("Unexpected event timestamp", data.timestamp, 2001);
        assertTrue("Unexpected value", data.isNightMode);
    }

    public void testIgnitionState() throws CarNotConnectedException {
        CarSensorEvent event = mCarSensorManager.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_IGNITION_STATE);
        assertNotNull(event);
        assertEquals(CarSensorEvent.IGNITION_STATE_ACC, event.intValues[0]);
    }

    public void testIgnitionEvents() throws Exception {
        SensorListener listener = new SensorListener();
        mCarSensorManager.registerListener(listener, CarSensorManager.SENSOR_TYPE_IGNITION_STATE,
                CarSensorManager.SENSOR_RATE_NORMAL);


        // Mapping of HAL -> Manager ignition states.
        int[] ignitionStates = new int[] {
                VehicleIgnitionState.UNDEFINED, CarSensorEvent.IGNITION_STATE_UNDEFINED,
                VehicleIgnitionState.LOCK, CarSensorEvent.IGNITION_STATE_LOCK,
                VehicleIgnitionState.OFF, CarSensorEvent.IGNITION_STATE_OFF,
                VehicleIgnitionState.ACC, CarSensorEvent.IGNITION_STATE_ACC,
                VehicleIgnitionState.ON, CarSensorEvent.IGNITION_STATE_ON,
                VehicleIgnitionState.START, CarSensorEvent.IGNITION_STATE_START,
                VehicleIgnitionState.ON, CarSensorEvent.IGNITION_STATE_ON,
                VehicleIgnitionState.LOCK, CarSensorEvent.IGNITION_STATE_LOCK,
        };

        for (int i = 0; i < ignitionStates.length; i += 2) {
            injectIgnitionStateAndAssert(listener, ignitionStates[i], ignitionStates[i + 1]);
        }
    }

    private void injectIgnitionStateAndAssert(SensorListener listener, int halIgnitionState,
            int mgrIgnitionState) throws Exception{
        listener.reset();
        long time = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(
                VehiclePropValueBuilder.newBuilder(VehicleProperty.IGNITION_STATE)
                        .addIntValue(halIgnitionState)
                        .setTimestamp(time)
                        .build());
        assertTrue(listener.waitForSensorChange(time));

        CarSensorEvent eventReceived = listener.getLastEvent();
        assertEquals(CarSensorManager.SENSOR_TYPE_IGNITION_STATE, eventReceived.sensorType);
        assertEquals(mgrIgnitionState, eventReceived.intValues[0]);
    }

    public void testIgnitionEvents_Bad() throws Exception {
        SensorListener listener = new SensorListener();
        mCarSensorManager.registerListener(listener, CarSensorManager.SENSOR_TYPE_IGNITION_STATE,
                CarSensorManager.SENSOR_RATE_NORMAL);

        listener.reset();
        long time = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(
                VehiclePropValueBuilder.newBuilder(VehicleProperty.IGNITION_STATE)
                        .addIntValue(0xdeadbeef)
                        .setTimestamp(time)
                        .build());

        // Make sure invalid events are never propagated to clients.
        assertFalse(listener.waitForSensorChange(time));
    }

    public void testGear() throws Exception {
        SensorListener listener = new SensorListener();
        mCarSensorManager.registerListener(listener, CarSensorManager.SENSOR_TYPE_GEAR,
                CarSensorManager.SENSOR_RATE_NORMAL);

        // Mapping of HAL -> Manager gear selection states.
        int[] gears = new int[] {
                VehicleGear.GEAR_PARK, CarSensorEvent.GEAR_PARK,
                VehicleGear.GEAR_DRIVE, CarSensorEvent.GEAR_DRIVE,
                VehicleGear.GEAR_NEUTRAL, CarSensorEvent.GEAR_NEUTRAL,
                VehicleGear.GEAR_REVERSE, CarSensorEvent.GEAR_REVERSE,
                VehicleGear.GEAR_LOW, CarSensorEvent.GEAR_FIRST,
                VehicleGear.GEAR_1, CarSensorEvent.GEAR_FIRST,
                VehicleGear.GEAR_2, CarSensorEvent.GEAR_SECOND,
                VehicleGear.GEAR_3, CarSensorEvent.GEAR_THIRD,
                VehicleGear.GEAR_4, CarSensorEvent.GEAR_FOURTH,
                VehicleGear.GEAR_5, CarSensorEvent.GEAR_FIFTH,
                VehicleGear.GEAR_6, CarSensorEvent.GEAR_SIXTH,
                VehicleGear.GEAR_7, CarSensorEvent.GEAR_SEVENTH,
                VehicleGear.GEAR_8, CarSensorEvent.GEAR_EIGHTH,
                VehicleGear.GEAR_9, CarSensorEvent.GEAR_NINTH,
        };

        for (int i = 0; i < gears.length; i += 2) {
            injectGearEventAndAssert(listener, gears[i], gears[i + 1]);
        }

        // invalid input should not be forwarded
        long time = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(
                VehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValue(0xdeadbeef)
                        .setTimestamp(time)
                        .build());
        assertFalse(listener.waitForSensorChange(time));
        CarSensorEvent event = mCarSensorManager.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_GEAR);
        assertNotNull(event);  // Still holds an old event.
        assertEquals(CarSensorEvent.GEAR_NINTH, event.intValues[0]);
    }

    private void injectGearEventAndAssert(SensorListener listener, int halValue,
            int carSensorValue) throws Exception {
        listener.reset();
        long time = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(
                VehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValue(halValue)
                        .setTimestamp(time)
                        .build());
        assertTrue(listener.waitForSensorChange(time));
        CarSensorEvent event = mCarSensorManager.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_GEAR);
        assertNotNull(event);
        assertEquals(carSensorValue, event.intValues[0]);
    }

    /**
     * Test senor multiple liseners notification registration, delivery and unregistration.
     * @throws Exception
     */
    public void testEventsWithMultipleListeners() throws Exception {
        // Set up our listeners callback
        SensorListener listener1 = new SensorListener();
        SensorListener listener2 = new SensorListener();
        SensorListener listener3 = new SensorListener();

        mCarSensorManager.registerListener(listener1,
                CarSensorManager.SENSOR_TYPE_NIGHT,
                CarSensorManager.SENSOR_RATE_NORMAL);

        mCarSensorManager.registerListener(listener2,
                CarSensorManager.SENSOR_TYPE_NIGHT,
                CarSensorManager.SENSOR_RATE_NORMAL);

        mCarSensorManager.registerListener(listener3,
                CarSensorManager.SENSOR_TYPE_NIGHT,
                CarSensorManager.SENSOR_RATE_FASTEST);

        CarSensorEvent.NightData data = null;
        VehiclePropValue value;
        CarSensorEvent event;

        listener1.reset();
        listener2.reset();
        listener3.reset();

        // Set the value TRUE and wait for the event to arrive
        value = VehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                    .setTimestamp(42L)
                    .setBooleanValue(true)
                    .build();

        getMockedVehicleHal().injectEvent(value);

        assertTrue(listener1.waitForSensorChange(42L));
        assertTrue(listener2.waitForSensorChange(42L));
        assertTrue(listener3.waitForSensorChange(42L));

        // Ensure we got the expected event
        assertEquals(listener1.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);
        assertEquals(listener2.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);
        assertEquals(listener3.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);

        // Ensure we got the expected value in our callback
        data = listener1.getLastEvent().getNightData(data);
        Log.d(TAG, "NightMode " + data.isNightMode + " at " + data.timestamp);
        assertTrue(data.isNightMode);

        data = listener2.getLastEvent().getNightData(data);
        Log.d(TAG, "NightMode " + data.isNightMode + " at " + data.timestamp);
        assertTrue(data.isNightMode);

        data = listener3.getLastEvent().getNightData(data);
        Log.d(TAG, "NightMode " + data.isNightMode + " at " + data.timestamp);
        assertTrue(data.isNightMode);

        // Ensure we have the expected value in the sensor manager's cache
        event = mCarSensorManager.getLatestSensorEvent(CarSensorManager.SENSOR_TYPE_NIGHT);
        data = event.getNightData(data);
        assertEquals("Unexpected event timestamp", 42, data.timestamp);
        assertTrue("Unexpected value", data.isNightMode);

        listener1.reset();
        listener2.reset();
        listener3.reset();
        // Set the value FALSE
        value = VehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                .setTimestamp(1001)
                .setBooleanValue(false)
                .build();
        getMockedVehicleHal().injectEvent(value);
        assertTrue(listener1.waitForSensorChange(1001));
        assertTrue(listener2.waitForSensorChange(1001));
        assertTrue(listener3.waitForSensorChange(1001));

        // Ensure we got the expected event
        assertEquals(listener1.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);
        assertEquals(listener2.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);
        assertEquals(listener3.getLastEvent().sensorType, CarSensorManager.SENSOR_TYPE_NIGHT);

        // Ensure we got the expected value in our callback
        data = listener1.getLastEvent().getNightData(data);
        assertEquals("Unexpected event timestamp", 1001, data.timestamp);
        assertFalse("Unexpected value", data.isNightMode);

        data = listener2.getLastEvent().getNightData(data);
        assertEquals("Unexpected event timestamp", 1001, data.timestamp);
        assertFalse("Unexpected value", data.isNightMode);

        data = listener3.getLastEvent().getNightData(data);
        listener3.reset();
        assertEquals("Unexpected event timestamp", 1001, data.timestamp);
        assertFalse("Unexpected value", data.isNightMode);

        // Ensure we have the expected value in the sensor manager's cache
        event = mCarSensorManager.getLatestSensorEvent(CarSensorManager.SENSOR_TYPE_NIGHT);
        data = event.getNightData(data);
        assertFalse(data.isNightMode);

        Log.d(TAG, "Unregistering listener3");
        mCarSensorManager.unregisterListener(listener3);

        Log.d(TAG, "Rate changed - expect sensor restart and change event sent.");
        assertTrue(listener1.waitForSensorChange());
        assertTrue(listener2.waitForSensorChange());
        assertFalse(listener3.waitForSensorChange());

        listener1.reset();
        listener2.reset();
        listener3.reset();
        // Set the value TRUE again
        value = VehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                .setTimestamp()
                .setBooleanValue(true)
                .build();
        getMockedVehicleHal().injectEvent(value);

        assertTrue(listener1.waitForSensorChange());
        assertTrue(listener2.waitForSensorChange());

        listener1.reset();
        listener2.reset();

        // Ensure we did not get a callback (should timeout)
        Log.i(TAG, "waiting for unexpected callback -- should timeout.");
        assertFalse(listener3.waitForSensorChange());

        Log.d(TAG, "Unregistering listener2");
        mCarSensorManager.unregisterListener(listener3);

        Log.d(TAG, "Rate did nor change - dont expect sensor restart and change event sent.");
        assertFalse(listener1.waitForSensorChange());
        assertFalse(listener2.waitForSensorChange());
        assertFalse(listener3.waitForSensorChange());
    }


    /**
     * Callback function we register for sensor update notifications.
     * This tracks the number of times it has been called via the mAvailable semaphore,
     * and keeps a reference to the most recent event delivered.
     */
    class SensorListener implements CarSensorManager.OnSensorChangedListener {
        private final Object mSync = new Object();

        private CarSensorEvent mLastEvent = null;

        CarSensorEvent getLastEvent() {
            return mLastEvent;
        }

        void reset() {
            synchronized (mSync) {
                mLastEvent = null;
            }
        }

        boolean waitForSensorChange() throws InterruptedException {
            return waitForSensorChange(0);
        }

        // Returns True to indicate receipt of a sensor event.  False indicates a timeout.
        boolean waitForSensorChange(long eventTimeStamp) throws InterruptedException {
            long start = SystemClock.elapsedRealtime();
            boolean matchTimeStamp = eventTimeStamp != 0;
            synchronized (mSync) {
                Log.d(TAG, "waitForSensorChange, mLastEvent: " + mLastEvent);
                while ((mLastEvent == null
                        || (matchTimeStamp && mLastEvent.timestamp != eventTimeStamp))
                        && (start + SHORT_WAIT_TIMEOUT_MS > SystemClock.elapsedRealtime())) {
                    mSync.wait(10L);
                }
                return mLastEvent != null &&
                        (!matchTimeStamp || mLastEvent.timestamp == eventTimeStamp);
            }
        }

        @Override
        public void onSensorChanged(CarSensorEvent event) {
            Log.d(TAG, "onSensorChanged, event: " + event);
            synchronized (mSync) {
                // We're going to hold a reference to this object
                mLastEvent = event;
                mSync.notify();
            }
        }
    }

}
