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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receiver used by {@link VoicemailBroadcastActivity} to receive {@link
 * android.telephony.TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION}, which must be a manifest
 * receiver.
 */
public class VoicemailBroadcastReceiver extends BroadcastReceiver {

    public interface ReceivedListener {

        void onReceived();
    }

    private static ReceivedListener sListener;

    public static void setListener(ReceivedListener listener) {
        sListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (sListener != null) {
            sListener.onReceived();
        }
    }
}
