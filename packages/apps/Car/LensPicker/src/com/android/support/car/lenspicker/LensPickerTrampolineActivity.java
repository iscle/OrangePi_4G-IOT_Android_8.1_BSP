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
 * limitations under the License.
 */
package com.android.support.car.lenspicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.statusbar.IStatusBarService;

import java.net.URISyntaxException;

/**
 * An activity to determine if the lens picker should be launched or if the
 * preferred/last run app should be launched.
 */
public class LensPickerTrampolineActivity extends Activity {
    private static final String TAG = "LensPickerTrampoline";

    /**
     * A lock to be used when retrieving the {@link IStaturBarService}.
     */
    private final Object mServiceAcquireLock = new Object();

    private SharedPreferences mSharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageManager packageManager = getPackageManager();
        mSharedPrefs = LensPickerUtils.getFacetSharedPrefs(this);

        Intent intent = getIntent();

        String systemCommand =
                intent.getStringExtra(LensPickerConstants.EXTRA_FACET_SYSTEM_COMMAND);
        if (systemCommand != null && executeSystemCommand(systemCommand)) {
            finish();
            return;
        }

        // Hide the shade if switching to a different facet.
        hideNotificationsShade();

        String facetId = intent.getStringExtra(LensPickerConstants.EXTRA_FACET_ID);

        // If no facetId was passed to this activity, then that means that we cannot retrieve the
        // application categories to determine an activity to launch. Thus, just launch the
        // default application.
        if (TextUtils.isEmpty(facetId)) {
            launchLastRunOrDefaultApplication();
            finish();
            return;
        }

        String facetKey = LensPickerUtils.getFacetKey(facetId);
        String savedPackageName = mSharedPrefs.getString(facetKey, null /* defaultValue */);
        String[] categories = intent.getStringArrayExtra(
                LensPickerConstants.EXTRA_FACET_CATEGORIES);
        String[] packages = intent.getStringArrayExtra(LensPickerConstants.EXTRA_FACET_PACKAGES);

        boolean alwaysLaunchPicker = intent.getBooleanExtra(
                LensPickerConstants.EXTRA_FACET_LAUNCH_PICKER, false);

        Intent launchIntent;
        if (!alwaysLaunchPicker && savedPackageName != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Launching saved package: " + savedPackageName);
            }

            launchIntent = packageManager.getLaunchIntentForPackage(savedPackageName);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Delegating to LensPickerActivity to handle application launch.");
            }

            launchIntent = new Intent(this, LensPickerActivity.class);
            launchIntent.putExtra(LensPickerConstants.EXTRA_FACET_PACKAGES, packages);
            launchIntent.putExtra(LensPickerConstants.EXTRA_FACET_CATEGORIES, categories);
            launchIntent.putExtra(LensPickerConstants.EXTRA_FACET_ID, facetId);
        }

        startActivity(launchIntent);
        finish();
    }

    /**
     * Executes the given system command and returns {@code true} if the command succeeded.
     */
    private boolean executeSystemCommand(String command) {
        switch (command) {
            case LensPickerConstants.SYSTEM_COMMAND_TOGGLE_NOTIFICATIONS:
                toggleNotificationsShade();
                return true;

            default:
                Log.w(TAG, "Unknown system command: " + command);
                return false;
        }
    }

    /**
     * Checks if there was an application that was last launched by the LensPicker. If there was,
     * then launches that. Otherwise, launches the default application.
     */
    private void launchLastRunOrDefaultApplication() {
        if (maybeRelaunchLastIntent()) {
            return;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Launching default application.");
        }

        ComponentName defaultComponent = new ComponentName(
                getString(R.string.default_application_package),
                getString(R.string.default_application_activity));

        Intent intent = new Intent();
        intent.setComponent(defaultComponent);

        startActivity(intent);
    }

    /**
     * Attempts to relaunch the last application that was started by the LensPicker and returns
     * {@code true} if this was successful. If the LensPicker has not launched an application yet
     * or the process to retrieve the last launched app fails, then {@code false} is returned.
     */
    private boolean maybeRelaunchLastIntent() {
        LensPickerUtils.AppLaunchInformation info =
                LensPickerUtils.getLastLaunchedAppInfo(mSharedPrefs);

        if (info == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No last launched application information available.");
            }
            return false;
        }

        String facetId = info.getFacetId();
        String packageName = info.getPackageName();
        String intentString = info.getIntentString();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("mLastLaunchedFacetId: %s, mLastLaunchedPackageName: %s,"
                            + " intentString: %s", facetId, packageName,
                    intentString));
        }

        Intent launchIntent;
        try {
            launchIntent = Intent.parseUri(intentString, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Could not parse intent string: " + intentString);
            return false;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Launching last launched application.");
        }

        try {
            LensPickerUtils.launch(this /* context */, mSharedPrefs, facetId, packageName,
                launchIntent);
        } catch (ActivityNotFoundException e) {
            // This can happen during development if someone changes the Activity used by an app.
            Log.e(TAG, "Unable to launch activity! " + packageName, e);
            return false;
        }
        return true;
    }

    private void hideNotificationsShade() {
        IStatusBarService service = getStatusBarService();
        if (service != null) {
            try {
                service.collapsePanels();
            } catch (RemoteException e) {
                // Do nothing
            }
        }
    }

    private void toggleNotificationsShade() {
        IStatusBarService service = getStatusBarService();
        if (service != null) {
            try {
                service.togglePanel();
            } catch (RemoteException e) {
                // Do nothing
            }
        }
    }

    private IStatusBarService getStatusBarService() {
        synchronized (mServiceAcquireLock) {
            return IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
        }
    }
}
