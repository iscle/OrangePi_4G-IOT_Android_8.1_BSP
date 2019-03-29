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

package android.telecom.cts;
import static android.telecom.cts.TestUtils.PACKAGE;
import static android.telecom.cts.TestUtils.TAG;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.InCallService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.MockInCallService.InCallServiceCallbacks;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Telecom CTS tests that require a {@link CtsConnectionService} and
 * {@link MockInCallService} to verify Telecom functionality.
 */
public class BaseTelecomTestWithMockServices extends InstrumentationTestCase {

    public static final int FLAG_REGISTER = 0x1;
    public static final int FLAG_ENABLE = 0x2;

    private static int sCounter = 5549999;

    Context mContext;
    TelecomManager mTelecomManager;

    TestUtils.InvokeCounter mOnBringToForegroundCounter;
    TestUtils.InvokeCounter mOnCallAudioStateChangedCounter;
    TestUtils.InvokeCounter mOnPostDialWaitCounter;
    TestUtils.InvokeCounter mOnCannedTextResponsesLoadedCounter;
    TestUtils.InvokeCounter mOnSilenceRingerCounter;
    TestUtils.InvokeCounter mOnConnectionEventCounter;
    TestUtils.InvokeCounter mOnExtrasChangedCounter;
    TestUtils.InvokeCounter mOnPropertiesChangedCounter;
    TestUtils.InvokeCounter mOnRttModeChangedCounter;
    TestUtils.InvokeCounter mOnRttStatusChangedCounter;
    TestUtils.InvokeCounter mOnRttInitiationFailedCounter;
    TestUtils.InvokeCounter mOnRttRequestCounter;
    Bundle mPreviousExtras;
    int mPreviousProperties = -1;

    InCallServiceCallbacks mInCallCallbacks;
    String mPreviousDefaultDialer = null;
    MockConnectionService connectionService = null;

    boolean mShouldTestTelecom = true;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        mShouldTestTelecom = TestUtils.shouldTestTelecom(mContext);
        if (mShouldTestTelecom) {
            mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
            TestUtils.setDefaultDialer(getInstrumentation(), PACKAGE);
            setupCallbacks();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            cleanupCalls();
            if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
                TestUtils.setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
            }
            tearDownConnectionService(TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
            assertMockInCallServiceUnbound();
        }
        super.tearDown();
    }

    protected PhoneAccount setupConnectionService(MockConnectionService connectionService,
            int flags) throws Exception {
        if (connectionService != null) {
            this.connectionService = connectionService;
        } else {
            // Generate a vanilla mock connection service, if not provided.
            this.connectionService = new MockConnectionService();
        }
        CtsConnectionService.setUp(this.connectionService);

        if ((flags & FLAG_REGISTER) != 0) {
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT);
        }
        if ((flags & FLAG_ENABLE) != 0) {
            TestUtils.enablePhoneAccount(getInstrumentation(), TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
            // Wait till the adb commands have executed and account is enabled in Telecom database.
            assertPhoneAccountEnabled(TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        }

        return TestUtils.TEST_PHONE_ACCOUNT;
    }

    protected void tearDownConnectionService(PhoneAccountHandle accountHandle) throws Exception {
        if (this.connectionService != null) {
            assertNumConnections(this.connectionService, 0);
        }
        mTelecomManager.unregisterPhoneAccount(accountHandle);
        CtsConnectionService.tearDown();
        assertCtsConnectionServiceUnbound();
        this.connectionService = null;
    }

    protected void startCallTo(Uri address, PhoneAccountHandle accountHandle) {
        final Intent intent = new Intent(Intent.ACTION_CALL, address);
        if (accountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private void setupCallbacks() {
        mInCallCallbacks = new InCallServiceCallbacks() {
            @Override
            public void onCallAdded(Call call, int numCalls) {
                Log.i(TAG, "onCallAdded, Call: " + call + ", Num Calls: " + numCalls);
                this.lock.release();
            }
            @Override
            public void onCallRemoved(Call call, int numCalls) {
                Log.i(TAG, "onCallRemoved, Call: " + call + ", Num Calls: " + numCalls);
            }
            @Override
            public void onParentChanged(Call call, Call parent) {
                Log.i(TAG, "onParentChanged, Call: " + call + ", Parent: " + parent);
                this.lock.release();
            }
            @Override
            public void onChildrenChanged(Call call, List<Call> children) {
                Log.i(TAG, "onChildrenChanged, Call: " + call + "Children: " + children);
                this.lock.release();
            }
            @Override
            public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
                Log.i(TAG, "onConferenceableCallsChanged, Call: " + call + ", Conferenceables: " +
                        conferenceableCalls);
            }
            @Override
            public void onDetailsChanged(Call call, Call.Details details) {
                Log.i(TAG, "onDetailsChanged, Call: " + call + ", Details: " + details);
                if (!areBundlesEqual(mPreviousExtras, details.getExtras())) {
                    mOnExtrasChangedCounter.invoke(call, details);
                }
                mPreviousExtras = details.getExtras();

                if (mPreviousProperties != details.getCallProperties()) {
                    mOnPropertiesChangedCounter.invoke(call, details);
                    Log.i(TAG, "onDetailsChanged; properties changed from " + Call.Details.propertiesToString(mPreviousProperties) +
                            " to " + Call.Details.propertiesToString(details.getCallProperties()));
                }
                mPreviousProperties = details.getCallProperties();
            }
            @Override
            public void onCallDestroyed(Call call) {
                Log.i(TAG, "onCallDestroyed, Call: " + call);
            }
            @Override
            public void onCallStateChanged(Call call, int newState) {
                Log.i(TAG, "onCallStateChanged, Call: " + call + ", New State: " + newState);
            }
            @Override
            public void onBringToForeground(boolean showDialpad) {
                mOnBringToForegroundCounter.invoke(showDialpad);
            }
            @Override
            public void onCallAudioStateChanged(CallAudioState audioState) {
                Log.i(TAG, "onCallAudioStateChanged, audioState: " + audioState);
                mOnCallAudioStateChangedCounter.invoke(audioState);
            }
            @Override
            public void onPostDialWait(Call call, String remainingPostDialSequence) {
                mOnPostDialWaitCounter.invoke(call, remainingPostDialSequence);
            }
            @Override
            public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {
                mOnCannedTextResponsesLoadedCounter.invoke(call, cannedTextResponses);
            }
            @Override
            public void onConnectionEvent(Call call, String event, Bundle extras) {
                mOnConnectionEventCounter.invoke(call, event, extras);
            }

            @Override
            public void onSilenceRinger() {
                Log.i(TAG, "onSilenceRinger");
                mOnSilenceRingerCounter.invoke();
            }

            @Override
            public void onRttModeChanged(Call call, int mode) {
                mOnRttModeChangedCounter.invoke(call, mode);
            }

            @Override
            public void onRttStatusChanged(Call call, boolean enabled, Call.RttCall rttCall) {
                mOnRttStatusChangedCounter.invoke(call, enabled, rttCall);
            }

            @Override
            public void onRttRequest(Call call, int id) {
                mOnRttRequestCounter.invoke(call, id);
            }

            @Override
            public void onRttInitiationFailure(Call call, int reason) {
                mOnRttInitiationFailedCounter.invoke(call, reason);
            }

        };

        MockInCallService.setCallbacks(mInCallCallbacks);

        // TODO: If more InvokeCounters are added in the future, consider consolidating them into a
        // single Collection.
        mOnBringToForegroundCounter = new TestUtils.InvokeCounter("OnBringToForeground");
        mOnCallAudioStateChangedCounter = new TestUtils.InvokeCounter("OnCallAudioStateChanged");
        mOnPostDialWaitCounter = new TestUtils.InvokeCounter("OnPostDialWait");
        mOnCannedTextResponsesLoadedCounter = new TestUtils.InvokeCounter("OnCannedTextResponsesLoaded");
        mOnSilenceRingerCounter = new TestUtils.InvokeCounter("OnSilenceRinger");
        mOnConnectionEventCounter = new TestUtils.InvokeCounter("OnConnectionEvent");
        mOnExtrasChangedCounter = new TestUtils.InvokeCounter("OnDetailsChangedCounter");
        mOnPropertiesChangedCounter = new TestUtils.InvokeCounter("OnPropertiesChangedCounter");
        mOnRttModeChangedCounter = new TestUtils.InvokeCounter("mOnRttModeChangedCounter");
        mOnRttStatusChangedCounter = new TestUtils.InvokeCounter("mOnRttStatusChangedCounter");
        mOnRttInitiationFailedCounter =
                new TestUtils.InvokeCounter("mOnRttInitiationFailedCounter");
        mOnRttRequestCounter = new TestUtils.InvokeCounter("mOnRttRequestCounter");
    }

    /**
     * Puts Telecom in a state where there is an incoming call provided by the
     * {@link CtsConnectionService} which can be tested.
     */
    void addAndVerifyNewIncomingCall(Uri incomingHandle, Bundle extras) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentCallCount = mInCallCallbacks.getService().getCallCount();
        }

        if (extras == null) {
            extras = new Bundle();
        }
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, incomingHandle);
        mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);

        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                        TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a call.",
                currentCallCount + 1,
                mInCallCallbacks.getService().getCallCount());
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall() {
        placeAndVerifyCall(null);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     *
     *  @param videoState the video state of the call.
     */
    void placeAndVerifyCall(int videoState) {
        placeAndVerifyCall(null, videoState);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall(Bundle extras) {
        placeAndVerifyCall(extras, VideoProfile.STATE_AUDIO_ONLY);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall(Bundle extras, int videoState) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentCallCount = mInCallCallbacks.getService().getCallCount();
        }
        int currentConnectionCount = getNumberOfConnections();
        placeNewCallWithPhoneAccount(extras, videoState);

        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                        TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a call.",
                currentCallCount + 1,
                mInCallCallbacks.getService().getCallCount());

        // The connectionService.lock is released in
        // MockConnectionService#onCreateOutgoingConnection, however the connection will not
        // actually be added to the list of connections in the ConnectionService until shortly
        // afterwards.  So there is still a potential for the lock to be released before it would
        // be seen by calls to ConnectionService#getAllConnections().
        // We will wait here until the list of connections includes one more connection to ensure
        // that placing the call has fully completed.
        final int expectedConnectionCount = currentConnectionCount + 1;
        assertCSConnections(expectedConnectionCount);
    }

    int getNumberOfConnections() {
        return CtsConnectionService.getAllConnectionsFromTelecom().size();
    }

    MockConnection verifyConnectionForOutgoingCall() {
        // Assuming only 1 connection present
        return verifyConnectionForOutgoingCall(0);
    }

    MockConnection verifyConnectionForOutgoingCall(int connectionIndex) {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create outgoing connection for outgoing call",
                connectionService.outgoingConnections.size(), not(equalTo(0)));
        MockConnection connection = connectionService.outgoingConnections.get(connectionIndex);
        return connection;
    }

    MockConnection verifyConnectionForIncomingCall() {
        // Assuming only 1 connection present
        return verifyConnectionForIncomingCall(0);
    }

    MockConnection verifyConnectionForIncomingCall(int connectionIndex) {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create incoming connections for incoming calls",
                connectionService.incomingConnections.size(), not(equalTo(0)));
        MockConnection connection = connectionService.incomingConnections.get(connectionIndex);
        setAndVerifyConnectionForIncomingCall(connection);
        return connection;
    }

    void setAndVerifyConnectionForIncomingCall(MockConnection connection) {
        connection.setRinging();
        assertConnectionState(connection, Connection.STATE_RINGING);
    }

    void setAndVerifyConferenceablesForOutgoingConnection(int connectionIndex) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        // Make all other outgoing connections as conferenceable with this connection.
        MockConnection connection = connectionService.outgoingConnections.get(connectionIndex);
        List<Connection> confConnections =
                new ArrayList<>(connectionService.outgoingConnections.size());
        for (Connection c : connectionService.outgoingConnections) {
            if (c != connection) {
                confConnections.add(c);
            }
        }
        connection.setConferenceableConnections(confConnections);
        assertEquals(connection.getConferenceables(), confConnections);
    }

    void addConferenceCall(Call call1, Call call2) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentConfCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentConfCallCount = mInCallCallbacks.getService().getConferenceCallCount();
        }
        // Verify that the calls have each other on their conferenceable list before proceeding
        List<Call> callConfList = new ArrayList<>();
        callConfList.add(call2);
        assertCallConferenceableList(call1, callConfList);

        callConfList.clear();
        callConfList.add(call1);
        assertCallConferenceableList(call2, callConfList);

        call1.conference(call2);

        /**
         * We should have 1 onCallAdded, 2 onChildrenChanged and 2 onParentChanged invoked, so
         * we should have 5 available permits on the incallService lock.
         */
        try {
            if (!mInCallCallbacks.lock.tryAcquire(5, 3, TimeUnit.SECONDS)) {
                fail("Conference addition failed.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a conf call.",
                currentConfCallCount + 1,
                mInCallCallbacks.getService().getConferenceCallCount());
    }

    void splitFromConferenceCall(Call call1) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());

        call1.splitFromConference();
        /**
         * We should have 1 onChildrenChanged and 1 onParentChanged invoked, so
         * we should have 2 available permits on the incallService lock.
         */
        try {
            if (!mInCallCallbacks.lock.tryAcquire(2, 3, TimeUnit.SECONDS)) {
                fail("Conference split failed");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
    }

    MockConference verifyConferenceForOutgoingCall() {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing conference requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
        // Return the newly created conference object to the caller
        MockConference conference = connectionService.conferences.get(0);
        setAndVerifyConferenceForOutgoingCall(conference);
        return conference;
    }

    void setAndVerifyConferenceForOutgoingCall(MockConference conference) {
        conference.setActive();
        assertConferenceState(conference, Connection.STATE_ACTIVE);
    }

    /**
     * Disconnect the created test call and verify that Telecom has cleared all calls.
     */
    void cleanupCalls() {
        if (mInCallCallbacks != null && mInCallCallbacks.getService() != null) {
            mInCallCallbacks.getService().disconnectAllConferenceCalls();
            mInCallCallbacks.getService().disconnectAllCalls();
            assertNumConferenceCalls(mInCallCallbacks.getService(), 0);
            assertNumCalls(mInCallCallbacks.getService(), 0);
        }
    }

    /**
     * Place a new outgoing call via the {@link CtsConnectionService}
     */
    private void placeNewCallWithPhoneAccount(Bundle extras, int videoState) {
        if (extras == null) {
            extras = new Bundle();
        }
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TestUtils.TEST_PHONE_ACCOUNT_HANDLE);

        if (!VideoProfile.isAudioOnly(videoState)) {
            extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
        }

        mTelecomManager.placeCall(createTestNumber(), extras);
    }

    /**
     * Create a new number each time for a new test. Telecom has special logic to reuse certain
     * calls if multiple calls to the same number are placed within a short period of time which
     * can cause certain tests to fail.
     */
    Uri createTestNumber() {
        return Uri.fromParts("tel", String.valueOf(++sCounter), null);
    }

    public static Uri getTestNumber() {
        return Uri.fromParts("tel", String.valueOf(sCounter), null);
    }

    void assertNumCalls(final MockInCallService inCallService, final int numCalls) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return numCalls;
            }
            @Override
            public Object actual() {
                return inCallService.getCallCount();
            }
        },
        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
        "InCallService should contain " + numCalls + " calls."
    );
    }

    void assertNumConferenceCalls(final MockInCallService inCallService, final int numCalls) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return numCalls;
            }
            @Override
            public Object actual() {
                return inCallService.getConferenceCallCount();
            }
        },
        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
        "InCallService should contain " + numCalls + " conference calls."
    );
    }

    void assertCSConnections(final int numConnections) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return numConnections;
                                              }

                                              @Override
                                              public Object actual() {
                                                  return CtsConnectionService
                                                          .getAllConnectionsFromTelecom()
                                                          .size();
                                              }
                                          },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "ConnectionService should contain " + numConnections + " connections."
        );
    }

    void assertNumConnections(final MockConnectionService connService, final int numConnections) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return numConnections;
                                              }
                                              @Override
                                              public Object actual() {
                                                  return connService.getAllConnections().size();
                                              }
                                          },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "ConnectionService should contain " + numConnections + " connections."
        );
    }

    void assertMuteState(final InCallService incallService, final boolean isMuted) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isMuted;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return state == null ? null : state.isMuted();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's mute state should be: " + isMuted
        );
    }

    void assertMuteState(final MockConnection connection, final boolean isMuted) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isMuted;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = connection.getCallAudioState();
                        return state == null ? null : state.isMuted();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection's mute state should be: " + isMuted
        );
    }

    void assertAudioRoute(final InCallService incallService, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return route;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return state == null ? null : state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's audio route should be: " + route
        );
    }

    void assertNotAudioRoute(final InCallService incallService, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return new Boolean(true);
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return route != state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's audio route should not be: " + route
        );
    }

    void assertAudioRoute(final MockConnection connection, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return route;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = ((Connection) connection).getCallAudioState();
                        return state == null ? null : state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection's audio route should be: " + route
        );
    }

    void assertConnectionState(final Connection connection, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return connection.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection should be in state " + state
        );
    }

    void assertCallState(final Call call, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return call.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call: " + call + " should be in state " + state
        );
    }

    void assertCallConferenceableList(final Call call, final List<Call> conferenceableList) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return conferenceableList;
                    }

                    @Override
                    public Object actual() {
                        return call.getConferenceableCalls();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call: " + call + " does not have the correct conferenceable call list."
        );
    }

    void assertDtmfString(final MockConnection connection, final String dtmfString) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                @Override
                public Object expected() {
                    return dtmfString;
                }

                @Override
                public Object actual() {
                    return connection.getDtmfString();
                }
            },
            WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
            "DTMF string should be equivalent to entered DTMF characters: " + dtmfString
        );
    }

    void assertDtmfString(final MockConference conference, final String dtmfString) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                @Override
                public Object expected() {
                    return dtmfString;
                }

                @Override
                public Object actual() {
                    return conference.getDtmfString();
                }
            },
            WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
            "DTMF string should be equivalent to entered DTMF characters: " + dtmfString
        );
    }

    void assertCallDisplayName(final Call call, final String name) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return name;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getCallerDisplayName();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have display name: " + name
        );
    }

    void assertConnectionCallDisplayName(final Connection connection, final String name) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return name;
                    }

                    @Override
                    public Object actual() {
                        return connection.getCallerDisplayName();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection should have display name: " + name
        );
    }

    void assertDisconnectReason(final Connection connection, final String disconnectReason) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return disconnectReason;
                    }

                    @Override
                    public Object actual() {
                        return connection.getDisconnectCause().getReason();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection should have been disconnected with reason: " + disconnectReason
        );
    }

    void assertConferenceState(final Conference conference, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return conference.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Conference should be in state " + state
        );
    }

    /**
     * Checks all fields of two PhoneAccounts for equality, with the exception of the enabled state.
     * Should only be called after assertPhoneAccountRegistered when it can be guaranteed
     * that the PhoneAccount is registered.
     * @param expected The expected PhoneAccount.
     * @param actual The actual PhoneAccount.
     */
    void assertPhoneAccountEquals(final PhoneAccount expected,
            final PhoneAccount actual) {
        assertEquals(expected.getAddress(), actual.getAddress());
        assertEquals(expected.getAccountHandle(), actual.getAccountHandle());
        assertEquals(expected.getCapabilities(), actual.getCapabilities());
        assertTrue(areBundlesEqual(expected.getExtras(), actual.getExtras()));
        assertEquals(expected.getHighlightColor(), actual.getHighlightColor());
        assertEquals(expected.getIcon(), actual.getIcon());
        assertEquals(expected.getLabel(), actual.getLabel());
        assertEquals(expected.getShortDescription(), actual.getShortDescription());
        assertEquals(expected.getSubscriptionAddress(), actual.getSubscriptionAddress());
        assertEquals(expected.getSupportedUriSchemes(), actual.getSupportedUriSchemes());
    }

    void assertPhoneAccountRegistered(final PhoneAccountHandle handle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.getPhoneAccount(handle) != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone account registration failed for " + handle
        );
    }

    void assertPhoneAccountEnabled(final PhoneAccountHandle handle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        PhoneAccount phoneAccount = mTelecomManager.getPhoneAccount(handle);
                        return (phoneAccount != null && phoneAccount.isEnabled());
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone account enable failed for " + handle
        );
    }

    void assertCtsConnectionServiceUnbound() {
        if (CtsConnectionService.isBound()) {
            assertTrue("CtsConnectionService not yet unbound!",
                    CtsConnectionService.waitForUnBinding());
        }
    }

    void assertMockInCallServiceUnbound() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return false;
                    }

                    @Override
                    public Object actual() {
                        return MockInCallService.isServiceBound();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "MockInCallService not yet unbound!"
        );
    }

    void assertIsInCall(boolean isIncall) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isIncall;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.isInCall();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected isInCall to be " + isIncall
        );
    }

    void assertIsInManagedCall(boolean isIncall) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isIncall;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.isInManagedCall();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected isInManagedCall to be " + isIncall
        );
    }

    /**
     * Asserts that a call's properties are as expected.
     *
     * @param call The call.
     * @param properties The expected properties.
     */
    public void assertCallProperties(final Call call, final int properties) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().hasProperty(properties);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have properties " + properties
        );
    }

    /**
     * Asserts that a call's capabilities are as expected.
     *
     * @param call The call.
     * @param capabilities The expected capabiltiies.
     */
    public void assertCallCapabilities(final Call call, final int capabilities) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (call.getDetails().getCallCapabilities() & capabilities) ==
                                capabilities;
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have properties " + capabilities
        );
    }

    void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    /**
     * Performs some work, and waits for the condition to be met.  If the condition is not met in
     * each step of the loop, the work is performed again.
     *
     * @param work The work to perform.
     * @param condition The condition.
     * @param timeout The timeout.
     * @param description Description of the work being performed.
     */
    void doWorkAndWaitUntilConditionIsTrueOrTimeout(Work work, Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        work.doWork();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
            work.doWork();
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual();
    }

    protected interface Work {
        void doWork();
    }

    public static boolean areBundlesEqual(Bundle extras, Bundle newExtras) {
        if (extras == null || newExtras == null) {
            return extras == newExtras;
        }

        if (extras.size() != newExtras.size()) {
            return false;
        }

        for (String key : extras.keySet()) {
            if (key != null) {
                final Object value = extras.get(key);
                final Object newValue = newExtras.get(key);
                if (!Objects.equals(value, newValue)) {
                    return false;
                }
            }
        }
        return true;
    }
}
