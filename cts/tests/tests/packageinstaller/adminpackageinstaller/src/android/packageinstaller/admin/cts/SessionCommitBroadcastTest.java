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
package android.packageinstaller.admin.cts;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class tests {@link PackageInstaller#ACTION_SESSION_COMMITTED} is properly sent to the
 * launcher app.
 */
public class SessionCommitBroadcastTest extends BasePackageInstallTest {

    private static final long BROADCAST_TIMEOUT_SECS = 20;

    private ComponentName mDefaultLauncher;
    private ComponentName mThisAppLauncher;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDefaultLauncher = ComponentName.unflattenFromString(getDefaultLauncher());
        mThisAppLauncher = new ComponentName(mContext, LauncherActivity.class);
    }

    public void testBroadcastNotReceivedForDifferentLauncher() throws Exception {
        if (!mHasFeature) {
            return;
        }
        if (mDefaultLauncher.equals(mThisAppLauncher)) {
            // Find a different launcher
            Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addCategory(Intent.CATEGORY_DEFAULT);
            for (ResolveInfo info : mPackageManager.queryIntentActivities(homeIntent, 0)) {
                mDefaultLauncher =
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                if (!mDefaultLauncher.equals(mThisAppLauncher)) {
                    setLauncher(mDefaultLauncher.flattenToString());
                    break;
                }
            }
        }

        assertFalse("No default launcher found", mDefaultLauncher.equals(mThisAppLauncher));
        SessionCommitReceiver receiver = new SessionCommitReceiver();
        // install the app
        assertInstallPackage();
        // Broadcast not received
        assertNull(receiver.blockingGetIntent());

        tryUninstallPackage();
    }

    private void verifySessionIntent(Intent intent) {
        assertNotNull(intent);
        PackageInstaller.SessionInfo info = intent
                .getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        assertEquals(TEST_APP_PKG, info.getAppPackageName());
    }

    public void testBroadcastReceivedForNewInstall() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setLauncher(mThisAppLauncher.flattenToString());

        SessionCommitReceiver receiver = new SessionCommitReceiver();
        // install the app
        assertInstallPackage();

        verifySessionIntent(receiver.blockingGetIntent());

        forceUninstall();
        receiver = new SessionCommitReceiver();
        assertInstallPackage();
        verifySessionIntent(receiver.blockingGetIntent());

        tryUninstallPackage();
        // Revert to default launcher
        setLauncher(mDefaultLauncher.flattenToString());
    }

    public void testBroadcastReceivedForEnablingApp() throws Exception {
        if (!mHasFeature || !UserManager.supportsMultipleUsers()) {
            return;
        }
        setLauncher(mThisAppLauncher.flattenToString());

        ComponentName cn = new ComponentName(mContext, BasicAdminReceiver.class);
        UserHandle user = mDevicePolicyManager.createAndManageUser(cn,
                "Test User " + System.currentTimeMillis(), cn,
                null, DevicePolicyManager.SKIP_SETUP_WIZARD);
        int userId = user.getIdentifier();
        assertTrue(TextUtils.join(" ", runShellCommand("am start-user " + userId))
                .toLowerCase().contains("success"));

        // Install app for the second user
        assertTrue(TextUtils.join(" ", runShellCommand(
                "pm install -r --user " + userId + "  " + TEST_APP_LOCATION))
                .toLowerCase().contains("success"));

        // Enable the app for this user
        SessionCommitReceiver receiver = new SessionCommitReceiver();
        runShellCommand("cmd package install-existing --user " +
                Process.myUserHandle().getIdentifier() + "  " + TEST_APP_PKG);
        verifySessionIntent(receiver.blockingGetIntent());

        // Cleanup
        setLauncher(mDefaultLauncher.flattenToString());
        mDevicePolicyManager.removeUser(cn, user);
        forceUninstall();
    }

    private String getDefaultLauncher() throws Exception {
        final String PREFIX = "Launcher: ComponentInfo{";
        final String POSTFIX = "}";
        for (String s : runShellCommand("cmd shortcut get-default-launcher")) {
            if (s.startsWith(PREFIX) && s.endsWith(POSTFIX)) {
                return s.substring(PREFIX.length(), s.length() - POSTFIX.length());
            }
        }
        throw new Exception("Default launcher not found");
    }

    private void setLauncher(String component) throws Exception {
        runShellCommand("cmd package set-home-activity --user "
                + getInstrumentation().getContext().getUserId() + " " + component);
    }

    private class SessionCommitReceiver extends BroadcastReceiver {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private Intent mIntent;

        SessionCommitReceiver() {
            mContext.registerReceiver(this,
                    new IntentFilter(PackageInstaller.ACTION_SESSION_COMMITTED));
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            assertNull(mIntent);
            mIntent = intent;
            mLatch.countDown();
        }

        public Intent blockingGetIntent() throws Exception {
            mLatch.await(BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS);
            mContext.unregisterReceiver(this);
            return mIntent;
        }
    }
}
