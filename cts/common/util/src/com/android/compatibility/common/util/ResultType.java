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

package com.android.compatibility.common.util;

/**
 * Enum for distinguishing results.
 */
public enum ResultType {
    /** Lower score is better. */
    LOWER_BETTER,
    /** Higher score is better. */
    HIGHER_BETTER,
    /** This value is not directly correlated with score. */
    NEUTRAL,
    /** Presence of this type requires some attention although it may not be an error. */
    WARNING;

    /**
     * @return a string to be used in the report.
     */
    public String toReportString() {
        return name().toLowerCase();
    }

    /**
     * Returns a {@link ResultType} given a string from the report.
     */
    public static ResultType parseReportString(String value) {
        return ResultType.valueOf(value.toUpperCase());
    }
}
