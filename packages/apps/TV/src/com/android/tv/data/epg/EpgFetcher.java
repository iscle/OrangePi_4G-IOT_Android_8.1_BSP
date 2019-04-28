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

package com.android.tv.data.epg;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.media.tv.TvInputInfo;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.Features;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.config.RemoteConfigUtils;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ChannelLogoFetcher;
import com.android.tv.data.Lineup;
import com.android.tv.data.Program;
import com.android.tv.perf.EventNames;
import com.android.tv.perf.PerformanceMonitor;
import com.android.tv.perf.TimerEvent;
import com.android.tv.tuner.util.PostalCodeUtils;
import com.android.tv.util.LocationUtils;
import com.android.tv.util.NetworkTrafficTags;
import com.android.tv.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The service class to fetch EPG routinely or on-demand during channel scanning
 *
 * <p>Since the default executor of {@link AsyncTask} is {@link AsyncTask#SERIAL_EXECUTOR}, only one
 * task can run at a time. Because fetching EPG takes long time, the fetching task shouldn't run on
 * the serial executor. Instead, it should run on the {@link AsyncTask#THREAD_POOL_EXECUTOR}.
 */
public class EpgFetcher {
    private static final String TAG = "EpgFetcher";
    private static final boolean DEBUG = false;

    private static final int EPG_ROUTINELY_FETCHING_JOB_ID = 101;

    private static final long INITIAL_BACKOFF_MS = TimeUnit.SECONDS.toMillis(10);

    private static final int REASON_EPG_READER_NOT_READY = 1;
    private static final int REASON_LOCATION_INFO_UNAVAILABLE = 2;
    private static final int REASON_LOCATION_PERMISSION_NOT_GRANTED = 3;
    private static final int REASON_NO_EPG_DATA_RETURNED = 4;
    private static final int REASON_NO_NEW_EPG = 5;

    private static final long FETCH_DURING_SCAN_WAIT_TIME_MS = TimeUnit.SECONDS.toMillis(10);

    private static final long FETCH_DURING_SCAN_DURATION_SEC = TimeUnit.HOURS.toSeconds(3);
    private static final long FAST_FETCH_DURATION_SEC = TimeUnit.DAYS.toSeconds(2);

    private static final int DEFAULT_ROUTINE_INTERVAL_HOUR = 4;
    private static final String KEY_ROUTINE_INTERVAL = "live_channels_epg_fetcher_interval_hour";

    private static final int MSG_PREPARE_FETCH_DURING_SCAN = 1;
    private static final int MSG_CHANNEL_UPDATED_DURING_SCAN = 2;
    private static final int MSG_FINISH_FETCH_DURING_SCAN = 3;
    private static final int MSG_RETRY_PREPARE_FETCH_DURING_SCAN = 4;

    private static final int QUERY_CHANNEL_COUNT = 50;
    private static final int MINIMUM_CHANNELS_TO_DECIDE_LINEUP = 3;

    private static EpgFetcher sInstance;

    private final Context mContext;
    private final ChannelDataManager mChannelDataManager;
    private final EpgReader mEpgReader;
    private final PerformanceMonitor mPerformanceMonitor;
    private FetchAsyncTask mFetchTask;
    private FetchDuringScanHandler mFetchDuringScanHandler;
    private long mEpgTimeStamp;
    private List<Lineup> mPossibleLineups;
    private final Object mPossibleLineupsLock = new Object();
    private final Object mFetchDuringScanHandlerLock = new Object();
    // A flag to block the re-entrance of onChannelScanStarted and onChannelScanFinished.
    private boolean mScanStarted;

    private final long mRoutineIntervalMs;
    private final long mEpgDataExpiredTimeLimitMs;
    private final long mFastFetchDurationSec;

    public static EpgFetcher getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new EpgFetcher(context);
        }
        return sInstance;
    }

    /** Creates and returns {@link EpgReader}. */
    public static EpgReader createEpgReader(Context context, String region) {
        return new StubEpgReader(context);
    }

    private EpgFetcher(Context context) {
        mContext = context.getApplicationContext();
        ApplicationSingletons applicationSingletons = TvApplication.getSingletons(mContext);
        mChannelDataManager = applicationSingletons.getChannelDataManager();
        mPerformanceMonitor = applicationSingletons.getPerformanceMonitor();
        mEpgReader = createEpgReader(mContext, LocationUtils.getCurrentCountry(mContext));

        int remoteInteval =
                (int) RemoteConfigUtils.getRemoteConfig(
                        context, KEY_ROUTINE_INTERVAL, DEFAULT_ROUTINE_INTERVAL_HOUR);
        mRoutineIntervalMs =
                remoteInteval < 0
                        ? TimeUnit.HOURS.toMillis(DEFAULT_ROUTINE_INTERVAL_HOUR)
                        : TimeUnit.HOURS.toMillis(remoteInteval);
        mEpgDataExpiredTimeLimitMs = mRoutineIntervalMs * 2;
        mFastFetchDurationSec = FAST_FETCH_DURATION_SEC + mRoutineIntervalMs / 1000;
    }

    /**
     * Starts the routine service of EPG fetching. It use {@link JobScheduler} to schedule the EPG
     * fetching routine. The EPG fetching routine will be started roughly every 4 hours, unless
     * the channel scanning of tuner input is started.
     */
    @MainThread
    public void startRoutineService() {
        JobScheduler jobScheduler =
                (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (JobInfo job : jobScheduler.getAllPendingJobs()) {
            if (job.getId() == EPG_ROUTINELY_FETCHING_JOB_ID) {
                return;
            }
        }
        JobInfo job =
                new JobInfo.Builder(
                                EPG_ROUTINELY_FETCHING_JOB_ID,
                                new ComponentName(mContext, EpgFetchService.class))
                        .setPeriodic(mRoutineIntervalMs)
                        .setBackoffCriteria(INITIAL_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
        Log.i(TAG, "EPG fetching routine service started.");
    }

    /**
     * Fetches EPG immediately if current EPG data are out-dated, i.e., not successfully updated
     * by routine fetching service due to various reasons.
     */
    @MainThread
    public void fetchImmediatelyIfNeeded() {
        if (TvCommonUtils.isRunningInTest()) {
            // Do not run EpgFetcher in test.
            return;
        }
        new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... args) {
                return EpgFetchHelper.getLastEpgUpdatedTimestamp(mContext);
            }

            @Override
            protected void onPostExecute(Long result) {
                if (System.currentTimeMillis() - EpgFetchHelper.getLastEpgUpdatedTimestamp(mContext)
                        > mEpgDataExpiredTimeLimitMs) {
                    Log.i(TAG, "EPG data expired. Start fetching immediately.");
                    fetchImmediately();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Fetches EPG immediately.
     */
    @MainThread
    public void fetchImmediately() {
        if (!mChannelDataManager.isDbLoadFinished()) {
            mChannelDataManager.addListener(new ChannelDataManager.Listener() {
                @Override
                public void onLoadFinished() {
                    mChannelDataManager.removeListener(this);
                    executeFetchTaskIfPossible(null, null);
                }

                @Override
                public void onChannelListUpdated() { }

                @Override
                public void onChannelBrowsableChanged() { }
            });
        } else {
            executeFetchTaskIfPossible(null, null);
        }
    }

    /**
     * Notifies EPG fetch service that channel scanning is started.
     */
    @MainThread
    public void onChannelScanStarted() {
        if (mScanStarted || !Features.ENABLE_CLOUD_EPG_REGION.isEnabled(mContext)) {
            return;
        }
        mScanStarted = true;
        stopFetchingJob();
        synchronized (mFetchDuringScanHandlerLock) {
            if (mFetchDuringScanHandler == null) {
                HandlerThread thread = new HandlerThread("EpgFetchDuringScan");
                thread.start();
                mFetchDuringScanHandler = new FetchDuringScanHandler(thread.getLooper());
            }
            mFetchDuringScanHandler.sendEmptyMessage(MSG_PREPARE_FETCH_DURING_SCAN);
        }
        Log.i(TAG, "EPG fetching on channel scanning started.");
    }

    /**
     * Notifies EPG fetch service that channel scanning is finished.
     */
    @MainThread
    public void onChannelScanFinished() {
        if (!mScanStarted) {
            return;
        }
        mScanStarted = false;
        mFetchDuringScanHandler.sendEmptyMessage(MSG_FINISH_FETCH_DURING_SCAN);
    }

    @MainThread
    private void stopFetchingJob() {
        if (DEBUG) Log.d(TAG, "Try to stop routinely fetching job...");
        if (mFetchTask != null) {
            mFetchTask.cancel(true);
            mFetchTask = null;
            Log.i(TAG, "EPG routinely fetching job stopped.");
        }
    }

    @MainThread
    private boolean executeFetchTaskIfPossible(JobService service, JobParameters params) {
        SoftPreconditions.checkState(mChannelDataManager.isDbLoadFinished());
        if (!TvCommonUtils.isRunningInTest() && checkFetchPrerequisite()) {
            mFetchTask = new FetchAsyncTask(service, params);
            mFetchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;
        }
        return false;
    }

    @MainThread
    private boolean checkFetchPrerequisite() {
        if (DEBUG) Log.d(TAG, "Check prerequisite of routinely fetching job.");
        if (!Features.ENABLE_CLOUD_EPG_REGION.isEnabled(mContext)) {
            Log.i(TAG, "Cannot start routine service: country not supported: "
                    + LocationUtils.getCurrentCountry(mContext));
            return false;
        }
        if (mFetchTask != null) {
            // Fetching job is already running or ready to run, no need to start again.
            return false;
        }
        if (mFetchDuringScanHandler != null) {
            if (DEBUG) Log.d(TAG, "Cannot start routine service: scanning channels.");
            return false;
        }
        if (getTunerChannelCount() == 0) {
            if (DEBUG) Log.d(TAG, "Cannot start routine service: no internal tuner channels.");
            return false;
        }
        if (!TextUtils.isEmpty(EpgFetchHelper.getLastLineupId(mContext))) {
            return true;
        }
        if (!TextUtils.isEmpty(PostalCodeUtils.getLastPostalCode(mContext))) {
            return true;
        }
        return true;
    }

    @MainThread
    private int getTunerChannelCount() {
        for (TvInputInfo input : TvApplication.getSingletons(mContext)
                .getTvInputManagerHelper().getTvInputInfos(true, true)) {
            String inputId = input.getId();
            if (Utils.isInternalTvInput(mContext, inputId)) {
                return mChannelDataManager.getChannelCountForInput(inputId);
            }
        }
        return 0;
    }

    @AnyThread
    private void clearUnusedLineups(@Nullable String lineupId) {
        synchronized (mPossibleLineupsLock) {
            if (mPossibleLineups == null) {
                return;
            }
            for (Lineup lineup : mPossibleLineups) {
                if (!TextUtils.equals(lineupId, lineup.id)) {
                    mEpgReader.clearCachedChannels(lineup.id);
                }
            }
            mPossibleLineups = null;
        }
    }

    @WorkerThread
    private Integer prepareFetchEpg(boolean forceUpdatePossibleLineups) {
        if (!mEpgReader.isAvailable()) {
            Log.i(TAG, "EPG reader is temporarily unavailable.");
            return REASON_EPG_READER_NOT_READY;
        }
        // Checks the EPG Timestamp.
        mEpgTimeStamp = mEpgReader.getEpgTimestamp();
        if (mEpgTimeStamp <= EpgFetchHelper.getLastEpgUpdatedTimestamp(mContext)) {
            if (DEBUG) Log.d(TAG, "No new EPG.");
            return REASON_NO_NEW_EPG;
        }
        // Updates postal code.
        boolean postalCodeChanged = false;
        try {
            postalCodeChanged = PostalCodeUtils.updatePostalCode(mContext);
        } catch (IOException e) {
            if (DEBUG) Log.d(TAG, "Couldn't get the current location.", e);
            if (TextUtils.isEmpty(PostalCodeUtils.getLastPostalCode(mContext))) {
                return REASON_LOCATION_INFO_UNAVAILABLE;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to get the current location.");
            if (TextUtils.isEmpty(PostalCodeUtils.getLastPostalCode(mContext))) {
                return REASON_LOCATION_PERMISSION_NOT_GRANTED;
            }
        } catch (PostalCodeUtils.NoPostalCodeException e) {
            Log.i(TAG, "Cannot get address or postal code.");
            return REASON_LOCATION_INFO_UNAVAILABLE;
        }
        // Updates possible lineups if necessary.
        SoftPreconditions.checkState(mPossibleLineups == null, TAG, "Possible lineups not reset.");
        if (postalCodeChanged || forceUpdatePossibleLineups
                || EpgFetchHelper.getLastLineupId(mContext) == null) {
            // To prevent main thread being blocked, though theoretically it should not happen.
            List<Lineup> possibleLineups =
                    mEpgReader.getLineups(PostalCodeUtils.getLastPostalCode(mContext));
            if (possibleLineups.isEmpty()) {
                return REASON_NO_EPG_DATA_RETURNED;
            }
            for (Lineup lineup : possibleLineups) {
                mEpgReader.preloadChannels(lineup.id);
            }
            synchronized (mPossibleLineupsLock) {
                mPossibleLineups = possibleLineups;
            }
            EpgFetchHelper.setLastLineupId(mContext, null);
        }
        return null;
    }

    @WorkerThread
    private void batchFetchEpg(List<Channel> channels, long durationSec) {
        Log.i(TAG, "Start batch fetching (" + durationSec + ")...." + channels.size());
        if (channels.size() == 0) {
            return;
        }
        List<Long> queryChannelIds = new ArrayList<>(QUERY_CHANNEL_COUNT);
        for (Channel channel : channels) {
            queryChannelIds.add(channel.getId());
            if (queryChannelIds.size() >= QUERY_CHANNEL_COUNT) {
                batchUpdateEpg(mEpgReader.getPrograms(queryChannelIds, durationSec));
                queryChannelIds.clear();
            }
        }
        if (!queryChannelIds.isEmpty()) {
            batchUpdateEpg(mEpgReader.getPrograms(queryChannelIds, durationSec));
        }
    }

    @WorkerThread
    private void batchUpdateEpg(Map<Long, List<Program>> allPrograms) {
        for (Map.Entry<Long, List<Program>> entry : allPrograms.entrySet()) {
            List<Program> programs = entry.getValue();
            if (programs == null) {
                continue;
            }
            Collections.sort(programs);
            Log.i(TAG, "Batch fetched " + programs.size() + " programs for channel "
                    + entry.getKey());
            EpgFetchHelper.updateEpgData(mContext, entry.getKey(), programs);
        }
    }

    @Nullable
    @WorkerThread
    private String pickBestLineupId(List<Channel> currentChannelList) {
        String maxLineupId = null;
        synchronized (mPossibleLineupsLock) {
            if (mPossibleLineups == null) {
                return null;
            }
            int maxCount = 0;
            for (Lineup lineup : mPossibleLineups) {
                int count = getMatchedChannelCount(lineup.id, currentChannelList);
                Log.i(TAG, lineup.name + " (" + lineup.id + ") - " + count + " matches");
                if (count > maxCount) {
                    maxCount = count;
                    maxLineupId = lineup.id;
                }
            }
        }
        return maxLineupId;
    }

    @WorkerThread
    private int getMatchedChannelCount(String lineupId, List<Channel> currentChannelList) {
        // Construct a list of display numbers for existing channels.
        if (currentChannelList.isEmpty()) {
            if (DEBUG) Log.d(TAG, "No existing channel to compare");
            return 0;
        }
        List<String> numbers = new ArrayList<>(currentChannelList.size());
        for (Channel channel : currentChannelList) {
            // We only support channels from internal tuner inputs.
            if (Utils.isInternalTvInput(mContext, channel.getInputId())) {
                numbers.add(channel.getDisplayNumber());
            }
        }
        numbers.retainAll(mEpgReader.getChannelNumbers(lineupId));
        return numbers.size();
    }

    public static class EpgFetchService extends JobService {
        private EpgFetcher mEpgFetcher;

        @Override
        public void onCreate() {
            super.onCreate();
            TvApplication.setCurrentRunningProcess(this, true);
            mEpgFetcher = EpgFetcher.getInstance(this);
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            if (!mEpgFetcher.mChannelDataManager.isDbLoadFinished()) {
                mEpgFetcher.mChannelDataManager.addListener(new ChannelDataManager.Listener() {
                    @Override
                    public void onLoadFinished() {
                        mEpgFetcher.mChannelDataManager.removeListener(this);
                        if (!mEpgFetcher.executeFetchTaskIfPossible(EpgFetchService.this, params)) {
                            jobFinished(params, false);
                        }
                    }

                    @Override
                    public void onChannelListUpdated() { }

                    @Override
                    public void onChannelBrowsableChanged() { }
                });
                return true;
            } else {
                return mEpgFetcher.executeFetchTaskIfPossible(this, params);
            }
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            mEpgFetcher.stopFetchingJob();
            return false;
        }
    }

    private class FetchAsyncTask extends AsyncTask<Void, Void, Integer> {
        private final JobService mService;
        private final JobParameters mParams;
        private List<Channel> mCurrentChannelList;
        private TimerEvent mTimerEvent;

        private FetchAsyncTask(JobService service, JobParameters params) {
            mService = service;
            mParams = params;
        }

        @Override
        protected void onPreExecute() {
            mTimerEvent = mPerformanceMonitor.startTimer();
            mCurrentChannelList = mChannelDataManager.getChannelList();
        }

        @Override
        protected Integer doInBackground(Void... args) {
            final int oldTag = TrafficStats.getThreadStatsTag();
            TrafficStats.setThreadStatsTag(NetworkTrafficTags.EPG_FETCH);
            try {
                if (DEBUG) Log.d(TAG, "Start EPG routinely fetching.");
                Integer failureReason = prepareFetchEpg(false);
                // InterruptedException might be caught by RPC, we should check it here.
                if (failureReason != null || this.isCancelled()) {
                    return failureReason;
                }
                String lineupId = EpgFetchHelper.getLastLineupId(mContext);
                lineupId = lineupId == null ? pickBestLineupId(mCurrentChannelList) : lineupId;
                if (lineupId != null) {
                    Log.i(TAG, "Selecting the lineup " + lineupId);
                    // During normal fetching process, the lineup ID should be confirmed since all
                    // channels are known, clear up possible lineups to save resources.
                    EpgFetchHelper.setLastLineupId(mContext, lineupId);
                    clearUnusedLineups(lineupId);
                } else {
                    Log.i(TAG, "Failed to get lineup id");
                    return REASON_NO_EPG_DATA_RETURNED;
                }
                final List<Channel> channels = mEpgReader.getChannels(lineupId);
                // InterruptedException might be caught by RPC, we should check it here.
                if (this.isCancelled()) {
                    return null;
                }
                if (channels.isEmpty()) {
                    Log.i(TAG, "Failed to get EPG channels.");
                    return REASON_NO_EPG_DATA_RETURNED;
                }
                if (System.currentTimeMillis() - EpgFetchHelper.getLastEpgUpdatedTimestamp(mContext)
                        > mEpgDataExpiredTimeLimitMs) {
                    batchFetchEpg(channels, mFastFetchDurationSec);
                }
                new Handler(mContext.getMainLooper())
                        .post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        ChannelLogoFetcher.startFetchingChannelLogos(
                                                mContext, channels);
                                    }
                                });
                for (Channel channel : channels) {
                    if (this.isCancelled()) {
                        return null;
                    }
                    long channelId = channel.getId();
                    List<Program> programs = new ArrayList<>(mEpgReader.getPrograms(channelId));
                    // InterruptedException might be caught by RPC, we should check it here.
                    Collections.sort(programs);
                    Log.i(TAG, "Fetched " + programs.size() + " programs for channel " + channelId);
                    EpgFetchHelper.updateEpgData(mContext, channelId, programs);
                }
                EpgFetchHelper.setLastEpgUpdatedTimestamp(mContext, mEpgTimeStamp);
                if (DEBUG) Log.d(TAG, "Fetching EPG is finished.");
                return null;
            } finally {
                TrafficStats.setThreadStatsTag(oldTag);
            }
        }

        @Override
        protected void onPostExecute(Integer failureReason) {
            mFetchTask = null;
            if (failureReason == null || failureReason == REASON_LOCATION_PERMISSION_NOT_GRANTED
                    || failureReason == REASON_NO_NEW_EPG) {
                jobFinished(false);
            } else {
                // Applies back-off policy
                jobFinished(true);
            }
            mPerformanceMonitor.stopTimer(mTimerEvent, EventNames.FETCH_EPG_TASK);
            mPerformanceMonitor.recordMemory(EventNames.FETCH_EPG_TASK);
        }

        @Override
        protected void onCancelled(Integer failureReason) {
            clearUnusedLineups(null);
            jobFinished(false);
        }

        private void jobFinished(boolean reschedule) {
            if (mService != null && mParams != null) {
                // Task is executed from JobService, need to report jobFinished.
                mService.jobFinished(mParams, reschedule);
            }
        }
    }

    @WorkerThread
    private class FetchDuringScanHandler extends Handler {
        private final Set<Long> mFetchedChannelIdsDuringScan = new HashSet<>();
        private String mPossibleLineupId;

        private final ChannelDataManager.Listener mDuringScanChannelListener =
                new ChannelDataManager.Listener() {
                    @Override
                    public void onLoadFinished() {
                        if (DEBUG) Log.d(TAG, "ChannelDataManager.onLoadFinished()");
                        if (getTunerChannelCount() >= MINIMUM_CHANNELS_TO_DECIDE_LINEUP
                                && !hasMessages(MSG_CHANNEL_UPDATED_DURING_SCAN)) {
                            Message.obtain(FetchDuringScanHandler.this,
                                    MSG_CHANNEL_UPDATED_DURING_SCAN, new ArrayList<>(
                                            mChannelDataManager.getChannelList())).sendToTarget();
                        }
                    }

                    @Override
                    public void onChannelListUpdated() {
                        if (DEBUG) Log.d(TAG, "ChannelDataManager.onChannelListUpdated()");
                        if (getTunerChannelCount() >= MINIMUM_CHANNELS_TO_DECIDE_LINEUP
                                && !hasMessages(MSG_CHANNEL_UPDATED_DURING_SCAN)) {
                            Message.obtain(FetchDuringScanHandler.this,
                                    MSG_CHANNEL_UPDATED_DURING_SCAN,
                                            mChannelDataManager.getChannelList()).sendToTarget();
                        }
                    }

                    @Override
                    public void onChannelBrowsableChanged() {
                        // Do nothing
                    }
                };

        @AnyThread
        private FetchDuringScanHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PREPARE_FETCH_DURING_SCAN:
                case MSG_RETRY_PREPARE_FETCH_DURING_SCAN:
                    onPrepareFetchDuringScan();
                    break;
                case MSG_CHANNEL_UPDATED_DURING_SCAN:
                    if (!hasMessages(MSG_CHANNEL_UPDATED_DURING_SCAN)) {
                        onChannelUpdatedDuringScan((List<Channel>) msg.obj);
                    }
                    break;
                case MSG_FINISH_FETCH_DURING_SCAN:
                    removeMessages(MSG_RETRY_PREPARE_FETCH_DURING_SCAN);
                    if (hasMessages(MSG_CHANNEL_UPDATED_DURING_SCAN)) {
                        sendEmptyMessage(MSG_FINISH_FETCH_DURING_SCAN);
                    } else {
                        onFinishFetchDuringScan();
                    }
                    break;
            }
        }

        private void onPrepareFetchDuringScan() {
            Integer failureReason = prepareFetchEpg(true);
            if (failureReason != null) {
                sendEmptyMessageDelayed(
                        MSG_RETRY_PREPARE_FETCH_DURING_SCAN, FETCH_DURING_SCAN_WAIT_TIME_MS);
                return;
            }
            mChannelDataManager.addListener(mDuringScanChannelListener);
        }

        private void onChannelUpdatedDuringScan(List<Channel> currentChannelList) {
            String lineupId = pickBestLineupId(currentChannelList);
            Log.i(TAG, "Fast fetch channels for lineup ID: " + lineupId);
            if (TextUtils.isEmpty(lineupId)) {
                if (TextUtils.isEmpty(mPossibleLineupId)) {
                    return;
                }
            } else if (!TextUtils.equals(lineupId, mPossibleLineupId)) {
                mFetchedChannelIdsDuringScan.clear();
                mPossibleLineupId = lineupId;
            }
            List<Long> currentChannelIds = new ArrayList<>();
            for (Channel channel : currentChannelList) {
                currentChannelIds.add(channel.getId());
            }
            mFetchedChannelIdsDuringScan.retainAll(currentChannelIds);
            List<Channel> newChannels = new ArrayList<>();
            for (Channel channel : mEpgReader.getChannels(mPossibleLineupId)) {
                if (!mFetchedChannelIdsDuringScan.contains(channel.getId())) {
                    newChannels.add(channel);
                    mFetchedChannelIdsDuringScan.add(channel.getId());
                }
            }
            batchFetchEpg(newChannels, FETCH_DURING_SCAN_DURATION_SEC);
        }

        private void onFinishFetchDuringScan() {
            mChannelDataManager.removeListener(mDuringScanChannelListener);
            EpgFetchHelper.setLastLineupId(mContext, mPossibleLineupId);
            clearUnusedLineups(null);
            mFetchedChannelIdsDuringScan.clear();
            synchronized (mFetchDuringScanHandlerLock) {
                if (!hasMessages(MSG_PREPARE_FETCH_DURING_SCAN)) {
                    removeCallbacksAndMessages(null);
                    getLooper().quit();
                    mFetchDuringScanHandler = null;
                }
            }
            // Clear timestamp to make routine service start right away.
            EpgFetchHelper.setLastEpgUpdatedTimestamp(mContext, 0);
            Log.i(TAG, "EPG Fetching during channel scanning finished.");
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    fetchImmediately();
                }
            });
        }
    }
}
