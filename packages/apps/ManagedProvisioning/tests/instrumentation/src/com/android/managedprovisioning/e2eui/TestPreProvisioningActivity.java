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

import android.content.Intent;
import android.util.Log;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivity;

public class TestPreProvisioningActivity extends PreProvisioningActivity {
    private static final String TAG = "TestPreProvisioningActivity";

    private final ProvisioningResultListener mlistener;

    public TestPreProvisioningActivity(ProvisioningResultListener listener) {
        mlistener = listener;
    }

    /** ManagedProfileTest is running in ManagedProvisioning process, while the AdminReceiver is in
     * test package process. Mock the calling package to pretend we provision it from test package,
     * not from ManagedProvisioning.
     */
    @Override
    public String getCallingPackage() {
        return TestInstrumentationRunner.TEST_PACKAGE_NAME;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult " + requestCode);
        // Return the result code to the test and verify it.
        if (requestCode == PROVISIONING_REQUEST_CODE) {
            mlistener.setPreprovisioningActivityResult(resultCode == RESULT_OK);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
