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
import static android.server.cts.StateLogger.log;
import static android.server.cts.StateLogger.logE;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WindowManagerState {

    public static final String TRANSIT_ACTIVITY_OPEN = "TRANSIT_ACTIVITY_OPEN";
    public static final String TRANSIT_ACTIVITY_CLOSE = "TRANSIT_ACTIVITY_CLOSE";
    public static final String TRANSIT_TASK_OPEN = "TRANSIT_TASK_OPEN";
    public static final String TRANSIT_TASK_CLOSE = "TRANSIT_TASK_CLOSE";

    public static final String TRANSIT_WALLPAPER_OPEN = "TRANSIT_WALLPAPER_OPEN";
    public static final String TRANSIT_WALLPAPER_CLOSE = "TRANSIT_WALLPAPER_CLOSE";
    public static final String TRANSIT_WALLPAPER_INTRA_OPEN = "TRANSIT_WALLPAPER_INTRA_OPEN";
    public static final String TRANSIT_WALLPAPER_INTRA_CLOSE = "TRANSIT_WALLPAPER_INTRA_CLOSE";

    public static final String TRANSIT_KEYGUARD_GOING_AWAY = "TRANSIT_KEYGUARD_GOING_AWAY";
    public static final String TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER =
            "TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER";
    public static final String TRANSIT_KEYGUARD_OCCLUDE = "TRANSIT_KEYGUARD_OCCLUDE";
    public static final String TRANSIT_KEYGUARD_UNOCCLUDE = "TRANSIT_KEYGUARD_UNOCCLUDE";

    public static final String APP_STATE_IDLE = "APP_STATE_IDLE";

    private static final String DUMPSYS_WINDOW = "dumpsys window -a";

    private static final Pattern sWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{([0-9a-fA-F]+) u(\\d+) (.+)\\}\\:");
    private static final Pattern sStartingWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{([0-9a-fA-F]+) u(\\d+) Starting (.+)\\}\\:");
    private static final Pattern sExitingWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{([0-9a-fA-F]+) u(\\d+) (.+) EXITING\\}\\:");
    private static final Pattern sDebuggerWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{([0-9a-fA-F]+) u(\\d+) Waiting For Debugger: (.+)\\}\\:");

    private static final Pattern sFocusedWindowPattern = Pattern.compile(
            "mCurrentFocus=Window\\{([0-9a-fA-F]+) u(\\d+) (\\S+)\\}");
    private static final Pattern sAppErrorFocusedWindowPattern = Pattern.compile(
            "mCurrentFocus=Window\\{([0-9a-fA-F]+) u(\\d+) Application Error\\: (\\S+)\\}");
    private static final Pattern sWaitingForDebuggerFocusedWindowPattern = Pattern.compile(
            "mCurrentFocus=Window\\{([0-9a-fA-F]+) u(\\d+) Waiting For Debugger\\: (\\S+)\\}");

    private static final Pattern sFocusedAppPattern =
            Pattern.compile("mFocusedApp=AppWindowToken\\{(.+) token=Token\\{(.+) "
                    + "ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)");
    private static final Pattern sStableBoundsPattern = Pattern.compile(
            "mStable=\\((\\d+),(\\d+)\\)-\\((\\d+),(\\d+)\\)");
    private static final Pattern sDefaultPinnedStackBoundsPattern = Pattern.compile(
            "defaultBounds=\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");
    private static final Pattern sPinnedStackMovementBoundsPattern = Pattern.compile(
            "movementBounds=\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");
    private static final Pattern sRotationPattern = Pattern.compile(
            "mRotation=(\\d).*");
    private static final Pattern sLastOrientationPattern = Pattern.compile(
            ".*mLastOrientation=(\\d)");

    private static final Pattern sLastAppTransitionPattern =
            Pattern.compile("mLastUsedAppTransition=(.+)");
    private static final Pattern sAppTransitionStatePattern =
            Pattern.compile("mAppTransitionState=(.+)");

    private static final Pattern sStackIdPattern = Pattern.compile("mStackId=(\\d+)");

    private static final Pattern sInputMethodWindowPattern =
            Pattern.compile("mInputMethodWindow=Window\\{([0-9a-fA-F]+) u\\d+ .+\\}.*");

    private static final Pattern sDisplayIdPattern =
            Pattern.compile("Display: mDisplayId=(\\d+)");

    private static final Pattern sDisplayFrozenPattern =
            Pattern.compile("mDisplayFrozen=([a-z]*) .*");

    private static final Pattern sDockedStackMinimizedPattern =
            Pattern.compile("mMinimizedDock=([a-z]*)");

    private static final Pattern[] sExtractStackExitPatterns = {
            sStackIdPattern, sWindowPattern, sStartingWindowPattern, sExitingWindowPattern,
            sDebuggerWindowPattern, sFocusedWindowPattern, sAppErrorFocusedWindowPattern,
            sWaitingForDebuggerFocusedWindowPattern,
            sFocusedAppPattern, sLastAppTransitionPattern, sDefaultPinnedStackBoundsPattern,
            sPinnedStackMovementBoundsPattern, sDisplayIdPattern, sDockedStackMinimizedPattern};

    // Windows in z-order with the top most at the front of the list.
    private List<WindowState> mWindowStates = new ArrayList();
    // Stacks in z-order with the top most at the front of the list, starting with primary display.
    private final List<WindowStack> mStacks = new ArrayList();
    // Stacks on all attached displays, in z-order with the top most at the front of the list.
    private final Map<Integer, List<WindowStack>> mDisplayStacks
            = new HashMap<>();
    private List<Display> mDisplays = new ArrayList();
    private String mFocusedWindow = null;
    private String mFocusedApp = null;
    private String mLastTransition = null;
    private String mAppTransitionState = null;
    private String mInputMethodWindowAppToken = null;
    private Rectangle mStableBounds = new Rectangle();
    private final Rectangle mDefaultPinnedStackBounds = new Rectangle();
    private final Rectangle mPinnedStackMovementBounds = new Rectangle();
    private final LinkedList<String> mSysDump = new LinkedList();
    private int mRotation;
    private int mLastOrientation;
    private boolean mDisplayFrozen;
    private boolean mIsDockedStackMinimized;

    void computeState(ITestDevice device) throws DeviceNotAvailableException {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        String dump = null;

        log("==============================");
        log("      WindowManagerState      ");
        log("==============================");
        do {
            if (retry) {
                log("***Incomplete WM state. Retrying...");
                // Wait half a second between retries for window manager to finish transitioning...
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            }

            final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            device.executeShellCommand(DUMPSYS_WINDOW, outputReceiver);
            dump = outputReceiver.getOutput();
            parseSysDump(dump);

            retry = mWindowStates.isEmpty() || mFocusedApp == null;
        } while (retry && retriesLeft-- > 0);

        if (retry) {
            log(dump);
        }

        if (mWindowStates.isEmpty()) {
            logE("No Windows found...");
        }
        if (mFocusedWindow == null) {
            logE("No Focused Window...");
        }
        if (mFocusedApp == null) {
            logE("No Focused App...");
        }
    }

    private void parseSysDump(String sysDump) {
        reset();

        Collections.addAll(mSysDump, sysDump.split("\\n"));

        int currentDisplayId = DEFAULT_DISPLAY_ID;
        while (!mSysDump.isEmpty()) {
            final Display display =
                    Display.create(mSysDump, sExtractStackExitPatterns);
            if (display != null) {
                log(display.toString());
                mDisplays.add(display);
                currentDisplayId = display.mDisplayId;
                mDisplayStacks.put(currentDisplayId, new ArrayList<>());
                continue;
            }

            final WindowStack stack =
                    WindowStack.create(mSysDump, sStackIdPattern, sExtractStackExitPatterns);

            if (stack != null) {
                mStacks.add(stack);
                mDisplayStacks.get(currentDisplayId).add(stack);
                continue;
            }


            final WindowState ws = WindowState.create(mSysDump, sExtractStackExitPatterns);
            if (ws != null) {
                log(ws.toString());

                // Check to see if we are in the middle of transitioning. If we are, we want to
                // skip dumping until window manager is done transitioning windows.
                if (ws.isStartingWindow()) {
                    log("Skipping dump due to starting window transition...");
                    return;
                }

                if (ws.isExitingWindow()) {
                    log("Skipping dump due to exiting window transition...");
                    return;
                }

                mWindowStates.add(ws);
                continue;
            }

            final String line = mSysDump.pop().trim();

            Matcher matcher = sFocusedWindowPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String focusedWindow = matcher.group(3);
                log(focusedWindow);
                mFocusedWindow = focusedWindow;
                continue;
            }

            matcher = sAppErrorFocusedWindowPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String focusedWindow = matcher.group(3);
                log(focusedWindow);
                mFocusedWindow = focusedWindow;
                continue;
            }

            matcher = sWaitingForDebuggerFocusedWindowPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String focusedWindow = matcher.group(3);
                log(focusedWindow);
                mFocusedWindow = focusedWindow;
                continue;
            }

            matcher = sFocusedAppPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String focusedApp = matcher.group(5);
                log(focusedApp);
                mFocusedApp = focusedApp;
                continue;
            }

            matcher = sAppTransitionStatePattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String appTransitionState = matcher.group(1);
                log(appTransitionState);
                mAppTransitionState = appTransitionState;
                continue;
            }

            matcher = sLastAppTransitionPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String lastAppTransitionPattern = matcher.group(1);
                log(lastAppTransitionPattern);
                mLastTransition = lastAppTransitionPattern;
                continue;
            }

            matcher = sStableBoundsPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                int left = Integer.parseInt(matcher.group(1));
                int top = Integer.parseInt(matcher.group(2));
                int right = Integer.parseInt(matcher.group(3));
                int bottom = Integer.parseInt(matcher.group(4));
                mStableBounds.setBounds(left, top, right - left, bottom - top);
                log(mStableBounds.toString());
                continue;
            }

            matcher = sDefaultPinnedStackBoundsPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                int left = Integer.parseInt(matcher.group(1));
                int top = Integer.parseInt(matcher.group(2));
                int right = Integer.parseInt(matcher.group(3));
                int bottom = Integer.parseInt(matcher.group(4));
                mDefaultPinnedStackBounds.setBounds(left, top, right - left, bottom - top);
                log(mDefaultPinnedStackBounds.toString());
                continue;
            }

            matcher = sPinnedStackMovementBoundsPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                int left = Integer.parseInt(matcher.group(1));
                int top = Integer.parseInt(matcher.group(2));
                int right = Integer.parseInt(matcher.group(3));
                int bottom = Integer.parseInt(matcher.group(4));
                mPinnedStackMovementBounds.setBounds(left, top, right - left, bottom - top);
                log(mPinnedStackMovementBounds.toString());
                continue;
            }

            matcher = sInputMethodWindowPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                mInputMethodWindowAppToken = matcher.group(1);
                log(mInputMethodWindowAppToken);
                continue;
            }

            matcher = sRotationPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                mRotation = Integer.parseInt(matcher.group(1));
                continue;
            }

            matcher = sLastOrientationPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                mLastOrientation = Integer.parseInt(matcher.group(1));
                continue;
            }

            matcher = sDisplayFrozenPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                mDisplayFrozen = Boolean.parseBoolean(matcher.group(1));
                continue;
            }

            matcher = sDockedStackMinimizedPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                mIsDockedStackMinimized = Boolean.parseBoolean(matcher.group(1));
                continue;
            }
        }
    }

    void getMatchingWindowTokens(final String windowName, List<String> tokenList) {
        tokenList.clear();

        for (WindowState ws : mWindowStates) {
            if (windowName.equals(ws.getName())) {
                tokenList.add(ws.getToken());
            }
        }
    }

    void getMatchingVisibleWindowState(final String windowName, List<WindowState> windowList) {
        windowList.clear();
        for (WindowState ws : mWindowStates) {
            if (ws.isShown() && windowName.equals(ws.getName())) {
                windowList.add(ws);
            }
        }
    }

    void getPrefixMatchingVisibleWindowState(final String windowName, List<WindowState> windowList) {
        windowList.clear();
        for (WindowState ws : mWindowStates) {
            if (ws.isShown() && ws.getName().startsWith(windowName)) {
                windowList.add(ws);
            }
        }
    }

    WindowState getWindowByPackageName(String packageName, int windowType) {
        for (WindowState ws : mWindowStates) {
            final String name = ws.getName();
            if (name == null || !name.contains(packageName)) {
                continue;
            }
            if (windowType != ws.getType()) {
                continue;
            }
            return ws;
        }

        return null;
    }

    void getWindowsByPackageName(String packageName, List<Integer> restrictToTypeList,
            List<WindowState> outWindowList) {
        outWindowList.clear();
        for (WindowState ws : mWindowStates) {
            final String name = ws.getName();
            if (name == null || !name.contains(packageName)) {
                continue;
            }
            if (restrictToTypeList != null && !restrictToTypeList.contains(ws.getType())) {
                continue;
            }
            outWindowList.add(ws);
        }
    }

    void sortWindowsByLayer(List<WindowState> windows) {
        windows.sort(Comparator.comparingInt(WindowState::getLayer));
    }

    WindowState getWindowStateForAppToken(String appToken) {
        for (WindowState ws : mWindowStates) {
            if (ws.getToken().equals(appToken)) {
                return ws;
            }
        }
        return null;
    }

    Display getDisplay(int displayId) {
        for (Display display : mDisplays) {
            if (displayId == display.getDisplayId()) {
                return display;
            }
        }
        return null;
    }

    String getFrontWindow() {
        if (mWindowStates == null || mWindowStates.isEmpty()) {
            return null;
        }
        return mWindowStates.get(0).getName();
    }

    String getFocusedWindow() {
        return mFocusedWindow;
    }

    String getFocusedApp() {
        return mFocusedApp;
    }

    String getLastTransition() {
        return mLastTransition;
    }

    String getAppTransitionState() {
        return mAppTransitionState;
    }

    int getFrontStackId(int displayId) {
        return mDisplayStacks.get(displayId).get(0).mStackId;
    }

    public int getRotation() {
        return mRotation;
    }

    int getLastOrientation() {
        return mLastOrientation;
    }

    boolean containsStack(int stackId) {
        for (WindowStack stack : mStacks) {
            if (stackId == stack.mStackId) {
                return true;
            }
        }
        return false;
    }

    /** Check if there exists a window record with matching windowName. */
    boolean containsWindow(String windowName) {
        for (WindowState window : mWindowStates) {
            if (window.getName().equals(windowName)) {
                return true;
            }
        }
        return false;
    }

    /** Check if at least one window which matches provided window name is visible. */
    boolean isWindowVisible(String windowName) {
        for (WindowState window : mWindowStates) {
            if (window.getName().equals(windowName)) {
                if (window.isShown()) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean allWindowsVisible(String windowName) {
        boolean allVisible = false;
        for (WindowState window : mWindowStates) {
            if (window.getName().equals(windowName)) {
                if (!window.isShown()) {
                    log("[VISIBLE] not visible" + windowName);
                    return false;
                }
                log("[VISIBLE] visible" + windowName);
                allVisible = true;
            }
        }
        return allVisible;
    }

    WindowStack getStack(int stackId) {
        for (WindowStack stack : mStacks) {
            if (stackId == stack.mStackId) {
                return stack;
            }
        }
        return null;
    }


    int getStackPosition(int stackId) {
        for (int i = 0; i < mStacks.size(); i++) {
            if (stackId == mStacks.get(i).mStackId) {
                return i;
            }
        }
        return -1;
    }

    WindowState getInputMethodWindowState() {
        return getWindowStateForAppToken(mInputMethodWindowAppToken);
    }

    Rectangle getStableBounds() {
        return mStableBounds;
    }

    Rectangle getDefaultPinnedStackBounds() {
        return mDefaultPinnedStackBounds;
    }

    Rectangle getPinnedStackMomentBounds() {
        return mPinnedStackMovementBounds;
    }

    WindowState findFirstWindowWithType(int type) {
        for (WindowState window : mWindowStates) {
            if (window.getType() == type) {
                return window;
            }
        }
        return null;
    }

    public boolean isDisplayFrozen() {
        return mDisplayFrozen;
    }

    public boolean isDockedStackMinimized() {
        return mIsDockedStackMinimized;
    }

    private void reset() {
        mSysDump.clear();
        mStacks.clear();
        mDisplays.clear();
        mWindowStates.clear();
        mFocusedWindow = null;
        mFocusedApp = null;
        mInputMethodWindowAppToken = null;
    }

    static class WindowStack extends WindowContainer {

        private static final Pattern sTaskIdPattern = Pattern.compile("taskId=(\\d+)");
        private static final Pattern sWindowAnimationBackgroundSurfacePattern =
                Pattern.compile("mWindowAnimationBackgroundSurface:");

        int mStackId;
        ArrayList<WindowTask> mTasks = new ArrayList();
        boolean mWindowAnimationBackgroundSurfaceShowing;

        private WindowStack() {

        }

        static WindowStack create(
                LinkedList<String> dump, Pattern stackIdPattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = stackIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a stack.
                return null;
            }
            // For the stack Id line we just read.
            dump.pop();

            final WindowStack stack = new WindowStack();
            log(line);
            final String stackId = matcher.group(1);
            log(stackId);
            stack.mStackId = Integer.parseInt(stackId);
            stack.extract(dump, exitPatterns);
            return stack;
        }

        void extract(LinkedList<String> dump, Pattern[] exitPatterns) {

            final List<Pattern> taskExitPatterns = new ArrayList();
            Collections.addAll(taskExitPatterns, exitPatterns);
            taskExitPatterns.add(sTaskIdPattern);
            taskExitPatterns.add(sWindowAnimationBackgroundSurfacePattern);
            final Pattern[] taskExitPatternsArray =
                    taskExitPatterns.toArray(new Pattern[taskExitPatterns.size()]);

            while (!doneExtracting(dump, exitPatterns)) {
                final WindowTask task =
                        WindowTask.create(dump, sTaskIdPattern, taskExitPatternsArray);

                if (task != null) {
                    mTasks.add(task);
                    continue;
                }

                final String line = dump.pop().trim();

                if (extractFullscreen(line)) {
                    continue;
                }

                if (extractBounds(line)) {
                    continue;
                }

                if (extractWindowAnimationBackgroundSurface(line)) {
                    continue;
                }
            }
        }

        boolean extractWindowAnimationBackgroundSurface(String line) {
            if (sWindowAnimationBackgroundSurfacePattern.matcher(line).matches()) {
                log(line);
                mWindowAnimationBackgroundSurfaceShowing = true;
                return true;
            }
            return false;
        }

        WindowTask getTask(int taskId) {
            for (WindowTask task : mTasks) {
                if (taskId == task.mTaskId) {
                    return task;
                }
            }
            return null;
        }

        boolean isWindowAnimationBackgroundSurfaceShowing() {
            return mWindowAnimationBackgroundSurfaceShowing;
        }
    }

    static class WindowTask extends WindowContainer {
        private static final Pattern sTempInsetBoundsPattern =
                Pattern.compile("mTempInsetBounds=\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        private static final Pattern sAppTokenPattern = Pattern.compile(
                "Activity #(\\d+) AppWindowToken\\{(\\S+) token=Token\\{(\\S+) "
                + "ActivityRecord\\{(\\S+) u(\\d+) (\\S+) t(\\d+)\\}\\}\\}");


        int mTaskId;
        Rectangle mTempInsetBounds;
        List<String> mAppTokens = new ArrayList();

        private WindowTask() {
        }

        static WindowTask create(
                LinkedList<String> dump, Pattern taskIdPattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = taskIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a task.
                return null;
            }
            // For the task Id line we just read.
            dump.pop();

            final WindowTask task = new WindowTask();
            log(line);
            final String taskId = matcher.group(1);
            log(taskId);
            task.mTaskId = Integer.parseInt(taskId);
            task.extract(dump, exitPatterns);
            return task;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {
            while (!doneExtracting(dump, exitPatterns)) {
                final String line = dump.pop().trim();

                if (extractFullscreen(line)) {
                    continue;
                }

                if (extractBounds(line)) {
                    continue;
                }

                Matcher matcher = sTempInsetBoundsPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    mTempInsetBounds = extractBounds(matcher);
                }

                matcher = sAppTokenPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    final String appToken = matcher.group(6);
                    log(appToken);
                    mAppTokens.add(appToken);
                    continue;
                }
            }
        }
    }

    static abstract class WindowContainer {
        protected static final Pattern sFullscreenPattern = Pattern.compile("mFillsParent=(\\S+)");
        protected static final Pattern sBoundsPattern =
                Pattern.compile("mBounds=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");

        protected boolean mFullscreen;
        protected Rectangle mBounds;

        static boolean doneExtracting(LinkedList<String> dump, Pattern[] exitPatterns) {
            if (dump.isEmpty()) {
                return true;
            }
            final String line = dump.peek().trim();

            for (Pattern pattern : exitPatterns) {
                if (pattern.matcher(line).matches()) {
                    return true;
                }
            }
            return false;
        }

        boolean extractFullscreen(String line) {
            final Matcher matcher = sFullscreenPattern.matcher(line);
            if (!matcher.matches()) {
                return false;
            }
            log(line);
            final String fullscreen = matcher.group(1);
            log(fullscreen);
            mFullscreen = Boolean.valueOf(fullscreen);
            return true;
        }

        boolean extractBounds(String line) {
            final Matcher matcher = sBoundsPattern.matcher(line);
            if (!matcher.matches()) {
                return false;
            }
            log(line);
            mBounds = extractBounds(matcher);
            return true;
        }

        static Rectangle extractBounds(Matcher matcher) {
            final int left = Integer.valueOf(matcher.group(1));
            final int top = Integer.valueOf(matcher.group(2));
            final int right = Integer.valueOf(matcher.group(3));
            final int bottom = Integer.valueOf(matcher.group(4));
            final Rectangle rect = new Rectangle(left, top, right - left, bottom - top);

            log(rect.toString());
            return rect;
        }

        static void extractMultipleBounds(Matcher matcher, int groupIndex, Rectangle... rectList) {
            for (Rectangle rect : rectList) {
                if (rect == null) {
                    return;
                }
                final int left = Integer.valueOf(matcher.group(groupIndex++));
                final int top = Integer.valueOf(matcher.group(groupIndex++));
                final int right = Integer.valueOf(matcher.group(groupIndex++));
                final int bottom = Integer.valueOf(matcher.group(groupIndex++));
                rect.setBounds(left, top, right - left, bottom - top);
            }
        }

        Rectangle getBounds() {
            return mBounds;
        }

        boolean isFullscreen() {
            return mFullscreen;
        }
    }

    static class Display extends WindowContainer {
        private static final String TAG = "[Display] ";

        private static final Pattern sDisplayInfoPattern =
                Pattern.compile("(.+) (\\d+)dpi cur=(\\d+)x(\\d+) app=(\\d+)x(\\d+) (.+)");

        private final int mDisplayId;
        private Rectangle mDisplayRect = new Rectangle();
        private Rectangle mAppRect = new Rectangle();
        private int mDpi;

        private Display(int displayId) {
            mDisplayId = displayId;
        }

        int getDisplayId() {
            return mDisplayId;
        }

        int getDpi() {
            return mDpi;
        }

        Rectangle getDisplayRect() {
            return mDisplayRect;
        }

        Rectangle getAppRect() {
            return mAppRect;
        }

        static Display create(LinkedList<String> dump, Pattern[] exitPatterns) {
            // TODO: exit pattern for displays?
            final String line = dump.peek().trim();

            Matcher matcher = sDisplayIdPattern.matcher(line);
            if (!matcher.matches()) {
                return null;
            }

            log(TAG + "DISPLAY_ID: " + line);
            dump.pop();

            final int displayId = Integer.valueOf(matcher.group(1));
            final Display display = new Display(displayId);
            display.extract(dump, exitPatterns);
            return display;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {
            while (!doneExtracting(dump, exitPatterns)) {
                final String line = dump.pop().trim();

                final Matcher matcher = sDisplayInfoPattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "DISPLAY_INFO: " + line);
                    mDpi = Integer.valueOf(matcher.group(2));

                    final int displayWidth = Integer.valueOf(matcher.group(3));
                    final int displayHeight = Integer.valueOf(matcher.group(4));
                    mDisplayRect.setBounds(0, 0, displayWidth, displayHeight);

                    final int appWidth = Integer.valueOf(matcher.group(5));
                    final int appHeight = Integer.valueOf(matcher.group(6));
                    mAppRect.setBounds(0, 0, appWidth, appHeight);

                    // break as we don't need other info for now
                    break;
                }
                // Extract other info here if needed
            }
        }

        @Override
        public String toString() {
            return "Display #" + mDisplayId + ": mDisplayRect=" + mDisplayRect
                    + " mAppRect=" + mAppRect;
        }
    }

    public static class WindowState extends WindowContainer {
        private static final String TAG = "[WindowState] ";

        public static final int TYPE_WALLPAPER = 2013;

        private static final int WINDOW_TYPE_NORMAL   = 0;
        private static final int WINDOW_TYPE_STARTING = 1;
        private static final int WINDOW_TYPE_EXITING  = 2;
        private static final int WINDOW_TYPE_DEBUGGER = 3;

        private static final String RECT_STR = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]";
        private static final String NEGATIVE_VALUES_ALLOWED_RECT_STR =
                "\\[([-\\d]+),([-\\d]+)\\]\\[([-\\d]+),([-\\d]+)\\]";
        private static final Pattern sMainFramePattern = Pattern.compile("mFrame=" + RECT_STR + ".+");
        private static final Pattern sFramePattern =
                Pattern.compile("Frames: containing=" + RECT_STR + " parent=" + RECT_STR);
        private static final Pattern sContentFramePattern =
            Pattern.compile("content=" + RECT_STR + " .+");
        private static final Pattern sWindowAssociationPattern =
                Pattern.compile("mDisplayId=(\\d+) stackId=(\\d+) (.+)");
        private static final Pattern sSurfaceInsetsPattern =
            Pattern.compile("Cur insets.+surface=" + RECT_STR + ".+");
        private static final Pattern sContentInsetsPattern =
                Pattern.compile("Cur insets.+content=" + NEGATIVE_VALUES_ALLOWED_RECT_STR + ".+");
        private static final Pattern sGivenContentInsetsPattern =
                Pattern.compile("mGivenContentInsets=" + RECT_STR + ".+");
        private static final Pattern sCropPattern =
            Pattern.compile(".+mLastClipRect=" + RECT_STR + ".*");
        private static final Pattern sSurfacePattern =
                Pattern.compile("Surface: shown=(\\S+) layer=(\\d+) alpha=[\\d.]+ rect=\\([\\d.-]+,[\\d.-]+\\) [\\d.]+ x [\\d.]+.*");
        private static final Pattern sAttrsPattern=
                Pattern.compile("mAttrs=WM\\.LayoutParams\\{.*ty=(\\d+).*\\}");


        private final String mName;
        private final String mAppToken;
        private final int mWindowType;
        private int mType;
        private int mDisplayId;
        private int mStackId;
        private int mLayer;
        private boolean mShown;
        private Rectangle mContainingFrame = new Rectangle();
        private Rectangle mParentFrame = new Rectangle();
        private Rectangle mContentFrame = new Rectangle();
        private Rectangle mFrame = new Rectangle();
        private Rectangle mSurfaceInsets = new Rectangle();
        private Rectangle mContentInsets = new Rectangle();
        private Rectangle mGivenContentInsets = new Rectangle();
        private Rectangle mCrop = new Rectangle();


        private WindowState(Matcher matcher, int windowType) {
            mName = matcher.group(4);
            mAppToken = matcher.group(2);
            mWindowType = windowType;
        }

        public String getName() {
            return mName;
        }

        String getToken() {
            return mAppToken;
        }

        boolean isStartingWindow() {
            return mWindowType == WINDOW_TYPE_STARTING;
        }

        boolean isExitingWindow() {
            return mWindowType == WINDOW_TYPE_EXITING;
        }

        boolean isDebuggerWindow() {
            return mWindowType == WINDOW_TYPE_DEBUGGER;
        }

        int getDisplayId() {
            return mDisplayId;
        }

        int getStackId() {
            return mStackId;
        }

        int getLayer() {
            return mLayer;
        }

        Rectangle getContainingFrame() {
            return mContainingFrame;
        }

        Rectangle getFrame() {
            return mFrame;
        }

        Rectangle getSurfaceInsets() {
            return mSurfaceInsets;
        }

        Rectangle getContentInsets() {
            return mContentInsets;
        }

        Rectangle getGivenContentInsets() {
            return mGivenContentInsets;
        }

        Rectangle getContentFrame() {
            return mContentFrame;
        }

        Rectangle getParentFrame() {
            return mParentFrame;
        }

        Rectangle getCrop() {
            return mCrop;
        }

        boolean isShown() {
            return mShown;
        }

        int getType() {
            return mType;
        }

        static WindowState create(LinkedList<String> dump, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            Matcher matcher = sWindowPattern.matcher(line);
            if (!matcher.matches()) {
                return null;
            }

            log(TAG + "WINDOW: " + line);
            dump.pop();

            final WindowState window;
            Matcher specialMatcher;
            if ((specialMatcher = sStartingWindowPattern.matcher(line)).matches()) {
                log(TAG + "STARTING: " + line);
                window = new WindowState(specialMatcher, WINDOW_TYPE_STARTING);
            } else if ((specialMatcher = sExitingWindowPattern.matcher(line)).matches()) {
                log(TAG + "EXITING: " + line);
                window = new WindowState(specialMatcher, WINDOW_TYPE_EXITING);
            } else if ((specialMatcher = sDebuggerWindowPattern.matcher(line)).matches()) {
                log(TAG + "DEBUGGER: " + line);
                window = new WindowState(specialMatcher, WINDOW_TYPE_DEBUGGER);
            } else {
                window = new WindowState(matcher, WINDOW_TYPE_NORMAL);
            }

            window.extract(dump, exitPatterns);
            return window;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {
            while (!doneExtracting(dump, exitPatterns)) {
                final String line = dump.pop().trim();

                Matcher matcher = sWindowAssociationPattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "WINDOW_ASSOCIATION: " + line);
                    mDisplayId = Integer.valueOf(matcher.group(1));
                    mStackId = Integer.valueOf(matcher.group(2));
                    continue;
                }

                matcher = sMainFramePattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "MAIN WINDOW FRAME: " + line);
                    mFrame = extractBounds(matcher);
                    continue;
                }

                matcher = sFramePattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "FRAME: " + line);
                    extractMultipleBounds(matcher, 1, mContainingFrame, mParentFrame);
                    continue;
                }

                matcher = sContentFramePattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "CONTENT FRAME: " + line);
                    mContentFrame = extractBounds(matcher);
                }

                matcher = sSurfaceInsetsPattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "INSETS: " + line);
                    mSurfaceInsets = extractBounds(matcher);
                }

                matcher = sContentInsetsPattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "CONTENT INSETS: " + line);
                    mContentInsets = extractBounds(matcher);
                }

                matcher = sCropPattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "CROP: " + line);
                    mCrop = extractBounds(matcher);
                }

                matcher = sSurfacePattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "SURFACE: " + line);
                    mShown = Boolean.valueOf(matcher.group(1));
                    mLayer = Integer.valueOf(matcher.group(2));
                }

                matcher = sAttrsPattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "ATTRS: " + line);
                    mType = Integer.valueOf(matcher.group(1));
                }

                matcher = sGivenContentInsetsPattern.matcher(line);
                if (matcher.matches()) {
                    log(TAG + "GIVEN CONTENT INSETS: " + line);
                    mGivenContentInsets = extractBounds(matcher);
                }

                // Extract other info here if needed
            }
        }

        private static String getWindowTypeSuffix(int windowType) {
            switch (windowType) {
            case WINDOW_TYPE_STARTING: return " STARTING";
            case WINDOW_TYPE_EXITING: return " EXITING";
            case WINDOW_TYPE_DEBUGGER: return " DEBUGGER";
            default: break;
            }
            return "";
        }

        @Override
        public String toString() {
            return "WindowState: {" + mAppToken + " " + mName
                    + getWindowTypeSuffix(mWindowType) + "}" + " type=" + mType
                    + " cf=" + mContainingFrame + " pf=" + mParentFrame;
        }
    }
}
