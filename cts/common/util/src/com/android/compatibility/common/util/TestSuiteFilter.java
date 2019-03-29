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

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.util.Enumeration;
import java.util.List;
import java.util.Set;

public class TestSuiteFilter {

    private TestSuiteFilter() {}

    public static TestSuite createSuite(List<Class<?>> classes, Set<String> includes,
            Set<String> excludes) {
        return new FilterableTestSuite(classes, includes, excludes);
    }

    /**
     * A {@link TestSuite} that can filter which tests run, given the include and exclude filters.
     *
     * This had to be private inner class because the test runner would find it and think it was a
     * suite of tests, but it has no tests in it, causing a crash.
     */
    private static class FilterableTestSuite extends TestSuite {

        private Set<String> mIncludes;
        private Set<String> mExcludes;

        public FilterableTestSuite(List<Class<?>> classes, Set<String> includes,
                Set<String> excludes) {
            super(classes.toArray(new Class<?>[classes.size()]));
            mIncludes = includes;
            mExcludes = excludes;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int countTestCases() {
            return countTests(this);
        }

        private int countTests(Test test) {
            if (test instanceof TestSuite) {
                // If the test is a suite it could contain multiple tests, these need to be split
                // out into separate tests so they can be filtered
                TestSuite suite = (TestSuite) test;
                Enumeration<Test> enumerator = suite.tests();
                int count = 0;
                while (enumerator.hasMoreElements()) {
                    count += countTests(enumerator.nextElement());
                }
                return count;
            } else if (shouldRun(test)) {
                return 1;
            }
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void runTest(Test test, TestResult result) {
            runTests(test, result);
        }

        private void runTests(Test test, TestResult result) {
            if (test instanceof TestSuite) {
                // If the test is a suite it could contain multiple tests, these need to be split
                // out into separate tests so they can be filtered
                TestSuite suite = (TestSuite) test;
                Enumeration<Test> enumerator = suite.tests();
                while (enumerator.hasMoreElements()) {
                    runTests(enumerator.nextElement(), result);
                }
            } else if (shouldRun(test)) {
                test.run(result);
            }
        }

        private boolean shouldRun(Test test) {
            String fullName = test.toString();
            String[] parts = fullName.split("[\\(\\)]");
            String className = parts[1];
            String methodName = String.format("%s#%s", className, parts[0]);
            int index = className.lastIndexOf('.');
            String packageName = index < 0 ? "" : className.substring(0, index);

            if (mExcludes.contains(packageName)) {
                // Skip package because it was excluded
                return false;
            }
            if (mExcludes.contains(className)) {
                // Skip class because it was excluded
                return false;
            }
            if (mExcludes.contains(methodName)) {
                // Skip method because it was excluded
                return false;
            }
            return mIncludes.isEmpty()
                    || mIncludes.contains(methodName)
                    || mIncludes.contains(className)
                    || mIncludes.contains(packageName);
        }
    }
}
