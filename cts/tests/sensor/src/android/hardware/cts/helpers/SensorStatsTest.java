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

import junit.framework.TestCase;

import java.util.Map;

/**
 * Unit tests for the {@link SensorStats} class.
 */
public class SensorStatsTest extends TestCase {

    /**
     * Test that {@link SensorStats#flatten()} works correctly.
     */
    public void testFlatten() {
        SensorStats stats = new SensorStats();
        stats.addValue("value0", 0);
        stats.addValue("value1", 1);

        SensorStats subStats = new SensorStats();
        subStats.addValue("value2", 2);
        subStats.addValue("value3", 3);

        SensorStats subSubStats = new SensorStats();
        subSubStats.addValue("value4", 4);
        subSubStats.addValue("value5", 5);

        subStats.addSensorStats("stats1", subSubStats);
        stats.addSensorStats("stats0", subStats);

        // Add empty stats, expect no value in flattened map
        stats.addSensorStats("stats2", new SensorStats());

        // Add null values, expect no value in flattened map
        stats.addSensorStats("stats3", null);
        stats.addValue("value6", null);

        Map<String, Object> flattened = stats.flatten();

        assertEquals(6, flattened.size());
        assertEquals(0, (int) (Integer) flattened.get("value0"));
        assertEquals(1, (int) (Integer) flattened.get("value1"));
        assertEquals(2, (int) (Integer) flattened.get("stats0__value2"));
        assertEquals(3, (int) (Integer) flattened.get("stats0__value3"));
        assertEquals(4, (int) (Integer) flattened.get("stats0__stats1__value4"));
        assertEquals(5, (int) (Integer) flattened.get("stats0__stats1__value5"));
    }
}
