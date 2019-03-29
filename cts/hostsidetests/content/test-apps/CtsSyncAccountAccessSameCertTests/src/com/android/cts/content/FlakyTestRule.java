/*
 * Copyright (C) 2017 The Android Open Source Project
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
 */

package android.content.cts;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Rule for running flaky tests that runs the test up to attempt
 * count and if one run succeeds reports the tests as passing.
 */
// TODO: Move this puppy in a common place, so ppl can use it.
public class FlakyTestRule implements TestRule {
    private final int mAttemptCount;

    public FlakyTestRule(int attemptCount) {
        mAttemptCount = attemptCount;
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable throwable = null;
                for (int i = 0; i < mAttemptCount; i++) {
                    try {
                        statement.evaluate();
                        return;
                    } catch (Throwable t) {
                        throwable = t;
                    }
                }
                throw throwable;
            };
        };
    }
}
