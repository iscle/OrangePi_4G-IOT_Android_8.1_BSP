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
 * limitations under the License
 */

package com.android.phone.vvm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.VoicemailContract;
import android.telephony.SubscriptionManager;
import android.telephony.VisualVoicemailSms;

/**
 * Receives the SMS filtered by {@link com.android.internal.telephony.VisualVoicemailSmsFilter} and
 * redirect it to the visual voicemail client. The redirection is required to let telephony service
 * handle tasks with {@link RemoteVvmTaskManager}
 */
public class VvmSmsReceiver extends BroadcastReceiver {

    private static final String TAG = "VvmSmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        VisualVoicemailSms sms = intent.getExtras()
                .getParcelable(VoicemailContract.EXTRA_VOICEMAIL_SMS);

        if (sms.getPhoneAccountHandle() == null) {
            // This should never happen
            VvmLog.e(TAG, "Received message for null phone account");
            return;
        }

        int subId = PhoneAccountHandleConverter.toSubId(sms.getPhoneAccountHandle());
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            VvmLog.e(TAG, "Received message for invalid subId");
            return;
        }

        if (RemoteVvmTaskManager.hasRemoteService(context, subId)) {
            VvmLog.i(TAG, "Sending SMS received event to remote service");
            RemoteVvmTaskManager.startSmsReceived(context, sms);
        } else {
            VvmLog.w(TAG, "Sending SMS received event to remote service");
        };
    }
}
