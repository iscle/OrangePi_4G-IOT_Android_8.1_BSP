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

package com.android.compatibility.common.util;

import com.android.compatibility.common.util.Stat;
import junit.framework.TestCase;

/**
 * Unit tests for the {@link Stat} class.
 */
public class StatTest extends TestCase {

    /**
     * Test {@link Stat#get95PercentileValue(double[])}.
     */
    public void testGet95PercentileValue() {
        double[] values = new double[100];
        for (int i = 0; i < 100; i++) {
            values[i] = i;
        }
        assertEquals(95, (int) Stat.get95PercentileValue(values));

        values = new double[1000];
        for (int i = 0; i < 1000; i++) {
            values[i] = i;
        }
        assertEquals(950, (int) Stat.get95PercentileValue(values));

        values = new double[100];
        for (int i = 0; i < 100; i++) {
            values[i] = i * i;
        }
        assertEquals(95 * 95, (int) Stat.get95PercentileValue(values));
    }

    /**
     * Test {@link Stat#getAverage(double[])}.
     */
    public void testGetAverage() {
        double[] values = new double[]{0, 1, 2, 3, 4};
        double average = Stat.getAverage(values);
        assertEquals(2.0, average, 0.00001);

        values = new double[]{1, 2, 3, 4, 5};
        average = Stat.getAverage(values);
        assertEquals(3.0, average, 0.00001);

        values = new double[]{0, 1, 4, 9, 16};
        average = Stat.getAverage(values);
        assertEquals(6.0, average, 0.00001);
    }

    /**
     * Test standard deviation.
     */
    public void testGetStandardDeviation() {
        double[] values = new double[]{0, 1, 2, 3, 4};
        double stddev = Stat.getStat(values).mStddev;
        assertEquals(Math.sqrt(2.5), stddev, 0.00001);

        values = new double[]{1, 2, 3, 4, 5};
        stddev = Stat.getStat(values).mStddev;
        assertEquals(Math.sqrt(2.5), stddev, 0.00001);

        values = new double[]{0, 2, 4, 6, 8};
        stddev = Stat.getStat(values).mStddev;
        assertEquals(Math.sqrt(10.0), stddev, 0.00001);
    }


}
