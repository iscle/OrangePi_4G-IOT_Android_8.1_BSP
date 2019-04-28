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
 * limitations under the License.
 */
package com.android.bluetooth.hfpclient.connserv;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.net.Uri;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.UUID;

public class HfpClientConnection extends Connection {
    private static final String TAG = "HfpClientConnection";
    private static final boolean DBG = false;

    private final Context mContext;
    private final BluetoothDevice mDevice;
    private BluetoothHeadsetClient mHeadsetProfile;

    private BluetoothHeadsetClientCall mCurrentCall;
    private int mPreviousCallState = -1;
    private boolean mClosed;
    private boolean mClosing = false;
    private boolean mLocalDisconnect;
    private boolean mClientHasEcc;
    private boolean mAdded;

    // Constructor to be used when there's an existing call (such as that created on the AG or
    // when connection happens and we see calls for the first time).
    public HfpClientConnection(Context context, BluetoothDevice device,
            BluetoothHeadsetClient client, BluetoothHeadsetClientCall call) {
        mDevice = device;
        mContext = context;
        mHeadsetProfile = client;

        if (call == null) {
            throw new IllegalStateException("Call is null");
        }

        mCurrentCall = call;
        handleCallChanged();
        finishInitializing();
    }

    // Constructor to be used when a call is intiated on the HF. The call handle is obtained by
    // using the dial() command.
    public HfpClientConnection(Context context, BluetoothDevice device,
            BluetoothHeadsetClient client, Uri number) {
        mDevice = device;
        mContext = context;
        mHeadsetProfile = client;

        if (mHeadsetProfile == null) {
            throw new IllegalStateException("HeadsetProfile is null, returning");
        }

        mCurrentCall = mHeadsetProfile.dial(
            mDevice, number.getSchemeSpecificPart());
        if (mCurrentCall == null) {
            close(DisconnectCause.ERROR);
            Log.e(TAG, "Failed to create the call, dial failed.");
            return;
        }

        mHeadsetProfile.connectAudio(device);
        setInitializing();
        setDialing();
        finishInitializing();
    }

    void finishInitializing() {
        mClientHasEcc = HfpClientConnectionService.hasHfpClientEcc(mHeadsetProfile, mDevice);
        setAudioModeIsVoip(false);
        Uri number = Uri.fromParts(PhoneAccount.SCHEME_TEL, mCurrentCall.getNumber(), null);
        setAddress(number, TelecomManager.PRESENTATION_ALLOWED);
        setConnectionCapabilities(CAPABILITY_SUPPORT_HOLD | CAPABILITY_MUTE |
                CAPABILITY_SEPARATE_FROM_CONFERENCE | CAPABILITY_DISCONNECT_FROM_CONFERENCE |
                (getState() == STATE_ACTIVE || getState() == STATE_HOLDING ? CAPABILITY_HOLD : 0));
    }

    public UUID getUUID() {
        return mCurrentCall.getUUID();
    }

    public void onHfpDisconnected() {
        mHeadsetProfile = null;
        close(DisconnectCause.ERROR);
    }

    public void onAdded() {
        mAdded = true;
    }

    public BluetoothHeadsetClientCall getCall() {
        return mCurrentCall;
    }

    public boolean inConference() {
        return mAdded && mCurrentCall != null && mCurrentCall.isMultiParty() &&
                getState() != Connection.STATE_DISCONNECTED;
    }

    public void enterPrivateMode() {
        mHeadsetProfile.enterPrivateMode(mDevice, mCurrentCall.getId());
        setActive();
    }

    public void updateCall(BluetoothHeadsetClientCall call) {
        if (call == null) {
            Log.e(TAG, "Updating call to a null value.");
            return;
        }
        mCurrentCall = call;
    }

    public void handleCallChanged() {
        HfpClientConference conference = (HfpClientConference) getConference();
        int state = mCurrentCall.getState();

        if (DBG) {
            Log.d(TAG, "Got call state change to " + state);
        }
        switch (state) {
            case BluetoothHeadsetClientCall.CALL_STATE_ACTIVE:
                setActive();
                if (conference != null) {
                    conference.setActive();
                }
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD:
            case BluetoothHeadsetClientCall.CALL_STATE_HELD:
                setOnHold();
                if (conference != null) {
                    conference.setOnHold();
                }
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_DIALING:
            case BluetoothHeadsetClientCall.CALL_STATE_ALERTING:
                setDialing();
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_INCOMING:
            case BluetoothHeadsetClientCall.CALL_STATE_WAITING:
                setRinging();
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_TERMINATED:
                if (mPreviousCallState == BluetoothHeadsetClientCall.CALL_STATE_INCOMING
                        || mPreviousCallState == BluetoothHeadsetClientCall.CALL_STATE_WAITING) {
                    close(DisconnectCause.MISSED);
                } else if (mLocalDisconnect) {
                    close(DisconnectCause.LOCAL);
                } else {
                    close(DisconnectCause.REMOTE);
                }
                break;
            default:
                Log.wtf(TAG, "Unexpected phone state " + state);
        }
        mPreviousCallState = state;
    }

    public synchronized void close(int cause) {
        if (DBG) {
            Log.d(TAG, "Closing call " + mCurrentCall + "state: " + mClosed);
        }
        if (mClosed) {
            return;
        }
        Log.d(TAG, "Setting " + mCurrentCall + " to disconnected " + getTelecomCallId());
        setDisconnected(new DisconnectCause(cause));

        mClosed = true;
        mCurrentCall = null;

        destroy();
    }

    public synchronized boolean isClosing() {
        return mClosing;
    }

    public synchronized BluetoothDevice getDevice() {
        return mDevice;
    }

    @Override
    public synchronized void onPlayDtmfTone(char c) {
        if (DBG) {
            Log.d(TAG, "onPlayDtmfTone " + c + " " + mCurrentCall);
        }
        if (!mClosed) {
            mHeadsetProfile.sendDTMF(mDevice, (byte) c);
        }
    }

    @Override
    public synchronized void onDisconnect() {
        if (DBG) {
            Log.d(TAG, "onDisconnect call: " + mCurrentCall + " state: " + mClosed);
        }
        // The call is not closed so we should send a terminate here.
        if (!mClosed) {
            mHeadsetProfile.terminateCall(mDevice, mCurrentCall);
            mLocalDisconnect = true;
            mClosing = true;
        }
    }

    @Override
    public void onAbort() {
        if (DBG) {
            Log.d(TAG, "onAbort " + mCurrentCall);
        }
        onDisconnect();
    }

    @Override
    public synchronized void onHold() {
        if (DBG) {
            Log.d(TAG, "onHold " + mCurrentCall);
        }
        if (!mClosed) {
            mHeadsetProfile.holdCall(mDevice);
        }
    }

    @Override
    public synchronized void onUnhold() {
        if (getConnectionService().getAllConnections().size() > 1) {
            Log.w(TAG, "Ignoring unhold; call hold on the foreground call");
            return;
        }
        if (DBG) {
            Log.d(TAG, "onUnhold " + mCurrentCall);
        }
        if (!mClosed) {
            mHeadsetProfile.acceptCall(mDevice, BluetoothHeadsetClient.CALL_ACCEPT_HOLD);
        }
    }

    @Override
    public synchronized void onAnswer() {
        if (DBG) {
            Log.d(TAG, "onAnswer " + mCurrentCall);
        }
        if (!mClosed) {
            mHeadsetProfile.acceptCall(mDevice, BluetoothHeadsetClient.CALL_ACCEPT_NONE);
        }
        mHeadsetProfile.connectAudio(mDevice);
    }

    @Override
    public synchronized void onReject() {
        if (DBG) {
            Log.d(TAG, "onReject " + mCurrentCall);
        }
        if (!mClosed) {
            mHeadsetProfile.rejectCall(mDevice);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HfpClientConnection)) {
            return false;
        }
        Uri otherAddr = ((HfpClientConnection) o).getAddress();
        return getAddress() == otherAddr || otherAddr != null && otherAddr.equals(getAddress());
    }

    @Override
    public String toString() {
        return "HfpClientConnection{" + getAddress() + "," + stateToString(getState()) + "," +
                mCurrentCall + "}";
    }
}
