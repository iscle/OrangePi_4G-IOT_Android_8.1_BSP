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

package com.android.tv.tuner.exoplayer.audio;

import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.util.MimeTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Monitors the rendering position of {@link AudioTrack}.
 */
public class AudioTrackMonitor {
    private static final String TAG = "AudioTrackMonitor";
    private static final boolean DEBUG = false;

    // For fetched audio samples
    private final ArrayList<Pair<Long, Integer>> mPtsList = new ArrayList<>();
    private final Set<Integer> mSampleSize = new HashSet<>();
    private final Set<Integer> mCurSampleSize = new HashSet<>();
    private final Set<Integer> mHeader = new HashSet<>();

    private long mExpireMs;
    private long mDuration;
    private long mSampleCount;
    private long mTotalCount;
    private long mStartMs;

    private boolean mIsMp2;

    private void flush() {
        mExpireMs += mDuration;
        mSampleCount = 0;
        mCurSampleSize.clear();
        mPtsList.clear();
    }

    /**
     * Resets and initializes {@link AudioTrackMonitor}.
     *
     * @param duration the frequency of monitoring in milliseconds
     */
    public void reset(long duration) {
        mExpireMs = SystemClock.elapsedRealtime();
        mDuration = duration;
        mTotalCount = 0;
        mStartMs = 0;
        mSampleSize.clear();
        mHeader.clear();
        flush();
    }

    public void setEncoding(String mime) {
        mIsMp2 = MimeTypes.AUDIO_MPEG_L2.equalsIgnoreCase(mime);
    }

    /**
     * Adds an audio sample information for monitoring.
     *
     * @param pts the presentation timestamp of the sample
     * @param sampleSize the size in bytes of the sample
     * @param header the bitrate &amp; sampling information header of the sample
     */
    public void addPts(long pts, int sampleSize, int header) {
        mTotalCount++;
        mSampleCount++;
        mSampleSize.add(sampleSize);
        mHeader.add(header);
        mCurSampleSize.add(sampleSize);
        if (mTotalCount == 1) {
            mStartMs = SystemClock.elapsedRealtime();
        }
        if (mPtsList.isEmpty() || mPtsList.get(mPtsList.size() - 1).first != pts) {
            mPtsList.add(Pair.create(pts, 1));
            return;
        }
        Pair<Long, Integer> pair = mPtsList.get(mPtsList.size() - 1);
        mPtsList.set(mPtsList.size() - 1, Pair.create(pair.first, pair.second + 1));
    }

    /**
     * Logs if interested events are present.
     * <p>
     * Periodic logging is not enabled in release mode in order to avoid verbose logging.
     */
    public void maybeLog() {
        long now = SystemClock.elapsedRealtime();
        if (mExpireMs != 0 && now >= mExpireMs) {
            if (DEBUG) {
                long unitDuration = mIsMp2 ? MpegTsDefaultAudioTrackRenderer.MP2_SAMPLE_DURATION_US
                        : MpegTsDefaultAudioTrackRenderer.AC3_SAMPLE_DURATION_US;
                long sampleDuration = (mTotalCount - 1) * unitDuration / 1000;
                long totalDuration = now - mStartMs;
                StringBuilder ptsBuilder = new StringBuilder();
                ptsBuilder.append("PTS received ").append(mSampleCount).append(", ")
                        .append(totalDuration - sampleDuration).append(' ');

                for (Pair<Long, Integer> pair : mPtsList) {
                    ptsBuilder.append('[').append(pair.first).append(':').append(pair.second)
                            .append("], ");
                }
                Log.d(TAG, ptsBuilder.toString());
            }
            if (DEBUG || mCurSampleSize.size() > 1) {
                Log.d(TAG, "PTS received sample size: "
                        + String.valueOf(mSampleSize) + mCurSampleSize + mHeader);
            }
            flush();
        }
    }
}
