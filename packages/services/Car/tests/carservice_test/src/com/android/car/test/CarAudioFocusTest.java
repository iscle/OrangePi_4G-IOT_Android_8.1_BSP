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

import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_FOCUS;
import static com.android.car.test.AudioTestUtils.doRequestFocus;

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
import android.util.Log;

import com.google.android.collect.Lists;

import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.FailingPropertyHandler;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// TODO: refactor all CarAudio*Test classes, they have a lot of common logic.
@MediumTest
public class CarAudioFocusTest extends MockedCarTestBase {
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

    public void testMediaGainFocus() throws Exception {
        //TODO update this to check config
        checkSingleRequestRelease(
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
                VehicleAudioStream.STREAM0,
                VehicleAudioContextFlag.MUSIC_FLAG);
    }

    public void testMediaGainTransientFocus() throws Exception {
        checkSingleRequestRelease(
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                VehicleAudioStream.STREAM0,
                VehicleAudioContextFlag.MUSIC_FLAG);
    }

    public void testMediaGainTransientMayDuckFocus() throws Exception {
        checkSingleRequestRelease(
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                VehicleAudioStream.STREAM0,
                VehicleAudioContextFlag.MUSIC_FLAG);
    }

    public void testAlarmGainTransientFocus() throws Exception {
        checkSingleRequestRelease(
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                VehicleAudioStream.STREAM1,
                VehicleAudioContextFlag.ALARM_FLAG);
    }

    public void testAlarmGainTransientMayDuckFocus() throws Exception {
        checkSingleRequestRelease(
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                VehicleAudioStream.STREAM1,
                VehicleAudioContextFlag.ALARM_FLAG);
    }

    public void testMediaNavFocus() throws Exception {
        //music start
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

        // nav guidance start
        AudioFocusListener listenerNav = new AudioFocusListener();
        AudioAttributes navAttrib = (new AudioAttributes.Builder()).
                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).
                setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).
                build();
        doRequestFocus(mAudioManager, listenerNav, navAttrib,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x3, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG |
                VehicleAudioContextFlag.NAVIGATION_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN, request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);

        // nav guidance done
        mAudioManager.abandonAudioFocus(listenerNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN, request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);

        // music done
        mAudioManager.abandonAudioFocus(listenerMusic);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS, request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    public void testMediaExternalMediaNavFocus() throws Exception {
        // android music
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

        // car plays external media (=outside Android)
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                VehicleAudioExtFocusFlag.PERMANENT_FLAG);
        int focusChange = listenerMusic.waitAndGetFocusChange(TIMEOUT_MS);
        assertEquals(AudioManager.AUDIOFOCUS_LOSS, focusChange);

        // nav guidance start
        AudioFocusListener listenerNav = new AudioFocusListener();
        AudioAttributes navAttrib = (new AudioAttributes.Builder()).
                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).
                setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).
                build();
        doRequestFocus(mAudioManager, listenerNav, navAttrib,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK,
                request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM1, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.NAVIGATION_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN_TRANSIENT,
                0x1 << VehicleAudioStream.STREAM1,
                VehicleAudioExtFocusFlag.PERMANENT_FLAG);

        // nav guidance ends
        mAudioManager.abandonAudioFocus(listenerNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                VehicleAudioExtFocusFlag.PERMANENT_FLAG);

        // now ends external play
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                0);
        mAudioManager.abandonAudioFocus(listenerMusic);
        //TODO how to check this?
    }

    public void testMediaExternalRadioNavMediaFocus() throws Exception {
        // android music
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

        // android radio
        AudioFocusListener listenerRadio = new AudioFocusListener();
        CarAudioManager carAudioManager = (CarAudioManager) getCar().getCarManager(
                Car.AUDIO_SERVICE);
        assertNotNull(carAudioManager);
        AudioAttributes radioAttributes = carAudioManager.getAudioAttributesForCarUsage(
                CarAudioManager.CAR_AUDIO_USAGE_RADIO);
        res = doRequestFocus(mAudioManager, listenerRadio,
                radioAttributes, AudioManager.AUDIOFOCUS_GAIN);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
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

        // nav guidance start
        AudioFocusListener listenerNav = new AudioFocusListener();
        AudioAttributes navAttrib = (new AudioAttributes.Builder()).
                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).
                setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).
                build();
        res = doRequestFocus(mAudioManager, listenerNav, navAttrib,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN,
                request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM1, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.NAVIGATION_FLAG |
                VehicleAudioContextFlag.RADIO_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0x1 << VehicleAudioStream.STREAM1,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);

        // nav guidance ends
        mAudioManager.abandonAudioFocus(listenerNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN,
                request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.RADIO_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);

        // ends radio. music will get the focus GAIN.
        // Music app is supposed to stop and release focus when it has lost focus, but here just
        // check if focus is working.
        mAudioManager.abandonAudioFocus(listenerRadio);
        listenerMusic.waitForFocus(TIMEOUT_MS, AudioManager.AUDIOFOCUS_GAIN);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0x1 << VehicleAudioStream.STREAM0,
                0);

        // now music release focus.
        mAudioManager.abandonAudioFocus(listenerMusic);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    private void checkSingleRequestRelease(int streamType, int androidFocus, int streamNumber,
            int context)
            throws Exception {
        AudioFocusListener lister = new AudioFocusListener();
        int res = doRequestFocus(mAudioManager, lister,
                streamType,
                androidFocus);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        int expectedRequest = VehicleAudioFocusRequest.REQUEST_RELEASE;
        int response = VehicleAudioFocusState.STATE_LOSS;
        switch (androidFocus) {
            case AudioManager.AUDIOFOCUS_GAIN:
                expectedRequest = VehicleAudioFocusRequest.REQUEST_GAIN;
                response = VehicleAudioFocusState.STATE_GAIN;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                expectedRequest =
                    VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT;
                response = VehicleAudioFocusState.STATE_GAIN_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                expectedRequest =
                    VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK;
                response = VehicleAudioFocusState.STATE_GAIN_TRANSIENT;
                break;
        }
        assertEquals(expectedRequest, request[0]);
        assertEquals(0x1 << streamNumber, request[1]);
        assertEquals(0, request[2]);
        assertEquals(context, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                response,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
        mAudioManager.abandonAudioFocus(lister);
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

    public void testRadioMute() throws Exception {
        doTestMediaMute(CarAudioManager.CAR_AUDIO_USAGE_RADIO,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.RADIO_FLAG);
    }

    public void testMusicMute() throws Exception {
        doTestMediaMute(CarAudioManager.CAR_AUDIO_USAGE_MUSIC,
                0x1,
                0,
                VehicleAudioContextFlag.MUSIC_FLAG);
    }

    private void doTestMediaMute(int mediaUsage, int primaryStream, int extFocusFlag,
            int mediaContext) throws Exception {
        // android radio
        AudioFocusListener listenerMedia = new AudioFocusListener();
        CarAudioManager carAudioManager = (CarAudioManager) getCar().getCarManager(
                Car.AUDIO_SERVICE);
        assertNotNull(carAudioManager);
        AudioAttributes radioAttributes = carAudioManager.getAudioAttributesForCarUsage(mediaUsage);
        Log.i(TAG, "request media Focus");
        int res = doRequestFocus(mAudioManager, listenerMedia,
                radioAttributes, AudioManager.AUDIOFOCUS_GAIN);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(primaryStream, request[1]);
        assertEquals(extFocusFlag, request[2]);
        assertEquals(mediaContext, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                primaryStream,
                extFocusFlag);
        // now mute it.
        assertFalse(carAudioManager.isMediaMuted());
        Log.i(TAG, "mute media");
        assertTrue(carAudioManager.setMediaMute(true));
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT,
                request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG,
                request[2]);
        assertEquals(0, request[3]);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG);
        assertTrue(carAudioManager.isMediaMuted());
        // nav guidance on top of it
        AudioFocusListener listenerNav = new AudioFocusListener();
        AudioAttributes navAttrib = (new AudioAttributes.Builder()).
                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).
                setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).
                build();
        Log.i(TAG, "request nav Focus");
        res = doRequestFocus(mAudioManager, listenerNav, navAttrib,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK,
                request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM1, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.NAVIGATION_FLAG, request[3]);
        assertTrue(carAudioManager.isMediaMuted());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0x1 << VehicleAudioStream.STREAM1,
                VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG);
        assertTrue(carAudioManager.isMediaMuted());
        // nav guidance ends
        mAudioManager.abandonAudioFocus(listenerNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT,
                request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG,
                request[2]);
        assertEquals(0, request[3]);
        assertTrue(carAudioManager.isMediaMuted());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG);
        // now unmute it. radio should resume.
        assertTrue(carAudioManager.isMediaMuted());
        assertFalse(carAudioManager.setMediaMute(false));
        assertFalse(carAudioManager.isMediaMuted());
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(primaryStream, request[1]);
        assertEquals(extFocusFlag,
                request[2]);
        assertEquals(mediaContext, request[3]);
        assertFalse(carAudioManager.isMediaMuted());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                primaryStream,
                extFocusFlag);
        assertFalse(carAudioManager.isMediaMuted());
        // release focus
        mAudioManager.abandonAudioFocus(listenerMedia);
    }

    static class AudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        private final Semaphore mFocusChangeWait = new Semaphore(0);
        private int mLastFocusChange;

        int waitAndGetFocusChange(long timeoutMs) throws Exception {
            if (!mFocusChangeWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("timeout waiting for focus change");
            }
            return mLastFocusChange;
        }

        void waitForFocus(long timeoutMs, int expectedFocus) throws Exception {
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

    static class FocusPropertyHandler implements VehicleHalPropertyHandler {

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
                    VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_FOCUS)
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

        @Override
        public void onPropertySet(VehiclePropValue value) {
            assertEquals(VehicleProperty.AUDIO_FOCUS, value.prop);
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
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_FOCUS)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(state, streams, extFocus, 0)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, int zones, float sampleRate) {
            assertEquals(AUDIO_FOCUS, property);
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            assertEquals(AUDIO_FOCUS, property);
        }
    }
}
