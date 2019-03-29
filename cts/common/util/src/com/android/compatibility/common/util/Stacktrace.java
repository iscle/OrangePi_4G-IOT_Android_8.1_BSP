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

package com.android.compatibility.common.util;

/**
 * Helper methods for dealing with stack traces
 */
public class Stacktrace {

    private static final int SAFETY_DEPTH = 4;
    private static final String TEST_POSTFIX = "Test";

    private Stacktrace() {}

    /**
     * @return classname#methodname from call stack of the current thread
     */
    public static String getTestCallerClassMethodName() {
        return getTestCallerClassMethodName(false /*includeLineNumber*/);
    }

    /**
     * @return classname#methodname from call stack of the current thread
     */
    public static String getTestCallerClassMethodNameLineNumber() {
        return getTestCallerClassMethodName(true /*includeLineNumber*/);
    }

    /**
     * @return classname#methodname from call stack of the current thread
     */
    private static String getTestCallerClassMethodName(boolean includeLineNumber) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        // Look for the first class name in the elements array that ends with Test
        for (int i = 0; i < elements.length; i++) {
            if (elements[i].getClassName().endsWith(TEST_POSTFIX)) {
                return buildClassMethodName(elements, i, includeLineNumber);
            }
        }

        // Use a reasonable default if the test name isn't found
        return buildClassMethodName(elements, SAFETY_DEPTH, includeLineNumber);
    }

    private static String buildClassMethodName(
            StackTraceElement[] elements, int depth, boolean includeLineNumber) {
        depth = Math.min(depth, elements.length - 1);
        StringBuilder builder = new StringBuilder();
        builder.append(elements[depth].getClassName()).append("#")
                .append(elements[depth].getMethodName());
        if (includeLineNumber) {
            builder.append(":").append(elements[depth].getLineNumber());
        }
        return builder.toString();
    }
}
