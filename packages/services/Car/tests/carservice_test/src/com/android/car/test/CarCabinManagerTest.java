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
import android.car.hardware.CarPropertyValue;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.cabin.CarCabinManager.CarCabinEventCallback;
import android.car.hardware.cabin.CarCabinManager.PropertyId;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaDoor;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaWindow;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.util.MutableInt;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarCabinManagerTest extends MockedCarTestBase {
    private static final String TAG = CarCabinManagerTest.class.getSimpleName();

    // Use this semaphore to block until the callback is heard of.
    private Semaphore mAvailable;

    private CarCabinManager mCarCabinManager;
    private boolean mEventBoolVal;
    private int mEventIntVal;
    private int mEventZoneVal;

    @Override
    protected synchronized void configureMockedHal() {
        CabinPropertyHandler handler = new CabinPropertyHandler();
        addProperty(VehicleProperty.DOOR_LOCK, handler)
                .setSupportedAreas(VehicleAreaDoor.ROW_1_LEFT);
        addProperty(VehicleProperty.WINDOW_POS, handler)
                .setSupportedAreas(VehicleAreaWindow.ROW_1_LEFT);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAvailable = new Semaphore(0);
        mCarCabinManager = (CarCabinManager) getCar().getCarManager(Car.CABIN_SERVICE);
    }

    // Test a boolean property
    public void testCabinDoorLockOn() throws Exception {
        mCarCabinManager.setBooleanProperty(CarCabinManager.ID_DOOR_LOCK,
                VehicleAreaDoor.ROW_1_LEFT, true);
        boolean lock = mCarCabinManager.getBooleanProperty(CarCabinManager.ID_DOOR_LOCK,
                VehicleAreaDoor.ROW_1_LEFT);
        assertTrue(lock);

        mCarCabinManager.setBooleanProperty(CarCabinManager.ID_DOOR_LOCK,
                VehicleAreaDoor.ROW_1_LEFT, false);
        lock = mCarCabinManager.getBooleanProperty(CarCabinManager.ID_DOOR_LOCK,
                VehicleAreaDoor.ROW_1_LEFT);
        assertFalse(lock);
    }

    // Test an integer property
    public void testCabinWindowPos() throws Exception {
        mCarCabinManager.setIntProperty(CarCabinManager.ID_WINDOW_POS,
                VehicleAreaWindow.ROW_1_LEFT, 50);
        int windowPos = mCarCabinManager.getIntProperty(CarCabinManager.ID_WINDOW_POS,
                VehicleAreaWindow.ROW_1_LEFT);
        assertEquals(50, windowPos);

        mCarCabinManager.setIntProperty(CarCabinManager.ID_WINDOW_POS,
                VehicleAreaWindow.ROW_1_LEFT, 25);
        windowPos = mCarCabinManager.getIntProperty(CarCabinManager.ID_WINDOW_POS,
                VehicleAreaWindow.ROW_1_LEFT);
        assertEquals(25, windowPos);
    }

    public void testError() throws Exception {
        final int PROP = VehicleProperty.DOOR_LOCK;
        final int AREA = VehicleAreaWindow.ROW_1_LEFT;
        final int ERR_CODE = 42;

        CountDownLatch errorLatch = new CountDownLatch(1);
        MutableInt propertyIdReceived = new MutableInt(0);
        MutableInt areaIdReceived = new MutableInt(0);

        mCarCabinManager.registerCallback(new CarCabinEventCallback() {
            @Override
            public void onChangeEvent(CarPropertyValue value) {

            }

            @Override
            public void onErrorEvent(@PropertyId int propertyId, int area) {
                propertyIdReceived.value = propertyId;
                areaIdReceived.value = area;
                errorLatch.countDown();
            }
        });

        getMockedVehicleHal().injectError(ERR_CODE, PROP, AREA);
        assertTrue(errorLatch.await(DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(PROP, propertyIdReceived.value);
        assertEquals(AREA, areaIdReceived.value);
    }


    // Test an event
    public void testEvent() throws Exception {
        mCarCabinManager.registerCallback(new EventListener());

        // Inject a boolean event and wait for its callback in onPropertySet.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.DOOR_LOCK)
                .setAreaId(VehicleAreaDoor.ROW_1_LEFT)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .addIntValue(1)
                .build();

        assertEquals(0, mAvailable.availablePermits());
        getMockedVehicleHal().injectEvent(v);

        assertTrue(mAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertTrue(mEventBoolVal);
        assertEquals(VehicleAreaDoor.ROW_1_LEFT, mEventZoneVal);

        // Inject an integer event and wait for its callback in onPropertySet.
        v = VehiclePropValueBuilder.newBuilder(VehicleProperty.WINDOW_POS)
                .setAreaId(VehicleAreaWindow.ROW_1_LEFT)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .addIntValue(75)
                .build();

        assertEquals(0, mAvailable.availablePermits());
        getMockedVehicleHal().injectEvent(v);

        assertTrue(mAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(mEventIntVal, 75);
        assertEquals(VehicleAreaWindow.ROW_1_LEFT, mEventZoneVal);
    }


    private class CabinPropertyHandler implements VehicleHalPropertyHandler {
        HashMap<Integer, VehiclePropValue> mMap = new HashMap<>();

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            mMap.put(value.prop, value);
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
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

    private class EventListener implements CarCabinEventCallback {
        EventListener() { }

        @Override
        public void onChangeEvent(final CarPropertyValue value) {
            Log.d(TAG, "onChangeEvent: "  + value);
            Object o = value.getValue();
            mEventZoneVal = value.getAreaId();

            if (o instanceof Integer) {
                mEventIntVal = (Integer) o;
            } else if (o instanceof Boolean) {
                mEventBoolVal = (Boolean) o;
            } else {
                Log.e(TAG, "onChangeEvent:  Unknown instance type = " + o.getClass().getName());
            }
            mAvailable.release();
        }

        @Override
        public void onErrorEvent(final int propertyId, final int zone) {
            Log.d(TAG, "Error:  propertyId=" + propertyId + "  zone=" + zone);
        }
    }
}
