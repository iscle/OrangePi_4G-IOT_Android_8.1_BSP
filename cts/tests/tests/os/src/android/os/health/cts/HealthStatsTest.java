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

package android.os.health.cts;

import android.os.Parcel;
import android.os.health.HealthKeys;
import android.os.health.HealthStats;
import android.os.health.HealthStatsParceler;
import android.os.health.HealthStatsWriter;
import android.os.health.TimerStat;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Map;

/**
 * Provides test cases for android.os.health.HealthStats and HealthStatsWriter.
 */
public class HealthStatsTest extends TestCase {
    @HealthKeys.Constant(type=HealthKeys.TYPE_TIMER)
    public static final int TIMER_0 = 10;

    @HealthKeys.Constant(type=HealthKeys.TYPE_TIMER)
    public static final int TIMER_1 = 11;

    @HealthKeys.Constant(type=HealthKeys.TYPE_MEASUREMENT)
    public static final int MEASUREMENT_0 = 20;

    @HealthKeys.Constant(type=HealthKeys.TYPE_MEASUREMENT)
    public static final int MEASUREMENT_1 = 21;

    @HealthKeys.Constant(type=HealthKeys.TYPE_MEASUREMENT)
    public static final int MEASUREMENT_2 = 21;

    @HealthKeys.Constant(type=HealthKeys.TYPE_STATS)
    public static final int STATS_0 = 30;

    @HealthKeys.Constant(type=HealthKeys.TYPE_TIMERS)
    public static final int TIMERS_0 = 30;

    @HealthKeys.Constant(type=HealthKeys.TYPE_MEASUREMENTS)
    public static final int MEASUREMENTS_0 = 40;

    @HealthKeys.Constant(type=HealthKeys.TYPE_MEASUREMENTS)
    public static final int MEASUREMENTS_1 = 41;


    public static final HealthKeys.Constants CONSTANTS
            = new HealthKeys.Constants(HealthStatsTest.class);

    /**
     * Check all the fields.
     */
    private void checkHealthStats(HealthStats readback) {
        // Header fields
        Assert.assertEquals("HealthStatsTest", readback.getDataType());


        // TimerStat fields
        Assert.assertEquals(2, readback.getTimerKeyCount());

        Assert.assertEquals(TIMER_0, readback.getTimerKeyAt(0));
        Assert.assertTrue(readback.hasTimer(TIMER_0));
        Assert.assertEquals(1, readback.getTimerCount(TIMER_0));
        Assert.assertEquals(100, readback.getTimerTime(TIMER_0));

        Assert.assertEquals(TIMER_1, readback.getTimerKeyAt(1));
        Assert.assertTrue(readback.hasTimer(TIMER_1));
        Assert.assertEquals(Integer.MAX_VALUE, readback.getTimerCount(TIMER_1));
        Assert.assertEquals(Long.MAX_VALUE, readback.getTimerTime(TIMER_1));

        Assert.assertEquals(Integer.MAX_VALUE, readback.getTimer(TIMER_1).getCount());
        Assert.assertEquals(Long.MAX_VALUE, readback.getTimer(TIMER_1).getTime());


        // Measurement fields
        Assert.assertEquals(2, readback.getMeasurementKeyCount());

        Assert.assertEquals(MEASUREMENT_0, readback.getMeasurementKeyAt(0));
        Assert.assertTrue(readback.hasMeasurement(MEASUREMENT_0));
        Assert.assertEquals(300, readback.getMeasurement(MEASUREMENT_0));

        Assert.assertEquals(MEASUREMENT_1, readback.getMeasurementKeyAt(1));
        Assert.assertTrue(readback.hasMeasurement(MEASUREMENT_1));
        Assert.assertEquals(Long.MAX_VALUE, readback.getMeasurement(MEASUREMENT_1));
        

        // Stats fields
        Assert.assertEquals(1, readback.getStatsKeyCount());

        Assert.assertEquals(STATS_0, readback.getStatsKeyAt(0));
        Assert.assertTrue(readback.hasStats(STATS_0));

        final Map<String,HealthStats> stats = readback.getStats(STATS_0);
        Assert.assertEquals(1, stats.size());
        final HealthStats child = stats.get("a");
        Assert.assertEquals(0, child.getTimerKeyCount());
        Assert.assertEquals(1, child.getMeasurementKeyCount());
        Assert.assertEquals(Long.MIN_VALUE, child.getMeasurement(MEASUREMENT_2));
        Assert.assertEquals(0, child.getStatsKeyCount());
        Assert.assertEquals(0, child.getTimersKeyCount());


        // Timers fields
        Assert.assertEquals(1, readback.getTimersKeyCount());

        Assert.assertEquals(TIMERS_0, readback.getTimersKeyAt(0));
        Assert.assertTrue(readback.hasTimers(TIMERS_0));
        final Map<String,TimerStat> timers = readback.getTimers(TIMERS_0);
        Assert.assertEquals(1, timers.size());
        Assert.assertEquals(200, timers.get("b").getCount());
        Assert.assertEquals(400, timers.get("b").getTime());


        // Measurements fields
        Map<String,Long> measurements;
        Assert.assertEquals(2, readback.getMeasurementsKeyCount());

        Assert.assertEquals(MEASUREMENTS_0, readback.getMeasurementsKeyAt(0));
        Assert.assertTrue(readback.hasMeasurements(MEASUREMENTS_0));
        measurements = readback.getMeasurements(MEASUREMENTS_0);
        Assert.assertEquals(1, measurements.size());
        Assert.assertEquals(800L, measurements.get("Z").longValue());

        Assert.assertEquals(MEASUREMENTS_1, readback.getMeasurementsKeyAt(1));
        measurements = readback.getMeasurements(MEASUREMENTS_1);
        Assert.assertTrue(readback.hasMeasurements(MEASUREMENTS_1));
        Assert.assertEquals(2, measurements.size());
        Assert.assertEquals(900L, measurements.get("Y").longValue());
        Assert.assertEquals(901L, measurements.get("X").longValue());
    }

    /**
     * Tests parceling empty HealthStats.
     */
    @SmallTest
    public void testParcelEmpty() throws Exception {
        final HealthStatsWriter writer = new HealthStatsWriter(CONSTANTS);

        Parcel parcel = Parcel.obtain();
        writer.flattenToParcel(parcel);

        parcel.setDataPosition(0);

        HealthStats readback = new HealthStats(parcel);

        // Check that there is no more data in the parcel
        Assert.assertEquals(0, parcel.dataAvail());
        parcel.recycle();
        
        Assert.assertEquals(0, readback.getTimerKeyCount());
        Assert.assertEquals(0, readback.getMeasurementKeyCount());
        Assert.assertEquals(0, readback.getStatsKeyCount());
        Assert.assertEquals(0, readback.getTimersKeyCount());
        Assert.assertEquals(0, readback.getMeasurementsKeyCount());
    }


    /**
     * Tests parceling HealthStats.
     */
    @SmallTest
    public void testParcelling() throws Exception {
        final HealthStatsWriter writer = new HealthStatsWriter(CONSTANTS);

        // TimerStat
        writer.addTimer(TIMER_0, 1, 100);
        writer.addTimer(TIMER_1, Integer.MAX_VALUE, Long.MAX_VALUE);

        // Measurement
        writer.addMeasurement(MEASUREMENT_0, 300);
        writer.addMeasurement(MEASUREMENT_1, Long.MAX_VALUE);

        // Stats
        HealthStatsWriter writer2 = new HealthStatsWriter(CONSTANTS);
        writer2.addMeasurement(MEASUREMENT_2, Long.MIN_VALUE);
        writer.addStats(STATS_0, "a", writer2);

        // Timers
        writer.addTimers(TIMERS_0, "b", new TimerStat(200, 400));

        // Measurements
        writer.addMeasurements(MEASUREMENTS_0, "Z", 800);
        writer.addMeasurements(MEASUREMENTS_1, "Y", 900);
        writer.addMeasurements(MEASUREMENTS_1, "X", 901);


        // Parcel and unparcel it via HealthStatsWriter.writeToParcel.
        Parcel parcel = Parcel.obtain();
        writer.flattenToParcel(parcel);
        parcel.setDataPosition(0);
        HealthStats readback = new HealthStats(parcel);
        
        // Check that there is no more data in the parcel
        Assert.assertEquals(0, parcel.dataAvail());
        parcel.recycle();
        
        checkHealthStats(readback);
    }

    /**
     * Tests the HealthStatsParceler.
     */
    @SmallTest
    public void testParceler() throws Exception {
        final HealthStatsWriter writer = new HealthStatsWriter(CONSTANTS);
        writer.addMeasurement(MEASUREMENT_0, 300);

        final HealthStatsParceler parceler = new HealthStatsParceler(writer);

        final Parcel parcel = Parcel.obtain();
        parceler.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        final HealthStatsParceler parceler2 = new HealthStatsParceler(parcel);

        // Check that there is no more data in the parcel
        Assert.assertEquals(0, parcel.dataAvail());
        parcel.recycle();

        final HealthStats readback = parceler2.getHealthStats();
        
        Assert.assertEquals(300, readback.getMeasurement(MEASUREMENT_0));

        // Should fail
        try {
            final Parcel parcel2 = Parcel.obtain();
            parceler2.writeToParcel(parcel2, 0);
            parcel2.recycle();
            throw new Exception("Expected IndexOutOfBoundsException");
        } catch (RuntimeException ex) {
            // expected
        }
    }
}

