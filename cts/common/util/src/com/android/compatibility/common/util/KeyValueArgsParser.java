/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.HashMap;

/**
 * Parses an array of arguments into a HashMap.
 *
 * This class assumed the arguments are in the form "-<key> <value> ..."
 */
public class KeyValueArgsParser {

    private KeyValueArgsParser() {}

    public static HashMap<String, String> parse(String[] args) {
        final HashMap<String, String> map = new HashMap<String, String>();
        String key = null;
        for (String s : args) {
            if (key == null) {
                if (!s.startsWith("-")) {
                    throw new RuntimeException("Invalid Key: " + s);
                }
                key = s;
            } else {
                map.put(key, s);
                key = null;
            }
        }
        if (key != null) {
            throw new RuntimeException("Left over key");
        }
        return map;
    }
}
