/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Verify elimination of the vulnerability that allows using an intent (with
 * an extra) that bypasses the confirmation step of entering the original password/pattern
 * before creating a new password/pattern for the lockscreen.
 *
 * First ask the user to ensure that some pattern or password is set for the lockscreen.
 * Then issue the intent that was used to exploit the vulnerability and ask the user
 * if he/she was prompted for the original pattern or password. If the user wasn't prompted,
 * the test fails.
 */
public class LockConfirmBypassTest extends PassFailButtons.Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the UI.
        setContentView(R.layout.pass_fail_lockconfirm);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.lock_confirm_test_title, R.string.lock_confirm_message, -1);
        // Get the lock set button and attach the listener.
        Button lockSetButton = (Button) findViewById(R.id.lock_set_btn);
        lockSetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent setPasswordIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
                startActivity(setPasswordIntent);
            }
        });
        // Get the lock change button and attach the listener.
        Button lockChangeButton = (Button) findViewById(R.id.lock_change_btn);
        lockChangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent setPasswordIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
                setPasswordIntent.putExtra("confirm_credentials", false);
                startActivity(setPasswordIntent);
            }
        });
    }

}
