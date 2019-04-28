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
 * limitations under the License.
 */
package com.android.devicehealth.tests;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.platform.test.annotations.GlobalPresubmit;
import android.support.test.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

/*
 * Tests used for basic sensors validation after the device boot is completed.
 * This test is used for global presubmit.
 */

@GlobalPresubmit
public class SensorsBootCheck {
    private Context mContext;
    private PackageManager mPackageManager;
    private SensorManager mSensorManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    /*
     * Test if sensors are available on the device as advertised.
     */
    @Test
    public void checkSensors() {
        /*
         * This is a simple test that checks if sensors that are expected on the device are actually
         * available. This is a generic test and relies on the fact that a device must declare some
         * of the sensors it supports as features. We do not check the actual function of the
         * sensors in this method.
         */

        int numErrors = 0;

        mPackageManager = mContext.getPackageManager();
        Assert.assertNotNull("Package Manager not found", mPackageManager);

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        Assert.assertNotNull("Sensor Manager not found", mSensorManager);

        StringBuilder errorDetails = new StringBuilder("Error details: \n");
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_ACCELEROMETER,
            Sensor.TYPE_ACCELEROMETER, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_AMBIENT_TEMPERATURE,
            Sensor.TYPE_AMBIENT_TEMPERATURE, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_BAROMETER,
            Sensor.TYPE_PRESSURE, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_COMPASS,
            Sensor.TYPE_MAGNETIC_FIELD, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_GYROSCOPE,
            Sensor.TYPE_GYROSCOPE, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_LIGHT,
            Sensor.TYPE_LIGHT, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_PROXIMITY,
            Sensor.TYPE_PROXIMITY, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_RELATIVE_HUMIDITY,
            Sensor.TYPE_RELATIVE_HUMIDITY, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_STEP_COUNTER,
            Sensor.TYPE_STEP_COUNTER, errorDetails) ? 1 : 0;
        numErrors += isSensorMissing(PackageManager.FEATURE_SENSOR_STEP_DETECTOR,
            Sensor.TYPE_STEP_DETECTOR, errorDetails) ? 1 : 0;

        // TODO: test heart rate and other related sensor types.

        Assert.assertEquals(errorDetails.toString(), numErrors, 0);
    }

    private boolean isSensorMissing(String featureString, int sensorType, StringBuilder errString) {
        if (!mPackageManager.hasSystemFeature(featureString)) {
            // no claim to support the sensor, do not check farther
            return false;
        }

        if (mSensorManager.getDefaultSensor(sensorType) == null) {
            errString.append("Cannot find sensor type " + sensorType + " even though " +
                featureString + " is defined\n");
            return true;
        }
        return false;
    }
}
