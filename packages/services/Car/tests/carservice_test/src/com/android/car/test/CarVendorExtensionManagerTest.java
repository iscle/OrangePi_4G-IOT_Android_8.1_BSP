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

package com.android.car.test;

import static com.android.car.CarServiceUtils.toByteArray;

import android.car.Car;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarVendorExtensionManager;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.VehicleArea;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaZone;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyType;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.vehiclehal.test.MockedVehicleHal;
import com.android.car.vehiclehal.test.VehiclePropConfigBuilder;

import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Tests for {@link CarVendorExtensionManager}
 */
@MediumTest
public class CarVendorExtensionManagerTest extends MockedCarTestBase {

    private static final String TAG = CarVendorExtensionManager.class.getSimpleName();

    private static final int CUSTOM_GLOBAL_INT_PROP_ID =
            0x1 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;

    private static final int CUSTOM_ZONED_FLOAT_PROP_ID =
            0x2 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.FLOAT | VehicleArea.ZONE;

    private static final int CUSTOM_BYTES_PROP_ID_1 =
            0x3 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.BYTES | VehicleArea.ZONE;

    private static final int CUSTOM_BYTES_PROP_ID_2 =
            0x4 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.BYTES | VehicleArea.GLOBAL;

    private static final int CUSTOM_STRING_PROP_ID =
            0x5 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.STRING | VehicleArea.GLOBAL;

    private static final float EPS = 1e-9f;
    private static final int MILLION = 1000 * 1000;

    private static final int MIN_PROP_INT32 = 0x0000005;
    private static final int MAX_PROP_INT32 = 0xDeadBee;

    private static final float MIN_PROP_FLOAT = 10.42f;
    private static final float MAX_PROP_FLOAT = 42.10f;

//    private static final MockedVehicleHal mVehicleHal = new MockedVehicleHal();

    private static final VehiclePropConfig mConfigs[] = new VehiclePropConfig[] {
            VehiclePropConfigBuilder.newBuilder(CUSTOM_GLOBAL_INT_PROP_ID)
                    .addAreaConfig(0, MIN_PROP_INT32, MAX_PROP_INT32)
                    .build(),
            VehiclePropConfigBuilder.newBuilder(CUSTOM_ZONED_FLOAT_PROP_ID)
                    .setSupportedAreas(VehicleAreaZone.ROW_1_LEFT | VehicleAreaZone.ROW_1_RIGHT)
                    .addAreaConfig(VehicleAreaZone.ROW_1_LEFT, MIN_PROP_FLOAT, MAX_PROP_FLOAT)
                    .addAreaConfig(VehicleAreaZone.ROW_2_RIGHT, MIN_PROP_FLOAT, MAX_PROP_FLOAT)
                    .build(),
            VehiclePropConfigBuilder.newBuilder(CUSTOM_BYTES_PROP_ID_1)
                    .setSupportedAreas(VehicleAreaZone.ROW_1_LEFT | VehicleAreaZone.ROW_1_RIGHT)
                    .build(),
            VehiclePropConfigBuilder.newBuilder(CUSTOM_BYTES_PROP_ID_2).build(),
            VehiclePropConfigBuilder.newBuilder(CUSTOM_STRING_PROP_ID).build(),
    };

    private CarVendorExtensionManager mManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mManager = (CarVendorExtensionManager) getCar().getCarManager(Car.VENDOR_EXTENSION_SERVICE);
        assertNotNull(mManager);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testPropertyList() throws Exception {
        List<CarPropertyConfig> configs = mManager.getProperties();
        assertEquals(mConfigs.length, configs.size());

        SparseArray<CarPropertyConfig> configById = new SparseArray<>(configs.size());
        for (CarPropertyConfig config : configs) {
            configById.put(config.getPropertyId(), config);
        }

        CarPropertyConfig prop1 = configById.get(CUSTOM_GLOBAL_INT_PROP_ID);
        assertNotNull(prop1);
        assertEquals(Integer.class, prop1.getPropertyType());
        assertEquals(MIN_PROP_INT32, prop1.getMinValue());
        assertEquals(MAX_PROP_INT32, prop1.getMaxValue());
    }

    public void testIntGlobalProperty() throws Exception {
        final int value = 0xbeef;
        mManager.setGlobalProperty(Integer.class, CUSTOM_GLOBAL_INT_PROP_ID, value);
        int actualValue = mManager.getGlobalProperty(Integer.class, CUSTOM_GLOBAL_INT_PROP_ID);
        assertEquals(value, actualValue);
    }

    public void testFloatZonedProperty() throws Exception {
        final float value = MIN_PROP_FLOAT + 1;
        mManager.setProperty(
                Float.class,
                CUSTOM_ZONED_FLOAT_PROP_ID,
                VehicleAreaZone.ROW_1_RIGHT,
                value);

        float actualValue = mManager.getProperty(
                Float.class, CUSTOM_ZONED_FLOAT_PROP_ID, VehicleAreaZone.ROW_1_RIGHT);
        assertEquals(value, actualValue, EPS);
    }

    public void testByteArrayProperty() throws Exception {
        final byte[] expectedData = new byte[] { 1, 2, 3, 4, -1, 127, -127, 0 };

        // Write to CUSTOM_BYTES_PROP_ID_1 and read this value from CUSTOM_BYTES_PROP_ID_2
        mManager.setGlobalProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_1,
                expectedData);

        byte[] actualData = mManager.getGlobalProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_2);

        assertEquals(Arrays.toString(expectedData), Arrays.toString(actualData));
    }

    public void testLargeByteArrayProperty() throws Exception {
        // Allocate array of byte which is greater than binder transaction buffer limitation.
        byte[] expectedData = new byte[2 * MILLION];

        new Random(SystemClock.elapsedRealtimeNanos())
            .nextBytes(expectedData);

        // Write to CUSTOM_BYTES_PROP_ID_1 and read this value from CUSTOM_BYTES_PROP_ID_2
        mManager.setGlobalProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_1,
                expectedData);

        byte[] actualData = mManager.getGlobalProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_2);

        Assert.assertArrayEquals(expectedData, actualData);
    }

    public void testLargeStringProperty() throws Exception {
        // Allocate string which is greater than binder transaction buffer limitation.
        String expectedString = generateRandomString(2 * MILLION,
                "abcdefghijKLMNεὕρηκα!@#$%^&*()[]{}:\"\t\n\r!'");

        mManager.setGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID,
                expectedString);

        String actualString = mManager.getGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID);

        assertEquals(expectedString, actualString);
    }

    public void testStringProperty() throws Exception {
        final String expectedString = "εὕρηκα!";  // Test some utf as well.

        mManager.setGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID,
                expectedString);

        String actualString = mManager.getGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID);

        assertEquals(expectedString, actualString);
    }

    private static String generateRandomString(int length, String allowedSymbols) {
        Random r = new Random(SystemClock.elapsedRealtimeNanos());
        StringBuilder sb = new StringBuilder(length);
        char[] chars = allowedSymbols.toCharArray();
        for (int i = 0; i < length; i++) {
            sb.append(chars[r.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    @Override
    protected synchronized MockedVehicleHal createMockedVehicleHal() {
        MockedVehicleHal hal = new VendorExtMockedVehicleHal();
        hal.addProperties(mConfigs);
        return hal;
    }

    private static class VendorExtMockedVehicleHal extends MockedVehicleHal {
        private final SparseArray<VehiclePropValue> mValues = new SparseArray<>();

        private byte[] mBytes = null;

        @Override
        public synchronized int set(VehiclePropValue propValue) {
            if (propValue.prop == CUSTOM_BYTES_PROP_ID_1) {
                mBytes = toByteArray(propValue.value.bytes);
            }

            mValues.put(propValue.prop, propValue);
            return StatusCode.OK;
        }

        @Override
        public synchronized void get(VehiclePropValue requestedPropValue, getCallback cb) {
            if (!isVendorProperty(requestedPropValue.prop)) {
                cb.onValues(StatusCode.INVALID_ARG, null);
                return;
            }
            VehiclePropValue result = new VehiclePropValue();
            result.prop = requestedPropValue.prop;
            result.areaId = requestedPropValue.areaId;

            if (requestedPropValue.prop == CUSTOM_BYTES_PROP_ID_2 && mBytes != null) {
                Log.d(TAG, "Returning byte array property, value size " + mBytes.length);
                result.value.bytes.ensureCapacity(mBytes.length);
                for (byte b : mBytes) {
                    result.value.bytes.add(b);
                }
            } else {
                VehiclePropValue existingValue = mValues.get(requestedPropValue.prop);
                if (existingValue != null) {
                    result = existingValue;
                } else {
                    result = requestedPropValue;
                }
            }
            cb.onValues(StatusCode.OK, result);
        }

        private boolean isVendorProperty(int prop) {
            return VehiclePropertyGroup.VENDOR == (prop & VehiclePropertyGroup.VENDOR);
        }
    }
}
