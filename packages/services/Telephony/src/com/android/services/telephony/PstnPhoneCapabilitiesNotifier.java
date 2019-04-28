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

package com.android.services.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.internal.telephony.Phone;
import com.android.internal.util.Preconditions;
import com.android.phone.PhoneUtils;

/**
 * Listens to phone's capabilities changed event and notifies Telecomm. One instance of these exists
 * for each of the telephony-based call services.
 */
final class PstnPhoneCapabilitiesNotifier {
    private static final int EVENT_VIDEO_CAPABILITIES_CHANGED = 1;

    /**
     * Listener called when video capabilities have changed.
     */
    public interface Listener {
        public void onVideoCapabilitiesChanged(boolean isVideoCapable);
    }

    private final Phone mPhone;
    private final Listener mListener;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_VIDEO_CAPABILITIES_CHANGED:
                    handleVideoCapabilitesChanged((AsyncResult) msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    /*package*/
    PstnPhoneCapabilitiesNotifier(Phone phone, Listener listener) {
        Preconditions.checkNotNull(phone);

        mPhone = phone;
        mListener = listener;

        registerForNotifications();
    }

    /*package*/
    void teardown() {
        unregisterForNotifications();
    }

    private void registerForNotifications() {
        if (mPhone != null) {
            Log.d(this, "Registering: " + mPhone);
            mPhone.registerForVideoCapabilityChanged(mHandler, EVENT_VIDEO_CAPABILITIES_CHANGED,
                    null);
        }
    }

    private void unregisterForNotifications() {
        if (mPhone != null) {
            Log.d(this, "Unregistering: " + mPhone);
            mPhone.unregisterForVideoCapabilityChanged(mHandler);
        }
    }

    private void handleVideoCapabilitesChanged(AsyncResult ar) {
        try {
            boolean isVideoCapable = (Boolean) ar.result;
            Log.i(this, "handleVideoCapabilitesChanged. Video capability - " + isVideoCapable);
            mListener.onVideoCapabilitiesChanged(isVideoCapable);
        } catch (Exception e) {
            Log.w(this, "handleVideoCapabilitesChanged. Exception=" + e);
        }
    }

    private int newCapabilities(int capabilities, int capability, boolean set) {
        if (set) {
            capabilities |= capability;
        } else {
            capabilities &= ~capability;
        }
        return capabilities;
    }
}
