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

import static android.autofillservice.cts.Helper.runShellCommand;

import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
/**
 * Custom JUnit4 rule that improves autofill-related logging by:
 *
 * <ol>
 *   <li>Setting logging level to verbose before test start.
 *   <li>Call {@code dumpsys autofill} in case of failure.
 * </ol>
 */
public class AutofillLoggingTestRule implements TestRule {

    private final String mTag;

    public AutofillLoggingTestRule(String tag) {
        mTag = tag;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                final String levelBefore = runShellCommand("cmd autofill get log_level");
                if (!levelBefore.equals("verbose")) {
                    runShellCommand("cmd autofill set log_level verbose");
                }
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    final String dump = runShellCommand("dumpsys autofill");
                    Log.e(mTag, "dump for " + description.getDisplayName() + ": \n" + dump, t);
                    throw t;
                } finally {
                    if (!levelBefore.equals("verbose")) {
                        runShellCommand("cmd autofill set log_level %s", levelBefore);
                    }
                }
            }
        };
    }

}
