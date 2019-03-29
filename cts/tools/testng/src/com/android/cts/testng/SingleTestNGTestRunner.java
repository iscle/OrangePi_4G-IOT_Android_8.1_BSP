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

package com.android.cts.testng;

import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Test runner to run a single TestNG test. It will output either [PASSED] or [FAILED] at the end.
 */
public class SingleTestNGTestRunner {
    private static String mUsage = "Usage: java -cp <classpath> SingleTestNGTestRunner" +
            " class#testMethod";
    private static final String PASSED_TEST_MARKER = "[ PASSED ]";
    private static final String FAILED_TEST_MARKER = "[ FAILED ]";

    public static void main(String... args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(mUsage);
        }
        String[] classAndMethod = args[0].split("#");
        if (classAndMethod.length != 2) {
            throw new IllegalArgumentException(mUsage);
        }

        TestNG testng = createTestNG(classAndMethod[0], classAndMethod[1]);
        testng.run();
        String status = (!testng.hasFailure()) ? PASSED_TEST_MARKER : FAILED_TEST_MARKER;
        System.out.println(String.format("%s %s.%s", status,
                classAndMethod[0], classAndMethod[1]));
    }

    private static org.testng.TestNG createTestNG(String klass, String method) {
        org.testng.TestNG testng = new org.testng.TestNG();
        testng.setUseDefaultListeners(false);  // Don't create the testng-specific HTML/XML reports.
        testng.addListener(new SingleTestNGTestRunListener());

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
}
