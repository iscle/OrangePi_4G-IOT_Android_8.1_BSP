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

package com.android.tv.tuner.util;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Proxy class that gives an access to a hidden API {@link android.os.SystemProperties#getBoolean}.
 */
public class SystemPropertiesProxy {
    private static final String TAG = "SystemPropertiesProxy";

    private SystemPropertiesProxy() { }

    public static boolean getBoolean(String key, boolean def)
            throws IllegalArgumentException {
        try {
            Class SystemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method getBooleanMethod = SystemPropertiesClass.getDeclaredMethod("getBoolean",
                    String.class, boolean.class);
            getBooleanMethod.setAccessible(true);
            return (boolean) getBooleanMethod.invoke(SystemPropertiesClass, key, def);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException
                | ClassNotFoundException e) {
            Log.e(TAG, "Failed to invoke SystemProperties.getBoolean()", e);
        }
        return def;
    }

    public static int getInt(String key, int def)
            throws IllegalArgumentException {
        try {
            Class SystemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method getIntMethod = SystemPropertiesClass.getDeclaredMethod("getInt",
                    String.class, int.class);
            getIntMethod.setAccessible(true);
            return (int) getIntMethod.invoke(SystemPropertiesClass, key, def);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException
                | ClassNotFoundException e) {
            Log.e(TAG, "Failed to invoke SystemProperties.getInt()", e);
        }
        return def;
    }

    public static String getString(String key, String def) throws IllegalArgumentException {
        try {
            Class SystemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method getIntMethod =
                    SystemPropertiesClass.getDeclaredMethod("get", String.class, String.class);
            getIntMethod.setAccessible(true);
            return (String) getIntMethod.invoke(SystemPropertiesClass, key, def);
        } catch (InvocationTargetException
                | IllegalAccessException
                | NoSuchMethodException
                | ClassNotFoundException e) {
            Log.e(TAG, "Failed to invoke SystemProperties.get()", e);
        }
        return def;
    }
}
