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
 * Tests for {@link StandardDeviationVerification}.
 */
public class StandardDeviationVerificationTest extends TestCase {

    /**
     * Test {@link StandardDeviationVerification#verify(TestSensorEnvironment, SensorStats)}.
     */
    public void testVerify() {
        // Stddev should be {sqrt(2.5), sqrt(2.5), sqrt(2.5)}
        float[][] values = {
                {0, 1, 0},
                {1, 2, 2},
                {2, 3, 4},
                {3, 4, 6},
                {4, 5, 8},
        };
        float[] standardDeviations = {
                (float) Math.sqrt(2.5), (float) Math.sqrt(2.5), (float) Math.sqrt(10.0)
        };

        float[] threshold = {2, 2, 4};
        runVerification(threshold, values, true, standardDeviations);

        threshold = new float[]{1, 2, 4};
        runVerification(threshold, values, false, standardDeviations);

        threshold = new float[]{2, 1, 4};
        runVerification(threshold, values, false, standardDeviations);

        threshold = new float[]{2, 2, 3};
        runVerification(threshold, values, false, standardDeviations);
    }

    private void runVerification(float[] threshold, float[][] values, boolean pass,
            float[] standardDeviations) {
        SensorStats stats = new SensorStats();
        StandardDeviationVerification verification = getVerification(threshold, values);
        if (pass) {
            verification.verify(stats);
        } else {
            boolean failed = false;
            try {
                verification.verify(stats);
            } catch (AssertionError e) {
                // Expected;
                failed = true;
            }
            assertTrue("Expected an AssertionError", failed);
        }
        assertEquals(pass, stats.getValue(StandardDeviationVerification.PASSED_KEY));
        float[] actual = (float[]) stats.getValue(SensorStats.STANDARD_DEVIATION_KEY);
        for (int i = 0; i < standardDeviations.length; i++) {
            assertEquals(standardDeviations[i], actual[i], 0.1);
        }
    }

    private static StandardDeviationVerification getVerification(
            float[] threshold,
            float[] ... values) {
        Collection<TestSensorEvent> events = new ArrayList<>(values.length);
        for (float[] value : values) {
            events.add(new TestSensorEvent(null, 0, 0, value));
        }
        StandardDeviationVerification verification = new StandardDeviationVerification(threshold);
        verification.addSensorEvents(events);
        return verification;
    }
}
