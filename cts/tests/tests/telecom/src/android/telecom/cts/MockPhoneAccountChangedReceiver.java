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

package android.telecom.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

/**
 * Receives {@link android.telecom.TelecomManager#ACTION_PHONE_ACCOUNT_REGISTERED} and
 * {@link android.telecom.TelecomManager#ACTION_PHONE_ACCOUNT_UNREGISTERED} intents.
 */
public class MockPhoneAccountChangedReceiver extends BroadcastReceiver {
    public interface IntentListener {
        void onPhoneAccountRegistered(PhoneAccountHandle handle);
        void onPhoneAccountUnregistered(PhoneAccountHandle handle);
    }

    private static IntentListener sIntentListener = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (sIntentListener != null) {
            if (TelecomManager.ACTION_PHONE_ACCOUNT_REGISTERED.equals(intent.getAction())) {
                sIntentListener.onPhoneAccountRegistered(intent.getParcelableExtra(
                        TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
            } else if (TelecomManager.ACTION_PHONE_ACCOUNT_UNREGISTERED.equals(
                    intent.getAction())) {
                sIntentListener.onPhoneAccountUnregistered(intent.getParcelableExtra(
                        TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
            }
        }
    }

    public static void setIntentListener(IntentListener listener) {
        sIntentListener = listener;
    }
}
