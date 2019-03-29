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

package android.view.cts;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class PanicPressBackTest {
    static final String TAG = "PanicPressBackTest";

    @Rule
    public ActivityTestRule<PanicPressBackActivity> mActivityRule =
            new ActivityTestRule<>(PanicPressBackActivity.class);

    private static final int PANIC_PRESS_COUNT = 4;
    private PanicPressBackActivity mActivity;

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
    }

    /**
     * Tests to ensure that the foregrounded app does not handle back button panic press on
     * non-watch devices
     */
    @Test
    public void testNonWatchBackPanicDoesNothing() throws Exception {
        // Only run for non-watch devices
        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return;
        }

        final UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();

        // Press back button PANIC_PRESS_COUNT times
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < PANIC_PRESS_COUNT; ++i) {
            long currentTime = startTime + i;
            automation.injectInputEvent(new KeyEvent(currentTime, currentTime, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_BACK, 0), true);
            automation.injectInputEvent(new KeyEvent(currentTime, currentTime, KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_BACK, 0), true);
        }

        // Wait multi press time out plus some time to give the system time to respond
        long timeoutMs = ViewConfiguration.getMultiPressTimeout() + TimeUnit.SECONDS.toMillis(1);

        // Assert activity was not stopped, indicating panic press was not able to exit the app
        assertFalse(mActivity.mWaitForPanicBackLatch.await(timeoutMs, TimeUnit.MILLISECONDS));
    }

    /**
     * Tests to ensure that the foregrounded app does handle back button panic press on watch
     * devices
     */
    @Test
    public void testWatchBackPanicReceivesHomeRequest() throws Exception {
        // Only run for watch devices
        if (!mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return;
        }

        final UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();

        // Press back button PANIC_PRESS_COUNT times
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < PANIC_PRESS_COUNT; ++i) {
            // Assert activity hasn't stopped yet
            assertFalse(mActivity.mWaitForPanicBackLatch.await(0, TimeUnit.MILLISECONDS));
            long currentTime = startTime + i;
            automation.injectInputEvent(new KeyEvent(currentTime, currentTime, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_BACK, 0), true);
            automation.injectInputEvent(new KeyEvent(currentTime, currentTime, KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_BACK, 0), true);
        }

        // Wait multi press time out plus some time to give the system time to respond
        long timeoutMs = ViewConfiguration.getMultiPressTimeout() + TimeUnit.SECONDS.toMillis(1);

        // Assert activity was stopped, indicating that panic press was able to exit the app
        assertTrue(mActivity.mWaitForPanicBackLatch.await(timeoutMs, TimeUnit.MILLISECONDS));
    }
}