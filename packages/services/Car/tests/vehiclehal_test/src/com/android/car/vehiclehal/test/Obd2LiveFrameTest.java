/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.vehiclehal.test;

import static com.android.car.vehiclehal.test.Utils.isVhalPropertyAvailable;
import static com.android.car.vehiclehal.test.Utils.readVhalProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.RemoteException;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;

/** Test retrieving the OBD2_LIVE_FRAME property from VHAL */
public class Obd2LiveFrameTest {
    private static final String TAG = Utils.concatTag(Obd2LiveFrameTest.class);

    private IVehicle mVehicle = null;

    @Before
    public void setUp() throws Exception {
        mVehicle = Utils.getVehicle();
        assumeTrue("Live frame not available, test-case ignored.", isLiveFrameAvailable());
    }

    @Test
    public void testLiveFrame() throws RemoteException {
        readVhalProperty(
                mVehicle,
                VehicleProperty.OBD2_LIVE_FRAME,
                (Integer status, VehiclePropValue value) -> {
                    assertEquals(StatusCode.OK, status.intValue());
                    assertNotNull("OBD2_LIVE_FRAME is supported; should not be null", value);
                    Log.i(TAG, "dump of OBD2_LIVE_FRAME:\n" + value);
                    return true;
                });
    }

    private boolean isLiveFrameAvailable() throws RemoteException {
        return isVhalPropertyAvailable(mVehicle, VehicleProperty.OBD2_LIVE_FRAME);
    }
}
