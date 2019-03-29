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
 * limitations under the License.
 */

package com.android.cts.net.hostside;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.cts.net.hostside.IMyService;

import java.io.FileDescriptor;

public class MyServiceClient {
    private static final int TIMEOUT_MS = 5000;
    private static final String PACKAGE = MyServiceClient.class.getPackage().getName();
    private static final String APP2_PACKAGE = PACKAGE + ".app2";
    private static final String SERVICE_NAME = APP2_PACKAGE + ".MyService";

    private Context mContext;
    private ServiceConnection mServiceConnection;
    private IMyService mService;

    public MyServiceClient(Context context) {
        mContext = context;
    }

    public void bind() {
        if (mService != null) {
            throw new IllegalStateException("Already bound");
        }

        final ConditionVariable cv = new ConditionVariable();
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mService = IMyService.Stub.asInterface(service);
                cv.open();
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
            }
        };

        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(APP2_PACKAGE, SERVICE_NAME));
        // Needs to use BIND_ALLOW_OOM_MANAGEMENT and BIND_NOT_FOREGROUND so app2 does not run in
        // the same process state as app
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE
                | Context.BIND_ALLOW_OOM_MANAGEMENT | Context.BIND_NOT_FOREGROUND);
        cv.block(TIMEOUT_MS);
        if (mService == null) {
            throw new IllegalStateException(
                    "Could not bind to MyService service after " + TIMEOUT_MS + "ms");
        }
    }

    public void unbind() {
        if (mService != null) {
            mContext.unbindService(mServiceConnection);
        }
    }

    public void registerBroadcastReceiver() throws RemoteException {
        mService.registerBroadcastReceiver();
    }

    public int getCounters(String receiverName, String action) throws RemoteException {
        return mService.getCounters(receiverName, action);
    }

    public String checkNetworkStatus() throws RemoteException {
        return mService.checkNetworkStatus();
    }

    public String getRestrictBackgroundStatus() throws RemoteException {
        return mService.getRestrictBackgroundStatus();
    }

    public void sendNotification(int notificationId, String notificationType) throws RemoteException {
        mService.sendNotification(notificationId, notificationType);
    }
}
