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
package com.android.car.test;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarAudioManagerTest extends MockedCarTestBase {
    private final AudioParametersPropertyHandler mAudioParametersPropertyHandler =
            new AudioParametersPropertyHandler();
    CarAudioManager mCarAudioManager;

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.AUDIO_PARAMETERS, mAudioParametersPropertyHandler)
	    .setAccess(VehiclePropertyAccess.READ_WRITE)
	    .setConfigString("com.android.test.param1;com.android.test.param2");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarAudioManager = (CarAudioManager) getCar().getCarManager(
                Car.AUDIO_SERVICE);
        assertNotNull(mCarAudioManager);
    }

    public void testAudioParamConfig() throws Exception {
        String[] keys = mCarAudioManager.getParameterKeys();
        assertNotNull(keys);
        assertEquals(2, keys.length);
        assertEquals("com.android.test.param1", keys[0]);
        assertEquals("com.android.test.param2", keys[1]);
    }

    public void testAudioParamSet() throws Exception {
        try {
            mCarAudioManager.setParameters(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mCarAudioManager.setParameters("com.android.test.param3=3");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        final String SET_OK1 = "com.android.test.param1=1";
        mCarAudioManager.setParameters(SET_OK1);
        mAudioParametersPropertyHandler.waitForSet(DEFAULT_WAIT_TIMEOUT_MS, SET_OK1);

        final String SET_OK2 = "com.android.test.param1=1;com.android.test.param2=2";
        mCarAudioManager.setParameters(SET_OK2);
        mAudioParametersPropertyHandler.waitForSet(DEFAULT_WAIT_TIMEOUT_MS, SET_OK2);
    }

    public void testAudioParamGet() throws Exception {
        try {
            mCarAudioManager.getParameters(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mCarAudioManager.getParameters("com.android.test.param3");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mCarAudioManager.getParameters("com.android.test.param1;com.android.test.param3");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        final String GET_RESP1 = "com.android.test.param1=1";
        mAudioParametersPropertyHandler.setValueForGet(GET_RESP1);
        String get1 = mCarAudioManager.getParameters("com.android.test.param1");
        assertEquals(GET_RESP1, get1);

        final String GET_RESP2 = "com.android.test.param1=1;com.android.test.param2=2";
        mAudioParametersPropertyHandler.setValueForGet(GET_RESP2);
        String get2 = mCarAudioManager.getParameters(
                "com.android.test.param1;com.android.test.param2");
        assertEquals(GET_RESP2, get2);
    }

    public void testAudioParamChangeListener() throws Exception {
        AudioParamListener listener1 = new AudioParamListener();
        AudioParamListener listener2 = new AudioParamListener();

        mCarAudioManager.setOnParameterChangeListener(listener1);
        final String EVENT1 = "com.android.test.param1=10";
        sendAudioParamChange(EVENT1);
        listener1.waitForChange(DEFAULT_WAIT_TIMEOUT_MS, EVENT1);

        mCarAudioManager.setOnParameterChangeListener(listener2);
        listener1.clearParameter();
        final String EVENT2 = "com.android.test.param1=20;com.android.test.param2=10";
        sendAudioParamChange(EVENT2);
        listener2.waitForChange(DEFAULT_WAIT_TIMEOUT_MS, EVENT2);
        listener1.assertParameter(null);

        mCarAudioManager.setOnParameterChangeListener(null);
        listener2.clearParameter();
        sendAudioParamChange(EVENT1);
        Thread.sleep(200);
        listener1.assertParameter(null);
        listener2.assertParameter(null);
    }

    private void sendAudioParamChange(String params) {
        getMockedVehicleHal().injectEvent(
                VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_PARAMETERS)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .setStringValue(params)
                .build());
    }

    static class AudioParametersPropertyHandler implements VehicleHalPropertyHandler {
        private final Semaphore mSetWaitSemaphore = new Semaphore(0);

        private String mValueSet;
        private String mGetResponse;

        public void waitForSet(long waitTimeMs, String expected) throws Exception {
            mSetWaitSemaphore.tryAcquire(waitTimeMs, TimeUnit.MILLISECONDS);
            synchronized (this) {
                assertEquals(expected, mValueSet);
            }
        }

        public synchronized void setValueForGet(String value) {
            mGetResponse = value;
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            assertEquals(VehicleProperty.AUDIO_PARAMETERS, value.prop);
            String setValue = value.value.stringValue;
            synchronized (this) {
                mValueSet = setValue;
            }
            mSetWaitSemaphore.release();
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertEquals(VehicleProperty.AUDIO_PARAMETERS, value.prop);
            String response;
            synchronized (this) {
                response = mGetResponse;
            }
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_PARAMETERS)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .setStringValue(mGetResponse)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, int zones, float sampleRate) {
            assertEquals(VehicleProperty.AUDIO_PARAMETERS, property);
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            assertEquals(VehicleProperty.AUDIO_PARAMETERS, property);
        }
    }

    static class AudioParamListener implements CarAudioManager.OnParameterChangeListener {
        private String mParameter;
        private final Semaphore mChangeWaitSemaphore = new Semaphore(0);

        @Override
        public void onParameterChange(String parameters) {
            mParameter = parameters;
            mChangeWaitSemaphore.release();
        }

        public void waitForChange(long waitTimeMs, String expected) throws Exception {
            mChangeWaitSemaphore.tryAcquire(waitTimeMs, TimeUnit.MILLISECONDS);
            assertEquals(expected, mParameter);
        }

        public void clearParameter() {
            mParameter = null;
        }

        public void assertParameter(String expected) {
            assertEquals(expected, mParameter);
        }
    }
}
