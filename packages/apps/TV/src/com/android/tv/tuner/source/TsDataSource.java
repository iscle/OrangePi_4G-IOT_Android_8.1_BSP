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

package com.android.tv.tuner.source;

import com.google.android.exoplayer.upstream.DataSource;

/**
 * {@link DataSource} for MPEG-TS stream, which will be used by {@link TsExtractor}.
 */
public abstract class TsDataSource implements DataSource {

    /**
     * Returns the number of bytes being buffered by {@link TsStreamer} so far.
     *
     * @return the buffered position
     */
    public long getBufferedPosition() {
        return 0;
    }

    /**
     * Returns the offset position where the last {@link DataSource#read} read.
     *
     * @return the last read position
     */
    public long getLastReadPosition() {
        return 0;
    }

    /**
     * Shifts start position by the specified offset.
     * Do not call this method when the class already provided MPEG-TS stream to the extractor.
     * @param offset 0 <= offset <= buffered position
     */
    public void shiftStartPosition(long offset) { }
}