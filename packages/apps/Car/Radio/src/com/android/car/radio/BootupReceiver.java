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

package com.android.car.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * {@link BroadcastReceiver} that will start the {@link RadioService} when the system has finished
 * booting.
 */
public class BootupReceiver extends BroadcastReceiver {
    private static final String TAG = "Em.RadioBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "ACTION_BOOT_COMPLETED");
            }

            context.startService(new Intent(context, RadioService.class));
        }
    }
}
