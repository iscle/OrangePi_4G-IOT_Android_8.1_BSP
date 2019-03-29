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
package com.android.cts.delegate;

import static android.app.admin.DevicePolicyManager.ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED;
import static android.app.admin.DevicePolicyManager.EXTRA_DELEGATION_SCOPES;

import android.app.Activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Bundle;

import java.util.List;

/**
 * Simple activity that registers a {@code BroadcastReceiver} for intercepting
 * {@link DevicePolicyManager#ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED} intents sent when the
 * a DO/PO calls grants this app new delegation scopes via
 * {@link DevicePolicyManager#setDelegatedScopes}.
 */
public class DelegatedScopesReceiverActivity extends Activity {

    /**
     * Broadcast action sent reporting the scopes delegated to this app.
     */
    private static final String ACTION_REPORT_SCOPES = "com.android.cts.delegate.report_scopes";

    /**
     * Broadcast action sent reporting that this app is running.
     */
    private static final String ACTION_RUNNING = "com.android.cts.delegate.running";

    private final BroadcastReceiver mScopesChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED.equals(intent.getAction())) {
                handleIntent(intent);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED);
        registerReceiver(mScopesChangedReceiver, filter);
        sendRunningBroadcast();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mScopesChangedReceiver);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        if (ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED.equals(intent.getAction())) {
            final List<String> scopes = intent.getStringArrayListExtra(EXTRA_DELEGATION_SCOPES);
            sendScopeReportBroadcast(scopes);
            finish();
        }
    }

    private void sendScopeReportBroadcast(List<String> scopes) {
        Intent intent = new Intent();
        intent.setAction(ACTION_REPORT_SCOPES);
        intent.putExtra(EXTRA_DELEGATION_SCOPES, scopes.toArray(new String[scopes.size()]));
        sendBroadcast(intent);
    }

    private void sendRunningBroadcast() {
        Intent intent = new Intent();
        intent.setAction(ACTION_RUNNING);
        sendBroadcast(intent);
    }
}
