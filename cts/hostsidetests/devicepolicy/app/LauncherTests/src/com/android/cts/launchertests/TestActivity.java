/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.launchertests;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.lang.Override;

/**
 * A simple activity to install and launch for various users to
 * test LauncherApps.
 */
public class TestActivity extends Activity {
    public static final String USER_EXTRA = "user_extra";
    public static final int MSG_RESULT = 0;
    public static final int RESULT_PASS = 1;

    private static final String TAG = "SimpleActivity";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i(TAG, "Created for user " + android.os.Process.myUserHandle());
    }

    @Override
    public void onStart() {
        super.onStart();
    }
}
