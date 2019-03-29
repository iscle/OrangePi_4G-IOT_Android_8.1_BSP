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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.WindowManager;

/**
 * Must register it in AndroidManifest.xml
 * <activity android:name="com.android.compatibility.common.util.devicepolicy.provisioning.StartProvisioningActivity"></activity>
 */
public class StartProvisioningActivity extends Activity {
    private static final int REQUEST_CODE = 1;
    private static final String TAG = "StartProvisionActivity";

    public static final String EXTRA_BOOLEAN_CALLBACK = "EXTRA_BOOLEAN_CALLBACK";

    IBooleanCallback mResultCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Reduce flakiness of the test
        // Show activity on top of keyguard
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // Turn on screen to prevent activity being paused by system
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mResultCallback = IBooleanCallback.Stub.asInterface(
                getIntent().getExtras().getBinder(EXTRA_BOOLEAN_CALLBACK));
        Log.i(TAG, "result callback class name " + mResultCallback);

        // Only provision it if the activity is not re-created
        if (savedInstanceState == null) {
            Intent provisioningIntent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);

            startActivityForResult(provisioningIntent, REQUEST_CODE);
            Log.i(TAG, "Start provisioning intent");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            try {
                boolean result = resultCode == RESULT_OK;
                mResultCallback.onResult(result);
                Log.i(TAG, "onActivityResult result: " + result);
            } catch (RemoteException e) {
                Log.e(TAG, "onActivityResult", e);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}