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

package com.android.tv.common;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Lazy loaded boolean system property.
 *
 * <p>Set with  <code>adb shell setprop <em>key</em> <em>value</em></code> where:
 * Values 'n', 'no', '0', 'false' or 'off' are considered false.
 * Values 'y', 'yes', '1', 'true' or 'on' are considered true.
 * (case sensitive). See <a href=
 * "https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/SystemProperties.java"
 * >android.os.SystemProperties.getBoolean</a>.
 */
public class BooleanSystemProperty {
    private final static String TAG = "BooleanSystemProperty";
    private final static boolean DEBUG = false;
    private static final List<BooleanSystemProperty> ALL_PROPERTIES = new ArrayList<>();
    private final boolean mDefaultValue;
    private final String mKey;
    private Boolean mValue = null;

    /**
     * Create a boolean system property.
     *
     * @param key the system property key.
     * @param defaultValue the value to return if the property is undefined or empty.
     */
    public BooleanSystemProperty(String key, boolean defaultValue) {
        mDefaultValue = defaultValue;
        mKey = key;
        ALL_PROPERTIES.add(this);
    }

    public static void resetAll() {
        for (BooleanSystemProperty prop : ALL_PROPERTIES) {
            prop.reset();
        }
    }

    /**
     * Gets system properties set by <code>adb shell setprop <em>key</em> <em>value</em></code>
     *
     * @param key the property key.
     * @param defaultValue the value to return if the property is undefined or empty.
     * @return the system property value or the default value.
     */
    private static boolean getBoolean(String key, boolean defaultValue) {
        try {
            final Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            final Method get = systemProperties.getMethod("getBoolean", String.class, Boolean.TYPE);
            return (boolean) get.invoke(null, key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Error getting boolean for  " + key, e);
            // This should never happen
            return defaultValue;
        }
    }

    /**
     * Clears the cached value.  The next call to getValue will check {@code
     * android.os.SystemProperties}.
     */
    public void reset() {
        mValue = null;
    }

    /**
     * Returns the value of the system property.
     *
     * <p>If the value is cached  get the value from {@code android.os.SystemProperties} with the
     * default set in the constructor.
     */
    public boolean getValue() {
        if (mValue == null) {
            mValue = getBoolean(mKey, mDefaultValue);
            if (DEBUG) Log.d(TAG, mKey + "=" + mValue);
        }
        return mValue;
    }

    @Override
    public String toString() {
        return "SystemProperty[" + mKey + "]=" + getValue();
    }
}
