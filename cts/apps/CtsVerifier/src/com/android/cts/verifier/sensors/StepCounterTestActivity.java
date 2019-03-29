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

package com.android.cts.verifier.sensors;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;

import junit.framework.Assert;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.MovementDetectorHelper;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StepCounterTestActivity
        extends SensorCtsVerifierTestActivity
        implements SensorEventListener {
    public StepCounterTestActivity() {
        super(StepCounterTestActivity.class);
    }

    private static final int TEST_DURATION_SECONDS = 20;
    private static final long TIMESTAMP_SYNCHRONIZATION_THRESHOLD_NANOS =
            TimeUnit.MILLISECONDS.toNanos(500);

    private static final int MIN_NUM_STEPS_PER_TEST = 10;
    private static final int MAX_STEP_DISCREPANCY = 5;
    private static final long MAX_TOLERANCE_STEP_TIME_NANOS = TimeUnit.SECONDS.toNanos(10);

    private static final long[] VIBRATE_PATTERN = {
            1000L, 500L, 1000L, 750L, 1000L, 500L, 1000L, 750L, 1000L, 1000L, 500L, 1000L,
            750L, 1000L, 500L, 1000L };

    private SensorManager mSensorManager;
    private Sensor mSensorStepCounter;
    private Sensor mSensorStepDetector;
    private MovementDetectorHelper mMovementDetectorHelper;

    private volatile boolean mMoveDetected;

    private final List<Long> mTimestampsUserReported = new ArrayList<Long>();
    private final List<TestSensorEvent> mStepCounterEvents = new ArrayList<TestSensorEvent>();
    private final List<TestSensorEvent> mStepDetectorEvents = new ArrayList<TestSensorEvent>();

    /**
     * A flag that indicates if the test is interested in registering steps reported by the
     * operator. The registration of events happens by tapping the screen throughout the test.
     */
    private volatile boolean mCheckForMotion;

    @Override
    protected void activitySetUp() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorStepCounter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mSensorStepDetector = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        if (mSensorStepCounter == null && mSensorStepDetector == null) {
            throw new SensorTestStateNotSupportedException(
                    "Sensors Step Counter/Detector are not supported.");
        }

        setLogScrollViewListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // during movement of the device, the ScrollView will detect user taps as attempts
                // to scroll, when in reality they are taps in the layout
                // to overcome the fact that a ScrollView cannot be disabled from scrolling, we
                // listen for ACTION_UP events instead of click events in the child layout
                long elapsedTime = SystemClock.elapsedRealtimeNanos();
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }

                try {
                    logUserReportedStep(elapsedTime);
                } catch (InterruptedException e) {
                    // we cannot propagate the exception in the main thread, so we just catch and
                    // restore the status, we don't need to log as we are terminating anyways
                    Thread.currentThread().interrupt();
                }
                return false;
            }
        });
    }

    @Override
    protected void activityCleanUp() {
        setLogScrollViewListener(null /* listener */);
    }

    public String testWalking() throws Throwable {
        return runTest(
                R.string.snsr_step_counter_test_walking,
                MIN_NUM_STEPS_PER_TEST,
                false /* vibrate */);
    }

    public String testStill() throws Throwable {
        return runTest(
                R.string.snsr_step_counter_test_still,
                0 /* expectedSteps */,
                true /* vibrate */);
    }

    /**
     * @param instructionsResId Resource ID containing instruction to be shown to testers
     * @param expectedSteps Number of steps expected in this test
     * @param vibrate If TRUE, vibration will be concurrent with the test
     */
    private String runTest(int instructionsResId, int expectedSteps, boolean vibrate)
            throws Throwable {
        mTimestampsUserReported.clear();
        mStepCounterEvents.clear();
        mStepDetectorEvents.clear();

        mMoveDetected = false;
        mCheckForMotion = false;

        getTestLogger().logInstructions(instructionsResId);
        waitForUserToBegin();

        mCheckForMotion = (expectedSteps > 0);
        if (vibrate) {
            vibrate(VIBRATE_PATTERN);
        }
        startMeasurements();
        getTestLogger().logWaitForSound();

        Thread.sleep(TimeUnit.SECONDS.toMillis(TEST_DURATION_SECONDS));
        mCheckForMotion = false;
        playSound();

        return verifyMeasurements(expectedSteps);
    }

    private void startMeasurements() {
        if (mSensorStepCounter != null) {
            mSensorManager.registerListener(this, mSensorStepCounter,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (mSensorStepDetector != null) {
            mSensorManager.registerListener(this, mSensorStepDetector,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        mMovementDetectorHelper = new MovementDetectorHelper(getApplicationContext()) {
            @Override
            protected void onMovementDetected() {
                mMoveDetected = true;
            }
        };
        mMovementDetectorHelper.start();
    }

    private String verifyMeasurements(int stepsExpected) {
        mSensorManager.unregisterListener(this);
        mMovementDetectorHelper.stop();

        if (mCheckForMotion) {
            Assert.assertTrue(
                    getString(R.string.snsr_movement_expected, mMoveDetected),
                    mMoveDetected);
        }

        final int userReportedSteps = mTimestampsUserReported.size();
        String stepsReportedMessage = getString(
                R.string.snsr_step_counter_expected_steps,
                stepsExpected,
                userReportedSteps);
        Assert.assertFalse(stepsReportedMessage, userReportedSteps < stepsExpected);

        // TODO: split test cases for step detector and counter
        verifyStepDetectorMeasurements();
        verifyStepCounterMeasurements();
        return null;
    }

    private void verifyStepCounterMeasurements() {
        if (mSensorStepCounter == null) {
            // sensor not supported, so no-op
            return;
        }

        final int userReportedSteps = mTimestampsUserReported.size();
        int totalStepsCounted = 0;
        int initialStepCount = -1;
        for (TestSensorEvent counterEvent : mStepCounterEvents) {
            String sensorName = counterEvent.sensor.getName();
            float[] values = counterEvent.values;

            final int expectedLength = 1;
            int valuesLength = values.length;
            String eventLengthMessage = getString(
                    R.string.snsr_event_length,
                    expectedLength,
                    valuesLength,
                    sensorName);
            Assert.assertEquals(eventLengthMessage, expectedLength, valuesLength);

            int stepValue = (int) values[0];
            if (initialStepCount == -1) {
                initialStepCount = stepValue;
            } else {
                int stepsCounted = stepValue - initialStepCount;
                int countDelta = stepsCounted - totalStepsCounted;

                String eventTriggered = getString(
                        R.string.snsr_step_counter_event_changed,
                        countDelta,
                        counterEvent.timestamp);
                Assert.assertTrue(eventTriggered, countDelta > 0);

                // TODO: abstract this into an ISensorVerification

                long deltaThreshold = TIMESTAMP_SYNCHRONIZATION_THRESHOLD_NANOS
                        + TestSensorEnvironment.getSensorMaxDetectionLatencyNs(counterEvent.sensor);
                assertTimestampSynchronization(
                        counterEvent.timestamp,
                        counterEvent.receivedTimestamp,
                        deltaThreshold,
                        counterEvent.sensor.getName());

                totalStepsCounted = stepsCounted;
            }
        }

        int stepsCountedDelta = Math.abs(totalStepsCounted - userReportedSteps);
        String stepsDeltaMessage = getString(
                R.string.snsr_step_counter_detected_reported,
                userReportedSteps,
                totalStepsCounted,
                stepsCountedDelta,
                MAX_STEP_DISCREPANCY);
        Assert.assertFalse(stepsDeltaMessage, stepsCountedDelta > MAX_STEP_DISCREPANCY);

        int stepCounterLength = mStepCounterEvents.size();
        for (int i = 0; i < userReportedSteps && i < stepCounterLength; ++i) {
            long userReportedTimestamp = mTimestampsUserReported.get(i);
            TestSensorEvent counterEvent = mStepCounterEvents.get(i);

            assertTimestampSynchronization(
                    counterEvent.timestamp,
                    userReportedTimestamp,
                    MAX_TOLERANCE_STEP_TIME_NANOS,
                    counterEvent.sensor.getName());
        }
    }

    private void verifyStepDetectorMeasurements() {
        if (mSensorStepDetector == null) {
            // sensor not supported, so no-op
            return;
        }

        final int userReportedSteps = mTimestampsUserReported.size();
        int stepsDetected = mStepDetectorEvents.size();
        int stepsDetectedDelta = Math.abs(stepsDetected - userReportedSteps);
        String stepsDetectedMessage = getString(
                R.string.snsr_step_detector_detected_reported,
                userReportedSteps,
                stepsDetected,
                stepsDetectedDelta,
                MAX_STEP_DISCREPANCY);
        Assert.assertFalse(stepsDetectedMessage, stepsDetectedDelta > MAX_STEP_DISCREPANCY);

        for (TestSensorEvent detectorEvent : mStepDetectorEvents) {
            String sensorName = detectorEvent.sensor.getName();
            float[] values = detectorEvent.values;

            final int expectedLength = 1;
            int valuesLength = values.length;
            String eventLengthMessage = getString(
                    R.string.snsr_event_length,
                    expectedLength,
                    valuesLength,
                    sensorName);
            Assert.assertEquals(eventLengthMessage, expectedLength, valuesLength);

            final float expectedValue = 1.0f;
            float value0 = values[0];
            String eventValueMessage =
                    getString(R.string.snsr_event_value, expectedValue, value0, sensorName);
            Assert.assertEquals(eventValueMessage, expectedValue, value0);
        }

        // TODO: verify correlation of events with steps from user
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public final void onSensorChanged(SensorEvent event) {
        long elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_STEP_COUNTER) {
            mStepCounterEvents.add(new TestSensorEvent(event, elapsedRealtimeNanos));
            getTestLogger().logMessage(
                    R.string.snsr_step_counter_event,
                    elapsedRealtimeNanos,
                    (int) event.values[0]);
        } else if (type == Sensor.TYPE_STEP_DETECTOR) {
            mStepDetectorEvents.add(new TestSensorEvent(event, elapsedRealtimeNanos));
            getTestLogger().logMessage(R.string.snsr_step_detector_event, elapsedRealtimeNanos);

        }
        // TODO: with delayed assertions check events of other types are tracked
    }

    private void logUserReportedStep(long timestamp) throws InterruptedException {
        if (!mCheckForMotion) {
            return;
        }
        playSound();
        mTimestampsUserReported.add(timestamp);
        getTestLogger().logMessage(R.string.snsr_step_reported, timestamp);
    }
}
