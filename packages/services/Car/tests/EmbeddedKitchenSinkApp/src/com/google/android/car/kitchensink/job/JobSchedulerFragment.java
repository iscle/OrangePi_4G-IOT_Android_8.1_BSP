/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.car.kitchensink.job;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.car.kitchensink.R;

import java.util.List;

public class JobSchedulerFragment extends Fragment {
    private static final String TAG = "JobSchedulerFragment";
    private static final String PREFS_NEXT_JOB_ID = "next_job_id";

    private Button mScheduleButton;
    private Button mRefreshButton;
    private Button mCancelButton;
    private CheckBox mRequireCharging;
    private CheckBox mRequireIdle;
    private CheckBox mRequirePersisted;
    private RadioGroup mNetworkGroup;
    private EditText mDishNum;
    private TextView mJobInfo;
    private JobScheduler mJobScheduler;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.job_scheduler, container, false);
        mScheduleButton = (Button) v.findViewById(R.id.schedule_button);
        mScheduleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scheduleJob();
            }
        });
        mRefreshButton = (Button) v.findViewById(R.id.refresh_button);
        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshCurrentJobs();
            }
        });

        mNetworkGroup = (RadioGroup) v.findViewById(R.id.network_group);
        mDishNum = (EditText) v.findViewById(R.id.dish_num);
        mRequireCharging = (CheckBox) v.findViewById(R.id.require_charging);
        mRequireIdle = (CheckBox) v.findViewById(R.id.require_idle);
        mRequirePersisted = (CheckBox) v.findViewById(R.id.require_persisted);
        mJobScheduler = (JobScheduler) getContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);

        mJobInfo = (TextView) v.findViewById(R.id.current_jobs);

        mCancelButton = (Button) v.findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mJobScheduler.cancelAll();
                refreshCurrentJobs();
            }
        });
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCurrentJobs();
    }

    private void refreshCurrentJobs() {
        StringBuilder sb = new StringBuilder();
        List<JobInfo> jobs = mJobScheduler.getAllPendingJobs();
        for (JobInfo job : jobs) {
            sb.append("JobId: ");
            sb.append(job.getId());
            sb.append("\nDishCount: ");
            sb.append(job.getExtras().getInt(DishService.EXTRA_DISH_COUNT, 0));
            sb.append("\n");
        }
        mJobInfo.setText(sb.toString());
    }

    private void scheduleJob() {
        ComponentName jobComponentName = new ComponentName(getContext(), DishService.class);
        SharedPreferences prefs = getContext()
                .getSharedPreferences(PREFS_NEXT_JOB_ID, Context.MODE_PRIVATE);
        int jobId = prefs.getInt(PREFS_NEXT_JOB_ID, 0);
        PersistableBundle bundle = new PersistableBundle();
        int count = 50;
        try {
            count = Integer.valueOf(mDishNum.getText().toString());
        } catch (NumberFormatException e) {
            Log.e(TAG, "NOT A NUMBER!!!");
        }

        int selected = mNetworkGroup.getCheckedRadioButtonId();
        int networkType = JobInfo.NETWORK_TYPE_ANY;
        switch (selected) {
            case R.id.network_none:
                networkType = JobInfo.NETWORK_TYPE_NONE;
                break;
            case R.id.network_unmetered:
                networkType = JobInfo.NETWORK_TYPE_UNMETERED;
                break;
            case R.id.network_any:
                networkType = JobInfo.NETWORK_TYPE_ANY;
                break;
        }
        bundle.putInt(DishService.EXTRA_DISH_COUNT, count);
        JobInfo jobInfo = new JobInfo.Builder(jobId, jobComponentName)
                .setRequiresCharging(mRequireCharging.isChecked())
                .setRequiresDeviceIdle(mRequireIdle.isChecked())
                // TODO: figure out why we crash here even we hold
                // the RECEIVE_BOOT_COMPLETE permission
                //.setPersisted(mRequirePersisted.isChecked())
                .setExtras(bundle)
                .setRequiredNetworkType(networkType)
                .build();


        mJobScheduler.schedule(jobInfo);
        Toast.makeText(getContext(), "Scheduled: " + jobInfo, Toast.LENGTH_LONG ).show();

        Log.d(TAG, "Scheduled a job: " + jobInfo);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREFS_NEXT_JOB_ID, jobId + 1);
        editor.commit();

        refreshCurrentJobs();
    }
}
