/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.test.aupt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiWatcher;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;
import android.util.Log;

import junit.framework.Assert;

/**
 * Base class for AuptTests.
 */
public class AuptTestCase extends InstrumentationTestCase {
    /* Constants */
    static final int STEPS_BACK = 10;
    static final int RECOVERY_SLEEP = 2000;
    static final long DEFAULT_SHORT_SLEEP = 5 * 1000;
    static final long DEFAULT_LONG_SLEEP = 30 * 1000;
    static final String TAG = AuptTestCase.class.getSimpleName();

    /* State */
    private UiWatchers mWatchers;
    private UiDevice mDevice;

    /* *******************  InstrumentationTestCase Hooks ******************* */

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = UiDevice.getInstance(getInstrumentation());
        mWatchers = new UiWatchers();
        mWatchers.registerAnrAndCrashWatchers(getInstrumentation());

        mDevice.registerWatcher("LockScreenWatcher", new LockScreenWatcher());
        mDevice.setOrientationNatural();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        mDevice.removeWatcher("LockScreenWatcher");
        mDevice.unfreezeRotation();

        super.tearDown();
    }

    /* *******************  Device Methods ******************* */

    /**
     * @deprecated you should be using an AppHelper library to do this.
     */
    @Deprecated
    protected UiDevice getUiDevice() {
        return mDevice;
    }

    /**
     * @deprecated you should be using an AppHelper library to do this.
     */
    @Deprecated
    public void launchIntent(Intent intent) {
        getInstrumentation().getContext().startActivity(intent);
    }

    /**
     * Press back button repeatedly in order to attempt to bring the app back to home screen.
     * This is intended so that an app can recover if the previous session left an app in a weird
     * state.
     *
     * @deprecated you should be using an AppHelper library to do this.
     */
    @Deprecated
    public void navigateToHome() {
        int iterations = 0;
        String launcherPkg = mDevice.getLauncherPackageName();
        while (!launcherPkg.equals(mDevice.getCurrentPackageName())
                && iterations < STEPS_BACK) {
            mDevice.pressBack();
            SystemClock.sleep(RECOVERY_SLEEP);
            iterations++;
        }
    }

    /* *******************  Parameter Accessors ******************* */

    protected Bundle getParams() {
        return ((InstrumentationTestRunner)getInstrumentation()).getArguments();
    }

    /**
     * Looks up a parameter or returns a default value if parameter is not
     * present.
     * @param key
     * @param defaultValue
     * @return passed in parameter or default value if parameter is not found.
     */
    public long getLongParam(String key, long defaultValue) throws NumberFormatException {
        if (getParams().containsKey(key)) {
            return Long.parseLong(getParams().getString(key));
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns the timeout for short sleep. Can be set with shortSleep command
     * line option. Default is 5 seconds.
     * @return time in milliseconds
     */
    public long getShortSleep() {
        return getLongParam("shortSleep", DEFAULT_SHORT_SLEEP);
    }

    /**
     * Returns the timeout for long sleep. Can be set with longSleep command
     * line option. Default is 30 seconds
     * @return time in milliseconds.
     */
    public long getLongSleep() {
        return getLongParam("longSleep", DEFAULT_LONG_SLEEP);
    }

    /**
     * @return the jar-file arguments of this AUPT test run
     */
    public List<String> getDexedJarPaths() {
        return DexTestRunner.parseDexedJarPaths(getParams().getString("jars", ""));
    }

    /**
     * @return the version corresponding to a given package name
     *
     * @deprecated you should be using an AppHelper library to do this.
     */
    @Deprecated
    public String getPackageVersion(String packageName) throws NameNotFoundException {
        if (null == packageName || packageName.isEmpty()) {
              throw new RuntimeException("Package name can't be null or empty");
        }
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        PackageInfo pInfo = pm.getPackageInfo(packageName, 0);
        String version = pInfo.versionName;
        if (null == version || version.isEmpty()) {
              throw new RuntimeException(
                      String.format("Version isn't found for package = %s", packageName));
        }

        return version;
    }

    /**
     * Get registered accounts
     * Ensures there is at least one account registered
     * returns the google account name
     *
     * @deprecated you should be using an AppHelper library to do this.
     */
    @Deprecated
    public String getRegisteredEmailAccount() {
        Account[] accounts = AccountManager.get(getInstrumentation().getContext()).getAccounts();
        Assert.assertTrue("Device doesn't have any account registered", accounts.length >= 1);
        for(int i =0; i < accounts.length; ++i) {
            if(accounts[i].type.equals("com.google")) {
                return accounts[i].name;
            }
        }

        throw new RuntimeException("The device is not registered with a google account");
    }

    /* *******************  Logging ******************* */

    protected void dumpMemInfo(String notes) {
        FilesystemUtil.dumpMeminfo(getInstrumentation(), notes);
    }

    /* *******************  Utilities ******************* */

    private class LockScreenWatcher implements UiWatcher {
        @Override
        public boolean checkForCondition() {
            if (mDevice.hasObject(By.desc("Slide area."))) {
                mDevice.pressMenu();
                Log.v(TAG, "Lock screen dismissed.");
                return true;
            }
            return false;
        }
    }
}
