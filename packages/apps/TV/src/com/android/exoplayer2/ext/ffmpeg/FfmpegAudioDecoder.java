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
package com.google.android.exoplayer2.ext.ffmpeg;

import android.content.Context;
import android.content.pm.PackageManager;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import com.android.tv.common.SoftPreconditions;

import java.nio.ByteBuffer;

/**
 * Audio decoder which uses ffmpeg extension of ExoPlayer2. Since {@link FfmpegDecoder} is package
 * private, expose the decoder via this class. Supported formats are AC3 and MP2.
 */
public class FfmpegAudioDecoder {
    private static final int NUM_DECODER_BUFFERS = 1;

    // The largest AC3 sample size. This is bigger than the largest MP2 sample size (1729).
    private static final int INITIAL_INPUT_BUFFER_SIZE = 2560;
    private static boolean AVAILABLE;

    static {
        AVAILABLE =
                FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_AC3)
                        && FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_MPEG_L2);
    }

    private FfmpegDecoder mDecoder;
    private DecoderInputBuffer mInputBuffer;
    private SimpleOutputBuffer mOutputBuffer;
    private boolean mStarted;

    /** Return whether Ffmpeg based software audio decoder is available. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Creates an Ffmpeg based software audio decoder. */
    public FfmpegAudioDecoder(Context context) {
        if (context.checkSelfPermission("android.permission.INTERNET")
                == PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("This code should run in an isolated process");
        }
    }

    /**
     * Decodes an audio sample.
     *
     * @param timeUs presentation timestamp of the sample
     * @param sample data
     */
    public void decode(long timeUs, byte[] sample) {
        SoftPreconditions.checkState(AVAILABLE);
        mInputBuffer.data.clear();
        mInputBuffer.data.put(sample);
        mInputBuffer.data.flip();
        mInputBuffer.timeUs = timeUs;
        mDecoder.decode(mInputBuffer, mOutputBuffer, !mStarted);
        if (!mStarted) {
            mStarted = true;
        }
    }

    /** Returns a decoded sample from decoder. */
    public ByteBuffer getDecodedSample() {
        return mOutputBuffer.data;
    }

    /** Returns the presentation time for the decoded sample. */
    public long getDecodedTimeUs() {
        return mOutputBuffer.timeUs;
    }

    /**
     * Clear previous decode state if any. Prepares to decode samples of the specified encoding.
     * This method should be called before using decode.
     *
     * @param mime audio encoding
     */
    public void resetDecoderState(String mime) {
        SoftPreconditions.checkState(AVAILABLE);
        release();
        try {
            mDecoder =
                    new FfmpegDecoder(
                            NUM_DECODER_BUFFERS,
                            NUM_DECODER_BUFFERS,
                            INITIAL_INPUT_BUFFER_SIZE,
                            mime,
                            null);
            mStarted = false;
            mInputBuffer = mDecoder.createInputBuffer();
            // Since native JNI requires direct buffer, we should allocate it by #allocateDirect.
            mInputBuffer.data = ByteBuffer.allocateDirect(INITIAL_INPUT_BUFFER_SIZE);
            mOutputBuffer = mDecoder.createOutputBuffer();
        } catch (FfmpegDecoderException e) {
            // if AVAILABLE is {@code true}, this will not happen.
        }
    }

    /** Releases all the resource. */
    public void release() {
        SoftPreconditions.checkState(AVAILABLE);
        if (mDecoder != null) {
            mDecoder.release();
            mInputBuffer = null;
            mOutputBuffer = null;
            mDecoder = null;
        }
    }
}
