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
 * Unit tests for {@link CaseResult}
 */
public class CaseResultTest extends TestCase {

    private static final String CLASS = "android.test.FoorBar";
    private static final String METHOD_1 = "testBlah1";
    private static final String METHOD_2 = "testBlah2";
    private static final String METHOD_3 = "testBlah3";
    private static final String MESSAGE = "Something small is not alright";
    private static final String STACK_TRACE = "Something small is not alright\n " +
            "at four.big.insects.Marley.sing(Marley.java:10)";
    private CaseResult mResult;

    @Override
    public void setUp() throws Exception {
        mResult = new CaseResult(CLASS);
    }

    @Override
    public void tearDown() throws Exception {
        mResult = null;
    }

    public void testAccessors() throws Exception {
        assertEquals("Incorrect case name", CLASS, mResult.getName());
    }

    public void testResultCreation() throws Exception {
        ITestResult testResult = mResult.getOrCreateResult(METHOD_1);
        // Should create one
        assertEquals("Expected one result", 1, mResult.getResults().size());
        assertTrue("Expected test result", mResult.getResults().contains(testResult));
        // Should not create another one
        ITestResult testResult2 = mResult.getOrCreateResult(METHOD_1);
        assertEquals("Expected the same result", testResult, testResult2);
        assertEquals("Expected one result", 1, mResult.getResults().size());
    }

    public void testResultReporting() throws Exception {
        ITestResult testResult = mResult.getOrCreateResult(METHOD_1);
        testResult.failed(STACK_TRACE);
        assertEquals("Expected status to be set", TestStatus.FAIL, testResult.getResultStatus());
        assertEquals("Expected message to be set", MESSAGE, testResult.getMessage());
        assertEquals("Expected stack to be set", STACK_TRACE, testResult.getStackTrace());
        testResult = mResult.getOrCreateResult(METHOD_2);
        testResult.passed(null);
        assertEquals("Expected status to be set", TestStatus.PASS, testResult.getResultStatus());
        assertEquals("Expected two results", 2, mResult.getResults().size());
    }

    public void testCountResults() throws Exception {
        mResult.getOrCreateResult(METHOD_1).failed(STACK_TRACE);
        mResult.getOrCreateResult(METHOD_2).failed(STACK_TRACE);
        mResult.getOrCreateResult(METHOD_3).passed(null);
        assertEquals("Expected two failures", 2, mResult.countResults(TestStatus.FAIL));
        assertEquals("Expected one pass", 1, mResult.countResults(TestStatus.PASS));
    }

    public void testMergeCase() throws Exception {
        mResult.getOrCreateResult(METHOD_1).failed(STACK_TRACE);
        mResult.getOrCreateResult(METHOD_2).passed(null);

        // Same case another test and passing results in method 2
        CaseResult otherResult = new CaseResult(CLASS);
        otherResult.getOrCreateResult(METHOD_1).passed(null);
        otherResult.getOrCreateResult(METHOD_2).passed(null);
        otherResult.getOrCreateResult(METHOD_3).failed(STACK_TRACE);

        mResult.mergeFrom(otherResult);
        assertEquals("Expected one result", 3, mResult.getResults().size());
        assertEquals("Expected one failures", 1, mResult.countResults(TestStatus.FAIL));
        assertEquals("Expected two pass", 2, mResult.countResults(TestStatus.PASS));
    }

     public void testMergeCase_passToFail() throws Exception {
        mResult.getOrCreateResult(METHOD_1).passed(null);

        // Same case another test and passing results in method 2
        CaseResult otherResult = new CaseResult(CLASS);
        otherResult.getOrCreateResult(METHOD_1).passed(null);
        otherResult.getOrCreateResult(METHOD_2).passed(null);
        otherResult.getOrCreateResult(METHOD_3).failed(STACK_TRACE);

        mResult.mergeFrom(otherResult);

        assertEquals("Expected one result", 3, mResult.getResults().size());
        assertEquals("Expected one failures", 1, mResult.countResults(TestStatus.FAIL));
        assertEquals("Expected two pass", 2, mResult.countResults(TestStatus.PASS));
    }

    public void testMergeCase_mismatchedModuleName() throws Exception {

        CaseResult otherResult = new CaseResult(CLASS + "foo");
        try {
            mResult.mergeFrom(otherResult);
            fail("Expected IlleglArgumentException");
        } catch (IllegalArgumentException expected) {}
    }
}
