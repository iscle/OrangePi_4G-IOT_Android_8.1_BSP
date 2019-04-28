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
 * limitations under the License.
 */
package com.android.car.pm;

import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.ICarAppBlockingPolicy;
import android.car.content.pm.ICarAppBlockingPolicySetter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.CarLog;
import com.android.internal.annotations.GuardedBy;

public class AppBlockingPolicyProxy implements ServiceConnection {

    private final CarPackageManagerService mService;
    private final Context mContext;
    private final ServiceInfo mServiceInfo;
    private final ICarAppBlockingPolicySetterImpl mSetter;

    @GuardedBy("this")
    private ICarAppBlockingPolicy mPolicyService = null;

    /**
     * policy not set within this time after binding will be treated as failure and will be
     * ignored.
     */
    private static final long TIMEOUT_MS = 5000;
    private static final int MAX_CRASH_RETRY = 2;
    @GuardedBy("this")
    private int mCrashCount = 0;
    @GuardedBy("this")
    private boolean mBound = false;

    private final Handler mHandler;
    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            Log.w(CarLog.TAG_PACKAGE, "Timeout for policy setting for service:" + mServiceInfo);
            disconnect();
            mService.onPolicyConnectionFailure(AppBlockingPolicyProxy.this);
        }
    };

    public AppBlockingPolicyProxy(CarPackageManagerService service, Context context,
            ServiceInfo serviceInfo) {
        mService = service;
        mContext = context;
        mServiceInfo = serviceInfo;
        mSetter = new ICarAppBlockingPolicySetterImpl();
        mHandler = new Handler(mService.getLooper());
    }

    public String getPackageName() {
        return mServiceInfo.packageName;
    }

    public void connect() {
        Intent intent = new Intent();
        intent.setClassName(mServiceInfo.packageName, mServiceInfo.name);
        mContext.bindServiceAsUser(intent, this, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                UserHandle.SYSTEM);
        synchronized (this) {
            mBound = true;
        }
        mHandler.postDelayed(mTimeoutRunnable, TIMEOUT_MS);
    }

    public void disconnect() {
        synchronized (this) {
            if (!mBound) {
                return;
            }
            mBound = false;
            mPolicyService = null;
        }
        mHandler.removeCallbacks(mTimeoutRunnable);
        try {
            mContext.unbindService(this);
        } catch (IllegalArgumentException e) {
            Log.w(CarLog.TAG_PACKAGE, "unbind", e);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ICarAppBlockingPolicy policy = null;
        boolean failed = false;
        synchronized (this) {
            mPolicyService = ICarAppBlockingPolicy.Stub.asInterface(service);
            policy = mPolicyService;
            if (policy == null) {
                failed = true;
            }
        }
        if (failed) {
            Log.w(CarLog.TAG_PACKAGE, "Policy service connected with null binder:" + name);
            mService.onPolicyConnectionFailure(this);
            return;
        }
        try {
            mPolicyService.setAppBlockingPolicySetter(mSetter);
        } catch (RemoteException e) {
            // let retry handle this
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        boolean failed = false;
        synchronized (this) {
            mCrashCount++;
            if (mCrashCount > MAX_CRASH_RETRY) {
                mPolicyService = null;
                failed = true;
            }
        }
        if (failed) {
            Log.w(CarLog.TAG_PACKAGE, "Policy service keep crashing, giving up:" + name);
            mService.onPolicyConnectionFailure(this);
        }
    }

    @Override
    public String toString() {
        return "AppBlockingPolicyProxy [mServiceInfo=" + mServiceInfo + ", mCrashCount="
                + mCrashCount + "]";
    }

    private class ICarAppBlockingPolicySetterImpl extends ICarAppBlockingPolicySetter.Stub {

        @Override
        public void setAppBlockingPolicy(CarAppBlockingPolicy policy) {
            mHandler.removeCallbacks(mTimeoutRunnable);
            if (policy == null) {
                Log.w(CarLog.TAG_PACKAGE, "setAppBlockingPolicy null policy from policy service:" +
                        mServiceInfo);
            }
            mService.onPolicyConnectionAndSet(AppBlockingPolicyProxy.this, policy);
        }
    }
}
