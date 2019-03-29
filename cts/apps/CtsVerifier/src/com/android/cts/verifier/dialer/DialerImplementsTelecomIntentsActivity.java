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

package com.android.cts.verifier.dialer;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/** Tests that a third party dialer implements certain telecom intents. */
public class DialerImplementsTelecomIntentsActivity extends PassFailButtons.Activity {

  private Button mLaunchCallSettingsButton;
  private CheckBox mLaunchCallSettingsCheckBox;
  private Button mLaunchShortSmsAnswerButton;
  private CheckBox mLaunchShortSmsAnswerCheckBox;
  private Button mLaunchCallingAccountsSettingsButton;
  private CheckBox mLaunchCallingAccountsSettingsCheckBox;
  private Button mLaunchAccessibilitySettingsButton;
  private CheckBox mLaunchAccessibilitySettingsCheckBox;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    View view = getLayoutInflater().inflate(R.layout.dialer_telecom_intents, null);
    setContentView(view);
    setInfoResources(
        R.string.dialer_telecom_intents_test,
        R.string.dialer_telecom_intents_test_instructions,
        -1);
    setPassFailButtonClickListeners();
    getPassButton().setEnabled(false);

    mLaunchCallSettingsCheckBox = findViewById(R.id.dialer_telecom_intents_call_settings_check_box);
    mLaunchShortSmsAnswerCheckBox = findViewById(R.id.dialer_telecom_intents_short_sms_check_box);
    mLaunchCallingAccountsSettingsCheckBox =
        findViewById(R.id.dialer_telecom_intents_calling_accounts_check_box);
    mLaunchAccessibilitySettingsCheckBox =
        findViewById(R.id.dialer_telecom_intents_accessibility_settings_check_box);

    mLaunchCallSettingsCheckBox.setOnCheckedChangeListener(
        (CompoundButton unusedButton, boolean unusedBoolean) -> onCheckedChangeListener());
    mLaunchShortSmsAnswerCheckBox.setOnCheckedChangeListener(
        (CompoundButton unusedButton, boolean unusedBoolean) -> onCheckedChangeListener());
    mLaunchCallingAccountsSettingsCheckBox.setOnCheckedChangeListener(
        (CompoundButton unusedButton, boolean unusedBoolean) -> onCheckedChangeListener());
    mLaunchAccessibilitySettingsCheckBox.setOnCheckedChangeListener(
        (CompoundButton unusedButton, boolean unusedBoolean) -> onCheckedChangeListener());

    mLaunchCallSettingsButton = findViewById(R.id.dialer_telecom_intents_call_settings);
    mLaunchCallSettingsButton.setOnClickListener(
        (View unused) -> startActivity(new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)));

    mLaunchShortSmsAnswerButton = findViewById(R.id.dialer_telecom_intents_short_sms);
    mLaunchShortSmsAnswerButton.setOnClickListener(
        (View unused) ->
            startActivity(new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS)));

    mLaunchCallingAccountsSettingsButton =
        findViewById(R.id.dialer_telecom_intents_calling_accounts);
    mLaunchCallingAccountsSettingsButton.setOnClickListener(
        (View unused) -> startActivity(new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)));

    mLaunchAccessibilitySettingsButton =
        findViewById(R.id.dialer_telecom_intents_accessibility_settings);
    mLaunchAccessibilitySettingsButton.setOnClickListener(
        (View unused) ->
            startActivity(new Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS)));
  }

  private void onCheckedChangeListener() {
    if (mLaunchCallSettingsCheckBox.isChecked()
        && mLaunchShortSmsAnswerCheckBox.isChecked()
        && mLaunchCallingAccountsSettingsCheckBox.isChecked()
        && mLaunchAccessibilitySettingsCheckBox.isChecked()) {
      getPassButton().setEnabled(true);
    } else {
      getPassButton().setEnabled(false);
    }
  }
}
