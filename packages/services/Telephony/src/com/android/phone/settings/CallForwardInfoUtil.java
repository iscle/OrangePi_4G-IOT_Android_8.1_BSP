/**
 * Copyright (C) 2008 The Android Open Source Project
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

import android.os.Message;
import android.util.Log;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.phone.PhoneGlobals;

public class CallForwardInfoUtil {
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);
    private static final String LOG_TAG = CallForwardInfoUtil.class.getSimpleName();

    /**
     * @see CallForwardInfo#status
     */
    private static final int CALL_FORWARD_INFO_INACTIVE_STATUS = 0;
    private static final int CALL_FORWARD_INFO_ACTIVE_STATUS = 1;

    /**
     * Returns the first CallForwardInfo in infos which has the specified reason.
     * @param infos array of CallForwardInfo objects.
     * @param reason The reason we want to find a CallForwardInfo for.
     */
    public static CallForwardInfo infoForReason(CallForwardInfo[] infos, int reason) {
        if (infos == null) {
            return null;
        }

        CallForwardInfo result = null;
        for (int i = 0; i < infos.length; i++) {
            if (infos[i].reason == reason) {
                return infos[i];
            }
        }

        return null;
    }

    /**
     * Update, unless we're disabling a type of forwarding and it's already disabled.
     */
    public static boolean isUpdateRequired(CallForwardInfo oldInfo, CallForwardInfo newInfo) {
        if (oldInfo == null) {
            return true;
        }

        if (newInfo.status == CALL_FORWARD_INFO_INACTIVE_STATUS
                && oldInfo.status == CALL_FORWARD_INFO_INACTIVE_STATUS) {
            return false;
        }

        return true;
    }

    /**
     * Sets the call forwarding option on the phone, with the command interface action set to the
     * appropriate value depending on whether the CallForwardInfo is active or inactive.
     */
    public static void setCallForwardingOption(Phone phone, CallForwardInfo info, Message message) {
        int commandInterfaceCfAction = info.status == CALL_FORWARD_INFO_ACTIVE_STATUS
                ? CommandsInterface.CF_ACTION_REGISTRATION
                : CommandsInterface.CF_ACTION_DISABLE;

        phone.setCallForwardingOption(commandInterfaceCfAction,
                info.reason,
                info.number,
                info.timeSeconds,
                message);
    }

    /**
     * Retrieves a CallForwardInfo object of type {@link CommandInterface.SERVICE_CLASS_VOICE} from
     * the array of CallForwardInfo objects. If one does not exist, instantiates an CallForwardInfo
     * object which disables the specified reason.
     */
    public static CallForwardInfo getCallForwardInfo(CallForwardInfo[] infos, int reason) {
        CallForwardInfo info = null;
        for (int i = 0 ; i < infos.length; i++) {
            if (isServiceClassVoice(infos[i])) {
                info = infos[i];
                break;
            }
        }

        if (info == null) {
            // If there is  no info, create a CallForwardInfo to disable this reason.
            info = new CallForwardInfo();
            info.status = CALL_FORWARD_INFO_INACTIVE_STATUS;
            info.reason = reason;
            info.serviceClass = CommandsInterface.SERVICE_CLASS_VOICE;

            if (DBG) Log.d(LOG_TAG, "Created default info for reason: " + reason);
        } else {
            if (!hasForwardingNumber(info)) {
                info.status = CALL_FORWARD_INFO_INACTIVE_STATUS;
            }

            if (DBG) Log.d(LOG_TAG, "Retrieved  " + info.toString() + " for " + reason);
        }

        return info;
    }

    private static boolean isServiceClassVoice(CallForwardInfo info) {
        return (info.serviceClass & CommandsInterface.SERVICE_CLASS_VOICE) != 0;
    }

    private static boolean hasForwardingNumber(CallForwardInfo info) {
        return info.number != null && info.number.length() > 0;
    }
}
