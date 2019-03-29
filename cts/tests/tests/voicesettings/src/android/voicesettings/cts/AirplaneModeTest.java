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

package android.voicesettings.cts;

import static android.provider.Settings.ACTION_VOICE_CONTROL_AIRPLANE_MODE;

import com.android.compatibility.common.util.BroadcastTestBase;
import com.android.compatibility.common.util.BroadcastUtils;

import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Log;

public class AirplaneModeTest extends BroadcastTestBase {
    static final String TAG = "AirplaneModeTest";
    private static final String VOICE_SETTINGS_PACKAGE = "android.voicesettings.service";
    private static final String VOICE_INTERACTION_CLASS =
        "android.voicesettings.service.VoiceInteractionMain";
    protected static final String FEATURE_VOICE_RECOGNIZERS = "android.software.voice_recognizers";

    private static final int AIRPLANE_MODE_IS_OFF = 0;
    private static final int AIRPLANE_MODE_IS_ON = 1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        mHasFeature = mContext.getPackageManager().hasSystemFeature(FEATURE_VOICE_RECOGNIZERS);
    }

    public AirplaneModeTest() {
        super();
    }

    public void testAll() throws Exception {
        if (!mHasFeature) {
            Log.i(TAG, "The device doesn't support feature: " + FEATURE_VOICE_RECOGNIZERS);
            return;
        }
        if (!isIntentSupported(ACTION_VOICE_CONTROL_AIRPLANE_MODE)) {
            Log.e(TAG, "Voice intent for Airplane Mode NOT supported. existing the test");
            return;
        }
        int mode;
        try {
            mode = getMode();
            Log.i(TAG, "Before testing, AIRPLANE_MODE is set to: " + mode);
        } catch (Settings.SettingNotFoundException e) {
            // if the mode is not supported, don't run the test.
            Log.i(TAG, "airplane mode is not found in Settings. Skipping AirplaneModeTest");
            return;
        }
        startTestActivity("AIRPLANE_MODE");
        if (mode == AIRPLANE_MODE_IS_OFF) {
            // mode is currently OFF.
            // run a test to turn it on.
            // After successful run of the test, run a test to turn it back off.
            if (!runTest(BroadcastUtils.TestcaseType.AIRPLANE_MODE_ON, AIRPLANE_MODE_IS_ON)) {
                // the test failed. don't test the next one.
                return;
            }
            runTest(BroadcastUtils.TestcaseType.AIRPLANE_MODE_OFF, AIRPLANE_MODE_IS_OFF);
        } else {
            // mode is currently ON.
            // run a test to turn it off.
            // After successful run of the test, run a test to turn it back on.
            if (!runTest(BroadcastUtils.TestcaseType.AIRPLANE_MODE_OFF, AIRPLANE_MODE_IS_OFF)) {
                // the test failed. don't test the next one.
                return;
            }
            runTest(BroadcastUtils.TestcaseType.AIRPLANE_MODE_ON, AIRPLANE_MODE_IS_ON);
        }
    }

    private boolean runTest(BroadcastUtils.TestcaseType test, int expectedMode) throws Exception {
        if (!startTestAndWaitForBroadcast(test, VOICE_SETTINGS_PACKAGE, VOICE_INTERACTION_CLASS)) {
            return false;
        }

        // verify the test results
        int mode = getMode();
        Log.i(TAG, "After testing, AIRPLANE_MODE is set to: " + mode);
        assertEquals(expectedMode, mode);
        Log.i(TAG, "Successfully Tested: " + test);
        return true;
    }

    private int getMode() throws Settings.SettingNotFoundException {
        return Settings.Global.getInt(mContext.getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON);
    }
}
