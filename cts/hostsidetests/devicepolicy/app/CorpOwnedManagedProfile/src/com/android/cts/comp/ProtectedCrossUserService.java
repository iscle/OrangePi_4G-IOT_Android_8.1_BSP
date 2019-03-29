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

package com.android.cts.comp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;

/**
 * Handle the cross user call from the device admin in other side.
 */
public class ProtectedCrossUserService extends Service {

    private final ICrossUserService.Stub mBinder = new ICrossUserService.Stub() {
        public String echo(String msg) {
            return msg;
        }

        public UserHandle getUserHandle() {
            return Process.myUserHandle();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}
