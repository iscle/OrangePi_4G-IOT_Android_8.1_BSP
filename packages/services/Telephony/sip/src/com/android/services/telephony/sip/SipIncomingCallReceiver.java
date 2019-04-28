/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.services.telephony.sip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/**
 * Broadcast receiver that handles explicit incoming call intents
 */
public class SipIncomingCallReceiver extends BroadcastReceiver {
    private static final String PREFIX = "[SipIncomingCallReceiver] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */

    @Override
    public void onReceive(Context context, final Intent intent) {
        String action = intent.getAction();

        if (!isRunningInSystemUser()) {
            if (VERBOSE) log("SipIncomingCallReceiver only run in system user, ignore " + action);
            return;
        }

        if (!SipUtil.isVoipSupported(context)) {
            if (VERBOSE) log("SIP VOIP not supported: " + action);
            return;
        }

        if (action.equals(SipManager.ACTION_SIP_INCOMING_CALL)) {
            takeCall(context, intent);
        } else {
            if (VERBOSE) log("onReceive, action not processed: " + action);
        }
    }

    private void takeCall(Context context, Intent intent) {
        if (VERBOSE) log("takeCall, intent: " + intent);
        PhoneAccountHandle accountHandle = null;
        try {
            accountHandle = intent.getParcelableExtra(SipUtil.EXTRA_PHONE_ACCOUNT);
        } catch (ClassCastException e) {
            log("takeCall, Bad account handle detected. Bailing!");
            return;
        }
        if (accountHandle != null) {
            Bundle extras = new Bundle();
            extras.putParcelable(SipUtil.EXTRA_INCOMING_CALL_INTENT, intent);
            TelecomManager tm = TelecomManager.from(context);
            PhoneAccount phoneAccount = tm.getPhoneAccount(accountHandle);
            if (phoneAccount != null && phoneAccount.isEnabled()) {
                tm.addNewIncomingCall(accountHandle, extras);
            } else {
                log("takeCall, PhoneAccount is disabled. Not accepting incoming call...");
            }
        }
    }

    private boolean isRunningInSystemUser() {
        return UserHandle.myUserId() == UserHandle.USER_SYSTEM;
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
