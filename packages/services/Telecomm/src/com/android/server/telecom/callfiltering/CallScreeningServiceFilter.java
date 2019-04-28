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

package com.android.server.telecom.callfiltering;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallScreeningService;
import android.telecom.Log;
import android.text.TextUtils;

import com.android.internal.telecom.ICallScreeningAdapter;
import com.android.internal.telecom.ICallScreeningService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomServiceImpl;
import com.android.server.telecom.TelecomSystem;

import java.util.List;

/**
 * Binds to {@link ICallScreeningService} to allow call blocking. A single instance of this class
 * handles a single call.
 */
public class CallScreeningServiceFilter implements IncomingCallFilter.CallFilter {
    private class CallScreeningServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.startSession("CSCR.oSC");
            try {
                synchronized (mTelecomLock) {
                    Log.addEvent(mCall, LogUtils.Events.SCREENING_BOUND, componentName);
                    if (!mHasFinished) {
                        onServiceBound(ICallScreeningService.Stub.asInterface(service));
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.startSession("CSCR.oSD");
            try {
                synchronized (mTelecomLock) {
                    finishCallScreening();
                }
            } finally {
                Log.endSession();
            }
        }
    }

    private class CallScreeningAdapter extends ICallScreeningAdapter.Stub {
        @Override
        public void allowCall(String callId) {
            Log.startSession("CSCR.aC");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mTelecomLock) {
                    Log.d(this, "allowCall(%s)", callId);
                    if (mCall != null && mCall.getId().equals(callId)) {
                        mResult = new CallFilteringResult(
                                true, // shouldAllowCall
                                false, //shouldReject
                                true, //shouldAddToCallLog
                                true // shouldShowNotification
                        );
                    } else {
                        Log.w(this, "allowCall, unknown call id: %s", callId);
                    }
                    finishCallScreening();
                }
            } finally {
                Binder.restoreCallingIdentity(token);
                Log.endSession();
            }
        }

        @Override
        public void disallowCall(
                String callId,
                boolean shouldReject,
                boolean shouldAddToCallLog,
                boolean shouldShowNotification) {
            Log.startSession("CSCR.dC");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mTelecomLock) {
                    Log.i(this, "disallowCall(%s), shouldReject: %b, shouldAddToCallLog: %b, "
                                    + "shouldShowNotification: %b", callId, shouldReject,
                            shouldAddToCallLog, shouldShowNotification);
                    if (mCall != null && mCall.getId().equals(callId)) {
                        mResult = new CallFilteringResult(
                                false, // shouldAllowCall
                                shouldReject, //shouldReject
                                shouldAddToCallLog, //shouldAddToCallLog
                                shouldShowNotification // shouldShowNotification
                        );
                    } else {
                        Log.w(this, "disallowCall, unknown call id: %s", callId);
                    }
                    finishCallScreening();
                }
            } finally {
                Binder.restoreCallingIdentity(token);
                Log.endSession();
            }
        }
    }

    private final Context mContext;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final CallsManager mCallsManager;
    private final DefaultDialerCache mDefaultDialerCache;
    private final ParcelableCallUtils.Converter mParcelableCallUtilsConverter;
    private final TelecomSystem.SyncRoot mTelecomLock;

    private Call mCall;
    private CallFilterResultCallback mCallback;
    private ICallScreeningService mService;
    private ServiceConnection mConnection;

    private boolean mHasFinished = false;
    private CallFilteringResult mResult = new CallFilteringResult(
            true, // shouldAllowCall
            false, //shouldReject
            true, //shouldAddToCallLog
            true // shouldShowNotification
    );

    public CallScreeningServiceFilter(
            Context context,
            CallsManager callsManager,
            PhoneAccountRegistrar phoneAccountRegistrar,
            DefaultDialerCache defaultDialerCache,
            ParcelableCallUtils.Converter parcelableCallUtilsConverter,
            TelecomSystem.SyncRoot lock) {
        mContext = context;
        mPhoneAccountRegistrar = phoneAccountRegistrar;
        mCallsManager = callsManager;
        mDefaultDialerCache = defaultDialerCache;
        mParcelableCallUtilsConverter = parcelableCallUtilsConverter;
        mTelecomLock = lock;
    }

    @Override
    public void startFilterLookup(Call call, CallFilterResultCallback callback) {
        if (mHasFinished) {
            Log.w(this, "Attempting to reuse CallScreeningServiceFilter. Ignoring.");
            return;
        }
        Log.addEvent(call, LogUtils.Events.SCREENING_SENT);
        mCall = call;
        mCallback = callback;
        if (!bindService()) {
            Log.i(this, "Could not bind to call screening service");
            finishCallScreening();
        }
    }

    private void finishCallScreening() {
        if (!mHasFinished) {
            Log.addEvent(mCall, LogUtils.Events.SCREENING_COMPLETED, mResult);
            mCallback.onCallFilteringComplete(mCall, mResult);

            if (mConnection != null) {
                // We still need to call unbind even if the service disconnected.
                mContext.unbindService(mConnection);
                mConnection = null;
            }
            mService = null;
            mHasFinished = true;
        }
    }

    private boolean bindService() {
        String dialerPackage = mDefaultDialerCache
                .getDefaultDialerApplication(UserHandle.USER_CURRENT);
        if (TextUtils.isEmpty(dialerPackage)) {
            Log.i(this, "Default dialer is empty. Not performing call screening.");
            return false;
        }

        Intent intent = new Intent(CallScreeningService.SERVICE_INTERFACE)
            .setPackage(dialerPackage);
        List<ResolveInfo> entries = mContext.getPackageManager().queryIntentServicesAsUser(
                intent, 0, mCallsManager.getCurrentUserHandle().getIdentifier());
        if (entries.isEmpty()) {
            Log.i(this, "There are no call screening services installed on this device.");
            return false;
        }

        ResolveInfo entry = entries.get(0);
        if (entry.serviceInfo == null) {
            Log.w(this, "The call screening service has invalid service info");
            return false;
        }

        if (entry.serviceInfo.permission == null || !entry.serviceInfo.permission.equals(
                Manifest.permission.BIND_SCREENING_SERVICE)) {
            Log.w(this, "CallScreeningService must require BIND_SCREENING_SERVICE permission: " +
                    entry.serviceInfo.packageName);
            return false;
        }

        ComponentName componentName =
                new ComponentName(entry.serviceInfo.packageName, entry.serviceInfo.name);
        Log.addEvent(mCall, LogUtils.Events.BIND_SCREENING, componentName);
        intent.setComponent(componentName);
        ServiceConnection connection = new CallScreeningServiceConnection();
        if (mContext.bindServiceAsUser(
                intent,
                connection,
                Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE,
                UserHandle.CURRENT)) {
            Log.d(this, "bindService, found service, waiting for it to connect");
            mConnection = connection;
            return true;
        }

        return false;
    }

    private void onServiceBound(ICallScreeningService service) {
        mService = service;
        try {
            mService.screenCall(new CallScreeningAdapter(),
                    mParcelableCallUtilsConverter.toParcelableCall(
                            mCall,
                            false, /* includeVideoProvider */
                            mPhoneAccountRegistrar));
        } catch (RemoteException e) {
            Log.e(this, e, "Failed to set the call screening adapter.");
            finishCallScreening();
        }
    }
}
