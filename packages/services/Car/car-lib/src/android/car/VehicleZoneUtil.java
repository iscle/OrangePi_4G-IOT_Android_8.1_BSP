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
package android.car;

import android.annotation.SystemApi;

/**
 * Collection of utilities for handling zones
 * @hide
 */
@SystemApi
public final class VehicleZoneUtil {

    /**
     * Change zone flag into index with available zones.
     * For example with zoned of 0x80000001, 0x1 will be index 0 and 0x80000000 will be index 2.
     * @param available zones, should not be zero
     * @param zone flag for the zone to get index, should not be zero and should be one of the flags
     *             defined in zones.
     * @return index of desired zone.
     * @throws IllegalArgumentException if zones or zone is invalid.
     */
    public static int zoneToIndex(int zones, int zone) throws IllegalArgumentException {
        if ((zone == 0) || // check that zone is non-zero
                ((zone & zones) != zone) || // check that zone is inside of zones
                ((zone & (zone - 1)) != 0)) { // check that zone only has one bit set
            throw new IllegalArgumentException("Invalid zones 0x" + Integer.toHexString(zones) +
                    " or zone 0x" + Integer.toHexString(zone));
        }
        int index = -1;
        while((zone & zones) != 0) {
            index++;
            zones &= zones - 1;
        }
        return index;
    }

    /**
     * Return number of zones (non-zero flag) from given zones.
     */
    public static int getNumberOfZones(int zones) {
        int numZones = 0;
        while (zones != 0) {
            zones &= zones - 1;
            numZones++;
        }
        return numZones;
    }

    /**
     * Return bit flag of first zone. If zones is 0, it will just return 0.
     * @param zones can be 0 if there is no zone
     * @return
     */
    public static int getFirstZone(int zones) {
        if (zones == 0) {
            return 0;
        }
        int xorFlag = zones & (zones - 1);

        return zones ^ xorFlag;
    }

    /**
     * Return bit flag of zone available after startingZone. For zones of 0x7 with startingZone of
     * 0x2, it will return 0x4. If no zone exist after startingZone, it will return 0.
     * @param zones
     * @param startingZone A bit flag representing a zone. This does not necessarily be one of flags
     *                     available in zones.
     * @return
     * @throws IllegalArgumentException If startingZone is invalid.
     */
    public static int getNextZone(int zones, int startingZone) throws IllegalArgumentException {
        if ((startingZone & (startingZone - 1)) != 0 || startingZone == 0) {
            throw new IllegalArgumentException(
                    "Starting zone should represent only one bit flag: 0x" +
                            Integer.toHexString(startingZone));
        }

        // Create a mask that sets all bits above the current one
        int mask = startingZone << 1;
        mask -= 1;
        mask = ~mask;
        return getFirstZone(zones & mask);
    }

    /**
     * Return array of zone with each active zone in one index. This can be useful for iterating
     * all available zones.
     */
    public static int[] listAllZones(int zones) {
        int numberOfZones = getNumberOfZones(zones);
        int[] list = new int[numberOfZones];
        if (numberOfZones == 0) {
            return list;
        }

        int arrayIndex = 0;
        while (zones != 0) {
            int xorFlag = zones & (zones - 1);
            int zone = zones ^ xorFlag;
            list[arrayIndex++] = zone;
            zones = xorFlag;
        }
        return list;
    }

    private VehicleZoneUtil() {}
}
