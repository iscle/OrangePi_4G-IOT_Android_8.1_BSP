/*
 * Copyright 2014, The Android Open Source Project
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
/*
 * Copyright (C) 2017 MediaTek Inc., this file is modified on 07/05/2017
 * by MediaTek Inc. based on Apache License, Version 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. See NOTICE for more details.
 */

package com.android.services.telephony;

import java.lang.reflect.Method;

import android.content.Context;

/**
 * Manages logging for the entire module.
 */
final public class Log {

    // Generic tag for all In Call logging
    private static final String TAG = "Telephony";

    public static final boolean FORCE_LOGGING = false; /* STOP SHIP if true */
    public static final boolean DEBUG = isLoggable(android.util.Log.DEBUG);
    public static final boolean INFO = isLoggable(android.util.Log.INFO);
    public static final boolean VERBOSE = isLoggable(android.util.Log.VERBOSE);
    public static final boolean WARN = isLoggable(android.util.Log.WARN);
    public static final boolean ERROR = isLoggable(android.util.Log.ERROR);

    private Log() {}

    public static boolean isLoggable(int level) {
        return FORCE_LOGGING || android.util.Log.isLoggable(TAG, level);
    }

    static final String EXTENSION_CLASS_NAME = "com.mediatek.services.telephony.MtkLogUtils";

    public static void initLogging(Context context) {
        try {
            Class clazz = Class.forName(EXTENSION_CLASS_NAME);
            Class[] argTypes = new Class[] { Context.class };
            Method m = clazz.getDeclaredMethod("initLogging", argTypes);
            Object[] params = { context };
            i(TAG, "invoke redirect to " + clazz.getName() + "." + m.getName() + "("
                    + params.toString() + ")");
            m.invoke(null, params);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            e(TAG, e, "initLogging invoke redirect fails. Use AOSP instead.");
        }

        // Register Telephony with the Telecom Logger.
        android.telecom.Log.setTag(TAG);
        android.telecom.Log.setSessionContext(context);
        android.telecom.Log.initMd5Sum();
    }

    // Relay log messages to Telecom
    // TODO: Redo namespace of Telephony to use these methods directly.

    public static void d(String prefix, String format, Object... args) {
        android.telecom.Log.d(prefix, format, args);
    }

    public static void d(Object objectPrefix, String format, Object... args) {
        android.telecom.Log.d(objectPrefix, format, args);
    }

    public static void i(String prefix, String format, Object... args) {
        android.telecom.Log.i(prefix, format, args);
    }

    public static void i(Object objectPrefix, String format, Object... args) {
        android.telecom.Log.i(objectPrefix, format, args);
    }

    public static void v(String prefix, String format, Object... args) {
        android.telecom.Log.v(prefix, format, args);
    }

    public static void v(Object objectPrefix, String format, Object... args) {
        android.telecom.Log.v(objectPrefix, format, args);
    }

    public static void w(String prefix, String format, Object... args) {
        android.telecom.Log.w(prefix, format, args);
    }

    public static void w(Object objectPrefix, String format, Object... args) {
        android.telecom.Log.w(objectPrefix, format, args);
    }

    public static void e(String prefix, Throwable tr, String format, Object... args) {
        android.telecom.Log.e(prefix, tr, format, args);
    }

    public static void e(Object objectPrefix, Throwable tr, String format, Object... args) {
        android.telecom.Log.e(objectPrefix, tr, format, args);
    }

    public static void wtf(String prefix, Throwable tr, String format, Object... args) {
        android.telecom.Log.wtf(prefix, tr, format, args);
    }

    public static void wtf(Object objectPrefix, Throwable tr, String format, Object... args) {
        android.telecom.Log.wtf(objectPrefix, tr, format, args);
    }

    public static void wtf(String prefix, String format, Object... args) {
        android.telecom.Log.wtf(prefix, format, args);
    }

    public static void wtf(Object objectPrefix, String format, Object... args) {
        android.telecom.Log.wtf(objectPrefix, format, args);
    }

    public static String pii(Object pii) {
        return android.telecom.Log.pii(pii);
    }
}
