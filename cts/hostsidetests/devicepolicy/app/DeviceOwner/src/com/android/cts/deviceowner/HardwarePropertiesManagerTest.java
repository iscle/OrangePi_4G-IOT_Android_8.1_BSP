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
import android.os.CpuUsageInfo;
import android.os.HardwarePropertiesManager;
import android.os.SystemClock;

import java.lang.Math;

/**
 * Test {@link HardwarePropertiesManager}
 */
public class HardwarePropertiesManagerTest extends BaseDeviceOwnerTest {
    public static final int MAX_FAN_SPEED = 20000;
    public static final int MAX_DEVICE_TEMPERATURE = 200;
    public static final int MONITORING_ITERATION_NUMBER = 10;

    // Time between checks in milliseconds.
    public static final long SLEEP_TIME = 10;

    private void checkFanSpeed(float speed) {
        assertTrue(speed >= 0 && speed < MAX_FAN_SPEED);
    }

    private void checkDeviceTemp(float temp, float throttlingTemp, float shutdownTemp) {
        // Check validity of current temperature.
        assertTrue(Math.abs(temp) < MAX_DEVICE_TEMPERATURE
                || temp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE);

        // Compare current temperature and shutdown threshold.
        assertTrue(temp < shutdownTemp || temp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE
                || shutdownTemp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE);
        // Compare throttling and shutdown thresholds.
        assertTrue(throttlingTemp < shutdownTemp
                || throttlingTemp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE
                || shutdownTemp == HardwarePropertiesManager.UNDEFINED_TEMPERATURE);
    }

    private void checkCpuUsageInfo(CpuUsageInfo info) {
        assertTrue(info == null || (info.getActive() >= 0 && info.getTotal() >= 0
                && info.getTotal() >= info.getActive()));
    }

    private void checkFanSpeeds(float[] fanSpeeds) {
        for (float speed : fanSpeeds) {
            checkFanSpeed(speed);
        }
    }

    private void checkTemps(float[] temps, float[] throttlingThresholds,
            float[] shutdownThresholds) {
        assertEquals(temps.length, throttlingThresholds.length);
        assertEquals(temps.length, shutdownThresholds.length);
        for (int i = 0; i < temps.length; ++i) {
            checkDeviceTemp(temps[i], throttlingThresholds[i], shutdownThresholds[i]);
        }
    }

    private void checkCpuUsages(CpuUsageInfo[] cpuUsages) {
        for (CpuUsageInfo info : cpuUsages) {
            checkCpuUsageInfo(info);
        }
    }

    // Check validity of new array of fan speeds:
    // the number of fans should be the same.
    private void checkFanSpeeds(float[] speeds, float[] oldSpeeds) {
        assertEquals(speeds.length, oldSpeeds.length);
    }

    // Check validity of new array of cpu usages:
    // The number of CPUs should be the same and total/active time should not decrease.
    private void checkCpuUsages(CpuUsageInfo[] infos,
            CpuUsageInfo[] oldInfos) {
        assertEquals(infos.length, oldInfos.length);
        for (int i = 0; i < infos.length; ++i) {
            assertTrue(oldInfos[i] == null || infos[i] == null
                    || (oldInfos[i].getActive() <= infos[i].getActive()
                        && oldInfos[i].getTotal() <= infos[i].getTotal()));
        }
    }

    /**
     * test points:
     * 1. Get fan speeds, device temperatures and CPU usage information.
     * 2. Check for validity.
     * 3. Sleep.
     * 4. Do it 10 times and compare with old ones.
     */
    public void testHardwarePropertiesManager() throws InterruptedException,
            SecurityException {
        HardwarePropertiesManager hm = (HardwarePropertiesManager) getContext().getSystemService(
                Context.HARDWARE_PROPERTIES_SERVICE);

        float[] oldFanSpeeds = hm.getFanSpeeds();

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

        CpuUsageInfo[] oldCpuUsages = hm.getCpuUsages();

        checkFanSpeeds(oldFanSpeeds);
        checkTemps(cpuTemps, cpuThrottlingThresholds, cpuShutdownThresholds);
        checkTemps(gpuTemps, gpuThrottlingThresholds, gpuShutdownThresholds);
        checkTemps(batteryTemps, batteryThrottlingThresholds, batteryShutdownThresholds);
        checkTemps(skinTemps, skinThrottlingThresholds, skinShutdownThresholds);
        checkCpuUsages(oldCpuUsages);

        for (int i = 0; i < MONITORING_ITERATION_NUMBER; i++) {
            Thread.sleep(SLEEP_TIME);

            float[] fanSpeeds = hm.getFanSpeeds();
            cpuTemps = hm.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT);
            gpuTemps = hm.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT);
            batteryTemps = hm.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT);
            skinTemps = hm.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT);
            CpuUsageInfo[] cpuUsages = hm.getCpuUsages();

            checkFanSpeeds(fanSpeeds);
            checkTemps(cpuTemps, cpuThrottlingThresholds, cpuShutdownThresholds);
            checkTemps(gpuTemps, gpuThrottlingThresholds, gpuShutdownThresholds);
            checkTemps(batteryTemps, batteryThrottlingThresholds, batteryShutdownThresholds);
            checkTemps(skinTemps, skinThrottlingThresholds, skinShutdownThresholds);
            checkCpuUsages(cpuUsages);

            // No need to compare length of old and new temperature arrays:
            // they are compared through throttling and shutdown threshold arrays lengths.
            checkFanSpeeds(fanSpeeds, oldFanSpeeds);
            checkCpuUsages(cpuUsages, oldCpuUsages);

            oldFanSpeeds = fanSpeeds;
            oldCpuUsages = cpuUsages;
        }
    }
}
