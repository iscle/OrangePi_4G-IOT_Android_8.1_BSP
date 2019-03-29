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

package com.android.cts.verifier.voicemail;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Tests {@link android.telephony.TelephonyManager#METADATA_HIDE_VOICEMAIL_SETTINGS_MENU}
 */
public class CallSettingsCheckActivity extends PassFailButtons.Activity {

    private DefaultDialerChanger mDefaultDialerChanger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.voicemail_hide_in_call_settings, null);
        setContentView(view);
        setInfoResources(R.string.call_settings_check_test,
                R.string.call_settings_check_instructions, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mDefaultDialerChanger = new DefaultDialerChanger(this);

        findViewById(R.id.open_call_settings).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS));
                    }
                }
        );

        findViewById(R.id.settings_hidden).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getPassButton().setEnabled(true);
                        setTestResultAndFinish(true);
                    }
                }
        );

        findViewById(R.id.settings_not_hidden).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setTestResultAndFinish(false);
                    }
                }
        );

    }

    @Override
    protected void onDestroy() {
        mDefaultDialerChanger.destroy();
        super.onDestroy();
    }
}
