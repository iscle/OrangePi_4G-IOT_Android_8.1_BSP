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
package com.android.car.test;

import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_FOCUS;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_STREAM_STATE;
import static com.android.car.test.AudioTestUtils.doRequestFocus;

import com.google.android.collect.Lists;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioContextFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioExtFocusFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusRequest;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusState;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioStream;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.FailingPropertyHandler;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Test to check if system sound can be played without having focus.
 */
@MediumTest
public class CarAudioFocusSystemSoundTest extends MockedCarTestBase {
    private static final String TAG = CarAudioFocusTest.class.getSimpleName();

    private static final long TIMEOUT_MS = 3000;

    private final VehicleHalPropertyHandler mAudioRoutingPolicyPropertyHandler =
            new FailingPropertyHandler() {
        @Override
        public void onPropertySet(VehiclePropValue value) {
            //TODO
        }
    };

    private final FocusPropertyHandler mAudioFocusPropertyHandler =
            new FocusPropertyHandler(this);

    private AudioManager mAudioManager;

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.AUDIO_ROUTING_POLICY, mAudioRoutingPolicyPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE);
        addProperty(VehicleProperty.AUDIO_FOCUS, mAudioFocusPropertyHandler);
        addProperty(VehicleProperty.AUDIO_STREAM_STATE);


        addStaticProperty(VehicleProperty.AUDIO_HW_VARIANT,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_HW_VARIANT)
                        .addIntValue(-1)
                        .build())
                .setConfigArray(Lists.newArrayList(0));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // AudioManager should be created in main thread to get focus event. :(
        runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
        });
    }

    private void notifyStreamState(int streamNumber, boolean active) {
        getMockedVehicleHal().injectEvent(VehiclePropValueBuilder.newBuilder(AUDIO_STREAM_STATE)
                .setTimestamp()
                .addIntValue(new int[] { active ? 1 : 0, streamNumber })
                .build());
    }

    public void testSystemSoundPlayStop() throws Exception {
        //system sound start
        notifyStreamState(1, true);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_NO_DUCK,
                request[0]);
        assertEquals(0x2, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.SYSTEM_SOUND_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN_TRANSIENT,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
        // system sound stop
        notifyStreamState(1, false);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE,
                request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    public void testRadioSystemSound() throws Exception {
        // radio start
        AudioFocusListener listenerRadio = new AudioFocusListener();
        CarAudioManager carAudioManager = (CarAudioManager) getCar().getCarManager(
                Car.AUDIO_SERVICE);
        assertNotNull(carAudioManager);
        AudioAttributes radioAttributes = carAudioManager.getAudioAttributesForCarUsage(
                CarAudioManager.CAR_AUDIO_USAGE_RADIO);
        int res = doRequestFocus(mAudioManager, listenerRadio,
                radioAttributes, AudioManager.AUDIOFOCUS_GAIN);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.RADIO_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);
        // system sound start
        notifyStreamState(1, true);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x2, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.RADIO_FLAG |
                VehicleAudioContextFlag.SYSTEM_SOUND_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);
        // system sound stop
        notifyStreamState(1, false);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.RADIO_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);
        // radio stop
        mAudioManager.abandonAudioFocus(listenerRadio);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    public void testMusicSystemSound() throws Exception {
        // music start
        AudioFocusListener listenerMusic = new AudioFocusListener();
        int res = doRequestFocus(mAudioManager, listenerMusic,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
        // system sound start
        notifyStreamState(1, true);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x1 | 0x2, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG |
                VehicleAudioContextFlag.SYSTEM_SOUND_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
        // system sound stop
        notifyStreamState(1, false);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
        // music stop
        mAudioManager.abandonAudioFocus(listenerMusic);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    public void testNavigationSystemSound() throws Exception {
        // nav guidance start
        AudioFocusListener listenerNav = new AudioFocusListener();
        AudioAttributes navAttrib = (new AudioAttributes.Builder()).
                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).
                setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).
                build();
        int res = doRequestFocus(mAudioManager, listenerNav, navAttrib,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK,
                request[0]);
        assertEquals(0x2, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.NAVIGATION_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN_TRANSIENT,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
        // system sound start
        notifyStreamState(1, true);
        // cannot distinguish this case from nav only. so no focus change.
        mAudioFocusPropertyHandler.assertNoFocusRequest(1000);
        // cannot distinguish this case from nav only. so no focus change.
        notifyStreamState(1, false);
        mAudioFocusPropertyHandler.assertNoFocusRequest(1000);
        // nav guidance stop
        mAudioManager.abandonAudioFocus(listenerNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    private static class AudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        private final Semaphore mFocusChangeWait = new Semaphore(0);
        private int mLastFocusChange;

        // TODO: not used?
        public int waitAndGetFocusChange(long timeoutMs) throws Exception {
            if (!mFocusChangeWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("timeout waiting for focus change");
            }
            return mLastFocusChange;
        }

        public void waitForFocus(long timeoutMs, int expectedFocus) throws Exception {
            while (mLastFocusChange != expectedFocus) {
                if (!mFocusChangeWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                    fail("timeout waiting for focus change");
                }
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            mLastFocusChange = focusChange;
            mFocusChangeWait.release();
        }
    }

    private static class FocusPropertyHandler implements VehicleHalPropertyHandler {

        private int mState = VehicleAudioFocusState.STATE_LOSS;
        private int mStreams = 0;
        private int mExtFocus = 0;
        private int mRequest;
        private int mRequestedStreams;
        private int mRequestedExtFocus;
        private int mRequestedAudioContexts;
        private final MockedCarTestBase mCarTest;

        private final Semaphore mSetWaitSemaphore = new Semaphore(0);

        FocusPropertyHandler(MockedCarTestBase carTest) {
            mCarTest = carTest;
        }

        void sendAudioFocusState(int state, int streams, int extFocus) {
            synchronized (this) {
                mState = state;
                mStreams = streams;
                mExtFocus = extFocus;
            }
            mCarTest.getMockedVehicleHal().injectEvent(
                    VehiclePropValueBuilder.newBuilder(AUDIO_FOCUS)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(state, streams, extFocus, 0)
                            .build());
        }

        int[] waitForAudioFocusRequest(long timeoutMs) throws Exception {
            if (!mSetWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("timeout");
            }
            synchronized (this) {
                return new int[] { mRequest, mRequestedStreams, mRequestedExtFocus,
                        mRequestedAudioContexts };
            }
        }

        void assertNoFocusRequest(long timeoutMs) throws Exception {
            if (mSetWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("should not get focus request");
            }
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            assertEquals(AUDIO_FOCUS, value.prop);
            ArrayList<Integer> v = value.value.int32Values;
            synchronized (this) {
                mRequest = v.get(VehicleAudioFocusIndex.FOCUS);
                mRequestedStreams = v.get(VehicleAudioFocusIndex.STREAMS);
                mRequestedExtFocus = v.get(VehicleAudioFocusIndex.EXTERNAL_FOCUS_STATE);
                mRequestedAudioContexts = v.get(VehicleAudioFocusIndex.AUDIO_CONTEXTS);
            }
            mSetWaitSemaphore.release();
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertEquals(VehicleProperty.AUDIO_FOCUS, value.prop);
            int state, streams, extFocus;
            synchronized (this) {
                state = mState;
                streams = mStreams;
                extFocus = mExtFocus;
            }
            return VehiclePropValueBuilder.newBuilder(AUDIO_FOCUS)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(state, streams, extFocus, 0)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, int zones, float sampleRate) {
            assertEquals(VehicleProperty.AUDIO_FOCUS, property);
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            assertEquals(VehicleProperty.AUDIO_FOCUS, property);
        }
    }
}
