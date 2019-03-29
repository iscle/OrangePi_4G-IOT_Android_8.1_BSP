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

package com.android.cts.net.hostside.app2;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import com.android.cts.net.hostside.IRemoteSocketFactory;

import java.net.Socket;


public class RemoteSocketFactoryService extends Service {

    private static final String TAG = RemoteSocketFactoryService.class.getSimpleName();

    private IRemoteSocketFactory.Stub mBinder = new IRemoteSocketFactory.Stub() {
        @Override
        public ParcelFileDescriptor openSocketFd(String host, int port, int timeoutMs) {
            try {
                Socket s = new Socket(host, port);
                s.setSoTimeout(timeoutMs);
                return ParcelFileDescriptor.fromSocket(s);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public String getPackageName() {
            return RemoteSocketFactoryService.this.getPackageName();
        }

        @Override
        public int getUid() {
            return Process.myUid();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
