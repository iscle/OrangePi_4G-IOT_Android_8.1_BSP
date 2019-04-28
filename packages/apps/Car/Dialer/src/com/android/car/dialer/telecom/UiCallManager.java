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
 * limitations under the License.
 */
package com.android.car.dialer.telecom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.car.dialer.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The entry point for all interactions between UI and telecom.
 */
public class UiCallManager {
    private static String TAG = "Em.TelecomMgr";

    // Rate limit how often you can place outgoing calls.
    private static final long MIN_TIME_BETWEEN_CALLS_MS = 3000;
    private static final List<Integer> sCallStateRank = new ArrayList<>();

    // Used to assign id's to UiCall objects as they're created.
    private static int nextCarPhoneCallId = 0;

    static {
        // States should be added from lowest rank to highest
        sCallStateRank.add(Call.STATE_DISCONNECTED);
        sCallStateRank.add(Call.STATE_DISCONNECTING);
        sCallStateRank.add(Call.STATE_NEW);
        sCallStateRank.add(Call.STATE_CONNECTING);
        sCallStateRank.add(Call.STATE_SELECT_PHONE_ACCOUNT);
        sCallStateRank.add(Call.STATE_HOLDING);
        sCallStateRank.add(Call.STATE_ACTIVE);
        sCallStateRank.add(Call.STATE_DIALING);
        sCallStateRank.add(Call.STATE_RINGING);
    }

    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private long mLastPlacedCallTimeMs;

    private TelecomManager mTelecomManager;
    private InCallServiceImpl mInCallService;
    private final Map<UiCall, Call> mCallMapping = new HashMap<>();
    private final List<CallListener> mCallListeners = new CopyOnWriteArrayList<>();

    public UiCallManager(Context context) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "SetUp");
        }

        mContext = context;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        Intent intent = new Intent(context, InCallServiceImpl.class);
        intent.setAction(InCallServiceImpl.ACTION_LOCAL_BIND);
        context.bindService(intent, mInCallServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection mInCallServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServiceConnected: " + name + ", service: " + binder);
            }
            mInCallService = ((InCallServiceImpl.LocalBinder) binder).getService();
            mInCallService.registerCallback(mInCallServiceCallback);

            // The InCallServiceImpl could be bound when we already have some active calls, let's
            // notify UI about these calls.
            for (Call telecomCall : mInCallService.getCalls()) {
                UiCall uiCall = doTelecomCallAdded(telecomCall);
                onStateChanged(uiCall, uiCall.getState());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServiceDisconnected: " + name);
            }
            mInCallService.unregisterCallback(mInCallServiceCallback);
        }

        private InCallServiceImpl.Callback mInCallServiceCallback =
                new InCallServiceImpl.Callback() {
                    @Override
                    public void onTelecomCallAdded(Call telecomCall) {
                        doTelecomCallAdded(telecomCall);
                    }

                    @Override
                    public void onTelecomCallRemoved(Call telecomCall) {
                        doTelecomCallRemoved(telecomCall);
                    }

                    @Override
                    public void onCallAudioStateChanged(CallAudioState audioState) {
                        doCallAudioStateChanged(audioState);
                    }
                };
    };

    public void tearDown() {
        if (mInCallService != null) {
            mContext.unbindService(mInCallServiceConnection);
            mInCallService = null;
        }
        mCallMapping.clear();
    }

    public void addListener(CallListener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "addListener: " + listener);
        }
        mCallListeners.add(listener);
    }

    public void removeListener(CallListener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "removeListener: " + listener);
        }
        mCallListeners.remove(listener);
    }

    protected void placeCall(String number) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "placeCall: " + number);
        }
        Uri uri = Uri.fromParts("tel", number, null);
        Log.d(TAG, "android.telecom.TelecomManager#placeCall: " + uri);
        mTelecomManager.placeCall(uri, null);
    }

    public void answerCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "answerCall: " + uiCall);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.answer(0);
        }
    }

    public void rejectCall(UiCall uiCall, boolean rejectWithMessage, String textMessage) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "rejectCall: " + uiCall + ", rejectWithMessage: " + rejectWithMessage
                    + "textMessage: " + textMessage);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.reject(rejectWithMessage, textMessage);
        }
    }

    public void disconnectCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "disconnectCall: " + uiCall);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.disconnect();
        }
    }

    public List<UiCall> getCalls() {
        return new ArrayList<>(mCallMapping.keySet());
    }

    public boolean getMuted() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getMuted");
        }
        if (mInCallService == null) {
            return false;
        }
        CallAudioState audioState = mInCallService.getCallAudioState();
        return audioState != null && audioState.isMuted();
    }

    public void setMuted(boolean muted) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setMuted: " + muted);
        }
        if (mInCallService == null) {
            return;
        }
        mInCallService.setMuted(muted);
    }

    public int getSupportedAudioRouteMask() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getSupportedAudioRouteMask");
        }

        CallAudioState audioState = getCallAudioStateOrNull();
        return audioState != null ? audioState.getSupportedRouteMask() : 0;
    }

    public int getAudioRoute() {
        CallAudioState audioState = getCallAudioStateOrNull();
        int audioRoute = audioState != null ? audioState.getRoute() : 0;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getAudioRoute " + audioRoute);
        }
        return audioRoute;
    }

    public void setAudioRoute(int audioRoute) {
        // In case of embedded where the CarKitt is always connected to one kind of speaker we
        // should simply ignore any setAudioRoute requests.
        Log.w(TAG, "setAudioRoute ignoring request " + audioRoute);
    }

    public void holdCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "holdCall: " + uiCall);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.hold();
        }
    }

    public void unholdCall(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "unholdCall: " + uiCall);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.unhold();
        }
    }

    public void playDtmfTone(UiCall uiCall, char digit) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "playDtmfTone: call: " + uiCall + ", digit: " + digit);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.playDtmfTone(digit);
        }
    }

    public void stopDtmfTone(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "stopDtmfTone: call: " + uiCall);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.stopDtmfTone();
        }
    }

    public void postDialContinue(UiCall uiCall, boolean proceed) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "postDialContinue: call: " + uiCall + ", proceed: " + proceed);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.postDialContinue(proceed);
        }
    }

    public void conference(UiCall uiCall, UiCall otherUiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "conference: call: " + uiCall + ", otherCall: " + otherUiCall);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        Call otherTelecomCall = mCallMapping.get(otherUiCall);
        if (telecomCall != null) {
            telecomCall.conference(otherTelecomCall);
        }
    }

    public void splitFromConference(UiCall uiCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "splitFromConference: call: " + uiCall);
        }

        Call telecomCall = mCallMapping.get(uiCall);
        if (telecomCall != null) {
            telecomCall.splitFromConference();
        }
    }

    private UiCall doTelecomCallAdded(final Call telecomCall) {
        Log.d(TAG, "doTelecomCallAdded: " + telecomCall);

        UiCall uiCall = getOrCreateCallContainer(telecomCall);
        telecomCall.registerCallback(new TelecomCallListener(this, uiCall));
        for (CallListener listener : mCallListeners) {
            listener.onCallAdded(uiCall);
        }
        Log.d(TAG, "Call backs registered");

        if (telecomCall.getState() == Call.STATE_SELECT_PHONE_ACCOUNT) {
            // TODO(b/26189994): need to show Phone Account picker to let user choose a phone
            // account. It should be an account from TelecomManager#getCallCapablePhoneAccounts
            // list.
            Log.w(TAG, "Need to select phone account for the given call: " + telecomCall + ", "
                    + "but this feature is not implemented yet.");
            telecomCall.disconnect();
        }
        return uiCall;
    }

    private void doTelecomCallRemoved(Call telecomCall) {
        UiCall uiCall = getOrCreateCallContainer(telecomCall);

        mCallMapping.remove(uiCall);

        for (CallListener listener : mCallListeners) {
            listener.onCallRemoved(uiCall);
        }
    }

    private void doCallAudioStateChanged(CallAudioState audioState) {
        for (CallListener listener : mCallListeners) {
            listener.onAudioStateChanged(audioState.isMuted(), audioState.getRoute(),
                    audioState.getSupportedRouteMask());
        }
    }

    private void onStateChanged(UiCall uiCall, int state) {
        for (CallListener listener : mCallListeners) {
            listener.onStateChanged(uiCall, state);
        }
    }

    private void onCallUpdated(UiCall uiCall) {
        for (CallListener listener : mCallListeners) {
            listener.onCallUpdated(uiCall);
        }
    }

    private UiCall getOrCreateCallContainer(Call telecomCall) {
        for (Map.Entry<UiCall, Call> entry : mCallMapping.entrySet()) {
            if (entry.getValue() == telecomCall) {
                return entry.getKey();
            }
        }

        UiCall uiCall = new UiCall(nextCarPhoneCallId++);
        updateCallContainerFromTelecom(uiCall, telecomCall);
        mCallMapping.put(uiCall, telecomCall);
        return uiCall;
    }

    private static void updateCallContainerFromTelecom(UiCall uiCall, Call telecomCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "updateCallContainerFromTelecom: call: " + uiCall + ", telecomCall: "
                    + telecomCall);
        }

        uiCall.setState(telecomCall.getState());
        uiCall.setHasChildren(!telecomCall.getChildren().isEmpty());
        uiCall.setHasParent(telecomCall.getParent() != null);

        Call.Details details = telecomCall.getDetails();
        if (details == null) {
            return;
        }

        uiCall.setConnectTimeMillis(details.getConnectTimeMillis());

        DisconnectCause cause = details.getDisconnectCause();
        uiCall.setDisconnectCause(cause == null ? null : cause.getLabel());

        GatewayInfo gatewayInfo = details.getGatewayInfo();
        uiCall.setGatewayInfoOriginalAddress(
                gatewayInfo == null ? null : gatewayInfo.getOriginalAddress());

        String number = "";
        if (gatewayInfo != null) {
            number = gatewayInfo.getOriginalAddress().getSchemeSpecificPart();
        } else if (details.getHandle() != null) {
            number = details.getHandle().getSchemeSpecificPart();
        }
        uiCall.setNumber(number);
    }

    private CallAudioState getCallAudioStateOrNull() {
        return mInCallService != null ? mInCallService.getCallAudioState() : null;
    }

    public static class CallListener {
        @SuppressWarnings("unused")
        public void dispatchPhoneKeyEvent(KeyEvent event) {}
        @SuppressWarnings("unused")
        public void onAudioStateChanged(boolean isMuted, int route, int supportedRouteMask) {}
        @SuppressWarnings("unused")
        public void onCallAdded(UiCall call) {}
        @SuppressWarnings("unused")
        public void onStateChanged(UiCall call, int state) {}
        @SuppressWarnings("unused")
        public void onCallUpdated(UiCall call) {}
        @SuppressWarnings("unused")
        public void onCallRemoved(UiCall call) {}
    }

    /** Returns a first call that matches at least one provided call state */
    public UiCall getCallWithState(int... callStates) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getCallWithState: " + callStates);
        }
        for (UiCall call : getCalls()) {
            for (int callState : callStates) {
                if (call.getState() == callState) {
                    return call;
                }
            }
        }
        return null;
    }

    public UiCall getPrimaryCall() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getPrimaryCall");
        }
        List<UiCall> calls = getCalls();
        if (calls.isEmpty()) {
            return null;
        }

        Collections.sort(calls, getCallComparator());
        UiCall uiCall = calls.get(0);
        if (uiCall.hasParent()) {
            return null;
        }
        return uiCall;
    }

    public UiCall getSecondaryCall() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getSecondaryCall");
        }
        List<UiCall> calls = getCalls();
        if (calls.size() < 2) {
            return null;
        }

        Collections.sort(calls, getCallComparator());
        UiCall uiCall = calls.get(1);
        if (uiCall.hasParent()) {
            return null;
        }
        return uiCall;
    }

    public static final int CAN_PLACE_CALL_RESULT_OK = 0;
    public static final int CAN_PLACE_CALL_RESULT_NETWORK_UNAVAILABLE = 1;
    public static final int CAN_PLACE_CALL_RESULT_HFP_UNAVAILABLE = 2;
    public static final int CAN_PLACE_CALL_RESULT_AIRPLANE_MODE = 3;

    public int getCanPlaceCallStatus(String number, boolean bluetoothRequired) {
        // TODO(b/26191392): figure out the logic for projected and embedded modes
        return CAN_PLACE_CALL_RESULT_OK;
    }

    public String getFailToPlaceCallMessage(int canPlaceCallResult) {
        switch (canPlaceCallResult) {
            case CAN_PLACE_CALL_RESULT_OK:
                return "";
            case CAN_PLACE_CALL_RESULT_HFP_UNAVAILABLE:
                return mContext.getString(R.string.error_no_hfp);
            case CAN_PLACE_CALL_RESULT_AIRPLANE_MODE:
                return mContext.getString(R.string.error_airplane_mode);
            case CAN_PLACE_CALL_RESULT_NETWORK_UNAVAILABLE:
            default:
                return mContext.getString(R.string.error_network_not_available);
        }
    }

    /** Places call only if there's no outgoing call right now */
    public void safePlaceCall(String number, boolean bluetoothRequired) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "safePlaceCall: " + number);
        }

        int placeCallStatus = getCanPlaceCallStatus(number, bluetoothRequired);
        if (placeCallStatus != CAN_PLACE_CALL_RESULT_OK) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unable to place a call: " + placeCallStatus);
            }
            return;
        }

        UiCall outgoingCall = getCallWithState(
                Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_DIALING);
        if (outgoingCall == null) {
            long now = Calendar.getInstance().getTimeInMillis();
            if (now - mLastPlacedCallTimeMs > MIN_TIME_BETWEEN_CALLS_MS) {
                placeCall(number);
                mLastPlacedCallTimeMs = now;
            } else {
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, "You have to wait " + MIN_TIME_BETWEEN_CALLS_MS
                            + "ms between making calls");
                }
            }
        }
    }

    public void callVoicemail() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "callVoicemail");
        }

        String voicemailNumber = TelecomUtils.getVoicemailNumber(mContext);
        if (TextUtils.isEmpty(voicemailNumber)) {
            Log.w(TAG, "Unable to get voicemail number.");
            return;
        }
        safePlaceCall(voicemailNumber, false);
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     * <p/>
     * It uses the next {@code count} rows in the cursor to extract the types.
     * <p/>
     * Its position in the cursor is unchanged by this function.
     */
    public int[] getCallTypes(Cursor cursor, int count) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getCallTypes: cursor: " + cursor + ", count: " + count);
        }

        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        String voicemailNumber = mTelephonyManager.getVoiceMailNumber();
        int column;
        for (int index = 0; index < count; ++index) {
            column = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            String phoneNumber = cursor.getString(column);
            if (phoneNumber != null && phoneNumber.equals(voicemailNumber)) {
                callTypes[index] = PhoneLoader.VOICEMAIL_TYPE;
            } else {
                column = cursor.getColumnIndex(CallLog.Calls.TYPE);
                callTypes[index] = cursor.getInt(column);
            }
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }

    private static Comparator<UiCall> getCallComparator() {
        return new Comparator<UiCall>() {
            @Override
            public int compare(UiCall call, UiCall otherCall) {
                if (call.hasParent() && !otherCall.hasParent()) {
                    return 1;
                } else if (!call.hasParent() && otherCall.hasParent()) {
                    return -1;
                }
                int carCallRank = sCallStateRank.indexOf(call.getState());
                int otherCarCallRank = sCallStateRank.indexOf(otherCall.getState());

                return otherCarCallRank - carCallRank;
            }
        };
    }

    private static class TelecomCallListener extends Call.Callback {
        private final WeakReference<UiCallManager> mCarTelecomMangerRef;
        private final WeakReference<UiCall> mCallContainerRef;

        TelecomCallListener(UiCallManager carTelecomManager, UiCall uiCall) {
            mCarTelecomMangerRef = new WeakReference<>(carTelecomManager);
            mCallContainerRef = new WeakReference<>(uiCall);
        }

        @Override
        public void onStateChanged(Call telecomCall, int state) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onStateChanged: " + state);
            }
            UiCallManager manager = mCarTelecomMangerRef.get();
            UiCall call = mCallContainerRef.get();
            if (manager != null && call != null) {
                call.setState(state);
                manager.onStateChanged(call, state);
            }
        }

        @Override
        public void onParentChanged(Call telecomCall, Call parent) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onCallDestroyed(Call telecomCall) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCallDestroyed");
            }
        }

        @Override
        public void onDetailsChanged(Call telecomCall, Call.Details details) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onVideoCallChanged(Call telecomCall, InCallService.VideoCall videoCall) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onCannedTextResponsesLoaded(Call telecomCall,
                List<String> cannedTextResponses) {
            doCallUpdated(telecomCall);
        }

        @Override
        public void onChildrenChanged(Call telecomCall, List<Call> children) {
            doCallUpdated(telecomCall);
        }

        private void doCallUpdated(Call telecomCall) {
            UiCallManager manager = mCarTelecomMangerRef.get();
            UiCall uiCall = mCallContainerRef.get();
            if (manager != null && uiCall != null) {
                updateCallContainerFromTelecom(uiCall, telecomCall);
                manager.onCallUpdated(uiCall);
            }
        }
    }
}
