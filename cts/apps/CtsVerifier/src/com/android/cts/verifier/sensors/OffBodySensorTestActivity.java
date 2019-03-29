/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.verifier.sensors;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorNotSupportedException;
import android.hardware.cts.helpers.SuspendStateMonitor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.helpers.SensorTestScreenManipulator;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import static junit.framework.Assert.fail;


/**
 * Manual test for testing the low-latency offbody detect sensor. This test consists of 3
 * sub-tests designed to verify the sensor event data, verify event trigger response times
 * are within spec for the sensor type, and to verify that the sensor can wake the device.
 */
public class OffBodySensorTestActivity
        extends SensorCtsVerifierTestActivity {
    private static final String TAG="OffbodySensorTest";
    private static String ACTION_ALARM = "OffBodySensorTestActivity.ACTION_ALARM";
    private static final int MAX_OFF_BODY_EVENT_LATENCY_MS = 1000;
    private static final int MAX_ON_BODY_EVENT_LATENCY_MS = 5000;
    private static final int COUNTDOWN_INTERVAL_MS = 1000;
    private static final int LLOB_EVENT_MAX_DELAY_SEC = 20;
    private static final long MAX_ALLOWED_DELAY_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long RESULT_REPORT_SHOW_TIME_MS = TimeUnit.SECONDS.toMillis(5);
    private static final int OFFBODY_EVENT_VALUES_LENGTH = 1;
    private static final int COUNTDOWN_NUM_INTERVALS = 3;

    private static final float OFF_BODY_EVENT_VALUE = 0;
    private static final float ON_BODY_EVENT_VALUE = 1;
    private static final float BAD_VALUE_SEEN_INIT = 0;
    private static float mBadValueSeen = BAD_VALUE_SEEN_INIT;

    private enum State {
        OFF_BODY, ON_BODY, UNKNOWN
    }

    // time to wait for offbody event after the device has gone into suspend. Even after
    // 45 secs if LLOB sensor does not trigger, the test will fail.
    private static final long ALARM_WAKE_UP_AP_DELAY_MS = TimeUnit.SECONDS.toMillis(45);

    // acceptable time difference between event time and AP wake up time.
    private static final long MAX_ACCEPTABLE_DELAY_EVENT_AP_WAKE_UP_NS =
            TimeUnit.MILLISECONDS.toNanos(1200);

    private static final int NANOSECONDS_PER_MILLISECOND = 1000000;
    private AlarmManager mAlarmManager;
    private SensorManager mSensorManager;
    private Sensor mOffBodySensor;
    private boolean mOffBodySensorRegistered;
    private long mTestStartTimestampMs;
    private State mPreviousSensorState;
    private PendingIntent mPendingIntent;
    private PowerManager.WakeLock mDeviceSuspendLock;
    private SensorEventVerifier mVerifier;
    private SensorTestScreenManipulator mScreenManipulator;

    public class SensorEventRegistry {
        public final SensorEvent event;
        public final long receiveTimestampNanos;

        public SensorEventRegistry(SensorEvent event, long realtimeTimestampNanos) {
            this.event = event;
            this.receiveTimestampNanos = realtimeTimestampNanos;
        }
    }

    private class SensorEventVerifier implements SensorEventListener {
        private volatile CountDownLatch mCountDownLatch;
        private volatile SensorEventRegistry mEventRegistry;
        private volatile long mTimestampForLastSensorEvent = 0;

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override
        public void onSensorChanged(SensorEvent event) {
            long elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
            int type = event.sensor.getType();

            if (type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
                switch((int) event.values[0]) {
                    case (int) OFF_BODY_EVENT_VALUE:
                        Log.i(TAG, "onSensorChanged(): OFF_BODY ts="+event.timestamp+
                                ", now="+elapsedRealtimeNanos+", delta="+
                                (elapsedRealtimeNanos-event.timestamp)/1000000+"mS");
                        mPreviousSensorState = State.OFF_BODY;
                        break;
                    case (int) ON_BODY_EVENT_VALUE:
                        Log.i(TAG, "onSensorChanged(): ON_BODY ts = "+event.timestamp+
                                ", now="+elapsedRealtimeNanos+", delta="+
                                (elapsedRealtimeNanos-event.timestamp)/1000000+"mS");
                        mPreviousSensorState = State.ON_BODY;
                        break;
                    default:
                        Log.e(TAG, "onSensorChanged(): invalid value "+event.values[0]+
                                " received");
                        mBadValueSeen = event.values[0];
                        break;
                }
                mEventRegistry = new SensorEventRegistry(event, elapsedRealtimeNanos);
                getTestLogger().logMessage(
                        R.string.snsr_offbody_state_change,
                        (int) event.values[0],
                        elapsedRealtimeNanos);
                releaseLatch();
            }
        }

        public void releaseLatch() {
            if (mCountDownLatch != null) {
                mCountDownLatch.countDown();
            }
        }

        public long getTimeStampForSensorEvent() {
            return mTimestampForLastSensorEvent;
        }

        public String awaitAndVerifyEvent(float expectedResponseValue) throws Throwable {
            return awaitAndVerifyEvent(expectedResponseValue, 0);
        }

        public String awaitAndVerifyEvent(float expectedResponseValue, int maxEventLatencyMs)
                throws Throwable {
            SensorEventRegistry registry = waitForEvent();
            String eventArrivalMessage;
            if ((registry == null) || (registry.event == null)) {
                eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival, false);
                Assert.fail(eventArrivalMessage);
            }

            // verify an event arrived, and it is indeed a Low Latency Offbody Detect event
            SensorEvent event = registry.event;
            eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival, event != null);
            Assert.assertNotNull(eventArrivalMessage, event);

            String result = verifyEvent(registry, expectedResponseValue, maxEventLatencyMs);
            return result;
        }

        public String verifyEvent(SensorEventRegistry registry, float expectedResponseValue,
                int maxEventLatencyMs) throws Throwable {
            int eventType = registry.event.sensor.getType();
            String eventTypeMessage = getString(
                    R.string.snsr_offbody_event_type,
                    Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT,
                    eventType);
            Assert.assertEquals(eventTypeMessage,
                    Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT,
                    eventType);

            float value = registry.event.values[0];
            String sensorName = registry.event.sensor.getName();
            String eventName = (value == ON_BODY_EVENT_VALUE) ? "ON-BODY" : "OFF-BODY";

            long eventLatencyMs = (registry.receiveTimestampNanos/NANOSECONDS_PER_MILLISECOND)
                    - mTestStartTimestampMs;

            int valuesLength = registry.event.values.length;
            String valuesLengthMessage = getString(
                    R.string.snsr_event_length,
                    OFFBODY_EVENT_VALUES_LENGTH,
                    valuesLength,
                    sensorName);
            Assert.assertEquals(valuesLengthMessage, OFFBODY_EVENT_VALUES_LENGTH, valuesLength);

            String valuesMessage = getString(
                    R.string.snsr_event_value,
                    expectedResponseValue,
                    value,
                    sensorName);
            Assert.assertEquals(valuesMessage, expectedResponseValue, value);

            if (maxEventLatencyMs != 0) {
                Log.i(TAG, "event latency was "+eventLatencyMs+" ms for "+
                        eventName+" event");
                String responseViolationMessage = getString(
                    R.string.snsr_offbody_response_timing_violation,
                    eventName,
                    maxEventLatencyMs,
                    eventLatencyMs);
                boolean violation = (eventLatencyMs > maxEventLatencyMs);
                Assert.assertFalse(responseViolationMessage, violation);
            }
            return null;
        }

        private void verifyOffbodyEventNotInvalid() throws InterruptedException {
            if (mBadValueSeen != BAD_VALUE_SEEN_INIT) {
                Assert.fail(
                    String.format(getString(R.string.snsr_offbody_event_invalid_value),
                    OFF_BODY_EVENT_VALUE,
                    ON_BODY_EVENT_VALUE,
                    mBadValueSeen));
            }
        }

        private SensorEventRegistry waitForEvent() throws InterruptedException {
            return waitForEvent(null);
        }

        private SensorEventRegistry waitForEvent(PowerManager.WakeLock suspendLock)
                throws InterruptedException {
            mCountDownLatch = new CountDownLatch(1);

            if ((suspendLock != null) && suspendLock.isHeld()) {
                suspendLock.release();
            }

            mCountDownLatch.await(LLOB_EVENT_MAX_DELAY_SEC, TimeUnit.SECONDS);

            if ((suspendLock != null) && !suspendLock.isHeld()) {
                suspendLock.acquire();
            }

            SensorEventRegistry registry = mEventRegistry;

            // Save the last timestamp when the event triggered.
            if (mEventRegistry != null && mEventRegistry.event != null) {
                mTimestampForLastSensorEvent = mEventRegistry.event.timestamp;
            }

            mEventRegistry = null;
            verifyOffbodyEventNotInvalid();
            return registry != null ? registry : new SensorEventRegistry(null, 0);
        }

        public SensorEvent waitForSensorEvent() throws InterruptedException {
            SensorEvent event = null;
            mCountDownLatch = new CountDownLatch(1);
            mCountDownLatch.await(LLOB_EVENT_MAX_DELAY_SEC, TimeUnit.SECONDS);

            if (mEventRegistry != null && mEventRegistry.event != null) {
                event = mEventRegistry.event;
            }

            mEventRegistry = null;
            verifyOffbodyEventNotInvalid();
            return event;
        }
    }

    public OffBodySensorTestActivity() {
        super(OffBodySensorTestActivity.class);
    }


    @Override
    protected void activitySetUp() throws InterruptedException {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mDeviceSuspendLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                            "OffBodySensorTestActivity");
        mDeviceSuspendLock.acquire();
        mOffBodySensorRegistered = false;
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mOffBodySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT,
                true);
        if (mOffBodySensor == null) {
            setTestResultAndFinish(true);
            return;
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(myBroadCastReceiver,
                                        new IntentFilter(ACTION_ALARM));
        Intent intent = new Intent(this, AlarmReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        mScreenManipulator = new SensorTestScreenManipulator(this);
        try {
            mScreenManipulator.initialize(this);
        } catch (InterruptedException e) {
        }
    }

    private void startTimeoutTimer(long delayMs) {
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                SystemClock.elapsedRealtime() + delayMs,
                                mPendingIntent);
    }

    private void stopTimeoutTimer() {
        mAlarmManager.cancel(mPendingIntent);
    }

    private void stopOffbodySensorListener(SensorEventVerifier verifier) {
        if (mOffBodySensorRegistered) {
            mSensorManager.unregisterListener(verifier);
            mOffBodySensorRegistered = false;
        }
    }

    private boolean startOffbodySensorListener(SensorEventVerifier verifier) {
        if (!mOffBodySensorRegistered) {
            if (!mSensorManager.registerListener(verifier, mOffBodySensor,
                        SensorManager.SENSOR_DELAY_FASTEST)) {
                Log.e(TAG, "error registering listener for LLOB");
                setTestResultAndFinish(true);
                return false;
            }
            mOffBodySensorRegistered = true;
        }
        return true;
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent alarm_intent = new Intent(context, OffBodySensorTestActivity.class);
            alarm_intent.setAction(OffBodySensorTestActivity.ACTION_ALARM);
            LocalBroadcastManager.getInstance(context).sendBroadcastSync(alarm_intent);
        }
    }

    public BroadcastReceiver myBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mVerifier.releaseLatch();
            mScreenManipulator.turnScreenOn();
            if (!mDeviceSuspendLock.isHeld()) {
                mDeviceSuspendLock.acquire();
            }
        }
    };

    public String testOffbodyDetectResponseTime() throws Throwable {
        Sensor wakeUpSensor = mSensorManager.getDefaultSensor(
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        if (wakeUpSensor == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        }
        return runOffbodyDetectResponseTimeTest(wakeUpSensor);
    }

    public String testOnbodyDetectResponseTime() throws Throwable {
        Sensor wakeUpSensor = mSensorManager.getDefaultSensor(
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        if (wakeUpSensor == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        }
        return runOnbodyDetectResponseTimeTest(wakeUpSensor);
    }

    public String testWakeAPOffbodyDetect() throws Throwable {
        Sensor wakeUpSensor = mSensorManager.getDefaultSensor(
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        if (wakeUpSensor == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        }
        return runWakeAPOffbodyDetectTest(wakeUpSensor);
    }

    public String runOffbodyDetectResponseTimeTest(Sensor sensor) throws Throwable {
        boolean success;
        String eventArrivalMessage;
        mOffBodySensor = sensor;
        mBadValueSeen = BAD_VALUE_SEEN_INIT;

        try {
            // If device not currently on-body, instruct user to put it on wrist
            mTestStartTimestampMs = 0;
            mVerifier = new SensorEventVerifier();
            success = startOffbodySensorListener(mVerifier);
            Assert.assertTrue(
                    getString(R.string.snsr_offbody_sensor_registration, success),
                    success);
            SensorEvent event = mVerifier.waitForSensorEvent();
            eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival, event != null);
            Assert.assertNotNull(eventArrivalMessage, event);

            SensorTestLogger logger = getTestLogger();
            if (event.values[0] != ON_BODY_EVENT_VALUE) {
                // Instruct user on how to perform offbody detect test
                logger.logInstructions(R.string.snsr_start_offbody_sensor_test_instr);
                waitForUserToBegin();
                if (mPreviousSensorState != State.ON_BODY) {
                    event = mVerifier.waitForSensorEvent();
                    eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival,
                            event != null);
                    Assert.assertNotNull(eventArrivalMessage, event);
                    if (event.values[0] != ON_BODY_EVENT_VALUE) {
                        Assert.fail(
                            String.format(getString(R.string.snsr_offbody_event_wrong_value),
                            ON_BODY_EVENT_VALUE,
                            event.values[0]));
                    }
                }
            }

            // Instruct user on how to perform offbody detect test
            logger.logInstructions(R.string.snsr_offbody_detect_test_instr);
            waitForUserToBegin();

            // Count down before actually starting, leaving time to react after pressing the Next
            // button.
            for (int i = 0; i < COUNTDOWN_NUM_INTERVALS; i++) {
                try {
                    Thread.sleep(COUNTDOWN_INTERVAL_MS);
                } catch (InterruptedException e) {
                    // Ignore the interrupt and continue counting down.
                }
                logger.logInstructions(R.string.snsr_offbody_detect_test_countdown,
                        COUNTDOWN_NUM_INTERVALS - i - 1);
            }
            mTestStartTimestampMs = SystemClock.elapsedRealtime();

            // Verify off-body event latency is within spec
            mVerifier.awaitAndVerifyEvent(OFF_BODY_EVENT_VALUE, MAX_OFF_BODY_EVENT_LATENCY_MS);
        } finally {
            stopOffbodySensorListener(mVerifier);
        }
        return null;
    }

    public String runOnbodyDetectResponseTimeTest(Sensor sensor) throws Throwable {
        mOffBodySensor = sensor;
        SensorTestLogger logger = getTestLogger();
        mBadValueSeen = BAD_VALUE_SEEN_INIT;

        try {
            // If device not currently off-body, instruct user to remove it from wrist
            mTestStartTimestampMs = 0;
            mVerifier = new SensorEventVerifier();
            boolean success = startOffbodySensorListener(mVerifier);
            Assert.assertTrue(
                    getString(R.string.snsr_offbody_sensor_registration, success),
                    success);
            SensorEvent event = mVerifier.waitForSensorEvent();
            String eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival,
                    event != null);
            Assert.assertNotNull(eventArrivalMessage, event);
            if (event.values[0] != OFF_BODY_EVENT_VALUE) {
                // Instruct user on how to perform offbody detect test
                logger.logInstructions(R.string.snsr_start_onbody_sensor_test_instr);
                waitForUserToBegin();
                if (mPreviousSensorState != State.OFF_BODY) {
                    event = mVerifier.waitForSensorEvent();
                    eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival,
                            event != null);
                    Assert.assertNotNull(eventArrivalMessage, event);
                    if (event.values[0] != OFF_BODY_EVENT_VALUE) {
                        Assert.fail(
                            String.format(getString(R.string.snsr_offbody_event_wrong_value),
                            OFF_BODY_EVENT_VALUE,
                            event.values[0]));
                    }
                }
            }

            // Display on-body latency test instructions
            logger.logInstructions(R.string.snsr_onbody_detect_test_instr);
            waitForUserToBegin();
            mTestStartTimestampMs = SystemClock.elapsedRealtime();
            mVerifier.awaitAndVerifyEvent(ON_BODY_EVENT_VALUE, MAX_ON_BODY_EVENT_LATENCY_MS);
        } finally {
            stopOffbodySensorListener(mVerifier);
        }
        return null;
    }

    public String runWakeAPOffbodyDetectTest(Sensor sensor) throws Throwable {
        final long ALARM_WAKE_UP_DELAY_MS = 40000;
        String eventArrivalMessage;
        SensorEventRegistry registry;
        SensorTestLogger logger = getTestLogger();
        mBadValueSeen = BAD_VALUE_SEEN_INIT;
        mVerifier = new SensorEventVerifier();
        mOffBodySensor = sensor;
        mTestStartTimestampMs = 0;

        mTestStartTimestampMs = SystemClock.elapsedRealtime();
        SuspendStateMonitor suspendStateMonitor = new SuspendStateMonitor();
        try {
            boolean success = startOffbodySensorListener(mVerifier);
            Assert.assertTrue(
                    getString(R.string.snsr_offbody_sensor_registration, success),
                    success);

            // grab the current off-body state, which should be ON-BODY
            if (mPreviousSensorState != State.ON_BODY) {
                registry = mVerifier.waitForEvent();
                if ((registry == null) || (registry.event == null) ||
                        (registry.event.values[0] != ON_BODY_EVENT_VALUE)) {
                    eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival, false);
                    Assert.fail(eventArrivalMessage);

                    // Tell user to put watch on wrist
                    logger.logInstructions(R.string.snsr_start_offbody_sensor_test_instr);
                    waitForUserToBegin();
                    if (mPreviousSensorState != State.ON_BODY) {
                        registry = mVerifier.waitForEvent();
                        if ((registry == null) || (registry.event == null)) {
                            eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival, false);
                            Assert.fail(eventArrivalMessage);
                        } else {
                            Assert.assertTrue(
                                String.format(getString(R.string.snsr_offbody_event_wrong_value),
                                ON_BODY_EVENT_VALUE,
                                registry.event.values[0]),
                                ON_BODY_EVENT_VALUE == registry.event.values[0]);
                        }
                    }
                }
            }

            // Instruct user on how to perform offbody detect sleep test
            logger.logInstructions(R.string.snsr_ap_wake_offbody_detect_test_instr);
            waitForUserToBegin();

            long testStartTimeNs = SystemClock.elapsedRealtimeNanos();
            startTimeoutTimer(ALARM_WAKE_UP_AP_DELAY_MS);

            // Wait for the first event to trigger. Device is expected to go into suspend here.
            registry = mVerifier.waitForEvent(mDeviceSuspendLock);
            if ((registry == null) || (registry.event == null)) {
                eventArrivalMessage = getString(R.string.snsr_offbody_event_arrival, false);
                Assert.fail(eventArrivalMessage);
            }

            mVerifier.verifyEvent(registry, OFF_BODY_EVENT_VALUE, 0);

            long eventTimeStampNs = registry.event.timestamp;
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
            stopTimeoutTimer();
            suspendStateMonitor.cancel();
            mScreenManipulator.turnScreenOn();
            playSound();
        }
        return null;
    }

    @Override
    protected void activityCleanUp() {
        if (mOffBodySensorRegistered) {
            stopOffbodySensorListener(mVerifier);
        }
        stopTimeoutTimer();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myBroadCastReceiver);
        if (mOffBodySensor != null) {
            mOffBodySensor = null;
        }
        if (mScreenManipulator != null){
            mScreenManipulator.close();
        }
        if ((mDeviceSuspendLock != null) && mDeviceSuspendLock.isHeld()) {
            mDeviceSuspendLock.release();
        }
    }
}
