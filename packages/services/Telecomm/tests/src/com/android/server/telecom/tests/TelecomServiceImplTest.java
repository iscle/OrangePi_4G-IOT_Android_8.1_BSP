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

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telecom.ITelecomService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallIntentProcessor;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomServiceImpl;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.components.UserCallIntentProcessor;
import com.android.server.telecom.components.UserCallIntentProcessorFactory;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.compat.ArgumentMatcher;
import org.mockito.internal.matchers.VarargMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static android.Manifest.permission.REGISTER_SIM_SUBSCRIPTION;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TelecomServiceImplTest extends TelecomTestCase {
    public static class CallIntentProcessAdapterFake implements CallIntentProcessor.Adapter {
        @Override
        public void processOutgoingCallIntent(Context context, CallsManager callsManager,
                Intent intent) {

        }

        @Override
        public void processIncomingCallIntent(CallsManager callsManager, Intent intent) {

        }

        @Override
        public void processUnknownCallIntent(CallsManager callsManager, Intent intent) {

        }
    }

    public static class SubscriptionManagerAdapterFake
            implements TelecomServiceImpl.SubscriptionManagerAdapter {
        @Override
        public int getDefaultVoiceSubId() {
            return 0;
        }
    }

    private static class AnyStringIn extends ArgumentMatcher<String> {
        private Collection<String> mStrings;
        public AnyStringIn(Collection<String> strings) {
            this.mStrings = strings;
        }

        @Override
        public boolean matchesObject(Object string) {
            return mStrings.contains(string);
        }
    }

    private static class IntVarArgMatcher extends ArgumentMatcher<int[]> implements VarargMatcher {
        @Override
        public boolean matchesObject(Object argument) {
            return true;
        }
    }

    private ITelecomService.Stub mTSIBinder;
    private AppOpsManager mAppOpsManager;
    private UserManager mUserManager;

    @Mock private CallsManager mFakeCallsManager;
    @Mock private PhoneAccountRegistrar mFakePhoneAccountRegistrar;
    @Mock private TelecomManager mTelecomManager;
    private CallIntentProcessor.Adapter mCallIntentProcessorAdapter =
            spy(new CallIntentProcessAdapterFake());
    @Mock private DefaultDialerCache mDefaultDialerCache;
    private TelecomServiceImpl.SubscriptionManagerAdapter mSubscriptionManagerAdapter =
            spy(new SubscriptionManagerAdapterFake());
    @Mock private UserCallIntentProcessor mUserCallIntentProcessor;

    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    private static final String DEFAULT_DIALER_PACKAGE = "com.google.android.dialer";
    private static final UserHandle USER_HANDLE_16 = new UserHandle(16);
    private static final UserHandle USER_HANDLE_17 = new UserHandle(17);
    private static final PhoneAccountHandle TEL_PA_HANDLE_16 = new PhoneAccountHandle(
            new ComponentName("test", "telComponentName"), "0", USER_HANDLE_16);
    private static final PhoneAccountHandle SIP_PA_HANDLE_17 = new PhoneAccountHandle(
            new ComponentName("test", "sipComponentName"), "1", USER_HANDLE_17);
    private static final PhoneAccountHandle TEL_PA_HANDLE_CURRENT = new PhoneAccountHandle(
            new ComponentName("test", "telComponentName"), "2", Binder.getCallingUserHandle());
    private static final PhoneAccountHandle SIP_PA_HANDLE_CURRENT = new PhoneAccountHandle(
            new ComponentName("test", "sipComponentName"), "3", Binder.getCallingUserHandle());

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mComponentContextFixture.putBooleanResource(
                com.android.internal.R.bool.config_voice_capable, true);

        doReturn(mContext).when(mContext).getApplicationContext();
        doNothing().when(mContext).sendBroadcastAsUser(any(Intent.class), any(UserHandle.class),
                anyString());
        TelecomServiceImpl telecomServiceImpl = new TelecomServiceImpl(
                mContext,
                mFakeCallsManager,
                mFakePhoneAccountRegistrar,
                mCallIntentProcessorAdapter,
                new UserCallIntentProcessorFactory() {
                    @Override
                    public UserCallIntentProcessor create(Context context, UserHandle userHandle) {
                        return mUserCallIntentProcessor;
                    }
                },
                mDefaultDialerCache,
                mSubscriptionManagerAdapter,
                mLock);
        mTSIBinder = telecomServiceImpl.getBinder();
        mComponentContextFixture.setTelecomManager(mTelecomManager);
        when(mTelecomManager.getDefaultDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);
        when(mTelecomManager.getSystemDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);

        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        when(mDefaultDialerCache.getDefaultDialerApplication(anyInt()))
                .thenReturn(DEFAULT_DIALER_PACKAGE);
        when(mDefaultDialerCache.isDefaultOrSystemDialer(eq(DEFAULT_DIALER_PACKAGE), anyInt()))
                .thenReturn(true);
    }

    @SmallTest
    public void testGetDefaultOutgoingPhoneAccount() throws RemoteException {
        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("tel"), any(UserHandle.class)))
                .thenReturn(TEL_PA_HANDLE_16);
        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("sip"), any(UserHandle.class)))
                .thenReturn(SIP_PA_HANDLE_17);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);

        PhoneAccountHandle returnedHandleTel
                = mTSIBinder.getDefaultOutgoingPhoneAccount("tel", DEFAULT_DIALER_PACKAGE);
        assertEquals(TEL_PA_HANDLE_16, returnedHandleTel);

        PhoneAccountHandle returnedHandleSip
                = mTSIBinder.getDefaultOutgoingPhoneAccount("sip", DEFAULT_DIALER_PACKAGE);
        assertEquals(SIP_PA_HANDLE_17, returnedHandleSip);
    }

    @SmallTest
    public void testGetDefaultOutgoingPhoneAccountFailure() throws RemoteException {
        // make sure that the list of user profiles doesn't include anything the PhoneAccountHandles
        // are associated with

        when(mFakePhoneAccountRegistrar
                .getOutgoingPhoneAccountForScheme(eq("tel"), any(UserHandle.class)))
                .thenReturn(TEL_PA_HANDLE_16);
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_16)).thenReturn(
                makePhoneAccount(TEL_PA_HANDLE_16).build());
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_IGNORED);
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(READ_PRIVILEGED_PHONE_STATE), anyString());

        PhoneAccountHandle returnedHandleTel
                = mTSIBinder.getDefaultOutgoingPhoneAccount("tel", "");
        assertNull(returnedHandleTel);
    }

    @SmallTest
    public void testGetUserSelectedOutgoingPhoneAccount() throws RemoteException {
        when(mFakePhoneAccountRegistrar.getUserSelectedOutgoingPhoneAccount(any(UserHandle.class)))
                .thenReturn(TEL_PA_HANDLE_16);
        when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(TEL_PA_HANDLE_16)).thenReturn(
                makeMultiUserPhoneAccount(TEL_PA_HANDLE_16).build());

        PhoneAccountHandle returnedHandle
                = mTSIBinder.getUserSelectedOutgoingPhoneAccount();
        assertEquals(TEL_PA_HANDLE_16, returnedHandle);
    }

    @SmallTest
    public void testSetUserSelectedOutgoingPhoneAccount() throws RemoteException {
        mTSIBinder.setUserSelectedOutgoingPhoneAccount(TEL_PA_HANDLE_16);
        verify(mFakePhoneAccountRegistrar)
                .setUserSelectedOutgoingPhoneAccount(eq(TEL_PA_HANDLE_16), any(UserHandle.class));
    }

    @SmallTest
    public void testSetUserSelectedOutgoingPhoneAccountFailure() throws RemoteException {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                anyString(), nullable(String.class));
        try {
            mTSIBinder.setUserSelectedOutgoingPhoneAccount(TEL_PA_HANDLE_16);
        } catch (SecurityException e) {
            // desired result
        }
        verify(mFakePhoneAccountRegistrar, never())
                .setUserSelectedOutgoingPhoneAccount(
                        any(PhoneAccountHandle.class), any(UserHandle.class));
    }

    @SmallTest
    public void testGetCallCapablePhoneAccounts() throws RemoteException {
        List<PhoneAccountHandle> fullPHList = new ArrayList<PhoneAccountHandle>() {{
            add(TEL_PA_HANDLE_16);
            add(SIP_PA_HANDLE_17);
        }};

        List<PhoneAccountHandle> smallPHList = new ArrayList<PhoneAccountHandle>() {{
            add(SIP_PA_HANDLE_17);
        }};
        // Returns all phone accounts when getCallCapablePhoneAccounts is called.
        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(nullable(String.class), eq(true),
                        nullable(UserHandle.class))).thenReturn(fullPHList);
        // Returns only enabled phone accounts when getCallCapablePhoneAccounts is called.
        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(nullable(String.class), eq(false),
                        nullable(UserHandle.class))).thenReturn(smallPHList);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);

        assertEquals(fullPHList,
                mTSIBinder.getCallCapablePhoneAccounts(true, DEFAULT_DIALER_PACKAGE));
        assertEquals(smallPHList,
                mTSIBinder.getCallCapablePhoneAccounts(false, DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testGetCallCapablePhoneAccountsFailure() throws RemoteException {
        List<String> enforcedPermissions = new ArrayList<String>() {{
            add(READ_PHONE_STATE);
            add(READ_PRIVILEGED_PHONE_STATE);
        }};
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                argThat(new AnyStringIn(enforcedPermissions)), anyString());

        List<PhoneAccountHandle> result = null;
        try {
            result = mTSIBinder.getCallCapablePhoneAccounts(true, "");
        } catch (SecurityException e) {
            // intended behavior
        }
        assertNull(result);
        verify(mFakePhoneAccountRegistrar, never())
                .getCallCapablePhoneAccounts(anyString(), anyBoolean(), any(UserHandle.class));
    }

    @SmallTest
    public void testGetPhoneAccountsSupportingScheme() throws RemoteException {
        List<PhoneAccountHandle> sipPHList = new ArrayList<PhoneAccountHandle>() {{
            add(SIP_PA_HANDLE_17);
        }};

        List<PhoneAccountHandle> telPHList = new ArrayList<PhoneAccountHandle>() {{
            add(TEL_PA_HANDLE_16);
        }};
        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(eq("tel"), anyBoolean(), any(UserHandle.class)))
                .thenReturn(telPHList);
        when(mFakePhoneAccountRegistrar
                .getCallCapablePhoneAccounts(eq("sip"), anyBoolean(), any(UserHandle.class)))
                .thenReturn(sipPHList);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);

        assertEquals(telPHList,
                mTSIBinder.getPhoneAccountsSupportingScheme("tel", DEFAULT_DIALER_PACKAGE));
        assertEquals(sipPHList,
                mTSIBinder.getPhoneAccountsSupportingScheme("sip", DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testGetPhoneAccountsForPackage() throws RemoteException {
        List<PhoneAccountHandle> phoneAccountHandleList = new ArrayList<PhoneAccountHandle>() {{
            add(TEL_PA_HANDLE_16);
            add(SIP_PA_HANDLE_17);
        }};
        when(mFakePhoneAccountRegistrar
                .getPhoneAccountsForPackage(anyString(), any(UserHandle.class)))
                .thenReturn(phoneAccountHandleList);
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        assertEquals(phoneAccountHandleList,
                mTSIBinder.getPhoneAccountsForPackage(
                        TEL_PA_HANDLE_16.getComponentName().getPackageName()));
    }

    @SmallTest
    public void testGetPhoneAccount() throws RemoteException {
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_16, SIP_PA_HANDLE_17);
        assertEquals(TEL_PA_HANDLE_16, mTSIBinder.getPhoneAccount(TEL_PA_HANDLE_16)
                .getAccountHandle());
        assertEquals(SIP_PA_HANDLE_17, mTSIBinder.getPhoneAccount(SIP_PA_HANDLE_17)
                .getAccountHandle());
    }

    @SmallTest
    public void testGetAllPhoneAccounts() throws RemoteException {
        List<PhoneAccount> phoneAccountList = new ArrayList<PhoneAccount>() {{
            add(makePhoneAccount(TEL_PA_HANDLE_16).build());
            add(makePhoneAccount(SIP_PA_HANDLE_17).build());
        }};
        when(mFakePhoneAccountRegistrar.getAllPhoneAccounts(any(UserHandle.class)))
                .thenReturn(phoneAccountList);

        assertEquals(2, mTSIBinder.getAllPhoneAccounts().size());
    }

    @SmallTest
    public void testRegisterPhoneAccount() throws RemoteException {
        String packageNameToUse = "com.android.officialpackage";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "test", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).build();
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        registerPhoneAccountTestHelper(phoneAccount, true);
    }

    @SmallTest
    public void testRegisterPhoneAccountWithoutModifyPermission() throws RemoteException {
        // tests the case where the package does not have MODIFY_PHONE_STATE but is
        // registering its own phone account as a third-party connection service
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).build();

        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        PackageManager pm = mContext.getPackageManager();
        when(pm.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)).thenReturn(true);

        registerPhoneAccountTestHelper(phoneAccount, true);
    }

    @SmallTest
    public void testRegisterPhoneAccountWithoutModifyPermissionFailure() throws RemoteException {
        // tests the case where the third party package should not be allowed to register a phone
        // account due to the lack of modify permission.
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle).build();

        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        PackageManager pm = mContext.getPackageManager();
        when(pm.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)).thenReturn(false);

        registerPhoneAccountTestHelper(phoneAccount, false);
    }

    @SmallTest
    public void testRegisterPhoneAccountWithoutSimSubscriptionPermissionFailure()
            throws RemoteException {
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makePhoneAccount(phHandle)
                .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION).build();

        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        doThrow(new SecurityException())
                .when(mContext)
                .enforceCallingOrSelfPermission(eq(REGISTER_SIM_SUBSCRIPTION),
                        nullable(String.class));

        registerPhoneAccountTestHelper(phoneAccount, false);
    }

    @SmallTest
    public void testRegisterPhoneAccountWithoutMultiUserPermissionFailure()
            throws Exception {
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());
        PhoneAccount phoneAccount = makeMultiUserPhoneAccount(phHandle).build();

        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        PackageManager packageManager = mContext.getPackageManager();
        when(packageManager.getApplicationInfo(packageNameToUse, PackageManager.GET_META_DATA))
                .thenReturn(new ApplicationInfo());

        registerPhoneAccountTestHelper(phoneAccount, false);
    }

    private void registerPhoneAccountTestHelper(PhoneAccount testPhoneAccount,
            boolean shouldSucceed) throws RemoteException {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        boolean didExceptionOccur = false;
        try {
            mTSIBinder.registerPhoneAccount(testPhoneAccount);
        } catch (Exception e) {
            didExceptionOccur = true;
        }

        if (shouldSucceed) {
            assertFalse(didExceptionOccur);
            verify(mFakePhoneAccountRegistrar).registerPhoneAccount(testPhoneAccount);
        } else {
            assertTrue(didExceptionOccur);
            verify(mFakePhoneAccountRegistrar, never())
                    .registerPhoneAccount(any(PhoneAccount.class));
        }
    }

    @SmallTest
    public void testUnregisterPhoneAccount() throws RemoteException {
        String packageNameToUse = "com.android.officialpackage";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "test", Binder.getCallingUserHandle());

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        mTSIBinder.unregisterPhoneAccount(phHandle);
        verify(mFakePhoneAccountRegistrar).unregisterPhoneAccount(phHandle);
    }

    @SmallTest
    public void testUnregisterPhoneAccountFailure() throws RemoteException {
        String packageNameToUse = "com.thirdparty.connectionservice";
        PhoneAccountHandle phHandle = new PhoneAccountHandle(new ComponentName(
                packageNameToUse, "cs"), "asdf", Binder.getCallingUserHandle());

        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingOrSelfPermission(MODIFY_PHONE_STATE);
        PackageManager pm = mContext.getPackageManager();
        when(pm.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)).thenReturn(false);

        try {
            mTSIBinder.unregisterPhoneAccount(phHandle);
        } catch (UnsupportedOperationException e) {
            // expected behavior
        }
        verify(mFakePhoneAccountRegistrar, never())
                .unregisterPhoneAccount(any(PhoneAccountHandle.class));
        verify(mContext, never())
                .sendBroadcastAsUser(any(Intent.class), any(UserHandle.class), anyString());
    }

    @SmallTest
    public void testAddNewIncomingCall() throws Exception {
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_CURRENT, extras);

        addCallTestHelper(TelecomManager.ACTION_INCOMING_CALL,
                CallIntentProcessor.KEY_IS_INCOMING_CALL, extras, false);
    }

    @SmallTest
    public void testAddNewIncomingCallFailure() throws Exception {
        try {
            mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_16, null);
        } catch (SecurityException e) {
            // expected
        }

        doThrow(new SecurityException()).when(mAppOpsManager).checkPackage(anyInt(), anyString());

        try {
            mTSIBinder.addNewIncomingCall(TEL_PA_HANDLE_CURRENT, null);
        } catch (SecurityException e) {
            // expected
        }

        // Verify that neither of these attempts got through
        verify(mCallIntentProcessorAdapter, never())
                .processIncomingCallIntent(any(CallsManager.class), any(Intent.class));
    }

    @SmallTest
    public void testAddNewUnknownCall() throws Exception {
        PhoneAccount phoneAccount = makePhoneAccount(TEL_PA_HANDLE_CURRENT).build();
        phoneAccount.setIsEnabled(true);
        doReturn(phoneAccount).when(mFakePhoneAccountRegistrar).getPhoneAccount(
                eq(TEL_PA_HANDLE_CURRENT), any(UserHandle.class));
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), anyString());
        Bundle extras = createSampleExtras();

        mTSIBinder.addNewUnknownCall(TEL_PA_HANDLE_CURRENT, extras);

        addCallTestHelper(TelecomManager.ACTION_NEW_UNKNOWN_CALL,
                CallIntentProcessor.KEY_IS_UNKNOWN_CALL, extras, true);
    }

    @SmallTest
    public void testAddNewUnknownCallFailure() throws Exception {
        try {
            mTSIBinder.addNewUnknownCall(TEL_PA_HANDLE_16, null);
        } catch (SecurityException e) {
            // expected
        }

        doThrow(new SecurityException()).when(mAppOpsManager).checkPackage(anyInt(), anyString());

        try {
            mTSIBinder.addNewUnknownCall(TEL_PA_HANDLE_CURRENT, null);
        } catch (SecurityException e) {
            // expected
        }

        // Verify that neither of these attempts got through
        verify(mCallIntentProcessorAdapter, never())
                .processIncomingCallIntent(any(CallsManager.class), any(Intent.class));
    }

    private void addCallTestHelper(String expectedAction, String extraCallKey,
            Bundle expectedExtras, boolean isUnknown) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        if (isUnknown) {
            verify(mCallIntentProcessorAdapter).processUnknownCallIntent(any(CallsManager.class),
                    intentCaptor.capture());
        } else {
            verify(mCallIntentProcessorAdapter).processIncomingCallIntent(any(CallsManager.class),
                    intentCaptor.capture());
        }
        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(expectedAction, capturedIntent.getAction());
        Bundle intentExtras = capturedIntent.getExtras();
        assertEquals(TEL_PA_HANDLE_CURRENT,
                intentExtras.get(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertTrue(intentExtras.getBoolean(extraCallKey));

        if (isUnknown) {
            for (String expectedKey : expectedExtras.keySet()) {
                assertTrue(intentExtras.containsKey(expectedKey));
                assertEquals(expectedExtras.get(expectedKey), intentExtras.get(expectedKey));
            }
        }
        else {
            assertTrue(areBundlesEqual(expectedExtras,
                    (Bundle) intentExtras.get(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)));
        }
    }

    @SmallTest
    public void testPlaceCallWithNonEmergencyPermission() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingPermission(CALL_PHONE);

        mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE);
        placeCallTestHelper(handle, extras, true);
    }

    @SmallTest
    public void testPlaceCallWithAppOpsOff() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_IGNORED);
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext).checkCallingPermission(CALL_PHONE);

        mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE);
        placeCallTestHelper(handle, extras, false);
    }

    @SmallTest
    public void testPlaceCallWithNoCallingPermission() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_CALL_PHONE), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mContext).checkCallingPermission(CALL_PHONE);

        mTSIBinder.placeCall(handle, extras, DEFAULT_DIALER_PACKAGE);
        placeCallTestHelper(handle, extras, false);
    }

    private void placeCallTestHelper(Uri expectedHandle, Bundle expectedExtras,
            boolean shouldNonEmergencyBeAllowed) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mUserCallIntentProcessor).processIntent(intentCaptor.capture(), anyString(),
                eq(shouldNonEmergencyBeAllowed));
        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(Intent.ACTION_CALL, capturedIntent.getAction());
        assertEquals(expectedHandle, capturedIntent.getData());
        assertTrue(areBundlesEqual(expectedExtras, capturedIntent.getExtras()));
    }

    @SmallTest
    public void testPlaceCallFailure() throws Exception {
        Uri handle = Uri.parse("tel:6505551234");
        Bundle extras = createSampleExtras();

        doThrow(new SecurityException())
                .when(mContext).enforceCallingOrSelfPermission(eq(CALL_PHONE), anyString());

        try {
            mTSIBinder.placeCall(handle, extras, "arbitrary_package_name");
        } catch (SecurityException e) {
            // expected
        }

        verify(mUserCallIntentProcessor, never())
                .processIntent(any(Intent.class), anyString(), anyBoolean());
    }

    @SmallTest
    public void testSetDefaultDialer() throws Exception {
        String packageName = "sample.package";
        int currentUser = ActivityManager.getCurrentUser();

        when(mDefaultDialerCache.setDefaultDialer(eq(packageName), eq(currentUser)))
                .thenReturn(true);

        mTSIBinder.setDefaultDialer(packageName);

        verify(mDefaultDialerCache).setDefaultDialer(eq(packageName), eq(currentUser));
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(intentCaptor.capture(), any(UserHandle.class));
        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED, capturedIntent.getAction());
        String packageNameExtra = capturedIntent.getStringExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME);
        assertEquals(packageName, packageNameExtra);
    }

    @SmallTest
    public void testSetDefaultDialerNoModifyPhoneStatePermission() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(MODIFY_PHONE_STATE), nullable(String.class));
        setDefaultDialerFailureTestHelper();
    }

    @SmallTest
    public void testSetDefaultDialerNoWriteSecureSettingsPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(WRITE_SECURE_SETTINGS), nullable(String.class));
        setDefaultDialerFailureTestHelper();
    }

    private void setDefaultDialerFailureTestHelper() throws Exception {
        boolean exceptionThrown = false;
        try {
            mTSIBinder.setDefaultDialer(DEFAULT_DIALER_PACKAGE);
        } catch (SecurityException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        verify(mDefaultDialerCache, never()).setDefaultDialer(anyString(), anyInt());
        verify(mContext, never()).sendBroadcastAsUser(any(Intent.class), any(UserHandle.class));
    }

    @SmallTest
    public void testIsVoicemailNumber() throws Exception {
        String vmNumber = "010";
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_CURRENT);

        doReturn(true).when(mFakePhoneAccountRegistrar).isVoiceMailNumber(TEL_PA_HANDLE_CURRENT,
                vmNumber);
        assertTrue(mTSIBinder.isVoiceMailNumber(TEL_PA_HANDLE_CURRENT,
                vmNumber, DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testIsVoicemailNumberAccountNotVisibleFailure() throws Exception {
        String vmNumber = "010";

        doReturn(true).when(mFakePhoneAccountRegistrar).isVoiceMailNumber(TEL_PA_HANDLE_CURRENT,
                vmNumber);

        when(mFakePhoneAccountRegistrar.getPhoneAccount(TEL_PA_HANDLE_CURRENT,
                Binder.getCallingUserHandle())).thenReturn(null);
        assertFalse(mTSIBinder
                .isVoiceMailNumber(TEL_PA_HANDLE_CURRENT, vmNumber, DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testGetVoicemailNumberWithNullAccountHandle() throws Exception {
        when(mFakePhoneAccountRegistrar.getPhoneAccount(isNull(PhoneAccountHandle.class),
                eq(Binder.getCallingUserHandle())))
                .thenReturn(makePhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        int subId = 58374;
        String vmNumber = "543";
        doReturn(subId).when(mSubscriptionManagerAdapter).getDefaultVoiceSubId();

        TelephonyManager mockTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        when(mockTelephonyManager.getVoiceMailNumber(subId)).thenReturn(vmNumber);

        assertEquals(vmNumber, mTSIBinder.getVoiceMailNumber(null, DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testGetVoicemailNumberWithNonNullAccountHandle() throws Exception {
        when(mFakePhoneAccountRegistrar.getPhoneAccount(eq(TEL_PA_HANDLE_CURRENT),
                eq(Binder.getCallingUserHandle())))
                .thenReturn(makePhoneAccount(TEL_PA_HANDLE_CURRENT).build());
        int subId = 58374;
        String vmNumber = "543";

        TelephonyManager mockTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        when(mockTelephonyManager.getVoiceMailNumber(subId)).thenReturn(vmNumber);
        when(mFakePhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(TEL_PA_HANDLE_CURRENT))
                .thenReturn(subId);

        assertEquals(vmNumber,
                mTSIBinder.getVoiceMailNumber(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testGetLine1Number() throws Exception {
        int subId = 58374;
        String line1Number = "9482752023479";
        makeAccountsVisibleToAllUsers(TEL_PA_HANDLE_CURRENT);
        when(mFakePhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(TEL_PA_HANDLE_CURRENT))
                .thenReturn(subId);
        TelephonyManager mockTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        when(mockTelephonyManager.getLine1Number(subId)).thenReturn(line1Number);

        assertEquals(line1Number,
                mTSIBinder.getLine1Number(TEL_PA_HANDLE_CURRENT, DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testEndCallWithRingingForegroundCall() throws Exception {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.RINGING);
        when(mFakeCallsManager.getForegroundCall()).thenReturn(call);
        assertTrue(mTSIBinder.endCall());
        verify(call).reject(false, null);
    }

    @SmallTest
    public void testEndCallWithNonRingingForegroundCall() throws Exception {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        when(mFakeCallsManager.getForegroundCall()).thenReturn(call);
        assertTrue(mTSIBinder.endCall());
        verify(call).disconnect();
    }

    @SmallTest
    public void testEndCallWithNoForegroundCall() throws Exception {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        when(mFakeCallsManager.getFirstCallWithState(argThat(new IntVarArgMatcher())))
                .thenReturn(call);
        assertTrue(mTSIBinder.endCall());
        verify(call).disconnect();
    }

    @SmallTest
    public void testEndCallWithNoCalls() throws Exception {
        assertFalse(mTSIBinder.endCall());
    }

    @SmallTest
    public void testAcceptRingingCall() throws Exception {
        Call call = mock(Call.class);
        when(mFakeCallsManager.getFirstCallWithState(anyInt())).thenReturn(call);
        // Not intended to be a real video state. Here to ensure that the call will be answered
        // with whatever video state it's currently in.
        int fakeVideoState = 29578215;
        when(call.getVideoState()).thenReturn(fakeVideoState);
        mTSIBinder.acceptRingingCall("");
        verify(call).answer(eq(fakeVideoState));
    }

    @SmallTest
    public void testAcceptRingingCallWithValidVideoState() throws Exception {
        Call call = mock(Call.class);
        when(mFakeCallsManager.getFirstCallWithState(anyInt())).thenReturn(call);
        // Not intended to be a real video state. Here to ensure that the call will be answered
        // with the video state passed in to acceptRingingCallWithVideoState
        int fakeVideoState = 29578215;
        int realVideoState = VideoProfile.STATE_RX_ENABLED | VideoProfile.STATE_TX_ENABLED;
        when(call.getVideoState()).thenReturn(fakeVideoState);
        mTSIBinder.acceptRingingCallWithVideoState("", realVideoState);
        verify(call).answer(realVideoState);
    }

    @SmallTest
    public void testIsInCall() throws Exception {
        when(mFakeCallsManager.hasOngoingCalls()).thenReturn(true);
        assertTrue(mTSIBinder.isInCall(DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testNotIsInCall() throws Exception {
        when(mFakeCallsManager.hasOngoingCalls()).thenReturn(false);
        assertFalse(mTSIBinder.isInCall(DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testIsInCallFail() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                anyString(), any());
        try {
            mTSIBinder.isInCall("blah");
            fail();
        } catch (SecurityException e) {
            // desired result
        }
        verify(mFakeCallsManager, never()).hasOngoingCalls();
    }

    @SmallTest
    public void testIsInManagedCall() throws Exception {
        when(mFakeCallsManager.hasOngoingManagedCalls()).thenReturn(true);
        assertTrue(mTSIBinder.isInManagedCall(DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testNotIsInManagedCall() throws Exception {
        when(mFakeCallsManager.hasOngoingManagedCalls()).thenReturn(false);
        assertFalse(mTSIBinder.isInManagedCall(DEFAULT_DIALER_PACKAGE));
    }

    @SmallTest
    public void testIsInManagedCallFail() throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                anyString(), any());
        try {
            mTSIBinder.isInManagedCall("blah");
            fail();
        } catch (SecurityException e) {
            // desired result
        }
        verify(mFakeCallsManager, never()).hasOngoingCalls();
    }

    /**
     * Register phone accounts for the supplied PhoneAccountHandles to make them
     * visible to all users (via the isVisibleToCaller method in TelecomServiceImpl.
     * @param handles the handles for which phone accounts should be created for.
     */
    private void makeAccountsVisibleToAllUsers(PhoneAccountHandle... handles) {
        for (PhoneAccountHandle ph : handles) {
            when(mFakePhoneAccountRegistrar.getPhoneAccountUnchecked(eq(ph))).thenReturn(
                    makeMultiUserPhoneAccount(ph).build());
            when(mFakePhoneAccountRegistrar
                    .getPhoneAccount(eq(ph), nullable(UserHandle.class), anyBoolean()))
                    .thenReturn(makeMultiUserPhoneAccount(ph).build());
            when(mFakePhoneAccountRegistrar
                    .getPhoneAccount(eq(ph), nullable(UserHandle.class)))
                    .thenReturn(makeMultiUserPhoneAccount(ph).build());
        }
    }

    private PhoneAccount.Builder makeMultiUserPhoneAccount(PhoneAccountHandle paHandle) {
        PhoneAccount.Builder paBuilder = makePhoneAccount(paHandle);
        paBuilder.setCapabilities(PhoneAccount.CAPABILITY_MULTI_USER);
        return paBuilder;
    }

    private PhoneAccount.Builder makePhoneAccount(PhoneAccountHandle paHandle) {
        return new PhoneAccount.Builder(paHandle, "testLabel");
    }

    private Bundle createSampleExtras() {
        Bundle extras = new Bundle();
        extras.putString("test_key", "test_value");
        return extras;
    }

    private static boolean areBundlesEqual(Bundle b1, Bundle b2) {
        for (String key1 : b1.keySet()) {
            if (!b1.get(key1).equals(b2.get(key1))) {
                return false;
            }
        }

        for (String key2 : b2.keySet()) {
            if (!b2.get(key2).equals(b1.get(key2))) {
                return false;
            }
        }
        return true;
    }
}
