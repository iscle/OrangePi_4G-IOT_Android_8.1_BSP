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
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class LongPressBackTest {
    private LongPressBackActivity mActivity;

    @Rule
    public ActivityTestRule<LongPressBackActivity> mActivityRule =
            new ActivityTestRule<>(LongPressBackActivity.class);

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
    }

    /**
     * Tests to ensure that the foregrounded app can handle a long-press on the back button on
     * non-watch devices
     */
    @Test
    public void testAppIsNotDismissed() {
        // Only run for non-watch devices
        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return;
        }

        final UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();

        // Inject key down event for back
        long currentTime = System.currentTimeMillis();
        automation.injectInputEvent(new KeyEvent(currentTime, currentTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_BACK, 0), true);

        // Wait long press time plus a few ms to ensure events get triggered
        long timeout = ViewConfiguration.get(mActivity).getDeviceGlobalActionKeyTimeout();
        SystemClock.sleep(timeout + 500);

        // Activity should not have been stopped and back key down should have been registered
        assertFalse(mActivity.wasPaused());
        assertFalse(mActivity.wasStopped());
        assertFalse(mActivity.sawOnBackPressed());
        assertTrue(mActivity.sawBackDown());
        assertFalse(mActivity.sawBackUp());

        currentTime = System.currentTimeMillis();
        // Inject key up event for back
        automation.injectInputEvent(new KeyEvent(currentTime, currentTime, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_BACK, 0), true);

        // Assert that the test app correctly saw the up event and onBackPressed signal
        assertTrue(mActivity.sawBackUp());
        assertTrue(mActivity.sawOnBackPressed());
    }
}