/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.experiments;

import android.support.annotation.VisibleForTesting;

/**
 * Experiments return values based on user, device and other criteria.
 */
public final class ExperimentFlag<T> {

    private static boolean sAllowOverrides = false;

    @VisibleForTesting
    public static void initForTest() {
        sAllowOverrides = true;
    }

    /** Returns a boolean experiment */
    public static ExperimentFlag<Boolean> createFlag(
            boolean defaultValue) {
        return new ExperimentFlag<>(
                defaultValue);
    }

    private final T mDefaultValue;

    private T mOverrideValue = null;
    private boolean mOverridden = false;

    private ExperimentFlag(
            T defaultValue) {
        mDefaultValue = defaultValue;
    }

    /** Returns value for this experiment */
    public T get() {
        return sAllowOverrides && mOverridden ? mOverrideValue : mDefaultValue;
    }

    @VisibleForTesting
    public void override(T t) {
        if (sAllowOverrides) {
            mOverridden = true;
            mOverrideValue = t;
        }
    }

    @VisibleForTesting
    public void resetOverride() {
        mOverridden = false;
    }



}
