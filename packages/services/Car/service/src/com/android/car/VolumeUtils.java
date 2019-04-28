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

package com.android.car;

import android.car.settings.CarSettings;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioContextFlag;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.util.Log;
import android.util.SparseArray;

import java.util.Arrays;

public class VolumeUtils {
    private static final String TAG = "VolumeUtils";

    public static final int[] LOGICAL_STREAMS = {
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_DTMF,
    };

    public static final int[] CAR_AUDIO_CONTEXT = {
            VehicleAudioContextFlag.MUSIC_FLAG,
            VehicleAudioContextFlag.NAVIGATION_FLAG,
            VehicleAudioContextFlag.VOICE_COMMAND_FLAG,
            VehicleAudioContextFlag.CALL_FLAG,
            VehicleAudioContextFlag.RINGTONE_FLAG,
            VehicleAudioContextFlag.ALARM_FLAG,
            VehicleAudioContextFlag.NOTIFICATION_FLAG,
            VehicleAudioContextFlag.UNKNOWN_FLAG,
            VehicleAudioContextFlag.SAFETY_ALERT_FLAG,
            VehicleAudioContextFlag.CD_ROM_FLAG,
            VehicleAudioContextFlag.AUX_AUDIO_FLAG,
            VehicleAudioContextFlag.SYSTEM_SOUND_FLAG,
            VehicleAudioContextFlag.RADIO_FLAG
    };

    public static final SparseArray<String> CAR_AUDIO_CONTEXT_SETTINGS = new SparseArray<>();
    static {
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.UNKNOWN_FLAG,
                CarSettings.Global.KEY_VOLUME_MUSIC);
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.MUSIC_FLAG,
                CarSettings.Global.KEY_VOLUME_MUSIC);
        CAR_AUDIO_CONTEXT_SETTINGS.put(
                VehicleAudioContextFlag.NAVIGATION_FLAG,
                CarSettings.Global.KEY_VOLUME_NAVIGATION);
        CAR_AUDIO_CONTEXT_SETTINGS.put(
                VehicleAudioContextFlag.VOICE_COMMAND_FLAG,
                CarSettings.Global.KEY_VOLUME_VOICE_COMMAND);
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.CALL_FLAG,
                CarSettings.Global.KEY_VOLUME_CALL);
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.RINGTONE_FLAG,
                CarSettings.Global.KEY_VOLUME_RINGTONE);
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.ALARM_FLAG,
                CarSettings.Global.KEY_VOLUME_ALARM);
        CAR_AUDIO_CONTEXT_SETTINGS.put(
                VehicleAudioContextFlag.NOTIFICATION_FLAG,
                CarSettings.Global.KEY_VOLUME_NOTIFICATION);
        CAR_AUDIO_CONTEXT_SETTINGS.put(
                VehicleAudioContextFlag.SAFETY_ALERT_FLAG,
                CarSettings.Global.KEY_VOLUME_SAFETY_ALERT);
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.CD_ROM_FLAG,
                CarSettings.Global.KEY_VOLUME_CD_ROM);
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.AUX_AUDIO_FLAG,
                CarSettings.Global.KEY_VOLUME_AUX);
        CAR_AUDIO_CONTEXT_SETTINGS.put(
                VehicleAudioContextFlag.SYSTEM_SOUND_FLAG,
                CarSettings.Global.KEY_VOLUME_SYSTEM_SOUND);
        CAR_AUDIO_CONTEXT_SETTINGS.put(VehicleAudioContextFlag.RADIO_FLAG,
                CarSettings.Global.KEY_VOLUME_RADIO);
    }

    public static String streamToName(int stream) {
        switch (stream) {
            case AudioManager.STREAM_ALARM: return "Alarm";
            case AudioManager.STREAM_MUSIC: return "Music";
            case AudioManager.STREAM_NOTIFICATION: return "Notification";
            case AudioManager.STREAM_RING: return "Ring";
            case AudioManager.STREAM_VOICE_CALL: return "Call";
            case AudioManager.STREAM_SYSTEM: return "System";
            case AudioManager.STREAM_DTMF: return "DTMF";
            default: return "Unknown";
        }
    }

    public static int androidStreamToCarContext(int logicalAndroidStream) {
        switch (logicalAndroidStream) {
            case AudioManager.STREAM_VOICE_CALL:
                return VehicleAudioContextFlag.CALL_FLAG;
            case AudioManager.STREAM_SYSTEM:
                return VehicleAudioContextFlag.SYSTEM_SOUND_FLAG;
            case AudioManager.STREAM_RING:
                return VehicleAudioContextFlag.RINGTONE_FLAG;
            case AudioManager.STREAM_MUSIC:
                return VehicleAudioContextFlag.MUSIC_FLAG;
            case AudioManager.STREAM_ALARM:
                return VehicleAudioContextFlag.ALARM_FLAG;
            case AudioManager.STREAM_NOTIFICATION:
                return VehicleAudioContextFlag.NOTIFICATION_FLAG;
            case AudioManager.STREAM_DTMF:
                return VehicleAudioContextFlag.SYSTEM_SOUND_FLAG;
            default:
                return VehicleAudioContextFlag.UNKNOWN_FLAG;
        }
    }

    public static int carContextToAndroidStream(int carContext) {
        switch (carContext) {
            case VehicleAudioContextFlag.CALL_FLAG:
                return AudioManager.STREAM_VOICE_CALL;
            case VehicleAudioContextFlag.RINGTONE_FLAG:
                return AudioManager.STREAM_RING;
            case VehicleAudioContextFlag.SYSTEM_SOUND_FLAG:
                return AudioManager.STREAM_SYSTEM;
            case VehicleAudioContextFlag.NOTIFICATION_FLAG:
                return AudioManager.STREAM_NOTIFICATION;
            case VehicleAudioContextFlag.MUSIC_FLAG:
                return AudioManager.STREAM_MUSIC;
            case VehicleAudioContextFlag.ALARM_FLAG:
                return AudioManager.STREAM_ALARM;
            default:
                return AudioManager.STREAM_MUSIC;
        }
    }

    public static int androidStreamToCarUsage(int logicalAndroidStream) {
        return CarAudioAttributesUtil.getCarUsageFromAudioAttributes(
                new AudioAttributes.Builder()
                        .setLegacyStreamType(logicalAndroidStream).build());
    }

    private final SparseArray<Float[]> mStreamAmplLookup = new SparseArray<>(7);

    private static final float LN_10 = 2.302585093f;
    // From cs/#android/frameworks/av/media/libmedia/AudioSystem.cpp
    private static final float DB_PER_STEP = -.5f;

    private final AudioManager mAudioManager;

    public VolumeUtils(AudioManager audioManager) {
        mAudioManager = audioManager;
        for(int i : LOGICAL_STREAMS) {
            initStreamLookup(i);
        }
    }

    private void initStreamLookup(int streamType) {
        int maxIndex = mAudioManager.getStreamMaxVolume(streamType);
        Float[] amplList = new Float[maxIndex + 1];

        for (int i = 0; i <= maxIndex; i++) {
            amplList[i] = volIndexToAmpl(i, maxIndex);
        }
        Log.d(TAG, streamToName(streamType) + ": " + Arrays.toString(amplList));
        mStreamAmplLookup.put(streamType, amplList);
    }


    public static int closestIndex(float desired, Float[] list) {
        float min = Float.MAX_VALUE;
        int closestIndex = 0;

        for (int i = 0; i < list.length; i++) {
            float diff = Math.abs(list[i] - desired);
            if (diff < min) {
                min = diff;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    public void adjustStreamVol(int stream, int desired, int actual, int maxIndex) {
        float gain = getTrackGain(desired, actual, maxIndex);
        int index = closestIndex(gain, mStreamAmplLookup.get(stream));
        if (index == mAudioManager.getStreamVolume(stream)) {
            return;
        } else {
            mAudioManager.setStreamVolume(stream, index, 0 /*don't show UI*/);
        }
    }

    /**
     * Returns the gain which, when applied to an a stream with volume
     * actualVolIndex, will make the output volume equivalent to a stream with a gain of
     * 1.0 playing on a stream with volume desiredVolIndex.
     *
     * Computing this is non-trivial because the gain is applied on a linear scale while the volume
     * indices map to a log (dB) scale.
     *
     * The computation is copied from cs/#android/frameworks/av/media/libmedia/AudioSystem.cpp
     */
    float getTrackGain(int desiredVolIndex, int actualVolIndex, int maxIndex) {
        if (desiredVolIndex == actualVolIndex) {
            return 1.0f;
        }
        return volIndexToAmpl(desiredVolIndex, maxIndex)
                / volIndexToAmpl(actualVolIndex, maxIndex);
    }

    /**
     * Returns the amplitude corresponding to volIndex. Guaranteed to return a non-negative value.
     */
    private float volIndexToAmpl(int volIndex, int maxIndex) {
        // Normalize volIndex to be in the range [0, 100].
        int volume = (int) ((float) volIndex / maxIndex * 100.0f);
        return logToLinear(volumeToDecibels(volume));
    }

    /**
     * volume is in the range [0, 100].
     */
    private static float volumeToDecibels(int volume) {
        return (100 - volume) * DB_PER_STEP;
    }

    /**
     * Corresponds to the function linearToLog in AudioSystem.cpp.
     */
    private static float logToLinear(float decibels) {
        return decibels < 0.0f ? (float) Math.exp(decibels * LN_10 / 20.0f) : 1.0f;
    }
}
