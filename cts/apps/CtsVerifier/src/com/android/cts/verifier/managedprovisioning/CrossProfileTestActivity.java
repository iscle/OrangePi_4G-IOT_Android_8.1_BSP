/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;
//package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.android.cts.verifier.R;

/**
 * Test activity for cross profile intents.
 */
public class CrossProfileTestActivity extends Activity {
    // Intent for app in both profiles
    public static final String ACTION_CROSS_PROFILE_TO_PERSONAL =
            "com.android.cts.verifier.managedprovisioning.CROSS_PROFILE_TO_PERSONAL";
    public static final String ACTION_CROSS_PROFILE_TO_WORK =
            "com.android.cts.verifier.managedprovisioning.CROSS_PROFILE_TO_WORK";
    public static final String EXTRA_STARTED_FROM_WORK
            = "com.android.cts.verifier.managedprovisioning.STARTED_FROM_WORK";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.provisioning_cross_profile);
        TextView textView = (TextView) findViewById(R.id.text);

        // Check if we are running in the work or personal side, by testing if currently we are the
        // profile owner or not.
        boolean inWorkProfile = isProfileOwner();
        boolean startedFromWork = getIntent().getBooleanExtra(EXTRA_STARTED_FROM_WORK, false);
        if (inWorkProfile && !startedFromWork) {
            textView.setText(R.string.provisioning_byod_cross_profile_app_work);
        } else if (!inWorkProfile && startedFromWork) {
            textView.setText(R.string.provisioning_byod_cross_profile_app_personal);
        } else { // started from the same side we're currently running in
            textView.setText(R.string.provisioning_byod_cross_profile_app_ctsverifier);
        }
        findViewById(R.id.button_finish).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                CrossProfileTestActivity.this.finish();
            }
        });
    }

    private boolean isProfileOwner() {
        ComponentName adminReceiver = new ComponentName(this, DeviceAdminTestReceiver.class.getName());
        DevicePolicyManager dpm = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm.isAdminActive(adminReceiver) && dpm.isProfileOwnerApp(adminReceiver.getPackageName());
    }
}
