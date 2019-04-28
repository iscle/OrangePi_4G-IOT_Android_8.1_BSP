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

package com.android.tv.tuner.tvinput;

/**
 * The listener for buffer events occurred during playback.
 */
public interface PlaybackBufferListener {

    /**
     * Invoked when the start position of the buffer has been changed.
     *
     * @param startTimeMs the new start time of the buffer in millisecond
     */
    void onBufferStartTimeChanged(long startTimeMs);

    /**
     * Invoked when the state of the buffer has been changed.
     *
     * @param available whether the buffer is available or not
     */
    void onBufferStateChanged(boolean available);

    /**
     * Invoked when the disk speed is too slow to write the buffers.
     */
    void onDiskTooSlow();
}
