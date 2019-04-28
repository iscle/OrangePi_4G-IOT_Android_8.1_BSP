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
package android.support.car.media;

import android.media.AudioRecord;
import android.support.car.CarNotConnectedException;

/**
 * Enables applications to use the microphone.
 */
public abstract class CarAudioRecord {
    /**
     * Get the buffer size specified in {@link CarAudioManager#createCarAudioRecord(int)}.
     * @return Buffer size in bytes.
     */
    public abstract int getBufferSize() throws CarNotConnectedException;

    /**
     * Start audio recording.
     */
    public abstract void startRecording() throws CarNotConnectedException;

    /**
     * Stop audio recording. Calling stop multiple times is a safe operation.
     */
    public abstract void stop();

    /**
     * Release native resource allocated for this instance. {@link CarAudioRecord} can no longer
     * be used after release is called.
     */
    public abstract void release();

    /** See {@link AudioRecord#getRecordingState() }. */
    public abstract int getRecordingState() throws CarNotConnectedException;

    /** See {@link AudioRecord#getState() }. */
    public abstract int getState() throws CarNotConnectedException;

    /** See {@link AudioRecord#getAudioSessionId() }. */
    public abstract int getAudioSessionId() throws CarNotConnectedException;

    /**
     * Read recorded audio. Be sure to start audio recording with {@link #startRecording()}
     * before this.
     * @param audioData
     * @param offsetInBytes
     * @param sizeInBytes
     * @return Number of bytes read. Returns {@link android.media.AudioRecord#ERROR} on error.
     * @throws IllegalStateException if audio recording was not started.
     */
    public abstract int read(byte[] audioData, int offsetInBytes, int sizeInBytes)
            throws IllegalStateException, CarNotConnectedException;
}
