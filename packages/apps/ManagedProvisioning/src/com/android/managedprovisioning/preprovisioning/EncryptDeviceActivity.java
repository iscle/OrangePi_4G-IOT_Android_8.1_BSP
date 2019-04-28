/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_START_ENCRYPTION;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ENCRYPT_DEVICE_ACTIVITY_TIME_MS;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Activity to ask for permission to activate full-filesystem encryption.
 *
 * Pressing 'settings' will launch settings to prompt the user to encrypt
 * the device.
 */
public class EncryptDeviceActivity extends SetupGlifLayoutActivity {
    private ProvisioningParams mParams;

    protected EncryptionController getEncryptionController() {
        return EncryptionController.getInstance(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        if (mParams == null) {
            ProvisionLogger.loge("Missing params in EncryptDeviceActivity activity");
            finish();
            return;
        }

        if (getUtils().isProfileOwnerAction(mParams.provisioningAction)) {
            initializeUi(R.string.setup_work_profile,
                    R.string.setup_profile_encryption,
                    R.string.encrypt_device_text_for_profile_owner_setup);
        } else if (getUtils().isDeviceOwnerAction(mParams.provisioningAction)) {
            initializeUi(R.string.setup_work_device,
                    R.string.setup_device_encryption,
                    R.string.encrypt_device_text_for_device_owner_setup);
        } else {
            ProvisionLogger.loge("Unknown provisioning action: " + mParams.provisioningAction);
            finish();
            return;
        }

        Button encryptButton = (Button) findViewById(R.id.encrypt_button);
        encryptButton.setOnClickListener((View v) -> {
                    getEncryptionController().setEncryptionReminder(mParams);
                    // Use settings so user confirms password/pattern and its passed
                    // to encryption tool.
                    startActivity(new Intent(ACTION_START_ENCRYPTION));
                });
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_ENCRYPT_DEVICE_ACTIVITY_TIME_MS;
    }

    private void initializeUi(int headerRes, int titleRes, int mainTextRes) {
        initializeLayoutParams(R.layout.encrypt_device, headerRes, false,
                CustomizationParams.createInstance(mParams, this, mUtils).statusBarColor);
        setTitle(titleRes);
        ((TextView) findViewById(R.id.encrypt_main_text)).setText(mainTextRes);
    }
}