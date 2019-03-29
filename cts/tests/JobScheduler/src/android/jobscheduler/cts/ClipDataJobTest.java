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
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that grant permissions through
 * ClipData.
 */
@TargetApi(26)
public class ClipDataJobTest extends ConstraintTest {
    private static final String TAG = "ClipDataJobTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int CLIP_DATA_JOB_ID = ClipDataJobTest.class.hashCode();

    JobInfo.Builder mBuilder;
    private ContentProviderClient mProvider;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(CLIP_DATA_JOB_ID, kJobServiceComponent);
        mProvider = getContext().getContentResolver().acquireContentProviderClient(mFirstUri);
        assertNotNull(mProvider);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mProvider.close();
        mJobScheduler.cancel(CLIP_DATA_JOB_ID);
    }

    /**
     * Test basic granting of URI permissions associated with jobs.
     */
    public void testClipDataGrant() throws Exception {
        // Start out with storage low, so job is enqueued but not executed yet.
        setStorageState(true);

        // We need to get a permission grant so that we can grant it to ourself.
        mProvider.call("grant", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Schedule the job, the system should now also be holding a URI grant for us.
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true)
                .setClipData(mFirstClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());

        // Remove the explicit grant, we should still have a grant due to the job.
        mProvider.call("revoke", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Now allow the job to run and wait for it.
        setStorageState(false);
        assertTrue("Job with storage not low constraint did not fire when storage not low.",
                kTestEnvironment.awaitExecution());

        // Make sure the job still had the permission granted.
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckRead());
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckWrite());

        // And wait for everything to be cleaned up.
        waitPermissionRevoke(mFirstUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 5000);
    }

    /**
     * Test that we correctly fail when trying to grant permissions to things we don't
     * have access to.
     */
    public void testClipDataGrant_Failed() throws Exception {
        try {
            mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true)
                    .setClipData(mFirstClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());
        } catch (SecurityException e) {
            return;
        }

        fail("Security exception not thrown");
    }

    /**
     * Test basic granting of URI permissions associated with jobs and are correctly
     * retained when rescheduling the job.
     */
    public void testClipDataGrantReschedule() throws Exception {
        // We need to get a permission grant so that we can grant it to ourself.
        mProvider.call("grant", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Schedule the job, the system should now also be holding a URI grant for us.
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setMinimumLatency(60*60*1000)
                .setClipData(mFirstClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());

        // Remove the explicit grant, we should still have a grant due to the job.
        mProvider.call("revoke", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Now reschedule the job to have it happen right now.
        mJobScheduler.schedule(mBuilder.setMinimumLatency(0)
                .setClipData(mFirstClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());
        assertTrue("Job with storage not low constraint did not fire when storage not low.",
                kTestEnvironment.awaitExecution());

        // Make sure the job still had the permission granted.
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckRead());
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckWrite());

        // And wait for everything to be cleaned up.
        waitPermissionRevoke(mFirstUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 5000);
    }
}
