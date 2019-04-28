/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.net.Uri;
import android.os.UserHandle;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telephony.CallerInfo;

/**
 * Creates a notification for calls that the user missed (neither answered nor rejected).
 */
public interface MissedCallNotifier extends CallsManager.CallsManagerListener {
    class CallInfoFactory {
        public CallInfo makeCallInfo(CallerInfo callerInfo, PhoneAccountHandle phoneAccountHandle,
                Uri handle, long creationTimeMillis) {
            return new CallInfo(callerInfo, phoneAccountHandle, handle, creationTimeMillis);
        }
    }

    class CallInfo {
        private CallerInfo mCallerInfo;
        private PhoneAccountHandle mPhoneAccountHandle;
        private Uri mHandle;
        private long mCreationTimeMillis;

        public CallInfo(CallerInfo callerInfo, PhoneAccountHandle phoneAccountHandle, Uri handle,
                long creationTimeMillis) {
            mCallerInfo = callerInfo;
            mPhoneAccountHandle = phoneAccountHandle;
            mHandle = handle;
            mCreationTimeMillis = creationTimeMillis;
        }

        public CallInfo(Call call) {
            mCallerInfo = call.getCallerInfo();
            mPhoneAccountHandle = call.getTargetPhoneAccount();
            mHandle = call.getHandle();
            mCreationTimeMillis = call.getCreationTimeMillis();
        }

        public CallerInfo getCallerInfo() {
            return mCallerInfo;
        }

        public PhoneAccountHandle getPhoneAccountHandle() {
            return mPhoneAccountHandle;
        }

        public Uri getHandle() {
            return mHandle;
        }

        public String getHandleSchemeSpecificPart() {
            return mHandle == null ? null : mHandle.getSchemeSpecificPart();
        }

        public long getCreationTimeMillis() {
            return mCreationTimeMillis;
        }

        public String getPhoneNumber() {
            return mCallerInfo == null ? null : mCallerInfo.phoneNumber;
        }

        public String getName() {
            return mCallerInfo == null ? null : mCallerInfo.name;
        }
    }

    void clearMissedCalls(UserHandle userHandle);

    void showMissedCallNotification(CallInfo call);

    void reloadAfterBootComplete(CallerInfoLookupHelper callerInfoLookupHelper,
            CallInfoFactory callInfoFactory);

    void reloadFromDatabase(CallerInfoLookupHelper callerInfoLookupHelper,
            CallInfoFactory callInfoFactory, UserHandle userHandle);

    void setCurrentUserHandle(UserHandle userHandle);
}
