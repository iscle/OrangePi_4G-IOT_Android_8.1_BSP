/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.ephemeralapp1;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.util.Log;

import com.android.cts.util.TestResult;

import java.io.FileDescriptor;

public class EphemeralService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        TestResult.getBuilder()
                .setPackageName("com.android.cts.ephemeralapp1")
                .setComponentName("EphemeralService")
                .setMethodName("onBind")
                .setStatus("PASS")
                .build()
                .broadcast(this);
        return new Binder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TestResult.getBuilder()
                .setPackageName("com.android.cts.ephemeralapp1")
                .setComponentName("EphemeralService")
                .setMethodName("onStartCommand")
                .setStatus("PASS")
                .build()
                .broadcast(this);
        return super.onStartCommand(intent, flags, startId);
    }
}
