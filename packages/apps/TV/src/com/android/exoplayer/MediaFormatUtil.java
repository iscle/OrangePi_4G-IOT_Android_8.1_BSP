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
package com.google.android.exoplayer;

import android.support.annotation.Nullable;

import com.google.android.exoplayer.util.MimeTypes;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/** {@link MediaFormat} creation helper util */
public class MediaFormatUtil {

    /**
     * Creates {@link MediaFormat} from {@link android.media.MediaFormat}.
     * Since {@link com.google.android.exoplayer.TrackRenderer} uses {@link MediaFormat},
     * {@link android.media.MediaFormat} should be converted to be used with ExoPlayer.
     */
    public static MediaFormat createMediaFormat(android.media.MediaFormat format) {
        String mimeType = format.getString(android.media.MediaFormat.KEY_MIME);
        String language = getOptionalStringV16(format, android.media.MediaFormat.KEY_LANGUAGE);
        int maxInputSize =
                getOptionalIntegerV16(format, android.media.MediaFormat.KEY_MAX_INPUT_SIZE);
        int width = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_WIDTH);
        int height = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_HEIGHT);
        int rotationDegrees = getOptionalIntegerV16(format, "rotation-degrees");
        int channelCount =
                getOptionalIntegerV16(format, android.media.MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_SAMPLE_RATE);
        int encoderDelay = getOptionalIntegerV16(format, "encoder-delay");
        int encoderPadding = getOptionalIntegerV16(format, "encoder-padding");
        ArrayList<byte[]> initializationData = new ArrayList<>();
        for (int i = 0; format.containsKey("csd-" + i); i++) {
            ByteBuffer buffer = format.getByteBuffer("csd-" + i);
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            initializationData.add(data);
            buffer.flip();
        }
        long durationUs = format.containsKey(android.media.MediaFormat.KEY_DURATION)
                ? format.getLong(android.media.MediaFormat.KEY_DURATION) : C.UNKNOWN_TIME_US;
        int pcmEncoding = MimeTypes.AUDIO_RAW.equals(mimeType) ? C.ENCODING_PCM_16BIT
                : MediaFormat.NO_VALUE;
        MediaFormat mediaFormat = new MediaFormat(null, mimeType, MediaFormat.NO_VALUE,
                maxInputSize, durationUs, width, height, rotationDegrees, MediaFormat.NO_VALUE,
                channelCount, sampleRate, language, MediaFormat.OFFSET_SAMPLE_RELATIVE,
                initializationData, false, MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, pcmEncoding,
                encoderDelay, encoderPadding, null, MediaFormat.NO_VALUE);
        mediaFormat.setFrameworkFormatV16(format);
        return mediaFormat;
    }

    @Nullable
    private static String getOptionalStringV16(android.media.MediaFormat format, String key) {
        return format.containsKey(key) ? format.getString(key) : null;
    }

    private static int getOptionalIntegerV16(android.media.MediaFormat format, String key) {
        return format.containsKey(key) ? format.getInteger(key) : MediaFormat.NO_VALUE;
    }

}
