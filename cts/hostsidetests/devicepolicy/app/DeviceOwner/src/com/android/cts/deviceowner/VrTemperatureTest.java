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
package com.android.cts.deviceowner;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.HardwarePropertiesManager;
import android.test.AndroidTestCase;
import android.util.Log;

import java.lang.Math;

public class VrTemperatureTest extends BaseDeviceOwnerTest {
    public static final int MIN_DEVICE_TEMPERATURE = -20;
    public static final int MAX_DEVICE_TEMPERATURE = 200;

    public boolean supportsVrHighPerformance() {
        PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    private void checkDeviceTemp(float temp, float throttlingTemp, float shutdownTemp,
        float vrThrottlingTemp) {
        // Compare current temperature and shutdown threshold.
        assertTrue(temp <= shutdownTemp ||
                shutdownTemp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE);

        // Compare throttling and shutdown thresholds.
        assertTrue(throttlingTemp <= shutdownTemp ||
                shutdownTemp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE ||
                throttlingTemp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE);

        // Compare VR throttling and shutdown thresholds.
        assertTrue(vrThrottlingTemp <= MIN_DEVICE_TEMPERATURE ||
                vrThrottlingTemp <= shutdownTemp ||
                shutdownTemp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE);
    }

    private void checkTemps(float[] temps, float[] throttlingThresholds,
            float[] shutdownThresholds, float[] vrThrottlingThresholds) {
        assertEquals(temps.length, throttlingThresholds.length);
        assertEquals(temps.length, shutdownThresholds.length);
        boolean hasVrThreshold = vrThrottlingThresholds != null &&
                vrThrottlingThresholds.length > 0;
        if (hasVrThreshold) {
          assertEquals(temps.length, vrThrottlingThresholds.length);
        }
        for (int i = 0; i < temps.length; ++i) {
            checkDeviceTemp(temps[i], throttlingThresholds[i], shutdownThresholds[i],
                    !hasVrThreshold ? MIN_DEVICE_TEMPERATURE : vrThrottlingThresholds[i]);
        }
    }

    /**
     * Tests that temperature sensors return valid values.
     */
    public void testVrTemperatures() throws InterruptedException, SecurityException {
        if (!supportsVrHighPerformance())
            return;

        HardwarePropertiesManager hm = (HardwarePropertiesManager) getContext().getSystemService(
                Context.HARDWARE_PROPERTIES_SERVICE);

        float[] cpuTemps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT);
        float[] cpuThrottlingThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_THROTTLING);
        float[] cpuShutdownThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_SHUTDOWN);

        float[] gpuTemps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT);
        float[] gpuThrottlingThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                HardwarePropertiesManager.TEMPERATURE_THROTTLING);
        float[] gpuShutdownThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                HardwarePropertiesManager.TEMPERATURE_SHUTDOWN);

        float[] batteryTemps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY,
                HardwarePropertiesManager.TEMPERATURE_CURRENT);
        float[] batteryThrottlingThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY,
                HardwarePropertiesManager.TEMPERATURE_THROTTLING);
        float[] batteryShutdownThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY,
                HardwarePropertiesManager.TEMPERATURE_SHUTDOWN);

        float[] skinTemps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                HardwarePropertiesManager.TEMPERATURE_CURRENT);
        float[] skinThrottlingThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                HardwarePropertiesManager.TEMPERATURE_THROTTLING);
        float[] skinShutdownThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                HardwarePropertiesManager.TEMPERATURE_SHUTDOWN);
        float[] skinVrThrottlingThresholds = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                HardwarePropertiesManager.TEMPERATURE_THROTTLING_BELOW_VR_MIN);

        checkTemps(cpuTemps, cpuThrottlingThresholds, cpuShutdownThresholds, null);
        checkTemps(gpuTemps, gpuThrottlingThresholds, gpuShutdownThresholds, null);
        checkTemps(batteryTemps, batteryThrottlingThresholds, batteryShutdownThresholds, null);
        checkTemps(skinTemps, skinThrottlingThresholds, skinShutdownThresholds,
                skinVrThrottlingThresholds);
    }
}
