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

/**
 * An enum of possible test statuses.
 */
public enum TestStatus {
    PASS("pass"),
    FAIL("fail");

    private final String mValue;

    private TestStatus(String storedValue) {
        mValue = storedValue;
    }

    /**
     * Get the String representation of this test status that should be stored in
     * xml
     */
    public String getValue() {
       return mValue;
    }

    /**
     * Find the {@link TestStatus} corresponding to given string value
     * <p/>
     * Performs a case insensitive search
     *
     * @param value
     * @return the {@link TestStatus} or <code>null</code> if it could not be found
     */
    static TestStatus getStatus(String value) {
        for (TestStatus status : TestStatus.values()) {
            if (value.compareToIgnoreCase(status.getValue()) == 0) {
                return status;
            }
        }
        return null;
    }
}
