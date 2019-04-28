/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.telecom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Log;
import android.telecom.TelecomManager;

/**
 * Receiver for "secret codes" broadcast by Dialer.
 */
public class DialerCodeReceiver extends BroadcastReceiver {
    // Copied from TelephonyIntents.java.
    public static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";

    // Enables extended logging for a period of time.
    public static final String TELECOM_SECRET_CODE_DEBUG_ON = "823241";

    // Disables extended logging.
    public static final String TELECOM_SECRET_CODE_DEBUG_OFF = "823240";

    // Writes a MARK to the Telecom log.
    public static final String TELECOM_SECRET_CODE_MARK = "826275";

    private final CallsManager mCallsManager;

    DialerCodeReceiver(CallsManager callsManager) {
        mCallsManager = callsManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SECRET_CODE_ACTION.equals(intent.getAction()) && intent.getData() != null &&
                intent.getData().getHost() != null) {
            if (intent.getData().getHost().equals(TELECOM_SECRET_CODE_DEBUG_ON)) {
                Log.i("DialerCodeReceiver", "Secret code used to enable extended logging mode");
                Log.setIsExtendedLoggingEnabled(true);
            } else if (intent.getData().getHost().equals(TELECOM_SECRET_CODE_DEBUG_OFF)) {
                Log.i("DialerCodeReceiver", "Secret code used to disable extended logging mode");
                Log.setIsExtendedLoggingEnabled(false);
            } else if (intent.getData().getHost().equals(TELECOM_SECRET_CODE_MARK)) {
                Log.i("DialerCodeReceiver", "Secret code used to mark logs.");

                // If there is an active call, add the "log mark" for that call; otherwise we will
                // add a non-call event.
                Call currentCall = mCallsManager.getActiveCall();
                Log.addEvent(currentCall, LogUtils.Events.USER_LOG_MARK);
            }
        }
    }
}
