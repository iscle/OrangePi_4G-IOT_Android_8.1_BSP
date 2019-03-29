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
package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link DevicePolicyManager#setApplicationHidden} and
 * {@link DevicePolicyManager#isApplicationHidden} APIs.
 */
public class ApplicationHiddenTest extends BaseDeviceAdminTest {

    private static final String TAG = "ApplicationHiddenTest";

    private static final String PACKAGE_TO_HIDE = "com.android.cts.permissionapp";
    private static final String NONEXISTING_PACKAGE_NAME = "a.b.c.d";

    private static final IntentFilter PACKAGE_INTENT_FILTER;
    static {
        PACKAGE_INTENT_FILTER = new IntentFilter();
        PACKAGE_INTENT_FILTER.addAction(Intent.ACTION_PACKAGE_ADDED);
        PACKAGE_INTENT_FILTER.addAction(Intent.ACTION_PACKAGE_REMOVED);
        PACKAGE_INTENT_FILTER.addDataScheme("package");
    }
    private final ApplicationHiddenReceiver mReceiver = new ApplicationHiddenReceiver();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext.registerReceiver(mReceiver, PACKAGE_INTENT_FILTER);
    }

    @Override
    protected void tearDown() throws Exception {
        mContext.unregisterReceiver(mReceiver);
        mDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT, PACKAGE_TO_HIDE, false);
        super.tearDown();
    }

    public void testSetApplicationHidden() throws Exception {
        assertTrue(mDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                PACKAGE_TO_HIDE, true));
        assertTrue(mDevicePolicyManager.isApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                PACKAGE_TO_HIDE));
        mReceiver.waitForRemovedBroadcast();
        assertTrue(mDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                PACKAGE_TO_HIDE, false));
        assertFalse(mDevicePolicyManager.isApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                PACKAGE_TO_HIDE));
        mReceiver.waitForAddedBroadcast();
    }

    public void testCannotHideActiveAdmin() throws Exception {
        assertFalse(mDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                PACKAGE_NAME, true));
    }

    public void testCannotHideNonExistingPackage() throws Exception {
        assertFalse(mDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                NONEXISTING_PACKAGE_NAME, true));
    }

    private class ApplicationHiddenReceiver extends BroadcastReceiver {
        private final Semaphore mAddedSemaphore = new Semaphore(0);
        private final Semaphore mRemovedSemaphore = new Semaphore(0);

        @Override
        public void onReceive(Context context, Intent intent) {
            Uri uri = intent.getData();
            if (uri == null) {
                return;
            }
            String pkgName = uri.getSchemeSpecificPart();
            if (!PACKAGE_TO_HIDE.equals(pkgName)) {
                return;
            }
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                Log.d(TAG, "Received PACKAGE_ADDED broadcast");
                mAddedSemaphore.release();
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                Log.d(TAG, "Received PACKAGE_REMOVED broadcast");
                mRemovedSemaphore.release();
            }
        }

        public void waitForAddedBroadcast() throws Exception {
            if (!mAddedSemaphore.tryAcquire(60, TimeUnit.SECONDS)) {
                fail("Did not receive PACKAGE_ADDED broadcast.");
            }
        }

        public void waitForRemovedBroadcast() throws Exception {
            if (!mRemovedSemaphore.tryAcquire(60, TimeUnit.SECONDS)) {
                fail("Did not receive PACKAGE_REMOVED broadcast.");
            }
        }
    }
}
