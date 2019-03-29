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

package com.android.cts.verifier.voicemail;

import android.telecom.PhoneAccountHandle;
import android.telephony.VisualVoicemailService;
import android.telephony.VisualVoicemailSms;

/**
 * Forwards {@link VisualVoicemailService} events to tests. The CTS Verifier app needs to be set to
 * the default dialer for it to receive the events.
 */
public class CtsVisualVoicemailService extends VisualVoicemailService {

    public abstract static class Callback {

        public abstract void onCellServiceConnected(VisualVoicemailTask task,
                PhoneAccountHandle phoneAccountHandle);

        public abstract void onSimRemoved(VisualVoicemailTask task,
                PhoneAccountHandle phoneAccountHandle);
    }

    private static Callback sCallback;

    public static void setCallback(Callback callback) {
        sCallback = callback;
    }

    @Override
    public void onCellServiceConnected(VisualVoicemailTask task,
            PhoneAccountHandle phoneAccountHandle) {
        if (sCallback != null) {
            sCallback.onCellServiceConnected(task, phoneAccountHandle);
        }
        task.finish();
    }

    @Override
    public void onSmsReceived(VisualVoicemailTask task, VisualVoicemailSms sms) {
        task.finish();
    }

    @Override
    public void onSimRemoved(VisualVoicemailTask task, PhoneAccountHandle phoneAccountHandle) {
        if (sCallback != null) {
            sCallback.onSimRemoved(task, phoneAccountHandle);
        }
        task.finish();
    }

    @Override
    public void onStopped(VisualVoicemailTask task) {
        task.finish();
    }
}
