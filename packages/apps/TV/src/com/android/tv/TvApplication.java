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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.TvInputCallback;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.tv.analytics.Analytics;
import com.android.tv.analytics.StubAnalytics;
import com.android.tv.analytics.StubAnalytics;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.BuildConfig;
import com.android.tv.common.SharedPreferencesUtils;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper;
import com.android.tv.config.DefaultConfigManager;
import com.android.tv.config.RemoteConfig;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.PreviewDataManager;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.data.epg.EpgFetcher;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrDataManagerImpl;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.DvrStorageStatusManager;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.recorder.RecordingScheduler;
import com.android.tv.perf.EventNames;
import com.android.tv.perf.PerformanceMonitor;
import com.android.tv.perf.StubPerformanceMonitor;
import com.android.tv.perf.TimerEvent;
import com.android.tv.recommendation.ChannelPreviewUpdater;
import com.android.tv.recommendation.RecordedProgramPreviewUpdater;
import com.android.tv.tuner.TunerInputController;
import com.android.tv.tuner.TunerPreferences;
import com.android.tv.tuner.tvinput.TunerTvInputService;
import com.android.tv.tuner.util.TunerInputInfoUtils;
import com.android.tv.util.AccountHelper;
import com.android.tv.util.Clock;
import com.android.tv.util.Debug;
import com.android.tv.util.PermissionUtils;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.SystemProperties;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.List;

public class TvApplication extends Application implements ApplicationSingletons {
    private static final String TAG = "TvApplication";
    private static final boolean DEBUG = false;
    private static final TimerEvent sAppStartTimer = StubPerformanceMonitor.startBootstrapTimer();

    /**
     * An instance of {@link ApplicationSingletons}. Note that this can be set directly only for the
     * test purpose.
     */
    @VisibleForTesting
    public static ApplicationSingletons sAppSingletons;

    /**
     * Broadcast Action: The user has updated LC to a new version that supports tuner input.
     * {@link com.android.tv.tuner.TunerInputController} will recevice this intent to check
     * the existence of tuner input when the new version is first launched.
     */
    public static final String ACTION_APPLICATION_FIRST_LAUNCHED =
            "com.android.tv.action.APPLICATION_FIRST_LAUNCHED";
    private static final String PREFERENCE_IS_FIRST_LAUNCH = "is_first_launch";

    private RemoteConfig mRemoteConfig;
    private String mVersionName = "";

    private final MainActivityWrapper mMainActivityWrapper = new MainActivityWrapper();

    private SelectInputActivity mSelectInputActivity;
    private Analytics mAnalytics;
    private Tracker mTracker;
    private TvInputManagerHelper mTvInputManagerHelper;
    private ChannelDataManager mChannelDataManager;
    private volatile ProgramDataManager mProgramDataManager;
    private PreviewDataManager mPreviewDataManager;
    private DvrManager mDvrManager;
    private DvrScheduleManager mDvrScheduleManager;
    private DvrDataManager mDvrDataManager;
    private DvrStorageStatusManager mDvrStorageStatusManager;
    private DvrWatchedPositionManager mDvrWatchedPositionManager;
    private RecordingScheduler mRecordingScheduler;
    @Nullable
    private InputSessionManager mInputSessionManager;
    private AccountHelper mAccountHelper;
    // When this variable is null, we don't know in which process TvApplication runs.
    private Boolean mRunningInMainProcess;
    private PerformanceMonitor mPerformanceMonitor;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!PermissionUtils.hasInternet(this)) {
            // When an isolated process starts, just skip all the initialization.
            return;
        }
        Debug.getTimer(Debug.TAG_START_UP_TIMER).start();
        Debug.getTimer(Debug.TAG_START_UP_TIMER).log("Start TvApplication.onCreate");
        SharedPreferencesUtils.initialize(this, new Runnable() {
            @Override
            public void run() {
                if (mRunningInMainProcess != null && mRunningInMainProcess) {
                    checkTunerServiceOnFirstLaunch();
                }
            }
        });
        // TunerPreferences is used to enable/disable the tuner input even when TUNER feature is
        // disabled.
        TunerPreferences.initialize(this);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            mVersionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unable to find package '" + getPackageName() + "'.", e);
            mVersionName = "";
        }
        Log.i(TAG, "Starting Live TV " + getVersionName());

        // Only set StrictMode for ENG builds because the build server only produces userdebug
        // builds.
        if (BuildConfig.ENG && SystemProperties.ALLOW_STRICT_MODE.getValue()) {
            StrictMode.ThreadPolicy.Builder threadPolicyBuilder =
                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog();
            StrictMode.VmPolicy.Builder vmPolicyBuilder =
                    new StrictMode.VmPolicy.Builder().detectAll().penaltyDeath();
            if (!TvCommonUtils.isRunningInTest()) {
                threadPolicyBuilder.penaltyDialog();
            }
            StrictMode.setThreadPolicy(threadPolicyBuilder.build());
            StrictMode.setVmPolicy(vmPolicyBuilder.build());
        }
        if (BuildConfig.ENG && !SystemProperties.ALLOW_ANALYTICS_IN_ENG.getValue()) {
            mAnalytics = StubAnalytics.getInstance(this);
        } else {
            mAnalytics = StubAnalytics.getInstance(this);
        }
        mTracker = mAnalytics.getDefaultTracker();
        getTvInputManagerHelper();
        // In SetupFragment, transitions are set in the constructor. Because the fragment can be
        // created in Activity.onCreate() by the framework, SetupAnimationHelper should be
        // initialized here before Activity.onCreate() is called.
        SetupAnimationHelper.initialize(this);


        Log.i(TAG, "Started Live TV " + mVersionName);
        Debug.getTimer(Debug.TAG_START_UP_TIMER).log("finish TvApplication.onCreate");
        getPerformanceMonitor().stopTimer(sAppStartTimer, EventNames.APPLICATION_ONCREATE);
    }

    private void setCurrentRunningProcess(boolean isMainProcess) {
        if (mRunningInMainProcess != null) {
            SoftPreconditions.checkState(isMainProcess == mRunningInMainProcess);
            return;
        }
        Debug.getTimer(Debug.TAG_START_UP_TIMER).log(
                "start TvApplication.setCurrentRunningProcess");
        mRunningInMainProcess = isMainProcess;
        if (CommonFeatures.DVR.isEnabled(this)) {
            mDvrStorageStatusManager = new DvrStorageStatusManager(this, mRunningInMainProcess);
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // Fetch remote config
                getRemoteConfig().fetch(null);
                return null;
            }
        }.execute();
        if (mRunningInMainProcess) {
            getTvInputManagerHelper().addCallback(new TvInputCallback() {
                @Override
                public void onInputAdded(String inputId) {
                    if (Features.TUNER.isEnabled(TvApplication.this) && TextUtils.equals(inputId,
                            TunerTvInputService.getInputId(TvApplication.this))) {
                        TunerInputInfoUtils.updateTunerInputInfo(TvApplication.this);
                    }
                    handleInputCountChanged();
                }

                @Override
                public void onInputRemoved(String inputId) {
                    handleInputCountChanged();
                }
            });
            if (Features.TUNER.isEnabled(this)) {
                // If the tuner input service is added before the app is started, we need to
                // handle it here.
                TunerInputInfoUtils.updateTunerInputInfo(TvApplication.this);
            }
            if (CommonFeatures.DVR.isEnabled(this)) {
                mDvrScheduleManager = new DvrScheduleManager(this);
                mDvrManager = new DvrManager(this);
                mRecordingScheduler = RecordingScheduler.createScheduler(this);
            }
            EpgFetcher.getInstance(this).startRoutineService();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ChannelPreviewUpdater.getInstance(this).startRoutineService();
                RecordedProgramPreviewUpdater.getInstance(this)
                        .updatePreviewDataForRecordedPrograms();
            }
        }
        Debug.getTimer(Debug.TAG_START_UP_TIMER).log(
                "finish TvApplication.setCurrentRunningProcess");
    }

    private void checkTunerServiceOnFirstLaunch() {
        SharedPreferences sharedPreferences = this.getSharedPreferences(
                SharedPreferencesUtils.SHARED_PREF_FEATURES, Context.MODE_PRIVATE);
        boolean isFirstLaunch = sharedPreferences.getBoolean(PREFERENCE_IS_FIRST_LAUNCH, true);
        if (isFirstLaunch) {
            if (DEBUG) Log.d(TAG, "Congratulations, it's the first launch!");
            TunerInputController.onCheckingUsbTunerStatus(this, ACTION_APPLICATION_FIRST_LAUNCHED);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREFERENCE_IS_FIRST_LAUNCH, false);
            editor.apply();
        }
    }

    /**
     * Returns the {@link DvrManager}.
     */
    @Override
    public DvrManager getDvrManager() {
        return mDvrManager;
    }

    /**
     * Returns the {@link DvrScheduleManager}.
     */
    @Override
    public DvrScheduleManager getDvrScheduleManager() {
        return mDvrScheduleManager;
    }

    /**
     * Returns the {@link RecordingScheduler}.
     */
    @Override
    @Nullable
    public RecordingScheduler getRecordingScheduler() {
        return mRecordingScheduler;
    }

    /**
     * Returns the {@link DvrWatchedPositionManager}.
     */
    @Override
    public DvrWatchedPositionManager getDvrWatchedPositionManager() {
        if (mDvrWatchedPositionManager == null) {
            mDvrWatchedPositionManager = new DvrWatchedPositionManager(this);
        }
        return mDvrWatchedPositionManager;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.N)
    public InputSessionManager getInputSessionManager() {
        if (mInputSessionManager == null) {
            mInputSessionManager = new InputSessionManager(this);
        }
        return mInputSessionManager;
    }

    /**
     * Returns the {@link Analytics}.
     */
    @Override
    public Analytics getAnalytics() {
        return mAnalytics;
    }

    /**
     * Returns the default tracker.
     */
    @Override
    public Tracker getTracker() {
        return mTracker;
    }

    /**
     * Returns {@link ChannelDataManager}.
     */
    @Override
    public ChannelDataManager getChannelDataManager() {
        if (mChannelDataManager == null) {
            mChannelDataManager = new ChannelDataManager(this, getTvInputManagerHelper());
            mChannelDataManager.start();
        }
        return mChannelDataManager;
    }

    @Override
    public boolean isChannelDataManagerLoadFinished() {
        return mChannelDataManager != null && mChannelDataManager.isDbLoadFinished();
    }

    /**
     * Returns {@link ProgramDataManager}.
     */
    @Override
    public ProgramDataManager getProgramDataManager() {
        if (mProgramDataManager != null) {
            return mProgramDataManager;
        }
        Utils.runInMainThreadAndWait(new Runnable() {
            @Override
            public void run() {
                if (mProgramDataManager == null) {
                    mProgramDataManager = new ProgramDataManager(TvApplication.this);
                    mProgramDataManager.start();
                }
            }
        });
        return mProgramDataManager;
    }

    @Override
    public boolean isProgramDataManagerCurrentProgramsLoadFinished() {
        return mProgramDataManager != null && mProgramDataManager.isCurrentProgramsLoadFinished();
    }

    /**
     * Returns {@link PreviewDataManager}.
     */
    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public PreviewDataManager getPreviewDataManager() {
        if (mPreviewDataManager == null) {
            mPreviewDataManager = new PreviewDataManager(this);
            mPreviewDataManager.start();
        }
        return mPreviewDataManager;
    }

    /**
     * Returns {@link DvrDataManager}.
     */
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public DvrDataManager getDvrDataManager() {
        if (mDvrDataManager == null) {
            DvrDataManagerImpl dvrDataManager = new DvrDataManagerImpl(this, Clock.SYSTEM);
            mDvrDataManager = dvrDataManager;
            dvrDataManager.start();
        }
        return mDvrDataManager;
    }

    /**
     * Returns {@link DvrStorageStatusManager}.
     */
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public DvrStorageStatusManager getDvrStorageStatusManager() {
        return mDvrStorageStatusManager;
    }

    /**
     * Returns {@link TvInputManagerHelper}.
     */
    @Override
    public TvInputManagerHelper getTvInputManagerHelper() {
        if (mTvInputManagerHelper == null) {
            mTvInputManagerHelper = new TvInputManagerHelper(this);
            mTvInputManagerHelper.start();
        }
        return mTvInputManagerHelper;
    }

    /**
     * Returns the main activity information.
     */
    @Override
    public MainActivityWrapper getMainActivityWrapper() {
        return mMainActivityWrapper;
    }

    /**
     * Returns the {@link AccountHelper}.
     */
    @Override
    public AccountHelper getAccountHelper() {
        if (mAccountHelper == null) {
            mAccountHelper = new AccountHelper(getApplicationContext());
        }
        return mAccountHelper;
    }

    @Override
    public RemoteConfig getRemoteConfig() {
        if (mRemoteConfig == null) {
            // No need to synchronize this, it does not hurt to create two and throw one away.
            mRemoteConfig = DefaultConfigManager.createInstance(this).getRemoteConfig();
        }
        return mRemoteConfig;
    }

    @Override
    public boolean isRunningInMainProcess() {
        return mRunningInMainProcess != null && mRunningInMainProcess;
    }

    @Override
    public PerformanceMonitor getPerformanceMonitor() {
        if (mPerformanceMonitor == null) {
            mPerformanceMonitor = StubPerformanceMonitor.initialize(this);
        }
        return mPerformanceMonitor;
    }

    /**
     * SelectInputActivity is set in {@link SelectInputActivity#onCreate} and cleared in
     * {@link SelectInputActivity#onDestroy}.
     */
    public void setSelectInputActivity(SelectInputActivity activity) {
        mSelectInputActivity = activity;
    }

    public void handleGuideKey() {
        if (!mMainActivityWrapper.isResumed()) {
            startActivity(new Intent(Intent.ACTION_VIEW, TvContract.Programs.CONTENT_URI));
        } else {
            mMainActivityWrapper.getMainActivity().getOverlayManager().toggleProgramGuide();
        }
    }

    /**
     * Handles the global key KEYCODE_TV.
     */
    public void handleTvKey() {
        if (!mMainActivityWrapper.isResumed()) {
            startMainActivity(null);
        }
    }

    /**
     * Handles the global key KEYCODE_TV_INPUT.
     */
    public void handleTvInputKey() {
        TvInputManager tvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> tvInputs = tvInputManager.getTvInputList();
        int inputCount = 0;
        boolean hasTunerInput = false;
        for (TvInputInfo input : tvInputs) {
            if (input.isPassthroughInput()) {
                if (!input.isHidden(this)) {
                    ++inputCount;
                }
            } else if (!hasTunerInput) {
                hasTunerInput = true;
                ++inputCount;
            }
        }
        if (inputCount < 2) {
            return;
        }
        Activity activityToHandle = mMainActivityWrapper.isResumed()
                ? mMainActivityWrapper.getMainActivity() : mSelectInputActivity;
        if (activityToHandle != null) {
            // If startActivity is called, MainActivity.onPause is unnecessarily called. To
            // prevent it, MainActivity.dispatchKeyEvent is directly called.
            activityToHandle.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TV_INPUT));
            activityToHandle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_TV_INPUT));
        } else if (mMainActivityWrapper.isStarted()) {
            Bundle extras = new Bundle();
            extras.putString(Utils.EXTRA_KEY_ACTION, Utils.EXTRA_ACTION_SHOW_TV_INPUT);
            startMainActivity(extras);
        } else {
            startActivity(new Intent(this, SelectInputActivity.class).setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void startMainActivity(Bundle extras) {
        // The use of FLAG_ACTIVITY_NEW_TASK enables arbitrary applications to access the intent
        // sent to the root activity. Having said that, we should be fine here since such an intent
        // does not carry any important user data.
        Intent intent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
    }

    /**
     * Returns the version name of the live channels.
     *
     * @see PackageInfo#versionName
     */
    public String getVersionName() {
        return mVersionName;
    }

    /**
     * Checks the input counts and enable/disable TvActivity. Also updates the input list in
     * {@link SetupUtils}.
     */
    public void handleInputCountChanged() {
        handleInputCountChanged(false, false, false);
    }

    /**
     * Checks the input counts and enable/disable TvActivity. Also updates the input list in
     * {@link SetupUtils}.
     *
     * @param calledByTunerServiceChanged true if it is called when TunerTvInputService
     *        is enabled or disabled.
     * @param tunerServiceEnabled it's available only when calledByTunerServiceChanged is true.
     * @param dontKillApp when TvActivity is enabled or disabled by this method, the app restarts
     *        by default. But, if dontKillApp is true, the app won't restart.
     */
    public void handleInputCountChanged(boolean calledByTunerServiceChanged,
            boolean tunerServiceEnabled, boolean dontKillApp) {
        TvInputManager inputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        boolean enable = (calledByTunerServiceChanged && tunerServiceEnabled)
                || Features.UNHIDE.isEnabled(TvApplication.this);
        if (!enable) {
            List<TvInputInfo> inputs = inputManager.getTvInputList();
            boolean skipTunerInputCheck = false;
            // Enable the TvActivity only if there is at least one tuner type input.
            if (!skipTunerInputCheck) {
                for (TvInputInfo input : inputs) {
                    if (calledByTunerServiceChanged && !tunerServiceEnabled
                            && TunerTvInputService.getInputId(this).equals(input.getId())) {
                        continue;
                    }
                    if (input.getType() == TvInputInfo.TYPE_TUNER) {
                        enable = true;
                        break;
                    }
                }
            }
            if (DEBUG) Log.d(TAG, "Enable MainActivity: " + enable);
        }
        PackageManager packageManager = getPackageManager();
        ComponentName name = new ComponentName(this, TvActivity.class);
        int newState = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (packageManager.getComponentEnabledSetting(name) != newState) {
            packageManager.setComponentEnabledSetting(name, newState,
                    dontKillApp ? PackageManager.DONT_KILL_APP : 0);
            Log.i(TAG, (enable ? "Un-hide" : "Hide") + " Live TV.");
        }
        SetupUtils.getInstance(TvApplication.this).onInputListUpdated(inputManager);
    }

    /**
     * Returns the @{@link ApplicationSingletons} using the application context.
     */
    public static ApplicationSingletons getSingletons(Context context) {
        // No need to be "synchronized" because this doesn't create any instance.
        if (sAppSingletons == null) {
            sAppSingletons = (ApplicationSingletons) context.getApplicationContext();
        }
        return sAppSingletons;
    }

    /**
     * Sets true, if TvApplication is running on the main process. If TvApplication runs on
     * tuner process or other process, it sets false.
     *
     * Note: it should be called at the beginning of Service.onCreate Activity.onCreate, or
     * BroadcastReceiver.onCreate. When it is firstly called after launch, it runs process
     * specific initializations.
     */
    public static void setCurrentRunningProcess(Context context, boolean isMainProcess) {
        // TODO(b/63064354) TvApplication should not have to know if it is "the main process"
        if (context.getApplicationContext() instanceof TvApplication) {
            TvApplication tvApplication = (TvApplication) context.getApplicationContext();
            tvApplication.setCurrentRunningProcess(isMainProcess);
        } else {
            // Application context can be MockTvApplication.
            Log.w(TAG, "It is not a context of TvApplication");
        }
    }
}
