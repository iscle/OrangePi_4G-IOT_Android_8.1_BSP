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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.service.euicc.EuiccService;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.euicc.EuiccConnector;

/** Trampoline activity to forward eUICC intents from apps to the active UI implementation. */
public class EuiccUiDispatcherActivity extends Activity {
    private static final String TAG = "EuiccUiDispatcher";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent euiccUiIntent = resolveEuiccUiIntent();
            if (euiccUiIntent == null) {
                setResult(RESULT_CANCELED);
                onDispatchFailure();
                return;
            }

            euiccUiIntent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(euiccUiIntent);
        } finally {
            // Since we're using Theme.NO_DISPLAY, we must always finish() at the end of onCreate().
            finish();
        }
    }

    @VisibleForTesting
    @Nullable
    Intent resolveEuiccUiIntent() {
        EuiccManager euiccManager = (EuiccManager) getSystemService(Context.EUICC_SERVICE);
        if (!euiccManager.isEnabled()) {
            Log.w(TAG, "eUICC not enabled");
            return null;
        }

        Intent euiccUiIntent = getEuiccUiIntent();
        if (euiccUiIntent == null) {
            Log.w(TAG, "Unable to handle intent");
            return null;
        }

        ActivityInfo activityInfo = findBestActivity(euiccUiIntent);
        if (activityInfo == null) {
            Log.w(TAG, "Could not resolve activity for intent: " + euiccUiIntent);
            return null;
        }

        euiccUiIntent.setComponent(activityInfo.getComponentName());
        return euiccUiIntent;
    }

    /** Called when dispatch fails. May be overridden to perform some operation here. */
    protected void onDispatchFailure() {
    }

    /**
     * Return an Intent to start the Euicc app's UI for the given intent, or null if given intent
     * cannot be handled.
     */
    @Nullable
    protected Intent getEuiccUiIntent() {
        String action = getIntent().getAction();

        Intent intent = new Intent();
        switch (action) {
            case EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS:
                intent.setAction(EuiccService.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS);
                break;
            case EuiccManager.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION:
                if (isDeviceProvisioned()) {
                    Log.w(TAG, "Cannot perform eUICC provisioning once device is provisioned");
                    return null;
                }
                intent.setAction(EuiccService.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION);
                intent.putExtra(
                        EuiccManager.EXTRA_FORCE_PROVISION,
                        getIntent().getBooleanExtra(EuiccManager.EXTRA_FORCE_PROVISION, false));
                break;
            default:
                Log.w(TAG, "Unsupported action: " + action);
                return null;
        }

        return intent;
    }

    @VisibleForTesting
    boolean isDeviceProvisioned() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    @VisibleForTesting
    @Nullable
    ActivityInfo findBestActivity(Intent euiccUiIntent) {
        return EuiccConnector.findBestActivity(getPackageManager(), euiccUiIntent);
    }
}
