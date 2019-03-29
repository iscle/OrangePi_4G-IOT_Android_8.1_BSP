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

package com.android.cts.content;

import static junit.framework.Assert.assertTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.content.cts.FlakyTestRule;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Tests whether a sync adapter can access accounts.
 */
@RunWith(AndroidJUnit4.class)
public class CtsSyncAccountAccessSameCertTestCases {
    private static final long SYNC_TIMEOUT_MILLIS = 20000; // 20 sec

    @Rule
    public final TestRule mFlakyTestTRule = new FlakyTestRule(3);

    @Before
    public void setUp() throws Exception {
        allowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @After
    public void tearDown() throws Exception {
        disallowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @Test
    public void testAccountAccess_sameCertAsAuthenticatorCanSeeAccount() throws Exception {
        if (!hasDataConnection() || !hasNotificationSupport()) {
            return;
        }

        Intent intent = new Intent(getContext(), StubActivity.class);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        AccountManager accountManager = getContext().getSystemService(AccountManager.class);
        Bundle result = accountManager.addAccount("com.stub", null, null, null, activity,
                null, null).getResult();

        Account addedAccount = new Account(
                result.getString(AccountManager.KEY_ACCOUNT_NAME),
                        result.getString(AccountManager.KEY_ACCOUNT_TYPE));

        waitForSyncManagerAccountChangeUpdate();

        try {
            CountDownLatch latch = new CountDownLatch(1);

            SyncAdapter.setOnPerformSyncDelegate((Account account, Bundle extras,
                    String authority, ContentProviderClient provider, SyncResult syncResult)
                    -> latch.countDown());

            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_PRIORITY, true);
            extras.getBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
            SyncRequest request = new SyncRequest.Builder()
                    .setSyncAdapter(null, "com.android.cts.stub.provider")
                    .syncOnce()
                    .setExtras(extras)
                    .setExpedited(true)
                    .setManual(true)
                    .build();
            ContentResolver.requestSync(request);

            assertTrue(latch.await(SYNC_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            accountManager.removeAccount(addedAccount, activity, null, null);
            activity.finish();
        }
    }

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void waitForSyncManagerAccountChangeUpdate() {
        // Wait for the sync manager to be notified for the new account.
        // Unfortunately, there is no way to detect this event, sigh...
        SystemClock.sleep(SYNC_TIMEOUT_MILLIS);
    }

    private boolean hasDataConnection() {
        ConnectivityManager connectivityManager = getContext().getSystemService(
                ConnectivityManager.class);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private boolean hasNotificationSupport() {
        return !getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private void allowSyncAdapterRunInBackgroundAndDataInBackground() throws IOException {
        // Allow us to run in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist +" + getContext().getPackageName());
        // Allow us to use data in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy add restrict-background-whitelist " + Process.myUid());
    }

    private void disallowSyncAdapterRunInBackgroundAndDataInBackground() throws IOException {
        // Allow us to run in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist -" + getContext().getPackageName());
        // Allow us to use data in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy remove restrict-background-whitelist " + Process.myUid());
    }
}
