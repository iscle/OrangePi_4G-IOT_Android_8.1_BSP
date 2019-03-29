/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.jobscheduler;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handles callback from the framework {@link android.app.job.JobScheduler}. The behaviour of this
 * class is configured through the static
 * {@link TestEnvironment}.
 */
@TargetApi(21)
public class MockJobService extends JobService {
    private static final String TAG = "MockJobService";

    /** Wait this long before timing out the test. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000L; // 5 seconds.

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "Created test service.");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Test job executing: " + params.getJobId());

        TestEnvironment.getTestEnvironment().notifyExecution(params.getJobId());
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
        public static final int INVALID_JOB_ID = -1;

        private CountDownLatch mLatch;
        private int mExecutedJobId;

        public static TestEnvironment getTestEnvironment() {
            if (kTestEnvironment == null) {
                kTestEnvironment = new TestEnvironment();
            }
            return kTestEnvironment;
        }

        /**
         * Block the test thread, waiting on the JobScheduler to execute some previously scheduled
         * job on this service.
         */
        public boolean awaitExecution() throws InterruptedException {
            final boolean executed = mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return executed;
        }

        /**
         * Block the test thread, expecting to timeout but still listening to ensure that no jobs
         * land in the interim.
         * @return True if the latch timed out waiting on an execution.
         */
        public boolean awaitTimeout() throws InterruptedException {
            return !mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        private void notifyExecution(int jobId) {
            Log.d(TAG, "Job executed:" + jobId);
            mExecutedJobId = jobId;
            mLatch.countDown();
        }

        public void setExpectedExecutions(int numExecutions) {
            // For no executions expected, set count to 1 so we can still block for the timeout.
            if (numExecutions == 0) {
                mLatch = new CountDownLatch(1);
            } else {
                mLatch = new CountDownLatch(numExecutions);
            }
        }

        /** Called in each testCase#setup */
        public void setUp() {
            mLatch = null;
            mExecutedJobId = INVALID_JOB_ID;
        }

    }
}