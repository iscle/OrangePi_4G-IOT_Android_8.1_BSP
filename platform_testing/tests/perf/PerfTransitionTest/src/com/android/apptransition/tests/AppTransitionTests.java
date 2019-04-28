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

package com.android.apptransition.tests;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.rule.logging.AtraceLogger;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AppTransitionTests {

    private static final String TAG = AppTransitionTests.class.getSimpleName();
    private static final int JOIN_TIMEOUT = 10000;
    private static final int DEFAULT_DROP_CACHE_DELAY = 2000;
    private static final String DEFAULT_POST_LAUNCH_TIMEOUT = "5000";
    private static final String DEFAULT_LAUNCH_COUNT = "10";
    private static final String SUCCESS_MESSAGE = "Status: ok";
    private static final String HOT_LAUNCH_MESSAGE = "Warning: Activity not started, its current"
            + " task has been brought to the front";
    private static final String DROP_CACHE_SCRIPT = "/data/local/tmp/dropCache.sh";
    private static final String APP_LAUNCH_CMD = "am start -W -n";
    private static final String FORCE_STOP = "am force-stop ";
    private static final String PRE_LAUNCH_APPS = "pre_launch_apps";
    private static final String LAUNCH_APPS = "launch_apps";
    private static final String KEY_LAUNCH_ITERATIONS = "launch_iteration";
    private static final String KEY_POST_LAUNCH_TIMEOUT = "postlaunch_timeout";
    private static final String COLD_LAUNCH = "cold_launch";
    private static final String HOT_LAUNCH = "hot_launch";
    private static final String NOT_SURE = "not_sure";
    private static final String ACTIVITY = "Activity";
    private static final String NOT_SUCCESSFUL_MESSAGE = "App launch not successful";
    private static final String KEY_TRACE_DIRECTORY = "trace_directory";
    private static final String KEY_TRACE_CATEGORY = "trace_categories";
    private static final String KEY_TRACE_BUFFERSIZE = "trace_bufferSize";
    private static final String KEY_TRACE_DUMPINTERVAL = "tracedump_interval";
    private static final String DEFAULT_TRACE_CATEGORIES = "sched,freq,gfx,view,dalvik,webview,"
            + "input,wm,disk,am,wm";
    private static final String DEFAULT_TRACE_BUFFER_SIZE = "20000";
    private static final String DEFAULT_TRACE_DUMP_INTERVAL = "10";
    private static final String DELIMITER = ",";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private Context mContext;
    private UiDevice mDevice;
    private PackageManager mPackageManager;
    private IActivityManager mActivityManager;
    private ILauncherStrategy mLauncherStrategy = null;
    private Map<String, Intent> mAppLaunchIntentsMapping = null;
    private String mTraceDirectoryStr = null;
    private Bundle mResult = new Bundle();
    private Bundle mArgs;
    private String mPreAppsList;
    private int mLaunchIterations;
    private int mPostLaunchTimeout;
    private String[] mAppListArray;
    private String[] mPreAppsListArray;
    private File mRootTrace = null;
    private File mRootTraceSubDir = null;
    private int mTraceBufferSize = 0;
    private int mTraceDumpInterval = 0;
    private Set<String> mTraceCategoriesSet = null;
    private AtraceLogger mAtraceLogger = null;
    private String mComponentName = null;
    private Map<String,String> mPreAppsComponentName = new HashMap<String, String>();

    @Before
    public void setUp() throws Exception {
        mPackageManager = getInstrumentation().getContext().getPackageManager();
        mContext = getInstrumentation().getContext();
        mArgs = InstrumentationRegistry.getArguments();
        mActivityManager = ActivityManager.getService();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mLauncherStrategy = LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
        createLaunchIntentMappings();
        String mAppsList = mArgs.getString(LAUNCH_APPS);
        mPreAppsList = mArgs.getString(PRE_LAUNCH_APPS);
        mLaunchIterations = Integer.parseInt(mArgs.getString(KEY_LAUNCH_ITERATIONS,
                DEFAULT_LAUNCH_COUNT));
        mPostLaunchTimeout = Integer.parseInt(mArgs.getString(KEY_POST_LAUNCH_TIMEOUT,
                DEFAULT_POST_LAUNCH_TIMEOUT));
        if (null == mAppsList && mAppsList.isEmpty()) {
            throw new IllegalArgumentException("Need atleast one app to do the"
                    + " app transition from launcher");
        }
        mAppsList = mAppsList.replaceAll("%"," ");
        mAppListArray = mAppsList.split(DELIMITER);

        // Parse the trace parameters
        mTraceDirectoryStr = mArgs.getString(KEY_TRACE_DIRECTORY);
        if (isTracesEnabled()) {
            String traceCategoriesStr = mArgs
                    .getString(KEY_TRACE_CATEGORY, DEFAULT_TRACE_CATEGORIES);
            mTraceBufferSize = Integer.parseInt(mArgs.getString(KEY_TRACE_BUFFERSIZE,
                    DEFAULT_TRACE_BUFFER_SIZE));
            mTraceDumpInterval = Integer.parseInt(mArgs.getString(KEY_TRACE_DUMPINTERVAL,
                    DEFAULT_TRACE_DUMP_INTERVAL));
            mTraceCategoriesSet = new HashSet<String>();
            if (!traceCategoriesStr.isEmpty()) {
                String[] traceCategoriesSplit = traceCategoriesStr.split(DELIMITER);
                for (int i = 0; i < traceCategoriesSplit.length; i++) {
                    mTraceCategoriesSet.add(traceCategoriesSplit[i]);
                }
            }
        }
        mDevice.setOrientationNatural();
        sleep(mPostLaunchTimeout);
        cleanTestApps();
    }

    @After
    public void tearDown() throws Exception{
        cleanTestApps();
        getInstrumentation().sendStatus(0, mResult);
    }

    /**
     * Cold launch given list of apps for given launch count from the launcher screen.
     * @throws IOException if there are issues in writing atrace file
     * @throws InterruptedException if there are interrupt during the sleep
     * @throws RemoteException if press home is not successful
     */
    @Test
    public void testColdLaunchFromLauncher() throws IOException, InterruptedException,
            RemoteException {
        if (isTracesEnabled()) {
            createTraceDirectory("testColdLaunchFromLauncher");
        }
        // Perform cold app launch from launcher screen
        for (int appCount = 0; appCount < mAppListArray.length; appCount++) {
            String appName = mAppListArray[appCount];
            // Additional launch to account for cold launch
            if (setupAppLaunch(appName) == ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP) {
                continue;
            }
            closeApps(new String[] {
                    appName
            });
            getInstrumentation().getUiAutomation()
                    .executeShellCommand(DROP_CACHE_SCRIPT);
            sleep(DEFAULT_DROP_CACHE_DELAY);
            for (int launchCount = 0; launchCount <= mLaunchIterations; launchCount++) {
                if (null != mAtraceLogger) {
                    mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                            mTraceDumpInterval, mRootTraceSubDir,
                            String.format("%s-%d", appName, launchCount));
                }
                mLauncherStrategy.launch(appName, mComponentName.split("\\/")[0]);
                if (null != mAtraceLogger) {
                    mAtraceLogger.atraceStop();
                }
                sleep(mPostLaunchTimeout);
                mDevice.pressHome();
		mDevice.waitForIdle();
                closeApps(new String[] {
                        appName
                });
                sleep(mPostLaunchTimeout);
                getInstrumentation().getUiAutomation()
                        .executeShellCommand(DROP_CACHE_SCRIPT);
                sleep(DEFAULT_DROP_CACHE_DELAY);
            }
            mComponentName = null;
            // Update the result with the component name
            updateResult(appName);
        }
    }

    /**
     * Hot launch given list of apps for given launch count from the launcher screen. Same method can be
     * used to test app to home transition delay information as well.
     * @throws IOException if there are issues in writing atrace file
     * @throws InterruptedException if there are interrupt during the sleep
     * @throws RemoteException if press home is not successful
     */
    @Test
    public void testHotLaunchFromLauncher() throws IOException, InterruptedException,
            RemoteException {
        if (isTracesEnabled()) {
            createTraceDirectory("testHotLaunchFromLauncher");
        }
        for (int appCount = 0; appCount < mAppListArray.length; appCount++) {
            String appName = mAppListArray[appCount];
            // Additional launch to account for cold launch
            if (setupAppLaunch(appName) == ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP) {
                continue;
            }
            // Hot app launch for given (launch iterations + 1) times.
            for (int launchCount = 0; launchCount <= (mLaunchIterations); launchCount++) {
                if (null != mAtraceLogger) {
                    mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                            mTraceDumpInterval, mRootTraceSubDir,
                            String.format("%s-%d", appName, (launchCount)));
                }
                mLauncherStrategy.launch(appName, mComponentName.split("\\/")[0]);
                if (null != mAtraceLogger) {
                    mAtraceLogger.atraceStop();
                }
                sleep(mPostLaunchTimeout);
                mDevice.pressHome();
                sleep(mPostLaunchTimeout);
            }
            mComponentName = null;
            // Update the result with the component name
            updateResult(appName);
        }
    }

    /**
     * Launch an app and press recents for given list of apps for given launch counts.
     * @throws IOException if there are issues in writing atrace file
     * @throws InterruptedException if there are interrupt during the sleep
     * @throws RemoteException if press recent apps is not successful
     */
    @Test
    public void testAppToRecents() throws IOException, InterruptedException, RemoteException {
        if (isTracesEnabled()) {
            createTraceDirectory("testAppToRecents");
        }
        if (null == mPreAppsList && mPreAppsList.isEmpty()) {
            throw new IllegalArgumentException("Need atleast few apps in the "
                    + "recents before starting the test");
        }
        mPreAppsList = mPreAppsList.replaceAll("%"," ");
        mPreAppsListArray = mPreAppsList.split(DELIMITER);
        mPreAppsComponentName.clear();
        populateRecentsList();
        for (int appCount = 0; appCount < mAppListArray.length; appCount++) {
            String appName = mAppListArray[appCount];
            long appLaunchTime = -1L;
            for (int launchCount = 0; launchCount <= mLaunchIterations; launchCount++) {
                mLauncherStrategy.launch(appName, mPreAppsComponentName.get(appName).split(
                        "\\/")[0]);
                sleep(mPostLaunchTimeout);
                if (null != mAtraceLogger && launchCount > 0) {
                    mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                            mTraceDumpInterval, mRootTraceSubDir,
                            String.format("%s-%d", appName, launchCount - 1));
                }
                pressUiRecentApps();
                sleep(mPostLaunchTimeout);
                if (null != mAtraceLogger && launchCount > 0) {
                    mAtraceLogger.atraceStop();
                }
                mDevice.pressHome();
                sleep(mPostLaunchTimeout);
            }
            updateResult(appName);
        }
    }

    /**
     * Hot launch an app from recents for given list of apps for given launch counts.
     * @throws IOException if there are issues in writing atrace file
     * @throws InterruptedException if there are interrupt during the sleep
     * @throws RemoteException if press recent apps is not successful
     */
    @Test
    public void testHotLaunchFromRecents() throws IOException, InterruptedException,
            RemoteException {
        if (isTracesEnabled()) {
            createTraceDirectory("testHotLaunchFromRecents");
        }
        if (null == mPreAppsList && mPreAppsList.isEmpty()) {
            throw new IllegalArgumentException("Need atleast few apps in the"
                    + " recents before starting the test");
        }
        mPreAppsList = mPreAppsList.replaceAll("%", " ");
        mPreAppsListArray = mPreAppsList.split(DELIMITER);
        mPreAppsComponentName.clear();
        populateRecentsList();
        for (int appCount = 0; appCount < mAppListArray.length; appCount++) {
            String appName = mAppListArray[appCount];
            // To bring the app to launch as first item from recents task.
            mLauncherStrategy.launch(appName, mPreAppsComponentName.get(appName).split(
                    "\\/")[0]);
            sleep(mPostLaunchTimeout);
            for (int launchCount = 0; launchCount <= mLaunchIterations; launchCount++) {
                if (null != mAtraceLogger) {
                    mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                            mTraceDumpInterval, mRootTraceSubDir,
                            String.format("%s-%d", appName, (launchCount)));
                }
                openMostRecentTask();
                sleep(mPostLaunchTimeout);
                if (null != mAtraceLogger) {
                    mAtraceLogger.atraceStop();
                }
                mDevice.pressHome();
                sleep(mPostLaunchTimeout);
            }
            updateResult(appName);
        }
    }

    /**
     * Launch given app to account for the cold launch and track
     * component name associated with the app.
     * @throws RemoteException if press home is not successful
     * @param appName
     * @return
     */
    public long setupAppLaunch(String appName) throws RemoteException {
        long appLaunchTime = startApp(appName, NOT_SURE);
        if (appLaunchTime == ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP) {
            return appLaunchTime;
        }
        sleep(mPostLaunchTimeout);
        mDevice.pressHome();
        sleep(mPostLaunchTimeout);
        return appLaunchTime;
    }

    /**
     * Press on the recents icon
     * @throws RemoteException if press recents is not successful
     */
    private void pressUiRecentApps() throws RemoteException {
        mDevice.findObject(By.res(SYSTEMUI_PACKAGE, "recent_apps")).click();
    }

    /**
     * To open the home screen.
     * @throws RemoteException if press home is not successful
     */
    private void pressUiHome() throws RemoteException {
        mDevice.findObject(By.res(SYSTEMUI_PACKAGE, "home")).click();
    }

    /**
     * Open recents task and click on the most recent task.
     * @throws RemoteException if press recents is not successful
     */
    public void openMostRecentTask() throws RemoteException {
        pressUiRecentApps();
        UiObject2 recentsView = mDevice.wait(Until.findObject(
                By.res(SYSTEMUI_PACKAGE, "recents_view")), 5000);
        List<UiObject2> recentsTasks = recentsView.getChildren().get(0)
                .getChildren();
        UiObject2 mostRecentTask = recentsTasks.get(recentsTasks.size() - 1);
        mostRecentTask.click();
    }

    /**
     * Create sub directory under the trace root directory to store the trace files captured during
     * the app transition.
     * @param subDirectoryName
     */
    private void createTraceDirectory(String subDirectoryName) throws IOException {
        mRootTrace = new File(mTraceDirectoryStr);
        if (!mRootTrace.exists() && !mRootTrace.mkdirs()) {
            throw new IOException("Unable to create the trace directory");
        }
        mRootTraceSubDir = new File(mRootTrace, subDirectoryName);
        if (!mRootTraceSubDir.exists() && !mRootTraceSubDir.mkdirs()) {
            throw new IOException("Unable to create the trace sub directory");
        }
        mAtraceLogger = AtraceLogger.getAtraceLoggerInstance(getInstrumentation());
    }

    /**
     * Force stop the given list of apps, clear the cache and return to home screen.
     * @throws RemoteException if press home is not successful
     */
    private void cleanTestApps() throws RemoteException {
        if (null != mPreAppsListArray && mPreAppsListArray.length > 0) {
            closeApps(mPreAppsListArray);
        }
        closeApps(mAppListArray);
        getInstrumentation().getUiAutomation()
                        .executeShellCommand(DROP_CACHE_SCRIPT);
        sleep(DEFAULT_DROP_CACHE_DELAY);
        mDevice.pressHome();
        sleep(mPostLaunchTimeout);
    }

    /**
     * Populate the recents list with given list of apps.
     * @throws RemoteException if press home is not successful
     */
    private void populateRecentsList() throws RemoteException {
        for (int preAppCount = 0; preAppCount < mPreAppsListArray.length; preAppCount++) {
            startApp(mPreAppsListArray[preAppCount], NOT_SURE);
            mPreAppsComponentName.put(mPreAppsListArray[preAppCount], mComponentName);
            sleep(mPostLaunchTimeout);
            mDevice.pressHome();
            sleep(mPostLaunchTimeout);
        }
        mComponentName = null;
    }


    /**
     * To obtain the app name and corresponding intent to launch the app.
     */
    private void createLaunchIntentMappings() {
        mAppLaunchIntentsMapping = new LinkedHashMap<String, Intent>();
        PackageManager pm = getInstrumentation().getContext()
                .getPackageManager();
        Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);
        resolveLoop(ris, intentToResolve, pm);
    }

    private void resolveLoop(List<ResolveInfo> ris, Intent intentToResolve, PackageManager pm) {
        if (ris == null || ris.isEmpty()) {
            Log.i(TAG, "Could not find any apps");
        } else {
            for (ResolveInfo ri : ris) {
                Intent startIntent = new Intent(intentToResolve);
                startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startIntent.setClassName(ri.activityInfo.packageName,
                        ri.activityInfo.name);
                String appName = ri.loadLabel(pm).toString();
                if (appName != null) {
                    mAppLaunchIntentsMapping.put(appName, startIntent);
                }
            }
        }
    }

    /**
     * Launch an app using the app name and return the app launch time. If app launch time is -1
     * then app launch is not successful.
     * @param appName Name of an app as listed in the launcher
     * @param launchMode Cold or Hot launch
     * @return
     */
    private long startApp(String appName, String launchMode) {
        Log.i(TAG, "Starting " + appName);
        Intent startIntent = mAppLaunchIntentsMapping.get(appName);
        if (startIntent == null) {
            return -1L;
        }
        AppLaunchRunnable runnable = new AppLaunchRunnable(startIntent, launchMode);
        Thread t = new Thread(runnable);
        t.start();
        try {
            t.join(JOIN_TIMEOUT);
        } catch (InterruptedException e) {
            // ignore
        }
        mComponentName = runnable.getCmpName();
        return runnable.getResult();
    }

    private class AppLaunchRunnable implements Runnable {
        private Intent mLaunchIntent;
        private String mLaunchMode;
        private Long mResult = -1L;
        private String mCmpName;

        public AppLaunchRunnable(Intent intent, String launchMode) {
            mLaunchIntent = intent;
            mLaunchMode = launchMode;
        }

        public Long getResult() {
            return mResult;
        }

        public String getCmpName() {
            return mCmpName;
        }

        @Override
        public void run() {
            String packageName = mLaunchIntent.getComponent().getPackageName();
            String componentName = mLaunchIntent.getComponent().flattenToString();
            String launchCmd = String.format("%s %s", APP_LAUNCH_CMD, componentName);
            ParcelFileDescriptor parcelDesc = getInstrumentation().getUiAutomation()
                    .executeShellCommand(launchCmd);
            mResult = Long.parseLong(parseLaunchTime(parcelDesc));
        }

        /**
         * Returns launch time if app launch is successful otherwise "-1"
         * @param parcelDesc
         * @return
         */
        private String parseLaunchTime(ParcelFileDescriptor parcelDesc) {
            String launchTime = "-1";
            boolean launchSuccess = false;
            mCmpName = null;
            try {
                InputStream inputStream = new FileInputStream(parcelDesc.getFileDescriptor());
                StringBuilder appLaunchOuput = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                        inputStream));
                String line = null;
                int lineCount = 1;
                while ((line = bufferedReader.readLine()) != null) {
                    if (lineCount == 2) {
                        if ((mLaunchMode.contains(COLD_LAUNCH) || mLaunchMode.contains(NOT_SURE))
                                && line.contains(SUCCESS_MESSAGE)) {
                            launchSuccess = true;
                        } else if ((mLaunchMode.contains(HOT_LAUNCH) || mLaunchMode
                                .contains(NOT_SURE)) && line.contains(HOT_LAUNCH_MESSAGE)) {
                            launchSuccess = true;
                        }
                    }
                    if ((launchSuccess && (mLaunchMode.contains(COLD_LAUNCH)
                            || mLaunchMode.contains(NOT_SURE)) && lineCount == 4) ||
                            (launchSuccess && (mLaunchMode.contains(HOT_LAUNCH) ||
                                    mLaunchMode.contains(NOT_SURE)) && lineCount == 5)) {
                        String launchSplit[] = line.split(":");
                        launchTime = launchSplit[1].trim();
                    }
                    // Needed to update the component name if the very first launch activity
                    // is different from hot launch activity (i.e YouTube)
                    if ((launchSuccess && (mLaunchMode.contains(HOT_LAUNCH) ||
                            mLaunchMode.contains(NOT_SURE)) && lineCount == 3)) {
                        String activitySplit[] = line.split(":");
                        if (activitySplit[0].contains(ACTIVITY)) {
                            mCmpName = activitySplit[1].trim();
                        }
                    }
                    lineCount++;
                }
                inputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Error writing the launch file", e);
            }
            return launchTime;
        }
    }

    /**
     * To force stop the given list of apps based on the app name.
     * @param appNames
     */
    private void closeApps(String[] appNames) {
        for (int i = 0; i < appNames.length; i++) {
            Intent startIntent = mAppLaunchIntentsMapping.get(appNames[i]);
            if (startIntent != null) {
                String packageName = startIntent.getComponent().getPackageName();

                getInstrumentation().getUiAutomation().executeShellCommand(
                        FORCE_STOP + packageName);
            }
            sleep(1000);
        }
        sleep(mPostLaunchTimeout);
    }

    /**
     * @return
     */
    private boolean isTracesEnabled(){
        return (null != mTraceDirectoryStr && !mTraceDirectoryStr.isEmpty());
    }

    /**
     * Update the result status
     * @param appName
     */
    private void updateResult(String appName) {
            // Component name needed for parsing the events log
            if (null != mComponentName) {
                mResult.putString(appName, mComponentName);
            } else {
                // Component name needed for parsing the events log
                mResult.putString(appName, mAppLaunchIntentsMapping.get(appName).
                        getComponent().flattenToString());
            }
    }


    /**
     * To sleep for given millisecs.
     * @param time
     */
    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Return the instrumentation from the registry.
     * @return
     */
    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}
