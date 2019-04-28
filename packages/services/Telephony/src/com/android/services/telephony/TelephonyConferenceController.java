/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
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

package com.android.services.telephony;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import android.net.Uri;
import android.telecom.Conference;
import android.telecom.ConferenceParticipant;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import com.android.phone.PhoneUtils;

import com.android.internal.telephony.Call;

/**
 * Maintains a list of all the known TelephonyConnections connections and controls GSM and
 * default IMS conference call behavior. This functionality is characterized by the support of
 * two top-level calls, in contrast to a CDMA conference call which automatically starts a
 * conference when there are two calls.
 */
public class TelephonyConferenceController {
    private static final int TELEPHONY_CONFERENCE_MAX_SIZE = 5;

    private final Connection.Listener mConnectionListener = new Connection.Listener() {
        @Override
        public void onStateChanged(Connection c, int state) {
            Log.v(this, "onStateChange triggered in Conf Controller : connection = "+ c
                 + " state = " + state);
            recalculate();
        }

        /** ${inheritDoc} */
        @Override
        public void onDisconnected(Connection c, DisconnectCause disconnectCause) {
            recalculate();
        }

        @Override
        public void onDestroyed(Connection connection) {
            remove(connection);
        }
    };

    /** The known connections. */
    protected final List<TelephonyConnection> mTelephonyConnections = new ArrayList<>();

    protected final TelephonyConnectionServiceProxy mConnectionService;
    protected boolean mTriggerRecalculate = false;

    public TelephonyConferenceController(TelephonyConnectionServiceProxy connectionService) {
        mConnectionService = connectionService;
    }
    /** The TelephonyConference connection object. */
    protected TelephonyConference mTelephonyConference;

    boolean shouldRecalculate() {
        Log.d(this, "shouldRecalculate is " + mTriggerRecalculate);
        return mTriggerRecalculate;
    }

    public void add(TelephonyConnection connection) {
        if (mTelephonyConnections.contains(connection)) {
            // Adding a duplicate realistically shouldn't happen.
            Log.w(this, "add - connection already tracked; connection=%s", connection);
            return;
        }
        mTelephonyConnections.add(connection);
        connection.addConnectionListener(mConnectionListener);
        recalculate();
    }

    public void remove(Connection connection) {
        if (!mTelephonyConnections.contains(connection)) {
            // Debug only since TelephonyConnectionService tries to clean up the connections tracked
            // when the original connection changes.  It does this proactively.
            Log.d(this, "remove - connection not tracked; connection=%s", connection);
            return;
        }
        connection.removeConnectionListener(mConnectionListener);
        mTelephonyConnections.remove(connection);
        recalculate();
    }

    void recalculate() {
        recalculateConference();
        recalculateConferenceable();
    }

    protected boolean isFullConference(Conference conference) {
        return conference.getConnections().size() >= TELEPHONY_CONFERENCE_MAX_SIZE;
    }

    protected boolean participatesInFullConference(Connection connection) {
        return connection.getConference() != null &&
                isFullConference(connection.getConference());
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    protected void recalculateConferenceable() {
        Log.v(this, "recalculateConferenceable : %d", mTelephonyConnections.size());
        HashSet<Connection> conferenceableConnections = new HashSet<>(mTelephonyConnections.size());

        // Loop through and collect all calls which are active or holding
        for (TelephonyConnection connection : mTelephonyConnections) {
            Log.d(this, "recalc - %s %s supportsConf? %s", connection.getState(), connection,
                    connection.isConferenceSupported());

            if (connection.isConferenceSupported() && !participatesInFullConference(connection)) {
                switch (connection.getState()) {
                    case Connection.STATE_ACTIVE:
                        //fall through
                    case Connection.STATE_HOLDING:
                        conferenceableConnections.add(connection);
                        continue;
                    default:
                        break;
                }
            }

            connection.setConferenceableConnections(Collections.<Connection>emptyList());
        }

        Log.v(this, "conferenceable: " + conferenceableConnections.size());

        // Go through all the conferenceable connections and add all other conferenceable
        // connections that is not the connection itself
        for (Connection c : conferenceableConnections) {
            List<Connection> connections = conferenceableConnections
                    .stream()
                    // Filter out this connection from the list of connections
                    .filter(connection -> c != connection)
                    .collect(Collectors.toList());
            c.setConferenceableConnections(connections);
        }

        // Set the conference as conferenceable with all of the connections that are not in the
        // conference.
        if (mTelephonyConference != null && !isFullConference(mTelephonyConference)) {
            List<Connection> nonConferencedConnections = mTelephonyConnections
                    .stream()
                    // Only retrieve Connections that are not in a conference (but support
                    // conferences).
                    .filter(c -> c.isConferenceSupported() && c.getConference() == null)
                    .collect(Collectors.toList());
            mTelephonyConference.setConferenceableConnections(nonConferencedConnections);
        }
        // TODO: Do not allow conferencing of already conferenced connections.
    }

    protected void recalculateConference() {
        Set<Connection> conferencedConnections = new HashSet<>();
        int numGsmConnections = 0;

        for (TelephonyConnection connection : mTelephonyConnections) {
            com.android.internal.telephony.Connection radioConnection =
                connection.getOriginalConnection();
            if (radioConnection != null) {
                Call.State state = radioConnection.getState();
                Call call = radioConnection.getCall();
                if ((state == Call.State.ACTIVE || state == Call.State.HOLDING) &&
                        (call != null && call.isMultiparty())) {
                    numGsmConnections++;
                    conferencedConnections.add(connection);
                }
            }
        }

        Log.d(this, "Recalculate conference calls %s %s.",
                mTelephonyConference, conferencedConnections);

        // Check if all conferenced connections are in Connection Service
        boolean allConnInService = true;
        Collection<Connection> allConnections = mConnectionService.getAllConnections();
        for (Connection connection : conferencedConnections) {
            Log.v (this, "Finding connection in Connection Service for " + connection);
            if (!allConnections.contains(connection)) {
                allConnInService = false;
                Log.v(this, "Finding connection in Connection Service Failed");
                break;
            }
        }

        Log.d(this, "Is there a match for all connections in connection service " +
            allConnInService);

        // If this is a GSM conference and the number of connections drops below 2, we will
        // terminate the conference.
        if (numGsmConnections < 2) {
            Log.d(this, "not enough connections to be a conference!");

            // No more connections are conferenced, destroy any existing conference.
            if (mTelephonyConference != null) {
                Log.d(this, "with a conference to destroy!");
                mTelephonyConference.destroy();
                mTelephonyConference = null;
            }
        } else {
            if (mTelephonyConference != null) {
                List<Connection> existingConnections = mTelephonyConference.getConnections();
                // Remove any that no longer exist
                for (Connection connection : existingConnections) {
                    if (connection instanceof TelephonyConnection &&
                            !conferencedConnections.contains(connection)) {
                        mTelephonyConference.removeConnection(connection);
                    }
                }
                if (allConnInService) {
                    mTriggerRecalculate = false;
                    // Add any new ones
                    for (Connection connection : conferencedConnections) {
                        if (!existingConnections.contains(connection)) {
                            mTelephonyConference.addConnection(connection);
                        }
                    }
                } else {
                    Log.d(this, "Trigger recalculate later");
                    mTriggerRecalculate = true;
                }
            } else {
                if (allConnInService) {
                    mTriggerRecalculate = false;

                    // Get PhoneAccount from one of the conferenced connections and use it to set
                    // the phone account on the conference.
                    PhoneAccountHandle phoneAccountHandle = null;
                    if (!conferencedConnections.isEmpty()) {
                        TelephonyConnection telephonyConnection =
                                (TelephonyConnection) conferencedConnections.iterator().next();
                        phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(
                                telephonyConnection.getPhone());
                    }

                    mTelephonyConference = new TelephonyConference(phoneAccountHandle);
                    for (Connection connection : conferencedConnections) {
                        Log.d(this, "Adding a connection to a conference call: %s %s",
                                mTelephonyConference, connection);
                        mTelephonyConference.addConnection(connection);
                    }
                    mConnectionService.addConference(mTelephonyConference);
                } else {
                    Log.d(this, "Trigger recalculate later");
                    mTriggerRecalculate = true;
                }
            }
            if (mTelephonyConference != null) {
                Connection conferencedConnection = mTelephonyConference.getPrimaryConnection();
                Log.v(this, "Primary Conferenced connection is " + conferencedConnection);
                if (conferencedConnection != null) {
                    switch (conferencedConnection.getState()) {
                        case Connection.STATE_ACTIVE:
                            Log.v(this, "Setting conference to active");
                            mTelephonyConference.setActive();
                            break;
                        case Connection.STATE_HOLDING:
                            Log.v(this, "Setting conference to hold");
                            mTelephonyConference.setOnHold();
                            break;
                    }
                }
            }
        }
    }
}
