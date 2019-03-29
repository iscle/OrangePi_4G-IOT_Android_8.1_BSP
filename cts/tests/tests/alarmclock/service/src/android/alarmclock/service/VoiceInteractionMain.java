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

package android.alarmclock.service;

import android.alarmclock.common.Utils;
import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Bundle;
import android.util.Log;

public class VoiceInteractionMain extends Activity {
    static final String TAG = "VoiceInteractionMain";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent();
        String testCaseType = getIntent().getStringExtra(Utils.TESTCASE_TYPE);
        Log.i(TAG, "received_testcasetype = " + testCaseType);
        intent.putExtra(Utils.TESTCASE_TYPE, testCaseType);
        intent.setComponent(new ComponentName(this, MainInteractionService.class));
        ComponentName serviceName = startService(intent);
        Log.i(TAG, "Started service: " + serviceName);
    }
}
