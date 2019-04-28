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

import com.android.internal.annotations.VisibleForTesting;
import android.telecom.Log;

/**
 * Handles acquisition and release of wake locks relating to call state.
 */
@VisibleForTesting
public class InCallWakeLockController extends CallsManagerListenerBase {

    private final TelecomWakeLock mTelecomWakeLock;
    private final CallsManager mCallsManager;

    @VisibleForTesting
    public InCallWakeLockController(TelecomWakeLock telecomWakeLock, CallsManager callsManager) {
        mCallsManager = callsManager;

        mTelecomWakeLock = telecomWakeLock;
        mTelecomWakeLock.setReferenceCounted(false);
    }

    @Override
    public void onCallAdded(Call call) {
        if (call.isExternalCall()) {
            return;
        }
        handleWakeLock();
    }

    @Override
    public void onCallRemoved(Call call) {
        if (call.isExternalCall()) {
            return;
        }
        handleWakeLock();
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        if (call.isExternalCall()) {
            return;
        }
        handleWakeLock();
    }

    private void handleWakeLock() {
        // We grab a full lock as long as there exists a ringing call.
        Call ringingCall = mCallsManager.getRingingCall();
        if (ringingCall != null) {
            mTelecomWakeLock.acquire();
            Log.i(this, "Acquiring full wake lock");
        } else {
            mTelecomWakeLock.release(0);
            Log.i(this, "Releasing full wake lock");
        }
    }
}
