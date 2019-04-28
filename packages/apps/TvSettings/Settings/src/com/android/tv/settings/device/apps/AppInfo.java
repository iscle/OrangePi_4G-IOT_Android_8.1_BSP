/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.device.apps;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.format.Formatter;

import com.android.settingslib.applications.ApplicationsState;

/**
 * Contains all the info necessary to manage an application.
 */
public class AppInfo {

    private final Object mLock = new Object();
    private final Context mContext;
    private ApplicationsState.AppEntry mEntry;

    public AppInfo(Context context, ApplicationsState.AppEntry entry) {
        mContext = context;
        mEntry = entry;
    }

    public void setEntry(ApplicationsState.AppEntry entry) {
        synchronized (mLock) {
            mEntry = entry;
        }
    }

    public String getName() {
        synchronized (mLock) {
            mEntry.ensureLabel(mContext);
            return mEntry.label;
        }
    }

    public String getSize() {
        synchronized (mLock) {
            return mEntry.sizeStr;
        }
    }

    public String getPackageName() {
        synchronized (mLock) {
            return mEntry.info.packageName;
        }
    }

    @Override
    public String toString() {
        return getName();
    }
}
