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

/**
 * Utility class for handling device ABIs
 */
public class AbiUtils {

    /**
     * Creates a unique id from the given ABI and name.
     * @param abi The ABI to use.
     * @param name The name to use.
     * @return a string which uniquely identifies a run.
     */
    public static String createId(String abi, String name) {
        return String.format("%s %s", abi, name);
    }

    /**
     * @return the abi portion of the test id.
     *         e.g. armeabi-v7a android.mytest = armeabi-v7a
     */
    public static String parseAbi(String id) {
        return parseId(id)[0];
    }

    /**
     * Parses a unique id into the ABI and name.
     * @param id The id to parse.
     * @return a string array containing the ABI and name.
     */
    public static String[] parseId(String id) {
        if (id == null || !id.contains(" ")) {
            return new String[] {"", ""};
        }
        return id.split(" ");
    }

    /**
     * @return the test name portion of the test id.
     *         e.g. armeabi-v7a android.mytest = android.mytest
     */
    public static String parseTestName(String id) {
        return parseId(id)[1];
    }

}
