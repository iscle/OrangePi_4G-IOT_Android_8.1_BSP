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
 * limitations under the License
 */

package com.android.tv.common.feature;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.tv.common.TvCommonUtils;

/**
 * When run in a test harness this feature can be turned on or off, overriding the normal value.
 *
 * <p><b>Warning</b> making a feature testable will cause the code to stay in the APK and
 * could leak unreleased features.
 */
public class TestableFeature implements Feature {
    private final static String TAG = "TestableFeature";
    private final static String DETAIL_MESSAGE
            = "TestableFeatures should only be changed in tests.";

    private final Feature mDelegate;
    private Boolean mTestValue = null;

    /**
     * Creates testable feature.
     */
    public static TestableFeature createTestableFeature(Feature delegate) {
        return new TestableFeature(delegate);
    }

    /**
     * Creates testable feature with initial value.
     */
    public static TestableFeature createTestableFeature(Feature delegate, Boolean initialValue) {
        return new TestableFeature(delegate, initialValue);
    }

    private TestableFeature(Feature delegate) {
        mDelegate = delegate;
    }

    private TestableFeature(Feature delegate, Boolean initialValue) {
        mDelegate = delegate;
        mTestValue = initialValue;
    }

    @VisibleForTesting
    public void enableForTest() {
        if (!TvCommonUtils.isRunningInTest()) {
            Log.e(TAG, "Not enabling for test:" + this,
                    new IllegalStateException(DETAIL_MESSAGE));
        } else {
            mTestValue = true;
        }
    }

    @VisibleForTesting
    public void disableForTests() {
        if (!TvCommonUtils.isRunningInTest()) {
            Log.e(TAG, "Not disabling for test: " + this,
                    new IllegalStateException(DETAIL_MESSAGE));
        } else {
            mTestValue = false;
        }
    }

    @VisibleForTesting
    public void resetForTests() {
        if (!TvCommonUtils.isRunningInTest()) {
            Log.e(TAG, "Not resetting feature: " + this, new IllegalStateException(DETAIL_MESSAGE));
        } else {
            mTestValue = null;
        }
    }

    @Override
    public boolean isEnabled(Context context) {
        if (TvCommonUtils.isRunningInTest() && mTestValue != null) {
            return mTestValue;
        }
        return mDelegate.isEnabled(context);
    }

    @Override
    public String toString() {
        String msg = mDelegate.toString();
        if (TvCommonUtils.isRunningInTest()) {
            if (mTestValue == null) {
                msg = "Testable Feature is unchanged: " + msg;
            } else {
                msg = "Testable Feature is " + (mTestValue ? "on" : "off") + " was " + msg;
            }
        }
        return msg;
    }
}
