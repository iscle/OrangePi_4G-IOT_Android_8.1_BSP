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

import junit.framework.Assert;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener2;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link SensorEventListener2} which performs operations such as waiting for a specific number of
 * events or for a specific time, or waiting for a flush to complete. This class performs
 * verifications and will throw {@link AssertionError}s if there are any errors. It may also wrap
 * another {@link SensorEventListener2}.
 */
public class TestSensorEventListener implements SensorEventListener2 {
    public static final String LOG_TAG = "TestSensorEventListener";

    private static final long EVENT_TIMEOUT_US = TimeUnit.SECONDS.toMicros(5);
    private static final long FLUSH_TIMEOUT_US = TimeUnit.SECONDS.toMicros(10);

    private final ArrayList<TestSensorEvent> mCollectedEvents = new ArrayList<>();
    private final ArrayList<Long> mTimeStampFlushCompleteEvents = new ArrayList<>();
    private final List<CountDownLatch> mEventLatches = new ArrayList<>();
    private final List<CountDownLatch> mFlushLatches = new ArrayList<>();
    private final AtomicInteger mEventsReceivedOutsideHandler = new AtomicInteger();

    private final Handler mHandler;
    private final TestSensorEnvironment mEnvironment;

    // Wakelock for keeping the system running after terminate criterion is met.
    // Useful for CtsVerifier test cases in which cpu can sleep if usb is not connected.
    private final PowerManager.WakeLock mTestSensorEventListenerWakeLock;

    /**
     * @deprecated Use {@link TestSensorEventListener(TestSensorEnvironment)}.
     */
    @Deprecated
    public TestSensorEventListener() {
        this(null /* environment */);
    }

    /**
     * Construct a {@link TestSensorEventListener}.
     */
    public TestSensorEventListener(TestSensorEnvironment environment) {
        this(environment, null /* handler */);
    }

    /**
     * Construct a {@link TestSensorEventListener}.
     */
    public TestSensorEventListener(TestSensorEnvironment environment, Handler handler) {
        mEnvironment = environment;
        mHandler = handler;
        PowerManager pm = (PowerManager) environment.getContext().getSystemService(
                Context.POWER_SERVICE);
        mTestSensorEventListenerWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                                "TestSensorEventListenerWakeLock");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestampNs = SystemClock.elapsedRealtimeNanos();
        checkHandler();
        synchronized (mCollectedEvents) {
            mCollectedEvents.add(new TestSensorEvent(event, timestampNs));
        }
        synchronized (mEventLatches) {
            for (CountDownLatch latch : mEventLatches) {
                latch.countDown();
                if (latch.getCount() == 0 && !mTestSensorEventListenerWakeLock.isHeld()) {
                    mTestSensorEventListenerWakeLock.acquire();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        checkHandler();
    }

    /**
     * @param eventCount
     * @return A CountDownLatch initialzed with eventCount and decremented as sensor events arrive
     * for this listerner.
     */
    public CountDownLatch getLatchForSensorEvents(int eventCount) {
        CountDownLatch latch = new CountDownLatch(eventCount);
        synchronized (mEventLatches) {
            mEventLatches.add(latch);
        }
        return latch;
    }

    /**
     * @return A CountDownLatch initialzed with 1 and decremented as a flush complete arrives
     * for this listerner.
     */
    public CountDownLatch getLatchForFlushCompleteEvent() {
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (mFlushLatches) {
            mFlushLatches.add(latch);
        }
        return latch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFlushCompleted(Sensor sensor) {
        checkHandler();
        long timestampNs = SystemClock.elapsedRealtimeNanos();
        synchronized (mTimeStampFlushCompleteEvents) {
           mTimeStampFlushCompleteEvents.add(timestampNs);
        }
        synchronized (mFlushLatches) {
            for (CountDownLatch latch : mFlushLatches) {
                latch.countDown();
            }
        }
    }

    /**
     * @return The handler (if any) associated with the instance.
     */
    public Handler getHandler() {
        return mHandler;
    }

    /**
     * @return A list of {@link TestSensorEvent}s collected by the listener.
     */
    public List<TestSensorEvent> getCollectedEvents() {
        synchronized (mCollectedEvents){
            return Collections.unmodifiableList((List<TestSensorEvent>) mCollectedEvents.clone());
        }
    }

    /**
     * Clears the internal list of collected {@link TestSensorEvent}s.
     */
    public void clearEvents() {
        synchronized (mCollectedEvents) {
            mCollectedEvents.clear();
        }
    }


    /**
     * Utility method to log the collected events to a file.
     * It will overwrite the file if it already exists, the file is created in a relative directory
     * named 'events' under the sensor test directory (part of external storage).
     */
    public void logCollectedEventsToFile(String fileName, long deviceWakeUpTimeMs,
            long testStartTimeMs, long testStopTimeMs)
        throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("Sensor='").append(mEnvironment.getSensor()).append("', ");
        builder.append("SamplingRateOverloaded=")
                .append(mEnvironment.isSensorSamplingRateOverloaded()).append(", ");
        builder.append("RequestedSamplingPeriod=")
                .append(mEnvironment.getRequestedSamplingPeriodUs()).append("us, ");
        builder.append("MaxReportLatency=")
                .append(mEnvironment.getMaxReportLatencyUs()).append("us, ");
        builder.append("StartedTimestamp=")
                .append(testStartTimeMs).append("ms, ");
        builder.append("StoppedTimestamp=")
                .append(testStopTimeMs).append("ms");
        synchronized (mCollectedEvents) {
            int i = 0, j = 0;
            while (i < mCollectedEvents.size() && j < mTimeStampFlushCompleteEvents.size()) {
                if (mCollectedEvents.get(i).receivedTimestamp <
                        mTimeStampFlushCompleteEvents.get(j)) {
                    TestSensorEvent event = mCollectedEvents.get(i);
                    if (deviceWakeUpTimeMs != -1 && deviceWakeUpTimeMs <
                            event.receivedTimestamp/1000000) {
                        builder.append("\n");
                        builder.append("AP wake-up time=").append(deviceWakeUpTimeMs).append("ms");
                        deviceWakeUpTimeMs = -1;
                    }
                    builder.append("\n");
                    builder.append("Timestamp=").append(event.timestamp/1000).append("us, ");
                    builder.append("ReceivedTimestamp=").append(event.receivedTimestamp/1000).
                        append("us, ");
                    builder.append("Accuracy=").append(event.accuracy).append(", ");
                    builder.append("Values=").append(Arrays.toString(event.values));
                    ++i;
                } else {
                    builder.append("\n");
                    builder.append("ReceivedTimestamp=")
                    .append(mTimeStampFlushCompleteEvents.get(j)/1000)
                    .append("us Flush complete Event");
                    ++j;
                }
            }
            for (;i < mCollectedEvents.size(); ++i) {
                TestSensorEvent event = mCollectedEvents.get(i);
                if (deviceWakeUpTimeMs != -1 && deviceWakeUpTimeMs <
                        event.receivedTimestamp/1000000) {
                    builder.append("\n");
                    builder.append("AP wake-up time=").append(deviceWakeUpTimeMs).append("ms");
                    deviceWakeUpTimeMs = -1;
                }
                builder.append("\n");
                builder.append("Timestamp=").append(event.timestamp/1000).append("us, ");
                builder.append("ReceivedTimestamp=").append(event.receivedTimestamp/1000).
                    append("us, ");
                builder.append("Accuracy=").append(event.accuracy).append(", ");
                builder.append("Values=").append(Arrays.toString(event.values));
            }
            for (;j < mTimeStampFlushCompleteEvents.size(); ++j) {
                builder.append("\n");
                builder.append("ReceivedTimestamp=")
                    .append(mTimeStampFlushCompleteEvents.get(j)/1000)
                    .append("us Flush complete Event");
            }
        }

        File eventsDirectory = SensorCtsHelper.getSensorTestDataDirectory("events/");
        File logFile = new File(eventsDirectory, fileName);
        FileWriter fileWriter = new FileWriter(logFile, false /* append */);
        try (BufferedWriter writer = new BufferedWriter(fileWriter)) {
            writer.write(builder.toString());
        }
    }

    /**
     * Wait for {@link #onFlushCompleted(Sensor)} to be called.
     *
     * A wake lock may be acquired at the return if operation is successful. Do
     * {@link releaseWakeLock()} if the wakelock is not necessary.
     *
     * @throws AssertionError if there was a timeout after {@link #FLUSH_TIMEOUT_US} &micro;s
     */
    public void waitForFlushComplete(CountDownLatch latch,
                                      boolean clearCollectedEvents) throws InterruptedException {
        if (clearCollectedEvents) {
            clearEvents();
        }
        try {
            String message = SensorCtsHelper.formatAssertionMessage(
                    "WaitForFlush",
                    mEnvironment,
                    "timeout=%dus",
                    FLUSH_TIMEOUT_US);
            Assert.assertTrue(message, latch.await(FLUSH_TIMEOUT_US, TimeUnit.MICROSECONDS));
        } finally {
            synchronized (mFlushLatches) {
                mFlushLatches.remove(latch);
            }
        }
    }

    /**
     * Collect a specific number of {@link TestSensorEvent}s.
     *
     * A wake lock may be acquired at the return if operation is successful. Do
     * {@link releaseWakeLock()} if the wakelock is not necessary.
     *
     * @throws AssertionError if there was a timeout after {@link #FLUSH_TIMEOUT_US} &micro;s
     */
    public void waitForEvents(CountDownLatch latch, int eventCount,
                               boolean clearCollectedEvents) throws InterruptedException {
        if (clearCollectedEvents) {
            clearEvents();
        }
        try {
            long samplingPeriodUs = mEnvironment.getMaximumExpectedSamplingPeriodUs();
            // timeout is 2 * event count * expected period + batch timeout + default wait
            // we multiply by two as not to raise an error in this function even if the events are
            // streaming at a lower rate than expected, as long as it's not streaming twice as slow
            // as expected
            long timeoutUs = (2 * eventCount * samplingPeriodUs)
                    + mEnvironment.getMaxReportLatencyUs()
                    + EVENT_TIMEOUT_US;
            boolean success = latch.await(timeoutUs, TimeUnit.MICROSECONDS);
            if (!success) {
                String message = SensorCtsHelper.formatAssertionMessage(
                        "WaitForEvents",
                        mEnvironment,
                        "requested=%d, received=%d, timeout=%dus",
                        eventCount,
                        eventCount - latch.getCount(),
                        timeoutUs);
                Assert.fail(message);
            }
        } finally {
            synchronized (mEventLatches) {
                mEventLatches.remove(latch);
            }
        }
    }

    /**
     * Collect {@link TestSensorEvent} for a specific duration.
     */
    public void waitForEvents(long duration, TimeUnit timeUnit) throws InterruptedException {
        SensorCtsHelper.sleep(duration, timeUnit);
    }

    /**
     * Asserts that sensor events arrived in the proper thread if a {@link Handler} was associated
     * with the current instance.
     *
     * If no events were received this assertion will be evaluated to {@code true}.
     */
    public void assertEventsReceivedInHandler() {
        int eventsOutsideHandler = mEventsReceivedOutsideHandler.get();
        String message = String.format(
                "Events arrived outside the associated Looper. Expected=0, Found=%d",
                eventsOutsideHandler);
        Assert.assertEquals(message, 0 /* expected */, eventsOutsideHandler);
    }

    public void releaseWakeLock() {
        if (mTestSensorEventListenerWakeLock.isHeld()) {
            mTestSensorEventListenerWakeLock.release();
        }
    }

    /**
     * Keeps track of the number of events that arrived in a different {@link Looper} than the one
     * associated with the {@link TestSensorEventListener}.
     */
    private void checkHandler() {
        if (mHandler != null && mHandler.getLooper() != Looper.myLooper()) {
            mEventsReceivedOutsideHandler.incrementAndGet();
        }
    }
}
