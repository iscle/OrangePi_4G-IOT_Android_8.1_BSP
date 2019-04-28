/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.cluster.sample;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Log;

/**
 * An immutable class which hold the the information about the currently connected media app.
 */
public class MediaAppInfo {
    private static final String TAG = DebugUtil.getTag(MediaAppInfo.class);

    private int mAccentColor;

    public MediaAppInfo(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA);

            fetchAppColors(packageName, appInfo.theme, context);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Got a component that doesn't exist (" + packageName + ")");
        }
    }

    public int getMediaClientAccentColor() {
        return mAccentColor;
    }

    private void fetchAppColors(String packageName, int appTheme, Context context) {
        TypedArray ta = null;
        try {
            Context packageContext = context.createPackageContext(packageName, 0);
            packageContext.setTheme(appTheme);
            Resources.Theme theme = packageContext.getTheme();
            ta = theme.obtainStyledAttributes(new int[]{ android.R.attr.colorAccent });
            mAccentColor = ta.getColor(1,
                    context.getResources().getColor(android.R.color.holo_green_light));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to update media client package attributes.", e);
        } finally {
            if (ta != null) {
                ta.recycle();
            }
        }
    }
}