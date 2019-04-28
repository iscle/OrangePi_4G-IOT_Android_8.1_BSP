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

import android.os.SystemClock;
import android.util.Log;

/**
 * A class to maintain various debugging information.
 */
public class TunerDebug {
    private static final String TAG = "TunerDebug";
    public static final boolean ENABLED = false;

    private int mVideoFrameDrop;
    private int mBytesInQueue;

    private long mAudioPositionUs;
    private long mAudioPtsUs;
    private long mVideoPtsUs;

    private long mLastAudioPositionUs;
    private long mLastAudioPtsUs;
    private long mLastVideoPtsUs;
    private long mLastCheckTimestampMs;

    private long mAudioPositionUsRate;
    private long mAudioPtsUsRate;
    private long mVideoPtsUsRate;

    private TunerDebug() {
        mVideoFrameDrop = 0;
        mLastCheckTimestampMs = SystemClock.elapsedRealtime();
    }

    private static class LazyHolder {
        private static final TunerDebug INSTANCE = new TunerDebug();
    }

    public static TunerDebug getInstance() {
        return LazyHolder.INSTANCE;
    }

    public static void notifyVideoFrameDrop(int count, long delta) {
        // TODO: provide timestamp mismatch information using delta
        TunerDebug sTunerDebug = getInstance();
        sTunerDebug.mVideoFrameDrop += count;
    }

    public static int getVideoFrameDrop() {
        TunerDebug sTunerDebug = getInstance();
        int videoFrameDrop = sTunerDebug.mVideoFrameDrop;
        if (videoFrameDrop > 0) {
            Log.d(TAG, "Dropped video frame: " + videoFrameDrop);
        }
        sTunerDebug.mVideoFrameDrop = 0;
        return videoFrameDrop;
    }

    public static void setBytesInQueue(int bytesInQueue) {
        TunerDebug sTunerDebug = getInstance();
        sTunerDebug.mBytesInQueue = bytesInQueue;
    }

    public static int getBytesInQueue() {
        TunerDebug sTunerDebug = getInstance();
        return sTunerDebug.mBytesInQueue;
    }

    public static void setAudioPositionUs(long audioPositionUs) {
        TunerDebug sTunerDebug = getInstance();
        sTunerDebug.mAudioPositionUs = audioPositionUs;
    }

    public static long getAudioPositionUs() {
        TunerDebug sTunerDebug = getInstance();
        return sTunerDebug.mAudioPositionUs;
    }

    public static void setAudioPtsUs(long audioPtsUs) {
        TunerDebug sTunerDebug = getInstance();
        sTunerDebug.mAudioPtsUs = audioPtsUs;
    }

    public static long getAudioPtsUs() {
        TunerDebug sTunerDebug = getInstance();
        return sTunerDebug.mAudioPtsUs;
    }

    public static void setVideoPtsUs(long videoPtsUs) {
        TunerDebug sTunerDebug = getInstance();
        sTunerDebug.mVideoPtsUs = videoPtsUs;
    }

    public static long getVideoPtsUs() {
        TunerDebug sTunerDebug = getInstance();
        return sTunerDebug.mVideoPtsUs;
    }

    public static void calculateDiff() {
        TunerDebug sTunerDebug = getInstance();
        long currentTime = SystemClock.elapsedRealtime();
        long duration = currentTime - sTunerDebug.mLastCheckTimestampMs;
        if (duration != 0) {
            sTunerDebug.mAudioPositionUsRate =
                    (sTunerDebug.mAudioPositionUs - sTunerDebug.mLastAudioPositionUs) * 1000
                    / duration;
            sTunerDebug.mAudioPtsUsRate =
                    (sTunerDebug.mAudioPtsUs - sTunerDebug.mLastAudioPtsUs) * 1000
                    / duration;
            sTunerDebug.mVideoPtsUsRate =
                    (sTunerDebug.mVideoPtsUs - sTunerDebug.mLastVideoPtsUs) * 1000
                    / duration;
        }

        sTunerDebug.mLastAudioPositionUs = sTunerDebug.mAudioPositionUs;
        sTunerDebug.mLastAudioPtsUs = sTunerDebug.mAudioPtsUs;
        sTunerDebug.mLastVideoPtsUs = sTunerDebug.mVideoPtsUs;
        sTunerDebug.mLastCheckTimestampMs = currentTime;
    }

    public static long getAudioPositionUsRate() {
        TunerDebug sTunerDebug = getInstance();
        return sTunerDebug.mAudioPositionUsRate;
    }

    public static long getAudioPtsUsRate() {
        TunerDebug sTunerDebug = getInstance();
        return sTunerDebug.mAudioPtsUsRate;
    }

    public static long getVideoPtsUsRate() {
        TunerDebug sTunerDebug = getInstance();
        return sTunerDebug.mVideoPtsUsRate;
    }
}
