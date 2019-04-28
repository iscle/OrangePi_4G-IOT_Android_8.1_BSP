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

import android.car.VehicleZoneUtil;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class VehicleZoneUtilTest extends AndroidTestCase {

    public void testZoneToIndex() {
        int[] zones = {0, 0, 1, 0xf0, 0xf};
        int[] zone  = {0, 1, 0,  0x1, 0x6};

        // Test failure cases
        for (int i = 0; i < zones.length; i++) {
            try {
                int r = VehicleZoneUtil.zoneToIndex(zones[i], zone[i]);
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        zones = new int[] {0xffffffff, 0xffffffff, 0x1002, 0x1002};
        zone  = new int[] {       0x1, 0x80000000,    0x2, 0x1000};
        int[] result =    {         0,         31,      0,      1};

        // Test passing cases
        for (int i = 0; i < zones.length; i++) {
            assertEquals(result[i], VehicleZoneUtil.zoneToIndex(zones[i], zone[i]));
        }
    }

    public void testGetNumberOfZones() {
        int[] zones  = {0, 0x1, 0x7, 0xffffffff};
        int[] result = {0,   1,   3,         32};

        for (int i = 0; i < zones.length; i++) {
            assertEquals(result[i], VehicleZoneUtil.getNumberOfZones(zones[i]));
        }
    }

    public void testGetFirstZone() {
        int[] zones  = {0, 1, 0xffff00};
        int[] result = {0, 1,    0x100};

        for (int i = 0; i < zones.length; i++) {
            assertEquals(result[i], VehicleZoneUtil.getFirstZone(zones[i]));
        }
    }

    public void testGetNextZone() {
        int[] zones        = {0, 1, 0x7};
        int[] startingZone = {0, 0, 0x3};

        // Test failure cases
        for (int i = 0; i < zones.length; i++) {
            try {
                int r = VehicleZoneUtil.getNextZone(zones[i], startingZone[i]);
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        zones        = new int[] {0, 1, 0xff00, 0xff00, 0xf, 0xf0000000};
        startingZone = new int[] {1, 1,      1,  0x100, 0x2, 0x40000000};
        int[] result =           {0, 0,  0x100,  0x200, 0x4, 0x80000000};

        // Test passing cases
        for (int i = 0; i < zones.length; i++) {
            assertEquals(result[i], VehicleZoneUtil.getNextZone(zones[i], startingZone[i]));
        }
    }

    public void testGetAllZones() {
        int zones = 0;
        int[] list = VehicleZoneUtil.listAllZones(zones);
        assertEquals(0, list.length);
        zones = 0xffffffff;
        list = VehicleZoneUtil.listAllZones(zones);
        assertEquals(32, list.length);
        for (int i = 0; i < 32; i++) {
            assertEquals(0x1<<i, list[i]);
        }
        zones = 0x1001;
        list = VehicleZoneUtil.listAllZones(zones);
        assertEquals(2, list.length);
        assertEquals(0x1, list[0]);
        assertEquals(0x1000, list[1]);
    }
}
