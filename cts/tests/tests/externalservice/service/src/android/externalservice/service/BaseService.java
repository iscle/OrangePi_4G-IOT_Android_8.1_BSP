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
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import android.externalservice.common.ServiceMessages;

public class BaseService extends Service {
    private static final String TAG = "ExternalServiceTest.Service";

    private final Handler mHandler = new BaseHandler(this);
    private final Messenger mMessenger = new Messenger(mHandler);

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind " + intent);
        return mMessenger.getBinder();
    }

    static class BaseHandler extends Handler {
        private Context mContext;

        public BaseHandler(Context context) {
            mContext = context;
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "Received message: " + msg);
            switch (msg.what) {
                case ServiceMessages.MSG_IDENTIFY:
                    Message reply = Message.obtain(null, ServiceMessages.MSG_IDENTIFY_RESPONSE,
                            Process.myUid(), Process.myPid());
                    reply.getData().putString(ServiceMessages.IDENTIFY_PACKAGE,
                            mContext.getPackageName());
                    try {
                        msg.replyTo.send(reply);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error sending MSG_IDENTIFY_RESPONSE", e);
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    }
}
