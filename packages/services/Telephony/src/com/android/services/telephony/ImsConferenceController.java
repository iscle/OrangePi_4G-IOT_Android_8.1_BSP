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
 * limitations under the License
 */

package com.android.services.telephony;

import com.android.ims.ImsReasonInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneUtils;

import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.Conferenceable;
import android.telecom.PhoneAccountHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages conferences for IMS connections.
 */
public class ImsConferenceController {

    /**
     * Conference listener; used to receive notification when a conference has been disconnected.
     */
    protected Conference.Listener mConferenceListener = new Conference.Listener() {
        @Override
        public void onDestroyed(Conference conference) {
            if (Log.VERBOSE) {
                Log.v(ImsConferenceController.class, "onDestroyed: %s", conference);
            }

            mImsConferences.remove(conference);
        }
    };

    /**
     * Ims conference controller connection listener.  Used to respond to changes in state of the
     * Telephony connections the controller is aware of.
     */
    protected Connection.Listener mConnectionListener = new Connection.Listener() {
        @Override
        public void onStateChanged(Connection c, int state) {
            Log.v(this, "onStateChanged: %s", Log.pii(c.getAddress()));
            recalculate();
        }

        @Override
        public void onDisconnected(Connection c, DisconnectCause disconnectCause) {
            Log.v(this, "onDisconnected: %s", Log.pii(c.getAddress()));
            recalculate();
        }

        @Override
        public void onDestroyed(Connection connection) {
            remove(connection);
        }

        @Override
        public void onConferenceStarted() {
            Log.v(this, "onConferenceStarted");
            recalculate();
        }

        @Override
        public void onConferenceSupportedChanged(Connection c, boolean isConferenceSupported) {
            Log.v(this, "onConferenceSupportedChanged");
            recalculate();
        }
    };

    /**
     * The current {@link ConnectionService}.
     */
    protected final TelephonyConnectionServiceProxy mConnectionService;

    /**
     * List of known {@link TelephonyConnection}s.
     */
    private final ArrayList<TelephonyConnection> mTelephonyConnections = new ArrayList<>();

    /**
     * List of known {@link ImsConference}s.  Realistically there will only ever be a single
     * concurrent IMS conference.
     */
    protected final ArrayList<ImsConference> mImsConferences = new ArrayList<>(1);

    protected TelecomAccountRegistry mTelecomAccountRegistry;

    /**
     * Creates a new instance of the Ims conference controller.
     *
     * @param connectionService The current connection service.
     */
    public ImsConferenceController(TelecomAccountRegistry telecomAccountRegistry,
                                   TelephonyConnectionServiceProxy connectionService) {
        mConnectionService = connectionService;
        mTelecomAccountRegistry = telecomAccountRegistry;
    }

    /**
     * Adds a new connection to the IMS conference controller.
     *
     * @param connection
     */
    public void add(TelephonyConnection connection) {
        // DO NOT add external calls; we don't want to consider them as a potential conference
        // member.
        if ((connection.getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL) ==
                Connection.PROPERTY_IS_EXTERNAL_CALL) {
            return;
        }

        if (mTelephonyConnections.contains(connection)) {
            // Adding a duplicate realistically shouldn't happen.
            Log.w(this, "add - connection already tracked; connection=%s", connection);
            return;
        }

        // Note: Wrap in Log.VERBOSE to avoid calling connection.toString if we are not going to be
        // outputting the value.
        if (Log.VERBOSE) {
            Log.v(this, "add connection %s", connection);
        }

        mTelephonyConnections.add(connection);
        connection.addConnectionListener(mConnectionListener);
        recalculateConference();
    }

    /**
     * Removes a connection from the IMS conference controller.
     *
     * @param connection
     */
    public void remove(Connection connection) {
        // External calls are not part of the conference controller, so don't remove them.
        if ((connection.getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL) ==
                Connection.PROPERTY_IS_EXTERNAL_CALL) {
            return;
        }

        if (!mTelephonyConnections.contains(connection)) {
            // Debug only since TelephonyConnectionService tries to clean up the connections tracked
            // when the original connection changes.  It does this proactively.
            Log.d(this, "remove - connection not tracked; connection=%s", connection);
            return;
        }

        if (Log.VERBOSE) {
            Log.v(this, "remove connection: %s", connection);
        }

        connection.removeConnectionListener(mConnectionListener);
        mTelephonyConnections.remove(connection);
        recalculateConferenceable();
    }

    /**
     * Triggers both a re-check of conferenceable connections, as well as checking for new
     * conferences.
     */
    protected void recalculate() {
        recalculateConferenceable();
        recalculateConference();
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    private void recalculateConferenceable() {
        Log.v(this, "recalculateConferenceable : %d", mTelephonyConnections.size());
        HashSet<Conferenceable> conferenceableSet = new HashSet<>(mTelephonyConnections.size() +
                mImsConferences.size());
        HashSet<Conferenceable> conferenceParticipantsSet = new HashSet<>();

        // Loop through and collect all calls which are active or holding
        for (TelephonyConnection connection : mTelephonyConnections) {
            if (Log.DEBUG) {
                Log.d(this, "recalc - %s %s supportsConf? %s", connection.getState(), connection,
                        connection.isConferenceSupported());
            }

            // If this connection is a member of a conference hosted on another device, it is not
            // conferenceable with any other connections.
            if (isMemberOfPeerConference(connection)) {
                if (Log.VERBOSE) {
                    Log.v(this, "Skipping connection in peer conference: %s", connection);
                }
                continue;
            }

            // If this connection does not support being in a conference call, then it is not
            // conferenceable with any other connection.
            if (!connection.isConferenceSupported()) {
                connection.setConferenceables(Collections.<Conferenceable>emptyList());
                continue;
            }

            switch (connection.getState()) {
                case Connection.STATE_ACTIVE:
                    // fall through
                case Connection.STATE_HOLDING:
                    conferenceableSet.add(connection);
                    continue;
                default:
                    break;
            }
            // This connection is not active or holding, so clear all conferencable connections
            connection.setConferenceables(Collections.<Conferenceable>emptyList());
        }
        // Also loop through all active conferences and collect the ones that are ACTIVE or HOLDING.
        for (ImsConference conference : mImsConferences) {
            if (Log.DEBUG) {
                Log.d(this, "recalc - %s %s", conference.getState(), conference);
            }

            if (!conference.isConferenceHost()) {
                if (Log.VERBOSE) {
                    Log.v(this, "skipping conference (not hosted on this device): %s", conference);
                }
                continue;
            }

            switch (conference.getState()) {
                case Connection.STATE_ACTIVE:
                    //fall through
                case Connection.STATE_HOLDING:
                    if (!conference.isFullConference()) {
                        conferenceParticipantsSet.addAll(conference.getConnections());
                        conferenceableSet.add(conference);
                    }
                    continue;
                default:
                    break;
            }
        }

        Log.v(this, "conferenceableSet size: " + conferenceableSet.size());

        for (Conferenceable c : conferenceableSet) {
            if (c instanceof Connection) {
                // Remove this connection from the Set and add all others
                List<Conferenceable> conferenceables = conferenceableSet
                        .stream()
                        .filter(conferenceable -> c != conferenceable)
                        .collect(Collectors.toList());
                // TODO: Remove this once RemoteConnection#setConferenceableConnections is fixed.
                // Add all conference participant connections as conferenceable with a standalone
                // Connection.  We need to do this to ensure that RemoteConnections work properly.
                // At the current time, a RemoteConnection will not be conferenceable with a
                // Conference, so we need to add its children to ensure the user can merge the call
                // into the conference.
                // We should add support for RemoteConnection#setConferenceables, which accepts a
                // list of remote conferences and connections in the future.
                conferenceables.addAll(conferenceParticipantsSet);

                ((Connection) c).setConferenceables(conferenceables);
            } else if (c instanceof ImsConference) {
                ImsConference imsConference = (ImsConference) c;

                // If the conference is full, don't allow anything to be conferenced with it.
                if (imsConference.isFullConference()) {
                    imsConference.setConferenceableConnections(Collections.<Connection>emptyList());
                }

                // Remove all conferences from the set, since we can not conference a conference
                // to another conference.
                List<Connection> connections = conferenceableSet
                        .stream()
                        .filter(conferenceable -> conferenceable instanceof Connection)
                        .map(conferenceable -> (Connection) conferenceable)
                        .collect(Collectors.toList());
                // Conference equivalent to setConferenceables that only accepts Connections
                imsConference.setConferenceableConnections(connections);
            }
        }
    }

    /**
     * Determines if a connection is a member of a conference hosted on another device.
     *
     * @param connection The connection.
     * @return {@code true} if the connection is a member of a conference hosted on another device.
     */
    private boolean isMemberOfPeerConference(Connection connection) {
        if (!(connection instanceof TelephonyConnection)) {
            return false;
        }
        TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
        com.android.internal.telephony.Connection originalConnection =
                telephonyConnection.getOriginalConnection();

        return originalConnection != null && originalConnection.isMultiparty() &&
                originalConnection.isMemberOfPeerConference();
    }

    /**
     * Starts a new ImsConference for a connection which just entered a multiparty state.
     */
    private void recalculateConference() {
        Log.v(this, "recalculateConference");

        Iterator<TelephonyConnection> it = mTelephonyConnections.iterator();
        while (it.hasNext()) {
            TelephonyConnection connection = it.next();
            if (connection.isImsConnection() && connection.getOriginalConnection() != null &&
                    connection.getOriginalConnection().isMultiparty()) {

                startConference(connection);
                it.remove();
            }
        }
    }

    /**
     * Starts a new {@link ImsConference} for the given IMS connection.
     * <p>
     * Creates a new IMS Conference to manage the conference represented by the connection.
     * Internally the ImsConference wraps the radio connection with a new TelephonyConnection
     * which is NOT reported to the connection service and Telecom.
     * <p>
     * Once the new IMS Conference has been created, the connection passed in is held and removed
     * from the connection service (removing it from Telecom).  The connection is put into a held
     * state to ensure that telecom removes the connection without putting it into a disconnected
     * state first.
     *
     * @param connection The connection to the Ims server.
     */
    private void startConference(TelephonyConnection connection) {
        if (Log.VERBOSE) {
            Log.v(this, "Start new ImsConference - connection: %s", connection);
        }

        // Make a clone of the connection which will become the Ims conference host connection.
        // This is necessary since the Connection Service does not support removing a connection
        // from Telecom.  Instead we create a new instance and remove the old one from telecom.
        TelephonyConnection conferenceHostConnection = connection.cloneConnection();
        conferenceHostConnection.setVideoPauseSupported(connection.getVideoPauseSupported());

        PhoneAccountHandle phoneAccountHandle = null;

        // Attempt to determine the phone account associated with the conference host connection.
        if (connection.getPhone() != null &&
                connection.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) {
            Phone imsPhone = connection.getPhone();
            // The phone account handle for an ImsPhone is based on the default phone (ie the
            // base GSM or CDMA phone, not on the ImsPhone itself).
            phoneAccountHandle =
                    PhoneUtils.makePstnPhoneAccountHandle(imsPhone.getDefaultPhone());
        }

        ImsConference conference = new ImsConference(mTelecomAccountRegistry, mConnectionService,
                conferenceHostConnection, phoneAccountHandle);
        conference.setState(conferenceHostConnection.getState());
        conference.addListener(mConferenceListener);
        conference.updateConferenceParticipantsAfterCreation();
        mConnectionService.addConference(conference);
        conferenceHostConnection.setTelecomCallId(conference.getTelecomCallId());

        // Cleanup TelephonyConnection which backed the original connection and remove from telecom.
        // Use the "Other" disconnect cause to ensure the call is logged to the call log but the
        // disconnect tone is not played.
        connection.removeConnectionListener(mConnectionListener);
        connection.clearOriginalConnection();
        connection.setDisconnected(new DisconnectCause(DisconnectCause.OTHER,
                android.telephony.DisconnectCause.toString(
                        android.telephony.DisconnectCause.IMS_MERGED_SUCCESSFULLY)));
        connection.destroy();
        mImsConferences.add(conference);
    }
}
