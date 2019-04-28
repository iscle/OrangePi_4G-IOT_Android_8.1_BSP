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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import java.lang.ref.WeakReference;

public class DishService extends JobService {
    private static final String TAG = "JobScheduler_DishService";
    private static final int DELAY_MS = 1000; // wash a plate every second!

    private static final int MSG_FINISHED = 0;
    private static final int MSG_RUN_JOB = 1;
    private static final int MSG_CANCEL_JOB = 2;

    public static final String EXTRA_DISH_COUNT = "dish_count";

    private final Handler mHandler = new Handler() {
        private SparseArray<JobParameters> mTaskMap = new SparseArray<JobParameters>();
        @Override
        public void handleMessage(Message msg) {
            JobParameters job = (JobParameters) msg.obj;
            switch (msg.what) {
                case MSG_FINISHED:
                    Log.d(TAG, "Job done! " + job.getJobId());
                    mTaskMap.remove(job.getJobId());
                    jobFinished(job, false);
                    break;
                case MSG_RUN_JOB:
                    DishWasherTask task = new DishWasherTask(this, job, msg.arg1);
                    task.execute();
                    mTaskMap.put(job.getJobId(), job);
                    break;
                case MSG_CANCEL_JOB:
                    JobParameters job1 = mTaskMap.get(job.getJobId());
                    if (job1 != null) {
                        removeMessages(MSG_RUN_JOB, job1);
                        Log.d(TAG, "cancelled job " + job1);
                        mTaskMap.remove(job.getJobId());
                    }
                    break;
                default:
                    Log.w(TAG, "Unknown message " + msg.what);
            }
        }
    };

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "onStopJob " + jobParameters);
        Message msg = mHandler.obtainMessage(MSG_CANCEL_JOB, 0, 0, jobParameters);
        mHandler.sendMessage(msg);
        return false;
    }

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        Log.d(TAG, "onStartJob " + jobParameters);
        Message msg = mHandler.obtainMessage(MSG_RUN_JOB, 0, 0, jobParameters);
        mHandler.sendMessage(msg);
        return true;
    }

    private static final class DishWasherTask extends AsyncTask<Void, Void, Boolean> {
        private final WeakReference<Handler> mHandler;
        private final JobParameters mJobParameter;
        private final int mMyDishNum;


        public DishWasherTask(Handler handler, JobParameters jobParameters, int dishNum) {
            mHandler = new WeakReference<Handler>(handler);
            mJobParameter = jobParameters;
            mMyDishNum = dishNum;
        }

        @Override
        protected Boolean doInBackground(Void... infos) {
            int dishTotal = mJobParameter.getExtras().getInt(EXTRA_DISH_COUNT);

            Log.d(TAG, "jobId: " + mJobParameter.getJobId() + " totalDish: " + dishTotal
                    + " washing: #" + mMyDishNum);
            wash();
            if (mMyDishNum >= dishTotal - 1) {
                // all done!
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mHandler.get() == null) {
                return;
            }
            if (result) {
                Message msg = mHandler.get().obtainMessage(MSG_RUN_JOB,
                        mMyDishNum +1, 0, mJobParameter);
                mHandler.get().sendMessageDelayed(msg, DELAY_MS);
            } else {
                Message msg = mHandler.get().obtainMessage(MSG_FINISHED, 0,
                        0, mJobParameter);
                mHandler.get().sendMessage(msg);
            }
        }

        private void wash() {
            // TODO: add heavy wash tasks here...
        }
    }
}
