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
package com.android.cts.delegate;

import android.test.MoreAsserts;
import junit.framework.Assert;

/**
 * Utils class for delegation tests.
 */
public class DelegateTestUtils {

    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    public static void assertExpectException(Class<? extends Throwable> expectedExceptionType,
            String expectedExceptionMessageRegex, ExceptionRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            Assert.assertTrue("Expected " + expectedExceptionType.getName() + " but caught " + e,
                expectedExceptionType.isAssignableFrom(e.getClass()));
            if (expectedExceptionMessageRegex != null) {
                MoreAsserts.assertContainsRegex(expectedExceptionMessageRegex, e.getMessage());
            }
            return; // Pass
        }
        Assert.fail("Expected " + expectedExceptionType.getName() + " was not thrown");
    }
}
