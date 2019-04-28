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

package com.android.tv.dvr.data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A class which generate the ID which increases sequentially.
 */
public class IdGenerator {
    /**
     * ID generator for the scheduled recording.
     */
    public static final IdGenerator SCHEDULED_RECORDING = new IdGenerator();

    /**
     * ID generator for the series recording.
     */
    public static final IdGenerator SERIES_RECORDING = new IdGenerator();

    private final AtomicLong mMaxId = new AtomicLong(0);

    /**
     * Sets the new maximum ID.
     */
    public void setMaxId(long maxId) {
        mMaxId.set(maxId);
    }

    /**
     * Returns the new ID which is greater than the existing maximum ID by 1.
     */
    public long newId() {
        return mMaxId.incrementAndGet();
    }
}
