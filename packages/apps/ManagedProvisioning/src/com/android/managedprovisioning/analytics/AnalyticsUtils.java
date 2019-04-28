/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.analytics;

import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static com.android.managedprovisioning.common.Globals.ACTION_RESUME_PROVISIONING;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.content.Intent;
import android.nfc.NdefRecord;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.android.managedprovisioning.parser.PropertiesProvisioningDataParser;
import com.android.managedprovisioning.task.AbstractProvisioningTask;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Properties;

/**
 * Class containing various auxiliary methods used by provisioning analytics tracker.
 */
public class AnalyticsUtils {

    public AnalyticsUtils() {}

    private static final String PROVISIONING_EXTRA_PREFIX = "android.app.extra.PROVISIONING_";

    /**
     * Returns package name of the installer package, null if package is not present on the device
     * and empty string if installer package is not present on the device.
     *
     * @param context Context used to get package manager
     * @param packageName Package name of the installed package
     */
    @Nullable
    public static String getInstallerPackageName(Context context, String packageName) {
        try {
            return context.getPackageManager().getInstallerPackageName(packageName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns elapsed real time.
     */
    public Long elapsedRealTime() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * Returns list of all valid provisioning extras sent by the dpc.
     *
     * @param intent Intent that started provisioning
     */
    @NonNull
    public static List<String> getAllProvisioningExtras(Intent intent) {
        if (intent == null || ACTION_RESUME_PROVISIONING.equals(intent.getAction())) {
            // Provisioning extras should have already been logged for resume case.
            return new ArrayList<String>();
        } else if (ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            return getExtrasFromProperties(intent);
        } else {
            return getExtrasFromBundle(intent);
        }
    }

    /**
     * Returns unique string for all provisioning task errors.
     *
     * @param task Provisioning task which threw error
     * @param errorCode Unique code from class indicating the error
     */
    @Nullable
    public static String getErrorString(AbstractProvisioningTask task, int errorCode) {
        if (task == null) {
            return null;
        }
        // We do not have definite codes for all provisioning errors yet. We just pass the task's
        // class name and the internal task's error code to generate a unique error code.
        return task.getClass().getSimpleName() + ":" + errorCode;
    }

    @NonNull
    private static List<String> getExtrasFromBundle(Intent intent) {
        List<String> provisioningExtras = new ArrayList<String>();
        if (intent != null && intent.getExtras() != null) {
            final Set<String> keys = intent.getExtras().keySet();
            for (String key : keys) {
                if (isValidProvisioningExtra(key)) {
                    provisioningExtras.add(key);
                }
            }
        }
        return provisioningExtras;
    }

    @NonNull
    private static List<String> getExtrasFromProperties(Intent intent) {
        List<String> provisioningExtras = new ArrayList<String>();
        NdefRecord firstRecord = PropertiesProvisioningDataParser.getFirstNdefRecord(intent);
        if (firstRecord != null) {
            try {
                Properties props = new Properties();
                props.load(new StringReader(new String(firstRecord.getPayload(), UTF_8)));
                final Set<String> keys = props.stringPropertyNames();
                for (String key : keys) {
                    if (isValidProvisioningExtra(key)) {
                        provisioningExtras.add(key);
                    }
                }
            } catch (IOException e) {
            }
        }
        return provisioningExtras;
    }

    /**
     * Returns if a string is a valid provisioning extra.
     */
    private static boolean isValidProvisioningExtra(String provisioningExtra) {
        // Currently it verifies using the prefix. We should further change this to verify using the
        // actual DPM extras.
        return provisioningExtra != null && provisioningExtra.startsWith(PROVISIONING_EXTRA_PREFIX);
    }
}
