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
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Context;
import android.os.PowerManager;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;

/**
 * This class manages the proximity sensor and allows callers to turn it on and off.
 */
public class ProximitySensorManager extends CallsManagerListenerBase {

    private final CallsManager mCallsManager;
    private final TelecomWakeLock mTelecomWakeLock;

    public ProximitySensorManager(TelecomWakeLock telecomWakeLock, CallsManager callsManager) {

        mTelecomWakeLock = telecomWakeLock;
        mCallsManager = callsManager;
        Log.d(this, "onCreate: mProximityWakeLock: ", mTelecomWakeLock);
    }

    @Override
    public void onCallRemoved(Call call) {
        if (call.isExternalCall()) {
            return;
        }
        if (mCallsManager.getCalls().isEmpty()) {
            Log.i(this, "All calls removed, resetting proximity sensor to default state");
            turnOff(true);
        }
        super.onCallRemoved(call);
    }

    /**
     * Turn the proximity sensor on.
     */
    @VisibleForTesting
    public void turnOn() {
        if (mCallsManager.getCalls().isEmpty()) {
            Log.w(this, "Asking to turn on prox sensor without a call? I don't think so.");
            return;
        }

        mTelecomWakeLock.acquire();
    }

    /**
     * Turn the proximity sensor off.
     * @param screenOnImmediately
     */
    @VisibleForTesting
    public void turnOff(boolean screenOnImmediately) {
        int flags = (screenOnImmediately ? 0 : PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
        mTelecomWakeLock.release(flags);
    }
}
