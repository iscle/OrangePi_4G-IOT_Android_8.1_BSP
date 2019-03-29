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
package android.jobscheduler.cts;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;

/**
 * Schedules jobs with various timing constraints and ensures that they are executed when
 * appropriate.
 */
@TargetApi(21)
public class TimingConstraintsTest extends ConstraintTest {
    private static final int TIMING_JOB_ID = TimingConstraintsTest.class.hashCode() + 0;
    private static final int CANCEL_JOB_ID = TimingConstraintsTest.class.hashCode() + 1;
    private static final int EXPIRED_JOB_ID = TimingConstraintsTest.class.hashCode() + 2;
    private static final int UNEXPIRED_JOB_ID = TimingConstraintsTest.class.hashCode() + 3;

    public void testScheduleOnce() throws Exception {
        JobInfo oneTimeJob = new JobInfo.Builder(TIMING_JOB_ID, kJobServiceComponent)
                        .setOverrideDeadline(5000)  // 5 secs
                        .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(oneTimeJob);
        final boolean executed = kTestEnvironment.awaitExecution();
        assertTrue("Timed out waiting for override deadline.", executed);
    }

    public void testCancel() throws Exception {
        JobInfo cancelJob = new JobInfo.Builder(CANCEL_JOB_ID, kJobServiceComponent)
                .setMinimumLatency(5000L) // make sure it doesn't actually run immediately
                .setOverrideDeadline(7000L)
                .build();

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(cancelJob);
        // Now cancel it.
        mJobScheduler.cancel(CANCEL_JOB_ID);
        assertTrue("Cancel failed: job executed when it shouldn't have.",
                kTestEnvironment.awaitTimeout());
    }

    /**
     * Ensure that when a job is executed because its deadline has expired, that
     * {@link JobParameters#isOverrideDeadlineExpired()} returns the correct value.
     */
    public void testJobParameters_expiredDeadline() throws Exception {
        // It is expected that the "device idle" constraint will *not* be met
        // for the duration of the override deadline.
        JobInfo deadlineJob =
                new JobInfo.Builder(EXPIRED_JOB_ID, kJobServiceComponent)
                        .setRequiresDeviceIdle(true)
                        .setOverrideDeadline(2000L)
                        .build();
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(deadlineJob);
        assertTrue("Failed to execute deadline job", kTestEnvironment.awaitExecution());
        assertTrue("Job does not show its deadline as expired",
                kTestEnvironment.getLastJobParameters().isOverrideDeadlineExpired());
    }


    /**
     * Ensure that when a job is executed and its deadline hasn't expired, that
     * {@link JobParameters#isOverrideDeadlineExpired()} returns the correct value.
     */
    public void testJobParameters_unexpiredDeadline() throws Exception {

        JobInfo deadlineJob =
                new JobInfo.Builder(UNEXPIRED_JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(500L)
                        .setRequiresCharging(true)
                        .build();
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(deadlineJob);
        // Run everything by pretending the device was just plugged in.
        sendExpediteStableChargingBroadcast();
        assertTrue("Failed to execute non-deadline job", kTestEnvironment.awaitExecution());
        assertFalse("Job that ran early (unexpired) didn't have" +
                        " JobParameters#isOverrideDeadlineExpired=false",
                kTestEnvironment.getLastJobParameters().isOverrideDeadlineExpired());
    }
}
