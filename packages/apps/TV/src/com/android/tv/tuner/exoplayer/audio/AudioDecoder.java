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

package com.android.tv.tuner.exoplayer.audio;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;

import java.nio.ByteBuffer;

/** A base class for audio decoders. */
public abstract class AudioDecoder {

    /**
     * Decodes an audio sample.
     *
     * @param sampleHolder a holder that contains the sample data and corresponding metadata
     */
    public abstract void decode(SampleHolder sampleHolder);

    /** Returns a decoded sample from decoder. */
    public abstract ByteBuffer getDecodedSample();

    /** Returns the presentation time for the decoded sample. */
    public abstract long getDecodedTimeUs();

    /**
     * Clear previous decode state if any. Prepares to decode samples of the specified encoding.
     * This method should be called before using decode.
     *
     * @param mime audio encoding
     */
    public abstract void resetDecoderState(String mimeType);

    /** Releases all the resource. */
    public abstract void release();

    /**
     * Init decoder if needed.
     *
     * @param format the format used to initialize decoder
     */
    public void maybeInitDecoder(MediaFormat format) throws ExoPlaybackException {
        // Do nothing.
    }

    /** Returns input buffer that will be used in decoder. */
    public ByteBuffer getInputBuffer() {
        return null;
    }

    /** Returns the output format. */
    public android.media.MediaFormat getOutputFormat() {
        return null;
    }
}
