/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.task;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Task to install an existing package on a given user.
 */
public class InstallExistingPackageTask extends AbstractProvisioningTask {

    private final String mPackageName;

    public InstallExistingPackageTask(
            String packageName,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);

        mPackageName = checkNotNull(packageName);
    }

    public int getStatusMsgId() {
        return R.string.progress_install;
    }

    @Override
    public void run(int userId) {
        PackageManager pm = mContext.getPackageManager();
        try {
            int status = pm.installExistingPackageAsUser(mPackageName, userId);
            if (status == PackageManager.INSTALL_SUCCEEDED) {
                success();
            } else {
                ProvisionLogger.loge("Install failed, result code = " + status);
                error(0);
            }
        } catch (PackageManager.NameNotFoundException e) {
            error(0);
        }

    }
}
