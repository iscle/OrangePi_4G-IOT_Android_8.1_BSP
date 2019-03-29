/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that the screen refresh rate claimed by
 * android.view.Display.getRefreshRate() matches the steady-state framerate
 * achieved by vsync-limited eglSwapBuffers(). The primary goal is to test
 * Display.getRefreshRate() -- using GL is just an easy and hopefully reliable
 * way of measuring the actual refresh rate.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class DisplayRefreshRateTest {
    // The test passes if
    //   abs(measured_fps - Display.getRefreshRate()) <= FPS_TOLERANCE.
    // A smaller tolerance requires a more accurate measured_fps in order
    // to avoid false negatives.
    private static final float FPS_TOLERANCE = 2.0f;

    private static final String TAG = "DisplayRefreshRateTest";

    @Rule
    public ActivityTestRule<DisplayRefreshRateCtsActivity> mActivityRule =
            new ActivityTestRule<>(DisplayRefreshRateCtsActivity.class);

    private DisplayRefreshRateCtsActivity mActivity;
    private DisplayRefreshRateCtsActivity.FpsResult mFpsResult;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mFpsResult = mActivity.getFpsResult();
    }

    @Test
    public void testRefreshRate() {
        boolean fpsOk = false;

        WindowManager wm = (WindowManager) mActivity
                .getView()
                .getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        Display dpy = wm.getDefaultDisplay();
        float claimedFps = dpy.getRefreshRate();

        for (int i = 0; i < 3; i++) {
            float achievedFps = mFpsResult.waitResult();
            Log.d(TAG, "claimed " + claimedFps + " fps, " +
                       "achieved " + achievedFps + " fps");
            fpsOk = Math.abs(claimedFps - achievedFps) <= FPS_TOLERANCE;
            if (fpsOk) {
                break;
            } else {
                // it could be other activity like bug report capturing for other failures
                // sleep for a while and re-try
                SystemClock.sleep(10000);
                mFpsResult.restart();
            }
        }
        mActivity.finish();
        assertTrue(fpsOk);
    }
}
