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
package com.android.cts.launchertests;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.test.InstrumentationRegistry;
import android.test.AndroidTestCase;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Tests for LauncherApps service
 */
public class LauncherAppsTests extends AndroidTestCase {

    public static final String SIMPLE_APP_PACKAGE = "com.android.cts.launcherapps.simpleapp";

    public static final String USER_EXTRA = "user_extra";
    public static final String PACKAGE_EXTRA = "package_extra";
    public static final String REPLY_EXTRA = "reply_extra";

    public static final int MSG_RESULT = 0;
    public static final int MSG_CHECK_PACKAGE_ADDED = 1;
    public static final int MSG_CHECK_PACKAGE_REMOVED = 2;
    public static final int MSG_CHECK_PACKAGE_CHANGED = 3;
    public static final int MSG_CHECK_NO_PACKAGE_ADDED = 4;

    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;
    public static final int RESULT_TIMEOUT = 3;

    private LauncherApps mLauncherApps;
    private UserHandle mUser;
    private Instrumentation mInstrumentation;
    private Messenger mService;
    private Connection mConnection;
    private Result mResult;
    private Messenger mResultMessenger;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Bundle arguments = InstrumentationRegistry.getArguments();
        UserManager userManager = (UserManager) mInstrumentation.getContext().getSystemService(
                Context.USER_SERVICE);
        mUser = getUserHandleArgument(userManager, "testUser", arguments);
        mLauncherApps = (LauncherApps) mInstrumentation.getContext().getSystemService(
                Context.LAUNCHER_APPS_SERVICE);

        final Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.android.cts.launchertests.support",
                        "com.android.cts.launchertests.support.LauncherCallbackTestsService"));

        mConnection = new Connection();
        mInstrumentation.getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mConnection.waitForService();
        mResult = new Result(Looper.getMainLooper());
        mResultMessenger = new Messenger(mResult);
    }

    public void testGetActivitiesForUserFails() throws Exception {
        try {
            List<LauncherActivityInfo> activities =
                    mLauncherApps.getActivityList(null, mUser);
            fail("getActivities for non-profile user failed to throw exception");
        } catch (SecurityException e) {
            // Expected.
        }
    }

    public void testSimpleAppInstalledForUser() throws Exception {
        List<LauncherActivityInfo> activities =
                mLauncherApps.getActivityList(null, mUser);
        // Check simple app is there.
        boolean foundSimpleApp = false;
        for (LauncherActivityInfo activity : activities) {
            if (activity.getComponentName().getPackageName().equals(
                    SIMPLE_APP_PACKAGE)) {
                foundSimpleApp = true;
            }
            assertTrue(activity.getUser().equals(mUser));
        }
        assertTrue(foundSimpleApp);

        // Also make sure getApplicationInfo works too.
        final ApplicationInfo ai =
                mLauncherApps.getApplicationInfo(SIMPLE_APP_PACKAGE, /* flags= */ 0, mUser);
        assertEquals(SIMPLE_APP_PACKAGE, ai.packageName);
        assertEquals(mUser, UserHandle.getUserHandleForUid(ai.uid));
    }

    public void testAccessPrimaryProfileFromManagedProfile() throws Exception {
        // Try to access main profile from managed profile, which is not allowed.
        assertEquals(0, mLauncherApps.getActivityList(null, mUser).size());
        try {
            mLauncherApps.getApplicationInfo(SIMPLE_APP_PACKAGE, /* flags= */ 0, mUser);
            fail("Missing exception");
        } catch (PackageManager.NameNotFoundException e) {
            // Expected.
        }
        assertFalse(mLauncherApps.isPackageEnabled(SIMPLE_APP_PACKAGE, mUser));

        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.android.com/"));
        assertNull(mLauncherApps.resolveActivity(intent, mUser));
    }

    public void testGetProfiles_fromMainProfile() {
        final List<UserHandle> profiles = mLauncherApps.getProfiles();
        assertEquals(2, profiles.size());
        assertTrue(profiles.contains(android.os.Process.myUserHandle()));
        assertEquals(getContext().getSystemService(UserManager.class).getUserProfiles(),
                profiles);
    }

    public void testGetProfiles_fromManagedProfile() {
        final List<UserHandle> profiles = mLauncherApps.getProfiles();
        assertEquals(1, profiles.size());
        assertEquals(android.os.Process.myUserHandle(), profiles.get(0));
    }

    public void testPackageAddedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_PACKAGE_ADDED,
                mUser, SIMPLE_APP_PACKAGE);
        assertEquals(RESULT_PASS, result);
    }

    public void testPackageRemovedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_PACKAGE_REMOVED,
                mUser, SIMPLE_APP_PACKAGE);
        assertEquals(RESULT_PASS, result);
    }
    public void testPackageChangedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_PACKAGE_CHANGED,
                mUser, SIMPLE_APP_PACKAGE);
        assertEquals(RESULT_PASS, result);
    }

    public void testNoPackageAddedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_NO_PACKAGE_ADDED,
                mUser, SIMPLE_APP_PACKAGE);
        assertEquals(RESULT_PASS, result);
    }

    public void testLaunchNonExportActivityFails() throws Exception {
        try {
            mLauncherApps.startMainActivity(new ComponentName(
                    SIMPLE_APP_PACKAGE,
                    SIMPLE_APP_PACKAGE + ".NonExportedActivity"),
                    mUser, null, null);
            fail("starting non-exported activity failed to throw exception");
        } catch (SecurityException e) {
            // Expected.
        }
    }

    public void testLaunchNonExportLauncherFails() throws Exception {
        try {
            mLauncherApps.startMainActivity(new ComponentName(
                    SIMPLE_APP_PACKAGE,
                    SIMPLE_APP_PACKAGE + ".NonLauncherActivity"),
                    mUser, null, null);
            fail("starting non-launcher activity failed to throw exception");
        } catch (SecurityException e) {
            // Expected.
        }
    }

    public void testLaunchMainActivity() throws Exception {
        ActivityLaunchedReceiver receiver = new ActivityLaunchedReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ActivityLaunchedReceiver.ACTIVITY_LAUNCHED_ACTION);
        mInstrumentation.getContext().registerReceiver(receiver, filter);
        mLauncherApps.startMainActivity(new ComponentName(
                SIMPLE_APP_PACKAGE,
                SIMPLE_APP_PACKAGE + ".SimpleActivity"),
                mUser, null, null);
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        mInstrumentation.getContext().unregisterReceiver(receiver);
    }

    private UserHandle getUserHandleArgument(UserManager userManager, String key,
            Bundle arguments) throws Exception {
        String serial = arguments.getString(key);
        if (serial == null) {
            return null;
        }
        int serialNo = Integer.parseInt(serial);
        return userManager.getUserForSerialNumber(serialNo);
    }

    private class Connection implements ServiceConnection {
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            mSemaphore.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }

        public void waitForService() {
            try {
                if (mSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
            }
            fail("failed to connec to service");
        }
    };

    private static class Result extends Handler {

        private final Semaphore mSemaphore = new Semaphore(0);
        public int result = 0;

        public Result(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RESULT) {
                result = msg.arg1;
                mSemaphore.release();
            } else {
                super.handleMessage(msg);
            }
        }

        public int waitForResult() {
            try {
                if (mSemaphore.tryAcquire(120, TimeUnit.SECONDS)) {
                     return result;
                }
            } catch (InterruptedException e) {
            }
            return RESULT_TIMEOUT;
        }
    }

    public class ActivityLaunchedReceiver extends BroadcastReceiver {
        public static final String ACTIVITY_LAUNCHED_ACTION =
                "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";

        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTIVITY_LAUNCHED_ACTION)) {
                mSemaphore.release();
            }
        }

        public int waitForActivity() {
            try {
                if (mSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    return RESULT_PASS;
                }
            } catch (InterruptedException e) {
            }
            return RESULT_TIMEOUT;
        }
    }

    private int sendMessageToCallbacksService(int msg, UserHandle user, String packageName)
            throws Throwable {
        Bundle params = new Bundle();
        params.putParcelable(USER_EXTRA, user);
        params.putString(PACKAGE_EXTRA, packageName);

        Message message = Message.obtain(null, msg, params);
        message.replyTo = mResultMessenger;

        mService.send(message);

        return mResult.waitForResult();
    }
}
