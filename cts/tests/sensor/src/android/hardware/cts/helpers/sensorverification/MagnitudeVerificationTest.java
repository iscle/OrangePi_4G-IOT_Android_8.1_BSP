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
 * Tests for {@link MagnitudeVerification}.
 */
public class MagnitudeVerificationTest extends TestCase {

    /**
     * Test {@link MagnitudeVerification#verify(TestSensorEnvironment, SensorStats)}.
     */
    public void testVerify() {
        float[][] values = {
                {0, 3, 4},
                {4, 0, 3},
                {3, 4, 0},
                {0, 0, 4},
                {6, 0, 0},
        };

        runStats(5.0f, 0.1f, values, true, 5.0f);
        runStats(4.5f, 0.6f, values, true, 5.0f);
        runStats(5.5f, 0.6f, values, true, 5.0f);
        runStats(4.5f, 0.1f, values, false, 5.0f);
        runStats(5.5f, 0.1f, values, false, 5.0f);
    }

    private void runStats(float expected, float threshold, float[][] values, boolean pass, float magnitude) {
        SensorStats stats = new SensorStats();
        MagnitudeVerification verification = getVerification(expected, threshold, values);
        if (pass) {
            verification.verify(stats);
        } else {
            try {
                verification.verify(stats);
                throw new Error("Expected an AssertionError");
            } catch (AssertionError e) {
                // Expected;
            }
        }
        assertEquals(pass, stats.getValue(MagnitudeVerification.PASSED_KEY));
        assertEquals(magnitude, (Float) stats.getValue(SensorStats.MAGNITUDE_KEY), 0.01);
    }

    private static MagnitudeVerification getVerification(float expected, float threshold,
            float[] ... values) {
        Collection<TestSensorEvent> events = new ArrayList<>(values.length);
        for (float[] value : values) {
            events.add(new TestSensorEvent(null, 0, 0, value));
        }
        MagnitudeVerification verification = new MagnitudeVerification(expected, threshold);
        verification.addSensorEvents(events);
        return verification;
    }
}
