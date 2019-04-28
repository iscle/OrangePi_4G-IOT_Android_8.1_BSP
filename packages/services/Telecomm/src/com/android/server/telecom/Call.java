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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.ContactsContract.Contacts;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.Connection;
import android.telecom.GatewayInfo;
import android.telecom.Log;
import android.telecom.Logging.EventManager;
import android.telecom.ParcelableConnection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.Response;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telecom.IVideoProvider;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.util.Preconditions;

import java.io.IOException;
import java.lang.String;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Encapsulates all aspects of a given phone call throughout its lifecycle, starting
 *  from the time the call intent was received by Telecom (vs. the time the call was
 *  connected etc).
 */
@VisibleForTesting
public class Call implements CreateConnectionResponse, EventManager.Loggable {
    public final static String CALL_ID_UNKNOWN = "-1";
    public final static long DATA_USAGE_NOT_SET = -1;

    public static final int CALL_DIRECTION_UNDEFINED = 0;
    public static final int CALL_DIRECTION_OUTGOING = 1;
    public static final int CALL_DIRECTION_INCOMING = 2;
    public static final int CALL_DIRECTION_UNKNOWN = 3;

    /** Identifies extras changes which originated from a connection service. */
    public static final int SOURCE_CONNECTION_SERVICE = 1;
    /** Identifies extras changes which originated from an incall service. */
    public static final int SOURCE_INCALL_SERVICE = 2;

    private static final int RTT_PIPE_READ_SIDE_INDEX = 0;
    private static final int RTT_PIPE_WRITE_SIDE_INDEX = 1;

    private static final int INVALID_RTT_REQUEST_ID = -1;
    /**
     * Listener for events on the call.
     */
    @VisibleForTesting
    public interface Listener {
        void onSuccessfulOutgoingCall(Call call, int callState);
        void onFailedOutgoingCall(Call call, DisconnectCause disconnectCause);
        void onSuccessfulIncomingCall(Call call);
        void onFailedIncomingCall(Call call);
        void onSuccessfulUnknownCall(Call call, int callState);
        void onFailedUnknownCall(Call call);
        void onRingbackRequested(Call call, boolean ringbackRequested);
        void onPostDialWait(Call call, String remaining);
        void onPostDialChar(Call call, char nextChar);
        void onConnectionCapabilitiesChanged(Call call);
        void onConnectionPropertiesChanged(Call call, boolean didRttChange);
        void onParentChanged(Call call);
        void onChildrenChanged(Call call);
        void onCannedSmsResponsesLoaded(Call call);
        void onVideoCallProviderChanged(Call call);
        void onCallerInfoChanged(Call call);
        void onIsVoipAudioModeChanged(Call call);
        void onStatusHintsChanged(Call call);
        void onExtrasChanged(Call c, int source, Bundle extras);
        void onExtrasRemoved(Call c, int source, List<String> keys);
        void onHandleChanged(Call call);
        void onCallerDisplayNameChanged(Call call);
        void onVideoStateChanged(Call call, int previousVideoState, int newVideoState);
        void onTargetPhoneAccountChanged(Call call);
        void onConnectionManagerPhoneAccountChanged(Call call);
        void onPhoneAccountChanged(Call call);
        void onConferenceableCallsChanged(Call call);
        boolean onCanceledViaNewOutgoingCallBroadcast(Call call, long disconnectionTimeout);
        void onHoldToneRequested(Call call);
        void onConnectionEvent(Call call, String event, Bundle extras);
        void onExternalCallChanged(Call call, boolean isExternalCall);
        void onRttInitiationFailure(Call call, int reason);
        void onRemoteRttRequest(Call call, int requestId);
        void onHandoverRequested(Call call, PhoneAccountHandle handoverTo, int videoState,
                                 Bundle extras);
    }

    public abstract static class ListenerBase implements Listener {
        @Override
        public void onSuccessfulOutgoingCall(Call call, int callState) {}
        @Override
        public void onFailedOutgoingCall(Call call, DisconnectCause disconnectCause) {}
        @Override
        public void onSuccessfulIncomingCall(Call call) {}
        @Override
        public void onFailedIncomingCall(Call call) {}
        @Override
        public void onSuccessfulUnknownCall(Call call, int callState) {}
        @Override
        public void onFailedUnknownCall(Call call) {}
        @Override
        public void onRingbackRequested(Call call, boolean ringbackRequested) {}
        @Override
        public void onPostDialWait(Call call, String remaining) {}
        @Override
        public void onPostDialChar(Call call, char nextChar) {}
        @Override
        public void onConnectionCapabilitiesChanged(Call call) {}
        @Override
        public void onConnectionPropertiesChanged(Call call, boolean didRttChange) {}
        @Override
        public void onParentChanged(Call call) {}
        @Override
        public void onChildrenChanged(Call call) {}
        @Override
        public void onCannedSmsResponsesLoaded(Call call) {}
        @Override
        public void onVideoCallProviderChanged(Call call) {}
        @Override
        public void onCallerInfoChanged(Call call) {}
        @Override
        public void onIsVoipAudioModeChanged(Call call) {}
        @Override
        public void onStatusHintsChanged(Call call) {}
        @Override
        public void onExtrasChanged(Call c, int source, Bundle extras) {}
        @Override
        public void onExtrasRemoved(Call c, int source, List<String> keys) {}
        @Override
        public void onHandleChanged(Call call) {}
        @Override
        public void onCallerDisplayNameChanged(Call call) {}
        @Override
        public void onVideoStateChanged(Call call, int previousVideoState, int newVideoState) {}
        @Override
        public void onTargetPhoneAccountChanged(Call call) {}
        @Override
        public void onConnectionManagerPhoneAccountChanged(Call call) {}
        @Override
        public void onPhoneAccountChanged(Call call) {}
        @Override
        public void onConferenceableCallsChanged(Call call) {}
        @Override
        public boolean onCanceledViaNewOutgoingCallBroadcast(Call call, long disconnectionTimeout) {
            return false;
        }
        @Override
        public void onHoldToneRequested(Call call) {}
        @Override
        public void onConnectionEvent(Call call, String event, Bundle extras) {}
        @Override
        public void onExternalCallChanged(Call call, boolean isExternalCall) {}
        @Override
        public void onRttInitiationFailure(Call call, int reason) {}
        @Override
        public void onRemoteRttRequest(Call call, int requestId) {}
        @Override
        public void onHandoverRequested(Call call, PhoneAccountHandle handoverTo, int videoState,
                                        Bundle extras) {}
    }

    private final CallerInfoLookupHelper.OnQueryCompleteListener mCallerInfoQueryListener =
            new CallerInfoLookupHelper.OnQueryCompleteListener() {
                /** ${inheritDoc} */
                @Override
                public void onCallerInfoQueryComplete(Uri handle, CallerInfo callerInfo) {
                    synchronized (mLock) {
                        Call.this.setCallerInfo(handle, callerInfo);
                    }
                }

                @Override
                public void onContactPhotoQueryComplete(Uri handle, CallerInfo callerInfo) {
                    synchronized (mLock) {
                        Call.this.setCallerInfo(handle, callerInfo);
                    }
                }
            };

    /**
     * One of CALL_DIRECTION_INCOMING, CALL_DIRECTION_OUTGOING, or CALL_DIRECTION_UNKNOWN
     */
    private final int mCallDirection;

    /**
     * The post-dial digits that were dialed after the network portion of the number
     */
    private final String mPostDialDigits;

    /**
     * The secondary line number that an incoming call has been received on if the SIM subscription
     * has multiple associated numbers.
     */
    private String mViaNumber = "";

    /**
     * The wall clock time this call was created. Beyond logging and such, may also be used for
     * bookkeeping and specifically for marking certain call attempts as failed attempts.
     * Note: This timestamp should NOT be used for calculating call duration.
     */
    private long mCreationTimeMillis;

    /** The time this call was made active. */
    private long mConnectTimeMillis = 0;

    /**
     * The time, in millis, since boot when this call was connected.  This should ONLY be used when
     * calculating the duration of the call.
     *
     * The reason for this is that the {@link SystemClock#elapsedRealtime()} is based on the
     * elapsed time since the device was booted.  Changes to the system clock (e.g. due to NITZ
     * time sync, time zone changes user initiated clock changes) would cause a duration calculated
     * based on {@link #mConnectTimeMillis} to change based on the delta in the time.
     * Using the {@link SystemClock#elapsedRealtime()} ensures that changes to the wall clock do
     * not impact the call duration.
     */
    private long mConnectElapsedTimeMillis = 0;

    /** The wall clock time this call was disconnected. */
    private long mDisconnectTimeMillis = 0;

    /**
     * The elapsed time since boot when this call was disconnected.  Recorded as the
     * {@link SystemClock#elapsedRealtime()}.  This ensures that the call duration is not impacted
     * by changes in the wall time clock.
     */
    private long mDisconnectElapsedTimeMillis = 0;

    /** The gateway information associated with this call. This stores the original call handle
     * that the user is attempting to connect to via the gateway, the actual handle to dial in
     * order to connect the call via the gateway, as well as the package name of the gateway
     * service. */
    private GatewayInfo mGatewayInfo;

    private PhoneAccountHandle mConnectionManagerPhoneAccountHandle;

    private PhoneAccountHandle mTargetPhoneAccountHandle;

    private UserHandle mInitiatingUser;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final List<Call> mConferenceableCalls = new ArrayList<>();

    /** The state of the call. */
    private int mState;

    /** The handle with which to establish this call. */
    private Uri mHandle;

    /**
     * The presentation requirements for the handle. See {@link TelecomManager} for valid values.
     */
    private int mHandlePresentation;

    /** The caller display name (CNAP) set by the connection service. */
    private String mCallerDisplayName;

    /**
     * The presentation requirements for the handle. See {@link TelecomManager} for valid values.
     */
    private int mCallerDisplayNamePresentation;

    /**
     * The connection service which is attempted or already connecting this call.
     */
    private ConnectionServiceWrapper mConnectionService;

    private boolean mIsEmergencyCall;

    private boolean mSpeakerphoneOn;

    private boolean mIsDisconnectingChildCall = false;

    /**
     * Tracks the video states which were applicable over the duration of a call.
     * See {@link VideoProfile} for a list of valid video states.
     * <p>
     * Video state history is tracked when the call is active, and when a call is rejected or
     * missed.
     */
    private int mVideoStateHistory;

    private int mVideoState;

    /**
     * Disconnect cause for the call. Only valid if the state of the call is STATE_DISCONNECTED.
     * See {@link android.telecom.DisconnectCause}.
     */
    private DisconnectCause mDisconnectCause = new DisconnectCause(DisconnectCause.UNKNOWN);

    private Bundle mIntentExtras = new Bundle();

    /**
     * The {@link Intent} which originally created this call.  Only populated when we are putting a
     * call into a pending state and need to pick up initiation of the call later.
     */
    private Intent mOriginalCallIntent = null;

    /** Set of listeners on this call.
     *
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));

    private CreateConnectionProcessor mCreateConnectionProcessor;

    /** Caller information retrieved from the latest contact query. */
    private CallerInfo mCallerInfo;

    /** The latest token used with a contact info query. */
    private int mQueryToken = 0;

    /** Whether this call is requesting that Telecom play the ringback tone on its behalf. */
    private boolean mRingbackRequested = false;

    /** Whether direct-to-voicemail query is pending. */
    private boolean mDirectToVoicemailQueryPending;

    private int mConnectionCapabilities;

    private int mConnectionProperties;

    private int mSupportedAudioRoutes = CallAudioState.ROUTE_ALL;

    private boolean mIsConference = false;

    private final boolean mShouldAttachToExistingConnection;

    private Call mParentCall = null;

    private List<Call> mChildCalls = new LinkedList<>();

    /** Set of text message responses allowed for this call, if applicable. */
    private List<String> mCannedSmsResponses = Collections.EMPTY_LIST;

    /** Whether an attempt has been made to load the text message responses. */
    private boolean mCannedSmsResponsesLoadingStarted = false;

    private IVideoProvider mVideoProvider;
    private VideoProviderProxy mVideoProviderProxy;

    private boolean mIsVoipAudioMode;
    private StatusHints mStatusHints;
    private Bundle mExtras;
    private final ConnectionServiceRepository mRepository;
    private final Context mContext;
    private final CallsManager mCallsManager;
    private final ClockProxy mClockProxy;
    private final TelecomSystem.SyncRoot mLock;
    private final String mId;
    private String mConnectionId;
    private Analytics.CallInfo mAnalytics;

    private boolean mWasConferencePreviouslyMerged = false;

    // For conferences which support merge/swap at their level, we retain a notion of an active
    // call. This is used for BluetoothPhoneService.  In order to support hold/merge, it must have
    // the notion of the current "active" call within the conference call. This maintains the
    // "active" call and switches every time the user hits "swap".
    private Call mConferenceLevelActiveCall = null;

    private boolean mIsLocallyDisconnecting = false;

    /**
     * Tracks the current call data usage as reported by the video provider.
     */
    private long mCallDataUsage = DATA_USAGE_NOT_SET;

    private boolean mIsWorkCall;

    // Set to true once the NewOutgoingCallIntentBroadcast comes back and is processed.
    private boolean mIsNewOutgoingCallIntentBroadcastDone = false;

    /**
     * Indicates whether the call is remotely held.  A call is considered remotely held when
     * {@link #onConnectionEvent(String)} receives the {@link Connection#EVENT_ON_HOLD_TONE_START}
     * event.
     */
    private boolean mIsRemotelyHeld = false;

    /**
     * Indicates whether the {@link PhoneAccount} associated with this call is self-managed.
     * See {@link PhoneAccount#CAPABILITY_SELF_MANAGED} for more information.
     */
    private boolean mIsSelfManaged = false;

    /**
     * Indicates whether the {@link PhoneAccount} associated with this call supports video calling.
     * {@code True} if the phone account supports video calling, {@code false} otherwise.
     */
    private boolean mIsVideoCallingSupported = false;

    private PhoneNumberUtilsAdapter mPhoneNumberUtilsAdapter;

    /**
     * For {@link Connection}s or {@link android.telecom.Conference}s added via a ConnectionManager
     * using the {@link android.telecom.ConnectionService#addExistingConnection(PhoneAccountHandle,
     * Connection)} or {@link android.telecom.ConnectionService#addConference(Conference)},
     * indicates the ID of this call as it was referred to by the {@code ConnectionService} which
     * originally created it.
     *
     * See {@link Connection#EXTRA_ORIGINAL_CONNECTION_ID} for more information.
     */
    private String mOriginalConnectionId;

    /**
     * Two pairs of {@link android.os.ParcelFileDescriptor}s that handle RTT text communication
     * between the in-call app and the connection service. If both non-null, this call should be
     * treated as an RTT call.
     * Each array should be of size 2. First one is the read side and the second one is the write
     * side.
     */
    private ParcelFileDescriptor[] mInCallToConnectionServiceStreams;
    private ParcelFileDescriptor[] mConnectionServiceToInCallStreams;
    /**
     * Integer constant from {@link android.telecom.Call.RttCall}. Describes the current RTT mode.
     */
    private int mRttMode;

    /**
     * Integer indicating the remote RTT request ID that is pending a response from the user.
     */
    private int mPendingRttRequestId = INVALID_RTT_REQUEST_ID;

    /**
     * When a call handover has been initiated via {@link #requestHandover(PhoneAccountHandle,
     * int, Bundle)}, contains the call which this call is being handed over to.
     */
    private Call mHandoverDestinationCall = null;

    /**
     * When a call handover has been initiated via {@link #requestHandover(PhoneAccountHandle,
     * int, Bundle)}, contains the call which this call is being handed over from.
     */
    private Call mHandoverSourceCall = null;

    /**
     * Indicates the current state of this call if it is in the process of a handover.
     */
    private int mHandoverState = HandoverState.HANDOVER_NONE;

    /**
     * Persists the specified parameters and initializes the new instance.
     *  @param context The context.
     * @param repository The connection service repository.
     * @param handle The handle to dial.
     * @param gatewayInfo Gateway information to use for the call.
     * @param connectionManagerPhoneAccountHandle Account to use for the service managing the call.
*         This account must be one that was registered with the
*         {@link PhoneAccount#CAPABILITY_CONNECTION_MANAGER} flag.
     * @param targetPhoneAccountHandle Account information to use for the call. This account must be
*         one that was registered with the {@link PhoneAccount#CAPABILITY_CALL_PROVIDER} flag.
     * @param callDirection one of CALL_DIRECTION_INCOMING, CALL_DIRECTION_OUTGOING,
*         or CALL_DIRECTION_UNKNOWN.
     * @param shouldAttachToExistingConnection Set to true to attach the call to an existing
     * @param clockProxy
     */
    public Call(
            String callId,
            Context context,
            CallsManager callsManager,
            TelecomSystem.SyncRoot lock,
            ConnectionServiceRepository repository,
            ContactsAsyncHelper contactsAsyncHelper,
            CallerInfoAsyncQueryFactory callerInfoAsyncQueryFactory,
            PhoneNumberUtilsAdapter phoneNumberUtilsAdapter,
            Uri handle,
            GatewayInfo gatewayInfo,
            PhoneAccountHandle connectionManagerPhoneAccountHandle,
            PhoneAccountHandle targetPhoneAccountHandle,
            int callDirection,
            boolean shouldAttachToExistingConnection,
            boolean isConference,
            ClockProxy clockProxy) {
        mId = callId;
        mConnectionId = callId;
        mState = isConference ? CallState.ACTIVE : CallState.NEW;
        mContext = context;
        mCallsManager = callsManager;
        mLock = lock;
        mRepository = repository;
        mPhoneNumberUtilsAdapter = phoneNumberUtilsAdapter;
        setHandle(handle);
        mPostDialDigits = handle != null
                ? PhoneNumberUtils.extractPostDialPortion(handle.getSchemeSpecificPart()) : "";
        mGatewayInfo = gatewayInfo;
        setConnectionManagerPhoneAccount(connectionManagerPhoneAccountHandle);
        setTargetPhoneAccount(targetPhoneAccountHandle);
        mCallDirection = callDirection;
        mIsConference = isConference;
        mShouldAttachToExistingConnection = shouldAttachToExistingConnection
                || callDirection == CALL_DIRECTION_INCOMING;
        maybeLoadCannedSmsResponses();
        mAnalytics = new Analytics.CallInfo();
        mClockProxy = clockProxy;
        mCreationTimeMillis = mClockProxy.currentTimeMillis();
    }

    /**
     * Persists the specified parameters and initializes the new instance.
     *  @param context The context.
     * @param repository The connection service repository.
     * @param handle The handle to dial.
     * @param gatewayInfo Gateway information to use for the call.
     * @param connectionManagerPhoneAccountHandle Account to use for the service managing the call.
*         This account must be one that was registered with the
*         {@link PhoneAccount#CAPABILITY_CONNECTION_MANAGER} flag.
     * @param targetPhoneAccountHandle Account information to use for the call. This account must be
*         one that was registered with the {@link PhoneAccount#CAPABILITY_CALL_PROVIDER} flag.
     * @param callDirection one of CALL_DIRECTION_INCOMING, CALL_DIRECTION_OUTGOING,
*         or CALL_DIRECTION_UNKNOWN
     * @param shouldAttachToExistingConnection Set to true to attach the call to an existing
*         connection, regardless of whether it's incoming or outgoing.
     * @param connectTimeMillis The connection time of the call.
     * @param clockProxy
     */
    Call(
            String callId,
            Context context,
            CallsManager callsManager,
            TelecomSystem.SyncRoot lock,
            ConnectionServiceRepository repository,
            ContactsAsyncHelper contactsAsyncHelper,
            CallerInfoAsyncQueryFactory callerInfoAsyncQueryFactory,
            PhoneNumberUtilsAdapter phoneNumberUtilsAdapter,
            Uri handle,
            GatewayInfo gatewayInfo,
            PhoneAccountHandle connectionManagerPhoneAccountHandle,
            PhoneAccountHandle targetPhoneAccountHandle,
            int callDirection,
            boolean shouldAttachToExistingConnection,
            boolean isConference,
            long connectTimeMillis,
            long connectElapsedTimeMillis,
            ClockProxy clockProxy) {
        this(callId, context, callsManager, lock, repository, contactsAsyncHelper,
                callerInfoAsyncQueryFactory, phoneNumberUtilsAdapter, handle, gatewayInfo,
                connectionManagerPhoneAccountHandle, targetPhoneAccountHandle, callDirection,
                shouldAttachToExistingConnection, isConference, clockProxy);

        mConnectTimeMillis = connectTimeMillis;
        mConnectElapsedTimeMillis = connectElapsedTimeMillis;
        mAnalytics.setCallStartTime(connectTimeMillis);
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    public void initAnalytics() {
        int analyticsDirection;
        switch (mCallDirection) {
            case CALL_DIRECTION_OUTGOING:
                analyticsDirection = Analytics.OUTGOING_DIRECTION;
                break;
            case CALL_DIRECTION_INCOMING:
                analyticsDirection = Analytics.INCOMING_DIRECTION;
                break;
            case CALL_DIRECTION_UNKNOWN:
            case CALL_DIRECTION_UNDEFINED:
            default:
                analyticsDirection = Analytics.UNKNOWN_DIRECTION;
        }
        mAnalytics = Analytics.initiateCallAnalytics(mId, analyticsDirection);
        Log.addEvent(this, LogUtils.Events.CREATED);
    }

    public Analytics.CallInfo getAnalytics() {
        return mAnalytics;
    }

    public void destroy() {
        // We should not keep these bitmaps around because the Call objects may be held for logging
        // purposes.
        // TODO: Make a container object that only stores the information we care about for Logging.
        if (mCallerInfo != null) {
            mCallerInfo.cachedPhotoIcon = null;
            mCallerInfo.cachedPhoto = null;
        }
        Log.addEvent(this, LogUtils.Events.DESTROYED);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        String component = null;
        if (mConnectionService != null && mConnectionService.getComponentName() != null) {
            component = mConnectionService.getComponentName().flattenToShortString();
        }

        return String.format(Locale.US, "[%s, %s, %s, %s, %s, childs(%d), has_parent(%b), %s, %s]",
                mId,
                CallState.toString(mState),
                component,
                Log.piiHandle(mHandle),
                getVideoStateDescription(getVideoState()),
                getChildCalls().size(),
                getParentCall() != null,
                Connection.capabilitiesToString(getConnectionCapabilities()),
                Connection.propertiesToString(getConnectionProperties()));
    }

    @Override
    public String getDescription() {
        StringBuilder s = new StringBuilder();
        if (isSelfManaged()) {
            s.append("SelfMgd Call");
        } else if (isExternalCall()) {
            s.append("External Call");
        } else {
            s.append("Call");
        }
        s.append(getId());
        s.append(" [");
        s.append(SimpleDateFormat.getDateTimeInstance().format(new Date(getCreationTimeMillis())));
        s.append("]");
        s.append(isIncoming() ? "(MT - incoming)" : "(MO - outgoing)");
        s.append("\n\tVia PhoneAccount: ");
        PhoneAccountHandle targetPhoneAccountHandle = getTargetPhoneAccount();
        if (targetPhoneAccountHandle != null) {
            s.append(targetPhoneAccountHandle);
            s.append(" (");
            s.append(getTargetPhoneAccountLabel());
            s.append(")");
        } else {
            s.append("not set");
        }

        s.append("\n\tTo address: ");
        s.append(Log.piiHandle(getHandle()));
        s.append(" Presentation: ");
        switch (getHandlePresentation()) {
            case TelecomManager.PRESENTATION_ALLOWED:
                s.append("Allowed");
                break;
            case TelecomManager.PRESENTATION_PAYPHONE:
                s.append("Payphone");
                break;
            case TelecomManager.PRESENTATION_RESTRICTED:
                s.append("Restricted");
                break;
            case TelecomManager.PRESENTATION_UNKNOWN:
                s.append("Unknown");
                break;
            default:
                s.append("<undefined>");
        }
        s.append("\n");
        return s.toString();
    }

    /**
     * Builds a debug-friendly description string for a video state.
     * <p>
     * A = audio active, T = video transmission active, R = video reception active, P = video
     * paused.
     *
     * @param videoState The video state.
     * @return A string indicating which bits are set in the video state.
     */
    private String getVideoStateDescription(int videoState) {
        StringBuilder sb = new StringBuilder();
        sb.append("A");

        if (VideoProfile.isTransmissionEnabled(videoState)) {
            sb.append("T");
        }

        if (VideoProfile.isReceptionEnabled(videoState)) {
            sb.append("R");
        }

        if (VideoProfile.isPaused(videoState)) {
            sb.append("P");
        }

        return sb.toString();
    }

    @VisibleForTesting
    public int getState() {
        return mState;
    }

    private boolean shouldContinueProcessingAfterDisconnect() {
        // Stop processing once the call is active.
        if (!CreateConnectionTimeout.isCallBeingPlaced(this)) {
            return false;
        }

        // Only Redial a Call in the case of it being an Emergency Call.
        if(!isEmergencyCall()) {
            return false;
        }

        // Make sure that there are additional connection services to process.
        if (mCreateConnectionProcessor == null
            || !mCreateConnectionProcessor.isProcessingComplete()
            || !mCreateConnectionProcessor.hasMorePhoneAccounts()) {
            return false;
        }

        if (mDisconnectCause == null) {
            return false;
        }

        // Continue processing if the current attempt failed or timed out.
        return mDisconnectCause.getCode() == DisconnectCause.ERROR ||
            mCreateConnectionProcessor.isCallTimedOut();
    }

    /**
     * Returns the unique ID for this call as it exists in Telecom.
     * @return The call ID.
     */
    public String getId() {
        return mId;
    }

    /**
     * Returns the unique ID for this call (see {@link #getId}) along with an attempt indicator that
     * iterates based on attempts to establish a {@link Connection} using createConnectionProcessor.
     * @return The call ID with an appended attempt id.
     */
    public String getConnectionId() {
        if(mCreateConnectionProcessor != null) {
            mConnectionId = mId + "_" +
                    String.valueOf(mCreateConnectionProcessor.getConnectionAttempt());
            return mConnectionId;
        } else {
            return mConnectionId;
        }
    }

    /**
     * Sets the call state. Although there exists the notion of appropriate state transitions
     * (see {@link CallState}), in practice those expectations break down when cellular systems
     * misbehave and they do this very often. The result is that we do not enforce state transitions
     * and instead keep the code resilient to unexpected state changes.
     */
    public void setState(int newState, String tag) {
        if (mState != newState) {
            Log.v(this, "setState %s -> %s", mState, newState);

            if (newState == CallState.DISCONNECTED && shouldContinueProcessingAfterDisconnect()) {
                Log.w(this, "continuing processing disconnected call with another service");
                mCreateConnectionProcessor.continueProcessingIfPossible(this, mDisconnectCause);
                return;
            }

            updateVideoHistoryViaState(mState, newState);

            mState = newState;
            maybeLoadCannedSmsResponses();

            if (mState == CallState.ACTIVE || mState == CallState.ON_HOLD) {
                if (mConnectTimeMillis == 0) {
                    // We check to see if mConnectTime is already set to prevent the
                    // call from resetting active time when it goes in and out of
                    // ACTIVE/ON_HOLD
                    mConnectTimeMillis = mClockProxy.currentTimeMillis();
                    mConnectElapsedTimeMillis = mClockProxy.elapsedRealtime();
                    mAnalytics.setCallStartTime(mConnectTimeMillis);
                }

                // We're clearly not disconnected, so reset the disconnected time.
                mDisconnectTimeMillis = 0;
                mDisconnectElapsedTimeMillis = 0;
            } else if (mState == CallState.DISCONNECTED) {
                mDisconnectTimeMillis = mClockProxy.currentTimeMillis();
                mDisconnectElapsedTimeMillis = mClockProxy.elapsedRealtime();
                mAnalytics.setCallEndTime(mDisconnectTimeMillis);
                setLocallyDisconnecting(false);
                fixParentAfterDisconnect();
            }

            // Log the state transition event
            String event = null;
            Object data = null;
            switch (newState) {
                case CallState.ACTIVE:
                    event = LogUtils.Events.SET_ACTIVE;
                    break;
                case CallState.CONNECTING:
                    event = LogUtils.Events.SET_CONNECTING;
                    break;
                case CallState.DIALING:
                    event = LogUtils.Events.SET_DIALING;
                    break;
                case CallState.PULLING:
                    event = LogUtils.Events.SET_PULLING;
                    break;
                case CallState.DISCONNECTED:
                    event = LogUtils.Events.SET_DISCONNECTED;
                    data = getDisconnectCause();
                    break;
                case CallState.DISCONNECTING:
                    event = LogUtils.Events.SET_DISCONNECTING;
                    break;
                case CallState.ON_HOLD:
                    event = LogUtils.Events.SET_HOLD;
                    break;
                case CallState.SELECT_PHONE_ACCOUNT:
                    event = LogUtils.Events.SET_SELECT_PHONE_ACCOUNT;
                    break;
                case CallState.RINGING:
                    event = LogUtils.Events.SET_RINGING;
                    break;
            }
            if (event != null) {
                // The string data should be just the tag.
                String stringData = tag;
                if (data != null) {
                    // If data exists, add it to tag.  If no tag, just use data.toString().
                    stringData = stringData == null ? data.toString() : stringData + "> " + data;
                }
                Log.addEvent(this, event, stringData);
            }
        }
    }

    void setRingbackRequested(boolean ringbackRequested) {
        mRingbackRequested = ringbackRequested;
        for (Listener l : mListeners) {
            l.onRingbackRequested(this, mRingbackRequested);
        }
    }

    boolean isRingbackRequested() {
        return mRingbackRequested;
    }

    @VisibleForTesting
    public boolean isConference() {
        return mIsConference;
    }

    public Uri getHandle() {
        return mHandle;
    }

    public String getPostDialDigits() {
        return mPostDialDigits;
    }

    public String getViaNumber() {
        return mViaNumber;
    }

    public void setViaNumber(String viaNumber) {
        // If at any point the via number is not empty throughout the call, save that via number.
        if (!TextUtils.isEmpty(viaNumber)) {
            mViaNumber = viaNumber;
        }
    }

    int getHandlePresentation() {
        return mHandlePresentation;
    }


    void setHandle(Uri handle) {
        setHandle(handle, TelecomManager.PRESENTATION_ALLOWED);
    }

    public void setHandle(Uri handle, int presentation) {
        if (!Objects.equals(handle, mHandle) || presentation != mHandlePresentation) {
            mHandlePresentation = presentation;
            if (mHandlePresentation == TelecomManager.PRESENTATION_RESTRICTED ||
                    mHandlePresentation == TelecomManager.PRESENTATION_UNKNOWN) {
                mHandle = null;
            } else {
                mHandle = handle;
                if (mHandle != null && !PhoneAccount.SCHEME_VOICEMAIL.equals(mHandle.getScheme())
                        && TextUtils.isEmpty(mHandle.getSchemeSpecificPart())) {
                    // If the number is actually empty, set it to null, unless this is a
                    // SCHEME_VOICEMAIL uri which always has an empty number.
                    mHandle = null;
                }
            }

            // Let's not allow resetting of the emergency flag. Once a call becomes an emergency
            // call, it will remain so for the rest of it's lifetime.
            if (!mIsEmergencyCall) {
                mIsEmergencyCall = mHandle != null &&
                        mPhoneNumberUtilsAdapter.isLocalEmergencyNumber(mContext,
                                mHandle.getSchemeSpecificPart());
            }
            startCallerInfoLookup();
            for (Listener l : mListeners) {
                l.onHandleChanged(this);
            }
        }
    }

    public String getCallerDisplayName() {
        return mCallerDisplayName;
    }

    public int getCallerDisplayNamePresentation() {
        return mCallerDisplayNamePresentation;
    }

    void setCallerDisplayName(String callerDisplayName, int presentation) {
        if (!TextUtils.equals(callerDisplayName, mCallerDisplayName) ||
                presentation != mCallerDisplayNamePresentation) {
            mCallerDisplayName = callerDisplayName;
            mCallerDisplayNamePresentation = presentation;
            for (Listener l : mListeners) {
                l.onCallerDisplayNameChanged(this);
            }
        }
    }

    public String getName() {
        return mCallerInfo == null ? null : mCallerInfo.name;
    }

    public String getPhoneNumber() {
        return mCallerInfo == null ? null : mCallerInfo.phoneNumber;
    }

    public Bitmap getPhotoIcon() {
        return mCallerInfo == null ? null : mCallerInfo.cachedPhotoIcon;
    }

    public Drawable getPhoto() {
        return mCallerInfo == null ? null : mCallerInfo.cachedPhoto;
    }

    /**
     * @param disconnectCause The reason for the disconnection, represented by
     *         {@link android.telecom.DisconnectCause}.
     */
    public void setDisconnectCause(DisconnectCause disconnectCause) {
        // TODO: Consider combining this method with a setDisconnected() method that is totally
        // separate from setState.
        mAnalytics.setCallDisconnectCause(disconnectCause);
        mDisconnectCause = disconnectCause;
    }

    public DisconnectCause getDisconnectCause() {
        return mDisconnectCause;
    }

    @VisibleForTesting
    public boolean isEmergencyCall() {
        return mIsEmergencyCall;
    }

    /**
     * @return The original handle this call is associated with. In-call services should use this
     * handle when indicating in their UI the handle that is being called.
     */
    public Uri getOriginalHandle() {
        if (mGatewayInfo != null && !mGatewayInfo.isEmpty()) {
            return mGatewayInfo.getOriginalAddress();
        }
        return getHandle();
    }

    @VisibleForTesting
    public GatewayInfo getGatewayInfo() {
        return mGatewayInfo;
    }

    void setGatewayInfo(GatewayInfo gatewayInfo) {
        mGatewayInfo = gatewayInfo;
    }

    @VisibleForTesting
    public PhoneAccountHandle getConnectionManagerPhoneAccount() {
        return mConnectionManagerPhoneAccountHandle;
    }

    @VisibleForTesting
    public void setConnectionManagerPhoneAccount(PhoneAccountHandle accountHandle) {
        if (!Objects.equals(mConnectionManagerPhoneAccountHandle, accountHandle)) {
            mConnectionManagerPhoneAccountHandle = accountHandle;
            for (Listener l : mListeners) {
                l.onConnectionManagerPhoneAccountChanged(this);
            }
        }

    }

    @VisibleForTesting
    public PhoneAccountHandle getTargetPhoneAccount() {
        return mTargetPhoneAccountHandle;
    }

    @VisibleForTesting
    public void setTargetPhoneAccount(PhoneAccountHandle accountHandle) {
        if (!Objects.equals(mTargetPhoneAccountHandle, accountHandle)) {
            mTargetPhoneAccountHandle = accountHandle;
            for (Listener l : mListeners) {
                l.onTargetPhoneAccountChanged(this);
            }
            configureIsWorkCall();
        }
        checkIfVideoCapable();
    }

    public CharSequence getTargetPhoneAccountLabel() {
        if (getTargetPhoneAccount() == null) {
            return null;
        }
        PhoneAccount phoneAccount = mCallsManager.getPhoneAccountRegistrar()
                .getPhoneAccountUnchecked(getTargetPhoneAccount());

        if (phoneAccount == null) {
            return null;
        }

        return phoneAccount.getLabel();
    }

    /**
     * Determines if this Call should be written to the call log.
     * @return {@code true} for managed calls or for self-managed calls which have the
     * {@link PhoneAccount#EXTRA_LOG_SELF_MANAGED_CALLS} extra set.
     */
    public boolean isLoggedSelfManaged() {
        if (!isSelfManaged()) {
            // Managed calls are always logged.
            return true;
        }
        if (getTargetPhoneAccount() == null) {
            return false;
        }
        PhoneAccount phoneAccount = mCallsManager.getPhoneAccountRegistrar()
                .getPhoneAccountUnchecked(getTargetPhoneAccount());

        if (phoneAccount == null) {
            return false;
        }

        return phoneAccount.getExtras() != null && phoneAccount.getExtras().getBoolean(
                PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false);
    }

    @VisibleForTesting
    public boolean isIncoming() {
        return mCallDirection == CALL_DIRECTION_INCOMING;
    }

    public boolean isExternalCall() {
        return (getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL) ==
                Connection.PROPERTY_IS_EXTERNAL_CALL;
    }

    public boolean isWorkCall() {
        return mIsWorkCall;
    }

    public boolean isVideoCallingSupported() {
        return mIsVideoCallingSupported;
    }

    public boolean isSelfManaged() {
        return mIsSelfManaged;
    }

    public void setIsSelfManaged(boolean isSelfManaged) {
        mIsSelfManaged = isSelfManaged;

        // Connection properties will add/remove the PROPERTY_SELF_MANAGED.
        setConnectionProperties(getConnectionProperties());
    }

    public void markFinishedHandoverStateAndCleanup(int handoverState) {
        if (mHandoverSourceCall != null) {
            mHandoverSourceCall.setHandoverState(handoverState);
        } else if (mHandoverDestinationCall != null) {
            mHandoverDestinationCall.setHandoverState(handoverState);
        }
        setHandoverState(handoverState);
        maybeCleanupHandover();
    }

    public void maybeCleanupHandover() {
        if (mHandoverSourceCall != null) {
            mHandoverSourceCall.setHandoverSourceCall(null);
            mHandoverSourceCall.setHandoverDestinationCall(null);
            mHandoverSourceCall = null;
        } else if (mHandoverDestinationCall != null) {
            mHandoverDestinationCall.setHandoverSourceCall(null);
            mHandoverDestinationCall.setHandoverDestinationCall(null);
            mHandoverDestinationCall = null;
        }
    }

    public boolean isHandoverInProgress() {
        return mHandoverSourceCall != null || mHandoverDestinationCall != null;
    }

    public Call getHandoverDestinationCall() {
        return mHandoverDestinationCall;
    }

    public void setHandoverDestinationCall(Call call) {
        mHandoverDestinationCall = call;
    }

    public Call getHandoverSourceCall() {
        return mHandoverSourceCall;
    }

    public void setHandoverSourceCall(Call call) {
        mHandoverSourceCall = call;
    }

    public void setHandoverState(int handoverState) {
        Log.d(this, "setHandoverState: callId=%s, handoverState=%s", getId(),
                HandoverState.stateToString(handoverState));
        mHandoverState = handoverState;
    }

    public int getHandoverState() {
        return mHandoverState;
    }

    private void configureIsWorkCall() {
        PhoneAccountRegistrar phoneAccountRegistrar = mCallsManager.getPhoneAccountRegistrar();
        boolean isWorkCall = false;
        PhoneAccount phoneAccount =
                phoneAccountRegistrar.getPhoneAccountUnchecked(mTargetPhoneAccountHandle);
        if (phoneAccount != null) {
            final UserHandle userHandle;
            if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_MULTI_USER)) {
                userHandle = mInitiatingUser;
            } else {
                userHandle = mTargetPhoneAccountHandle.getUserHandle();
            }
            if (userHandle != null) {
                isWorkCall = UserUtil.isManagedProfile(mContext, userHandle);
            }
        }
        mIsWorkCall = isWorkCall;
    }

    /**
     * Caches the state of the {@link PhoneAccount#CAPABILITY_VIDEO_CALLING} {@link PhoneAccount}
     * capability and ensures that the video state is updated if the phone account does not support
     * video calling.
     */
    private void checkIfVideoCapable() {
        PhoneAccountRegistrar phoneAccountRegistrar = mCallsManager.getPhoneAccountRegistrar();
        if (mTargetPhoneAccountHandle == null) {
            // If no target phone account handle is specified, assume we can potentially perform a
            // video call; once the phone account is set, we can confirm that it is video capable.
            mIsVideoCallingSupported = true;
            Log.d(this, "checkIfVideoCapable: no phone account selected; assume video capable.");
            return;
        }
        PhoneAccount phoneAccount =
                phoneAccountRegistrar.getPhoneAccountUnchecked(mTargetPhoneAccountHandle);
        mIsVideoCallingSupported = phoneAccount != null && phoneAccount.hasCapabilities(
                    PhoneAccount.CAPABILITY_VIDEO_CALLING);

        if (!mIsVideoCallingSupported && VideoProfile.isVideo(getVideoState())) {
            // The PhoneAccount for the Call was set to one which does not support video calling,
            // and the current call is configured to be a video call; downgrade to audio-only.
            setVideoState(VideoProfile.STATE_AUDIO_ONLY);
            Log.d(this, "checkIfVideoCapable: selected phone account doesn't support video.");
        }
    }

    boolean shouldAttachToExistingConnection() {
        return mShouldAttachToExistingConnection;
    }

    /**
     * Note: This method relies on {@link #mConnectElapsedTimeMillis} and
     * {@link #mDisconnectElapsedTimeMillis} which are independent of the wall clock (which could
     * change due to clock changes).
     * @return The "age" of this call object in milliseconds, which typically also represents the
     *     period since this call was added to the set pending outgoing calls.
     */
    @VisibleForTesting
    public long getAgeMillis() {
        if (mState == CallState.DISCONNECTED &&
                (mDisconnectCause.getCode() == DisconnectCause.REJECTED ||
                 mDisconnectCause.getCode() == DisconnectCause.MISSED)) {
            // Rejected and missed calls have no age. They're immortal!!
            return 0;
        } else if (mConnectElapsedTimeMillis == 0) {
            // Age is measured in the amount of time the call was active. A zero connect time
            // indicates that we never went active, so return 0 for the age.
            return 0;
        } else if (mDisconnectElapsedTimeMillis == 0) {
            // We connected, but have not yet disconnected
            return mClockProxy.elapsedRealtime() - mConnectElapsedTimeMillis;
        }

        return mDisconnectElapsedTimeMillis - mConnectElapsedTimeMillis;
    }

    /**
     * @return The time when this call object was created and added to the set of pending outgoing
     *     calls.
     */
    public long getCreationTimeMillis() {
        return mCreationTimeMillis;
    }

    public void setCreationTimeMillis(long time) {
        mCreationTimeMillis = time;
    }

    long getConnectTimeMillis() {
        return mConnectTimeMillis;
    }

    int getConnectionCapabilities() {
        return mConnectionCapabilities;
    }

    int getConnectionProperties() {
        return mConnectionProperties;
    }

    void setConnectionCapabilities(int connectionCapabilities) {
        setConnectionCapabilities(connectionCapabilities, false /* forceUpdate */);
    }

    void setConnectionCapabilities(int connectionCapabilities, boolean forceUpdate) {
        Log.v(this, "setConnectionCapabilities: %s", Connection.capabilitiesToString(
                connectionCapabilities));
        if (forceUpdate || mConnectionCapabilities != connectionCapabilities) {
            // If the phone account does not support video calling, and the connection capabilities
            // passed in indicate that the call supports video, remove those video capabilities.
            if (!isVideoCallingSupported() && doesCallSupportVideo(connectionCapabilities)) {
                Log.w(this, "setConnectionCapabilities: attempt to set connection as video " +
                        "capable when not supported by the phone account.");
                connectionCapabilities = removeVideoCapabilities(connectionCapabilities);
            }

            int previousCapabilities = mConnectionCapabilities;
            mConnectionCapabilities = connectionCapabilities;
            for (Listener l : mListeners) {
                l.onConnectionCapabilitiesChanged(this);
            }

            int xorCaps = previousCapabilities ^ mConnectionCapabilities;
            Log.addEvent(this, LogUtils.Events.CAPABILITY_CHANGE,
                    "Current: [%s], Removed [%s], Added [%s]",
                    Connection.capabilitiesToStringShort(mConnectionCapabilities),
                    Connection.capabilitiesToStringShort(previousCapabilities & xorCaps),
                    Connection.capabilitiesToStringShort(mConnectionCapabilities & xorCaps));
        }
    }

    void setConnectionProperties(int connectionProperties) {
        Log.v(this, "setConnectionProperties: %s", Connection.propertiesToString(
                connectionProperties));

        // Ensure the ConnectionService can't change the state of the self-managed property.
        if (isSelfManaged()) {
            connectionProperties |= Connection.PROPERTY_SELF_MANAGED;
        } else {
            connectionProperties &= ~Connection.PROPERTY_SELF_MANAGED;
        }

        int changedProperties = mConnectionProperties ^ connectionProperties;

        if (changedProperties != 0) {
            int previousProperties = mConnectionProperties;
            mConnectionProperties = connectionProperties;
            setRttStreams((mConnectionProperties & Connection.PROPERTY_IS_RTT) ==
                    Connection.PROPERTY_IS_RTT);
            boolean didRttChange =
                    (changedProperties & Connection.PROPERTY_IS_RTT) == Connection.PROPERTY_IS_RTT;
            for (Listener l : mListeners) {
                l.onConnectionPropertiesChanged(this, didRttChange);
            }

            boolean wasExternal = (previousProperties & Connection.PROPERTY_IS_EXTERNAL_CALL)
                    == Connection.PROPERTY_IS_EXTERNAL_CALL;
            boolean isExternal = (connectionProperties & Connection.PROPERTY_IS_EXTERNAL_CALL)
                    == Connection.PROPERTY_IS_EXTERNAL_CALL;
            if (wasExternal != isExternal) {
                Log.v(this, "setConnectionProperties: external call changed isExternal = %b",
                        isExternal);
                Log.addEvent(this, LogUtils.Events.IS_EXTERNAL, isExternal);
                for (Listener l : mListeners) {
                    l.onExternalCallChanged(this, isExternal);
                }
            }

            mAnalytics.addCallProperties(mConnectionProperties);

            int xorProps = previousProperties ^ mConnectionProperties;
            Log.addEvent(this, LogUtils.Events.PROPERTY_CHANGE,
                    "Current: [%s], Removed [%s], Added [%s]",
                    Connection.propertiesToStringShort(mConnectionProperties),
                    Connection.propertiesToStringShort(previousProperties & xorProps),
                    Connection.propertiesToStringShort(mConnectionProperties & xorProps));
        }
    }

    public int getSupportedAudioRoutes() {
        return mSupportedAudioRoutes;
    }

    void setSupportedAudioRoutes(int audioRoutes) {
        if (mSupportedAudioRoutes != audioRoutes) {
            mSupportedAudioRoutes = audioRoutes;
        }
    }

    @VisibleForTesting
    public Call getParentCall() {
        return mParentCall;
    }

    @VisibleForTesting
    public List<Call> getChildCalls() {
        return mChildCalls;
    }

    @VisibleForTesting
    public boolean wasConferencePreviouslyMerged() {
        return mWasConferencePreviouslyMerged;
    }

    public boolean isDisconnectingChildCall() {
        return mIsDisconnectingChildCall;
    }

    /**
     * Sets whether this call is a child call.
     */
    private void maybeSetCallAsDisconnectingChild() {
        if (mParentCall != null) {
            mIsDisconnectingChildCall = true;
        }
    }

    @VisibleForTesting
    public Call getConferenceLevelActiveCall() {
        return mConferenceLevelActiveCall;
    }

    @VisibleForTesting
    public ConnectionServiceWrapper getConnectionService() {
        return mConnectionService;
    }

    /**
     * Retrieves the {@link Context} for the call.
     *
     * @return The {@link Context}.
     */
    public Context getContext() {
        return mContext;
    }

    @VisibleForTesting
    public void setConnectionService(ConnectionServiceWrapper service) {
        Preconditions.checkNotNull(service);

        clearConnectionService();

        service.incrementAssociatedCallCount();
        mConnectionService = service;
        mAnalytics.setCallConnectionService(service.getComponentName().flattenToShortString());
        mConnectionService.addCall(this);
    }

    /**
     * Perform an in-place replacement of the {@link ConnectionServiceWrapper} for this Call.
     * Removes the call from its former {@link ConnectionServiceWrapper}, ensuring that the
     * ConnectionService is NOT unbound if the call count hits zero.
     * This is used by the {@link ConnectionServiceWrapper} when handling {@link Connection} and
     * {@link Conference} additions via a ConnectionManager.
     * The original {@link android.telecom.ConnectionService} will directly add external calls and
     * conferences to Telecom as well as the ConnectionManager, which will add to Telecom.  In these
     * cases since its first added to via the original CS, we want to change the CS responsible for
     * the call to the ConnectionManager rather than adding it again as another call/conference.
     *
     * @param service The new {@link ConnectionServiceWrapper}.
     */
    public void replaceConnectionService(ConnectionServiceWrapper service) {
        Preconditions.checkNotNull(service);

        if (mConnectionService != null) {
            ConnectionServiceWrapper serviceTemp = mConnectionService;
            mConnectionService = null;
            serviceTemp.removeCall(this);
            serviceTemp.decrementAssociatedCallCount(true /*isSuppressingUnbind*/);
        }

        service.incrementAssociatedCallCount();
        mConnectionService = service;
        mAnalytics.setCallConnectionService(service.getComponentName().flattenToShortString());
    }

    /**
     * Clears the associated connection service.
     */
    void clearConnectionService() {
        if (mConnectionService != null) {
            ConnectionServiceWrapper serviceTemp = mConnectionService;
            mConnectionService = null;
            serviceTemp.removeCall(this);

            // Decrementing the count can cause the service to unbind, which itself can trigger the
            // service-death code.  Since the service death code tries to clean up any associated
            // calls, we need to make sure to remove that information (e.g., removeCall()) before
            // we decrement. Technically, invoking removeCall() prior to decrementing is all that is
            // necessary, but cleaning up mConnectionService prior to triggering an unbind is good
            // to do.
            decrementAssociatedCallCount(serviceTemp);
        }
    }

    /**
     * Starts the create connection sequence. Upon completion, there should exist an active
     * connection through a connection service (or the call will have failed).
     *
     * @param phoneAccountRegistrar The phone account registrar.
     */
    void startCreateConnection(PhoneAccountRegistrar phoneAccountRegistrar) {
        if (mCreateConnectionProcessor != null) {
            Log.w(this, "mCreateConnectionProcessor in startCreateConnection is not null. This is" +
                    " due to a race between NewOutgoingCallIntentBroadcaster and " +
                    "phoneAccountSelected, but is harmlessly resolved by ignoring the second " +
                    "invocation.");
            return;
        }
        mCreateConnectionProcessor = new CreateConnectionProcessor(this, mRepository, this,
                phoneAccountRegistrar, mContext);
        mCreateConnectionProcessor.process();
    }

    @Override
    public void handleCreateConnectionSuccess(
            CallIdMapper idMapper,
            ParcelableConnection connection) {
        Log.v(this, "handleCreateConnectionSuccessful %s", connection);
        setTargetPhoneAccount(connection.getPhoneAccount());
        setHandle(connection.getHandle(), connection.getHandlePresentation());
        setCallerDisplayName(
                connection.getCallerDisplayName(), connection.getCallerDisplayNamePresentation());

        setConnectionCapabilities(connection.getConnectionCapabilities());
        setConnectionProperties(connection.getConnectionProperties());
        setSupportedAudioRoutes(connection.getSupportedAudioRoutes());
        setVideoProvider(connection.getVideoProvider());
        setVideoState(connection.getVideoState());
        setRingbackRequested(connection.isRingbackRequested());
        setStatusHints(connection.getStatusHints());
        putExtras(SOURCE_CONNECTION_SERVICE, connection.getExtras());

        mConferenceableCalls.clear();
        for (String id : connection.getConferenceableConnectionIds()) {
            mConferenceableCalls.add(idMapper.getCall(id));
        }

        switch (mCallDirection) {
            case CALL_DIRECTION_INCOMING:
                // Listeners (just CallsManager for now) will be responsible for checking whether
                // the call should be blocked.
                for (Listener l : mListeners) {
                    l.onSuccessfulIncomingCall(this);
                }
                break;
            case CALL_DIRECTION_OUTGOING:
                for (Listener l : mListeners) {
                    l.onSuccessfulOutgoingCall(this,
                            getStateFromConnectionState(connection.getState()));
                }
                break;
            case CALL_DIRECTION_UNKNOWN:
                for (Listener l : mListeners) {
                    l.onSuccessfulUnknownCall(this, getStateFromConnectionState(connection
                            .getState()));
                }
                break;
        }
    }

    @Override
    public void handleCreateConnectionFailure(DisconnectCause disconnectCause) {
        clearConnectionService();
        setDisconnectCause(disconnectCause);
        mCallsManager.markCallAsDisconnected(this, disconnectCause);

        switch (mCallDirection) {
            case CALL_DIRECTION_INCOMING:
                for (Listener listener : mListeners) {
                    listener.onFailedIncomingCall(this);
                }
                break;
            case CALL_DIRECTION_OUTGOING:
                for (Listener listener : mListeners) {
                    listener.onFailedOutgoingCall(this, disconnectCause);
                }
                break;
            case CALL_DIRECTION_UNKNOWN:
                for (Listener listener : mListeners) {
                    listener.onFailedUnknownCall(this);
                }
                break;
        }
    }

    /**
     * Plays the specified DTMF tone.
     */
    void playDtmfTone(char digit) {
        if (mConnectionService == null) {
            Log.w(this, "playDtmfTone() request on a call without a connection service.");
        } else {
            Log.i(this, "Send playDtmfTone to connection service for call %s", this);
            mConnectionService.playDtmfTone(this, digit);
            Log.addEvent(this, LogUtils.Events.START_DTMF, Log.pii(digit));
        }
    }

    /**
     * Stops playing any currently playing DTMF tone.
     */
    void stopDtmfTone() {
        if (mConnectionService == null) {
            Log.w(this, "stopDtmfTone() request on a call without a connection service.");
        } else {
            Log.i(this, "Send stopDtmfTone to connection service for call %s", this);
            Log.addEvent(this, LogUtils.Events.STOP_DTMF);
            mConnectionService.stopDtmfTone(this);
        }
    }

    /**
     * Silences the ringer.
     */
    void silence() {
        if (mConnectionService == null) {
            Log.w(this, "silence() request on a call without a connection service.");
        } else {
            Log.i(this, "Send silence to connection service for call %s", this);
            Log.addEvent(this, LogUtils.Events.SILENCE);
            mConnectionService.silence(this);
        }
    }

    @VisibleForTesting
    public void disconnect() {
        disconnect(0);
    }

    /**
     * Attempts to disconnect the call through the connection service.
     */
    @VisibleForTesting
    public void disconnect(long disconnectionTimeout) {
        Log.addEvent(this, LogUtils.Events.REQUEST_DISCONNECT);

        // Track that the call is now locally disconnecting.
        setLocallyDisconnecting(true);
        maybeSetCallAsDisconnectingChild();

        if (mState == CallState.NEW || mState == CallState.SELECT_PHONE_ACCOUNT ||
                mState == CallState.CONNECTING) {
            Log.v(this, "Aborting call %s", this);
            abort(disconnectionTimeout);
        } else if (mState != CallState.ABORTED && mState != CallState.DISCONNECTED) {
            if (mConnectionService == null) {
                Log.e(this, new Exception(), "disconnect() request on a call without a"
                        + " connection service.");
            } else {
                Log.i(this, "Send disconnect to connection service for call: %s", this);
                // The call isn't officially disconnected until the connection service
                // confirms that the call was actually disconnected. Only then is the
                // association between call and connection service severed, see
                // {@link CallsManager#markCallAsDisconnected}.
                mConnectionService.disconnect(this);
            }
        }
    }

    void abort(long disconnectionTimeout) {
        if (mCreateConnectionProcessor != null &&
                !mCreateConnectionProcessor.isProcessingComplete()) {
            mCreateConnectionProcessor.abort();
        } else if (mState == CallState.NEW || mState == CallState.SELECT_PHONE_ACCOUNT
                || mState == CallState.CONNECTING) {
            if (disconnectionTimeout > 0) {
                // If the cancelation was from NEW_OUTGOING_CALL with a timeout of > 0
                // milliseconds, do not destroy the call.
                // Instead, we announce the cancellation and CallsManager handles
                // it through a timer. Since apps often cancel calls through NEW_OUTGOING_CALL and
                // then re-dial them quickly using a gateway, allowing the first call to end
                // causes jank. This timeout allows CallsManager to transition the first call into
                // the second call so that in-call only ever sees a single call...eliminating the
                // jank altogether. The app will also be able to set the timeout via an extra on
                // the ordered broadcast.
                for (Listener listener : mListeners) {
                    if (listener.onCanceledViaNewOutgoingCallBroadcast(
                            this, disconnectionTimeout)) {
                        // The first listener to handle this wins. A return value of true means that
                        // the listener will handle the disconnection process later and so we
                        // should not continue it here.
                        setLocallyDisconnecting(false);
                        return;
                    }
                }
            }

            handleCreateConnectionFailure(new DisconnectCause(DisconnectCause.CANCELED));
        } else {
            Log.v(this, "Cannot abort a call which is neither SELECT_PHONE_ACCOUNT or CONNECTING");
        }
    }

    /**
     * Answers the call if it is ringing.
     *
     * @param videoState The video state in which to answer the call.
     */
    @VisibleForTesting
    public void answer(int videoState) {
        // Check to verify that the call is still in the ringing state. A call can change states
        // between the time the user hits 'answer' and Telecom receives the command.
        if (isRinging("answer")) {
            if (!isVideoCallingSupported() && VideoProfile.isVideo(videoState)) {
                // Video calling is not supported, yet the InCallService is attempting to answer as
                // video.  We will simply answer as audio-only.
                videoState = VideoProfile.STATE_AUDIO_ONLY;
            }
            // At this point, we are asking the connection service to answer but we don't assume
            // that it will work. Instead, we wait until confirmation from the connectino service
            // that the call is in a non-STATE_RINGING state before changing the UI. See
            // {@link ConnectionServiceAdapter#setActive} and other set* methods.
            if (mConnectionService != null) {
                mConnectionService.answer(this, videoState);
            } else {
                Log.e(this, new NullPointerException(),
                        "answer call failed due to null CS callId=%s", getId());
            }
            Log.addEvent(this, LogUtils.Events.REQUEST_ACCEPT);
        }
    }

    /**
     * Rejects the call if it is ringing.
     *
     * @param rejectWithMessage Whether to send a text message as part of the call rejection.
     * @param textMessage An optional text message to send as part of the rejection.
     */
    @VisibleForTesting
    public void reject(boolean rejectWithMessage, String textMessage) {
        // Check to verify that the call is still in the ringing state. A call can change states
        // between the time the user hits 'reject' and Telecomm receives the command.
        if (isRinging("reject")) {
            // Ensure video state history tracks video state at time of rejection.
            mVideoStateHistory |= mVideoState;

            if (mConnectionService != null) {
                mConnectionService.reject(this, rejectWithMessage, textMessage);
            } else {
                Log.e(this, new NullPointerException(),
                        "reject call failed due to null CS callId=%s", getId());
            }
            Log.addEvent(this, LogUtils.Events.REQUEST_REJECT);

        }
    }

    /**
     * Puts the call on hold if it is currently active.
     */
    void hold() {
        if (mState == CallState.ACTIVE) {
            if (mConnectionService != null) {
                mConnectionService.hold(this);
            } else {
                Log.e(this, new NullPointerException(),
                        "hold call failed due to null CS callId=%s", getId());
            }
            Log.addEvent(this, LogUtils.Events.REQUEST_HOLD);
        }
    }

    /**
     * Releases the call from hold if it is currently active.
     */
    void unhold() {
        if (mState == CallState.ON_HOLD) {
            if (mConnectionService != null) {
                mConnectionService.unhold(this);
            } else {
                Log.e(this, new NullPointerException(),
                        "unhold call failed due to null CS callId=%s", getId());
            }
            Log.addEvent(this, LogUtils.Events.REQUEST_UNHOLD);
        }
    }

    /** Checks if this is a live call or not. */
    @VisibleForTesting
    public boolean isAlive() {
        switch (mState) {
            case CallState.NEW:
            case CallState.RINGING:
            case CallState.DISCONNECTED:
            case CallState.ABORTED:
                return false;
            default:
                return true;
        }
    }

    boolean isActive() {
        return mState == CallState.ACTIVE;
    }

    Bundle getExtras() {
        return mExtras;
    }

    /**
     * Adds extras to the extras bundle associated with this {@link Call}.
     *
     * Note: this method needs to know the source of the extras change (see
     * {@link #SOURCE_CONNECTION_SERVICE}, {@link #SOURCE_INCALL_SERVICE}).  Extras changes which
     * originate from a connection service will only be notified to incall services.  Likewise,
     * changes originating from the incall services will only notify the connection service of the
     * change.
     *
     * @param source The source of the extras addition.
     * @param extras The extras.
     */
    void putExtras(int source, Bundle extras) {
        if (extras == null) {
            return;
        }
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putAll(extras);

        for (Listener l : mListeners) {
            l.onExtrasChanged(this, source, extras);
        }

        // If the change originated from an InCallService, notify the connection service.
        if (source == SOURCE_INCALL_SERVICE) {
            if (mConnectionService != null) {
                mConnectionService.onExtrasChanged(this, mExtras);
            } else {
                Log.e(this, new NullPointerException(),
                        "putExtras failed due to null CS callId=%s", getId());
            }
        }
    }

    /**
     * Removes extras from the extras bundle associated with this {@link Call}.
     *
     * Note: this method needs to know the source of the extras change (see
     * {@link #SOURCE_CONNECTION_SERVICE}, {@link #SOURCE_INCALL_SERVICE}).  Extras changes which
     * originate from a connection service will only be notified to incall services.  Likewise,
     * changes originating from the incall services will only notify the connection service of the
     * change.
     *
     * @param source The source of the extras removal.
     * @param keys The extra keys to remove.
     */
    void removeExtras(int source, List<String> keys) {
        if (mExtras == null) {
            return;
        }
        for (String key : keys) {
            mExtras.remove(key);
        }

        for (Listener l : mListeners) {
            l.onExtrasRemoved(this, source, keys);
        }

        // If the change originated from an InCallService, notify the connection service.
        if (source == SOURCE_INCALL_SERVICE) {
            if (mConnectionService != null) {
                mConnectionService.onExtrasChanged(this, mExtras);
            } else {
                Log.e(this, new NullPointerException(),
                        "removeExtras failed due to null CS callId=%s", getId());
            }
        }
    }

    @VisibleForTesting
    public Bundle getIntentExtras() {
        return mIntentExtras;
    }

    void setIntentExtras(Bundle extras) {
        mIntentExtras = extras;
    }

    public Intent getOriginalCallIntent() {
        return mOriginalCallIntent;
    }

    public void setOriginalCallIntent(Intent intent) {
        mOriginalCallIntent = intent;
    }

    /**
     * @return the uri of the contact associated with this call.
     */
    @VisibleForTesting
    public Uri getContactUri() {
        if (mCallerInfo == null || !mCallerInfo.contactExists) {
            return getHandle();
        }
        return Contacts.getLookupUri(mCallerInfo.contactIdOrZero, mCallerInfo.lookupKey);
    }

    Uri getRingtone() {
        return mCallerInfo == null ? null : mCallerInfo.contactRingtoneUri;
    }

    void onPostDialWait(String remaining) {
        for (Listener l : mListeners) {
            l.onPostDialWait(this, remaining);
        }
    }

    void onPostDialChar(char nextChar) {
        for (Listener l : mListeners) {
            l.onPostDialChar(this, nextChar);
        }
    }

    void postDialContinue(boolean proceed) {
        if (mConnectionService != null) {
            mConnectionService.onPostDialContinue(this, proceed);
        } else {
            Log.e(this, new NullPointerException(),
                    "postDialContinue failed due to null CS callId=%s", getId());
        }
    }

    void conferenceWith(Call otherCall) {
        if (mConnectionService == null) {
            Log.w(this, "conference requested on a call without a connection service.");
        } else {
            Log.addEvent(this, LogUtils.Events.CONFERENCE_WITH, otherCall);
            mConnectionService.conference(this, otherCall);
        }
    }

    void splitFromConference() {
        if (mConnectionService == null) {
            Log.w(this, "splitting from conference call without a connection service");
        } else {
            Log.addEvent(this, LogUtils.Events.SPLIT_FROM_CONFERENCE);
            mConnectionService.splitFromConference(this);
        }
    }

    @VisibleForTesting
    public void mergeConference() {
        if (mConnectionService == null) {
            Log.w(this, "merging conference calls without a connection service.");
        } else if (can(Connection.CAPABILITY_MERGE_CONFERENCE)) {
            Log.addEvent(this, LogUtils.Events.CONFERENCE_WITH);
            mConnectionService.mergeConference(this);
            mWasConferencePreviouslyMerged = true;
        }
    }

    @VisibleForTesting
    public void swapConference() {
        if (mConnectionService == null) {
            Log.w(this, "swapping conference calls without a connection service.");
        } else if (can(Connection.CAPABILITY_SWAP_CONFERENCE)) {
            Log.addEvent(this, LogUtils.Events.SWAP);
            mConnectionService.swapConference(this);
            switch (mChildCalls.size()) {
                case 1:
                    mConferenceLevelActiveCall = mChildCalls.get(0);
                    break;
                case 2:
                    // swap
                    mConferenceLevelActiveCall = mChildCalls.get(0) == mConferenceLevelActiveCall ?
                            mChildCalls.get(1) : mChildCalls.get(0);
                    break;
                default:
                    // For anything else 0, or 3+, set it to null since it is impossible to tell.
                    mConferenceLevelActiveCall = null;
                    break;
            }
        }
    }

    /**
     * Initiates a request to the connection service to pull this call.
     * <p>
     * This method can only be used for calls that have the
     * {@link android.telecom.Connection#CAPABILITY_CAN_PULL_CALL} capability and
     * {@link android.telecom.Connection#PROPERTY_IS_EXTERNAL_CALL} property set.
     * <p>
     * An external call is a representation of a call which is taking place on another device
     * associated with a PhoneAccount on this device.  Issuing a request to pull the external call
     * tells the {@link android.telecom.ConnectionService} that it should move the call from the
     * other device to this one.  An example of this is the IMS multi-endpoint functionality.  A
     * user may have two phones with the same phone number.  If the user is engaged in an active
     * call on their first device, the network will inform the second device of that ongoing call in
     * the form of an external call.  The user may wish to continue their conversation on the second
     * device, so will issue a request to pull the call to the second device.
     * <p>
     * Requests to pull a call which is not external, or a call which is not pullable are ignored.
     */
    public void pullExternalCall() {
        if (mConnectionService == null) {
            Log.w(this, "pulling a call without a connection service.");
        }

        if (!hasProperty(Connection.PROPERTY_IS_EXTERNAL_CALL)) {
            Log.w(this, "pullExternalCall - call %s is not an external call.", mId);
            return;
        }

        if (!can(Connection.CAPABILITY_CAN_PULL_CALL)) {
            Log.w(this, "pullExternalCall - call %s is external but cannot be pulled.", mId);
            return;
        }
        Log.addEvent(this, LogUtils.Events.REQUEST_PULL);
        mConnectionService.pullExternalCall(this);
    }

    /**
     * Sends a call event to the {@link ConnectionService} for this call.
     *
     * See {@link Call#sendCallEvent(String, Bundle)}.
     *
     * @param event The call event.
     * @param extras Associated extras.
     */
    public void sendCallEvent(String event, Bundle extras) {
        if (mConnectionService != null) {
            if (android.telecom.Call.EVENT_REQUEST_HANDOVER.equals(event)) {
                // Handover requests are targeted at Telecom, not the ConnectionService.
                if (extras == null) {
                    Log.w(this, "sendCallEvent: %s event received with null extras.",
                            android.telecom.Call.EVENT_REQUEST_HANDOVER);
                    mConnectionService.sendCallEvent(this,
                            android.telecom.Call.EVENT_HANDOVER_FAILED, null);
                    return;
                }
                Parcelable parcelable = extras.getParcelable(
                        android.telecom.Call.EXTRA_HANDOVER_PHONE_ACCOUNT_HANDLE);
                if (!(parcelable instanceof PhoneAccountHandle) || parcelable == null) {
                    Log.w(this, "sendCallEvent: %s event received with invalid handover acct.",
                            android.telecom.Call.EVENT_REQUEST_HANDOVER);
                    mConnectionService.sendCallEvent(this,
                            android.telecom.Call.EVENT_HANDOVER_FAILED, null);
                    return;
                }
                PhoneAccountHandle phoneAccountHandle = (PhoneAccountHandle) parcelable;
                int videoState = extras.getInt(android.telecom.Call.EXTRA_HANDOVER_VIDEO_STATE,
                        VideoProfile.STATE_AUDIO_ONLY);
                Parcelable handoverExtras = extras.getParcelable(
                        android.telecom.Call.EXTRA_HANDOVER_EXTRAS);
                Bundle handoverExtrasBundle = null;
                if (handoverExtras instanceof Bundle) {
                    handoverExtrasBundle = (Bundle) handoverExtras;
                }
                requestHandover(phoneAccountHandle, videoState, handoverExtrasBundle);
            } else {
                Log.addEvent(this, LogUtils.Events.CALL_EVENT, event);
                mConnectionService.sendCallEvent(this, event, extras);
            }
        } else {
            Log.e(this, new NullPointerException(),
                    "sendCallEvent failed due to null CS callId=%s", getId());
        }
    }

    /**
     * Sets this {@link Call} to has the specified {@code parentCall}.  Also sets the parent to
     * have this call as a child.
     * @param parentCall
     */
    void setParentAndChildCall(Call parentCall) {
        boolean isParentChanging = (mParentCall != parentCall);
        setParentCall(parentCall);
        setChildOf(parentCall);
        if (isParentChanging) {
            notifyParentChanged(parentCall);
        }
    }

    /**
     * Notifies listeners when the parent call changes.
     * Used by {@link #setParentAndChildCall(Call)}, and in {@link CallsManager}.
     * @param parentCall The new parent call for this call.
     */
    void notifyParentChanged(Call parentCall) {
        Log.addEvent(this, LogUtils.Events.SET_PARENT, parentCall);
        for (Listener l : mListeners) {
            l.onParentChanged(this);
        }
    }

    /**
     * Unlike {@link #setParentAndChildCall(Call)}, only sets the parent call but does NOT set
     * the child.
     * TODO: This is only required when adding existing connections as a workaround so that we
     * can avoid sending the "onParentChanged" callback until later.
     * @param parentCall The new parent call.
     */
    void setParentCall(Call parentCall) {
        if (parentCall == this) {
            Log.e(this, new Exception(), "setting the parent to self");
            return;
        }
        if (parentCall == mParentCall) {
            // nothing to do
            return;
        }
        if (mParentCall != null) {
            mParentCall.removeChildCall(this);
        }
        mParentCall = parentCall;
    }

    /**
     * To be called after {@link #setParentCall(Call)} to complete setting the parent by adding
     * this call as a child of another call.
     * <p>
     * Note: if using this method alone, the caller must call {@link #notifyParentChanged(Call)} to
     * ensure the InCall UI is updated with the change in parent.
     * @param parentCall The new parent for this call.
     */
    void setChildOf(Call parentCall) {
        if (parentCall != null && !parentCall.getChildCalls().contains(this)) {
            parentCall.addChildCall(this);
        }
    }

    void setConferenceableCalls(List<Call> conferenceableCalls) {
        mConferenceableCalls.clear();
        mConferenceableCalls.addAll(conferenceableCalls);

        for (Listener l : mListeners) {
            l.onConferenceableCallsChanged(this);
        }
    }

    @VisibleForTesting
    public List<Call> getConferenceableCalls() {
        return mConferenceableCalls;
    }

    @VisibleForTesting
    public boolean can(int capability) {
        return (mConnectionCapabilities & capability) == capability;
    }

    @VisibleForTesting
    public boolean hasProperty(int property) {
        return (mConnectionProperties & property) == property;
    }

    private void addChildCall(Call call) {
        if (!mChildCalls.contains(call)) {
            // Set the pseudo-active call to the latest child added to the conference.
            // See definition of mConferenceLevelActiveCall for more detail.
            mConferenceLevelActiveCall = call;
            mChildCalls.add(call);

            Log.addEvent(this, LogUtils.Events.ADD_CHILD, call);

            for (Listener l : mListeners) {
                l.onChildrenChanged(this);
            }
        }
    }

    private void removeChildCall(Call call) {
        if (mChildCalls.remove(call)) {
            Log.addEvent(this, LogUtils.Events.REMOVE_CHILD, call);
            for (Listener l : mListeners) {
                l.onChildrenChanged(this);
            }
        }
    }

    /**
     * Return whether the user can respond to this {@code Call} via an SMS message.
     *
     * @return true if the "Respond via SMS" feature should be enabled
     * for this incoming call.
     *
     * The general rule is that we *do* allow "Respond via SMS" except for
     * the few (relatively rare) cases where we know for sure it won't
     * work, namely:
     *   - a bogus or blank incoming number
     *   - a call from a SIP address
     *   - a "call presentation" that doesn't allow the number to be revealed
     *
     * In all other cases, we allow the user to respond via SMS.
     *
     * Note that this behavior isn't perfect; for example we have no way
     * to detect whether the incoming call is from a landline (with most
     * networks at least), so we still enable this feature even though
     * SMSes to that number will silently fail.
     */
    boolean isRespondViaSmsCapable() {
        if (mState != CallState.RINGING) {
            return false;
        }

        if (getHandle() == null) {
            // No incoming number known or call presentation is "PRESENTATION_RESTRICTED", in
            // other words, the user should not be able to see the incoming phone number.
            return false;
        }

        if (mPhoneNumberUtilsAdapter.isUriNumber(getHandle().toString())) {
            // The incoming number is actually a URI (i.e. a SIP address),
            // not a regular PSTN phone number, and we can't send SMSes to
            // SIP addresses.
            // (TODO: That might still be possible eventually, though. Is
            // there some SIP-specific equivalent to sending a text message?)
            return false;
        }

        // Is there a valid SMS application on the phone?
        if (SmsApplication.getDefaultRespondViaMessageApplication(mContext,
                true /*updateIfNeeded*/) == null) {
            return false;
        }

        // TODO: with some carriers (in certain countries) you *can* actually
        // tell whether a given number is a mobile phone or not. So in that
        // case we could potentially return false here if the incoming call is
        // from a land line.

        // If none of the above special cases apply, it's OK to enable the
        // "Respond via SMS" feature.
        return true;
    }

    List<String> getCannedSmsResponses() {
        return mCannedSmsResponses;
    }

    /**
     * We need to make sure that before we move a call to the disconnected state, it no
     * longer has any parent/child relationships.  We want to do this to ensure that the InCall
     * Service always has the right data in the right order.  We also want to do it in telecom so
     * that the insurance policy lives in the framework side of things.
     */
    private void fixParentAfterDisconnect() {
        setParentAndChildCall(null);
    }

    /**
     * @return True if the call is ringing, else logs the action name.
     */
    private boolean isRinging(String actionName) {
        if (mState == CallState.RINGING) {
            return true;
        }

        Log.i(this, "Request to %s a non-ringing call %s", actionName, this);
        return false;
    }

    @SuppressWarnings("rawtypes")
    private void decrementAssociatedCallCount(ServiceBinder binder) {
        if (binder != null) {
            binder.decrementAssociatedCallCount();
        }
    }

    /**
     * Looks up contact information based on the current handle.
     */
    private void startCallerInfoLookup() {
        mCallerInfo = null;
        mCallsManager.getCallerInfoLookupHelper().startLookup(mHandle, mCallerInfoQueryListener);
    }

    /**
     * Saves the specified caller info if the specified token matches that of the last query
     * that was made.
     *
     * @param callerInfo The new caller information to set.
     */
    private void setCallerInfo(Uri handle, CallerInfo callerInfo) {
        Trace.beginSection("setCallerInfo");
        if (callerInfo == null) {
            Log.i(this, "CallerInfo lookup returned null, skipping update");
            return;
        }

        if (!handle.equals(mHandle)) {
            Log.i(this, "setCallerInfo received stale caller info for an old handle. Ignoring.");
            return;
        }

        mCallerInfo = callerInfo;
        Log.i(this, "CallerInfo received for %s: %s", Log.piiHandle(mHandle), callerInfo);

        if (mCallerInfo.contactDisplayPhotoUri == null ||
                mCallerInfo.cachedPhotoIcon != null || mCallerInfo.cachedPhoto != null) {
            for (Listener l : mListeners) {
                l.onCallerInfoChanged(this);
            }
        }

        Trace.endSection();
    }

    public CallerInfo getCallerInfo() {
        return mCallerInfo;
    }

    private void maybeLoadCannedSmsResponses() {
        if (mCallDirection == CALL_DIRECTION_INCOMING
                && isRespondViaSmsCapable()
                && !mCannedSmsResponsesLoadingStarted) {
            Log.d(this, "maybeLoadCannedSmsResponses: starting task to load messages");
            mCannedSmsResponsesLoadingStarted = true;
            mCallsManager.getRespondViaSmsManager().loadCannedTextMessages(
                    new Response<Void, List<String>>() {
                        @Override
                        public void onResult(Void request, List<String>... result) {
                            if (result.length > 0) {
                                Log.d(this, "maybeLoadCannedSmsResponses: got %s", result[0]);
                                mCannedSmsResponses = result[0];
                                for (Listener l : mListeners) {
                                    l.onCannedSmsResponsesLoaded(Call.this);
                                }
                            }
                        }

                        @Override
                        public void onError(Void request, int code, String msg) {
                            Log.w(Call.this, "Error obtaining canned SMS responses: %d %s", code,
                                    msg);
                        }
                    },
                    mContext
            );
        } else {
            Log.d(this, "maybeLoadCannedSmsResponses: doing nothing");
        }
    }

    /**
     * Sets speakerphone option on when call begins.
     */
    public void setStartWithSpeakerphoneOn(boolean startWithSpeakerphone) {
        mSpeakerphoneOn = startWithSpeakerphone;
    }

    /**
     * Returns speakerphone option.
     *
     * @return Whether or not speakerphone should be set automatically when call begins.
     */
    public boolean getStartWithSpeakerphoneOn() {
        return mSpeakerphoneOn;
    }

    public void stopRtt() {
        if (mConnectionService != null) {
            mConnectionService.stopRtt(this);
        } else {
            // If this gets called by the in-call app before the connection service is set, we'll
            // just ignore it since it's really not supposed to happen.
            Log.w(this, "stopRtt() called before connection service is set.");
        }
    }

    public void sendRttRequest() {
        setRttStreams(true);
        mConnectionService.startRtt(this, getInCallToCsRttPipeForCs(), getCsToInCallRttPipeForCs());
    }

    public void setRttStreams(boolean shouldBeRtt) {
        boolean areStreamsInitialized = mInCallToConnectionServiceStreams != null
                && mConnectionServiceToInCallStreams != null;
        if (shouldBeRtt && !areStreamsInitialized) {
            try {
                mInCallToConnectionServiceStreams = ParcelFileDescriptor.createReliablePipe();
                mConnectionServiceToInCallStreams = ParcelFileDescriptor.createReliablePipe();
            } catch (IOException e) {
                Log.e(this, e, "Failed to create pipes for RTT call.");
            }
        } else if (!shouldBeRtt && areStreamsInitialized) {
            closeRttPipes();
            mInCallToConnectionServiceStreams = null;
            mConnectionServiceToInCallStreams = null;
        }
    }

    public void onRttConnectionFailure(int reason) {
        setRttStreams(false);
        for (Listener l : mListeners) {
            l.onRttInitiationFailure(this, reason);
        }
    }

    public void onRemoteRttRequest() {
        if (isRttCall()) {
            Log.w(this, "Remote RTT request on a call that's already RTT");
            return;
        }

        mPendingRttRequestId = mCallsManager.getNextRttRequestId();
        for (Listener l : mListeners) {
            l.onRemoteRttRequest(this, mPendingRttRequestId);
        }
    }

    public void handleRttRequestResponse(int id, boolean accept) {
        if (mPendingRttRequestId == INVALID_RTT_REQUEST_ID) {
            Log.w(this, "Response received to a nonexistent RTT request: %d", id);
            return;
        }
        if (id != mPendingRttRequestId) {
            Log.w(this, "Response ID %d does not match expected %d", id, mPendingRttRequestId);
            return;
        }
        setRttStreams(accept);
        if (accept) {
            Log.i(this, "RTT request %d accepted.", id);
            mConnectionService.respondToRttRequest(
                    this, getInCallToCsRttPipeForCs(), getCsToInCallRttPipeForCs());
        } else {
            Log.i(this, "RTT request %d rejected.", id);
            mConnectionService.respondToRttRequest(this, null, null);
        }
    }

    public void closeRttPipes() {
        // TODO: may defer this until call is removed?
    }

    public boolean isRttCall() {
        return (mConnectionProperties & Connection.PROPERTY_IS_RTT) == Connection.PROPERTY_IS_RTT;
    }

    public ParcelFileDescriptor getCsToInCallRttPipeForCs() {
        return mConnectionServiceToInCallStreams == null ? null
                : mConnectionServiceToInCallStreams[RTT_PIPE_WRITE_SIDE_INDEX];
    }

    public ParcelFileDescriptor getInCallToCsRttPipeForCs() {
        return mInCallToConnectionServiceStreams == null ? null
                : mInCallToConnectionServiceStreams[RTT_PIPE_READ_SIDE_INDEX];
    }

    public ParcelFileDescriptor getCsToInCallRttPipeForInCall() {
        return mConnectionServiceToInCallStreams == null ? null
                : mConnectionServiceToInCallStreams[RTT_PIPE_READ_SIDE_INDEX];
    }

    public ParcelFileDescriptor getInCallToCsRttPipeForInCall() {
        return mInCallToConnectionServiceStreams == null ? null
                : mInCallToConnectionServiceStreams[RTT_PIPE_WRITE_SIDE_INDEX];
    }

    public int getRttMode() {
        return mRttMode;
    }

    /**
     * Sets a video call provider for the call.
     */
    public void setVideoProvider(IVideoProvider videoProvider) {
        Log.v(this, "setVideoProvider");

        if (videoProvider != null ) {
            try {
                mVideoProviderProxy = new VideoProviderProxy(mLock, videoProvider, this,
                        mCallsManager);
            } catch (RemoteException ignored) {
                // Ignore RemoteException.
            }
        } else {
            mVideoProviderProxy = null;
        }

        mVideoProvider = videoProvider;

        for (Listener l : mListeners) {
            l.onVideoCallProviderChanged(Call.this);
        }
    }

    /**
     * @return The {@link Connection.VideoProvider} binder.
     */
    public IVideoProvider getVideoProvider() {
        if (mVideoProviderProxy == null) {
            return null;
        }

        return mVideoProviderProxy.getInterface();
    }

    /**
     * @return The {@link VideoProviderProxy} for this call.
     */
    public VideoProviderProxy getVideoProviderProxy() {
        return mVideoProviderProxy;
    }

    /**
     * The current video state for the call.
     * See {@link VideoProfile} for a list of valid video states.
     */
    public int getVideoState() {
        return mVideoState;
    }

    /**
     * Returns the video states which were applicable over the duration of a call.
     * See {@link VideoProfile} for a list of valid video states.
     *
     * @return The video states applicable over the duration of the call.
     */
    public int getVideoStateHistory() {
        return mVideoStateHistory;
    }

    /**
     * Determines the current video state for the call.
     * For an outgoing call determines the desired video state for the call.
     * Valid values: see {@link VideoProfile}
     *
     * @param videoState The video state for the call.
     */
    public void setVideoState(int videoState) {
        // If the phone account associated with this call does not support video calling, then we
        // will automatically set the video state to audio-only.
        if (!isVideoCallingSupported()) {
            Log.d(this, "setVideoState: videoState=%s defaulted to audio (video not supported)",
                    VideoProfile.videoStateToString(videoState));
            videoState = VideoProfile.STATE_AUDIO_ONLY;
        }

        // Track Video State history during the duration of the call.
        // Only update the history when the call is active or disconnected. This ensures we do
        // not include the video state history when:
        // - Call is incoming (but not answered).
        // - Call it outgoing (but not answered).
        // We include the video state when disconnected to ensure that rejected calls reflect the
        // appropriate video state.
        // For all other times we add to the video state history, see #setState.
        if (isActive() || getState() == CallState.DISCONNECTED) {
            mVideoStateHistory = mVideoStateHistory | videoState;
        }

        int previousVideoState = mVideoState;
        mVideoState = videoState;
        if (mVideoState != previousVideoState) {
            Log.addEvent(this, LogUtils.Events.VIDEO_STATE_CHANGED,
                    VideoProfile.videoStateToString(videoState));
            for (Listener l : mListeners) {
                l.onVideoStateChanged(this, previousVideoState, mVideoState);
            }
        }

        if (VideoProfile.isVideo(videoState)) {
            mAnalytics.setCallIsVideo(true);
        }
    }

    public boolean getIsVoipAudioMode() {
        return mIsVoipAudioMode;
    }

    public void setIsVoipAudioMode(boolean audioModeIsVoip) {
        mIsVoipAudioMode = audioModeIsVoip;
        for (Listener l : mListeners) {
            l.onIsVoipAudioModeChanged(this);
        }
    }

    public StatusHints getStatusHints() {
        return mStatusHints;
    }

    public void setStatusHints(StatusHints statusHints) {
        mStatusHints = statusHints;
        for (Listener l : mListeners) {
            l.onStatusHintsChanged(this);
        }
    }

    public boolean isUnknown() {
        return mCallDirection == CALL_DIRECTION_UNKNOWN;
    }

    /**
     * Determines if this call is in a disconnecting state.
     *
     * @return {@code true} if this call is locally disconnecting.
     */
    public boolean isLocallyDisconnecting() {
        return mIsLocallyDisconnecting;
    }

    /**
     * Sets whether this call is in a disconnecting state.
     *
     * @param isLocallyDisconnecting {@code true} if this call is locally disconnecting.
     */
    private void setLocallyDisconnecting(boolean isLocallyDisconnecting) {
        mIsLocallyDisconnecting = isLocallyDisconnecting;
    }

    /**
     * @return user handle of user initiating the outgoing call.
     */
    public UserHandle getInitiatingUser() {
        return mInitiatingUser;
    }

    /**
     * Set the user handle of user initiating the outgoing call.
     * @param initiatingUser
     */
    public void setInitiatingUser(UserHandle initiatingUser) {
        Preconditions.checkNotNull(initiatingUser);
        mInitiatingUser = initiatingUser;
    }

    static int getStateFromConnectionState(int state) {
        switch (state) {
            case Connection.STATE_INITIALIZING:
                return CallState.CONNECTING;
            case Connection.STATE_ACTIVE:
                return CallState.ACTIVE;
            case Connection.STATE_DIALING:
                return CallState.DIALING;
            case Connection.STATE_PULLING_CALL:
                return CallState.PULLING;
            case Connection.STATE_DISCONNECTED:
                return CallState.DISCONNECTED;
            case Connection.STATE_HOLDING:
                return CallState.ON_HOLD;
            case Connection.STATE_NEW:
                return CallState.NEW;
            case Connection.STATE_RINGING:
                return CallState.RINGING;
        }
        return CallState.DISCONNECTED;
    }

    /**
     * Determines if this call is in disconnected state and waiting to be destroyed.
     *
     * @return {@code true} if this call is disconected.
     */
    public boolean isDisconnected() {
        return (getState() == CallState.DISCONNECTED || getState() == CallState.ABORTED);
    }

    /**
     * Determines if this call has just been created and has not been configured properly yet.
     *
     * @return {@code true} if this call is new.
     */
    public boolean isNew() {
        return getState() == CallState.NEW;
    }

    /**
     * Sets the call data usage for the call.
     *
     * @param callDataUsage The new call data usage (in bytes).
     */
    public void setCallDataUsage(long callDataUsage) {
        mCallDataUsage = callDataUsage;
    }

    /**
     * Returns the call data usage for the call.
     *
     * @return The call data usage (in bytes).
     */
    public long getCallDataUsage() {
        return mCallDataUsage;
    }

    public void setRttMode(int mode) {
        mRttMode = mode;
        // TODO: hook this up to CallAudioManager
    }

    /**
     * Returns true if the call is outgoing and the NEW_OUTGOING_CALL ordered broadcast intent
     * has come back to telecom and was processed.
     */
    public boolean isNewOutgoingCallIntentBroadcastDone() {
        return mIsNewOutgoingCallIntentBroadcastDone;
    }

    public void setNewOutgoingCallIntentBroadcastIsDone() {
        mIsNewOutgoingCallIntentBroadcastDone = true;
    }

    /**
     * Determines if the call has been held by the remote party.
     *
     * @return {@code true} if the call is remotely held, {@code false} otherwise.
     */
    public boolean isRemotelyHeld() {
        return mIsRemotelyHeld;
    }

    /**
     * Handles Connection events received from a {@link ConnectionService}.
     *
     * @param event The event.
     * @param extras The extras.
     */
    public void onConnectionEvent(String event, Bundle extras) {
        Log.addEvent(this, LogUtils.Events.CONNECTION_EVENT, event);
        if (Connection.EVENT_ON_HOLD_TONE_START.equals(event)) {
            mIsRemotelyHeld = true;
            Log.addEvent(this, LogUtils.Events.REMOTELY_HELD);
            // Inform listeners of the fact that a call hold tone was received.  This will trigger
            // the CallAudioManager to play a tone via the InCallTonePlayer.
            for (Listener l : mListeners) {
                l.onHoldToneRequested(this);
            }
        } else if (Connection.EVENT_ON_HOLD_TONE_END.equals(event)) {
            mIsRemotelyHeld = false;
            Log.addEvent(this, LogUtils.Events.REMOTELY_UNHELD);
            for (Listener l : mListeners) {
                l.onHoldToneRequested(this);
            }
        } else {
            for (Listener l : mListeners) {
                l.onConnectionEvent(this, event, extras);
            }
        }
    }

    public void setOriginalConnectionId(String originalConnectionId) {
        mOriginalConnectionId = originalConnectionId;
    }

    /**
     * For calls added via a ConnectionManager using the
     * {@link android.telecom.ConnectionService#addExistingConnection(PhoneAccountHandle,
     * Connection)}, or {@link android.telecom.ConnectionService#addConference(Conference)} APIS,
     * indicates the ID of this call as it was referred to by the {@code ConnectionService} which
     * originally created it.
     *
     * See {@link Connection#EXTRA_ORIGINAL_CONNECTION_ID}.
     * @return The original connection ID.
     */
    public String getOriginalConnectionId() {
        return mOriginalConnectionId;
    }

    /**
     * Determines if a {@link Call}'s capabilities bitmask indicates that video is supported either
     * remotely or locally.
     *
     * @param capabilities The {@link Connection} capabilities for the call.
     * @return {@code true} if video is supported, {@code false} otherwise.
     */
    private boolean doesCallSupportVideo(int capabilities) {
        return (capabilities & Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL) != 0 ||
                (capabilities & Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL) != 0;
    }

    /**
     * Remove any video capabilities set on a {@link Connection} capabilities bitmask.
     *
     * @param capabilities The capabilities.
     * @return The bitmask with video capabilities removed.
     */
    private int removeVideoCapabilities(int capabilities) {
        return capabilities & ~(Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL |
                Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL);
    }

    /**
     * Initiates a handover of this {@link Call} to another {@link PhoneAccount}.
     * @param handoverToHandle The {@link PhoneAccountHandle} to handover to.
     * @param videoState The video state of the call when handed over.
     * @param extras Optional extras {@link Bundle} provided by the initiating
     *      {@link android.telecom.InCallService}.
     */
    private void requestHandover(PhoneAccountHandle handoverToHandle, int videoState,
                                 Bundle extras) {
        for (Listener l : mListeners) {
            l.onHandoverRequested(this, handoverToHandle, videoState, extras);
        }
    }

    /**
     * Sets the video history based on the state and state transitions of the call. Always add the
     * current video state to the video state history during a call transition except for the
     * transitions DIALING->ACTIVE and RINGING->ACTIVE. In these cases, clear the history. If a
     * call starts dialing/ringing as a VT call and gets downgraded to audio, we need to record
     * the history as an audio call.
     */
    private void updateVideoHistoryViaState(int oldState, int newState) {
        if ((oldState == CallState.DIALING || oldState == CallState.RINGING)
                && newState == CallState.ACTIVE) {
            mVideoStateHistory = mVideoState;
        }

        mVideoStateHistory |= mVideoState;
    }

}
