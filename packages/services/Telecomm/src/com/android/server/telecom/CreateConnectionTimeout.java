/*
 * Copyright 2015, The Android Open Source Project
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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Log;
import android.telecom.Logging.Runnable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;

import java.util.Collection;
import java.util.Objects;

/**
 * Registers a timeout for a call and disconnects the call when the timeout expires.
 */
final class CreateConnectionTimeout extends Runnable {
    private final Context mContext;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final ConnectionServiceWrapper mConnectionService;
    private final Call mCall;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsRegistered;
    private boolean mIsCallTimedOut;

    CreateConnectionTimeout(Context context, PhoneAccountRegistrar phoneAccountRegistrar,
            ConnectionServiceWrapper service, Call call) {
        super("CCT", null /*lock*/);
        mContext = context;
        mPhoneAccountRegistrar = phoneAccountRegistrar;
        mConnectionService = service;
        mCall = call;
    }

    boolean isTimeoutNeededForCall(Collection<PhoneAccountHandle> accounts,
            PhoneAccountHandle currentAccount) {
        // Non-emergency calls timeout automatically at the radio layer. No need for a timeout here.
        if (!mCall.isEmergencyCall()) {
            return false;
        }

        // If there's no connection manager to fallback on then there's no point in having a
        // timeout.
        PhoneAccountHandle connectionManager =
                mPhoneAccountRegistrar.getSimCallManagerFromCall(mCall);
        if (!accounts.contains(connectionManager)) {
            return false;
        }

        // No need to add a timeout if the current attempt is over the connection manager.
        if (Objects.equals(connectionManager, currentAccount)) {
            return false;
        }

        // Timeout is only supported for SIM call managers that are set by the carrier.
        if (!Objects.equals(connectionManager.getComponentName(),
                mPhoneAccountRegistrar.getSystemSimCallManagerComponent())) {
            Log.d(this, "isTimeoutNeededForCall, not a system sim call manager");
            return false;
        }

        Log.i(this, "isTimeoutNeededForCall, returning true");
        return true;
    }

    void registerTimeout() {
        Log.d(this, "registerTimeout");
        mIsRegistered = true;

        long timeoutLengthMillis = getTimeoutLengthMillis();
        if (timeoutLengthMillis <= 0) {
            Log.d(this, "registerTimeout, timeout set to %d, skipping", timeoutLengthMillis);
        } else {
            mHandler.postDelayed(prepare(), timeoutLengthMillis);
        }
    }

    void unregisterTimeout() {
        Log.d(this, "unregisterTimeout");
        mIsRegistered = false;
        mHandler.removeCallbacksAndMessages(null);
        cancel();
    }

    boolean isCallTimedOut() {
        return mIsCallTimedOut;
    }

    @Override
    public void loggedRun() {
        if (mIsRegistered && isCallBeingPlaced(mCall)) {
            Log.i(this, "run, call timed out, calling disconnect");
            mIsCallTimedOut = true;
            mConnectionService.disconnect(mCall);
        }
    }

    static boolean isCallBeingPlaced(Call call) {
        int state = call.getState();
        return state == CallState.NEW
            || state == CallState.CONNECTING
            || state == CallState.DIALING
            || state == CallState.PULLING;
    }

    private long getTimeoutLengthMillis() {
        // If the radio is off then use a longer timeout. This gives us more time to power on the
        // radio.
        TelephonyManager telephonyManager =
            (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager.isRadioOn()) {
            return Timeouts.getEmergencyCallTimeoutMillis(mContext.getContentResolver());
        } else {
            return Timeouts.getEmergencyCallTimeoutRadioOffMillis(
                    mContext.getContentResolver());
        }
    }
}
