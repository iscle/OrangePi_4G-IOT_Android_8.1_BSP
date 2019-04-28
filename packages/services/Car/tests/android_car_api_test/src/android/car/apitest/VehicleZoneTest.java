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

import android.car.VehicleZone;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaZone;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class VehicleZoneTest extends AndroidTestCase {

    public void testMatchWithVehicleHal() {
        assertEquals(VehicleAreaZone.WHOLE_CABIN, VehicleZone.ZONE_ALL);
        assertEquals(VehicleAreaZone.ROW_1, VehicleZone.ZONE_ROW_1_ALL);
        assertEquals(VehicleAreaZone.ROW_1_CENTER, VehicleZone.ZONE_ROW_1_CENTER);
        assertEquals(VehicleAreaZone.ROW_1_LEFT, VehicleZone.ZONE_ROW_1_LEFT);
        assertEquals(VehicleAreaZone.ROW_1_RIGHT, VehicleZone.ZONE_ROW_1_RIGHT);
        assertEquals(VehicleAreaZone.ROW_2, VehicleZone.ZONE_ROW_2_ALL);
        assertEquals(VehicleAreaZone.ROW_2_CENTER, VehicleZone.ZONE_ROW_2_CENTER);
        assertEquals(VehicleAreaZone.ROW_2_LEFT, VehicleZone.ZONE_ROW_2_LEFT);
        assertEquals(VehicleAreaZone.ROW_2_RIGHT, VehicleZone.ZONE_ROW_2_RIGHT);
        assertEquals(VehicleAreaZone.ROW_3, VehicleZone.ZONE_ROW_3_ALL);
        assertEquals(VehicleAreaZone.ROW_3_CENTER, VehicleZone.ZONE_ROW_3_CENTER);
        assertEquals(VehicleAreaZone.ROW_3_LEFT, VehicleZone.ZONE_ROW_3_LEFT);
        assertEquals(VehicleAreaZone.ROW_3_RIGHT, VehicleZone.ZONE_ROW_3_RIGHT);
        assertEquals(VehicleAreaZone.ROW_4, VehicleZone.ZONE_ROW_4_ALL);
        assertEquals(VehicleAreaZone.ROW_4_CENTER, VehicleZone.ZONE_ROW_4_CENTER);
        assertEquals(VehicleAreaZone.ROW_4_LEFT, VehicleZone.ZONE_ROW_4_LEFT);
        assertEquals(VehicleAreaZone.ROW_4_RIGHT, VehicleZone.ZONE_ROW_4_RIGHT);
    }
}
