/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.phone.euicc;

import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.euicc.EuiccService;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

/**
 * Trampoline activity to forward eUICC intents for error resolutions to the active UI
 * implementation.
 *
 * <p>Unlike {@link EuiccUiDispatcherActivity}, this activity is started with extras that must not
 * be tampered with, because they are used to resume the operation after the error is resolved. We
 * thus declare it as a separate activity which requires a locked-down permission to start.
 */
public class EuiccResolutionUiDispatcherActivity extends EuiccUiDispatcherActivity {
    private static final String TAG = "EuiccResUiDispatcher";

    @Override
    @Nullable
    protected Intent getEuiccUiIntent() {
        String action = getIntent().getAction();
        if (!EuiccManager.ACTION_RESOLVE_ERROR.equals(action)) {
            Log.w(TAG, "Unsupported action: " + action);
            return null;
        }

        String euiccUiAction =
                getIntent().getStringExtra(
                        EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_RESOLUTION_ACTION);
        if (!EuiccService.RESOLUTION_ACTIONS.contains(euiccUiAction)) {
            Log.w(TAG, "Unknown resolution action: " + euiccUiAction);
            return null;
        }

        Intent euiccUiIntent = new Intent(euiccUiAction);
        // Propagate the extras from the original Intent.
        euiccUiIntent.putExtras(getIntent());
        return euiccUiIntent;
    }

    @Override
    protected void onDispatchFailure() {
        // Attempt to dispatch the callback so the caller knows the operation has failed.
        PendingIntent callbackIntent =
                getIntent().getParcelableExtra(
                        EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_RESOLUTION_CALLBACK_INTENT);
        if (callbackIntent != null) {
            try {
                callbackIntent.send(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR);
            } catch (PendingIntent.CanceledException e) {
                // Caller canceled the callback; do nothing.
            }
        }
    }
}
