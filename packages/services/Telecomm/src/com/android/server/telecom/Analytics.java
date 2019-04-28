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

import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.Logging.EventManager;
import android.telecom.ParcelableCallAnalytics;
import android.telecom.TelecomAnalytics;
import android.util.Base64;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.telecom.nano.TelecomLogClass;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import static android.telecom.ParcelableCallAnalytics.AnalyticsEvent;
import static android.telecom.TelecomAnalytics.SessionTiming;

/**
 * A class that collects and stores data on how calls are being made, in order to
 * aggregate these into useful statistics.
 */
public class Analytics {
    public static final String ANALYTICS_DUMPSYS_ARG = "analytics";
    private static final String CLEAR_ANALYTICS_ARG = "clear";

    public static final Map<String, Integer> sLogEventToAnalyticsEvent =
            new HashMap<String, Integer>() {{
                put(LogUtils.Events.SET_SELECT_PHONE_ACCOUNT,
                        AnalyticsEvent.SET_SELECT_PHONE_ACCOUNT);
                put(LogUtils.Events.REQUEST_HOLD, AnalyticsEvent.REQUEST_HOLD);
                put(LogUtils.Events.REQUEST_UNHOLD, AnalyticsEvent.REQUEST_UNHOLD);
                put(LogUtils.Events.SWAP, AnalyticsEvent.SWAP);
                put(LogUtils.Events.SKIP_RINGING, AnalyticsEvent.SKIP_RINGING);
                put(LogUtils.Events.CONFERENCE_WITH, AnalyticsEvent.CONFERENCE_WITH);
                put(LogUtils.Events.SPLIT_FROM_CONFERENCE, AnalyticsEvent.SPLIT_CONFERENCE);
                put(LogUtils.Events.SET_PARENT, AnalyticsEvent.SET_PARENT);
                put(LogUtils.Events.MUTE, AnalyticsEvent.MUTE);
                put(LogUtils.Events.UNMUTE, AnalyticsEvent.UNMUTE);
                put(LogUtils.Events.AUDIO_ROUTE_BT, AnalyticsEvent.AUDIO_ROUTE_BT);
                put(LogUtils.Events.AUDIO_ROUTE_EARPIECE, AnalyticsEvent.AUDIO_ROUTE_EARPIECE);
                put(LogUtils.Events.AUDIO_ROUTE_HEADSET, AnalyticsEvent.AUDIO_ROUTE_HEADSET);
                put(LogUtils.Events.AUDIO_ROUTE_SPEAKER, AnalyticsEvent.AUDIO_ROUTE_SPEAKER);
                put(LogUtils.Events.SILENCE, AnalyticsEvent.SILENCE);
                put(LogUtils.Events.SCREENING_COMPLETED, AnalyticsEvent.SCREENING_COMPLETED);
                put(LogUtils.Events.BLOCK_CHECK_FINISHED, AnalyticsEvent.BLOCK_CHECK_FINISHED);
                put(LogUtils.Events.DIRECT_TO_VM_FINISHED, AnalyticsEvent.DIRECT_TO_VM_FINISHED);
                put(LogUtils.Events.REMOTELY_HELD, AnalyticsEvent.REMOTELY_HELD);
                put(LogUtils.Events.REMOTELY_UNHELD, AnalyticsEvent.REMOTELY_UNHELD);
                put(LogUtils.Events.REQUEST_PULL, AnalyticsEvent.REQUEST_PULL);
                put(LogUtils.Events.REQUEST_ACCEPT, AnalyticsEvent.REQUEST_ACCEPT);
                put(LogUtils.Events.REQUEST_REJECT, AnalyticsEvent.REQUEST_REJECT);
                put(LogUtils.Events.SET_ACTIVE, AnalyticsEvent.SET_ACTIVE);
                put(LogUtils.Events.SET_DISCONNECTED, AnalyticsEvent.SET_DISCONNECTED);
                put(LogUtils.Events.SET_HOLD, AnalyticsEvent.SET_HOLD);
                put(LogUtils.Events.SET_DIALING, AnalyticsEvent.SET_DIALING);
                put(LogUtils.Events.START_CONNECTION, AnalyticsEvent.START_CONNECTION);
                put(LogUtils.Events.BIND_CS, AnalyticsEvent.BIND_CS);
                put(LogUtils.Events.CS_BOUND, AnalyticsEvent.CS_BOUND);
                put(LogUtils.Events.SCREENING_SENT, AnalyticsEvent.SCREENING_SENT);
                put(LogUtils.Events.DIRECT_TO_VM_INITIATED, AnalyticsEvent.DIRECT_TO_VM_INITIATED);
                put(LogUtils.Events.BLOCK_CHECK_INITIATED, AnalyticsEvent.BLOCK_CHECK_INITIATED);
                put(LogUtils.Events.FILTERING_INITIATED, AnalyticsEvent.FILTERING_INITIATED);
                put(LogUtils.Events.FILTERING_COMPLETED, AnalyticsEvent.FILTERING_COMPLETED);
                put(LogUtils.Events.FILTERING_TIMED_OUT, AnalyticsEvent.FILTERING_TIMED_OUT);
            }};

    public static final Map<String, Integer> sLogSessionToSessionId =
            new HashMap<String, Integer> () {{
                put(LogUtils.Sessions.ICA_ANSWER_CALL, SessionTiming.ICA_ANSWER_CALL);
                put(LogUtils.Sessions.ICA_REJECT_CALL, SessionTiming.ICA_REJECT_CALL);
                put(LogUtils.Sessions.ICA_DISCONNECT_CALL, SessionTiming.ICA_DISCONNECT_CALL);
                put(LogUtils.Sessions.ICA_HOLD_CALL, SessionTiming.ICA_HOLD_CALL);
                put(LogUtils.Sessions.ICA_UNHOLD_CALL, SessionTiming.ICA_UNHOLD_CALL);
                put(LogUtils.Sessions.ICA_MUTE, SessionTiming.ICA_MUTE);
                put(LogUtils.Sessions.ICA_SET_AUDIO_ROUTE, SessionTiming.ICA_SET_AUDIO_ROUTE);
                put(LogUtils.Sessions.ICA_CONFERENCE, SessionTiming.ICA_CONFERENCE);
                put(LogUtils.Sessions.CSW_HANDLE_CREATE_CONNECTION_COMPLETE,
                        SessionTiming.CSW_HANDLE_CREATE_CONNECTION_COMPLETE);
                put(LogUtils.Sessions.CSW_SET_ACTIVE, SessionTiming.CSW_SET_ACTIVE);
                put(LogUtils.Sessions.CSW_SET_RINGING, SessionTiming.CSW_SET_RINGING);
                put(LogUtils.Sessions.CSW_SET_DIALING, SessionTiming.CSW_SET_DIALING);
                put(LogUtils.Sessions.CSW_SET_DISCONNECTED, SessionTiming.CSW_SET_DISCONNECTED);
                put(LogUtils.Sessions.CSW_SET_ON_HOLD, SessionTiming.CSW_SET_ON_HOLD);
                put(LogUtils.Sessions.CSW_REMOVE_CALL, SessionTiming.CSW_REMOVE_CALL);
                put(LogUtils.Sessions.CSW_SET_IS_CONFERENCED, SessionTiming.CSW_SET_IS_CONFERENCED);
                put(LogUtils.Sessions.CSW_ADD_CONFERENCE_CALL,
                        SessionTiming.CSW_ADD_CONFERENCE_CALL);

            }};

    public static final Map<String, Integer> sLogEventTimingToAnalyticsEventTiming =
            new HashMap<String, Integer>() {{
                put(LogUtils.Events.Timings.ACCEPT_TIMING,
                        ParcelableCallAnalytics.EventTiming.ACCEPT_TIMING);
                put(LogUtils.Events.Timings.REJECT_TIMING,
                        ParcelableCallAnalytics.EventTiming.REJECT_TIMING);
                put(LogUtils.Events.Timings.DISCONNECT_TIMING,
                        ParcelableCallAnalytics.EventTiming.DISCONNECT_TIMING);
                put(LogUtils.Events.Timings.HOLD_TIMING,
                        ParcelableCallAnalytics.EventTiming.HOLD_TIMING);
                put(LogUtils.Events.Timings.UNHOLD_TIMING,
                        ParcelableCallAnalytics.EventTiming.UNHOLD_TIMING);
                put(LogUtils.Events.Timings.OUTGOING_TIME_TO_DIALING_TIMING,
                        ParcelableCallAnalytics.EventTiming.OUTGOING_TIME_TO_DIALING_TIMING);
                put(LogUtils.Events.Timings.BIND_CS_TIMING,
                        ParcelableCallAnalytics.EventTiming.BIND_CS_TIMING);
                put(LogUtils.Events.Timings.SCREENING_COMPLETED_TIMING,
                        ParcelableCallAnalytics.EventTiming.SCREENING_COMPLETED_TIMING);
                put(LogUtils.Events.Timings.DIRECT_TO_VM_FINISHED_TIMING,
                        ParcelableCallAnalytics.EventTiming.DIRECT_TO_VM_FINISHED_TIMING);
                put(LogUtils.Events.Timings.BLOCK_CHECK_FINISHED_TIMING,
                        ParcelableCallAnalytics.EventTiming.BLOCK_CHECK_FINISHED_TIMING);
                put(LogUtils.Events.Timings.FILTERING_COMPLETED_TIMING,
                        ParcelableCallAnalytics.EventTiming.FILTERING_COMPLETED_TIMING);
                put(LogUtils.Events.Timings.FILTERING_TIMED_OUT_TIMING,
                        ParcelableCallAnalytics.EventTiming.FILTERING_TIMED_OUT_TIMING);
            }};

    public static final Map<Integer, String> sSessionIdToLogSession = new HashMap<>();
    static {
        for (Map.Entry<String, Integer> e : sLogSessionToSessionId.entrySet()) {
            sSessionIdToLogSession.put(e.getValue(), e.getKey());
        }
    }

    public static class CallInfo {
        public void setCallStartTime(long startTime) {
        }

        public void setCallEndTime(long endTime) {
        }

        public void setCallIsAdditional(boolean isAdditional) {
        }

        public void setCallIsInterrupted(boolean isInterrupted) {
        }

        public void setCallDisconnectCause(DisconnectCause disconnectCause) {
        }

        public void addCallTechnology(int callTechnology) {
        }

        public void setCreatedFromExistingConnection(boolean createdFromExistingConnection) {
        }

        public void setCallConnectionService(String connectionServiceName) {
        }

        public void setCallEvents(EventManager.EventRecord records) {
        }

        public void setCallIsVideo(boolean isVideo) {
        }

        public void addVideoEvent(int eventId, int videoState) {
        }

        public void addInCallService(String serviceName, int type) {
        }

        public void addCallProperties(int properties) {
        }
    }

    /**
     * A class that holds data associated with a call.
     */
    @VisibleForTesting
    public static class CallInfoImpl extends CallInfo {
        public String callId;
        public long startTime;  // start time in milliseconds since the epoch. 0 if not yet set.
        public long endTime;  // end time in milliseconds since the epoch. 0 if not yet set.
        public int callDirection;  // one of UNKNOWN_DIRECTION, INCOMING_DIRECTION,
                                   // or OUTGOING_DIRECTION.
        public boolean isAdditionalCall = false;  // true if the call came in while another call was
                                                  // in progress or if the user dialed this call
                                                  // while in the middle of another call.
        public boolean isInterrupted = false;  // true if the call was interrupted by an incoming
                                               // or outgoing call.
        public int callTechnologies;  // bitmask denoting which technologies a call used.

        // true if the Telecom Call object was created from an existing connection via
        // CallsManager#createCallForExistingConnection, for example, by ImsConference.
        public boolean createdFromExistingConnection = false;

        public DisconnectCause callTerminationReason;
        public String connectionService;
        public boolean isEmergency = false;

        public EventManager.EventRecord callEvents;

        public boolean isVideo = false;
        public List<TelecomLogClass.VideoEvent> videoEvents;
        public List<TelecomLogClass.InCallServiceInfo> inCallServiceInfos;
        public int callProperties = 0;

        private long mTimeOfLastVideoEvent = -1;

        CallInfoImpl(String callId, int callDirection) {
            this.callId = callId;
            startTime = 0;
            endTime = 0;
            this.callDirection = callDirection;
            callTechnologies = 0;
            connectionService = "";
            videoEvents = new LinkedList<>();
            inCallServiceInfos = new LinkedList<>();
        }

        CallInfoImpl(CallInfoImpl other) {
            this.callId = other.callId;
            this.startTime = other.startTime;
            this.endTime = other.endTime;
            this.callDirection = other.callDirection;
            this.isAdditionalCall = other.isAdditionalCall;
            this.isInterrupted = other.isInterrupted;
            this.callTechnologies = other.callTechnologies;
            this.createdFromExistingConnection = other.createdFromExistingConnection;
            this.connectionService = other.connectionService;
            this.isEmergency = other.isEmergency;
            this.callEvents = other.callEvents;
            this.isVideo = other.isVideo;
            this.videoEvents = other.videoEvents;
            this.callProperties = other.callProperties;

            if (other.callTerminationReason != null) {
                this.callTerminationReason = new DisconnectCause(
                        other.callTerminationReason.getCode(),
                        other.callTerminationReason.getLabel(),
                        other.callTerminationReason.getDescription(),
                        other.callTerminationReason.getReason(),
                        other.callTerminationReason.getTone());
            } else {
                this.callTerminationReason = null;
            }
        }

        @Override
        public void setCallStartTime(long startTime) {
            Log.d(TAG, "setting startTime for call " + callId + " to " + startTime);
            this.startTime = startTime;
        }

        @Override
        public void setCallEndTime(long endTime) {
            Log.d(TAG, "setting endTime for call " + callId + " to " + endTime);
            this.endTime = endTime;
        }

        @Override
        public void setCallIsAdditional(boolean isAdditional) {
            Log.d(TAG, "setting isAdditional for call " + callId + " to " + isAdditional);
            this.isAdditionalCall = isAdditional;
        }

        @Override
        public void setCallIsInterrupted(boolean isInterrupted) {
            Log.d(TAG, "setting isInterrupted for call " + callId + " to " + isInterrupted);
            this.isInterrupted = isInterrupted;
        }

        @Override
        public void addCallTechnology(int callTechnology) {
            Log.d(TAG, "adding callTechnology for call " + callId + ": " + callTechnology);
            this.callTechnologies |= callTechnology;
        }

        @Override
        public void setCallDisconnectCause(DisconnectCause disconnectCause) {
            Log.d(TAG, "setting disconnectCause for call " + callId + " to " + disconnectCause);
            this.callTerminationReason = disconnectCause;
        }

        @Override
        public void setCreatedFromExistingConnection(boolean createdFromExistingConnection) {
            Log.d(TAG, "setting createdFromExistingConnection for call " + callId + " to "
                    + createdFromExistingConnection);
            this.createdFromExistingConnection = createdFromExistingConnection;
        }

        @Override
        public void setCallConnectionService(String connectionServiceName) {
            Log.d(TAG, "setting connection service for call " + callId + ": "
                    + connectionServiceName);
            this.connectionService = connectionServiceName;
        }

        @Override
        public void setCallEvents(EventManager.EventRecord records) {
            this.callEvents = records;
        }

        @Override
        public void setCallIsVideo(boolean isVideo) {
            this.isVideo = isVideo;
        }

        @Override
        public void addVideoEvent(int eventId, int videoState) {
            long timeSinceLastEvent;
            long currentTime = System.currentTimeMillis();
            if (mTimeOfLastVideoEvent < 0) {
                timeSinceLastEvent = -1;
            } else {
                timeSinceLastEvent = roundToOneSigFig(currentTime - mTimeOfLastVideoEvent);
            }
            mTimeOfLastVideoEvent = currentTime;

            videoEvents.add(new TelecomLogClass.VideoEvent()
                    .setEventName(eventId)
                    .setTimeSinceLastEventMillis(timeSinceLastEvent)
                    .setVideoState(videoState));
        }

        @Override
        public void addInCallService(String serviceName, int type) {
            inCallServiceInfos.add(new TelecomLogClass.InCallServiceInfo()
                    .setInCallServiceName(serviceName)
                    .setInCallServiceType(type));
        }

        @Override
        public void addCallProperties(int properties) {
            this.callProperties |= properties;
        }

        @Override
        public String toString() {
            return "{\n"
                    + "    startTime: " + startTime + '\n'
                    + "    endTime: " + endTime + '\n'
                    + "    direction: " + getCallDirectionString() + '\n'
                    + "    isAdditionalCall: " + isAdditionalCall + '\n'
                    + "    isInterrupted: " + isInterrupted + '\n'
                    + "    callTechnologies: " + getCallTechnologiesAsString() + '\n'
                    + "    callTerminationReason: " + getCallDisconnectReasonString() + '\n'
                    + "    connectionService: " + connectionService + '\n'
                    + "    isVideoCall: " + isVideo + '\n'
                    + "    inCallServices: " + getInCallServicesString() + '\n'
                    + "    callProperties: " + Connection.propertiesToStringShort(callProperties)
                    + '\n'
                    + "}\n";
        }

        public ParcelableCallAnalytics toParcelableAnalytics() {
            TelecomLogClass.CallLog analyticsProto = toProto();
            List<ParcelableCallAnalytics.AnalyticsEvent> events =
                    Arrays.stream(analyticsProto.callEvents)
                    .map(callEventProto -> new ParcelableCallAnalytics.AnalyticsEvent(
                                callEventProto.getEventName(),
                                callEventProto.getTimeSinceLastEventMillis())
                    ).collect(Collectors.toList());

            List<ParcelableCallAnalytics.EventTiming> timings =
                    Arrays.stream(analyticsProto.callTimings)
                    .map(callTimingProto -> new ParcelableCallAnalytics.EventTiming(
                            callTimingProto.getTimingName(),
                            callTimingProto.getTimeMillis())
                    ).collect(Collectors.toList());

            ParcelableCallAnalytics result = new ParcelableCallAnalytics(
                    // rounds down to nearest 5 minute mark
                    analyticsProto.getStartTime5Min(),
                    analyticsProto.getCallDurationMillis(),
                    analyticsProto.getType(),
                    analyticsProto.getIsAdditionalCall(),
                    analyticsProto.getIsInterrupted(),
                    analyticsProto.getCallTechnologies(),
                    analyticsProto.getCallTerminationCode(),
                    analyticsProto.getIsEmergencyCall(),
                    analyticsProto.connectionService[0],
                    analyticsProto.getIsCreatedFromExistingConnection(),
                    events,
                    timings);

            result.setIsVideoCall(analyticsProto.getIsVideoCall());
            result.setVideoEvents(Arrays.stream(analyticsProto.videoEvents)
                    .map(videoEventProto -> new ParcelableCallAnalytics.VideoEvent(
                            videoEventProto.getEventName(),
                            videoEventProto.getTimeSinceLastEventMillis(),
                            videoEventProto.getVideoState())
                    ).collect(Collectors.toList()));

            return result;
        }

        public TelecomLogClass.CallLog toProto() {
            TelecomLogClass.CallLog result = new TelecomLogClass.CallLog();
            result.setStartTime5Min(
                    startTime - startTime % ParcelableCallAnalytics.MILLIS_IN_5_MINUTES);

            // Rounds up to the nearest second.
            long callDuration = (endTime == 0 || startTime == 0) ? 0 : endTime - startTime;
            callDuration += (callDuration % MILLIS_IN_1_SECOND == 0) ?
                    0 : (MILLIS_IN_1_SECOND - callDuration % MILLIS_IN_1_SECOND);
            result.setCallDurationMillis(callDuration);

            result.setType(callDirection)
                    .setIsAdditionalCall(isAdditionalCall)
                    .setIsInterrupted(isInterrupted)
                    .setCallTechnologies(callTechnologies)
                    .setCallTerminationCode(
                            callTerminationReason == null ?
                                    ParcelableCallAnalytics.STILL_CONNECTED :
                                    callTerminationReason.getCode())
                    .setIsEmergencyCall(isEmergency)
                    .setIsCreatedFromExistingConnection(createdFromExistingConnection)
                    .setIsEmergencyCall(isEmergency)
                    .setIsVideoCall(isVideo)
                    .setConnectionProperties(callProperties);

            result.connectionService = new String[] {connectionService};
            if (callEvents != null) {
                result.callEvents = convertLogEventsToProtoEvents(callEvents.getEvents());
                result.callTimings = callEvents.extractEventTimings().stream()
                        .map(Analytics::logEventTimingToProtoEventTiming)
                        .toArray(TelecomLogClass.EventTimingEntry[]::new);
            }
            result.videoEvents =
                    videoEvents.toArray(new TelecomLogClass.VideoEvent[videoEvents.size()]);
            result.inCallServices = inCallServiceInfos.toArray(
                    new TelecomLogClass.InCallServiceInfo[inCallServiceInfos.size()]);

            return result;
        }

        private String getCallDirectionString() {
            switch (callDirection) {
                case UNKNOWN_DIRECTION:
                    return "UNKNOWN";
                case INCOMING_DIRECTION:
                    return "INCOMING";
                case OUTGOING_DIRECTION:
                    return "OUTGOING";
                default:
                    return "UNKNOWN";
            }
        }

        private String getCallTechnologiesAsString() {
            StringBuilder s = new StringBuilder();
            s.append('[');
            if ((callTechnologies & CDMA_PHONE) != 0) s.append("CDMA ");
            if ((callTechnologies & GSM_PHONE) != 0) s.append("GSM ");
            if ((callTechnologies & SIP_PHONE) != 0) s.append("SIP ");
            if ((callTechnologies & IMS_PHONE) != 0) s.append("IMS ");
            if ((callTechnologies & THIRD_PARTY_PHONE) != 0) s.append("THIRD_PARTY ");
            s.append(']');
            return s.toString();
        }

        private String getCallDisconnectReasonString() {
            if (callTerminationReason != null) {
                return callTerminationReason.toString();
            } else {
                return "NOT SET";
            }
        }

        private String getInCallServicesString() {
            StringBuilder s = new StringBuilder();
            s.append("[\n");
            for (TelecomLogClass.InCallServiceInfo service : inCallServiceInfos) {
                s.append("    ");
                s.append("name: ");
                s.append(service.getInCallServiceName());
                s.append(" type: ");
                s.append(service.getInCallServiceType());
                s.append("\n");
            }
            s.append("]");
            return s.toString();
        }
    }
    public static final String TAG = "TelecomAnalytics";

    // Constants for call direction
    public static final int UNKNOWN_DIRECTION = ParcelableCallAnalytics.CALLTYPE_UNKNOWN;
    public static final int INCOMING_DIRECTION = ParcelableCallAnalytics.CALLTYPE_INCOMING;
    public static final int OUTGOING_DIRECTION = ParcelableCallAnalytics.CALLTYPE_OUTGOING;

    // Constants for call technology
    public static final int CDMA_PHONE = ParcelableCallAnalytics.CDMA_PHONE;
    public static final int GSM_PHONE = ParcelableCallAnalytics.GSM_PHONE;
    public static final int IMS_PHONE = ParcelableCallAnalytics.IMS_PHONE;
    public static final int SIP_PHONE = ParcelableCallAnalytics.SIP_PHONE;
    public static final int THIRD_PARTY_PHONE = ParcelableCallAnalytics.THIRD_PARTY_PHONE;

    // Constants for video events
    public static final int SEND_LOCAL_SESSION_MODIFY_REQUEST =
            ParcelableCallAnalytics.VideoEvent.SEND_LOCAL_SESSION_MODIFY_REQUEST;
    public static final int SEND_LOCAL_SESSION_MODIFY_RESPONSE =
            ParcelableCallAnalytics.VideoEvent.SEND_LOCAL_SESSION_MODIFY_RESPONSE;
    public static final int RECEIVE_REMOTE_SESSION_MODIFY_REQUEST =
            ParcelableCallAnalytics.VideoEvent.RECEIVE_REMOTE_SESSION_MODIFY_REQUEST;
    public static final int RECEIVE_REMOTE_SESSION_MODIFY_RESPONSE =
            ParcelableCallAnalytics.VideoEvent.RECEIVE_REMOTE_SESSION_MODIFY_RESPONSE;

    public static final long MILLIS_IN_1_SECOND = ParcelableCallAnalytics.MILLIS_IN_1_SECOND;

    public static final int MAX_NUM_CALLS_TO_STORE = 100;

    private static final Object sLock = new Object(); // Coarse lock for all of analytics
    private static final Map<String, CallInfoImpl> sCallIdToInfo = new HashMap<>();
    private static final LinkedList<String> sActiveCallIds = new LinkedList<>();
    private static final List<SessionTiming> sSessionTimings = new LinkedList<>();

    public static void addSessionTiming(String sessionName, long time) {
        if (sLogSessionToSessionId.containsKey(sessionName)) {
            synchronized (sLock) {
                sSessionTimings.add(new SessionTiming(sLogSessionToSessionId.get(sessionName),
                        time));
            }
        }
    }

    public static CallInfo initiateCallAnalytics(String callId, int direction) {
        Log.d(TAG, "Starting analytics for call " + callId);
        CallInfoImpl callInfo = new CallInfoImpl(callId, direction);
        synchronized (sLock) {
            while (sActiveCallIds.size() >= MAX_NUM_CALLS_TO_STORE) {
                String callToRemove = sActiveCallIds.remove();
                sCallIdToInfo.remove(callToRemove);
            }
            sCallIdToInfo.put(callId, callInfo);
            sActiveCallIds.add(callId);
        }
        return callInfo;
    }

    public static TelecomAnalytics dumpToParcelableAnalytics() {
        List<ParcelableCallAnalytics> calls = new LinkedList<>();
        List<SessionTiming> sessionTimings = new LinkedList<>();
        synchronized (sLock) {
            calls.addAll(sCallIdToInfo.values().stream()
                    .map(CallInfoImpl::toParcelableAnalytics)
                    .collect(Collectors.toList()));
            sessionTimings.addAll(sSessionTimings);
            sCallIdToInfo.clear();
            sSessionTimings.clear();
        }
        return new TelecomAnalytics(sessionTimings, calls);
    }

    public static void dumpToEncodedProto(PrintWriter pw, String[] args) {
        TelecomLogClass.TelecomLog result = new TelecomLogClass.TelecomLog();

        synchronized (sLock) {
            result.callLogs = sCallIdToInfo.values().stream()
                    .map(CallInfoImpl::toProto)
                    .toArray(TelecomLogClass.CallLog[]::new);
            result.sessionTimings = sSessionTimings.stream()
                    .map(timing -> new TelecomLogClass.LogSessionTiming()
                            .setSessionEntryPoint(timing.getKey())
                            .setTimeMillis(timing.getTime()))
                    .toArray(TelecomLogClass.LogSessionTiming[]::new);
            if (args.length > 1 && CLEAR_ANALYTICS_ARG.equals(args[1])) {
                sCallIdToInfo.clear();
                sSessionTimings.clear();
            }
        }
        String encodedProto = Base64.encodeToString(
                TelecomLogClass.TelecomLog.toByteArray(result), Base64.DEFAULT);
        pw.write(encodedProto);
    }

    public static void dump(IndentingPrintWriter writer) {
        synchronized (sLock) {
            int prefixLength = CallsManager.TELECOM_CALL_ID_PREFIX.length();
            List<String> callIds = new ArrayList<>(sCallIdToInfo.keySet());
            // Sort the analytics in increasing order of call IDs
            try {
                Collections.sort(callIds, (id1, id2) -> {
                    int i1, i2;
                    try {
                        i1 = Integer.valueOf(id1.substring(prefixLength));
                    } catch (NumberFormatException e) {
                        i1 = Integer.MAX_VALUE;
                    }

                    try {
                        i2 = Integer.valueOf(id2.substring(prefixLength));
                    } catch (NumberFormatException e) {
                        i2 = Integer.MAX_VALUE;
                    }
                    return i1 - i2;
                });
            } catch (IllegalArgumentException e) {
                // do nothing, leave the list in a partially sorted state.
            }

            for (String callId : callIds) {
                writer.printf("Call %s: ", callId);
                writer.println(sCallIdToInfo.get(callId).toString());
            }

            Map<Integer, Double> averageTimings = SessionTiming.averageTimings(sSessionTimings);
            averageTimings.entrySet().stream()
                    .filter(e -> sSessionIdToLogSession.containsKey(e.getKey()))
                    .forEach(e -> writer.printf("%s: %.2f\n",
                            sSessionIdToLogSession.get(e.getKey()), e.getValue()));
        }
    }

    public static void reset() {
        synchronized (sLock) {
            sCallIdToInfo.clear();
        }
    }

    /**
     * Returns a copy of callIdToInfo. Use only for testing.
     */
    @VisibleForTesting
    public static Map<String, CallInfoImpl> cloneData() {
        synchronized (sLock) {
            Map<String, CallInfoImpl> result = new HashMap<>(sCallIdToInfo.size());
            for (Map.Entry<String, CallInfoImpl> entry : sCallIdToInfo.entrySet()) {
                result.put(entry.getKey(), new CallInfoImpl(entry.getValue()));
            }
            return result;
        }
    }

    private static TelecomLogClass.Event[] convertLogEventsToProtoEvents(
            List<EventManager.Event> logEvents) {
        long timeOfLastEvent = -1;
        ArrayList<TelecomLogClass.Event> events = new ArrayList<>(logEvents.size());
        for (EventManager.Event logEvent : logEvents) {
            if (sLogEventToAnalyticsEvent.containsKey(logEvent.eventId)) {
                TelecomLogClass.Event event = new TelecomLogClass.Event();
                event.setEventName(sLogEventToAnalyticsEvent.get(logEvent.eventId));
                event.setTimeSinceLastEventMillis(roundToOneSigFig(
                        timeOfLastEvent < 0 ? -1 : logEvent.time - timeOfLastEvent));
                events.add(event);
                timeOfLastEvent = logEvent.time;
            }
        }
        return events.toArray(new TelecomLogClass.Event[events.size()]);
    }

    private static TelecomLogClass.EventTimingEntry logEventTimingToProtoEventTiming(
            EventManager.EventRecord.EventTiming logEventTiming) {
        int analyticsEventTimingName =
                sLogEventTimingToAnalyticsEventTiming.containsKey(logEventTiming.name) ?
                        sLogEventTimingToAnalyticsEventTiming.get(logEventTiming.name) :
                        ParcelableCallAnalytics.EventTiming.INVALID;
        return new TelecomLogClass.EventTimingEntry()
                .setTimingName(analyticsEventTimingName)
                .setTimeMillis(logEventTiming.time);
    }

    @VisibleForTesting
    public static long roundToOneSigFig(long val)  {
        if (val == 0) {
            return val;
        }
        int logVal = (int) Math.floor(Math.log10(val < 0 ? -val : val));
        double s = Math.pow(10, logVal);
        double dec =  val / s;
        return (long) (Math.round(dec) * s);
    }
}
