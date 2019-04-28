/**
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

package com.android.phone.settings;

import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandsInterface;

/**
 * Settings for a voicemail provider, including any conditional forwarding information.
 */
public class VoicemailProviderSettings {

    // If no forwarding is set, leave the forwarding number unchanged from its current value.
    public static final CallForwardInfo[] NO_FORWARDING = null;

    /**
     * Reasons for the forwarding settings we are going to save.
     */
    public static final int [] FORWARDING_SETTINGS_REASONS = new int[] {
        CommandsInterface.CF_REASON_UNCONDITIONAL,
        CommandsInterface.CF_REASON_BUSY,
        CommandsInterface.CF_REASON_NO_REPLY,
        CommandsInterface.CF_REASON_NOT_REACHABLE
    };

    private String mVoicemailNumber;
    private CallForwardInfo[] mForwardingSettings;

    /**
     * Constructs settings object, setting all conditional forwarding to the specified number
     */
    public VoicemailProviderSettings(
            String voicemailNumber, String forwardingNumber, int timeSeconds) {
        mVoicemailNumber = voicemailNumber;
        if (forwardingNumber == null || forwardingNumber.length() == 0) {
            mForwardingSettings = NO_FORWARDING;
        } else {
            mForwardingSettings = new CallForwardInfo[FORWARDING_SETTINGS_REASONS.length];
            for (int i = 0; i < mForwardingSettings.length; i++) {
                CallForwardInfo fi = new CallForwardInfo();
                mForwardingSettings[i] = fi;
                fi.reason = FORWARDING_SETTINGS_REASONS[i];
                fi.status = (fi.reason == CommandsInterface.CF_REASON_UNCONDITIONAL) ? 0 : 1;
                fi.serviceClass = CommandsInterface.SERVICE_CLASS_VOICE;
                fi.toa = PhoneNumberUtils.TOA_International;
                fi.number = forwardingNumber;
                fi.timeSeconds = timeSeconds;
            }
        }
    }

    public VoicemailProviderSettings(String voicemailNumber, CallForwardInfo[] infos) {
        mVoicemailNumber = voicemailNumber;
        mForwardingSettings = infos;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof VoicemailProviderSettings)) {
            return false;
        }

        final VoicemailProviderSettings v = (VoicemailProviderSettings) o;
        return ((mVoicemailNumber == null && v.getVoicemailNumber() == null)
                || (mVoicemailNumber != null && mVoicemailNumber.equals(v.getVoicemailNumber()))
                        && forwardingSettingsEqual(mForwardingSettings, v.getForwardingSettings()));
    }

    @Override
    public String toString() {
        return mVoicemailNumber + ((mForwardingSettings == null) ? ""
                : ", " + mForwardingSettings.toString());
    }

    public String getVoicemailNumber() {
        return mVoicemailNumber;
    }

    public CallForwardInfo[] getForwardingSettings() {
        return mForwardingSettings;
    }

    private boolean forwardingSettingsEqual(CallForwardInfo[] infos1, CallForwardInfo[] infos2) {
        if (infos1 == infos2) {
            return true;
        }
        if (infos1 == null || infos2 == null) {
            return false;
        }
        if (infos1.length != infos2.length) {
            return false;
        }

        for (int i = 0; i < infos1.length; i++) {
            CallForwardInfo i1 = infos1[i];
            CallForwardInfo i2 = infos2[i];
            if (i1.status != i2.status
                    || i1.reason != i2.reason
                    || i1.serviceClass != i2.serviceClass
                    || i1.toa != i2.toa
                    || i1.number != i2.number
                    || i1.timeSeconds != i2.timeSeconds) {
                return false;
            }
        }
        return true;
    }
}
