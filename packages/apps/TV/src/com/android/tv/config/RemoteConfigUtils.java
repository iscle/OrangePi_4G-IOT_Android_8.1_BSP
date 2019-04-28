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

package com.android.tv.config;

import android.content.Context;
import android.util.Log;
import com.android.tv.TvApplication;

/** A utility class to get the remote config. */
public class RemoteConfigUtils {
    private static final String TAG = "RemoteConfigUtils";
    private static final boolean DEBUG = false;

    private RemoteConfigUtils() {}

    public static long getRemoteConfig(Context context, String key, long defaultValue) {
        RemoteConfig remoteConfig = TvApplication.getSingletons(context).getRemoteConfig();
        try {
            long remoteValue = remoteConfig.getLong(key);
            if (DEBUG) Log.d(TAG, "Got " + key + " from remote: " + remoteValue);
            return remoteValue;
        } catch (Exception e) {
            Log.w(TAG, "Cannot get " + key + " from RemoteConfig", e);
        }
        if (DEBUG) Log.d(TAG, "Use default value " + defaultValue);
        return defaultValue;
    }
}
