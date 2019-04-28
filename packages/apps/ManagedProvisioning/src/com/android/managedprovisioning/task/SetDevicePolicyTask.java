/*
 * Copyright 2014, The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * This tasks sets a given component as the device or profile owner. It also enables the management
 * app if it's not currently enabled and sets the component as active admin.
 */
public class SetDevicePolicyTask extends AbstractProvisioningTask {

    private final PackageManager mPackageManager;
    private final DevicePolicyManager mDevicePolicyManager;
    private final Utils mUtils;

    public SetDevicePolicyTask(
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(new Utils(), context, params, callback);
    }

    @VisibleForTesting
    SetDevicePolicyTask(Utils utils,
                        Context context,
                        ProvisioningParams params,
                        Callback callback) {
        super(context, params, callback);

        mUtils = checkNotNull(utils);
        mPackageManager = mContext.getPackageManager();
        mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_set_owner;
    }

    @Override
    public void run(int userId) {
        boolean success;
        try {
            ComponentName adminComponent = mUtils.findDeviceAdmin(
                    mProvisioningParams.deviceAdminPackageName,
                    mProvisioningParams.deviceAdminComponentName, mContext);
            String adminPackage = adminComponent.getPackageName();

            enableDevicePolicyApp(adminPackage);
            setActiveAdmin(adminComponent, userId);
            if (mUtils.isProfileOwnerAction(mProvisioningParams.provisioningAction)) {
                success = setProfileOwner(adminComponent, userId);
            } else {
                success = setDeviceOwner(adminComponent,
                        mContext.getResources().getString(R.string.default_owned_device_username),
                        userId);
            }
        } catch (Exception e) {
            ProvisionLogger.loge("Failure setting device or profile owner", e);
            error(0);
            return;
        }

        if (success) {
            success();
        } else {
            ProvisionLogger.loge("Error when setting device or profile owner.");
            error(0);
        }
    }

    private void enableDevicePolicyApp(String packageName) {
        int enabledSetting = mPackageManager.getApplicationEnabledSetting(packageName);
        if (enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                && enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            mPackageManager.setApplicationEnabledSetting(packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    // Device policy app may have launched ManagedProvisioning, play nice and don't
                    // kill it as a side-effect of this call.
                    PackageManager.DONT_KILL_APP);
        }
    }

    private void setActiveAdmin(ComponentName component, int userId) {
        ProvisionLogger.logd("Setting " + component + " as active admin.");
        mDevicePolicyManager.setActiveAdmin(component, true, userId);
    }

    private boolean setDeviceOwner(ComponentName component, String owner, int userId) {
        ProvisionLogger.logd("Setting " + component + " as device owner of user " + userId);
        if (!component.equals(mDevicePolicyManager.getDeviceOwnerComponentOnCallingUser())) {
            return mDevicePolicyManager.setDeviceOwner(component, owner, userId);
        }
        return true;
    }

    private boolean setProfileOwner(ComponentName component, int userId) {
        ProvisionLogger.logd("Setting " + component + " as profile owner of user " + userId);
        if (!component.equals(mDevicePolicyManager.getProfileOwnerAsUser(userId))) {
            return mDevicePolicyManager.setProfileOwner(component, component.getPackageName(),
                    userId);
        }
        return true;
    }
}
