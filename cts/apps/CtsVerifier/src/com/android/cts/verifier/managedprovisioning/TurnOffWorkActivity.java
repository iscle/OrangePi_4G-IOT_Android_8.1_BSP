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

package com.android.cts.verifier.managedprovisioning;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

public class TurnOffWorkActivity extends DialogTestListActivity {

    private static final String TAG = "TurnOffWorkActivity";
    private DialogTestListItem mTurnOffWorkTest;
    private DialogTestListItem mTurnOnWorkTest;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context content, Intent intent) {
            if (Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(intent.getAction())) {
                setTestResult(mTurnOffWorkTest, TestResult.TEST_RESULT_PASSED);
            } else if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(intent.getAction())) {
                setTestResult(mTurnOnWorkTest, TestResult.TEST_RESULT_PASSED);
            }
        }
    };

    public TurnOffWorkActivity() {
        super(R.layout.provisioning_byod,
                R.string.provisioning_byod_turn_off_work,
                R.string.provisioning_byod_turn_off_work_info,
                R.string.provisioning_byod_turn_off_work_instructions);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrepareTestButton.setText(R.string.provisioning_byod_turn_off_work_prepare_button);
        mPrepareTestButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "Cannot start activity.", e);
                    Toast.makeText(TurnOffWorkActivity.this,
                            "Cannot start settings", Toast.LENGTH_SHORT).show();
                }
            }
        });
        setTestResult(mTurnOffWorkTest, TestResult.TEST_RESULT_NOT_EXECUTED);
        setTestResult(mTurnOnWorkTest, TestResult.TEST_RESULT_NOT_EXECUTED);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        final Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);

        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_turn_off_work_prepare_notifications,
                "BYOD_TurnOffWorkCreateNotification",
                R.string.provisioning_byod_turn_off_work_prepare_notifications_instruction,
                new Intent(ByodHelperActivity.ACTION_NOTIFICATION)));

        mTurnOffWorkTest = new DialogTestListItem(this,
                R.string.provisioning_byod_turn_off_work_turned_off,
                "BYOD_WorkTurnedOff") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                Toast.makeText(TurnOffWorkActivity.this,
                        R.string.provisioning_byod_turn_off_work_turned_off_toast,
                        Toast.LENGTH_SHORT).show();
            }
        };
        adapter.add(mTurnOffWorkTest);

        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_turn_off_work_notifications,
                "BYOD_TurnOffWorkNotifications",
                R.string.provisioning_byod_turn_off_work_notifications_instruction,
                new Intent(ByodHelperActivity.ACTION_NOTIFICATION)));

        adapter.add(new DialogTestListItem(this, R.string.provisioning_byod_turn_off_work_icon,
                "BYOD_TurnOffWorkIcon",
                R.string.provisioning_byod_turn_off_work_icon_instruction,
                new Intent(Settings.ACTION_SETTINGS)));

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            adapter.add(new DialogTestListItem(this, R.string.provisioning_byod_turn_off_work_launcher,
                    "BYOD_TurnOffWorkStartApps",
                    R.string.provisioning_byod_turn_off_work_launcher_instruction,
                    homeIntent));
        }

        mTurnOnWorkTest = new DialogTestListItem(this,
                R.string.provisioning_byod_turn_off_work_turned_on,
                "BYOD_WorkTurnedOn") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                Toast.makeText(TurnOffWorkActivity.this,
                        R.string.provisioning_byod_turn_off_work_turned_on_toast,
                        Toast.LENGTH_SHORT).show();
            }
        };
        adapter.add(mTurnOnWorkTest);

        adapter.add(new DialogTestListItem(this, R.string.provisioning_byod_turn_on_work_icon,
                "BYOD_TurnOnWorkIcon",
                R.string.provisioning_byod_turn_on_work_icon_instruction,
                new Intent(Settings.ACTION_SETTINGS)));

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            adapter.add(new DialogTestListItem(this, R.string.provisioning_byod_turn_on_work_launcher,
                    "BYOD_TurnOnWorkStartApps",
                    R.string.provisioning_byod_turn_on_work_launcher_instruction,
                    homeIntent));
        }
    }
}
