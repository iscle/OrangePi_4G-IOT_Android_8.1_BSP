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

import junit.framework.TestCase;

/**
 * Unit tests for {@link TestFilter}
 */
public class TestFilterTest extends TestCase {

    private static final String NAME = "ModuleName";
    private static final String ABI = "mips64";
    private static final String TEST = "com.android.foobar.Blah#testAllTheThings";
    private static final String NAME_FILTER = String.format("%s", NAME);
    private static final String ABI_NAME_FILTER = String.format("%s %s", ABI, NAME);
    private static final String NAME_TEST_FILTER = String.format("%s %s", NAME, TEST);
    private static final String FULL_FILTER = String.format("%s %s %s", ABI, NAME, TEST);

    public void testParseNameFilter() {
        TestFilter filter = TestFilter.createFrom(NAME_FILTER);
        assertNull("Incorrect abi", filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertNull("Incorrect test", filter.getTest());
    }

    public void testParseAbiNameFilter() {
        TestFilter filter = TestFilter.createFrom(ABI_NAME_FILTER);
        assertEquals("Incorrect abi", ABI, filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertNull("Incorrect test", filter.getTest());
    }

    public void testParseNameTestFilter() {
        TestFilter filter = TestFilter.createFrom(NAME_TEST_FILTER);
        assertNull("Incorrect abi", filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertEquals("Incorrect test", TEST, filter.getTest());
    }

    public void testParseFullFilter() {
        TestFilter filter = TestFilter.createFrom(FULL_FILTER);
        assertEquals("Incorrect abi", ABI, filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertEquals("Incorrect test", TEST, filter.getTest());
    }

    public void testCreateNameFilter() {
        TestFilter filter = new TestFilter(null, NAME, null);
        assertEquals("Incorrect filter", NAME_FILTER, filter.toString());
    }

    public void testCreateAbiNameFilter() {
        TestFilter filter = new TestFilter(ABI, NAME, null);
        assertEquals("Incorrect filter", ABI_NAME_FILTER, filter.toString());
    }

    public void testCreateNameTestFilter() {
        TestFilter filter = new TestFilter(null, NAME, TEST);
        assertEquals("Incorrect filter", NAME_TEST_FILTER, filter.toString());
    }

    public void testCreateFullFilter() {
        TestFilter filter = new TestFilter(ABI, NAME, TEST);
        assertEquals("Incorrect filter", FULL_FILTER, filter.toString());
    }

}
