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

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Log;
import android.telephony.DisconnectCause;

/**
 * Handles actions from the self-managed calling sample app incoming call UX.
 */
public class SelfManagedCallNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_ANSWER_CALL =
            "com.android.server.telecom.testapps.action.ANSWER_CALL";
    public static final String ACTION_REJECT_CALL =
            "com.android.server.telecom.testapps.action.REJECT_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int callId = intent.getIntExtra(IncomingSelfManagedCallActivity.EXTRA_CALL_ID, 0);
        NotificationManager notificationManager = context.getSystemService(
                NotificationManager.class);
        SelfManagedConnection connection = SelfManagedCallList.getInstance()
                .getConnectionById(callId);
        switch (action) {
            case ACTION_ANSWER_CALL:
                Log.i(this, "onReceive - answerCall %d", callId);
                if (connection != null) {
                    connection.setConnectionActive();
                }
                notificationManager.cancel(SelfManagedConnection.CALL_NOTIFICATION, callId);
                break;

            case ACTION_REJECT_CALL:
                Log.i(this, "onReceive - rejectCall %d", callId);
                if (connection != null) {
                    connection.setConnectionDisconnected(DisconnectCause.INCOMING_REJECTED);
                    connection.destroy();
                }
                notificationManager.cancel(SelfManagedConnection.CALL_NOTIFICATION, callId);
                break;
        }
    }
}
