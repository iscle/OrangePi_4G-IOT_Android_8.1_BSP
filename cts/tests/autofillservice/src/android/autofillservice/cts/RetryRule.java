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
 * limitations under the License.
 */

package android.autofillservice.cts;

import android.support.test.uiautomator.StaleObjectException;
import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Custom JUnit4 rule that retry tests when they fail due to a {@link RetryableException}.
 */
public class RetryRule implements TestRule {

    private static final String TAG = "RetryRule";
    private final int mMaxAttempts;

    public RetryRule(int maxAttempts) {
        if (maxAttempts < 2) {
            throw new IllegalArgumentException(
                    "Must retry at least once; otherwise, what's the point?");
        }
        mMaxAttempts = maxAttempts;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                Throwable caught = null;
                for (int i = 1; i <= mMaxAttempts; i++) {
                    try {
                        base.evaluate();
                        return;
                    } catch (RetryableException | StaleObjectException e) {
                        caught = e;
                        Log.w(TAG,
                                description.getDisplayName() + ": attempt " + i + " failed: " + e);
                    }
                }
                throw caught;
            }
        };
    }
}
