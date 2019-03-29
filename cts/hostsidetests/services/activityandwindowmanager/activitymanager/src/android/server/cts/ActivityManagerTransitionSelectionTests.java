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

package android.server.cts;

import android.platform.test.annotations.Presubmit;

import static android.server.cts.WindowManagerState.TRANSIT_ACTIVITY_CLOSE;
import static android.server.cts.WindowManagerState.TRANSIT_ACTIVITY_OPEN;
import static android.server.cts.WindowManagerState.TRANSIT_TASK_CLOSE;
import static android.server.cts.WindowManagerState.TRANSIT_TASK_OPEN;
import static android.server.cts.WindowManagerState.TRANSIT_WALLPAPER_CLOSE;
import static android.server.cts.WindowManagerState.TRANSIT_WALLPAPER_INTRA_CLOSE;
import static android.server.cts.WindowManagerState.TRANSIT_WALLPAPER_INTRA_OPEN;
import static android.server.cts.WindowManagerState.TRANSIT_WALLPAPER_OPEN;

/**
 * This test tests the transition type selection logic in ActivityManager/
 * WindowManager. BottomActivity is started first, then TopActivity, and we
 * check the transition type that the system selects when TopActivity enters
 * or exits under various setups.
 *
 * Note that we only require the correct transition type to be reported (eg.
 * TRANSIT_ACTIVITY_OPEN, TRANSIT_TASK_CLOSE, TRANSIT_WALLPAPER_OPEN, etc.).
 * The exact animation is unspecified and can be overridden.
 *
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerTransitionSelectionTests
 */
@Presubmit
public class ActivityManagerTransitionSelectionTests extends ActivityManagerTestBase {

    private static final String BOTTOM_ACTIVITY_NAME = "BottomActivity";
    private static final String TOP_ACTIVITY_NAME = "TopActivity";
    private static final String TRANSLUCENT_TOP_ACTIVITY_NAME = "TranslucentTopActivity";

    //------------------------------------------------------------------------//

    // Test activity open/close under normal timing
    public void testOpenActivity_NeitherWallpaper() throws Exception {
        testOpenActivity(false /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_ACTIVITY_OPEN);
    }

    public void testCloseActivity_NeitherWallpaper() throws Exception {
        testCloseActivity(false /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_ACTIVITY_CLOSE);
    }

    public void testOpenActivity_BottomWallpaper() throws Exception {
        testOpenActivity(true /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_CLOSE);
    }

    public void testCloseActivity_BottomWallpaper() throws Exception {
        testCloseActivity(true /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_OPEN);
    }

    public void testOpenActivity_BothWallpaper() throws Exception {
        testOpenActivity(true /*bottomWallpaper*/, true /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_INTRA_OPEN);
    }

    public void testCloseActivity_BothWallpaper() throws Exception {
        testCloseActivity(true /*bottomWallpaper*/, true /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    //------------------------------------------------------------------------//

    // Test task open/close under normal timing
    public void testOpenTask_NeitherWallpaper() throws Exception {
        testOpenTask(false /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_TASK_OPEN);
    }

    public void testCloseTask_NeitherWallpaper() throws Exception {
        testCloseTask(false /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_TASK_CLOSE);
    }

    public void testOpenTask_BottomWallpaper() throws Exception {
        testOpenTask(true /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_CLOSE);
    }

    public void testCloseTask_BottomWallpaper() throws Exception {
        testCloseTask(true /*bottomWallpaper*/, false /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_OPEN);
    }

    public void testOpenTask_BothWallpaper() throws Exception {
        testOpenTask(true /*bottomWallpaper*/, true /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_INTRA_OPEN);
    }

    public void testCloseTask_BothWallpaper() throws Exception {
        testCloseTask(true /*bottomWallpaper*/, true /*topWallpaper*/,
                false /*slowStop*/, TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    //------------------------------------------------------------------------//

    // Test activity close -- bottom activity slow in stopping
    // These simulate the case where the bottom activity is resumed
    // before AM receives its activitiyStopped
    public void testCloseActivity_NeitherWallpaper_SlowStop() throws Exception {
        testCloseActivity(false /*bottomWallpaper*/, false /*topWallpaper*/,
                true /*slowStop*/, TRANSIT_ACTIVITY_CLOSE);
    }

    public void testCloseActivity_BottomWallpaper_SlowStop() throws Exception {
        testCloseActivity(true /*bottomWallpaper*/, false /*topWallpaper*/,
                true /*slowStop*/, TRANSIT_WALLPAPER_OPEN);
    }

    public void testCloseActivity_BothWallpaper_SlowStop() throws Exception {
        testCloseActivity(true /*bottomWallpaper*/, true /*topWallpaper*/,
                true /*slowStop*/, TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    //------------------------------------------------------------------------//

    // Test task close -- bottom task top activity slow in stopping
    // These simulate the case where the bottom activity is resumed
    // before AM receives its activitiyStopped
    public void testCloseTask_NeitherWallpaper_SlowStop() throws Exception {
        testCloseTask(false /*bottomWallpaper*/, false /*topWallpaper*/,
                true /*slowStop*/, TRANSIT_TASK_CLOSE);
    }

    public void testCloseTask_BottomWallpaper_SlowStop() throws Exception {
        testCloseTask(true /*bottomWallpaper*/, false /*topWallpaper*/,
                true /*slowStop*/, TRANSIT_WALLPAPER_OPEN);
    }

    public void testCloseTask_BothWallpaper_SlowStop() throws Exception {
        testCloseTask(true /*bottomWallpaper*/, true /*topWallpaper*/,
                true /*slowStop*/, TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    //------------------------------------------------------------------------//

    /// Test closing of translucent activity/task
    public void testCloseActivity_NeitherWallpaper_Translucent() throws Exception {
        testCloseActivityTranslucent(false /*bottomWallpaper*/, false /*topWallpaper*/,
                TRANSIT_ACTIVITY_CLOSE);
    }

    public void testCloseActivity_BottomWallpaper_Translucent() throws Exception {
        testCloseActivityTranslucent(true /*bottomWallpaper*/, false /*topWallpaper*/,
                TRANSIT_WALLPAPER_OPEN);
    }

    public void testCloseActivity_BothWallpaper_Translucent() throws Exception {
        testCloseActivityTranslucent(true /*bottomWallpaper*/, true /*topWallpaper*/,
                TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    public void testCloseTask_NeitherWallpaper_Translucent() throws Exception {
        testCloseTaskTranslucent(false /*bottomWallpaper*/, false /*topWallpaper*/,
                TRANSIT_TASK_CLOSE);
    }

    public void testCloseTask_BottomWallpaper_Translucent() throws Exception {
        testCloseTaskTranslucent(true /*bottomWallpaper*/, false /*topWallpaper*/,
                TRANSIT_WALLPAPER_OPEN);
    }

    public void testCloseTask_BothWallpaper_Translucent() throws Exception {
        testCloseTaskTranslucent(true /*bottomWallpaper*/, true /*topWallpaper*/,
                TRANSIT_WALLPAPER_INTRA_CLOSE);
    }

    //------------------------------------------------------------------------//

    private void testOpenActivity(boolean bottomWallpaper,
            boolean topWallpaper, boolean slowStop, String expectedTransit) throws Exception {
        testTransitionSelection(true /*testOpen*/, false /*testNewTask*/,
                bottomWallpaper, topWallpaper, false /*topTranslucent*/, slowStop, expectedTransit);
    }
    private void testCloseActivity(boolean bottomWallpaper,
            boolean topWallpaper, boolean slowStop, String expectedTransit) throws Exception {
        testTransitionSelection(false /*testOpen*/, false /*testNewTask*/,
                bottomWallpaper, topWallpaper, false /*topTranslucent*/, slowStop, expectedTransit);
    }
    private void testOpenTask(boolean bottomWallpaper,
            boolean topWallpaper, boolean slowStop, String expectedTransit) throws Exception {
        testTransitionSelection(true /*testOpen*/, true /*testNewTask*/,
                bottomWallpaper, topWallpaper, false /*topTranslucent*/, slowStop, expectedTransit);
    }
    private void testCloseTask(boolean bottomWallpaper,
            boolean topWallpaper, boolean slowStop, String expectedTransit) throws Exception {
        testTransitionSelection(false /*testOpen*/, true /*testNewTask*/,
                bottomWallpaper, topWallpaper, false /*topTranslucent*/, slowStop, expectedTransit);
    }
    private void testCloseActivityTranslucent(boolean bottomWallpaper,
            boolean topWallpaper, String expectedTransit) throws Exception {
        testTransitionSelection(false /*testOpen*/, false /*testNewTask*/,
                bottomWallpaper, topWallpaper, true /*topTranslucent*/,
                false /*slowStop*/, expectedTransit);
    }
    private void testCloseTaskTranslucent(boolean bottomWallpaper,
            boolean topWallpaper, String expectedTransit) throws Exception {
        testTransitionSelection(false /*testOpen*/, true /*testNewTask*/,
                bottomWallpaper, topWallpaper, true /*topTranslucent*/,
                false /*slowStop*/, expectedTransit);
    }
    //------------------------------------------------------------------------//

    private void testTransitionSelection(
            boolean testOpen, boolean testNewTask,
            boolean bottomWallpaper, boolean topWallpaper, boolean topTranslucent,
            boolean testSlowStop, String expectedTransit) throws Exception {
        String bottomStartCmd = getAmStartCmd(BOTTOM_ACTIVITY_NAME);
        if (bottomWallpaper) {
            bottomStartCmd += " --ez USE_WALLPAPER true";
        }
        if (testSlowStop) {
            bottomStartCmd += " --ei STOP_DELAY 3000";
        }
        executeShellCommand(bottomStartCmd);

        final String topActivityName = topTranslucent ?
                TRANSLUCENT_TOP_ACTIVITY_NAME : TOP_ACTIVITY_NAME;
        final String[] bottomActivityArray = new String[] {BOTTOM_ACTIVITY_NAME};
        final String[] topActivityArray = new String[] {topActivityName};

        mAmWmState.computeState(mDevice, bottomActivityArray);

        String topStartCmd = getAmStartCmd(topActivityName);
        if (testNewTask) {
            topStartCmd += " -f 0x18000000";
        }
        if (topWallpaper) {
            topStartCmd += " --ez USE_WALLPAPER true";
        }
        if (!testOpen) {
            topStartCmd += " --ei FINISH_DELAY 1000";
        }
        executeShellCommand(topStartCmd);
        Thread.sleep(5000);
        if (testOpen) {
            mAmWmState.computeState(mDevice, topActivityArray);
        } else {
            mAmWmState.computeState(mDevice, bottomActivityArray);
        }

        assertEquals("Picked wrong transition", expectedTransit,
                mAmWmState.getWmState().getLastTransition());
    }
}
