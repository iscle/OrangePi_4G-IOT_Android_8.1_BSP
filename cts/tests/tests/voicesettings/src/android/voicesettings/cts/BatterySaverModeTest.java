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

import static android.provider.Settings.ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE;

import com.android.compatibility.common.util.BroadcastTestBase;
import com.android.compatibility.common.util.BroadcastUtils;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

public class BatterySaverModeTest extends BroadcastTestBase {
    static final String TAG = "BatterySaverModeTest";
    private static final String VOICE_SETTINGS_PACKAGE = "android.voicesettings.service";
    private static final String VOICE_INTERACTION_CLASS =
        "android.voicesettings.service.VoiceInteractionMain";
    protected static final String FEATURE_VOICE_RECOGNIZERS = "android.software.voice_recognizers";

    public BatterySaverModeTest() {
        super();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        mHasFeature = mContext.getPackageManager().hasSystemFeature(FEATURE_VOICE_RECOGNIZERS);
    }

    public void testAll() throws Exception {
        if (!mHasFeature) {
            Log.i(TAG, "The device doesn't support feature: " + FEATURE_VOICE_RECOGNIZERS);
            return;
        }
        if (!isIntentSupported(ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE)) {
            Log.e(TAG, "Voice intent for Battery Saver Mode NOT supported. existing the test");
            return;
        }
        startTestActivity("BATTERYSAVER_MODE");
        boolean modeIsOn = isModeOn();
        Log.i(TAG, "Before testing, BATTERYSAVER_MODE is set to: " + modeIsOn);
        if (modeIsOn) {
            // mode is currently ON.
            // run a test to turn it off.
            // After successful run of the test, run a test to turn it back on.
            if (!runTest(BroadcastUtils.TestcaseType.BATTERYSAVER_MODE_OFF, false)) {
                // the test failed. don't test the next one.
                return;
            }
            runTest(BroadcastUtils.TestcaseType.BATTERYSAVER_MODE_ON, true);
        } else {
            // mode is currently OFF.
            // run a test to turn it on.
            // After successful run of the test, run a test to turn it back off.
            if (!runTest(BroadcastUtils.TestcaseType.BATTERYSAVER_MODE_ON, true)) {
                // the test failed. don't test the next one.
                return;
            }
            runTest(BroadcastUtils.TestcaseType.BATTERYSAVER_MODE_OFF, false);
        }
    }

    private boolean runTest(BroadcastUtils.TestcaseType test, boolean expectedMode) throws Exception {
        if (!startTestAndWaitForBroadcast(test, VOICE_SETTINGS_PACKAGE, VOICE_INTERACTION_CLASS)) {
            return false;
        }

        // Verify the test results
        // Since CTS test needs the device to be connected to the host computer via USB,
        // Batter Saver mode can't be turned on/off.
        // The most we can do is that the broadcast frmo MainInteractionSession is received
        // because that signals the firing and completion of BatterySaverModeVoiceActivity
        // caused by the intent to set Battery Saver mode.
        return true;
    }

    private boolean isModeOn() {
        PowerManager powerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        return powerManager.isPowerSaveMode();
    }
}
