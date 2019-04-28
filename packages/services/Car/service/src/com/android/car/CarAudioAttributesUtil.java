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

import android.car.media.CarAudioManager;
import android.media.AudioAttributes;
import android.os.Bundle;

/**
 * Utility class to map car usage into AudioAttributes and the other way around.
 */
public class CarAudioAttributesUtil {

    public static final int CAR_AUDIO_USAGE_CARSERVICE_BOTTOM = 100;
    public static final int CAR_AUDIO_USAGE_CARSERVICE_CAR_PROXY = 101;
    public static final int CAR_AUDIO_USAGE_CARSERVICE_MEDIA_MUTE = 102;

    /** Bundle key for storing media type. */
    public static final String KEY_CAR_AUDIO_TYPE = "car_audio_type";

    private static final int CAR_AUDIO_TYPE_DEFAULT = 0;
    private static final int CAR_AUDIO_TYPE_VOICE_COMMAND = 1;
    private static final int CAR_AUDIO_TYPE_SAFETY_ALERT = 2;
    private static final int CAR_AUDIO_TYPE_RADIO = 3;
    private static final int CAR_AUDIO_TYPE_CARSERVICE_BOTTOM = 4;
    private static final int CAR_AUDIO_TYPE_CARSERVICE_CAR_PROXY = 5;
    private static final int CAR_AUDIO_TYPE_CARSERVICE_MEDIA_MUTE = 6;
    private static final int CAR_AUDIO_TYPE_EXTERNAL_SOURCE = 7;

    /** Bundle key for storing routing type which is String. */
    public static final String KEY_EXT_ROUTING_TYPE = "ext_routing_type";

    public static AudioAttributes getAudioAttributesForCarUsage(int carUsage) {
        switch (carUsage) {
            case CarAudioManager.CAR_AUDIO_USAGE_MUSIC:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_MUSIC,
                        AudioAttributes.USAGE_MEDIA);
            case CarAudioManager.CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE: // default to radio
            case CarAudioManager.CAR_AUDIO_USAGE_RADIO:
                return createCustomAudioAttributes(CAR_AUDIO_TYPE_RADIO,
                        AudioAttributes.CONTENT_TYPE_MUSIC, AudioAttributes.USAGE_MEDIA);
            case CarAudioManager.CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_SPEECH,
                        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
            case CarAudioManager.CAR_AUDIO_USAGE_RINGTONE:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_SONIFICATION,
                        AudioAttributes.USAGE_NOTIFICATION_RINGTONE);
            case CarAudioManager.CAR_AUDIO_USAGE_VOICE_CALL:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_SPEECH,
                        AudioAttributes.USAGE_VOICE_COMMUNICATION);
            case CarAudioManager.CAR_AUDIO_USAGE_VOICE_COMMAND:
                return createCustomAudioAttributes(CAR_AUDIO_TYPE_VOICE_COMMAND,
                        AudioAttributes.CONTENT_TYPE_SPEECH,
                        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
            case CarAudioManager.CAR_AUDIO_USAGE_ALARM:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_SONIFICATION,
                        AudioAttributes.USAGE_ALARM);
            case CarAudioManager.CAR_AUDIO_USAGE_NOTIFICATION:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_SONIFICATION,
                        AudioAttributes.USAGE_NOTIFICATION);
            case CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SOUND:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_SONIFICATION,
                        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION);
            case CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT:
                return createCustomAudioAttributes(CAR_AUDIO_TYPE_SAFETY_ALERT,
                        AudioAttributes.CONTENT_TYPE_SONIFICATION,
                        AudioAttributes.USAGE_NOTIFICATION);
            case CAR_AUDIO_USAGE_CARSERVICE_BOTTOM:
                return createCustomAudioAttributes(CAR_AUDIO_TYPE_CARSERVICE_BOTTOM,
                        AudioAttributes.CONTENT_TYPE_UNKNOWN,
                        AudioAttributes.USAGE_UNKNOWN);
            case CAR_AUDIO_USAGE_CARSERVICE_CAR_PROXY:
                return createCustomAudioAttributes(CAR_AUDIO_TYPE_CARSERVICE_CAR_PROXY,
                        AudioAttributes.CONTENT_TYPE_UNKNOWN,
                        AudioAttributes.USAGE_UNKNOWN);
            case CAR_AUDIO_USAGE_CARSERVICE_MEDIA_MUTE:
                return createCustomAudioAttributes(CAR_AUDIO_TYPE_CARSERVICE_MEDIA_MUTE,
                        AudioAttributes.CONTENT_TYPE_MUSIC,
                        AudioAttributes.USAGE_UNKNOWN);
            case CarAudioManager.CAR_AUDIO_USAGE_DEFAULT:
            default:
                return createAudioAttributes(AudioAttributes.CONTENT_TYPE_UNKNOWN,
                        AudioAttributes.USAGE_UNKNOWN);
        }
    }

    public static int getCarUsageFromAudioAttributes(AudioAttributes attr) {
        int usage = attr.getUsage();
        Bundle bundle = attr.getBundle();
        int type = CAR_AUDIO_TYPE_DEFAULT;
        if (bundle != null) {
            type = bundle.getInt(KEY_CAR_AUDIO_TYPE, CAR_AUDIO_TYPE_DEFAULT);
        }
        switch (usage) {
            case AudioAttributes.USAGE_MEDIA:
                switch (type) {
                    case CAR_AUDIO_TYPE_RADIO:
                        return CarAudioManager.CAR_AUDIO_USAGE_RADIO;
                    case CAR_AUDIO_TYPE_EXTERNAL_SOURCE:
                        return CarAudioManager.CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE;
                    default:
                        return CarAudioManager.CAR_AUDIO_USAGE_MUSIC;
                }
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
                if (type == CAR_AUDIO_TYPE_VOICE_COMMAND) {
                    return CarAudioManager.CAR_AUDIO_USAGE_VOICE_COMMAND;
                } else {
                    return CarAudioManager.CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE;
                }
            case AudioAttributes.USAGE_VOICE_COMMUNICATION:
                return CarAudioManager.CAR_AUDIO_USAGE_VOICE_CALL;
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE:
                return CarAudioManager.CAR_AUDIO_USAGE_RINGTONE;
            case AudioAttributes.USAGE_ALARM:
                return CarAudioManager.CAR_AUDIO_USAGE_ALARM;
            case AudioAttributes.USAGE_NOTIFICATION:
                if (type == CAR_AUDIO_TYPE_SAFETY_ALERT) {
                    return CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT;
                } else {
                    return CarAudioManager.CAR_AUDIO_USAGE_NOTIFICATION;
                }
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION:
                return CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SOUND;
            case AudioAttributes.USAGE_UNKNOWN:
            default: {
                switch (type) {
                    case CAR_AUDIO_TYPE_CARSERVICE_BOTTOM:
                        return CAR_AUDIO_USAGE_CARSERVICE_BOTTOM;
                    case CAR_AUDIO_TYPE_CARSERVICE_CAR_PROXY:
                        return CAR_AUDIO_USAGE_CARSERVICE_CAR_PROXY;
                    case CAR_AUDIO_TYPE_CARSERVICE_MEDIA_MUTE:
                        return CAR_AUDIO_USAGE_CARSERVICE_MEDIA_MUTE;
                    default:
                        return CarAudioManager.CAR_AUDIO_USAGE_DEFAULT;
                }
            }
        }
    }

    private static AudioAttributes createAudioAttributes(int contentType, int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        return builder.setContentType(contentType).setUsage(usage).build();
    }

    private static AudioAttributes createCustomAudioAttributes(int carAudioType,
            int contentType, int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_CAR_AUDIO_TYPE, carAudioType);
        return builder.setContentType(contentType).setUsage(usage).addBundle(bundle).build();
    }

    public static AudioAttributes getCarRadioAttributes(String radioType) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_CAR_AUDIO_TYPE, CAR_AUDIO_TYPE_RADIO);
        bundle.putString(KEY_EXT_ROUTING_TYPE, radioType);
        return builder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).
                setUsage(AudioAttributes.USAGE_MEDIA).addBundle(bundle).build();
    }

    public static AudioAttributes getCarExtSourceAttributes(String externalSourceType) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_CAR_AUDIO_TYPE, CAR_AUDIO_TYPE_EXTERNAL_SOURCE);
        bundle.putString(KEY_EXT_ROUTING_TYPE, externalSourceType);
        return builder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).
                setUsage(AudioAttributes.USAGE_MEDIA).addBundle(bundle).build();
    }

    /**
     * Get ext routing type from given AudioAttributes.
     * @param attr
     * @return {@link CarAudioManager#CAR_RADIO_TYPE_AM_FM} if ext routing info does not exist.
     */
    public static String getExtRouting(AudioAttributes attr) {
        Bundle bundle = attr.getBundle();
        String extRouting = CarAudioManager.CAR_RADIO_TYPE_AM_FM;
        if (bundle != null) {
            extRouting = bundle.getString(KEY_EXT_ROUTING_TYPE);
        }
        return extRouting;
    }
}
