/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.Parcelable;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/**
 * This activity receives the standard telecom intent to open settings for a PhoneAccount. It
 * translates the incoming phone account to a SIP profile and opens the corresponding
 * PreferenceActivity for said profile.
 */
public final class SipPhoneAccountSettingsActivity extends Activity {
    private static final String TAG = "SipSettingsActivity";

    /** ${inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Log.i(TAG, "" + intent);
        if (intent != null) {
            PhoneAccountHandle accountHandle = (PhoneAccountHandle)
                    intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
            Log.i(TAG, "" + accountHandle);

            if (accountHandle != null) {
                SipProfileDb profileDb = new SipProfileDb(this);
                String profileName = SipUtil.getSipProfileNameFromPhoneAccount(accountHandle);
                SipProfile profile = profileDb.retrieveSipProfileFromName(profileName);
                if (profile != null) {
                    Intent settingsIntent = new Intent(this, SipEditor.class);
                    settingsIntent.putExtra(SipSettings.KEY_SIP_PROFILE, (Parcelable) profile);
                    startActivity(settingsIntent);
                }
            }
        }

        finish();
    }
}
