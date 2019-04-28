/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.wearable.touch.janktests;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.UiDevice;

public class BouncingBallJankTest extends JankTestBase {

    private UiDevice mDevice;
    private TouchLatencyHelper mTouchLatencyHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mTouchLatencyHelper = TouchLatencyHelper.getInstance(mDevice, getInstrumentation());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void openBouncingBall() {
        mTouchLatencyHelper.openBouncingBallActivity();
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) {
        mTouchLatencyHelper.goBackHome();
        super.afterTest(metrics);
    }

    // Measure jank during bouncing ball animation

    @JankTest(beforeTest = "openBouncingBall", afterTest = "goBackHome",
            expectedFrames = TouchLatencyHelper.EXPECTED_FRAMES)
    @GfxMonitor(processName = TouchLatencyHelper.TOUCH_LATENCY_PKG)
    public void testBouncingBall() {
        SystemClock.sleep(10000);
    }
}
