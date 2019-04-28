/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.testinput.instrument;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.testing.Constants;
import com.android.tv.testinput.TestTvInputService;
import com.android.tv.testinput.TestTvInputSetupActivity;

/**
 * An instrumentation utility to set up the needed inputs, channels, programs and other settings
 * for automated unit tests.
 *
 * <p><pre>{@code
 * adb shell am instrument \
 *   -e testSetupMode {func,jank,unit} \
 *   -w com.android.tv.testinput/.instrument.TestSetupInstrumentation
 * }</pre>
 *
 * <p>Optional arguments are:
 * <pre>
 *     -e channelCount number
 * </pre>
 */
public class TestSetupInstrumentation extends Instrumentation {
    private static final String TAG = "TestSetupInstrument";
    private static final String TEST_SETUP_MODE_ARG = "testSetupMode";
    private static final String CHANNEL_COUNT_ARG = "channelCount";
    private Bundle mArguments;
    private String mInputId;

    /**
     * Fails an instrumentation request.
     *
     * @param errMsg an error message
     */
    protected void fail(String errMsg) {
        Log.e(TAG, errMsg);
        Bundle result = new Bundle();
        result.putString("error", errMsg);
        finish(Activity.RESULT_CANCELED, result);
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            mInputId = TestTvInputService.buildInputId(getContext());
            setup();
            finish(Activity.RESULT_OK, new Bundle());
        } catch (TestSetupException e) {
            fail(e.getMessage());
        }
    }

    private void setup() throws TestSetupException {
        final String testSetupMode = mArguments.getString(TEST_SETUP_MODE_ARG);
        if (TextUtils.isEmpty(testSetupMode)) {
            Log.i(TAG, "Performing no setup actions because " + TEST_SETUP_MODE_ARG
                    + " was not passed as an argument");
        } else {
            Log.i(TAG, "Running setup for " + testSetupMode + " tests.");
            int channelCount;
            switch (testSetupMode) {
                case "func":
                    channelCount = getArgumentAsInt(CHANNEL_COUNT_ARG,
                            Constants.FUNC_TEST_CHANNEL_COUNT);
                    break;
                case "jank":
                    channelCount = getArgumentAsInt(CHANNEL_COUNT_ARG,
                            Constants.JANK_TEST_CHANNEL_COUNT);
                    break;
                case "unit":
                    channelCount = getArgumentAsInt(CHANNEL_COUNT_ARG,
                            Constants.UNIT_TEST_CHANNEL_COUNT);
                    break;
                default:
                    throw new TestSetupException(
                            "Unknown " + TEST_SETUP_MODE_ARG + " of " + testSetupMode);
            }
            TestTvInputSetupActivity.registerChannels(getContext(), mInputId, channelCount);
        }
    }

    private int getArgumentAsInt(String arg, int defaultValue) {
        String stringValue = mArguments.getString(arg);
        if (stringValue != null) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Unable to parse arg " + arg + " with value " + stringValue
                        + " to a integer.", e);
            }
        }
        return defaultValue;
    }

    static class TestSetupException extends Exception {
        public TestSetupException(String msg) {
            super(msg);
        }

        public static TestSetupException fromMissingArg(String arg) {
            return new TestSetupException(
                    String.format("Error: missing mandatory argument '%s'", arg));
        }
    }
}
