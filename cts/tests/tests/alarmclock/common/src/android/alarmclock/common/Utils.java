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
package android.alarmclock.common;

import android.os.Bundle;

public class Utils {
    // private constructor - so it can't be instantiated.
    private Utils() {
    }

    public enum TestcaseType {
        DISMISS_ALARM,
        SET_ALARM,
        SET_ALARM_FOR_DISMISSAL,
        SNOOZE_ALARM,
    }
    public static final String TESTCASE_TYPE = "Testcase_type";
    public static final String BROADCAST_INTENT =
            "android.intent.action.FROM_ALARMCLOCK_CTS_TEST_";
    public static final String TEST_RESULT = "test_result";
    public static final String COMPLETION_RESULT = "completion";
    public static final String ABORT_RESULT = "abort";

    public static final String toBundleString(Bundle bundle) {
        if (bundle == null) {
            return "*** Bundle is null ****";
        }
        StringBuilder buf = new StringBuilder();
        if (bundle != null) {
            buf.append("extras: ");
            for (String s : bundle.keySet()) {
                buf.append("(" + s + " = " + bundle.get(s) + "), ");
            }
        }
        return buf.toString();
    }
}
