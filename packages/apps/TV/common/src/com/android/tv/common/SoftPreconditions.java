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
 * limitations under the License
 */

package com.android.tv.common;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.common.feature.Feature;

/**
 * Simple static methods to be called at the start of your own methods to verify
 * correct arguments and state.
 *
 * <p>{@code checkXXX} methods throw exceptions when {@link BuildConfig#ENG} is true, and
 * logs a warning when it is false.
 *
 * <p>This is based on com.android.internal.util.Preconditions.
 */
public final class SoftPreconditions {
    private static final String TAG = "SoftPreconditions";

    /**
     * Throws or logs if an expression involving the parameter of the calling
     * method is not true.
     *
     * @param expression a boolean expression
     * @param tag Used to identify the source of a log message.  It usually
     *            identifies the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @return the evaluation result of the boolean expression
     * @throws IllegalArgumentException if {@code expression} is true
     */
    public static boolean checkArgument(final boolean expression, String tag, String msg) {
        if (!expression) {
            warn(tag, "Illegal argument", msg, new IllegalArgumentException(msg));
        }
        return expression;
    }

    /**
     * Throws or logs if an expression involving the parameter of the calling
     * method is not true.
     *
     * @param expression a boolean expression
     * @return the evaluation result of the boolean expression
     * @throws IllegalArgumentException if {@code expression} is true
     */
    public static boolean checkArgument(final boolean expression) {
        checkArgument(expression, null, null);
        return expression;
    }

    /**
     * Throws or logs if an and object is null.
     *
     * @param reference an object reference
     * @param tag Used to identify the source of a log message.  It usually
     *            identifies the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @return true if the object is null
     * @throws NullPointerException if {@code reference} is null
     */
    public static <T> T checkNotNull(final T reference, String tag, String msg) {
        if (reference == null) {
            warn(tag, "Null Pointer", msg, new NullPointerException(msg));
        }
        return reference;
    }

    /**
     * Throws or logs if an and object is null.
     *
     * @param reference an object reference
     * @return true if the object is null
     * @throws NullPointerException if {@code reference} is null
     */
    public static <T> T checkNotNull(final T reference) {
        return checkNotNull(reference, null, null);
    }

    /**
     * Throws or logs if an expression involving the state of the calling
     * instance, but not involving any parameters to the calling method is not true.
     *
     * @param expression a boolean expression
     * @param tag Used to identify the source of a log message.  It usually
     *            identifies the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @return the evaluation result of the boolean expression
     * @throws IllegalStateException if {@code expression} is true
     */
    public static boolean checkState(final boolean expression, String tag, String msg) {
        if (!expression) {
            warn(tag, "Illegal State", msg, new IllegalStateException(msg));
        }
        return expression;
    }

    /**
     * Throws or logs if an expression involving the state of the calling
     * instance, but not involving any parameters to the calling method is not true.
     *
     * @param expression a boolean expression
     * @return the evaluation result of the boolean expression
     * @throws IllegalStateException if {@code expression} is true
     */
    public static boolean checkState(final boolean expression) {
        checkState(expression, null, null);
        return expression;
    }

    /**
     * Throws or logs if the Feature is not enabled
     *
     * @param context an android context
     * @param feature the required feature
     * @param tag used to identify the source of a log message.  It usually
     *            identifies the class or activity where the log call occurs
     * @throws IllegalStateException if {@code feature} is not enabled
     */
    public static void checkFeatureEnabled(Context context, Feature feature, String tag) {
        checkState(feature.isEnabled(context), tag, feature.toString());
    }

    /**
     * Throws a {@link RuntimeException} if {@link BuildConfig#ENG} is true, else log a warning.
     *
     * @param tag Used to identify the source of a log message.  It usually
     *            identifies the class or activity where the log call occurs.
     * @param msg The message you would like logged
     * @param e The exception to wrap with a RuntimeException when thrown.
     */
    public static void warn(String tag, String prefix, String msg, Exception e)
            throws RuntimeException {
        if (TextUtils.isEmpty(tag)) {
            tag = TAG;
        }
        String logMessage;
        if (TextUtils.isEmpty(msg)) {
            logMessage = prefix;
        } else if (TextUtils.isEmpty(prefix)) {
            logMessage = msg;
        } else {
            logMessage = prefix + ": " + msg;
        }

        if (BuildConfig.ENG) {
            throw new RuntimeException(msg, e);
        } else {
            Log.w(tag, logMessage, e);
        }
    }

    private SoftPreconditions() {
    }
}
