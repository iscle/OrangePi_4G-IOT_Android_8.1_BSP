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
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests for {@link EventBasicVerification}.
 */
public class EventBasicVerificationTest extends AndroidTestCase {

    /**
     * Test {@link EventBasicVerification#verify(TestSensorEnvironment, SensorStats)}.
     */
    public void testVerify() {

        /* Sensor contents is not used in this verification, use Object as mock */
        SensorManager mgr = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        Sensor sensor1 = null;

        // accelerometer is the most likely sensor to exist
        Sensor sensor2 = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        SensorStats stats;

        EventBasicVerification verification;

        // case 1
        verification = getVerification(10, sensor1, sensor1, new float[20][3]);
        stats = new SensorStats();

        verification.verify(stats);
        assertEquals(true, stats.getValue(EventBasicVerification.PASSED_KEY));
        assertEquals(20, (long) stats.getValue(SensorStats.EVENT_COUNT_KEY));
        assertEquals(false, stats.getValue(SensorStats.WRONG_SENSOR_KEY));

        // case 2
        verification = getVerification(10, sensor1, sensor1, new float[5][3]);
        stats = new SensorStats();

        try {
            verification.verify(stats);
            throw new Error("Expect an AssertionError due to insufficient samples");
        } catch (AssertionError e) {
            //Expected
        }
        assertEquals(false, stats.getValue(EventBasicVerification.PASSED_KEY));
        assertEquals(5, (long) stats.getValue(SensorStats.EVENT_COUNT_KEY));
        assertEquals(false, stats.getValue(SensorStats.WRONG_SENSOR_KEY));

        // case 3
        if (sensor1 != sensor2) {
            // if we cannot even get a second sensor then do not bother this test.
            verification = getVerification(10, sensor1, sensor2, new float[15][3]);
            stats = new SensorStats();

            try {
                verification.verify(stats);
                throw new Error("Expect an AssertionError due to wrong sensor event");
            } catch (AssertionError e) {
                //Expected
            }
            assertEquals(false, stats.getValue(EventBasicVerification.PASSED_KEY));
            // zero here because wrong sensor is used.
            assertEquals(0, (long) stats.getValue(SensorStats.EVENT_COUNT_KEY));
            assertEquals(true, stats.getValue(SensorStats.WRONG_SENSOR_KEY));
        }
    }

    private static EventBasicVerification getVerification(
            int expectedMinNumEvent, Sensor sensor, Sensor eventSensor, float[] ... values) {

        Collection<TestSensorEvent> events = new ArrayList<>(values.length);
        for (float[] value : values) {
            events.add(new TestSensorEvent(eventSensor, 0, 0, value));
        }
        EventBasicVerification verification =
                new EventBasicVerification(expectedMinNumEvent, sensor);
        verification.addSensorEvents(events);
        return verification;
    }
}
