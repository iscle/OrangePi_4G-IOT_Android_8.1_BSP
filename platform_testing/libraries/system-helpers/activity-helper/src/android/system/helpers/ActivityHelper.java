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

package android.system.helpers;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.KeyEvent;

import junit.framework.Assert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implement common helper methods for activities.
 */
public class ActivityHelper {
    private static final String TAG = ActivityHelper.class.getSimpleName();

    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    public static final int FULLSCREEN = 1;
    public static final int SPLITSCREEN = 3;
    public static final int TIMEOUT = 1000;
    public static final int INVALID_TASK_ID = -1;

    private static ActivityHelper sInstance = null;
    private Context mContext = null;
    private UiDevice mDevice = null;

    public ActivityHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public static ActivityHelper getInstance() {
        if (sInstance == null) {
            sInstance = new ActivityHelper();
        }
        return sInstance;
    }

    /**
     * Gets task id for an activity
     *
     * @param pkgName
     * @param activityName
     * @return taskId or -1 when no activity is found
     */
    public int getTaskIdForActivity(String pkgName, String activityName) {
        int taskId = INVALID_TASK_ID;
        // Find task id for given package and activity
        final Pattern TASK_REGEX = Pattern.compile(
                String.format("taskId=(\\d+): %s/%s", pkgName, activityName));
        Matcher matcher = TASK_REGEX.matcher(CommandsHelper.execute("am stack list"));
        if (matcher.find()) {
            taskId = Integer.parseInt(matcher.group(1));
            Log.i(TAG, String.format("TaskId found: %d for %s/%s",
                    taskId, pkgName, activityName));
        }
        Assert.assertTrue("Taskid hasn't been found", taskId != -1);
        return taskId;
    }

    /**
     * Helper to change window mode between fullscreen and splitscreen for a given task
     *
     * @param taskId
     * @param mode
     * @throws InterruptedException
     */
    public void changeWindowMode(int taskId, int mode) throws InterruptedException {
        CommandsHelper.execute(
                String.format("am stack move-task %d %d true", taskId, mode));
        Thread.sleep(TIMEOUT);
    }

    /**
     * Clears apps in overview/recents
     *
     * @throws InterruptedException
     * @throws RemoteException
     */
    public void clearRecents() throws InterruptedException, RemoteException {
        // Launch recents if it's not already
        int retry = 5;
        while (!mDevice.wait(Until.hasObject(By.res(SYSTEMUI_PACKAGE, "recents_view")),
                TIMEOUT * 5) && --retry > 0) {
            mDevice.pressRecentApps();
            Thread.sleep(TIMEOUT);
        }
        // Return if there is no apps in recents
        if (mDevice.wait(Until.hasObject(By.text("No recent items")), TIMEOUT * 5)) {
            return;
        } else {
            Assert.assertTrue("Device expects recent items", mDevice.wait(Until.hasObject(
                    By.res(SYSTEMUI_PACKAGE, "recents_view")), TIMEOUT * 5));
        }
        // Get recents items
        int recents = mDevice.wait(Until.findObjects(
                By.res(SYSTEMUI_PACKAGE, "task_view_thumbnail")), TIMEOUT * 5).size();
        // Clear recents
        for (int i = 0; i < recents; ++i) {
            mDevice.pressKeyCode(KeyEvent.KEYCODE_APP_SWITCH);
            Thread.sleep(TIMEOUT);
            mDevice.pressKeyCode(KeyEvent.KEYCODE_DEL);
            Thread.sleep(TIMEOUT);
        }
    }

    /**
     * Clear recent apps by click 'CLEAR ALL' button in the recents view.
     *
     * @throws Exception
     */
    public void clearRecentsByClearAll() throws Exception {
        int retry = 5;
        while (!mDevice.wait(Until.hasObject(By.res(SYSTEMUI_PACKAGE, "recents_view")),
                TIMEOUT * 5) && --retry > 0) {
            mDevice.pressRecentApps();
            Thread.sleep(TIMEOUT);
        }
        int maxTries = 20;
        while (!mDevice.hasObject(By.text("No recent items")) && maxTries-- >= 0) {
            UiScrollable thumbnailScrollable = new UiScrollable(
                    new UiSelector().className("android.widget.ScrollView"));
            thumbnailScrollable.scrollToBeginning(100);
            if (!mDevice.wait(Until.hasObject(By.text("CLEAR ALL")), TIMEOUT * 2)) {
                continue;
            } else {
                int tries = 3;
                while (mDevice.hasObject(By.text("CLEAR ALL")) && tries-- > 0) {
                    mDevice.findObject(By.text("CLEAR ALL")).click();
                    Thread.sleep(TIMEOUT * 2);
                }
                break;
            }
        }
    }

    /**
     * Enable/disable bmgr service
     *
     * @param enable true to enable, false to disable
     */
    public void enableBmgr(boolean enable) {
        String output = CommandsHelper.execute("bmgr enable " + Boolean.toString(enable));
        if (enable) {
            Assert.assertTrue("Bmgr not enabled",
                    output.indexOf("Backup Manager now enabled") >= 0);
        } else {
            Assert.assertTrue("Bmgr not disabled",
                    output.indexOf("Backup Manager now disabled") >= 0);
        }
    }

    /**
     * Launch an intent when intent is of string type
     *
     * @param intentName
     * @throws InterruptedException
     */
    public void launchIntent(String intentName) throws InterruptedException {
        mDevice.pressHome();
        Intent intent = new Intent(intentName);
        launchIntent(intent);
    }

    /**
     * Find intent of a package and launch
     *
     * @param pkgName
     * @throws InterruptedException
     */
    public void launchPackage(String pkgName) throws InterruptedException {
        Intent pkgIntent = mContext.getPackageManager()
                .getLaunchIntentForPackage(pkgName);
        launchIntent(pkgIntent);
    }

    /**
     * launch an intent when intent is of Intent type
     *
     * @param intent
     * @throws InterruptedException
     */
    public void launchIntent(Intent intent) throws InterruptedException {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        Thread.sleep(TIMEOUT * 5);
    }
}
