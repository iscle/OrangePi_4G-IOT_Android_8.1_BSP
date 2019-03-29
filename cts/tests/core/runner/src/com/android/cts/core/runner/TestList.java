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
package com.android.cts.core.runner;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A list of the tests to run.
 */
class TestList {

    /** The set of test pacakges to run */
    private final Set<String> mIncludedPackages = new HashSet<>();

    /** The set of test packages not to run */
    private final Set<String> mExcludedPackages = new HashSet<>();

    /** The set of tests (classes or methods) to run */
    private final Set<String> mIncludedTests = new HashSet<>();

    /** The set of tests (classes or methods) not to run */
    private final Set<String> mExcludedTests = new HashSet<>();

    /** The list of all test classes to run (without filtering applied)*/
    private final Collection<Class<?>> classesToRun;

    public static TestList rootList(List<String> rootList) {

        // Run from the root test class.
        Set<String> classNamesToRun = new LinkedHashSet<>(rootList);
        Log.d(CoreTestRunner.TAG, "Running all tests rooted at " + classNamesToRun);

        List<Class<?>> classesToRun1 = getClasses(classNamesToRun);

        return new TestList(classesToRun1);
    }

    private static List<Class<?>> getClasses(Set<String> classNames) {
        // Populate the list of classes to run.
        List<Class<?>> classesToRun = new ArrayList<>();
        for (String className : classNames) {
            try {
                classesToRun.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Could not load class '" + className, e);
            }
        }
        return classesToRun;
    }

    /**
     * @param classes The list of classes to run.
     */
    public TestList(Collection<Class<?>> classes) {
        this.classesToRun = classes;
    }

    public void addIncludeTestPackages(Set<String> packageNameSet) {
        mIncludedPackages.addAll(packageNameSet);
    }

    public void addExcludeTestPackages(Set<String> packageNameSet) {
        mExcludedPackages.addAll(packageNameSet);
    }

    public void addIncludeTests(Set<String> testNameSet) {
        mIncludedTests.addAll(testNameSet);
    }

    public void addExcludeTests(Set<String> testNameSet) {
        mExcludedTests.addAll(testNameSet);
    }

    /**
     * Return all the classes to run.
     */
    public Class[] getClassesToRun() {
        return classesToRun.toArray(new Class[classesToRun.size()]);
    }

    /**
     * Return true if the test with the specified name should be run, false otherwise.
     */
    public boolean shouldRunTest(String testName) {

        int index = testName.indexOf('#');
        String className;
        if (index == -1) {
            className = testName;
        } else {
            className = testName.substring(0, index);
        }
        try {
            Class<?> testClass = Class.forName(className);
            Package testPackage = testClass.getPackage();
            String testPackageName = "";
            if (testPackage != null) {
                testPackageName = testPackage.getName();
            }

            boolean include =
                    (mIncludedPackages.isEmpty() || mIncludedPackages.contains(testPackageName)) &&
                    (mIncludedTests.isEmpty() || mIncludedTests.contains(className) ||
                            mIncludedTests.contains(testName));

            boolean exclude =
                    mExcludedPackages.contains(testPackageName) ||
                    mExcludedTests.contains(className) ||
                    mExcludedTests.contains(testName);

            return include && !exclude;
        } catch (ClassNotFoundException e) {
            Log.w("Could not load class '" + className, e);
            return false;
        }
    }
}
