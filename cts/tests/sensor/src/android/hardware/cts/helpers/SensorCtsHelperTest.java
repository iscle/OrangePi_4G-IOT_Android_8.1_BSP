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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for the {@link SensorCtsHelper} class.
 */
public class SensorCtsHelperTest extends TestCase {

    /**
     * Test {@link SensorCtsHelper#getFrequency(Number, TimeUnit)}.
     */
    public void testGetFrequency() {
        assertEquals(1.0, SensorCtsHelper.getFrequency(1, TimeUnit.SECONDS), 0.001);
        assertEquals(10.0, SensorCtsHelper.getFrequency(0.1, TimeUnit.SECONDS), 0.001);
        assertEquals(10.0, SensorCtsHelper.getFrequency(100, TimeUnit.MILLISECONDS), 0.001);
        assertEquals(1000.0, SensorCtsHelper.getFrequency(1, TimeUnit.MILLISECONDS), 0.001);
        assertEquals(10000.0, SensorCtsHelper.getFrequency(0.1, TimeUnit.MILLISECONDS), 0.001);
        assertEquals(10000.0, SensorCtsHelper.getFrequency(100, TimeUnit.MICROSECONDS), 0.001);
        assertEquals(1000000.0, SensorCtsHelper.getFrequency(1, TimeUnit.MICROSECONDS), 0.001);
        assertEquals(10000000.0, SensorCtsHelper.getFrequency(0.1, TimeUnit.MICROSECONDS), 0.001);
        assertEquals(10000000.0, SensorCtsHelper.getFrequency(100, TimeUnit.NANOSECONDS), 0.001);
        assertEquals(1000000000.0, SensorCtsHelper.getFrequency(1, TimeUnit.NANOSECONDS), 0.001);
    }

    /**
     * Test {@link SensorCtsHelper#getPeriod(Number, TimeUnit)}.
     */
    public void testGetPeriod() {
        assertEquals(1.0, SensorCtsHelper.getPeriod(1, TimeUnit.SECONDS), 0.001);
        assertEquals(0.1, SensorCtsHelper.getPeriod(10, TimeUnit.SECONDS), 0.001);
        assertEquals(100, SensorCtsHelper.getPeriod(10, TimeUnit.MILLISECONDS), 0.001);
        assertEquals(1, SensorCtsHelper.getPeriod(1000, TimeUnit.MILLISECONDS), 0.001);
        assertEquals(0.1, SensorCtsHelper.getPeriod(10000, TimeUnit.MILLISECONDS), 0.001);
        assertEquals(100, SensorCtsHelper.getPeriod(10000, TimeUnit.MICROSECONDS), 0.001);
        assertEquals(1, SensorCtsHelper.getPeriod(1000000, TimeUnit.MICROSECONDS), 0.001);
        assertEquals(0.1, SensorCtsHelper.getPeriod(10000000, TimeUnit.MICROSECONDS), 0.001);
        assertEquals(100, SensorCtsHelper.getPeriod(10000000, TimeUnit.NANOSECONDS), 0.001);
        assertEquals(1, SensorCtsHelper.getPeriod(1000000000, TimeUnit.NANOSECONDS), 0.001);
    }
}
