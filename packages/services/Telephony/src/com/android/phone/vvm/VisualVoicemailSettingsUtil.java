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
package com.android.phone.vvm;

import android.content.Context;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;

/**
 * Save whether or not a particular account is enabled in shared to be retrieved later.
 */
public class VisualVoicemailSettingsUtil {

    private static final String IS_ENABLED_KEY = "is_enabled";

    private static final String DEFAULT_OLD_PIN_KEY = "default_old_pin";

    public static Bundle dump(Context context, PhoneAccountHandle phoneAccountHandle){
        Bundle result = new Bundle();
        VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context,
                phoneAccountHandle);
        if (prefs.contains(IS_ENABLED_KEY)) {
            result.putBoolean(TelephonyManager.EXTRA_VISUAL_VOICEMAIL_ENABLED_BY_USER_BOOL,
                    prefs.getBoolean(IS_ENABLED_KEY, false));
        }
        result.putString(TelephonyManager.EXTRA_VOICEMAIL_SCRAMBLED_PIN_STRING,
                prefs.getString(DEFAULT_OLD_PIN_KEY));
        return result;
    }
}
