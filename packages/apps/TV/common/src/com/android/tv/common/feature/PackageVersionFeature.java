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

package com.android.tv.common.feature;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/*
 * A feature controlled by package version.
 */
public class PackageVersionFeature implements Feature {
    private static final String TAG = "PackageVersionFeature";
    private static final boolean DEBUG = false;

    private final String mPackageName;
    private final int mRequiredVersionCode;

    public PackageVersionFeature(String packageName, int requiredVersionCode) {
        mPackageName = packageName;
        mRequiredVersionCode = requiredVersionCode;
    }

    @Override
    public boolean isEnabled(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(mPackageName, 0);
            return pInfo != null && pInfo.versionCode >= mRequiredVersionCode;
        } catch (PackageManager.NameNotFoundException e) {
            if (DEBUG) Log.d(TAG, "Can't find package '" + mPackageName + "'.", e);
            return false;
        }
    }

    @Override
    public String toString() {
        return "PackageVersionFeature[packageName=" + mPackageName + ",requiredVersion="
                + mRequiredVersionCode + "]";
    }
}
