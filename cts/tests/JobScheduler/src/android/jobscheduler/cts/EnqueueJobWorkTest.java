/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.app.job.JobWorkItem;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.jobscheduler.MockJobService.TestWorkItem;
import android.net.Uri;

import com.android.compatibility.common.util.SystemUtil;

import java.util.ArrayList;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} by enqueue work in to
 * them and processing it.
 */
@TargetApi(26)
public class EnqueueJobWorkTest extends ConstraintTest {
    private static final String TAG = "ClipDataJobTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int ENQUEUE_WORK_JOB_ID = EnqueueJobWorkTest.class.hashCode();

    private JobInfo.Builder mBuilder;
    private ContentProviderClient mProvider;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(ENQUEUE_WORK_JOB_ID, kJobServiceComponent);
        mProvider = getContext().getContentResolver().acquireContentProviderClient(mFirstUri);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mProvider.close();
        mJobScheduler.cancel(ENQUEUE_WORK_JOB_ID);
    }

    private boolean intentEquals(Intent i1, Intent i2) {
        if (i1 == i2) {
            return true;
        }
        if (i1 == null || i2 == null) {
            return false;
        }
        return i1.filterEquals(i2);
    }

    private void compareWork(TestWorkItem[] expected, ArrayList<JobWorkItem> received) {
        if (received == null) {
            fail("Didn't receive any expected work.");
        }
        ArrayList<TestWorkItem> expectedArray = new ArrayList<>();
        for (int i = 0; i < expected.length; i++) {
            expectedArray.add(expected[i]);
        }
        for (int i = 0; i < received.size(); i++) {
            JobWorkItem work = received.get(i);
            if (i < expected.length && expected[i].subitems != null) {
                TestWorkItem[] sub = expected[i].subitems;
                for (int j = 0; j < sub.length; j++) {
                    expectedArray.add(sub[j]);
                }
            }
            if (i >= expectedArray.size()) {
                fail("Received more than " + expected.length + " work items, first extra is "
                        + work);
            }
            if (!intentEquals(work.getIntent(), expectedArray.get(i).intent)) {
                fail("Received work #" + i + " " + work.getIntent() + " but expected " + expected[i]);
            }
            if (work.getDeliveryCount() != expectedArray.get(i).deliveryCount) {
                fail("Received work #" + i + " " + work.getIntent() + " delivery count is "
                        + work.getDeliveryCount() + " but expected "
                        + expectedArray.get(i).deliveryCount);
            }
        }
        if (received.size() < expected.length) {
            fail("Received only " + received.size() + " work items, but expected "
                            + expected.length);
        }
    }

    /**
     * Test basic enqueueing of work.
     */
    public void testEnqueueOneWork() throws Exception {
        Intent work1 = new Intent("work1");
        TestWorkItem[] work = new TestWorkItem[] { new TestWorkItem(work1) };
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWork(work);
        mJobScheduler.enqueue(mBuilder.setOverrideDeadline(0).build(), new JobWorkItem(work1));
        kTestEnvironment.readyToWork();
        assertTrue("Job with work enqueued did not fire.",
                kTestEnvironment.awaitExecution());
        compareWork(work, kTestEnvironment.getLastReceivedWork());
        if (kTestEnvironment.getLastErrorMessage() != null) {
            fail(kTestEnvironment.getLastErrorMessage());
        }
    }

    /**
     * Test basic enqueueing batches of work.
     */
    public void testEnqueueMultipleWork() throws Exception {
        Intent work1 = new Intent("work1");
        Intent work2 = new Intent("work2");
        Intent work3 = new Intent("work3");
        Intent work4 = new Intent("work4");
        Intent work5 = new Intent("work5");
        Intent work6 = new Intent("work6");
        Intent work7 = new Intent("work7");
        Intent work8 = new Intent("work8");
        TestWorkItem[] work = new TestWorkItem[] {
                new TestWorkItem(work1), new TestWorkItem(work2), new TestWorkItem(work3),
                new TestWorkItem(work4), new TestWorkItem(work5), new TestWorkItem(work6),
                new TestWorkItem(work7), new TestWorkItem(work8) };
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWork(work);
        JobInfo ji = mBuilder.setOverrideDeadline(0).build();
        mJobScheduler.enqueue(ji, new JobWorkItem(work1));
        mJobScheduler.enqueue(ji, new JobWorkItem(work2));
        mJobScheduler.enqueue(ji, new JobWorkItem(work3));
        mJobScheduler.enqueue(ji, new JobWorkItem(work4));
        mJobScheduler.enqueue(ji, new JobWorkItem(work5));
        mJobScheduler.enqueue(ji, new JobWorkItem(work6));
        mJobScheduler.enqueue(ji, new JobWorkItem(work7));
        mJobScheduler.enqueue(ji, new JobWorkItem(work8));
        kTestEnvironment.readyToWork();
        assertTrue("Job with work enqueued did not fire.",
                kTestEnvironment.awaitExecution());
        compareWork(work, kTestEnvironment.getLastReceivedWork());
    }

    /**
     * Test basic enqueueing batches of work, with new work coming in while processing existing
     * work.
     */
    public void testEnqueueMultipleSubWork() throws Exception {
        Intent work1 = new Intent("work1");
        Intent work2 = new Intent("work2");
        Intent work3 = new Intent("work3");
        Intent work4 = new Intent("work4");
        Intent work5 = new Intent("work5");
        Intent work6 = new Intent("work6");
        Intent work7 = new Intent("work7");
        Intent work8 = new Intent("work8");
        JobInfo ji = mBuilder.setOverrideDeadline(0).build();
        TestWorkItem[] work = new TestWorkItem[]{
                new TestWorkItem(work1), new TestWorkItem(work2), new TestWorkItem(work3),
                new TestWorkItem(work4, ji, new TestWorkItem[] {
                        new TestWorkItem(work5), new TestWorkItem(work6),
                        new TestWorkItem(work7), new TestWorkItem(work8)})
        };
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWork(work);
        mJobScheduler.enqueue(ji, new JobWorkItem(work1));
        mJobScheduler.enqueue(ji, new JobWorkItem(work2));
        mJobScheduler.enqueue(ji, new JobWorkItem(work3));
        mJobScheduler.enqueue(ji, new JobWorkItem(work4));
        kTestEnvironment.readyToWork();
        assertTrue("Job with work enqueued did not fire.",
                kTestEnvironment.awaitExecution());
        compareWork(work, kTestEnvironment.getLastReceivedWork());
    }

    /**
     * Test job getting stopped while processing work and that work being redelivered.
     */
    public void testEnqueueMultipleRedeliver() throws Exception {
        Intent work1 = new Intent("work1");
        Intent work2 = new Intent("work2");
        Intent work3 = new Intent("work3");
        Intent work4 = new Intent("work4");
        Intent work5 = new Intent("work5");
        Intent work6 = new Intent("work6");
        TestWorkItem[] initialWork = new TestWorkItem[] {
                new TestWorkItem(work1), new TestWorkItem(work2), new TestWorkItem(work3),
                new TestWorkItem(work4, TestWorkItem.FLAG_WAIT_FOR_STOP, 1) };
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForStop();
        kTestEnvironment.setExpectedWork(initialWork);
        JobInfo ji = mBuilder.setOverrideDeadline(0).build();
        mJobScheduler.enqueue(ji, new JobWorkItem(work1));
        mJobScheduler.enqueue(ji, new JobWorkItem(work2));
        mJobScheduler.enqueue(ji, new JobWorkItem(work3));
        mJobScheduler.enqueue(ji, new JobWorkItem(work4));
        kTestEnvironment.readyToWork();

        // Now wait for the job to get to the point where it is processing the last
        // work and waiting for it to be stopped.
        assertTrue("Job with work enqueued did not wait to stop.",
                kTestEnvironment.awaitWaitingForStop());

        // Cause the job to timeout (stop) immediately, and wait for its execution to finish.
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler timeout "
                + kJobServiceComponent.getPackageName() + " " + ENQUEUE_WORK_JOB_ID);
        assertTrue("Job with work enqueued did not finish.",
                kTestEnvironment.awaitExecution());
        compareWork(initialWork, kTestEnvironment.getLastReceivedWork());

        // Now we are going to add some more work, restart the job, and see if it correctly
        // redelivers the last work and delivers the new work.
        TestWorkItem[] finalWork = new TestWorkItem[] {
                new TestWorkItem(work4, 0, 2), new TestWorkItem(work5), new TestWorkItem(work6) };
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWork(finalWork);
        mJobScheduler.enqueue(ji, new JobWorkItem(work5));
        mJobScheduler.enqueue(ji, new JobWorkItem(work6));
        kTestEnvironment.readyToWork();
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler run "
                + kJobServiceComponent.getPackageName() + " " + ENQUEUE_WORK_JOB_ID);

        assertTrue("Restarted with work enqueued did not execute.",
                kTestEnvironment.awaitExecution());
        compareWork(finalWork, kTestEnvironment.getLastReceivedWork());
    }

    /**
     * Test basic enqueueing batches of work.
     */
    public void testEnqueueMultipleUriGrantWork() throws Exception {
        // Start out with storage low, so job is enqueued but not executed yet.
        setStorageState(true);

        // We need to get a permission grant so that we can grant it to ourself.
        mProvider.call("grant", MY_PACKAGE, mFirstUriBundle);
        mProvider.call("grant", MY_PACKAGE, mSecondUriBundle);
        assertHasUriPermission(mFirstUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertHasUriPermission(mSecondUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        Intent work1 = new Intent("work1");
        work1.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        work1.setData(mFirstUri);
        work1.setClipData(mSecondClipData);

        Intent work2 = new Intent("work2");
        work2.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        work2.setData(mFirstUri);

        TestWorkItem[] work = new TestWorkItem[] {
                new TestWorkItem(work1, new Uri[] { mFirstUri, mSecondUri}, new Uri[0]),
                new TestWorkItem(work2, new Uri[] { mFirstUri }, new Uri[] { mSecondUri}) };
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWork(work);
        JobInfo ji = mBuilder.setOverrideDeadline(0).setRequiresStorageNotLow(true).build();
        mJobScheduler.enqueue(ji, new JobWorkItem(work1));
        mJobScheduler.enqueue(ji, new JobWorkItem(work2));

        // Remove the explicit grant, we should still have a grant due to the job.
        mProvider.call("revoke", MY_PACKAGE, mFirstUriBundle);
        mProvider.call("revoke", MY_PACKAGE, mSecondUriBundle);
        assertHasUriPermission(mFirstUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertHasUriPermission(mSecondUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        kTestEnvironment.readyToWork();

        // Now allow the job to run.
        setStorageState(false);

        assertTrue("Job with work enqueued did not fire.",
                kTestEnvironment.awaitExecution());
        compareWork(work, kTestEnvironment.getLastReceivedWork());

        // And wait for everything to be cleaned up.
        waitPermissionRevoke(mFirstUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 5000);
        waitPermissionRevoke(mSecondUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 5000);
    }
}
