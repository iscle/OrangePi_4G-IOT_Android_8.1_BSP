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

package com.android.tv.recommendation;

import com.android.tv.data.Program;

public final class WatchedProgram {
    private final Program mProgram;
    private final long mWatchStartTimeMs;
    private final long mWatchEndTimeMs;

    public WatchedProgram(Program program, long watchStartTimeMs, long watchEndTimeMs) {
        mProgram = program;
        mWatchStartTimeMs = watchStartTimeMs;
        mWatchEndTimeMs = watchEndTimeMs;
    }

    public long getWatchStartTimeMs() {
        return mWatchStartTimeMs;
    }

    public long getWatchEndTimeMs() {
        return mWatchEndTimeMs;
    }

    public long getWatchedDurationMs() {
        return mWatchEndTimeMs - mWatchStartTimeMs;
    }

    public Program getProgram() {
        return mProgram;
    }
}
