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

package com.android.server.telecom.components;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.telecom.DefaultDialerManager;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.server.telecom.R;

/**
 * Activity that shows a dialog for the user to confirm whether or not the default dialer should
 * be changed.
 *
 * This dialog can be skipped directly for CTS tests using the adb command:
 * adb shell am start -a android.telecom.action.CHANGE_DEFAULT_DIALER_PRIVILEGED -e android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME <packageName>
 */
public class ChangeDefaultDialerDialog extends AlertActivity implements
        DialogInterface.OnClickListener{
    private static final String TAG = ChangeDefaultDialerDialog.class.getSimpleName();
    private String mNewPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String oldPackage = DefaultDialerManager.getDefaultDialerApplication(this);
        mNewPackage = getIntent().getStringExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME);
        if (!canChangeToProvidedPackage(oldPackage, mNewPackage)) {
            setResult(RESULT_CANCELED);
            finish();
        }

        // Show dialog to require user confirmation.
         buildDialog(mNewPackage);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                TelecomManager.from(this).setDefaultDialer(mNewPackage);
                setResult(RESULT_OK);
                break;
            case BUTTON_NEGATIVE:
                setResult(RESULT_CANCELED);
                break;
        }
    }

    private boolean canChangeToProvidedPackage(String oldPackage, String newPackage) {
        final TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        if (!tm.isVoiceCapable()) {
            Log.w(TAG, "Dialog launched but device is not voice capable.");
            return false;
        }

        if (!DefaultDialerManager.getInstalledDialerApplications(this).contains(newPackage)) {
            Log.w(TAG, "Provided package name does not correspond to an installed Phone "
                    + "application.");
            return false;
        }

        if (!TextUtils.isEmpty(oldPackage) && TextUtils.equals(oldPackage, newPackage)) {
            Log.w(TAG, "Provided package name is already the current default Phone application.");
            return false;
        }
        return true;
    }

    private boolean buildDialog(String newPackage) {
        final PackageManager pm = getPackageManager();
        final String newPackageLabel = getApplicationLabelForPackageName(pm, newPackage);
        final AlertController.AlertParams p = mAlertParams;
        p.mTitle = getString(R.string.change_default_dialer_dialog_title, newPackageLabel);
        p.mMessage = getString(R.string.change_default_dialer_warning_message, newPackageLabel);
        p.mPositiveButtonText = getString(R.string.change_default_dialer_dialog_affirmative);
        p.mNegativeButtonText = getString(R.string.change_default_dialer_dialog_negative);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonListener = this;
        setupAlert();

        return true;
    }

    /**
     * Returns the application label that corresponds to the given package name
     *
     * @param pm An instance of a {@link PackageManager}.
     * @param packageName A valid package name.
     *
     * @return Application label for the given package name, or null if not found.
     */
    private String getApplicationLabelForPackageName(PackageManager pm, String packageName) {
        ApplicationInfo info = null;
        try {
            info = pm.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Application info not found for packageName " + packageName);
        }
        if (info == null) {
            return packageName;
        } else {
            return info.loadLabel(pm).toString();
        }
    }
}
