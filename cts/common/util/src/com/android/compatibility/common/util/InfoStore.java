/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public abstract class InfoStore {

    protected static final int MAX_STRING_LENGTH = 1000;
    protected static final int MAX_ARRAY_LENGTH = 1000;
    protected static final int MAX_LIST_LENGTH = 1000;

    /**
     * Opens the file for storage and creates the writer.
     */
    abstract void open() throws IOException;

    /**
     * Closes the writer.
     */
    abstract void close() throws IOException;

    /**
     * Start a new group of result.
     */
    abstract void startGroup() throws IOException;

    /**
     * Start a new group of result with specified name.
     */
    abstract void startGroup(String name) throws IOException;

    /**
     * Complete adding result to the last started group.
     */
    abstract void endGroup() throws IOException;

    /**
     * Start a new array of result.
     */
    abstract void startArray() throws IOException;

    /**
     * Start a new array of result with specified name.
     */
    abstract void startArray(String name) throws IOException;

    /**
     * Complete adding result to the last started array.
     */
    abstract void endArray() throws IOException;

    /**
     * Adds a int value to the InfoStore
     */
    abstract void addResult(String name, int value) throws IOException;

    /**
     * Adds a long value to the InfoStore
     */
    abstract void addResult(String name, long value) throws IOException;

    /**
     * Adds a float value to the InfoStore
     */
    abstract void addResult(String name, float value) throws IOException;

    /**
     * Adds a double value to the InfoStore
     */
    abstract void addResult(String name, double value) throws IOException;

    /**
     * Adds a boolean value to the InfoStore
     */
    abstract void addResult(String name, boolean value) throws IOException;

    /**
     * Adds a String value to the InfoStore
     */
    abstract void addResult(String name, String value) throws IOException;

    /**
     * Adds a int array to the InfoStore
     */
    abstract void addArrayResult(String name, int[] array) throws IOException;

    /**
     * Adds a long array to the InfoStore
     */
    abstract void addArrayResult(String name, long[] array) throws IOException;

    /**
     * Adds a float array to the InfoStore
     */
    abstract void addArrayResult(String name, float[] array) throws IOException;

    /**
     * Adds a double array to the InfoStore
     */
    abstract void addArrayResult(String name, double[] array) throws IOException;

    /**
     * Adds a boolean array to the InfoStore
     */
    abstract void addArrayResult(String name, boolean[] array) throws IOException;

    /**
     * Adds a List of String to the InfoStore
     */
    abstract void addListResult(String name, List<String> list) throws IOException;

    protected static int[] checkArray(int[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    protected static long[] checkArray(long[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    protected static float[] checkArray(float[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    protected static double[] checkArray(double[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    protected static boolean[] checkArray(boolean[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    protected static List<String> checkStringList(List<String> list) {
        if (list.size() > MAX_LIST_LENGTH) {
            return list.subList(0, MAX_LIST_LENGTH);
        }
        return list;
    }

    protected static String checkString(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() > MAX_STRING_LENGTH) {
            return value.substring(0, MAX_STRING_LENGTH);
        }
        return value;
    }

    protected static String checkName(String value) {
        if (value == null || value.isEmpty()) {
            throw new NullPointerException();
        }
        return value;
    }

    protected static boolean isDoubleNaNOrInfinite(Double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }
}
