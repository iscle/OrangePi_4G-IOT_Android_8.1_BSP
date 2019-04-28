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

import android.car.VehicleDoor;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaDoor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class VehicleDoorTest extends AndroidTestCase {

    public void testMatchWithVehicleHal() {
        assertEquals(VehicleAreaDoor.HOOD, VehicleDoor.DOOR_HOOD);
        assertEquals(VehicleAreaDoor.REAR, VehicleDoor.DOOR_REAR);
        assertEquals(VehicleAreaDoor.ROW_1_LEFT, VehicleDoor.DOOR_ROW_1_LEFT);
        assertEquals(VehicleAreaDoor.ROW_1_RIGHT, VehicleDoor.DOOR_ROW_1_RIGHT);
        assertEquals(VehicleAreaDoor.ROW_2_LEFT, VehicleDoor.DOOR_ROW_2_LEFT);
        assertEquals(VehicleAreaDoor.ROW_2_RIGHT, VehicleDoor.DOOR_ROW_2_RIGHT);
        assertEquals(VehicleAreaDoor.ROW_3_LEFT, VehicleDoor.DOOR_ROW_3_LEFT);
        assertEquals(VehicleAreaDoor.ROW_3_RIGHT, VehicleDoor.DOOR_ROW_3_RIGHT);
    }
}
