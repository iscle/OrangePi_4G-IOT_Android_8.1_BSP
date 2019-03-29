/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.hardware.cts;

import junit.framework.Assert;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorNotSupportedException;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEventListener;
import android.hardware.cts.helpers.TestSensorManager;
import android.hardware.cts.helpers.sensoroperations.ParallelSensorOperation;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.EventGapVerification;
import android.hardware.cts.helpers.sensorverification.EventOrderingVerification;
import android.hardware.cts.helpers.sensorverification.EventTimestampSynchronizationVerification;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SensorTest extends SensorTestCase {
    private static final String TAG = "SensorTest";

    // Test only SDK defined sensors. Any sensors with type > 100 are ignored.
    private static final int MAX_OFFICIAL_ANDROID_SENSOR_TYPE = 100;

    private PowerManager.WakeLock mWakeLock;
    private SensorManager mSensorManager;
    private TestSensorManager mTestSensorManager;
    private NullTriggerEventListener mNullTriggerEventListener;
    private NullSensorEventListener mNullSensorEventListener;
    private Sensor mTriggerSensor;
    private List<Sensor> mSensorList;
    private List<Sensor> mAndroidSensorList;

    @Override
    protected void setUp() throws Exception {
        Context context = getContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mNullTriggerEventListener = new NullTriggerEventListener();
        mNullSensorEventListener = new NullSensorEventListener();

        mSensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        assertNotNull("SensorList was null.", mSensorList);
        if (mSensorList.isEmpty()) {
            // several devices will not have sensors, so we need to skip the tests in those cases
            throw new SensorTestStateNotSupportedException(
                    "Sensors are not available in the system.");
        }

        mAndroidSensorList = new ArrayList<>();
        for (Sensor s : mSensorList) {
            if (s.getType() < Sensor.TYPE_DEVICE_PRIVATE_BASE) {
                mAndroidSensorList.add(s);
            }
        }

        mWakeLock.acquire();
    }

    @Override
    protected void tearDown() {
        if (mSensorManager != null) {
            // SensorManager will check listener and status, so just unregister listener
            mSensorManager.unregisterListener(mNullSensorEventListener);
            if (mTriggerSensor != null) {
                mSensorManager.cancelTriggerSensor(mNullTriggerEventListener, mTriggerSensor);
                mTriggerSensor = null;
            }
        }

        if (mTestSensorManager != null) {
            mTestSensorManager.unregisterListener();
            mTestSensorManager = null;
        }

        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @SuppressWarnings("deprecation")
    public void testSensorOperations() {
        // Because we can't know every sensors unit details, so we can't assert
        // get values with specified values.
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        boolean hasAccelerometer = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SENSOR_ACCELEROMETER);
        // accelerometer sensor is optional
        if (hasAccelerometer) {
            assertEquals(Sensor.TYPE_ACCELEROMETER, sensor.getType());
            assertSensorValues(sensor);
        } else {
            assertNull(sensor);
        }

        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        boolean hasStepCounter = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SENSOR_STEP_COUNTER);
        // stepcounter sensor is optional
        if (hasStepCounter) {
            assertEquals(Sensor.TYPE_STEP_COUNTER, sensor.getType());
            assertSensorValues(sensor);
        } else {
            assertNull(sensor);
        }

        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        boolean hasStepDetector = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SENSOR_STEP_DETECTOR);
        // stepdetector sensor is optional
        if (hasStepDetector) {
            assertEquals(Sensor.TYPE_STEP_DETECTOR, sensor.getType());
            assertSensorValues(sensor);
        } else {
            assertNull(sensor);
        }

        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        boolean hasHeartRate = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SENSOR_HEART_RATE);
        // heartrate sensor is optional
        if (hasHeartRate) {
            assertEquals(Sensor.TYPE_HEART_RATE, sensor.getType());
            assertSensorValues(sensor);
        } else {
            assertNull(sensor);
        }

        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        boolean hasCompass = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SENSOR_COMPASS);
        // compass sensor is optional
        if (hasCompass) {
            assertEquals(Sensor.TYPE_MAGNETIC_FIELD, sensor.getType());
            assertSensorValues(sensor);
        } else {
            assertNull(sensor);
        }

        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        // Note: orientation sensor is deprecated.
        if (sensor != null) {
            assertEquals(Sensor.TYPE_ORIENTATION, sensor.getType());
            assertSensorValues(sensor);
        }

        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE);
        // temperature sensor is optional
        if (sensor != null) {
            assertEquals(Sensor.TYPE_TEMPERATURE, sensor.getType());
            assertSensorValues(sensor);
        }
    }

    public void testValuesForAllSensors() {
        for (Sensor sensor : mSensorList) {
            assertSensorValues(sensor);
        }
    }

    private void hasOnlyOneWakeUpSensorOrEmpty(List<Sensor> sensors) {
        if (sensors == null || sensors.isEmpty()) return;
        if (sensors.size() > 1) {
            fail("More than one " + sensors.get(0).getName() + " defined.");
            return;
        }
        assertTrue(sensors.get(0).getName() + " defined as non-wake-up sensor",
                sensors.get(0).isWakeUpSensor());
    }

    // Some sensors like proximity, significant motion etc. are defined as wake-up sensors by
    // default. Check if the wake-up flag is set correctly.
    @Presubmit
    public void testWakeUpFlags() {
        final int TYPE_WAKE_GESTURE = 23;
        final int TYPE_GLANCE_GESTURE = 24;
        final int TYPE_PICK_UP_GESTURE = 25;

        hasOnlyOneWakeUpSensorOrEmpty(mSensorManager.getSensorList(Sensor.TYPE_SIGNIFICANT_MOTION));
        hasOnlyOneWakeUpSensorOrEmpty(mSensorManager.getSensorList(TYPE_WAKE_GESTURE));
        hasOnlyOneWakeUpSensorOrEmpty(mSensorManager.getSensorList(TYPE_GLANCE_GESTURE));
        hasOnlyOneWakeUpSensorOrEmpty(mSensorManager.getSensorList(TYPE_PICK_UP_GESTURE));

        List<Sensor> proximity_sensors = mSensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
        if (proximity_sensors.isEmpty()) return;
        boolean hasWakeUpProximitySensor = false;
        for (Sensor sensor : proximity_sensors) {
            if (sensor.isWakeUpSensor()) {
                hasWakeUpProximitySensor = true;
                break;
            }
        }
        assertTrue("No wake-up proximity sensors implemented", hasWakeUpProximitySensor);
    }

    public void testGetDefaultSensorWithWakeUpFlag() {
        // With wake-up flags set to false, the sensor returned should be a non wake-up sensor.
        for (Sensor sensor : mSensorList) {
            Sensor curr_sensor = mSensorManager.getDefaultSensor(sensor.getType(), false);
            if (curr_sensor != null) {
                assertFalse("getDefaultSensor wakeup=false returns a wake-up sensor" +
                        curr_sensor.getName(),
                        curr_sensor.isWakeUpSensor());
            }

            curr_sensor = mSensorManager.getDefaultSensor(sensor.getType(), true);
            if (curr_sensor != null) {
                assertTrue("getDefaultSensor wake-up returns non wake sensor" +
                        curr_sensor.getName(),
                        curr_sensor.isWakeUpSensor());
            }
        }
    }

    @Presubmit
    public void testSensorStringTypes() {
        for (Sensor sensor : mSensorList) {
            if (sensor.getType() < MAX_OFFICIAL_ANDROID_SENSOR_TYPE &&
                    !sensor.getStringType().startsWith("android.sensor.")) {
                fail("StringType not set correctly for android defined sensor " +
                        sensor.getName() + " " + sensor.getStringType());
            }
        }
    }

    public void testRequestTriggerWithNonTriggerSensor() {
        mTriggerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mTriggerSensor == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER);
        }
        boolean  result =
            mSensorManager.requestTriggerSensor(mNullTriggerEventListener, mTriggerSensor);
        assertFalse(result);
    }

    public void testCancelTriggerWithNonTriggerSensor() {
        mTriggerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mTriggerSensor == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER);
        }
        boolean result =
            mSensorManager.cancelTriggerSensor(mNullTriggerEventListener, mTriggerSensor);
        assertFalse(result);
    }

    public void testRegisterWithTriggerSensor() {
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        if (sensor == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_SIGNIFICANT_MOTION);
        }
        boolean result = mSensorManager.registerListener(
                mNullSensorEventListener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        assertFalse(result);
    }

    public void testRegisterTwiceWithSameSensor() {
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER);
        }

        boolean result = mSensorManager.registerListener(mNullSensorEventListener, sensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        assertTrue(result);

        result = mSensorManager.registerListener(mNullSensorEventListener, sensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        assertFalse(result);
    }

    // TODO: remove when parametized tests are supported and EventTimestampSynchronization
    //       verification is added to default verifications
    public void testSensorTimeStamps() throws Exception {
        ArrayList<Throwable> errorsFound = new ArrayList<>();
        for (Sensor sensor : mAndroidSensorList) {
            // test both continuous and batching mode sensors
            verifyLongActivation(sensor, 0 /* maxReportLatencyUs */, errorsFound);
            verifyLongActivation(sensor, (int) TimeUnit.SECONDS.toMicros(10), errorsFound);
        }
        assertOnErrors(errorsFound);
    }

    // TODO: remove when parameterized tests are supported (see SensorBatchingTests.java)
    public void testBatchAndFlush() throws Exception {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        ArrayList<Throwable> errorsFound = new ArrayList<>();
        for (Sensor sensor : mAndroidSensorList) {
            verifyRegisterListenerCallFlush(sensor, null /* handler */, errorsFound);
        }
        assertOnErrors(errorsFound);
    }

    /**
     * Verifies that sensor events arrive in the given message queue (Handler).
     */
    public void testBatchAndFlushWithHandler() throws Exception {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        Sensor sensor = null;
        for (Sensor s : mAndroidSensorList) {
            if (s.getReportingMode() == Sensor.REPORTING_MODE_CONTINUOUS) {
                sensor = s;
                break;
            }
        }
        if (sensor == null) {
            throw new SensorTestStateNotSupportedException(
                    "There are no Continuous sensors in the device.");
        }

        TestSensorEnvironment environment = new TestSensorEnvironment(
                getContext(),
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                (int) TimeUnit.SECONDS.toMicros(5));
        mTestSensorManager = new TestSensorManager(environment);

        HandlerThread handlerThread = new HandlerThread("sensorThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        TestSensorEventListener listener = new TestSensorEventListener(environment, handler);

        CountDownLatch eventLatch = mTestSensorManager.registerListener(listener, 1);
        listener.waitForEvents(eventLatch, 1, true);
        CountDownLatch flushLatch = mTestSensorManager.requestFlush();
        listener.waitForFlushComplete(flushLatch, true);
        listener.assertEventsReceivedInHandler();
    }

    /**
     *  Explicit testing the SensorManager.registerListener(SensorEventListener, Sensor, int, int).
     */
    public void testBatchAndFlushUseDefaultHandler() throws Exception {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        Sensor sensor = null;
        for (Sensor s : mAndroidSensorList) {
            if (s.getReportingMode() == Sensor.REPORTING_MODE_CONTINUOUS) {
                sensor = s;
                break;
            }
        }
        if (sensor == null) {
            throw new SensorTestStateNotSupportedException(
                    "There are no Continuous sensors in the device.");
        }

        TestSensorEnvironment environment = new TestSensorEnvironment(
                getContext(),
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                (int) TimeUnit.SECONDS.toMicros(5));
        mTestSensorManager = new TestSensorManager(environment);

        TestSensorEventListener listener = new TestSensorEventListener(environment, null);

        // specifyHandler <= false, use the SensorManager API without Handler parameter
        CountDownLatch eventLatch = mTestSensorManager.registerListener(listener, 1, false);
        listener.waitForEvents(eventLatch, 1, true);
        CountDownLatch flushLatch = mTestSensorManager.requestFlush();
        listener.waitForFlushComplete(flushLatch, true);
        listener.assertEventsReceivedInHandler();
    }

    // TODO: after L release move to SensorBatchingTests and run in all sensors with default
    //       verifications enabled
    public void testBatchAndFlushWithMultipleSensors() throws Exception {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        final int maxSensors = 3;
        final int maxReportLatencyUs = (int) TimeUnit.SECONDS.toMicros(10);
        List<Sensor> sensorsToTest = new ArrayList<Sensor>();
        for (Sensor sensor : mAndroidSensorList) {
            if (sensor.getReportingMode() == Sensor.REPORTING_MODE_CONTINUOUS) {
                sensorsToTest.add(sensor);
                if (sensorsToTest.size()  == maxSensors) break;
            }
        }
        final int numSensorsToTest = sensorsToTest.size();
        if (numSensorsToTest == 0) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        ParallelSensorOperation parallelSensorOperation = new ParallelSensorOperation();
        for (Sensor sensor : sensorsToTest) {
            TestSensorEnvironment environment = new TestSensorEnvironment(
                    getContext(),
                    sensor,
                    shouldEmulateSensorUnderLoad(),
                    SensorManager.SENSOR_DELAY_FASTEST,
                    maxReportLatencyUs);
            FlushExecutor executor = new FlushExecutor(environment, 500 /* eventCount */);
            parallelSensorOperation.add(new TestSensorOperation(environment, executor));
            builder.append(sensor.getName()).append(", ");
        }

        Log.i(TAG, "Testing batch/flush for sensors: " + builder);
        parallelSensorOperation.execute(getCurrentTestNode());
    }

    private void assertSensorValues(Sensor sensor) {
        assertTrue("Max range must be positive. Range=" + sensor.getMaximumRange()
                + " " + sensor.getName(), sensor.getMaximumRange() >= 0);
        assertTrue("Max power must be positive. Power=" + sensor.getPower() + " " +
                sensor.getName(), sensor.getPower() >= 0);
        assertTrue("Max resolution must be positive. Resolution=" + sensor.getResolution() +
                " " + sensor.getName(), sensor.getResolution() >= 0);
        assertNotNull("Vendor name must not be null " + sensor.getName(), sensor.getVendor());
        assertTrue("Version must be positive version=" + sensor.getVersion() + " " +
                sensor.getName(), sensor.getVersion() > 0);
        int fifoMaxEventCount = sensor.getFifoMaxEventCount();
        int fifoReservedEventCount = sensor.getFifoReservedEventCount();
        assertTrue(fifoMaxEventCount >= 0);
        assertTrue(fifoReservedEventCount >= 0);
        assertTrue(fifoReservedEventCount <= fifoMaxEventCount);
        if (sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
            assertTrue("One shot sensors should have zero FIFO Size " + sensor.getName(),
                    sensor.getFifoMaxEventCount() == 0);
            assertTrue("One shot sensors should have zero FIFO Size "  + sensor.getName(),
                    sensor.getFifoReservedEventCount() == 0);
        }
    }

    @SuppressWarnings("deprecation")
    public void testLegacySensorOperations() {
        final SensorManager mSensorManager =
                (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);

        // We expect the set of sensors reported by the new and legacy APIs to be consistent.
        int sensors = 0;
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensors |= SensorManager.SENSOR_ACCELEROMETER;
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            sensors |= SensorManager.SENSOR_MAGNETIC_FIELD;
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) != null) {
            sensors |= SensorManager.SENSOR_ORIENTATION | SensorManager.SENSOR_ORIENTATION_RAW;
        }
        assertEquals(sensors, mSensorManager.getSensors());
    }

    /**
     * Verifies that a continuous sensor produces events that have timestamps synchronized with
     * {@link SystemClock#elapsedRealtimeNanos()}.
     */
    private void verifyLongActivation(
            Sensor sensor,
            int maxReportLatencyUs,
            ArrayList<Throwable> errorsFound) throws InterruptedException {
        if (sensor.getReportingMode() != Sensor.REPORTING_MODE_CONTINUOUS) {
            return;
        }

        try {
            TestSensorEnvironment environment = new TestSensorEnvironment(
                    getContext(),
                    sensor,
                    shouldEmulateSensorUnderLoad(),
                    SensorManager.SENSOR_DELAY_FASTEST,
                    maxReportLatencyUs);
            TestSensorOperation operation =
                    TestSensorOperation.createOperation(environment, 20, TimeUnit.SECONDS);
            operation.addVerification(EventGapVerification.getDefault(environment));
            operation.addVerification(EventOrderingVerification.getDefault(environment));
            operation.addVerification(
                    EventTimestampSynchronizationVerification.getDefault(environment));

            Log.i(TAG, "Running timestamp test on: " + sensor.getName());
            operation.execute(getCurrentTestNode());
        } catch (InterruptedException e) {
            // propagate so the test can stop
            throw e;
        } catch (Throwable e) {
            errorsFound.add(e);
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Verifies that a client can listen for events, and that
     * {@link SensorManager#flush(SensorEventListener)} will trigger the appropriate notification
     * for {@link SensorEventListener2#onFlushCompleted(Sensor)}.
     */
    private void verifyRegisterListenerCallFlush(
            Sensor sensor,
            Handler handler,
            ArrayList<Throwable> errorsFound)
            throws InterruptedException {
        if (sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
            return;
        }

        try {
            TestSensorEnvironment environment = new TestSensorEnvironment(
                    getContext(),
                    sensor,
                    shouldEmulateSensorUnderLoad(),
                    SensorManager.SENSOR_DELAY_FASTEST,
                    (int) TimeUnit.SECONDS.toMicros(10));
            FlushExecutor executor = new FlushExecutor(environment, 500 /* eventCount */);
            TestSensorOperation operation = new TestSensorOperation(environment, executor, handler);

            Log.i(TAG, "Running flush test on: " + sensor.getName());
            operation.execute(getCurrentTestNode());
        } catch (InterruptedException e) {
            // propagate so the test can stop
            throw e;
        } catch (Throwable e) {
            errorsFound.add(e);
            Log.e(TAG, e.getMessage());
        }
    }

    private void assertOnErrors(List<Throwable> errorsFound) {
        if (!errorsFound.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Throwable error : errorsFound) {
                builder.append(error.getMessage()).append("\n");
            }
            Assert.fail(builder.toString());
        }
    }

    /**
     * A delegate that drives the execution of Batch/Flush tests.
     * It performs several operations in order:
     * - registration
     * - for continuous sensors it first ensures that the FIFO is filled
     *      - if events do not arrive on time, an assert will be triggered
     * - requests flush of sensor data
     * - waits for {@link SensorEventListener2#onFlushCompleted(Sensor)}
     *      - if the event does not arrive, an assert will be triggered
     */
    private class FlushExecutor implements TestSensorOperation.Executor {
        private final TestSensorEnvironment mEnvironment;
        private final int mEventCount;

        public FlushExecutor(TestSensorEnvironment environment, int eventCount) {
            mEnvironment = environment;
            mEventCount = eventCount;
        }

        /**
         * Consider only continuous mode sensors for testing register listener.
         *
         * For on-change sensors, we only use
         * {@link TestSensorManager#registerListener(TestSensorEventListener)} to associate the
         * listener with the sensor. So that {@link TestSensorManager#requestFlush()} can be
         * invoked on it.
         */
        @Override
        public void execute(TestSensorManager sensorManager, TestSensorEventListener listener)
                throws InterruptedException {
            int sensorReportingMode = mEnvironment.getSensor().getReportingMode();
            try {
                CountDownLatch eventLatch = sensorManager.registerListener(listener, mEventCount);
                if (sensorReportingMode == Sensor.REPORTING_MODE_CONTINUOUS) {
                    listener.waitForEvents(eventLatch, mEventCount, true);
                }
                CountDownLatch flushLatch = sensorManager.requestFlush();
                listener.waitForFlushComplete(flushLatch, true);
            } finally {
                sensorManager.unregisterListener();
            }
        }
    }

    private class NullTriggerEventListener extends TriggerEventListener {
        @Override
        public void onTrigger(TriggerEvent event) {}
    }

    private class NullSensorEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {}

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

}
