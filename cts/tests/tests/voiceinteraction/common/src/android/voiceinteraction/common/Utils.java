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
package android.voiceinteraction.common;

import android.app.VoiceInteractor;
import android.app.VoiceInteractor.PickOptionRequest.Option;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;

public class Utils {
    public enum TestCaseType {
        COMPLETION_REQUEST_TEST,
        COMPLETION_REQUEST_CANCEL_TEST,
        CONFIRMATION_REQUEST_TEST,
        CONFIRMATION_REQUEST_CANCEL_TEST,
        ABORT_REQUEST_TEST,
        ABORT_REQUEST_CANCEL_TEST,
        PICKOPTION_REQUEST_TEST,
        PICKOPTION_REQUEST_CANCEL_TEST,
        COMMANDREQUEST_TEST,
        COMMANDREQUEST_CANCEL_TEST,
        SUPPORTS_COMMANDS_TEST,
    }
    public static final String TESTCASE_TYPE = "testcase_type";
    public static final String TESTINFO = "testinfo";
    public static final String BROADCAST_INTENT = "android.intent.action.VOICE_TESTAPP";
    public static final String TEST_PROMPT = "testprompt";
    public static final String PICKOPTON_1 = "one";
    public static final String PICKOPTON_2 = "two";
    public static final String PICKOPTON_3 = "3";
    public static final String TEST_COMMAND = "test_command";
    public static final String TEST_ONCOMMAND_RESULT = "test_oncommand_result";
    public static final String TEST_ONCOMMAND_RESULT_VALUE = "test_oncommand_result value";

    public static final String CONFIRMATION_REQUEST_SUCCESS = "confirmation ok";
    public static final String COMPLETION_REQUEST_SUCCESS = "completion ok";
    public static final String ABORT_REQUEST_SUCCESS = "abort ok";
    public static final String PICKOPTION_REQUEST_SUCCESS = "pickoption ok";
    public static final String COMMANDREQUEST_SUCCESS = "commandrequest ok";
    public static final String SUPPORTS_COMMANDS_SUCCESS = "supportsCommands ok";

    public static final String CONFIRMATION_REQUEST_CANCEL_SUCCESS = "confirm cancel ok";
    public static final String COMPLETION_REQUEST_CANCEL_SUCCESS = "completion canel ok";
    public static final String ABORT_REQUEST_CANCEL_SUCCESS = "abort cancel ok";
    public static final String PICKOPTION_REQUEST_CANCEL_SUCCESS = "pickoption  cancel ok";
    public static final String COMMANDREQUEST_CANCEL_SUCCESS = "commandrequest cancel ok";
    public static final String TEST_ERROR = "Error In Test:";

    public static final String PRIVATE_OPTIONS_KEY = "private_key";
    public static final String PRIVATE_OPTIONS_VALUE = "private_value";

    public static final String toBundleString(Bundle bundle) {
        if (bundle == null) {
            return "*** Bundle is null ****";
        }
        StringBuffer buf = new StringBuffer("Bundle is: ");
        String testType = bundle.getString(TESTCASE_TYPE);
        if (testType != null) {
            buf.append("testcase type = " + testType);
        }
        ArrayList<String> info = bundle.getStringArrayList(TESTINFO);
        if (info != null) {
            for (String s : info) {
                buf.append(s + "\n\t\t");
            }
        }
        return buf.toString();
    }

    public static final String toOptionsString(Option[] options) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < options.length; i++) {
            if (i >= 1) {
                sb.append(", ");
            }
            sb.append(options[i].getLabel());
        }
        sb.append("}");
        return sb.toString();
    }

    public static final void addErrorResult(final Bundle testinfo, final String msg) {
        testinfo.getStringArrayList(testinfo.getString(Utils.TESTCASE_TYPE))
            .add(TEST_ERROR + " " + msg);
    }
}
