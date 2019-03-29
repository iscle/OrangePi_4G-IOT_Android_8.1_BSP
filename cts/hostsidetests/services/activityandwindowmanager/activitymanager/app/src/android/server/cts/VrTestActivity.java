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
 * limitations under the License
 */

package android.server.cts;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.verifier.vr.MockVrListenerService;

/**
 * Activity that is able to create and destroy a virtual display.
 */
public class VrTestActivity extends Activity {
    private static final String TAG = "VrTestActivity";
    private static final boolean DEBUG = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);
        try {
            setVrModeEnabled(true, new ComponentName(this, MockVrListenerService.class));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not set VR mode: " + e);
        }
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.i(TAG, "onResume called.");
        super.onResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (DEBUG) Log.i(TAG, "onWindowFocusChanged called with " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.i(TAG, "onPause called.");
        super.onPause();
    }
}
