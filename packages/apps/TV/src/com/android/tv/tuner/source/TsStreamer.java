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

package com.android.tv.tuner.source;

import com.android.tv.tuner.ChannelScanFileParser;
import com.android.tv.tuner.data.TunerChannel;

/**
 * Interface definition for a stream generator. The interface will provide streams
 * for scanning channels and/or playback.
 */
public interface TsStreamer {
    /**
     * Starts streaming the data for channel scanning process.
     *
     * @param channel {@link ChannelScanFileParser.ScanChannel} to be scanned
     * @return {@code true} if ready to stream, otherwise {@code false}
     */
    boolean startStream(ChannelScanFileParser.ScanChannel channel);

    /**
     * Starts streaming the data for channel playing or recording.
     *
     * @param channel {@link TunerChannel} to tune
     * @return {@code true} if ready to stream, otherwise {@code false}
     */
    boolean startStream(TunerChannel channel);

    /**
     * Stops streaming the data.
     */
    void stopStream();

    /**
     * Creates {@link TsDataSource} which will provide MPEG-2 TS stream for
     * {@link android.media.MediaExtractor}. The source will start from the position
     * where it is created.
     *
     * @return {@link TsDataSource}
     */
    TsDataSource createDataSource();
}
