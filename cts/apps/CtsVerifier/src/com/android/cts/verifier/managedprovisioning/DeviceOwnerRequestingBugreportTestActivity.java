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

import static com.android.cts.verifier.managedprovisioning.Utils.createInteractiveTestItem;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.TestResult;

/**
 * Activity that lists all requesting bugreport device owner tests. Requires the following
 * adb command be issued by the user prior to starting the tests:
 *
 * adb shell dpm set-device-owner
 *  'com.android.cts.verifier/com.android.cts.verifier.managedprovisioning.DeviceAdminTestReceiver'
 */
public class DeviceOwnerRequestingBugreportTestActivity extends PassFailButtons.TestListActivity {
    private static final String TAG = "DeviceOwnerRequestingBugreportTestActivity";

    private static final String ACTION_CHECK_DEVICE_OWNER_FOR_REQUESTING_BUGREPORT =
            "com.android.cts.verifier.managedprovisioning.action" +
            ".CHECK_DEVICE_OWNER_FOR_REQUESTING_BUGREPORT";
    static final String EXTRA_TEST_ID = "extra-test-id";

    private static final String CHECK_DEVICE_OWNER_TEST_ID = "CHECK_DEVICE_OWNER";
    private static final String DEVICE_ADMIN_SETTINGS_ID = "DEVICE_ADMIN_SETTINGS";
    private static final String BUGREPORT_SHARING_DECLINED_WHILE_BEING_TAKEN =
            "BUGREPORT_SHARING_DECLINED_WHILE_RUNNING";
    private static final String BUGREPORT_SHARING_ACCEPTED_WHILE_BEING_TAKEN =
            "BUGREPORT_SHARING_ACCEPTED_WHILE_RUNNING";
    private static final String BUGREPORT_SHARING_DECLINED_AFTER_HAVING_BEEN_TAKEN =
            "BUGREPORT_SHARING_DECLINED_AFTER_HAVING_BEEN_TAKEN";
    private static final String BUGREPORT_SHARING_ACCEPTED_AFTER_HAVING_BEEN_TAKEN =
            "BUGREPORT_SHARING_ACCEPTED_AFTER_HAVING_BEEN_TAKEN";
    private static final String REMOVE_DEVICE_OWNER_TEST_ID = "REMOVE_DEVICE_OWNER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ACTION_CHECK_DEVICE_OWNER_FOR_REQUESTING_BUGREPORT.equals(getIntent().getAction())) {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            if (dpm.isDeviceOwnerApp(getPackageName())) {
                TestResult.setPassedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        null, null);
            } else {
                TestResult.setFailedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        getString(R.string.device_owner_incorrect_device_owner), null);
            }
            finish();
            return;
        }

        // Tidy up in case previous run crashed.
        new ByodFlowTestHelper(this).tearDown();

        setContentView(R.layout.requesting_bugreport_device_owner);
        setInfoResources(R.string.device_owner_requesting_bugreport_tests,
                R.string.device_owner_requesting_bugreport_tests_info, 0);
        setPassFailButtonClickListeners();

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        adapter.add(TestListItem.newCategory(this,
                R.string.device_owner_requesting_bugreport_category));

        addTestsToAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);

        View setDeviceOwnerButton = findViewById(R.id.set_device_owner_button);
        setDeviceOwnerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(
                        DeviceOwnerRequestingBugreportTestActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setTitle(R.string.set_device_owner_dialog_title)
                        .setMessage(R.string.set_device_owner_dialog_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    @Override
    public void finish() {
        // If this activity was started for checking device owner status, then no need to do any
        // tear down.
        if (!ACTION_CHECK_DEVICE_OWNER_FOR_REQUESTING_BUGREPORT.equals(getIntent().getAction())) {
            // Pass and fail buttons are known to call finish() when clicked,
            // and this is when we want to remove the device owner.
            startActivity(createTearDownIntent());
        }
        super.finish();
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createTestItem(this, CHECK_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_check_device_owner_test,
                new Intent(ACTION_CHECK_DEVICE_OWNER_FOR_REQUESTING_BUGREPORT)
                        .putExtra(EXTRA_TEST_ID, getIntent().getStringExtra(EXTRA_TEST_ID))));

        // bugreport sharing declined while running test
        adapter.add(createInteractiveTestItem(this, BUGREPORT_SHARING_DECLINED_WHILE_BEING_TAKEN,
                R.string.device_owner_bugreport_sharing_declined_while_being_taken,
                R.string.device_owner_bugreport_sharing_declined_while_being_taken_info,
                new ButtonInfo(
                        R.string.device_owner_request_bugreport,
                        createRequestBugreportIntent())));

        // bugreport sharing accepted while running test
        adapter.add(createInteractiveTestItem(this, BUGREPORT_SHARING_ACCEPTED_WHILE_BEING_TAKEN,
                R.string.device_owner_bugreport_sharing_accepted_while_being_taken,
                R.string.device_owner_bugreport_sharing_accepted_while_being_taken_info,
                new ButtonInfo(
                        R.string.device_owner_request_bugreport,
                        createRequestBugreportIntent())));

        // bugreport sharing declined after having been taken test
        adapter.add(createInteractiveTestItem(this,
                BUGREPORT_SHARING_DECLINED_AFTER_HAVING_BEEN_TAKEN,
                R.string.device_owner_bugreport_sharing_declined_after_having_been_taken,
                R.string.device_owner_bugreport_sharing_declined_after_having_been_taken_info,
                new ButtonInfo(
                        R.string.device_owner_request_bugreport,
                        createRequestBugreportIntent())));

        // bugreport sharing accepted after having been taken test
        adapter.add(createInteractiveTestItem(this,
                BUGREPORT_SHARING_ACCEPTED_AFTER_HAVING_BEEN_TAKEN,
                R.string.device_owner_bugreport_sharing_accepted_after_having_been_taken,
                R.string.device_owner_bugreport_sharing_accepted_after_having_been_taken_info,
                new ButtonInfo(
                        R.string.device_owner_request_bugreport,
                        createRequestBugreportIntent())));

        // removeDeviceOwner
        adapter.add(createInteractiveTestItem(this, REMOVE_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_remove_device_owner_test,
                R.string.device_owner_remove_device_owner_test_info,
                new ButtonInfo(
                        R.string.remove_device_owner_button,
                        createTearDownIntent())));
    }

    static TestListItem createTestItem(Activity activity, String id, int titleRes,
            Intent intent) {
        intent.putExtra(EXTRA_TEST_ID, id);
        return TestListItem.newTest(activity, titleRes, id, intent, null);
    }

    private Intent createTearDownIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_REMOVE_DEVICE_OWNER);
    }

    private Intent createRequestBugreportIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_REQUEST_BUGREPORT);
    }
}
