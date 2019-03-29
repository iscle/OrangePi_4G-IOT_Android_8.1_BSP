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

package com.android.cts.deviceowner;

import android.app.ActivityManager;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link DevicePolicyManager#createAndManageUser}.
 */
public class CreateAndManageUserTest extends BaseDeviceOwnerTest {
    private static final String TAG = "CreateAndManageUserTest";

    private static final String BROADCAST_EXTRA = "broadcastExtra";
    private static final String ACTION_EXTRA = "actionExtra";
    private static final String SERIAL_EXTRA = "serialExtra";
    private static final String PROFILE_OWNER_EXTRA = "profileOwnerExtra";
    private static final String SETUP_COMPLETE_EXTRA = "setupCompleteExtra";
    private static final int BROADCAST_TIMEOUT = 15_000;
    private static final int USER_SWITCH_DELAY = 10_000;
    private PackageManager mPackageManager;
    private ActivityManager mActivityManager;
    private volatile boolean mReceived;
    private volatile boolean mTestProfileOwnerWasUsed;
    private volatile boolean mSetupComplete;
    private UserHandle mUserHandle;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPackageManager = mContext.getPackageManager();
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_ADD_USER);
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);
        // Remove user in case of failed test.
        if (mUserHandle != null) {
            mDevicePolicyManager.removeUser(getWho(), mUserHandle);
            mUserHandle = null;
        }
        super.tearDown();
    }

    // This class is used by createAndManageUserTest as profile owner for the new user. When
    // enabled, it sends a broadcast to signal success.
    public static class TestProfileOwner extends DeviceAdminReceiver {
        @Override
        public void onEnabled(Context context, Intent intent) {
            if (intent.getBooleanExtra(BROADCAST_EXTRA, false)) {
                Intent i = new Intent(intent.getStringExtra(ACTION_EXTRA));
                UserManager userManager = (UserManager)
                        context.getSystemService(Context.USER_SERVICE);
                long serial = intent.getLongExtra(SERIAL_EXTRA, 0);
                UserHandle handle = userManager.getUserForSerialNumber(serial);
                i.putExtra(PROFILE_OWNER_EXTRA, true);
                // find value of user_setup_complete on new user, and send the result back
                try {
                    boolean setupComplete = (Settings.Secure.getInt(context.getContentResolver(),
                            "user_setup_complete") == 1);
                    i.putExtra(SETUP_COMPLETE_EXTRA, setupComplete);
                } catch (Settings.SettingNotFoundException e) {
                    fail("Did not find settings user_setup_complete");
                }

                context.sendBroadcastAsUser(i, handle);
            }
        }

        public static ComponentName getComponentName() {
            return new ComponentName(CreateAndManageUserTest.class.getPackage().getName(),
                    TestProfileOwner.class.getName());
        }
    }

    private void waitForBroadcastLocked() {
        // Wait for broadcast. Time is measured in a while loop because of spurious wakeups.
        final long initTime = System.currentTimeMillis();
        while (!mReceived) {
            try {
                wait(BROADCAST_TIMEOUT - (System.currentTimeMillis() - initTime));
            } catch (InterruptedException e) {
                fail("InterruptedException: " + e.getMessage());
            }
            if (!mReceived && System.currentTimeMillis() - initTime > BROADCAST_TIMEOUT) {
                fail("Timeout while waiting for broadcast after createAndManageUser.");
            }
        }
    }

// Disabled due to b/29072728
//    // This test will create a user that will get passed a bundle that we specify. The bundle will
//    // contain an action and a serial (for user handle) to broadcast to notify the test that the
//    // configuration was triggered.
//    private void createAndManageUserTest(final int flags) {
//        // This test sets a profile owner on the user, which requires the managed_users feature.
//        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
//            return;
//        }
//
//        final boolean expectedSetupComplete = (flags & DevicePolicyManager.SKIP_SETUP_WIZARD) != 0;
//        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
//
//        UserHandle firstUser = Process.myUserHandle();
//        final String testUserName = "TestUser_" + System.currentTimeMillis();
//        String action = "com.android.cts.TEST_USER_ACTION";
//        PersistableBundle bundle = new PersistableBundle();
//        bundle.putBoolean(BROADCAST_EXTRA, true);
//        bundle.putLong(SERIAL_EXTRA, userManager.getSerialNumberForUser(firstUser));
//        bundle.putString(ACTION_EXTRA, action);
//
//        mReceived = false;
//        mTestProfileOwnerWasUsed = false;
//        mSetupComplete = !expectedSetupComplete;
//        BroadcastReceiver receiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                mReceived = true;
//                if (intent.getBooleanExtra(PROFILE_OWNER_EXTRA, false)) {
//                    mTestProfileOwnerWasUsed = true;
//                }
//                mSetupComplete = intent.getBooleanExtra(SETUP_COMPLETE_EXTRA,
//                        !expectedSetupComplete);
//                synchronized (CreateAndManageUserTest.this) {
//                    CreateAndManageUserTest.this.notify();
//                }
//            }
//        };
//
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(action);
//        mContext.registerReceiver(receiver, filter);
//
//        synchronized (this) {
//            mUserHandle = mDevicePolicyManager.createAndManageUser(getWho(), testUserName,
//                    TestProfileOwner.getComponentName(), bundle, flags);
//            assertNotNull(mUserHandle);
//
//            mDevicePolicyManager.switchUser(getWho(), mUserHandle);
//            try {
//                wait(USER_SWITCH_DELAY);
//            } catch (InterruptedException e) {
//                fail("InterruptedException: " + e.getMessage());
//            }
//            mDevicePolicyManager.switchUser(getWho(), firstUser);
//
//            waitForBroadcastLocked();
//
//            assertTrue(mReceived);
//            assertTrue(mTestProfileOwnerWasUsed);
//            assertEquals(expectedSetupComplete, mSetupComplete);
//
//            assertTrue(mDevicePolicyManager.removeUser(getWho(), mUserHandle));
//
//            mUserHandle = null;
//        }
//
//        mContext.unregisterReceiver(receiver);
//    }

    /**
     * Test creating an ephemeral user using the {@link DevicePolicyManager#createAndManageUser}
     * method.
     *
     * <p>The test creates a user by calling to {@link DevicePolicyManager#createAndManageUser}. It
     * doesn't remove the user afterwards, so its properties can be queried and tested by host-side
     * tests.
     * <p>The user's flags will be checked from the corresponding host-side test.
     */
    public void testCreateAndManageEphemeralUser() throws Exception {
        String testUserName = "TestUser_" + System.currentTimeMillis();

        // Use reflection to get the value of the hidden flag to make the new user ephemeral.
        Field field = DevicePolicyManager.class.getField("MAKE_USER_EPHEMERAL");
        int makeEphemeralFlag = field.getInt(null);

        // Do not assign return value to mUserHandle, so it is not removed in tearDown.
        mDevicePolicyManager.createAndManageUser(
                getWho(),
                testUserName,
                getWho(),
                null,
                makeEphemeralFlag);
    }

    /**
     * Test creating an ephemeral user using the {@link DevicePolicyManager#createAndManageUser}
     * method fails on systems without the split system user.
     *
     * <p>To be used by host-side test on systems without the split system user.
     */
    public void testCreateAndManageEphemeralUserFails() throws Exception {
        String testUserName = "TestUser_" + System.currentTimeMillis();

        // Use reflection to get the value of the hidden flag to make the new user ephemeral.
        Field field = DevicePolicyManager.class.getField("MAKE_USER_EPHEMERAL");
        int makeEphemeralFlag = field.getInt(null);

        try {
            mDevicePolicyManager.createAndManageUser(
                    getWho(),
                    testUserName,
                    getWho(),
                    null,
                    makeEphemeralFlag);
        } catch (IllegalArgumentException e) {
            // Success, the expected exception was thrown.
            return;
        }
        fail("createAndManageUser should have thrown IllegalArgumentException");
    }

// Disabled due to b/29072728
//    public void testCreateAndManageUser_SkipSetupWizard() {
//        createAndManageUserTest(DevicePolicyManager.SKIP_SETUP_WIZARD);
//    }
//
//    public void testCreateAndManageUser_DontSkipSetupWizard() {
//        if (!mActivityManager.isRunningInTestHarness()) {
//            // In test harness, the setup wizard will be disabled by default, so this test is always
//            // failing.
//            createAndManageUserTest(0);
//        }
//    }

    // createAndManageUser should circumvent the DISALLOW_ADD_USER restriction
    public void testCreateAndManageUser_AddRestrictionSet() {
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_ADD_USER);

        mUserHandle = mDevicePolicyManager.createAndManageUser(getWho(), "Test User", getWho(),
                null, 0);
        assertNotNull(mUserHandle);
    }

    public void testCreateAndManageUser_RemoveRestrictionSet() {
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);

        mUserHandle = mDevicePolicyManager.createAndManageUser(getWho(), "Test User", getWho(),
                null, 0);
        assertNotNull(mUserHandle);

        boolean removed = mDevicePolicyManager.removeUser(getWho(), mUserHandle);
        // When the device owner itself has set the user restriction, it should still be allowed
        // to remove a user.
        assertTrue(removed);
    }

    public void testUserAddedOrRemovedBroadcasts() throws InterruptedException {
        LocalBroadcastReceiver receiver = new LocalBroadcastReceiver();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(
                getContext());
        localBroadcastManager.registerReceiver(receiver, new IntentFilter(ACTION_USER_ADDED));
        try {
            mUserHandle = mDevicePolicyManager.createAndManageUser(getWho(), "Test User", getWho(),
                    null, 0);
            assertNotNull(mUserHandle);
            assertEquals(mUserHandle, receiver.waitForBroadcastReceived());
        } finally {
            localBroadcastManager.unregisterReceiver(receiver);
        }
        localBroadcastManager.registerReceiver(receiver, new IntentFilter(ACTION_USER_REMOVED));
        try {
            assertTrue(mDevicePolicyManager.removeUser(getWho(), mUserHandle));
            assertEquals(mUserHandle, receiver.waitForBroadcastReceived());
            mUserHandle = null;
        } finally {
            localBroadcastManager.unregisterReceiver(receiver);
        }
    }

    static class LocalBroadcastReceiver extends BroadcastReceiver {
        private SynchronousQueue<UserHandle> mQueue = new SynchronousQueue<UserHandle>();

        @Override
        public void onReceive(Context context, Intent intent) {
            UserHandle userHandle = intent.getParcelableExtra(EXTRA_USER_HANDLE);
            Log.d(TAG, "broadcast receiver received " + intent + " with userHandle "
                    + userHandle);
            mQueue.offer(userHandle);

        }

        public UserHandle waitForBroadcastReceived() throws InterruptedException {
            return mQueue.poll(BROADCAST_TIMEOUT, TimeUnit.MILLISECONDS);
        }
    }
}
