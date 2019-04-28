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

package com.android.server.telecom;

import android.app.StatusBarManager;
import android.content.Context;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;

// TODO: Needed for move to system service: import com.android.internal.R;

/**
 * Manages the special status bar notifications used by the phone app.
 */
@VisibleForTesting
public class StatusBarNotifier extends CallsManagerListenerBase {
    private static final String SLOT_MUTE = "mute";
    private static final String SLOT_SPEAKERPHONE = "speakerphone";

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final StatusBarManager mStatusBarManager;

    private boolean mIsShowingMute;
    private boolean mIsShowingSpeakerphone;

    StatusBarNotifier(Context context, CallsManager callsManager) {
        mContext = context;
        mCallsManager = callsManager;
        mStatusBarManager = (StatusBarManager) context.getSystemService(Context.STATUS_BAR_SERVICE);
    }

    /** ${inheritDoc} */
    @Override
    public void onCallRemoved(Call call) {
        if (!mCallsManager.hasAnyCalls()) {
            notifyMute(false);
            notifySpeakerphone(false);
        }
    }

    @VisibleForTesting
    public void notifyMute(boolean isMuted) {
        // Never display anything if there are no calls.
        if (!mCallsManager.hasAnyCalls()) {
            isMuted = false;
        }

        if (mIsShowingMute == isMuted) {
            return;
        }

        Log.d(this, "Mute status bar icon being set to %b", isMuted);

        if (isMuted) {
            mStatusBarManager.setIcon(
                    SLOT_MUTE,
                    android.R.drawable.stat_notify_call_mute,
                    0,  /* iconLevel */
                    mContext.getString(R.string.accessibility_call_muted));
        } else {
            mStatusBarManager.removeIcon(SLOT_MUTE);
        }
        mIsShowingMute = isMuted;
    }

    @VisibleForTesting
    public void notifySpeakerphone(boolean isSpeakerphone) {
        // Never display anything if there are no calls.
        if (!mCallsManager.hasAnyCalls()) {
            isSpeakerphone = false;
        }

        if (mIsShowingSpeakerphone == isSpeakerphone) {
            return;
        }

        Log.d(this, "Speakerphone status bar icon being set to %b", isSpeakerphone);

        if (isSpeakerphone) {
            mStatusBarManager.setIcon(
                    SLOT_SPEAKERPHONE,
                    android.R.drawable.stat_sys_speakerphone,
                    0,  /* iconLevel */
                    mContext.getString(R.string.accessibility_speakerphone_enabled));
        } else {
            mStatusBarManager.removeIcon(SLOT_SPEAKERPHONE);
        }
        mIsShowingSpeakerphone = isSpeakerphone;
    }
}
