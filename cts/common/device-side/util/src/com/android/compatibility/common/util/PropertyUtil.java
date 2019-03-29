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

import java.io.IOException;
import java.util.Scanner;

/**
 * Device-side utility class for reading properties and gathering information for testing
 * Android device compatibility.
 */
public class PropertyUtil {

    /**
     * Name of read-only property detailing the first API level for which the product was
     * shipped. Property should be undefined for factory ROM products.
     */
    public static final String FIRST_API_LEVEL = "ro.product.first_api_level";
    private static final String BUILD_TYPE_PROPERTY = "ro.build.type";
    private static final String TAG_DEV_KEYS = "dev-keys";

    /** Value to be returned by getPropertyInt() if property is not found */
    public static int INT_VALUE_IF_UNSET = -1;

    /** Returns whether the device build is a user build */
    public static boolean isUserBuild() {
        return propertyEquals(BUILD_TYPE_PROPERTY, "user");
    }

    /** Returns whether the device build is the factory ROM */
    public static boolean isFactoryROM() {
        // property should be undefined if and only if the product is factory ROM.
        return getPropertyInt(FIRST_API_LEVEL) == INT_VALUE_IF_UNSET;
    }

    /** Returns whether this build is built with dev-keys */
    public static boolean isDevKeysBuild() {
        for (String tag : Build.TAGS.split(",")) {
            if (TAG_DEV_KEYS.equals(tag.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the first API level for this product. If the read-only property is unset,
     * this means the first API level is the current API level, and the current API level
     * is returned.
     */
    public static int getFirstApiLevel() {
        int firstApiLevel = getPropertyInt(FIRST_API_LEVEL);
        return (firstApiLevel == INT_VALUE_IF_UNSET) ? Build.VERSION.SDK_INT : firstApiLevel;
    }

    /** Returns whether the property exists on this device */
    public static boolean propertyExists(String property) {
        return getProperty(property) != null;
    }

    /** Returns whether the property value is equal to a given string */
    public static boolean propertyEquals(String property, String value) {
        if (value == null) {
            return !propertyExists(property); // null value implies property does not exist
        }
        return value.equals(getProperty(property));
    }

    /**
     * Retrieves the desired integer property, returning INT_VALUE_IF_UNSET if not found.
     */
    public static int getPropertyInt(String property) {
        String value = getProperty(property);
        if (value == null) {
            return INT_VALUE_IF_UNSET;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return INT_VALUE_IF_UNSET;
        }
    }

    /** Retrieves the desired property value in string form */
    public static String getProperty(String property) {
        Scanner scanner = null;
        try {
            Process process = new ProcessBuilder("getprop", property).start();
            scanner = new Scanner(process.getInputStream());
            String value = scanner.nextLine().trim();
            return (value.isEmpty()) ? null : value;
        } catch (IOException e) {
            return null;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }
}
