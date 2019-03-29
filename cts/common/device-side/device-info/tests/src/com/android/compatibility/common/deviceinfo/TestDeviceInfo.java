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

import android.os.Bundle;

import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.android.compatibility.common.util.DeviceInfoStore;

/**
 * Collector for testing DeviceInfo
 */
public class TestDeviceInfo extends DeviceInfo {

    @Override
    protected void setUp() throws Exception {
        mActivityList = new HashSet<String>();
        mActivityList.add(getClass().getName());
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {

        // Test primitive results
        store.addResult("test_boolean", true);
        store.addResult("test_double", 1.23456789);
        store.addResult("test_int", 123456789);
        store.addResult("test_long", Long.MAX_VALUE);
        store.addResult("test_string", "test string");
        List<String> list = new ArrayList<>();
        list.add("test string 1");
        list.add("test string 2");
        list.add("test string 3");
        store.addListResult("test_strings", list);

        // Test group
        store.startGroup("test_group");
        store.addResult("test_boolean", false);
        store.addResult("test_double", 9.87654321);
        store.addResult("test_int", 987654321);
        store.addResult("test_long", Long.MAX_VALUE);
        store.addResult("test_string", "test group string");
        list = new ArrayList<>();
        list.add("test group string 1");
        list.add("test group string 2");
        list.add("test group string 3");
        store.addListResult("test_strings", list);
        store.endGroup(); // test_group

        // Test array of groups
        store.startArray("test_groups");
        for (int i = 1; i < 4; i++) {
            store.startGroup();
            store.addResult("test_string", "test groups string " + i);
            list = new ArrayList<>();
            list.add("test groups string " + i + "-1");
            list.add("test groups string " + i + "-2");
            list.add("test groups string " + i + "-3");
            store.addListResult("test_strings", list);
            store.endGroup();
        }
        store.endArray(); // test_groups

        // Test max
        StringBuilder sb = new StringBuilder();
        int[] arr = new int[1001];
        for (int i = 0; i < 1001; i++) {
            sb.append("a");
            arr[i] = i;
        }
        store.addResult("max_length_string", sb.toString());
        store.addArrayResult("max_num_ints", arr);
    }
}
