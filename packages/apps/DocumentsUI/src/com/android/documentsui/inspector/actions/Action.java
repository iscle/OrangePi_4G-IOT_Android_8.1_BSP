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

package com.android.documentsui.inspector.actions;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;

import com.android.documentsui.base.DocumentInfo;

/**
 * Abstract class for using ActionView with different actions.
 */
public abstract class Action {

    public static final String APP_NAME_UNKNOWN = "unknown";
    public static final String APP_NOT_CHOSEN = "android";

    protected Context mContext;
    protected PackageManager mPm;
    protected DocumentInfo mDoc;

    public Action(Context context, PackageManager pm, DocumentInfo doc) {
        assert context != null;
        assert pm != null;
        assert doc != null;
        mContext = context;
        mPm = pm;
        mDoc = doc;
    }

    public abstract String getHeader();

    public abstract int getButtonIcon();

    public abstract boolean canPerformAction();

    public abstract @Nullable String getPackageName();

    public @Nullable Drawable getAppIcon() {

        String packageName = getPackageName();
        Drawable icon = null;
        if (packageName == null || APP_NOT_CHOSEN.equals(packageName)) {
            return null;
        }

        try {
            icon = mPm.getApplicationIcon(packageName);
        } catch(NameNotFoundException e) {
            icon = null;
        }
        return icon;
    }

    public String getAppName() {

        String packageName = getPackageName();
        if (packageName == null) {
            return APP_NAME_UNKNOWN;
        }

        ApplicationInfo appInfo = null;
        String appName = "";

        if (APP_NOT_CHOSEN.equals(packageName)) {
            return APP_NOT_CHOSEN;
        }

        try {
            appInfo = mPm.getApplicationInfo(packageName, 0);
            appName = (String) mPm.getApplicationLabel(appInfo);
        } catch (NameNotFoundException e) {
            appName = APP_NAME_UNKNOWN;
        }
        return appName;
    }
}