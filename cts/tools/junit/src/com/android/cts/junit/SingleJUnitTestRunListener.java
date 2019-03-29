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

package com.android.cts.junit;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class SingleJUnitTestRunListener extends RunListener {
    private static class Prefixes {
        @SuppressWarnings("unused")
        private static final String INFORMATIONAL_MARKER = "[----------]";
        private static final String START_TEST_RUN_MARKER = "[==========] Running";
        private static final String TEST_RUN_MARKER = "[==========]";
        private static final String START_TEST_MARKER = "[ RUN      ]";
        private static final String OK_TEST_MARKER = "[       OK ]";
        private static final String FAILED_TEST_MARKER = "[  FAILED  ]";
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        String status = result.wasSuccessful() ? Prefixes.OK_TEST_MARKER
                : Prefixes.FAILED_TEST_MARKER;
        System.out.println(status);
    }

    @Override
    public void testStarted(Description description) throws Exception {
        System.out.println(String.format("%s %s.%s", Prefixes.START_TEST_MARKER,
                description.getClassName(), description.getMethodName()));
    }

    @Override
    public void testFinished(Description description) throws Exception {
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
    }

    @Override
    public void testIgnored(Description description) throws Exception {
    }
}
