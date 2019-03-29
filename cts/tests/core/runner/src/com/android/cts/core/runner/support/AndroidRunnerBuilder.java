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
package com.android.cts.core.runner.support;

import android.support.test.internal.runner.junit3.AndroidJUnit3Builder;
import android.support.test.internal.runner.junit3.AndroidSuiteBuilder;
import android.support.test.internal.runner.junit4.AndroidAnnotatedBuilder;
import android.support.test.internal.runner.junit4.AndroidJUnit4Builder;
import android.support.test.internal.util.AndroidRunnerParams;

import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.internal.builders.AnnotatedBuilder;
import org.junit.internal.builders.IgnoredBuilder;
import org.junit.internal.builders.JUnit3Builder;
import org.junit.internal.builders.JUnit4Builder;
import org.junit.runners.model.RunnerBuilder;

/**
 * A {@link RunnerBuilder} that can handle all types of tests.
 */
// A copy of package private class android.support.test.internal.runner.AndroidRunnerBuilder.
// Copied here so that it can be extended.
class AndroidRunnerBuilder extends AllDefaultPossibilitiesBuilder {

    private final AndroidJUnit3Builder mAndroidJUnit3Builder;
    private final AndroidJUnit4Builder mAndroidJUnit4Builder;
    private final AndroidSuiteBuilder mAndroidSuiteBuilder;
    private final AndroidAnnotatedBuilder mAndroidAnnotatedBuilder;
    // TODO: customize for Android ?
    private final IgnoredBuilder mIgnoredBuilder;

    /**
     * @param runnerParams {@link AndroidRunnerParams} that stores common runner parameters
     */
    // Added canUseSuiteMethod parameter.
    AndroidRunnerBuilder(AndroidRunnerParams runnerParams, boolean canUseSuiteMethod) {
        super(canUseSuiteMethod);
        mAndroidJUnit3Builder = new AndroidJUnit3Builder(runnerParams);
        mAndroidJUnit4Builder = new AndroidJUnit4Builder(runnerParams);
        mAndroidSuiteBuilder = new AndroidSuiteBuilder(runnerParams);
        mAndroidAnnotatedBuilder = new AndroidAnnotatedBuilder(this, runnerParams);
        mIgnoredBuilder = new IgnoredBuilder();
    }

    @Override
    protected JUnit4Builder junit4Builder() {
        return mAndroidJUnit4Builder;
    }

    @Override
    protected JUnit3Builder junit3Builder() {
        return mAndroidJUnit3Builder;
    }

    @Override
    protected AnnotatedBuilder annotatedBuilder() {
        return mAndroidAnnotatedBuilder;
    }

    @Override
    protected IgnoredBuilder ignoredBuilder() {
        return mIgnoredBuilder;
    }

    @Override
    protected RunnerBuilder suiteMethodBuilder() {
        return mAndroidSuiteBuilder;
    }
}
