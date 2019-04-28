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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.STATE_USER_PROFILE_COMPLETE;
import static android.app.admin.DevicePolicyManager.STATE_USER_SETUP_COMPLETE;
import static android.app.admin.DevicePolicyManager.STATE_USER_SETUP_FINALIZED;
import static android.app.admin.DevicePolicyManager.STATE_USER_SETUP_INCOMPLETE;
import static android.app.admin.DevicePolicyManager.STATE_USER_UNMANAGED;
import static android.content.Context.DEVICE_POLICY_SERVICE;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Helper class to handle the user provisioning states.
 *
 * <p>This class calls interacts with {@link DevicePolicyManager} to handle the setting of
 * user provisioning states at the end of provisioning.</p>
 */
@VisibleForTesting
public class UserProvisioningStateHelper {
    private final Context mContext;
    private final DevicePolicyManager mDevicePolicyManager;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final int mMyUserId;

    UserProvisioningStateHelper(Context context) {
        this(context, new Utils(), new SettingsFacade(), UserHandle.myUserId());
    }

    @VisibleForTesting
    UserProvisioningStateHelper(Context context,
            Utils utils,
            SettingsFacade settingsFacade,
            int myUserId) {
        mContext = checkNotNull(context);
        mDevicePolicyManager = (DevicePolicyManager) mContext.getSystemService(
                DEVICE_POLICY_SERVICE);
        mUtils = checkNotNull(utils);
        mSettingsFacade = checkNotNull(settingsFacade);
        mMyUserId = myUserId;
    }

    /**
     * Set the current users userProvisioningState depending on the following factors:
     * <ul>
     *     <li>We're setting up a managed-profile - need to set state on two users.</li>
     *     <li>User-setup has previously been completed or not - skip states relating to
     *     communicating with setup-wizard.</li>
     *     <li>DPC requested we skip the rest of setup-wizard.</li>
     * </ul>
     *
     * @param params configuration for current provisioning attempt
     */
    @VisibleForTesting
    public void markUserProvisioningStateInitiallyDone(ProvisioningParams params) {
        final boolean userSetupCompleted = mSettingsFacade.isUserSetupCompleted(mContext);

        int managedProfileUserId = UserHandle.USER_NULL;

        // new provisioning state for current user, if non-null
        Integer newState = null;
        // New provisioning state for managed-profile of current user, if non-null.
        Integer newProfileState = null;

        if (params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
            // Managed profiles are a special case as two users are involved.
            managedProfileUserId = mUtils.getManagedProfile(mContext).getIdentifier();
            if (userSetupCompleted) {
                // SUW on current user is complete, so nothing much to do beyond indicating we're
                // all done.
                newProfileState = STATE_USER_SETUP_FINALIZED;
            } else {
                // We're still in SUW, so indicate that a managed-profile was setup on current user,
                // and that we're awaiting finalization on both.
                newState = STATE_USER_PROFILE_COMPLETE;
                newProfileState = STATE_USER_SETUP_COMPLETE;
            }
        } else if (userSetupCompleted) {
            // User setup was previously completed this is an unexpected case.
            ProvisionLogger.logw("user_setup_complete set, but provisioning was started");
        } else if (params.skipUserSetup) {
            // DPC requested setup-wizard is skipped, indicate this to SUW.
            newState = STATE_USER_SETUP_COMPLETE;
        } else {
            // DPC requested setup-wizard is not skipped, indicate this to SUW.
            newState = STATE_USER_SETUP_INCOMPLETE;
        }

        if (newState != null) {
            setUserProvisioningState(newState, mMyUserId);
        }
        if (newProfileState != null) {
            setUserProvisioningState(newProfileState, managedProfileUserId);
        }
    }

    /**
     * Finalize the current users userProvisioningState depending on the following factors:
     * <ul>
     *     <li>We're setting up a managed-profile - need to set state on two users.</li>
     * </ul>
     *
     * @param params configuration for current provisioning attempt - if null (because
     *               ManagedProvisioning wasn't used for first phase of provisioning) aassumes we
     *               can just mark current user as being in finalized provisioning state
     */
    @VisibleForTesting
    public void markUserProvisioningStateFinalized(ProvisioningParams params) {

        if (params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
            // Managed profiles are a special case as two users are involved.
            final int managedProfileUserId = mUtils.getManagedProfile(mContext).getIdentifier();
            setUserProvisioningState(STATE_USER_SETUP_FINALIZED, managedProfileUserId);
            setUserProvisioningState(STATE_USER_UNMANAGED, mMyUserId);
        } else {
            setUserProvisioningState(STATE_USER_SETUP_FINALIZED, mMyUserId);
        }
    }

    @VisibleForTesting
    public boolean isStateUnmanagedOrFinalized() {
        final int currentState = mDevicePolicyManager.getUserProvisioningState();
        return currentState == STATE_USER_UNMANAGED || currentState == STATE_USER_SETUP_FINALIZED;
    }

    private void setUserProvisioningState(int state, int userId) {
        ProvisionLogger.logi("Setting userProvisioningState for user " + userId + " to: " + state);
        mDevicePolicyManager.setUserProvisioningState(state, userId);
    }
}
