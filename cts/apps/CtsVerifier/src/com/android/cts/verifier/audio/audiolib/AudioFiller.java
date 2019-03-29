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

/**
 * An interface for objects which provide streamed audio data to a StreamPlayer instance.
 */
public interface AudioFiller {
    /**
     * Reset a stream to the beginning.
     */
    public void reset();

    /**
     * Process a request for audio data.
     * @param buffer The buffer to be filled.
     * @param numFrames The number of frames of audio to provide.
     * @param numChans The number of channels (in the buffer) required by the player.
     * @return The number of frames actually generated. If this value is less than that
     * requested, it may be interpreted by the player as the end of playback.
     */
    public int fill(float[] buffer, int numFrames, int numChans);
}
