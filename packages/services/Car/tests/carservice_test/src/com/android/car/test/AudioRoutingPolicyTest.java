/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.hardware.automotive.vehicle.V2_0.VehicleAudioContextFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioRoutingPolicyIndex;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.test.suitebuilder.annotation.SmallTest;

import com.google.android.collect.Lists;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.FailingPropertyHandler;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
public class AudioRoutingPolicyTest extends MockedCarTestBase {
    private static final String TAG = AudioRoutingPolicyTest.class.getSimpleName();

    private static final long TIMEOUT_MS = 3000;

    private final VehicleHalPropertyHandler mAudioRoutingPolicyHandler =
            new FailingPropertyHandler() {

        @Override
        public void onPropertySet(VehiclePropValue value) {
            handlePropertySetEvent(value);
        }
    };

    private final Semaphore mWaitSemaphore = new Semaphore(0);
    private final LinkedList<VehiclePropValue> mEvents = new LinkedList<>();

    @Override
    protected synchronized void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.AUDIO_ROUTING_POLICY, mAudioRoutingPolicyHandler)
                .setAccess(VehiclePropertyAccess.WRITE);
    }

    public void testNoHwVariant() throws Exception {
        checkPolicy0();
    }

    public void testHwVariant0() throws Exception {
        addStaticProperty(VehicleProperty.AUDIO_HW_VARIANT,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_HW_VARIANT)
                        .addIntValue(0)
                        .build())
                .setConfigArray(Lists.newArrayList(0));

        mEvents.clear();
        reinitializeMockedHal();

        checkPolicy0();
    }

    public void testHwVariantForTest() throws Exception {
        addStaticProperty(VehicleProperty.AUDIO_HW_VARIANT,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_HW_VARIANT)
                        .addIntValue(-1)
                        .build())
                .setConfigArray(Lists.newArrayList(0));

        mEvents.clear();
        reinitializeMockedHal();

        checkPolicyForTest();
    }

    private void checkPolicy0() throws Exception {
        assertTrue(mWaitSemaphore.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        ArrayList<Integer> v = mEvents.get(0).value.int32Values;
        assertEquals(0, v.get(VehicleAudioRoutingPolicyIndex.STREAM).intValue());
        int contexts = v.get(VehicleAudioRoutingPolicyIndex.CONTEXTS);
        // check if all contexts are allowed ones.
        assertTrue((contexts & ~(
                VehicleAudioContextFlag.RINGTONE_FLAG |
                VehicleAudioContextFlag.ALARM_FLAG |
                VehicleAudioContextFlag.CALL_FLAG |
                VehicleAudioContextFlag.MUSIC_FLAG |
                VehicleAudioContextFlag.RADIO_FLAG |
                VehicleAudioContextFlag.NAVIGATION_FLAG |
                VehicleAudioContextFlag.NOTIFICATION_FLAG |
                VehicleAudioContextFlag.UNKNOWN_FLAG |
                VehicleAudioContextFlag.VOICE_COMMAND_FLAG |
                VehicleAudioContextFlag.SYSTEM_SOUND_FLAG |
                VehicleAudioContextFlag.SAFETY_ALERT_FLAG)) == 0);
    }

    private void checkPolicyForTest() throws Exception {
        // write happens twice.
        assertTrue(mWaitSemaphore.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mWaitSemaphore.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        ArrayList<Integer> v = mEvents.get(0).value.int32Values;
        assertEquals(0, v.get(VehicleAudioRoutingPolicyIndex.STREAM).intValue());
        assertEquals(
                VehicleAudioContextFlag.CALL_FLAG |
                VehicleAudioContextFlag.MUSIC_FLAG |
                VehicleAudioContextFlag.RADIO_FLAG |
                VehicleAudioContextFlag.UNKNOWN_FLAG,
                v.get(VehicleAudioRoutingPolicyIndex.CONTEXTS).intValue());
        v = mEvents.get(1).value.int32Values;
        assertEquals(1, v.get(VehicleAudioRoutingPolicyIndex.STREAM).intValue());
        assertEquals(
                VehicleAudioContextFlag.ALARM_FLAG |
                VehicleAudioContextFlag.NAVIGATION_FLAG |
                VehicleAudioContextFlag.NOTIFICATION_FLAG |
                VehicleAudioContextFlag.VOICE_COMMAND_FLAG |
                VehicleAudioContextFlag.SYSTEM_SOUND_FLAG |
                VehicleAudioContextFlag.SAFETY_ALERT_FLAG,
                v.get(VehicleAudioRoutingPolicyIndex.CONTEXTS).intValue());
    }

    private void handlePropertySetEvent(VehiclePropValue value) {
        mEvents.add(value);
        mWaitSemaphore.release();
    }
}
