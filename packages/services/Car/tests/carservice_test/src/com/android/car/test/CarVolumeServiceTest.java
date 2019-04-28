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

import static com.android.car.test.AudioTestUtils.doRequestFocus;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioContextFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioExtFocusFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusState;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioVolumeIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.IVolumeController;
import android.os.RemoteException;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.android.car.VolumeUtils;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;
import com.android.internal.annotations.GuardedBy;

import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.List;

@MediumTest
public class CarVolumeServiceTest extends MockedCarTestBase {
    private static final String TAG = CarVolumeServiceTest.class.getSimpleName();

    private static final int MIN_VOL = 1;
    private static final int MAX_VOL = 20;
    private static final long TIMEOUT_MS = 3000;
    private static final long POLL_INTERVAL_MS = 50;

    private static final int[] LOGICAL_STREAMS = {
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_DTMF,
    };

    private CarAudioManager mCarAudioManager;
    private AudioManager mAudioManager;

    @Override
    protected synchronized void setUp() throws Exception {
        super.setUp();
        // AudioManager should be created in main thread to get focus event. :(
        runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
        });
    }

    private SingleChannelVolumeHandler setupExternalVolumeEmulation(boolean supportAudioContext)
            throws Exception {
        List<Integer> maxs = new ArrayList<>();
        int supportedAudioContext = 0;
        if (!supportAudioContext) {
            // set up 2 physical streams
            maxs.add(MAX_VOL);
            maxs.add(MAX_VOL);
        } else {
            // add supported contexts
            int[] contexts = VolumeUtils.CAR_AUDIO_CONTEXT;
            for (int context : contexts) {
                supportedAudioContext |= context;
                maxs.add(MAX_VOL);
            }
        }
        SingleChannelVolumeHandler handler =
                startVolumeEmulation(supportedAudioContext, maxs);
        mCarAudioManager = (CarAudioManager) getCar().getCarManager(Car.AUDIO_SERVICE);
        return handler;
    }

    public void testUnknownVolumeChange() throws Exception {
        SingleChannelVolumeHandler volumeHandler = setupExternalVolumeEmulation(true);
        VolumeController volumeController = new VolumeController();
        mCarAudioManager.setVolumeController(volumeController);
        mCarAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0);
        // give focus to music, now current context becomes VehicleAudioContextFlag.MUSIC_FLAG
        CarAudioFocusTest.AudioFocusListener listenerMusic =
                new CarAudioFocusTest.AudioFocusListener();
        int res = doRequestFocus(mAudioManager, listenerMusic,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
        int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
        mAudioFocusPropertyHandler.sendAudioFocusState(
                VehicleAudioFocusState.STATE_GAIN,
                request[1],
                VehicleAudioExtFocusFlag.NONE_FLAG);

        // let vehicle hal report volume change from unknown context, we should map it to the
        // current context (music).
        volumeHandler.injectVolumeEvent(VehicleAudioContextFlag.UNKNOWN_FLAG, 3);
        // now music volume should be recorded as 3.
        volumeVerificationPoll(createStreamVolPair(AudioManager.STREAM_MUSIC, 3));
    }

    public void testVolumeLimits() throws Exception {
        setupExternalVolumeEmulation(false);
        for (int stream : LOGICAL_STREAMS) {
            assertEquals(MAX_VOL, mCarAudioManager.getStreamMaxVolume(stream));
        }
    }

    public void testVolumeSet() throws Exception {
        try {
            setupExternalVolumeEmulation(false);
            int callVol = 10;
            int musicVol = 15;
            mCarAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVol, 0);
            mCarAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callVol, 0);

            volumeVerificationPoll(createStreamVolPair(AudioManager.STREAM_MUSIC, musicVol),
                    createStreamVolPair(AudioManager.STREAM_VOICE_CALL, callVol));

            musicVol = MAX_VOL + 1;
            mCarAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVol, 0);

            volumeVerificationPoll(createStreamVolPair(AudioManager.STREAM_MUSIC, MAX_VOL),
                    createStreamVolPair(AudioManager.STREAM_VOICE_CALL, callVol));
        } catch (CarNotConnectedException e) {
            fail("Car not connected");
        }
    }

    public void testSuppressVolumeUI() {
        try {
            setupExternalVolumeEmulation(false);
            VolumeController volumeController = new VolumeController();
            mCarAudioManager.setVolumeController(volumeController);

            // first give focus to system sound
            CarAudioFocusTest.AudioFocusListener listenerMusic =
                    new CarAudioFocusTest.AudioFocusListener();
            int res = doRequestFocus(mAudioManager, listenerMusic,
                    AudioManager.STREAM_SYSTEM,
                    AudioManager.AUDIOFOCUS_GAIN);
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
            mAudioFocusPropertyHandler.sendAudioFocusState(
                    VehicleAudioFocusState.STATE_GAIN,
                    request[1],
                    VehicleAudioExtFocusFlag.NONE_FLAG);

            // focus gives to Alarm, there should be a audio context change.
            CarAudioFocusTest.AudioFocusListener listenerAlarm = new
                    CarAudioFocusTest.AudioFocusListener();
            AudioAttributes callAttrib = (new AudioAttributes.Builder()).
                    setUsage(AudioAttributes.USAGE_ALARM).
                    build();
            res = doRequestFocus(mAudioManager, listenerAlarm, callAttrib,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
            mAudioFocusPropertyHandler.sendAudioFocusState(
                    VehicleAudioFocusState.STATE_GAIN, request[1],
                    VehicleAudioExtFocusFlag.NONE_FLAG);
            // should not show UI
            volumeChangeVerificationPoll(AudioManager.STREAM_ALARM, false, volumeController);

            int alarmVol = mCarAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            // set alarm volume with show_ui flag and a different volume
            mCarAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    (alarmVol + 1) % mCarAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    AudioManager.FLAG_SHOW_UI);
            // should show ui
            volumeChangeVerificationPoll(AudioManager.STREAM_ALARM, true, volumeController);
            mAudioManager.abandonAudioFocus(listenerAlarm);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testVolumeKeys() throws Exception {
        try {
            setupExternalVolumeEmulation(false);
            int musicVol = 10;
            mCarAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVol, 0);
            int callVol = 12;
            mCarAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callVol, 0);

            CarAudioFocusTest.AudioFocusListener listenerMusic =
                    new CarAudioFocusTest.AudioFocusListener();
            int res = doRequestFocus(mAudioManager, listenerMusic,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            int[] request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
            mAudioFocusPropertyHandler.sendAudioFocusState(
                    VehicleAudioFocusState.STATE_GAIN,
                    request[1],
                    VehicleAudioExtFocusFlag.NONE_FLAG);


            assertEquals(musicVol, mCarAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
            sendVolumeKey(true /*vol up*/);
            musicVol++;
            volumeVerificationPoll(createStreamVolPair(AudioManager.STREAM_MUSIC, musicVol));

            // call start
            CarAudioFocusTest.AudioFocusListener listenerCall = new
                    CarAudioFocusTest.AudioFocusListener();
            AudioAttributes callAttrib = (new AudioAttributes.Builder()).
                    setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).
                    build();
            doRequestFocus(mAudioManager, listenerCall, callAttrib,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            request = mAudioFocusPropertyHandler.waitForAudioFocusRequest(TIMEOUT_MS);
            mAudioFocusPropertyHandler.sendAudioFocusState(
                    VehicleAudioFocusState.STATE_GAIN, request[1],
                    VehicleAudioExtFocusFlag.NONE_FLAG);

            sendVolumeKey(true /*vol up*/);
            callVol++;
            volumeVerificationPoll(createStreamVolPair(AudioManager.STREAM_MUSIC, musicVol),
                    createStreamVolPair(AudioManager.STREAM_VOICE_CALL, callVol));
        } catch (CarNotConnectedException | InterruptedException e) {
            fail(e.toString());
        }
    }

    private Pair<Integer, Integer> createStreamVolPair(int stream, int vol) {
        return new Pair<>(stream, vol);
    }

    private void volumeVerificationPoll(Pair<Integer, Integer>... expectedStreamVolPairs) {
        boolean isVolExpected = false;
        int timeElapsedMs = 0;
        try {
            while (!isVolExpected && timeElapsedMs <= TIMEOUT_MS) {
                Thread.sleep(POLL_INTERVAL_MS);
                isVolExpected = true;
                for (Pair<Integer, Integer> vol : expectedStreamVolPairs) {
                    if (mCarAudioManager.getStreamVolume(vol.first) != vol.second) {
                        isVolExpected = false;
                        break;
                    }
                }
                timeElapsedMs += POLL_INTERVAL_MS;
            }
            assertEquals(isVolExpected, true);
        } catch (InterruptedException | CarNotConnectedException e) {
            fail(e.toString());
        }
    }

    private void volumeChangeVerificationPoll(int stream, boolean showUI,
            VolumeController controller) {
        boolean isVolExpected = false;
        int timeElapsedMs = 0;
        try {
            while (!isVolExpected && timeElapsedMs <= TIMEOUT_MS) {
                Thread.sleep(POLL_INTERVAL_MS);
                Pair<Integer, Integer> volChange = controller.getLastVolumeChanges();
                if (volChange.first == stream
                        && (((volChange.second.intValue() & AudioManager.FLAG_SHOW_UI) != 0)
                        == showUI)) {
                    isVolExpected = true;
                    break;
                }
                timeElapsedMs += POLL_INTERVAL_MS;
            }
            assertEquals(true, isVolExpected);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    private class SingleChannelVolumeHandler implements VehicleHalPropertyHandler {
        private final List<Integer> mMaxs;
        private final SparseIntArray mCurrent;

        public SingleChannelVolumeHandler(List<Integer> maxs) {
            mMaxs = maxs;
            int size = maxs.size();
            mCurrent = new SparseIntArray(size);
            // initialize the vol to be the min volume.
            for (int i = 0; i < size; i++) {
                mCurrent.put(i, mMaxs.get(i));
            }
        }

        public void injectVolumeEvent(int context, int volume) {
            getMockedVehicleHal().injectEvent(
                    VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_VOLUME)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(context, volume, 0)
                            .build());
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            ArrayList<Integer> v = value.value.int32Values;
            int stream = v.get(VehicleAudioVolumeIndex.STREAM);
            int volume = v.get(VehicleAudioVolumeIndex.VOLUME);
            int state = v.get(VehicleAudioVolumeIndex.STATE);
            Log.d(TAG, "state " + state);

            mCurrent.put(stream, volume);
            getMockedVehicleHal().injectEvent(
                    VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_VOLUME)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(stream, volume, state)
                            .build());
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            int stream = value.value.int32Values.get(VehicleAudioVolumeIndex.STREAM);
            int volume = mCurrent.get(stream);
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_VOLUME)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(stream, volume, 0)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, int zones, float sampleRate) {
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
        }
    }

    private final CarAudioFocusTest.FocusPropertyHandler mAudioFocusPropertyHandler =
            new CarAudioFocusTest.FocusPropertyHandler(this);

    private final VehicleHalPropertyHandler mAudioRoutingPolicyPropertyHandler =
            new VehicleHalPropertyHandler() {
                @Override
                public void onPropertySet(VehiclePropValue value) {
                    //TODO
                }

                @Override
                public VehiclePropValue onPropertyGet(VehiclePropValue value) {
                    fail("cannot get");
                    return null;
                }

                @Override
                public void onPropertySubscribe(int property, int zones, float sampleRate) {
                    fail("cannot subscribe");
                }

                @Override
                public void onPropertyUnsubscribe(int property) {
                    fail("cannot unsubscribe");
                }
            };

    private final VehicleHalPropertyHandler mHWKeyHandler = new VehicleHalPropertyHandler() {
                @Override
                public void onPropertySet(VehiclePropValue value) {
                    //TODO
                }

                @Override
                public VehiclePropValue onPropertyGet(VehiclePropValue value) {
                    return VehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(0, 0, 0, 0)
                            .build();
                }

                @Override
                public void onPropertySubscribe(int property, int zones, float sampleRate) {
                    //
                }

                @Override
                public void onPropertyUnsubscribe(int property) {
                    //
                }
            };

    private SingleChannelVolumeHandler startVolumeEmulation(int supportedAudioVolumeContext,
            List<Integer> maxs) {
        SingleChannelVolumeHandler singleChannelVolumeHandler =
                new SingleChannelVolumeHandler(maxs);
        int zones = (1<<maxs.size()) - 1;

        ArrayList<Integer> audioVolumeConfigArray =
                Lists.newArrayList(
                        supportedAudioVolumeContext,
                        0  /* capability flag*/,
                        0, /* reserved */
                        0  /* reserved */);
        audioVolumeConfigArray.addAll(maxs);

        addProperty(VehicleProperty.AUDIO_VOLUME, singleChannelVolumeHandler)
                        .setConfigArray(audioVolumeConfigArray)
                        .setSupportedAreas(zones);

        addProperty(VehicleProperty.HW_KEY_INPUT, mHWKeyHandler)
                .setAccess(VehiclePropertyAccess.READ);

        addProperty(VehicleProperty.AUDIO_FOCUS, mAudioFocusPropertyHandler);

        addProperty(VehicleProperty.AUDIO_ROUTING_POLICY, mAudioRoutingPolicyPropertyHandler)
                        .setAccess(VehiclePropertyAccess.WRITE);

        addStaticProperty(VehicleProperty.AUDIO_HW_VARIANT,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_HW_VARIANT)
                        .addIntValue(-1)
                        .build())
                .setConfigArray(Lists.newArrayList(0));

        reinitializeMockedHal();
        return singleChannelVolumeHandler;
    }

    private void sendVolumeKey(boolean volUp) {
        int[] actionDown = {
                VehicleHwKeyInputAction.ACTION_DOWN,
                volUp ? KeyEvent.KEYCODE_VOLUME_UP : KeyEvent.KEYCODE_VOLUME_DOWN, 0, 0};

        VehiclePropValue injectValue =
                VehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .addIntValue(actionDown)
                        .build();

        getMockedVehicleHal().injectEvent(injectValue);

        int[] actionUp = {
                VehicleHwKeyInputAction.ACTION_UP,
                volUp ? KeyEvent.KEYCODE_VOLUME_UP : KeyEvent.KEYCODE_VOLUME_DOWN, 0, 0 };

        injectValue =
                VehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .addIntValue(actionUp)
                        .build();

        getMockedVehicleHal().injectEvent(injectValue);
    }

    private static class VolumeController extends IVolumeController.Stub {
        @GuardedBy("this")
        private int mLastStreamChanged = -1;

        @GuardedBy("this")
        private int mLastFlags = -1;

        public synchronized Pair<Integer, Integer> getLastVolumeChanges() {
            return new Pair<>(mLastStreamChanged, mLastFlags);
        }

        @Override
        public void displaySafeVolumeWarning(int flags) throws RemoteException {}

        @Override
        public void volumeChanged(int streamType, int flags) throws RemoteException {
            synchronized (this) {
                mLastStreamChanged = streamType;
                mLastFlags = flags;
            }
        }

        @Override
        public void masterMuteChanged(int flags) throws RemoteException {}

        @Override
        public void setLayoutDirection(int layoutDirection) throws RemoteException {
        }

        @Override
        public void dismiss() throws RemoteException {
        }

        @Override
        public void setA11yMode(int mode) throws RemoteException {
        }
    }
}
