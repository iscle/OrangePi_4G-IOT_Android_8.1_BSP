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

package android.assist.testapp;

import android.app.Activity;
import android.assist.common.Utils;
import android.content.Intent;
import android.util.Log;

public class FocusChangeActivity extends Activity {
    private static final String TAG = "FocusChangeActivity";
    private boolean mGainedFocus = false;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && !mGainedFocus) {
            mGainedFocus = true;
            Log.i(TAG, "gained focus");
            sendBroadcast(new Intent(Utils.GAINED_FOCUS));
        } else if (!hasFocus && mGainedFocus) {
            Log.i(TAG, "lost focus");
            sendBroadcast(new Intent(Utils.LOST_FOCUS));
        }
    }
}
