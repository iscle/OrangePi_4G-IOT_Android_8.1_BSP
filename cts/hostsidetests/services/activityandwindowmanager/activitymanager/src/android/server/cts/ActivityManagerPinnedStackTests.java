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
 * limitations under the License
 */

package android.server.cts;

import static android.server.cts.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.cts.ActivityManagerState.STATE_STOPPED;

import android.server.cts.ActivityManagerState.ActivityStack;
import android.server.cts.ActivityManagerState.ActivityTask;

import java.awt.Rectangle;
import java.lang.Exception;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerPinnedStackTests
 */
public class ActivityManagerPinnedStackTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY = "TestActivity";
    private static final String TEST_ACTIVITY_WITH_SAME_AFFINITY = "TestActivityWithSameAffinity";
    private static final String TRANSLUCENT_TEST_ACTIVITY = "TranslucentTestActivity";
    private static final String NON_RESIZEABLE_ACTIVITY = "NonResizeableActivity";
    private static final String RESUME_WHILE_PAUSING_ACTIVITY = "ResumeWhilePausingActivity";
    private static final String PIP_ACTIVITY = "PipActivity";
    private static final String PIP_ACTIVITY2 = "PipActivity2";
    private static final String PIP_ACTIVITY_WITH_SAME_AFFINITY = "PipActivityWithSameAffinity";
    private static final String ALWAYS_FOCUSABLE_PIP_ACTIVITY = "AlwaysFocusablePipActivity";
    private static final String LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY =
            "LaunchIntoPinnedStackPipActivity";
    private static final String LAUNCH_ENTER_PIP_ACTIVITY = "LaunchEnterPipActivity";
    private static final String PIP_ON_STOP_ACTIVITY = "PipOnStopActivity";

    private static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";
    private static final String EXTRA_ENTER_PIP = "enter_pip";
    private static final String EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR =
            "enter_pip_aspect_ratio_numerator";
    private static final String EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR =
            "enter_pip_aspect_ratio_denominator";
    private static final String EXTRA_SET_ASPECT_RATIO_NUMERATOR = "set_aspect_ratio_numerator";
    private static final String EXTRA_SET_ASPECT_RATIO_DENOMINATOR = "set_aspect_ratio_denominator";
    private static final String EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR =
            "set_aspect_ratio_with_delay_numerator";
    private static final String EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR =
            "set_aspect_ratio_with_delay_denominator";
    private static final String EXTRA_ENTER_PIP_ON_PAUSE = "enter_pip_on_pause";
    private static final String EXTRA_TAP_TO_FINISH = "tap_to_finish";
    private static final String EXTRA_START_ACTIVITY = "start_activity";
    private static final String EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume";
    private static final String EXTRA_REENTER_PIP_ON_EXIT = "reenter_pip_on_exit";
    private static final String EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP = "assert_no_on_stop_before_pip";
    private static final String EXTRA_ON_PAUSE_DELAY = "on_pause_delay";

    private static final String PIP_ACTIVITY_ACTION_ENTER_PIP =
            "android.server.cts.PipActivity.enter_pip";
    private static final String PIP_ACTIVITY_ACTION_MOVE_TO_BACK =
            "android.server.cts.PipActivity.move_to_back";
    private static final String PIP_ACTIVITY_ACTION_EXPAND_PIP =
            "android.server.cts.PipActivity.expand_pip";
    private static final String PIP_ACTIVITY_ACTION_SET_REQUESTED_ORIENTATION =
            "android.server.cts.PipActivity.set_requested_orientation";
    private static final String PIP_ACTIVITY_ACTION_FINISH =
            "android.server.cts.PipActivity.finish";
    private static final String TEST_ACTIVITY_ACTION_FINISH =
            "android.server.cts.TestActivity.finish_self";

    private static final String APP_OPS_OP_ENTER_PICTURE_IN_PICTURE = "PICTURE_IN_PICTURE";
    private static final int APP_OPS_MODE_ALLOWED = 0;
    private static final int APP_OPS_MODE_IGNORED = 1;
    private static final int APP_OPS_MODE_ERRORED = 2;

    private static final int ROTATION_0 = 0;
    private static final int ROTATION_90 = 1;
    private static final int ROTATION_180 = 2;
    private static final int ROTATION_270 = 3;

    // Corresponds to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    private static final int ORIENTATION_LANDSCAPE = 0;
    // Corresponds to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private static final int ORIENTATION_PORTRAIT = 1;

    private static final float FLOAT_COMPARE_EPSILON = 0.005f;

    // Corresponds to com.android.internal.R.dimen.config_pictureInPictureMinAspectRatio
    private static final int MIN_ASPECT_RATIO_NUMERATOR = 100;
    private static final int MIN_ASPECT_RATIO_DENOMINATOR = 239;
    private static final int BELOW_MIN_ASPECT_RATIO_DENOMINATOR = MIN_ASPECT_RATIO_DENOMINATOR + 1;
    // Corresponds to com.android.internal.R.dimen.config_pictureInPictureMaxAspectRatio
    private static final int MAX_ASPECT_RATIO_NUMERATOR = 239;
    private static final int MAX_ASPECT_RATIO_DENOMINATOR = 100;
    private static final int ABOVE_MAX_ASPECT_RATIO_NUMERATOR = MAX_ASPECT_RATIO_NUMERATOR + 1;

    public void testMinimumDeviceSize() throws Exception {
        if (!supportsPip()) return;

        mAmWmState.assertDeviceDefaultDisplaySize(mDevice,
                "Devices supporting picture-in-picture must be larger than the default minimum"
                        + " task size");
    }

    public void testEnterPictureInPictureMode() throws Exception {
        pinnedStackTester(getAmStartCmd(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true"), PIP_ACTIVITY,
                false /* moveTopToPinnedStack */, false /* isFocusable */);
    }

    public void testMoveTopActivityToPinnedStack() throws Exception {
        pinnedStackTester(getAmStartCmd(PIP_ACTIVITY), PIP_ACTIVITY,
                true /* moveTopToPinnedStack */, false /* isFocusable */);
    }

    public void testAlwaysFocusablePipActivity() throws Exception {
        pinnedStackTester(getAmStartCmd(ALWAYS_FOCUSABLE_PIP_ACTIVITY),
                ALWAYS_FOCUSABLE_PIP_ACTIVITY, false /* moveTopToPinnedStack */,
                true /* isFocusable */);
    }

    public void testLaunchIntoPinnedStack() throws Exception {
        pinnedStackTester(getAmStartCmd(LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY),
                ALWAYS_FOCUSABLE_PIP_ACTIVITY, false /* moveTopToPinnedStack */,
                true /* isFocusable */);
    }

    public void testNonTappablePipActivity() throws Exception {
        if (!supportsPip()) return;

        // Launch the tap-to-finish activity at a specific place
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_TAP_TO_FINISH, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();

        // Tap the screen at a known location in the pinned stack bounds, and ensure that it is
        // not passed down to the top task
        tapToFinishPip();
        mAmWmState.computeState(mDevice, new String[] {PIP_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);
    }

    public void testPinnedStackDefaultBounds() throws Exception {
        if (!supportsPip()) return;

        // Launch a PIP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");

        setDeviceRotation(ROTATION_0);
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        Rectangle defaultPipBounds = wmState.getDefaultPinnedStackBounds();
        Rectangle stableBounds = wmState.getStableBounds();
        assertTrue(defaultPipBounds.width > 0 && defaultPipBounds.height > 0);
        assertTrue(stableBounds.contains(defaultPipBounds));

        setDeviceRotation(ROTATION_90);
        wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        defaultPipBounds = wmState.getDefaultPinnedStackBounds();
        stableBounds = wmState.getStableBounds();
        assertTrue(defaultPipBounds.width > 0 && defaultPipBounds.height > 0);
        assertTrue(stableBounds.contains(defaultPipBounds));
        setDeviceRotation(ROTATION_0);
    }

    public void testPinnedStackMovementBounds() throws Exception {
        if (!supportsPip()) return;

        // Launch a PIP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");

        setDeviceRotation(ROTATION_0);
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        Rectangle pipMovementBounds = wmState.getPinnedStackMomentBounds();
        Rectangle stableBounds = wmState.getStableBounds();
        assertTrue(pipMovementBounds.width > 0 && pipMovementBounds.height > 0);
        assertTrue(stableBounds.contains(pipMovementBounds));

        setDeviceRotation(ROTATION_90);
        wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        pipMovementBounds = wmState.getPinnedStackMomentBounds();
        stableBounds = wmState.getStableBounds();
        assertTrue(pipMovementBounds.width > 0 && pipMovementBounds.height > 0);
        assertTrue(stableBounds.contains(pipMovementBounds));
        setDeviceRotation(ROTATION_0);
    }

    public void testPinnedStackOutOfBoundsInsetsNonNegative() throws Exception {
        if (!supportsPip()) return;

        final WindowManagerState wmState = mAmWmState.getWmState();

        // Launch an activity into the pinned stack
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_TAP_TO_FINISH, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        // Get the display dimensions
        WindowManagerState.WindowState windowState = getWindowState(PIP_ACTIVITY);
        WindowManagerState.Display display = wmState.getDisplay(windowState.getDisplayId());
        Rectangle displayRect = display.getDisplayRect();

        // Move the pinned stack offscreen
        String moveStackOffscreenCommand = String.format("am stack resize 4 %d %d %d %d",
                displayRect.width - 200, 0, displayRect.width + 200, 500);
        executeShellCommand(moveStackOffscreenCommand);

        // Ensure that the surface insets are not negative
        windowState = getWindowState(PIP_ACTIVITY);
        Rectangle contentInsets = windowState.getContentInsets();
        assertTrue(contentInsets.x >= 0 && contentInsets.y >= 0 && contentInsets.width >= 0 &&
                contentInsets.height >= 0);
    }

    public void testPinnedStackInBoundsAfterRotation() throws Exception {
        if (!supportsPip()) return;

        // Launch an activity into the pinned stack
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_TAP_TO_FINISH, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        // Ensure that the PIP stack is fully visible in each orientation
        setDeviceRotation(ROTATION_0);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_90);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_180);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_270);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_0);
    }

    public void testEnterPipToOtherOrientation() throws Exception {
        if (!supportsPip()) return;

        // Launch a portrait only app on the fullscreen stack
        launchActivity(TEST_ACTIVITY,
                EXTRA_FIXED_ORIENTATION, String.valueOf(ORIENTATION_PORTRAIT));
        // Launch the PiP activity fixed as landscape
        launchActivity(PIP_ACTIVITY,
                EXTRA_FIXED_ORIENTATION, String.valueOf(ORIENTATION_LANDSCAPE));
        // Enter PiP, and assert that the PiP is within bounds now that the device is back in
        // portrait
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
    }

    public void testEnterPipAspectRatioMin() throws Exception {
        testEnterPipAspectRatio(MIN_ASPECT_RATIO_NUMERATOR, MIN_ASPECT_RATIO_DENOMINATOR);
    }

    public void testEnterPipAspectRatioMax() throws Exception {
        testEnterPipAspectRatio(MAX_ASPECT_RATIO_NUMERATOR, MAX_ASPECT_RATIO_DENOMINATOR);
    }

    private void testEnterPipAspectRatio(int num, int denom) throws Exception {
        if (!supportsPip()) return;

        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR, Integer.toString(num),
                EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR, Integer.toString(denom));
        assertPinnedStackExists();

        // Assert that we have entered PIP and that the aspect ratio is correct
        Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        assertTrue(floatEquals((float) pinnedStackBounds.width / pinnedStackBounds.height,
                (float) num / denom));
    }

    public void testResizePipAspectRatioMin() throws Exception {
        testResizePipAspectRatio(MIN_ASPECT_RATIO_NUMERATOR, MIN_ASPECT_RATIO_DENOMINATOR);
    }

    public void testResizePipAspectRatioMax() throws Exception {
        testResizePipAspectRatio(MAX_ASPECT_RATIO_NUMERATOR, MAX_ASPECT_RATIO_DENOMINATOR);
    }

    private void testResizePipAspectRatio(int num, int denom) throws Exception {
        if (!supportsPip()) return;

        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_SET_ASPECT_RATIO_NUMERATOR, Integer.toString(num),
                EXTRA_SET_ASPECT_RATIO_DENOMINATOR, Integer.toString(denom));
        assertPinnedStackExists();

        // Hacky, but we need to wait for the enterPictureInPicture animation to complete and
        // the resize to be called before we can check the pinned stack bounds
        final boolean[] result = new boolean[1];
        mAmWmState.waitForWithAmState(mDevice, (state) -> {
            Rectangle pinnedStackBounds = state.getStackById(PINNED_STACK_ID).getBounds();
            boolean isValidAspectRatio = floatEquals(
                    (float) pinnedStackBounds.width / pinnedStackBounds.height,
                    (float) num / denom);
            result[0] = isValidAspectRatio;
            return isValidAspectRatio;
        }, "Waiting for pinned stack to be resized");
        assertTrue(result[0]);
    }

    public void testEnterPipExtremeAspectRatioMin() throws Exception {
        testEnterPipExtremeAspectRatio(MIN_ASPECT_RATIO_NUMERATOR,
                BELOW_MIN_ASPECT_RATIO_DENOMINATOR);
    }

    public void testEnterPipExtremeAspectRatioMax() throws Exception {
        testEnterPipExtremeAspectRatio(ABOVE_MAX_ASPECT_RATIO_NUMERATOR,
                MAX_ASPECT_RATIO_DENOMINATOR);
    }

    private void testEnterPipExtremeAspectRatio(int num, int denom) throws Exception {
        if (!supportsPip()) return;

        // Assert that we could not create a pinned stack with an extreme aspect ratio
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR, Integer.toString(num),
                EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR, Integer.toString(denom));
        assertPinnedStackDoesNotExist();
    }

    public void testSetPipExtremeAspectRatioMin() throws Exception {
        testSetPipExtremeAspectRatio(MIN_ASPECT_RATIO_NUMERATOR,
                BELOW_MIN_ASPECT_RATIO_DENOMINATOR);
    }

    public void testSetPipExtremeAspectRatioMax() throws Exception {
        testSetPipExtremeAspectRatio(ABOVE_MAX_ASPECT_RATIO_NUMERATOR,
                MAX_ASPECT_RATIO_DENOMINATOR);
    }

    private void testSetPipExtremeAspectRatio(int num, int denom) throws Exception {
        if (!supportsPip()) return;

        // Try to resize the a normal pinned stack to an extreme aspect ratio and ensure that
        // fails (the aspect ratio remains the same)
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR,
                        Integer.toString(MAX_ASPECT_RATIO_NUMERATOR),
                EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR,
                        Integer.toString(MAX_ASPECT_RATIO_DENOMINATOR),
                EXTRA_SET_ASPECT_RATIO_NUMERATOR, Integer.toString(num),
                EXTRA_SET_ASPECT_RATIO_DENOMINATOR, Integer.toString(denom));
        assertPinnedStackExists();
        Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        assertTrue(floatEquals((float) pinnedStackBounds.width / pinnedStackBounds.height,
                (float) MAX_ASPECT_RATIO_NUMERATOR / MAX_ASPECT_RATIO_DENOMINATOR));
    }

    public void testDisallowPipLaunchFromStoppedActivity() throws Exception {
        if (!supportsPip()) return;

        // Launch the bottom pip activity
        launchActivity(PIP_ON_STOP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        // Wait for the bottom pip activity to be stopped
        mAmWmState.waitForActivityState(mDevice, PIP_ON_STOP_ACTIVITY, STATE_STOPPED);

        // Assert that there is no pinned stack (that enterPictureInPicture() failed)
        assertPinnedStackDoesNotExist();
    }

    public void testAutoEnterPictureInPicture() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch the PIP activity on pause
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP_ON_PAUSE, "true");
        assertPinnedStackDoesNotExist();

        // Go home and ensure that there is a pinned stack
        launchHomeActivity();
        assertPinnedStackExists();
    }

    public void testAutoEnterPictureInPictureLaunchActivity() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch the PIP activity on pause, and have it start another activity on
        // top of itself.  Wait for the new activity to be visible and ensure that the pinned stack
        // was not created in the process
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_START_ACTIVITY, getActivityComponentName(NON_RESIZEABLE_ACTIVITY));
        mAmWmState.computeState(mDevice, new String[] {NON_RESIZEABLE_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        assertPinnedStackDoesNotExist();

        // Go home while the pip activity is open and ensure the previous activity is not PIPed
        launchHomeActivity();
        assertPinnedStackDoesNotExist();
    }

    public void testAutoEnterPictureInPictureFinish() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch the PIP activity on pause, and set it to finish itself after
        // some period.  Wait for the previous activity to be visible, and ensure that the pinned
        // stack was not created in the process
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_FINISH_SELF_ON_RESUME, "true");
        assertPinnedStackDoesNotExist();
    }

    public void testAutoEnterPictureInPictureAspectRatio() throws Exception {
        if (!supportsPip()) return;

        // Launch the PIP activity on pause, and set the aspect ratio
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_SET_ASPECT_RATIO_NUMERATOR, Integer.toString(MAX_ASPECT_RATIO_NUMERATOR),
                EXTRA_SET_ASPECT_RATIO_DENOMINATOR, Integer.toString(MAX_ASPECT_RATIO_DENOMINATOR));

        // Go home while the pip activity is open to trigger auto-PIP
        launchHomeActivity();
        assertPinnedStackExists();

        // Hacky, but we need to wait for the auto-enter picture-in-picture animation to complete
        // and before we can check the pinned stack bounds
        final boolean[] result = new boolean[1];
        mAmWmState.waitForWithAmState(mDevice, (state) -> {
            Rectangle pinnedStackBounds = state.getStackById(PINNED_STACK_ID).getBounds();
            boolean isValidAspectRatio = floatEquals(
                    (float) pinnedStackBounds.width / pinnedStackBounds.height,
                    (float) MAX_ASPECT_RATIO_NUMERATOR / MAX_ASPECT_RATIO_DENOMINATOR);
            result[0] = isValidAspectRatio;
            return isValidAspectRatio;
        }, "Waiting for pinned stack to be resized");
        assertTrue(result[0]);
    }

    public void testAutoEnterPictureInPictureOverPip() throws Exception {
        if (!supportsPip()) return;

        // Launch another PIP activity
        launchActivity(LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();

        // Launch the PIP activity on pause
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP_ON_PAUSE, "true");

        // Go home while the PIP activity is open to trigger auto-enter PIP
        launchHomeActivity();
        assertPinnedStackExists();

        // Ensure that auto-enter pip failed and that the resumed activity in the pinned stack is
        // still the first activity
        final ActivityStack pinnedStack = mAmWmState.getAmState().getStackById(PINNED_STACK_ID);
        assertTrue(pinnedStack.getTasks().size() == 1);
        assertTrue(pinnedStack.getTasks().get(0).mRealActivity.equals(getActivityComponentName(
                ALWAYS_FOCUSABLE_PIP_ACTIVITY)));
    }

    public void testDisallowMultipleTasksInPinnedStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we have multiple fullscreen tasks
        launchActivity(TEST_ACTIVITY);

        // Launch first PIP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");

        // Launch second PIP activity
        launchActivity(PIP_ACTIVITY2, EXTRA_ENTER_PIP, "true");

        final ActivityStack pinnedStack = mAmWmState.getAmState().getStackById(PINNED_STACK_ID);
        assertEquals(1, pinnedStack.getTasks().size());

        assertTrue(pinnedStack.getTasks().get(0).mRealActivity.equals(getActivityComponentName(
                PIP_ACTIVITY2)));

        final ActivityStack fullScreenStack = mAmWmState.getAmState().getStackById(
                FULLSCREEN_WORKSPACE_STACK_ID);
        assertTrue(fullScreenStack.getBottomTask().mRealActivity.equals(getActivityComponentName(
                PIP_ACTIVITY)));
    }

    public void testPipUnPipOverHome() throws Exception {
        if (!supportsPip()) return;

        // Go home
        launchHomeActivity();
        // Launch an auto pip activity
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_REENTER_PIP_ON_EXIT, "true");
        assertPinnedStackExists();

        // Relaunch the activity to fullscreen to trigger the activity to exit and re-enter pip
        launchActivity(PIP_ACTIVITY);
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == FULLSCREEN_WORKSPACE_STACK_ID;
        }, "Waiting for PIP to exit to fullscreen");
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == PINNED_STACK_ID;
        }, "Waiting to re-enter PIP");
        mAmWmState.assertFocusedStack("Expected home stack focused", HOME_STACK_ID);
    }

    public void testPipUnPipOverApp() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch an auto pip activity
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_REENTER_PIP_ON_EXIT, "true");
        assertPinnedStackExists();

        // Relaunch the activity to fullscreen to trigger the activity to exit and re-enter pip
        launchActivity(PIP_ACTIVITY);
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == FULLSCREEN_WORKSPACE_STACK_ID;
        }, "Waiting for PIP to exit to fullscreen");
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == PINNED_STACK_ID;
        }, "Waiting to re-enter PIP");
        mAmWmState.assertFocusedStack("Expected fullscreen stack focused",
                FULLSCREEN_WORKSPACE_STACK_ID);
    }

    public void testRemovePipWithNoFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Start with a clean slate, remove all the stacks but home
        removeStacks(ALL_STACK_IDS_BUT_HOME);

        // Launch a pip activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is now in the fullscreen stack (when no
        // fullscreen stack existed before)
        removeStacks(PINNED_STACK_ID);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                true /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testRemovePipWithVisibleFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, and a pip activity over that
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed in the fullscreen stack, behind the
        // top fullscreen activity
        removeStacks(PINNED_STACK_ID);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID,
                false /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testRemovePipWithHiddenFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, return home and while the fullscreen stack is hidden,
        // launch a pip activity over home
        launchActivity(TEST_ACTIVITY);
        launchHomeActivity();
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed on top of the hidden fullscreen
        // stack, but that the home stack is still focused
        removeStacks(PINNED_STACK_ID);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                false /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testMovePipToBackWithNoFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Start with a clean slate, remove all the stacks but home
        removeStacks(ALL_STACK_IDS_BUT_HOME);

        // Launch a pip activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is now in the fullscreen stack (when no
        // fullscreen stack existed before)
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_MOVE_TO_BACK);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                false /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testMovePipToBackWithVisibleFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, and a pip activity over that
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed in the fullscreen stack, behind the
        // top fullscreen activity
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_MOVE_TO_BACK);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID,
                false /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testMovePipToBackWithHiddenFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, return home and while the fullscreen stack is hidden,
        // launch a pip activity over home
        launchActivity(TEST_ACTIVITY);
        launchHomeActivity();
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed on top of the hidden fullscreen
        // stack, but that the home stack is still focused
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_MOVE_TO_BACK);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                false /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testPinnedStackAlwaysOnTop() throws Exception {
        if (!supportsPip()) return;

        // Launch activity into pinned stack and assert it's on top.
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();
        assertPinnedStackIsOnTop();

        // Launch another activity in fullscreen stack and check that pinned stack is still on top.
        launchActivity(TEST_ACTIVITY);
        assertPinnedStackExists();
        assertPinnedStackIsOnTop();

        // Launch home and check that pinned stack is still on top.
        launchHomeActivity();
        assertPinnedStackExists();
        assertPinnedStackIsOnTop();
    }

    public void testAppOpsDenyPipOnPause() throws Exception {
        if (!supportsPip()) return;

        // Disable enter-pip and try to enter pip
        setAppOpsOpToMode(ActivityManagerTestBase.componentName,
                APP_OPS_OP_ENTER_PICTURE_IN_PICTURE, APP_OPS_MODE_IGNORED);

        // Launch the PIP activity on pause
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackDoesNotExist();

        // Go home and ensure that there is no pinned stack
        launchHomeActivity();
        assertPinnedStackDoesNotExist();

        // Re-enable enter-pip-on-hide
        setAppOpsOpToMode(ActivityManagerTestBase.componentName,
                APP_OPS_OP_ENTER_PICTURE_IN_PICTURE, APP_OPS_MODE_ALLOWED);
    }

    public void testEnterPipFromTaskWithMultipleActivities() throws Exception {
        if (!supportsPip()) return;

        // Try to enter picture-in-picture from an activity that has more than one activity in the
        // task and ensure that it works
        launchActivity(LAUNCH_ENTER_PIP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();
    }

    public void testEnterPipWithResumeWhilePausingActivityNoStop() throws Exception {
        if (!supportsPip()) return;

        /*
         * Launch the resumeWhilePausing activity and ensure that the PiP activity did not get
         * stopped and actually went into the pinned stack.
         *
         * Note that this is a workaround because to trigger the path that we want to happen in
         * activity manager, we need to add the leaving activity to the stopping state, which only
         * happens when a hidden stack is brought forward. Normally, this happens when you go home,
         * but since we can't launch into the home stack directly, we have a workaround.
         *
         * 1) Launch an activity in a new dynamic stack
         * 2) Resize the dynamic stack to non-fullscreen bounds
         * 3) Start the PiP activity that will enter picture-in-picture when paused in the
         *    fullscreen stack
         * 4) Bring the activity in the dynamic stack forward to trigger PiP
         */
        int stackId = launchActivityInNewDynamicStack(RESUME_WHILE_PAUSING_ACTIVITY);
        resizeStack(stackId, 0, 0, 500, 500);
        // Launch an activity that will enter PiP when it is paused with a delay that is long enough
        // for the next resumeWhilePausing activity to finish resuming, but slow enough to not
        // trigger the current system pause timeout (currently 500ms)
        launchActivityInStack(PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_ON_PAUSE_DELAY, "350",
                EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP, "true");
        launchActivity(RESUME_WHILE_PAUSING_ACTIVITY);
        assertPinnedStackExists();
    }

    public void testDisallowEnterPipActivityLocked() throws Exception {
        if (!supportsPip()) return;

        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP_ON_PAUSE, "true");
        ActivityTask task =
                mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID).getTopTask();

        // Lock the task and ensure that we can't enter picture-in-picture both explicitly and
        // when paused
        executeShellCommand("am task lock " + task.mTaskId);
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackDoesNotExist();
        launchHomeActivity();
        assertPinnedStackDoesNotExist();
        executeShellCommand("am task lock stop");
    }

    public void testConfigurationChangeOrderDuringTransition() throws Exception {
        if (!supportsPip()) return;

        // Launch a PiP activity and ensure configuration change only happened once, and that the
        // configuration change happened after the picture-in-picture and multi-window callbacks
        launchActivity(PIP_ACTIVITY);
        String logSeparator = clearLogcat();
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();
        waitForValidPictureInPictureCallbacks(PIP_ACTIVITY, logSeparator);
        assertValidPictureInPictureCallbackOrder(PIP_ACTIVITY, logSeparator);

        // Trigger it to go back to fullscreen and ensure that only triggered one configuration
        // change as well
        logSeparator = clearLogcat();
        launchActivity(PIP_ACTIVITY);
        waitForValidPictureInPictureCallbacks(PIP_ACTIVITY, logSeparator);
        assertValidPictureInPictureCallbackOrder(PIP_ACTIVITY, logSeparator);
    }

    public void testEnterPipInterruptedCallbacks() throws Exception {
        if (!supportsPip()) return;

        // Slow down the transition animations for this test
        setWindowTransitionAnimationDurationScale(20);

        // Launch a PiP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        // Wait until the PiP activity has moved into the pinned stack (happens before the
        // transition has started)
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();

        // Relaunch the PiP activity back into fullscreen
        String logSeparator = clearLogcat();
        launchActivity(PIP_ACTIVITY);
        // Wait until the PiP activity is reparented into the fullscreen stack (happens after the
        // transition has finished)
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);

        // Ensure that we get the callbacks indicating that PiP/MW mode was cancelled, but no
        // configuration change (since none was sent)
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(
                PIP_ACTIVITY, logSeparator);
        assertTrue(lifecycleCounts.mConfigurationChangedCount == 0);
        assertTrue(lifecycleCounts.mPictureInPictureModeChangedCount == 1);
        assertTrue(lifecycleCounts.mMultiWindowModeChangedCount == 1);

        // Reset the animation scale
        setWindowTransitionAnimationDurationScale(1);
    }

    public void testStopBeforeMultiWindowCallbacksOnDismiss() throws Exception {
        if (!supportsPip()) return;

        // Launch a PiP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Dismiss it
        String logSeparator = clearLogcat();
        removeStacks(PINNED_STACK_ID);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);

        // Confirm that we get stop before the multi-window and picture-in-picture mode change
        // callbacks
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(PIP_ACTIVITY,
                logSeparator);
        if (lifecycleCounts.mStopCount != 1) {
            fail(PIP_ACTIVITY + " has received " + lifecycleCounts.mStopCount
                    + " onStop() calls, expecting 1");
        } else if (lifecycleCounts.mPictureInPictureModeChangedCount != 1) {
            fail(PIP_ACTIVITY + " has received " + lifecycleCounts.mPictureInPictureModeChangedCount
                    + " onPictureInPictureModeChanged() calls, expecting 1");
        } else if (lifecycleCounts.mMultiWindowModeChangedCount != 1) {
            fail(PIP_ACTIVITY + " has received " + lifecycleCounts.mMultiWindowModeChangedCount
                    + " onMultiWindowModeChanged() calls, expecting 1");
        } else {
            int lastStopLine = lifecycleCounts.mLastStopLineIndex;
            int lastPipLine = lifecycleCounts.mLastPictureInPictureModeChangedLineIndex;
            int lastMwLine = lifecycleCounts.mLastMultiWindowModeChangedLineIndex;
            if (!(lastStopLine < lastPipLine && lastPipLine < lastMwLine)) {
                fail(PIP_ACTIVITY + " has received callbacks in unexpected order.  Expected:"
                        + " stop < pip < mw, but got line indices: " + lastStopLine + ", "
                        + lastPipLine + ", " + lastMwLine + " respectively");
            }
        }
    }

    public void testPreventSetAspectRatioWhileExpanding() throws Exception {
        if (!supportsPip()) return;

        // Launch the PiP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");

        // Trigger it to go back to fullscreen and try to set the aspect ratio, and ensure that the
        // call to set the aspect ratio did not prevent the PiP from returning to fullscreen
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_EXPAND_PIP
                + " -e " + EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR + " 123456789"
                + " -e " + EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR + " 100000000");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        assertPinnedStackDoesNotExist();
    }

    public void testSetRequestedOrientationWhilePinned() throws Exception {
        if (!supportsPip()) return;

        // Launch the PiP activity fixed as portrait, and enter picture-in-picture
        launchActivity(PIP_ACTIVITY,
                EXTRA_FIXED_ORIENTATION, String.valueOf(ORIENTATION_PORTRAIT),
                EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Request that the orientation is set to landscape
        executeShellCommand("am broadcast -a "
                + PIP_ACTIVITY_ACTION_SET_REQUESTED_ORIENTATION + " -e "
                + EXTRA_FIXED_ORIENTATION + " " + String.valueOf(ORIENTATION_LANDSCAPE));

        // Launch the activity back into fullscreen and ensure that it is now in landscape
        launchActivity(PIP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        assertPinnedStackDoesNotExist();
        assertTrue(mAmWmState.getWmState().getLastOrientation() == ORIENTATION_LANDSCAPE);
    }

    public void testWindowButtonEntersPip() throws Exception {
        if (!supportsPip()) return;

        // Launch the PiP activity trigger the window button, ensure that we have entered PiP
        launchActivity(PIP_ACTIVITY);
        pressWindowButton();
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();
    }

    public void testFinishPipActivityWithTaskOverlay() throws Exception {
        if (!supportsPip()) return;

        // Launch PiP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();
        int taskId = mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getTopTask().mTaskId;

        // Ensure that we don't any any other overlays as a result of launching into PIP
        launchHomeActivity();

        // Launch task overlay activity into PiP activity task
        launchActivityAsTaskOverlay(TRANSLUCENT_TEST_ACTIVITY, taskId, PINNED_STACK_ID);

        // Finish the PiP activity and ensure that there is no pinned stack
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_FINISH);
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            ActivityStack stack = amState.getStackById(PINNED_STACK_ID);
            return stack == null;
        }, "Waiting for pinned stack to be removed...");
        assertPinnedStackDoesNotExist();
    }

    public void testNoResumeAfterTaskOverlayFinishes() throws Exception {
        if (!supportsPip()) return;

        // Launch PiP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();
        int taskId = mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getTopTask().mTaskId;

        // Launch task overlay activity into PiP activity task
        launchActivityAsTaskOverlay(TRANSLUCENT_TEST_ACTIVITY, taskId, PINNED_STACK_ID);

        // Finish the task overlay activity while animating and ensure that the PiP activity never
        // got resumed
        String logSeparator = clearLogcat();
        executeShellCommand("am stack resize-animated 4 20 20 500 500");
        executeShellCommand("am broadcast -a " + TEST_ACTIVITY_ACTION_FINISH);
        mAmWmState.waitFor(mDevice, (amState, wmState) -> !amState.containsActivity(
                TRANSLUCENT_TEST_ACTIVITY), "Waiting for test activity to finish...");
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(PIP_ACTIVITY,
                logSeparator);
        assertTrue(lifecycleCounts.mResumeCount == 0);
        assertTrue(lifecycleCounts.mPauseCount == 0);
    }

    public void testPinnedStackWithDockedStack() throws Exception {
        if (!supportsPip() || !supportsSplitScreenMultiWindow()) return;

        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        launchActivityToSide(true, false, TEST_ACTIVITY);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        // Launch the activities again to take focus and make sure nothing is hidden
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        launchActivityToSide(true, false, TEST_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        // Go to recents to make sure that fullscreen stack is invisible
        // Some devices do not support recents or implement it differently (instead of using a
        // separate stack id or as an activity), for those cases the visibility asserts will be
        // ignored
        pressAppSwitchButton();
        if (mAmWmState.waitForRecentsActivityVisible(mDevice)) {
            mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true);
            mAmWmState.assertVisibility(TEST_ACTIVITY, false);
        }
    }

    public void testLaunchTaskByComponentMatchMultipleTasks() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity which will launch a PiP activity in a new task with the same
        // affinity
        launchActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY);
        launchActivity(PIP_ACTIVITY_WITH_SAME_AFFINITY);
        assertPinnedStackExists();

        // Launch the root activity again...
        int rootActivityTaskId = mAmWmState.getAmState().getTaskByActivityName(
                TEST_ACTIVITY_WITH_SAME_AFFINITY).mTaskId;
        launchHomeActivity();
        launchActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY);

        // ...and ensure that the root activity task is found and reused, and that the pinned stack
        // is unaffected
        assertPinnedStackExists();
        mAmWmState.assertFocusedActivity("Expected root activity focused",
                TEST_ACTIVITY_WITH_SAME_AFFINITY);
        assertTrue(rootActivityTaskId == mAmWmState.getAmState().getTaskByActivityName(
                TEST_ACTIVITY_WITH_SAME_AFFINITY).mTaskId);
    }

    public void testLaunchTaskByAffinityMatchMultipleTasks() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity which will launch a PiP activity in a new task with the same
        // affinity, and also launch another activity in the same task, while finishing itself. As
        // a result, the task will not have a component matching the same activity as what it was
        // started with
        launchActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY,
                EXTRA_START_ACTIVITY, getActivityComponentName(TEST_ACTIVITY),
                EXTRA_FINISH_SELF_ON_RESUME, "true");
        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        launchActivity(PIP_ACTIVITY_WITH_SAME_AFFINITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY_WITH_SAME_AFFINITY, PINNED_STACK_ID);
        assertPinnedStackExists();

        // Launch the root activity again...
        int rootActivityTaskId = mAmWmState.getAmState().getTaskByActivityName(
                TEST_ACTIVITY).mTaskId;
        launchHomeActivity();
        launchActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY);

        // ...and ensure that even while matching purely by task affinity, the root activity task is
        // found and reused, and that the pinned stack is unaffected
        assertPinnedStackExists();
        mAmWmState.assertFocusedActivity("Expected root activity focused", TEST_ACTIVITY);
        assertTrue(rootActivityTaskId == mAmWmState.getAmState().getTaskByActivityName(
                TEST_ACTIVITY).mTaskId);
    }

    public void testLaunchTaskByAffinityMatchSingleTask() throws Exception {
        if (!supportsPip()) return;

        // Launch an activity into the pinned stack with a fixed affinity
        launchActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_START_ACTIVITY, getActivityComponentName(PIP_ACTIVITY),
                EXTRA_FINISH_SELF_ON_RESUME, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();

        // Launch the root activity again, of the matching task and ensure that we expand to
        // fullscreen
        int activityTaskId = mAmWmState.getAmState().getTaskByActivityName(
                PIP_ACTIVITY).mTaskId;
        launchHomeActivity();
        launchActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        assertPinnedStackDoesNotExist();
        assertTrue(activityTaskId == mAmWmState.getAmState().getTaskByActivityName(
                PIP_ACTIVITY).mTaskId);
    }

    /** Test that reported display size corresponds to fullscreen after exiting PiP. */
    public void testDisplayMetricsPinUnpin() throws Exception {
        if (!supportsPip()) return;

        String logSeparator = clearLogcat();
        launchActivity(TEST_ACTIVITY);
        final int defaultDisplayStackId = mAmWmState.getAmState().getFocusedStackId();
        final ReportedSizes initialSizes = getLastReportedSizesForActivity(TEST_ACTIVITY,
                logSeparator);
        final Rectangle initialAppBounds = readAppBounds(TEST_ACTIVITY, logSeparator);
        assertNotNull("Must report display dimensions", initialSizes);
        assertNotNull("Must report app bounds", initialAppBounds);

        logSeparator = clearLogcat();
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        final ReportedSizes pinnedSizes = getLastReportedSizesForActivity(PIP_ACTIVITY,
                logSeparator);
        final Rectangle pinnedAppBounds = readAppBounds(PIP_ACTIVITY, logSeparator);
        assertFalse("Reported display size when pinned must be different from default",
                initialSizes.equals(pinnedSizes));
        assertFalse("Reported app bounds when pinned must be different from default",
                initialAppBounds.width == pinnedAppBounds.width
                        && initialAppBounds.height == pinnedAppBounds.height);

        logSeparator = clearLogcat();
        launchActivityInStack(PIP_ACTIVITY, defaultDisplayStackId);
        final ReportedSizes finalSizes = getLastReportedSizesForActivity(PIP_ACTIVITY,
                logSeparator);
        final Rectangle finalAppBounds = readAppBounds(PIP_ACTIVITY, logSeparator);
        assertEquals("Must report default size after exiting PiP", initialSizes, finalSizes);
        assertEquals("Must report default app width after exiting PiP", initialAppBounds.width,
                finalAppBounds.width);
        assertEquals("Must report default app height after exiting PiP", initialAppBounds.height,
                finalAppBounds.height);
    }

    private static final Pattern sAppBoundsPattern = Pattern.compile(
            "(.+)appBounds=Rect\\((\\d+), (\\d+) - (\\d+), (\\d+)\\)(.*)");

    /** Read app bounds in last applied configuration from logs. */
    private Rectangle readAppBounds(String activityName, String logSeparator) throws Exception {
        final String[] lines = getDeviceLogsForComponent(activityName, logSeparator);
        for (int i = lines.length - 1; i >= 0; i--) {
            final String line = lines[i].trim();
            final Matcher matcher = sAppBoundsPattern.matcher(line);
            if (matcher.matches()) {
                final int left = Integer.parseInt(matcher.group(2));
                final int top = Integer.parseInt(matcher.group(3));
                final int right = Integer.parseInt(matcher.group(4));
                final int bottom = Integer.parseInt(matcher.group(5));
                return new Rectangle(left, top, right - left, bottom - top);
            }
        }
        return null;
    }

    /**
     * Called after the given {@param activityName} has been moved to the fullscreen stack. Ensures
     * that the {@param focusedStackId} is focused, and checks the top and/or bottom tasks in the
     * fullscreen stack if {@param expectTopTaskHasActivity} or {@param expectBottomTaskHasActivity}
     * are set respectively.
     */
    private void assertPinnedStackStateOnMoveToFullscreen(String activityName, int focusedStackId,
            boolean expectTopTaskHasActivity, boolean expectBottomTaskHasActivity)
                    throws Exception {
        mAmWmState.waitForFocusedStack(mDevice, focusedStackId);
        mAmWmState.assertFocusedStack("Wrong focused stack", focusedStackId);
        mAmWmState.waitForActivityState(mDevice, activityName, STATE_STOPPED);
        assertTrue(mAmWmState.getAmState().hasActivityState(activityName, STATE_STOPPED));
        assertPinnedStackDoesNotExist();

        if (expectTopTaskHasActivity) {
            ActivityTask topTask = mAmWmState.getAmState().getStackById(
                    FULLSCREEN_WORKSPACE_STACK_ID).getTopTask();
            assertTrue(topTask.containsActivity(ActivityManagerTestBase.getActivityComponentName(
                    activityName)));
        }
        if (expectBottomTaskHasActivity) {
            ActivityTask bottomTask = mAmWmState.getAmState().getStackById(
                    FULLSCREEN_WORKSPACE_STACK_ID).getBottomTask();
            assertTrue(bottomTask.containsActivity(ActivityManagerTestBase.getActivityComponentName(
                    activityName)));
        }
    }

    /**
     * Asserts that the pinned stack bounds does not intersect with the IME bounds.
     */
    private void assertPinnedStackDoesNotIntersectIME() throws Exception {
        // Ensure that the IME is visible
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        WindowManagerState.WindowState imeWinState = wmState.getInputMethodWindowState();
        assertTrue(imeWinState != null);

        // Ensure that the PIP movement is constrained by the display bounds intersecting the
        // non-IME bounds
        Rectangle imeContentFrame = imeWinState.getContentFrame();
        Rectangle imeContentInsets = imeWinState.getGivenContentInsets();
        Rectangle imeBounds = new Rectangle(imeContentFrame.x + imeContentInsets.x,
                imeContentFrame.y + imeContentInsets.y,
                imeContentFrame.width - imeContentInsets.width,
                imeContentFrame.height - imeContentInsets.height);
        wmState.computeState(mDevice);
        Rectangle pipMovementBounds = wmState.getPinnedStackMomentBounds();
        assertTrue(!pipMovementBounds.intersects(imeBounds));
    }

    /**
     * Asserts that the pinned stack bounds is contained in the display bounds.
     */
    private void assertPinnedStackActivityIsInDisplayBounds(String activity) throws Exception {
        final WindowManagerState.WindowState windowState = getWindowState(activity);
        final WindowManagerState.Display display = mAmWmState.getWmState().getDisplay(
                windowState.getDisplayId());
        final Rectangle displayRect = display.getDisplayRect();
        final Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        assertTrue(displayRect.contains(pinnedStackBounds));
    }

    /**
     * Asserts that the pinned stack exists.
     */
    private void assertPinnedStackExists() throws Exception {
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
    }

    /**
     * Asserts that the pinned stack does not exist.
     */
    private void assertPinnedStackDoesNotExist() throws Exception {
        mAmWmState.assertDoesNotContainStack("Must not contain pinned stack.", PINNED_STACK_ID);
    }

    /**
     * Asserts that the pinned stack is the front stack.
     */
    private void assertPinnedStackIsOnTop() throws Exception {
        mAmWmState.assertFrontStack("Pinned stack must always be on top.", PINNED_STACK_ID);
    }

    /**
     * Asserts that the activity received exactly one of each of the callbacks when entering and
     * exiting picture-in-picture.
     */
    private void assertValidPictureInPictureCallbackOrder(String activityName, String logSeparator)
            throws Exception {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);

        if (lifecycleCounts.mConfigurationChangedCount != 1) {
            fail(activityName + " has received " + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, expecting 1");
        } else if (lifecycleCounts.mPictureInPictureModeChangedCount != 1) {
            fail(activityName + " has received " + lifecycleCounts.mPictureInPictureModeChangedCount
                    + " onPictureInPictureModeChanged() calls, expecting 1");
        } else if (lifecycleCounts.mMultiWindowModeChangedCount != 1) {
            fail(activityName + " has received " + lifecycleCounts.mMultiWindowModeChangedCount
                    + " onMultiWindowModeChanged() calls, expecting 1");
        } else {
            int lastPipLine = lifecycleCounts.mLastPictureInPictureModeChangedLineIndex;
            int lastMwLine = lifecycleCounts.mLastMultiWindowModeChangedLineIndex;
            int lastConfigLine = lifecycleCounts.mLastConfigurationChangedLineIndex;
            if (!(lastPipLine < lastMwLine && lastMwLine < lastConfigLine)) {
                fail(activityName + " has received callbacks in unexpected order.  Expected:"
                        + " pip < mw < config change, but got line indices: " + lastPipLine + ", "
                        + lastMwLine + ", " + lastConfigLine + " respectively");
            }
        }
    }

    /**
     * Waits until the expected picture-in-picture callbacks have been made.
     */
    private void waitForValidPictureInPictureCallbacks(String activityName, String logSeparator)
            throws Exception {
        mAmWmState.waitFor(mDevice, (amState, wmState) -> {
            try {
                final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(
                        activityName, logSeparator);
                return lifecycleCounts.mConfigurationChangedCount == 1 &&
                        lifecycleCounts.mPictureInPictureModeChangedCount == 1 &&
                        lifecycleCounts.mMultiWindowModeChangedCount == 1;
            } catch (Exception e) {
                return false;
            }
        }, "Waiting for picture-in-picture activity callbacks...");
    }

    /**
     * @return the window state for the given {@param activity}'s window.
     */
    private WindowManagerState.WindowState getWindowState(String activity) throws Exception {
        String windowName = getWindowName(activity);
        mAmWmState.computeState(mDevice, new String[] {activity});
        final List<WindowManagerState.WindowState> tempWindowList = new ArrayList<>();
        mAmWmState.getWmState().getMatchingVisibleWindowState(windowName, tempWindowList);
        return tempWindowList.get(0);
    }

    /**
     * Compares two floats with a common epsilon.
     */
    private boolean floatEquals(float f1, float f2) {
        return Math.abs(f1 - f2) < FLOAT_COMPARE_EPSILON;
    }

    /**
     * Triggers a tap over the pinned stack bounds to trigger the PIP to close.
     */
    private void tapToFinishPip() throws Exception {
        Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        int tapX = pinnedStackBounds.x + pinnedStackBounds.width - 100;
        int tapY = pinnedStackBounds.y + pinnedStackBounds.height - 100;
        executeShellCommand(String.format("input tap %d %d", tapX, tapY));
    }

    /**
     * Launches the given {@param activityName} into the {@param taskId} as a task overlay.
     */
    private void launchActivityAsTaskOverlay(String activityName, int taskId, int stackId)
            throws Exception {
        executeShellCommand(getAmStartCmd(activityName) + " --task " + taskId + " --task-overlay");

        mAmWmState.waitForValidState(mDevice, activityName, stackId);
    }

    /**
     * Sets an app-ops op for a given package to a given mode.
     */
    private void setAppOpsOpToMode(String packageName, String op, int mode) throws Exception {
        executeShellCommand(String.format("appops set %s %s %d", packageName, op, mode));
    }

    /**
     * Triggers the window keycode.
     */
    private void pressWindowButton() throws Exception {
        executeShellCommand(INPUT_KEYEVENT_WINDOW);
    }

    /**
     * TODO: Improve tests check to actually check that apps are not interactive instead of checking
     *       if the stack is focused.
     */
    private void pinnedStackTester(String startActivityCmd, String topActivityName,
            boolean moveTopToPinnedStack, boolean isFocusable) throws Exception {

        executeShellCommand(startActivityCmd);
        if (moveTopToPinnedStack) {
            executeShellCommand(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND);
        }

        mAmWmState.waitForValidState(mDevice, topActivityName, PINNED_STACK_ID);
        mAmWmState.computeState(mDevice, null);

        if (supportsPip()) {
            final String windowName = getWindowName(topActivityName);
            assertPinnedStackExists();
            mAmWmState.assertFrontStack("Pinned stack must be the front stack.", PINNED_STACK_ID);
            mAmWmState.assertVisibility(topActivityName, true);

            if (isFocusable) {
                mAmWmState.assertFocusedStack(
                        "Pinned stack must be the focused stack.", PINNED_STACK_ID);
                mAmWmState.assertFocusedActivity(
                        "Pinned activity must be focused activity.", topActivityName);
                mAmWmState.assertFocusedWindow(
                        "Pinned window must be focused window.", windowName);
                // Not checking for resumed state here because PiP overlay can be launched on top
                // in different task by SystemUI.
            } else {
                // Don't assert that the stack is not focused as a focusable PiP overlay can be
                // launched on top as a task overlay by SystemUI.
                mAmWmState.assertNotFocusedActivity(
                        "Pinned activity can't be the focused activity.", topActivityName);
                mAmWmState.assertNotResumedActivity(
                        "Pinned activity can't be the resumed activity.", topActivityName);
                mAmWmState.assertNotFocusedWindow(
                        "Pinned window can't be focused window.", windowName);
            }
        } else {
            mAmWmState.assertDoesNotContainStack(
                    "Must not contain pinned stack.", PINNED_STACK_ID);
        }
    }
}
