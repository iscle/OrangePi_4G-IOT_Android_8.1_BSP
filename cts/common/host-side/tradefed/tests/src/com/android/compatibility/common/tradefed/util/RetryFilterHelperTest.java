/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.util;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for {@link RetryFilterHelper}
 */
@RunWith(JUnit4.class)
public class RetryFilterHelperTest {

    private static final String TEST_STRING = "abcd";
    private static final RetryType TEST_RETRY_TYPE = RetryType.FAILED;

    /**
     * Tests that options can be internally copied using
     * {@link RetryFilterHelper#setAllOptionsFrom(RetryFilterHelper)}.
     */
    @Test
    public void testSetAllOptionsFrom() throws Exception {
        RetryFilterHelper helper = new RetryFilterHelper(null, 0);
        RetryFilterHelper otherObj = new RetryFilterHelper(null, 0, TEST_STRING,
                new HashSet<String>(), new HashSet<String>(), null ,null, null, null);
        helper.setAllOptionsFrom(otherObj);
        assertEquals(TEST_STRING, helper.mSubPlan);
    }

    /**
     * Tests that options can be cleared using {@link RetryFilterHelper#clearOptions()}.
     */
    @Test
    public void testClearOptions() throws Exception {
        Set<String> include = new HashSet<>();
        include.add(TEST_STRING);
        Set<String> exclude = new HashSet<>();
        exclude.add(TEST_STRING);
        RetryFilterHelper helper = new RetryFilterHelper(null, 0, TEST_STRING, include, exclude,
                TEST_STRING, TEST_STRING, TEST_STRING, TEST_RETRY_TYPE);
        helper.clearOptions();
        assertTrue(helper.mSubPlan == null);
        assertTrue(helper.mIncludeFilters.isEmpty());
        assertTrue(helper.mExcludeFilters.isEmpty());
        assertTrue(helper.mAbiName == null);
        assertTrue(helper.mModuleName == null);
        assertTrue(helper.mTestName == null);
        assertTrue(helper.mRetryType == null);
    }

}
