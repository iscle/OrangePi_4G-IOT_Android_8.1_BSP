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

import android.os.Build;

import java.lang.reflect.Field;

/**
 * Device-side compatibility utility class for reading device API level.
 */
public class ApiLevelUtil {

    public static boolean isBefore(int version) {
        return Build.VERSION.SDK_INT < version;
    }

    public static boolean isBefore(String version) {
        return Build.VERSION.SDK_INT < resolveVersionString(version);
    }

    public static boolean isAfter(int version) {
        return Build.VERSION.SDK_INT > version;
    }

    public static boolean isAfter(String version) {
        return Build.VERSION.SDK_INT > resolveVersionString(version);
    }

    public static boolean isAtLeast(int version) {
        return Build.VERSION.SDK_INT >= version;
    }

    public static boolean isAtLeast(String version) {
        return Build.VERSION.SDK_INT > resolveVersionString(version);
    }

    public static boolean isAtMost(int version) {
        return Build.VERSION.SDK_INT <= version;
    }

    public static boolean isAtMost(String version) {
        return Build.VERSION.SDK_INT <= resolveVersionString(version);
    }

    public static int getApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    public static boolean codenameEquals(String name) {
        return Build.VERSION.CODENAME.equalsIgnoreCase(name.trim());
    }

    public static boolean codenameStartsWith(String prefix) {
        return Build.VERSION.CODENAME.startsWith(prefix);
    }

    public static String getCodename() {
        return Build.VERSION.CODENAME;
    }

    protected static int resolveVersionString(String versionString) {
        try {
            return Integer.parseInt(versionString); // e.g. "24" for M
        } catch (NumberFormatException e1) {
            try {
                Field versionField = Build.VERSION_CODES.class.getField(
                        versionString.toUpperCase());
                return versionField.getInt(null); // no instance for VERSION_CODES, use null
            } catch (IllegalAccessException | NoSuchFieldException e2) {
                throw new RuntimeException(
                        String.format("Failed to parse version string %s", versionString), e2);
            }
        }
    }
}
