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

package com.android.server.telecom.tests;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.Handler;
import android.telecom.CallAudioState;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallAudioModeStateMachine;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ConnectionServiceWrapper;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.StatusBarNotifier;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.WiredHeadsetManager;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class CallAudioRouteStateMachineTest
        extends StateMachineTestBase<CallAudioRouteStateMachine> {
    private static final int NONE = 0;
    private static final int ON = 1;
    private static final int OFF = 2;

    static class RoutingTestParameters extends TestParameters {
        public String name;
        public int initialRoute;
        public int availableRoutes; // may excl. speakerphone, because that's always available
        public int speakerInteraction; // one of NONE, ON, or OFF
        public int bluetoothInteraction; // one of NONE, ON, or OFF
        public int action;
        public int expectedRoute;
        public int expectedAvailableRoutes; // also may exclude the speakerphone.
        public boolean doesDeviceSupportEarpiece; // set to false in the case of Wear devices
        public boolean shouldRunWithFocus;

        public int callSupportedRoutes = CallAudioState.ROUTE_ALL;

        public RoutingTestParameters(String name, int initialRoute,
                int availableRoutes, int speakerInteraction,
                int bluetoothInteraction, int action, int expectedRoute,
                int expectedAvailableRoutes, boolean doesDeviceSupportEarpiece,
                boolean shouldRunWithFocus) {
            this.name = name;
            this.initialRoute = initialRoute;
            this.availableRoutes = availableRoutes;
            this.speakerInteraction = speakerInteraction;
            this.bluetoothInteraction = bluetoothInteraction;
            this.action = action;
            this.expectedRoute = expectedRoute;
            this.expectedAvailableRoutes = expectedAvailableRoutes;
            this.doesDeviceSupportEarpiece = doesDeviceSupportEarpiece;
            this.shouldRunWithFocus = shouldRunWithFocus;
        }

        public RoutingTestParameters setCallSupportedRoutes(int routes) {
            callSupportedRoutes = routes;
            return this;
        }

        @Override
        public String toString() {
            return "RoutingTestParameters{" +
                    "name='" + name + '\'' +
                    ", initialRoute=" + initialRoute +
                    ", availableRoutes=" + availableRoutes +
                    ", speakerInteraction=" + speakerInteraction +
                    ", bluetoothInteraction=" + bluetoothInteraction +
                    ", action=" + action +
                    ", expectedRoute=" + expectedRoute +
                    ", expectedAvailableRoutes=" + expectedAvailableRoutes +
                    ", doesDeviceSupportEarpiece=" + doesDeviceSupportEarpiece +
                    ", shouldRunWithFocus=" + shouldRunWithFocus +
                    '}';
        }
    }

    @Mock CallsManager mockCallsManager;
    @Mock BluetoothRouteManager mockBluetoothRouteManager;
    @Mock IAudioService mockAudioService;
    @Mock ConnectionServiceWrapper mockConnectionServiceWrapper;
    @Mock WiredHeadsetManager mockWiredHeadsetManager;
    @Mock StatusBarNotifier mockStatusBarNotifier;
    @Mock Call fakeCall;

    private CallAudioManager.AudioServiceFactory mAudioServiceFactory;
    private static final int TEST_TIMEOUT = 500;
    private AudioManager mockAudioManager;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mockAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mAudioServiceFactory = new CallAudioManager.AudioServiceFactory() {
            @Override
            public IAudioService getAudioService() {
                return mockAudioService;
            }
        };

        when(mockCallsManager.getForegroundCall()).thenReturn(fakeCall);
        when(mockCallsManager.getLock()).thenReturn(mLock);
        when(mockCallsManager.hasVideoCall()).thenReturn(false);
        when(fakeCall.getConnectionService()).thenReturn(mockConnectionServiceWrapper);
        when(fakeCall.isAlive()).thenReturn(true);
        when(fakeCall.getSupportedAudioRoutes()).thenReturn(CallAudioState.ROUTE_ALL);

        doNothing().when(mockConnectionServiceWrapper).onCallAudioStateChanged(any(Call.class),
                any(CallAudioState.class));
    }

    @LargeTest
    public void testStateMachineTransitionsWithFocus() throws Throwable {
        List<RoutingTestParameters> paramList = generateTransitionTests(true);
        parametrizedTestStateMachine(paramList);
    }

    @LargeTest
    public void testStateMachineTransitionsWithoutFocus() throws Throwable {
        List<RoutingTestParameters> paramList = generateTransitionTests(false);
        parametrizedTestStateMachine(paramList);
    }

    @MediumTest
    public void testSpeakerPersistence() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                true);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(true);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                when(mockAudioManager.isSpeakerphoneOn()).thenReturn((Boolean) args[0]);
                return null;
            }
        }).when(mockAudioManager).setSpeakerphoneOn(any(Boolean.class));
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.CONNECT_WIRED_HEADSET);
        CallAudioState expectedMiddleState = new CallAudioState(false,
                CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER);
        waitForHandlerAction(stateMachine.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(stateMachine.getHandler(), TEST_TIMEOUT);
        verifyNewSystemCallAudioState(initState, expectedMiddleState);
        resetMocks(true);

        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET);
        verifyNewSystemCallAudioState(expectedMiddleState, initState);
    }

    @MediumTest
    public void testUserBluetoothSwitchOff() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                true);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(true);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.USER_SWITCH_BASELINE_ROUTE);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);

        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);
        verifyNewSystemCallAudioState(initState, expectedEndState);
        resetMocks(false);
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH);
        stateMachine.sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.CONNECT_BLUETOOTH);

        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);
        assertEquals(expectedEndState, stateMachine.getCurrentCallAudioState());
    }

    @MediumTest
    public void testBluetoothRinging() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                true);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);

        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.RINGING_FOCUS);
        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);

        verify(mockBluetoothRouteManager, never()).connectBluetoothAudio(null);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);
        verify(mockBluetoothRouteManager, times(1)).connectBluetoothAudio(null);
    }

    @MediumTest
    public void testConnectBluetoothDuringRinging() {
        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                true);

        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(false);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(false);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(false);
        CallAudioState initState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE);
        stateMachine.initialize(initState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.RINGING_FOCUS);

        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(true);
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.CONNECT_BLUETOOTH);
        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);

        verify(mockBluetoothRouteManager, never()).connectBluetoothAudio(null);
        CallAudioState expectedEndState = new CallAudioState(false,
                CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH);
        verifyNewSystemCallAudioState(initState, expectedEndState);

        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);
        verify(mockBluetoothRouteManager, times(1)).connectBluetoothAudio(null);
    }

    @SmallTest
    public void testInitializationWithEarpieceNoHeadsetNoBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, true);
    }

    @SmallTest
    public void testInitializationWithEarpieceAndHeadsetNoBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, true);
    }

    @SmallTest
    public void testInitializationWithEarpieceAndHeadsetAndBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER
                | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, true);
    }

    @SmallTest
    public void testInitializationWithEarpieceAndBluetoothNoHeadset() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER
                        | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, true);
    }

    @SmallTest
    public void testInitializationWithNoEarpieceNoHeadsetNoBluetooth() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, false);
    }

    @SmallTest
    public void testInitializationWithHeadsetNoBluetoothNoEarpiece() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_WIRED_HEADSET,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER);
        initializationTestHelper(expectedState, false);
    }

    @SmallTest
    public void testInitializationWithHeadsetAndBluetoothNoEarpiece() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_SPEAKER
                | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, false);
    }

    @SmallTest
    public void testInitializationWithBluetoothNoHeadsetNoEarpiece() {
        CallAudioState expectedState = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_SPEAKER | CallAudioState.ROUTE_BLUETOOTH);
        initializationTestHelper(expectedState, false);
    }

    private void initializationTestHelper(CallAudioState expectedState,
            boolean doesDeviceSupportEarpiece) {
        when(mockWiredHeadsetManager.isPluggedIn()).thenReturn(
                (expectedState.getSupportedRouteMask() & CallAudioState.ROUTE_WIRED_HEADSET) != 0);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(
                (expectedState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0);

        CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                doesDeviceSupportEarpiece);
        stateMachine.initialize();
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    private List<RoutingTestParameters> generateTransitionTests(boolean shouldRunWithFocus) {
        List<RoutingTestParameters> params = new ArrayList<>();
        params.add(new RoutingTestParameters(
                "Connect headset during earpiece", // name
                CallAudioState.ROUTE_EARPIECE, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.CONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Connect headset during bluetooth", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                NONE, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.CONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // expectedAvai
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Connect headset during speakerphone", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                OFF, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.CONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect headset during headset", // name
                CallAudioState.ROUTE_WIRED_HEADSET, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // availableRoutes
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect headset during headset with bluetooth available", // name
                CallAudioState.ROUTE_WIRED_HEADSET, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // availableRou
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect headset during bluetooth", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // availableRou
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect headset during speakerphone", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // availableRoutes
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect headset during speakerphone with bluetooth available", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // availableRou
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Connect bluetooth during earpiece", // name
                CallAudioState.ROUTE_EARPIECE, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                NONE, // speakerInteraction
                ON, // bluetoothInteraction
                CallAudioRouteStateMachine.CONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_EARPIECE, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Connect bluetooth during wired headset", // name
                CallAudioState.ROUTE_WIRED_HEADSET, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // availableRoutes
                NONE, // speakerInteraction
                ON, // bluetoothInteraction
                CallAudioRouteStateMachine.CONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_WIRED_HEADSET, // expectedAvai
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Connect bluetooth during speakerphone", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                OFF, // speakerInteraction
                ON, // bluetoothInteraction
                CallAudioRouteStateMachine.CONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_EARPIECE, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect bluetooth during bluetooth without headset in", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                NONE, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect bluetooth during bluetooth without headset in, priority mode ", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                NONE, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect bluetooth during bluetooth with headset in", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // availableRou
                NONE, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect bluetooth during speakerphone", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // availableRou
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect bluetooth during earpiece", // name
                CallAudioState.ROUTE_EARPIECE, // initialRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                NONE, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to speakerphone from earpiece", // name
                CallAudioState.ROUTE_EARPIECE, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                ON, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_SPEAKER, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to speakerphone from headset", // name
                CallAudioState.ROUTE_WIRED_HEADSET, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // availableRoutes
                ON, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_SPEAKER, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to speakerphone from bluetooth", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // availableRou
                ON, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_SPEAKER, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // expectedAvai
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to earpiece from bluetooth", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                NONE, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_EARPIECE, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to earpiece from speakerphone", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                OFF, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_EARPIECE, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to earpiece from speakerphone, priority notifications", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                OFF, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_EARPIECE, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to earpiece from speakerphone, silent mode", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_EARPIECE, // availableRoutes
                OFF, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_EARPIECE, // action
                CallAudioState.ROUTE_EARPIECE, // expectedRoute
                CallAudioState.ROUTE_EARPIECE, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to bluetooth from speakerphone", // name
                CallAudioState.ROUTE_SPEAKER, // initialRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                OFF, // speakerInteraction
                ON, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_BLUETOOTH, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to bluetooth from earpiece", // name
                CallAudioState.ROUTE_EARPIECE, // initialRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                NONE, // speakerInteraction
                ON, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_BLUETOOTH, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH, // expectedAvailable
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch to bluetooth from wired headset", // name
                CallAudioState.ROUTE_WIRED_HEADSET, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // availableRou
                NONE, // speakerInteraction
                ON, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_BLUETOOTH, // action
                CallAudioState.ROUTE_BLUETOOTH, // expectedRoute
                CallAudioState.ROUTE_WIRED_HEADSET | CallAudioState.ROUTE_BLUETOOTH, // expectedAvai
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Switch from bluetooth to wired/earpiece when neither are available", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_BLUETOOTH, // availableRoutes
                ON, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.SWITCH_BASELINE_ROUTE, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_BLUETOOTH, // expectedAvailableRoutes
                false, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect wired headset when device does not support earpiece", // name
                CallAudioState.ROUTE_WIRED_HEADSET, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // availableRoutes
                ON, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_SPEAKER, // expectedAvailableRoutes
                false, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ));

        params.add(new RoutingTestParameters(
                "Disconnect wired headset when call doesn't support earpiece", // name
                CallAudioState.ROUTE_WIRED_HEADSET, // initialRoute
                CallAudioState.ROUTE_WIRED_HEADSET, // availableRoutes
                ON, // speakerInteraction
                NONE, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_SPEAKER, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ).setCallSupportedRoutes(CallAudioState.ROUTE_ALL & ~CallAudioState.ROUTE_EARPIECE));

        params.add(new RoutingTestParameters(
                "Disconnect bluetooth when call does not support earpiece", // name
                CallAudioState.ROUTE_BLUETOOTH, // initialRoute
                CallAudioState.ROUTE_BLUETOOTH,  // availableRoutes
                ON, // speakerInteraction
                OFF, // bluetoothInteraction
                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH, // action
                CallAudioState.ROUTE_SPEAKER, // expectedRoute
                CallAudioState.ROUTE_SPEAKER, // expectedAvailableRoutes
                true, // doesDeviceSupportEarpiece
                shouldRunWithFocus
        ).setCallSupportedRoutes(CallAudioState.ROUTE_ALL & ~CallAudioState.ROUTE_EARPIECE));

        return params;
    }

    @Override
    protected void runParametrizedTestCase(TestParameters _params) throws Throwable {
        RoutingTestParameters params = (RoutingTestParameters) _params;
        if (params.shouldRunWithFocus) {
            runParametrizedTestCaseWithFocus(params);
        } else {
            runParametrizedTestCaseWithoutFocus(params);
        }
    }

    private void runParametrizedTestCaseWithFocus(final RoutingTestParameters params)
            throws Throwable {
        resetMocks(true);

        // Construct a fresh state machine on every case
        final CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                params.doesDeviceSupportEarpiece);

        setupMocksForParams(params);

        // Set the initial CallAudioState object
        final CallAudioState initState = new CallAudioState(false,
                params.initialRoute, (params.availableRoutes | CallAudioState.ROUTE_SPEAKER));
        stateMachine.initialize(initState);

        // Make the state machine have focus so that we actually do something
        stateMachine.sendMessageWithSessionInfo(CallAudioRouteStateMachine.SWITCH_FOCUS,
                CallAudioRouteStateMachine.ACTIVE_FOCUS);
        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);

        // Reset mocks one more time to discard stuff from initialization
        resetMocks(false);
        setupMocksForParams(params);
        stateMachine.sendMessageWithSessionInfo(params.action);

        waitForStateMachineActionCompletion(stateMachine, CallAudioRouteStateMachine.RUN_RUNNABLE);

        Handler h = stateMachine.getHandler();
        waitForHandlerAction(h, TEST_TIMEOUT);
        stateMachine.quitStateMachine();

        // Verify interactions with the speakerphone and bluetooth systems
        switch (params.bluetoothInteraction) {
            case NONE:
                verify(mockBluetoothRouteManager, never()).disconnectBluetoothAudio();
                verify(mockBluetoothRouteManager, never()).connectBluetoothAudio(null);
                break;
            case ON:
                verify(mockBluetoothRouteManager).connectBluetoothAudio(null);

                verify(mockBluetoothRouteManager, never()).disconnectBluetoothAudio();
                break;
            case OFF:
                verify(mockBluetoothRouteManager, never()).connectBluetoothAudio(null);
                verify(mockBluetoothRouteManager).disconnectBluetoothAudio();
        }

        switch (params.speakerInteraction) {
            case NONE:
                verify(mockAudioManager, never()).setSpeakerphoneOn(any(Boolean.class));
                break;
            case ON: // fall through
            case OFF:
                verify(mockAudioManager).setSpeakerphoneOn(params.speakerInteraction == ON);
        }

        // Verify the end state
        CallAudioState expectedState = new CallAudioState(false, params.expectedRoute,
                params.expectedAvailableRoutes | CallAudioState.ROUTE_SPEAKER);
        verifyNewSystemCallAudioState(initState, expectedState);
    }

    private void setupMocksForParams(RoutingTestParameters params) {
        // Set up bluetooth and speakerphone state
        when(mockBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(
                params.initialRoute == CallAudioState.ROUTE_BLUETOOTH);
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(
                (params.availableRoutes & CallAudioState.ROUTE_BLUETOOTH) != 0
                        || (params.expectedAvailableRoutes & CallAudioState.ROUTE_BLUETOOTH) != 0);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(
                params.initialRoute == CallAudioState.ROUTE_SPEAKER);
        when(fakeCall.getSupportedAudioRoutes()).thenReturn(params.callSupportedRoutes);
    }

    private void runParametrizedTestCaseWithoutFocus(final RoutingTestParameters params)
            throws Throwable {
        resetMocks(true);

        // Construct a fresh state machine on every case
        final CallAudioRouteStateMachine stateMachine = new CallAudioRouteStateMachine(
                mContext,
                mockCallsManager,
                mockBluetoothRouteManager,
                mockWiredHeadsetManager,
                mockStatusBarNotifier,
                mAudioServiceFactory,
                params.doesDeviceSupportEarpiece);

        // Set up bluetooth and speakerphone state
        when(mockBluetoothRouteManager.isBluetoothAvailable()).thenReturn(
                (params.availableRoutes & CallAudioState.ROUTE_BLUETOOTH) != 0
                || (params.expectedAvailableRoutes & CallAudioState.ROUTE_BLUETOOTH) != 0);
        when(mockAudioManager.isSpeakerphoneOn()).thenReturn(
                params.initialRoute == CallAudioState.ROUTE_SPEAKER);
        when(fakeCall.getSupportedAudioRoutes()).thenReturn(params.callSupportedRoutes);

        // Set the initial CallAudioState object
        CallAudioState initState = new CallAudioState(false,
                params.initialRoute, (params.availableRoutes | CallAudioState.ROUTE_SPEAKER));
        stateMachine.initialize(initState);
        // Omit the focus-getting statement
        stateMachine.sendMessageWithSessionInfo(params.action);

        waitForStateMachineActionCompletion(stateMachine, CallAudioModeStateMachine.RUN_RUNNABLE);

        Handler h = stateMachine.getHandler();
        waitForHandlerAction(h, TEST_TIMEOUT);
        stateMachine.quitStateMachine();

        // Verify that no substantive interactions have taken place with the
        // rest of the system
        verifyNoSystemAudioChanges();

        // Verify the end state
        CallAudioState expectedState = new CallAudioState(false, params.expectedRoute,
                params.expectedAvailableRoutes | CallAudioState.ROUTE_SPEAKER);
        assertEquals(expectedState, stateMachine.getCurrentCallAudioState());
    }

    private void verifyNoSystemAudioChanges() {
        verify(mockBluetoothRouteManager, never()).disconnectBluetoothAudio();
        verify(mockBluetoothRouteManager, never()).connectBluetoothAudio(null);
        verify(mockAudioManager, never()).setSpeakerphoneOn(any(Boolean.class));
        verify(mockCallsManager, never()).onCallAudioStateChanged(any(CallAudioState.class),
                any(CallAudioState.class));
        verify(mockConnectionServiceWrapper, never()).onCallAudioStateChanged(
                any(Call.class), any(CallAudioState.class));
    }

    private void verifyNewSystemCallAudioState(CallAudioState expectedOldState,
            CallAudioState expectedNewState) {
        ArgumentCaptor<CallAudioState> oldStateCaptor = ArgumentCaptor.forClass(
                CallAudioState.class);
        ArgumentCaptor<CallAudioState> newStateCaptor1 = ArgumentCaptor.forClass(
                CallAudioState.class);
        ArgumentCaptor<CallAudioState> newStateCaptor2 = ArgumentCaptor.forClass(
                CallAudioState.class);
        verify(mockCallsManager, timeout(TEST_TIMEOUT).atLeastOnce()).onCallAudioStateChanged(
                oldStateCaptor.capture(), newStateCaptor1.capture());
        verify(mockConnectionServiceWrapper, timeout(TEST_TIMEOUT).atLeastOnce())
                .onCallAudioStateChanged(same(fakeCall), newStateCaptor2.capture());

        assertTrue(oldStateCaptor.getValue().equals(expectedOldState));
        assertTrue(newStateCaptor1.getValue().equals(expectedNewState));
        assertTrue(newStateCaptor2.getValue().equals(expectedNewState));
    }

    private void resetMocks(boolean resetNotificationFilter) {
        reset(mockAudioManager, mockBluetoothRouteManager, mockCallsManager,
                mockConnectionServiceWrapper, fakeCall);
        when(mockCallsManager.getForegroundCall()).thenReturn(fakeCall);
        when(fakeCall.getConnectionService()).thenReturn(mockConnectionServiceWrapper);
        when(fakeCall.isAlive()).thenReturn(true);
        when(fakeCall.getSupportedAudioRoutes()).thenReturn(CallAudioState.ROUTE_ALL);
        when(mockCallsManager.getLock()).thenReturn(mLock);
        doNothing().when(mockConnectionServiceWrapper).onCallAudioStateChanged(any(Call.class),
                any(CallAudioState.class));
    }
}
