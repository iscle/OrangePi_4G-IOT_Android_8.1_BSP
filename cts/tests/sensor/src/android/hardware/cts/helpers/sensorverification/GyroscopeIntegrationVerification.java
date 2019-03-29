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
 * limitations under the License
 */

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.concurrent.TimeUnit;

/**
 * A {@link ISensorVerification} that verifies the measurements of the sensors:
 * - {@link Sensor#TYPE_GYROSCOPE}
 * - {@link Sensor#TYPE_GYROSCOPE_UNCALIBRATED}
 *
 * It uses a routine to integrate gyroscope's data, and that they correspond to expected angular
 * positions.
 */
public class GyroscopeIntegrationVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "gyroscope_integration_passed";

    private static final long ONE_SECOND_AS_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final double[] mIntermediatesDeg;
    private final int[] mExpectationsDeg;
    private final float[] mThresholdsDeg;

    private volatile Long mLastTimestampNs;

    public GyroscopeIntegrationVerification(int[] expectationsDeg, float[] thresholdsDeg) {
        mIntermediatesDeg = new double[expectationsDeg.length];
        mExpectationsDeg = expectationsDeg;
        mThresholdsDeg = thresholdsDeg;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        int sensorType = environment.getSensor().getType();
        if (sensorType != Sensor.TYPE_GYROSCOPE
                && sensorType != Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            // the verification does not apply, so no-op
            stats.addValue(PASSED_KEY, true);
        }

        boolean success = true;
        int gyroscopeAxes = environment.getSensorAxesCount();
        StringBuilder builder = new StringBuilder("Gyroscope Integration | failures:");
        for (int i = 0; i < gyroscopeAxes; ++i) {
            double integrationDeg = Math.toDegrees(mIntermediatesDeg[i]);
            double integrationDelta = Math.abs(integrationDeg - mExpectationsDeg[i]);
            if (integrationDelta > mThresholdsDeg[i]) {
                success = false;
                String message = String.format(
                        "\naxis=%s, expected=%sdeg, observed=%sdeg, delta=%sdeg, threshold=%sdeg",
                        i,
                        mExpectationsDeg[i],
                        integrationDeg,
                        integrationDelta,
                        mThresholdsDeg[i]);
                builder.append(message);
            }
        }

        stats.addValue(PASSED_KEY, success);
        Assert.assertTrue(builder.toString(), success);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GyroscopeIntegrationVerification clone() {
        return new GyroscopeIntegrationVerification(mExpectationsDeg, mThresholdsDeg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        float[] eventValues = event.values.clone();
        long eventTimestampNs = event.timestamp;
        if (mLastTimestampNs == null) {
            mLastTimestampNs = eventTimestampNs;
            return;
        }

        int intermediatesLength = mIntermediatesDeg.length;
        long timestampDeltaNs = eventTimestampNs - mLastTimestampNs;
        for (int i = 0; i < intermediatesLength; ++i) {
            mIntermediatesDeg[i] += eventValues[i] * timestampDeltaNs / ONE_SECOND_AS_NANOS;
        }
        mLastTimestampNs = eventTimestampNs;
    }
}
