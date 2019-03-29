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
package com.android.compatibility.common.util;

import android.os.Bundle;

public class BroadcastUtils {
    public enum TestcaseType {
        ZEN_MODE_ON,
        ZEN_MODE_OFF,
        AIRPLANE_MODE_ON,
        AIRPLANE_MODE_OFF,
        BATTERYSAVER_MODE_ON,
        BATTERYSAVER_MODE_OFF,
        THEATER_MODE_ON,
        THEATER_MODE_OFF
    }
    public static final String TESTCASE_TYPE = "Testcase_type";
    public static final String BROADCAST_INTENT =
            "android.intent.action.FROM_UTIL_CTS_TEST_";
    public static final int NUM_MINUTES_FOR_ZENMODE = 10;

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
