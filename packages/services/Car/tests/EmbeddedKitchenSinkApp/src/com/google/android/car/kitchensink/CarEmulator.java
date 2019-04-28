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

package com.google.android.car.kitchensink;

import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_VOLUME_LIMIT;

import com.google.android.collect.Lists;

import android.car.Car;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusRequest;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusState;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioVolumeIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioVolumeLimitIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.os.SystemClock;
import android.util.SparseIntArray;

import com.android.car.ICarImpl;
import com.android.car.SystemInterface;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;
import com.android.car.vehiclehal.test.VehiclePropConfigBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CarEmulator {

    private final Car mCar;
    private final MockedVehicleHal mHalEmulator;

    private final AudioFocusPropertyHandler mAudioFocusPropertyHandler =
            new AudioFocusPropertyHandler();
    private final AudioVolumeLimitPropertyHandler mAudioVolumeLimitPropertyHandler =
            new AudioVolumeLimitPropertyHandler();

    private static final Integer[] STREAM_MAX_VOLUME = {30, 30};

    public static CarEmulator create(Context context) {
        CarEmulator emulator = new CarEmulator(context);
        emulator.init();
        return emulator;
    }

    private CarEmulator(Context context) {
        mHalEmulator = new MockedVehicleHal();
        ICarImpl carService = new ICarImpl(context, mHalEmulator,
                SystemInterface.getDefault(context), null /* error notifier */);
        mCar = new Car(context, carService, null /* Handler */);
    }

    public Car getCar() {
        return mCar;
    }

    private void init() {
        final int streamCount = STREAM_MAX_VOLUME.length;
        SingleChannelVolumeHandler volumeHandler = new SingleChannelVolumeHandler(streamCount);

        int supportedStreams = (1 << streamCount) - 1;  // A bitwise mask of supported streams.

        List<Integer> audioVolumeConfigArray = Lists.newArrayList(supportedStreams, 0, 0, 0);
        audioVolumeConfigArray.addAll(Arrays.asList(STREAM_MAX_VOLUME));

        mHalEmulator.addProperty(
                VehiclePropConfigBuilder.newBuilder(VehicleProperty.AUDIO_FOCUS).build(),
                mAudioFocusPropertyHandler);
        //TODO(b/32665590): no internal properties in Vehicle Hal 2.0
//        mHalEmulator.addProperty(
//                VehiclePropConfigUtil.getBuilder(
//                        INTERNAL_AUDIO_STREAM_STATE,
//                        VehiclePropertyAccess.READ_WRITE,
//                        VehiclePropertyChangeMode.ON_CHANGE,
//                        VehicleValueType.VEHICLE_VALUE_TYPE_INT32_VEC2,
//                        VehiclePermissionModel.SYSTEM_APP_ONLY,
//                        0 /*configFlags*/, 0 /*sampleRateMax*/, 0 /*sampleRateMin*/).build(),
//                        NoOpPropertyHandler);
        mHalEmulator.addProperty(
                VehiclePropConfigBuilder.newBuilder(VehicleProperty.AUDIO_VOLUME)
                        .setConfigArray(audioVolumeConfigArray)
                        .build(),
                volumeHandler);
        mHalEmulator.addProperty(
                VehiclePropConfigBuilder.newBuilder(VehicleProperty.AUDIO_VOLUME_LIMIT).build(),
                mAudioVolumeLimitPropertyHandler);

        mHalEmulator.addProperty(
                VehiclePropConfigBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                        .setAccess(VehiclePropertyAccess.READ)
                        .build(),
                mHWKeyHandler);
    }

    public void start() {
    }

    public void stop() {
    }

    public void setAudioFocusControl(boolean reject) {
        mAudioFocusPropertyHandler.setAudioFocusControl(reject);
    }

    public void injectKey(int keyCode, int action) {
        VehiclePropValue injectValue =
                VehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                        .setTimestamp()
                        .addIntValue(action, keyCode, 0, 0)
                        .build();

        mHalEmulator.injectEvent(injectValue);
    }

    public void injectKey(int keyCode) {
        injectKey(keyCode, VehicleHwKeyInputAction.ACTION_DOWN);
        injectKey(keyCode, VehicleHwKeyInputAction.ACTION_UP);
    }

    private final VehicleHalPropertyHandler mHWKeyHandler =
            new VehicleHalPropertyHandler() {
                @Override
                public VehiclePropValue onPropertyGet(VehiclePropValue value) {
                    return VehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(0, 0, 0, 0)
                            .build();

                }
            };

    private class AudioFocusPropertyHandler implements VehicleHalPropertyHandler {
        private boolean mRejectFocus;
        private int mCurrentFocusState = VehicleAudioFocusState.STATE_LOSS;
        private int mCurrentFocusStreams = 0;
        private int mCurrentFocusExtState = 0;

        void setAudioFocusControl(boolean reject) {
            boolean inject = false;
            synchronized (this) {
                if (reject) {
                    if (!mRejectFocus) {
                        mCurrentFocusState = VehicleAudioFocusState.STATE_LOSS_TRANSIENT_EXLCUSIVE;
                        mCurrentFocusStreams = 0;
                        mCurrentFocusExtState = 0;
                        mRejectFocus = true;
                        inject = true;
                    }
                } else {
                    if (mRejectFocus) {
                        mCurrentFocusState = VehicleAudioFocusState.STATE_LOSS;
                        mCurrentFocusStreams = 0;
                        mCurrentFocusExtState = 0;
                        inject = true;
                    }
                    mRejectFocus = false;
                }
            }

            if (inject) {
                injectCurrentStateToHal();
            }
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            synchronized (this) {
                if (!mRejectFocus) {
                    ArrayList<Integer> v = value.value.int32Values;

                    int request = v.get(VehicleAudioFocusIndex.FOCUS);
                    int requestedStreams = v.get(VehicleAudioFocusIndex.STREAMS);
                    int requestedExtFocus = v.get(VehicleAudioFocusIndex.EXTERNAL_FOCUS_STATE);
                    int response = VehicleAudioFocusState.STATE_LOSS;
                    switch (request) {
                        case VehicleAudioFocusRequest.REQUEST_GAIN:
                            response = VehicleAudioFocusState.STATE_GAIN;
                            break;
                        case VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT:
                        case VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK:
                            response =
                                VehicleAudioFocusState.STATE_GAIN_TRANSIENT;
                            break;
                        case VehicleAudioFocusRequest.REQUEST_RELEASE:
                            response = VehicleAudioFocusState.STATE_LOSS;
                            break;
                    }
                    mCurrentFocusState = response;
                    mCurrentFocusStreams = requestedStreams;
                    mCurrentFocusExtState = requestedExtFocus;
                }
            }
            injectCurrentStateToHal();
        }

        private void injectCurrentStateToHal() {
            mHalEmulator.injectEvent(VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_FOCUS)
                    .setTimestamp()
                    .addIntValue(mCurrentFocusState, mCurrentFocusStreams, mCurrentFocusExtState, 0)
                    .build());
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_FOCUS)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(mCurrentFocusState, mCurrentFocusStreams, mCurrentFocusExtState, 0)
                    .build();
        }
    }

    private static class AudioVolumeLimitPropertyHandler implements VehicleHalPropertyHandler {
        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            int stream = value.value.int32Values.get(VehicleAudioVolumeLimitIndex.STREAM);

            return VehiclePropValueBuilder.newBuilder(AUDIO_VOLUME_LIMIT)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(stream, 20)
                    .build();
        }
    }

    private class SingleChannelVolumeHandler implements VehicleHalPropertyHandler {
        private final SparseIntArray mCurrent;

        SingleChannelVolumeHandler(int streamCount) {
            mCurrent = new SparseIntArray(streamCount);
            // Initialize the vol to be the min (mute) volume.
            for (int i = 0; i < streamCount; i++) {
                mCurrent.put(i, 0);
            }
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            ArrayList<Integer> v = value.value.int32Values;

            int stream = v.get(VehicleAudioVolumeIndex.STREAM);
            int volume = v.get(VehicleAudioVolumeIndex.VOLUME);
            int state = v.get(VehicleAudioVolumeIndex.STATE);

            VehiclePropValue injectValue =
                    VehiclePropValueBuilder.newBuilder(VehicleProperty.AUDIO_VOLUME)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(state, volume, state)
                            .build();

            mCurrent.put(stream, volume);
            mHalEmulator.injectEvent(injectValue);
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
    }
}
