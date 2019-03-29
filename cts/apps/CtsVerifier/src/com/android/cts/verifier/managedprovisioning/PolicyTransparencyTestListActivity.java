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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Pair;
import android.view.View;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import java.util.ArrayList;

/**
 * Test class to verify transparency for policies enforced by device/profile owner.
 */
public class PolicyTransparencyTestListActivity extends PassFailButtons.TestListActivity
        implements View.OnClickListener {
    public static final String ACTION_CHECK_POLICY_TRANSPARENCY =
            "com.android.cts.verifier.managedprovisioning.action.CHECK_POLICY_TRANSPARENCY";

    public static final String EXTRA_MODE =
            "com.android.cts.verifier.managedprovisioning.extra.mode";

    public static final int MODE_DEVICE_OWNER = 1;
    public static final int MODE_PROFILE_OWNER = 2;
    public static final int MODE_COMP = 3;

    /**
     * Pairs of:
     * <ul>
     *   <li>An intent to start {@link PolicyTransparencyTestActivity}
     *   <li>a label to show the user.
     * </ul>
     * These contain all the policies except for the user restriction ones.
     */
    private static final Pair<Intent, Integer>[] POLICIES;
    static {
        final String[] policyTests = new String[] {
            PolicyTransparencyTestActivity.TEST_CHECK_AUTO_TIME_REQUIRED,
            PolicyTransparencyTestActivity.TEST_CHECK_KEYGURAD_UNREDACTED_NOTIFICATION,
            PolicyTransparencyTestActivity.TEST_CHECK_LOCK_SCREEN_INFO,
            PolicyTransparencyTestActivity.TEST_CHECK_MAXIMUM_TIME_TO_LOCK,
            PolicyTransparencyTestActivity.TEST_CHECK_PASSWORD_QUALITY,
            PolicyTransparencyTestActivity.TEST_CHECK_PERMITTED_ACCESSIBILITY_SERVICE,
            PolicyTransparencyTestActivity.TEST_CHECK_PERMITTED_INPUT_METHOD
        };
        final String[] settingsIntentActions = new String[] {
            Settings.ACTION_DATE_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_SECURITY_SETTINGS,
            Settings.ACTION_DISPLAY_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            Settings.ACTION_INPUT_METHOD_SETTINGS
        };
        final int[] policyLabels = new int[] {
            R.string.set_auto_time_required,
            R.string.disallow_keyguard_unredacted_notifications,
            R.string.set_lock_screen_info,
            R.string.set_maximum_time_to_lock,
            R.string.set_password_quality,
            R.string.set_permitted_accessibility_services,
            R.string.set_permitted_input_methods
        };
        if (policyTests.length != settingsIntentActions.length ||
                policyTests.length != policyLabels.length) {
            throw new AssertionError("Number of items in policyTests, "
                    + " settingsIntentActions and policyLabels do not match");
        }
        POLICIES = new Pair[policyTests.length];
        for (int i = 0; i < policyTests.length; ++i) {
            final Intent intent =
                    new Intent(PolicyTransparencyTestActivity.ACTION_SHOW_POLICY_TRANSPARENCY_TEST)
                            .putExtra(PolicyTransparencyTestActivity.EXTRA_TEST, policyTests[i])
                            .putExtra(PolicyTransparencyTestActivity.EXTRA_SETTINGS_INTENT_ACTION,
                                    settingsIntentActions[i]);
            POLICIES[i] = Pair.create(intent, policyLabels[i]);
        }
    }

    private static final ArrayList<String> ALSO_VALID_FOR_PO = new ArrayList<String>();
    static {
        ALSO_VALID_FOR_PO.add(
                PolicyTransparencyTestActivity.TEST_CHECK_PERMITTED_ACCESSIBILITY_SERVICE);
        ALSO_VALID_FOR_PO.add(PolicyTransparencyTestActivity.TEST_CHECK_PERMITTED_INPUT_METHOD);
    }

    private int mMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.policy_transparency_test_list);
        setInfoResources(R.string.device_profile_owner_policy_transparency_test,
                R.string.device_profile_owner_policy_transparency_test_info, 0);
        setPassFailButtonClickListeners();
        setSupportMsgButtonClickListeners();

        if (!getIntent().hasExtra(EXTRA_MODE)) {
            throw new RuntimeException("PolicyTransparencyTestListActivity started without extra "
                    + EXTRA_MODE);
        }
        mMode = getIntent().getIntExtra(EXTRA_MODE, MODE_DEVICE_OWNER);
        if (mMode != MODE_DEVICE_OWNER && mMode != MODE_PROFILE_OWNER && mMode != MODE_COMP) {
            throw new RuntimeException("Unknown mode " + mMode);
        }

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        addTestsToAdapter(adapter);
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        for (String restriction :
                UserRestrictions.getUserRestrictionsForPolicyTransparency(mMode)) {
            final Intent intent = UserRestrictions.getUserRestrictionTestIntent(this, restriction);
            if (!UserRestrictions.isRestrictionValid(this, restriction)) {
                continue;
            }
            final String title = UserRestrictions.getRestrictionLabel(this, restriction);
            String testId = getTestId(title);
            intent.putExtra(PolicyTransparencyTestActivity.EXTRA_TEST_ID, testId);
            adapter.add(TestListItem.newTest(title, testId, intent, null));
        }
        if (mMode == MODE_COMP) {
            // no other policies for COMP
            return;
        }
        for (Pair<Intent, Integer> policy : POLICIES) {
            final Intent intent = policy.first;
            String test = intent.getStringExtra(PolicyTransparencyTestActivity.EXTRA_TEST);
            if (!isPolicyValid(test)) {
                continue;
            }
            if (mMode == MODE_PROFILE_OWNER && !ALSO_VALID_FOR_PO.contains(test)) {
                continue;
            }
            final String title = getString(policy.second);
            String testId = getTestId(title);
            intent.putExtra(PolicyTransparencyTestActivity.EXTRA_TITLE, title);
            intent.putExtra(PolicyTransparencyTestActivity.EXTRA_TEST_ID, testId);
            adapter.add(TestListItem.newTest(title, testId, intent, null));
        }
    }

    private String getTestId(String title) {
        if (mMode == MODE_DEVICE_OWNER) {
            return "DO_" + title;
        } else if (mMode == MODE_PROFILE_OWNER) {
            return "PO_" + title;
        } else if (mMode == MODE_COMP){
            return "COMP_" + title;
        }
        throw new RuntimeException("Unknown mode " + mMode);
    }

    private boolean isPolicyValid(String test) {
        final PackageManager pm = getPackageManager();
        switch (test) {
            case PolicyTransparencyTestActivity.TEST_CHECK_PERMITTED_INPUT_METHOD:
                return pm.hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS);
            case PolicyTransparencyTestActivity.TEST_CHECK_PERMITTED_ACCESSIBILITY_SERVICE:
                return pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
            case PolicyTransparencyTestActivity.TEST_CHECK_LOCK_SCREEN_INFO:
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            default:
                return true;
        }
    }

    private void setSupportMsgButtonClickListeners() {
        findViewById(R.id.short_msg_button).setOnClickListener(this);
        findViewById(R.id.long_msg_button).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.short_msg_button) {
            final Intent intent = new Intent(SetSupportMessageActivity.ACTION_SET_SUPPORT_MSG);
            intent.putExtra(SetSupportMessageActivity.EXTRA_SUPPORT_MSG_TYPE,
                    SetSupportMessageActivity.TYPE_SHORT_MSG);
            startActivity(intent);
        } else if (view.getId() == R.id.long_msg_button) {
            final Intent intent = new Intent(SetSupportMessageActivity.ACTION_SET_SUPPORT_MSG);
            intent.putExtra(SetSupportMessageActivity.EXTRA_SUPPORT_MSG_TYPE,
                    SetSupportMessageActivity.TYPE_LONG_MSG);
            startActivity(intent);
        }
    }

    @Override
    public String getTestId() {
        return getIntent().getStringExtra(PolicyTransparencyTestActivity.EXTRA_TEST_ID);
    }

    @Override
    public void finish() {
        super.finish();
        final Intent intent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
        intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                CommandReceiverActivity.COMMAND_CLEAR_POLICIES);
        intent.putExtra(PolicyTransparencyTestListActivity.EXTRA_MODE, mMode);
        startActivity(intent);
    }
}
