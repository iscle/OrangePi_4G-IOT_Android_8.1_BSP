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
package com.android.car;

import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Holds audio routing policy from config.xml. R.array.audioRoutingPolicy can contain
 * multiple policies and VEHICLE_PROPERTY_AUDIO_HW_VARIANT decide which one to use.
 */
public class AudioRoutingPolicy {

    private final int USAGE_TYPE_INVALID = -1;

    private static final String ROUTING_POLICY_FOR_MOCKED_TEST =
            "0:call,media,radio,unknown#1:nav_guidance,voice_command,alarm,notification,system,safety";

    /** Physical stream to logical streams mapping */
    private final int[][] mLogicalStreams;
    /** Logical stream to physical stream mapping */
    private final int[] mPhysicalStreamForLogicalStream;

    public static AudioRoutingPolicy create(Context context, int policyNumber) {
        final Resources res = context.getResources();
        String[] policies = res.getStringArray(R.array.audioRoutingPolicy);
        String policy;
        if (policyNumber > (policies.length - 1)) {
            Log.e(CarLog.TAG_AUDIO, "AudioRoutingPolicy.create got wrong policy number:" +
                    policyNumber + ", num of avaiable policies:" + policies.length);
            policy = policies[0];
        } else if (policyNumber < 0) { // this is special case for mocked testing.
            policy = ROUTING_POLICY_FOR_MOCKED_TEST;
        } else {
            policy = policies[policyNumber];
        }
        return new AudioRoutingPolicy(policy);
    }

    private static int getStreamType(String str) {
        switch (str) {
            case "call":
                return CarAudioManager.CAR_AUDIO_USAGE_VOICE_CALL;
            case "media":
                return CarAudioManager.CAR_AUDIO_USAGE_MUSIC;
            case "radio":
                return CarAudioManager.CAR_AUDIO_USAGE_RADIO;
            case "nav_guidance":
                return CarAudioManager.CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE;
            case "voice_command":
                return CarAudioManager.CAR_AUDIO_USAGE_VOICE_COMMAND;
            case "alarm":
                return CarAudioManager.CAR_AUDIO_USAGE_ALARM;
            case "notification":
                return CarAudioManager.CAR_AUDIO_USAGE_NOTIFICATION;
            case "system":
                return CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SOUND;
            case "safety":
                return CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT;
            case "unknown":
                return CarAudioManager.CAR_AUDIO_USAGE_DEFAULT;
        }
        throw new IllegalArgumentException("Wrong audioRoutingPolicy config, unknown stream type:" +
                str);
    }

    private AudioRoutingPolicy(String policy) {
        String[] streamPolicies = policy.split("#");
        final int nPhysicalStreams = streamPolicies.length;
        mLogicalStreams = new int[nPhysicalStreams][];
        mPhysicalStreamForLogicalStream = new int[CarAudioManager.CAR_AUDIO_USAGE_MAX + 1];
        for (int i = 0; i < mPhysicalStreamForLogicalStream.length; i++) {
            mPhysicalStreamForLogicalStream[i] = USAGE_TYPE_INVALID;
        }
        int defaultStreamType = USAGE_TYPE_INVALID;
        for (String streamPolicy : streamPolicies) {
            String[] numberVsStreams = streamPolicy.split(":");
            int physicalStream = Integer.parseInt(numberVsStreams[0]);
            String[] logicalStreams = numberVsStreams[1].split(",");
            int[] logicalStreamsInt = new int[logicalStreams.length];
            for (int i = 0; i < logicalStreams.length; i++) {
                int logicalStreamNumber = getStreamType(logicalStreams[i]);
                if (logicalStreamNumber == CarAudioManager.CAR_AUDIO_USAGE_DEFAULT) {
                    defaultStreamType = physicalStream;
                }
                logicalStreamsInt[i] = logicalStreamNumber;
                mPhysicalStreamForLogicalStream[logicalStreamNumber] = physicalStream;
            }
            Arrays.sort(logicalStreamsInt);
            mLogicalStreams[physicalStream] = logicalStreamsInt;
        }
        if (defaultStreamType == USAGE_TYPE_INVALID) {
            Log.e(CarLog.TAG_AUDIO, "Audio routing policy did not include unknown");
            defaultStreamType = 0;
        }
        for (int i = 0; i < mPhysicalStreamForLogicalStream.length; i++) {
            if (mPhysicalStreamForLogicalStream[i] == USAGE_TYPE_INVALID) {
                Log.w(CarLog.TAG_AUDIO, "Audio routing policy did not cover logical stream " + i);
                mPhysicalStreamForLogicalStream[i] = defaultStreamType;
            }
        }
    }

    public int getPhysicalStreamsCount() {
        return mLogicalStreams.length;
    }

    public int[] getLogicalStreamsForPhysicalStream(int physicalStreamNumber) {
        return mLogicalStreams[physicalStreamNumber];
    }

    public int getPhysicalStreamForLogicalStream(int logicalStream) {
        return mPhysicalStreamForLogicalStream[logicalStream];
    }

    public void dump(PrintWriter writer) {
        writer.println("*AudioRoutingPolicy*");
        writer.println("**Logical Streams**");
        for (int i = 0; i < mLogicalStreams.length; i++) {
            writer.print("physical stream " + i + ":");
            for (int logicalStream : mLogicalStreams[i]) {
                writer.print(Integer.toString(logicalStream) + ",");
            }
            writer.println("");
        }
    }
}
