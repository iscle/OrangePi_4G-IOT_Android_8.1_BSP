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

import android.support.test.runner.AndroidJUnitRunner;

/**
 * Constants used to communicate to and from {@link AndroidJUnitRunner}.
 */
public interface AndroidJUnitRunnerConstants {

    /**
     * The name of the file containing the names of the tests to run.
     *
     * <p>This is an internal constant used within
     * {@code android.support.test.internal.runner.RunnerArgs}, which is used on both the server
     * and
     * client side. The constant is used when there are too many test names to pass on the command
     * line, in which case they are stored in a file that is pushed to the device and then the
     * location of that file is passed in this argument. The {@code RunnerArgs} on the client will
     * read the contents of that file in order to retrieve the list of names and then return that
     * to
     * its client without the client being aware of how that was done.
     */
    String ARGUMENT_TEST_FILE = "testFile";

    /**
     * The name of the file containing the names of the tests not to run.
     *
     * <p>This is an internal constant used within
     * {@code android.support.test.internal.runner.RunnerArgs}, which is used on both the server
     * and
     * client side. The constant is used when there are too many test names to pass on the command
     * line, in which case they are stored in a file that is pushed to the device and then the
     * location of that file is passed in this argument. The {@code RunnerArgs} on the client will
     * read the contents of that file in order to retrieve the list of names and then return that
     * to
     * its client without the client being aware of how that was done.
     */
    String ARGUMENT_NOT_TEST_FILE = "notTestFile";

    /**
     * A comma separated list of the names of test classes to run.
     *
     * <p>The equivalent constant in {@code InstrumentationTestRunner} is hidden and so not
     * available
     * through the public API.
     */
    String ARGUMENT_TEST_CLASS = "class";

    /**
     * A comma separated list of the names of test classes not to run
     */
    String ARGUMENT_NOT_TEST_CLASS = "notClass";

    /**
     * A comma separated list of the names of test packages to run.
     *
     * <p>The equivalent constant in {@code InstrumentationTestRunner} is hidden and so not
     * available
     * through the public API.
     */
    String ARGUMENT_TEST_PACKAGE = "package";

    /**
     * A comma separated list of the names of test packages not to run.
     */
    String ARGUMENT_NOT_TEST_PACKAGE = "notPackage";

    /**
     * Log the results as if the tests were executed but don't actually run the tests.
     *
     * <p>The equivalent constant in {@code InstrumentationTestRunner} is private.
     */
    String ARGUMENT_LOG_ONLY = "log";

    /**
     * Wait for the debugger before starting.
     *
     * <p>There is no equivalent constant in {@code InstrumentationTestRunner} but the string is
     * used
     * within that class.
     */
    String ARGUMENT_DEBUG = "debug";

    /**
     * Only count the number of tests to run.
     *
     * <p>There is no equivalent constant in {@code InstrumentationTestRunner} but the string is
     * used
     * within that class.
     */
    String ARGUMENT_COUNT = "count";

    /**
     * The per test timeout value.
     */
    String ARGUMENT_TIMEOUT = "timeout_msec";

    /**
     * Token representing how long (in seconds) the current test took to execute.
     *
     * <p>The equivalent constant in {@code InstrumentationTestRunner} is private.
     */
    String REPORT_KEY_RUNTIME = "runtime";

    /**
     * An identifier for tests run using this class.
     */
    String REPORT_VALUE_ID = "CoreTestRunner";
}
