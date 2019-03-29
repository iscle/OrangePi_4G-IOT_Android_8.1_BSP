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

package com.android.cts.verifier.companion;

import android.bluetooth.BluetoothDevice;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.List;


/**
 * Test that checks that the {@link CompanionDeviceManager} API is functional
 */
public class CompanionDeviceTestActivity extends PassFailButtons.Activity {

    private static final String LOG_TAG = "CompanionDeviceTestActi";
    private static final int REQUEST_CODE_CHOOSER = 0;

    private CompanionDeviceManager mCompanionDeviceManager;
    private List<String> mInitialAssociations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.companion_test_main);
        setPassFailButtonClickListeners();

        mCompanionDeviceManager = getSystemService(CompanionDeviceManager.class);

        getPassButton().setEnabled(false);

        findViewById(R.id.button).setOnClickListener(v -> test());
    }

    private void test() {
        mInitialAssociations = mCompanionDeviceManager.getAssociations();

        AssociationRequest request = new AssociationRequest.Builder()
                .addDeviceFilter(new BluetoothDeviceFilter.Builder().build())
                .build();
        CompanionDeviceManager.Callback callback = new CompanionDeviceManager.Callback() {
            @Override
            public void onDeviceFound(IntentSender chooserLauncher) {
                try {
                    startIntentSenderForResult(chooserLauncher,
                            REQUEST_CODE_CHOOSER, null, 0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    fail(e);
                }
            }

            @Override
            public void onFailure(CharSequence error) {
                fail(error);
            }
        };
        mCompanionDeviceManager.associate(request, callback, null);
    }

    private void fail(Throwable reason) {
        Log.e(LOG_TAG, "Test failed", reason);
        fail(reason.getMessage());
    }

    private void fail(CharSequence reason) {
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        Log.e(LOG_TAG, reason.toString());
        setTestResultAndFinish(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CHOOSER) {

            if (resultCode != RESULT_OK) fail("Activity result code " + resultCode);

            List<String> newAssociations = mCompanionDeviceManager.getAssociations();
            if (!newAssociations.containsAll(mInitialAssociations)) {
                fail("New associations " + newAssociations
                        + " lack some of the original items from "
                        + mInitialAssociations);
            }
            if (newAssociations.size() != mInitialAssociations.size() + 1) {
                fail("New associations " + newAssociations + " are not 1 item larger from initial "
                        + mInitialAssociations);
            }

            BluetoothDevice associatedDevice
                    = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
            String deviceAddress = associatedDevice.getAddress();
            if (!newAssociations.contains(deviceAddress)) {
                fail("Selected device is not present among new associations " + newAssociations);
            }

            mCompanionDeviceManager.disassociate(associatedDevice.getAddress());
            List<String> associations = mCompanionDeviceManager.getAssociations();
            if (associations.contains(deviceAddress)) {
                fail("Disassociating device " + deviceAddress
                        + " did not remove it from associations list"
                        + associations);
            }

            if (!isFinishing()) {
                getPassButton().setEnabled(true);
            }

        } else super.onActivityResult(requestCode, resultCode, data);
    }
}
