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
import android.telephony.DisconnectCause;
import android.view.View;
import android.widget.Button;

import com.android.server.telecom.testapps.R;

/**
 * Sample Incoming Call activity for Self-Managed calls.
 */
public class IncomingSelfManagedCallActivity extends Activity {
    public static final String EXTRA_CALL_ID = "com.android.server.telecom.testapps.extra.CALL_ID";

    private Button mAnswerCallButton;
    private Button mRejectCallButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent launchingIntent = getIntent();
        int callId = launchingIntent.getIntExtra(EXTRA_CALL_ID, 0);
        Log.i(this, "showing fullscreen answer ux for call id %d", callId);

        setContentView(R.layout.self_managed_incoming_call);
        final SelfManagedConnection connection = SelfManagedCallList.getInstance()
                .getConnectionById(callId);
        mAnswerCallButton = (Button) findViewById(R.id.answerCallButton);
        mAnswerCallButton.setOnClickListener((View v) -> {
            if (connection != null) {
                connection.setConnectionActive();
            }
            finish();
        });
        mRejectCallButton = (Button) findViewById(R.id.rejectCallButton);
        mRejectCallButton.setOnClickListener((View v) -> {
            if (connection != null) {
                connection.setConnectionDisconnected(DisconnectCause.INCOMING_REJECTED);
                connection.destroy();
            }
            finish();
        });
    }
}