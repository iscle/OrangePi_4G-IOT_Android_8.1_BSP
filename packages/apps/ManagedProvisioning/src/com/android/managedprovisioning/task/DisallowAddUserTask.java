/*
 * Copyright 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Disables user addition for all users on the device.
 */
public class DisallowAddUserTask extends AbstractProvisioningTask {
    private final boolean mIsSplitSystemUser;
    private final UserManager mUserManager;

    public DisallowAddUserTask(
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(UserManager.isSplitSystemUser(), context, params, callback);
    }

    @VisibleForTesting
    DisallowAddUserTask(boolean splitSystemUser,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);
        mIsSplitSystemUser = splitSystemUser;
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    @Override
    public void run(int userId) {

        if (mIsSplitSystemUser && (userId == UserHandle.USER_SYSTEM)) {
            ProvisionLogger.logi("Not setting DISALLOW_ADD_USER as system device-owner detected.");
            success();
            return;
        }

        for (UserInfo userInfo : mUserManager.getUsers()) {
            UserHandle userHandle = userInfo.getUserHandle();
            if (!mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER, userHandle)) {
                mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_USER, true, userHandle);
                ProvisionLogger.logi("DISALLOW_ADD_USER restriction set on user: " + userInfo.id);
            }
        }
        success();
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_finishing_touches;
    }
}
