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

import android.os.Bundle;
import android.util.Log;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runners.ParentRunner;
import org.junit.runners.Suite;
import vogar.Expectation;
import vogar.ExpectationStore;
import vogar.ModeId;
import vogar.Result;

/**
 * Filter out tests/classes that are not requested or which are expected to fail.
 *
 * <p>This filter has to handle both a hierarchy of {@code Description descriptions} that looks
 * something like this:
 * <pre>
 * Suite
 *     Suite
 *         Suite
 *             ParentRunner
 *                 Test
 *                 ...
 *             ...
 *         ParentRunner
 *             Test
 *             ...
 *         ...
 *     Suite
 *         ParentRunner
 *             Test
 *             ...
 *         ...
 *     ...
 * </pre>
 *
 * <p>It cannot filter out the non-leaf nodes in the hierarchy, i.e. {@link Suite} and
 * {@link ParentRunner}, as that would prevent it from traversing the hierarchy and finding
 * the leaf nodes.
 */
class ExpectationBasedFilter extends Filter {

    static final String TAG = "ExpectationBasedFilter";

    private static final String ARGUMENT_EXPECTATIONS = "core-expectations";

    private static final Splitter CLASS_LIST_SPLITTER = Splitter.on(',').trimResults();

    private final ExpectationStore expectationStore;

    private static List<String> getExpectationResourcePaths(Bundle args) {
        return CLASS_LIST_SPLITTER.splitToList(args.getString(ARGUMENT_EXPECTATIONS));
    }

    public ExpectationBasedFilter(Bundle args) {
        ExpectationStore expectationStore = null;
        try {
            // Get the set of resource names containing the expectations.
            Set<String> expectationResources = new LinkedHashSet<>(
                getExpectationResourcePaths(args));
            Log.i(TAG, "Loading expectations from: " + expectationResources);
            expectationStore = ExpectationStore.parseResources(
                getClass(), expectationResources, ModeId.DEVICE);
        } catch (IOException e) {
            Log.e(TAG, "Could not initialize ExpectationStore: ", e);
        }

        this.expectationStore = expectationStore;
    }

    @Override
    public boolean shouldRun(Description description) {
        // Only filter leaf nodes. The description is for a test if and only if it is a leaf node.
        // Non-leaf nodes must not be filtered out as that would prevent leaf nodes from being
        // visited in the case when we are traversing the hierarchy of classes.
        Description testDescription = getTestDescription(description);
        if (testDescription != null) {
            String className = testDescription.getClassName();
            String methodName = testDescription.getMethodName();
            String testName = className + "#" + methodName;

            if (expectationStore != null) {
                Expectation expectation = expectationStore.get(testName);
                if (expectation.getResult() != Result.SUCCESS) {
                    Log.d(CoreTestRunner.TAG, "Excluding test " + testDescription
                            + " as it matches expectation: " + expectation);
                    return false;
                }
            }
        }

        return true;
    }

    private Description getTestDescription(Description description) {
        List<Description> children = description.getChildren();
        // An empty description is by definition a test.
        if (children.isEmpty()) {
            return description;
        }

        // Handle initialization errors that were wrapped in an ErrorReportingRunner as a special
        // case. This is needed because ErrorReportingRunner is treated as a suite of Throwables,
        // (where each Throwable corresponds to a test called initializationError) and so its
        // description contains children, one for each Throwable, and so is not treated as a test
        // to filter. Unfortunately, it does not support Filterable so this filter is never applied
        // to its children.
        // See https://github.com/junit-team/junit/issues/1253
        Description child = children.get(0);
        String methodName = child.getMethodName();
        if ("initializationError".equals(methodName)) {
            return child;
        }

        return null;
    }

    @Override
    public String describe() {
        return "TestFilter";
    }
}
