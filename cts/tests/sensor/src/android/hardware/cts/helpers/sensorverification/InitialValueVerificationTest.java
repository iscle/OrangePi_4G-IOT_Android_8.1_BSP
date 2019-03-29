/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.TestCase;

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Tests for {@link InitialValueVerification}.
 */
public class InitialValueVerificationTest extends TestCase {
    private static final long INITIAL_WINDOW_LENGTH = 2_000_000_000L; // 2s
    private static final long TOTAL_WINDOW_LENGTH = 5_000_000_000L; // 5s
    private static final long SENSOR_PERIOD = 500_000_000L; // 0.5s
    private static final float MAX_ABSOLUTE_DELTA = 3f;
    private static final Random random = new Random(123L);
    private static final float NOISE_STD = 0.01f;

    /**
     * Test {@link InitialValueVerification#verify(SensorStats)}.
     */
    public void testVerify() {
        float[] initialValues = new float[] {80.4f, 12.3f, -67f};
        verifyStatsWithTwoWindows(initialValues, initialValues, true);

        // Only modify the first element in the array but close enough
        float[] laterValues = new float[] {78.1f, 12.3f, -67f};
        verifyStatsWithTwoWindows(initialValues, laterValues, true);
        // Only modify the first element in the array but by more than the MAX_ABSOLUTE_DELTA
        laterValues = new float[] {70.1f, 12.3f, -67f};
        verifyStatsWithTwoWindows(initialValues, laterValues, false);

        // Only modify the second element in the array but close enough
        laterValues = new float[] {80.4f, 11.3f, -67f};
        verifyStatsWithTwoWindows(initialValues, laterValues, true);
        // Only modify the second element in the array but by more than the MAX_ABSOLUTE_DELTA
        laterValues = new float[] {80.4f, 7.3f, -67f};
        verifyStatsWithTwoWindows(initialValues, laterValues, false);

        // Only modify the third element in the array but close enough
        laterValues = new float[] {80.4f, 12.3f, -65f};
        verifyStatsWithTwoWindows(initialValues, laterValues, true);
        // Only modify the third element in the array but by more than the MAX_ABSOLUTE_DELTA
        laterValues = new float[] {80.4f, 12.3f, 45f};
        verifyStatsWithTwoWindows(initialValues, laterValues, false);
    }

    private static InitialValueVerification getVerification(Collection<TestSensorEvent> events,
            float maxAbsoluteDelta, long initialWindowLength) {
        InitialValueVerification verification =
                new InitialValueVerification(maxAbsoluteDelta, initialWindowLength);
        verification.addSensorEvents(events);
        return verification;
    }

    private static void verifyStatsWithTwoWindows(float[] initialValues, float[] laterValues,
            boolean pass) {
        List<TestSensorEvent> events = new ArrayList<>();
        // Initial window
        for (long timestamp = 0L; timestamp <= INITIAL_WINDOW_LENGTH; timestamp += SENSOR_PERIOD) {
            float[] initialValuesWithNoise = addNoise(initialValues);
            events.add(new TestSensorEvent(null /* sensor */, timestamp, 0 /* accuracy */,
                    initialValuesWithNoise));
        }
        // Later window
        for (long timestamp = INITIAL_WINDOW_LENGTH
                + SENSOR_PERIOD; timestamp <= TOTAL_WINDOW_LENGTH; timestamp += SENSOR_PERIOD) {
            float[] laterValuesWithNoise = addNoise(laterValues);
            events.add(new TestSensorEvent(null /* sensor */, timestamp, 0 /* accuracy */,
                    laterValuesWithNoise));
        }
        SensorStats stats = new SensorStats();
        InitialValueVerification verification =
                getVerification(events, MAX_ABSOLUTE_DELTA, INITIAL_WINDOW_LENGTH);

        try {
            verification.verify(stats);
            assertTrue(pass);
        } catch (AssertionError e) {
            assertFalse(pass);
        }
        verifyStats(stats, pass, initialValues, laterValues);
    }

    private static float[] addNoise(float[] values) {
        float[] valuesWithNoise = new float[values.length];
        for(int i = 0; i < values.length; i++) {
            valuesWithNoise[i] = values[i] + random.nextFloat() * NOISE_STD;
        }
        return valuesWithNoise;
    }

    private static void verifyStats(SensorStats stats, boolean passed, float[] initialMeans,
            float[] laterMeans) {
        assertEquals(passed, stats.getValue(InitialValueVerification.PASSED_KEY));
        float[] actualInitialMeans = (float[]) stats.getValue(SensorStats.INITIAL_MEAN_KEY);
        float[] actualLaterMeans = (float[]) stats.getValue(SensorStats.LATER_MEAN_KEY);
        assertEquals(initialMeans.length, actualInitialMeans.length);
        assertEquals(laterMeans.length, actualLaterMeans.length);
        for (int i = 0; i < initialMeans.length; i++) {
            assertEquals(initialMeans[i], actualInitialMeans[i], 0.1);
            assertEquals(laterMeans[i], actualLaterMeans[i], 0.1);
        }
    }
}
