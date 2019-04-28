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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import android.content.Context;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.finalization.FinalizationController;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.CopyAccountToUserTask;
import com.android.managedprovisioning.task.CreateManagedProfileTask;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;
import com.android.managedprovisioning.task.DisableInstallShortcutListenersTask;
import com.android.managedprovisioning.task.InstallExistingPackageTask;
import com.android.managedprovisioning.task.ManagedProfileSettingsTask;
import com.android.managedprovisioning.task.SetDevicePolicyTask;
import com.android.managedprovisioning.task.StartManagedProfileTask;

/**
 * Controller for Profile Owner provisioning.
 */
// TODO: Consider splitting this controller into one for managed profile and one for user owner
public class ProfileOwnerProvisioningController extends AbstractProvisioningController {
    private final int mParentUserId;

    public ProfileOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        this(context, params, userId, callback, new FinalizationController(context));
    }

    @VisibleForTesting
    ProfileOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback,
            FinalizationController finalizationController) {
        super(context, params, userId, callback, finalizationController);
        mParentUserId = userId;
    }

    protected void setUpTasks() {
        if (ACTION_PROVISION_MANAGED_PROFILE.equals(mParams.provisioningAction)) {
            setUpTasksManagedProfile();
        } else {
            setUpTasksManagedUser();
        }
    }

    private void setUpTasksManagedProfile() {
        addTasks(
                new CreateManagedProfileTask(mContext, mParams, this),
                new InstallExistingPackageTask(mParams.inferDeviceAdminPackageName(), mContext,
                        mParams, this),
                new SetDevicePolicyTask(mContext, mParams, this),
                new ManagedProfileSettingsTask(mContext, mParams, this),
                new DisableInstallShortcutListenersTask(mContext, mParams, this),
                new StartManagedProfileTask(mContext, mParams, this),
                new CopyAccountToUserTask(mParentUserId, mContext, mParams, this));
    }

    private void setUpTasksManagedUser() {
        addTasks(
                new DeleteNonRequiredAppsTask(true /* new profile */, mContext, mParams, this),
                new InstallExistingPackageTask(mParams.inferDeviceAdminPackageName(), mContext,
                        mParams, this),
                new SetDevicePolicyTask(mContext, mParams, this));
    }

    @Override
    public synchronized void onSuccess(AbstractProvisioningTask task) {
        if (task instanceof CreateManagedProfileTask) {
            // If the task was creating a managed profile, store the profile id
            mUserId = ((CreateManagedProfileTask) task).getProfileUserId();
        }
        super.onSuccess(task);
    }

    @Override
    protected void performCleanup() {
        if (ACTION_PROVISION_MANAGED_PROFILE.equals(mParams.provisioningAction)
                && mCurrentTaskIndex != 0) {
            ProvisionLogger.logd("Removing managed profile");
            UserManager um = mContext.getSystemService(UserManager.class);
            um.removeUserEvenWhenDisallowed(mUserId);
        }
    }

    @Override protected int getErrorTitle() {
        return R.string.cant_set_up_profile;
    }

    @Override
    protected int getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        return R.string.managed_provisioning_error_text;
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        return false;
    }
}
