/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

class Version {

    private static final String TAG = Version.class.getSimpleName();

    private static final String UNKNOWN = "unknown";

    static String getVersionName(Context context) {
        return getPackageInfo(context).versionName;
    }

    static int getVersionCode(Context context) {
        return getPackageInfo(context).versionCode;
    }

    static PackageInfo getPackageInfo(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            return packageManager.getPackageInfo(context.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            throw new RuntimeException("Could not get find package information for "
                    + context.getPackageName());
        }
    }

    static String getMetadata(Context context, String name) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            String value = applicationInfo.metaData.getString(name);
            if (value != null) {
                return value;
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Version.getMetadata: " + name, e);
        }
        return UNKNOWN;
    }
}
