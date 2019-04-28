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
package com.google.android.car.usb.aoap.host;

import android.app.Service;
import android.car.IUsbAoapSupportCheckService;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.IBinder;

/**
 * Service to check is AOAP device supports Android Auto.
 */
public class AoapSupportCheckService extends Service {

    private final IUsbAoapSupportCheckService.Stub mBinder =
            new IUsbAoapSupportCheckService.Stub() {
                public boolean isDeviceSupported(UsbDevice device) {
                    // TODO: do some check.
                    return true;
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }
}
