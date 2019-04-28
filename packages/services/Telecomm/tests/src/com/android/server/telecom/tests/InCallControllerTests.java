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

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.telecom.InCallService;
import android.telecom.ParcelableCall;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.mock.MockContext;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;

import com.android.internal.telecom.IInCallAdapter;
import com.android.internal.telecom.IInCallService;
import com.android.server.telecom.Analytics;
import com.android.server.telecom.BluetoothHeadsetProxy;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.EmergencyCallHelper;
import com.android.server.telecom.InCallController;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.R;
import com.android.server.telecom.SystemStateProvider;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.LinkedList;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class InCallControllerTests extends TelecomTestCase {
    @Mock CallsManager mMockCallsManager;
    @Mock PhoneAccountRegistrar mMockPhoneAccountRegistrar;
    @Mock BluetoothHeadsetProxy mMockBluetoothHeadset;
    @Mock SystemStateProvider mMockSystemStateProvider;
    @Mock PackageManager mMockPackageManager;
    @Mock Call mMockCall;
    @Mock Resources mMockResources;
    @Mock MockContext mMockContext;
    @Mock Timeouts.Adapter mTimeoutsAdapter;
    @Mock DefaultDialerCache mDefaultDialerCache;

    private static final int CURRENT_USER_ID = 900973;
    private static final String DEF_PKG = "defpkg";
    private static final String DEF_CLASS = "defcls";
    private static final String SYS_PKG = "syspkg";
    private static final String SYS_CLASS = "syscls";
    private static final PhoneAccountHandle PA_HANDLE =
            new PhoneAccountHandle(new ComponentName("pa_pkg", "pa_cls"), "pa_id");

    private UserHandle mUserHandle = UserHandle.of(CURRENT_USER_ID);
    private InCallController mInCallController;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {};
    private EmergencyCallHelper mEmergencyCallHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        when(mMockCall.getAnalytics()).thenReturn(new Analytics.CallInfo());
        doReturn(mMockResources).when(mMockContext).getResources();
        doReturn(SYS_PKG).when(mMockResources).getString(R.string.ui_default_package);
        doReturn(SYS_CLASS).when(mMockResources).getString(R.string.incall_default_class);
        doReturn(true).when(mMockResources).getBoolean(R.bool.grant_location_permission_enabled);
        mEmergencyCallHelper = new EmergencyCallHelper(mMockContext, SYS_PKG,
                mTimeoutsAdapter);
        mInCallController = new InCallController(mMockContext, mLock, mMockCallsManager,
                mMockSystemStateProvider, mDefaultDialerCache, mTimeoutsAdapter,
                mEmergencyCallHelper);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @MediumTest
    public void testBindToService_NoServicesFound_IncomingCall() throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.hasEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(true);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        setupMockPackageManager(false /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
        assertNull(bindIntent.getExtras());
    }

    @MediumTest
    public void testBindToService_NoServicesFound_OutgoingCall() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.hasEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        Intent queryIntent = new Intent(InCallService.SERVICE_INTERFACE);
        setupMockPackageManager(false /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));
    }

    @MediumTest
    public void testBindToService_DefaultDialer_NoEmergency() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.hasEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(true);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA), eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(DEF_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(DEF_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));
    }

    @MediumTest
    public void testBindToService_SystemDialer_Emergency() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.hasEmergencyCall()).thenReturn(true);
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT))).thenReturn(true);
        when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class)))
                .thenReturn(300_000L);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        setupMockPackageManagerLocationPermission(SYS_PKG, false /* granted */);

        mInCallController.bindToServices(mMockCall);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA), eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));

        verify(mMockPackageManager).grantRuntimePermission(eq(SYS_PKG),
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(mUserHandle));

        // Pretend that the call has gone away.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.emptyList());
        mInCallController.onCallRemoved(mMockCall);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TelecomSystemTest.TEST_TIMEOUT);

        verify(mMockPackageManager).revokeRuntimePermission(eq(SYS_PKG),
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(mUserHandle));
    }

    @MediumTest
    public void testBindToService_DefaultDialer_FallBackToSystem() throws Exception {
        Bundle callExtras = new Bundle();
        callExtras.putBoolean("whatever", true);

        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.hasEmergencyCall()).thenReturn(false);
        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockCall));
        when(mMockCallsManager.getAudioState()).thenReturn(null);
        when(mMockCallsManager.canAddCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mMockCall.getIntentExtras()).thenReturn(callExtras);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mMockCall.getConferenceableCalls()).thenReturn(Collections.emptyList());
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID))
                .thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(
                any(Intent.class), any(ServiceConnection.class), anyInt(), any(UserHandle.class)))
                .thenReturn(true);

        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA), eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(DEF_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(DEF_CLASS, bindIntent.getComponent().getClassName());
        assertEquals(PA_HANDLE, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
        assertEquals(callExtras, bindIntent.getExtras().getParcelable(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));

        // We have a ServiceConnection for the default dialer, lets start the connection, and then
        // simulate a crash so that we fallback to system.
        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        ComponentName defDialerComponentName = new ComponentName(DEF_PKG, DEF_CLASS);
        IBinder mockBinder = mock(IBinder.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);


        // Start the connection with IInCallService
        serviceConnection.onServiceConnected(defDialerComponentName, mockBinder);
        verify(mockInCallService).setInCallAdapter(any(IInCallAdapter.class));

        // Now crash the damn thing!
        serviceConnection.onServiceDisconnected(defDialerComponentName);

        ArgumentCaptor<Intent> bindIntentCaptor2 = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(2)).bindServiceAsUser(
                bindIntentCaptor2.capture(),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        bindIntent = bindIntentCaptor2.getValue();
        assertEquals(SYS_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(SYS_CLASS, bindIntent.getComponent().getClassName());
    }

    /**
     * Ensures that the {@link InCallController} will bind to an {@link InCallService} which
     * supports external calls.
     */
    @MediumTest
    public void testBindToService_IncludeExternal() throws Exception {
        setupMocks(true /* isExternalCall */);
        setupMockPackageManager(true /* default */, true /* system */, true /* external calls */);
        mInCallController.bindToServices(mMockCall);

        // Query for the different InCallServices
        ArgumentCaptor<Intent> queryIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockPackageManager, times(4)).queryIntentServicesAsUser(
                queryIntentCaptor.capture(),
                eq(PackageManager.GET_META_DATA), eq(CURRENT_USER_ID));

        // Verify call for default dialer InCallService
        assertEquals(DEF_PKG, queryIntentCaptor.getAllValues().get(0).getPackage());
        // Verify call for car-mode InCallService
        assertEquals(null, queryIntentCaptor.getAllValues().get(1).getPackage());
        // Verify call for non-UI InCallServices
        assertEquals(null, queryIntentCaptor.getAllValues().get(2).getPackage());

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent bindIntent = bindIntentCaptor.getValue();
        assertEquals(InCallService.SERVICE_INTERFACE, bindIntent.getAction());
        assertEquals(DEF_PKG, bindIntent.getComponent().getPackageName());
        assertEquals(DEF_CLASS, bindIntent.getComponent().getClassName());
    }

    /**
     * Make sure that if a call goes away before the in-call service finishes binding and another
     * call gets connected soon after, the new call will still be sent to the in-call service.
     */
    @MediumTest
    public void testUnbindDueToCallDisconnect() throws Exception {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.hasEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(true);
        when(mMockCall.isExternalCall()).thenReturn(false);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(nullable(Intent.class),
                nullable(ServiceConnection.class), anyInt(), nullable(UserHandle.class)))
                .thenReturn(true);
        when(mTimeoutsAdapter.getCallRemoveUnbindInCallServicesDelay(
                nullable(ContentResolver.class))).thenReturn(500L);

        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockCall));
        setupMockPackageManager(true /* default */, true /* system */, false /* external calls */);
        mInCallController.bindToServices(mMockCall);

        ArgumentCaptor<Intent> bindIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, times(1)).bindServiceAsUser(
                bindIntentCaptor.capture(),
                serviceConnectionCaptor.capture(),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        // Pretend that the call has gone away.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.emptyList());
        mInCallController.onCallRemoved(mMockCall);

        // Start the connection, make sure we don't unbind, and make sure that we don't send
        // anything to the in-call service yet.
        ServiceConnection serviceConnection = serviceConnectionCaptor.getValue();
        ComponentName defDialerComponentName = new ComponentName(DEF_PKG, DEF_CLASS);
        IBinder mockBinder = mock(IBinder.class);
        IInCallService mockInCallService = mock(IInCallService.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockInCallService);

        serviceConnection.onServiceConnected(defDialerComponentName, mockBinder);
        verify(mockInCallService).setInCallAdapter(nullable(IInCallAdapter.class));
        verify(mMockContext, never()).unbindService(serviceConnection);
        verify(mockInCallService, never()).addCall(any(ParcelableCall.class));

        // Now, we add in the call again and make sure that it's sent to the InCallService.
        when(mMockCallsManager.getCalls()).thenReturn(Collections.singletonList(mMockCall));
        mInCallController.onCallAdded(mMockCall);
        verify(mockInCallService).addCall(any(ParcelableCall.class));
    }

    private void setupMocks(boolean isExternalCall) {
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockCallsManager.hasEmergencyCall()).thenReturn(false);
        when(mMockCall.isIncoming()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(PA_HANDLE);
        when(mDefaultDialerCache.getDefaultDialerApplication(CURRENT_USER_ID)).thenReturn(DEF_PKG);
        when(mMockContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(true);
        when(mMockCall.isExternalCall()).thenReturn(isExternalCall);
    }

    private ResolveInfo getDefResolveInfo(final boolean includeExternalCalls) {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = DEF_PKG;
            serviceInfo.name = DEF_CLASS;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
            serviceInfo.metaData = new Bundle();
            serviceInfo.metaData.putBoolean(
                    TelecomManager.METADATA_IN_CALL_SERVICE_UI, true);
            if (includeExternalCalls) {
                serviceInfo.metaData.putBoolean(
                        TelecomManager.METADATA_INCLUDE_EXTERNAL_CALLS, true);
            }
        }};
    }

    private ResolveInfo getSysResolveinfo() {
        return new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = SYS_PKG;
            serviceInfo.name = SYS_CLASS;
            serviceInfo.permission = Manifest.permission.BIND_INCALL_SERVICE;
        }};
    }

    private void setupMockPackageManager(final boolean useDefaultDialer,
            final boolean useSystemDialer, final boolean includeExternalCalls) {

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Intent intent = (Intent) args[0];
                String packageName = intent.getPackage();
                ComponentName componentName = intent.getComponent();
                if (componentName != null) {
                    packageName = componentName.getPackageName();
                }
                LinkedList<ResolveInfo> resolveInfo = new LinkedList<ResolveInfo>();
                if (!TextUtils.isEmpty(packageName)) {
                    if ((TextUtils.isEmpty(packageName) || packageName.equals(DEF_PKG)) &&
                            useDefaultDialer) {
                        resolveInfo.add(getDefResolveInfo(includeExternalCalls));
                    }

                    if ((TextUtils.isEmpty(packageName) || packageName.equals(SYS_PKG)) &&
                           useSystemDialer) {
                        resolveInfo.add(getSysResolveinfo());
                    }
                }
                return resolveInfo;
            }
        }).when(mMockPackageManager).queryIntentServicesAsUser(
                any(Intent.class), eq(PackageManager.GET_META_DATA), eq(CURRENT_USER_ID));
    }

    private void setupMockPackageManagerLocationPermission(final String pkg,
            final boolean granted) {
        when(mMockPackageManager.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, pkg))
                .thenReturn(granted
                        ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED);
  }
}
