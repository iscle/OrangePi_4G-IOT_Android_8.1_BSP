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

import java.lang.reflect.Field;

/**
 * Device-side compatibility utility class for reading device API level.
 */
public class ApiLevelUtil {

    public static final String CODENAME = "ro.build.version.codename";

    public static boolean isBefore(ITestDevice device, int version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() < version;
    }

    public static boolean isBefore(ITestDevice device, String version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() < resolveVersionString(version);
    }

    public static boolean isAfter(ITestDevice device, int version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() > version;
    }

    public static boolean isAfter(ITestDevice device, String version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() > resolveVersionString(version);
    }

    public static boolean isAtLeast(ITestDevice device, int version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() >= version;
    }

    public static boolean isAtLeast(ITestDevice device, String version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() >= resolveVersionString(version);
    }

    public static boolean isAtMost(ITestDevice device, int version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() <= version;
    }

    public static boolean isAtMost(ITestDevice device, String version)
            throws DeviceNotAvailableException {
        return device.getApiLevel() <= resolveVersionString(version);
    }

    public static int getApiLevel(ITestDevice device) throws DeviceNotAvailableException {
        return device.getApiLevel();
    }

    public static boolean codenameEquals(ITestDevice device, String name)
            throws DeviceNotAvailableException {
        return device.getProperty(CODENAME).equalsIgnoreCase(name.trim());
    }

    public static boolean codenameStartsWith(ITestDevice device, String prefix)
            throws DeviceNotAvailableException {
        return device.getProperty(CODENAME).startsWith(prefix);
    }

    public static String getCodename(ITestDevice device)
            throws DeviceNotAvailableException {
        return device.getProperty(CODENAME);
    }

    protected static int resolveVersionString(String versionString) {
        try {
            return Integer.parseInt(versionString); // e.g. "24" for M
        } catch (NumberFormatException e1) {
            try {
                Field versionField = VersionCodes.class.getField(
                        versionString.toUpperCase());
                return versionField.getInt(null); // no instance for VERSION_CODES, use null
            } catch (IllegalAccessException | NoSuchFieldException e2) {
                throw new RuntimeException(
                        String.format("Failed to parse version string %s", versionString), e2);
            }
        }
    }
}
