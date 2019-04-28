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

import android.os.RemoteException;
import android.os.ServiceManager;
import android.telecom.Log;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.ITelephonyRegistry;

/**
 * Send a {@link TelephonyManager#ACTION_PHONE_STATE_CHANGED} broadcast when the call state
 * changes.
 */
final class PhoneStateBroadcaster extends CallsManagerListenerBase {

    private final CallsManager mCallsManager;
    private final ITelephonyRegistry mRegistry;
    private int mCurrentState = TelephonyManager.CALL_STATE_IDLE;

    public PhoneStateBroadcaster(CallsManager callsManager) {
        mCallsManager = callsManager;
        mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                "telephony.registry"));
        if (mRegistry == null) {
            Log.w(this, "TelephonyRegistry is null");
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        if (call.isExternalCall()) {
            return;
        }
        updateStates(call);
    }

    @Override
    public void onCallAdded(Call call) {
        if (call.isExternalCall()) {
            return;
        }
        updateStates(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        if (call.isExternalCall()) {
            return;
        }
        updateStates(call);
    }

    /**
     * Handles changes to a call's external property.  If the call becomes external, we end up
     * updating the call state to idle.  If the call becomes non-external, then the call state can
     * update to off hook.
     *
     * @param call The call.
     * @param isExternalCall {@code True} if the call is external, {@code false} otherwise.
     */
    @Override
    public void onExternalCallChanged(Call call, boolean isExternalCall) {
        updateStates(call);
    }

    private void updateStates(Call call) {
        // Recalculate the current phone state based on the consolidated state of the remaining
        // calls in the call list.
        // Note: CallsManager#hasRingingCall() and CallsManager#getFirstCallWithState(..) do not
        // consider external calls, so an external call is going to cause the state to be idle.
        int callState = TelephonyManager.CALL_STATE_IDLE;
        if (mCallsManager.hasRingingCall()) {
            callState = TelephonyManager.CALL_STATE_RINGING;
        } else if (mCallsManager.getFirstCallWithState(CallState.DIALING, CallState.PULLING,
                CallState.ACTIVE, CallState.ON_HOLD) != null) {
            callState = TelephonyManager.CALL_STATE_OFFHOOK;
        }
        sendPhoneStateChangedBroadcast(call, callState);
    }

    int getCallState() {
        return mCurrentState;
    }

    private void sendPhoneStateChangedBroadcast(Call call, int phoneState) {
        if (phoneState == mCurrentState) {
            return;
        }

        mCurrentState = phoneState;

        String callHandle = null;
        if (call.getHandle() != null) {
            callHandle = call.getHandle().getSchemeSpecificPart();
        }

        try {
            if (mRegistry != null) {
                mRegistry.notifyCallState(phoneState, callHandle);
                Log.i(this, "Broadcasted state change: %s", mCurrentState);
            }
        } catch (RemoteException e) {
            Log.w(this, "RemoteException when notifying TelephonyRegistry of call state change.");
        }
    }
}
