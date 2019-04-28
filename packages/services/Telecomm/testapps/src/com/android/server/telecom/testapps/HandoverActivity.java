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

package com.android.server.telecom.testapps;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.Log;
import android.telecom.TelecomManager;
import android.telephony.DisconnectCause;
import android.view.View;
import android.widget.Button;

/**
 * Displays a UX to the user confirming whether they want to handover a call to the self-managed CS.
 */
public class HandoverActivity extends Activity {
    public static final String EXTRA_CALL_ID = "com.android.server.telecom.testapps.extra.CALL_ID";

    private Button mAcceptHandoverButton;
    private Button mRejectHandoverButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent launchingIntent = getIntent();
        int callId = launchingIntent.getIntExtra(EXTRA_CALL_ID, 0);
        Log.i(this, "showing fullscreen upgrade ux for call id %d", callId);

        setContentView(R.layout.self_managed_handover);
        final SelfManagedConnection connection = SelfManagedCallList.getInstance()
                .getConnectionById(callId);
        mAcceptHandoverButton = (Button) findViewById(R.id.acceptUpgradeButton);
        mAcceptHandoverButton.setOnClickListener((View v) -> {
            if (connection != null) {
                connection.setConnectionActive();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.setClass(this, SelfManagedCallingActivity.class);
                startActivity(intent);
            }
            finish();
        });
        mRejectHandoverButton = (Button) findViewById(R.id.rejectUpgradeButton);
        mRejectHandoverButton.setOnClickListener((View v) -> {
            if (connection != null) {
                connection.setConnectionDisconnected(DisconnectCause.INCOMING_REJECTED);
                connection.destroy();
                TelecomManager tm = TelecomManager.from(this);
                tm.showInCallScreen(false);
            }
            finish();
        });
    }
}
