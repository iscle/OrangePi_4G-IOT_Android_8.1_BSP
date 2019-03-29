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

package com.android.cts.deviceandprofileowner;

import static android.app.admin.DevicePolicyManager.EXTRA_DELEGATION_SCOPES;
import static android.app.admin.DevicePolicyManager.DELEGATION_APP_RESTRICTIONS;
import static android.app.admin.DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL;
import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_INSTALL;
import static android.app.admin.DevicePolicyManager.DELEGATION_ENABLE_SYSTEM_APP;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.test.MoreAsserts;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Test that an app granted delegation scopes via {@link DevicePolicyManager#setDelegatedScopes} is
 * notified of its new scopes by a broadcast.
 */
public class DelegationTest extends BaseDeviceAdminTest {

    private static final String DELEGATE_PKG = "com.android.cts.delegate";
    private static final String DELEGATE_ACTIVITY_NAME =
            DELEGATE_PKG + ".DelegatedScopesReceiverActivity";
    private static final String TEST_PKG = "com.android.cts.apprestrictions.targetapp";

    // Broadcasts received from the delegate app.
    private static final String ACTION_REPORT_SCOPES = "com.android.cts.delegate.report_scopes";
    private static final String ACTION_RUNNING = "com.android.cts.delegate.running";

    // Semaphores to synchronize communication with delegate app.
    private volatile String[] mReceivedScopes;
    private Semaphore mReceivedScopeReportSemaphore;
    private Semaphore mReceivedRunningSemaphore;

    // Receiver for incoming broadcasts from the delegate app.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_REPORT_SCOPES.equals(intent.getAction())) {
                synchronized (DelegationTest.this) {
                    mReceivedScopes = intent.getStringArrayExtra(EXTRA_DELEGATION_SCOPES);
                    mReceivedScopeReportSemaphore.release();
                }
            } else if (ACTION_RUNNING.equals(intent.getAction())) {
                synchronized (DelegationTest.this) {
                    mReceivedRunningSemaphore.release();
                }
            }
        }
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mReceivedScopeReportSemaphore = new Semaphore(0);
        mReceivedRunningSemaphore = new Semaphore(0);
        mReceivedScopes = null;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REPORT_SCOPES);
        filter.addAction(ACTION_RUNNING);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Override
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(mReceiver);
        super.tearDown();
    }

    public void testDelegateReceivesScopeChangedBroadcast() throws InterruptedException {
        // Prepare the scopes to be delegated.
        final List<String> scopes = Arrays.asList(
                DELEGATION_CERT_INSTALL,
                DELEGATION_APP_RESTRICTIONS,
                DELEGATION_BLOCK_UNINSTALL,
                DELEGATION_ENABLE_SYSTEM_APP);

        // Start delegate so it can receive the scopes changed broadcast from DevicePolicyManager.
        startAndWaitDelegateActivity();

        // Set the delegated scopes.
        mDevicePolicyManager.setDelegatedScopes(ADMIN_RECEIVER_COMPONENT, DELEGATE_PKG, scopes);

        // Wait until the delegate reports its new scopes.
        String reportedScopes[] = waitReportedScopes();

        // Check that the reported scopes correspond to scopes we delegated.
        assertNotNull("Received null scopes from delegate", reportedScopes);
        MoreAsserts.assertContentsInAnyOrder("Delegated scopes do not match broadcasted scopes",
                scopes, reportedScopes);
    }

    public void testCantDelegateToUninstalledPackage() {
        // Prepare the package name and scopes to be delegated.
        final String NON_EXISTENT_PKG = "com.android.nonexistent.delegate";
        final List<String> scopes = Arrays.asList(
                DELEGATION_CERT_INSTALL,
                DELEGATION_ENABLE_SYSTEM_APP);
        try {
            // Trying to delegate to non existent package should throw.
            mDevicePolicyManager.setDelegatedScopes(ADMIN_RECEIVER_COMPONENT,
                    NON_EXISTENT_PKG, scopes);
            fail("Should throw when delegating to non existent package");
        } catch(IllegalArgumentException expected) {
        }
        // Assert no scopes were delegated.
        assertTrue("Delegation scopes granted to non existent package", mDevicePolicyManager
                .getDelegatedScopes(ADMIN_RECEIVER_COMPONENT, NON_EXISTENT_PKG).isEmpty());
    }

    public void testCanRetrieveDelegates() {
        final List<String> someScopes = Arrays.asList(
                DELEGATION_APP_RESTRICTIONS,
                DELEGATION_ENABLE_SYSTEM_APP);
        final List<String> otherScopes = Arrays.asList(
                DELEGATION_BLOCK_UNINSTALL,
                DELEGATION_ENABLE_SYSTEM_APP);

        // In the beginning there are no delegates.
        assertTrue("No delegates should be found", getDelegatePackages(DELEGATION_APP_RESTRICTIONS)
                .isEmpty());
        assertTrue("No delegates should be found", getDelegatePackages(DELEGATION_BLOCK_UNINSTALL)
                .isEmpty());
        assertTrue("No delegates should be found", getDelegatePackages(DELEGATION_ENABLE_SYSTEM_APP)
                .isEmpty());

        // After delegating scopes to two packages.
        mDevicePolicyManager.setDelegatedScopes(ADMIN_RECEIVER_COMPONENT,
                DELEGATE_PKG, someScopes);
        mDevicePolicyManager.setDelegatedScopes(ADMIN_RECEIVER_COMPONENT,
                TEST_PKG, otherScopes);

        // The expected delegates are returned.
        assertTrue("Expected delegate not found", getDelegatePackages(DELEGATION_APP_RESTRICTIONS)
                .contains(DELEGATE_PKG));
        assertTrue("Expected delegate not found", getDelegatePackages(DELEGATION_BLOCK_UNINSTALL)
                .contains(TEST_PKG));
        assertTrue("Expected delegate not found", getDelegatePackages(DELEGATION_ENABLE_SYSTEM_APP)
                .contains(DELEGATE_PKG));
        assertTrue("Expected delegate not found", getDelegatePackages(DELEGATION_ENABLE_SYSTEM_APP)
                .contains(TEST_PKG));

        // Packages are only returned in their recpective scopes.
        assertFalse("Unexpected delegate package", getDelegatePackages(DELEGATION_APP_RESTRICTIONS)
                .contains(TEST_PKG));
        assertFalse("Unexpected delegate package", getDelegatePackages(DELEGATION_BLOCK_UNINSTALL)
                .contains(DELEGATE_PKG));
        assertFalse("Unexpected delegate package", getDelegatePackages(DELEGATION_CERT_INSTALL)
                .contains(DELEGATE_PKG));
        assertFalse("Unexpected delegate package", getDelegatePackages(DELEGATION_CERT_INSTALL)
                .contains(TEST_PKG));
    }

    private List<String> getDelegatePackages(String scope) {
        return mDevicePolicyManager.getDelegatePackages(ADMIN_RECEIVER_COMPONENT, scope);
    }

    private void startAndWaitDelegateActivity() throws InterruptedException {
        mContext.startActivity(new Intent()
                .setComponent(new ComponentName(DELEGATE_PKG, DELEGATE_ACTIVITY_NAME))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        assertTrue("DelegateApp did not start in time.",
                mReceivedRunningSemaphore.tryAcquire(10, TimeUnit.SECONDS));
    }

    private String[] waitReportedScopes() throws InterruptedException {
        assertTrue("DelegateApp did not report scope in time.",
                mReceivedScopeReportSemaphore.tryAcquire(10, TimeUnit.SECONDS));
        return mReceivedScopes;
    }
}
