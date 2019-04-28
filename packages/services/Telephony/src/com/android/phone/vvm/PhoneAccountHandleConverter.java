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

import android.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.PhoneUtils;
import com.android.phone.vvm.VvmLog;

/**
 * Utility to convert between PhoneAccountHandle and subId, which is a common operation in OMTP
 * client
 *
 * TODO(b/28977379): remove dependency on PhoneUtils and use public APIs
 */
public class PhoneAccountHandleConverter {

    private static final String TAG = "PhoneAccountHndCvtr";

    @Nullable
    public static PhoneAccountHandle fromSubId(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            VvmLog.e(TAG, "invalid subId " + subId);
            return null;
        }
        // Calling PhoneUtils.makePstnPhoneAccountHandle() with a phoneId might throw a NPE if the
        // phone object cannot be found, so the Phone object should be created and checked here.
        Phone phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
        if (phone == null) {
            VvmLog.e(TAG, "Unable to find Phone for subId " + subId);
            return null;
        }
        return PhoneUtils.makePstnPhoneAccountHandle(phone);
    }

    public static int toSubId(PhoneAccountHandle handle) {
        return PhoneUtils.getSubIdForPhoneAccountHandle(handle);
    }

    private PhoneAccountHandleConverter() {
    }
}
