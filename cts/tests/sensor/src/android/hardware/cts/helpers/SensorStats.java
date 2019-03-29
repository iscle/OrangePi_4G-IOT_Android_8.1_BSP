/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.cts.helpers;

import android.hardware.Sensor;
import android.hardware.cts.helpers.sensoroperations.SensorOperation;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Class used to store stats related to {@link SensorOperation}s. Sensor stats may be linked
 * together so that they form a tree.
 */
public class SensorStats {
    public static final String DELIMITER = "__";

    public static final String ERROR = "error";
    public static final String EVENT_FIFO_LENGTH = "event_fifo_length_observed";
    public static final String EVENT_GAP_COUNT_KEY = "event_gap_count";
    public static final String EVENT_GAP_POSITIONS_KEY = "event_gap_positions";
    public static final String EVENT_OUT_OF_ORDER_COUNT_KEY = "event_out_of_order_count";
    public static final String EVENT_OUT_OF_ORDER_POSITIONS_KEY = "event_out_of_order_positions";
    public static final String EVENT_TIME_SYNCHRONIZATION_COUNT_KEY =
            "event_time_synchronization_count";
    public static final String EVENT_TIME_SYNCHRONIZATION_POSITIONS_KEY =
            "event_time_synchronization_positions";
    public static final String EVENT_TIME_WRONG_CLOCKSOURCE_COUNT_KEY =
            "event_time_wrong_clocksource_count";
    public static final String EVENT_TIME_WRONG_CLOCKSOURCE_POSITIONS_KEY =
            "event_time_wrong_clocksource_positions";
    public static final String EVENT_COUNT_KEY = "event_count";
    public static final String EVENT_COUNT_EXPECTED_KEY = "event_count_expected";
    public static final String EVENT_LOG_FILENAME = "event_log_filename";
    public static final String WRONG_SENSOR_KEY = "wrong_sensor_observed";
    public static final String FREQUENCY_KEY = "frequency";
    public static final String JITTER_95_PERCENTILE_PERCENT_KEY = "jitter_95_percentile_percent";
    public static final String MEAN_KEY = "mean";
    public static final String STANDARD_DEVIATION_KEY = "standard_deviation";
    public static final String MAGNITUDE_KEY = "magnitude";
    public static final String DELAYED_BATCH_DELIVERY = "delayed_batch_delivery";
    public static final String INITIAL_MEAN_KEY = "initial_mean";
    public static final String LATER_MEAN_KEY = "later_mean";

    private final Map<String, Object> mValues = new HashMap<>();
    private final Map<String, SensorStats> mSensorStats = new HashMap<>();

    /**
     * Add a value.
     *
     * @param key the key.
     * @param value the value as an {@link Object}.
     */
    public synchronized void addValue(String key, Object value) {
        if (value == null) {
            return;
        }
        mValues.put(key, value);
    }

    /**
     * Add a nested {@link SensorStats}. This is useful for keeping track of stats in a
     * {@link SensorOperation} tree.
     *
     * @param key the key
     * @param stats the sub {@link SensorStats} object.
     */
    public synchronized void addSensorStats(String key, SensorStats stats) {
        if (stats == null) {
            return;
        }
        mSensorStats.put(key, stats);
    }

    /**
     * Get the keys from the values table. Will not get the keys from the nested
     * {@link SensorStats}.
     */
    public synchronized Set<String> getKeys() {
        return mValues.keySet();
    }

    /**
     * Get a value from the values table. Will not attempt to get values from nested
     * {@link SensorStats}.
     */
    public synchronized Object getValue(String key) {
        return mValues.get(key);
    }

    /**
     * Flattens the map and all sub {@link SensorStats} objects. Keys will be flattened using
     * {@value #DELIMITER}. For example, if a sub {@link SensorStats} is added with key
     * {@code "key1"} containing the key value pair {@code \("key2", "value"\)}, the flattened map
     * will contain the entry {@code \("key1__key2", "value"\)}.
     *
     * @return a {@link Map} containing all stats from the value and sub {@link SensorStats}.
     */
    public synchronized Map<String, Object> flatten() {
        final Map<String, Object> flattenedMap = new HashMap<>(mValues);
        for (Entry<String, SensorStats> statsEntry : mSensorStats.entrySet()) {
            for (Entry<String, Object> valueEntry : statsEntry.getValue().flatten().entrySet()) {
                String key = statsEntry.getKey() + DELIMITER + valueEntry.getKey();
                flattenedMap.put(key, valueEntry.getValue());
            }
        }
        return flattenedMap;
    }

    /**
     * Utility method to log the stats to the logcat.
     */
    public void log(String tag) {
        final Map<String, Object> flattened = flatten();
        for (String key : getSortedKeys(flattened)) {
            Object value = flattened.get(key);
            Log.v(tag, String.format("%s: %s", key, getValueString(value)));
        }
    }

    /**
     * Utility method to log the stats to a file. Will overwrite the file if it already exists.
     */
    public void logToFile(String fileName) throws IOException {
        File statsDirectory = SensorCtsHelper.getSensorTestDataDirectory("stats/");
        File logFile = new File(statsDirectory, fileName);
        final Map<String, Object> flattened = flatten();
        FileWriter fileWriter = new FileWriter(logFile, false /* append */);
        try (BufferedWriter writer = new BufferedWriter(fileWriter)) {
            for (String key : getSortedKeys(flattened)) {
                Object value = flattened.get(key);
                writer.write(String.format("%s: %s\n", key, getValueString(value)));
            }
        }
    }

    /**
     * Provides a sanitized sensor name, that can be used in file names.
     * See {@link #logToFile(String)}.
     */
    public static String getSanitizedSensorName(Sensor sensor) throws SensorTestPlatformException {
        return SensorCtsHelper.sanitizeStringForFileName(sensor.getStringType());
    }

    private static List<String> getSortedKeys(Map<String, Object> flattenedStats) {
        List<String> keys = new ArrayList<>(flattenedStats.keySet());
        Collections.sort(keys);
        return keys;
    }

    private static String getValueString(Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof boolean[]) {
            return Arrays.toString((boolean[]) value);
        } else if (value instanceof byte[]) {
            return Arrays.toString((byte[]) value);
        } else if (value instanceof char[]) {
            return Arrays.toString((char[]) value);
        } else if (value instanceof double[]) {
            return Arrays.toString((double[]) value);
        } else if (value instanceof float[]) {
            return Arrays.toString((float[]) value);
        } else if (value instanceof int[]) {
            return Arrays.toString((int[]) value);
        } else if (value instanceof long[]) {
            return Arrays.toString((long[]) value);
        } else if (value instanceof short[]) {
            return Arrays.toString((short[]) value);
        } else if (value instanceof Object[]) {
            return Arrays.toString((Object[]) value);
        } else {
            return value.toString();
        }
    }
}
