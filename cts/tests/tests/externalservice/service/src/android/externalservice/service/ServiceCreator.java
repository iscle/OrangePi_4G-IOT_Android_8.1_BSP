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

package android.externalservice.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

import android.externalservice.common.ServiceMessages;

public class ServiceCreator extends Service {
    private static final String TAG = "ExternalServiceTest.ServiceCreator";

    private final ArrayList<CreatorConnection> mConnections = new ArrayList<>();

    private final Handler mHandler = new BaseService.BaseHandler(this) {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "Received message: " + msg);
            switch (msg.what) {
                case ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE:
                    final Context context = ServiceCreator.this;
                    final String pkgName = context.getPackageName();
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(pkgName, pkgName+".ExternalService"));
                    CreatorConnection conn = new CreatorConnection(msg.replyTo);
                    boolean result = context.bindService(intent, conn,
                            Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
                    if (result) {
                        mConnections.add(conn);
                    } else {
                        Message reply = Message.obtain();
                        reply.what = ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE_RESPONSE;
                        try {
                            msg.replyTo.send(reply);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Failed to send MSG_CREATE_EXTERNAL_SERVICE_RESPONSE", e);
                        }
                    }
            }
            super.handleMessage(msg);
        }
    };

    private final Messenger mMessenger = new Messenger(mHandler);

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind " + intent);
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        for (final CreatorConnection conn : mConnections) {
            unbindService(conn);
        }
        super.onDestroy();
    }

    private class CreatorConnection implements ServiceConnection {
        private IBinder mService = null;
        private Messenger mReplyTo = null;

        public CreatorConnection(Messenger replyTo) {
            mReplyTo = replyTo;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected " + name);
            Message msg =
                    Message.obtain(null, ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE_RESPONSE);
            msg.obj = new Messenger(service);
            try {
                mReplyTo.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send MSG_CREATE_EXTERNAL_SERVICE_RESPONSE", e);
            }
            mReplyTo = null;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
            mService = null;
        }
    }
}
