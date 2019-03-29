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
package com.android.compatibility.common.deviceinfo;

/**
 * Example Objects for {@link DeviceInfo} test package.
 */
public final class ExampleObjects {

    // Must match DeviceInfo.MAX_STRING_VALUE_LENGTH and
    // DeviceInfo.MAX_ARRAY_LENGTH
    private static final int MAX_LENGTH = 1000;

    private static final String TEST_DEVICE_INFO_JSON = "{\n" +
        "  \"test_boolean\": true,\n" +
        "  \"test_double\": 1.23456789,\n" +
        "  \"test_int\": 123456789,\n" +
        "  \"test_long\": 9223372036854775807,\n" +
        "  \"test_string\": \"test string\",\n" +
        "  \"test_strings\": [\n" +
        "    \"test string 1\",\n" +
        "    \"test string 2\",\n" +
        "    \"test string 3\"\n" +
        "  ],\n" +
        "  \"test_group\": {\n" +
        "    \"test_boolean\": false,\n" +
        "    \"test_double\": 9.87654321,\n" +
        "    \"test_int\": 987654321,\n" +
        "    \"test_long\": 9223372036854775807,\n" +
        "    \"test_string\": \"test group string\",\n" +
        "    \"test_strings\": [\n" +
        "      \"test group string 1\",\n" +
        "      \"test group string 2\",\n" +
        "      \"test group string 3\"\n" +
        "    ]\n" +
        "  },\n" +
        "  \"test_groups\": [\n" +
        "    {\n" +
        "      \"test_string\": \"test groups string 1\",\n" +
        "      \"test_strings\": [\n" +
        "        \"test groups string 1-1\",\n" +
        "        \"test groups string 1-2\",\n" +
        "        \"test groups string 1-3\"\n" +
        "      ]\n" +
        "    },\n" +
        "    {\n" +
        "      \"test_string\": \"test groups string 2\",\n" +
        "      \"test_strings\": [\n" +
        "        \"test groups string 2-1\",\n" +
        "        \"test groups string 2-2\",\n" +
        "        \"test groups string 2-3\"\n" +
        "      ]\n" +
        "    },\n" +
        "    {\n" +
        "      \"test_string\": \"test groups string 3\",\n" +
        "      \"test_strings\": [\n" +
        "        \"test groups string 3-1\",\n" +
        "        \"test groups string 3-2\",\n" +
        "        \"test groups string 3-3\"\n" +
        "      ]\n" +
        "    }\n" +
        "  ],\n" +
        "  \"max_length_string\": \"%s\",\n" +
        "  \"max_num_ints\": [\n%s" +
        "  ]\n" +
        "}\n";

    public static String testDeviceInfoJson() {
        StringBuilder longStringSb = new StringBuilder();
        StringBuilder longArraySb = new StringBuilder();
        int lastNum = MAX_LENGTH - 1;
        for (int i = 0; i < MAX_LENGTH; i++) {
            longStringSb.append("a");
            longArraySb.append(String.format("    %d%s\n", i, ((i == lastNum)? "" : ",")));
        }
        return String.format(TEST_DEVICE_INFO_JSON,
            longStringSb.toString(), longArraySb.toString());
    }
}