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
package com.android.cts.deviceowner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class that is meant to be driven from the host and can't be run alone, which is required
 * for tests that include rebooting or other connection-breaking steps. For this reason, this class
 * does not override tearDown and setUp just initializes the test state, changing nothing in the
 * device. Therefore, the host is responsible for making sure the tests leave the device in a clean
 * state after running.
 */
@RunWith(AndroidJUnit4.class)
public class LockTaskHostDrivenTest {

    private static final String TAG = LockTaskHostDrivenTest.class.getName();

    private static final String PACKAGE_NAME = LockTaskHostDrivenTest.class.getPackage().getName();
    private static final ComponentName ADMIN_COMPONENT =
            new ComponentName(PACKAGE_NAME, BaseDeviceOwnerTest.BasicAdminReceiver.class.getName());

    private static final String LOCK_TASK_ACTIVITY
            = LockTaskUtilityActivityIfWhitelisted.class.getName();

    private UiDevice mUiDevice;
    private Context mContext;
    private ActivityManager mActivityManager;
    private DevicePolicyManager mDevicePolicyManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mDevicePolicyManager =  mContext.getSystemService(DevicePolicyManager.class);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void startLockTask() throws Exception {
        Log.d(TAG, "startLockTask on host-driven test (no cleanup)");
        setDefaultHomeIntentReceiver();
        launchLockTaskActivity();
        mUiDevice.waitForIdle();
    }

    @Test
    public void testLockTaskIsActiveAndCantBeInterrupted() throws Exception {
        mUiDevice.waitForIdle();

        checkLockedActivityIsRunning();

        mUiDevice.pressBack();
        mUiDevice.waitForIdle();
        checkLockedActivityIsRunning();

        mUiDevice.pressHome();
        mUiDevice.waitForIdle();
        checkLockedActivityIsRunning();

        mUiDevice.pressRecentApps();
        mUiDevice.waitForIdle();
        checkLockedActivityIsRunning();

        mUiDevice.waitForIdle();
    }

    @Test
    public void clearDefaultHomeIntentReceiver() {
        mDevicePolicyManager.clearPackagePersistentPreferredActivities(ADMIN_COMPONENT,
                PACKAGE_NAME);
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
    }

    private void checkLockedActivityIsRunning() throws Exception {
        assertTrue(isActivityOnTop());
        assertEquals(ActivityManager.LOCK_TASK_MODE_LOCKED,
                mActivityManager.getLockTaskModeState());
    }

    private boolean isActivityOnTop() {
        return mActivityManager.getAppTasks().get(0).getTaskInfo().topActivity
                .getClassName().equals(LOCK_TASK_ACTIVITY);
    }

    private void launchLockTaskActivity() {
        Intent intent = new Intent();
        intent.setClassName(PACKAGE_NAME, LOCK_TASK_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LockTaskUtilityActivity.START_LOCK_TASK, true);
        mContext.startActivity(intent);
    }

    private void setDefaultHomeIntentReceiver() {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        intentFilter.addCategory(Intent.CATEGORY_HOME);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        mDevicePolicyManager.addPersistentPreferredActivity(ADMIN_COMPONENT, intentFilter,
                new ComponentName(PACKAGE_NAME, LOCK_TASK_ACTIVITY));
    }
}
