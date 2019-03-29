/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.example.android.tv.channelsprograms;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.AsyncTask;
import android.support.media.tv.TvContractCompat;
import android.util.Log;

import com.example.android.tv.channelsprograms.model.MockDatabase;
import com.example.android.tv.channelsprograms.model.MockMovieService;
import com.example.android.tv.channelsprograms.model.Subscription;
import com.example.android.tv.channelsprograms.util.TvUtil;

import java.util.List;

/**
 * Populates the TV provider with channels that every user should have. Once a channel is created,
 * it triggers another service to add programs.
 */
public class SyncChannelJobService extends JobService {

    private static final String TAG = "RecommendChannelJobSvc";

    private SyncChannelTask mSyncChannelTask;

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        Log.d(TAG, "Starting channel creation job");
        mSyncChannelTask =
                new SyncChannelTask(getApplicationContext()) {
                    @Override
                    protected void onPostExecute(Boolean success) {
                        super.onPostExecute(success);
                        jobFinished(jobParameters, !success);
                    }
                };
        mSyncChannelTask.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (mSyncChannelTask != null) {
            mSyncChannelTask.cancel(true);
        }
        return true;
    }

    private static class SyncChannelTask extends AsyncTask<Void, Void, Boolean> {

        private final Context mContext;

        SyncChannelTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            List<Subscription> subscriptions = MockDatabase.getSubscriptions(mContext);
            int numOfChannelsInTVProvider = TvUtil.getNumberOfChannels(mContext);
            // Checks if the default channels are added. Since a user can add more channels from
            // your app later, the number of channels in the provider can be greater than the number
            // of default channels.
            if (numOfChannelsInTVProvider >= subscriptions.size() && !subscriptions.isEmpty()) {
                Log.d(TAG, "Already loaded default channels into the provider");
            } else {
                // Create subscriptions from mocked source.
                subscriptions = MockMovieService.createUniversalSubscriptions(mContext);
                for (Subscription subscription : subscriptions) {
                    long channelId = TvUtil.createChannel(mContext, subscription);
                    subscription.setChannelId(channelId);
                    TvContractCompat.requestChannelBrowsable(mContext, channelId);
                }

                MockDatabase.saveSubscriptions(mContext, subscriptions);
            }

            // Kick off a job to update default programs.
            // The program job should verify if the channel is visible before updating programs.
            for (Subscription channel : subscriptions) {
                TvUtil.scheduleSyncingProgramsForChannel(mContext, channel.getChannelId());
            }
            return true;
        }
    }
}
