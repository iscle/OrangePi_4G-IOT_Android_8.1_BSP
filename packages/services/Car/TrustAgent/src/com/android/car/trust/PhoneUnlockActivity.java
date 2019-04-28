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
 * Activity to allow the user to unlock a remote devices with the stored escrow token.
 */
public class PhoneUnlockActivity extends Activity {
    private Button mScannButton;
    private Button mUnlockButton;

    private TextView mTextOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_client);

        mScannButton = (Button) findViewById(R.id.ble_scan_btn);
        mUnlockButton = (Button) findViewById(R.id.action_button);
        mUnlockButton.setText(getString(R.string.unlock_button));

        mTextOutput = (TextView) findViewById(R.id.output);

        PhoneUnlockController controller = new PhoneUnlockController(this /* context */);
        controller.bind(mTextOutput, mScannButton, mUnlockButton);
    }
}
