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

package com.android.cts.splitapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import java.io.File;

public class BaseBootReceiver extends BroadcastReceiver {
    private static final String TAG = "SplitApp";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            context = context.createDeviceProtectedStorageContext();
            final File probe = new File(context.getFilesDir(),
                    getBootCount(context) + "." + intent.getAction());
            Log.d(TAG, "Touching probe " + probe);
            probe.createNewFile();
            exposeFile(probe);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int getBootCount(Context context) throws Exception {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT);
    }

    private static File exposeFile(File file) throws Exception {
        file.setReadable(true, false);
        file.setReadable(true, true);

        File dir = file.getParentFile();
        do {
            dir.setExecutable(true, false);
            dir.setExecutable(true, true);
            dir = dir.getParentFile();
        } while (dir != null);

        return file;
    }
}
