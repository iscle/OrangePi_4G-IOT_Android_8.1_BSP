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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.TestCase;

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests for {@link MeanVerification}.
 */
public class MeanVerificationTest extends TestCase {
    private static final float[] MEANS = {2.0f, 3.0f, 6.0f};

    /**
     * Test {@link MeanVerification#verify(TestSensorEnvironment, SensorStats)}.
     */
    public void testVerify() {
        float[][] values = {
                {0, 1, 0},
                {1, 2, 1},
                {2, 3, 4},
                {3, 4, 9},
                {4, 5, 16},
        };

        float[] expected = {2.0f, 3.0f, 6.0f};
        float[] upperThresholds = {0.3f, 0.3f, 0.3f};
        float[] lowerThresholds = {0.1f, 0.1f, 0.1f};
        SensorStats stats = new SensorStats();
        MeanVerification verification =
            getVerification(expected, upperThresholds, lowerThresholds, values);
        verification.verify(stats);
        verifyStats(stats, true, MEANS);

        // Test the lower threshold
        expected = new float[]{2.4f, 3.3f, 6.4f};
        lowerThresholds = new float[]{0.6f, 0.6f, 0.6f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        verification.verify(stats);
        verifyStats(stats, true, MEANS);

        lowerThresholds = new float[]{0.1f, 0.6f, 0.6f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, MEANS);

        lowerThresholds = new float[]{0.6f, 0.1f, 0.6f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, MEANS);

        lowerThresholds = new float[]{0.6f, 0.6f, 0.1f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, MEANS);

        // Test the upper threshold
        expected = new float[]{1.5f, 2.8f, 5.7f};
        upperThresholds = new float[]{0.6f, 0.6f, 0.6f};
        lowerThresholds = new float[]{0.1f, 0.1f, 0.1f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        verification.verify(stats);
        verifyStats(stats, true, MEANS);

        upperThresholds = new float[]{0.1f, 0.6f, 0.6f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, MEANS);

        upperThresholds = new float[]{0.6f, 0.1f, 0.6f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, MEANS);

        upperThresholds = new float[]{0.6f, 0.6f, 0.1f};
        stats = new SensorStats();
        verification = getVerification(expected, upperThresholds, lowerThresholds, values);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, MEANS);
    }

    private static MeanVerification getVerification(float[] expected, float[] upperThresholds,
            float[] lowerThresholds, float[] ... values) {
        Collection<TestSensorEvent> events = new ArrayList<>(values.length);
        for (float[] value : values) {
            events.add(new TestSensorEvent(null, 0, 0, value));
        }
        MeanVerification verification =
            new MeanVerification(expected, upperThresholds, lowerThresholds);
        verification.addSensorEvents(events);
        return verification;
    }

    private void verifyStats(SensorStats stats, boolean passed, float[] means) {
        assertEquals(passed, stats.getValue(MeanVerification.PASSED_KEY));
        float[] actual = (float[]) stats.getValue(SensorStats.MEAN_KEY);
        assertEquals(means.length, actual.length);
        for (int i = 0; i < means.length; i++) {
            assertEquals(means[i], actual[i], 0.1);
        }
    }
}
