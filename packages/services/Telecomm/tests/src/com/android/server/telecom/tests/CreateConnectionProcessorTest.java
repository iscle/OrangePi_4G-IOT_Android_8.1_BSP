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

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Binder;
import android.os.Debug;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallIdMapper;
import com.android.server.telecom.ConnectionServiceRepository;
import com.android.server.telecom.ConnectionServiceWrapper;
import com.android.server.telecom.CreateConnectionProcessor;
import com.android.server.telecom.CreateConnectionResponse;
import com.android.server.telecom.PhoneAccountRegistrar;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit testing for CreateConnectionProcessor as well as CreateConnectionTimeout classes.
 */
public class CreateConnectionProcessorTest extends TelecomTestCase {

    private static final String TEST_PACKAGE = "com.android.server.telecom.tests";
    private static final String TEST_CLASS =
            "com.android.server.telecom.tests.MockConnectionService";

    @Mock
    ConnectionServiceRepository mMockConnectionServiceRepository;
    @Mock
    PhoneAccountRegistrar mMockAccountRegistrar;
    @Mock
    CreateConnectionResponse mMockCreateConnectionResponse;
    @Mock
    Call mMockCall;

    CreateConnectionProcessor mTestCreateConnectionProcessor;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();

        mTestCreateConnectionProcessor = new CreateConnectionProcessor(mMockCall,
                mMockConnectionServiceRepository, mMockCreateConnectionResponse,
                mMockAccountRegistrar, mContext);
    }

    @Override
    public void tearDown() throws Exception {
        mTestCreateConnectionProcessor = null;
        super.tearDown();
    }

    @SmallTest
    public void testSimPhoneAccountSuccess() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // No Connection Manager in this case
        when(mMockAccountRegistrar.getSimCallManagerFromCall(any(Call.class))).thenReturn(null);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();

        mTestCreateConnectionProcessor.process();

        verify(mMockCall).setConnectionManagerPhoneAccount(eq(pAHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(pAHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    public void testbadPhoneAccount() throws Exception {
        PhoneAccountHandle pAHandle = null;
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(pAHandle);
        givePhoneAccountBindPermission(pAHandle);
        // No Connection Manager in this case
        when(mMockAccountRegistrar.getSimCallManagerFromCall(any(Call.class))).thenReturn(null);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();

        mTestCreateConnectionProcessor.process();

        verify(service, never()).createConnection(eq(mMockCall),
                any(CreateConnectionResponse.class));
        verify(mMockCreateConnectionResponse).handleCreateConnectionFailure(
                eq(new DisconnectCause(DisconnectCause.ERROR)));
    }

    @SmallTest
    public void testConnectionManagerSuccess() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // Include a Connection Manager
        PhoneAccountHandle callManagerPAHandle = getNewConnectionMangerHandle("cm_acct");
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        // Make sure the target phone account has the correct permissions
        PhoneAccount mFakeTargetPhoneAccount = makeQuickAccount("cm_acct",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(pAHandle)).thenReturn(
                mFakeTargetPhoneAccount);

        mTestCreateConnectionProcessor.process();

        verify(mMockCall).setConnectionManagerPhoneAccount(eq(callManagerPAHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(pAHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    public void testConnectionManagerFailedFallToSim() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // Include a Connection Manager
        PhoneAccountHandle callManagerPAHandle = getNewConnectionMangerHandle("cm_acct");
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        PhoneAccount mFakeTargetPhoneAccount = makeQuickAccount("cm_acct",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(pAHandle)).thenReturn(
                mFakeTargetPhoneAccount);
        when(mMockCall.getConnectionService()).thenReturn(service);
        // Put CreateConnectionProcessor in correct state to fail with ConnectionManager
        mTestCreateConnectionProcessor.process();
        reset(mMockCall);
        reset(service);

        // Notify that the ConnectionManager has denied the call.
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        when(mMockCall.getConnectionService()).thenReturn(service);
        mTestCreateConnectionProcessor.handleCreateConnectionFailure(
                new DisconnectCause(DisconnectCause.CONNECTION_MANAGER_NOT_SUPPORTED));

        // Verify that the Sim Phone Account is used correctly
        verify(mMockCall).setConnectionManagerPhoneAccount(eq(pAHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(pAHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    public void testConnectionManagerFailedDoNotFallToSim() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // Include a Connection Manager
        PhoneAccountHandle callManagerPAHandle = getNewConnectionMangerHandle("cm_acct");
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        PhoneAccount mFakeTargetPhoneAccount = makeQuickAccount("cm_acct",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(pAHandle)).thenReturn(
                mFakeTargetPhoneAccount);
        when(mMockCall.getConnectionService()).thenReturn(service);
        // Put CreateConnectionProcessor in correct state to fail with ConnectionManager
        mTestCreateConnectionProcessor.process();
        reset(mMockCall);
        reset(service);

        // Notify that the ConnectionManager has rejected the call.
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        when(mMockCall.getConnectionService()).thenReturn(service);
        when(service.isServiceValid("createConnection")).thenReturn(true);
        mTestCreateConnectionProcessor.handleCreateConnectionFailure(
                new DisconnectCause(DisconnectCause.OTHER));

        // Verify call connection rejected
        verify(mMockCreateConnectionResponse).handleCreateConnectionFailure(
                new DisconnectCause(DisconnectCause.OTHER));
    }

    @SmallTest
    public void testEmergencyCallToSim() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        // Put in a regular phone account to be sure it doesn't call that
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        // Include a Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = getNewConnectionManagerPhoneAccount("cm_acct", 0);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount = makeEmergencyPhoneAccount("tel_emer");
        PhoneAccountHandle emergencyPhoneAccountHandle = emergencyPhoneAccount.getAccountHandle();

        mTestCreateConnectionProcessor.process();

        verify(mMockCall).setConnectionManagerPhoneAccount(eq(emergencyPhoneAccountHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(emergencyPhoneAccountHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    public void testEmergencyCallSimFailToConnectionManager() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockCall.getHandle()).thenReturn(Uri.parse(""));
        // Put in a regular phone account to be sure it doesn't call that
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        when(mMockAccountRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                nullable(String.class))).thenReturn(pAHandle);
        // Include a normal Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = getNewConnectionManagerPhoneAccount("cm_acct", 0);
        // Include a connection Manager for the user with the capability to make calls
        PhoneAccount emerCallManagerPA = getNewEmergencyConnectionManagerPhoneAccount("cm_acct",
                PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount = makeEmergencyPhoneAccount("tel_emer");
        mTestCreateConnectionProcessor.process();
        reset(mMockCall);
        reset(service);
        when(mMockCall.isEmergencyCall()).thenReturn(true);

        // When Notify SIM connection fails, fall back to connection manager
        mTestCreateConnectionProcessor.handleCreateConnectionFailure(new DisconnectCause(
                DisconnectCause.REJECTED));

        verify(mMockCall).setConnectionManagerPhoneAccount(
                eq(emerCallManagerPA.getAccountHandle()));
        verify(mMockCall).setTargetPhoneAccount(eq(pAHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
    }

    private PhoneAccount makeEmergencyPhoneAccount(String id) {
        final PhoneAccount emergencyPhoneAccount = makeQuickAccount(id,
                PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        PhoneAccountHandle emergencyPhoneAccountHandle = emergencyPhoneAccount.getAccountHandle();
        givePhoneAccountBindPermission(emergencyPhoneAccountHandle);
        ArrayList<PhoneAccount> phoneAccounts = new ArrayList<PhoneAccount>() {{
            add(emergencyPhoneAccount);
        }};
        when(mMockAccountRegistrar.getAllPhoneAccountsOfCurrentUser()).thenReturn(phoneAccounts);
        return emergencyPhoneAccount;
    }

    private void givePhoneAccountBindPermission(PhoneAccountHandle handle) {
        when(mMockAccountRegistrar.phoneAccountRequiresBindPermission(eq(handle))).thenReturn(true);
    }

    private PhoneAccountHandle getNewConnectionMangerHandle(String id) {
        PhoneAccountHandle callManagerPAHandle = makeQuickAccountHandle(id);
        when(mMockAccountRegistrar.getSimCallManagerFromCall(any(Call.class))).thenReturn(
                callManagerPAHandle);
        givePhoneAccountBindPermission(callManagerPAHandle);
        return callManagerPAHandle;
    }

    private PhoneAccountHandle getNewTargetPhoneAccountHandle(String id) {
        PhoneAccountHandle pAHandle = makeQuickAccountHandle(id);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(pAHandle);
        givePhoneAccountBindPermission(pAHandle);
        return pAHandle;
    }

    private PhoneAccount getNewConnectionManagerPhoneAccount(String id, int capability) {
        PhoneAccount callManagerPA = makeQuickAccount(id, capability);
        when(mMockAccountRegistrar.getSimCallManagerFromCall(any(Call.class))).thenReturn(
                callManagerPA.getAccountHandle());
        givePhoneAccountBindPermission(callManagerPA.getAccountHandle());
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(
                callManagerPA.getAccountHandle())).thenReturn(callManagerPA);
        return callManagerPA;
    }

    private PhoneAccount getNewEmergencyConnectionManagerPhoneAccount(String id, int capability) {
        PhoneAccount callManagerPA = makeQuickAccount(id, capability);
        when(mMockAccountRegistrar.getSimCallManagerOfCurrentUser()).thenReturn(
                callManagerPA.getAccountHandle());
        givePhoneAccountBindPermission(callManagerPA.getAccountHandle());
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(
                callManagerPA.getAccountHandle())).thenReturn(callManagerPA);
        return callManagerPA;
    }

    private static ComponentName makeQuickConnectionServiceComponentName() {
        return new ComponentName(TEST_PACKAGE, TEST_CLASS);
    }

    private ConnectionServiceWrapper makeConnectionServiceWrapper() {
        ConnectionServiceWrapper wrapper = mock(ConnectionServiceWrapper.class);
        when(mMockConnectionServiceRepository.getService(
                eq(makeQuickConnectionServiceComponentName()),
                eq(Binder.getCallingUserHandle()))).thenReturn(wrapper);
        return wrapper;
    }

    private static PhoneAccountHandle makeQuickAccountHandle(String id) {
        return new PhoneAccountHandle(makeQuickConnectionServiceComponentName(), id,
                Binder.getCallingUserHandle());
    }

    private PhoneAccount.Builder makeQuickAccountBuilder(String id, int idx) {
        return new PhoneAccount.Builder(makeQuickAccountHandle(id), "label" + idx);
    }

    private PhoneAccount makeQuickAccount(String id, int idx) {
        return makeQuickAccountBuilder(id, idx)
                .setAddress(Uri.parse("http://foo.com/" + idx))
                .setSubscriptionAddress(Uri.parse("tel:555-000" + idx))
                .setCapabilities(idx)
                .setIcon(Icon.createWithResource(
                        "com.android.server.telecom.tests", R.drawable.stat_sys_phone_call))
                .setShortDescription("desc" + idx)
                .setIsEnabled(true)
                .build();
    }
}