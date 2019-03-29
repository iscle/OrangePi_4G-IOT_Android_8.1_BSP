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
package com.android.compatibility.common.util;

import com.android.tradefed.util.AbiUtils;

/**
 * Represents a filter for including and excluding tests.
 */
public class TestFilter {

    private final String mAbi;
    private final String mName;
    private final String mTest;

    /**
     * Builds a new {@link TestFilter} from the given string. Filters can be in one of four forms,
     * the instance will be initialized as;
     * -"name"                 -> abi = null, name = "name", test = null
     * -"name" "test..."       -> abi = null, name = "name", test = "test..."
     * -"abi" "name"           -> abi = "abi", name = "name", test = null
     * -"abi" "name" "test..." -> abi = "abi", name = "name", test = "test..."
     *
     * Test identifier can contain multiple parts, eg parameterized tests.
     *
     * @param filter the filter to parse
     * @return the {@link TestFilter}
     */
    public static TestFilter createFrom(String filter) {
        if (filter.isEmpty()) {
            throw new IllegalArgumentException("Filter was empty");
        }
        String[] parts = filter.split(" ");
        String abi = null, name = null, test = null;
        // Either:
        // <name>
        // <name> <test>
        // <abi> <name>
        // <abi> <name> <test>
        if (parts.length == 1) {
            name = parts[0];
        } else {
            int index = 0;
            if (AbiUtils.isAbiSupportedByCompatibility(parts[0])) {
                abi = parts[0];
                index++;
            }
            name = parts[index];
            index++;
            parts = filter.split(" ", index + 1);
            if (parts.length > index) {
                test = parts[index];
            }
        }
        return new TestFilter(abi, name, test);
    }

    /**
     * Creates a new {@link TestFilter} from the given parts.
     *
     * @param abi The ABI must be supported {@link AbiUtils#isAbiSupportedByCompatibility(String)}
     * @param name The module's name
     * @param test The test's identifier eg <package>.<class>#<method>
     */
    public TestFilter(String abi, String name, String test) {
        mAbi = abi;
        mName = name;
        mTest = test;
    }

    /**
     * Returns a String representation of this filter. This function is the inverse of
     * {@link TestFilter#createFrom(String)}.
     *
     * For a valid filter f;
     * <pre>
     * {@code
     * new TestFilter(f).toString().equals(f)
     * }
     * </pre>
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (mAbi != null) {
            sb.append(mAbi.trim());
            sb.append(" ");
        }
        if (mName != null) {
            sb.append(mName.trim());
        }
        if (mTest != null) {
            sb.append(" ");
            sb.append(mTest.trim());
        }
        return sb.toString();
    }

    /**
     * @return the abi of this filter, or null if not specified.
     */
    public String getAbi() {
        return mAbi;
    }

    /**
     * @return the module name of this filter, or null if not specified.
     */
    public String getName() {
        return mName;
    }

    /**
     * @return the test identifier of this filter, or null if not specified.
     */
    public String getTest() {
        return mTest;
    }

}
