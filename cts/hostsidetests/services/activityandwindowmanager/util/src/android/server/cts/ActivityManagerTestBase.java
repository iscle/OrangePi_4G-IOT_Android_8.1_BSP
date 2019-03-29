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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceTestCase;

import java.awt.image.BufferedImage;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.String;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.server.cts.StateLogger.log;
import static android.server.cts.StateLogger.logE;

import android.server.cts.ActivityManagerState.ActivityStack;

import javax.imageio.ImageIO;

public abstract class ActivityManagerTestBase extends DeviceTestCase {
    private static final boolean PRETEND_DEVICE_SUPPORTS_PIP = false;
    private static final boolean PRETEND_DEVICE_SUPPORTS_FREEFORM = false;
    private static final String LOG_SEPARATOR = "LOG_SEPARATOR";

    // Constants copied from ActivityManager.StackId. If they are changed there, these must be
    // updated.
    /** Invalid stack ID. */
    public static final int INVALID_STACK_ID = -1;

    /** First static stack ID. */
    public static final int FIRST_STATIC_STACK_ID = 0;

    /** Home activity stack ID. */
    public static final int HOME_STACK_ID = FIRST_STATIC_STACK_ID;

    /** ID of stack where fullscreen activities are normally launched into. */
    public static final int FULLSCREEN_WORKSPACE_STACK_ID = 1;

    /** ID of stack where freeform/resized activities are normally launched into. */
    public static final int FREEFORM_WORKSPACE_STACK_ID = FULLSCREEN_WORKSPACE_STACK_ID + 1;

    /** ID of stack that occupies a dedicated region of the screen. */
    public static final int DOCKED_STACK_ID = FREEFORM_WORKSPACE_STACK_ID + 1;

    /** ID of stack that always on top (always visible) when it exist. */
    public static final int PINNED_STACK_ID = DOCKED_STACK_ID + 1;

    /** Recents activity stack ID. */
    public static final int RECENTS_STACK_ID = PINNED_STACK_ID + 1;

    /** Assistant activity stack ID.  This stack is fullscreen and non-resizeable. */
    public static final int ASSISTANT_STACK_ID = RECENTS_STACK_ID + 1;

    protected static final int[] ALL_STACK_IDS_BUT_HOME = {
            FULLSCREEN_WORKSPACE_STACK_ID, FREEFORM_WORKSPACE_STACK_ID, DOCKED_STACK_ID,
            PINNED_STACK_ID, ASSISTANT_STACK_ID
    };

    protected static final int[] ALL_STACK_IDS_BUT_HOME_AND_FULLSCREEN = {
            FREEFORM_WORKSPACE_STACK_ID, DOCKED_STACK_ID, PINNED_STACK_ID, ASSISTANT_STACK_ID
    };

    private static final String TASK_ID_PREFIX = "taskId";

    private static final String AM_STACK_LIST = "am stack list";

    private static final String AM_FORCE_STOP_TEST_PACKAGE = "am force-stop android.server.cts";
    private static final String AM_FORCE_STOP_SECOND_TEST_PACKAGE
            = "am force-stop android.server.cts.second";
    private static final String AM_FORCE_STOP_THIRD_TEST_PACKAGE
            = "am force-stop android.server.cts.third";

    private static final String AM_REMOVE_STACK = "am stack remove ";

    protected static final String AM_START_HOME_ACTIVITY_COMMAND =
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME";

    protected static final String AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND =
            "am stack move-top-activity-to-pinned-stack 1 0 0 500 500";

    static final String LAUNCHING_ACTIVITY = "LaunchingActivity";
    static final String ALT_LAUNCHING_ACTIVITY = "AltLaunchingActivity";
    static final String BROADCAST_RECEIVER_ACTIVITY = "BroadcastReceiverActivity";

    /** Broadcast shell command for finishing {@link BroadcastReceiverActivity}. */
    static final String FINISH_ACTIVITY_BROADCAST
            = "am broadcast -a trigger_broadcast --ez finish true";

    /** Broadcast shell command for finishing {@link BroadcastReceiverActivity}. */
    static final String MOVE_TASK_TO_BACK_BROADCAST
            = "am broadcast -a trigger_broadcast --ez moveToBack true";

    private static final String AM_RESIZE_DOCKED_STACK = "am stack resize-docked-stack ";
    private static final String AM_RESIZE_STACK = "am stack resize ";

    static final String AM_MOVE_TASK = "am stack move-task ";

    private static final String AM_SUPPORTS_SPLIT_SCREEN_MULTIWINDOW =
            "am supports-split-screen-multi-window";
    private static final String AM_NO_HOME_SCREEN = "am no-home-screen";

    private static final String INPUT_KEYEVENT_HOME = "input keyevent 3";
    private static final String INPUT_KEYEVENT_BACK = "input keyevent 4";
    private static final String INPUT_KEYEVENT_APP_SWITCH = "input keyevent 187";
    public static final String INPUT_KEYEVENT_WINDOW = "input keyevent 171";

    private static final String LOCK_CREDENTIAL = "1234";

    private static final int INVALID_DISPLAY_ID = -1;

    private static final String DEFAULT_COMPONENT_NAME = "android.server.cts";

    static String componentName = DEFAULT_COMPONENT_NAME;

    protected static final int INVALID_DEVICE_ROTATION = -1;

    /** A reference to the device under test. */
    protected ITestDevice mDevice;

    private HashSet<String> mAvailableFeatures;

    private boolean mLockCredentialsSet;

    private boolean mLockDisabled;

    protected static String getAmStartCmd(final String activityName) {
        return "am start -n " + getActivityComponentName(activityName);
    }

    /**
     * @return the am command to start the given activity with the following extra key/value pairs.
     *         {@param keyValuePairs} must be a list of arguments defining each key/value extra.
     */
    protected static String getAmStartCmd(final String activityName,
            final String... keyValuePairs) {
        String base = getAmStartCmd(activityName);
        if (keyValuePairs.length % 2 != 0) {
            throw new RuntimeException("keyValuePairs must be pairs of key/value arguments");
        }
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            base += " --es " + keyValuePairs[i] + " " + keyValuePairs[i + 1];
        }
        return base;
    }

    protected static String getAmStartCmd(final String activityName, final int displayId) {
        return "am start -n " + getActivityComponentName(activityName) + " -f 0x18000000"
                + " --display " + displayId;
    }

    protected static String getAmStartCmdInNewTask(final String activityName) {
        return "am start -n " + getActivityComponentName(activityName) + " -f 0x18000000";
    }

    protected static String getAmStartCmdOverHome(final String activityName) {
        return "am start --activity-task-on-home -n " + getActivityComponentName(activityName);
    }

    protected static String getOrientationBroadcast(int orientation) {
        return "am broadcast -a trigger_broadcast --ei orientation " + orientation;
    }

    static String getActivityComponentName(final String activityName) {
        return getActivityComponentName(componentName, activityName);
    }

    private static boolean isFullyQualifiedActivityName(String name) {
        return name != null && name.contains(".");
    }

    static String getActivityComponentName(final String packageName, final String activityName) {
        return packageName + "/" + (isFullyQualifiedActivityName(activityName) ? "" : ".") +
                activityName;
    }

    // A little ugly, but lets avoid having to strip static everywhere for
    // now.
    public static void setComponentName(String name) {
        componentName = name;
    }

    protected static void setDefaultComponentName() {
        setComponentName(DEFAULT_COMPONENT_NAME);
    }

    static String getBaseWindowName() {
        return getBaseWindowName(componentName);
    }

    static String getBaseWindowName(final String packageName) {
        return getBaseWindowName(packageName, true /*prependPackageName*/);
    }

    static String getBaseWindowName(final String packageName, boolean prependPackageName) {
        return packageName + "/" + (prependPackageName ? packageName + "." : "");
    }

    static String getWindowName(final String activityName) {
        return getWindowName(componentName, activityName);
    }

    static String getWindowName(final String packageName, final String activityName) {
        return getBaseWindowName(packageName, !isFullyQualifiedActivityName(activityName))
                + activityName;
    }

    protected ActivityAndWindowManagersState mAmWmState = new ActivityAndWindowManagersState();

    private int mInitialAccelerometerRotation;
    private int mUserRotation;
    private float mFontScale;

    private SurfaceTraceReceiver mSurfaceTraceReceiver;
    private Thread mSurfaceTraceThread;

    void installSurfaceObserver(SurfaceTraceReceiver.SurfaceObserver observer) {
        mSurfaceTraceReceiver = new SurfaceTraceReceiver(observer);
        mSurfaceTraceThread = new Thread() {
            @Override
            public void run() {
                try {
                    mDevice.executeShellCommand("wm surface-trace", mSurfaceTraceReceiver);
                } catch (DeviceNotAvailableException e) {
                    logE("Device not available: " + e.toString());
                }
            }
        };
        mSurfaceTraceThread.start();
    }

    void removeSurfaceObserver() {
        mSurfaceTraceReceiver.cancel();
        mSurfaceTraceThread.interrupt();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setDefaultComponentName();

        // Get the device, this gives a handle to run commands and install APKs.
        mDevice = getDevice();
        wakeUpAndUnlockDevice();
        pressHomeButton();
        // Remove special stacks.
        removeStacks(ALL_STACK_IDS_BUT_HOME_AND_FULLSCREEN);
        // Store rotation settings.
        mInitialAccelerometerRotation = getAccelerometerRotation();
        mUserRotation = getUserRotation();
        mFontScale = getFontScale();
        mLockCredentialsSet = false;
        mLockDisabled = isLockDisabled();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            setLockDisabled(mLockDisabled);
            executeShellCommand(AM_FORCE_STOP_TEST_PACKAGE);
            executeShellCommand(AM_FORCE_STOP_SECOND_TEST_PACKAGE);
            executeShellCommand(AM_FORCE_STOP_THIRD_TEST_PACKAGE);
            // Restore rotation settings to the state they were before test.
            setAccelerometerRotation(mInitialAccelerometerRotation);
            setUserRotation(mUserRotation);
            setFontScale(mFontScale);
            setWindowTransitionAnimationDurationScale(1);
            // Remove special stacks.
            removeStacks(ALL_STACK_IDS_BUT_HOME_AND_FULLSCREEN);
            wakeUpAndUnlockDevice();
            pressHomeButton();
        } catch (DeviceNotAvailableException e) {
        }
    }

    protected void removeStacks(int... stackIds) {
        try {
            for (Integer stackId : stackIds) {
                executeShellCommand(AM_REMOVE_STACK + stackId);
            }
        } catch (DeviceNotAvailableException e) {
        }
    }

    protected String executeShellCommand(String command) throws DeviceNotAvailableException {
        return executeShellCommand(mDevice, command);
    }

    protected static String executeShellCommand(ITestDevice device, String command)
            throws DeviceNotAvailableException {
        log("adb shell " + command);
        return device.executeShellCommand(command);
    }

    protected void executeShellCommand(String command, CollectingOutputReceiver outputReceiver)
            throws DeviceNotAvailableException {
        log("adb shell " + command);
        mDevice.executeShellCommand(command, outputReceiver);
    }

    protected BufferedImage takeScreenshot() throws Exception {
        final InputStreamSource stream = mDevice.getScreenshot("PNG", false /* rescale */);
        if (stream == null) {
            fail("Failed to take screenshot of device");
        }
        return ImageIO.read(stream.createInputStream());
    }

    protected void launchActivityInComponent(final String componentName,
            final String targetActivityName, final String... keyValuePairs) throws Exception {
        final String originalComponentName = ActivityManagerTestBase.componentName;
        setComponentName(componentName);
        launchActivity(targetActivityName, keyValuePairs);
        setComponentName(originalComponentName);
    }

    protected void launchActivity(final String targetActivityName, final String... keyValuePairs)
            throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, keyValuePairs));
        mAmWmState.waitForValidState(mDevice, targetActivityName);
    }

    protected void launchActivityNoWait(final String targetActivityName,
            final String... keyValuePairs) throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, keyValuePairs));
    }

    protected void launchActivityInNewTask(final String targetActivityName) throws Exception {
        executeShellCommand(getAmStartCmdInNewTask(targetActivityName));
        mAmWmState.waitForValidState(mDevice, targetActivityName);
    }

    /**
     * Starts an activity in a new stack.
     * @return the stack id of the newly created stack.
     */
    protected int launchActivityInNewDynamicStack(final String activityName) throws Exception {
        HashSet<Integer> stackIds = getStackIds();
        executeShellCommand("am stack start " + ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID
                + " " + getActivityComponentName(activityName));
        HashSet<Integer> newStackIds = getStackIds();
        newStackIds.removeAll(stackIds);
        if (newStackIds.isEmpty()) {
            return INVALID_STACK_ID;
        } else {
            assertTrue(newStackIds.size() == 1);
            return newStackIds.iterator().next();
        }
    }

    /**
     * Returns the set of stack ids.
     */
    private HashSet<Integer> getStackIds() throws Exception {
        mAmWmState.computeState(mDevice, null);
        final List<ActivityStack> stacks = mAmWmState.getAmState().getStacks();
        final HashSet<Integer> stackIds = new HashSet<>();
        for (ActivityStack s : stacks) {
            stackIds.add(s.mStackId);
        }
        return stackIds;
    }

    protected void launchHomeActivity()
            throws Exception {
        executeShellCommand(AM_START_HOME_ACTIVITY_COMMAND);
        mAmWmState.waitForHomeActivityVisible(mDevice);
    }

    protected void launchActivityOnDisplay(String targetActivityName, int displayId)
            throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, displayId));

        mAmWmState.waitForValidState(mDevice, targetActivityName);
    }

    /**
     * Launch specific target activity. It uses existing instance of {@link #LAUNCHING_ACTIVITY}, so
     * that one should be started first.
     * @param toSide Launch to side in split-screen.
     * @param randomData Make intent URI random by generating random data.
     * @param multipleTask Allow multiple task launch.
     * @param targetActivityName Target activity to be launched. Only class name should be provided,
     *                           package name of {@link #LAUNCHING_ACTIVITY} will be added
     *                           automatically.
     * @param displayId Display id where target activity should be launched.
     * @throws Exception
     */
    protected void launchActivityFromLaunching(boolean toSide, boolean randomData,
            boolean multipleTask, String targetActivityName, int displayId) throws Exception {
        StringBuilder commandBuilder = new StringBuilder(getAmStartCmd(LAUNCHING_ACTIVITY));
        commandBuilder.append(" -f 0x20000000");
        if (toSide) {
            commandBuilder.append(" --ez launch_to_the_side true");
        }
        if (randomData) {
            commandBuilder.append(" --ez random_data true");
        }
        if (multipleTask) {
            commandBuilder.append(" --ez multiple_task true");
        }
        if (targetActivityName != null) {
            commandBuilder.append(" --es target_activity ").append(targetActivityName);
        }
        if (displayId != INVALID_DISPLAY_ID) {
            commandBuilder.append(" --ei display_id ").append(displayId);
        }
        executeShellCommand(commandBuilder.toString());

        mAmWmState.waitForValidState(mDevice, targetActivityName);
    }

    protected void launchActivityInStack(String activityName, int stackId,
            final String... keyValuePairs) throws Exception {
        executeShellCommand(getAmStartCmd(activityName, keyValuePairs) + " --stack " + stackId);

        mAmWmState.waitForValidState(mDevice, activityName, stackId);
    }

    protected void launchActivityInDockStack(String activityName) throws Exception {
        launchActivity(activityName);
        // TODO(b/36279415): The way we launch an activity into the docked stack is different from
        // what the user actually does. Long term we should use
        // "adb shell input keyevent --longpress _app_swich_key_code_" to trigger a long press on
        // the recents button which is consistent with what the user does. However, currently sys-ui
        // does handle FLAG_LONG_PRESS for the app switch key. It just listens for long press on the
        // view. We need to fix that in sys-ui before we can change this.
        moveActivityToDockStack(activityName);

        mAmWmState.waitForValidState(mDevice, activityName, DOCKED_STACK_ID);
    }

    protected void launchActivityToSide(boolean randomData, boolean multipleTaskFlag,
            String targetActivity) throws Exception {
        final String activityToLaunch = targetActivity != null ? targetActivity : "TestActivity";
        getLaunchActivityBuilder().setToSide(true).setRandomData(randomData)
                .setMultipleTask(multipleTaskFlag).setTargetActivityName(activityToLaunch)
                .execute();

        mAmWmState.waitForValidState(mDevice, activityToLaunch, FULLSCREEN_WORKSPACE_STACK_ID);
    }

    protected void moveActivityToDockStack(String activityName) throws Exception {
        moveActivityToStack(activityName, DOCKED_STACK_ID);
    }

    protected void moveActivityToStack(String activityName, int stackId) throws Exception {
        final int taskId = getActivityTaskId(activityName);
        final String cmd = AM_MOVE_TASK + taskId + " " + stackId + " true";
        executeShellCommand(cmd);

        mAmWmState.waitForValidState(mDevice, activityName, stackId);
    }

    protected void resizeActivityTask(String activityName, int left, int top, int right, int bottom)
            throws Exception {
        final int taskId = getActivityTaskId(activityName);
        final String cmd = "am task resize "
                + taskId + " " + left + " " + top + " " + right + " " + bottom;
        executeShellCommand(cmd);
    }

    protected void resizeDockedStack(
            int stackWidth, int stackHeight, int taskWidth, int taskHeight)
                    throws DeviceNotAvailableException {
        executeShellCommand(AM_RESIZE_DOCKED_STACK
                + "0 0 " + stackWidth + " " + stackHeight
                + " 0 0 " + taskWidth + " " + taskHeight);
    }

    protected void resizeStack(int stackId, int stackLeft, int stackTop, int stackWidth,
            int stackHeight) throws DeviceNotAvailableException {
        executeShellCommand(AM_RESIZE_STACK + String.format("%d %d %d %d %d", stackId, stackLeft,
                stackTop, stackWidth, stackHeight));
    }

    protected void pressHomeButton() throws DeviceNotAvailableException {
        executeShellCommand(INPUT_KEYEVENT_HOME);
    }

    protected void pressBackButton() throws DeviceNotAvailableException {
        executeShellCommand(INPUT_KEYEVENT_BACK);
    }

    protected void pressAppSwitchButton() throws DeviceNotAvailableException {
        executeShellCommand(INPUT_KEYEVENT_APP_SWITCH);
    }

    // Utility method for debugging, not used directly here, but useful, so kept around.
    protected void printStacksAndTasks() throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        executeShellCommand(AM_STACK_LIST, outputReceiver);
        String output = outputReceiver.getOutput();
        for (String line : output.split("\\n")) {
            CLog.logAndDisplay(LogLevel.INFO, line);
        }
    }

    protected int getActivityTaskId(String name) throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        executeShellCommand(AM_STACK_LIST, outputReceiver);
        final String output = outputReceiver.getOutput();
        final Pattern activityPattern = Pattern.compile("(.*) " + getWindowName(name) + " (.*)");
        for (String line : output.split("\\n")) {
            Matcher matcher = activityPattern.matcher(line);
            if (matcher.matches()) {
                for (String word : line.split("\\s+")) {
                    if (word.startsWith(TASK_ID_PREFIX)) {
                        final String withColon = word.split("=")[1];
                        return Integer.parseInt(withColon.substring(0, withColon.length() - 1));
                    }
                }
            }
        }
        return -1;
    }

    protected boolean supportsVrMode() throws DeviceNotAvailableException {
        return hasDeviceFeature("android.software.vr.mode") &&
                hasDeviceFeature("android.hardware.vr.high_performance");
    }

    protected boolean supportsPip() throws DeviceNotAvailableException {
        return hasDeviceFeature("android.software.picture_in_picture")
                || PRETEND_DEVICE_SUPPORTS_PIP;
    }

    protected boolean supportsFreeform() throws DeviceNotAvailableException {
        return hasDeviceFeature("android.software.freeform_window_management")
                || PRETEND_DEVICE_SUPPORTS_FREEFORM;
    }

    protected boolean isHandheld() throws DeviceNotAvailableException {
        return !hasDeviceFeature("android.software.leanback")
                && !hasDeviceFeature("android.hardware.type.watch")
                && !hasDeviceFeature("android.hardware.type.embedded");
    }

    protected boolean supportsSplitScreenMultiWindow() throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        executeShellCommand(AM_SUPPORTS_SPLIT_SCREEN_MULTIWINDOW, outputReceiver);
        String output = outputReceiver.getOutput();
        return !output.startsWith("false");
    }

    protected boolean noHomeScreen() throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        executeShellCommand(AM_NO_HOME_SCREEN, outputReceiver);
        String output = outputReceiver.getOutput();
        return output.startsWith("true");
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    protected boolean supportsRotation() throws DeviceNotAvailableException {
        return (hasDeviceFeature("android.hardware.screen.landscape")
                    && hasDeviceFeature("android.hardware.screen.portrait"))
            || (!hasDeviceFeature("android.hardware.screen.landscape")
                    && !hasDeviceFeature("android.hardware.screen.portrait"));
    }

    protected boolean hasDeviceFeature(String requiredFeature) throws DeviceNotAvailableException {
        if (mAvailableFeatures == null) {
            // TODO: Move this logic to ITestDevice.
            final String output = runCommandAndPrintOutput("pm list features");

            // Extract the id of the new user.
            mAvailableFeatures = new HashSet<>();
            for (String feature: output.split("\\s+")) {
                // Each line in the output of the command has the format "feature:{FEATURE_VALUE}".
                String[] tokens = feature.split(":");
                assertTrue("\"" + feature + "\" expected to have format feature:{FEATURE_VALUE}",
                        tokens.length > 1);
                assertEquals(feature, "feature", tokens[0]);
                mAvailableFeatures.add(tokens[1]);
            }
        }
        boolean result = mAvailableFeatures.contains(requiredFeature);
        if (!result) {
            CLog.logAndDisplay(LogLevel.INFO, "Device doesn't support " + requiredFeature);
        }
        return result;
    }

    protected boolean isDisplayOn() throws DeviceNotAvailableException {
        final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand("dumpsys power", outputReceiver);

        for (String line : outputReceiver.getOutput().split("\\n")) {
            line = line.trim();

            final Matcher matcher = sDisplayStatePattern.matcher(line);
            if (matcher.matches()) {
                final String state = matcher.group(1);
                log("power state=" + state);
                return "ON".equals(state);
            }
        }
        log("power state :(");
        return false;
    }

    protected void sleepDevice() throws DeviceNotAvailableException {
        int retriesLeft = 5;
        runCommandAndPrintOutput("input keyevent 26");
        do {
            if (isDisplayOn()) {
                log("***Waiting for display to turn off...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);
    }

    protected void wakeUpAndUnlockDevice() throws DeviceNotAvailableException {
        wakeUpDevice();
        unlockDevice();
    }

    protected void wakeUpAndRemoveLock() throws DeviceNotAvailableException {
        wakeUpDevice();
        setLockDisabled(true);
    }

    protected void wakeUpDevice() throws DeviceNotAvailableException {
        runCommandAndPrintOutput("input keyevent 224");
    }

    protected void unlockDevice() throws DeviceNotAvailableException {
        runCommandAndPrintOutput("input keyevent 82");
    }

    protected void unlockDeviceWithCredential() throws Exception {
        runCommandAndPrintOutput("input keyevent 82");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            //ignored
        }
        enterAndConfirmLockCredential();
    }

    protected void enterAndConfirmLockCredential() throws Exception {
        // TODO: This should use waitForIdle..but there ain't such a thing on hostside tests, boo :(
        Thread.sleep(500);

        runCommandAndPrintOutput("input text " + LOCK_CREDENTIAL);
        runCommandAndPrintOutput("input keyevent KEYCODE_ENTER");
    }

    protected void gotoKeyguard() throws DeviceNotAvailableException {
        sleepDevice();
        wakeUpDevice();
    }

    protected void setLockCredential() throws DeviceNotAvailableException {
        mLockCredentialsSet = true;
        runCommandAndPrintOutput("locksettings set-pin " + LOCK_CREDENTIAL);
    }

    private void removeLockCredential() throws DeviceNotAvailableException {
        runCommandAndPrintOutput("locksettings clear --old " + LOCK_CREDENTIAL);
    }

    /**
     * Returns whether the lock screen is disabled.
     * @return true if the lock screen is disabled, false otherwise.
     */
    private boolean isLockDisabled() throws DeviceNotAvailableException {
        final String isLockDisabled = runCommandAndPrintOutput("locksettings get-disabled").trim();
        if ("null".equals(isLockDisabled)) {
            return false;
        }
        return Boolean.parseBoolean(isLockDisabled);

    }

    /**
     * Disable the lock screen.
     * @param lockDisabled true if should disable, false otherwise.
     */
    void setLockDisabled(boolean lockDisabled) throws DeviceNotAvailableException {
        runCommandAndPrintOutput("locksettings set-disabled " + lockDisabled);
    }

    /**
     * Sets the device rotation, value corresponds to one of {@link Surface.ROTATION_0},
     * {@link Surface.ROTATION_90}, {@link Surface.ROTATION_180}, {@link Surface.ROTATION_270}.
     */
    protected void setDeviceRotation(int rotation) throws Exception {
        setAccelerometerRotation(0);
        setUserRotation(rotation);
        mAmWmState.waitForRotation(mDevice, rotation);
    }

    protected int getDeviceRotation(int displayId) throws DeviceNotAvailableException {
        final String displays = runCommandAndPrintOutput("dumpsys display displays").trim();
        Pattern pattern = Pattern.compile(
                "(mDisplayId=" + displayId + ")([\\s\\S]*)(mOverrideDisplayInfo)(.*)"
                        + "(rotation)(\\s+)(\\d+)");
        Matcher matcher = pattern.matcher(displays);
        while (matcher.find()) {
            final String match = matcher.group(7);
            return Integer.parseInt(match);
        }

        return INVALID_DEVICE_ROTATION;
    }

    private int getAccelerometerRotation() throws DeviceNotAvailableException {
        final String rotation =
                runCommandAndPrintOutput("settings get system accelerometer_rotation");
        return Integer.parseInt(rotation.trim());
    }

    private void setAccelerometerRotation(int rotation) throws DeviceNotAvailableException {
        runCommandAndPrintOutput(
                "settings put system accelerometer_rotation " + rotation);
    }

    protected int getUserRotation() throws DeviceNotAvailableException {
        final String rotation =
                runCommandAndPrintOutput("settings get system user_rotation").trim();
        if ("null".equals(rotation)) {
            return -1;
        }
        return Integer.parseInt(rotation);
    }

    private void setUserRotation(int rotation) throws DeviceNotAvailableException {
        if (rotation == -1) {
            runCommandAndPrintOutput(
                    "settings delete system user_rotation");
        } else {
            runCommandAndPrintOutput(
                    "settings put system user_rotation " + rotation);
        }
    }

    protected void setFontScale(float fontScale) throws DeviceNotAvailableException {
        if (fontScale == 0.0f) {
            runCommandAndPrintOutput(
                    "settings delete system font_scale");
        } else {
            runCommandAndPrintOutput(
                    "settings put system font_scale " + fontScale);
        }
    }

    protected void setWindowTransitionAnimationDurationScale(float animDurationScale)
            throws DeviceNotAvailableException {
        runCommandAndPrintOutput(
                "settings put global transition_animation_scale " + animDurationScale);
    }

    protected float getFontScale() throws DeviceNotAvailableException {
        try {
            final String fontScale =
                    runCommandAndPrintOutput("settings get system font_scale").trim();
            return Float.parseFloat(fontScale);
        } catch (NumberFormatException e) {
            // If we don't have a valid font scale key, return 0.0f now so
            // that we delete the key in tearDown().
            return 0.0f;
        }
    }

    protected String runCommandAndPrintOutput(String command) throws DeviceNotAvailableException {
        final String output = executeShellCommand(command);
        log(output);
        return output;
    }

    /**
     * Tries to clear logcat and inserts log separator in case clearing didn't succeed, so we can
     * always find the starting point from where to evaluate following logs.
     * @return Unique log separator.
     */
    protected String clearLogcat() throws DeviceNotAvailableException {
        mDevice.executeAdbCommand("logcat", "-c");
        final String uniqueString = UUID.randomUUID().toString();
        executeShellCommand("log -t " + LOG_SEPARATOR + " " + uniqueString);
        return uniqueString;
    }

    void assertActivityLifecycle(String activityName, boolean relaunched,
            String logSeparator) throws DeviceNotAvailableException {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = verifyLifecycleCondition(activityName, logSeparator, relaunched);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    /** @return Error string if lifecycle counts don't match, null if everything is fine. */
    private String verifyLifecycleCondition(String activityName, String logSeparator,
            boolean relaunched) throws DeviceNotAvailableException {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);
        if (relaunched) {
            if (lifecycleCounts.mDestroyCount < 1) {
                return activityName + " must have been destroyed. mDestroyCount="
                        + lifecycleCounts.mDestroyCount;
            }
            if (lifecycleCounts.mCreateCount < 1) {
                return activityName + " must have been (re)created. mCreateCount="
                        + lifecycleCounts.mCreateCount;
            }
        } else {
            if (lifecycleCounts.mDestroyCount > 0) {
                return activityName + " must *NOT* have been destroyed. mDestroyCount="
                        + lifecycleCounts.mDestroyCount;
            }
            if (lifecycleCounts.mCreateCount > 0) {
                return activityName + " must *NOT* have been (re)created. mCreateCount="
                        + lifecycleCounts.mCreateCount;
            }
            if (lifecycleCounts.mConfigurationChangedCount < 1) {
                return activityName + " must have received configuration changed. "
                        + "mConfigurationChangedCount="
                        + lifecycleCounts.mConfigurationChangedCount;
            }
        }
        return null;
    }

    protected void assertRelaunchOrConfigChanged(
            String activityName, int numRelaunch, int numConfigChange, String logSeparator)
            throws DeviceNotAvailableException {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = verifyRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange,
                    logSeparator);
            if (resultString != null) {
                log("***Waiting for relaunch or config changed: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    /** @return Error string if lifecycle counts don't match, null if everything is fine. */
    private String verifyRelaunchOrConfigChanged(String activityName, int numRelaunch,
            int numConfigChange, String logSeparator) throws DeviceNotAvailableException {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);

        if (lifecycleCounts.mDestroyCount != numRelaunch) {
            return activityName + " has been destroyed " + lifecycleCounts.mDestroyCount
                    + " time(s), expecting " + numRelaunch;
        } else if (lifecycleCounts.mCreateCount != numRelaunch) {
            return activityName + " has been (re)created " + lifecycleCounts.mCreateCount
                    + " time(s), expecting " + numRelaunch;
        } else if (lifecycleCounts.mConfigurationChangedCount != numConfigChange) {
            return activityName + " has received " + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, expecting " + numConfigChange;
        }
        return null;
    }

    protected void assertActivityDestroyed(String activityName, String logSeparator)
            throws DeviceNotAvailableException {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = verifyActivityDestroyed(activityName, logSeparator);
            if (resultString != null) {
                log("***Waiting for activity destroyed: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    /** @return Error string if lifecycle counts don't match, null if everything is fine. */
    private String verifyActivityDestroyed(String activityName, String logSeparator)
            throws DeviceNotAvailableException {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);

        if (lifecycleCounts.mDestroyCount != 1) {
            return activityName + " has been destroyed " + lifecycleCounts.mDestroyCount
                    + " time(s), expecting single destruction.";
        } else if (lifecycleCounts.mCreateCount != 0) {
            return activityName + " has been (re)created " + lifecycleCounts.mCreateCount
                    + " time(s), not expecting any.";
        } else if (lifecycleCounts.mConfigurationChangedCount != 0) {
            return activityName + " has received " + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, not expecting any.";
        }
        return null;
    }

    protected String[] getDeviceLogsForComponent(String componentName, String logSeparator)
            throws DeviceNotAvailableException {
        return getDeviceLogsForComponents(new String[]{componentName}, logSeparator);
    }

    protected String[] getDeviceLogsForComponents(final String[] componentNames,
            String logSeparator) throws DeviceNotAvailableException {
        String filters = LOG_SEPARATOR + ":I ";
        for (String component : componentNames) {
            filters += component + ":I ";
        }
        final String[] result = mDevice.executeAdbCommand(
                "logcat", "-v", "brief", "-d", filters, "*:S").split("\\n");
        if (logSeparator == null) {
            return result;
        }

        // Make sure that we only check logs after the separator.
        int i = 0;
        boolean lookingForSeparator = true;
        while (i < result.length && lookingForSeparator) {
            if (result[i].contains(logSeparator)) {
                lookingForSeparator = false;
            }
            i++;
        }
        final String[] filteredResult = new String[result.length - i];
        for (int curPos = 0; i < result.length; curPos++, i++) {
            filteredResult[curPos] = result[i];
        }
        return filteredResult;
    }

    void assertSingleLaunch(String activityName, String logSeparator) throws DeviceNotAvailableException {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = validateLifecycleCounts(activityName, logSeparator, 1 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 0 /* pauseCount */, 0 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    public void assertSingleLaunchAndStop(String activityName, String logSeparator) throws DeviceNotAvailableException {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = validateLifecycleCounts(activityName, logSeparator, 1 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 1 /* pauseCount */, 1 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    public void assertSingleStartAndStop(String activityName, String logSeparator) throws DeviceNotAvailableException {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString =  validateLifecycleCounts(activityName, logSeparator, 0 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 1 /* pauseCount */, 1 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    void assertSingleStart(String activityName, String logSeparator) throws DeviceNotAvailableException {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = validateLifecycleCounts(activityName, logSeparator, 0 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 0 /* pauseCount */, 0 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    private String validateLifecycleCounts(String activityName, String logSeparator,
            int createCount, int startCount, int resumeCount, int pauseCount, int stopCount,
            int destroyCount) throws DeviceNotAvailableException {

        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);

        if (lifecycleCounts.mCreateCount != createCount) {
            return activityName + " created " + lifecycleCounts.mCreateCount + " times.";
        }
        if (lifecycleCounts.mStartCount != startCount) {
            return activityName + " started " + lifecycleCounts.mStartCount + " times.";
        }
        if (lifecycleCounts.mResumeCount != resumeCount) {
            return activityName + " resumed " + lifecycleCounts.mResumeCount + " times.";
        }
        if (lifecycleCounts.mPauseCount != pauseCount) {
            return activityName + " paused " + lifecycleCounts.mPauseCount + " times.";
        }
        if (lifecycleCounts.mStopCount != stopCount) {
            return activityName + " stopped " + lifecycleCounts.mStopCount + " times.";
        }
        if (lifecycleCounts.mDestroyCount != destroyCount) {
            return activityName + " destroyed " + lifecycleCounts.mDestroyCount + " times.";
        }
        return null;
    }

    private static final Pattern sCreatePattern = Pattern.compile("(.+): onCreate");
    private static final Pattern sStartPattern = Pattern.compile("(.+): onStart");
    private static final Pattern sResumePattern = Pattern.compile("(.+): onResume");
    private static final Pattern sPausePattern = Pattern.compile("(.+): onPause");
    private static final Pattern sConfigurationChangedPattern =
            Pattern.compile("(.+): onConfigurationChanged");
    private static final Pattern sMovedToDisplayPattern =
            Pattern.compile("(.+): onMovedToDisplay");
    private static final Pattern sStopPattern = Pattern.compile("(.+): onStop");
    private static final Pattern sDestroyPattern = Pattern.compile("(.+): onDestroy");
    private static final Pattern sMultiWindowModeChangedPattern =
            Pattern.compile("(.+): onMultiWindowModeChanged");
    private static final Pattern sPictureInPictureModeChangedPattern =
            Pattern.compile("(.+): onPictureInPictureModeChanged");
    private static final Pattern sNewConfigPattern = Pattern.compile(
            "(.+): config size=\\((\\d+),(\\d+)\\) displaySize=\\((\\d+),(\\d+)\\)"
            + " metricsSize=\\((\\d+),(\\d+)\\) smallestScreenWidth=(\\d+) densityDpi=(\\d+)"
            + " orientation=(\\d+)");
    private static final Pattern sDisplayStatePattern =
            Pattern.compile("Display Power: state=(.+)");

    class ReportedSizes {
        int widthDp;
        int heightDp;
        int displayWidth;
        int displayHeight;
        int metricsWidth;
        int metricsHeight;
        int smallestWidthDp;
        int densityDpi;
        int orientation;

        @Override
        public String toString() {
            return "ReportedSizes: {widthDp=" + widthDp + " heightDp=" + heightDp
                    + " displayWidth=" + displayWidth + " displayHeight=" + displayHeight
                    + " metricsWidth=" + metricsWidth + " metricsHeight=" + metricsHeight
                    + " smallestWidthDp=" + smallestWidthDp + " densityDpi=" + densityDpi
                    + " orientation=" + orientation + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if ( this == obj ) return true;
            if ( !(obj instanceof ReportedSizes) ) return false;
            ReportedSizes that = (ReportedSizes) obj;
            return widthDp == that.widthDp
                    && heightDp == that.heightDp
                    && displayWidth == that.displayWidth
                    && displayHeight == that.displayHeight
                    && metricsWidth == that.metricsWidth
                    && metricsHeight == that.metricsHeight
                    && smallestWidthDp == that.smallestWidthDp
                    && densityDpi == that.densityDpi
                    && orientation == that.orientation;
        }
    }

    ReportedSizes getLastReportedSizesForActivity(String activityName, String logSeparator)
            throws DeviceNotAvailableException {
        int retriesLeft = 5;
        ReportedSizes result;
        do {
            result = readLastReportedSizes(activityName, logSeparator);
            if (result == null) {
                log("***Waiting for sizes to be reported...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);
        return result;
    }

    private ReportedSizes readLastReportedSizes(String activityName, String logSeparator)
            throws DeviceNotAvailableException {
        final String[] lines = getDeviceLogsForComponent(activityName, logSeparator);
        for (int i = lines.length - 1; i >= 0; i--) {
            final String line = lines[i].trim();
            final Matcher matcher = sNewConfigPattern.matcher(line);
            if (matcher.matches()) {
                ReportedSizes details = new ReportedSizes();
                details.widthDp = Integer.parseInt(matcher.group(2));
                details.heightDp = Integer.parseInt(matcher.group(3));
                details.displayWidth = Integer.parseInt(matcher.group(4));
                details.displayHeight = Integer.parseInt(matcher.group(5));
                details.metricsWidth = Integer.parseInt(matcher.group(6));
                details.metricsHeight = Integer.parseInt(matcher.group(7));
                details.smallestWidthDp = Integer.parseInt(matcher.group(8));
                details.densityDpi = Integer.parseInt(matcher.group(9));
                details.orientation = Integer.parseInt(matcher.group(10));
                return details;
            }
        }
        return null;
    }

    class ActivityLifecycleCounts {
        int mCreateCount;
        int mStartCount;
        int mResumeCount;
        int mConfigurationChangedCount;
        int mLastConfigurationChangedLineIndex;
        int mMovedToDisplayCount;
        int mMultiWindowModeChangedCount;
        int mLastMultiWindowModeChangedLineIndex;
        int mPictureInPictureModeChangedCount;
        int mLastPictureInPictureModeChangedLineIndex;
        int mPauseCount;
        int mStopCount;
        int mLastStopLineIndex;
        int mDestroyCount;

        public ActivityLifecycleCounts(String activityName, String logSeparator)
                throws DeviceNotAvailableException {
            int lineIndex = 0;
            for (String line : getDeviceLogsForComponent(activityName, logSeparator)) {
                line = line.trim();
                lineIndex++;

                Matcher matcher = sCreatePattern.matcher(line);
                if (matcher.matches()) {
                    mCreateCount++;
                    continue;
                }

                matcher = sStartPattern.matcher(line);
                if (matcher.matches()) {
                    mStartCount++;
                    continue;
                }

                matcher = sResumePattern.matcher(line);
                if (matcher.matches()) {
                    mResumeCount++;
                    continue;
                }

                matcher = sConfigurationChangedPattern.matcher(line);
                if (matcher.matches()) {
                    mConfigurationChangedCount++;
                    mLastConfigurationChangedLineIndex = lineIndex;
                    continue;
                }

                matcher = sMovedToDisplayPattern.matcher(line);
                if (matcher.matches()) {
                    mMovedToDisplayCount++;
                    continue;
                }

                matcher = sMultiWindowModeChangedPattern.matcher(line);
                if (matcher.matches()) {
                    mMultiWindowModeChangedCount++;
                    mLastMultiWindowModeChangedLineIndex = lineIndex;
                    continue;
                }

                matcher = sPictureInPictureModeChangedPattern.matcher(line);
                if (matcher.matches()) {
                    mPictureInPictureModeChangedCount++;
                    mLastPictureInPictureModeChangedLineIndex = lineIndex;
                    continue;
                }

                matcher = sPausePattern.matcher(line);
                if (matcher.matches()) {
                    mPauseCount++;
                    continue;
                }

                matcher = sStopPattern.matcher(line);
                if (matcher.matches()) {
                    mStopCount++;
                    mLastStopLineIndex = lineIndex;
                    continue;
                }

                matcher = sDestroyPattern.matcher(line);
                if (matcher.matches()) {
                    mDestroyCount++;
                    continue;
                }
            }
        }
    }

    protected void stopTestCase() throws Exception {
        executeShellCommand("am force-stop " + componentName);
    }

    protected LaunchActivityBuilder getLaunchActivityBuilder() {
        return new LaunchActivityBuilder(mAmWmState, mDevice);
    }

    protected static class LaunchActivityBuilder {
        private final ActivityAndWindowManagersState mAmWmState;
        private final ITestDevice mDevice;

        private String mTargetActivityName;
        private String mTargetPackage = componentName;
        private boolean mToSide;
        private boolean mRandomData;
        private boolean mNewTask;
        private boolean mMultipleTask;
        private int mDisplayId = INVALID_DISPLAY_ID;
        private String mLaunchingActivityName = LAUNCHING_ACTIVITY;
        private boolean mReorderToFront;
        private boolean mWaitForLaunched;

        public LaunchActivityBuilder(ActivityAndWindowManagersState amWmState,
                                     ITestDevice device) {
            mAmWmState = amWmState;
            mDevice = device;
            mWaitForLaunched = true;
        }

        public LaunchActivityBuilder setToSide(boolean toSide) {
            mToSide = toSide;
            return this;
        }

        public LaunchActivityBuilder setRandomData(boolean randomData) {
            mRandomData = randomData;
            return this;
        }

        public LaunchActivityBuilder setNewTask(boolean newTask) {
            mNewTask = newTask;
            return this;
        }

        public LaunchActivityBuilder setMultipleTask(boolean multipleTask) {
            mMultipleTask = multipleTask;
            return this;
        }

        public LaunchActivityBuilder setReorderToFront(boolean reorderToFront) {
            mReorderToFront = reorderToFront;
            return this;
        }

        public LaunchActivityBuilder setTargetActivityName(String name) {
            mTargetActivityName = name;
            return this;
        }

        public LaunchActivityBuilder setTargetPackage(String pkg) {
            mTargetPackage = pkg;
            return this;
        }

        public LaunchActivityBuilder setDisplayId(int id) {
            mDisplayId = id;
            return this;
        }

        public LaunchActivityBuilder setLaunchingActivityName(String name) {
            mLaunchingActivityName = name;
            return this;
        }

        public LaunchActivityBuilder setWaitForLaunched(boolean shouldWait) {
            mWaitForLaunched = shouldWait;
            return this;
        }

        public void execute() throws Exception {
            StringBuilder commandBuilder = new StringBuilder(getAmStartCmd(mLaunchingActivityName));
            commandBuilder.append(" -f 0x20000000");

            // Add a flag to ensure we actually mean to launch an activity.
            commandBuilder.append(" --ez launch_activity true");

            if (mToSide) {
                commandBuilder.append(" --ez launch_to_the_side true");
            }
            if (mRandomData) {
                commandBuilder.append(" --ez random_data true");
            }
            if (mNewTask) {
                commandBuilder.append(" --ez new_task true");
            }
            if (mMultipleTask) {
                commandBuilder.append(" --ez multiple_task true");
            }
            if (mReorderToFront) {
                commandBuilder.append(" --ez reorder_to_front true");
            }
            if (mTargetActivityName != null) {
                commandBuilder.append(" --es target_activity ").append(mTargetActivityName);
                commandBuilder.append(" --es package_name ").append(mTargetPackage);
            }
            if (mDisplayId != INVALID_DISPLAY_ID) {
                commandBuilder.append(" --ei display_id ").append(mDisplayId);
            }
            executeShellCommand(mDevice, commandBuilder.toString());

            if (mWaitForLaunched) {
                mAmWmState.waitForValidState(mDevice, new String[]{mTargetActivityName},
                        null /* stackIds */, false /* compareTaskAndStackBounds */, mTargetPackage);
            }
        }
    }

    void tearDownLockCredentials() throws Exception {
        if (!mLockCredentialsSet) {
            return;
        }

        removeLockCredential();
        // Dismiss active keyguard after credential is cleared, so
        // keyguard doesn't ask for the stale credential.
        pressBackButton();
        sleepDevice();
        wakeUpAndUnlockDevice();
    }
}
