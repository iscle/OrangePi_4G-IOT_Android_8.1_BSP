/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.intent.receiver;

import android.app.admin.DevicePolicyManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import android.os.Bundle;

/**
 * An activity that receives an intent and returns immediately, indicating its own name and if it is
 * running in a managed profile.
 */
public class SimpleIntentReceiverActivity extends Activity {
    private static final String TAG = "SimpleIntentReceiverActivity";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String className = getIntent().getComponent().getClassName();

        // We try to check if we are in a managed profile or not.
        // To do this, check if com.android.cts.managedprofile is the profile owner.
        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean inManagedProfile = dpm.isProfileOwnerApp("com.android.cts.managedprofile");

        Log.i(TAG, "activity " + className + " started, is in managed profile: "
                + inManagedProfile);
        Intent result = new Intent();
        result.putExtra("extra_receiver_class", className);
        result.putExtra("extra_in_managed_profile", inManagedProfile);
        setResult(Activity.RESULT_OK, result);
        finish();
    }
}
