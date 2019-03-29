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

package com.android.server.cts;

import android.service.battery.BatteryServiceDumpProto;
import com.android.tradefed.device.DeviceNotAvailableException;

/** Test to check that the battery manager properly outputs its dump state. */
public class BatteryIncidentTest extends ProtoDumpTestCase {
    private final String LEANBACK_FEATURE = "android.software.leanback";

    public void testBatteryServiceDump() throws Exception {
        final BatteryServiceDumpProto dump =
                getDump(BatteryServiceDumpProto.parser(), "dumpsys battery --proto");

        if (!dump.getIsPresent()) {
            /* If the battery isn't present, no need to run this test. */
            return;
        }

        if (isLeanback()) {
            /* Android TV reports that it has a battery, but it doesn't really. */
            return;
        }

        assertTrue(
                dump.getPlugged()
                        != BatteryServiceDumpProto.BatteryPlugged.BATTERY_PLUGGED_WIRELESS);
        assertTrue(dump.getChargeCounter() > 0);
        assertTrue(
                dump.getStatus() != BatteryServiceDumpProto.BatteryStatus.BATTERY_STATUS_INVALID);
        assertTrue(
                dump.getHealth() != BatteryServiceDumpProto.BatteryHealth.BATTERY_HEALTH_INVALID);
        int scale = dump.getScale();
        assertTrue(scale > 0);
        int level = dump.getLevel();
        assertTrue(level >= 0 && level <= scale);
        assertTrue(dump.getVoltage() > 0);
        assertTrue(dump.getTemperature() > 0);
    }

    private boolean isLeanback() throws DeviceNotAvailableException {
        final String commandOutput = getDevice().executeShellCommand("pm list features");
        return commandOutput.contains(LEANBACK_FEATURE);
    }
}
