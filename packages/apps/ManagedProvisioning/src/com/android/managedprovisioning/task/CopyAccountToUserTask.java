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

package com.android.managedprovisioning.task;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_COPY_ACCOUNT_TASK_MS;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.COPY_ACCOUNT_EXCEPTION;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.COPY_ACCOUNT_FAILED;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.COPY_ACCOUNT_SUCCEEDED;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.COPY_ACCOUNT_TIMED_OUT;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This task copies the account in {@link ProvisioningParams#accountToMigrate} from an existing
 * user to the user that is being provisioned.
 *
 * <p>If the account migration fails or times out, we still return success as we consider account
 * migration not to be a critical operation.</p>
 */
public class CopyAccountToUserTask extends AbstractProvisioningTask {
    private static final int ACCOUNT_COPY_TIMEOUT_SECONDS = 60 * 3;  // 3 minutes

    private final int mSourceUserId;
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;

    public CopyAccountToUserTask(
            int sourceUserId,
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback) {
        super(context, provisioningParams, callback);

        mSourceUserId = sourceUserId;
        mProvisioningAnalyticsTracker = ProvisioningAnalyticsTracker.getInstance();
    }

    @Override
    public void run(int userId) {
        startTaskTimer();

        final boolean copySucceeded = maybeCopyAccount(userId);
        // Do not log time if account migration did not succeed, as that isn't useful.
        if (copySucceeded) {
            stopTaskTimer();
        }
        // account migration is not considered a critical operation, so succeed anyway
        success();
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_finishing_touches;
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_COPY_ACCOUNT_TASK_MS;
    }

    @VisibleForTesting
    boolean maybeCopyAccount(int targetUserId) {
        Account accountToMigrate = mProvisioningParams.accountToMigrate;
        UserHandle sourceUser = UserHandle.of(mSourceUserId);
        UserHandle targetUser = UserHandle.of(targetUserId);

        if (accountToMigrate == null) {
            ProvisionLogger.logd("No account to migrate.");
            return false;
        }
        if (sourceUser.equals(targetUser)) {
            ProvisionLogger.loge("sourceUser and targetUser are the same, won't migrate account.");
            return false;
        }
        ProvisionLogger.logd("Attempting to copy account from " + sourceUser + " to " + targetUser);
        try {
            AccountManager accountManager = (AccountManager)
                    mContext.getSystemService(Context.ACCOUNT_SERVICE);
            boolean copySucceeded = accountManager.copyAccountToUser(
                    accountToMigrate,
                    sourceUser,
                    targetUser,
                    /* callback= */ null, /* handler= */ null)
                    .getResult(ACCOUNT_COPY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (copySucceeded) {
                ProvisionLogger.logi("Copied account to " + targetUser);
                mProvisioningAnalyticsTracker.logCopyAccountStatus(mContext,
                        COPY_ACCOUNT_SUCCEEDED);
                return true;
            } else {
                mProvisioningAnalyticsTracker.logCopyAccountStatus(mContext, COPY_ACCOUNT_FAILED);
                ProvisionLogger.loge("Could not copy account to " + targetUser);
            }
        } catch (OperationCanceledException e) {
            mProvisioningAnalyticsTracker.logCopyAccountStatus(mContext, COPY_ACCOUNT_TIMED_OUT);
            ProvisionLogger.loge("Exception copying account to " + targetUser, e);
        } catch (AuthenticatorException | IOException e) {
            mProvisioningAnalyticsTracker.logCopyAccountStatus(mContext, COPY_ACCOUNT_EXCEPTION);
            ProvisionLogger.loge("Exception copying account to " + targetUser, e);
        }
        return false;
    }
}
