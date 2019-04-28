/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.content.Context;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.telecom.ParcelableConnection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

// TODO: Needed for move to system service: import com.android.internal.R;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * This class creates connections to place new outgoing calls or to attach to an existing incoming
 * call. In either case, this class cycles through a set of connection services until:
 *   - a connection service returns a newly created connection in which case the call is displayed
 *     to the user
 *   - a connection service cancels the process, in which case the call is aborted
 */
@VisibleForTesting
public class CreateConnectionProcessor implements CreateConnectionResponse {

    // Describes information required to attempt to make a phone call
    private static class CallAttemptRecord {
        // The PhoneAccount describing the target connection service which we will
        // contact in order to process an attempt
        public final PhoneAccountHandle connectionManagerPhoneAccount;
        // The PhoneAccount which we will tell the target connection service to use
        // for attempting to make the actual phone call
        public final PhoneAccountHandle targetPhoneAccount;

        public CallAttemptRecord(
                PhoneAccountHandle connectionManagerPhoneAccount,
                PhoneAccountHandle targetPhoneAccount) {
            this.connectionManagerPhoneAccount = connectionManagerPhoneAccount;
            this.targetPhoneAccount = targetPhoneAccount;
        }

        @Override
        public String toString() {
            return "CallAttemptRecord("
                    + Objects.toString(connectionManagerPhoneAccount) + ","
                    + Objects.toString(targetPhoneAccount) + ")";
        }

        /**
         * Determines if this instance of {@code CallAttemptRecord} has the same underlying
         * {@code PhoneAccountHandle}s as another instance.
         *
         * @param obj The other instance to compare against.
         * @return {@code True} if the {@code CallAttemptRecord}s are equal.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CallAttemptRecord) {
                CallAttemptRecord other = (CallAttemptRecord) obj;
                return Objects.equals(connectionManagerPhoneAccount,
                        other.connectionManagerPhoneAccount) &&
                        Objects.equals(targetPhoneAccount, other.targetPhoneAccount);
            }
            return false;
        }
    }

    private final Call mCall;
    private final ConnectionServiceRepository mRepository;
    private List<CallAttemptRecord> mAttemptRecords;
    private Iterator<CallAttemptRecord> mAttemptRecordIterator;
    private CreateConnectionResponse mCallResponse;
    private DisconnectCause mLastErrorDisconnectCause;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final Context mContext;
    private CreateConnectionTimeout mTimeout;
    private ConnectionServiceWrapper mService;
    private int mConnectionAttempt;

    @VisibleForTesting
    public CreateConnectionProcessor(
            Call call, ConnectionServiceRepository repository, CreateConnectionResponse response,
            PhoneAccountRegistrar phoneAccountRegistrar, Context context) {
        Log.v(this, "CreateConnectionProcessor created for Call = %s", call);
        mCall = call;
        mRepository = repository;
        mCallResponse = response;
        mPhoneAccountRegistrar = phoneAccountRegistrar;
        mContext = context;
        mConnectionAttempt = 0;
    }

    boolean isProcessingComplete() {
        return mCallResponse == null;
    }

    boolean isCallTimedOut() {
        return mTimeout != null && mTimeout.isCallTimedOut();
    }

    public int getConnectionAttempt() {
        return mConnectionAttempt;
    }

    @VisibleForTesting
    public void process() {
        Log.v(this, "process");
        clearTimeout();
        mAttemptRecords = new ArrayList<>();
        if (mCall.getTargetPhoneAccount() != null) {
            mAttemptRecords.add(new CallAttemptRecord(
                    mCall.getTargetPhoneAccount(), mCall.getTargetPhoneAccount()));
        }
        if (!mCall.isSelfManaged()) {
            adjustAttemptsForConnectionManager();
            adjustAttemptsForEmergency(mCall.getTargetPhoneAccount());
        }
        mAttemptRecordIterator = mAttemptRecords.iterator();
        attemptNextPhoneAccount();
    }

    boolean hasMorePhoneAccounts() {
        return mAttemptRecordIterator.hasNext();
    }

    void continueProcessingIfPossible(CreateConnectionResponse response,
            DisconnectCause disconnectCause) {
        Log.v(this, "continueProcessingIfPossible");
        mCallResponse = response;
        mLastErrorDisconnectCause = disconnectCause;
        attemptNextPhoneAccount();
    }

    void abort() {
        Log.v(this, "abort");

        // Clear the response first to prevent attemptNextConnectionService from attempting any
        // more services.
        CreateConnectionResponse response = mCallResponse;
        mCallResponse = null;
        clearTimeout();

        ConnectionServiceWrapper service = mCall.getConnectionService();
        if (service != null) {
            service.abort(mCall);
            mCall.clearConnectionService();
        }
        if (response != null) {
            response.handleCreateConnectionFailure(new DisconnectCause(DisconnectCause.LOCAL));
        }
    }

    private void attemptNextPhoneAccount() {
        Log.v(this, "attemptNextPhoneAccount");
        CallAttemptRecord attempt = null;
        if (mAttemptRecordIterator.hasNext()) {
            attempt = mAttemptRecordIterator.next();

            if (!mPhoneAccountRegistrar.phoneAccountRequiresBindPermission(
                    attempt.connectionManagerPhoneAccount)) {
                Log.w(this,
                        "Connection mgr does not have BIND_TELECOM_CONNECTION_SERVICE for "
                                + "attempt: %s", attempt);
                attemptNextPhoneAccount();
                return;
            }

            // If the target PhoneAccount differs from the ConnectionManager phone acount, ensure it
            // also requires the BIND_TELECOM_CONNECTION_SERVICE permission.
            if (!attempt.connectionManagerPhoneAccount.equals(attempt.targetPhoneAccount) &&
                    !mPhoneAccountRegistrar.phoneAccountRequiresBindPermission(
                            attempt.targetPhoneAccount)) {
                Log.w(this,
                        "Target PhoneAccount does not have BIND_TELECOM_CONNECTION_SERVICE for "
                                + "attempt: %s", attempt);
                attemptNextPhoneAccount();
                return;
            }
        }

        if (mCallResponse != null && attempt != null) {
            Log.i(this, "Trying attempt %s", attempt);
            PhoneAccountHandle phoneAccount = attempt.connectionManagerPhoneAccount;
            mService = mRepository.getService(phoneAccount.getComponentName(),
                    phoneAccount.getUserHandle());
            if (mService == null) {
                Log.i(this, "Found no connection service for attempt %s", attempt);
                attemptNextPhoneAccount();
            } else {
                mConnectionAttempt++;
                mCall.setConnectionManagerPhoneAccount(attempt.connectionManagerPhoneAccount);
                mCall.setTargetPhoneAccount(attempt.targetPhoneAccount);
                mCall.setConnectionService(mService);
                setTimeoutIfNeeded(mService, attempt);

                mService.createConnection(mCall, this);
            }
        } else {
            Log.v(this, "attemptNextPhoneAccount, no more accounts, failing");
            DisconnectCause disconnectCause = mLastErrorDisconnectCause != null ?
                    mLastErrorDisconnectCause : new DisconnectCause(DisconnectCause.ERROR);
            notifyCallConnectionFailure(disconnectCause);
        }
    }

    private void setTimeoutIfNeeded(ConnectionServiceWrapper service, CallAttemptRecord attempt) {
        clearTimeout();

        CreateConnectionTimeout timeout = new CreateConnectionTimeout(
                mContext, mPhoneAccountRegistrar, service, mCall);
        if (timeout.isTimeoutNeededForCall(getConnectionServices(mAttemptRecords),
                attempt.connectionManagerPhoneAccount)) {
            mTimeout = timeout;
            timeout.registerTimeout();
        }
    }

    private void clearTimeout() {
        if (mTimeout != null) {
            mTimeout.unregisterTimeout();
            mTimeout = null;
        }
    }

    private boolean shouldSetConnectionManager() {
        if (mAttemptRecords.size() == 0) {
            return false;
        }

        if (mAttemptRecords.size() > 1) {
            Log.d(this, "shouldSetConnectionManager, error, mAttemptRecords should not have more "
                    + "than 1 record");
            return false;
        }

        PhoneAccountHandle connectionManager =
                mPhoneAccountRegistrar.getSimCallManagerFromCall(mCall);
        if (connectionManager == null) {
            return false;
        }

        PhoneAccountHandle targetPhoneAccountHandle = mAttemptRecords.get(0).targetPhoneAccount;
        if (Objects.equals(connectionManager, targetPhoneAccountHandle)) {
            return false;
        }

        // Connection managers are only allowed to manage SIM subscriptions.
        // TODO: Should this really be checking the "calling user" test for phone account?
        PhoneAccount targetPhoneAccount = mPhoneAccountRegistrar
                .getPhoneAccountUnchecked(targetPhoneAccountHandle);
        if (targetPhoneAccount == null) {
            Log.d(this, "shouldSetConnectionManager, phone account not found");
            return false;
        }
        boolean isSimSubscription = (targetPhoneAccount.getCapabilities() &
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != 0;
        if (!isSimSubscription) {
            return false;
        }

        return true;
    }

    // If there exists a registered connection manager then use it.
    private void adjustAttemptsForConnectionManager() {
        if (shouldSetConnectionManager()) {
            CallAttemptRecord record = new CallAttemptRecord(
                    mPhoneAccountRegistrar.getSimCallManagerFromCall(mCall),
                    mAttemptRecords.get(0).targetPhoneAccount);
            Log.v(this, "setConnectionManager, changing %s -> %s", mAttemptRecords.get(0), record);
            mAttemptRecords.add(0, record);
        } else {
            Log.v(this, "setConnectionManager, not changing");
        }
    }

    // If we are possibly attempting to call a local emergency number, ensure that the
    // plain PSTN connection services are listed, and nothing else.
    private void adjustAttemptsForEmergency(PhoneAccountHandle preferredPAH) {
        if (mCall.isEmergencyCall()) {
            Log.i(this, "Emergency number detected");
            mAttemptRecords.clear();
            // Phone accounts in profile do not handle emergency call, use phone accounts in
            // current user.
            List<PhoneAccount> allAccounts = mPhoneAccountRegistrar
                    .getAllPhoneAccountsOfCurrentUser();

            if (allAccounts.isEmpty()) {
                // If the list of phone accounts is empty at this point, it means Telephony hasn't
                // registered any phone accounts yet. Add a fallback emergency phone account so
                // that emergency calls can still go through. We create a new ArrayLists here just
                // in case the implementation of PhoneAccountRegistrar ever returns an unmodifiable
                // list.
                allAccounts = new ArrayList<PhoneAccount>();
                allAccounts.add(TelephonyUtil.getDefaultEmergencyPhoneAccount());
            }

            // First, possibly add the SIM phone account that the user prefers
            PhoneAccount preferredPA = mPhoneAccountRegistrar.getPhoneAccountUnchecked(
                    preferredPAH);
            if (preferredPA != null &&
                    preferredPA.hasCapabilities(PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS) &&
                    preferredPA.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                Log.i(this, "Will try PSTN account %s for emergency",
                        preferredPA.getAccountHandle());
                mAttemptRecords.add(new CallAttemptRecord(preferredPAH, preferredPAH));
            }

            // Next, add all SIM phone accounts which can place emergency calls.
            TelephonyUtil.sortSimPhoneAccounts(mContext, allAccounts);
            for (PhoneAccount phoneAccount : allAccounts) {
                if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS) &&
                        phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                    PhoneAccountHandle phoneAccountHandle = phoneAccount.getAccountHandle();
                    // Don't add the preferred account since it has already been added previously.
                    if (!phoneAccountHandle.equals(preferredPAH)) {
                        Log.i(this, "Will try PSTN account %s for emergency", phoneAccountHandle);
                        mAttemptRecords.add(new CallAttemptRecord(phoneAccountHandle,
                                phoneAccountHandle));
                    }
                }
            }

            // Next, add the connection manager account as a backup if it can place emergency calls.
            PhoneAccountHandle callManagerHandle =
                    mPhoneAccountRegistrar.getSimCallManagerOfCurrentUser();
            if (callManagerHandle != null) {
                // TODO: Should this really be checking the "calling user" test for phone account?
                PhoneAccount callManager = mPhoneAccountRegistrar
                        .getPhoneAccountUnchecked(callManagerHandle);
                if (callManager != null && callManager.hasCapabilities(
                        PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)) {
                    CallAttemptRecord callAttemptRecord = new CallAttemptRecord(callManagerHandle,
                            mPhoneAccountRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                                    mCall.getHandle().getScheme()));
                    if (!mAttemptRecords.contains(callAttemptRecord)) {
                        Log.i(this, "Will try Connection Manager account %s for emergency",
                                callManager);
                        mAttemptRecords.add(callAttemptRecord);
                    }
                }
            }
        }
    }

    /** Returns all connection services used by the call attempt records. */
    private static Collection<PhoneAccountHandle> getConnectionServices(
            List<CallAttemptRecord> records) {
        HashSet<PhoneAccountHandle> result = new HashSet<>();
        for (CallAttemptRecord record : records) {
            result.add(record.connectionManagerPhoneAccount);
        }
        return result;
    }


    private void notifyCallConnectionFailure(DisconnectCause errorDisconnectCause) {
        if (mCallResponse != null) {
            clearTimeout();
            mCallResponse.handleCreateConnectionFailure(errorDisconnectCause);
            mCallResponse = null;
            mCall.clearConnectionService();
        }
    }

    @Override
    public void handleCreateConnectionSuccess(
            CallIdMapper idMapper,
            ParcelableConnection connection) {
        if (mCallResponse == null) {
            // Nobody is listening for this connection attempt any longer; ask the responsible
            // ConnectionService to tear down any resources associated with the call
            mService.abort(mCall);
        } else {
            // Success -- share the good news and remember that we are no longer interested
            // in hearing about any more attempts
            mCallResponse.handleCreateConnectionSuccess(idMapper, connection);
            mCallResponse = null;
            // If there's a timeout running then don't clear it. The timeout can be triggered
            // after the call has successfully been created but before it has become active.
        }
    }

    private boolean shouldFailCallIfConnectionManagerFails(DisconnectCause cause) {
        // Connection Manager does not exist or does not match registered Connection Manager
        // Since Connection manager is a proxy for SIM, fall back to SIM
        PhoneAccountHandle handle = mCall.getConnectionManagerPhoneAccount();
        if (handle == null || !handle.equals(mPhoneAccountRegistrar.getSimCallManagerFromCall(
                mCall))) {
            return false;
        }

        // The Call's Connection Service does not exist
        ConnectionServiceWrapper connectionManager = mCall.getConnectionService();
        if (connectionManager == null) {
            return true;
        }

        // In this case, fall back to a sim because connection manager declined
        if (cause.getCode() == DisconnectCause.CONNECTION_MANAGER_NOT_SUPPORTED) {
            Log.d(CreateConnectionProcessor.this, "Connection manager declined to handle the "
                    + "call, falling back to not using a connection manager");
            return false;
        }

        if (!connectionManager.isServiceValid("createConnection")) {
            Log.d(CreateConnectionProcessor.this, "Connection manager unbound while trying "
                    + "create a connection, falling back to not using a connection manager");
            return false;
        }

        // Do not fall back from connection manager and simply fail call if the failure reason is
        // other
        Log.d(CreateConnectionProcessor.this, "Connection Manager denied call with the following " +
                "error: " + cause.getReason() + ". Not falling back to SIM.");
        return true;
    }

    @Override
    public void handleCreateConnectionFailure(DisconnectCause errorDisconnectCause) {
        // Failure of some sort; record the reasons for failure and try again if possible
        Log.d(CreateConnectionProcessor.this, "Connection failed: (%s)", errorDisconnectCause);
        if (shouldFailCallIfConnectionManagerFails(errorDisconnectCause)) {
            notifyCallConnectionFailure(errorDisconnectCause);
            return;
        }
        mLastErrorDisconnectCause = errorDisconnectCause;
        attemptNextPhoneAccount();
    }
}
