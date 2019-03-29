/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.os.Build;
import android.util.Log;

import junit.framework.Assert;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS Verifier case for verifying dynamic sensor discovery feature.
 */
public class DynamicSensorDiscoveryTestActivity extends SensorCtsVerifierTestActivity {

    private final static String TAG = "DynamicSensorDiscoveryTestActivity";
    private final static int CONNECTION_TIMEOUT_SEC = 30;
    private final static int DISCONNECTION_TIMEOUT_SEC = 30;
    private final static int EVENT_TIMEOUT_SEC = 30;
    private SensorManager mSensorManager;
    private boolean mFeatureSupported = false;
    private boolean mSensorConnected = false;
    private boolean mSensorDisconnected = false;
    private Integer mSensorId;
    private Callback mCallback;

    public DynamicSensorDiscoveryTestActivity() {
        super(DynamicSensorDiscoveryTestActivity.class);
    }

    @Override
    protected void activitySetUp() throws InterruptedException {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager == null || !(Build.VERSION.SDK_INT > Build.VERSION_CODES.M ||
                Build.VERSION.CODENAME.startsWith("N") /* useful when N in dev */) ) {
            return;
        }
        mFeatureSupported = mSensorManager.isDynamicSensorDiscoverySupported();

        try {
            featureSupportedOrSkip();
        } catch (SensorTestStateNotSupportedException e) {
            // This device doesn't support dynamic sensors.  So we won't
            // be running any of the tests, and really don't want to
            // confuse the user by telling them they need to hoook one up.
            // TODO(b/29606675): This is pretty hack, and should have
            //     a better overall approach.
            return;
        }
        showUserMessage("This test will requires the user to connect an external sensor (" +
                "physical or simulated) and then disconnect it.");
        waitForUserToContinue();

        mCallback = new Callback();
        mSensorManager.registerDynamicSensorCallback(mCallback);
    }

    @SuppressWarnings("unused")
    public String test0_OnConnect() {
        featureSupportedOrSkip();

        showUserMessage(String.format("Please connect an external sensor to device in %d seconds.",
                    CONNECTION_TIMEOUT_SEC));

        Assert.assertTrue("Cannot detect sensor connection.", mCallback.waitForConnection(null));
        mSensorConnected = true;
        mSensorId = mCallback.getSensorId();
        return "OnConnect: Success";
    }

    @SuppressWarnings("unused")
    public String test1_DynamicSensorList() {
        featureSupportedOrSkip();
        sensorConnectedOrSkip();

        Assert.assertTrue("Dynamic sensor flag is not set correctly for at least one sensor",
                isDynamicFlagSetCorrectly());

        Assert.assertTrue("Sensor connected, but is not in dynamic sensor list",
                mCallback.isSensorInList());

        Assert.assertTrue("Sensor connected, but is not in dynamic sensor list of its type",
                mCallback.isSensorInListOfSpecificType());

        return "DynamicSensorList: Success";
    }

    @SuppressWarnings("unused")
    public String test2_SensorOperation() {
        featureSupportedOrSkip();
        sensorConnectedOrSkip();

        showUserMessage("Testing sensor operation ... Please make sure sensor generates sensor " +
                "events if it does not automatically do so.");

        Assert.assertTrue("Failed to receive sensor events", mCallback.waitForSensorEvent());
        return "SensorOperation: Success";
    }

    @SuppressWarnings("unused")
    public String test3_OnDisconnect() {
        featureSupportedOrSkip();
        sensorConnectedOrSkip();

        showUserMessage(String.format("Please disconnect the external sensor that was previously " +
                    "connected in %d seconds", DISCONNECTION_TIMEOUT_SEC));
        Assert.assertTrue("Cannot detect sensor disconnection.", mCallback.waitForDisconnection());
        mSensorDisconnected = true;
        return "OnDisconnect: Success";
    }

    @SuppressWarnings("unused")
    public String test4_OnReconnect() {
        featureSupportedOrSkip();
        sensorConnectedOrSkip();
        sensorDisconnectedOrSkip();

        showUserMessage(String.format("Please connect the same sensor that was previously " +
                    "connected in %d seconds", CONNECTION_TIMEOUT_SEC));
        Assert.assertTrue("Cannot detect sensor reconnection.",
                mCallback.waitForConnection(mSensorId));

        Integer sensorId = mCallback.getSensorId();
        boolean match = mSensorId != null && sensorId != null &&
                sensorId.intValue() == mSensorId.intValue();
        Assert.assertTrue("Id mismatch for the reconnected sensor", match);
        return "OnReconnect: Success";
    }

    private class Callback extends SensorManager.DynamicSensorCallback {

        private Sensor mSensor = null;
        private Integer mExpectSensorId = null;
        private CountDownLatch mConnectLatch;
        private CountDownLatch mDisconnectLatch;

        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            Log.d(TAG, "Sensor Connected: " + sensor);

            if (mExpectSensorId == null || mExpectSensorId == sensor.getId()) {
                mSensor = sensor;
                if (mConnectLatch != null) {
                    mConnectLatch.countDown();
                }
            }
        }

        @Override
        public void onDynamicSensorDisconnected(Sensor sensor) {
            if (mSensor == sensor) {
                mSensor = null;
                if (mDisconnectLatch != null) {
                    mDisconnectLatch.countDown();
                }
            }
        }

        public boolean waitForConnection(Integer sensorId) {
            boolean ret;
            mExpectSensorId = sensorId;
            mConnectLatch = new CountDownLatch(1);
            try {
                ret = mConnectLatch.await(CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ret = false;
                Thread.currentThread().interrupt();
            } finally {
                mConnectLatch = null;
            }
            return ret;
        }

        public boolean waitForDisconnection() {
            boolean ret;
            mDisconnectLatch = new CountDownLatch(1);
            try {
                ret = mDisconnectLatch.await(DISCONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ret = false;
                Thread.currentThread().interrupt();
            } finally {
                mDisconnectLatch = null;
            }
            return ret;
        }

        public boolean waitForSensorEvent() {
            if (mSensor == null) {
                Log.e(TAG, "Sensor is not set");
                return false;
            }

            final CountDownLatch eventLatch = new CountDownLatch(1);

            SensorEventCallback eventCallback =
                    new SensorEventCallback() {
                        @Override
                        public void onSensorChanged(SensorEvent e) {
                            eventLatch.countDown();
                        }
                    };

            if (!mSensorManager.registerListener(
                    eventCallback, mSensor, SensorManager.SENSOR_DELAY_FASTEST)) {
                return false;
            }

            boolean ret;
            try {
                ret = eventLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ret = false;
                Thread.currentThread().interrupt();
            } finally {
                mSensorManager.unregisterListener(eventCallback);
            }
            return ret;
        }

        public boolean isSensorInList() {
            // This is intentional event if Sensor did not override equals().
            // Sensor objects representing the same sensor will be the same object.
            return assumeSensorIsSet() &&
                    mSensorManager.getDynamicSensorList(Sensor.TYPE_ALL).contains(mSensor);
        }

        public boolean isSensorInListOfSpecificType() {
            // This is intentional event if Sensor did not override equals().
            // Sensor objects representing the same sensor will be the same object.
            return assumeSensorIsSet() &&
                    mSensorManager.getDynamicSensorList(mSensor.getType()).contains(mSensor);
        }

        public Integer getSensorId() {
            return assumeSensorIsSet() ? mSensor.getId() : null;
        }

        // name assumeSensorIsSet instead of is... because the Log print is one of the main purpose.
        private boolean assumeSensorIsSet() {
            if (mSensor == null) {
                Log.e(TAG, "Sensor is not set");
                return false;
            }
            return true;
        }
    }

    private boolean isDynamicFlagSetCorrectly() {
        boolean ret = true;
        List<Sensor> dynamicSensors = mSensorManager.getDynamicSensorList(Sensor.TYPE_ALL);
        for (Sensor s : dynamicSensors) {
            if (!s.isDynamicSensor()) {
                Log.e(TAG, String.format(
                        "Dynamic sensor \"%s\" isDynamicSensor() return false", s.getName()));
                ret = false;
            }
        }

        List<Sensor> staticSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : staticSensors) {
            if (s.isDynamicSensor()) {
                Log.e(TAG, String.format(
                        "Static sensor \"%s\" isDynamicSensor() return true", s.getName()));
                ret = false;
            }
        }
        return ret;
    }

    private void featureSupportedOrSkip() {
        if (!mFeatureSupported) {
            throw new SensorTestStateNotSupportedException(
                    "Dynamic sensor discovery not supported, skip.");
        }
    }

    private void sensorConnectedOrSkip() {
        if (!mSensorConnected) {
            throw new SensorTestStateNotSupportedException(
                    "Sensor not connected, skip.");
        }
    }

    private void sensorDisconnectedOrSkip() {
        if (!mSensorDisconnected) {
            throw new SensorTestStateNotSupportedException(
                    "Sensor has not been disconnected, skip.");
        }
    }

    /*
     *  This function serves as a proxy as appendText is marked to be deprecated.
     *  When appendText is removed, this function will have a different implementation.
     *
     */
    private void showUserMessage(String s) {
        appendText(s);
    }
}
