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

package android.hardware.cts.helpers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.sensoroperations.SensorOperation;

import java.util.concurrent.TimeUnit;

/* TODO: Refactor constructors into a builder */

/**
 * A class that encapsulates base environment information for the {@link SensorOperation}.
 * The environment is self contained and carries its state around all the sensor test framework.
 */
public class TestSensorEnvironment {

    /**
     * It represents the fraction of the expected sampling frequency, at which the sensor can
     * actually produce events.
     */
    private static final float MAXIMUM_EXPECTED_SAMPLING_FREQUENCY_MULTIPLIER = 0.9f;

    private final Context mContext;
    private final Sensor mSensor;
    private final boolean mSensorMightHaveMoreListeners;
    private final int mSamplingPeriodUs;
    private final int mMaxReportLatencyUs;
    private final boolean mIsDeviceSuspendTest;
    private final boolean mIsIntegrationTest;

    /**
     * Constructs an environment for sensor testing.
     *
     * @param context The context for the test
     * @param sensorType The type of the sensor under test
     * @param samplingPeriodUs The requested collection period for the sensor under test
     *
     * @deprecated Use variants with {@link Sensor} objects.
     */
    @Deprecated
    public TestSensorEnvironment(Context context, int sensorType, int samplingPeriodUs) {
        this(context, sensorType, false /* sensorMightHaveMoreListeners */, samplingPeriodUs);
    }

    /**
     * Constructs an environment for sensor testing.
     *
     * @param context The context for the test
     * @param sensorType The type of the sensor under test
     * @param samplingPeriodUs The requested collection period for the sensor under test
     * @param maxReportLatencyUs The requested collection report latency for the sensor under test
     *
     * @deprecated Use variants with {@link Sensor} objects.
     */
    @Deprecated
    public TestSensorEnvironment(
            Context context,
            int sensorType,
            int samplingPeriodUs,
            int maxReportLatencyUs) {
        this(context,
                sensorType,
                false /* sensorMightHaveMoreListeners */,
                samplingPeriodUs,
                maxReportLatencyUs);
    }

    /**
     * Constructs an environment for sensor testing.
     *
     * @param context The context for the test
     * @param sensorType The type of the sensor under test
     * @param sensorMightHaveMoreListeners Whether the sensor under test is acting under load
     * @param samplingPeriodUs The requested collection period for the sensor under test
     *
     * @deprecated Use variants with {@link Sensor} objects.
     */
    @Deprecated
    public TestSensorEnvironment(
            Context context,
            int sensorType,
            boolean sensorMightHaveMoreListeners,
            int samplingPeriodUs) {
        this(context,
                sensorType,
                sensorMightHaveMoreListeners,
                samplingPeriodUs,
                0 /* maxReportLatencyUs */);
    }

    /**
     * Constructs an environment for sensor testing.
     *
     * @param context The context for the test
     * @param sensorType The type of the sensor under test
     * @param sensorMightHaveMoreListeners Whether the sensor under test is acting under load
     * @param samplingPeriodUs The requested collection period for the sensor under test
     * @param maxReportLatencyUs The requested collection report latency for the sensor under test
     *
     * @deprecated Use variants with {@link Sensor} objects.
     */
    @Deprecated
    public TestSensorEnvironment(
            Context context,
            int sensorType,
            boolean sensorMightHaveMoreListeners,
            int samplingPeriodUs,
            int maxReportLatencyUs) {
        this(context,
                getSensor(context, sensorType),
                sensorMightHaveMoreListeners,
                samplingPeriodUs,
                maxReportLatencyUs);
    }

    /**
     * Constructs an environment for sensor testing.
     *
     * @param context The context for the test
     * @param sensorType The type of the sensor under test
     * @param sensorMightHaveMoreListeners Whether the sensor under test is acting under load
     * @param isIntegrationTest Whether this is an integration test (more than one sensor actived)
     * @param samplingPeriodUs The requested collection period for the sensor under test
     * @param maxReportLatencyUs The requested collection report latency for the sensor under test
     *
     * @deprecated Use variants with {@link Sensor} objects.
     */
    @Deprecated
    public TestSensorEnvironment(
            Context context,
            int sensorType,
            boolean sensorMightHaveMoreListeners,
            boolean isIntegrationTest,
            int samplingPeriodUs,
            int maxReportLatencyUs) {
        this(context,
                getSensor(context, sensorType),
                sensorMightHaveMoreListeners,
                isIntegrationTest,
                samplingPeriodUs,
                maxReportLatencyUs);
    }

    /**
     * Constructs an environment for sensor testing.
     *
     * @param context The context for the test
     * @param sensor The sensor under test
     * @param samplingPeriodUs The requested collection period for the sensor under test
     * @param maxReportLatencyUs The requested collection report latency for the sensor under test
     */
    public TestSensorEnvironment(
            Context context,
            Sensor sensor,
            int samplingPeriodUs,
            int maxReportLatencyUs) {
        this(context,
                sensor,
                false /* sensorMightHaveMoreListeners */,
                samplingPeriodUs,
                maxReportLatencyUs);
    }

    /**
     * Constructs an environment for sensor testing.
     *
     * @param context The context for the test
     * @param sensor The sensor under test
     * @param sensorMightHaveMoreListeners Whether the sensor under test is acting under load (this
     *                                     usually implies that there are several listeners
     *                                     requesting different sampling periods)
     * @param samplingPeriodUs The requested collection period for the sensor under test
     * @param maxReportLatencyUs The requested collection report latency for the sensor under test
     */
    public TestSensorEnvironment(
            Context context,
            Sensor sensor,
            boolean sensorMightHaveMoreListeners,
            int samplingPeriodUs,
            int maxReportLatencyUs) {
        this(context,
                sensor,
                sensorMightHaveMoreListeners,
                samplingPeriodUs,
                maxReportLatencyUs,
                false /* isDeviceSuspendTest */);
    }

    public TestSensorEnvironment(
            Context context,
            Sensor sensor,
            boolean sensorMightHaveMoreListeners,
            boolean isIntegrationTest,
            int samplingPeriodUs,
            int maxReportLatencyUs) {
        this(context,
                sensor,
                sensorMightHaveMoreListeners,
                samplingPeriodUs,
                maxReportLatencyUs,
                false /* isDeviceSuspendTest */,
                isIntegrationTest);
    }

    public TestSensorEnvironment(
            Context context,
            Sensor sensor,
            boolean sensorMightHaveMoreListeners,
            int samplingPeriodUs,
            int maxReportLatencyUs,
            boolean isDeviceSuspendTest) {
        this(context, sensor, sensorMightHaveMoreListeners,
                samplingPeriodUs, maxReportLatencyUs,
                false /* isDeviceSuspendTest */,
                false /* isIntegrationTest */);
    }

    public TestSensorEnvironment(
            Context context,
            Sensor sensor,
            boolean sensorMightHaveMoreListeners,
            int samplingPeriodUs,
            int maxReportLatencyUs,
            boolean isDeviceSuspendTest,
            boolean isIntegrationTest) {
        mContext = context;
        mSensor = sensor;
        mSensorMightHaveMoreListeners = sensorMightHaveMoreListeners;
        mSamplingPeriodUs = samplingPeriodUs;
        mMaxReportLatencyUs = maxReportLatencyUs;
        mIsDeviceSuspendTest = isDeviceSuspendTest;
        mIsIntegrationTest = isIntegrationTest;
    }

    /**
     * @return The context instance associated with the test.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * @return The sensor under test.
     */
    public Sensor getSensor() {
        return mSensor;
    }

    /**
     * @return The requested collection rate in microseconds.
     */
    public int getRequestedSamplingPeriodUs() {
        return mSamplingPeriodUs;
    }

    /**
     * @return The frequency equivalent to {@link #getRequestedSamplingPeriodUs()}.
     */
    public double getFrequencyHz() {
        return SensorCtsHelper.getFrequency(mSamplingPeriodUs, TimeUnit.MICROSECONDS);
    }

    /**
     * @return A string representing the frequency equivalent to
     * {@link #getRequestedSamplingPeriodUs()}.
     */
    public String getFrequencyString() {
        if (mSamplingPeriodUs == SensorManager.SENSOR_DELAY_FASTEST) {
            return "fastest";
        }
        return String.format("%.2fhz", getFrequencyHz());
    }

    /**
     * @return The requested collection max batch report latency in microseconds.
     */
    public int getMaxReportLatencyUs() {
        return mMaxReportLatencyUs;
    }

    /**
     * Returns {@code true} if there might be other listeners of {@link #getSensor()} requesting
     * data at different sampling rates (the rates are unknown); false otherwise.
     */
    public boolean isSensorSamplingRateOverloaded() {
        return mSensorMightHaveMoreListeners
                && mSamplingPeriodUs != SensorManager.SENSOR_DELAY_FASTEST;
    }

    /**
     * Convert the {@link #getRequestedSamplingPeriodUs()} into delay in microseconds.
     * <p>
     * The flags SensorManager.SENSOR_DELAY_[GAME|UI|NORMAL] are not supported since the CDD does
     * not specify values for these flags. The rate is set to the max of
     * {@link Sensor#getMinDelay()} and the rate given.
     * </p>
     */
    public int getExpectedSamplingPeriodUs() {
        if (!isDelayRateTestable()) {
            throw new IllegalArgumentException("rateUs cannot be SENSOR_DELAY_[GAME|UI|NORMAL]");
        }

        int expectedSamplingPeriodUs = mSamplingPeriodUs;
        int sensorMaxDelay = mSensor.getMaxDelay();
        if (sensorMaxDelay > 0) {
            expectedSamplingPeriodUs = Math.min(expectedSamplingPeriodUs, sensorMaxDelay);
        }

        return Math.max(expectedSamplingPeriodUs, mSensor.getMinDelay());
    }

    /**
     * Calculate the maximum expected sampling period in us.
     * @return The maximum acceptable actual sampling period of this sensor.
     *         For continuous sensors, this is higher than {@link #getExpectedSamplingPeriodUs()}
     *         because sensors are allowed to run up to 10% slower than requested.
     *         For sensors with other reporting modes, this is the maximum integer
     *         {@link Integer#MAX_VALUE} as they can report no events for long
     *         periods of time.
     */
    public int getMaximumExpectedSamplingPeriodUs() {
        int sensorReportingMode = mSensor.getReportingMode();
        if (sensorReportingMode != Sensor.REPORTING_MODE_CONTINUOUS) {
            return Integer.MAX_VALUE;
        }

        int expectedSamplingPeriodUs = getExpectedSamplingPeriodUs();
        return (int) (expectedSamplingPeriodUs / MAXIMUM_EXPECTED_SAMPLING_FREQUENCY_MULTIPLIER);
    }


    /**
     * Calculate the allowed sensor start delay.
     *
     * CDD Section 7.3:
     * MUST report the first sensor sample within 400 milliseconds + 2 * sample_time of the
     * sensor being activated. It is acceptable for this sample to have an accuracy of 0.
     *
     * [CDD] Keep this updated with CDD.
     */
    public long getAllowedSensorStartDelay() {
        return TimeUnit.MILLISECONDS.toMicros(400) + 2 * getMaximumExpectedSamplingPeriodUs();
    }

    /**
     * @return The number of axes in the coordinate system of the sensor under test.
     */
    public int getSensorAxesCount() {
        switch (mSensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                return 3;
            default:
                throw new IllegalStateException("Axes count needs to be defined for sensor type: "
                        + mSensor.getStringType());
        }
    }

    /**
     * Get the default sensor for a given type.
     *
     * @deprecated Used for historical reasons, sensor tests must be written around Sensor objects,
     * so all sensors of a given type are exercised.
     */
    @Deprecated
    public static Sensor getSensor(Context context, int sensorType) {
        SensorManager sensorManager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            throw new IllegalStateException("SensorService is not present in the system.");
        }

        Sensor sensor = sensorManager.getDefaultSensor(sensorType);
        if(sensor == null) {
            throw new SensorNotSupportedException(sensorType);
        }
        return sensor;
    }

    /**
     * @return The maximum latency of a given sensor, on top of {@link #getMaxReportLatencyUs()}.
     *
     * NOTE: The latency is defined as the time between the event happens and the time the event is
     * generated.
     *
     * - At time event_time (reported in the sensor event), the physical event happens
     * - At time event_time + detection_latency, the physical event is detected and the event is
     *   saved in the hardware fifo
     * - At time event_time + detection_latency + report_latency, the event is reported through the
     *   HAL
     *
     * Soon after that, the event is piped through the framework to the application. This time may
     * vary depending on the CPU load. The time 'detection_latency' must be less than
     * {@link #getSensorMaxDetectionLatencyNs(Sensor)}, and 'report_latency' must be less than
     * {@link #getMaxReportLatencyUs()} passed through batch() at the HAL level.
     */
    // TODO: when all tests are moved to use the Sensor test framework, make this method non-static
    public static long getSensorMaxDetectionLatencyNs(Sensor sensor) {
        int reportLatencySec;
        switch (sensor.getType()) {
            case Sensor.TYPE_STEP_DETECTOR:
                reportLatencySec = 2;
                break;
            case Sensor.TYPE_STEP_COUNTER:
                reportLatencySec = 10;
                break;
            case Sensor.TYPE_SIGNIFICANT_MOTION:
                reportLatencySec = 10;
                break;
            default:
                reportLatencySec = 0;
        }
        return TimeUnit.SECONDS.toNanos(reportLatencySec);
    }

    @Override
    public String toString() {
        return String.format(
                "Sensor='%s', SamplingRateOverloaded=%s, SamplingPeriod=%sus, "
                        + "MaxReportLatency=%sus",
                mSensor,
                isSensorSamplingRateOverloaded(),
                mSamplingPeriodUs,
                mMaxReportLatencyUs);
    }

    /**
     * Return true if {@link #getRequestedSamplingPeriodUs()} is not one of
     * {@link SensorManager#SENSOR_DELAY_GAME}, {@link SensorManager#SENSOR_DELAY_UI}, or
     * {@link SensorManager#SENSOR_DELAY_NORMAL}.
     */
    private boolean isDelayRateTestable() {
        return (mSamplingPeriodUs >= 0
                && mSamplingPeriodUs != SensorManager.SENSOR_DELAY_GAME
                && mSamplingPeriodUs != SensorManager.SENSOR_DELAY_UI
                && mSamplingPeriodUs != SensorManager.SENSOR_DELAY_NORMAL);
    }

    public boolean isDeviceSuspendTest() {
        return mIsDeviceSuspendTest;
    }

    public boolean isIntegrationTest() {
        return mIsIntegrationTest;
    }
}

