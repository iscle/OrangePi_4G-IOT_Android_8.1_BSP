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

package com.android.services.telephony;

import android.os.Looper;
import android.telecom.Conference;
import android.telecom.Connection;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests the functionality in TelephonyConferenceController.java
 * Assumption: these tests are based on setting status manually
 */

public class TelephonyConferenceControllerTest {

    @Mock
    private TelephonyConnectionServiceProxy mMockTelephonyConnectionServiceProxy;

    @Mock
    private Conference.Listener mMockListener;

    private MockTelephonyConnection mMockTelephonyConnectionA;
    private MockTelephonyConnection mMockTelephonyConnectionB;

    private TelephonyConferenceController mControllerTest;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mMockTelephonyConnectionA = new MockTelephonyConnection();
        mMockTelephonyConnectionB = new MockTelephonyConnection();

        mControllerTest = new TelephonyConferenceController(mMockTelephonyConnectionServiceProxy);
    }

    /**
     * Behavior: add telephony connections B and A to conference controller,
     *           set status for connections and calls, remove one call
     * Assumption: after performing the behaviours, the status of Connection A is STATE_ACTIVE;
     *             the status of Connection B is STATE_HOLDING;
     *             the call in the original connection is Call.State.ACTIVE;
     *             isMultiparty of the call is false;
     *             isConferenceSupported of the connection is True
     * Expected: Connection A and Connection B are conferenceable with each other
     */
    @Test
    @SmallTest
    public void testConferenceable() {

        when(mMockTelephonyConnectionA.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(false);
        when(mMockTelephonyConnectionB.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(false);

        // add telephony connection B
        mControllerTest.add(mMockTelephonyConnectionB);

        // add telephony connection A
        mControllerTest.add(mMockTelephonyConnectionA);

        mMockTelephonyConnectionA.setActive();
        mMockTelephonyConnectionB.setOnHold();

        assertTrue(mMockTelephonyConnectionA.getConferenceables()
                .contains(mMockTelephonyConnectionB));
        assertTrue(mMockTelephonyConnectionB.getConferenceables()
                .contains(mMockTelephonyConnectionA));

        // verify addConference method is never called
        verify(mMockTelephonyConnectionServiceProxy, never())
                .addConference(any(TelephonyConference.class));

        // call A removed
        mControllerTest.remove(mMockTelephonyConnectionA);
        assertFalse(mMockTelephonyConnectionB.getConferenceables()
                .contains(mMockTelephonyConnectionA));
    }

    /**
     * Behavior: add telephony connection B and A to conference controller,
     *           set status for connections and merged calls, remove one call
     * Assumption: after performing the behaviours, the status of Connection A is STATE_ACTIVE;
     *             the status of Connection B is STATE_HOLDING;
     *             the call in the original connection is Call.State.ACTIVE;
     *             isMultiparty of the call is True;
     *             isConferenceSupported of the connection is True
     * Expected: Connection A and Connection B are conferenceable with each other
     *           addConference is called
     */
    @Test
    @SmallTest
    public void testMergeMultiPartyCalls() {

        // set isMultiparty() true to create the same senario of merge behaviour
        when(mMockTelephonyConnectionA.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(true);
        when(mMockTelephonyConnectionB.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(true);

        // Add connections into connection Service
        Collection<Connection> allConnections = new ArrayList<Connection>();
        allConnections.add(mMockTelephonyConnectionA);
        allConnections.add(mMockTelephonyConnectionB);
        when(mMockTelephonyConnectionServiceProxy.getAllConnections())
                .thenReturn(allConnections);

        // add telephony connection B
        mControllerTest.add(mMockTelephonyConnectionB);

        // add telephony connection A
        mControllerTest.add(mMockTelephonyConnectionA);

        mMockTelephonyConnectionA.setActive();
        mMockTelephonyConnectionB.setOnHold();

        assertTrue(mMockTelephonyConnectionA.getConferenceables()
                .contains(mMockTelephonyConnectionB));
        assertTrue(mMockTelephonyConnectionB.getConferenceables()
                .contains(mMockTelephonyConnectionA));

        // capture the argument in the addConference method, and verify it is called
        ArgumentCaptor<TelephonyConference> argumentCaptor = ArgumentCaptor.
                forClass(TelephonyConference.class);
        verify(mMockTelephonyConnectionServiceProxy).addConference(argumentCaptor.capture());

        // add a listener to the added conference
        argumentCaptor.getValue().addListener(mMockListener);

        verify(mMockListener, never()).onDestroyed(any(Conference.class));

        // call A removed
        mControllerTest.remove(mMockTelephonyConnectionA);
        assertFalse(mMockTelephonyConnectionB.getConferenceables()
                .contains(mMockTelephonyConnectionA));

        //onDestroy should be called during the destroy
        verify(mMockListener).onDestroyed(any(Conference.class));
    }
}
