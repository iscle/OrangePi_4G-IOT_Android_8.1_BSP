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
package com.android.cts.deviceowner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertArrayEquals;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LockTaskTest {

    private static final String TAG = "LockTaskTest";

    private static final String PACKAGE_NAME = LockTaskTest.class.getPackage().getName();
    private static final ComponentName ADMIN_COMPONENT =
            new ComponentName(PACKAGE_NAME, BaseDeviceOwnerTest.BasicAdminReceiver.class.getName());
    private static final String TEST_PACKAGE = "com.google.android.example.somepackage";

    private static final String UTILITY_ACTIVITY
            = "com.android.cts.deviceowner.LockTaskUtilityActivity";
    private static final String UTILITY_ACTIVITY_IF_WHITELISTED
            = "com.android.cts.deviceowner.LockTaskUtilityActivityIfWhitelisted";

    private static final String RECEIVING_ACTIVITY_PACKAGE_NAME
            = "com.android.cts.intent.receiver";
    private static final String RECEIVING_ACTIVITY_NAME
            = "com.android.cts.intent.receiver.IntentReceiverActivity";
    private static final String ACTION_JUST_CREATE =
            "com.android.cts.action.JUST_CREATE";

    private static final int ACTIVITY_RESUMED_TIMEOUT_MILLIS = 20000;  // 20 seconds
    private static final int ACTIVITY_RUNNING_TIMEOUT_MILLIS = 10000;  // 10 seconds
    private static final int ACTIVITY_DESTROYED_TIMEOUT_MILLIS = 60000;  // 60 seconds
    public static final String RECEIVING_ACTIVITY_CREATED_ACTION
            = "com.android.cts.deviceowner.RECEIVING_ACTIVITY_CREATED_ACTION";
    /**
     * The tests below need to keep detailed track of the state of the activity
     * that is started and stopped frequently.  To do this it sends a number of
     * broadcasts that are caught here and translated into booleans (as well as
     * notify some locks in case we are waiting).  There is also an action used
     * to specify that the activity has finished handling the current command
     * (INTENT_ACTION).
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive: " + action);
            if (LockTaskUtilityActivity.CREATE_ACTION.equals(action)) {
                synchronized (mActivityRunningLock) {
                    mIsActivityRunning = true;
                    mActivityRunningLock.notify();
                }
            } else if (LockTaskUtilityActivity.DESTROY_ACTION.equals(action)) {
                synchronized (mActivityRunningLock) {
                    mIsActivityRunning = false;
                    mActivityRunningLock.notify();
                }
            } else if (LockTaskUtilityActivity.RESUME_ACTION.equals(action)) {
                synchronized (mActivityResumedLock) {
                    mIsActivityResumed = true;
                    mActivityResumedLock.notify();
                }
            } else if (LockTaskUtilityActivity.PAUSE_ACTION.equals(action)) {
                synchronized (mActivityResumedLock) {
                    mIsActivityResumed = false;
                    mActivityResumedLock.notify();
                }
            } else if (LockTaskUtilityActivity.INTENT_ACTION.equals(action)) {
                // Notify that intent has been handled.
                synchronized (LockTaskTest.this) {
                    mIntentHandled = true;
                    LockTaskTest.this.notify();
                }
            } else if (RECEIVING_ACTIVITY_CREATED_ACTION.equals(action)) {
                synchronized(mReceivingActivityCreatedLock) {
                    mReceivingActivityWasCreated = true;
                    mReceivingActivityCreatedLock.notify();
                }
            }
        }
    };

    public static class IntentReceivingActivity extends Activity {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sendBroadcast(new Intent(RECEIVING_ACTIVITY_CREATED_ACTION));
            finish();
        }
    }

    private volatile boolean mIsActivityRunning;
    private volatile boolean mIsActivityResumed;
    private volatile boolean mReceivingActivityWasCreated;
    private volatile boolean mIntentHandled;
    private final Object mActivityRunningLock = new Object();
    private final Object mActivityResumedLock = new Object();
    private final Object mReceivingActivityCreatedLock = new Object();

    private Context mContext;
    private ActivityManager mActivityManager;
    private DevicePolicyManager mDevicePolicyManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();

        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(LockTaskUtilityActivity.CREATE_ACTION);
        filter.addAction(LockTaskUtilityActivity.DESTROY_ACTION);
        filter.addAction(LockTaskUtilityActivity.INTENT_ACTION);
        filter.addAction(LockTaskUtilityActivity.RESUME_ACTION);
        filter.addAction(LockTaskUtilityActivity.PAUSE_ACTION);
        filter.addAction(RECEIVING_ACTIVITY_CREATED_ACTION);
        mContext.registerReceiver(mReceiver, filter);
    }

    @After
    public void tearDown() {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
        mContext.unregisterReceiver(mReceiver);
    }

    // Setting and unsetting the lock task packages.
    @Test
    public void testSetLockTaskPackages() {
        final String[] packages = new String[] { TEST_PACKAGE, "some.other.package" };
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, packages);
        assertArrayEquals(packages, mDevicePolicyManager.getLockTaskPackages(ADMIN_COMPONENT));
        assertTrue(mDevicePolicyManager.isLockTaskPermitted(TEST_PACKAGE));

        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
        assertEquals(0, mDevicePolicyManager.getLockTaskPackages(ADMIN_COMPONENT).length);
        assertFalse(mDevicePolicyManager.isLockTaskPermitted(TEST_PACKAGE));
    }

    // Start lock task, verify that ActivityManager knows thats what is going on.
    @Test
    public void testStartLockTask() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        // Verify that activity open and activity manager is in lock task.
        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY);
    }

    // Verifies that the act of finishing is blocked by ActivityManager in lock task.
    // This results in onDestroy not being called until stopLockTask is called before finish.
    @Test
    public void testCannotFinish() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);

        // If lock task has not exited then the activity shouldn't actually receive onDestroy.
        finishAndWait(UTILITY_ACTIVITY);
        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);

        stopAndFinish(UTILITY_ACTIVITY);
    }

    // Verifies that updating the whitelisting during lock task mode finishes the locked task.
    @Test
    public void testUpdateWhitelisting() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);

        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);

        synchronized (mActivityRunningLock) {
            mActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
        }

        assertLockTaskModeInactive();
        assertFalse(mIsActivityRunning);
        assertFalse(mIsActivityResumed);
    }

    // This launches an activity that is in the current task.
    // This should always be permitted as a part of lock task (since it isn't a new task).
    @Test
    public void testStartActivity_withinTask() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        mReceivingActivityWasCreated = false;
        Intent launchIntent = getIntentReceivingActivityIntent(0);
        Intent lockTaskUtility = getLockTaskUtility(UTILITY_ACTIVITY);
        lockTaskUtility.putExtra(LockTaskUtilityActivity.START_ACTIVITY, launchIntent);
        mContext.startActivity(lockTaskUtility);

        synchronized (mReceivingActivityCreatedLock) {
            mReceivingActivityCreatedLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertTrue(mReceivingActivityWasCreated);
        }
        stopAndFinish(UTILITY_ACTIVITY);
    }

    // This launches a whitelisted activity that is not part of the current task.
    // This should be permitted as a part of lock task.
    @Test
    public void testStartActivity_outsideTaskWhitelisted() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME,
                RECEIVING_ACTIVITY_PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        mReceivingActivityWasCreated = false;
        Intent launchIntent = getIntentReceivingActivityIntent(0);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(launchIntent);
        synchronized (mReceivingActivityCreatedLock) {
            mReceivingActivityCreatedLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertTrue(mReceivingActivityWasCreated);
        }
        stopAndFinish(UTILITY_ACTIVITY);
    }

    // This launches a non-whitelisted activity that is not part of the current task.
    // This should be blocked.
    @Test
    public void testStartActivity_outsideTaskNonWhitelisted() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        Intent launchIntent = getIntentReceivingActivityIntent(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(launchIntent);
        synchronized (mActivityResumedLock) {
            mActivityResumedLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertFalse(mReceivingActivityWasCreated);
        }
        stopAndFinish(UTILITY_ACTIVITY);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // Whitelist the activity and verify that lock task mode is started.
    @Test
    public void testManifestArgument_whitelisted() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_WHITELISTED));
        waitForResume();

        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY_IF_WHITELISTED);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // Don't whitelist the activity and verify that lock task mode is not started.
    @Test
    public void testManifestArgument_nonWhitelisted() throws Exception {
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_WHITELISTED));
        waitForResume();

        assertLockTaskModeInactive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY_IF_WHITELISTED);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // An activity locked via manifest argument cannot finish without calling stopLockTask.
    @Test
    public void testManifestArgument_cannotFinish() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_WHITELISTED));
        waitForResume();

        // If lock task has not exited then the activity shouldn't actually receive onDestroy.
        finishAndWait(UTILITY_ACTIVITY_IF_WHITELISTED);
        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);

        stopAndFinish(UTILITY_ACTIVITY_IF_WHITELISTED);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // Verifies that updating the whitelisting during lock task mode finishes the locked task.
    @Test
    public void testManifestArgument_updateWhitelisting() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_WHITELISTED));
        waitForResume();

        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);

        synchronized (mActivityRunningLock) {
            mActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
        }

        assertLockTaskModeInactive();
        assertFalse(mIsActivityRunning);
        assertFalse(mIsActivityResumed);
    }

    /**
     * Checks that lock task mode is active and fails the test if it isn't.
     */
    private void assertLockTaskModeActive() {
        assertTrue(mActivityManager.isInLockTaskMode());
        assertEquals(ActivityManager.LOCK_TASK_MODE_LOCKED,
                mActivityManager.getLockTaskModeState());
    }

    /**
     * Checks that lock task mode is not active and fails the test if it is.
     */
    private void assertLockTaskModeInactive() throws InterruptedException {
        // Retry 10 times with 200 ms interval.
        for (int i = 0; i < 10 && mActivityManager.isInLockTaskMode(); i++) {
            Thread.sleep(200);
        }
        assertFalse(mActivityManager.isInLockTaskMode());
        assertEquals(ActivityManager.LOCK_TASK_MODE_NONE, mActivityManager.getLockTaskModeState());
    }

    /**
     * Call stopLockTask and finish on the LockTaskUtilityActivity.
     *
     * Verify that the activity is no longer running.
     *
     * If activityManager is not null then verify that the ActivityManager
     * is no longer in lock task mode.
     */
    private void stopAndFinish(String className) throws InterruptedException {
        stopLockTask(className);
        finishAndWait(className);
        assertLockTaskModeInactive();
        assertFalse(mIsActivityRunning);
    }

    /**
     * Call finish on the LockTaskUtilityActivity and wait for
     * onDestroy to be called.
     */
    private void finishAndWait(String className) throws InterruptedException {
        synchronized (mActivityRunningLock) {
            finish(className);
            if (mIsActivityRunning) {
                mActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
            }
        }
    }

    /**
     * Wait for onResume to be called on the LockTaskUtilityActivity.
     */
    private void waitForResume() throws InterruptedException {
        // It may take a moment for the resume to come in.
        synchronized (mActivityResumedLock) {
            if (!mIsActivityResumed) {
                mActivityResumedLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            }
        }
    }

    /**
     * Calls startLockTask on the LockTaskUtilityActivity
     */
    private void startLockTask(String className) throws InterruptedException {
        Intent intent = getLockTaskUtility(className);
        intent.putExtra(LockTaskUtilityActivity.START_LOCK_TASK, true);
        startAndWait(intent);
    }

    /**
     * Calls stopLockTask on the LockTaskUtilityActivity
     */
    private void stopLockTask(String className) throws InterruptedException {
        Intent intent = getLockTaskUtility(className);
        intent.putExtra(LockTaskUtilityActivity.STOP_LOCK_TASK, true);
        startAndWait(intent);
    }

    /**
     * Calls finish on the LockTaskUtilityActivity
     */
    private void finish(String className) throws InterruptedException {
        Intent intent = getLockTaskUtility(className);
        intent.putExtra(LockTaskUtilityActivity.FINISH, true);
        startAndWait(intent);
    }

    /**
     * Sends a command intent to the LockTaskUtilityActivity and waits
     * to receive the broadcast back confirming it has finished processing
     * the command.
     */
    private void startAndWait(Intent intent) throws InterruptedException {
        mIntentHandled = false;
        synchronized (this) {
            mContext.startActivity(intent);
            // Give 20 secs to finish.
            wait(ACTIVITY_RUNNING_TIMEOUT_MILLIS);
            assertTrue(mIntentHandled);
        }
    }

    /**
     * Get basic intent that points at the LockTaskUtilityActivity.
     *
     * This intent includes the flags to make it act as single top.
     */
    private Intent getLockTaskUtility(String className) {
        Intent intent = new Intent();
        intent.setClassName(PACKAGE_NAME, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private Intent getIntentReceivingActivityIntent(int flags) {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(RECEIVING_ACTIVITY_PACKAGE_NAME, RECEIVING_ACTIVITY_NAME));
        intent.setAction(ACTION_JUST_CREATE);
        intent.setFlags(flags);
        return intent;
    }
}
