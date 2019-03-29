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

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class CrossAppDragAndDropTests extends DeviceTestCase {
    // Constants copied from ActivityManager.StackId. If they are changed there, these must be
    // updated.
    /** ID of stack where fullscreen activities are normally launched into. */
    private static final int FULLSCREEN_WORKSPACE_STACK_ID = 1;

    /** ID of stack where freeform/resized activities are normally launched into. */
    private static final int FREEFORM_WORKSPACE_STACK_ID = FULLSCREEN_WORKSPACE_STACK_ID + 1;

    /** ID of stack that occupies a dedicated region of the screen. */
    private static final int DOCKED_STACK_ID = FREEFORM_WORKSPACE_STACK_ID + 1;

    /** ID of stack that always on top (always visible) when it exists. */
    private static final int PINNED_STACK_ID = DOCKED_STACK_ID + 1;

    private static final String AM_FORCE_STOP = "am force-stop ";
    private static final String AM_MOVE_TASK = "am stack move-task ";
    private static final String AM_RESIZE_TASK = "am task resize ";
    private static final String AM_REMOVE_STACK = "am stack remove ";
    private static final String AM_START_N = "am start -n ";
    private static final String AM_STACK_LIST = "am stack list";
    private static final String INPUT_MOUSE_SWIPE = "input mouse swipe ";
    private static final String TASK_ID_PREFIX = "taskId";

    // Regex pattern to match adb shell am stack list output of the form:
    // taskId=<TASK_ID>: <componentName> bounds=[LEFT,TOP][RIGHT,BOTTOM]
    private static final String TASK_REGEX_PATTERN_STRING =
            "taskId=[0-9]+: %s bounds=\\[[0-9]+,[0-9]+\\]\\[[0-9]+,[0-9]+\\]";

    private static final int SWIPE_DURATION_MS = 500;

    private static final String SOURCE_PACKAGE_NAME = "android.wm.cts.dndsourceapp";
    private static final String TARGET_PACKAGE_NAME = "android.wm.cts.dndtargetapp";
    private static final String TARGET_23_PACKAGE_NAME = "android.wm.cts.dndtargetappsdk23";


    private static final String SOURCE_ACTIVITY_NAME = "DragSource";
    private static final String TARGET_ACTIVITY_NAME = "DropTarget";

    private static final String FILE_GLOBAL = "file_global";
    private static final String FILE_LOCAL = "file_local";
    private static final String DISALLOW_GLOBAL = "disallow_global";
    private static final String CANCEL_SOON = "cancel_soon";
    private static final String GRANT_NONE = "grant_none";
    private static final String GRANT_READ = "grant_read";
    private static final String GRANT_WRITE = "grant_write";
    private static final String GRANT_READ_PREFIX = "grant_read_prefix";
    private static final String GRANT_READ_NOPREFIX = "grant_read_noprefix";
    private static final String GRANT_READ_PERSISTABLE = "grant_read_persistable";

    private static final String REQUEST_NONE = "request_none";
    private static final String REQUEST_READ = "request_read";
    private static final String REQUEST_READ_NESTED = "request_read_nested";
    private static final String REQUEST_TAKE_PERSISTABLE = "request_take_persistable";
    private static final String REQUEST_WRITE = "request_write";

    private static final String SOURCE_LOG_TAG = "DragSource";
    private static final String TARGET_LOG_TAG = "DropTarget";

    private static final String RESULT_KEY_START_DRAG = "START_DRAG";
    private static final String RESULT_KEY_DRAG_STARTED = "DRAG_STARTED";
    private static final String RESULT_KEY_DRAG_ENDED = "DRAG_ENDED";
    private static final String RESULT_KEY_EXTRAS = "EXTRAS";
    private static final String RESULT_KEY_DROP_RESULT = "DROP";
    private static final String RESULT_KEY_ACCESS_BEFORE = "BEFORE";
    private static final String RESULT_KEY_ACCESS_AFTER = "AFTER";
    private static final String RESULT_KEY_CLIP_DATA_ERROR = "CLIP_DATA_ERROR";
    private static final String RESULT_KEY_CLIP_DESCR_ERROR = "CLIP_DESCR_ERROR";
    private static final String RESULT_KEY_LOCAL_STATE_ERROR = "LOCAL_STATE_ERROR";

    private static final String RESULT_MISSING = "Missing";
    private static final String RESULT_OK = "OK";
    private static final String RESULT_EXCEPTION = "Exception";
    private static final String RESULT_NULL_DROP_PERMISSIONS = "Null DragAndDropPermissions";

    private static final String AM_SUPPORTS_SPLIT_SCREEN_MULTIWINDOW =
            "am supports-split-screen-multi-window";

    private ITestDevice mDevice;

    private Map<String, String> mSourceResults;
    private Map<String, String> mTargetResults;

    private String mSourcePackageName;
    private String mTargetPackageName;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = getDevice();

        if (!supportsDragAndDrop()) {
            return;
        }

        mSourcePackageName = SOURCE_PACKAGE_NAME;
        mTargetPackageName = TARGET_PACKAGE_NAME;
        cleanupState();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (!supportsDragAndDrop()) {
            return;
        }

        mDevice.executeShellCommand(AM_FORCE_STOP + mSourcePackageName);
        mDevice.executeShellCommand(AM_FORCE_STOP + mTargetPackageName);
    }

    private String executeShellCommand(String command) throws DeviceNotAvailableException {
        return mDevice.executeShellCommand(command);
    }

    private void clearLogs() throws DeviceNotAvailableException {
        executeShellCommand("logcat -c");
    }

    private String getStartCommand(String componentName, String modeExtra) {
        return AM_START_N + componentName + " -e mode " + modeExtra;
    }

    private String getMoveTaskCommand(int taskId, int stackId) throws Exception {
        return AM_MOVE_TASK + taskId + " " + stackId + " true";
    }

    private String getResizeTaskCommand(int taskId, Point topLeft, Point bottomRight)
            throws Exception {
        return AM_RESIZE_TASK + taskId + " " + topLeft.x + " " + topLeft.y + " " + bottomRight.x
                + " " + bottomRight.y;
    }

    private String getComponentName(String packageName, String activityName) {
        return packageName + "/" + packageName + "." + activityName;
    }

    /**
     * Make sure that the special activity stacks are removed and the ActivityManager/WindowManager
     * is in a good state.
     */
    private void cleanupState() throws Exception {
        executeShellCommand(AM_FORCE_STOP + SOURCE_PACKAGE_NAME);
        executeShellCommand(AM_FORCE_STOP + TARGET_PACKAGE_NAME);
        executeShellCommand(AM_FORCE_STOP + TARGET_23_PACKAGE_NAME);
        unlockDevice();

        // Reinitialize the docked stack to force the window manager to reset its default bounds.
        // See b/29068935.
        clearLogs();
        final String componentName = getComponentName(mSourcePackageName, SOURCE_ACTIVITY_NAME);
        executeShellCommand(getStartCommand(componentName, null) + " --stack " +
                FULLSCREEN_WORKSPACE_STACK_ID);
        final int taskId = getActivityTaskId(componentName);
        // Moving a task from the full screen stack to the docked stack resets
        // WindowManagerService#mDockedStackCreateBounds.
        executeShellCommand(getMoveTaskCommand(taskId, DOCKED_STACK_ID));
        waitForResume(mSourcePackageName, SOURCE_ACTIVITY_NAME);
        executeShellCommand(AM_FORCE_STOP + SOURCE_PACKAGE_NAME);

        // Remove special stacks.
        executeShellCommand(AM_REMOVE_STACK + PINNED_STACK_ID);
        executeShellCommand(AM_REMOVE_STACK + DOCKED_STACK_ID);
        executeShellCommand(AM_REMOVE_STACK + FREEFORM_WORKSPACE_STACK_ID);
    }

    private void launchDockedActivity(String packageName, String activityName, String mode)
            throws Exception {
        clearLogs();
        final String componentName = getComponentName(packageName, activityName);
        executeShellCommand(getStartCommand(componentName, mode) + " --stack " + DOCKED_STACK_ID);
        waitForResume(packageName, activityName);
    }

    private void launchFullscreenActivity(String packageName, String activityName, String mode)
            throws Exception {
        clearLogs();
        final String componentName = getComponentName(packageName, activityName);
        executeShellCommand(getStartCommand(componentName, mode) + " --stack "
                + FULLSCREEN_WORKSPACE_STACK_ID);
        waitForResume(packageName, activityName);
    }

    /**
     * @param displaySize size of the display
     * @param leftSide {@code true} to launch the app taking up the left half of the display,
     *         {@code false} to launch the app taking up the right half of the display.
     */
    private void launchFreeformActivity(String packageName, String activityName, String mode,
            Point displaySize, boolean leftSide) throws Exception{
        clearLogs();
        final String componentName = getComponentName(packageName, activityName);
        executeShellCommand(getStartCommand(componentName, mode) + " --stack "
                + FREEFORM_WORKSPACE_STACK_ID);
        waitForResume(packageName, activityName);
        Point topLeft = new Point(leftSide ? 0 : displaySize.x / 2, 0);
        Point bottomRight = new Point(leftSide ? displaySize.x / 2 : displaySize.x, displaySize.y);
        executeShellCommand(getResizeTaskCommand(getActivityTaskId(componentName), topLeft,
                bottomRight));
    }

    private void waitForResume(String packageName, String activityName) throws Exception {
        final String fullActivityName = packageName + "." + activityName;
        int retryCount = 3;
        do {
            Thread.sleep(500);
            String logs = executeShellCommand("logcat -d -b events");
            for (String line : logs.split("\\n")) {
                if(line.contains("am_on_resume_called") && line.contains(fullActivityName)) {
                    return;
                }
            }
        } while (retryCount-- > 0);

        throw new Exception(fullActivityName + " has failed to start");
    }

    private void injectInput(Point from, Point to, int durationMs) throws Exception {
        executeShellCommand(
                INPUT_MOUSE_SWIPE + from.x + " " + from.y + " " + to.x + " " + to.y + " " +
                durationMs);
    }

    static class Point {
        public int x, y;

        public Point(int _x, int _y) {
            x=_x;
            y=_y;
        }

        public Point() {}
    }

    private String findTaskInfo(String name) throws Exception {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(AM_STACK_LIST, outputReceiver);
        final String output = outputReceiver.getOutput();
        final StringBuilder builder = new StringBuilder();
        builder.append("Finding task info for task: ");
        builder.append(name);
        builder.append("\nParsing adb shell am output: " );
        builder.append(output);
        CLog.i(builder.toString());
        final Pattern pattern = Pattern.compile(String.format(TASK_REGEX_PATTERN_STRING, name));
        for (String line : output.split("\\n")) {
            final String truncatedLine;
            // Only look for the activity name before the "topActivity" string.
            final int pos = line.indexOf("topActivity");
            if (pos > 0) {
                truncatedLine = line.substring(0, pos);
            } else {
                truncatedLine = line;
            }
            if (pattern.matcher(truncatedLine).find()) {
                return truncatedLine;
            }
        }
        return "";
    }

    private boolean getWindowBounds(String name, Point from, Point to) throws Exception {
        final String taskInfo = findTaskInfo(name);
        final String[] sections = taskInfo.split("\\[");
        if (sections.length > 2) {
            try {
                parsePoint(sections[1], from);
                parsePoint(sections[2], to);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private int getActivityTaskId(String name) throws Exception {
        final String taskInfo = findTaskInfo(name);
        for (String word : taskInfo.split("\\s+")) {
            if (word.startsWith(TASK_ID_PREFIX)) {
                final String withColon = word.split("=")[1];
                return Integer.parseInt(withColon.substring(0, withColon.length() - 1));
            }
        }
        return -1;
    }

    private Point getDisplaySize() throws Exception {
        final String output = executeShellCommand("wm size");
        final String[] sizes = output.split(" ")[2].split("x");
        return new Point(Integer.valueOf(sizes[0].trim()), Integer.valueOf(sizes[1].trim()));
    }

    private Point getWindowCenter(String name) throws Exception {
        Point p1 = new Point();
        Point p2 = new Point();
        if (getWindowBounds(name, p1, p2)) {
            return new Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
        }
        return null;
    }

    private void parsePoint(String string, Point point) {
        final String[] parts = string.split("[,|\\]]");
        point.x = Integer.parseInt(parts[0]);
        point.y = Integer.parseInt(parts[1]);
    }

    private void unlockDevice() throws DeviceNotAvailableException {
        // Wake up the device, if necessary.
        executeShellCommand("input keyevent 224");
        // Unlock the screen.
        executeShellCommand("input keyevent 82");
    }

    private Map<String, String> getLogResults(String className, String lastResultKey)
            throws Exception {
        int retryCount = 10;
        Map<String, String> output = new HashMap<String, String>();
        do {

            String logs = executeShellCommand("logcat -v brief -d " + className + ":I" + " *:S");
            for (String line : logs.split("\\n")) {
                if (line.startsWith("I/" + className)) {
                    String payload = line.split(":")[1].trim();
                    final String[] split = payload.split("=");
                    if (split.length > 1) {
                        output.put(split[0], split[1]);
                    }
                }
            }
            if (output.containsKey(lastResultKey)) {
                return output;
            }
        } while (retryCount-- > 0);
        return output;
    }

    private void assertDropResult(String sourceMode, String targetMode, String expectedDropResult)
            throws Exception {
        assertDragAndDropResults(sourceMode, targetMode, RESULT_OK, expectedDropResult, RESULT_OK);
    }

    private void assertNoGlobalDragEvents(String sourceMode, String expectedStartDragResult)
            throws Exception {
        assertDragAndDropResults(
                sourceMode, REQUEST_NONE, expectedStartDragResult, RESULT_MISSING, RESULT_MISSING);
    }

    private void assertDragAndDropResults(String sourceMode, String targetMode,
                                          String expectedStartDragResult, String expectedDropResult,
                                          String expectedListenerResults) throws Exception {
        if (!supportsDragAndDrop()) {
            return;
        }

        if (supportsSplitScreenMultiWindow()) {
            launchDockedActivity(mSourcePackageName, SOURCE_ACTIVITY_NAME, sourceMode);
            launchFullscreenActivity(mTargetPackageName, TARGET_ACTIVITY_NAME, targetMode);
        } else if (supportsFreeformMultiWindow()) {
            // Fallback to try to launch two freeform windows side by side.
            Point displaySize = getDisplaySize();
            launchFreeformActivity(mSourcePackageName, SOURCE_ACTIVITY_NAME, sourceMode,
                    displaySize, true /* leftSide */);
            launchFreeformActivity(mTargetPackageName, TARGET_ACTIVITY_NAME, targetMode,
                    displaySize, false /* leftSide */);
        } else {
            return;
        }

        clearLogs();

        injectInput(
                getWindowCenter(getComponentName(mSourcePackageName, SOURCE_ACTIVITY_NAME)),
                getWindowCenter(getComponentName(mTargetPackageName, TARGET_ACTIVITY_NAME)),
                SWIPE_DURATION_MS);

        mSourceResults = getLogResults(SOURCE_LOG_TAG, RESULT_KEY_START_DRAG);
        assertSourceResult(RESULT_KEY_START_DRAG, expectedStartDragResult);

        mTargetResults = getLogResults(TARGET_LOG_TAG, RESULT_KEY_DRAG_ENDED);
        assertTargetResult(RESULT_KEY_DROP_RESULT, expectedDropResult);
        if (!RESULT_MISSING.equals(expectedDropResult)) {
            assertTargetResult(RESULT_KEY_ACCESS_BEFORE, RESULT_EXCEPTION);
            assertTargetResult(RESULT_KEY_ACCESS_AFTER, RESULT_EXCEPTION);
        }
        assertListenerResults(expectedListenerResults);
    }

    private void assertListenerResults(String expectedResult) throws Exception {
        assertTargetResult(RESULT_KEY_DRAG_STARTED, expectedResult);
        assertTargetResult(RESULT_KEY_DRAG_ENDED, expectedResult);
        assertTargetResult(RESULT_KEY_EXTRAS, expectedResult);

        assertTargetResult(RESULT_KEY_CLIP_DATA_ERROR, RESULT_MISSING);
        assertTargetResult(RESULT_KEY_CLIP_DESCR_ERROR, RESULT_MISSING);
        assertTargetResult(RESULT_KEY_LOCAL_STATE_ERROR, RESULT_MISSING);
    }

    private void assertSourceResult(String resultKey, String expectedResult) throws Exception {
        assertResult(mSourceResults, resultKey, expectedResult);
    }

    private void assertTargetResult(String resultKey, String expectedResult) throws Exception {
        assertResult(mTargetResults, resultKey, expectedResult);
    }

    private void assertResult(Map<String, String> results, String resultKey, String expectedResult)
            throws Exception {
        if (!supportsDragAndDrop()) {
            return;
        }

        if (RESULT_MISSING.equals(expectedResult)) {
            if (results.containsKey(resultKey)) {
                fail("Unexpected " + resultKey + "=" + results.get(resultKey));
            }
        } else {
            assertTrue("Missing " + resultKey, results.containsKey(resultKey));
            assertEquals(resultKey + " result mismatch,", expectedResult,
                    results.get(resultKey));
        }
    }

    private boolean supportsDragAndDrop() throws Exception {
        String supportsMultiwindow = mDevice.executeShellCommand("am supports-multiwindow").trim();
        if ("true".equals(supportsMultiwindow)) {
            return true;
        } else if ("false".equals(supportsMultiwindow)) {
            return false;
        } else {
            throw new Exception(
                    "device does not support \"am supports-multiwindow\" shell command.");
        }
    }

    private boolean supportsSplitScreenMultiWindow() throws DeviceNotAvailableException {
        return !executeShellCommand(AM_SUPPORTS_SPLIT_SCREEN_MULTIWINDOW).startsWith("false");
    }

    private boolean supportsFreeformMultiWindow() throws DeviceNotAvailableException {
        return mDevice.hasFeature("feature:android.software.freeform_window_management");
    }

    public void testCancelSoon() throws Exception {
        assertDropResult(CANCEL_SOON, REQUEST_NONE, RESULT_MISSING);
    }

    public void testDisallowGlobal() throws Exception {
        assertNoGlobalDragEvents(DISALLOW_GLOBAL, RESULT_OK);
    }

    public void testDisallowGlobalBelowSdk24() throws Exception {
        mTargetPackageName = TARGET_23_PACKAGE_NAME;
        assertNoGlobalDragEvents(GRANT_NONE, RESULT_OK);
    }

    public void testFileUriLocal() throws Exception {
        assertNoGlobalDragEvents(FILE_LOCAL, RESULT_OK);
    }

    public void testFileUriGlobal() throws Exception {
        assertNoGlobalDragEvents(FILE_GLOBAL, RESULT_EXCEPTION);
    }

    public void testGrantNoneRequestNone() throws Exception {
        assertDropResult(GRANT_NONE, REQUEST_NONE, RESULT_EXCEPTION);
    }

    public void testGrantNoneRequestRead() throws Exception {
        assertDropResult(GRANT_NONE, REQUEST_READ, RESULT_NULL_DROP_PERMISSIONS);
    }

    public void testGrantNoneRequestWrite() throws Exception {
        assertDropResult(GRANT_NONE, REQUEST_WRITE, RESULT_NULL_DROP_PERMISSIONS);
    }

    public void testGrantReadRequestNone() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_NONE, RESULT_EXCEPTION);
    }

    public void testGrantReadRequestRead() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_READ, RESULT_OK);
    }

    public void testGrantReadRequestWrite() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_WRITE, RESULT_EXCEPTION);
    }

    public void testGrantReadNoPrefixRequestReadNested() throws Exception {
        assertDropResult(GRANT_READ_NOPREFIX, REQUEST_READ_NESTED, RESULT_EXCEPTION);
    }

    public void testGrantReadPrefixRequestReadNested() throws Exception {
        assertDropResult(GRANT_READ_PREFIX, REQUEST_READ_NESTED, RESULT_OK);
    }

    public void testGrantPersistableRequestTakePersistable() throws Exception {
        assertDropResult(GRANT_READ_PERSISTABLE, REQUEST_TAKE_PERSISTABLE, RESULT_OK);
    }

    public void testGrantReadRequestTakePersistable() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_TAKE_PERSISTABLE, RESULT_EXCEPTION);
    }

    public void testGrantWriteRequestNone() throws Exception {
        assertDropResult(GRANT_WRITE, REQUEST_NONE, RESULT_EXCEPTION);
    }

    public void testGrantWriteRequestRead() throws Exception {
        assertDropResult(GRANT_WRITE, REQUEST_READ, RESULT_EXCEPTION);
    }

    public void testGrantWriteRequestWrite() throws Exception {
        assertDropResult(GRANT_WRITE, REQUEST_WRITE, RESULT_OK);
    }
}
