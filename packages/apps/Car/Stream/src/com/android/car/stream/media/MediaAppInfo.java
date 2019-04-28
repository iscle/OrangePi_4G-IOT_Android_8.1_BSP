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
package com.android.car.stream.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Log;

/**
 * An immutable class which hold the the information about the currently connected media app, if
 * it supports {@link android.service.media.MediaBrowserService}.
 */
public class MediaAppInfo {
    private static final String TAG = "MediaAppInfo";
    private static final String KEY_SMALL_ICON =
            "com.google.android.gms.car.notification.SmallIcon";

    /** Third-party defined application theme to use **/
    private static final String THEME_META_DATA_NAME
            = "com.google.android.gms.car.application.theme";

    private final ComponentName mComponentName;
    private final Resources mPackageResources;
    private final String mAppName;
    private final String mPackageName;
    private final int mSmallIcon;

    private int mPrimaryColor;
    private int mPrimaryColorDark;
    private int mAccentColor;

    public MediaAppInfo(Context context, String packageName) {
        Resources resources = null;
        try {
            resources = context.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to get resources for " + packageName);
        }
        mPackageResources = resources;

        mComponentName = MediaUtils.getMediaBrowserService(packageName, context);
        String appName = null;
        int smallIconResId = 0;
        try {
            PackageManager packageManager = context.getPackageManager();
            ServiceInfo serviceInfo = null;
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA);

            int labelResId;

            if (mComponentName != null) {
                serviceInfo =
                        packageManager.getServiceInfo(mComponentName, PackageManager.GET_META_DATA);
                smallIconResId = serviceInfo.metaData == null ? 0 : serviceInfo.metaData.getInt
                        (KEY_SMALL_ICON, 0);
                labelResId = serviceInfo.labelRes;
            } else {
                Log.w(TAG, "Service label is null for " + packageName +
                        ". Falling back to app name.");
                labelResId = appInfo.labelRes;
            }

            int appTheme = 0;
            if (serviceInfo != null && serviceInfo.metaData != null) {
                appTheme = serviceInfo.metaData.getInt(THEME_META_DATA_NAME);
            }
            if (appTheme == 0 && appInfo.metaData != null) {
                appTheme = appInfo.metaData.getInt(THEME_META_DATA_NAME);
            }
            if (appTheme == 0) {
                appTheme = appInfo.theme;
            }

            fetchAppColors(packageName, appTheme, context);
            appName = (labelResId == 0 || mPackageResources == null) ? null
                    : mPackageResources.getString(labelResId);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Got a component that doesn't exist (" + packageName + ")");
        }
        mSmallIcon = smallIconResId;
        mAppName = appName;

        mPackageName = packageName;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public String getAppName() {
        return mAppName;
    }

    public int getSmallIcon() {
        return mSmallIcon;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public Resources getPackageResources() {
        return mPackageResources;
    }

    public int getMediaClientPrimaryColor() {
        return mPrimaryColor;
    }

    public int getMediaClientPrimaryColorDark() {
        return mPrimaryColorDark;
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
            ta = theme.obtainStyledAttributes(new int[]{
                    android.R.attr.colorPrimary,
                    android.R.attr.colorAccent,
                    android.R.attr.colorPrimaryDark
            });
            int defaultColor =
                    context.getColor(android.R.color.holo_green_light);
            mPrimaryColor = ta.getColor(0, defaultColor);
            mAccentColor = ta.getColor(1, defaultColor);
            mPrimaryColorDark = ta.getColor(2, defaultColor);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to update media client package attributes.", e);
        } finally {
            if (ta != null) {
                ta.recycle();
            }
        }
    }
}