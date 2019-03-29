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

package com.android.cts.verifier.audio.audiolib;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

// TODO - This functionality probably exists in the framework function. Remove this and
//    use that instead.
public class AudioUtils {
    @SuppressWarnings("unused")
    private static final String TAG = "AudioUtils";

    public static int countIndexChannels(int chanConfig) {
        return Integer.bitCount(chanConfig & ~0x80000000);
    }

    public static int countToIndexMask(int chanCount) {
        // From the documentation for AudioFormat:
        // The canonical channel index masks by channel count are given by the formula
        // (1 << channelCount) - 1.
        return (1 << chanCount) - 1;
    }

    public static int countToOutPositionMask(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;

            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;

            case 4:
                return AudioFormat.CHANNEL_OUT_QUAD;

            default:
                return AudioTrack.ERROR_BAD_VALUE;
        }
    }

    public static int countToInPositionMask(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;

            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;

            default:
                return AudioRecord.ERROR_BAD_VALUE;
        }
    }

    // Encodings
    public static int sampleSizeInBytes(int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_16BIT:
                return 2;

            case AudioFormat.ENCODING_PCM_FLOAT:
                return 4;

            default:
                return 0;
        }
    }

    public static int calcFrameSizeInBytes(int encoding, int numChannels) {
        return sampleSizeInBytes(encoding) * numChannels;
    }
}