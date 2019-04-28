/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.automatic;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import com.android.storagemanager.testing.TestingConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=TestingConstants.MANIFEST, sdk=TestingConstants.SDK_VERSION)
public class AutomaticStorageBroadcastReceiverTest {
    @Mock private Context mMockContext;
    @Mock private JobScheduler mJobScheduler;
    @Mock private Intent mMockIntent;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetupJobServicesOnBoot() {
        when(mMockContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(
                mJobScheduler);

        AutomaticStorageBroadcastReceiver br = new AutomaticStorageBroadcastReceiver();
        br.onReceive(mMockContext, mMockIntent);

        // Verify that the JobScheduler scheduled two jobs.
        ArgumentCaptor<JobInfo> jobCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mJobScheduler, times(1)).schedule(jobCaptor.capture());
        verifyNoMoreInteractions(mJobScheduler);

        // Ensure that the jobs are the ones we expect.
        List<JobInfo> capturedJobs = jobCaptor.getAllValues();
        JobInfo asmJob = capturedJobs.get(0);
        assertThat(asmJob.getService().getClassName())
                .isEqualTo(AutomaticStorageManagementJobService.class.getName());
        assertThat(asmJob.isRequireCharging()).isTrue();
        assertThat(asmJob.isRequireDeviceIdle()).isTrue();
    }
}
