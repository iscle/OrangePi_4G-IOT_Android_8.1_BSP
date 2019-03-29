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
package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import junit.framework.Assert;

public class Utils {
    private Utils() {
    }

    public static void removeActiveAdmin(Context context, ComponentName cn) throws Exception {
        final DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        if (dpm.isAdminActive(cn)) {
            dpm.removeActiveAdmin(cn);
            assertNotActiveAdmin(context, cn);
        }
    }

    public static void assertNotActiveAdmin(Context context, ComponentName cn) throws Exception {
        final DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);

        for (int i = 0; i < 1000 && dpm.isAdminActive(cn); i++) {
            Thread.sleep(100);
        }
        Assert.assertFalse(dpm.isAdminActive(cn));
    }
}
