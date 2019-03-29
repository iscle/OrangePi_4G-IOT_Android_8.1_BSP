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

import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;


public class Utils {

    private static final String TAG = "BindDeviceAdminTest";

    /**
     * Returns the user handle of the other profile of the same user, or {@code null} if there
     * are no other profiles or there are more than one.
     */
    static @Nullable
    UserHandle getOtherProfile(Context context) {
        List<UserHandle> otherProfiles = context.getSystemService(UserManager.class)
                .getUserProfiles();
        otherProfiles.remove(Process.myUserHandle());
        if (otherProfiles.size() == 1) {
            return otherProfiles.get(0);
        }
        Log.d(TAG, "There are " + otherProfiles.size() + " other profiles for user "
                + Process.myUserHandle() + ": " + otherProfiles);
        return null;
    }
}
