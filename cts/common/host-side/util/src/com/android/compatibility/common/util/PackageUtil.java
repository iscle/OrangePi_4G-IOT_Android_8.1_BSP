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

package com.android.compatibility.common.util;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * Host-side utility class for PackageManager-related operations
 */
public class PackageUtil {

    /** Returns true if a package with the given name exists on the device */
    public static boolean exists(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        return device.getInstalledPackageNames().contains(packageName);
    }

    /** Returns true if the app for the given package name is a system app for this device */
    public static boolean isSystemApp(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        return device.getAppPackageInfo(packageName).isSystemApp();
    }
}
