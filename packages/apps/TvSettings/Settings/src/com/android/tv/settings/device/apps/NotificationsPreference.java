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

package com.android.tv.settings.device.apps;

import android.app.INotificationManager;
import android.app.NotificationManager;
import android.content.Context;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v14.preference.SwitchPreference;
import android.util.Log;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

public class NotificationsPreference extends SwitchPreference {
    private static final String TAG = "NotificationsPreference";

    private final INotificationManager mNotificationManager;
    private ApplicationsState.AppEntry mEntry;

    public NotificationsPreference(Context context, ApplicationsState.AppEntry entry) {
        super(context);
        mEntry = entry;

        mNotificationManager = NotificationManager.getService();

        refresh();
    }

    /**
     * Set entry and refresh pref.
     * @param entry entry
     */
    public void setEntry(@NonNull ApplicationsState.AppEntry entry) {
        mEntry = entry;
        refresh();
    }

    public void refresh() {
        setTitle(R.string.device_apps_app_management_notifications);

        try {
            super.setChecked(
                    mNotificationManager.areNotificationsEnabledForPackage(mEntry.info.packageName,
                            mEntry.info.uid));
        } catch (RemoteException e) {
            Log.d(TAG, "Remote exception while checking notifications for package "
                    + mEntry.info.packageName, e);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (setNotificationsEnabled(checked)) {
            super.setChecked(checked);
        }
    }

    private boolean setNotificationsEnabled(boolean enabled) {
        boolean result = true;
        if (isChecked() != enabled) {
            try {
                mNotificationManager.setNotificationsEnabledForPackage(
                        mEntry.info.packageName, mEntry.info.uid, enabled);
            } catch (android.os.RemoteException ex) {
                result = false;
            }
        }
        return result;
    }

}
