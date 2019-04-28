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
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.hvac.CarHvacManager.CarHvacEventCallback;
import android.car.hardware.hvac.CarHvacManager.PropertyId;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaWindow;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaZone;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyChangeMode;
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
public class CarHvacManagerTest extends MockedCarTestBase {
    private static final String TAG = CarHvacManagerTest.class.getSimpleName();

    // Use this semaphore to block until the callback is heard of.
    private Semaphore mAvailable;

    private CarHvacManager mCarHvacManager;
    private boolean mEventBoolVal;
    private float mEventFloatVal;
    private int mEventIntVal;
    private int mEventZoneVal;

    @Override
    protected synchronized void configureMockedHal() {
        HvacPropertyHandler handler = new HvacPropertyHandler();
        addProperty(VehicleProperty.HVAC_DEFROSTER, handler)
                .setSupportedAreas(VehicleAreaWindow.FRONT_WINDSHIELD);
        addProperty(VehicleProperty.HVAC_FAN_SPEED, handler)
                .setSupportedAreas(VehicleAreaZone.ROW_1_LEFT);
        addProperty(VehicleProperty.HVAC_TEMPERATURE_SET, handler)
                .setSupportedAreas(VehicleAreaZone.ROW_1_LEFT);
        addProperty(VehicleProperty.HVAC_TEMPERATURE_CURRENT, handler)
                .setChangeMode(VehiclePropertyChangeMode.CONTINUOUS)
                .setAccess(VehiclePropertyAccess.READ)
                .setSupportedAreas(VehicleAreaZone.ROW_1);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAvailable = new Semaphore(0);
        mCarHvacManager = (CarHvacManager) getCar().getCarManager(Car.HVAC_SERVICE);
    }

    // Test a boolean property
    public void testHvacRearDefrosterOn() throws Exception {
        mCarHvacManager.setBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                VehicleAreaWindow.FRONT_WINDSHIELD, true);
        boolean defrost = mCarHvacManager.getBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                VehicleAreaWindow.FRONT_WINDSHIELD);
        assertTrue(defrost);

        mCarHvacManager.setBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                VehicleAreaWindow.FRONT_WINDSHIELD, false);
        defrost = mCarHvacManager.getBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                VehicleAreaWindow.FRONT_WINDSHIELD);
        assertFalse(defrost);
    }

    // Test an integer property
    public void testHvacFanSpeed() throws Exception {
        mCarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT, 15);
        int speed = mCarHvacManager.getIntProperty(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT);
        assertEquals(15, speed);

        mCarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT, 23);
        speed = mCarHvacManager.getIntProperty(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT);
        assertEquals(23, speed);
    }

    // Test an float property
    public void testHvacTempSetpoint() throws Exception {
        mCarHvacManager.setFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT, 70);
        float temp = mCarHvacManager.getFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT);
        assertEquals(70.0, temp, 0);

        mCarHvacManager.setFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT, (float) 65.5);
        temp = mCarHvacManager.getFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                VehicleAreaZone.ROW_1_LEFT);
        assertEquals(65.5, temp, 0);
    }

    public void testError() throws Exception {
        final int PROP = VehicleProperty.HVAC_DEFROSTER;
        final int AREA = VehicleAreaWindow.FRONT_WINDSHIELD;
        final int ERR_CODE = 42;

        CountDownLatch errorLatch = new CountDownLatch(1);
        MutableInt propertyIdReceived = new MutableInt(0);
        MutableInt areaIdReceived = new MutableInt(0);

        mCarHvacManager.registerCallback(new CarHvacEventCallback() {
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
        mCarHvacManager.registerCallback(new EventListener());

        // Inject a boolean event and wait for its callback in onPropertySet.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.HVAC_DEFROSTER)
                .setAreaId(VehicleAreaWindow.FRONT_WINDSHIELD)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .addIntValue(1)
                .build();
        assertEquals(0, mAvailable.availablePermits());
        getMockedVehicleHal().injectEvent(v);

        assertTrue(mAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertTrue(mEventBoolVal);
        assertEquals(mEventZoneVal, VehicleAreaWindow.FRONT_WINDSHIELD);

        // Inject a float event and wait for its callback in onPropertySet.
        v = VehiclePropValueBuilder.newBuilder(VehicleProperty.HVAC_TEMPERATURE_CURRENT)
                .setAreaId(VehicleAreaZone.ROW_1)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .addFloatValue(67f)
                .build();
        assertEquals(0, mAvailable.availablePermits());
        getMockedVehicleHal().injectEvent(v);

        assertTrue(mAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(67, mEventFloatVal, 0);
        assertEquals(VehicleAreaZone.ROW_1, mEventZoneVal);

        // Inject an integer event and wait for its callback in onPropertySet.
        v = VehiclePropValueBuilder.newBuilder(VehicleProperty.HVAC_FAN_SPEED)
                .setAreaId(VehicleAreaZone.ROW_1_LEFT)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .addIntValue(4)
                .build();
        assertEquals(0, mAvailable.availablePermits());
        getMockedVehicleHal().injectEvent(v);

        assertTrue(mAvailable.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(4, mEventIntVal);
        assertEquals(VehicleAreaZone.ROW_1_LEFT, mEventZoneVal);
    }

    private class HvacPropertyHandler implements VehicleHalPropertyHandler {
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

    private class EventListener implements CarHvacEventCallback {
        EventListener() { }

        @Override
        public void onChangeEvent(final CarPropertyValue value) {
            Log.d(TAG, "onChangeEvent: "  + value);
            Object o = value.getValue();
            mEventZoneVal = value.getAreaId();

            if (o instanceof Integer) {
                mEventIntVal = (Integer) o;
            } else if (o instanceof Float) {
                mEventFloatVal = (Float) o;
            } else if (o instanceof Boolean) {
                mEventBoolVal = (Boolean) o;
            }
            mAvailable.release();
        }

        @Override
        public void onErrorEvent(final int propertyId, final int zone) {
            Log.d(TAG, "Error:  propertyId=" + propertyId + "  zone=" + zone);
        }
    }
}
