/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.ipp;

import android.os.AsyncTask;
import android.util.Log;

/** A background task that requests cancellation of a specific job */
class CancelJobTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = CancelJobTask.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final Backend mBackend;
    private final int mJobId;

    CancelJobTask(Backend backend, int jobId) {
        mBackend = backend;
        mJobId = jobId;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        if (DEBUG) Log.d(TAG, "doInBackground() for " + mJobId);

        // Success will result in a jobCallback.
        mBackend.nativeCancelJob(mJobId);
        return null;
    }
}