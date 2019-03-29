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

import junit.framework.TestCase;

/**
 * Unit tests for {@link TestResult}
 */
public class TestResultTest extends TestCase {

    private static final String CLASS = "android.test.FoorBar";
    private static final String METHOD_1 = "testBlah1";
    private static final String TEST_1 = String.format("%s#%s", CLASS, METHOD_1);
    private CaseResult mCase;
    private TestResult mResult;

    @Override
    public void setUp() throws Exception {
        mCase = new CaseResult(CLASS);
        mResult = new TestResult(mCase, METHOD_1);
    }

    @Override
    public void tearDown() throws Exception {
        mResult = null;
    }

    public void testAccessors() throws Exception {
        assertEquals("Incorrect test name", METHOD_1, mResult.getName());
        assertEquals("Incorrect full name", TEST_1, mResult.getFullName());
    }

}