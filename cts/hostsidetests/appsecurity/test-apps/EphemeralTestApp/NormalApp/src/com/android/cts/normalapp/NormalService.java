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

package com.android.cts.normalapp;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;

import com.android.cts.util.TestResult;

public class NormalService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean canAccessInstantApp = false;
        String exception = null;
        try {
            canAccessInstantApp = tryAccessingInstantApp();
        } catch (Throwable t) {
            exception = t.getClass().getName();
        }

        TestResult.getBuilder()
                .setPackageName("com.android.cts.normalapp")
                .setComponentName("NormalService")
                .setStatus("PASS")
                .setException(exception)
                .setEphemeralPackageInfoExposed(canAccessInstantApp)
                .build()
                .broadcast(this);
        stopSelf(startId);

        return super.onStartCommand(intent, flags, startId);
    }

    private boolean tryAccessingInstantApp() throws NameNotFoundException {
        final PackageInfo info = getPackageManager()
                .getPackageInfo("com.android.cts.ephemeralapp1", 0 /*flags*/);
        return (info != null);
    }
}
