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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.InCallService;
import android.telecom.Log;
import android.telecom.Logging.EventManager;
import android.telecom.ParcelableCallAnalytics;
import android.telecom.TelecomAnalytics;
import android.telecom.TelecomManager;
import android.telecom.VideoCallImpl;
import android.telecom.VideoProfile;
import android.support.test.filters.FlakyTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.telecom.Analytics;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.nano.TelecomLogClass;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class AnalyticsTests extends TelecomSystemTest {
    @MediumTest
    public void testAnalyticsSingleCall() throws Exception {
        IdPair testCall = startAndMakeActiveIncomingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);

        Map<String, Analytics.CallInfoImpl> analyticsMap = Analytics.cloneData();

        assertTrue(analyticsMap.containsKey(testCall.mCallId));

        Analytics.CallInfoImpl callAnalytics = analyticsMap.get(testCall.mCallId);
        assertTrue(callAnalytics.startTime > 0);
        assertEquals(0, callAnalytics.endTime);
        assertEquals(Analytics.INCOMING_DIRECTION, callAnalytics.callDirection);
        assertFalse(callAnalytics.isInterrupted);
        assertNull(callAnalytics.callTerminationReason);
        assertEquals(mConnectionServiceComponentNameA.flattenToShortString(),
                callAnalytics.connectionService);

        mConnectionServiceFixtureA.
                sendSetDisconnected(testCall.mConnectionId, DisconnectCause.ERROR);

        analyticsMap = Analytics.cloneData();
        callAnalytics = analyticsMap.get(testCall.mCallId);
        assertTrue(callAnalytics.endTime > 0);
        assertNotNull(callAnalytics.callTerminationReason);
        assertEquals(DisconnectCause.ERROR, callAnalytics.callTerminationReason.getCode());

        StringWriter sr = new StringWriter();
        IndentingPrintWriter ip = new IndentingPrintWriter(sr, "    ");
        Analytics.dump(ip);
        String dumpResult = sr.toString();
        String[] expectedFields = {"startTime", "endTime", "direction", "isAdditionalCall",
                "isInterrupted", "callTechnologies", "callTerminationReason", "connectionService"};
        for (String field : expectedFields) {
            assertTrue(dumpResult.contains(field));
        }
    }

    @FlakyTest
    @MediumTest
    public void testAnalyticsDumping() throws Exception {
        Analytics.reset();
        IdPair testCall = startAndMakeActiveIncomingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);

        mConnectionServiceFixtureA.
                sendSetDisconnected(testCall.mConnectionId, DisconnectCause.ERROR);
        Analytics.CallInfoImpl expectedAnalytics = Analytics.cloneData().get(testCall.mCallId);

        TelecomManager tm = (TelecomManager) mSpyContext.getSystemService(Context.TELECOM_SERVICE);
        List<ParcelableCallAnalytics> analyticsList = tm.dumpAnalytics().getCallAnalytics();

        assertEquals(1, analyticsList.size());
        ParcelableCallAnalytics pCA = analyticsList.get(0);

        assertTrue(Math.abs(expectedAnalytics.startTime - pCA.getStartTimeMillis()) <
                ParcelableCallAnalytics.MILLIS_IN_5_MINUTES);
        assertEquals(0, pCA.getStartTimeMillis() % ParcelableCallAnalytics.MILLIS_IN_5_MINUTES);
        assertTrue(Math.abs((expectedAnalytics.endTime - expectedAnalytics.startTime) -
                pCA.getCallDurationMillis()) < ParcelableCallAnalytics.MILLIS_IN_1_SECOND);
        assertEquals(0, pCA.getCallDurationMillis() % ParcelableCallAnalytics.MILLIS_IN_1_SECOND);

        assertEquals(expectedAnalytics.callDirection, pCA.getCallType());
        assertEquals(expectedAnalytics.isAdditionalCall, pCA.isAdditionalCall());
        assertEquals(expectedAnalytics.isInterrupted, pCA.isInterrupted());
        assertEquals(expectedAnalytics.callTechnologies, pCA.getCallTechnologies());
        assertEquals(expectedAnalytics.callTerminationReason.getCode(),
                pCA.getCallTerminationCode());
        assertEquals(expectedAnalytics.connectionService, pCA.getConnectionService());
        List<ParcelableCallAnalytics.AnalyticsEvent> analyticsEvents = pCA.analyticsEvents();
        Set<Integer> capturedEvents = new HashSet<>();
        for (ParcelableCallAnalytics.AnalyticsEvent e : analyticsEvents) {
            capturedEvents.add(e.getEventName());
            assertIsRoundedToOneSigFig(e.getTimeSinceLastEvent());
        }
        assertTrue(capturedEvents.contains(ParcelableCallAnalytics.AnalyticsEvent.SET_ACTIVE));
        assertTrue(capturedEvents.contains(
                ParcelableCallAnalytics.AnalyticsEvent.FILTERING_INITIATED));
    }

    @MediumTest
    public void testAnalyticsTwoCalls() throws Exception {
        IdPair testCall1 = startAndMakeActiveIncomingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);
        IdPair testCall2 = startAndMakeActiveOutgoingCall(
                "650-555-1213",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);

        Map<String, Analytics.CallInfoImpl> analyticsMap = Analytics.cloneData();
        assertTrue(analyticsMap.containsKey(testCall1.mCallId));
        assertTrue(analyticsMap.containsKey(testCall2.mCallId));

        Analytics.CallInfoImpl callAnalytics1 = analyticsMap.get(testCall1.mCallId);
        Analytics.CallInfoImpl callAnalytics2 = analyticsMap.get(testCall2.mCallId);
        assertTrue(callAnalytics1.startTime > 0);
        assertTrue(callAnalytics2.startTime > 0);
        assertEquals(0, callAnalytics1.endTime);
        assertEquals(0, callAnalytics2.endTime);

        assertEquals(Analytics.INCOMING_DIRECTION, callAnalytics1.callDirection);
        assertEquals(Analytics.OUTGOING_DIRECTION, callAnalytics2.callDirection);

        assertTrue(callAnalytics1.isInterrupted);
        assertTrue(callAnalytics2.isAdditionalCall);

        assertNull(callAnalytics1.callTerminationReason);
        assertNull(callAnalytics2.callTerminationReason);

        assertEquals(mConnectionServiceComponentNameA.flattenToShortString(),
                callAnalytics1.connectionService);
        assertEquals(mConnectionServiceComponentNameA.flattenToShortString(),
                callAnalytics1.connectionService);

        mConnectionServiceFixtureA.
                sendSetDisconnected(testCall2.mConnectionId, DisconnectCause.REMOTE);
        mConnectionServiceFixtureA.
                sendSetDisconnected(testCall1.mConnectionId, DisconnectCause.ERROR);

        analyticsMap = Analytics.cloneData();
        callAnalytics1 = analyticsMap.get(testCall1.mCallId);
        callAnalytics2 = analyticsMap.get(testCall2.mCallId);
        assertTrue(callAnalytics1.endTime > 0);
        assertTrue(callAnalytics2.endTime > 0);
        assertNotNull(callAnalytics1.callTerminationReason);
        assertNotNull(callAnalytics2.callTerminationReason);
        assertEquals(DisconnectCause.ERROR, callAnalytics1.callTerminationReason.getCode());
        assertEquals(DisconnectCause.REMOTE, callAnalytics2.callTerminationReason.getCode());
    }

    @MediumTest
    public void testAnalyticsVideo() throws Exception {
        Analytics.reset();
        IdPair callIds = startAndMakeActiveOutgoingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);

        CountDownLatch counter = new CountDownLatch(1);
        InCallService.VideoCall.Callback callback = mock(InCallService.VideoCall.Callback.class);

        doAnswer(invocation -> {
            counter.countDown();
            return null;
        }).when(callback)
                .onSessionModifyResponseReceived(anyInt(), any(VideoProfile.class),
                        any(VideoProfile.class));

        mConnectionServiceFixtureA.sendSetVideoProvider(
                mConnectionServiceFixtureA.mLatestConnectionId);
        InCallService.VideoCall videoCall =
                mInCallServiceFixtureX.getCall(callIds.mCallId).getVideoCallImpl(
                        mInCallServiceComponentNameX.getPackageName(), Build.VERSION.SDK_INT);
        videoCall.registerCallback(callback);
        ((VideoCallImpl) videoCall).setVideoState(VideoProfile.STATE_BIDIRECTIONAL);

        videoCall.sendSessionModifyRequest(new VideoProfile(VideoProfile.STATE_RX_ENABLED));
        counter.await(10000, TimeUnit.MILLISECONDS);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Analytics.dumpToEncodedProto(pw, new String[]{});
        TelecomLogClass.TelecomLog analyticsProto =
                TelecomLogClass.TelecomLog.parseFrom(Base64.decode(sw.toString(), Base64.DEFAULT));

        assertEquals(1, analyticsProto.callLogs.length);
        TelecomLogClass.VideoEvent[] videoEvents = analyticsProto.callLogs[0].videoEvents;
        assertEquals(2, videoEvents.length);

        assertEquals(Analytics.SEND_LOCAL_SESSION_MODIFY_REQUEST, videoEvents[0].getEventName());
        assertEquals(VideoProfile.STATE_RX_ENABLED, videoEvents[0].getVideoState());
        assertEquals(-1, videoEvents[0].getTimeSinceLastEventMillis());

        assertEquals(Analytics.RECEIVE_REMOTE_SESSION_MODIFY_RESPONSE,
                videoEvents[1].getEventName());
        assertEquals(VideoProfile.STATE_RX_ENABLED, videoEvents[1].getVideoState());
        assertIsRoundedToOneSigFig(videoEvents[1].getTimeSinceLastEventMillis());
    }

    @SmallTest
    public void testAnalyticsRounding() {
        long[] testVals = {0, -1, -10, -100, -57836, 1, 10, 100, 1000, 458457};
        long[] expected = {0, -1, -10, -100, -60000, 1, 10, 100, 1000, 500000};
        for (int i = 0; i < testVals.length; i++) {
            assertEquals(expected[i], Analytics.roundToOneSigFig(testVals[i]));
        }
    }

    @SmallTest
    public void testAnalyticsLogSessionTiming() throws Exception {
        long minTime = 50;
        Log.startSession(LogUtils.Sessions.CSW_ADD_CONFERENCE_CALL);
        Thread.sleep(minTime);
        Log.endSession();
        TelecomManager tm = (TelecomManager) mSpyContext.getSystemService(Context.TELECOM_SERVICE);
        List<TelecomAnalytics.SessionTiming> sessions = tm.dumpAnalytics().getSessionTimings();
        sessions.stream()
                .filter(s -> LogUtils.Sessions.CSW_ADD_CONFERENCE_CALL.equals(
                        Analytics.sSessionIdToLogSession.get(s.getKey())))
                .forEach(s -> assertTrue(s.getTime() >= minTime));
    }

    @MediumTest
    public void testAnalyticsDumpToProto() throws Exception {
        Analytics.reset();
        IdPair testCall = startAndMakeActiveIncomingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);

        mConnectionServiceFixtureA.
                sendSetDisconnected(testCall.mConnectionId, DisconnectCause.ERROR);
        Analytics.CallInfoImpl expectedAnalytics = Analytics.cloneData().get(testCall.mCallId);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Analytics.dumpToEncodedProto(pw, new String[]{});
        TelecomLogClass.TelecomLog analyticsProto =
                TelecomLogClass.TelecomLog.parseFrom(Base64.decode(sw.toString(), Base64.DEFAULT));

        assertEquals(1, analyticsProto.callLogs.length);
        TelecomLogClass.CallLog callLog = analyticsProto.callLogs[0];

        assertTrue(Math.abs(expectedAnalytics.startTime - callLog.getStartTime5Min()) <
                ParcelableCallAnalytics.MILLIS_IN_5_MINUTES);
        assertEquals(0, callLog.getStartTime5Min() % ParcelableCallAnalytics.MILLIS_IN_5_MINUTES);
        assertTrue(Math.abs((expectedAnalytics.endTime - expectedAnalytics.startTime) -
                callLog.getCallDurationMillis()) < ParcelableCallAnalytics.MILLIS_IN_1_SECOND);
        assertEquals(0,
                callLog.getCallDurationMillis() % ParcelableCallAnalytics.MILLIS_IN_1_SECOND);

        assertEquals(expectedAnalytics.callDirection, callLog.getType());
        assertEquals(expectedAnalytics.isAdditionalCall, callLog.getIsAdditionalCall());
        assertEquals(expectedAnalytics.isInterrupted, callLog.getIsInterrupted());
        assertEquals(expectedAnalytics.callTechnologies, callLog.getCallTechnologies());
        assertEquals(expectedAnalytics.callTerminationReason.getCode(),
                callLog.getCallTerminationCode());
        assertEquals(expectedAnalytics.connectionService, callLog.connectionService[0]);
        TelecomLogClass.Event[] analyticsEvents = callLog.callEvents;
        Set<Integer> capturedEvents = new HashSet<>();
        for (TelecomLogClass.Event e : analyticsEvents) {
            capturedEvents.add(e.getEventName());
            assertIsRoundedToOneSigFig(e.getTimeSinceLastEventMillis());
        }
        assertTrue(capturedEvents.contains(ParcelableCallAnalytics.AnalyticsEvent.SET_ACTIVE));
        assertTrue(capturedEvents.contains(
                ParcelableCallAnalytics.AnalyticsEvent.FILTERING_INITIATED));
    }

    @MediumTest
    public void testAnalyticsAudioRoutes() throws Exception {
        Analytics.reset();
        IdPair testCall = startAndMakeActiveIncomingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);
        List<Integer> audioRoutes = new LinkedList<>();

        waitForHandlerAction(
                mTelecomSystem.getCallsManager().getCallAudioManager()
                        .getCallAudioRouteStateMachine().getHandler(),
                TEST_TIMEOUT);
        audioRoutes.add(mInCallServiceFixtureX.mCallAudioState.getRoute());
        mInCallServiceFixtureX.getInCallAdapter().setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        waitForHandlerAction(
                mTelecomSystem.getCallsManager().getCallAudioManager()
                        .getCallAudioRouteStateMachine().getHandler(),
                TEST_TIMEOUT);
        audioRoutes.add(CallAudioState.ROUTE_SPEAKER);

        Map<String, Analytics.CallInfoImpl> analyticsMap = Analytics.cloneData();
        assertTrue(analyticsMap.containsKey(testCall.mCallId));

        Analytics.CallInfoImpl callAnalytics = analyticsMap.get(testCall.mCallId);
        List<EventManager.Event> events = callAnalytics.callEvents.getEvents();
        for (int route : audioRoutes) {
            String logEvent = CallAudioRouteStateMachine.AUDIO_ROUTE_TO_LOG_EVENT.get(route);
            assertTrue(events.stream().anyMatch(event -> event.eventId.equals(logEvent)));
        }
    }

    @MediumTest
    public void testAnalyticsConnectionProperties() throws Exception {
        Analytics.reset();
        IdPair testCall = startAndMakeActiveIncomingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);

        int properties1 = Connection.PROPERTY_IS_DOWNGRADED_CONFERENCE
                | Connection.PROPERTY_WIFI
                | Connection.PROPERTY_EMERGENCY_CALLBACK_MODE;
        int properties2 = Connection.PROPERTY_HIGH_DEF_AUDIO
                | Connection.PROPERTY_WIFI;
        int expectedProperties = properties1 | properties2;

        mConnectionServiceFixtureA.mConnectionById.get(testCall.mConnectionId).properties =
                properties1;
        mConnectionServiceFixtureA.sendSetConnectionProperties(testCall.mConnectionId);
        mConnectionServiceFixtureA.mConnectionById.get(testCall.mConnectionId).properties =
                properties2;
        mConnectionServiceFixtureA.sendSetConnectionProperties(testCall.mConnectionId);

        mConnectionServiceFixtureA.
                sendSetDisconnected(testCall.mConnectionId, DisconnectCause.ERROR);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Analytics.dumpToEncodedProto(pw, new String[]{});
        TelecomLogClass.TelecomLog analyticsProto =
                TelecomLogClass.TelecomLog.parseFrom(Base64.decode(sw.toString(), Base64.DEFAULT));

        assertEquals(expectedProperties,
                analyticsProto.callLogs[0].getConnectionProperties() & expectedProperties);
    }

    @SmallTest
    public void testAnalyticsMaxSize() throws Exception {
        Analytics.reset();
        for (int i = 0; i < Analytics.MAX_NUM_CALLS_TO_STORE * 2; i++) {
            Analytics.initiateCallAnalytics(String.valueOf(i), Analytics.INCOMING_DIRECTION)
                    .addCallTechnology(i);
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Analytics.dumpToEncodedProto(pw, new String[]{});
        TelecomLogClass.TelecomLog analyticsProto =
                TelecomLogClass.TelecomLog.parseFrom(Base64.decode(sw.toString(), Base64.DEFAULT));

        assertEquals(Analytics.MAX_NUM_CALLS_TO_STORE, analyticsProto.callLogs.length);
        assertEquals(Arrays.stream(analyticsProto.callLogs)
                .filter(x -> x.getCallTechnologies() < 100)
                .count(), 0);
    }

    private void assertIsRoundedToOneSigFig(long x) {
        assertEquals(x, Analytics.roundToOneSigFig(x));
    }
}
