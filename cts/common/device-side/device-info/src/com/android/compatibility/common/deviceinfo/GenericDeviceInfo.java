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
package com.android.compatibility.common.deviceinfo;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.lang.Integer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

/**
 * Generic device info collector.
 */
public class GenericDeviceInfo extends DeviceInfo {

    public static final String BUILD_ID = "build_id";
    public static final String BUILD_PRODUCT = "build_product";
    public static final String BUILD_DEVICE = "build_device";
    public static final String BUILD_BOARD = "build_board";
    public static final String BUILD_MANUFACTURER = "build_manufacturer";
    public static final String BUILD_BRAND = "build_brand";
    public static final String BUILD_MODEL = "build_model";
    public static final String BUILD_TYPE = "build_type";
    public static final String BUILD_FINGERPRINT = "build_fingerprint";
    public static final String BUILD_ABI = "build_abi";
    public static final String BUILD_ABI2 = "build_abi2";
    public static final String BUILD_ABIS = "build_abis";
    public static final String BUILD_ABIS_32 = "build_abis_32";
    public static final String BUILD_ABIS_64 = "build_abis_64";
    public static final String BUILD_SERIAL = "build_serial";
    public static final String BUILD_VERSION_RELEASE = "build_version_release";
    public static final String BUILD_VERSION_SDK = "build_version_sdk";
    public static final String BUILD_VERSION_SDK_INT = "build_version_sdk_int";
    public static final String BUILD_VERSION_BASE_OS = "build_version_base_os";
    public static final String BUILD_VERSION_SECURITY_PATCH = "build_version_security_patch";
    public static final String BUILD_REFERENCE_FINGERPRINT = "build_reference_fingerprint";

    private final Map<String, String> mDeviceInfo = new HashMap<>();

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        store.addResult(BUILD_ID, Build.ID);
        store.addResult(BUILD_PRODUCT, Build.PRODUCT);
        store.addResult(BUILD_DEVICE, Build.DEVICE);
        store.addResult(BUILD_BOARD, Build.BOARD);
        store.addResult(BUILD_MANUFACTURER, Build.MANUFACTURER);
        store.addResult(BUILD_BRAND, Build.BRAND);
        store.addResult(BUILD_MODEL, Build.MODEL);
        store.addResult(BUILD_TYPE, Build.TYPE);
        store.addResult(BUILD_FINGERPRINT, Build.FINGERPRINT);
        store.addResult(BUILD_ABI, Build.CPU_ABI);
        store.addResult(BUILD_ABI2, Build.CPU_ABI2);
        store.addResult(BUILD_SERIAL, Build.SERIAL);
        store.addResult(BUILD_VERSION_RELEASE, Build.VERSION.RELEASE);
        store.addResult(BUILD_VERSION_SDK, Build.VERSION.SDK);
        store.addResult(BUILD_REFERENCE_FINGERPRINT,
                SystemProperties.get("ro.build.reference.fingerprint", ""));

        // Collect build fields available in API level 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            store.addResult(BUILD_ABIS, TextUtils.join(",", Build.SUPPORTED_ABIS));
            store.addResult(BUILD_ABIS_32, TextUtils.join(",", Build.SUPPORTED_32_BIT_ABIS));
            store.addResult(BUILD_ABIS_64, TextUtils.join(",", Build.SUPPORTED_64_BIT_ABIS));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            store.addResult(BUILD_VERSION_BASE_OS, Build.VERSION.BASE_OS);
            store.addResult(BUILD_VERSION_SECURITY_PATCH, Build.VERSION.SECURITY_PATCH);
        } else {
            // Access system properties directly because Build.Version.BASE_OS and
            // Build.Version.SECURITY_PATCH are not defined pre-M.
            store.addResult(BUILD_VERSION_BASE_OS,
                    SystemProperties.get("ro.build.version.base_os", ""));
            store.addResult(BUILD_VERSION_SECURITY_PATCH,
                    SystemProperties.get("ro.build.version.security_patch", ""));
        }
    }
}
