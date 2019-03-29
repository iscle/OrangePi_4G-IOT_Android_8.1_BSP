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

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

/**
 * Test runner to run a single JUnit test. It will output either [PASSED] or [FAILED] at the end.
 */
public class SingleJUnitTestRunner {
    private static String mUsage = "Usage: java -cp <classpath> SingleJUnitTestRunner" +
            " class#testmethod";
    private static final String PASSED_TEST_MARKER = "[ PASSED ]";
    private static final String FAILED_TEST_MARKER = "[ FAILED ]";

    public static void main(String... args) throws ClassNotFoundException {
        if (args.length != 1) {
            throw new IllegalArgumentException(mUsage);
        }
        String[] classAndMethod = args[0].split("#");
        if (classAndMethod.length != 2) {
            throw new IllegalArgumentException(mUsage);
        }
        Request request = Request.method(Class.forName(classAndMethod[0]),
                classAndMethod[1]);
        JUnitCore jUnitCore = new JUnitCore();
        Result result = jUnitCore.run(request);
        String status = result.wasSuccessful() ? PASSED_TEST_MARKER : FAILED_TEST_MARKER;
        System.out.println(String.format("%s %s.%s", status,
                classAndMethod[0], classAndMethod[1]));
    }
}
