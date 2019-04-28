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

import android.os.Bundle;
import android.telecom.Call;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.InCallService;
import android.telecom.ParcelableCall;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Tests the {@link Connection} and {@link Call} extras functionality.
 */
public class CallExtrasTest extends TelecomSystemTest {

    public final static String EXTRA_KEY_STR = "STRINGKEY";
    public final static String EXTRA_KEY_STR2 = "BLAHTEST";
    public final static String EXTRA_KEY_INT = "INTKEY";
    public final static String EXTRA_KEY_BOOL = "BOOLKEY";
    public final static String EXTRA_VALUE_STR = "socks";
    public final static String EXTRA_VALUE2_STR = "mozzarella";
    public final static int EXTRA_VALUE_INT = 1234;

    /**
     * Tests setting extras on the connection side and ensuring they are propagated through to
     * the InCallService.
     *
     * @throws Exception
     */
    @MediumTest
    public void testCsPutExtras() throws Exception {
        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        // Communicate extras from the ConnectionService to the InCallService.
        Bundle extras = new Bundle();
        extras.putString(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        extras.putInt(EXTRA_KEY_INT, EXTRA_VALUE_INT);

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;
        connection.putExtras(extras);
        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(
                EXTRA_KEY_STR));
        assertTrue(mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(
                EXTRA_KEY_INT));
    }

    /**
     * Tests setting extras on the connection side and ensuring they are propagated through to
     * the InCallService.
     *
     * @throws Exception
     */
    @MediumTest
    public void testCsPutBooleanExtra() throws Exception {
        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;
        connection.putExtra(EXTRA_KEY_BOOL, true);
        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(mInCallServiceFixtureX.getCall(ids.mCallId).getExtras()
                .containsKey(EXTRA_KEY_BOOL));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().getBoolean(EXTRA_KEY_BOOL));
    }

    /**
     * Tests setting extras on the connection side and ensuring they are propagated through to
     * the InCallService.
     *
     * @throws Exception
     */
    @MediumTest
    public void testCsPutIntExtra() throws Exception {
        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;
        connection.putExtra(EXTRA_KEY_INT, EXTRA_VALUE_INT);
        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_INT));
        assertEquals(EXTRA_VALUE_INT,
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().getInt(EXTRA_KEY_INT));
    }

    /**
     * Tests setting extras on the connection side and ensuring they are propagated through to
     * the InCallService.
     *
     * @throws Exception
     */
    @MediumTest
    public void testCsPutStringExtra() throws Exception {
        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;
        connection.putExtra(EXTRA_KEY_STR, EXTRA_VALUE_STR);

        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertEquals(EXTRA_VALUE_STR,
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().getString(EXTRA_KEY_STR));
    }

    /**
     * Tests remove extras on the connection side and ensuring the removal is reflected in the
     * InCallService.
     *
     * @throws Exception
     */
    @MediumTest
    public void testCsRemoveExtra() throws Exception {
        // Get a call up and running."STRING"
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        // Add something.
        Connection connection = mConnectionServiceFixtureA.mLatestConnection;
        connection.putExtra(EXTRA_KEY_STR2, EXTRA_VALUE_STR);
        connection.putExtra(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertEquals(EXTRA_VALUE_STR,
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().getString(EXTRA_KEY_STR));

        // Take it away.
        connection.removeExtras(new ArrayList<String>(Arrays.asList(EXTRA_KEY_STR)));
        mInCallServiceFixtureX.waitForUpdate();
        assertFalse(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertTrue(mInCallServiceFixtureX.getCall(ids.mCallId).getExtras()
                .containsKey(EXTRA_KEY_STR2));
    }

    /**
     * Tests putting a new value for an existing extras key.
     *
     * @throws Exception
     */
    @MediumTest
    public void testCsUpdateExisting() throws Exception {
        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;

        // Communicate extras from the ConnectionService to the InCallService.
        Bundle extras = new Bundle();
        extras.putString(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        extras.putInt(EXTRA_KEY_INT, EXTRA_VALUE_INT);
        connection.putExtras(extras);
        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_INT));

        connection.putExtra(EXTRA_KEY_STR, EXTRA_VALUE2_STR);
        mInCallServiceFixtureX.waitForUpdate();
        assertEquals(EXTRA_VALUE2_STR,
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().getString(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_INT));
    }

    /**
     * Tests ability of the deprecated setExtras method to detect changes to the extras bundle
     * and merge these changes into the telecom extras.  The old setExtras worked by just replacing
     * the entire extras bundle, so we need to ensure that we can properly handle cases where an
     * API user has added or removed items from the extras, but still gracefully merge this into the
     * extras maintained for the connection.
     *
     * @throws Exception
     */
    @MediumTest
    public void testCsSetExtras() throws Exception {
        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;

        // Set the initial bundle.
        Bundle extras = new Bundle();
        extras.putString(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        extras.putInt(EXTRA_KEY_INT, EXTRA_VALUE_INT);
        connection.setExtras(extras);
        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_INT));

        // Modify the initial bundle to add a value and remove another.
        extras.putString(EXTRA_KEY_STR2, EXTRA_VALUE2_STR);
        extras.remove(EXTRA_KEY_STR);
        connection.setExtras(extras);
        mInCallServiceFixtureX.waitForUpdate();
        assertFalse(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_INT));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras()
                        .containsKey(EXTRA_KEY_STR2));
    }

    /**
     * Tests that additions to the extras via an {@link InCallService} are propagated back down to
     * the {@link Connection}.
     *
     * @throws Exception
     */
    @MediumTest
    public void testICSPutExtras() throws Exception {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        extras.putInt(EXTRA_KEY_INT, EXTRA_VALUE_INT);

        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);
        mInCallServiceFixtureX.mInCallAdapter.putExtras(ids.mCallId, extras);
        mConnectionServiceFixtureA.waitForExtras();

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;
        assertNotNull(connection);
        Bundle connectionExtras = connection.getExtras();
        assertTrue(connectionExtras.containsKey(EXTRA_KEY_STR));
        assertEquals(EXTRA_VALUE_STR, extras.getString(EXTRA_KEY_STR));
        assertTrue(connectionExtras.containsKey(EXTRA_KEY_INT));
        assertEquals(EXTRA_VALUE_INT, extras.getInt(EXTRA_KEY_INT));
    }

    /**
     * A bi-directional test of the extras.  Tests setting extras from both the ConnectionService
     * and InCall side and ensuring the bundles are merged appropriately.
     *
     * @throws Exception
     */
    @LargeTest
    public void testExtrasBidirectional() throws Exception {
        // Get a call up and running.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA);

        Connection connection = mConnectionServiceFixtureA.mLatestConnection;

        // Set the initial bundle.
        Bundle someExtras = new Bundle();
        someExtras.putString(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        someExtras.putInt(EXTRA_KEY_INT, EXTRA_VALUE_INT);
        connection.setExtras(someExtras);
        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_INT));

        // From the InCall side, add another key
        Bundle someMoreExtras = new Bundle();
        someMoreExtras.putBoolean(EXTRA_KEY_BOOL, true);
        mInCallServiceFixtureX.mInCallAdapter.putExtras(ids.mCallId, someMoreExtras);
        mConnectionServiceFixtureA.waitForExtras();
        Bundle connectionExtras = connection.getExtras();
        assertTrue(connectionExtras.containsKey(EXTRA_KEY_STR));
        assertTrue(connectionExtras.containsKey(EXTRA_KEY_INT));
        assertTrue(connectionExtras.containsKey(EXTRA_KEY_BOOL));

        // Modify the initial bundle to add a value and remove another.
        someExtras.putString(EXTRA_KEY_STR2, EXTRA_VALUE2_STR);
        someExtras.remove(EXTRA_KEY_STR);
        connection.setExtras(someExtras);
        mInCallServiceFixtureX.waitForUpdate();
        assertFalse(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras().containsKey(EXTRA_KEY_INT));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras()
                        .containsKey(EXTRA_KEY_STR2));
        assertTrue(
                mInCallServiceFixtureX.getCall(ids.mCallId).getExtras()
                        .containsKey(EXTRA_KEY_BOOL));
    }

    /**
     * Similar to {@link #testCsSetExtras()}, tests to ensure the existing setExtras functionality
     * is maintained.
     *
     * @throws Exception
     */
    @LargeTest
    public void testConferenceSetExtras() throws Exception {
        ParcelableCall call = makeConferenceCall();
        String conferenceId = call.getId();

        Conference conference = mConnectionServiceFixtureA.mLatestConference;
        assertNotNull(conference);

        Bundle someExtras = new Bundle();
        someExtras.putString(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        someExtras.putInt(EXTRA_KEY_INT, EXTRA_VALUE_INT);
        conference.setExtras(someExtras);

        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                        .containsKey(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                        .containsKey(EXTRA_KEY_INT));

        someExtras.putString(EXTRA_KEY_STR2, EXTRA_VALUE2_STR);
        someExtras.remove(EXTRA_KEY_INT);
        conference.setExtras(someExtras);

        mInCallServiceFixtureX.waitForUpdate();
        assertTrue(
                mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                        .containsKey(EXTRA_KEY_STR));
        assertFalse(
                mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                        .containsKey(EXTRA_KEY_INT));
        assertTrue(
                mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                        .containsKey(EXTRA_KEY_STR2));
    }

    /**
     * Tests putExtras for conferences.
     *
     * @throws Exception
     */
    @LargeTest
    public void testConferenceExtraOperations() throws Exception {
        ParcelableCall call = makeConferenceCall();
        String conferenceId = call.getId();
        Conference conference = mConnectionServiceFixtureA.mLatestConference;
        assertNotNull(conference);

        conference.putExtra(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        conference.putExtra(EXTRA_KEY_INT, EXTRA_VALUE_INT);
        conference.putExtra(EXTRA_KEY_BOOL, true);
        mInCallServiceFixtureX.waitForUpdate();

        assertTrue(mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                .containsKey(EXTRA_KEY_STR));
        assertEquals(EXTRA_VALUE_STR,
                mInCallServiceFixtureX.getCall(conferenceId).getExtras().get(EXTRA_KEY_STR));
        assertTrue(
                mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                        .containsKey(EXTRA_KEY_INT));
        assertEquals(EXTRA_VALUE_INT,
                mInCallServiceFixtureX.getCall(conferenceId).getExtras().get(EXTRA_KEY_INT));
        assertEquals(true,
                mInCallServiceFixtureX.getCall(conferenceId).getExtras().get(EXTRA_KEY_BOOL));

        conference.removeExtras(new ArrayList<String>(Arrays.asList(EXTRA_KEY_STR)));
        mInCallServiceFixtureX.waitForUpdate();
        assertFalse(mInCallServiceFixtureX.getCall(conferenceId).getExtras()
                .containsKey(EXTRA_KEY_STR));
    }

    /**
     * Tests communication of extras from an InCallService to a Conference.
     *
     * @throws Exception
     */
    @LargeTest
    public void testConferenceICS() throws Exception {
        ParcelableCall call = makeConferenceCall();
        String conferenceId = call.getId();
        Conference conference = mConnectionServiceFixtureA.mLatestConference;

        Bundle someExtras = new Bundle();
        someExtras.putString(EXTRA_KEY_STR, EXTRA_VALUE_STR);
        mInCallServiceFixtureX.mInCallAdapter.putExtras(conferenceId, someExtras);
        mConnectionServiceFixtureA.waitForExtras();

        Bundle conferenceExtras = conference.getExtras();
        assertTrue(conferenceExtras.containsKey(EXTRA_KEY_STR));

        Bundle someMoreExtras = new Bundle();
        someMoreExtras.putString(EXTRA_KEY_STR2, EXTRA_VALUE_STR);
        someMoreExtras.putInt(EXTRA_KEY_INT, EXTRA_VALUE_INT);
        someMoreExtras.putBoolean(EXTRA_KEY_BOOL, true);
        mInCallServiceFixtureX.mInCallAdapter.putExtras(conferenceId, someMoreExtras);
        mConnectionServiceFixtureA.waitForExtras();
        conferenceExtras = conference.getExtras();
        assertTrue(conferenceExtras.containsKey(EXTRA_KEY_STR));
        assertTrue(conferenceExtras.containsKey(EXTRA_KEY_STR2));
        assertTrue(conferenceExtras.containsKey(EXTRA_KEY_INT));
        assertTrue(conferenceExtras.containsKey(EXTRA_KEY_BOOL));

        mInCallServiceFixtureX.mInCallAdapter.removeExtras(conferenceId,
                new ArrayList<String>(Arrays.asList(EXTRA_KEY_STR)));
        mConnectionServiceFixtureA.waitForExtras();
        conferenceExtras = conference.getExtras();
        assertFalse(conferenceExtras.containsKey(EXTRA_KEY_STR));
    }
}
