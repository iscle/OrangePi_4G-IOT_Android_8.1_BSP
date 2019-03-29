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
package com.android.cts.comp;

import static junit.framework.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import java.util.List;

/**
 * Test class called when binding to a service across users should not work for some reason.
 */
public class BindDeviceAdminServiceFailsTest extends AndroidTestCase {

    private DevicePolicyManager mDpm;

    private static final ServiceConnection EMPTY_SERVICE_CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    @Override
    public void setUp() {
        mDpm = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public void testNoBindDeviceAdminTargetUsers() {
        List<UserHandle> allowedTargetUsers = mDpm.getBindDeviceAdminTargetUsers(
                AdminReceiver.getComponentName(mContext));
        assertEquals(0, allowedTargetUsers.size());
    }

    public void testCannotBind() throws Exception {
        UserHandle otherProfile = Utils.getOtherProfile(mContext);
        if (otherProfile != null) {
            checkCannotBind(AdminReceiver.COMP_DPC_PACKAGE_NAME, otherProfile);
            checkCannotBind(AdminReceiver.COMP_DPC_2_PACKAGE_NAME, otherProfile);
        }
    }

    private void checkCannotBind(String targetPackageName, UserHandle otherProfile) {
        try {
            final Intent serviceIntent = new Intent();
            serviceIntent.setClassName(targetPackageName, ProtectedCrossUserService.class.getName());
            bind(serviceIntent, EMPTY_SERVICE_CONNECTION, otherProfile);
            fail("SecurityException should be thrown");
        } catch (SecurityException ex) {
            MoreAsserts.assertContainsRegex(
                    "Not allowed to bind to target user id", ex.getMessage());
        }
    }

    private boolean bind(Intent serviceIntent, ServiceConnection serviceConnection,
            UserHandle userHandle) {
        return mDpm.bindDeviceAdminServiceAsUser(AdminReceiver.getComponentName(mContext),
                serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE, userHandle);
    }
}
