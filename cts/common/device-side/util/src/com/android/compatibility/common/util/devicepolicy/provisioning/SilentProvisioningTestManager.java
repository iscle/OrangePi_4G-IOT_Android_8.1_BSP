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
package com.android.compatibility.common.util.devicepolicy.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_ADDED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SilentProvisioningTestManager {
    private static final long TIMEOUT_SECONDS = 120L;
    private static final String TAG = "SilentProvisioningTest";

    private final LinkedBlockingQueue<Boolean> mProvisioningResults = new LinkedBlockingQueue(1);

    private final IBooleanCallback mProvisioningResultCallback = new IBooleanCallback.Stub() {
        @Override
        public void onResult(boolean result) {
            try {
                mProvisioningResults.put(result);
            } catch (InterruptedException e) {
                Log.e(TAG, "IBooleanCallback.callback", e);
            }
        }
    };

    private final Context mContext;
    private Intent mReceivedProfileProvisionedIntent;

    public SilentProvisioningTestManager(Context context) {
        mContext = context.getApplicationContext();
    }

    public Intent getReceviedProfileProvisionedIntent() {
        return mReceivedProfileProvisionedIntent;
    }

    public boolean startProvisioningAndWait(Intent provisioningIntent) throws InterruptedException {
        wakeUpAndDismissInsecureKeyguard();
        mContext.startActivity(getStartIntent(provisioningIntent));
        Log.i(TAG, "startActivity with intent: " + provisioningIntent);

        if (ACTION_PROVISION_MANAGED_PROFILE.equals(provisioningIntent.getAction())) {
            return waitManagedProfileProvisioning();
        } else {
            return waitDeviceOwnerProvisioning();
        }
    }

    private boolean waitDeviceOwnerProvisioning() throws InterruptedException {
        return pollProvisioningResult();
    }

    private boolean waitManagedProfileProvisioning() throws InterruptedException {
        BlockingBroadcastReceiver managedProfileProvisionedReceiver =
                new BlockingBroadcastReceiver(mContext, ACTION_MANAGED_PROFILE_PROVISIONED);
        BlockingBroadcastReceiver managedProfileAddedReceiver =
                new BlockingBroadcastReceiver(mContext, ACTION_MANAGED_PROFILE_ADDED);
        try {
            managedProfileProvisionedReceiver.register();
            managedProfileAddedReceiver.register();

            if (!pollProvisioningResult()) {
                return false;
            }

            mReceivedProfileProvisionedIntent =
                    managedProfileProvisionedReceiver.awaitForBroadcast();
            if (mReceivedProfileProvisionedIntent == null) {
                Log.i(TAG, "managedProfileProvisionedReceiver.awaitForBroadcast(): failed");
                return false;
            }

            if (managedProfileAddedReceiver.awaitForBroadcast() == null) {
                Log.i(TAG, "managedProfileAddedReceiver.awaitForBroadcast(): failed");
                return false;
            }
        } finally {
            managedProfileProvisionedReceiver.unregisterQuietly();
            managedProfileAddedReceiver.unregisterQuietly();
        }
        return true;
    }

    private boolean pollProvisioningResult() throws InterruptedException {
        Boolean result = mProvisioningResults.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (result == null) {
            Log.i(TAG, "ManagedProvisioning doesn't return result within "
                    + TIMEOUT_SECONDS + " seconds ");
            return false;
        }

        if (!result) {
            Log.i(TAG, "Failed to provision");
            return false;
        }
        return true;
    }

    private Intent getStartIntent(Intent intent) {
        final Bundle bundle = new Bundle();
        bundle.putParcelable(Intent.EXTRA_INTENT, intent);
        bundle.putBinder(StartProvisioningActivity.EXTRA_BOOLEAN_CALLBACK,
                mProvisioningResultCallback.asBinder());
        return new Intent(mContext, StartProvisioningActivity.class).putExtras(bundle);
    }

    private static void wakeUpAndDismissInsecureKeyguard() {
        try {
            UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            uiDevice.wakeUp();
            uiDevice.pressMenu();
        } catch (RemoteException e) {
            Log.e(TAG, "wakeUpScreen", e);
        }
    }

    private static class BlockingReceiver extends BroadcastReceiver {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final Context mContext;
        private final String mAction;
        private Intent mReceivedIntent;

        private BlockingReceiver(Context context, String action) {
            mContext = context;
            mAction = action;
            mReceivedIntent = null;
        }

        public void register() {
            mContext.registerReceiver(this, new IntentFilter(mAction));
        }

        public boolean await() throws InterruptedException {
            return mLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        public Intent getReceivedIntent() {
            return mReceivedIntent;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mReceivedIntent = intent;
            mLatch.countDown();
        }
    }
}
