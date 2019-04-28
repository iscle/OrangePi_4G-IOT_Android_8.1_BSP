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

package android.test.appsmoke;

import android.app.ActivityManager;
import android.app.IActivityController;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(Parameterized.class)
public class AppSmokeTest {

    private static final String TAG = AppSmokeTest.class.getSimpleName();
    private static final String EXCLUDE_LIST = "exclude_apps";
    private static final String DEBUG_LIST = "debug_apps";
    private static final long WAIT_FOR_ANR = 6000;

    @Parameter
    public LaunchParameter mAppInfo;

    private boolean mAppHasError = false;
    private boolean mLaunchIntentDetected = false;
    private boolean mHasLeanback = false;
    private ILauncherStrategy mLauncherStrategy = null;
    private static UiDevice sDevice = null;

    /**
     * Convenient internal class to hold some launch specific data
     */
    private static class LaunchParameter implements Comparable<LaunchParameter>{
        public String appName;
        public String packageName;
        public String activityName;

        private LaunchParameter(String appName, String packageName, String activityName) {
            this.appName = appName;
            this.packageName = packageName;
            this.activityName = activityName;
        }

        @Override
        public int compareTo(LaunchParameter another) {
            return appName.compareTo(another.appName);
        }

        @Override
        public String toString() {
            return appName;
        }

        public String toLongString() {
            return String.format("%s [activity: %s/%s]", appName, packageName, activityName);
        }
    }

    /**
     * an activity controller to detect app launch crashes/ANR etc
     */
    private IActivityController mActivityController = new IActivityController.Stub() {

        @Override
        public int systemNotResponding(String msg) throws RemoteException {
            // let system die
            return -1;
        }

        @Override
        public int appNotResponding(String processName, int pid, String processStats)
                throws RemoteException {
            if (processName.startsWith(mAppInfo.packageName)) {
                mAppHasError = true;
            }
            // kill app
            return -1;
        }

        @Override
        public int appEarlyNotResponding(String processName, int pid, String annotation)
                throws RemoteException {
            // do nothing
            return 0;
        }

        @Override
        public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg,
                long timeMillis, String stackTrace) throws RemoteException {
            if (processName.startsWith(mAppInfo.packageName)) {
                mAppHasError = true;
            }
            return false;
        }

        @Override
        public boolean activityStarting(Intent intent, String pkg) throws RemoteException {
            Log.d(TAG, String.format("activityStarting: pkg=%s intent=%s",
                    pkg, intent.toInsecureString()));
            // always allow starting
            if (pkg.equals(mAppInfo.packageName)) {
                mLaunchIntentDetected = true;
            }
            return true;
        }

        @Override
        public boolean activityResuming(String pkg) throws RemoteException {
            Log.d(TAG, String.format("activityResuming: pkg=%s", pkg));
            // always allow resuming
            return true;
        }
    };

    /**
     * Generate the list of apps to test for launches by querying package manager
     * @return
     */
    @Parameters(name = "{0}")
    public static Collection<LaunchParameter> generateAppsList() {
        Instrumentation instr = InstrumentationRegistry.getInstrumentation();
        Bundle args = InstrumentationRegistry.getArguments();
        Context ctx = instr.getTargetContext();
        List<LaunchParameter> ret = new ArrayList<>();
        Set<String> excludedApps = new HashSet<>();
        Set<String> debugApps = new HashSet<>();

        // parse list of app names that should be execluded from launch tests
        if (args.containsKey(EXCLUDE_LIST)) {
            excludedApps.addAll(Arrays.asList(args.getString(EXCLUDE_LIST).split(",")));
        }
        // parse list of app names used for debugging (i.e. essentially a whitelist)
        if (args.containsKey(DEBUG_LIST)) {
            debugApps.addAll(Arrays.asList(args.getString(DEBUG_LIST).split(",")));
        }
        LauncherApps la = (LauncherApps)ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager um = (UserManager)ctx.getSystemService(Context.USER_SERVICE);
        List<LauncherActivityInfo> activities = new ArrayList<>();
        for (UserHandle handle : um.getUserProfiles()) {
            activities.addAll(la.getActivityList(null, handle));
        }
        for (LauncherActivityInfo info : activities) {
            String label = info.getLabel().toString();
            if (!debugApps.isEmpty()) {
                if (!debugApps.contains(label)) {
                    // if debug apps non-empty, we are essentially in whitelist mode
                    // bypass any apps not on list
                    continue;
                }
            } else if (excludedApps.contains(label)) {
                // if not debugging apps, bypass any excluded apps
                continue;
            }
            ret.add(new LaunchParameter(label, info
                    .getApplicationInfo().packageName, info.getName()));
        }
        Collections.sort(ret);
        return ret;
    }

    @Before
    public void before() throws RemoteException {
        ActivityManager.getService().setActivityController(mActivityController, false);
        LauncherStrategyFactory factory = LauncherStrategyFactory.getInstance(sDevice);
        mLauncherStrategy = factory.getLauncherStrategy();
        // Inject an instance of instrumentation only if leanback. This enables to launch any app
        // in the Apps and Games row on leanback launcher.
        Instrumentation instr = InstrumentationRegistry.getInstrumentation();
        mHasLeanback = hasLeanback(instr.getTargetContext());
        if (mHasLeanback) {
            factory.getLeanbackLauncherStrategy().setInstrumentation(instr);
        }
        mAppHasError = false;
        mLaunchIntentDetected = false;
    }

    @BeforeClass
    public static void beforeClass() throws RemoteException {
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        sDevice.setOrientationNatural();
    }

    @After
    public void after() throws RemoteException {
        sDevice.pressHome();
        ActivityManager.getService().forceStopPackage(
                mAppInfo.packageName, UserHandle.USER_ALL);
        ActivityManager.getService().setActivityController(null, false);
    }

    @AfterClass
    public static void afterClass() throws RemoteException {
        sDevice.unfreezeRotation();
    }

    @Test
    public void testAppLaunch() {
        Log.d(TAG, "Launching: " + mAppInfo.toLongString());
        long timestamp = mLauncherStrategy.launch(mAppInfo.appName, mAppInfo.packageName);
        boolean launchResult = (timestamp != ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP);
        if (launchResult) {
            // poke app to check if it's responsive
            pokeApp();
            SystemClock.sleep(WAIT_FOR_ANR);
        }
        if (mAppHasError) {
            Assert.fail("app crash or ANR detected");
        }
        if (!launchResult && !mLaunchIntentDetected) {
            Assert.fail("no app crash or ANR detected, but failed to launch via UI");
        }
        // if launchResult is false but mLaunchIntentDetected is true, we consider it as success
        // this happens when an app is a trampoline activity to something else
    }

    private void pokeApp() {
        // The swipe action on leanback launcher that doesn't support swipe gesture may
        // cause unnecessary focus change and test to fail.
        // Use the dpad key to poke the app instead.
        if (!mHasLeanback) {
            int w = sDevice.getDisplayWidth();
            int h = sDevice.getDisplayHeight();
            int dY = h / 4;
            boolean ret = sDevice.swipe(w / 2, h / 2 + dY, w / 2, h / 2 - dY, 40);
            if (!ret) {
                Log.w(TAG, "Failed while attempting to poke front end window with swipe");
            }
        } else {
            sDevice.pressDPadUp();
        }
    }

    private boolean hasLeanback(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
