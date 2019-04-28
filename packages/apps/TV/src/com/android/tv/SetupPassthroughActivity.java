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

package com.android.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.util.Log;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.TvCommonConstants;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ChannelDataManager.Listener;
import com.android.tv.data.epg.EpgFetcher;
import com.android.tv.experiments.Experiments;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.concurrent.TimeUnit;

/**
 * An activity to launch a TV input setup activity.
 *
 * <p> After setup activity is finished, all channels will be browsable.
 */
public class SetupPassthroughActivity extends Activity {
    private static final String TAG = "SetupPassthroughAct";
    private static final boolean DEBUG = false;

    private static final int REQUEST_START_SETUP_ACTIVITY = 200;

    private static ScanTimeoutMonitor sScanTimeoutMonitor;

    private TvInputInfo mTvInputInfo;
    private Intent mActivityAfterCompletion;
    private boolean mEpgFetcherDuringScan;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        ApplicationSingletons appSingletons = TvApplication.getSingletons(this);
        TvInputManagerHelper inputManager = appSingletons.getTvInputManagerHelper();
        Intent intent = getIntent();
        String inputId = intent.getStringExtra(TvCommonConstants.EXTRA_INPUT_ID);
        mTvInputInfo = inputManager.getTvInputInfo(inputId);
        mActivityAfterCompletion = intent.getParcelableExtra(
                TvCommonConstants.EXTRA_ACTIVITY_AFTER_COMPLETION);
        boolean needToFetchEpg = Utils.isInternalTvInput(this, mTvInputInfo.getId())
                && Experiments.CLOUD_EPG.get();
        if (needToFetchEpg) {
            // In case when the activity is restored, this flag should be restored as well.
            mEpgFetcherDuringScan = true;
        }
        if (savedInstanceState == null) {
            SoftPreconditions.checkState(
                    intent.getAction().equals(TvCommonConstants.INTENT_ACTION_INPUT_SETUP));
            if (DEBUG) Log.d(TAG, "TvInputId " + inputId + " / TvInputInfo " + mTvInputInfo);
            if (mTvInputInfo == null) {
                Log.w(TAG, "There is no input with the ID " + inputId + ".");
                finish();
                return;
            }
            Intent setupIntent =
                    intent.getExtras().getParcelable(TvCommonConstants.EXTRA_SETUP_INTENT);
            if (DEBUG) Log.d(TAG, "Setup activity launch intent: " + setupIntent);
            if (setupIntent == null) {
                Log.w(TAG, "The input (" + mTvInputInfo.getId() + ") doesn't have setup.");
                finish();
                return;
            }
            SetupUtils.grantEpgPermission(this, mTvInputInfo.getServiceInfo().packageName);
            if (DEBUG) Log.d(TAG, "Activity after completion " + mActivityAfterCompletion);
            // If EXTRA_SETUP_INTENT is not removed, an infinite recursion happens during
            // setupIntent.putExtras(intent.getExtras()).
            Bundle extras = intent.getExtras();
            extras.remove(TvCommonConstants.EXTRA_SETUP_INTENT);
            setupIntent.putExtras(extras);
            try {
                startActivityForResult(setupIntent, REQUEST_START_SETUP_ACTIVITY);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Can't find activity: " + setupIntent.getComponent());
                finish();
                return;
            }
            if (needToFetchEpg) {
                if (sScanTimeoutMonitor == null) {
                    sScanTimeoutMonitor = new ScanTimeoutMonitor(this);
                }
                sScanTimeoutMonitor.startMonitoring();
                EpgFetcher.getInstance(this).onChannelScanStarted();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, final int resultCode, final Intent data) {
        if (DEBUG) Log.d(TAG, "onActivityResult");
        if (sScanTimeoutMonitor != null) {
            sScanTimeoutMonitor.stopMonitoring();
        }
        // Note: It's not guaranteed that this method is always called after scanning.
        boolean setupComplete = requestCode == REQUEST_START_SETUP_ACTIVITY
                && resultCode == Activity.RESULT_OK;
        // Tells EpgFetcher that channel source setup is finished.
        if (mEpgFetcherDuringScan) {
            EpgFetcher.getInstance(this).onChannelScanFinished();
        }
        if (!setupComplete) {
            setResult(resultCode, data);
            finish();
            return;
        }
        SetupUtils.getInstance(this).onTvInputSetupFinished(mTvInputInfo.getId(), new Runnable() {
            @Override
            public void run() {
                if (mActivityAfterCompletion != null) {
                    try {
                        startActivity(mActivityAfterCompletion);
                    } catch (ActivityNotFoundException e) {
                        Log.w(TAG, "Activity launch failed", e);
                    }
                }
                setResult(resultCode, data);
                finish();
            }
        });
    }

    /**
     * Monitors the scan progress and notifies the timeout of the scanning.
     * The purpose of this monitor is to call EpgFetcher.onChannelScanFinished() in case when
     * SetupPassthroughActivity.onActivityResult() is not called properly. b/36008534
     */
    @MainThread
    private static class ScanTimeoutMonitor {
        // Set timeout long enough. The message in Sony TV says the scanning takes about 30 minutes.
        private static final long SCAN_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

        private final Context mContext;
        private final ChannelDataManager mChannelDataManager;
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final Runnable mScanTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "No channels has been added for a while." +
                        " The scan might have finished unexpectedly.");
                onScanTimedOut();
            }
        };
        private final Listener mChannelDataManagerListener = new Listener() {
            @Override
            public void onLoadFinished() {
                setupTimer();
            }

            @Override
            public void onChannelListUpdated() {
                setupTimer();
            }

            @Override
            public void onChannelBrowsableChanged() { }
        };
        private boolean mStarted;

        private ScanTimeoutMonitor(Context context) {
            mContext = context.getApplicationContext();
            mChannelDataManager = TvApplication.getSingletons(context).getChannelDataManager();
        }

        private void startMonitoring() {
            if (!mStarted) {
                mStarted = true;
                mChannelDataManager.addListener(mChannelDataManagerListener);
            }
            if (mChannelDataManager.isDbLoadFinished()) {
                setupTimer();
            }
        }

        private void stopMonitoring() {
            if (mStarted) {
                mStarted = false;
                mHandler.removeCallbacks(mScanTimeoutRunnable);
                mChannelDataManager.removeListener(mChannelDataManagerListener);
            }
        }

        private void setupTimer() {
            mHandler.removeCallbacks(mScanTimeoutRunnable);
            mHandler.postDelayed(mScanTimeoutRunnable, SCAN_TIMEOUT_MS);
        }

        private void onScanTimedOut() {
            stopMonitoring();
            EpgFetcher.getInstance(mContext).onChannelScanFinished();
        }
    }
}
