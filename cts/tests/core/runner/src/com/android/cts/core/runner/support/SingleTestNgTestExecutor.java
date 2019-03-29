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

import android.util.Log;

import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test executor to run a single TestNG test method.
 */
public class SingleTestNgTestExecutor {
    // Execute any method which is in the class klass.
    // The klass is passed in separately to handle inherited methods only.
    // Returns true if all tests pass, false otherwise.
    public static Result execute(Class<?> klass, String methodName) {
        if (klass == null) {
          throw new NullPointerException("klass must not be null");
        }

        if (methodName == null) {
          throw new NullPointerException("methodName must not be null");
        }

        //if (!method.getDeclaringClass().isAssignableFrom(klass)) {
        //  throw new IllegalArgumentException("klass must match method's declaring class");
        //}

        SingleTestNGTestRunListener listener = new SingleTestNGTestRunListener();

        // Although creating a new testng "core" every time might seem heavyweight, in practice
        // it seems to take a mere few milliseconds at most.
        // Since we're running all the parameteric combinations of a test,
        // this ends up being neglible relative to that.
        TestNG testng = createTestNG(klass.getName(), methodName, listener);
        testng.run();

        if (listener.getNumTestStarted() <= 0) {
          // It's possible to be invoked here with an arbitrary method name
          // so print out a warning incase TestNG actually had a no-op.
          Log.w("TestNgExec", "execute class " + klass.getName() + ", method " + methodName +
              " had 0 tests executed. Not a test method?");
        }

        return new Result(testng.hasFailure(), listener.getFailures());
    }

    private static org.testng.TestNG createTestNG(String klass, String method,
            SingleTestNGTestRunListener listener) {
        org.testng.TestNG testng = new org.testng.TestNG();
        testng.setUseDefaultListeners(false);  // Don't create the testng-specific HTML/XML reports.
        // It still prints the X/Y tests succeeded/failed summary to stdout.

        // We don't strictly need this listener for CTS, but having it print SUCCESS/FAIL
        // makes it easier to diagnose which particular combination of a test method had failed
        // from looking at device logcat.
        testng.addListener(listener);

        /* Construct the following equivalent XML configuration:
         *
         * <!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd" >
         * <suite>
         *   <test>
         *     <classes>
         *       <class name="$klass">
         *         <include name="$method" />
         *       </class>
         *     </classes>
         *   </test>
         * </suite>
         *
         * This will ensure that only a single klass/method is being run by testng.
         * (It can still be run multiple times due to @DataProvider, with different parameters
         * each time)
         */
        List<XmlSuite> suites = new ArrayList<>();
        XmlSuite the_suite = new XmlSuite();
        XmlTest the_test = new XmlTest(the_suite);
        XmlClass the_class = new XmlClass(klass);
        XmlInclude the_include = new XmlInclude(method);

        the_class.getIncludedMethods().add(the_include);
        the_test.getXmlClasses().add(the_class);
        suites.add(the_suite);
        testng.setXmlSuites(suites);

        return testng;
    }

    public static class Result {
        private final boolean hasFailure;
        private final Map<String,Throwable> failures;


        Result(boolean hasFailure, Map<String, Throwable> failures) {
            this.hasFailure = hasFailure;
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
        }

        public boolean hasFailure() {
            return hasFailure;
        }

        public Map<String, Throwable> getFailures() {
            return failures;
        }
    }
}
