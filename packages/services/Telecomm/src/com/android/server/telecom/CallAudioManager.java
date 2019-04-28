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

package com.android.server.telecom;

import android.annotation.NonNull;
import android.media.IAudioService;
import android.media.ToneGenerator;
import android.telecom.CallAudioState;
import android.telecom.Log;
import android.telecom.VideoProfile;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;

public class CallAudioManager extends CallsManagerListenerBase {

    public interface AudioServiceFactory {
        IAudioService getAudioService();
    }

    private final String LOG_TAG = CallAudioManager.class.getSimpleName();

    private final LinkedHashSet<Call> mActiveDialingOrConnectingCalls;
    private final LinkedHashSet<Call> mRingingCalls;
    private final LinkedHashSet<Call> mHoldingCalls;
    private final Set<Call> mCalls;
    private final SparseArray<LinkedHashSet<Call>> mCallStateToCalls;

    private final CallAudioRouteStateMachine mCallAudioRouteStateMachine;
    private final CallAudioModeStateMachine mCallAudioModeStateMachine;
    private final CallsManager mCallsManager;
    private final InCallTonePlayer.Factory mPlayerFactory;
    private final Ringer mRinger;
    private final RingbackPlayer mRingbackPlayer;
    private final DtmfLocalTonePlayer mDtmfLocalTonePlayer;

    private Call mForegroundCall;
    private boolean mIsTonePlaying = false;
    private InCallTonePlayer mHoldTonePlayer;

    public CallAudioManager(CallAudioRouteStateMachine callAudioRouteStateMachine,
            CallsManager callsManager,
            CallAudioModeStateMachine callAudioModeStateMachine,
            InCallTonePlayer.Factory playerFactory,
            Ringer ringer,
            RingbackPlayer ringbackPlayer,
            DtmfLocalTonePlayer dtmfLocalTonePlayer) {
        mActiveDialingOrConnectingCalls = new LinkedHashSet<>();
        mRingingCalls = new LinkedHashSet<>();
        mHoldingCalls = new LinkedHashSet<>();
        mCalls = new HashSet<>();
        mCallStateToCalls = new SparseArray<LinkedHashSet<Call>>() {{
            put(CallState.CONNECTING, mActiveDialingOrConnectingCalls);
            put(CallState.ACTIVE, mActiveDialingOrConnectingCalls);
            put(CallState.DIALING, mActiveDialingOrConnectingCalls);
            put(CallState.PULLING, mActiveDialingOrConnectingCalls);
            put(CallState.RINGING, mRingingCalls);
            put(CallState.ON_HOLD, mHoldingCalls);
        }};

        mCallAudioRouteStateMachine = callAudioRouteStateMachine;
        mCallAudioModeStateMachine = callAudioModeStateMachine;
        mCallsManager = callsManager;
        mPlayerFactory = playerFactory;
        mRinger = ringer;
        mRingbackPlayer = ringbackPlayer;
        mDtmfLocalTonePlayer = dtmfLocalTonePlayer;

        mPlayerFactory.setCallAudioManager(this);
        mCallAudioModeStateMachine.setCallAudioManager(this);
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        if (shouldIgnoreCallForAudio(call)) {
            // No audio management for calls in a conference, or external calls.
            return;
        }
        Log.d(LOG_TAG, "Call state changed for TC@%s: %s -> %s", call.getId(),
                CallState.toString(oldState), CallState.toString(newState));

        for (int i = 0; i < mCallStateToCalls.size(); i++) {
            mCallStateToCalls.valueAt(i).remove(call);
        }
        if (mCallStateToCalls.get(newState) != null) {
            mCallStateToCalls.get(newState).add(call);
        }

        updateForegroundCall();
        if (shouldPlayDisconnectTone(oldState, newState)) {
            playToneForDisconnectedCall(call);
        }

        onCallLeavingState(call, oldState);
        onCallEnteringState(call, newState);
    }

    @Override
    public void onCallAdded(Call call) {
        if (shouldIgnoreCallForAudio(call)) {
            return; // Don't do audio handling for calls in a conference, or external calls.
        }

        addCall(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        if (shouldIgnoreCallForAudio(call)) {
            return; // Don't do audio handling for calls in a conference, or external calls.
        }

        removeCall(call);
    }

    private void addCall(Call call) {
        if (mCalls.contains(call)) {
            Log.w(LOG_TAG, "Call TC@%s is being added twice.", call.getId());
            return; // No guarantees that the same call won't get added twice.
        }

        Log.d(LOG_TAG, "Call added with id TC@%s in state %s", call.getId(),
                CallState.toString(call.getState()));

        if (mCallStateToCalls.get(call.getState()) != null) {
            mCallStateToCalls.get(call.getState()).add(call);
        }
        updateForegroundCall();
        mCalls.add(call);

        onCallEnteringState(call, call.getState());
    }

    private void removeCall(Call call) {
        if (!mCalls.contains(call)) {
            return; // No guarantees that the same call won't get removed twice.
        }

        Log.d(LOG_TAG, "Call removed with id TC@%s in state %s", call.getId(),
                CallState.toString(call.getState()));

        for (int i = 0; i < mCallStateToCalls.size(); i++) {
            mCallStateToCalls.valueAt(i).remove(call);
        }

        updateForegroundCall();
        mCalls.remove(call);

        onCallLeavingState(call, call.getState());
    }

    /**
     * Handles changes to the external state of a call.  External calls which become regular calls
     * should be tracked, and regular calls which become external should no longer be tracked.
     *
     * @param call The call.
     * @param isExternalCall {@code True} if the call is now external, {@code false} if it is now
     *      a regular call.
     */
    @Override
    public void onExternalCallChanged(Call call, boolean isExternalCall) {
        if (isExternalCall) {
            Log.d(LOG_TAG, "Removing call which became external ID %s", call.getId());
            removeCall(call);
        } else if (!isExternalCall) {
            Log.d(LOG_TAG, "Adding external call which was pulled with ID %s", call.getId());
            addCall(call);

            if (mCallsManager.isSpeakerphoneAutoEnabledForVideoCalls(call.getVideoState())) {
                // When pulling a video call, automatically enable the speakerphone.
                Log.d(LOG_TAG, "Switching to speaker because external video call %s was pulled." +
                        call.getId());
                mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                        CallAudioRouteStateMachine.SWITCH_SPEAKER);
            }
        }
    }

    /**
     * Determines if {@link CallAudioManager} should do any audio routing operations for a call.
     * We ignore child calls of a conference and external calls for audio routing purposes.
     *
     * @param call The call to check.
     * @return {@code true} if the call should be ignored for audio routing, {@code false}
     * otherwise
     */
    private boolean shouldIgnoreCallForAudio(Call call) {
        return call.getParentCall() != null || call.isExternalCall();
    }

    @Override
    public void onIncomingCallAnswered(Call call) {
        if (!mCalls.contains(call)) {
            return;
        }

        // This is called after the UI answers the call, but before the connection service
        // sets the call to active. Only thing to handle for mode here is the audio speedup thing.

        if (call.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO)) {
            if (mForegroundCall == call) {
                Log.i(LOG_TAG, "Invoking the MT_AUDIO_SPEEDUP mechanism. Transitioning into " +
                        "an active in-call audio state before connection service has " +
                        "connected the call.");
                if (mCallStateToCalls.get(call.getState()) != null) {
                    mCallStateToCalls.get(call.getState()).remove(call);
                }
                mActiveDialingOrConnectingCalls.add(call);
                mCallAudioModeStateMachine.sendMessageWithArgs(
                        CallAudioModeStateMachine.MT_AUDIO_SPEEDUP_FOR_RINGING_CALL,
                        makeArgsForModeStateMachine());
            }
        }

        // Turn off mute when a new incoming call is answered iff it's not a handover.
        if (!call.isHandoverInProgress()) {
            mute(false /* shouldMute */);
        }

        maybeStopRingingAndCallWaitingForAnsweredOrRejectedCall(call);
    }

    @Override
    public void onSessionModifyRequestReceived(Call call, VideoProfile videoProfile) {
        if (videoProfile == null) {
            return;
        }

        if (call != mForegroundCall) {
            // We only play tones for foreground calls.
            return;
        }

        int previousVideoState = call.getVideoState();
        int newVideoState = videoProfile.getVideoState();
        Log.v(this, "onSessionModifyRequestReceived : videoProfile = " + VideoProfile
                .videoStateToString(newVideoState));

        boolean isUpgradeRequest = !VideoProfile.isReceptionEnabled(previousVideoState) &&
                VideoProfile.isReceptionEnabled(newVideoState);

        if (isUpgradeRequest) {
            mPlayerFactory.createPlayer(InCallTonePlayer.TONE_VIDEO_UPGRADE).startTone();
        }
    }

    /**
     * Play or stop a call hold tone for a call.  Triggered via
     * {@link Connection#sendConnectionEvent(String)} when the
     * {@link Connection#EVENT_ON_HOLD_TONE_START} event or
     * {@link Connection#EVENT_ON_HOLD_TONE_STOP} event is passed through to the
     *
     * @param call The call which requested the hold tone.
     */
    @Override
    public void onHoldToneRequested(Call call) {
        maybePlayHoldTone();
    }

    @Override
    public void onIsVoipAudioModeChanged(Call call) {
        if (call != mForegroundCall) {
            return;
        }
        mCallAudioModeStateMachine.sendMessageWithArgs(
                CallAudioModeStateMachine.FOREGROUND_VOIP_MODE_CHANGE,
                makeArgsForModeStateMachine());
    }

    @Override
    public void onRingbackRequested(Call call, boolean shouldRingback) {
        if (call == mForegroundCall && shouldRingback) {
            mRingbackPlayer.startRingbackForCall(call);
        } else {
            mRingbackPlayer.stopRingbackForCall(call);
        }
    }

    @Override
    public void onIncomingCallRejected(Call call, boolean rejectWithMessage, String message) {
        maybeStopRingingAndCallWaitingForAnsweredOrRejectedCall(call);
    }

    @Override
    public void onIsConferencedChanged(Call call) {
        // This indicates a conferencing change, which shouldn't impact any audio mode stuff.
        Call parentCall = call.getParentCall();
        if (parentCall == null) {
            // Indicates that the call should be tracked for audio purposes. Treat it as if it were
            // just added.
            Log.i(LOG_TAG, "Call TC@" + call.getId() + " left conference and will" +
                            " now be tracked by CallAudioManager.");
            onCallAdded(call);
        } else {
            // The call joined a conference, so stop tracking it.
            if (mCallStateToCalls.get(call.getState()) != null) {
                mCallStateToCalls.get(call.getState()).remove(call);
            }

            updateForegroundCall();
            mCalls.remove(call);
        }
    }

    @Override
    public void onConnectionServiceChanged(Call call, ConnectionServiceWrapper oldCs,
            ConnectionServiceWrapper newCs) {
        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
    }

    @Override
    public void onVideoStateChanged(Call call, int previousVideoState, int newVideoState) {
        if (call != getForegroundCall()) {
            Log.d(LOG_TAG, "Ignoring video state change from %s to %s for call %s -- not " +
                    "foreground.", VideoProfile.videoStateToString(previousVideoState),
                    VideoProfile.videoStateToString(newVideoState), call.getId());
            return;
        }

        if (!VideoProfile.isVideo(previousVideoState) &&
                mCallsManager.isSpeakerphoneAutoEnabledForVideoCalls(newVideoState)) {
            Log.d(LOG_TAG, "Switching to speaker because call %s transitioned video state from %s" +
                    " to %s", call.getId(), VideoProfile.videoStateToString(previousVideoState),
                    VideoProfile.videoStateToString(newVideoState));
            mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                    CallAudioRouteStateMachine.SWITCH_SPEAKER);
        }
    }

    public CallAudioState getCallAudioState() {
        return mCallAudioRouteStateMachine.getCurrentCallAudioState();
    }

    public Call getPossiblyHeldForegroundCall() {
        return mForegroundCall;
    }

    public Call getForegroundCall() {
        if (mForegroundCall != null && mForegroundCall.getState() != CallState.ON_HOLD) {
            return mForegroundCall;
        }
        return null;
    }

    void toggleMute() {
        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.TOGGLE_MUTE);
    }

    @VisibleForTesting
    public void mute(boolean shouldMute) {
        Log.v(this, "mute, shouldMute: %b", shouldMute);

        // Don't mute if there are any emergency calls.
        if (mCallsManager.hasEmergencyCall()) {
            shouldMute = false;
            Log.v(this, "ignoring mute for emergency call");
        }

        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(shouldMute
                ? CallAudioRouteStateMachine.MUTE_ON : CallAudioRouteStateMachine.MUTE_OFF);
    }

    /**
     * Changed the audio route, for example from earpiece to speaker phone.
     *
     * @param route The new audio route to use. See {@link CallAudioState}.
     */
    void setAudioRoute(int route) {
        Log.v(this, "setAudioRoute, route: %s", CallAudioState.audioRouteToString(route));
        switch (route) {
            case CallAudioState.ROUTE_BLUETOOTH:
                mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                        CallAudioRouteStateMachine.USER_SWITCH_BLUETOOTH);
                return;
            case CallAudioState.ROUTE_SPEAKER:
                mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                        CallAudioRouteStateMachine.USER_SWITCH_SPEAKER);
                return;
            case CallAudioState.ROUTE_WIRED_HEADSET:
                mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                        CallAudioRouteStateMachine.USER_SWITCH_HEADSET);
                return;
            case CallAudioState.ROUTE_EARPIECE:
                mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                        CallAudioRouteStateMachine.USER_SWITCH_EARPIECE);
                return;
            case CallAudioState.ROUTE_WIRED_OR_EARPIECE:
                mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                        CallAudioRouteStateMachine.USER_SWITCH_BASELINE_ROUTE,
                        CallAudioRouteStateMachine.NO_INCLUDE_BLUETOOTH_IN_BASELINE);
                return;
            default:
                Log.wtf(this, "Invalid route specified: %d", route);
        }
    }

    void silenceRingers() {
        for (Call call : mRingingCalls) {
            call.silence();
        }

        mRinger.stopRinging();
        mRinger.stopCallWaiting();
    }

    @VisibleForTesting
    public boolean startRinging() {
        return mRinger.startRinging(mForegroundCall,
                mCallAudioRouteStateMachine.isHfpDeviceAvailable());
    }

    @VisibleForTesting
    public void startCallWaiting() {
        if (mRingingCalls.size() == 1) {
            mRinger.startCallWaiting(mRingingCalls.iterator().next());
        }
    }

    @VisibleForTesting
    public void stopRinging() {
        mRinger.stopRinging();
    }

    @VisibleForTesting
    public void stopCallWaiting() {
        mRinger.stopCallWaiting();
    }

    @VisibleForTesting
    public void setCallAudioRouteFocusState(int focusState) {
        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.SWITCH_FOCUS, focusState);
    }

    @VisibleForTesting
    public CallAudioRouteStateMachine getCallAudioRouteStateMachine() {
        return mCallAudioRouteStateMachine;
    }

    @VisibleForTesting
    public CallAudioModeStateMachine getCallAudioModeStateMachine() {
        return mCallAudioModeStateMachine;
    }

    void dump(IndentingPrintWriter pw) {
        pw.println("All calls:");
        pw.increaseIndent();
        dumpCallsInCollection(pw, mCalls);
        pw.decreaseIndent();

        pw.println("Active dialing, or connecting calls:");
        pw.increaseIndent();
        dumpCallsInCollection(pw, mActiveDialingOrConnectingCalls);
        pw.decreaseIndent();

        pw.println("Ringing calls:");
        pw.increaseIndent();
        dumpCallsInCollection(pw, mRingingCalls);
        pw.decreaseIndent();

        pw.println("Holding calls:");
        pw.increaseIndent();
        dumpCallsInCollection(pw, mHoldingCalls);
        pw.decreaseIndent();

        pw.println("Foreground call:");
        pw.println(mForegroundCall);

        pw.println("CallAudioModeStateMachine pending messages:");
        pw.increaseIndent();
        mCallAudioModeStateMachine.dumpPendingMessages(pw);
        pw.decreaseIndent();

        pw.println("CallAudioRouteStateMachine pending messages:");
        pw.increaseIndent();
        mCallAudioRouteStateMachine.dumpPendingMessages(pw);
        pw.decreaseIndent();
    }

    @VisibleForTesting
    public void setIsTonePlaying(boolean isTonePlaying) {
        mIsTonePlaying = isTonePlaying;
        mCallAudioModeStateMachine.sendMessageWithArgs(
                isTonePlaying ? CallAudioModeStateMachine.TONE_STARTED_PLAYING
                        : CallAudioModeStateMachine.TONE_STOPPED_PLAYING,
                makeArgsForModeStateMachine());
    }

    private void onCallLeavingState(Call call, int state) {
        switch (state) {
            case CallState.ACTIVE:
            case CallState.CONNECTING:
                onCallLeavingActiveDialingOrConnecting();
                break;
            case CallState.RINGING:
                onCallLeavingRinging();
                break;
            case CallState.ON_HOLD:
                onCallLeavingHold();
                break;
            case CallState.PULLING:
                onCallLeavingActiveDialingOrConnecting();
                break;
            case CallState.DIALING:
                stopRingbackForCall(call);
                onCallLeavingActiveDialingOrConnecting();
                break;
        }
    }

    private void onCallEnteringState(Call call, int state) {
        switch (state) {
            case CallState.ACTIVE:
            case CallState.CONNECTING:
                onCallEnteringActiveDialingOrConnecting();
                break;
            case CallState.RINGING:
                onCallEnteringRinging();
                break;
            case CallState.ON_HOLD:
                onCallEnteringHold();
                break;
            case CallState.PULLING:
                onCallEnteringActiveDialingOrConnecting();
                break;
            case CallState.DIALING:
                onCallEnteringActiveDialingOrConnecting();
                playRingbackForCall(call);
                break;
        }
    }

    private void onCallLeavingActiveDialingOrConnecting() {
        if (mActiveDialingOrConnectingCalls.size() == 0) {
            mCallAudioModeStateMachine.sendMessageWithArgs(
                    CallAudioModeStateMachine.NO_MORE_ACTIVE_OR_DIALING_CALLS,
                    makeArgsForModeStateMachine());
        }
    }

    private void onCallLeavingRinging() {
        if (mRingingCalls.size() == 0) {
            mCallAudioModeStateMachine.sendMessageWithArgs(
                    CallAudioModeStateMachine.NO_MORE_RINGING_CALLS,
                    makeArgsForModeStateMachine());
        }
    }

    private void onCallLeavingHold() {
        if (mHoldingCalls.size() == 0) {
            mCallAudioModeStateMachine.sendMessageWithArgs(
                    CallAudioModeStateMachine.NO_MORE_HOLDING_CALLS,
                    makeArgsForModeStateMachine());
        }
    }

    private void onCallEnteringActiveDialingOrConnecting() {
        if (mActiveDialingOrConnectingCalls.size() == 1) {
            mCallAudioModeStateMachine.sendMessageWithArgs(
                    CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL,
                    makeArgsForModeStateMachine());
        }
    }

    private void onCallEnteringRinging() {
        if (mRingingCalls.size() == 1) {
            mCallAudioModeStateMachine.sendMessageWithArgs(
                    CallAudioModeStateMachine.NEW_RINGING_CALL,
                    makeArgsForModeStateMachine());
        }
    }

    private void onCallEnteringHold() {
        if (mHoldingCalls.size() == 1) {
            mCallAudioModeStateMachine.sendMessageWithArgs(
                    CallAudioModeStateMachine.NEW_HOLDING_CALL,
                    makeArgsForModeStateMachine());
        }
    }

    private void updateForegroundCall() {
        Call oldForegroundCall = mForegroundCall;
        if (mActiveDialingOrConnectingCalls.size() > 0) {
            // Give preference for connecting calls over active/dialing for foreground-ness.
            Call possibleConnectingCall = null;
            for (Call call : mActiveDialingOrConnectingCalls) {
                if (call.getState() == CallState.CONNECTING) {
                    possibleConnectingCall = call;
                }
            }
            mForegroundCall = possibleConnectingCall == null ?
                    mActiveDialingOrConnectingCalls.iterator().next() : possibleConnectingCall;
        } else if (mRingingCalls.size() > 0) {
            mForegroundCall = mRingingCalls.iterator().next();
        } else if (mHoldingCalls.size() > 0) {
            mForegroundCall = mHoldingCalls.iterator().next();
        } else {
            mForegroundCall = null;
        }

        if (mForegroundCall != oldForegroundCall) {
            mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                    CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
            mDtmfLocalTonePlayer.onForegroundCallChanged(oldForegroundCall, mForegroundCall);
            maybePlayHoldTone();
        }
    }

    @NonNull
    private CallAudioModeStateMachine.MessageArgs makeArgsForModeStateMachine() {
        return new CallAudioModeStateMachine.MessageArgs(
                mActiveDialingOrConnectingCalls.size() > 0,
                mRingingCalls.size() > 0,
                mHoldingCalls.size() > 0,
                mIsTonePlaying,
                mForegroundCall != null && mForegroundCall.getIsVoipAudioMode(),
                Log.createSubsession());
    }

    private void playToneForDisconnectedCall(Call call) {
        // If this call is being disconnected as a result of being handed over to another call,
        // we will not play a disconnect tone.
        if (call.isHandoverInProgress()) {
            Log.i(LOG_TAG, "Omitting tone because %s is being handed over.", call);
            return;
        }

        if (mForegroundCall != null && call != mForegroundCall && mCalls.size() > 1) {
            Log.v(LOG_TAG, "Omitting tone because we are not foreground" +
                    " and there is another call.");
            return;
        }

        if (call.getDisconnectCause() != null) {
            int toneToPlay = InCallTonePlayer.TONE_INVALID;

            Log.v(this, "Disconnect cause: %s.", call.getDisconnectCause());

            switch(call.getDisconnectCause().getTone()) {
                case ToneGenerator.TONE_SUP_BUSY:
                    toneToPlay = InCallTonePlayer.TONE_BUSY;
                    break;
                case ToneGenerator.TONE_SUP_CONGESTION:
                    toneToPlay = InCallTonePlayer.TONE_CONGESTION;
                    break;
                case ToneGenerator.TONE_CDMA_REORDER:
                    toneToPlay = InCallTonePlayer.TONE_REORDER;
                    break;
                case ToneGenerator.TONE_CDMA_ABBR_INTERCEPT:
                    toneToPlay = InCallTonePlayer.TONE_INTERCEPT;
                    break;
                case ToneGenerator.TONE_CDMA_CALLDROP_LITE:
                    toneToPlay = InCallTonePlayer.TONE_CDMA_DROP;
                    break;
                case ToneGenerator.TONE_SUP_ERROR:
                    toneToPlay = InCallTonePlayer.TONE_UNOBTAINABLE_NUMBER;
                    break;
                case ToneGenerator.TONE_PROP_PROMPT:
                    toneToPlay = InCallTonePlayer.TONE_CALL_ENDED;
                    break;
            }

            Log.d(this, "Found a disconnected call with tone to play %d.", toneToPlay);

            if (toneToPlay != InCallTonePlayer.TONE_INVALID) {
                mPlayerFactory.createPlayer(toneToPlay).startTone();
            }
        }
    }

    private void playRingbackForCall(Call call) {
        if (call == mForegroundCall && call.isRingbackRequested()) {
            mRingbackPlayer.startRingbackForCall(call);
        }
    }

    private void stopRingbackForCall(Call call) {
        mRingbackPlayer.stopRingbackForCall(call);
    }

    /**
     * Determines if a hold tone should be played and then starts or stops it accordingly.
     */
    private void maybePlayHoldTone() {
        if (shouldPlayHoldTone()) {
            if (mHoldTonePlayer == null) {
                mHoldTonePlayer = mPlayerFactory.createPlayer(InCallTonePlayer.TONE_CALL_WAITING);
                mHoldTonePlayer.startTone();
            }
        } else {
            if (mHoldTonePlayer != null) {
                mHoldTonePlayer.stopTone();
                mHoldTonePlayer = null;
            }
        }
    }

    /**
     * Determines if a hold tone should be played.
     * A hold tone should be played only if foreground call is equals with call which is
     * remotely held.
     *
     * @return {@code true} if the the hold tone should be played, {@code false} otherwise.
     */
    private boolean shouldPlayHoldTone() {
        Call foregroundCall = getForegroundCall();
        // If there is no foreground call, no hold tone should play.
        if (foregroundCall == null) {
            return false;
        }

        // If another call is ringing, no hold tone should play.
        if (mCallsManager.hasRingingCall()) {
            return false;
        }

        // If the foreground call isn't active, no hold tone should play. This might happen, for
        // example, if the user puts a remotely held call on hold itself.
        if (!foregroundCall.isActive()) {
            return false;
        }

        return foregroundCall.isRemotelyHeld();
    }

    private void dumpCallsInCollection(IndentingPrintWriter pw, Collection<Call> calls) {
        for (Call call : calls) {
            if (call != null) pw.println(call.getId());
        }
    }

    private void maybeStopRingingAndCallWaitingForAnsweredOrRejectedCall(Call call) {
        // Check to see if the call being answered/rejected is the only ringing call, since this
        // will be called before the connection service acknowledges the state change.
        if (mRingingCalls.size() == 0 ||
                (mRingingCalls.size() == 1 && call == mRingingCalls.iterator().next())) {
            mRinger.stopRinging();
            mRinger.stopCallWaiting();
        }
    }

    private boolean shouldPlayDisconnectTone(int oldState, int newState) {
        if (newState != CallState.DISCONNECTED) {
            return false;
        }
        return oldState == CallState.ACTIVE ||
                oldState == CallState.DIALING ||
                oldState == CallState.ON_HOLD;
    }

    @VisibleForTesting
    public Set<Call> getTrackedCalls() {
        return mCalls;
    }

    @VisibleForTesting
    public SparseArray<LinkedHashSet<Call>> getCallStateToCalls() {
        return mCallStateToCalls;
    }
}