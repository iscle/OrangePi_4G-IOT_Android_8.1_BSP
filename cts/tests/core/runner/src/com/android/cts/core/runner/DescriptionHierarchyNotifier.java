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

import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;

/**
 * A {@link Runner} that does not actually run any tests but simply fires events for all leaf
 * {@link Description} instances in the supplied {@link Description} hierarchy.
 */
class DescriptionHierarchyNotifier extends Runner {

    private final Description description;

    DescriptionHierarchyNotifier(Description description) {
        this.description = description;
    }

    @Override
    public Description getDescription() {
        return description;
    }

    @Override
    public void run(RunNotifier notifier) {
        generateListOfTests(notifier, description);
    }

    /**
     * Generates a list of tests to run by recursing over the {@link Description} hierarchy and
     * firing events to simulate the tests being run successfully.
     * @param runNotifier the notifier to which the events are sent.
     * @param description the description to traverse.
     */
    private void generateListOfTests(RunNotifier runNotifier, Description description) {
        List<Description> children = description.getChildren();
        if (children.isEmpty()) {
            runNotifier.fireTestStarted(description);
            runNotifier.fireTestFinished(description);
        } else {
            for (Description child : children) {
                generateListOfTests(runNotifier, child);
            }
        }
    }
}
