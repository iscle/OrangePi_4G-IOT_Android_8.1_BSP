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

package com.android.managedprovisioning.provisioning;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TOTAL_TASK_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.CANCELLED_DURING_PROVISIONING;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton instance that provides communications between the ongoing provisioning process and the
 * UI layer.
 */
public class ProvisioningManager implements ProvisioningControllerCallback {
    private static ProvisioningManager sInstance;

    private static final Intent SERVICE_INTENT = new Intent().setComponent(new ComponentName(
            Globals.MANAGED_PROVISIONING_PACKAGE_NAME,
            ProvisioningService.class.getName()));

    private static final int CALLBACK_NONE = 0;
    private static final int CALLBACK_ERROR = 1;
    private static final int CALLBACK_PROGRESS = 2;
    private static final int CALLBACK_PRE_FINALIZED = 3;

    private final Context mContext;
    private final ProvisioningControllerFactory mFactory;
    private final Handler mUiHandler;

    @GuardedBy("this")
    private AbstractProvisioningController mController;
    @GuardedBy("this")
    private List<ProvisioningManagerCallback> mCallbacks = new ArrayList<>();

    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final TimeLogger mTimeLogger;
    private int mLastCallback = CALLBACK_NONE;
    private Pair<Pair<Integer, Integer>, Boolean> mLastError; // TODO: refactor
    private int mLastProgressMsgId;
    private HandlerThread mHandlerThread;

    public static ProvisioningManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ProvisioningManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private ProvisioningManager(Context context) {
        this(
                context,
                new Handler(Looper.getMainLooper()),
                new ProvisioningControllerFactory(),
                ProvisioningAnalyticsTracker.getInstance(),
                new TimeLogger(context, PROVISIONING_TOTAL_TASK_TIME_MS));
    }

    @VisibleForTesting
    ProvisioningManager(
            Context context,
            Handler uiHandler,
            ProvisioningControllerFactory factory,
            ProvisioningAnalyticsTracker analyticsTracker,
            TimeLogger timeLogger) {
        mContext = checkNotNull(context);
        mUiHandler = checkNotNull(uiHandler);
        mFactory = checkNotNull(factory);
        mProvisioningAnalyticsTracker = checkNotNull(analyticsTracker);
        mTimeLogger = checkNotNull(timeLogger);
    }

    /**
     * Initiate a new provisioning process, unless one is already ongoing.
     *
     * @param params {@link ProvisioningParams} associated with the new provisioning process.
     */
    public void maybeStartProvisioning(final ProvisioningParams params) {
        synchronized (this) {
            if (mController == null) {
                mTimeLogger.start();
                startNewProvisioningLocked(params);
                mProvisioningAnalyticsTracker.logProvisioningStarted(mContext, params);
            } else {
                ProvisionLogger.loge("Trying to start provisioning, but it's already running");
            }
        }
   }

    private void startNewProvisioningLocked(final ProvisioningParams params) {
        ProvisionLogger.logd("Initializing provisioning process");
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("Provisioning Worker");
            mHandlerThread.start();
            mContext.startService(SERVICE_INTENT);
        }
        mLastCallback = CALLBACK_NONE;
        mLastError = null;
        mLastProgressMsgId = 0;

        mController = mFactory.createProvisioningController(mContext, params, this);
        mController.start(mHandlerThread.getLooper());
    }

    /**
     * Cancel the provisioning progress.
     */
    public void cancelProvisioning() {
        synchronized (this) {
            if (mController != null) {
                mProvisioningAnalyticsTracker.logProvisioningCancelled(mContext,
                        CANCELLED_DURING_PROVISIONING);
                mController.cancel();
            } else {
                ProvisionLogger.loge("Trying to cancel provisioning, but controller is null");
            }
        }
    }

    /**
     * Register a listener for updates of the provisioning progress.
     *
     * <p>Registering a listener will immediately result in the last callback being sent to the
     * listener. All callbacks will occur on the UI thread.</p>
     *
     * @param callback listener to be registered.
     */
    public void registerListener(ProvisioningManagerCallback callback) {
        synchronized (this) {
            mCallbacks.add(callback);
            callLastCallbackLocked(callback);
        }
    }

    /**
     * Unregister a listener from updates of the provisioning progress.
     *
     * @param callback listener to be unregistered.
     */
    public void unregisterListener(ProvisioningManagerCallback callback) {
        synchronized (this) {
            mCallbacks.remove(callback);
        }
    }

    @Override
    public void cleanUpCompleted() {
        synchronized (this) {
            clearControllerLocked();
        }
    }

    @Override
    public void error(int titleId, int messageId, boolean factoryResetRequired) {
        synchronized (this) {
            for (ProvisioningManagerCallback callback : mCallbacks) {
                mUiHandler.post(() -> callback.error(titleId, messageId, factoryResetRequired));
            }
            mLastCallback = CALLBACK_ERROR;
            mLastError = Pair.create(Pair.create(titleId, messageId), factoryResetRequired);
        }
    }

    @Override
    public void progressUpdate(int progressMsgId) {
        synchronized (this) {
            for (ProvisioningManagerCallback callback : mCallbacks) {
                mUiHandler.post(() -> callback.progressUpdate(progressMsgId));
            }
            mLastCallback = CALLBACK_PROGRESS;
            mLastProgressMsgId = progressMsgId;
        }
    }

    @Override
    public void provisioningTasksCompleted() {
        synchronized (this) {
            mTimeLogger.stop();
            if (mController != null) {
                mUiHandler.post(mController::preFinalize);
            } else {
                ProvisionLogger.loge("Trying to pre-finalize provisioning, but controller is null");
            }
        }
    }

    @Override
    public void preFinalizationCompleted() {
        synchronized (this) {
            for (ProvisioningManagerCallback callback : mCallbacks) {
                mUiHandler.post(callback::preFinalizationCompleted);
            }
            mLastCallback = CALLBACK_PRE_FINALIZED;
            mProvisioningAnalyticsTracker.logProvisioningSessionCompleted(mContext);
            clearControllerLocked();
            ProvisionLogger.logi("ProvisioningManager pre-finalization completed");
        }
    }

    private void callLastCallbackLocked(ProvisioningManagerCallback callback) {
        switch (mLastCallback) {
            case CALLBACK_ERROR:
                final Pair<Pair<Integer, Integer>, Boolean> error = mLastError;
                mUiHandler.post(
                        () -> callback.error(error.first.first, error.first.second, error.second));
                break;
            case CALLBACK_PROGRESS:
                final int progressMsg = mLastProgressMsgId;
                mUiHandler.post(() -> callback.progressUpdate(progressMsg));
                break;
            case CALLBACK_PRE_FINALIZED:
                mUiHandler.post(callback::preFinalizationCompleted);
                break;
            default:
                ProvisionLogger.logd("No previous callback");
        }
    }

    private void clearControllerLocked() {
        mController = null;

        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
            mContext.stopService(SERVICE_INTENT);
        }
    }
}
