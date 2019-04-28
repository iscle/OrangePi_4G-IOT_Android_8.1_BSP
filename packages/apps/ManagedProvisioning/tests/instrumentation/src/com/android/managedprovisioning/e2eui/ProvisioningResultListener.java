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

package com.android.managedprovisioning.e2eui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listen to provisioning result from DPC running in test process
 */
public class ProvisioningResultListener {
    private static final String TAG = ProvisioningResultListener.class.getSimpleName();

    public static final String ACTION_PROVISION_RESULT_BROADCAST =
            "com.android.managedprovisioning.e2eui.ACTION_PROVISION_RESULT_BROADCAST";
    public static final String ACTION_PROVISION_RESULT_INTENT =
            "com.android.managedprovisioning.e2eui.ACTION_PROVISION_RESULT_INTENT";
    public static final String EXTRA_RESULT = "result";

    private final Context mContext;
    private final CountDownLatch mLatch = new CountDownLatch(3);
    private final AtomicBoolean mBroadcastResult = new AtomicBoolean(false);
    private final AtomicBoolean mPreprovisioningActivityResult = new AtomicBoolean(false);
    private final AtomicBoolean mIntentResult = new AtomicBoolean(false);
    private final ResultReceiver mReceiver = new ResultReceiver();
    private boolean mIsRegistered = false;

    private class ResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case ACTION_PROVISION_RESULT_BROADCAST:
                    mBroadcastResult.set(intent.getBooleanExtra(EXTRA_RESULT, false));
                    mLatch.countDown();
                    break;
                case ACTION_PROVISION_RESULT_INTENT:
                    mIntentResult.set(intent.getBooleanExtra(EXTRA_RESULT, false));
                    mLatch.countDown();
                    break;
            }
        }
    }

    public ProvisioningResultListener(Context context) {
        mContext = context;
    }

    public void register() {
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_PROVISION_RESULT_BROADCAST));
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_PROVISION_RESULT_INTENT));
        mIsRegistered = true;
    }

    public void unregister() {
        if (mIsRegistered) {
            mContext.unregisterReceiver(mReceiver);
            mIsRegistered = false;
        }
    }

    public boolean await(long timeoutSeconds) throws InterruptedException {
        return mLatch.await(timeoutSeconds, TimeUnit.SECONDS);
    }

    public void setPreprovisioningActivityResult(boolean result) {
        mPreprovisioningActivityResult.set(result);
        mLatch.countDown();
    }

    public boolean getResult() {
        Log.i(TAG, "mBroadcastResult: " + mBroadcastResult.get());
        Log.i(TAG, "mIntentResult: " + mIntentResult.get());
        Log.i(TAG, "mPreprovisioningActivityResult: " + mPreprovisioningActivityResult.get());
        return mBroadcastResult.get() && mIntentResult.get()
                && mPreprovisioningActivityResult.get();
    }
}
