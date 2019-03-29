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
package com.android.cts.packageinstaller;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.support.test.uiautomator.UiDevice;
import android.test.InstrumentationTestCase;

import com.android.cts.packageinstaller.ClearDeviceOwnerTest.BasicAdminReceiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Base test case for testing PackageInstaller.
 */
public class BasePackageInstallTest extends InstrumentationTestCase {
    protected static final String TEST_APP_LOCATION = "/data/local/tmp/CtsSimpleApp.apk";
    protected static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    protected static final int PACKAGE_INSTALLER_TIMEOUT_MS = 60000; // 60 seconds
    private static final String ACTION_INSTALL_COMMIT =
            "com.android.cts.deviceowner.INTENT_PACKAGE_INSTALL_COMMIT";
    protected static final int PACKAGE_INSTALLER_STATUS_UNDEFINED = -1000;
    public static final String PACKAGE_NAME = BasePackageInstallTest.class.getPackage().getName();

    protected Context mContext;
    protected UiDevice mDevice;
    protected DevicePolicyManager mDevicePolicyManager;
    private PackageManager mPackageManager;
    private PackageInstaller mPackageInstaller;
    private PackageInstaller.Session mSession;
    protected boolean mCallbackReceived;
    protected int mCallbackStatus;
    protected Intent mCallbackIntent;

    protected final Object mPackageInstallerTimeoutLock = new Object();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mPackageInstallerTimeoutLock) {
                mCallbackStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PACKAGE_INSTALLER_STATUS_UNDEFINED);
                if (mCallbackStatus == PackageInstaller.STATUS_SUCCESS) {
                    mContext.unregisterReceiver(this);
                    assertEquals(TEST_APP_PKG, intent.getStringExtra(
                            PackageInstaller.EXTRA_PACKAGE_NAME));
                } else if (mCallbackStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    mCallbackIntent = (Intent) intent.getExtras().get(Intent.EXTRA_INTENT);
                }
                mCallbackReceived = true;
                mPackageInstallerTimeoutLock.notify();
            }
        }
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mPackageManager = mContext.getPackageManager();
        mPackageInstaller = mPackageManager.getPackageInstaller();
        assertNotNull(mPackageInstaller);

        // check that app is not already installed
        assertFalse(isPackageInstalled(TEST_APP_PKG));
    }

    @Override
    protected void tearDown() throws Exception {
        if (mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME) ||
                mDevicePolicyManager.isProfileOwnerApp(PACKAGE_NAME)) {
            mDevicePolicyManager.setUninstallBlocked(getWho(), TEST_APP_PKG, false);
        }
        try {
            mContext.unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
        if (mSession != null) {
            mSession.abandon();
        }
        super.tearDown();
    }

    protected static ComponentName getWho() {
        return new ComponentName(PACKAGE_NAME, BasicAdminReceiver.class.getName());
    }

    protected void assertInstallPackage() throws Exception {
        assertFalse(isPackageInstalled(TEST_APP_PKG));
        synchronized (mPackageInstallerTimeoutLock) {
            mCallbackReceived = false;
            mCallbackStatus = PACKAGE_INSTALLER_STATUS_UNDEFINED;
        }
        installPackage(TEST_APP_LOCATION);
        synchronized (mPackageInstallerTimeoutLock) {
            try {
                mPackageInstallerTimeoutLock.wait(PACKAGE_INSTALLER_TIMEOUT_MS);
            } catch (InterruptedException e) {
            }
            assertTrue(mCallbackReceived);
            assertEquals(PackageInstaller.STATUS_SUCCESS, mCallbackStatus);
        }
        assertTrue(isPackageInstalled(TEST_APP_PKG));
    }

    protected void installPackage(String packageLocation) throws Exception {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = mPackageInstaller.createSession(params);
        mSession = mPackageInstaller.openSession(sessionId);

        File file = new File(packageLocation);
        InputStream in = new FileInputStream(file);
        OutputStream out = mSession.openWrite("SilentPackageInstallerTest", 0, file.length());
        byte[] buffer = new byte[65536];
        int c;
        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
        mSession.fsync(out);
        out.close();
        mSession.commit(getCommitCallback(sessionId));
        mSession.close();
    }

    private IntentSender getCommitCallback(int sessionId) {
        // Create an intent-filter and register the receiver
        String action = ACTION_INSTALL_COMMIT + "." + sessionId;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(action);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);

        // Create a PendingIntent and use it to generate the IntentSender
        Intent broadcastIntent = new Intent(action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext,
                sessionId,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent.getIntentSender();
    }

    protected boolean isPackageInstalled(String packageName) {
        try {
            PackageInfo pi = mPackageManager.getPackageInfo(packageName, 0);
            return pi != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
