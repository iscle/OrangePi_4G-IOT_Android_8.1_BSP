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
package com.android.tv.testing.uihelper;

import static junit.framework.Assert.assertTrue;

import android.support.test.uiautomator.SearchCondition;
import android.support.test.uiautomator.UiObject2;

/**
 * Asserts for {@link UiObject2}s.
 */
public final class UiObject2Asserts {

    /**
     * Assert that {@code searchCondition} becomes true within
     * {@value Constants#MAX_SHOW_DELAY_MILLIS} milliseconds.
     *
     * @param uiObject        the device under test.
     * @param searchCondition the condition to wait for.
     */
    public static void assertWaitForCondition(UiObject2 uiObject,
            SearchCondition<Boolean> searchCondition) {
        assertWaitForCondition(uiObject, searchCondition, Constants.MAX_SHOW_DELAY_MILLIS);
    }

    /**
     * Assert that {@code searchCondition} becomes true within {@code timeout} milliseconds.
     *
     * @param uiObject        the device under test.
     * @param searchCondition the condition to wait for.
     */
    public static void assertWaitForCondition(UiObject2 uiObject,
            SearchCondition<Boolean> searchCondition, long timeout) {
        long adjustedTimeout = getAdjustedTimeout(timeout);
        boolean result = uiObject.wait(searchCondition, adjustedTimeout);
        assertTrue(searchCondition + " not true after " + timeout / 1000.0 + " seconds.", result);
    }

    public static long getAdjustedTimeout(long timeout) {
        return timeout + Math.max(
                Constants.MIN_EXTRA_TIMEOUT, (long) (timeout * Constants.EXTRA_TIMEOUT_PERCENT));
    }

    private UiObject2Asserts() {
    }
}
