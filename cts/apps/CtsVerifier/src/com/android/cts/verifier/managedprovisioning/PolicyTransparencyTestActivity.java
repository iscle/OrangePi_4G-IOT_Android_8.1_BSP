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

import android.accessibilityservice.AccessibilityService;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.view.accessibility.AccessibilityEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Map;

public class PolicyTransparencyTestActivity extends PassFailButtons.Activity implements
        View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemSelectedListener {
    public static final String ACTION_SHOW_POLICY_TRANSPARENCY_TEST =
            "com.android.cts.verifier.managedprovisioning.action.SHOW_POLICY_TRANSPARENCY_TEST";

    // Identifies a test to perform. Type String. The possible values are the ones underneath.
    public static final String EXTRA_TEST =
            "com.android.cts.verifier.managedprovisioning.extra.TEST";

    // In this case: should also contain an extra
    // {@link CommandReceiverActivity.EXTRA_USER_RESTRICTION}
    public static final String TEST_CHECK_USER_RESTRICTION = "check-user-restriction";
    public static final String TEST_CHECK_AUTO_TIME_REQUIRED = "check-auto-time-required";
    public static final String TEST_CHECK_KEYGURAD_UNREDACTED_NOTIFICATION =
            "check-keyguard-unredacted-notification";
    public static final String TEST_CHECK_LOCK_SCREEN_INFO = "check-lock-screen-info";
    public static final String TEST_CHECK_MAXIMUM_TIME_TO_LOCK = "check-maximum-time-to-lock";
    public static final String TEST_CHECK_PASSWORD_QUALITY = "check-password-quality";
    public static final String TEST_CHECK_PERMITTED_ACCESSIBILITY_SERVICE =
            "check-permitted-accessibility-service";
    public static final String TEST_CHECK_PERMITTED_INPUT_METHOD = "check-permitted-input-method";

    public static final String EXTRA_SETTINGS_INTENT_ACTION =
            "com.android.cts.verifier.managedprovisioning.extra.SETTINGS_INTENT_ACTION";
    public static final String EXTRA_TITLE =
            "com.android.cts.verifier.managedprovisioning.extra.TITLE";
    // Identifies the test in the calling activity. We will set the result for this test.
    // Type: String
    public static final String EXTRA_TEST_ID =
            "com.android.cts.verifier.managedprovisioning.extra.TEST_ID";

    private static final Map<String, PolicyTestItem> POLICY_TEST_ITEMS = new ArrayMap<>();
    static {
        POLICY_TEST_ITEMS.put(TEST_CHECK_AUTO_TIME_REQUIRED, new PolicyTestItem(
                R.string.auto_time_required_set_step,
                R.string.set_auto_time_required_action,
                R.string.set_auto_time_required_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_SET_AUTO_TIME_REQUIRED));
        POLICY_TEST_ITEMS.put(TEST_CHECK_KEYGURAD_UNREDACTED_NOTIFICATION, new PolicyTestItem(
                R.string.disallow_keyguard_unredacted_notifications_set_step,
                R.string.disallow_keyguard_unredacted_notifications_action,
                R.string.disallow_keyguard_unredacted_notifications_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_DISALLOW_KEYGUARD_UNREDACTED_NOTIFICATIONS));
        POLICY_TEST_ITEMS.put(TEST_CHECK_LOCK_SCREEN_INFO, new PolicyTestItem(
                R.string.lock_screen_info_set_step,
                R.string.set_lock_screen_info_action,
                R.string.set_lock_screen_info_widget_label,
                R.id.edit_text_widget,
                CommandReceiverActivity.COMMAND_SET_LOCK_SCREEN_INFO));
        POLICY_TEST_ITEMS.put(TEST_CHECK_MAXIMUM_TIME_TO_LOCK, new PolicyTestItem(
                R.string.maximum_time_to_lock_set_step,
                R.string.set_maximum_time_to_lock_action,
                R.string.set_maximum_time_to_lock_widget_label,
                R.id.edit_text_widget,
                CommandReceiverActivity.COMMAND_SET_MAXIMUM_TO_LOCK));
        POLICY_TEST_ITEMS.put(TEST_CHECK_PASSWORD_QUALITY, new PolicyTestItem(
                R.string.password_quality_set_step,
                R.string.set_password_quality_action,
                R.string.set_password_quality_widget_label,
                R.id.spinner_widget,
                CommandReceiverActivity.COMMAND_SET_PASSWORD_QUALITY));
        POLICY_TEST_ITEMS.put(TEST_CHECK_PERMITTED_ACCESSIBILITY_SERVICE, new PolicyTestItem(
                R.string.permitted_accessibility_services_set_step,
                R.string.set_permitted_accessibility_services_action,
                R.string.set_permitted_accessibility_services_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_ALLOW_ONLY_SYSTEM_ACCESSIBILITY_SERVICES));
        POLICY_TEST_ITEMS.put(TEST_CHECK_PERMITTED_INPUT_METHOD, new PolicyTestItem(
                R.string.permitted_input_methods_set_step,
                R.string.set_permitted_input_methods_action,
                R.string.set_permitted_input_methods_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_ALLOW_ONLY_SYSTEM_INPUT_METHODS));
    }

    // IDs of settings for {@link DevicePolicyManager#setPasswordQuality(ComponentName, int)}.
    private final int[] passwordQualityIds = new int[] {
        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED,
        DevicePolicyManager.PASSWORD_QUALITY_SOMETHING,
        DevicePolicyManager.PASSWORD_QUALITY_NUMERIC,
        DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX,
        DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC,
        DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC,
        DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
    };
    // Strings to show for each password quality setting.
    private final int[] passwordQualityLabelResIds = new int[] {
        R.string.password_quality_unspecified,
        R.string.password_quality_something,
        R.string.password_quality_numeric,
        R.string.password_quality_numeric_complex,
        R.string.password_quality_alphabetic,
        R.string.password_quality_alphanumeric,
        R.string.password_quality_complex
    };

    private String mSettingsIntentAction;
    private String mTestId;
    private String mTitle;
    private String mTest;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.policy_transparency_test);
        setPassFailButtonClickListeners();

        mTitle = getIntent().getStringExtra(EXTRA_TITLE);
        mTestId = getIntent().getStringExtra(EXTRA_TEST_ID);
        mSettingsIntentAction = getIntent().getStringExtra(EXTRA_SETTINGS_INTENT_ACTION);
        mTest = getIntent().getStringExtra(EXTRA_TEST);

        setTitle(mTitle);
        findViewById(R.id.open_settings_button).setOnClickListener(this);
        updateTestInstructions();
    }

    private void updateTestInstructions() {
        String setStep = null;
        String userAction = null;
        String widgetLabel = null;
        int widgetId = 0;
        if (TEST_CHECK_USER_RESTRICTION.equals(mTest)) {
            setStep = getString(R.string.user_restriction_set_step, mTitle);
            final String userRestriction = getIntent().getStringExtra(
                    CommandReceiverActivity.EXTRA_USER_RESTRICTION);
            userAction = UserRestrictions.getUserAction(this, userRestriction);
            widgetLabel = mTitle;
            widgetId = R.id.switch_widget;
        } else {
            final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
            setStep = getString(testItem.setStep);
            userAction = getString(testItem.userAction);
            widgetLabel = getString(testItem.widgetLabel);
            widgetId = testItem.widgetId;
        }
        ((TextView) findViewById(R.id.widget_label)).setText(widgetLabel);
        ((TextView) findViewById(R.id.test_instructions)).setText(
                getString(R.string.policy_transparency_test_instructions, setStep, userAction));
        updateWidget(widgetId);
    }

    private void updateWidget(int widgetId) {
        switch (widgetId) {
            case R.id.switch_widget: {
                Switch switchWidget = (Switch) findViewById(R.id.switch_widget);
                switchWidget.setOnCheckedChangeListener(this);
                switchWidget.setVisibility(View.VISIBLE);
            } break;
            case R.id.edit_text_widget: {
                findViewById(R.id.edit_text_widget).setVisibility(View.VISIBLE);
                Button updateButton = (Button) findViewById(R.id.update_button);
                updateButton.setOnClickListener(this);
                updateButton.setVisibility(View.VISIBLE);
            } break;
            case R.id.spinner_widget: {
                if (TEST_CHECK_PASSWORD_QUALITY.equals(mTest)) {
                    Spinner spinner = (Spinner) findViewById(R.id.spinner_widget);
                    spinner.setVisibility(View.VISIBLE);
                    spinner.setOnItemSelectedListener(this);
                    final ArrayList<String> passwordQualityLabels = new ArrayList<String>();
                    for (int resId : passwordQualityLabelResIds) {
                        passwordQualityLabels.add(getString(resId));
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter(this,
                            android.R.layout.simple_spinner_item, passwordQualityLabels);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapter);
                }
            } break;
            default: {
                throw new IllegalArgumentException("Unknown widgetId: " + widgetId);
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.open_settings_button) {
            startActivity(new Intent(mSettingsIntentAction));
        } else if (view.getId() == R.id.update_button) {
            final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
            final Intent intent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
            intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND, testItem.command);
            final EditText editText = (EditText) findViewById(R.id.edit_text_widget);
            intent.putExtra(CommandReceiverActivity.EXTRA_VALUE, editText.getText().toString());
            startActivity(intent);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final Intent intent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
        if (TEST_CHECK_USER_RESTRICTION.equals(mTest)) {
            final String userRestriction = getIntent().getStringExtra(
                    CommandReceiverActivity.EXTRA_USER_RESTRICTION);
            intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                    CommandReceiverActivity.COMMAND_SET_USER_RESTRICTION);
            intent.putExtra(CommandReceiverActivity.EXTRA_USER_RESTRICTION, userRestriction);
            intent.putExtra(CommandReceiverActivity.EXTRA_ENFORCED, isChecked);
        } else {
            final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
            intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND, testItem.command);
            intent.putExtra(CommandReceiverActivity.EXTRA_ENFORCED, isChecked);
        }
        startActivity(intent);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
        final Intent intent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
        intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND, testItem.command);
        if (TEST_CHECK_PASSWORD_QUALITY.equals(mTest)) {
            intent.putExtra(CommandReceiverActivity.EXTRA_VALUE, passwordQualityIds[(int) id]);
        }
        startActivity(intent);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }

    @Override
    public String getTestId() {
        return mTestId;
    }

    public static class DummyAccessibilityService extends AccessibilityService {

        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            // Do nothing
        }

        @Override
        public void onInterrupt() {
            // Do nothing
        }
    }

    public static class DummyInputMethod extends InputMethodService {
        @Override
        public boolean onEvaluateFullscreenMode() {
            return false;
        }

        @Override
        public boolean onEvaluateInputViewShown() {
            return false;
        }
    }

    private static class PolicyTestItem {
        public final int setStep;
        public final int userAction;
        public final int widgetLabel;
        public final int widgetId;
        public final String command;

        public PolicyTestItem(int setStep, int userAction, int widgetLabel, int widgetId,
                String command) {
            this.setStep = setStep;
            this.userAction = userAction;
            this.widgetLabel = widgetLabel;
            this.widgetId = widgetId;
            this.command = command;
        }
    }
}
