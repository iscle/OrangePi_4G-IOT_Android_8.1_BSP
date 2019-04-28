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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * Selects whether the device is started as the car or remote device.
 */
public class MainActivity extends Activity {
    private static final int FINE_LOCATION_REQUEST_CODE = 13;

    private Button mCarEnrolmentButton;
    private Button mPhoneEnrolmentButton;
    private Button mPhoneUnlockButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_app);
        mCarEnrolmentButton = (Button) findViewById(R.id.car_button);
        mPhoneEnrolmentButton = (Button) findViewById(R.id.phone_enrolment_button);
        mPhoneUnlockButton = (Button) findViewById(R.id.phone_unlock_button);

        mCarEnrolmentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this /* context */,
                        CarEnrolmentActivity.class);
                startActivity(intent);
            }
        });

        mPhoneEnrolmentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this /* context */,
                        PhoneEnrolmentActivity.class);
                startActivity(intent);
            }
        });

        mPhoneUnlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this /* context */,
                        PhoneUnlockActivity.class);
                startActivity(intent);
            }
        });

        if (!checkPermissionGranted()) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    FINE_LOCATION_REQUEST_CODE);
            // If location access isn't granted, BLE scanning will fail.
            mCarEnrolmentButton.setEnabled(false);
            mPhoneEnrolmentButton.setEnabled(false);
            mPhoneUnlockButton.setEnabled(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String permissions[], int[] grantResults) {
        if (requestCode == FINE_LOCATION_REQUEST_CODE && checkPermissionGranted()) {
            mCarEnrolmentButton.setEnabled(true);
            mPhoneEnrolmentButton.setEnabled(true);
            mPhoneUnlockButton.setEnabled(true);
        }
    }

    private boolean checkPermissionGranted() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}