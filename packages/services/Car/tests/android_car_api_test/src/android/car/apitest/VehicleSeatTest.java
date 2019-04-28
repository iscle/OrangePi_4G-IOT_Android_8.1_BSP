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
package android.car.apitest;

import android.car.VehicleSeat;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaSeat;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class VehicleSeatTest extends AndroidTestCase {

    public void testMatchWithVehicleHal() {
        assertEquals(VehicleAreaSeat.ROW_1_LEFT, VehicleSeat.SEAT_ROW_1_LEFT);
        assertEquals(VehicleAreaSeat.ROW_1_CENTER, VehicleSeat.SEAT_ROW_1_CENTER);
        assertEquals(VehicleAreaSeat.ROW_1_RIGHT, VehicleSeat.SEAT_ROW_1_RIGHT);
        assertEquals(VehicleAreaSeat.ROW_2_LEFT, VehicleSeat.SEAT_ROW_2_LEFT);
        assertEquals(VehicleAreaSeat.ROW_2_CENTER, VehicleSeat.SEAT_ROW_2_CENTER);
        assertEquals(VehicleAreaSeat.ROW_2_RIGHT, VehicleSeat.SEAT_ROW_2_RIGHT);
        assertEquals(VehicleAreaSeat.ROW_3_LEFT, VehicleSeat.SEAT_ROW_3_LEFT);
        assertEquals(VehicleAreaSeat.ROW_3_CENTER, VehicleSeat.SEAT_ROW_3_CENTER);
        assertEquals(VehicleAreaSeat.ROW_3_RIGHT, VehicleSeat.SEAT_ROW_3_RIGHT);
    }
}
