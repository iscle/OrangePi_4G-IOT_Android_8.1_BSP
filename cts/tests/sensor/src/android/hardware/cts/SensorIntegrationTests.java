/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.ParallelSensorOperation;
import android.hardware.cts.helpers.sensoroperations.RepeatingSensorOperation;
import android.hardware.cts.helpers.sensoroperations.SequentialSensorOperation;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.EventOrderingVerification;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Set of tests that verifies proper interaction of the sensors in the platform.
 *
 * To execute these test cases, the following command can be used:
 *      $ adb shell am instrument -e class android.hardware.cts.SensorIntegrationTests \
 *          -w android.hardware.cts/android.test.InstrumentationCtsTestRunner
 */
public class SensorIntegrationTests extends SensorTestCase {
    private static final String TAG = "SensorIntegrationTests";

    /**
     * This test focuses in the interaction of continuous and batching clients for the same Sensor
     * under test. The verification ensures that sensor clients can interact with the System and
     * not affect other clients in the way.
     *
     * The test verifies for each client that the a set of sampled data arrives in order. However
     * each client in the test has different set of parameters that represent different types of
     * clients in the real world.
     *
     * A test failure might indicate that the HAL implementation does not respect the assumption
     * that the sensors must be independent. Activating one sensor should not cause another sensor
     * to deactivate or to change behavior.
     * It is however, acceptable that when a client is activated at a higher sampling rate, it would
     * cause other clients to receive data at a faster sampling rate. A client causing other clients
     * to receive data at a lower sampling rate is, however, not acceptable.
     *
     * The assertion associated with the test failure provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the event that caused the issue
     * It is important to look at the internals of the Sensor HAL to identify how the interaction
     * of several clients can lead to the failing state.
     */
    public void testSensorsWithSeveralClients() throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        final int ITERATIONS = 50;
        final int MAX_REPORTING_LATENCY_US = (int) TimeUnit.SECONDS.toMicros(5);
        final Context context = getContext();

        int sensorTypes[] = {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_GYROSCOPE };

        ParallelSensorOperation operation = new ParallelSensorOperation();
        for(int sensorType : sensorTypes) {
            TestSensorEnvironment environment = new TestSensorEnvironment(
                    context,
                    sensorType,
                    shouldEmulateSensorUnderLoad(),
                    SensorManager.SENSOR_DELAY_FASTEST);
            TestSensorOperation continuousOperation =
                    TestSensorOperation.createOperation(environment, 100 /* eventCount */);
            continuousOperation.addVerification(new EventOrderingVerification());
            operation.add(new RepeatingSensorOperation(continuousOperation, ITERATIONS));

            Sensor sensor = TestSensorEnvironment.getSensor(context, sensorType);
            TestSensorEnvironment batchingEnvironment = new TestSensorEnvironment(
                    context,
                    sensorType,
                    shouldEmulateSensorUnderLoad(),
                    true, /* isIntegrationTest */
                    sensor.getMinDelay(),
                    MAX_REPORTING_LATENCY_US);
            TestSensorOperation batchingOperation =
                    TestSensorOperation.createOperation(batchingEnvironment, 100 /* eventCount */);
            batchingOperation.addVerification(new EventOrderingVerification());
            operation.add(new RepeatingSensorOperation(batchingOperation, ITERATIONS));
        }
        operation.execute(getCurrentTestNode());
        operation.getStats().log(TAG);
    }

    /**
     * This test focuses in the interaction of several sensor Clients. The test characterizes by
     * using clients for different Sensors under Test that vary the sampling rates and report
     * latencies for the requests.
     * The verification ensures that the sensor clients can vary the parameters of their requests
     * without affecting other clients.
     *
     * The test verifies for each client that a set of sampled data arrives in order. However each
     * client in the test has different set of parameters that represent different types of clients
     * in the real world.
     *
     * The test can be susceptible to issues when several clients interacting with the system
     * actually affect the operation of other clients.
     *
     * The assertion associated with the test failure provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the event that caused the issue
     * It is important to look at the internals of the Sensor HAL to identify how the interaction
     * of several clients can lead to the failing state.
     */
    public void testSensorsMovingRates() throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        // use at least two instances to ensure more than one client of any given sensor is in play
        final int INSTANCES_TO_USE = 5;
        final int ITERATIONS_TO_EXECUTE = 100;

        ParallelSensorOperation operation = new ParallelSensorOperation();
        int sensorTypes[] = {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_GYROSCOPE };

        Context context = getContext();
        for(int sensorType : sensorTypes) {
            for(int instance = 0; instance < INSTANCES_TO_USE; ++instance) {
                SequentialSensorOperation sequentialOperation = new SequentialSensorOperation();
                for(int iteration = 0; iteration < ITERATIONS_TO_EXECUTE; ++iteration) {
                    TestSensorEnvironment environment = new TestSensorEnvironment(
                            context,
                            sensorType,
                            shouldEmulateSensorUnderLoad(),
                            true, /* isIntegrationTest */
                            generateSamplingRateInUs(sensorType),
                            generateReportLatencyInUs());
                    TestSensorOperation sensorOperation =
                            TestSensorOperation.createOperation(environment, 100 /* eventCount */);
                    sensorOperation.addVerification(new EventOrderingVerification());
                    sequentialOperation.add(sensorOperation);
                }
                operation.add(sequentialOperation);
            }
        }

        operation.execute(getCurrentTestNode());
        operation.getStats().log(TAG);
    }

    /**
     * Regress:
     * - b/10641388
     */

    public void testAccelerometerAccelerometerStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER);
    }

    public void testAccelerometerGyroscopeStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE);
    }

    public void testAccelerometerMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testGyroscopeAccelerometerStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER);
    }

    public void testGyroscopeGyroscopeStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE);
    }

    public void testGyroscopeMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testMagneticFieldAccelerometerStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ACCELEROMETER);
    }

    public void testMagneticFieldGyroscopeStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GYROSCOPE);
    }

    public void testMagneticFieldMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD);
    }

    /**
     * This test verifies that starting/stopping a particular Sensor client in the System does not
     * affect other sensor clients.
     * the test is used to validate that starting/stopping operations are independent on several
     * sensor clients.
     *
     * The test verifies for each client that the a set of sampled data arrives in order. However
     * each client in the test has different set of parameters that represent different types of
     * clients in the real world.
     *
     * The test can be susceptible to issues when several clients interacting with the system
     * actually affect the operation of other clients.
     *
     * The assertion associated with the test failure provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the event that caused the issue
     * It is important to look at the internals of the Sensor HAL to identify how the interaction
     * of several clients can lead to the failing state.
     */
    public void verifySensorStoppingInteraction(
            int sensorTypeTestee,
            int sensorTypeTester) throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        Context context = getContext();

        TestSensorEnvironment testerEnvironment = new TestSensorEnvironment(
                context,
                sensorTypeTester,
                shouldEmulateSensorUnderLoad(),
                SensorManager.SENSOR_DELAY_FASTEST);
        TestSensorOperation tester =
                TestSensorOperation.createOperation(testerEnvironment, 100 /* event count */);
        tester.addVerification(new EventOrderingVerification());

        TestSensorEnvironment testeeEnvironment = new TestSensorEnvironment(
                context,
                sensorTypeTestee,
                shouldEmulateSensorUnderLoad(),
                SensorManager.SENSOR_DELAY_FASTEST);
        TestSensorOperation testee =
                TestSensorOperation.createOperation(testeeEnvironment, 100 /* event count */);
        testee.addVerification(new EventOrderingVerification());

        ParallelSensorOperation operation = new ParallelSensorOperation();
        operation.add(tester, testee);
        operation.execute(getCurrentTestNode());
        operation.getStats().log(TAG);

        testee = testee.clone();
        testee.execute(getCurrentTestNode());
        testee.getStats().log(TAG);
    }

    /**
     * Private helpers.
     */
    private final Random mGenerator = new Random();

    private int generateSamplingRateInUs(int sensorType) {
        int rate;
        switch(mGenerator.nextInt(5)) {
            case 0:
                rate = SensorManager.SENSOR_DELAY_FASTEST;
                break;
            default:
                Sensor sensor = TestSensorEnvironment.getSensor(getContext(), sensorType);
                int maxSamplingRate = sensor.getMinDelay();
                rate = maxSamplingRate * mGenerator.nextInt(10);
        }
        return rate;
    }

    private int generateReportLatencyInUs() {
        long reportLatencyUs = TimeUnit.SECONDS.toMicros(mGenerator.nextInt(5) + 1);
        return (int) reportLatencyUs;
    }
}
