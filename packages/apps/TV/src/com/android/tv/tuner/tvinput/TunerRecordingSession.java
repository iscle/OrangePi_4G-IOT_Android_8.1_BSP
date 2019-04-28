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

import android.content.Context;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

/**
 * Processes DVR recordings, and deletes the previously recorded contents.
 */
public class TunerRecordingSession extends TvInputService.RecordingSession {
    private static final String TAG = "TunerRecordingSession";
    private static final boolean DEBUG = false;

    private final TunerRecordingSessionWorker mSessionWorker;

    public TunerRecordingSession(Context context, String inputId,
            ChannelDataManager channelDataManager) {
        super(context);
        mSessionWorker = new TunerRecordingSessionWorker(context, inputId, channelDataManager,
                this);
    }

    // RecordingSession
    @MainThread
    @Override
    public void onTune(Uri channelUri) {
        // TODO(dvr): support calling more than once, http://b/27171225
        if (DEBUG) {
            Log.d(TAG, "Requesting recording session tune: " + channelUri);
        }
        mSessionWorker.tune(channelUri);
    }

    @MainThread
    @Override
    public void onRelease() {
        if (DEBUG) {
            Log.d(TAG, "Requesting recording session release.");
        }
        mSessionWorker.release();
    }

    @MainThread
    @Override
    public void onStartRecording(@Nullable Uri programUri) {
        if (DEBUG) {
            Log.d(TAG, "Requesting start recording.");
        }
        mSessionWorker.startRecording(programUri);
    }

    @MainThread
    @Override
    public void onStopRecording() {
        if (DEBUG) {
            Log.d(TAG, "Requesting stop recording.");
        }
        mSessionWorker.stopRecording();
    }

    // Called from TunerRecordingSessionImpl in a worker thread.
    @WorkerThread
    public void onTuned(Uri channelUri) {
        if (DEBUG) {
            Log.d(TAG, "Notifying recording session tuned.");
        }
        notifyTuned(channelUri);
    }

    @WorkerThread
    public void onRecordFinished(final Uri recordedProgramUri) {
        if (DEBUG) {
            Log.d(TAG, "Notifying record successfully finished.");
        }
        notifyRecordingStopped(recordedProgramUri);
    }

    @WorkerThread
    public void onError(int reason) {
        Log.w(TAG, "Notifying recording error: " + reason);
        notifyError(reason);
    }
}
