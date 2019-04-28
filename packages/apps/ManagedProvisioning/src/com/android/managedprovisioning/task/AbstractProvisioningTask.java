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

package com.android.managedprovisioning.task;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.VIEW_UNKNOWN;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;

import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Base class for all provisioning tasks.
 */
public abstract class AbstractProvisioningTask {
    protected final Context mContext;
    protected final ProvisioningParams mProvisioningParams;
    private final Callback mCallback;
    private TimeLogger mTimeLogger;

    /**
     * Constructor for a provisioning task
     *
     * @param context {@link Context} object.
     * @param provisioningParams {@link ProvisioningParams} object for this provisioning process.
     * @param callback {@link Callback} object to return task results.
     */
    AbstractProvisioningTask(
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback) {
        mContext = checkNotNull(context);
        mProvisioningParams = provisioningParams;
        mCallback = checkNotNull(callback);

        mTimeLogger = new TimeLogger(context, getMetricsCategory());
    }

    protected final void success() {
        mCallback.onSuccess(this);
    }

    protected final void error(int resultCode) {
        mCallback.onError(this, resultCode);
    }

    protected void startTaskTimer() {
        mTimeLogger.start();
    }

    protected void stopTaskTimer() {
        mTimeLogger.stop();
    }

    protected int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    /**
     * Run the task.
     *
     * @param userId the id of the user the action should be performed on.
     */
    public abstract void run(int userId);

    /**
     * @return the resource id of the status message related to the task.
     */
    public abstract int getStatusMsgId();

    /**
     * Callback class for provisioning tasks.
     *
     * <p>Every execution of run should result in exactly one of
     * {@link Callback#onSuccess(AbstractProvisioningTask)} and
     * {@link Callback#onError(AbstractProvisioningTask, int)} to be called.</p>
     */
    public interface Callback {

        /**
         * Callback indicating that the task has finished successfully.
         *
         * @param task the task that finished executing.
         */
        void onSuccess(AbstractProvisioningTask task);

        /**
         * Callback indicating that the task has encountered an error.
         *
         * @param task the task that finished executing.
         * @param errorCode a error code indicating the type of error that happened.
         */
        void onError(AbstractProvisioningTask task, int errorCode);
    }
}
