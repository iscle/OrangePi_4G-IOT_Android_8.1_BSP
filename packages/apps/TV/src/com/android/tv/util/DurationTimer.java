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

package com.android.tv.util;

import android.os.SystemClock;
import android.util.Log;

import com.android.tv.common.BuildConfig;

/**
 * Times a duration.
 */
public final class DurationTimer {
    private static final String TAG = "DurationTimer";
    public static final long TIME_NOT_SET = -1;

    private long mStartTimeMs = TIME_NOT_SET;
    private String mTag = TAG;
    private boolean mLogEngOnly;

    public DurationTimer() { }

    public DurationTimer(String tag, boolean logEngOnly) {
        mTag = tag;
        mLogEngOnly = logEngOnly;
    }

    /**
     * Returns true if the timer is running.
     */
    public boolean isRunning() {
        return mStartTimeMs != TIME_NOT_SET;
    }

    /**
     * Start the timer.
     */
    public void start() {
        mStartTimeMs = SystemClock.elapsedRealtime();
    }

    /**
     * Returns true if timer is started.
     */
    public boolean isStarted() {
        return mStartTimeMs != TIME_NOT_SET;
    }

    /**
     * Returns the current duration in milliseconds or {@link #TIME_NOT_SET} if the timer is not
     * running.
     */
    public long getDuration() {
        return isRunning() ? SystemClock.elapsedRealtime() - mStartTimeMs : TIME_NOT_SET;
    }

    /**
     * Stops the timer and resets its value to {@link #TIME_NOT_SET}.
     *
     * @return the current duration in milliseconds or {@link #TIME_NOT_SET} if the timer is not
     * running.
     */
    public long reset() {
        long duration = getDuration();
        mStartTimeMs = TIME_NOT_SET;
        return duration;
    }

    /**
     * Adds information and duration time to the log.
     */
    public void log(String message) {
        if (isRunning() && (!mLogEngOnly || BuildConfig.ENG)) {
            Log.i(mTag, message + " : " + getDuration() + "ms");
        }
    }
}
