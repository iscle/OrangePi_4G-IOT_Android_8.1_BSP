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

package com.android.tv.recommendation;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.media.tv.TvContractCompat;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.PreviewDataManager;
import com.android.tv.data.PreviewProgramContent;
import com.android.tv.data.Program;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Class for updating the preview programs for {@link Channel}. */
@RequiresApi(Build.VERSION_CODES.O)
public class ChannelPreviewUpdater {
    private static final String TAG = "ChannelPreviewUpdater";
    // STOPSHIP: set it to false.
    private static final boolean DEBUG = true;

    private static final int UPATE_PREVIEW_PROGRAMS_JOB_ID = 1000001;
    private static final long ROUTINE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);
    // The left time of a program should meet the threshold so that it could be recommended.
    private static final long RECOMMENDATION_THRESHOLD_LEFT_TIME_MS =
            TimeUnit.MINUTES.toMillis(10);
    private static final int RECOMMENDATION_THRESHOLD_PROGRESS = 90;  // 90%
    private static final int RECOMMENDATION_COUNT = 6;
    private static final int MIN_COUNT_TO_ADD_ROW = 4;

    private static ChannelPreviewUpdater sChannelPreviewUpdater;

    /**
     * Creates and returns the {@link ChannelPreviewUpdater}.
     */
    public static ChannelPreviewUpdater getInstance(Context context) {
        if (sChannelPreviewUpdater == null) {
            sChannelPreviewUpdater = new ChannelPreviewUpdater(context.getApplicationContext());
        }
        return sChannelPreviewUpdater;
    }

    private final Context mContext;
    private final Recommender mRecommender;
    private final PreviewDataManager mPreviewDataManager;
    private JobService mJobService;
    private JobParameters mJobParams;

    private final ParentalControlSettings mParentalControlSettings;

    private boolean mNeedUpdateAfterRecommenderReady = false;

    private Recommender.Listener mRecommenderListener = new Recommender.Listener() {
        @Override
        public void onRecommenderReady() {
            if (mNeedUpdateAfterRecommenderReady) {
                if (DEBUG) Log.d(TAG, "Recommender is ready");
                updatePreviewDataForChannelsImmediately();
                mNeedUpdateAfterRecommenderReady = false;
            }
        }

        @Override
        public void onRecommendationChanged() {
            updatePreviewDataForChannelsImmediately();
        }
    };

    private ChannelPreviewUpdater(Context context) {
        mContext = context;
        mRecommender = new Recommender(context, mRecommenderListener, true);
        mRecommender.registerEvaluator(new RandomEvaluator(), 0.1, 0.1);
        mRecommender.registerEvaluator(new FavoriteChannelEvaluator(), 0.5, 0.5);
        mRecommender.registerEvaluator(new RoutineWatchEvaluator(), 1.0, 1.0);
        ApplicationSingletons appSingleton = TvApplication.getSingletons(context);
        mPreviewDataManager = appSingleton.getPreviewDataManager();
        mParentalControlSettings = appSingleton.getTvInputManagerHelper()
                .getParentalControlSettings();
    }

    /**
     * Starts the routine service for updating the preview programs.
     */
    public void startRoutineService() {
        JobScheduler jobScheduler =
                (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler.getPendingJob(UPATE_PREVIEW_PROGRAMS_JOB_ID) != null) {
            if (DEBUG) Log.d(TAG, "UPDATE_PREVIEW_JOB already exists");
            return;
        }
        JobInfo job = new JobInfo.Builder(UPATE_PREVIEW_PROGRAMS_JOB_ID,
                new ComponentName(mContext, ChannelPreviewUpdateService.class))
                .setPeriodic(ROUTINE_INTERVAL_MS)
                .setPersisted(true)
                .build();
        if (jobScheduler.schedule(job) < 0) {
            Log.i(TAG, "JobScheduler failed to schedule the job");
        }
    }

    /** Called when {@link ChannelPreviewUpdateService} is started. */
    void onStartJob(JobService service, JobParameters params) {
        if (DEBUG) Log.d(TAG, "onStartJob");
        mJobService = service;
        mJobParams = params;
        updatePreviewDataForChannelsImmediately();
    }

    /**
     * Updates the preview programs table.
     */
    public void updatePreviewDataForChannelsImmediately() {
        if (!mRecommender.isReady()) {
            mNeedUpdateAfterRecommenderReady = true;
            return;
        }

        if (!mPreviewDataManager.isLoadFinished()) {
            mPreviewDataManager.addListener(new PreviewDataManager.PreviewDataListener() {
                @Override
                public void onPreviewDataLoadFinished() {
                    mPreviewDataManager.removeListener(this);
                    updatePreviewDataForChannels();
                }

                @Override
                public void onPreviewDataUpdateFinished() { }
            });
            return;
        }
        updatePreviewDataForChannels();
    }

    /** Called when {@link ChannelPreviewUpdateService} is stopped. */
    void onStopJob() {
        if (DEBUG) Log.d(TAG, "onStopJob");
        mJobService = null;
        mJobParams = null;
    }

    private void updatePreviewDataForChannels() {
        new AsyncTask<Void, Void, Set<Program>>() {
            @Override
            protected Set<Program> doInBackground(Void... params) {
                Set<Program> programs = new HashSet<>();
                List<Channel> channels = new ArrayList<>(mRecommender.recommendChannels());
                for (Channel channel : channels) {
                    if (channel.isPhysicalTunerChannel()) {
                        final Program program = Utils.getCurrentProgram(mContext, channel.getId());
                        if (program != null
                                && isChannelRecommendationApplicable(channel, program)) {
                            programs.add(program);
                            if (programs.size() >= RECOMMENDATION_COUNT) {
                                break;
                            }
                        }
                    }
                }
                return programs;
            }

            private boolean isChannelRecommendationApplicable(Channel channel, Program program) {
                final long programDurationMs =
                        program.getEndTimeUtcMillis() - program.getStartTimeUtcMillis();
                if (programDurationMs <= 0) {
                    return false;
                }
                if (TextUtils.isEmpty(program.getPosterArtUri())) {
                    return false;
                }
                if (mParentalControlSettings.isParentalControlsEnabled()
                        && (channel.isLocked()
                                || mParentalControlSettings.isRatingBlocked(
                                        program.getContentRatings()))) {
                    return false;
                }
                long programLeftTimsMs = program.getEndTimeUtcMillis() - System.currentTimeMillis();
                final int programProgress =
                        (programDurationMs <= 0)
                                ? -1
                                : 100 - (int) (programLeftTimsMs * 100 / programDurationMs);

                // We recommend those programs that meet the condition only.
                return programProgress < RECOMMENDATION_THRESHOLD_PROGRESS
                        || programLeftTimsMs > RECOMMENDATION_THRESHOLD_LEFT_TIME_MS;
            }

            @Override
            protected void onPostExecute(Set<Program> programs) {
                updatePreviewDataForChannelsInternal(programs);
            }
        }.execute();
    }

    private void updatePreviewDataForChannelsInternal(Set<Program> programs) {
        long defaultPreviewChannelId = mPreviewDataManager.getPreviewChannelId(
                PreviewDataManager.TYPE_DEFAULT_PREVIEW_CHANNEL);
        if (defaultPreviewChannelId == PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID) {
            // Only create if there is enough programs
            if (programs.size() > MIN_COUNT_TO_ADD_ROW) {
                mPreviewDataManager.createDefaultPreviewChannel(
                        new PreviewDataManager.OnPreviewChannelCreationResultListener() {
                            @Override
                            public void onPreviewChannelCreationResult(
                                    long createdPreviewChannelId) {
                                if (createdPreviewChannelId
                                        != PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID) {
                                    TvContractCompat.requestChannelBrowsable(
                                            mContext, createdPreviewChannelId);
                                    updatePreviewProgramsForPreviewChannel(
                                            createdPreviewChannelId,
                                            generatePreviewProgramContentsFromPrograms(
                                                    createdPreviewChannelId, programs));
                                }
                            }
                        });
            }
        } else {
            updatePreviewProgramsForPreviewChannel(defaultPreviewChannelId,
                    generatePreviewProgramContentsFromPrograms(defaultPreviewChannelId, programs));
        }
    }

    private Set<PreviewProgramContent> generatePreviewProgramContentsFromPrograms(
            long previewChannelId, Set<Program> programs) {
        Set<PreviewProgramContent> result = new HashSet<>();
        for (Program program : programs) {
            PreviewProgramContent previewProgramContent =
                    PreviewProgramContent.createFromProgram(mContext, previewChannelId, program);
            if (previewProgramContent != null) {
                result.add(previewProgramContent);
            }
        }
        return result;
    }

    private void updatePreviewProgramsForPreviewChannel(long previewChannelId,
            Set<PreviewProgramContent> previewProgramContents) {
        PreviewDataManager.PreviewDataListener previewDataListener
                = new PreviewDataManager.PreviewDataListener() {
            @Override
            public void onPreviewDataLoadFinished() { }

            @Override
            public void onPreviewDataUpdateFinished() {
                mPreviewDataManager.removeListener(this);
                if (mJobService != null && mJobParams != null) {
                    if (DEBUG) Log.d(TAG, "UpdateAsyncTask.onPostExecute with JobService");
                    mJobService.jobFinished(mJobParams, false);
                    mJobService = null;
                    mJobParams = null;
                } else {
                    if (DEBUG) Log.d(TAG, "UpdateAsyncTask.onPostExecute without JobService");
                }
            }
        };
        mPreviewDataManager.updatePreviewProgramsForChannel(
                previewChannelId, previewProgramContents, previewDataListener);
    }

    /**
     * Job to execute the update of preview programs.
     */
    public static class ChannelPreviewUpdateService extends JobService {
        private ChannelPreviewUpdater mChannelPreviewUpdater;

        @Override
        public void onCreate() {
            TvApplication.setCurrentRunningProcess(this, true);
            if (DEBUG) Log.d(TAG, "ChannelPreviewUpdateService.onCreate");
            mChannelPreviewUpdater = ChannelPreviewUpdater.getInstance(this);
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            mChannelPreviewUpdater.onStartJob(this, params);
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            mChannelPreviewUpdater.onStopJob();
            return false;
        }

        @Override
        public void onDestroy() {
            if (DEBUG) Log.d(TAG, "ChannelPreviewUpdateService.onDestroy");
        }
    }
}
