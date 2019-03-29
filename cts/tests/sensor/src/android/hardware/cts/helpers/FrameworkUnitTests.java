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

import junit.framework.Test;
import junit.framework.TestSuite;

import android.hardware.cts.helpers.sensoroperations.SensorOperationTest;
import android.hardware.cts.helpers.sensorverification.EventGapVerificationTest;
import android.hardware.cts.helpers.sensorverification.EventOrderingVerificationTest;
import android.hardware.cts.helpers.sensorverification.FrequencyVerificationTest;
import android.hardware.cts.helpers.sensorverification.InitialValueVerificationTest;
import android.hardware.cts.helpers.sensorverification.JitterVerificationTest;
import android.hardware.cts.helpers.sensorverification.MagnitudeVerificationTest;
import android.hardware.cts.helpers.sensorverification.MeanVerificationTest;
import android.hardware.cts.helpers.sensorverification.StandardDeviationVerificationTest;
import android.hardware.cts.helpers.sensorverification.TimestampClockSourceVerificationTest;

/**
 * Unit test suite for the CTS sensor framework.
 */
public class FrameworkUnitTests extends TestSuite {

    public FrameworkUnitTests() {
        super();

        // helpers
        addTestSuite(SensorCtsHelperTest.class);
        addTestSuite(SensorStatsTest.class);

        // sensorverification
        addTestSuite(EventGapVerificationTest.class);
        addTestSuite(EventOrderingVerificationTest.class);
        addTestSuite(FrequencyVerificationTest.class);
        addTestSuite(InitialValueVerificationTest.class);
        addTestSuite(JitterVerificationTest.class);
        addTestSuite(MagnitudeVerificationTest.class);
        addTestSuite(MeanVerificationTest.class);
        addTestSuite(StandardDeviationVerificationTest.class);
        addTestSuite(TimestampClockSourceVerificationTest.class);


        // sensorOperations
        addTestSuite(SensorOperationTest.class);
    }

    public static Test suite() {
        return new FrameworkUnitTests();
    }
}
