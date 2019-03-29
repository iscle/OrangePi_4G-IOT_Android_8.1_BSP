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

import android.platform.test.annotations.Presubmit;
import android.server.displayservice.DisplayHelper;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.server.cts.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.cts.ActivityManagerState.STATE_PAUSED;
import static android.server.cts.ActivityManagerState.STATE_RESUMED;
import static android.server.cts.ActivityManagerState.STATE_STOPPED;
import static android.server.cts.StateLogger.log;
import static android.server.cts.StateLogger.logE;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerDisplayTests
 */
public class ActivityManagerDisplayTests extends ActivityManagerDisplayTestBase {
    private static final String WM_SIZE = "wm size";
    private static final String WM_DENSITY = "wm density";

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String VIRTUAL_DISPLAY_ACTIVITY = "VirtualDisplayActivity";
    private static final String RESIZEABLE_ACTIVITY_NAME = "ResizeableActivity";
    private static final String NON_RESIZEABLE_ACTIVITY_NAME = "NonResizeableActivity";
    private static final String SECOND_ACTIVITY_NAME = "SecondActivity";
    private static final String SECOND_ACTIVITY_NO_EMBEDDING_NAME = "SecondActivityNoEmbedding";
    private static final String THIRD_ACTIVITY_NAME = "ThirdActivity";
    private static final String VR_TEST_ACTIVITY_NAME = "VrTestActivity";
    private static final String SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME = "ShowWhenLockedAttrActivity";
    private static final String SECOND_PACKAGE_NAME = "android.server.cts.second";
    private static final String THIRD_PACKAGE_NAME = "android.server.cts.third";
    private static final int VR_VIRTUAL_DISPLAY_WIDTH = 70;
    private static final int VR_VIRTUAL_DISPLAY_HEIGHT = 90;
    private static final int VR_VIRTUAL_DISPLAY_DPI = 320;

    private DisplayHelper mExternalDisplayHelper;

    /** Physical display metrics and overrides in the beginning of the test. */
    private ReportedDisplayMetrics mInitialDisplayMetrics;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInitialDisplayMetrics = getDisplayMetrics();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            enablePersistentVrMode(false);
            restoreDisplayMetricsOverrides();
            if (mExternalDisplayHelper != null) {
                mExternalDisplayHelper.releaseDisplay();
                mExternalDisplayHelper = null;
            }
            setPrimaryDisplayState(true);
        } catch (DeviceNotAvailableException e) {
            logE(e.getMessage());
        }
        super.tearDown();
    }

    private void enablePersistentVrMode(boolean enabled) throws Exception {
        if (enabled) {
            executeShellCommand("setprop vr_virtualdisplay true");
            executeShellCommand("vr set-persistent-vr-mode-enabled true");
        } else {
            executeShellCommand("vr set-persistent-vr-mode-enabled false");
            executeShellCommand("setprop vr_virtualdisplay false");
        }
    }

    private void restoreDisplayMetricsOverrides() throws Exception {
        if (mInitialDisplayMetrics.sizeOverrideSet) {
            executeShellCommand(WM_SIZE + " " + mInitialDisplayMetrics.overrideWidth + "x"
                    + mInitialDisplayMetrics.overrideHeight);
        } else {
            executeShellCommand("wm size reset");
        }
        if (mInitialDisplayMetrics.densityOverrideSet) {
            executeShellCommand(WM_DENSITY + " " + mInitialDisplayMetrics.overrideDensity);
        } else {
            executeShellCommand("wm density reset");
        }
    }

    /**
     * Tests that the global configuration is equal to the default display's override configuration.
     */
    public void testDefaultDisplayOverrideConfiguration() throws Exception {
        final ReportedDisplays reportedDisplays = getDisplaysStates();
        assertNotNull("Global configuration must not be empty.", reportedDisplays.mGlobalConfig);
        final DisplayState primaryDisplay = reportedDisplays.getDisplayState(DEFAULT_DISPLAY_ID);
        assertEquals("Primary display's configuration should not be equal to global configuration.",
                reportedDisplays.mGlobalConfig, primaryDisplay.mOverrideConfig);
    }

    /**
     * Tests that secondary display has override configuration set.
     */
    public void testCreateVirtualDisplayWithCustomConfig() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Find the density of created display.
        final int newDensityDpi = newDisplay.getDpi();
        assertEquals(CUSTOM_DENSITY_DPI, newDensityDpi);
    }

    /**
     * Tests that launch on secondary display is not permitted if device has the feature disabled.
     * Activities requested to be launched on a secondary display in this case should land on the
     * default display.
     */
    public void testMultiDisplayDisabled() throws Exception {
        if (supportsMultiDisplay()) {
            // Only check devices with the feature disabled.
            return;
        }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Launched activity must be focused", TEST_ACTIVITY_NAME);

        // Check that activity is on the right display.
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        assertEquals("Front stack must be on the default display", DEFAULT_DISPLAY_ID,
                frontStack.mDisplayId);
        mAmWmState.assertFocusedStack("Focus must be on the default display", frontStackId);
    }

    /**
     * Tests that any new activity launch in Vr mode is in Vr display.
     */
    public void testVrActivityLaunch() throws Exception {
        if (!supportsVrMode() || !supportsMultiDisplay()) {
            // VR Mode is not supported on this device, bail from this test.
            return;
        }

        // Put the device in persistent vr mode.
        enablePersistentVrMode(true);

        // Launch the VR activity.
        launchActivity(VR_TEST_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {VR_TEST_ACTIVITY_NAME});
        mAmWmState.assertVisibility(VR_TEST_ACTIVITY_NAME, true /* visible */);

        // Launch the non-VR 2D activity and check where it ends up.
        launchActivity(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        // Ensure that the subsequent activity is visible
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Check that activity is launched in focused stack on primary display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", LAUNCHING_ACTIVITY);
        final int focusedStackId = mAmWmState.getAmState().getFocusedStackId();
        final ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(focusedStackId);
        assertEquals("Launched activity must be resumed in focused stack",
            getActivityComponentName(LAUNCHING_ACTIVITY), focusedStack.mResumedActivity);

        // Check if the launch activity is in Vr virtual display id.
        final ReportedDisplays reportedDisplays = getDisplaysStates();
        assertNotNull("Global configuration must not be empty.", reportedDisplays.mGlobalConfig);
        final DisplayState vrDisplay = reportedDisplays.getDisplayState(VR_VIRTUAL_DISPLAY_WIDTH,
            VR_VIRTUAL_DISPLAY_HEIGHT, VR_VIRTUAL_DISPLAY_DPI);
        assertNotNull("Vr mode should have a virtual display", vrDisplay);

        // Check if the focused activity is on this virtual stack.
        assertEquals("Launch in Vr mode should be in virtual stack", vrDisplay.mDisplayId,
            focusedStack.mDisplayId);

        // Put the device out of persistent vr mode.
        enablePersistentVrMode(false);
    }

    /**
     * Tests that any activity already present is re-launched in Vr display in vr mode.
     */
    public void testVrActivityReLaunch() throws Exception {
        if (!supportsVrMode() || !supportsMultiDisplay()) {
            // VR Mode is not supported on this device, bail from this test.
            return;
        }

        // Launch a 2D activity.
        launchActivity(LAUNCHING_ACTIVITY);

        // Put the device in persistent vr mode.
        enablePersistentVrMode(true);

        // Launch the VR activity.
        launchActivity(VR_TEST_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {VR_TEST_ACTIVITY_NAME});
        mAmWmState.assertVisibility(VR_TEST_ACTIVITY_NAME, true /* visible */);

        // Re-launch the non-VR 2D activity and check where it ends up.
        launchActivity(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        // Ensure that the subsequent activity is visible
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Check that activity is launched in focused stack on primary display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", LAUNCHING_ACTIVITY);
        final int focusedStackId = mAmWmState.getAmState().getFocusedStackId();
        final ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(focusedStackId);
        assertEquals("Launched activity must be resumed in focused stack",
            getActivityComponentName(LAUNCHING_ACTIVITY), focusedStack.mResumedActivity);

        // Check if the launch activity is in Vr virtual display id.
        final ReportedDisplays reportedDisplays = getDisplaysStates();
        assertNotNull("Global configuration must not be empty.", reportedDisplays.mGlobalConfig);
        final DisplayState vrDisplay = reportedDisplays.getDisplayState(VR_VIRTUAL_DISPLAY_WIDTH,
            VR_VIRTUAL_DISPLAY_HEIGHT, VR_VIRTUAL_DISPLAY_DPI);
        assertNotNull("Vr mode should have a virtual display", vrDisplay);

        // Check if the focused activity is on this virtual stack.
        assertEquals("Launch in Vr mode should be in virtual stack", vrDisplay.mDisplayId,
            focusedStack.mDisplayId);

        // Put the device out of persistent vr mode.
        enablePersistentVrMode(false);
    }

    /**
     * Tests that any new activity launch post Vr mode is in the main display.
     */
    public void testActivityLaunchPostVr() throws Exception {
        if (!supportsVrMode() || !supportsMultiDisplay()) {
            // VR Mode is not supported on this device, bail from this test.
            return;
        }

        // Put the device in persistent vr mode.
        enablePersistentVrMode(true);

        // Launch the VR activity.
        launchActivity(VR_TEST_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {VR_TEST_ACTIVITY_NAME});
        mAmWmState.assertVisibility(VR_TEST_ACTIVITY_NAME, true /* visible */);

        // Launch the non-VR 2D activity and check where it ends up.
        launchActivity(ALT_LAUNCHING_ACTIVITY);
        mAmWmState.computeState(mDevice, new String[] {ALT_LAUNCHING_ACTIVITY});

        // Ensure that the subsequent activity is visible
        mAmWmState.assertVisibility(ALT_LAUNCHING_ACTIVITY, true /* visible */);

        // Check that activity is launched in focused stack on primary display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", ALT_LAUNCHING_ACTIVITY);
        final int focusedStackId = mAmWmState.getAmState().getFocusedStackId();
        final ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(focusedStackId);
        assertEquals("Launched activity must be resumed in focused stack",
            getActivityComponentName(ALT_LAUNCHING_ACTIVITY), focusedStack.mResumedActivity);

        // Check if the launch activity is in Vr virtual display id.
        final ReportedDisplays reportedDisplays = getDisplaysStates();
        assertNotNull("Global configuration must not be empty.", reportedDisplays.mGlobalConfig);
        final DisplayState vrDisplay = reportedDisplays.getDisplayState(VR_VIRTUAL_DISPLAY_WIDTH,
            VR_VIRTUAL_DISPLAY_HEIGHT, VR_VIRTUAL_DISPLAY_DPI);
        assertNotNull("Vr mode should have a virtual display", vrDisplay);

        // Check if the focused activity is on this virtual stack.
        assertEquals("Launch in Vr mode should be in virtual stack", vrDisplay.mDisplayId,
            focusedStack.mDisplayId);

        // Put the device out of persistent vr mode.
        enablePersistentVrMode(false);

        // There isn't a direct launch of activity which can take an user out of persistent VR mode.
        // This sleep is to account for that delay and let device settle once it comes out of VR
        // mode.
        try {
            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Launch the non-VR 2D activity and check where it ends up.
        launchActivity(RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {RESIZEABLE_ACTIVITY_NAME});

        // Ensure that the subsequent activity is visible
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);

        // Check that activity is launched in focused stack on primary display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", RESIZEABLE_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(RESIZEABLE_ACTIVITY_NAME), frontStack.mResumedActivity);
        assertEquals("Front stack must be on primary display",
                DEFAULT_DISPLAY_ID, frontStack.mDisplayId);
    }

    public void testCreateMultipleVirtualDisplays() throws Exception {
        // Create new virtual display.
        final List<DisplayState> newDisplays = new VirtualDisplayBuilder(this).build(3);
        destroyVirtualDisplays();
        getDisplayStateAfterChange(1);
    }

    /**
     * Tests launching an activity on virtual display.
     */
    @Presubmit
    public void testLaunchActivityOnSecondaryDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        final String logSeparator = clearLogcat();
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);

        // Check that activity is on the right display.
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be on the secondary display and resumed",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        // Check that activity config corresponds to display config.
        final ReportedSizes reportedSizes = getLastReportedSizesForActivity(TEST_ACTIVITY_NAME,
                logSeparator);
        assertEquals("Activity launched on secondary display must have proper configuration",
                CUSTOM_DENSITY_DPI, reportedSizes.densityDpi);
    }

    /**
     * Tests launching a non-resizeable activity on virtual display. It should land on the
     * default display.
     */
    public void testLaunchNonResizeableActivityOnSecondaryDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(NON_RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {NON_RESIZEABLE_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                NON_RESIZEABLE_ACTIVITY_NAME);

        // Check that activity is on the right display.
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack frontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be on the primary display and resumed",
                getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on the primary display", frontStackId);
    }

    /**
     * Tests launching a non-resizeable activity on virtual display while split-screen is active
     * on the primary display. It should land on the primary display and dismiss docked stack.
     */
    public void testLaunchNonResizeableActivityWithSplitScreen() throws Exception {
        if (!supportsMultiDisplay() || !supportsSplitScreenMultiWindow()) { return; }

        // Start launching activity.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        // Create new virtual display.
        final DisplayState newDisplay =
                new VirtualDisplayBuilder(this).setLaunchInSplitScreen(true).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(NON_RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {NON_RESIZEABLE_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                NON_RESIZEABLE_ACTIVITY_NAME);

        // Check that activity is on the right display.
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack frontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be on the primary display and resumed",
                getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on the primary display", frontStackId);
        mAmWmState.assertDoesNotContainStack("Must not contain docked stack.", DOCKED_STACK_ID);
    }

    /**
     * Tests moving a non-resizeable activity to a virtual display. It should land on the default
     * display.
     */
    public void testMoveNonResizeableActivityToSecondaryDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        // Launch a non-resizeable activity on a primary display.
        launchActivityInNewTask(NON_RESIZEABLE_ACTIVITY_NAME);
        // Launch a resizeable activity on new secondary display to create a new stack there.
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);

        // Try to move the non-resizeable activity to new secondary display.
        moveActivityToStack(NON_RESIZEABLE_ACTIVITY_NAME, frontStackId);
        mAmWmState.computeState(mDevice, new String[] {NON_RESIZEABLE_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                NON_RESIZEABLE_ACTIVITY_NAME);

        // Check that activity is on the right display.
        frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack frontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be on the primary display and resumed",
                getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on the primary display", frontStackId);
    }

    /**
     * Tests launching a non-resizeable activity on virtual display from activity there. It should
     * land on the secondary display based on the resizeability of the root activity of the task.
     */
    public void testLaunchNonResizeableActivityFromSecondaryDisplaySameTask() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).setSimulateDisplay(true)
                .build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                BROADCAST_RECEIVER_ACTIVITY);

        // Check that launching activity is on the secondary display.
        int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        ActivityManagerState.ActivityStack frontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be on the secondary display and resumed",
                getActivityComponentName(BROADCAST_RECEIVER_ACTIVITY),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on the secondary display", frontStackId);

        // Launch non-resizeable activity from secondary display.
        executeShellCommand("am broadcast -a trigger_broadcast --ez launch_activity true "
                + "--ez new_task true --es target_activity " + NON_RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {NON_RESIZEABLE_ACTIVITY_NAME});

        // Check that non-resizeable activity is on the secondary display, because of the resizeable
        // root of the task.
        frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        frontStack = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be on the primary display and resumed",
                getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on the primary display", frontStackId);
    }

    /**
     * Tests launching a non-resizeable activity on virtual display from activity there. It should
     * land on some different suitable display (usually - on the default one).
     */
    public void testLaunchNonResizeableActivityFromSecondaryDisplayNewTask() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                LAUNCHING_ACTIVITY);

        // Check that launching activity is on the secondary display.
        int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        ActivityManagerState.ActivityStack frontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be on the secondary display and resumed",
                getActivityComponentName(LAUNCHING_ACTIVITY),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on the secondary display", frontStackId);

        // Launch non-resizeable activity from secondary display.
        getLaunchActivityBuilder().setTargetActivityName(NON_RESIZEABLE_ACTIVITY_NAME)
                .setNewTask(true).setMultipleTask(true).execute();

        // Check that non-resizeable activity is on the primary display.
        frontStackId = mAmWmState.getAmState().getFocusedStackId();
        frontStack = mAmWmState.getAmState().getStackById(frontStackId);
        assertFalse("Launched activity must be on a different display",
                newDisplay.mDisplayId == frontStack.mDisplayId);
        assertEquals("Launched activity must be resumed",
                getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on a just launched activity", frontStackId);
    }

    /**
     * Tests launching an activity on a virtual display without special permission must not be
     * allowed.
     */
    public void testLaunchWithoutPermissionOnVirtualDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        final String logSeparator = clearLogcat();

        // Try to launch an activity and check it security exception was triggered.
        final String broadcastTarget = "-a " + SECOND_PACKAGE_NAME + ".LAUNCH_BROADCAST_ACTION"
                + " -p " + SECOND_PACKAGE_NAME;
        final String includeStoppedPackagesFlag = " -f 0x00000020";
        executeShellCommand("am broadcast " + broadcastTarget
                + " --ez launch_activity true --es target_activity " + TEST_ACTIVITY_NAME
                + " --es package_name " + componentName
                + " --ei display_id " + newDisplay.mDisplayId
                + includeStoppedPackagesFlag);

        assertSecurityException("LaunchBroadcastReceiver", logSeparator);

        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        assertFalse("Restricted activity must not be launched",
                mAmWmState.getAmState().containsActivity(TEST_ACTIVITY_NAME));
    }

    /**
     * Tests launching an activity on a virtual display without special permission must be allowed
     * for activities with same UID.
     */
    public void testLaunchWithoutPermissionOnVirtualDisplayByOwner() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Try to launch an activity and check it security exception was triggered.
        final String broadcastTarget = "-a " + componentName + ".LAUNCH_BROADCAST_ACTION"
                + " -p " + componentName;
        executeShellCommand("am broadcast " + broadcastTarget
                + " --ez launch_activity true --es target_activity " + TEST_ACTIVITY_NAME
                + " --es package_name " + componentName
                + " --ei display_id " + newDisplay.mDisplayId);

        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY_NAME);

        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        final ActivityManagerState.ActivityStack focusedStack = mAmWmState.getAmState()
                .getStackById(externalFocusedStackId);
        assertEquals("Focused stack must be on secondary display", newDisplay.mDisplayId,
                focusedStack.mDisplayId);

        mAmWmState.assertFocusedActivity("Focus must be on newly launched app", TEST_ACTIVITY_NAME);
        assertEquals("Activity launched by owner must be on external display",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity via shell
     * command and without specifying the display id - the second activity must appear on the
     * primary display.
     */
    @Presubmit
    public void testConsequentLaunchActivity() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);

        // Launch second activity without specifying display.
        launchActivity(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        // Check that activity is launched in focused stack on primary display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", LAUNCHING_ACTIVITY);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(LAUNCHING_ACTIVITY), frontStack.mResumedActivity);
        assertEquals("Front stack must be on primary display",
                DEFAULT_DISPLAY_ID, frontStack.mDisplayId);
    }

    /**
     * Tests launching an activity on simulated display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    @Presubmit
    public void testConsequentLaunchActivityFromSecondaryDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).setSimulateDisplay(true)
                .build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be resumed",
                LAUNCHING_ACTIVITY);

        // Launch second activity from app on secondary display without specifying display id.
        getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME).execute();
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        // Check that activity is launched in focused stack on external display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    public void testConsequentLaunchActivityFromVirtualDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be resumed",
                LAUNCHING_ACTIVITY);

        // Launch second activity from app on secondary display without specifying display id.
        getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME).execute();
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        // Check that activity is launched in focused stack on external display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one with specifying the target display - it must appear on the secondary display.
     */
    public void testConsequentLaunchActivityFromVirtualDisplayToTargetDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be resumed",
                LAUNCHING_ACTIVITY);

        // Launch second activity from app on secondary display specifying same display id.
        getLaunchActivityBuilder().setTargetActivityName(SECOND_ACTIVITY_NAME)
                .setTargetPackage(SECOND_PACKAGE_NAME)
                .setDisplayId(newDisplay.mDisplayId).execute();
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        // Check that activity is launched in focused stack on external display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", SECOND_PACKAGE_NAME,
                SECOND_ACTIVITY_NAME);
        int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(SECOND_PACKAGE_NAME, SECOND_ACTIVITY_NAME),
                frontStack.mResumedActivity);

        // Launch other activity with different uid and check if it has launched successfully.
        final String broadcastAction = SECOND_PACKAGE_NAME + ".LAUNCH_BROADCAST_ACTION";
        executeShellCommand("am broadcast -a " + broadcastAction + " -p " + SECOND_PACKAGE_NAME
                + " --ei display_id " + newDisplay.mDisplayId
                + " --es target_activity " + THIRD_ACTIVITY_NAME
                + " --es package_name " + THIRD_PACKAGE_NAME);
        mAmWmState.waitForValidState(mDevice, new String[] {THIRD_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, THIRD_PACKAGE_NAME);

        // Check that activity is launched in focused stack on external display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", THIRD_PACKAGE_NAME,
                THIRD_ACTIVITY_NAME);
        frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        frontStack = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(THIRD_PACKAGE_NAME, THIRD_ACTIVITY_NAME),
                frontStack.mResumedActivity);
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity that
     * doesn't allow embedding - it should fail with security exception.
     */
    public void testConsequentLaunchActivityFromVirtualDisplayNoEmbedding() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be resumed",
                LAUNCHING_ACTIVITY);

        final String logSeparator = clearLogcat();

        // Launch second activity from app on secondary display specifying same display id.
        getLaunchActivityBuilder().setTargetActivityName(SECOND_ACTIVITY_NO_EMBEDDING_NAME)
                .setTargetPackage(SECOND_PACKAGE_NAME)
                .setDisplayId(newDisplay.mDisplayId).execute();

        assertSecurityException("ActivityLauncher", logSeparator);
    }

    /**
     * Tests launching an activity to secondary display from activity on primary display.
     */
    public void testLaunchActivityFromAppToSecondaryDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Start launching activity.
        launchActivity(LAUNCHING_ACTIVITY);
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).setSimulateDisplay(true)
                .build();

        // Launch activity on secondary display from the app on primary display.
        getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME)
                .setDisplayId(newDisplay.mDisplayId).execute();

        // Check that activity is launched on external display.
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
    }

    /**
     * Tests launching activities on secondary and then on primary display to see if the stack
     * visibility is not affected.
     */
    @Presubmit
    public void testLaunchActivitiesAffectsVisibility() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Start launching activity.
        launchActivity(LAUNCHING_ACTIVITY);
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on primary display and check if it doesn't affect activity on secondary
        // display.
        getLaunchActivityBuilder().setTargetActivityName(RESIZEABLE_ACTIVITY_NAME).execute();
        mAmWmState.waitForValidState(mDevice, RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
    }

    /**
     * Test that move-task works when moving between displays.
     */
    @Presubmit
    public void testMoveTaskBetweenDisplays() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        final int defaultDisplayStackId = mAmWmState.getAmState().getFocusedStackId();
        ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(defaultDisplayStackId);
        assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                focusedStack.mDisplayId);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display", TEST_ACTIVITY_NAME);
        int focusedStackId = mAmWmState.getAmState().getFocusedStackId();
        focusedStack = mAmWmState.getAmState().getStackById(focusedStackId);
        assertEquals("Focused stack must be on secondary display",
                newDisplay.mDisplayId, focusedStack.mDisplayId);

        // Move activity from secondary display to primary.
        moveActivityToStack(TEST_ACTIVITY_NAME, defaultDisplayStackId);
        mAmWmState.waitForFocusedStack(mDevice, defaultDisplayStackId);
        mAmWmState.assertFocusedActivity("Focus must be on moved activity", TEST_ACTIVITY_NAME);
        focusedStackId = mAmWmState.getAmState().getFocusedStackId();
        focusedStack = mAmWmState.getAmState().getStackById(focusedStackId);
        assertEquals("Focus must return to primary display", DEFAULT_DISPLAY_ID,
                focusedStack.mDisplayId);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to fullscreen stack in split-screen.
     */
    @Presubmit
    public void testStackFocusSwitchOnDisplayRemoved() throws Exception {
        if (!supportsMultiDisplay() || !supportsSplitScreenMultiWindow()) { return; }

        // Start launching activity into docked stack.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity(true /* splitScreen */,
                FULLSCREEN_WORKSPACE_STACK_ID);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to docked stack in split-screen.
     */
    public void testStackFocusSwitchOnDisplayRemoved2() throws Exception {
        if (!supportsMultiDisplay() || !supportsSplitScreenMultiWindow()) { return; }

        // Setup split-screen.
        launchActivityInDockStack(RESIZEABLE_ACTIVITY_NAME);

        // Start launching activity into fullscreen stack.
        launchActivityInStack(LAUNCHING_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity(true /* splitScreen */,
                FULLSCREEN_WORKSPACE_STACK_ID);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version works without split-screen.
     */
    public void testStackFocusSwitchOnDisplayRemoved3() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Start an activity on default display to determine default stack.
        launchActivity(BROADCAST_RECEIVER_ACTIVITY);
        final int focusedStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        // Finish probing activity.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);

        tryCreatingAndRemovingDisplayWithActivity(false /* splitScreen */, focusedStackId);
    }

    /**
     * Create a virtual display, launch a test activity there, destroy the display and check if test
     * activity is moved to a stack on the default display.
     */
    private void tryCreatingAndRemovingDisplayWithActivity(boolean splitScreen, int defaultStackId)
            throws Exception {
        // Create new virtual display.
        final VirtualDisplayBuilder builder = new VirtualDisplayBuilder(this)
                .setPublicDisplay(true);
        if (splitScreen) {
            builder.setLaunchInSplitScreen(true);
        }
        final DisplayState newDisplay = builder.build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        if (splitScreen) {
            mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);
        }

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        // Destroy virtual display.
        destroyVirtualDisplays();
        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY_NAME, defaultStackId);
        mAmWmState.assertSanity();
        mAmWmState.assertValidBounds(true /* compareTaskAndStackBounds */);

        // Check if the focus is switched back to primary display.
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedStack(
                "Default stack on primary display must be focused after display removed",
                defaultStackId);
        mAmWmState.assertFocusedActivity(
                "Focus must be switched back to activity on primary display",
                TEST_ACTIVITY_NAME);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     */
    public void testStackFocusSwitchOnStackEmptied() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        final int focusedStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                BROADCAST_RECEIVER_ACTIVITY);

        // Lock the device, so that activity containers will be detached.
        sleepDevice();

        // Finish activity on secondary display.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);

        // Unlock and check if the focus is switched back to primary display.
        wakeUpAndUnlockDevice();
        mAmWmState.waitForFocusedStack(mDevice, focusedStackId);
        mAmWmState.waitForValidState(mDevice, VIRTUAL_DISPLAY_ACTIVITY);
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                VIRTUAL_DISPLAY_ACTIVITY);
    }

    /**
     * Tests that input events on the primary display take focus from the virtual display.
     */
    public void testStackFocusSwitchOnTouchEvent() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        mAmWmState.computeState(mDevice, new String[] {VIRTUAL_DISPLAY_ACTIVITY});
        mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                VIRTUAL_DISPLAY_ACTIVITY);

        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);

        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);

        final ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
        final int width = displayMetrics.getWidth();
        final int height = displayMetrics.getHeight();
        executeShellCommand("input tap " + (width / 2) + " " + (height / 2));

        mAmWmState.computeState(mDevice, new String[] {VIRTUAL_DISPLAY_ACTIVITY});
        mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                VIRTUAL_DISPLAY_ACTIVITY);
    }

    /** Test that shell is allowed to launch on secondary displays. */
    public void testPermissionLaunchFromShell() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(defaultDisplayFocusedStackId);
        assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                focusedStack.mDisplayId);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        focusedStack = mAmWmState.getAmState().getStackById(externalFocusedStackId);
        assertEquals("Focused stack must be on secondary display", newDisplay.mDisplayId,
                focusedStack.mDisplayId);

        // Launch other activity with different uid and check it is launched on dynamic stack on
        // secondary display.
        final String startCmd =  "am start -n " + SECOND_PACKAGE_NAME + "/." + SECOND_ACTIVITY_NAME
                + " --display " + newDisplay.mDisplayId;
        executeShellCommand(startCmd);

        mAmWmState.waitForValidState(mDevice, new String[] {SECOND_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, SECOND_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app", SECOND_PACKAGE_NAME,
                SECOND_ACTIVITY_NAME);
        assertEquals("Activity launched by system must be on external display",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /** Test that launching from app that is on external display is allowed. */
    public void testPermissionLaunchFromAppOnSecondary() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).setSimulateDisplay(true)
                .build();

        // Launch activity with different uid on secondary display.
        final String startCmd =  "am start -n " + SECOND_PACKAGE_NAME + "/." + SECOND_ACTIVITY_NAME;
        final String displayTarget = " --display " + newDisplay.mDisplayId;
        executeShellCommand(startCmd + displayTarget);

        mAmWmState.waitForValidState(mDevice, new String[] {SECOND_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, SECOND_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                SECOND_PACKAGE_NAME, SECOND_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(externalFocusedStackId);
        assertEquals("Focused stack must be on secondary display", newDisplay.mDisplayId,
                focusedStack.mDisplayId);

        // Launch another activity with third different uid from app on secondary display and check
        // it is launched on secondary display.
        final String broadcastAction = SECOND_PACKAGE_NAME + ".LAUNCH_BROADCAST_ACTION";
        final String targetActivity = " --es target_activity " + THIRD_ACTIVITY_NAME
                + " --es package_name " + THIRD_PACKAGE_NAME
                + " --ei display_id " + newDisplay.mDisplayId;
        final String includeStoppedPackagesFlag = " -f 0x00000020";
        executeShellCommand("am broadcast -a " + broadcastAction + " -p " + SECOND_PACKAGE_NAME
                + targetActivity + includeStoppedPackagesFlag);

        mAmWmState.waitForValidState(mDevice, new String[] {THIRD_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, THIRD_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                THIRD_PACKAGE_NAME, THIRD_ACTIVITY_NAME);
        assertEquals("Activity launched by app on secondary display must be on that display",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /** Tests that an activity can launch an activity from a different UID into its own task. */
    public void testPermissionLaunchMultiUidTask() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        final DisplayState newDisplay = new VirtualDisplayBuilder(this).setSimulateDisplay(true)
                .build();

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        // Check that the first activity is launched onto the secondary display
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        ActivityManagerState.ActivityStack frontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityComponentName(LAUNCHING_ACTIVITY),
                frontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        // Launch an activity from a different UID into the first activity's task
        getLaunchActivityBuilder()
                .setTargetPackage(SECOND_PACKAGE_NAME)
                .setTargetActivityName(SECOND_ACTIVITY_NAME).execute();

        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);
        frontStack = mAmWmState.getAmState().getStackById(frontStackId);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                SECOND_PACKAGE_NAME, SECOND_ACTIVITY_NAME);
        assertEquals("Secondary display must contain 1 task", 1, frontStack.getTasks().size());
    }

    /**
     * Test that launching from display owner is allowed even when the the display owner
     * doesn't have anything on the display.
     */
    public void testPermissionLaunchFromOwner() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(defaultDisplayFocusedStackId);
        assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                focusedStack.mDisplayId);

        // Launch other activity with different uid on secondary display.
        final String startCmd =  "am start -n " + SECOND_PACKAGE_NAME + "/." + SECOND_ACTIVITY_NAME;
        final String displayTarget = " --display " + newDisplay.mDisplayId;
        executeShellCommand(startCmd + displayTarget);

        mAmWmState.waitForValidState(mDevice, new String[] {SECOND_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, SECOND_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                SECOND_PACKAGE_NAME, SECOND_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        focusedStack = mAmWmState.getAmState().getStackById(externalFocusedStackId);
        assertEquals("Focused stack must be on secondary display", newDisplay.mDisplayId,
                focusedStack.mDisplayId);

        // Check that owner uid can launch its own activity on secondary display.
        final String broadcastAction = componentName + ".LAUNCH_BROADCAST_ACTION";
        executeShellCommand("am broadcast -a " + broadcastAction + " -p " + componentName
                + " --ez launch_activity true --ez new_task true --ez multiple_task true"
                + " --ei display_id " + newDisplay.mDisplayId);

        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app", TEST_ACTIVITY_NAME);
        assertEquals("Activity launched by owner must be on external display",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /**
     * Test that launching from app that is not present on external display and doesn't own it to
     * that external display is not allowed.
     */
    public void testPermissionLaunchFromDifferentApp() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(defaultDisplayFocusedStackId);
        assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                focusedStack.mDisplayId);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        focusedStack = mAmWmState.getAmState().getStackById(externalFocusedStackId);
        assertEquals("Focused stack must be on secondary display", newDisplay.mDisplayId,
                focusedStack.mDisplayId);

        final String logSeparator = clearLogcat();

        // Launch other activity with different uid and check security exception is triggered.
        final String broadcastAction = SECOND_PACKAGE_NAME + ".LAUNCH_BROADCAST_ACTION";
        final String includeStoppedPackagesFlag = " -f 0x00000020";
        executeShellCommand("am broadcast -a " + broadcastAction + " -p " + SECOND_PACKAGE_NAME
                + " --ei display_id " + newDisplay.mDisplayId + includeStoppedPackagesFlag);

        assertSecurityException("LaunchBroadcastReceiver", logSeparator);

        mAmWmState.waitForValidState(mDevice, new String[] {TEST_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);
        mAmWmState.assertFocusedActivity(
                "Focus must be on first activity", componentName, TEST_ACTIVITY_NAME);
        assertEquals("Focused stack must be on secondary display's stack",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    private void assertSecurityException(String component, String logSeparator) throws Exception {
        int tries = 0;
        boolean match = false;
        final Pattern pattern = Pattern.compile(".*SecurityException launching activity.*");
        while (tries < 5 && !match) {
            String[] logs = getDeviceLogsForComponent(component, logSeparator);
            for (String line : logs) {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    match = true;
                    break;
                }
            }
            tries++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }

        assertTrue("Expected exception not found", match);
    }

    /**
     * Test that only private virtual display can show content with insecure keyguard.
     */
    public void testFlagShowWithInsecureKeyguardOnPublicVirtualDisplay() throws Exception {
        if (!supportsMultiDisplay()) {
            return;
        }

        // Try to create new show-with-insecure-keyguard public virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this)
                .setPublicDisplay(true)
                .setCanShowWithInsecureKeyguard(true)
                .setMustBeCreated(false)
                .build();

        // Check that the display is not created.
        assertNull(newDisplay);
    }

    /**
     * Test that all activities that were on the private display are destroyed on display removal.
     */
    // TODO: Flaky, add to presubmit when b/63404575 is fixed.
    public void testContentDestroyOnDisplayRemoved() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new private virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activities on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedActivity("Launched activity must be focused", TEST_ACTIVITY_NAME);
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedActivity("Launched activity must be focused",
                RESIZEABLE_ACTIVITY_NAME);

        // Destroy the display and check if activities are removed from system.
        final String logSeparator = clearLogcat();
        destroyVirtualDisplays();
        final String activityName1
                = ActivityManagerTestBase.getActivityComponentName(TEST_ACTIVITY_NAME);
        final String activityName2
                = ActivityManagerTestBase.getActivityComponentName(RESIZEABLE_ACTIVITY_NAME);
        final String windowName1
                = ActivityManagerTestBase.getWindowName(TEST_ACTIVITY_NAME);
        final String windowName2
                = ActivityManagerTestBase.getWindowName(RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.waitForWithAmState(mDevice,
                (state) -> !state.containsActivity(activityName1)
                        && !state.containsActivity(activityName2),
                "Waiting for activity to be removed");
        mAmWmState.waitForWithWmState(mDevice,
                (state) -> !state.containsWindow(windowName1)
                        && !state.containsWindow(windowName2),
                "Waiting for activity window to be gone");

        // Check AM state.
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(activityName1));
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(activityName2));
        // Check WM state.
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(windowName1));
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(windowName2));
        // Check activity logs.
        assertActivityDestroyed(TEST_ACTIVITY_NAME, logSeparator);
        assertActivityDestroyed(RESIZEABLE_ACTIVITY_NAME, logSeparator);
    }

    /**
     * Test that the update of display metrics updates all its content.
     */
    @Presubmit
    public void testDisplayResize() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch a resizeable activity on new secondary display.
        final String initialLogSeparator = clearLogcat();
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedActivity("Launched activity must be focused",
                RESIZEABLE_ACTIVITY_NAME);

        // Grab reported sizes and compute new with slight size change.
        final ReportedSizes initialSize = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY_NAME,
                initialLogSeparator);

        // Resize the docked stack, so that activity with virtual display will also be resized.
        final String logSeparator = clearLogcat();
        executeShellCommand(getResizeVirtualDisplayCommand());

        mAmWmState.waitForWithAmState(mDevice, amState -> {
            try {
                return readConfigChangeNumber(RESIZEABLE_ACTIVITY_NAME, logSeparator) == 1
                        && amState.hasActivityState(RESIZEABLE_ACTIVITY_NAME, STATE_RESUMED);
            } catch (Exception e) {
                logE("Error waiting for valid state: " + e.getMessage());
                return false;
            }
        }, "Wait for the configuration change to happen and for activity to be resumed.");

        mAmWmState.computeState(mDevice, new String[] {RESIZEABLE_ACTIVITY_NAME,
                VIRTUAL_DISPLAY_ACTIVITY}, false /* compareTaskAndStackBounds */);
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true);

        // Check if activity in virtual display was resized properly.
        assertRelaunchOrConfigChanged(RESIZEABLE_ACTIVITY_NAME, 0 /* numRelaunch */,
                1 /* numConfigChange */, logSeparator);

        final ReportedSizes updatedSize = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY_NAME,
                logSeparator);
        assertTrue(updatedSize.widthDp <= initialSize.widthDp);
        assertTrue(updatedSize.heightDp <= initialSize.heightDp);
        assertTrue(updatedSize.displayWidth == initialSize.displayWidth / 2);
        assertTrue(updatedSize.displayHeight == initialSize.displayHeight / 2);
    }

    /** Read the number of configuration changes sent to activity from logs. */
    private int readConfigChangeNumber(String activityName, String logSeparator) throws Exception {
        return (new ActivityLifecycleCounts(activityName, logSeparator)).mConfigurationChangedCount;
    }

    /**
     * Tests that when an activity is launched with displayId specified and there is an existing
     * matching task on some other display - that task will moved to the target display.
     */
    public void testMoveToDisplayOnLaunch() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Launch activity with unique affinity, so it will the only one in its task.
        launchActivity(LAUNCHING_ACTIVITY);

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        final int defaultDisplayStackId = mAmWmState.getAmState().getFocusedStackId();
        // Launch something to that display so that a new stack is created. We need this to be able
        // to compare task numbers in stacks later.
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);

        final int taskNum = mAmWmState.getAmState().getStackById(defaultDisplayStackId)
                .getTasks().size();
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final int taskNumOnSecondary = mAmWmState.getAmState().getStackById(frontStackId)
                .getTasks().size();

        // Launch activity on new secondary display.
        // Using custom command here, because normally we add flags Intent#FLAG_ACTIVITY_NEW_TASK
        // and Intent#FLAG_ACTIVITY_MULTIPLE_TASK when launching on some specific display. We don't
        // do it here as we want an existing task to be used.
        final String launchCommand = "am start -n " + getActivityComponentName(LAUNCHING_ACTIVITY)
                + " --display " + newDisplay.mDisplayId;
        executeShellCommand(launchCommand);
        mAmWmState.waitForActivityState(mDevice, LAUNCHING_ACTIVITY, STATE_RESUMED);

        // Check that activity is brought to front.
        mAmWmState.assertFocusedActivity("Existing task must be brought to front",
                LAUNCHING_ACTIVITY);
        mAmWmState.assertResumedActivity("Existing task must be resumed", LAUNCHING_ACTIVITY);

        // Check that activity is on the right display.
        final ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity must be moved to the secondary display",
                getActivityComponentName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        // Check that task has moved from primary display to secondary.
        final int taskNumFinal = mAmWmState.getAmState().getStackById(defaultDisplayStackId)
                .getTasks().size();
        mAmWmState.assertEquals("Task number in default stack must be decremented.", taskNum - 1,
                taskNumFinal);
        final int taskNumFinalOnSecondary = mAmWmState.getAmState().getStackById(frontStackId)
                .getTasks().size();
        mAmWmState.assertEquals("Task number in stack on external display must be incremented.",
                taskNumOnSecondary + 1, taskNumFinalOnSecondary);
    }

    /**
     * Tests that when primary display is rotated secondary displays are not affected.
     */
    public void testRotationNotAffectingSecondaryScreen() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this)
                .setResizeDisplay(false)
                .build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        String logSeparator = clearLogcat();
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                RESIZEABLE_ACTIVITY_NAME);
        final ReportedSizes initialSizes = getLastReportedSizesForActivity(
                RESIZEABLE_ACTIVITY_NAME, logSeparator);
        assertNotNull("Test activity must have reported initial sizes on launch", initialSizes);

        // Rotate primary display and check that activity on secondary display is not affected.
        rotateAndCheckSameSizes(RESIZEABLE_ACTIVITY_NAME);

        // Launch activity to secondary display when primary one is rotated.
        final int initialRotation = mAmWmState.getWmState().getRotation();
        setDeviceRotation((initialRotation + 1) % 4);

        logSeparator = clearLogcat();
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_RESUMED);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final ReportedSizes testActivitySizes = getLastReportedSizesForActivity(
                TEST_ACTIVITY_NAME, logSeparator);
        assertEquals("Sizes of secondary display must not change after rotation of primary display",
                initialSizes, testActivitySizes);
    }

    private void rotateAndCheckSameSizes(String activityName) throws Exception {
        for (int rotation = 3; rotation >= 0; --rotation) {
            final String logSeparator = clearLogcat();
            setDeviceRotation(rotation);
            final ReportedSizes rotatedSizes = getLastReportedSizesForActivity(activityName,
                    logSeparator);
            assertNull("Sizes must not change after rotation", rotatedSizes);
        }
    }

    /**
     * Tests that task affinity does affect what display an activity is launched on but that
     * matching the task component root does.
     */
    public void testTaskMatchAcrossDisplays() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        // Check that activity is on the right display.
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityComponentName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        executeShellCommand("am start -n " + getActivityComponentName(ALT_LAUNCHING_ACTIVITY));
        mAmWmState.waitForValidState(mDevice, new String[] {ALT_LAUNCHING_ACTIVITY},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);

        // Check that second activity gets launched on the default display
        final int defaultDisplayFrontStackId = mAmWmState.getAmState().getFrontStackId(
                DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack defaultDisplayFrontStack =
                mAmWmState.getAmState().getStackById(defaultDisplayFrontStackId);
        assertEquals("Activity launched on default display must be resumed",
                getActivityComponentName(ALT_LAUNCHING_ACTIVITY),
                defaultDisplayFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on primary display",
                defaultDisplayFrontStackId);

        executeShellCommand("am start -n " + getActivityComponentName(LAUNCHING_ACTIVITY));
        mAmWmState.waitForFocusedStack(mDevice, frontStackId);

        // Check that the third intent is redirected to the first task
        final ActivityManagerState.ActivityStack secondFrontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on default display must be resumed",
                getActivityComponentName(LAUNCHING_ACTIVITY), secondFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on primary display", frontStackId);
        assertEquals("Focused stack must only contain 1 task",
                1, secondFrontStack.getTasks().size());
        assertEquals("Focused task must only contain 1 activity",
                1, secondFrontStack.getTasks().get(0).mActivities.size());
    }

    /**
     * Tests than a new task launched by an activity will end up on that activity's display
     * even if the focused stack is not on that activity's display.
     */
    public void testNewTaskSameDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        final DisplayState newDisplay = new VirtualDisplayBuilder(this).setSimulateDisplay(true)
                .build();

        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {BROADCAST_RECEIVER_ACTIVITY});

        // Check that the first activity is launched onto the secondary display
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityComponentName(BROADCAST_RECEIVER_ACTIVITY),
                firstFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        executeShellCommand("am start -n " + getActivityComponentName(TEST_ACTIVITY_NAME));
        mAmWmState.waitForValidState(mDevice, new String[] {TEST_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);

        // Check that the second activity is launched on the default display
        final int focusedStackId = mAmWmState.getAmState().getFocusedStackId();
        final ActivityManagerState.ActivityStack focusedStack
                = mAmWmState.getAmState().getStackById(focusedStackId);
        assertEquals("Activity launched on default display must be resumed",
                getActivityComponentName(TEST_ACTIVITY_NAME), focusedStack.mResumedActivity);
        assertEquals("Focus must be on primary display", DEFAULT_DISPLAY_ID,
                focusedStack.mDisplayId);

        executeShellCommand("am broadcast -a trigger_broadcast --ez launch_activity true "
                + "--ez new_task true --es target_activity " + LAUNCHING_ACTIVITY);

        // Check that the third activity ends up in a new task in the same stack as the
        // first activity
        mAmWmState.waitForValidState(mDevice, new String[] {LAUNCHING_ACTIVITY},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);
        final ActivityManagerState.ActivityStack secondFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity must be launched on secondary display",
                getActivityComponentName(LAUNCHING_ACTIVITY),
                secondFrontStack.mResumedActivity);
        assertEquals("Secondary display must contain 2 tasks",
                2, secondFrontStack.getTasks().size());
    }

    /**
     * Test that display overrides apply correctly and won't be affected by display changes.
     * This sets overrides to display size and density, initiates a display changed event by locking
     * and unlocking the phone and verifies that overrides are kept.
     */
    @Presubmit
    public void testForceDisplayMetrics() throws Exception {
        launchHomeActivity();

        // Read initial sizes.
        final ReportedDisplayMetrics originalDisplayMetrics = getDisplayMetrics();

        // Apply new override values that don't match the physical metrics.
        final int overrideWidth = (int) (originalDisplayMetrics.physicalWidth * 1.5);
        final int overrideHeight = (int) (originalDisplayMetrics.physicalHeight * 1.5);
        executeShellCommand(WM_SIZE + " " + overrideWidth + "x" + overrideHeight);
        final int overrideDensity = (int) (originalDisplayMetrics.physicalDensity * 1.1);
        executeShellCommand(WM_DENSITY + " " + overrideDensity);

        // Check if overrides applied correctly.
        ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
        assertEquals(overrideWidth, displayMetrics.overrideWidth);
        assertEquals(overrideHeight, displayMetrics.overrideHeight);
        assertEquals(overrideDensity, displayMetrics.overrideDensity);

        // Lock and unlock device. This will cause a DISPLAY_CHANGED event to be triggered and
        // might update the metrics.
        sleepDevice();
        wakeUpAndUnlockDevice();
        mAmWmState.waitForHomeActivityVisible(mDevice);

        // Check if overrides are still applied.
        displayMetrics = getDisplayMetrics();
        assertEquals(overrideWidth, displayMetrics.overrideWidth);
        assertEquals(overrideHeight, displayMetrics.overrideHeight);
        assertEquals(overrideDensity, displayMetrics.overrideDensity);

        // All overrides will be cleared in tearDown.
    }

    /**
     * Tests than an immediate launch after new display creation is handled correctly.
     */
    public void testImmediateLaunchOnNewDisplay() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Create new virtual display and immediately launch an activity on it.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this)
                .setLaunchActivity(TEST_ACTIVITY_NAME).build();

        // Check that activity is launched and placed correctly.
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_RESUMED);
        mAmWmState.assertResumedActivity("Test activity must be launched on a new display",
                TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityComponentName(TEST_ACTIVITY_NAME), firstFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);
    }

    /**
     * Tests that turning the primary display off does not affect the activity running
     * on an external secondary display.
     */
    public void testExternalDisplayActivityTurnPrimaryOff() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY_NAME);
        waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                "Activity launched on primary display must be resumed");

        final DisplayState newDisplay = createExternalVirtualDisplay(
                true /* showContentWhenLocked */);

        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);

        // Check that the activity is launched onto the external display
        waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mDisplayId,
                "Activity launched on external display must be resumed");

        setPrimaryDisplayState(false);

        // Wait for the fullscreen stack to start sleeping, and then make sure the
        // test activity is still resumed. Note that on some devices, the top activity may go to
        // the stopped state by itself on sleep, causing the server side to believe it is still
        // paused.
        waitAndAssertActivityPausedOrStopped(RESIZEABLE_ACTIVITY_NAME,
                "Activity launched on primary display must be stopped or paused after turning off");
        waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mDisplayId,
                "Activity launched on external display must be resumed");
    }

    /**
     * Tests that an activity can be launched on a secondary display while the primary
     * display is off.
     */
    public void testLaunchExternalDisplayActivityWhilePrimaryOff() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY_NAME);
        waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                "Activity launched on primary display must be resumed");

        setPrimaryDisplayState(false);

        // Make sure there is no resumed activity when the primary display is off
        waitAndAssertActivityStopped(RESIZEABLE_ACTIVITY_NAME,
                "Activity launched on primary display must be stopped after turning off");
        assertEquals("Unexpected resumed activity",
                0, mAmWmState.getAmState().getResumedActivitiesCount());

        final DisplayState newDisplay = createExternalVirtualDisplay(
                true /* showContentWhenLocked */);

        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);

        // Check that the test activity is resumed on the external display
        waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mDisplayId,
                "Activity launched on external display must be resumed");
    }

    /**
     * Tests that turning the secondary display off stops activities running on that display.
     */
    public void testExternalDisplayToggleState() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        final DisplayState newDisplay = createExternalVirtualDisplay(
                false /* showContentWhenLocked */);

        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);

        // Check that the test activity is resumed on the external display
        waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mDisplayId,
                "Activity launched on external display must be resumed");

        mExternalDisplayHelper.turnDisplayOff();

        // Check that turning off the external display stops the activity
        waitAndAssertActivityStopped(TEST_ACTIVITY_NAME,
                "Activity launched on external display must be stopped after turning off");

        mExternalDisplayHelper.turnDisplayOn();

        // Check that turning on the external display resumes the activity
        waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mDisplayId,
                "Activity launched on external display must be resumed");
    }

    /**
     * Tests that tapping on the primary display after showing the keyguard resumes the
     * activity on the primary display.
     */
    public void testStackFocusSwitchOnTouchEventAfterKeyguard() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY_NAME);
        waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                "Activity launched on primary display must be resumed");

        sleepDevice();

        // Make sure there is no resumed activity when the primary display is off
        waitAndAssertActivityStopped(RESIZEABLE_ACTIVITY_NAME,
                "Activity launched on primary display must be stopped after turning off");
        assertEquals("Unexpected resumed activity",
                0, mAmWmState.getAmState().getResumedActivitiesCount());

        final DisplayState newDisplay = createExternalVirtualDisplay(
                true /* showContentWhenLocked */);

        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);

        // Check that the test activity is resumed on the external display
        waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mDisplayId,
                "Activity launched on external display must be resumed");

        // Unlock the device and tap on the middle of the primary display
        wakeUpDevice();
        executeShellCommand("wm dismiss-keyguard");
        final ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
        final int width = displayMetrics.getWidth();
        final int height = displayMetrics.getHeight();
        executeShellCommand("input tap " + (width / 2) + " " + (height / 2));

        // Check that the activity on the primary display is resumed
        waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                "Activity launched on primary display must be resumed");
        assertEquals("Unexpected resumed activity",
                1, mAmWmState.getAmState().getResumedActivitiesCount());
    }

    private void waitAndAssertActivityResumed(String activityName, int displayId, String message)
            throws Exception {
        mAmWmState.waitForActivityState(mDevice, activityName, STATE_RESUMED);

        final String fullActivityName = getActivityComponentName(activityName);
        assertEquals(message, fullActivityName, mAmWmState.getAmState().getResumedActivity());
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(displayId);
        ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals(message, fullActivityName, firstFrontStack.mResumedActivity);
        assertTrue(message,
                mAmWmState.getAmState().hasActivityState(activityName, STATE_RESUMED));
        mAmWmState.assertFocusedStack("Focus must be on external display", frontStackId);
        mAmWmState.assertVisibility(activityName, true /* visible */);
    }

    private void waitAndAssertActivityStopped(String activityName, String message)
            throws Exception {
        waitAndAssertActivityState(activityName, message, STATE_STOPPED);
    }

    private void waitAndAssertActivityPausedOrStopped(String activityName, String message)
            throws Exception {
        waitAndAssertActivityState(activityName, message, STATE_PAUSED, STATE_STOPPED);
    }

    private void waitAndAssertActivityState(String activityName, String message, String... states)
            throws Exception {
        mAmWmState.waitForActivityState(mDevice, activityName, states);

        boolean stateFound = false;

        for (String state : states) {
            if (mAmWmState.getAmState().hasActivityState(activityName, state)) {
                stateFound = true;
                break;
            }
        }

        assertTrue(message, stateFound);
    }

    /**
     * Tests that showWhenLocked works on a secondary display.
     */
    public void testSecondaryDisplayShowWhenLocked() throws Exception {
        if (!supportsMultiDisplay()) { return; }

        try {
            setLockCredential();

            launchActivity(TEST_ACTIVITY_NAME);

            final DisplayState newDisplay = createExternalVirtualDisplay(
                    false /* showContentWhenLocked */);
            launchActivityOnDisplay(SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME, newDisplay.mDisplayId);

            gotoKeyguard();
            mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);

            mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_STOPPED);
            mAmWmState.waitForActivityState(
                    mDevice, SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME, STATE_RESUMED);

            mAmWmState.computeState(mDevice, new String[] { SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME });
            assertTrue("Expected resumed activity on secondary display", mAmWmState.getAmState()
                    .hasActivityState(SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME, STATE_RESUMED));
        } finally {
            tearDownLockCredentials();
        }
    }

    /** Get physical and override display metrics from WM. */
    private ReportedDisplayMetrics getDisplayMetrics() throws Exception {
        mDumpLines.clear();
        final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(WM_SIZE, outputReceiver);
        mDevice.executeShellCommand(WM_DENSITY, outputReceiver);
        final String dump = outputReceiver.getOutput();
        mDumpLines.clear();
        Collections.addAll(mDumpLines, dump.split("\\n"));
        return ReportedDisplayMetrics.create(mDumpLines);
    }

    private static class ReportedDisplayMetrics {
        private static final Pattern sPhysicalSizePattern =
                Pattern.compile("Physical size: (\\d+)x(\\d+)");
        private static final Pattern sOverrideSizePattern =
                Pattern.compile("Override size: (\\d+)x(\\d+)");
        private static final Pattern sPhysicalDensityPattern =
                Pattern.compile("Physical density: (\\d+)");
        private static final Pattern sOverrideDensityPattern =
                Pattern.compile("Override density: (\\d+)");

        int physicalWidth;
        int physicalHeight;
        int physicalDensity;

        boolean sizeOverrideSet;
        int overrideWidth;
        int overrideHeight;
        boolean densityOverrideSet;
        int overrideDensity;

        /** Get width that WM operates with. */
        int getWidth() {
            return sizeOverrideSet ? overrideWidth : physicalWidth;
        }

        /** Get height that WM operates with. */
        int getHeight() {
            return sizeOverrideSet ? overrideHeight : physicalHeight;
        }

        /** Get density that WM operates with. */
        int getDensity() {
            return densityOverrideSet ? overrideDensity : physicalDensity;
        }

        static ReportedDisplayMetrics create(LinkedList<String> dump) {
            final ReportedDisplayMetrics result = new ReportedDisplayMetrics();

            boolean physicalSizeFound = false;
            boolean physicalDensityFound = false;

            while (!dump.isEmpty()) {
                final String line = dump.pop().trim();

                Matcher matcher = sPhysicalSizePattern.matcher(line);
                if (matcher.matches()) {
                    physicalSizeFound = true;
                    log(line);
                    result.physicalWidth = Integer.parseInt(matcher.group(1));
                    result.physicalHeight = Integer.parseInt(matcher.group(2));
                    continue;
                }

                matcher = sOverrideSizePattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    result.overrideWidth = Integer.parseInt(matcher.group(1));
                    result.overrideHeight = Integer.parseInt(matcher.group(2));
                    result.sizeOverrideSet = true;
                    continue;
                }

                matcher = sPhysicalDensityPattern.matcher(line);
                if (matcher.matches()) {
                    physicalDensityFound = true;
                    log(line);
                    result.physicalDensity = Integer.parseInt(matcher.group(1));
                    continue;
                }

                matcher = sOverrideDensityPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    result.overrideDensity = Integer.parseInt(matcher.group(1));
                    result.densityOverrideSet = true;
                    continue;
                }
            }

            assertTrue("Physical display size must be reported", physicalSizeFound);
            assertTrue("Physical display density must be reported", physicalDensityFound);

            return result;
        }
    }

    /** Assert that component received onMovedToDisplay and onConfigurationChanged callbacks. */
    private void assertMovedToDisplay(String componentName, String logSeparator) throws Exception {
        final ActivityLifecycleCounts lifecycleCounts
                = new ActivityLifecycleCounts(componentName, logSeparator);
        if (lifecycleCounts.mDestroyCount != 0) {
            fail(componentName + " has been destroyed " + lifecycleCounts.mDestroyCount
                    + " time(s), wasn't expecting any");
        } else if (lifecycleCounts.mCreateCount != 0) {
            fail(componentName + " has been (re)created " + lifecycleCounts.mCreateCount
                    + " time(s), wasn't expecting any");
        } else if (lifecycleCounts.mConfigurationChangedCount != 1) {
            fail(componentName + " has received "
                    + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, expecting " + 1);
        } else if (lifecycleCounts.mMovedToDisplayCount != 1) {
            fail(componentName + " has received "
                    + lifecycleCounts.mMovedToDisplayCount
                    + " onMovedToDisplay() calls, expecting " + 1);
        }
    }

    private static String getResizeVirtualDisplayCommand() {
        return getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY) + " -f 0x20000000" +
                " --es command resize_display";
    }

    /**
     * Creates a private virtual display with the external and show with insecure
     * keyguard flags set.
     */
    private DisplayState createExternalVirtualDisplay(boolean showContentWhenLocked)
            throws Exception {
        final ReportedDisplays originalDS = getDisplaysStates();
        final int originalDisplayCount = originalDS.getNumberOfDisplays();

        mExternalDisplayHelper = new DisplayHelper(getDevice());
        mExternalDisplayHelper.createAndWaitForDisplay(true /* external */, showContentWhenLocked);

        // Wait for the virtual display to be created and get configurations.
        final ReportedDisplays ds =
                getDisplayStateAfterChange(originalDisplayCount + 1);
        assertEquals("New virtual display must be created",
                originalDisplayCount + 1, ds.getNumberOfDisplays());

        // Find the newly added display.
        final List<DisplayState> newDisplays = findNewDisplayStates(originalDS, ds);
        return newDisplays.get(0);
    }

    /** Turns the primary display on/off by pressing the power key */
    private void setPrimaryDisplayState(boolean wantOn) throws DeviceNotAvailableException {
        // Either KeyEvent.KEYCODE_WAKEUP or KeyEvent.KEYCODE_SLEEP
        int keycode = wantOn ? 224 : 223;
        getDevice().executeShellCommand("input keyevent " + keycode);
        DisplayHelper.waitForDefaultDisplayState(getDevice(), wantOn);
    }
}
