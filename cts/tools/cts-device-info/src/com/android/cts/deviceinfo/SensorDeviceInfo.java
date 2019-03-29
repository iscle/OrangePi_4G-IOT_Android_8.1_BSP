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
package com.android.cts.deviceinfo;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorManager;
import android.os.Bundle;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

import java.lang.Exception;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Sensor device info collector.
 */
public class SensorDeviceInfo extends DeviceInfo {

    private static final String SENSOR = "sensor";
    private static final String REPORTING_MODE = "reporting_mode";
    private static final String NAME = "name";
    private static final String VENDOR = "vendor";
    private static final String TYPE = "type";
    private static final String VERSION = "version";
    private static final String MAXIMUM_RANGE = "maximum_range";
    private static final String RESOLUTION = "resolution";
    private static final String POWER = "power";
    private static final String MIN_DELAY = "min_delay";
    private static final String FIFO_RESERVED_EVENT_COUNT =
            "fifo_reserved_event_count";
    private static final String FIFO_MAX_EVENT_COUNT = "fifo_max_event_count";
    private static final String STRING_TYPE = "string_type";
    private static final String ID = "id";
    private static final String MAX_DELAY = "max_delay";
    private static final String IS_WAKE_UP_SENSOR = "is_wake_up_sensor";
    private static final String IS_DYNAMIC_SENSOR = "is_dynamic_sensor";
    private static final String IS_ADDITONAL_INFO_SUPPORTED =
            "is_additional_info_supported";
    private static final String HIGHEST_DIRECT_REPORT_RATE_LEVEL =
            "highest_direct_report_rate_level";
    private static final String SUPPORTED_DIRECT_CHANNEL_TYPE =
            "supported_direct_channel_type";
    private static final int[] CHANNEL_TYPES = new int[] {
            SensorDirectChannel.TYPE_MEMORY_FILE,
            SensorDirectChannel.TYPE_HARDWARE_BUFFER };

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        SensorManager sensorManager = (SensorManager)
                getContext().getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        store.startArray(SENSOR);
        for (Sensor sensor : sensors) {
            store.startGroup();
            store.addResult(REPORTING_MODE, sensor.getReportingMode());
            store.addResult(NAME, sensor.getName());
            store.addResult(VENDOR, sensor.getVendor());
            store.addResult(TYPE, sensor.getType());
            store.addResult(VERSION, sensor.getVersion());
            store.addResult(MAXIMUM_RANGE, sensor.getMaximumRange());
            store.addResult(RESOLUTION, sensor.getResolution());
            store.addResult(POWER, sensor.getPower());
            store.addResult(MIN_DELAY, sensor.getMinDelay());
            store.addResult(FIFO_RESERVED_EVENT_COUNT,
                    sensor.getFifoReservedEventCount());
            store.addResult(FIFO_MAX_EVENT_COUNT,
                    sensor.getFifoMaxEventCount());
            store.addResult(STRING_TYPE, sensor.getStringType());
            store.addResult(ID, sensor.getId());
            store.addResult(MAX_DELAY, sensor.getMaxDelay());
            store.addResult(IS_WAKE_UP_SENSOR, sensor.isWakeUpSensor());
            store.addResult(IS_DYNAMIC_SENSOR, sensor.isDynamicSensor());
            store.addResult(IS_ADDITONAL_INFO_SUPPORTED,
                    sensor.isAdditionalInfoSupported());
            store.addResult(HIGHEST_DIRECT_REPORT_RATE_LEVEL,
                    sensor.getHighestDirectReportRateLevel());

            List<Integer> supportedChannelType = new ArrayList<>();
            for (int channelType : CHANNEL_TYPES) {
                if (sensor.isDirectChannelTypeSupported(channelType)) {
                    supportedChannelType.add(channelType);
                }
            }
            store.addArrayResult(SUPPORTED_DIRECT_CHANNEL_TYPE,
                    supportedChannelType.stream().mapToInt(i->i).toArray());
            store.endGroup();
        }
        store.endArray(); // Sensor
    }
}
