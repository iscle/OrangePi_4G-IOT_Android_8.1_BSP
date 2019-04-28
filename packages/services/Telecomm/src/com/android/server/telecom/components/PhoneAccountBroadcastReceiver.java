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
 * limitations under the License
 */

package com.android.server.telecom.components;

import com.android.server.telecom.PhoneAccountRegistrar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.TelecomManager;

import java.lang.String;

/**
 * Captures {@code android.intent.action.ACTION_PACKAGE_FULLY_REMOVED} intents and triggers the
 * removal of associated {@link android.telecom.PhoneAccount}s via the
 * {@link com.android.telecom.PhoneAccountRegistrar}.
 * Note: This class listens for the {@code PACKAGE_FULLY_REMOVED} intent rather than
 * {@code PACKAGE_REMOVED} as {@code PACKAGE_REMOVED} is triggered on re-installation of the same
 * package, where {@code PACKAGE_FULLY_REMOVED} is triggered only when an application is completely
 * uninstalled.  This is desirable as we do not wish to un-register all
 * {@link android.telecom.PhoneAccount}s associated with a package being re-installed to ensure
 * the enabled state of the accounts is retained.
 */
public class PhoneAccountBroadcastReceiver extends BroadcastReceiver {
    /**
     * Receives the intents the class is configured to received.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri == null) {
                return;
            }

            String packageName = uri.getSchemeSpecificPart();
            handlePackageRemoved(context, packageName);
        }
    }

    /**
     * Handles the removal of a package by calling upon the {@link PhoneAccountRegistrar} to
     * un-register any {@link android.telecom.PhoneAccount}s associated with the package.
     *
     * @param packageName The name of the removed package.
     */
    private void handlePackageRemoved(Context context, String packageName) {
        final TelecomManager telecomManager = TelecomManager.from(context);
        if (telecomManager != null) {
            telecomManager.clearAccountsForPackage(packageName);
        }
    }
}
