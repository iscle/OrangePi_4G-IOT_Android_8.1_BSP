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
 * limitations under the License
 */

package com.android.bluetooth.util;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.util.List;

public final class DevicePolicyUtils {
    private static boolean isBluetoothWorkContactSharingDisabled(Context context) {
        final DevicePolicyManager dpm = (DevicePolicyManager) context
                .getSystemService(Context.DEVICE_POLICY_SERVICE);
        final UserManager userManager = (UserManager) context
                .getSystemService(Context.USER_SERVICE);
        final int myUserId = UserHandle.myUserId();
        final List<UserInfo> userInfoList = userManager.getProfiles(myUserId);

        // Check each user.
        for (UserInfo ui : userInfoList) {
            if (!ui.isManagedProfile()) {
                continue; // Not a managed user.
            }
            return dpm.getBluetoothContactSharingDisabled(new UserHandle(ui.id));
        }
        // No managed profile, so this feature is disabled
        return true;
    }

    // Now we support getBluetoothContactSharingDisabled() for managed profile only
    // TODO: Make primary profile can also support getBluetoothContactSharingDisabled()
    public static Uri getEnterprisePhoneUri(Context context) {
        return isBluetoothWorkContactSharingDisabled(context) ? Phone.CONTENT_URI
                : Phone.ENTERPRISE_CONTENT_URI;
    }
}
