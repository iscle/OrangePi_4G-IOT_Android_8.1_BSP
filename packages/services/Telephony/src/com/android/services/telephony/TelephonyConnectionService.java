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

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.phone.MMIDialogActivity;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for making GSM and CDMA connections.
 */
public class TelephonyConnectionService extends ConnectionService {

    // If configured, reject attempts to dial numbers matching this pattern.
    protected static final Pattern CDMA_ACTIVATION_CODE_REGEX_PATTERN =
            Pattern.compile("\\*228[0-9]{0,2}");

    protected TelephonyConnectionServiceProxy mTelephonyConnectionServiceProxy =
            new TelephonyConnectionServiceProxy() {
        @Override
        public Collection<Connection> getAllConnections() {
            return TelephonyConnectionService.this.getAllConnections();
        }
        @Override
        public void addConference(TelephonyConference mTelephonyConference) {
            TelephonyConnectionService.this.addConference(mTelephonyConference);
        }
        @Override
        public void addConference(ImsConference mImsConference) {
            TelephonyConnectionService.this.addConference(mImsConference);
        }
        @Override
        public void removeConnection(Connection connection) {
            TelephonyConnectionService.this.removeConnection(connection);
        }
        @Override
        public void addExistingConnection(PhoneAccountHandle phoneAccountHandle,
                                          Connection connection) {
            TelephonyConnectionService.this
                    .addExistingConnection(phoneAccountHandle, connection);
        }
        @Override
        public void addExistingConnection(PhoneAccountHandle phoneAccountHandle,
                Connection connection, Conference conference) {
            TelephonyConnectionService.this
                    .addExistingConnection(phoneAccountHandle, connection, conference);
        }
        @Override
        public void addConnectionToConferenceController(TelephonyConnection connection) {
            TelephonyConnectionService.this.addConnectionToConferenceController(connection);
        }
    };

    protected TelephonyConferenceController mTelephonyConferenceController =
            new TelephonyConferenceController(mTelephonyConnectionServiceProxy);
    protected final CdmaConferenceController mCdmaConferenceController =
            new CdmaConferenceController(this);
    protected ImsConferenceController mImsConferenceController =
            new ImsConferenceController(TelecomAccountRegistry.getInstance(this),
                    mTelephonyConnectionServiceProxy);

    private ComponentName mExpectedComponentName = null;
    protected EmergencyCallHelper mEmergencyCallHelper;
    protected EmergencyTonePlayer mEmergencyTonePlayer;

    // Contains one TelephonyConnection that has placed a call and a memory of which Phones it has
    // already tried to connect with. There should be only one TelephonyConnection trying to place a
    // call at one time. We also only access this cache from a TelephonyConnection that wishes to
    // redial, so we use a WeakReference that will become stale once the TelephonyConnection is
    // destroyed.
    private Pair<WeakReference<TelephonyConnection>, List<Phone>> mEmergencyRetryCache;

    /**
     * Keeps track of the status of a SIM slot.
     */
    private static class SlotStatus {
        public int slotId;
        // RAT capabilities
        public int capabilities;
        // By default, we will assume that the slots are not locked.
        public boolean isLocked = false;

        public SlotStatus(int slotId, int capabilities) {
            this.slotId = slotId;
            this.capabilities = capabilities;
        }
    }

    // SubscriptionManager Proxy interface for testing
    public interface SubscriptionManagerProxy {
        int getDefaultVoicePhoneId();
        int getSimStateForSlotIdx(int slotId);
        int getPhoneId(int subId);
    }

    private SubscriptionManagerProxy mSubscriptionManagerProxy = new SubscriptionManagerProxy() {
        @Override
        public int getDefaultVoicePhoneId() {
            return SubscriptionManager.getDefaultVoicePhoneId();
        }

        @Override
        public int getSimStateForSlotIdx(int slotId) {
            return SubscriptionManager.getSimStateForSlotIndex(slotId);
        }

        @Override
        public int getPhoneId(int subId) {
            return SubscriptionManager.getPhoneId(subId);
        }
    };

    // TelephonyManager Proxy interface for testing
    public interface TelephonyManagerProxy {
        int getPhoneCount();
        boolean hasIccCard(int slotId);
    }

    private TelephonyManagerProxy mTelephonyManagerProxy = new TelephonyManagerProxy() {
        private final TelephonyManager sTelephonyManager = TelephonyManager.getDefault();

        @Override
        public int getPhoneCount() {
            return sTelephonyManager.getPhoneCount();
        }

        @Override
        public boolean hasIccCard(int slotId) {
            return sTelephonyManager.hasIccCard(slotId);
        }
    };

    //PhoneFactory proxy interface for testing
    public interface PhoneFactoryProxy {
        Phone getPhone(int index);
        Phone getDefaultPhone();
        Phone[] getPhones();
    }

    protected PhoneFactoryProxy mPhoneFactoryProxy = new PhoneFactoryProxy() {
        @Override
        public Phone getPhone(int index) {
            return PhoneFactory.getPhone(index);
        }

        @Override
        public Phone getDefaultPhone() {
            return PhoneFactory.getDefaultPhone();
        }

        @Override
        public Phone[] getPhones() {
            return PhoneFactory.getPhones();
        }
    };

    @VisibleForTesting
    public void setSubscriptionManagerProxy(SubscriptionManagerProxy proxy) {
        mSubscriptionManagerProxy = proxy;
    }

    @VisibleForTesting
    public void setTelephonyManagerProxy(TelephonyManagerProxy proxy) {
        mTelephonyManagerProxy = proxy;
    }

    @VisibleForTesting
    public void setPhoneFactoryProxy(PhoneFactoryProxy proxy) {
        mPhoneFactoryProxy = proxy;
    }

    /**
     * A listener to actionable events specific to the TelephonyConnection.
     */
    protected final TelephonyConnection.TelephonyConnectionListener mTelephonyConnectionListener =
            new TelephonyConnection.TelephonyConnectionListener() {
        @Override
        public void onOriginalConnectionConfigured(TelephonyConnection c) {
            addConnectionToConferenceController(c);
        }

        @Override
        public void onOriginalConnectionRetry(TelephonyConnection c) {
            retryOutgoingOriginalConnection(c);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.initLogging(this);
        mExpectedComponentName = new ComponentName(this, this.getClass());
        mEmergencyTonePlayer = new EmergencyTonePlayer(this);
        TelecomAccountRegistry.getInstance(this).setTelephonyConnectionService(this);
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            final ConnectionRequest request) {
        Log.i(this, "onCreateOutgoingConnection, request: " + request);

        Uri handle = request.getAddress();
        if (handle == null) {
            Log.d(this, "onCreateOutgoingConnection, handle is null");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.NO_PHONE_NUMBER_SUPPLIED,
                            "No phone number supplied"));
        }

        String scheme = handle.getScheme();
        String number;
        if (PhoneAccount.SCHEME_VOICEMAIL.equals(scheme)) {
            // TODO: We don't check for SecurityException here (requires
            // CALL_PRIVILEGED permission).
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
            if (phone == null) {
                Log.d(this, "onCreateOutgoingConnection, phone is null");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                "Phone is null"));
            }
            number = phone.getVoiceMailNumber();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, no voicemail number set.");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.VOICEMAIL_NUMBER_MISSING,
                                "Voicemail scheme provided but no voicemail number set."));
            }

            // Convert voicemail: to tel:
            handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
        } else {
            if (!PhoneAccount.SCHEME_TEL.equals(scheme)) {
                Log.d(this, "onCreateOutgoingConnection, Handle %s is not type tel", scheme);
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Handle scheme is not type tel"));
            }

            number = handle.getSchemeSpecificPart();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, unable to parse number");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Unable to parse number"));
            }

            final Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
            if (phone != null && CDMA_ACTIVATION_CODE_REGEX_PATTERN.matcher(number).matches()) {
                // Obtain the configuration for the outgoing phone's SIM. If the outgoing number
                // matches the *228 regex pattern, fail the call. This number is used for OTASP, and
                // when dialed could lock LTE SIMs to 3G if not prohibited..
                boolean disableActivation = false;
                CarrierConfigManager cfgManager = (CarrierConfigManager)
                        phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
                if (cfgManager != null) {
                    disableActivation = cfgManager.getConfigForSubId(phone.getSubId())
                            .getBoolean(CarrierConfigManager.KEY_DISABLE_CDMA_ACTIVATION_CODE_BOOL);
                }

                if (disableActivation) {
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause
                                            .CDMA_ALREADY_ACTIVATED,
                                    "Tried to dial *228"));
                }
            }
        }

        // Convert into emergency number if necessary
        // This is required in some regions (e.g. Taiwan).
        if (!PhoneNumberUtils.isLocalEmergencyNumber(this, number)) {
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
            // We only do the conversion if the phone is not in service. The un-converted
            // emergency numbers will go to the correct destination when the phone is in-service,
            // so they will only need the special emergency call setup when the phone is out of
            // service.
            if (phone == null || phone.getServiceState().getState()
                    != ServiceState.STATE_IN_SERVICE) {
                String convertedNumber = PhoneNumberUtils.convertToEmergencyNumber(this, number);
                if (!TextUtils.equals(convertedNumber, number)) {
                    Log.i(this, "onCreateOutgoingConnection, converted to emergency number");
                    number = convertedNumber;
                    handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
                }
            }
        }
        final String numberToDial = number;

        final boolean isEmergencyNumber =
                PhoneNumberUtils.isLocalEmergencyNumber(this, numberToDial);

        final boolean isAirplaneModeOn = Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) > 0;

        if (isEmergencyNumber && (!isRadioOn() || isAirplaneModeOn)) {
            final Uri emergencyHandle = handle;
            // By default, Connection based on the default Phone, since we need to return to Telecom
            // now.
            final int defaultPhoneType = mPhoneFactoryProxy.getDefaultPhone().getPhoneType();
            final Connection emergencyConnection = getTelephonyConnection(request, numberToDial,
                    isEmergencyNumber, emergencyHandle, mPhoneFactoryProxy.getDefaultPhone());
            if (mEmergencyCallHelper == null) {
                mEmergencyCallHelper = new EmergencyCallHelper(this);
            }
            mEmergencyCallHelper.enableEmergencyCalling(new EmergencyCallStateListener.Callback() {
                @Override
                public void onComplete(EmergencyCallStateListener listener, boolean isRadioReady) {
                    // Make sure the Call has not already been canceled by the user.
                    if (emergencyConnection.getState() == Connection.STATE_DISCONNECTED) {
                        Log.i(this, "Emergency call disconnected before the outgoing call was " +
                                "placed. Skipping emergency call placement.");
                        return;
                    }
                    if (isRadioReady) {
                        // Get the right phone object since the radio has been turned on
                        // successfully.
                        final Phone phone = getPhoneForAccount(request.getAccountHandle(),
                                isEmergencyNumber);
                        // If the PhoneType of the Phone being used is different than the Default
                        // Phone, then we need create a new Connection using that PhoneType and
                        // replace it in Telecom.
                        if (phone.getPhoneType() != defaultPhoneType) {
                            Connection repConnection = getTelephonyConnection(request, numberToDial,
                                    isEmergencyNumber, emergencyHandle, phone);
                            // If there was a failure, the resulting connection will not be a
                            // TelephonyConnection, so don't place the call, just return!
                            if (repConnection instanceof TelephonyConnection) {
                                placeOutgoingConnection((TelephonyConnection) repConnection, phone,
                                        request);
                            }
                            // Notify Telecom of the new Connection type.
                            // TODO: Switch out the underlying connection instead of creating a new
                            // one and causing UI Jank.
                            addExistingConnection(PhoneUtils.makePstnPhoneAccountHandle(phone),
                                    repConnection);
                            // Remove the old connection from Telecom after.
                            emergencyConnection.setDisconnected(
                                    DisconnectCauseUtil.toTelecomDisconnectCause(
                                            android.telephony.DisconnectCause.OUTGOING_CANCELED,
                                            "Reconnecting outgoing Emergency Call."));
                            emergencyConnection.destroy();
                        } else {
                            placeOutgoingConnection((TelephonyConnection) emergencyConnection,
                                    phone, request);
                        }
                    } else {
                        Log.w(this, "onCreateOutgoingConnection, failed to turn on radio");
                        emergencyConnection.setDisconnected(
                                DisconnectCauseUtil.toTelecomDisconnectCause(
                                        android.telephony.DisconnectCause.POWER_OFF,
                                        "Failed to turn on radio."));
                        emergencyConnection.destroy();
                    }
                }
            });
            // Return the still unconnected GsmConnection and wait for the Radios to boot before
            // connecting it to the underlying Phone.
            return emergencyConnection;
        } else {
            if (!canAddCall() && !isEmergencyNumber) {
                Log.d(this, "onCreateOutgoingConnection, cannot add call .");
                return Connection.createFailedConnection(
                        new DisconnectCause(DisconnectCause.ERROR,
                                getApplicationContext().getText(
                                        R.string.incall_error_cannot_add_call),
                                getApplicationContext().getText(
                                        R.string.incall_error_cannot_add_call),
                                "Add call restricted due to ongoing video call"));
            }

            // Get the right phone object from the account data passed in.
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), isEmergencyNumber);
            Connection resultConnection = getTelephonyConnection(request, numberToDial,
                    isEmergencyNumber, handle, phone);
            // If there was a failure, the resulting connection will not be a TelephonyConnection,
            // so don't place the call!
            if(resultConnection instanceof TelephonyConnection) {
                placeOutgoingConnection((TelephonyConnection) resultConnection, phone, request);
            }
            return resultConnection;
        }
    }

    /**
     * @return {@code true} if any other call is disabling the ability to add calls, {@code false}
     *      otherwise.
     */
    protected boolean canAddCall() {
        Collection<Connection> connections = getAllConnections();
        for (Connection connection : connections) {
            if (connection.getExtras() != null &&
                    connection.getExtras().getBoolean(Connection.EXTRA_DISABLE_ADD_CALL, false)) {
                return false;
            }
        }
        return true;
    }

    protected Connection getTelephonyConnection(final ConnectionRequest request,
            final String number, boolean isEmergencyNumber, final Uri handle, Phone phone) {

        if (phone == null) {
            final Context context = getApplicationContext();
            if (context.getResources().getBoolean(R.bool.config_checkSimStateBeforeOutgoingCall)) {
                // Check SIM card state before the outgoing call.
                // Start the SIM unlock activity if PIN_REQUIRED.
                final Phone defaultPhone = mPhoneFactoryProxy.getDefaultPhone();
                final IccCard icc = defaultPhone.getIccCard();
                IccCardConstants.State simState = IccCardConstants.State.UNKNOWN;
                if (icc != null) {
                    simState = icc.getState();
                }
                if (simState == IccCardConstants.State.PIN_REQUIRED) {
                    final String simUnlockUiPackage = context.getResources().getString(
                            R.string.config_simUnlockUiPackage);
                    final String simUnlockUiClass = context.getResources().getString(
                            R.string.config_simUnlockUiClass);
                    if (simUnlockUiPackage != null && simUnlockUiClass != null) {
                        Intent simUnlockIntent = new Intent().setComponent(new ComponentName(
                                simUnlockUiPackage, simUnlockUiClass));
                        simUnlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(simUnlockIntent);
                        } catch (ActivityNotFoundException exception) {
                            Log.e(this, exception, "Unable to find SIM unlock UI activity.");
                        }
                    }
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                    "SIM_STATE_PIN_REQUIRED"));
                }
            }

            Log.d(this, "onCreateOutgoingConnection, phone is null");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUT_OF_SERVICE, "Phone is null"));
        }

        // Check both voice & data RAT to enable normal CS call,
        // when voice RAT is OOS but Data RAT is present.
        int state = phone.getServiceState().getState();
        if (state == ServiceState.STATE_OUT_OF_SERVICE) {
            int dataNetType = phone.getServiceState().getDataNetworkType();
            if (dataNetType == TelephonyManager.NETWORK_TYPE_LTE ||
                    dataNetType == TelephonyManager.NETWORK_TYPE_LTE_CA) {
                state = phone.getServiceState().getDataRegState();
            }
        }

        // If we're dialing a non-emergency number and the phone is in ECM mode, reject the call if
        // carrier configuration specifies that we cannot make non-emergency calls in ECM mode.
        if (!isEmergencyNumber && phone.isInEcm()) {
            boolean allowNonEmergencyCalls = true;
            CarrierConfigManager cfgManager = (CarrierConfigManager)
                    phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (cfgManager != null) {
                allowNonEmergencyCalls = cfgManager.getConfigForSubId(phone.getSubId())
                        .getBoolean(CarrierConfigManager.KEY_ALLOW_NON_EMERGENCY_CALLS_IN_ECM_BOOL);
            }

            if (!allowNonEmergencyCalls) {
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.CDMA_NOT_EMERGENCY,
                                "Cannot make non-emergency call in ECM mode."
                        ));
            }
        }

        if (!isEmergencyNumber) {
            switch (state) {
                case ServiceState.STATE_IN_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    break;
                case ServiceState.STATE_OUT_OF_SERVICE:
                    if (phone.isUtEnabled() && number.endsWith("#")) {
                        Log.d(this, "onCreateOutgoingConnection dial for UT");
                        break;
                    } else {
                        return Connection.createFailedConnection(
                                DisconnectCauseUtil.toTelecomDisconnectCause(
                                        android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                        "ServiceState.STATE_OUT_OF_SERVICE"));
                    }
                case ServiceState.STATE_POWER_OFF:
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.POWER_OFF,
                                    "ServiceState.STATE_POWER_OFF"));
                default:
                    Log.d(this, "onCreateOutgoingConnection, unknown service state: %d", state);
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                                    "Unknown service state " + state));
            }
        }

        final Context context = getApplicationContext();
        if (VideoProfile.isVideo(request.getVideoState()) && isTtyModeEnabled(context) &&
                !isEmergencyNumber) {
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.VIDEO_CALL_NOT_ALLOWED_WHILE_TTY_ENABLED));
        }

        // Check for additional limits on CDMA phones.
        final Connection failedConnection = checkAdditionalOutgoingCallLimits(phone);
        if (failedConnection != null) {
            return failedConnection;
        }

        // Check roaming status to see if we should block custom call forwarding codes
        if (blockCallForwardingNumberWhileRoaming(phone, number)) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.DIALED_CALL_FORWARDING_WHILE_ROAMING,
                            "Call forwarding while roaming"));
        }


        final TelephonyConnection connection =
                createConnectionFor(phone, null, true /* isOutgoing */, request.getAccountHandle(),
                        request.getTelecomCallId(), request.getAddress(), request.getVideoState());
        if (connection == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUTGOING_FAILURE,
                            "Invalid phone type"));
        }
        connection.setAddress(handle, PhoneConstants.PRESENTATION_ALLOWED);
        connection.setInitializing();
        connection.setVideoState(request.getVideoState());

        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateIncomingConnection, request: " + request);
        // If there is an incoming emergency CDMA Call (while the phone is in ECBM w/ No SIM),
        // make sure the PhoneAccount lookup retrieves the default Emergency Phone.
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        boolean isEmergency = false;
        if (accountHandle != null && PhoneUtils.EMERGENCY_ACCOUNT_HANDLE_ID.equals(
                accountHandle.getId())) {
            Log.i(this, "Emergency PhoneAccountHandle is being used for incoming call... " +
                    "Treat as an Emergency Call.");
            isEmergency = true;
        }
        Phone phone = getPhoneForAccount(accountHandle, isEmergency);
        if (phone == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED,
                            "Phone is null"));
        }

        Call call = phone.getRingingCall();
        if (!call.getState().isRinging()) {
            Log.i(this, "onCreateIncomingConnection, no ringing call");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.INCOMING_MISSED,
                            "Found no ringing call"));
        }

        com.android.internal.telephony.Connection originalConnection =
                call.getState() == Call.State.WAITING ?
                    call.getLatestConnection() : call.getEarliestConnection();
        if (isOriginalConnectionKnown(originalConnection)) {
            Log.i(this, "onCreateIncomingConnection, original connection already registered");
            return Connection.createCanceledConnection();
        }

        // We should rely on the originalConnection to get the video state.  The request coming
        // from Telecom does not know the video state of the incoming call.
        int videoState = originalConnection != null ? originalConnection.getVideoState() :
                VideoProfile.STATE_AUDIO_ONLY;

        Connection connection =
                createConnectionFor(phone, originalConnection, false /* isOutgoing */,
                        request.getAccountHandle(), request.getTelecomCallId(),
                        request.getAddress(), videoState);
        if (connection == null) {
            return Connection.createCanceledConnection();
        } else {
            return connection;
        }
    }

    /**
     * Called by the {@link ConnectionService} when a newly created {@link Connection} has been
     * added to the {@link ConnectionService} and sent to Telecom.  Here it is safe to send
     * connection events.
     *
     * @param connection the {@link Connection}.
     */
    @Override
    public void onCreateConnectionComplete(Connection connection) {
        if (connection instanceof TelephonyConnection) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
            maybeSendInternationalCallEvent(telephonyConnection);
        }
    }

    @Override
    public void triggerConferenceRecalculate() {
        if (mTelephonyConferenceController.shouldRecalculate()) {
            mTelephonyConferenceController.recalculate();
        }
    }

    @Override
    public Connection onCreateUnknownConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateUnknownConnection, request: " + request);
        // Use the registered emergency Phone if the PhoneAccountHandle is set to Telephony's
        // Emergency PhoneAccount
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        boolean isEmergency = false;
        if (accountHandle != null && PhoneUtils.EMERGENCY_ACCOUNT_HANDLE_ID.equals(
                accountHandle.getId())) {
            Log.i(this, "Emergency PhoneAccountHandle is being used for unknown call... " +
                    "Treat as an Emergency Call.");
            isEmergency = true;
        }
        Phone phone = getPhoneForAccount(accountHandle, isEmergency);
        if (phone == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED,
                            "Phone is null"));
        }
        Bundle extras = request.getExtras();

        final List<com.android.internal.telephony.Connection> allConnections = new ArrayList<>();

        // Handle the case where an unknown connection has an IMS external call ID specified; we can
        // skip the rest of the guesswork and just grad that unknown call now.
        if (phone.getImsPhone() != null && extras != null &&
                extras.containsKey(ImsExternalCallTracker.EXTRA_IMS_EXTERNAL_CALL_ID)) {

            ImsPhone imsPhone = (ImsPhone) phone.getImsPhone();
            ImsExternalCallTracker externalCallTracker = imsPhone.getExternalCallTracker();
            int externalCallId = extras.getInt(ImsExternalCallTracker.EXTRA_IMS_EXTERNAL_CALL_ID,
                    -1);

            if (externalCallTracker != null) {
                com.android.internal.telephony.Connection connection =
                        externalCallTracker.getConnectionById(externalCallId);

                if (connection != null) {
                    allConnections.add(connection);
                }
            }
        }

        if (allConnections.isEmpty()) {
            final Call ringingCall = phone.getRingingCall();
            if (ringingCall.hasConnections()) {
                allConnections.addAll(ringingCall.getConnections());
            }
            final Call foregroundCall = phone.getForegroundCall();
            if ((foregroundCall.getState() != Call.State.DISCONNECTED)
                    && (foregroundCall.hasConnections())) {
                allConnections.addAll(foregroundCall.getConnections());
            }
            if (phone.getImsPhone() != null) {
                final Call imsFgCall = phone.getImsPhone().getForegroundCall();
                if ((imsFgCall.getState() != Call.State.DISCONNECTED) && imsFgCall
                        .hasConnections()) {
                    allConnections.addAll(imsFgCall.getConnections());
                }
            }
            final Call backgroundCall = phone.getBackgroundCall();
            if (backgroundCall.hasConnections()) {
                allConnections.addAll(phone.getBackgroundCall().getConnections());
            }
        }

        com.android.internal.telephony.Connection unknownConnection = null;
        for (com.android.internal.telephony.Connection telephonyConnection : allConnections) {
            if (!isOriginalConnectionKnown(telephonyConnection)) {
                unknownConnection = telephonyConnection;
                Log.d(this, "onCreateUnknownConnection: conn = " + unknownConnection);
                break;
            }
        }

        if (unknownConnection == null) {
            Log.i(this, "onCreateUnknownConnection, did not find previously unknown connection.");
            return Connection.createCanceledConnection();
        }

        // We should rely on the originalConnection to get the video state.  The request coming
        // from Telecom does not know the video state of the unknown call.
        int videoState = unknownConnection != null ? unknownConnection.getVideoState() :
                VideoProfile.STATE_AUDIO_ONLY;

        TelephonyConnection connection =
                createConnectionFor(phone, unknownConnection,
                        !unknownConnection.isIncoming() /* isOutgoing */,
                        request.getAccountHandle(), request.getTelecomCallId(),
                        request.getAddress(), videoState);

        if (connection == null) {
            return Connection.createCanceledConnection();
        } else {
            connection.updateState();
            return connection;
        }
    }

    /**
     * Conferences two connections.
     *
     * Note: The {@link android.telecom.RemoteConnection#setConferenceableConnections(List)} API has
     * a limitation in that it can only specify conferenceables which are instances of
     * {@link android.telecom.RemoteConnection}.  In the case of an {@link ImsConference}, the
     * regular {@link Connection#setConferenceables(List)} API properly handles being able to merge
     * a {@link Conference} and a {@link Connection}.  As a result when, merging a
     * {@link android.telecom.RemoteConnection} into a {@link android.telecom.RemoteConference}
     * require merging a {@link ConferenceParticipantConnection} which is a child of the
     * {@link Conference} with a {@link TelephonyConnection}.  The
     * {@link ConferenceParticipantConnection} class does not have the capability to initiate a
     * conference merge, so we need to call
     * {@link TelephonyConnection#performConference(Connection)} on either {@code connection1} or
     * {@code connection2}, one of which is an instance of {@link TelephonyConnection}.
     *
     * @param connection1 A connection to merge into a conference call.
     * @param connection2 A connection to merge into a conference call.
     */
    @Override
    public void onConference(Connection connection1, Connection connection2) {
        if (connection1 instanceof TelephonyConnection) {
            ((TelephonyConnection) connection1).performConference(connection2);
        } else if (connection2 instanceof TelephonyConnection) {
            ((TelephonyConnection) connection2).performConference(connection1);
        } else {
            Log.w(this, "onConference - cannot merge connections " +
                    "Connection1: %s, Connection2: %2", connection1, connection2);
        }
    }

    protected boolean blockCallForwardingNumberWhileRoaming(Phone phone, String number) {
        if (phone == null || TextUtils.isEmpty(number) || !phone.getServiceState().getRoaming()) {
            return false;
        }
        String[] blockPrefixes = null;
        CarrierConfigManager cfgManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager != null) {
            blockPrefixes = cfgManager.getConfigForSubId(phone.getSubId()).getStringArray(
                    CarrierConfigManager.KEY_CALL_FORWARDING_BLOCKS_WHILE_ROAMING_STRING_ARRAY);
        }

        if (blockPrefixes != null) {
            for (String prefix : blockPrefixes) {
                if (number.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isRadioOn() {
        boolean result = false;
        for (Phone phone : mPhoneFactoryProxy.getPhones()) {
            result |= phone.isRadioOn();
        }
        return result;
    }

    private Pair<WeakReference<TelephonyConnection>, List<Phone>> makeCachedConnectionPhonePair(
            TelephonyConnection c) {
        List<Phone> phones = new ArrayList<>(Arrays.asList(mPhoneFactoryProxy.getPhones()));
        return new Pair<>(new WeakReference<>(c), phones);
    }

    // Check the mEmergencyRetryCache to see if it contains the TelephonyConnection. If it doesn't,
    // then it is stale. Create a new one!
    private void updateCachedConnectionPhonePair(TelephonyConnection c) {
        if (mEmergencyRetryCache == null) {
            Log.i(this, "updateCachedConnectionPhonePair, cache is null. Generating new cache");
            mEmergencyRetryCache = makeCachedConnectionPhonePair(c);
        } else {
            // Check to see if old cache is stale. If it is, replace it
            WeakReference<TelephonyConnection> cachedConnection = mEmergencyRetryCache.first;
            if (cachedConnection.get() != c) {
                Log.i(this, "updateCachedConnectionPhonePair, cache is stale. Regenerating.");
                mEmergencyRetryCache = makeCachedConnectionPhonePair(c);
            }
        }
    }

    /**
     * Returns the first Phone that has not been used yet to place the call. Any Phones that have
     * been used to place a call will have already been removed from mEmergencyRetryCache.second.
     * The phone that it excluded will be removed from mEmergencyRetryCache.second in this method.
     * @param phoneToExclude The Phone object that will be removed from our cache of available
     * phones.
     * @return the first Phone that is available to be used to retry the call.
     */
    private Phone getPhoneForRedial(Phone phoneToExclude) {
        List<Phone> cachedPhones = mEmergencyRetryCache.second;
        if (cachedPhones.contains(phoneToExclude)) {
            Log.i(this, "getPhoneForRedial, removing Phone[" + phoneToExclude.getPhoneId() +
                    "] from the available Phone cache.");
            cachedPhones.remove(phoneToExclude);
        }
        return cachedPhones.isEmpty() ? null : cachedPhones.get(0);
    }

    private void retryOutgoingOriginalConnection(TelephonyConnection c) {
        updateCachedConnectionPhonePair(c);
        Phone newPhoneToUse = getPhoneForRedial(c.getPhone());
        if (newPhoneToUse != null) {
            int videoState = c.getVideoState();
            Bundle connExtras = c.getExtras();
            Log.i(this, "retryOutgoingOriginalConnection, redialing on Phone Id: " + newPhoneToUse);
            c.clearOriginalConnection();
            placeOutgoingConnection(c, newPhoneToUse, videoState, connExtras);
        } else {
            // We have run out of Phones to use. Disconnect the call and destroy the connection.
            Log.i(this, "retryOutgoingOriginalConnection, no more Phones to use. Disconnecting.");
            c.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
            c.clearOriginalConnection();
            c.destroy();
        }
    }

    protected void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, ConnectionRequest request) {
        placeOutgoingConnection(connection, phone, request.getVideoState(), request.getExtras());
    }

    protected void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, int videoState, Bundle extras) {
        String number = connection.getAddress().getSchemeSpecificPart();

        com.android.internal.telephony.Connection originalConnection = null;
        try {
            if (phone != null) {
                originalConnection = phone.dial(number, null, videoState, extras);
            }
        } catch (CallStateException e) {
            Log.e(this, e, "placeOutgoingConnection, phone.dial exception: " + e);
            int cause = android.telephony.DisconnectCause.OUTGOING_FAILURE;
            if (e.getError() == CallStateException.ERROR_OUT_OF_SERVICE) {
                cause = android.telephony.DisconnectCause.OUT_OF_SERVICE;
            } else if (e.getError() == CallStateException.ERROR_POWER_OFF) {
                cause = android.telephony.DisconnectCause.POWER_OFF;
            }
            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    cause, e.getMessage()));
            return;
        }

        if (originalConnection == null) {
            int telephonyDisconnectCause = android.telephony.DisconnectCause.OUTGOING_FAILURE;
            // On GSM phones, null connection means that we dialed an MMI code
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                Log.d(this, "dialed MMI code");
                int subId = phone.getSubId();
                Log.d(this, "subId: "+subId);
                telephonyDisconnectCause = android.telephony.DisconnectCause.DIALED_MMI;
                final Intent intent = new Intent(this, MMIDialogActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                if (SubscriptionManager.isValidSubscriptionId(subId)) {
                    intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
                }
                startActivity(intent);
            }
            Log.d(this, "placeOutgoingConnection, phone.dial returned null");
            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    telephonyDisconnectCause, "Connection is null"));
        } else {
            connection.setOriginalConnection(originalConnection);
        }
    }

    protected TelephonyConnection createConnectionFor(
            Phone phone,
            com.android.internal.telephony.Connection originalConnection,
            boolean isOutgoing,
            PhoneAccountHandle phoneAccountHandle,
            String telecomCallId,
            Uri address,
            int videoState) {
        TelephonyConnection returnConnection = null;
        int phoneType = phone.getPhoneType();
        if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
            returnConnection = new GsmConnection(originalConnection, telecomCallId, isOutgoing);
        } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            boolean allowsMute = allowsMute(phone);
            returnConnection = new CdmaConnection(originalConnection, mEmergencyTonePlayer,
                    allowsMute, isOutgoing, telecomCallId);
        }
        if (returnConnection != null) {
            // Listen to Telephony specific callbacks from the connection
            returnConnection.addTelephonyConnectionListener(mTelephonyConnectionListener);
            returnConnection.setVideoPauseSupported(
                    TelecomAccountRegistry.getInstance(this).isVideoPauseSupported(
                            phoneAccountHandle));
        }
        return returnConnection;
    }

    private boolean isOriginalConnectionKnown(
            com.android.internal.telephony.Connection originalConnection) {
        for (Connection connection : getAllConnections()) {
            if (connection instanceof TelephonyConnection) {
                TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
                if (telephonyConnection.getOriginalConnection() == originalConnection) {
                    return true;
                }
            }
        }
        return false;
    }

    protected Phone getPhoneForAccount(PhoneAccountHandle accountHandle, boolean isEmergency) {
        Phone chosenPhone = null;
        int subId = PhoneUtils.getSubIdForPhoneAccountHandle(accountHandle);
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            int phoneId = mSubscriptionManagerProxy.getPhoneId(subId);
            chosenPhone = mPhoneFactoryProxy.getPhone(phoneId);
        }
        // If this is an emergency call and the phone we originally planned to make this call
        // with is not in service or was invalid, try to find one that is in service, using the
        // default as a last chance backup.
        if (isEmergency && (chosenPhone == null || ServiceState.STATE_IN_SERVICE != chosenPhone
                .getServiceState().getState())) {
            Log.d(this, "getPhoneForAccount: phone for phone acct handle %s is out of service "
                    + "or invalid for emergency call.", accountHandle);
            chosenPhone = getFirstPhoneForEmergencyCall();
            Log.d(this, "getPhoneForAccount: using subId: " +
                    (chosenPhone == null ? "null" : chosenPhone.getSubId()));
        }
        return chosenPhone;
    }

    /**
     * Retrieves the most sensible Phone to use for an emergency call using the following Priority
     *  list (for multi-SIM devices):
     *  1) The User's SIM preference for Voice calling
     *  2) The First Phone that is currently IN_SERVICE or is available for emergency calling
     *  3) If there is a PUK locked SIM, compare the SIMs that are not PUK locked. If all the SIMs
     *     are locked, skip to condition 4).
     *  4) The Phone with more Capabilities.
     *  5) The First Phone that has a SIM card in it (Starting from Slot 0...N)
     *  6) The Default Phone (Currently set as Slot 0)
     */
    @VisibleForTesting
    public Phone getFirstPhoneForEmergencyCall() {
        // 1)
        int phoneId = mSubscriptionManagerProxy.getDefaultVoicePhoneId();
        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
            Phone defaultPhone = mPhoneFactoryProxy.getPhone(phoneId);
            if (defaultPhone != null && isAvailableForEmergencyCalls(defaultPhone)) {
                return defaultPhone;
            }
        }

        Phone firstPhoneWithSim = null;
        int phoneCount = mTelephonyManagerProxy.getPhoneCount();
        List<SlotStatus> phoneSlotStatus = new ArrayList<>(phoneCount);
        for (int i = 0; i < phoneCount; i++) {
            Phone phone = mPhoneFactoryProxy.getPhone(i);
            if (phone == null) {
                continue;
            }
            // 2)
            if (isAvailableForEmergencyCalls(phone)) {
                // the slot has the radio on & state is in service.
                Log.i(this, "getFirstPhoneForEmergencyCall, radio on & in service, Phone Id:" + i);
                return phone;
            }
            // 4)
            // Store the RAF Capabilities for sorting later.
            int radioAccessFamily = phone.getRadioAccessFamily();
            SlotStatus status = new SlotStatus(i, radioAccessFamily);
            phoneSlotStatus.add(status);
            Log.i(this, "getFirstPhoneForEmergencyCall, RAF:" +
                    Integer.toHexString(radioAccessFamily) + " saved for Phone Id:" + i);
            // 3)
            // Report Slot's PIN/PUK lock status for sorting later.
            int simState = mSubscriptionManagerProxy.getSimStateForSlotIdx(i);
            if (simState == TelephonyManager.SIM_STATE_PIN_REQUIRED ||
                    simState == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
                status.isLocked = true;
            }
            // 5)
            if (firstPhoneWithSim == null && mTelephonyManagerProxy.hasIccCard(i)) {
                // The slot has a SIM card inserted, but is not in service, so keep track of this
                // Phone. Do not return because we want to make sure that none of the other Phones
                // are in service (because that is always faster).
                firstPhoneWithSim = phone;
                Log.i(this, "getFirstPhoneForEmergencyCall, SIM card inserted, Phone Id:" +
                        firstPhoneWithSim.getPhoneId());
            }
        }
        // 6)
        if (firstPhoneWithSim == null && phoneSlotStatus.isEmpty()) {
            // No Phones available, get the default.
            Log.i(this, "getFirstPhoneForEmergencyCall, return default phone");
            return mPhoneFactoryProxy.getDefaultPhone();
        } else {
            // 4)
            final int defaultPhoneId = mPhoneFactoryProxy.getDefaultPhone().getPhoneId();
            final Phone firstOccupiedSlot = firstPhoneWithSim;
            if (!phoneSlotStatus.isEmpty()) {
                // Only sort if there are enough elements to do so.
                if (phoneSlotStatus.size() > 1) {
                    Collections.sort(phoneSlotStatus, (o1, o2) -> {
                        // First start by seeing if either of the phone slots are locked. If they
                        // are, then sort by non-locked SIM first. If they are both locked, sort
                        // by capability instead.
                        if (o1.isLocked && !o2.isLocked) {
                            return -1;
                        }
                        if (o2.isLocked && !o1.isLocked) {
                            return 1;
                        }
                        // sort by number of RadioAccessFamily Capabilities.
                        int compare = Integer.bitCount(o1.capabilities) -
                                Integer.bitCount(o2.capabilities);
                        if (compare == 0) {
                            // Sort by highest RAF Capability if the number is the same.
                            compare = RadioAccessFamily.getHighestRafCapability(o1.capabilities) -
                                    RadioAccessFamily.getHighestRafCapability(o2.capabilities);
                            if (compare == 0) {
                                if (firstOccupiedSlot != null) {
                                    // If the RAF capability is the same, choose based on whether or
                                    // not any of the slots are occupied with a SIM card (if both
                                    // are, always choose the first).
                                    if (o1.slotId == firstOccupiedSlot.getPhoneId()) {
                                        return 1;
                                    } else if (o2.slotId == firstOccupiedSlot.getPhoneId()) {
                                        return -1;
                                    }
                                } else {
                                    // No slots have SIMs detected in them, so weight the default
                                    // Phone Id greater than the others.
                                    if (o1.slotId == defaultPhoneId) {
                                        return 1;
                                    } else if (o2.slotId == defaultPhoneId) {
                                        return -1;
                                    }
                                }
                            }
                        }
                        return compare;
                    });
                }
                int mostCapablePhoneId = phoneSlotStatus.get(phoneSlotStatus.size() - 1).slotId;
                Log.i(this, "getFirstPhoneForEmergencyCall, Using Phone Id: " + mostCapablePhoneId +
                        "with highest capability");
                return mPhoneFactoryProxy.getPhone(mostCapablePhoneId);
            } else {
                // 5)
                return firstPhoneWithSim;
            }
        }
    }

    /**
     * Returns true if the state of the Phone is IN_SERVICE or available for emergency calling only.
     */
    private boolean isAvailableForEmergencyCalls(Phone phone) {
        return ServiceState.STATE_IN_SERVICE == phone.getServiceState().getState() ||
                phone.getServiceState().isEmergencyOnly();
    }

    /**
     * Determines if the connection should allow mute.
     *
     * @param phone The current phone.
     * @return {@code True} if the connection should allow mute.
     */
    protected boolean allowsMute(Phone phone) {
        // For CDMA phones, check if we are in Emergency Callback Mode (ECM).  Mute is disallowed
        // in ECM mode.
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            if (phone.isInEcm()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void removeConnection(Connection connection) {
        super.removeConnection(connection);
        if (connection instanceof TelephonyConnection) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
            telephonyConnection.removeTelephonyConnectionListener(mTelephonyConnectionListener);
        }
    }

    /**
     * When a {@link TelephonyConnection} has its underlying original connection configured,
     * we need to add it to the correct conference controller.
     *
     * @param connection The connection to be added to the controller
     */
    public void addConnectionToConferenceController(TelephonyConnection connection) {
        // TODO: Need to revisit what happens when the original connection for the
        // TelephonyConnection changes.  If going from CDMA --> GSM (for example), the
        // instance of TelephonyConnection will still be a CdmaConnection, not a GsmConnection.
        // The CDMA conference controller makes the assumption that it will only have CDMA
        // connections in it, while the other conference controllers aren't as restrictive.  Really,
        // when we go between CDMA and GSM we should replace the TelephonyConnection.
        if (connection.isImsConnection()) {
            Log.d(this, "Adding IMS connection to conference controller: " + connection);
            mImsConferenceController.add(connection);
            mTelephonyConferenceController.remove(connection);
            if (connection instanceof CdmaConnection) {
                mCdmaConferenceController.remove((CdmaConnection) connection);
            }
        } else {
            int phoneType = connection.getCall().getPhone().getPhoneType();
            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                Log.d(this, "Adding GSM connection to conference controller: " + connection);
                mTelephonyConferenceController.add(connection);
                if (connection instanceof CdmaConnection) {
                    mCdmaConferenceController.remove((CdmaConnection) connection);
                }
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA &&
                    connection instanceof CdmaConnection) {
                Log.d(this, "Adding CDMA connection to conference controller: " + connection);
                mCdmaConferenceController.add((CdmaConnection) connection);
                mTelephonyConferenceController.remove(connection);
            }
            Log.d(this, "Removing connection from IMS conference controller: " + connection);
            mImsConferenceController.remove(connection);
        }
    }

    /**
     * Create a new CDMA connection. CDMA connections have additional limitations when creating
     * additional calls which are handled in this method.  Specifically, CDMA has a "FLASH" command
     * that can be used for three purposes: merging a call, swapping unmerged calls, and adding
     * a new outgoing call. The function of the flash command depends on the context of the current
     * set of calls. This method will prevent an outgoing call from being made if it is not within
     * the right circumstances to support adding a call.
     */
    protected Connection checkAdditionalOutgoingCallLimits(Phone phone) {
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            // Check to see if any CDMA conference calls exist, and if they do, check them for
            // limitations.
            for (Conference conference : getAllConferences()) {
                if (conference instanceof CdmaConference) {
                    CdmaConference cdmaConf = (CdmaConference) conference;

                    // If the CDMA conference has not been merged, add-call will not work, so fail
                    // this request to add a call.
                    if (cdmaConf.can(Connection.CAPABILITY_MERGE_CONFERENCE)) {
                        return Connection.createFailedConnection(new DisconnectCause(
                                    DisconnectCause.RESTRICTED,
                                    null,
                                    getResources().getString(R.string.callFailed_cdma_call_limit),
                                    "merge-capable call exists, prevent flash command."));
                    }
                }
            }
        }

        return null; // null means nothing went wrong, and call should continue.
    }

    protected boolean isTtyModeEnabled(Context context) {
        return (android.provider.Settings.Secure.getInt(
                context.getContentResolver(),
                android.provider.Settings.Secure.PREFERRED_TTY_MODE,
                TelecomManager.TTY_MODE_OFF) != TelecomManager.TTY_MODE_OFF);
    }

    /**
     * For outgoing dialed calls, potentially send a ConnectionEvent if the user is on WFC and is
     * dialing an international number.
     * @param telephonyConnection The connection.
     */
    private void maybeSendInternationalCallEvent(TelephonyConnection telephonyConnection) {
        if (telephonyConnection == null || telephonyConnection.getPhone() == null ||
                telephonyConnection.getPhone().getDefaultPhone() == null) {
            return;
        }
        Phone phone = telephonyConnection.getPhone().getDefaultPhone();
        if (phone instanceof GsmCdmaPhone) {
            GsmCdmaPhone gsmCdmaPhone = (GsmCdmaPhone) phone;
            if (telephonyConnection.isOutgoingCall() &&
                    gsmCdmaPhone.isNotificationOfWfcCallRequired(
                            telephonyConnection.getOriginalConnection().getOrigDialString())) {
                // Send connection event to InCall UI to inform the user of the fact they
                // are potentially placing an international call on WFC.
                Log.i(this, "placeOutgoingConnection - sending international call on WFC " +
                        "confirmation event");
                telephonyConnection.sendConnectionEvent(
                        TelephonyManager.EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC, null);
            }
        }
    }
}
