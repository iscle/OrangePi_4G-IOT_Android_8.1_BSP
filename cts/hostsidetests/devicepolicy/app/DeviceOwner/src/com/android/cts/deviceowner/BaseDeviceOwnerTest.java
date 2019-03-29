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

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.support.v4.content.LocalBroadcastManager;
import android.test.AndroidTestCase;

/**
 * Base class for device-owner based tests.
 *
 * This class handles making sure that the test is the device owner
 * and that it has an active admin registered, so that all tests may
 * assume these are done. The admin component can be accessed through
 * {@link #getWho()}.
 */
public abstract class BaseDeviceOwnerTest extends AndroidTestCase {

    final static String ACTION_USER_ADDED = "com.android.cts.deviceowner.action.USER_ADDED";
    final static String ACTION_USER_REMOVED = "com.android.cts.deviceowner.action.USER_REMOVED";
    final static String EXTRA_USER_HANDLE = "com.android.cts.deviceowner.extra.USER_HANDLE";
    final static String ACTION_NETWORK_LOGS_AVAILABLE =
            "com.android.cts.deviceowner.action.ACTION_NETWORK_LOGS_AVAILABLE";
    final static String EXTRA_NETWORK_LOGS_BATCH_TOKEN =
            "com.android.cts.deviceowner.extra.NETWORK_LOGS_BATCH_TOKEN";

    public static class BasicAdminReceiver extends DeviceAdminReceiver {

        public static ComponentName getComponentName(Context context) {
            return new ComponentName(context, BasicAdminReceiver.class);
        }

        @Override
        public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
                String suggestedAlias) {
            if (uid != Process.myUid() || uri == null) {
                return null;
            }
            return uri.getQueryParameter("alias");
        }

        @Override
        public void onUserAdded(Context context, Intent intent, UserHandle userHandle) {
            sendUserAddedOrRemovedBroadcast(context, ACTION_USER_ADDED, userHandle);
        }

        @Override
        public void onUserRemoved(Context context, Intent intent, UserHandle userHandle) {
            sendUserAddedOrRemovedBroadcast(context, ACTION_USER_REMOVED, userHandle);
        }

        @Override
        public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
                int networkLogsCount) {
            // send the broadcast, the rest of the test happens in NetworkLoggingTest
            Intent batchIntent = new Intent(ACTION_NETWORK_LOGS_AVAILABLE);
            batchIntent.putExtra(EXTRA_NETWORK_LOGS_BATCH_TOKEN, batchToken);
            LocalBroadcastManager.getInstance(context).sendBroadcast(batchIntent);
        }

        private void sendUserAddedOrRemovedBroadcast(Context context, String action,
                UserHandle userHandle) {
            Intent intent = new Intent(action);
            intent.putExtra(EXTRA_USER_HANDLE, userHandle);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    public static final String PACKAGE_NAME = BaseDeviceOwnerTest.class.getPackage().getName();

    protected DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        assertDeviceOwner(mDevicePolicyManager);
    }

    static void assertDeviceOwner(DevicePolicyManager dpm) {
        assertNotNull(dpm);
        assertTrue(dpm.isAdminActive(getWho()));
        assertTrue(dpm.isDeviceOwnerApp(PACKAGE_NAME));
        assertFalse(dpm.isManagedProfile(getWho()));
    }

    protected static ComponentName getWho() {
        return new ComponentName(PACKAGE_NAME, BasicAdminReceiver.class.getName());
    }
}
