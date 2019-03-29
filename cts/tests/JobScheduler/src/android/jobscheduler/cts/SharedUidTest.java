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
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.jobscheduler.MockJobService;
import android.test.AndroidTestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TargetApi(27)
public class SharedUidTest extends AndroidTestCase {
    private static final String TAG = "SharedUidTest";

    private static final String OTHER_PACKAGE = "android.jobscheduler.cts.shareduid";
    private static final long BROADCAST_TIMEOUT_SECONDS = 2 * 60;

    /**
     * Test to make sure disabling a package wouldn't cancel the jobs from other packages that use
     * the same shared UID.
     */
    public void testCancelDisabledPackageJob() throws Exception {
        final int JOBID = 1122331;

        final JobScheduler js = getContext().getSystemService(JobScheduler.class);

        try {
            JobInfo ji = new JobInfo.Builder(JOBID,
                    new ComponentName(getContext(), MockJobService.class))
                    .setMinimumLatency(1000 * 60 * 60 * 24 /* 1 day */)
                    .build();

            js.cancel(JOBID);

            assertEquals(JobScheduler.RESULT_SUCCESS, js.schedule(ji));

            // The job should be scheduled.
            assertNotNull("Job should be registered", js.getPendingJob(JOBID));

            // Create a filter with the loweset priority to wait until the jobscheduelr receives the
            // intent.
            final CountDownLatch latch = new CountDownLatch(1);

            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            filter.setPriority(IntentFilter.SYSTEM_LOW_PRIORITY);

            getContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (OTHER_PACKAGE.equals(intent.getData().getSchemeSpecificPart())) {
                        latch.countDown();
                    }
                }
            }, filter);


            // Disable the other package with the same UID.
            final PackageManager pm = getContext().getPackageManager();
            pm.setApplicationEnabledSetting(
                    OTHER_PACKAGE,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);

            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    pm.getApplicationEnabledSetting(OTHER_PACKAGE));

            // Wait until our receiver gets the broadcast.
            assertTrue(latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // Make sure the job hasn't been canceled.
            assertNotNull("Job shouldn't be canceled", js.getPendingJob(JOBID));
        } finally {
            js.cancel(JOBID);
        }
    }
}
