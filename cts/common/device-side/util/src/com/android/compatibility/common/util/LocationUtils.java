/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.util;

import android.app.Instrumentation;
import android.util.Log;

import java.io.IOException;

public class LocationUtils {
    private static String TAG = "LocationUtils";

    public static void registerMockLocationProvider(Instrumentation instrumentation,
            boolean enable) {
        StringBuilder command = new StringBuilder();
        command.append("appops set ");
        command.append(instrumentation.getContext().getPackageName());
        command.append(" android:mock_location ");
        command.append(enable ? "allow" : "deny");
        try {
            SystemUtil.runShellCommand(instrumentation, command.toString());
        } catch (IOException e) {
            Log.e(TAG, "Error managing mock location app. Command: " + command, e);
        }
    }
}
