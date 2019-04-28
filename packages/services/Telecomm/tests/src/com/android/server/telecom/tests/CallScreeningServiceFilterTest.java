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
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallScreeningService;
import android.telecom.ParcelableCall;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telecom.ICallScreeningAdapter;
import com.android.internal.telecom.ICallScreeningService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomServiceImpl;
import com.android.server.telecom.callfiltering.CallFilterResultCallback;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.callfiltering.CallScreeningServiceFilter;
import com.android.server.telecom.TelecomSystem;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CallScreeningServiceFilterTest extends TelecomTestCase {
    @Mock Context mContext;
    @Mock CallsManager mCallsManager;
    @Mock PhoneAccountRegistrar mPhoneAccountRegistrar;
    @Mock DefaultDialerCache mDefaultDialerCache;
    @Mock ParcelableCallUtils.Converter mParcelableCallUtilsConverter;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    @Mock Call mCall;
    @Mock CallFilterResultCallback mCallback;

    @Mock PackageManager mPackageManager;
    @Mock IBinder mBinder;
    @Mock ICallScreeningService mCallScreeningService;

    private static final String PKG_NAME = "com.android.services.telecom.tests";
    private static final String CLS_NAME = "CallScreeningService";
    private static final ComponentName COMPONENT_NAME = new ComponentName(PKG_NAME, CLS_NAME);
    private static final String CALL_ID = "u89prgt9ps78y5";

    private ResolveInfo mResolveInfo;

    private static final CallFilteringResult PASS_RESULT = new CallFilteringResult(
            true, // shouldAllowCall
            false, // shouldReject
            true, // shouldAddToCallLog
            true // shouldShowNotification
    );

    private CallScreeningServiceFilter mFilter;
    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(mCallsManager.getCurrentUserHandle()).thenReturn(UserHandle.CURRENT);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mCall.getId()).thenReturn(CALL_ID);
//        when(mBinder.queryLocalInterface(anyString())).thenReturn(mCallScreeningService);
        doReturn(mCallScreeningService).when(mBinder).queryLocalInterface(anyString());

        mResolveInfo =  new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = PKG_NAME;
            serviceInfo.name = CLS_NAME;
            serviceInfo.permission = Manifest.permission.BIND_SCREENING_SERVICE;
        }};

        mFilter = new CallScreeningServiceFilter(mContext, mCallsManager, mPhoneAccountRegistrar,
                mDefaultDialerCache, mParcelableCallUtilsConverter, mLock);

        when(mDefaultDialerCache.getDefaultDialerApplication(eq(UserHandle.USER_CURRENT)))
                .thenReturn(PKG_NAME);
        when(mPackageManager.queryIntentServicesAsUser(nullable(Intent.class), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(mResolveInfo));
        when(mParcelableCallUtilsConverter.toParcelableCall(
                eq(mCall), anyBoolean(), eq(mPhoneAccountRegistrar))).thenReturn(null);
        when(mContext.bindServiceAsUser(nullable(Intent.class), nullable(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(true);
    }

    @SmallTest
    public void testNoDefaultDialer() {
        when(mDefaultDialerCache.getDefaultDialerApplication(eq(UserHandle.USER_CURRENT)))
                .thenReturn(null);
        mFilter.startFilterLookup(mCall, mCallback);
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    public void testNoResolveEntries() {
        when(mPackageManager.queryIntentServicesAsUser(nullable(Intent.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        mFilter.startFilterLookup(mCall, mCallback);
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    public void testBadResolveEntry() {
        mResolveInfo.serviceInfo = null;
        mFilter.startFilterLookup(mCall, mCallback);
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    public void testPermissionlessFilterService() {
        mResolveInfo.serviceInfo.permission = null;
        mFilter.startFilterLookup(mCall, mCallback);
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    public void testContextFailToBind() {
        when(mContext.bindServiceAsUser(nullable(Intent.class), nullable(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(false);
        mFilter.startFilterLookup(mCall, mCallback);
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    public void testExceptionInScreeningService() throws Exception {
        doThrow(new RemoteException()).when(mCallScreeningService).screenCall(
                nullable(ICallScreeningAdapter.class), nullable(ParcelableCall.class));
        mFilter.startFilterLookup(mCall, mCallback);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    public void testAllowCall() throws Exception {
        mFilter.startFilterLookup(mCall, mCallback);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        ICallScreeningAdapter csAdapter = getCallScreeningAdapter();
        csAdapter.allowCall(CALL_ID);
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    public void testDisallowCall() throws Exception {
        mFilter.startFilterLookup(mCall, mCallback);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        ICallScreeningAdapter csAdapter = getCallScreeningAdapter();
        csAdapter.disallowCall(CALL_ID,
                true, // shouldReject
                false, // shouldAddToCallLog
                true // shouldShowNotification
        );
        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                false, // shouldAddToCallLog
                true // shouldShowNotification
        )));
    }

    private ServiceConnection verifyBindingIntent() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mContext).bindServiceAsUser(intentCaptor.capture(), serviceCaptor.capture(),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(CallScreeningService.SERVICE_INTERFACE, capturedIntent.getAction());
        assertEquals(PKG_NAME, capturedIntent.getPackage());
        assertEquals(COMPONENT_NAME, capturedIntent.getComponent());

        return serviceCaptor.getValue();
    }

    private ICallScreeningAdapter getCallScreeningAdapter() throws Exception {
        ArgumentCaptor<ICallScreeningAdapter> captor =
                ArgumentCaptor.forClass(ICallScreeningAdapter.class);
        verify(mCallScreeningService).screenCall(captor.capture(), nullable(ParcelableCall.class));
        return captor.getValue();
    }
}
