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

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.IUsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist;

import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

public class ClearDefaultsPreference extends AppActionPreference {
    private final IUsbManager mUsbManager;
    private final PackageManager mPackageManager;

    public ClearDefaultsPreference(Context context, ApplicationsState.AppEntry entry) {
        super(context, entry);

        final IBinder usbBinder = ServiceManager.getService(Context.USB_SERVICE);
        mUsbManager = IUsbManager.Stub.asInterface(usbBinder);
        mPackageManager = context.getPackageManager();

        refresh();
        ConfirmationFragment.prepareArgs(getExtras(), mEntry.info.packageName);
    }

    public void refresh() {
        setTitle(R.string.device_apps_app_management_clear_default);
        setSummary(AppUtils.getLaunchByDefaultSummary(
                mEntry, mUsbManager, mPackageManager, getContext()));
    }

    @Override
    public String getFragment() {
        return ConfirmationFragment.class.getName();
    }

    public static class ConfirmationFragment extends AppActionPreference.ConfirmationFragment {
        private static final String ARG_PACKAGE_NAME = "packageName";

        private static void prepareArgs(@NonNull Bundle args, String packageName) {
            args.putString(ARG_PACKAGE_NAME, packageName);
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            final AppManagementFragment fragment = (AppManagementFragment) getTargetFragment();
            return new GuidanceStylist.Guidance(
                    getString(R.string.device_apps_app_management_clear_default),
                    null,
                    fragment.getAppName(),
                    fragment.getAppIcon());
        }

        @Override
        public void onOk() {
            PackageManager packageManager = getActivity().getPackageManager();

            final String packageName = getArguments().getString(ARG_PACKAGE_NAME);
            packageManager.clearPackagePreferredActivities(packageName);
            try {
                final IBinder usbBinder = ServiceManager.getService(Context.USB_SERVICE);
                IUsbManager.Stub.asInterface(usbBinder)
                        .clearDefaults(packageName, UserHandle.myUserId());
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }
}
