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
 * limitations under the License
 */

package com.android.phone.vvm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PersistableBundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailService;
import android.text.TextUtils;
import android.util.ArraySet;

import java.util.Collections;
import java.util.Set;

/**
 * Receives {@link Intent#ACTION_PACKAGE_ADDED} for the system dialer to inform it a carrier visual
 * voicemail app has been installed. ACTION_PACKAGE_ADDED requires the receiver process to be
 * running so the system dialer cannot receive it itself.
 *
 * Carrier VVM apps are usually regular apps, not a
 * {@link VisualVoicemailService} nor {@link android.service.carrier.CarrierMessagingService} so it
 * will not take precedence over the system dialer. The system dialer should disable VVM it self
 * to let the carrier app take over since the installation is an explicit user interaction. Carrier
 * customer support might also ask the user to switch to their app if they believe there's any
 * issue in the system dialer so this transition should not require more user interaction.
 *
 * @see CarrierConfigManager#KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY
 */
public class CarrierVvmPackageInstalledReceiver extends BroadcastReceiver {

    private static final String TAG = "VvmPkgInstalledRcvr";

    /**
     * Hidden broadcast to the system dialer
     */
    private static final String ACTION_CARRIER_VVM_PACKAGE_INSTALLED =
            "com.android.internal.telephony.CARRIER_VVM_PACKAGE_INSTALLED";

    public void register(Context context) {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        context.registerReceiver(this, intentFilter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getData() == null) {
            return;
        }
        String packageName = intent.getData().getSchemeSpecificPart();
        if (packageName == null) {
            return;
        }

        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        String systemDialer = telecomManager.getSystemDialerPackage();
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        for (PhoneAccountHandle phoneAccountHandle : telecomManager.getCallCapablePhoneAccounts()) {
            TelephonyManager pinnedTelephonyManager = telephonyManager
                    .createForPhoneAccountHandle(phoneAccountHandle);

            if (pinnedTelephonyManager == null) {
                VvmLog.e(TAG, "cannot create TelephonyManager from " + phoneAccountHandle);
                continue;
            }

            if (!getCarrierVvmPackages(telephonyManager).contains(packageName)) {
                continue;
            }

            VvmLog.i(TAG, "Carrier VVM app " + packageName + " installed");

            String vvmPackage = pinnedTelephonyManager.getVisualVoicemailPackageName();
            if (!TextUtils.equals(vvmPackage, systemDialer)) {
                // Non system dialer do not need to prioritize carrier vvm app.
                VvmLog.i(TAG, "non system dialer " + vvmPackage + " ignored");
                continue;
            }

            VvmLog.i(TAG, "sending broadcast to " + vvmPackage);
            Intent broadcast = new Intent(ACTION_CARRIER_VVM_PACKAGE_INSTALLED);
            broadcast.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
            broadcast.setPackage(vvmPackage);
            context.sendBroadcast(broadcast);
        }
    }

    private static Set<String> getCarrierVvmPackages(TelephonyManager pinnedTelephonyManager) {
        Set<String> carrierPackages = new ArraySet<>();

        PersistableBundle config = pinnedTelephonyManager.getCarrierConfig();
        String singlePackage = config
                .getString(CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING);
        if (!TextUtils.isEmpty(singlePackage)) {
            carrierPackages.add(singlePackage);
        }
        String[] arrayPackages = config
                .getStringArray(CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY);
        if (arrayPackages != null) {
            Collections.addAll(carrierPackages, arrayPackages);
        }

        return carrierPackages;
    }
}
