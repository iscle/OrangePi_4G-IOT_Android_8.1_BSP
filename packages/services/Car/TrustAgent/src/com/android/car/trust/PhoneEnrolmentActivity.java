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
 * limitations under the License
 */
package com.android.car.trust;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity to allow the user to add an escrow token to a remote device. <p/>
 *
 * For this to work properly, the correct permissions must be set in the system config.  In AOSP,
 * this config is in frameworks/base/core/res/res/values/config.xml <p/>
 *
 * The config must set config_allowEscrowTokenForTrustAgent to true.  For the desired car
 * experience, the config should also set config_strongAuthRequiredOnBoot to false.
 */
public class PhoneEnrolmentActivity extends Activity {
    private Button mScanButton;
    private Button mEnrollButton;

    private TextView mTextOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_client);

        mScanButton = (Button) findViewById(R.id.ble_scan_btn);
        mEnrollButton = (Button) findViewById(R.id.action_button);
        mEnrollButton.setText(getString(R.string.enroll_button));

        mTextOutput = (TextView) findViewById(R.id.output);

        PhoneEnrolmentController controller = new PhoneEnrolmentController(this /* context */);
        controller.bind(mTextOutput, mScanButton, mEnrollButton);
    }
}
