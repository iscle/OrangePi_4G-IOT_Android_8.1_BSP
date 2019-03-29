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
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

/**
 * A test class that performs the actions of {@link SensorManager} on a single sensor.
 * This class allows for a single sensor to be registered and unregistered as well as performing
 * operations such as flushing the sensor events and gathering events.
 * This class also manages performing the test verifications for the sensor manager.
 *
 * NOTE: this class is expected to mirror {@link SensorManager} operations, and perform the
 * required test verifications along with them.
 */
public class TestSensorManager {
    private static final String LOG_TAG = "TestSensorManager";

    private final SensorManager mSensorManager;
    private final TestSensorEnvironment mEnvironment;

    private volatile TestSensorEventListener mTestSensorEventListener;

    /**
     * @deprecated Use {@link #TestSensorManager(TestSensorEnvironment)} instead.
     */
    @Deprecated
    public TestSensorManager(
            Context context,
            int sensorType,
            int rateUs,
            int maxBatchReportLatencyUs) {
        this(new TestSensorEnvironment(context, sensorType, rateUs, maxBatchReportLatencyUs));
    }

    /**
     * Construct a {@link TestSensorManager}.
     */
    public TestSensorManager(TestSensorEnvironment environment) {
        mSensorManager =
                (SensorManager) environment.getContext().getSystemService(Context.SENSOR_SERVICE);
        mEnvironment = environment;
    }

    /**
     * Register the listener. This method will perform a no-op if the sensor is already registered.
     *
     * @throws AssertionError if there was an error registering the listener with the
     * {@link SensorManager}
     */
    public void registerListener(TestSensorEventListener listener) {
        if (mTestSensorEventListener != null) {
            Log.w(LOG_TAG, "Listener already registered, returning.");
            return;
        }

        mTestSensorEventListener = listener;
        String message = SensorCtsHelper.formatAssertionMessage("registerListener", mEnvironment);

        boolean result = mSensorManager.registerListener(
                mTestSensorEventListener,
                mEnvironment.getSensor(),
                mEnvironment.getRequestedSamplingPeriodUs(),
                mEnvironment.getMaxReportLatencyUs(),
                mTestSensorEventListener.getHandler());
        Assert.assertTrue(message, result);
    }

    /**
     * Register the listener. This method will perform a no-op if the sensor is already registered.
     *
     * @return A CountDownLatch initialized with eventCount which is used to wait for sensor
     * events.
     * @throws AssertionError if there was an error registering the listener with the
     * {@link SensorManager}
     */
    public CountDownLatch registerListener(
            TestSensorEventListener listener,
            int eventCount,
            boolean specifyHandler) {
        if (mTestSensorEventListener != null) {
            Log.w(LOG_TAG, "Listener already registered, returning.");
            return null;
        }

        CountDownLatch latch = listener.getLatchForSensorEvents(eventCount);
        mTestSensorEventListener = listener;
        String message = SensorCtsHelper.formatAssertionMessage("registerListener", mEnvironment);

        boolean result;
        if (specifyHandler) {
            result = mSensorManager.registerListener(
                    mTestSensorEventListener,
                    mEnvironment.getSensor(),
                    mEnvironment.getRequestedSamplingPeriodUs(),
                    mEnvironment.getMaxReportLatencyUs(),
                    mTestSensorEventListener.getHandler());
        } else {
            result = mSensorManager.registerListener(
                    mTestSensorEventListener,
                    mEnvironment.getSensor(),
                    mEnvironment.getRequestedSamplingPeriodUs(),
                    mEnvironment.getMaxReportLatencyUs());
        }
        Assert.assertTrue(message, result);
        return latch;
    }

    /**
     * Register the listener. This method will perform a no-op if the sensor is already registered.
     *
     * @return A CountDownLatch initialized with eventCount which is used to wait for sensor
     * events.
     * @throws AssertionError if there was an error registering the listener with the
     * {@link SensorManager}
     */
    public CountDownLatch registerListener(
            TestSensorEventListener listener,
            int eventCount) {
        return registerListener(listener, eventCount, true);
    }

    /**
     * Unregister the listener. This method will perform a no-op if the sensor is not registered.
     */
    public void unregisterListener() {
        if (mTestSensorEventListener == null) {
            Log.w(LOG_TAG, "No listener registered, returning.");
            return;
        }
        mSensorManager.unregisterListener(mTestSensorEventListener, mEnvironment.getSensor());
        mTestSensorEventListener.assertEventsReceivedInHandler();
        mTestSensorEventListener.releaseWakeLock(); // clean up wakelock if it is acquired
        mTestSensorEventListener = null;
    }

    /**
     * Call {@link SensorManager#flush(SensorEventListener)}. This method will perform a no-op if
     * the sensor is not registered.
     *
     * @return A CountDownLatch which can be used to wait for a flush complete event.
     * @throws AssertionError if {@link SensorManager#flush(SensorEventListener)} fails.
     */
    public CountDownLatch requestFlush() {
        if (mTestSensorEventListener == null) {
            Log.w(LOG_TAG, "No listener registered, returning.");
            return null;
        }
        CountDownLatch latch = mTestSensorEventListener.getLatchForFlushCompleteEvent();
        Assert.assertTrue(
                SensorCtsHelper.formatAssertionMessage("Flush", mEnvironment),
                mSensorManager.flush(mTestSensorEventListener));
        return latch;
    }
}
