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

package com.android.tv.input;

import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A class to manage fake tuners for the tune and the recording.
 */
public class TunerHelper {
    private static final String TAG = "TunerHelper";
    private static final boolean DEBUG = false;

    private final List<Tuner> mTuners = new ArrayList<>();
    private final int mTunerCount;

    public TunerHelper(int tunerCount) {
        mTunerCount = tunerCount;
    }

    /**
     * Checks whether there are available tuners for the recording.
     */
    public boolean tunerAvailableForRecording() {
        if (mTuners.size() < mTunerCount) {
            return true;
        }
        for (Tuner tuner : mTuners) {
            if (!tuner.recording) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether there is available tuner.
     * If there's available tuner, it is assigned to the channel.
     */
    public boolean tune(@Nullable Uri channelUri, boolean forRecording) {
        if (channelUri == null) {
            return false;
        }
        for (Tuner tuner : mTuners) {
            // Find available tuner which is used only for the recording.
            if (tuner.channelUri.equals(channelUri)) {
                if (!forRecording && !tuner.tuning) {
                    tuner.tuning = true;
                    return true;
                } else if (forRecording && !tuner.recording) {
                    tuner.recording = true;
                    return true;
                }
            }
        }
        if (mTuners.size() < mTunerCount) {
            // Assign new tuner.
            mTuners.add(new Tuner(channelUri, forRecording));
            return true;
        }
        Log.i(TAG, "No available tuners. tuner count: " + mTunerCount);
        return false;
    }

    /**
     * Releases the tuner which was being used for the tune.
     */
    public void stopTune(@Nullable Uri channelUri) {
        if (channelUri == null) {
            return;
        }
        Tuner candidate = null;
        Iterator<Tuner> iterator = mTuners.iterator();
        while (iterator.hasNext()) {
            Tuner tuner = iterator.next();
            if (tuner.channelUri.equals(channelUri) && tuner.tuning) {
                if (tuner.recording) {
                    // A tuner which is used both for the tune and recording is the candidate.
                    candidate = tuner;
                } else {
                    // Remove the tuner which is used only for the tune.
                    if (DEBUG) Log.d(TAG, "Removed tuner for tune");
                    iterator.remove();
                    return;
                }
            }
        }
        if (candidate != null) {
            candidate.tuning = false;
        }
    }

    /**
     * Releases the tuner which was being used for the recording.
     */
    public void stopRecording(@Nullable Uri channelUri) {
        if (channelUri == null) {
            return;
        }
        Tuner candidate = null;
        Iterator<Tuner> iterator = mTuners.iterator();
        while (iterator.hasNext()) {
            Tuner tuner = iterator.next();
            if (tuner.channelUri.equals(channelUri)) {
                if (tuner.recording) {
                    if (tuner.tuning) {
                        // A tuner which is used both for the tune and recording is the candidate.
                        candidate = tuner;
                    } else {
                        // Remove the tuner which is used only for the recording.
                        iterator.remove();
                        return;
                    }
                } else {
                    Log.w(TAG, "Tuner found for " + channelUri + ", but not used for recording");
                }
            }
        }
        if (candidate != null) {
            candidate.recording = false;
        }
    }

    private static class Tuner {
        public final Uri channelUri;
        public boolean tuning;
        public boolean recording;

        public Tuner (Uri channelUri, boolean forRecording) {
            this.channelUri = channelUri;
            this.tuning = !forRecording;
            this.recording = forRecording;
        }
    }
}
