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

import android.hardware.Sensor;
import android.hardware.cts.SensorTestCase;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests for {@link EventOrderingVerification}.
 */
public class FrequencyVerificationTest extends SensorTestCase {

    /**
     * Test that the verifications passes/fails based on threshold given.
     */
    public void testVerifification() {
        long[] timestamps = {0, 1000000, 2000000, 3000000, 4000000};  // 1000Hz

        SensorStats stats = new SensorStats();
        ISensorVerification verification = getVerification(999.0, 1001.0, timestamps);
        verification.verify(getEnvironment(1000), stats);
        verifyStats(stats, true, 1000.0);

        stats = new SensorStats();
        verification = getVerification(850.0, 1050.0, timestamps);
        verification.verify(getEnvironment(950), stats);
        verifyStats(stats, true, 1000.0);

        stats = new SensorStats();
        verification = getVerification(950.0, 1150.0, timestamps);
        verification.verify(getEnvironment(1050), stats);
        verifyStats(stats, true, 1000.0);

        stats = new SensorStats();
        verification = getVerification(850.0, 975.0, timestamps);
        try {
            verification.verify(getEnvironment(950), stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, 1000.0);

        stats = new SensorStats();
        verification = getVerification(1025.0, 1150.0, timestamps);
        try {
            verification.verify(getEnvironment(1050), stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, 1000.0);
    }

    private TestSensorEnvironment getEnvironment(int rateUs) {
        return new TestSensorEnvironment(getContext(), Sensor.TYPE_ALL, rateUs);
    }

    private static FrequencyVerification getVerification(
            double lowerThreshold,
            double upperThreshold,
            long ... timestamps) {
        Collection<TestSensorEvent> events = new ArrayList<>(timestamps.length);
        for (long timestamp : timestamps) {
            events.add(new TestSensorEvent(null, timestamp, 0, null));
        }
        FrequencyVerification verification =
                new FrequencyVerification(lowerThreshold, upperThreshold);
        verification.addSensorEvents(events);
        return verification;
    }

    private void verifyStats(SensorStats stats, boolean passed, double frequency) {
        assertEquals(passed, stats.getValue(FrequencyVerification.PASSED_KEY));
        assertEquals(frequency, stats.getValue(SensorStats.FREQUENCY_KEY));
    }
}
