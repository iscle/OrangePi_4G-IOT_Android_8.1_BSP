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

package com.android.services.telephony.sip;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.DisconnectCause;
import android.util.Log;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.services.telephony.DisconnectCauseUtil;

import java.util.List;
import java.util.Objects;

public final class SipConnectionService extends ConnectionService {
    private interface IProfileFinderCallback {
        void onFound(SipProfile profile);
    }

    private static final String PREFIX = "[SipConnectionService] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */

    private SipProfileDb mSipProfileDb;
    private Handler mHandler;

    @Override
    public void onCreate() {
        mSipProfileDb = new SipProfileDb(this);
        mHandler = new Handler();
        super.onCreate();
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {
        if (VERBOSE) log("onCreateOutgoingConnection, request: " + request);

        Bundle extras = request.getExtras();
        if (extras != null &&
                extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE) != null) {
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.CALL_BARRED, "Cannot make a SIP call with a gateway number."));
        }

        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName sipComponentName = new ComponentName(this, SipConnectionService.class);
        if (!Objects.equals(accountHandle.getComponentName(), sipComponentName)) {
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.OUTGOING_FAILURE, "Did not match service connection"));
        }

        final SipConnection connection = new SipConnection();
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setInitializing();
        connection.onAddedToCallService();
        boolean attemptCall = true;

        if (!SipUtil.isVoipSupported(this)) {
            final CharSequence description = getString(R.string.no_voip);
            connection.setDisconnected(new android.telecom.DisconnectCause(
                    android.telecom.DisconnectCause.ERROR, null, description,
                    "VoIP unsupported"));
            attemptCall = false;
        }

        if (attemptCall && !isNetworkConnected()) {
            if (VERBOSE) log("start, network not connected, dropping call");
            final boolean wifiOnly = SipManager.isSipWifiOnly(this);
            final CharSequence description = getString(wifiOnly ? R.string.no_wifi_available
                    : R.string.no_internet_available);
            connection.setDisconnected(new android.telecom.DisconnectCause(
                    android.telecom.DisconnectCause.ERROR, null, description,
                    "Network not connected"));
            attemptCall = false;
        }

        if (attemptCall) {
            // The ID used for SIP-based phone account is the SIP profile Uri. Use it to find
            // the actual profile.
            String profileName = accountHandle.getId();
            findProfile(profileName, new IProfileFinderCallback() {
                @Override
                public void onFound(SipProfile profile) {
                    if (profile == null) {
                        connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                                DisconnectCause.OUTGOING_FAILURE, "SIP profile not found."));
                        connection.destroy();
                    } else {
                        com.android.internal.telephony.Connection chosenConnection =
                                createConnectionForProfile(profile, request);
                        if (chosenConnection == null) {
                            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                                    DisconnectCause.OUTGOING_FAILURE, "Connection failed."));
                            connection.destroy();
                        } else {
                            if (VERBOSE) log("initializing connection");
                            connection.initialize(chosenConnection);
                        }
                    }
                }
            });
        }

        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (VERBOSE) log("onCreateIncomingConnection, request: " + request);

        if (request.getExtras() == null) {
            if (VERBOSE) log("onCreateIncomingConnection, no extras");
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.ERROR_UNSPECIFIED, "No extras on request."));
        }

        Intent sipIntent = (Intent) request.getExtras().getParcelable(
                SipUtil.EXTRA_INCOMING_CALL_INTENT);
        if (sipIntent == null) {
            if (VERBOSE) log("onCreateIncomingConnection, no SIP intent");
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.ERROR_UNSPECIFIED, "No SIP intent."));
        }

        SipAudioCall sipAudioCall;
        try {
            sipAudioCall = SipManager.newInstance(this).takeAudioCall(sipIntent, null);
        } catch (SipException e) {
            log("onCreateIncomingConnection, takeAudioCall exception: " + e);
            return Connection.createCanceledConnection();
        }

        SipPhone phone = findPhoneForProfile(sipAudioCall.getLocalProfile());
        if (phone == null) {
            phone = createPhoneForProfile(sipAudioCall.getLocalProfile());
        }
        if (phone != null) {
            com.android.internal.telephony.Connection originalConnection = phone.takeIncomingCall(
                    sipAudioCall);
            if (VERBOSE) log("onCreateIncomingConnection, new connection: " + originalConnection);
            if (originalConnection != null) {
                SipConnection sipConnection = new SipConnection();
                sipConnection.initialize(originalConnection);
                sipConnection.onAddedToCallService();
                return sipConnection;
            } else {
                if (VERBOSE) log("onCreateIncomingConnection, takingIncomingCall failed");
                return Connection.createCanceledConnection();
            }
        }
        return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                DisconnectCause.ERROR_UNSPECIFIED));
    }

    private com.android.internal.telephony.Connection createConnectionForProfile(
            SipProfile profile,
            ConnectionRequest request) {
        SipPhone phone = findPhoneForProfile(profile);
        if (phone == null) {
            phone = createPhoneForProfile(profile);
        }
        if (phone != null) {
            return startCallWithPhone(phone, request);
        }
        return null;
    }

    /**
     * Searched for the specified profile in the SIP profile database.  This can take a long time
     * in communicating with the database, so it is done asynchronously with a separate thread and a
     * callback interface.
     */
    private void findProfile(final String profileName, final IProfileFinderCallback callback) {
        if (VERBOSE) log("findProfile");
        new Thread(new Runnable() {
            @Override
            public void run() {
                SipProfile profileToUse = null;
                List<SipProfile> profileList = mSipProfileDb.retrieveSipProfileList();
                if (profileList != null) {
                    for (SipProfile profile : profileList) {
                        if (Objects.equals(profileName, profile.getProfileName())) {
                            profileToUse = profile;
                            break;
                        }
                    }
                }

                final SipProfile profileFound = profileToUse;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFound(profileFound);
                    }
                });
            }
        }).start();
    }

    private SipPhone findPhoneForProfile(SipProfile profile) {
        if (VERBOSE) log("findPhoneForProfile, profile: " + profile);
        for (Connection connection : getAllConnections()) {
            if (connection instanceof SipConnection) {
                SipPhone phone = ((SipConnection) connection).getPhone();
                if (phone != null && phone.getSipUri().equals(profile.getUriString())) {
                    if (VERBOSE) log("findPhoneForProfile, found existing phone: " + phone);
                    return phone;
                }
            }
        }
        if (VERBOSE) log("findPhoneForProfile, no phone found");
        return null;
    }

    private SipPhone createPhoneForProfile(SipProfile profile) {
        if (VERBOSE) log("createPhoneForProfile, profile: " + profile);
        return PhoneFactory.makeSipPhone(profile.getUriString());
    }

    private com.android.internal.telephony.Connection startCallWithPhone(
            SipPhone phone, ConnectionRequest request) {
        String number = request.getAddress().getSchemeSpecificPart();
        if (VERBOSE) log("startCallWithPhone, number: " + number);

        try {
            com.android.internal.telephony.Connection originalConnection =
                    phone.dial(number, request.getVideoState());
            return originalConnection;
        } catch (CallStateException e) {
            log("startCallWithPhone, exception: " + e);
            return null;
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                return ni.getType() == ConnectivityManager.TYPE_WIFI ||
                        !SipManager.isSipWifiOnly(this);
            }
        }
        return false;
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
