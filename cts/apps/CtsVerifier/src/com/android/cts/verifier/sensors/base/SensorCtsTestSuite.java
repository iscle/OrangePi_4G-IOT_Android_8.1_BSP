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
 * limitations under the License
 */

package com.android.cts.verifier.sensors.base;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import android.content.Context;

import java.util.Enumeration;

/**
 * A wrapper class for a {@link TestSuite}.
 *
 * It provides a way to inject a {@link SensorCtsTestResult} during execution.
 */
class SensorCtsTestSuite extends TestSuite {
    private final Context mContext;
    private final TestSuite mWrappedTestSuite;

    public SensorCtsTestSuite(Context context, TestSuite testSuite) {
        mContext = context;
        mWrappedTestSuite = testSuite;
    }

    @Override
    public void run(TestResult testResult) {
        mWrappedTestSuite.run(new SensorCtsTestResult(mContext, testResult));
    }

    @Override
    public void addTest(Test test) {
        mWrappedTestSuite.addTest(test);
    }

    @Override
    public int countTestCases() {
        return mWrappedTestSuite.countTestCases();
    }

    @Override
    public String getName() {
        return mWrappedTestSuite.getName();
    }

    @Override
    public void runTest(Test test, TestResult testResult) {
        mWrappedTestSuite.runTest(test, testResult);
    }

    @Override
    public void setName(String name) {
        mWrappedTestSuite.setName(name);
    }

    @Override
    public Test testAt(int index) {
        return mWrappedTestSuite.testAt(index);
    }

    @Override
    public int testCount() {
        return mWrappedTestSuite.testCount();
    }

    @Override
    public Enumeration<Test> tests() {
        return mWrappedTestSuite.tests();
    }

    @Override
    public String toString() {
        return mWrappedTestSuite.toString();
    }
}
