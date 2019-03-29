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
package com.android.cts.devicepolicy;

public class DeviceAdminHelper {

    private static final String ADMIN_RECEIVER_TEST_CLASS = "BaseDeviceAdminTest$AdminReceiver";

    private static final String UNPROTECTED_ADMIN_RECEIVER_TEST_CLASS =
            "DeviceAdminReceiverWithNoProtection";

    /** returns "com.android.cts.deviceadmin" */
    static String getDeviceAdminJavaPackage() {
        return "com.android.cts.deviceadmin";
    }

    /** e.g. CtsDeviceAdminApp24.apk */
    static String getDeviceAdminApkFileName(int targetApiVersion) {
        return "CtsDeviceAdminApp" + targetApiVersion + ".apk";
    }

    /** e.g. "com.android.cts.deviceadmin24" */
    static String getDeviceAdminApkPackage(int targetApiVersion) {
        return getDeviceAdminJavaPackage() + targetApiVersion;
    }

    /**
     * e.g.
     * "com.android.cts.deviceadmin24/com.android.cts.deviceadmin.BaseDeviceAdminTest$AdminReceiver"
     */
    static String getAdminReceiverComponent(int targetApiVersion) {
        return getDeviceAdminApkPackage(targetApiVersion) + "/" + getDeviceAdminJavaPackage() + "."
                + ADMIN_RECEIVER_TEST_CLASS;
    }

    /**
     * e.g.
     * "com.android.cts.deviceadmin24/com.android.cts.deviceadmin.DeviceAdminReceiverWithNoProtection"
     */
     static String getUnprotectedAdminReceiverComponent(int targetApiVersion) {
        return getDeviceAdminApkPackage(targetApiVersion) + "/" + getDeviceAdminJavaPackage()
                + "." + UNPROTECTED_ADMIN_RECEIVER_TEST_CLASS;
    }
}
