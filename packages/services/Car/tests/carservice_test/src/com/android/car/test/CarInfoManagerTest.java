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
import android.car.CarInfoManager;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.car.vehiclehal.VehiclePropValueBuilder;

@MediumTest
public class CarInfoManagerTest extends MockedCarTestBase {
    private static final String MAKE_NAME = "ANDROID";

    private CarInfoManager mCarInfoManager;

    @Override
    protected synchronized void configureMockedHal() {
        addStaticProperty(VehicleProperty.INFO_MAKE,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.INFO_MAKE)
                        .setStringValue(MAKE_NAME)
                        .build());

    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarInfoManager = (CarInfoManager) getCar().getCarManager(Car.INFO_SERVICE);
    }

    public void testVehicleId() throws Exception {
        assertNotNull(mCarInfoManager.getVehicleId());
    }

    public void testManufacturer() throws Exception {
        assertEquals(MAKE_NAME, mCarInfoManager.getManufacturer());
    }

    public void testNullItems() throws Exception {
        assertNull(mCarInfoManager.getModel());
        assertNull(mCarInfoManager.getModelYear());
    }
}
