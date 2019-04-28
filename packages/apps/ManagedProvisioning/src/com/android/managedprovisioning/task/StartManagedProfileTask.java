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

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_START_PROFILE_TASK_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This task starts the managed profile and waits for it to be unlocked.
 */
public class StartManagedProfileTask extends AbstractProvisioningTask {
    // Maximum time we will wait for ACTION_USER_UNLOCK until we give up
    private static final int USER_UNLOCKED_TIMEOUT_SECONDS = 120; // 2 minutes
    @VisibleForTesting
    static final IntentFilter UNLOCK_FILTER = new IntentFilter(Intent.ACTION_USER_UNLOCKED);

    private final IActivityManager mIActivityManager;

    public StartManagedProfileTask(Context context, ProvisioningParams params, Callback callback) {
        this(ActivityManager.getService(), context, params, callback);
    }

    @VisibleForTesting
    StartManagedProfileTask(
            IActivityManager iActivityManager,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);

        mIActivityManager = checkNotNull(iActivityManager);
    }

    @Override
    public void run(int userId) {
        startTaskTimer();
        UserUnlockedReceiver unlockedReceiver = new UserUnlockedReceiver(userId);
        mContext.registerReceiverAsUser(unlockedReceiver, new UserHandle(userId), UNLOCK_FILTER,
                null, null);
        try {
            if (!mIActivityManager.startUserInBackground(userId)) {
                ProvisionLogger.loge("Unable to start user in background: " + userId);
                error(0);
                return;
            }

            if (!unlockedReceiver.waitForUserUnlocked()) {
                ProvisionLogger.loge("Timeout whilst waiting for unlock of user: " + userId);
                error(0);
                return;
            }
        } catch (RemoteException e) {
            ProvisionLogger.loge("Exception when starting user in background: " + userId, e);
            error(0);
            return;
        } finally {
            mContext.unregisterReceiver(unlockedReceiver);
        }
        stopTaskTimer();
        success();
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_finishing_touches;
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_START_PROFILE_TASK_MS;
    }

    /**
     * BroadcastReceiver that listens to {@link Intent#ACTION_USER_UNLOCKED} in order to provide
     * a blocking wait until the managed profile has been started and unlocked.
     */
    @VisibleForTesting
    static class UserUnlockedReceiver extends BroadcastReceiver {
        private final Semaphore semaphore = new Semaphore(0);
        private final int mUserId;

        UserUnlockedReceiver(int userId) {
            mUserId = userId;
        }

        @Override
        public void onReceive(Context context, Intent intent ) {
            if (!Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                ProvisionLogger.logw("Unexpected intent: " + intent);
                return;
            }
            if (intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL) == mUserId) {
                ProvisionLogger.logd("Received ACTION_USER_UNLOCKED for user " + mUserId);
                semaphore.release();
            }
        }

        public boolean waitForUserUnlocked() {
            ProvisionLogger.logd("Waiting for ACTION_USER_UNLOCKED");
            try {
                return semaphore.tryAcquire(USER_UNLOCKED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                return false;
            }
        }
    }
}
