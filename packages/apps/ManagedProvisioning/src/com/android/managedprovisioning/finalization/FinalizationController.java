/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.finalization;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.io.File;

/**
 * Controller for the finalization of managed provisioning.
 *
 * <p>This controller is invoked when the active provisioning is completed via
 * {@link #provisioningInitiallyDone(ProvisioningParams)}. In the case of provisioning during SUW,
 * it is invoked again when provisioning is finalized via {@link #provisioningFinalized()}.</p>
 */
public class FinalizationController {
    private static final String PROVISIONING_PARAMS_FILE_NAME =
            "finalization_activity_provisioning_params.xml";

    private final Context mContext;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final UserProvisioningStateHelper mHelper;

    public FinalizationController(Context context) {
        this(
                context,
                new Utils(),
                new SettingsFacade(),
                new UserProvisioningStateHelper(context));
    }

    @VisibleForTesting
    FinalizationController(Context context,
            Utils utils,
            SettingsFacade settingsFacade,
            UserProvisioningStateHelper helper) {
        mContext = checkNotNull(context);
        mUtils = checkNotNull(utils);
        mSettingsFacade = checkNotNull(settingsFacade);
        mHelper = checkNotNull(helper);
    }

    /**
     * This method is invoked when the provisioning process is done.
     *
     * <p>If provisioning happens as part of SUW, we rely on {@link #provisioningFinalized()} to be
     * called at the end of SUW. Otherwise, this method will finalize provisioning. If called after
     * SUW, this method notifies the DPC about the completed provisioning; otherwise, it stores the
     * provisioning params for later digestion.</p>
     *
     * @param params the provisioning params
     */
    public void provisioningInitiallyDone(ProvisioningParams params) {
        if (!mHelper.isStateUnmanagedOrFinalized()) {
            // In any other state than STATE_USER_UNMANAGED and STATE_USER_SETUP_FINALIZED, we've
            // already run this method, so don't do anything.
            // STATE_USER_SETUP_FINALIZED can occur here if a managed profile is provisioned on a
            // device owner device.
            ProvisionLogger.logw("provisioningInitiallyDone called, but state is not finalized or "
                    + "unmanaged");
            return;
        }

        mHelper.markUserProvisioningStateInitiallyDone(params);
        if (ACTION_PROVISION_MANAGED_PROFILE.equals(params.provisioningAction)
                && mSettingsFacade.isUserSetupCompleted(mContext)) {
            // If a managed profile was provisioned after SUW, notify the DPC straight away
            notifyDpcManagedProfile(params);
        } else {
            // Otherwise store the information and wait for provisioningFinalized to be called
            storeProvisioningParams(params);
        }
    }

    /**
     * This method is invoked when provisioning is finalized.
     *
     * <p>This method has to be invoked after {@link #provisioningInitiallyDone(ProvisioningParams)}
     * was called. It is commonly invoked at the end of SUW if provisioning occurs during SUW. It
     * loads the provisioning params from the storage, notifies the DPC about the completed
     * provisioning and sets the right user provisioning states.</p>
     */
    void provisioningFinalized() {
        if (mHelper.isStateUnmanagedOrFinalized()) {
            ProvisionLogger.logw("provisioningInitiallyDone called, but state is finalized or "
                    + "unmanaged");
            return;
        }

        final ProvisioningParams params = loadProvisioningParamsAndClearFile();
        if (params == null) {
            ProvisionLogger.logw("FinalizationController invoked, but no stored params");
            return;
        }

        if (params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
            notifyDpcManagedProfile(params);
        } else {
            // For managed user and device owner, we send the provisioning complete intent and maybe
            // launch the DPC.
            Intent provisioningCompleteIntent = createProvisioningCompleteIntent(params);
            if (provisioningCompleteIntent == null) {
                return;
            }
            mContext.sendBroadcast(provisioningCompleteIntent);

            maybeLaunchDpc(params, UserHandle.myUserId());
        }

        mHelper.markUserProvisioningStateFinalized(params);
    }

    /**
     * Notify the DPC on the managed profile that provisioning has completed. When the DPC has
     * received the intent, send notify the primary instance that the profile is ready.
     */
    private void notifyDpcManagedProfile(ProvisioningParams params) {
        UserHandle managedUserHandle = mUtils.getManagedProfile(mContext);

        // Use an ordered broadcast, so that we only finish when the DPC has received it.
        // Avoids a lag in the transition between provisioning and the DPC.
        BroadcastReceiver dpcReceivedSuccessReceiver =
                new DpcReceivedSuccessReceiver(params.accountToMigrate,
                        params.keepAccountMigrated, managedUserHandle,
                        params.deviceAdminComponentName.getPackageName());
        Intent completeIntent = createProvisioningCompleteIntent(params);

        mContext.sendOrderedBroadcastAsUser(completeIntent, managedUserHandle, null,
                dpcReceivedSuccessReceiver, null, Activity.RESULT_OK, null, null);
        ProvisionLogger.logd("Provisioning complete broadcast has been sent to user "
                + managedUserHandle.getIdentifier());

        maybeLaunchDpc(params, managedUserHandle.getIdentifier());
    }

    private void maybeLaunchDpc(ProvisioningParams params, int userId) {
        final Intent dpcLaunchIntent = createDpcLaunchIntent(params);
        if (mUtils.canResolveIntentAsUser(mContext, dpcLaunchIntent, userId)) {
            mContext.startActivityAsUser(dpcLaunchIntent, UserHandle.of(userId));
            ProvisionLogger.logd("Dpc was launched for user: " + userId);
        }
    }

    private Intent createProvisioningCompleteIntent(@NonNull ProvisioningParams params) {
        Intent intent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        try {
            intent.setComponent(mUtils.findDeviceAdmin(
                    params.deviceAdminPackageName,
                    params.deviceAdminComponentName, mContext));
        } catch (IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failed to infer the device admin component name", e);
            return null;
        }
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
        addExtrasToIntent(intent, params);
        return intent;
    }

    private Intent createDpcLaunchIntent(@NonNull ProvisioningParams params) {
        Intent intent = new Intent(ACTION_PROVISIONING_SUCCESSFUL);
        final String packageName = params.inferDeviceAdminPackageName();
        if (packageName == null) {
            ProvisionLogger.loge("Device admin package name is null");
            return null;
        }
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        addExtrasToIntent(intent, params);
        return intent;
    }

    private void addExtrasToIntent(Intent intent, ProvisioningParams params) {
        intent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, params.adminExtrasBundle);
    }

    private void storeProvisioningParams(ProvisioningParams params) {
        params.save(getProvisioningParamsFile());
    }

    private File getProvisioningParamsFile() {
        return new File(mContext.getFilesDir(), PROVISIONING_PARAMS_FILE_NAME);
    }

    @VisibleForTesting
    ProvisioningParams loadProvisioningParamsAndClearFile() {
        File file = getProvisioningParamsFile();
        ProvisioningParams result = ProvisioningParams.load(file);
        file.delete();
        return result;
    }
}
