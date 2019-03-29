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

package android.jobscheduler;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handles callback from the framework {@link android.app.job.JobScheduler}. The behaviour of this
 * class is configured through the static
 * {@link TestEnvironment}.
 */
@TargetApi(21)
public class TriggerContentJobService extends JobService {
    private static final String TAG = "TriggerContentJobService";

    /** Wait this long before timing out the test. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 30000L; // 30 seconds.

    /** How long to delay before rescheduling the job each time we repeat. */
    private static final long REPEAT_INTERVAL = 1000L; // 1 second.

    JobInfo mRunningJobInfo;
    JobParameters mRunningParams;

    final Handler mHandler = new Handler();
    final Runnable mWorkerReschedule = new Runnable() {
        @Override public void run() {
            scheduleJob(TriggerContentJobService.this, mRunningJobInfo);
            jobFinished(mRunningParams, false);
        }
    };
    final Runnable mWorkerFinishTrue = new Runnable() {
        @Override public void run() {
            jobFinished(mRunningParams, true);
        }
    };

    public static void scheduleJob(Context context, JobInfo jobInfo) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        js.schedule(jobInfo);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "Created test service.");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Test job executing: " + params.getJobId());

        int mode = TestEnvironment.getTestEnvironment().getMode();
        mRunningJobInfo = TestEnvironment.getTestEnvironment().getModeJobInfo();
        TestEnvironment.getTestEnvironment().setMode(TestEnvironment.MODE_ONESHOT, null);
        TestEnvironment.getTestEnvironment().notifyExecution(params);

        if (mode == TestEnvironment.MODE_ONE_REPEAT_RESCHEDULE) {
            mRunningParams = params;
            mHandler.postDelayed(mWorkerReschedule, REPEAT_INTERVAL);
            return true;
        } else if (mode == TestEnvironment.MODE_ONE_REPEAT_FINISH_TRUE) {
            mRunningParams = params;
            mHandler.postDelayed(mWorkerFinishTrue, REPEAT_INTERVAL);
            return true;
        } else {
            return false;  // No work to do.
        }
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
        //public static final int INVALID_JOB_ID = -1;

        private CountDownLatch mLatch;
        private JobParameters mExecutedJobParameters;
        private int mMode;
        private JobInfo mModeJobInfo;

        public static final int MODE_ONESHOT = 0;
        public static final int MODE_ONE_REPEAT_RESCHEDULE = 1;
        public static final int MODE_ONE_REPEAT_FINISH_TRUE = 2;

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
            final boolean executed = mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return executed;
        }

        public void setMode(int mode, JobInfo jobInfo) {
            synchronized (this) {
                mMode = mode;
                mModeJobInfo = jobInfo;
            }
        }

        public int getMode() {
            synchronized (this) {
                return mMode;
            }
        }

        public JobInfo getModeJobInfo() {
            synchronized (this) {
                return mModeJobInfo;
            }
        }

        /**
         * Block the test thread, expecting to timeout but still listening to ensure that no jobs
         * land in the interim.
         * @return True if the latch timed out waiting on an execution.
         */
        public boolean awaitTimeout() throws InterruptedException {
            return !mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        private void notifyExecution(JobParameters params) {
            Log.d(TAG, "Job executed:" + params.getJobId());
            mExecutedJobParameters = params;
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
            mExecutedJobParameters = null;
        }

    }
}