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
import static com.android.car.test.AudioTestUtils.doRequestFocus;
import static java.lang.Integer.toHexString;

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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class CarAudioExtFocusTest extends MockedCarTestBase {
    private static final String TAG = CarAudioExtFocusTest.class.getSimpleName();

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

    private final ExtRoutingHintPropertyHandler mExtRoutingHintPropertyHandler =
            new ExtRoutingHintPropertyHandler();

    private static final String EXT_ROUTING_CONFIG =
            "0:RADIO_AM_FM:0,1:RADIO_SATELLITE:0,33:CD_DVD:0," +
            "64:com.google.test.SOMETHING_SPECIAL," +
            "4:EXT_NAV_GUIDANCE:1," +
            "5:AUX_IN0:0";

    private final Semaphore mWaitSemaphore = new Semaphore(0);
    private final LinkedList<VehiclePropValue> mEvents = new LinkedList<VehiclePropValue>();
    private AudioManager mAudioManager;
    private CarAudioManager mCarAudioManager;

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.AUDIO_ROUTING_POLICY, mAudioRoutingPolicyPropertyHandler);
        addProperty(VehicleProperty.AUDIO_FOCUS, mAudioFocusPropertyHandler);

        addStaticProperty(VehicleProperty.AUDIO_HW_VARIANT,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_HW_VARIANT)
                        .addIntValue(-1)
                        .build())
                .setConfigArray(Lists.newArrayList(0));

        addProperty(VehicleProperty.AUDIO_EXT_ROUTING_HINT, mExtRoutingHintPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setConfigString(EXT_ROUTING_CONFIG);
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

        mCarAudioManager = (CarAudioManager) getCar().getCarManager(Car.AUDIO_SERVICE);
        assertNotNull(mCarAudioManager);
    }

    public void testExtRoutings() throws Exception {
        String[] radioTypes = mCarAudioManager.getSupportedRadioTypes();
        assertNotNull(radioTypes);
        checkStringArrayContents(new String[] {"RADIO_AM_FM", "RADIO_SATELLITE"}, radioTypes);

        String[] nonRadioTypes = mCarAudioManager.getSupportedExternalSourceTypes();
        assertNotNull(nonRadioTypes);
        checkStringArrayContents(new String[] {"CD_DVD", "com.google.test.SOMETHING_SPECIAL",
                "EXT_NAV_GUIDANCE", "AUX_IN0"}, nonRadioTypes);
    }

    private void checkStringArrayContents(String[] expected, String[] actual) throws Exception {
        Arrays.sort(expected);
        Arrays.sort(actual);
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    public void testRadioAttributeCreation() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForRadio(
                CarAudioManager.CAR_RADIO_TYPE_AM_FM);
        assertNotNull(attrb);

        attrb = mCarAudioManager.getAudioAttributesForRadio(
                CarAudioManager.CAR_RADIO_TYPE_SATELLITE);
        assertNotNull(attrb);

        try {
            mCarAudioManager.getAudioAttributesForRadio(CarAudioManager.CAR_RADIO_TYPE_AM_FM_HD);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testExtSourceAttributeCreation() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_CD_DVD);
        assertNotNull(attrb);

        attrb = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_EXT_NAV_GUIDANCE);
        assertNotNull(attrb);

        attrb = mCarAudioManager.getAudioAttributesForExternalSource(
                "com.google.test.SOMETHING_SPECIAL");
        assertNotNull(attrb);

        try {
            mCarAudioManager.getAudioAttributesForExternalSource(
                    CarAudioManager.CAR_RADIO_TYPE_AM_FM_HD);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testRadioAmFmGainFocus() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForRadio(
                CarAudioManager.CAR_RADIO_TYPE_AM_FM);
        assertNotNull(attrb);
        checkSingleRequestRelease(attrb, AudioManager.AUDIOFOCUS_GAIN, new int[] {1, 0, 0, 0},
                0, VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.RADIO_FLAG);
    }

    public void testRadioSatelliteGainFocus() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForRadio(
                CarAudioManager.CAR_RADIO_TYPE_SATELLITE);
        assertNotNull(attrb);
        checkSingleRequestRelease(attrb, AudioManager.AUDIOFOCUS_GAIN, new int[] {2, 0, 0, 0},
                0, VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.RADIO_FLAG);
    }

    public void testCdGainFocus() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_CD_DVD);
        assertNotNull(attrb);
        checkSingleRequestRelease(attrb, AudioManager.AUDIOFOCUS_GAIN, new int[] {0, 2, 0, 0},
                0, VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.CD_ROM_FLAG);
    }

    public void testAuxInFocus() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_AUX_IN0);
        assertNotNull(attrb);
        checkSingleRequestRelease(attrb, AudioManager.AUDIOFOCUS_GAIN, new int[] {0x1<<5, 0, 0, 0},
                0, VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.AUX_AUDIO_FLAG);
    }

    public void testExtNavInFocus() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_EXT_NAV_GUIDANCE);
        assertNotNull(attrb);
        checkSingleRequestRelease(attrb, AudioManager.AUDIOFOCUS_GAIN, new int[] {0x1<<4, 0, 0, 0},
                0, VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.EXT_SOURCE_FLAG);
    }

    public void testCustomInFocus() throws Exception {
        AudioAttributes attrb = mCarAudioManager.getAudioAttributesForExternalSource(
                "com.google.test.SOMETHING_SPECIAL");
        assertNotNull(attrb);
        checkSingleRequestRelease(attrb, AudioManager.AUDIOFOCUS_GAIN, new int[] {0, 0, 1, 0},
                0, VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.EXT_SOURCE_FLAG);
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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

    public void testExternalRadioExternalNav() throws Exception {
        // android radio
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
        assertArrayEquals(new int[] {1, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);

        //external nav
        AudioFocusListener listenerNav = new AudioFocusListener();
        AudioAttributes extNavAttributes = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_EXT_NAV_GUIDANCE);
        res = doRequestFocus(mAudioManager, listenerNav,
                extNavAttributes, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN,
                request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.RADIO_FLAG |
                VehicleAudioContextFlag.EXT_SOURCE_FLAG, request[3]);
        assertArrayEquals(new int[] {1 | 1<<4, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);

        mAudioManager.abandonAudioFocus(listenerNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.RADIO_FLAG, request[3]);
        assertArrayEquals(new int[] {1, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);

        mAudioManager.abandonAudioFocus(listenerRadio);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                0);
    }

    public void testMediaExternalNav() throws Exception {
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);

        //external nav
        AudioFocusListener listenerNav = new AudioFocusListener();
        AudioAttributes extNavAttributes = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_EXT_NAV_GUIDANCE);
        res = doRequestFocus(mAudioManager, listenerNav,
                extNavAttributes, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN,
                request[0]);
        assertEquals(0x1, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG |
                VehicleAudioContextFlag.EXT_SOURCE_FLAG, request[3]);
        assertArrayEquals(new int[] {1<<4, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0x1,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);

        mAudioManager.abandonAudioFocus(listenerNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(0x1 << VehicleAudioStream.STREAM0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.MUSIC_FLAG, request[3]);
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);

        mAudioManager.abandonAudioFocus(listenerMusic);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                0);
    }

    /**
     * Test internal nav - external nav case.
     * External nav takes the same physical stream as internal nav. So internal nav
     * will be lost while external nav is played. This should not happen in real case when
     * AppFocus is used, but this test is to make sure that audio focus works as expected.
     */
    public void testNavExternalNav() throws Exception {
        // android nav
        AudioFocusListener listenerIntNav = new AudioFocusListener();
        AudioAttributes intNavAttributes = mCarAudioManager.getAudioAttributesForCarUsage(
                CarAudioManager.CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE);
        int res = doRequestFocus(mAudioManager, listenerIntNav, intNavAttributes,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK,
                request[0]);
        assertEquals(0x2, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.NAVIGATION_FLAG, request[3]);
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);

        //external nav
        AudioFocusListener listenerExtNav = new AudioFocusListener();
        AudioAttributes extNavAttributes = mCarAudioManager.getAudioAttributesForExternalSource(
                CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_EXT_NAV_GUIDANCE);
        res = doRequestFocus(mAudioManager, listenerExtNav,
                extNavAttributes, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN,
                request[0]);
        assertEquals(0, request[1]);
        assertEquals(VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                request[2]);
        assertEquals(VehicleAudioContextFlag.EXT_SOURCE_FLAG, request[3]);
        assertArrayEquals(new int[] {1<<4, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0x1,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG);

        mAudioManager.abandonAudioFocus(listenerExtNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK,
                request[0]);
        assertEquals(0x2, request[1]);
        assertEquals(0, request[2]);
        assertEquals(VehicleAudioContextFlag.NAVIGATION_FLAG, request[3]);
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);

        mAudioManager.abandonAudioFocus(listenerIntNav);
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_RELEASE, request[0]);
        assertEquals(0, request[1]);
        assertEquals(0, request[2]);
        assertEquals(0, request[3]);
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                0);
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {1, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {1, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {1, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                0,
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    private void checkSingleRequestRelease(AudioAttributes attrb, int androidFocusToRequest,
            int[] expectedExtRouting, int expectedStreams,
            int expectedExtState, int expectedContexts) throws Exception {
        AudioFocusListener lister = new AudioFocusListener();
        int res = mCarAudioManager.requestAudioFocus(lister, attrb, androidFocusToRequest, 0);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        int expectedFocusRequest = VehicleAudioFocusRequest.REQUEST_RELEASE;
        int response = VehicleAudioFocusState.STATE_LOSS;
        switch (androidFocusToRequest) {
            case AudioManager.AUDIOFOCUS_GAIN:
                expectedFocusRequest = VehicleAudioFocusRequest.REQUEST_GAIN;
                response = VehicleAudioFocusState.STATE_GAIN;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                expectedFocusRequest =
                    VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT;
                response = VehicleAudioFocusState.STATE_GAIN_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                expectedFocusRequest =
                    VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK;
                response = VehicleAudioFocusState.STATE_GAIN_TRANSIENT;
                break;
        }
        assertEquals(expectedFocusRequest, request[0]);
        assertEquals(expectedStreams, request[1]);
        assertEquals(expectedExtState, request[2]);
        assertEquals(expectedContexts, request[3]);
        assertArrayEquals(expectedExtRouting, mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_LOSS,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);
    }

    public void testRadioMute() throws Exception {
        testMediaMute(CarAudioManager.CAR_AUDIO_USAGE_RADIO,
                0,
                VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG,
                VehicleAudioContextFlag.RADIO_FLAG);
    }

    public void testMusicMute() throws Exception {
        testMediaMute(CarAudioManager.CAR_AUDIO_USAGE_MUSIC,
                0x1,
                0,
                VehicleAudioContextFlag.MUSIC_FLAG);
    }

    private void testMediaMute(int mediaUsage, int primaryStream, int extFocusFlag,
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
        if (mediaUsage == CarAudioManager.CAR_AUDIO_USAGE_RADIO) {
            assertArrayEquals(new int[] {1, 0, 0, 0},
                    mExtRoutingHintPropertyHandler.getLastHint());
        } else {
            assertArrayEquals(new int[] {0, 0, 0, 0},
                    mExtRoutingHintPropertyHandler.getLastHint());
        }
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
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
        assertArrayEquals(new int[] {0, 0, 0, 0},
                mExtRoutingHintPropertyHandler.getLastHint());
        assertTrue(carAudioManager.isMediaMuted());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                0,
                VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG);
        // now unmute it. media should resume.
        assertTrue(carAudioManager.isMediaMuted());
        assertFalse(carAudioManager.setMediaMute(false));
        assertFalse(carAudioManager.isMediaMuted());
        request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        assertEquals(VehicleAudioFocusRequest.REQUEST_GAIN, request[0]);
        assertEquals(primaryStream, request[1]);
        assertEquals(extFocusFlag,
                request[2]);
        assertEquals(mediaContext, request[3]);
        if (mediaUsage == CarAudioManager.CAR_AUDIO_USAGE_RADIO) {
            assertArrayEquals(new int[] {1, 0, 0, 0},
                    mExtRoutingHintPropertyHandler.getLastHint());
        } else {
            assertArrayEquals(new int[] {0, 0, 0, 0},
                    mExtRoutingHintPropertyHandler.getLastHint());
        }
        assertFalse(carAudioManager.isMediaMuted());
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                primaryStream,
                extFocusFlag);
        assertFalse(carAudioManager.isMediaMuted());
        // release focus
        mAudioManager.abandonAudioFocus(listenerMedia);
    }

    protected static class AudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        private final Semaphore mFocusChangeWait = new Semaphore(0);
        private int mLastFocusChange;

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

    protected static class FocusPropertyHandler implements VehicleHalPropertyHandler {

        private int mState = VehicleAudioFocusState.STATE_LOSS;
        private int mStreams = 0;
        private int mExtFocus = 0;
        private int mRequest;
        private int mRequestedStreams;
        private int mRequestedExtFocus;
        private int mRequestedAudioContexts;
        private final MockedCarTestBase mCarTest;

        private final Semaphore mSetWaitSemaphore = new Semaphore(0);

        public FocusPropertyHandler(MockedCarTestBase carTest) {
            mCarTest = carTest;
        }

        public void sendAudioFocusState(int state, int streams, int extFocus) {
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

        public int[] waitForAudioFocusRequest(long timeoutMs) throws Exception {
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
            Log.i(TAG, "onPropertySet, prop: 0x" + toHexString(value.prop));
            assertEquals(AUDIO_FOCUS, value.prop);
            ArrayList<Integer> v = value.value.int32Values;
            synchronized (this) {
                mRequest = v.get(VehicleAudioFocusIndex.FOCUS);
                mRequestedStreams = v.get(VehicleAudioFocusIndex.STREAMS);
                mRequestedExtFocus = v.get(VehicleAudioFocusIndex.EXTERNAL_FOCUS_STATE);
                mRequestedAudioContexts = v.get(VehicleAudioFocusIndex.AUDIO_CONTEXTS);
            }
            Log.i(TAG, "onPropertySet, values: " + Arrays.toString(v.toArray()));
            mSetWaitSemaphore.release();
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertEquals(AUDIO_FOCUS, value.prop);
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

    private static class ExtRoutingHintPropertyHandler extends FailingPropertyHandler {
        private int[] mLastHint = {0, 0, 0, 0};

        public int[] getLastHint() {
            int[] lastHint = new int[mLastHint.length];
            synchronized (this) {
                System.arraycopy(mLastHint, 0, lastHint, 0, mLastHint.length);
            }
            return lastHint;
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            assertEquals(VehicleProperty.AUDIO_EXT_ROUTING_HINT, value.prop);
            assertEquals(mLastHint.length, value.value.int32Values.size());
            synchronized (this) {
                for (int i = 0; i < mLastHint.length; i++) {
                    mLastHint[i] = value.value.int32Values.get(i);
                }
            }
        }
    }
}
