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

package com.android.bips;

import android.print.PrintJobId;

import java.util.ArrayList;
import java.util.List;

/** Manages a job queue, ensuring only one job is printed at a time */
class JobQueue {
    private final List<LocalPrintJob> mJobs = new ArrayList<>();
    private LocalPrintJob mCurrent;

    /** Queue a print job for printing at the next available opportunity */
    void print(LocalPrintJob job) {
        mJobs.add(job);
        startNextJob();
    }

    /** Cancel a previously queued job */
    void cancel(PrintJobId id) {
        // If a job hasn't started, kill it instantly.
        for (LocalPrintJob job : mJobs) {
            if (job.getPrintJobId().equals(id)) {
                mJobs.remove(job);
                job.getPrintJob().cancel();
                return;
            }
        }

        if (mCurrent.getPrintJobId().equals(id)) {
            mCurrent.cancel();
        }
    }

    /** Launch the next job if possible */
    private void startNextJob() {
        if (mJobs.isEmpty() || mCurrent != null) return;

        mCurrent = mJobs.remove(0);
        mCurrent.start(job -> {
            mCurrent = null;
            startNextJob();
        });
    }
}