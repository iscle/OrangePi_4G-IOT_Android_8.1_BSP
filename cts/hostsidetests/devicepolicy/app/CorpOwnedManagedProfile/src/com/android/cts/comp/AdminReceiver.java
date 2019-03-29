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

package com.android.cts.comp;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "AdminReceiver";
    // These two apps are built with this source.
    public static final String COMP_DPC_PACKAGE_NAME = "com.android.cts.comp";
    public static final String COMP_DPC_2_PACKAGE_NAME = "com.android.cts.comp2";

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, AdminReceiver.class);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        Log.i(TAG, "onProfileProvisioningComplete");
        // Enabled profile
        getManager(context).setProfileEnabled(getComponentName(context));
        getManager(context).setProfileName(getComponentName(context), "Corp owned Managed Profile");
    }
}
