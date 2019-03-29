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

package com.android.cts.verifier.camera.intents;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Context;
import android.os.ConditionVariable;
import android.os.Handler;
import android.util.Log;

/**
 * Handles callback from the framework {@link android.app.job.JobScheduler}. The behaviour of this
 * class is configured through the static
 * {@link TestEnvironment}.
 */
@TargetApi(21)
public class CameraContentJobService extends JobService {
    private static final String TAG = "CameraContentJobService";

    /** Wait this long before timing out the test. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 60000L; // 60 seconds.

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Created test service.");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Test job executing: " + params.getJobId());
        TestEnvironment.getTestEnvironment().notifyExecution(params);
        return false;  // No work to do.
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     * Configures the expected behaviour for each test. This object is shared across consecutive
     * tests, so to clear state each test is responsible for calling
     * {@link TestEnvironment#setUp()}.
     */
    public static final class TestEnvironment {

        private static TestEnvironment kTestEnvironment;

        private ConditionVariable mCondition = new ConditionVariable();
        private JobParameters mExecutedJobParameters;
        private boolean mCancelled = false;

        public static TestEnvironment getTestEnvironment() {
            if (kTestEnvironment == null) {
                kTestEnvironment = new TestEnvironment();
            }
            return kTestEnvironment;
        }

        public JobParameters getLastJobParameters() {
            return mExecutedJobParameters;
        }

        /**
         * Block the test thread, waiting on the JobScheduler to execute some previously scheduled
         * job on this service.
         */
        public boolean awaitExecution() throws InterruptedException {
            final boolean executed = mCondition.block(DEFAULT_TIMEOUT_MILLIS);
            if (mCancelled) {
                return false;
            }
            return executed;
        }

        private void notifyExecution(JobParameters params) {
            Log.d(TAG, "Job executed:" + params.getJobId());
            mExecutedJobParameters = params;
            mCondition.open();
        }

        // Cancel any ongoing wait. Will cause awaitExecution to return false (timeout)
        public void cancelWait() {
            mCancelled = true;
            mCondition.open();
        }

        /** Called before starting a new test */
        public void setUp() {
            mCondition.close();
            mExecutedJobParameters = null;
            mCancelled = false;
        }

    }
}
