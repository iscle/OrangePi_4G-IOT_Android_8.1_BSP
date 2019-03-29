
package com.android.cts.verifier.sensors;

import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;

import junit.framework.Assert;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.TestSensorEvent;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Test cross-sensor timestamp alignment by detecting major change in each
 * sensor and comparing timestamps of that change.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class SensorSynchronizationTestActivity
        extends SensorCtsVerifierTestActivity
        implements SensorEventListener {
    public SensorSynchronizationTestActivity() {
        super(SensorSynchronizationTestActivity.class);
    }

    private final double NANOS_PER_MILLI = 1e6;
    private final int DATA_COLLECTION_TIME_IN_MS = 5000;
    private final int RATE_100HZ_IN_US = 10000;
    private final int MAX_CROSS_SENSOR_DELAY_MILLIS = 125;
    private final double THRESH_DEGREES = 10.0;
    private final double THRESH_RPS = 1.0;

    private SensorManager mSensorManager = null;
    private List<TestSensorEvent> mSensorEvents = new ArrayList<TestSensorEvent>();

    private void startDataCollection() {
        mSensorEvents.clear();

        mSensorManager = (SensorManager) getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                RATE_100HZ_IN_US);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                RATE_100HZ_IN_US);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                RATE_100HZ_IN_US);
    }

    private void stopDataCollection() {
        mSensorManager.unregisterListener(this);
    }

    private void analyzeData() {
        int numberOfCollectedEvents = mSensorEvents.size();
        Assert.assertTrue("No sensor events collected", numberOfCollectedEvents > 2);

        boolean accMovementDetected = false;
        boolean magMovementDetected = false;
        boolean gyrMovementDetected = false;
        long accMovementTimestamp = 0, magMovementTimestamp = 0, gyrMovementTimestamp = 0;
        float[] accInitValues = null, magInitValues = null, gyrInitValues = null;

        for (int i = 0; i < numberOfCollectedEvents; i++) {
            TestSensorEvent event = mSensorEvents.get(i);

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                if (accInitValues == null) {
                    accInitValues = event.values.clone();
                } else if (angleBetweenVecsDegrees(accInitValues, event.values) > THRESH_DEGREES
                        && !accMovementDetected) {
                    accMovementDetected = true;
                    accMovementTimestamp = event.timestamp;
                }
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                if (magInitValues == null) {
                    magInitValues = event.values.clone();
                } else if (angleBetweenVecsDegrees(magInitValues, event.values) > THRESH_DEGREES
                        && !magMovementDetected) {
                    magMovementDetected = true;
                    magMovementTimestamp = event.timestamp;
                }
            }
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                if (gyrInitValues == null) {
                    gyrInitValues = event.values.clone();
                } else if (normVec(event.values) > THRESH_RPS && !gyrMovementDetected) {
                    gyrMovementDetected = true;
                    gyrMovementTimestamp = event.timestamp;
                }
            }

            if (accMovementDetected && magMovementDetected && gyrMovementDetected) {
                double maxTimestamp = Math.max(accMovementTimestamp,
                        magMovementTimestamp);
                maxTimestamp = Math.max(gyrMovementTimestamp, maxTimestamp);

                double minTimestamp = Math.min(accMovementTimestamp,
                        magMovementTimestamp);
                minTimestamp = Math.min(gyrMovementTimestamp, minTimestamp);

                double timeDifferenceBetweenMovementMillis =
                        (maxTimestamp - minTimestamp) / NANOS_PER_MILLI;

                appendText(String.format("\nSensor  |  Relative Timestamp (msec)\n"
                        + "Accelerometer | %4.1f\nMagnetometer | %4.1f\nGyroscope | %4.1f\n",
                        (accMovementTimestamp - minTimestamp) / NANOS_PER_MILLI,
                        (magMovementTimestamp - minTimestamp) / NANOS_PER_MILLI,
                        (gyrMovementTimestamp - minTimestamp) / NANOS_PER_MILLI));
                Assert.assertEquals(String.format(
                        "Cross sensor timestamp alignment off by more than %d msec.",
                        MAX_CROSS_SENSOR_DELAY_MILLIS),
                        0, timeDifferenceBetweenMovementMillis, MAX_CROSS_SENSOR_DELAY_MILLIS);
                appendText(String.format(
                        "Maximum cross sensor time between movement: %4.1f msec is within "
                                + "required tolerance of %4.1f msec",
                        timeDifferenceBetweenMovementMillis,
                        (float) MAX_CROSS_SENSOR_DELAY_MILLIS));
                break;
            }
        }

        Assert.assertTrue("Accelerometer did not detect any movement", accMovementDetected);
        Assert.assertTrue("Magnetometer did not detect any movement", magMovementDetected);
        Assert.assertTrue("Gyroscope did not detect any movement", gyrMovementDetected);
    }

    public String testCrossSensorSynchronization() throws Throwable {
        appendText("This test provides a rough indication of cross-sensor timestamp synchronization.");
        appendText("Hold device still in hand and click 'Next'");
        waitForUserToBegin();
        clearText();
        appendText("Quickly twist device upside-down and back");

        startDataCollection();
        Thread.sleep(DATA_COLLECTION_TIME_IN_MS);

        stopDataCollection();
        analyzeData();
        return null;
    }

    protected double angleBetweenVecsDegrees(float[] vec1, float[] vec2) {
        return Math.toDegrees(Math.acos((vec1[0] * vec2[0] + vec1[1] * vec2[1] + vec1[2] * vec2[2])
                / normVec(vec1) / normVec(vec2)));
    }

    protected double normVec(float[] vec1) {
        return Math.sqrt(vec1[0] * vec1[0] + vec1[1] * vec1[1] + vec1[2] * vec1[2]);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        mSensorEvents.add(new TestSensorEvent(sensorEvent));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
