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

package android.platform.test.helpers.listeners;

import android.platform.test.helpers.IStandardAppHelper;
import android.support.annotation.Nullable;
import android.util.Log;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;

/**
 * A JUnit {@code TestWatcher} for collecting screenshots and UI XML files on test failure.
 */
public class FailureTestWatcher extends TestWatcher {
    private static final String LOG_TAG = FailureTestWatcher.class.getSimpleName();
    private static final String SCREENSHOT_NAME_FORMAT = "%s_%s";

    @Nullable
    private IStandardAppHelper mHelper;

    public void setHelper (IStandardAppHelper helper) {
        mHelper = helper;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        try {
            if (!mHelper.captureScreenshot(String.format(SCREENSHOT_NAME_FORMAT,
                    description.getClassName(), description.getMethodName()))) {
                Log.e(LOG_TAG, "Failed to capture a screenshot for unknown reasons.");
            }
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Failed to capture a screenshot.", ioe);
        }
    }
}
