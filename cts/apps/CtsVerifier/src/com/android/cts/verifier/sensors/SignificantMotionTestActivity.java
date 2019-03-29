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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;
import com.android.cts.verifier.sensors.helpers.SensorTestScreenManipulator;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.cts.helpers.SensorNotSupportedException;
import android.hardware.cts.helpers.SuspendStateMonitor;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import junit.framework.Assert;

/**
 * Test cases for Significant Motion sensor.
 * They use walking motion to change the location and trigger Significant Motion.
 */
public class SignificantMotionTestActivity extends SensorCtsVerifierTestActivity {
    public SignificantMotionTestActivity() {
        super(SignificantMotionTestActivity.class);
    }

    // acceptable time difference between event time and system time
    private static final long MAX_ACCEPTABLE_EVENT_TIME_DELAY_NANOS =
            TimeUnit.MILLISECONDS.toNanos(500);

    // acceptable time difference between event time and AP wake up time.
    private static final long MAX_ACCEPTABLE_DELAY_EVENT_AP_WAKE_UP_NS =
            TimeUnit.MILLISECONDS.toNanos(2000);

    // time to wait for SMD after the device has gone into suspend. Even after
    // 45 secs if SMD does not trigger, the test will fail.
    private static final long ALARM_WAKE_TIME_DELAY_MS = TimeUnit.SECONDS.toMillis(45);

    // time for the test to wait for a trigger
    private static final int TRIGGER_MAX_DELAY_SECONDS = 30;
    private static final int VIBRATE_DURATION_MILLIS = 10000;

    private static final int EVENT_VALUES_LENGTH = 1;
    private static final float EXPECTED_EVENT_VALUE = 1.0f;
    private static String ACTION_ALARM = "SignificantMotionTestActivity.ACTION_ALARM";

    private SensorManager mSensorManager;
    private Sensor mSensorSignificantMotion;
    private TriggerVerifier mVerifier;
    private SensorTestScreenManipulator mScreenManipulator;
    private WakeLock mPartialWakeLock;

    /**
     * Test cases.
     */
    @SuppressWarnings("unused")
    public String testTrigger() throws Throwable {
        return runTest(
                R.string.snsr_significant_motion_test_trigger,
                true /* isMotionExpected */,
                false /* cancelEventNotification */,
                false /* vibrate */);
    }

    @SuppressWarnings("unused")
    public String testNotTriggerAfterCancel() throws Throwable {
        return runTest(
                R.string.snsr_significant_motion_test_cancel,
                false /* isMotionExpected */,
                true /* cancelEventNotification */,
                false /* vibrate */);
    }

    /**
     * Verifies that Significant Motion is not trigger by the vibrator motion.
     */
    @SuppressWarnings("unused")
    public String testVibratorDoesNotTrigger() throws Throwable {
     return runTest(
             R.string.snsr_significant_motion_test_vibration,
             false /* isMotionExpected */,
             false /* cancelEventNotification */,
             true /* vibrate */);
    }

    /**
     * Verifies that the natural motion of keeping the device in hand does not change the location.
     * It ensures that Significant Motion will not trigger in that scenario.
     */
    @SuppressWarnings("unused")
    public String testInHandDoesNotTrigger() throws Throwable {
        return runTest(
                R.string.snsr_significant_motion_test_in_hand,
                false /* isMotionExpected */,
                false /* cancelEventNotification */,
                false /* vibrate */);
    }

    @SuppressWarnings("unused")
    public String testSittingDoesNotTrigger() throws Throwable {
        return runTest(
                R.string.snsr_significant_motion_test_sitting,
                false /* isMotionExpected */,
                false /* cancelEventNotification */,
                false /* vibrate */);
    }

    @SuppressWarnings("unused")
    public String testTriggerDeactivation() throws Throwable {
        SensorTestLogger logger = getTestLogger();
        logger.logInstructions(R.string.snsr_significant_motion_test_deactivation);
        waitForUserToBegin();

        TriggerVerifier verifier = new TriggerVerifier();
        mSensorManager.requestTriggerSensor(verifier, mSensorSignificantMotion);
        logger.logWaitForSound();

        String result;
        try {
            mPartialWakeLock.acquire();

            // wait for the first event to trigger
            verifier.verifyEventTriggered();

            // wait for a second event not to trigger
            result = verifier.verifyEventNotTriggered();
        } finally {
            playSound();
            mScreenManipulator.turnScreenOn();
            mPartialWakeLock.release();
        }
        return result;
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent alarm_intent = new Intent(context, SignificantMotionTestActivity.class);
            alarm_intent.setAction(SignificantMotionTestActivity.ACTION_ALARM);
            LocalBroadcastManager.getInstance(context).sendBroadcastSync(alarm_intent);
        }
    }

    public BroadcastReceiver myBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mVerifier.releaseLatch();
            mScreenManipulator.turnScreenOn();
            try {
                playSound();
            } catch (InterruptedException e) {
                // Ignore ...
            }
        }
    };

    @SuppressWarnings("unused")
    public String testAPWakeUpOnSMDTrigger() throws Throwable {
        SensorTestLogger logger = getTestLogger();
        logger.logInstructions(R.string.snsr_significant_motion_ap_suspend);
        waitForUserToBegin();
        mVerifier = new TriggerVerifier();
        mSensorManager.requestTriggerSensor(mVerifier, mSensorSignificantMotion);
        long testStartTimeNs = SystemClock.elapsedRealtimeNanos();
        Handler handler = new Handler(Looper.getMainLooper());
        SuspendStateMonitor suspendStateMonitor = new SuspendStateMonitor();

        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                     SystemClock.elapsedRealtime() + ALARM_WAKE_TIME_DELAY_MS, pendingIntent);
        try {
            // Wait for the first event to trigger. Device is expected to go into suspend here.
            mVerifier.verifyEventTriggered();
            long eventTimeStampNs = mVerifier.getTimeStampForTriggerEvent();
            long endTimeNs = SystemClock.elapsedRealtimeNanos();
            long lastWakeupTimeNs = TimeUnit.MILLISECONDS.toNanos(
                    suspendStateMonitor.getLastWakeUpTime());
            Assert.assertTrue(getString(R.string.snsr_device_did_not_go_into_suspend),
                              testStartTimeNs < lastWakeupTimeNs && lastWakeupTimeNs < endTimeNs);
            long timestampDelta = Math.abs(lastWakeupTimeNs - eventTimeStampNs);
            Assert.assertTrue(
                    String.format(getString(R.string.snsr_device_did_not_wake_up_at_trigger),
                              TimeUnit.NANOSECONDS.toMillis(lastWakeupTimeNs),
                              TimeUnit.NANOSECONDS.toMillis(eventTimeStampNs)),
                              timestampDelta < MAX_ACCEPTABLE_DELAY_EVENT_AP_WAKE_UP_NS);
        } finally {
            am.cancel(pendingIntent);
            suspendStateMonitor.cancel();
            mScreenManipulator.turnScreenOn();
            playSound();
        }
        return null;
    }

    /**
     * @param instructionsResId Instruction to be shown to testers
     * @param isMotionExpected Should the device detect significant motion event
     *            for this test?
     * @param cancelEventNotification If TRUE, motion notifications will be
     *            requested first and request will be cancelled
     * @param vibrate If TRUE, vibration will be concurrent with the test
     * @throws Throwable
     */
    private String runTest(
            int instructionsResId,
            boolean isMotionExpected,
            boolean cancelEventNotification,
            boolean vibrate) throws Throwable {
        SensorTestLogger logger = getTestLogger();
        logger.logInstructions(instructionsResId);
        waitForUserToBegin();

        if (vibrate) {
            vibrate(VIBRATE_DURATION_MILLIS);
        }

        TriggerVerifier verifier = new TriggerVerifier();
        boolean success = mSensorManager.requestTriggerSensor(verifier, mSensorSignificantMotion);
        Assert.assertTrue(
                getString(R.string.snsr_significant_motion_registration, success),
                success);
        if (cancelEventNotification) {
            Assert.assertTrue(
                    getString(R.string.snsr_significant_motion_cancelation),
                    mSensorManager.cancelTriggerSensor(verifier, mSensorSignificantMotion));
        }
        logger.logWaitForSound();

        String result;
        try {
            mPartialWakeLock.acquire();

            if (isMotionExpected) {
                result = verifier.verifyEventTriggered();
            } else {
                result = verifier.verifyEventNotTriggered();
            }
        } finally {
            mSensorManager.cancelTriggerSensor(verifier, mSensorSignificantMotion);

            // notify user test finished
            playSound();
            mScreenManipulator.turnScreenOn();
            mPartialWakeLock.release();
        }
        return result;
    }

    @Override
    protected void activitySetUp() {
        mSensorManager = (SensorManager) getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        mSensorSignificantMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        if (mSensorSignificantMotion == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_SIGNIFICANT_MOTION);
        }

        mScreenManipulator = new SensorTestScreenManipulator(this);
        try {
            mScreenManipulator.initialize(this);
        } catch (InterruptedException e) {
        }
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SignificantMotionTestActivity");
        LocalBroadcastManager.getInstance(this).registerReceiver(myBroadCastReceiver,
                                            new IntentFilter(ACTION_ALARM));
    }

    @Override
    protected void activityCleanUp() {
        if (mScreenManipulator != null) {
            // after this screen does not have to be on constantly
            mScreenManipulator.releaseScreenOn();
        }
        if (mPartialWakeLock != null && mPartialWakeLock.isHeld()) {
            mPartialWakeLock.release();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myBroadCastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mScreenManipulator != null){
            mScreenManipulator.close();
        }
    }

    /**
     * Helper Trigger listener for testing.
     * It cannot be reused.
     */
    private class TriggerVerifier extends TriggerEventListener {
        private volatile CountDownLatch mCountDownLatch;
        private volatile TriggerEventRegistry mEventRegistry;
        private volatile long mTimestampForTriggeredEvent = 0;

        // TODO: refactor out if needed
        private class TriggerEventRegistry {
            public final TriggerEvent triggerEvent;
            public final long realtimeTimestampNanos;

            public TriggerEventRegistry(TriggerEvent event, long realtimeTimestampNanos) {
                this.triggerEvent = event;
                this.realtimeTimestampNanos = realtimeTimestampNanos;
            }
        }

        public void onTrigger(TriggerEvent event) {
            long elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
            mEventRegistry = new TriggerEventRegistry(event, elapsedRealtimeNanos);
            mCountDownLatch.countDown();
        }

        public void releaseLatch() {
            if (mCountDownLatch != null) {
                mCountDownLatch.countDown();
            }
        }

        public long getTimeStampForTriggerEvent() {
            return mTimestampForTriggeredEvent;
        }

        public String verifyEventTriggered() throws Throwable {
            TriggerEventRegistry registry = awaitForEvent();

            // verify an event arrived, and it is indeed a Significant Motion event
            TriggerEvent event = registry.triggerEvent;
            String eventArrivalMessage =
                    getString(R.string.snsr_significant_motion_event_arrival, event != null);
            Assert.assertNotNull(eventArrivalMessage, event);

            int eventType = event.sensor.getType();
            String eventTypeMessage = getString(
                    R.string.snsr_significant_motion_event_type,
                    Sensor.TYPE_SIGNIFICANT_MOTION,
                    eventType);
            Assert.assertEquals(eventTypeMessage, Sensor.TYPE_SIGNIFICANT_MOTION, eventType);

            String sensorName = event.sensor.getName();
            int valuesLength = event.values.length;
            String valuesLengthMessage = getString(
                    R.string.snsr_event_length,
                    EVENT_VALUES_LENGTH,
                    valuesLength,
                    sensorName);
            Assert.assertEquals(valuesLengthMessage, EVENT_VALUES_LENGTH, valuesLength);

            float value = event.values[0];
            String valuesMessage = getString(
                    R.string.snsr_event_value,
                    EXPECTED_EVENT_VALUE,
                    value,
                    sensorName);
            Assert.assertEquals(valuesMessage, EXPECTED_EVENT_VALUE, value);

            long deltaThreshold = MAX_ACCEPTABLE_EVENT_TIME_DELAY_NANOS
                    + TestSensorEnvironment.getSensorMaxDetectionLatencyNs(event.sensor);
            return assertTimestampSynchronization(
                    event.timestamp,
                    registry.realtimeTimestampNanos,
                    deltaThreshold,
                    sensorName);
        }

        public String verifyEventNotTriggered() throws Throwable {
            TriggerEventRegistry registry = awaitForEvent();

            TriggerEvent event = registry.triggerEvent;
            String eventMessage =
                    getString(R.string.snsr_significant_motion_event_unexpected, event != null);
            Assert.assertNull(eventMessage, event);
            return eventMessage;
        }

        private TriggerEventRegistry awaitForEvent() throws InterruptedException {
            mCountDownLatch = new CountDownLatch(1);
            mCountDownLatch.await(TRIGGER_MAX_DELAY_SECONDS, TimeUnit.SECONDS);
            TriggerEventRegistry registry = mEventRegistry;

            // Save the last timestamp when the event triggered.
            if (mEventRegistry != null && mEventRegistry.triggerEvent != null) {
                mTimestampForTriggeredEvent = mEventRegistry.triggerEvent.timestamp;
            }

            mEventRegistry = null;
            return registry != null ? registry : new TriggerEventRegistry(null, 0);
        }
    }
}
