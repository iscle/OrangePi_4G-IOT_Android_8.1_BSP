/*
 * Copyright (c) 2016 The Android Open Source Project
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

/**
 * Bluetooth Headset Client StateMachine
 *                      (Disconnected)
 *                           | ^  ^
 *                   CONNECT | |  | DISCONNECTED
 *                           V |  |
 *                   (Connecting) |
 *                           |    |
 *                 CONNECTED |    | DISCONNECT
 *                           V    |
 *                        (Connected)
 *                           |    ^
 *             CONNECT_AUDIO |    | DISCONNECT_AUDIO
 *                           V    |
 *                         (AudioOn)
 */

package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Message;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.telecom.TelecomManager;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.android.bluetooth.R;

public class HeadsetClientStateMachine extends StateMachine {
    private static final String TAG = "HeadsetClientStateMachine";
    private static final boolean DBG = false;

    static final int NO_ACTION = 0;

    // external actions
    public static final int CONNECT = 1;
    public static final int DISCONNECT = 2;
    public static final int CONNECT_AUDIO = 3;
    public static final int DISCONNECT_AUDIO = 4;
    public static final int SET_MIC_VOLUME = 7;
    public static final int SET_SPEAKER_VOLUME = 8;
    public static final int DIAL_NUMBER = 10;
    public static final int ACCEPT_CALL = 12;
    public static final int REJECT_CALL = 13;
    public static final int HOLD_CALL = 14;
    public static final int TERMINATE_CALL = 15;
    public static final int ENTER_PRIVATE_MODE = 16;
    public static final int SEND_DTMF = 17;
    public static final int EXPLICIT_CALL_TRANSFER = 18;
    public static final int DISABLE_NREC = 20;

    // internal actions
    private static final int QUERY_CURRENT_CALLS = 50;
    private static final int QUERY_OPERATOR_NAME = 51;
    private static final int SUBSCRIBER_INFO = 52;
    private static final int CONNECTING_TIMEOUT = 53;

    // special action to handle terminating specific call from multiparty call
    static final int TERMINATE_SPECIFIC_CALL = 53;

    // Timeouts.
    static final int CONNECTING_TIMEOUT_MS = 10000;  // 10s
    static final int ROUTING_DELAY_MS = 250;
    static final int SCO_DISCONNECT_TIMEOUT_MS = 750;

    static final int MAX_HFP_SCO_VOICE_CALL_VOLUME = 15; // HFP 1.5 spec.
    static final int MIN_HFP_SCO_VOICE_CALL_VOLUME = 1; // HFP 1.5 spec.

    public static final Integer HF_ORIGINATED_CALL_ID = new Integer(-1);
    private long OUTGOING_TIMEOUT_MILLI = 10 * 1000; // 10 seconds
    private long QUERY_CURRENT_CALLS_WAIT_MILLIS = 2 * 1000; // 2 seconds

    // Keep track of audio routing across all devices.
    private static boolean sAudioIsRouted = true;

    private final Disconnected mDisconnected;
    private final Connecting mConnecting;
    private final Connected mConnected;
    private final AudioOn mAudioOn;
    private long mClccTimer = 0;

    private final HeadsetClientService mService;

    // Set of calls that represent the accurate state of calls that exists on AG and the calls that
    // are currently in process of being notified to the AG from HF.
    private final Hashtable<Integer, BluetoothHeadsetClientCall> mCalls = new Hashtable<>();
    // Set of calls received from AG via the AT+CLCC command. We use this map to update the mCalls
    // which is eventually used to inform the telephony stack of any changes to call on HF.
    private final Hashtable<Integer, BluetoothHeadsetClientCall> mCallsUpdate = new Hashtable<>();

    private int mIndicatorNetworkState;
    private int mIndicatorNetworkType;
    private int mIndicatorNetworkSignal;
    private int mIndicatorBatteryLevel;

    private String mOperatorName;
    private String mSubscriberInfo;

    private static int mMaxAmVcVol;
    private static int mMinAmVcVol;

    // queue of send actions (pair action, action_data)
    private Queue<Pair<Integer, Object>> mQueuedActions;

    // last executed command, before action is complete e.g. waiting for some
    // indicator
    private Pair<Integer, Object> mPendingAction;

    private static AudioManager sAudioManager;
    private int mAudioState;
    private boolean mAudioWbs;
    private final BluetoothAdapter mAdapter;
    private TelecomManager mTelecomManager;

    // currently connected device
    private BluetoothDevice mCurrentDevice = null;

    // general peer features and call handling features
    private int mPeerFeatures;
    private int mChldFeatures;

    // Accessor for the states, useful for reusing the state machines
    public IState getDisconnectedState() {
        return mDisconnected;
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + mCurrentDevice);
        ProfileService.println(sb, "mAudioState: " + mAudioState);
        ProfileService.println(sb, "mAudioWbs: " + mAudioWbs);
        ProfileService.println(sb, "mIndicatorNetworkState: " + mIndicatorNetworkState);
        ProfileService.println(sb, "mIndicatorNetworkType: " + mIndicatorNetworkType);
        ProfileService.println(sb, "mIndicatorNetworkSignal: " + mIndicatorNetworkSignal);
        ProfileService.println(sb, "mIndicatorBatteryLevel: " + mIndicatorBatteryLevel);
        ProfileService.println(sb, "mOperatorName: " + mOperatorName);
        ProfileService.println(sb, "mSubscriberInfo: " + mSubscriberInfo);

        ProfileService.println(sb, "mCalls:");
        if (mCalls != null) {
            for (BluetoothHeadsetClientCall call : mCalls.values()) {
                ProfileService.println(sb, "  " + call);
            }
        }

        ProfileService.println(sb, "mCallsUpdate:");
        if (mCallsUpdate != null) {
            for (BluetoothHeadsetClientCall call : mCallsUpdate.values()) {
                ProfileService.println(sb, "  " + call);
            }
        }

        ProfileService.println(sb, "State machine stats:");
        ProfileService.println(sb, this.toString());
    }

    private void clearPendingAction() {
        mPendingAction = new Pair<Integer, Object>(NO_ACTION, 0);
    }

    private void addQueuedAction(int action) {
        addQueuedAction(action, 0);
    }

    private void addQueuedAction(int action, Object data) {
        mQueuedActions.add(new Pair<Integer, Object>(action, data));
    }

    private void addQueuedAction(int action, int data) {
        mQueuedActions.add(new Pair<Integer, Object>(action, data));
    }

    private BluetoothHeadsetClientCall getCall(int... states) {
        if (DBG) {
            Log.d(TAG, "getFromCallsWithStates states:" + Arrays.toString(states));
        }
        for (BluetoothHeadsetClientCall c : mCalls.values()) {
            for (int s : states) {
                if (c.getState() == s) {
                    return c;
                }
            }
        }
        return null;
    }

    private int callsInState(int state) {
        int i = 0;
        for (BluetoothHeadsetClientCall c : mCalls.values()) {
            if (c.getState() == state) {
                i++;
            }
        }

        return i;
    }

    private void sendCallChangedIntent(BluetoothHeadsetClientCall c) {
        if (DBG) {
            Log.d(TAG, "sendCallChangedIntent " + c);
        }
        Intent intent = new Intent(BluetoothHeadsetClient.ACTION_CALL_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(BluetoothHeadsetClient.EXTRA_CALL, c);
        mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private boolean queryCallsStart() {
        if (DBG) {
            Log.d(TAG, "queryCallsStart");
        }
        clearPendingAction();
        NativeInterface.queryCurrentCallsNative(getByteAddress(mCurrentDevice));
        addQueuedAction(QUERY_CURRENT_CALLS, 0);
        return true;
    }

    private void queryCallsDone() {
        if (DBG) {
            Log.d(TAG, "queryCallsDone");
        }
        Iterator<Hashtable.Entry<Integer, BluetoothHeadsetClientCall>> it;

        // mCalls has two types of calls:
        // (a) Calls that are received from AG of a previous iteration of queryCallsStart()
        // (b) Calls that are outgoing initiated from HF
        // mCallsUpdate has all calls received from queryCallsUpdate() in current iteration of
        // queryCallsStart().
        //
        // We use the following steps to make sure that calls are update correctly.
        //
        // If there are no calls initiated from HF (i.e. ID = -1) then:
        // 1. All IDs which are common in mCalls & mCallsUpdate are updated and the upper layers are
        // informed of the change calls (if any changes).
        // 2. All IDs that are in mCalls but *not* in mCallsUpdate will be removed from mCalls and
        // the calls should be terminated
        // 3. All IDs that are new in mCallsUpdated should be added as new calls to mCalls.
        //
        // If there is an outgoing HF call, it is important to associate that call with one of the
        // mCallsUpdated calls hence,
        // 1. If from the above procedure we get N extra calls (i.e. {3}):
        // choose the first call as the one to associate with the HF call.

        // Create set of IDs for added calls, removed calls and consitent calls.
        // WARN!!! Java Map -> Set has association hence changes to Set are reflected in the Map
        // itself (i.e. removing an element from Set removes it from the Map hence use copy).
        Set<Integer> currCallIdSet = new HashSet<Integer>();
        currCallIdSet.addAll(mCalls.keySet());
        // Remove the entry for unassigned call.
        currCallIdSet.remove(HF_ORIGINATED_CALL_ID);

        Set<Integer> newCallIdSet = new HashSet<Integer>();
        newCallIdSet.addAll(mCallsUpdate.keySet());

        // Added.
        Set<Integer> callAddedIds = new HashSet<Integer>();
        callAddedIds.addAll(newCallIdSet);
        callAddedIds.removeAll(currCallIdSet);

        // Removed.
        Set<Integer> callRemovedIds = new HashSet<Integer>();
        callRemovedIds.addAll(currCallIdSet);
        callRemovedIds.removeAll(newCallIdSet);

        // Retained.
        Set<Integer> callRetainedIds = new HashSet<Integer>();
        callRetainedIds.addAll(currCallIdSet);
        callRetainedIds.retainAll(newCallIdSet);

        if (DBG) {
            Log.d(TAG, "currCallIdSet " + mCalls.keySet() + " newCallIdSet " + newCallIdSet +
                " callAddedIds " + callAddedIds + " callRemovedIds " + callRemovedIds +
                " callRetainedIds " + callRetainedIds);
        }

        // First thing is to try to associate the outgoing HF with a valid call.
        Integer hfOriginatedAssoc = -1;
        if (mCalls.containsKey(HF_ORIGINATED_CALL_ID)) {
            BluetoothHeadsetClientCall c = mCalls.get(HF_ORIGINATED_CALL_ID);
            long cCreationElapsed = c.getCreationElapsedMilli();
            if (callAddedIds.size() > 0) {
                if (DBG) {
                    Log.d(TAG, "Associating the first call with HF originated call");
                }
                hfOriginatedAssoc = (Integer) callAddedIds.toArray()[0];
                mCalls.put(hfOriginatedAssoc, mCalls.get(HF_ORIGINATED_CALL_ID));
                mCalls.remove(HF_ORIGINATED_CALL_ID);

                // Adjust this call in above sets.
                callAddedIds.remove(hfOriginatedAssoc);
                callRetainedIds.add(hfOriginatedAssoc);
            } else if (SystemClock.elapsedRealtime() - cCreationElapsed > OUTGOING_TIMEOUT_MILLI) {
                Log.w(TAG, "Outgoing call did not see a response, clear the calls and send CHUP");
                // We send a terminate because we are in a bad state and trying to
                // recover.
                terminateCall();

                // Clean out the state for outgoing call.
                for (Integer idx : mCalls.keySet()) {
                    BluetoothHeadsetClientCall c1 = mCalls.get(idx);
                    c1.setState(BluetoothHeadsetClientCall.CALL_STATE_TERMINATED);
                    sendCallChangedIntent(c1);
                }
                mCalls.clear();

                // We return here, if there's any update to the phone we should get a
                // follow up by getting some call indicators and hence update the calls.
                return;
            }
        }

        if (DBG) {
            Log.d(TAG, "ADJUST: currCallIdSet " + mCalls.keySet() + " newCallIdSet " +
                newCallIdSet + " callAddedIds " + callAddedIds + " callRemovedIds " +
                callRemovedIds + " callRetainedIds " + callRetainedIds);
        }

        // Terminate & remove the calls that are done.
        for (Integer idx : callRemovedIds) {
            BluetoothHeadsetClientCall c = mCalls.remove(idx);
            c.setState(BluetoothHeadsetClientCall.CALL_STATE_TERMINATED);
            sendCallChangedIntent(c);
        }

        // Add the new calls.
        for (Integer idx : callAddedIds) {
            BluetoothHeadsetClientCall c = mCallsUpdate.get(idx);
            mCalls.put(idx, c);
            sendCallChangedIntent(c);
        }

        // Update the existing calls.
        for (Integer idx : callRetainedIds) {
            BluetoothHeadsetClientCall cOrig = mCalls.get(idx);
            BluetoothHeadsetClientCall cUpdate = mCallsUpdate.get(idx);

            // Update the necessary fields.
            cOrig.setNumber(cUpdate.getNumber());
            cOrig.setState(cUpdate.getState());
            cOrig.setMultiParty(cUpdate.isMultiParty());

            // Send update with original object (UUID, idx).
            sendCallChangedIntent(cOrig);
        }

        if (mCalls.size() > 0) {
            sendMessageDelayed(QUERY_CURRENT_CALLS, QUERY_CURRENT_CALLS_WAIT_MILLIS);
        }

        mCallsUpdate.clear();
    }

    private void queryCallsUpdate(int id, int state, String number, boolean multiParty,
            boolean outgoing) {
        if (DBG) {
            Log.d(TAG, "queryCallsUpdate: " + id);
        }
        mCallsUpdate.put(id, new BluetoothHeadsetClientCall(
            mCurrentDevice, id, state, number, multiParty, outgoing));
    }

    private void acceptCall(int flag) {
        int action = -1;

        if (DBG) {
            Log.d(TAG, "acceptCall: (" + flag + ")");
        }

        BluetoothHeadsetClientCall c = getCall(BluetoothHeadsetClientCall.CALL_STATE_INCOMING,
                BluetoothHeadsetClientCall.CALL_STATE_WAITING);
        if (c == null) {
            c = getCall(BluetoothHeadsetClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD,
                    BluetoothHeadsetClientCall.CALL_STATE_HELD);

            if (c == null) {
                return;
            }
        }

        if (DBG) {
            Log.d(TAG, "Call to accept: " + c);
        }
        switch (c.getState()) {
            case BluetoothHeadsetClientCall.CALL_STATE_INCOMING:
                if (flag != BluetoothHeadsetClient.CALL_ACCEPT_NONE) {
                    return;
                }
                action = HeadsetClientHalConstants.CALL_ACTION_ATA;
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_WAITING:
                if (callsInState(BluetoothHeadsetClientCall.CALL_STATE_ACTIVE) == 0) {
                    // if no active calls present only plain accept is allowed
                    if (flag != BluetoothHeadsetClient.CALL_ACCEPT_NONE) {
                        return;
                    }
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_2;
                    break;
                }

                // if active calls are present then we have the option to either terminate the
                // existing call or hold the existing call. We hold the other call by default.
                if (flag == BluetoothHeadsetClient.CALL_ACCEPT_HOLD ||
                    flag == BluetoothHeadsetClient.CALL_ACCEPT_NONE) {
                    if (DBG) {
                        Log.d(TAG, "Accepting call with accept and hold");
                    }
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_2;
                } else if (flag == BluetoothHeadsetClient.CALL_ACCEPT_TERMINATE) {
                    if (DBG) {
                        Log.d(TAG, "Accepting call with accept and reject");
                    }
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_1;
                } else {
                    Log.e(TAG, "Aceept call with invalid flag: " + flag);
                    return;
                }
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_HELD:
                if (flag == BluetoothHeadsetClient.CALL_ACCEPT_HOLD) {
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_2;
                } else if (flag == BluetoothHeadsetClient.CALL_ACCEPT_TERMINATE) {
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_1;
                } else if (getCall(BluetoothHeadsetClientCall.CALL_STATE_ACTIVE) != null) {
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_3;
                } else if (flag == BluetoothHeadsetClient.CALL_ACCEPT_NONE) {
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_2;
                } else {
                    action = HeadsetClientHalConstants.CALL_ACTION_CHLD_2;
                }
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD:
                action = HeadsetClientHalConstants.CALL_ACTION_BTRH_1;
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_ALERTING:
            case BluetoothHeadsetClientCall.CALL_STATE_ACTIVE:
            case BluetoothHeadsetClientCall.CALL_STATE_DIALING:
            default:
                return;
        }

        if (flag == BluetoothHeadsetClient.CALL_ACCEPT_HOLD) {
            // When unholding a call over Bluetooth make sure to route audio.
            routeHfpAudio(true);
        }

        if (NativeInterface.handleCallActionNative(getByteAddress(mCurrentDevice), action, 0)) {
            addQueuedAction(ACCEPT_CALL, action);
        } else {
            Log.e(TAG, "ERROR: Couldn't accept a call, action:" + action);
        }
    }

    private void rejectCall() {
        int action;

        if (DBG) {
            Log.d(TAG, "rejectCall");
        }

        BluetoothHeadsetClientCall c =
                getCall(BluetoothHeadsetClientCall.CALL_STATE_INCOMING,
                BluetoothHeadsetClientCall.CALL_STATE_WAITING,
                BluetoothHeadsetClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD,
                BluetoothHeadsetClientCall.CALL_STATE_HELD);
        if (c == null) {
            if (DBG) {
                Log.d(TAG, "No call to reject, returning.");
            }
            return;
        }

        switch (c.getState()) {
            case BluetoothHeadsetClientCall.CALL_STATE_INCOMING:
                action = HeadsetClientHalConstants.CALL_ACTION_CHUP;
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_WAITING:
            case BluetoothHeadsetClientCall.CALL_STATE_HELD:
                action = HeadsetClientHalConstants.CALL_ACTION_CHLD_0;
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD:
                action = HeadsetClientHalConstants.CALL_ACTION_BTRH_2;
                break;
            case BluetoothHeadsetClientCall.CALL_STATE_ACTIVE:
            case BluetoothHeadsetClientCall.CALL_STATE_DIALING:
            case BluetoothHeadsetClientCall.CALL_STATE_ALERTING:
            default:
                return;
        }

        if (DBG) {
            Log.d(TAG, "Reject call action " + action);
        }
        if (NativeInterface.handleCallActionNative(getByteAddress(mCurrentDevice), action, 0)) {
            addQueuedAction(REJECT_CALL, action);
        } else {
            Log.e(TAG, "ERROR: Couldn't reject a call, action:" + action);
        }
    }

    private void holdCall() {
        int action;

        if (DBG) {
            Log.d(TAG, "holdCall");
        }

        BluetoothHeadsetClientCall c = getCall(BluetoothHeadsetClientCall.CALL_STATE_INCOMING);
        if (c != null) {
            action = HeadsetClientHalConstants.CALL_ACTION_BTRH_0;
        } else {
            c = getCall(BluetoothHeadsetClientCall.CALL_STATE_ACTIVE);
            if (c == null) {
                return;
            }

            action = HeadsetClientHalConstants.CALL_ACTION_CHLD_2;
        }

        if (NativeInterface.handleCallActionNative(getByteAddress(mCurrentDevice), action, 0)) {
            addQueuedAction(HOLD_CALL, action);
        } else {
            Log.e(TAG, "ERROR: Couldn't hold a call, action:" + action);
        }
    }

    private void terminateCall() {
        if (DBG) {
            Log.d(TAG, "terminateCall");
        }

        int action = HeadsetClientHalConstants.CALL_ACTION_CHUP;

        BluetoothHeadsetClientCall c = getCall(
                BluetoothHeadsetClientCall.CALL_STATE_DIALING,
                BluetoothHeadsetClientCall.CALL_STATE_ALERTING,
                BluetoothHeadsetClientCall.CALL_STATE_ACTIVE);
        if (c != null) {
            if (NativeInterface.handleCallActionNative(getByteAddress(mCurrentDevice), action, 0)) {
                addQueuedAction(TERMINATE_CALL, action);
            } else {
                Log.e(TAG, "ERROR: Couldn't terminate outgoing call");
            }
        }
    }

    private void enterPrivateMode(int idx) {
        if (DBG) {
            Log.d(TAG, "enterPrivateMode: " + idx);
        }

        BluetoothHeadsetClientCall c = mCalls.get(idx);

        if (c == null ||
            c.getState() != BluetoothHeadsetClientCall.CALL_STATE_ACTIVE ||
            !c.isMultiParty()) return;

        if (NativeInterface.handleCallActionNative(getByteAddress(mCurrentDevice),
                HeadsetClientHalConstants.CALL_ACTION_CHLD_2x, idx)) {
            addQueuedAction(ENTER_PRIVATE_MODE, c);
        } else {
            Log.e(TAG, "ERROR: Couldn't enter private " + " id:" + idx);
        }
    }

    private void explicitCallTransfer() {
        if (DBG) {
            Log.d(TAG, "explicitCallTransfer");
        }

        // can't transfer call if there is not enough call parties
        if (mCalls.size() < 2) {
            return;
        }

        if (NativeInterface.handleCallActionNative(getByteAddress(mCurrentDevice),
              HeadsetClientHalConstants.CALL_ACTION_CHLD_4, -1)) {
            addQueuedAction(EXPLICIT_CALL_TRANSFER);
        } else {
            Log.e(TAG, "ERROR: Couldn't transfer call");
        }
    }

    public Bundle getCurrentAgFeatures() {
        Bundle b = new Bundle();
        if ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_3WAY) ==
                HeadsetClientHalConstants.PEER_FEAT_3WAY) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_3WAY_CALLING, true);
        }
        if ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_REJECT) ==
                HeadsetClientHalConstants.PEER_FEAT_REJECT) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_REJECT_CALL, true);
        }
        if ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_ECC) ==
                HeadsetClientHalConstants.PEER_FEAT_ECC) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_ECC, true);
        }

        // add individual CHLD support extras
        if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_HOLD_ACC) ==
                HeadsetClientHalConstants.CHLD_FEAT_HOLD_ACC) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_ACCEPT_HELD_OR_WAITING_CALL, true);
        }
        if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_REL) ==
                HeadsetClientHalConstants.CHLD_FEAT_REL) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_RELEASE_HELD_OR_WAITING_CALL, true);
        }
        if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_REL_ACC) ==
                HeadsetClientHalConstants.CHLD_FEAT_REL_ACC) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_RELEASE_AND_ACCEPT, true);
        }
        if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_MERGE) ==
                HeadsetClientHalConstants.CHLD_FEAT_MERGE) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_MERGE, true);
        }
        if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_MERGE_DETACH) ==
                HeadsetClientHalConstants.CHLD_FEAT_MERGE_DETACH) {
            b.putBoolean(BluetoothHeadsetClient.EXTRA_AG_FEATURE_MERGE_AND_DETACH, true);
        }

        return b;
    }

    protected HeadsetClientStateMachine(HeadsetClientService context, Looper looper) {
        super(TAG, looper);
        mService = context;

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (sAudioManager == null) {
            sAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            // Initialize hfp_enable into a known state.
            routeHfpAudio(false);
        }
        mAudioState = BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED;
        mAudioWbs = false;

        mTelecomManager = (TelecomManager) context.getSystemService(context.TELECOM_SERVICE);

        mIndicatorNetworkState = HeadsetClientHalConstants.NETWORK_STATE_NOT_AVAILABLE;
        mIndicatorNetworkType = HeadsetClientHalConstants.SERVICE_TYPE_HOME;
        mIndicatorNetworkSignal = 0;
        mIndicatorBatteryLevel = 0;

        mMaxAmVcVol = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        mMinAmVcVol = sAudioManager.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL);

        mOperatorName = null;
        mSubscriberInfo = null;

        mQueuedActions = new LinkedList<Pair<Integer, Object>>();
        clearPendingAction();

        mCalls.clear();
        mCallsUpdate.clear();

        mDisconnected = new Disconnected();
        mConnecting = new Connecting();
        mConnected = new Connected();
        mAudioOn = new AudioOn();

        addState(mDisconnected);
        addState(mConnecting);
        addState(mConnected);
        addState(mAudioOn, mConnected);

        setInitialState(mDisconnected);
    }

    static HeadsetClientStateMachine make(HeadsetClientService context, Looper l) {
        if (DBG) {
            Log.d(TAG, "make");
        }
        HeadsetClientStateMachine hfcsm = new HeadsetClientStateMachine(context, l);
        hfcsm.start();
        return hfcsm;
    }

    static synchronized void routeHfpAudio(boolean enable) {
        if (DBG) {
            Log.d(TAG, "hfp_enable=" + enable);
        }
        if (enable && !sAudioIsRouted) {
            sAudioManager.setParameters("hfp_enable=true");
        } else if (!enable) {
            sAudioManager.setParameters("hfp_enable=false");
        }
        sAudioIsRouted = enable;
    }

    public void doQuit() {
        Log.d(TAG, "doQuit");
        if (sAudioManager != null) {
            routeHfpAudio(false);
        }
        quitNow();
    }

    public static void cleanup() {
    }

    static int hfToAmVol(int hfVol) {
        int amRange = mMaxAmVcVol - mMinAmVcVol;
        int hfRange = MAX_HFP_SCO_VOICE_CALL_VOLUME - MIN_HFP_SCO_VOICE_CALL_VOLUME;
        int amOffset =
            (amRange * (hfVol - MIN_HFP_SCO_VOICE_CALL_VOLUME)) / hfRange;
        int amVol = mMinAmVcVol + amOffset;
        Log.d(TAG, "HF -> AM " + hfVol + " " + amVol);
        return amVol;
    }

    static int amToHfVol(int amVol) {
        int amRange = mMaxAmVcVol - mMinAmVcVol;
        int hfRange = MAX_HFP_SCO_VOICE_CALL_VOLUME - MIN_HFP_SCO_VOICE_CALL_VOLUME;
        int hfOffset = (hfRange * (amVol - mMinAmVcVol)) / amRange;
        int hfVol = MIN_HFP_SCO_VOICE_CALL_VOLUME + hfOffset;
        Log.d(TAG, "AM -> HF " + amVol + " " + hfVol);
        return hfVol;
    }

    class Disconnected extends State {
        @Override
        public void enter() {
            Log.d(TAG, "Enter Disconnected: " + getCurrentMessage().what);

            // cleanup
            mIndicatorNetworkState = HeadsetClientHalConstants.NETWORK_STATE_NOT_AVAILABLE;
            mIndicatorNetworkType = HeadsetClientHalConstants.SERVICE_TYPE_HOME;
            mIndicatorNetworkSignal = 0;
            mIndicatorBatteryLevel = 0;

            mAudioWbs = false;

            // will be set on connect

            mOperatorName = null;
            mSubscriberInfo = null;

            mQueuedActions = new LinkedList<Pair<Integer, Object>>();
            clearPendingAction();


            mCurrentDevice = null;

            mCalls.clear();
            mCallsUpdate.clear();

            mPeerFeatures = 0;
            mChldFeatures = 0;

            removeMessages(QUERY_CURRENT_CALLS);
        }

        @Override
        public synchronized boolean processMessage(Message message) {
            Log.d(TAG, "Disconnected process message: " + message.what);

            if (mCurrentDevice != null) {
                Log.e(TAG, "ERROR: current device not null in Disconnected");
                return NOT_HANDLED;
            }

            switch (message.what) {
                case CONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                            BluetoothProfile.STATE_DISCONNECTED);

                    if (!NativeInterface.connectNative(getByteAddress(device))) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_CONNECTING);
                        break;
                    }

                    mCurrentDevice = device;

                    transitionTo(mConnecting);
                    break;
                case DISCONNECT:
                    // ignore
                    break;
                case StackEvent.STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                        Log.d(TAG, "Stack event type: " + event.type);
                    }
                    switch (event.type) {
                        case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            if (DBG) {
                                Log.d(TAG, "Disconnected: Connection " + event.device
                                        + " state changed:" + event.valueInt);
                            }
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        default:
                            Log.e(TAG, "Disconnected: Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in Disconnected state
        private void processConnectionEvent(int state, BluetoothDevice device)
        {
            switch (state) {
                case HeadsetClientHalConstants.CONNECTION_STATE_CONNECTED:
                    Log.w(TAG, "HFPClient Connecting from Disconnected state");
                    if (okToConnect(device)) {
                        Log.i(TAG, "Incoming AG accepted");
                        broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                BluetoothProfile.STATE_DISCONNECTED);
                        mCurrentDevice = device;
                        transitionTo(mConnecting);
                    } else {
                        Log.i(TAG, "Incoming AG rejected. priority=" +
                            mService.getPriority(device) +
                            " bondState=" + device.getBondState());
                        // reject the connection and stay in Disconnected state
                        // itself
                        NativeInterface.disconnectNative(getByteAddress(device));
                        // the other profile connection should be initiated
                        AdapterService adapterService = AdapterService.getAdapterService();
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_DISCONNECTED);
                    }
                    break;
                case HeadsetClientHalConstants.CONNECTION_STATE_CONNECTING:
                case HeadsetClientHalConstants.CONNECTION_STATE_DISCONNECTED:
                case HeadsetClientHalConstants.CONNECTION_STATE_DISCONNECTING:
                default:
                    Log.i(TAG, "ignoring state: " + state);
                    break;
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Log.d(TAG, "Exit Disconnected: " + getCurrentMessage().what);
            }
        }
    }

    class Connecting extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, "Enter Connecting: " + getCurrentMessage().what);
            }
            // This message is either consumed in processMessage or
            // removed in exit. It is safe to send a CONNECTING_TIMEOUT here since
            // the only transition is when connection attempt is initiated.
            sendMessageDelayed(CONNECTING_TIMEOUT, CONNECTING_TIMEOUT_MS);
        }

        @Override
        public synchronized boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, "Connecting process message: " + message.what);
            }

            switch (message.what) {
                case CONNECT:
                case CONNECT_AUDIO:
                case DISCONNECT:
                    deferMessage(message);
                    break;
                case StackEvent.STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                        Log.d(TAG, "Connecting: event type: " + event.type);
                    }
                    switch (event.type) {
                        case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            if (DBG) {
                                Log.d(TAG, "Connecting: Connection " + event.device + " state changed:"
                                        + event.valueInt);
                            }
                            processConnectionEvent(event.valueInt, event.valueInt2,
                                    event.valueInt3, event.device);
                            break;
                        case StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED:
                        case StackEvent.EVENT_TYPE_NETWORK_STATE:
                        case StackEvent.EVENT_TYPE_ROAMING_STATE:
                        case StackEvent.EVENT_TYPE_NETWORK_SIGNAL:
                        case StackEvent.EVENT_TYPE_BATTERY_LEVEL:
                        case StackEvent.EVENT_TYPE_CALL:
                        case StackEvent.EVENT_TYPE_CALLSETUP:
                        case StackEvent.EVENT_TYPE_CALLHELD:
                        case StackEvent.EVENT_TYPE_RESP_AND_HOLD:
                        case StackEvent.EVENT_TYPE_CLIP:
                        case StackEvent.EVENT_TYPE_CALL_WAITING:
                        case StackEvent.EVENT_TYPE_VOLUME_CHANGED:
                            deferMessage(message);
                            break;
                        case StackEvent.EVENT_TYPE_CMD_RESULT:
                        case StackEvent.EVENT_TYPE_SUBSCRIBER_INFO:
                        case StackEvent.EVENT_TYPE_CURRENT_CALLS:
                        case StackEvent.EVENT_TYPE_OPERATOR_NAME:
                        default:
                            Log.e(TAG, "Connecting: ignoring stack event: " + event.type);
                            break;
                    }
                    break;
                case CONNECTING_TIMEOUT:
                      // We timed out trying to connect, transition to disconnected.
                      Log.w(TAG, "Connection timeout for " + mCurrentDevice);
                      transitionTo(mDisconnected);
                      broadcastConnectionState(
                          mCurrentDevice,
                          BluetoothProfile.STATE_DISCONNECTED,
                          BluetoothProfile.STATE_CONNECTING);
                      break;

                default:
                    Log.w(TAG, "Message not handled " + message);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in Connecting state
        private void processConnectionEvent(
                int state, int peer_feat, int chld_feat, BluetoothDevice device) {
            switch (state) {
                case HeadsetClientHalConstants.CONNECTION_STATE_DISCONNECTED:
                    broadcastConnectionState(mCurrentDevice, BluetoothProfile.STATE_DISCONNECTED,
                            BluetoothProfile.STATE_CONNECTING);
                    transitionTo(mDisconnected);
                    break;

                case HeadsetClientHalConstants.CONNECTION_STATE_SLC_CONNECTED:
                    Log.d(TAG, "HFPClient Connected from Connecting state");

                    mPeerFeatures = peer_feat;
                    mChldFeatures = chld_feat;

                    // We do not support devices which do not support enhanced call status (ECS).
                    if ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_ECS) == 0) {
                        NativeInterface.disconnectNative(getByteAddress(device));
                        return;
                    }

                    broadcastConnectionState(mCurrentDevice, BluetoothProfile.STATE_CONNECTED,
                        BluetoothProfile.STATE_CONNECTING);

                    // Send AT+NREC to remote if supported by audio
                    if (HeadsetClientHalConstants.HANDSFREECLIENT_NREC_SUPPORTED &&
                            ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_ECNR) ==
                                    HeadsetClientHalConstants.PEER_FEAT_ECNR)) {
                        if (NativeInterface.sendATCmdNative(getByteAddress(mCurrentDevice),
                              HeadsetClientHalConstants.HANDSFREECLIENT_AT_CMD_NREC,
                              1 , 0, null)) {
                            addQueuedAction(DISABLE_NREC);
                        } else {
                            Log.e(TAG, "Failed to send NREC");
                        }
                    }
                    transitionTo(mConnected);

                    int amVol = sAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                    sendMessage(
                            obtainMessage(HeadsetClientStateMachine.SET_SPEAKER_VOLUME, amVol, 0));
                    // Mic is either in ON state (full volume) or OFF state. There is no way in
                    // Android to change the MIC volume.
                    sendMessage(obtainMessage(HeadsetClientStateMachine.SET_MIC_VOLUME,
                            sAudioManager.isMicrophoneMute() ? 0 : 15, 0));

                    // query subscriber info
                    sendMessage(HeadsetClientStateMachine.SUBSCRIBER_INFO);
                    break;

                case HeadsetClientHalConstants.CONNECTION_STATE_CONNECTED:
                    if (!mCurrentDevice.equals(device)) {
                        Log.w(TAG, "incoming connection event, device: " + device);

                        broadcastConnectionState(mCurrentDevice,
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_CONNECTING);
                        broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                BluetoothProfile.STATE_DISCONNECTED);

                        mCurrentDevice = device;
                    }
                    break;
                case HeadsetClientHalConstants.CONNECTION_STATE_CONNECTING:
                    /* outgoing connecting started */
                    if (DBG) {
                        Log.d(TAG, "outgoing connection started, ignore");
                    }
                    break;
                case HeadsetClientHalConstants.CONNECTION_STATE_DISCONNECTING:
                default:
                    Log.e(TAG, "Incorrect state: " + state);
                    break;
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Log.d(TAG, "Exit Connecting: " + getCurrentMessage().what);
            }
            removeMessages(CONNECTING_TIMEOUT);
        }
    }

    class Connected extends State {
        int mCommandedSpeakerVolume = -1;

        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, "Enter Connected: " + getCurrentMessage().what);
            }
            mAudioWbs = false;
            mCommandedSpeakerVolume = -1;
        }

        @Override
        public synchronized boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, "Connected process message: " + message.what);
            }
            if (DBG) {
                if (mCurrentDevice == null) {
                    Log.e(TAG, "ERROR: mCurrentDevice is null in Connected");
                    return NOT_HANDLED;
                }
            }

            switch (message.what) {
                case CONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice.equals(device)) {
                        // already connected to this device, do nothing
                        break;
                    }

                    NativeInterface.connectNative(getByteAddress(device));
                    break;
                case DISCONNECT:
                    BluetoothDevice dev = (BluetoothDevice) message.obj;
                    if (!mCurrentDevice.equals(dev)) {
                        break;
                    }
                    broadcastConnectionState(dev, BluetoothProfile.STATE_DISCONNECTING,
                            BluetoothProfile.STATE_CONNECTED);
                    if (!NativeInterface.disconnectNative(getByteAddress(dev))) {
                        // disconnecting failed
                        broadcastConnectionState(dev, BluetoothProfile.STATE_CONNECTED,
                                BluetoothProfile.STATE_DISCONNECTING);
                        break;
                    }
                    break;

                case CONNECT_AUDIO:
                    if (!NativeInterface.connectAudioNative(getByteAddress(mCurrentDevice))) {
                        Log.e(TAG, "ERROR: Couldn't connect Audio for device " + mCurrentDevice);
                        broadcastAudioState(mCurrentDevice,
                                BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED,
                                BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED);
                    } else { // We have successfully sent a connect request!
                        mAudioState = BluetoothHeadsetClient.STATE_AUDIO_CONNECTING;
                    }
                    break;

                case DISCONNECT_AUDIO:
                    if (!NativeInterface.disconnectAudioNative(getByteAddress(mCurrentDevice))) {
                        Log.e(TAG, "ERROR: Couldn't disconnect Audio for device " + mCurrentDevice);
                    }
                    break;

                // Called only for Mute/Un-mute - Mic volume change is not allowed.
                case SET_MIC_VOLUME:
                    break;
                case SET_SPEAKER_VOLUME:
                    // This message should always contain the volume in AudioManager max normalized.
                    int amVol = message.arg1;
                    int hfVol = amToHfVol(amVol);
                    if (amVol != mCommandedSpeakerVolume) {
                        Log.d(TAG, "Volume" + amVol + ":" + mCommandedSpeakerVolume);
                        // Volume was changed by a 3rd party
                        mCommandedSpeakerVolume = -1;
                        if (NativeInterface.setVolumeNative(getByteAddress(mCurrentDevice),
                                    HeadsetClientHalConstants.VOLUME_TYPE_SPK, hfVol)) {
                            addQueuedAction(SET_SPEAKER_VOLUME);
                        }
                    }
                    break;
                case DIAL_NUMBER:
                    // Add the call as an outgoing call.
                    BluetoothHeadsetClientCall c = (BluetoothHeadsetClientCall) message.obj;
                    mCalls.put(HF_ORIGINATED_CALL_ID, c);

                    if (NativeInterface.dialNative(getByteAddress(mCurrentDevice), c.getNumber())) {
                        addQueuedAction(DIAL_NUMBER, c.getNumber());
                        // Start looping on calling current calls.
                        sendMessage(QUERY_CURRENT_CALLS);
                    } else {
                        Log.e(TAG, "ERROR: Cannot dial with a given number:" + (String) message.obj);
                        // Set the call to terminated remove.
                        c.setState(BluetoothHeadsetClientCall.CALL_STATE_TERMINATED);
                        sendCallChangedIntent(c);
                        mCalls.remove(HF_ORIGINATED_CALL_ID);
                    }
                    break;
                case ACCEPT_CALL:
                    acceptCall(message.arg1);
                    break;
                case REJECT_CALL:
                    rejectCall();
                    break;
                case HOLD_CALL:
                    holdCall();
                    break;
                case TERMINATE_CALL:
                    terminateCall();
                    break;
                case ENTER_PRIVATE_MODE:
                    enterPrivateMode(message.arg1);
                    break;
                case EXPLICIT_CALL_TRANSFER:
                    explicitCallTransfer();
                    break;
                case SEND_DTMF:
                    if (NativeInterface.sendDtmfNative(getByteAddress(mCurrentDevice), (byte) message.arg1)) {
                        addQueuedAction(SEND_DTMF);
                    } else {
                        Log.e(TAG, "ERROR: Couldn't send DTMF");
                    }
                    break;
                case SUBSCRIBER_INFO:
                    if (NativeInterface.retrieveSubscriberInfoNative(getByteAddress(mCurrentDevice))) {
                        addQueuedAction(SUBSCRIBER_INFO);
                    } else {
                        Log.e(TAG, "ERROR: Couldn't retrieve subscriber info");
                    }
                    break;
                case QUERY_CURRENT_CALLS:
                    // Whenever the timer expires we query calls if there are outstanding requests
                    // for query calls.
                    long currentElapsed = SystemClock.elapsedRealtime();
                    if (mClccTimer < currentElapsed) {
                        queryCallsStart();
                        mClccTimer = currentElapsed + QUERY_CURRENT_CALLS_WAIT_MILLIS;
                        // Request satisfied, ignore all other call query messages.
                        removeMessages(QUERY_CURRENT_CALLS);
                    } else {
                        // Replace all messages with one concrete message.
                        removeMessages(QUERY_CURRENT_CALLS);
                        sendMessageDelayed(QUERY_CURRENT_CALLS, QUERY_CURRENT_CALLS_WAIT_MILLIS);
                    }
                    break;
                case StackEvent.STACK_EVENT:
                    Intent intent = null;
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                        Log.d(TAG, "Connected: event type: " + event.type);
                    }

                    switch (event.type) {
                        case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            if (DBG) {
                                Log.d(TAG, "Connected: Connection state changed: " + event.device
                                        + ": " + event.valueInt);
                            }
                            processConnectionEvent(
                                event.valueInt, event.device);
                            break;
                        case StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED:
                            if (DBG) {
                                Log.d(TAG, "Connected: Audio state changed: " + event.device + ": "
                                        + event.valueInt);
                            }
                            processAudioEvent(
                                event.valueInt, event.device);
                            break;
                        case StackEvent.EVENT_TYPE_NETWORK_STATE:
                            if (DBG) {
                                Log.d(TAG, "Connected: Network state: " + event.valueInt);
                            }
                            mIndicatorNetworkState = event.valueInt;

                            intent = new Intent(BluetoothHeadsetClient.ACTION_AG_EVENT);
                            intent.putExtra(BluetoothHeadsetClient.EXTRA_NETWORK_STATUS,
                                    event.valueInt);

                            if (mIndicatorNetworkState ==
                                    HeadsetClientHalConstants.NETWORK_STATE_NOT_AVAILABLE) {
                                mOperatorName = null;
                                intent.putExtra(BluetoothHeadsetClient.EXTRA_OPERATOR_NAME,
                                        mOperatorName);
                            }

                            intent.putExtra(
                                BluetoothDevice.EXTRA_DEVICE, event.device);
                            mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

                            if (mIndicatorNetworkState ==
                                    HeadsetClientHalConstants.NETWORK_STATE_AVAILABLE) {
                                if (NativeInterface.queryCurrentOperatorNameNative(
                                        getByteAddress(mCurrentDevice))) {
                                    addQueuedAction(QUERY_OPERATOR_NAME);
                                } else {
                                    Log.e(TAG, "ERROR: Couldn't querry operator name");
                                }
                            }
                            break;
                        case StackEvent.EVENT_TYPE_ROAMING_STATE:
                            mIndicatorNetworkType = event.valueInt;

                            intent = new Intent(BluetoothHeadsetClient.ACTION_AG_EVENT);
                            intent.putExtra(BluetoothHeadsetClient.EXTRA_NETWORK_ROAMING,
                                    event.valueInt);
                            intent.putExtra(
                                BluetoothDevice.EXTRA_DEVICE, event.device);
                            mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case StackEvent.EVENT_TYPE_NETWORK_SIGNAL:
                            mIndicatorNetworkSignal = event.valueInt;

                            intent = new Intent(BluetoothHeadsetClient.ACTION_AG_EVENT);
                            intent.putExtra(BluetoothHeadsetClient.EXTRA_NETWORK_SIGNAL_STRENGTH,
                                    event.valueInt);
                            intent.putExtra(
                                BluetoothDevice.EXTRA_DEVICE, event.device);
                            mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case StackEvent.EVENT_TYPE_BATTERY_LEVEL:
                            mIndicatorBatteryLevel = event.valueInt;

                            intent = new Intent(BluetoothHeadsetClient.ACTION_AG_EVENT);
                            intent.putExtra(BluetoothHeadsetClient.EXTRA_BATTERY_LEVEL,
                                    event.valueInt);
                            intent.putExtra(
                                BluetoothDevice.EXTRA_DEVICE, event.device);
                            mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case StackEvent.EVENT_TYPE_OPERATOR_NAME:
                            mOperatorName = event.valueString;

                            intent = new Intent(BluetoothHeadsetClient.ACTION_AG_EVENT);
                            intent.putExtra(BluetoothHeadsetClient.EXTRA_OPERATOR_NAME,
                                    event.valueString);
                            intent.putExtra(
                                BluetoothDevice.EXTRA_DEVICE, event.device);
                            mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case StackEvent.EVENT_TYPE_CALL:
                        case StackEvent.EVENT_TYPE_CALLSETUP:
                        case StackEvent.EVENT_TYPE_CALLHELD:
                        case StackEvent.EVENT_TYPE_RESP_AND_HOLD:
                        case StackEvent.EVENT_TYPE_CLIP:
                        case StackEvent.EVENT_TYPE_CALL_WAITING:
                            sendMessage(QUERY_CURRENT_CALLS);
                            break;
                        case StackEvent.EVENT_TYPE_CURRENT_CALLS:
                            queryCallsUpdate(
                                    event.valueInt,
                                    event.valueInt3,
                                    event.valueString,
                                    event.valueInt4 ==
                                            HeadsetClientHalConstants.CALL_MPTY_TYPE_MULTI,
                                    event.valueInt2 ==
                                            HeadsetClientHalConstants.CALL_DIRECTION_OUTGOING);
                            break;
                        case StackEvent.EVENT_TYPE_VOLUME_CHANGED:
                            if (event.valueInt == HeadsetClientHalConstants.VOLUME_TYPE_SPK) {
                                mCommandedSpeakerVolume = hfToAmVol(event.valueInt2);
                                Log.d(TAG, "AM volume set to " + mCommandedSpeakerVolume);
                                sAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                                        +mCommandedSpeakerVolume, AudioManager.FLAG_SHOW_UI);
                            } else if (event.valueInt
                                    == HeadsetClientHalConstants.VOLUME_TYPE_MIC) {
                                sAudioManager.setMicrophoneMute(event.valueInt2 == 0);
                            }
                            break;
                        case StackEvent.EVENT_TYPE_CMD_RESULT:
                            Pair<Integer, Object> queuedAction = mQueuedActions.poll();

                            // should not happen but...
                            if (queuedAction == null || queuedAction.first == NO_ACTION) {
                                clearPendingAction();
                                break;
                            }

                            if (DBG) {
                                Log.d(TAG, "Connected: command result: " + event.valueInt
                                        + " queuedAction: " + queuedAction.first);
                            }

                            switch (queuedAction.first) {
                                case QUERY_CURRENT_CALLS:
                                    queryCallsDone();
                                    break;
                                default:
                                    Log.w(TAG, "Unhandled AT OK " + event);
                                    break;
                            }

                            break;
                        case StackEvent.EVENT_TYPE_SUBSCRIBER_INFO:
                            mSubscriberInfo = event.valueString;
                            intent = new Intent(BluetoothHeadsetClient.ACTION_AG_EVENT);
                            intent.putExtra(BluetoothHeadsetClient.EXTRA_SUBSCRIBER_INFO,
                                    mSubscriberInfo);
                            intent.putExtra(
                                BluetoothDevice.EXTRA_DEVICE, event.device);
                            mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case StackEvent.EVENT_TYPE_RING_INDICATION:
                            // Ringing is not handled at this indication and rather should be
                            // implemented (by the client of this service). Use the
                            // CALL_STATE_INCOMING (and similar) handle ringing.
                            break;
                        default:
                            Log.e(TAG, "Unknown stack event: " + event.type);
                            break;
                    }

                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in Connected state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case HeadsetClientHalConstants.CONNECTION_STATE_DISCONNECTED:
                    if (DBG) {
                        Log.d(TAG, "Connected disconnects.");
                    }
                    // AG disconnects
                    if (mCurrentDevice.equals(device)) {
                        broadcastConnectionState(mCurrentDevice,
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_CONNECTED);
                        transitionTo(mDisconnected);
                    } else {
                        Log.e(TAG, "Disconnected from unknown device: " + device);
                    }
                    break;
                default:
                    Log.e(TAG, "Connection State Device: " + device + " bad state: " + state);
                    break;
            }
        }

        // in Connected state
        private void processAudioEvent(int state, BluetoothDevice device) {
            // message from old device
            if (!mCurrentDevice.equals(device)) {
                Log.e(TAG, "Audio changed on disconnected device: " + device);
                return;
            }

            switch (state) {
                case HeadsetClientHalConstants.AUDIO_STATE_CONNECTED_MSBC:
                    mAudioWbs = true;
                    // fall through
                case HeadsetClientHalConstants.AUDIO_STATE_CONNECTED:
                    // Audio state is split in two parts, the audio focus is maintained by the
                    // entity exercising this service (typically the Telecom stack) and audio
                    // routing is handled by the bluetooth stack itself. The only reason to do so is
                    // because Bluetooth SCO connection from the HF role is not entirely supported
                    // for routing and volume purposes.
                    // NOTE: All calls here are routed via the setParameters which changes the
                    // routing at the Audio HAL level.

                    if (mService.isScoRouted()) {
                        StackEvent event =
                                new StackEvent(StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED);
                        event.valueInt = state;
                        event.device = device;
                        sendMessageDelayed(StackEvent.STACK_EVENT, event, ROUTING_DELAY_MS);
                        break;
                    }

                    mAudioState = BluetoothHeadsetClient.STATE_AUDIO_CONNECTED;

                    // We need to set the volume after switching into HFP mode as some Audio HALs
                    // reset the volume to a known-default on mode switch.
                    final int amVol = sAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                    final int hfVol = amToHfVol(amVol);

                    if (DBG) {
                        Log.d(TAG,"hfp_enable=true mAudioWbs is " + mAudioWbs);
                    }
                    if (mAudioWbs) {
                        if (DBG) {
                            Log.d(TAG,"Setting sampling rate as 16000");
                        }
                        sAudioManager.setParameters("hfp_set_sampling_rate=16000");
                    }
                    else {
                        if (DBG) {
                            Log.d(TAG,"Setting sampling rate as 8000");
                        }
                        sAudioManager.setParameters("hfp_set_sampling_rate=8000");
                    }
                    if (DBG) {
                        Log.d(TAG, "hf_volume " + hfVol);
                    }
                    routeHfpAudio(true);
                    sAudioManager.setParameters("hfp_volume=" + hfVol);
                    transitionTo(mAudioOn);
                    break;

                case HeadsetClientHalConstants.AUDIO_STATE_CONNECTING:
                    broadcastAudioState(
                            device, BluetoothHeadsetClient.STATE_AUDIO_CONNECTING, mAudioState);
                    mAudioState = BluetoothHeadsetClient.STATE_AUDIO_CONNECTING;
                    break;

                case HeadsetClientHalConstants.AUDIO_STATE_DISCONNECTED:
                    broadcastAudioState(
                            device, BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED, mAudioState);
                    mAudioState = BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED;
                    break;

                default:
                    Log.e(TAG, "Audio State Device: " + device + " bad state: " + state);
                    break;
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Log.d(TAG, "Exit Connected: " + getCurrentMessage().what);
            }
        }
    }

    class AudioOn extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, "Enter AudioOn: " + getCurrentMessage().what);
            }
            broadcastAudioState(mCurrentDevice, BluetoothHeadsetClient.STATE_AUDIO_CONNECTED,
                BluetoothHeadsetClient.STATE_AUDIO_CONNECTING);
        }

        @Override
        public synchronized boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, "AudioOn process message: " + message.what);
            }
            if (DBG) {
                if (mCurrentDevice == null) {
                    Log.e(TAG, "ERROR: mCurrentDevice is null in Connected");
                    return NOT_HANDLED;
                }
            }

            switch (message.what) {
                case DISCONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mCurrentDevice.equals(device)) {
                        break;
                    }
                    deferMessage(message);
                    /*
                     * fall through - disconnect audio first then expect
                     * deferred DISCONNECT message in Connected state
                     */
                case DISCONNECT_AUDIO:
                    /*
                     * just disconnect audio and wait for
                     * StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED, that triggers State
                     * Machines state changing
                     */
                    if (NativeInterface.disconnectAudioNative(getByteAddress(mCurrentDevice))) {
                        routeHfpAudio(false);
                    }
                    break;

                case HOLD_CALL:
                    holdCall();
                    break;

                case StackEvent.STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                        Log.d(TAG, "AudioOn: event type: " + event.type);
                    }
                    switch (event.type) {
                        case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            if (DBG) {
                                Log.d(TAG, "AudioOn connection state changed" + event.device + ": "
                                        + event.valueInt);
                            }
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED:
                            if (DBG) {
                                Log.d(TAG, "AudioOn audio state changed" + event.device + ": "
                                        + event.valueInt);
                            }
                            processAudioEvent(event.valueInt, event.device);
                            break;
                        default:
                            return NOT_HANDLED;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in AudioOn state. Can AG disconnect RFCOMM prior to SCO? Handle this
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case HeadsetClientHalConstants.CONNECTION_STATE_DISCONNECTED:
                    if (mCurrentDevice.equals(device)) {
                        processAudioEvent(HeadsetClientHalConstants.AUDIO_STATE_DISCONNECTED,
                            device);
                        broadcastConnectionState(mCurrentDevice,
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_CONNECTED);
                        transitionTo(mDisconnected);
                    } else {
                        Log.e(TAG, "Disconnected from unknown device: " + device);
                    }
                    break;
                default:
                    Log.e(TAG, "Connection State Device: " + device + " bad state: " + state);
                    break;
            }
        }

        // in AudioOn state
        private void processAudioEvent(int state, BluetoothDevice device) {
            if (!mCurrentDevice.equals(device)) {
                Log.e(TAG, "Audio changed on disconnected device: " + device);
                return;
            }

            switch (state) {
                case HeadsetClientHalConstants.AUDIO_STATE_DISCONNECTED:
                    removeMessages(DISCONNECT_AUDIO);
                    mAudioState = BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED;
                    // Audio focus may still be held by the entity controlling the actual call
                    // (such as Telecom) and hence this will still keep the call around, there
                    // is not much we can do here since dropping the call without user consent
                    // even if the audio connection snapped may not be a good idea.
                    routeHfpAudio(false);
                    broadcastAudioState(device, BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED,
                            BluetoothHeadsetClient.STATE_AUDIO_CONNECTED);
                    transitionTo(mConnected);
                    break;

                default:
                    Log.e(TAG, "Audio State Device: " + device + " bad state: " + state);
                    break;
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Log.d(TAG, "Exit AudioOn: " + getCurrentMessage().what);
            }
        }
    }

    /**
     * @hide
     */
    public synchronized int getConnectionState(BluetoothDevice device) {
        if (mCurrentDevice == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }

        if (!mCurrentDevice.equals(device)) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }

        IState currentState = getCurrentState();
        if (currentState == mConnecting) {
            return BluetoothProfile.STATE_CONNECTING;
        }

        if (currentState == mConnected || currentState == mAudioOn) {
            return BluetoothProfile.STATE_CONNECTED;
        }

        Log.e(TAG, "Bad currentState: " + currentState);
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    private void broadcastAudioState(BluetoothDevice device, int newState, int prevState) {
        Intent intent = new Intent(BluetoothHeadsetClient.ACTION_AUDIO_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);

        if (newState == BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
            intent.putExtra(BluetoothHeadsetClient.EXTRA_AUDIO_WBS, mAudioWbs);
        }

        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        if (DBG) {
            Log.d(TAG, "Audio state " + device + ": " + prevState + "->" + newState);
        }
    }

    // This method does not check for error condition (newState == prevState)
    protected void broadcastConnectionState
            (BluetoothDevice device, int newState, int prevState) {
        if (DBG) {
            Log.d(TAG, "Connection state " + device + ": " + prevState + "->" + newState);
        }
        /*
         * Notifying the connection state change of the profile before sending
         * the intent for connection state change, as it was causing a race
         * condition, with the UI not being updated with the correct connection
         * state.
         */
        Intent intent = new Intent(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);

        // add feature extras when connected
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_3WAY) ==
                    HeadsetClientHalConstants.PEER_FEAT_3WAY) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_3WAY_CALLING, true);
            }
            if ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_REJECT) ==
                    HeadsetClientHalConstants.PEER_FEAT_REJECT) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_REJECT_CALL, true);
            }
            if ((mPeerFeatures & HeadsetClientHalConstants.PEER_FEAT_ECC) ==
                    HeadsetClientHalConstants.PEER_FEAT_ECC) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_ECC, true);
            }

            // add individual CHLD support extras
            if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_HOLD_ACC) ==
                    HeadsetClientHalConstants.CHLD_FEAT_HOLD_ACC) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_ACCEPT_HELD_OR_WAITING_CALL, true);
            }
            if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_REL) ==
                    HeadsetClientHalConstants.CHLD_FEAT_REL) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_RELEASE_HELD_OR_WAITING_CALL, true);
            }
            if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_REL_ACC) ==
                    HeadsetClientHalConstants.CHLD_FEAT_REL_ACC) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_RELEASE_AND_ACCEPT, true);
            }
            if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_MERGE) ==
                    HeadsetClientHalConstants.CHLD_FEAT_MERGE) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_MERGE, true);
            }
            if ((mChldFeatures & HeadsetClientHalConstants.CHLD_FEAT_MERGE_DETACH) ==
                    HeadsetClientHalConstants.CHLD_FEAT_MERGE_DETACH) {
                intent.putExtra(BluetoothHeadsetClient.EXTRA_AG_FEATURE_MERGE_AND_DETACH, true);
            }
        }
        mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    boolean isConnected() {
        IState currentState = getCurrentState();
        return (currentState == mConnected || currentState == mAudioOn);
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;
        synchronized (this) {
            for (BluetoothDevice device : bondedDevices) {
                ParcelUuid[] featureUuids = device.getUuids();
                if (!BluetoothUuid.isUuidPresent(featureUuids, BluetoothUuid.Handsfree_AG)) {
                    continue;
                }
                connectionState = getConnectionState(device);
                for (int state : states) {
                    if (connectionState == state) {
                        deviceList.add(device);
                    }
                }
            }
        }
        return deviceList;
    }

    boolean okToConnect(BluetoothDevice device) {
        int priority = mService.getPriority(device);
        boolean ret = false;
        // check priority and accept or reject the connection. if priority is
        // undefined
        // it is likely that our SDP has not completed and peer is initiating
        // the
        // connection. Allow this connection, provided the device is bonded
        if ((BluetoothProfile.PRIORITY_OFF < priority) ||
                ((BluetoothProfile.PRIORITY_UNDEFINED == priority) &&
                (device.getBondState() != BluetoothDevice.BOND_NONE))) {
            ret = true;
        }
        return ret;
    }

    boolean isAudioOn() {
        return (getCurrentState() == mAudioOn);
    }

    synchronized int getAudioState(BluetoothDevice device) {
        if (mCurrentDevice == null || !mCurrentDevice.equals(device)) {
            return BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED;
        }
        return mAudioState;
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized (this) {
            if (isConnected()) {
                devices.add(mCurrentDevice);
            }
        }
        return devices;
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    public List<BluetoothHeadsetClientCall> getCurrentCalls() {
        return new ArrayList<BluetoothHeadsetClientCall>(mCalls.values());
    }

    public Bundle getCurrentAgEvents() {
        Bundle b = new Bundle();
        b.putInt(BluetoothHeadsetClient.EXTRA_NETWORK_STATUS, mIndicatorNetworkState);
        b.putInt(BluetoothHeadsetClient.EXTRA_NETWORK_SIGNAL_STRENGTH, mIndicatorNetworkSignal);
        b.putInt(BluetoothHeadsetClient.EXTRA_NETWORK_ROAMING, mIndicatorNetworkType);
        b.putInt(BluetoothHeadsetClient.EXTRA_BATTERY_LEVEL, mIndicatorBatteryLevel);
        b.putString(BluetoothHeadsetClient.EXTRA_OPERATOR_NAME, mOperatorName);
        b.putString(BluetoothHeadsetClient.EXTRA_SUBSCRIBER_INFO, mSubscriberInfo);
        return b;
    }
}
