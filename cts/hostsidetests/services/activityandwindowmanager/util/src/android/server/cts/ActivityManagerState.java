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

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.awt.Rectangle;
import java.lang.Integer;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static android.server.cts.ActivityManagerTestBase.HOME_STACK_ID;
import static android.server.cts.ActivityManagerTestBase.RECENTS_STACK_ID;
import static android.server.cts.StateLogger.log;
import static android.server.cts.StateLogger.logE;

class ActivityManagerState {
    public static final int DUMP_MODE_ACTIVITIES = 0;

    public static final String STATE_RESUMED = "RESUMED";
    public static final String STATE_PAUSED = "PAUSED";
    public static final String STATE_STOPPED = "STOPPED";
    public static final String STATE_DESTROYED = "DESTROYED";

    public static final String RESIZE_MODE_RESIZEABLE = "RESIZE_MODE_RESIZEABLE";

    private static final String DUMPSYS_ACTIVITY_ACTIVITIES = "dumpsys activity activities";

    // Copied from ActivityRecord.java
    private static final int APPLICATION_ACTIVITY_TYPE = 0;
    private static final int HOME_ACTIVITY_TYPE = 1;
    private static final int RECENTS_ACTIVITY_TYPE = 2;

    private final Pattern mDisplayIdPattern = Pattern.compile("Display #(\\d+).*");
    private final Pattern mStackIdPattern = Pattern.compile("Stack #(\\d+)\\:");
    private final Pattern mResumedActivityPattern =
            Pattern.compile("ResumedActivity\\: ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)\\}");
    private final Pattern mFocusedStackPattern =
            Pattern.compile("mFocusedStack=ActivityStack\\{(.+) stackId=(\\d+), (.+)\\}(.+)");

    private final Pattern[] mExtractStackExitPatterns =
            { mStackIdPattern, mResumedActivityPattern, mFocusedStackPattern, mDisplayIdPattern };

    // Stacks in z-order with the top most at the front of the list, starting with primary display.
    private final List<ActivityStack> mStacks = new ArrayList();
    // Stacks on all attached displays, in z-order with the top most at the front of the list.
    private final Map<Integer, List<ActivityStack>> mDisplayStacks = new HashMap<>();
    private KeyguardControllerState mKeyguardControllerState;
    private int mFocusedStackId = -1;
    private String mResumedActivityRecord = null;
    private final List<String> mResumedActivities = new ArrayList();
    private final LinkedList<String> mSysDump = new LinkedList();

    void computeState(ITestDevice device) throws DeviceNotAvailableException {
        computeState(device, DUMP_MODE_ACTIVITIES);
    }

    void computeState(ITestDevice device, int dumpMode) throws DeviceNotAvailableException {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        String dump = null;

        log("==============================");
        log("     ActivityManagerState     ");
        log("==============================");

        do {
            if (retry) {
                log("***Incomplete AM state. Retrying...");
                // Wait half a second between retries for activity manager to finish transitioning.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            }

            final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            String dumpsysCmd = "";
            switch (dumpMode) {
                case DUMP_MODE_ACTIVITIES:
                    dumpsysCmd = DUMPSYS_ACTIVITY_ACTIVITIES; break;
            }
            device.executeShellCommand(dumpsysCmd, outputReceiver);
            dump = outputReceiver.getOutput();
            parseSysDump(dump);

            retry = mStacks.isEmpty() || mFocusedStackId == -1 || (mResumedActivityRecord == null
                    || mResumedActivities.isEmpty()) && !mKeyguardControllerState.keyguardShowing;
        } while (retry && retriesLeft-- > 0);

        if (retry) {
            log(dump);
        }

        if (mStacks.isEmpty()) {
            logE("No stacks found...");
        }
        if (mFocusedStackId == -1) {
            logE("No focused stack found...");
        }
        if (mResumedActivityRecord == null) {
            logE("No focused activity found...");
        }
        if (mResumedActivities.isEmpty()) {
            logE("No resumed activities found...");
        }
    }

    private void parseSysDump(String sysDump) {
        reset();

        Collections.addAll(mSysDump, sysDump.split("\\n"));

        int currentDisplayId = 0;
        while (!mSysDump.isEmpty()) {
            final ActivityStack stack = ActivityStack.create(mSysDump, mStackIdPattern,
                    mExtractStackExitPatterns, currentDisplayId);

            if (stack != null) {
                mStacks.add(stack);
                mDisplayStacks.get(currentDisplayId).add(stack);
                if (stack.mResumedActivity != null) {
                    mResumedActivities.add(stack.mResumedActivity);
                }
                continue;
            }

            KeyguardControllerState controller = KeyguardControllerState.create(
                    mSysDump, new Pattern[0]);
            if (controller != null) {
                mKeyguardControllerState = controller;
                continue;
            }

            final String line = mSysDump.pop().trim();

            Matcher matcher = mFocusedStackPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String stackId = matcher.group(2);
                log(stackId);
                mFocusedStackId = Integer.parseInt(stackId);
                continue;
            }

            matcher = mResumedActivityPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                mResumedActivityRecord = matcher.group(3);
                log(mResumedActivityRecord);
                continue;
            }

            matcher = mDisplayIdPattern.matcher(line);
            if (matcher.matches()) {
                log(line);
                final String displayId = matcher.group(1);
                log(displayId);
                currentDisplayId = Integer.parseInt(displayId);
                mDisplayStacks.put(currentDisplayId, new ArrayList<>());
            }
        }
    }

    private void reset() {
        mStacks.clear();
        mFocusedStackId = -1;
        mResumedActivityRecord = null;
        mResumedActivities.clear();
        mSysDump.clear();
        mKeyguardControllerState = null;
    }

    int getFrontStackId(int displayId) {
        return mDisplayStacks.get(displayId).get(0).mStackId;
    }

    int getFocusedStackId() {
        return mFocusedStackId;
    }

    String getFocusedActivity() {
        return mResumedActivityRecord;
    }

    String getResumedActivity() {
        return mResumedActivities.get(0);
    }

    int getResumedActivitiesCount() {
        return mResumedActivities.size();
    }

    public KeyguardControllerState getKeyguardControllerState() {
        return mKeyguardControllerState;
    }

    boolean containsStack(int stackId) {
        return getStackById(stackId) != null;
    }

    ActivityStack getStackById(int stackId) {
        for (ActivityStack stack : mStacks) {
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

    List<ActivityStack> getStacks() {
        return new ArrayList(mStacks);
    }

    int getStackCount() {
        return mStacks.size();
    }

    boolean containsActivity(String activityName) {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (activity.name.equals(activityName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean isActivityVisible(String activityName) {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
               for (Activity activity : task.mActivities) {
                   if (activity.name.equals(activityName)) {
                       return activity.visible;
                   }
               }
            }
        }
        return false;
    }

    boolean containsStartedActivities() {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (!activity.state.equals(STATE_STOPPED)
                            && !activity.state.equals(STATE_DESTROYED)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean hasActivityState(String activityName, String activityState) {
        String fullName = ActivityManagerTestBase.getActivityComponentName(activityName);
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (activity.name.equals(fullName)) {
                        return activity.state.equals(activityState);
                    }
                }
            }
        }
        return false;
    }

    int getActivityProcId(String activityName) {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
               for (Activity activity : task.mActivities) {
                   if (activity.name.equals(activityName)) {
                       return activity.procId;
                   }
               }
            }
        }
        return -1;
    }

    boolean isHomeActivityVisible() {
        final Activity homeActivity = getHomeActivity();
        return homeActivity != null && homeActivity.visible;
    }

    boolean isRecentsActivityVisible() {
        final Activity recentsActivity = getRecentsActivity();
        return recentsActivity != null && recentsActivity.visible;
    }

    String getHomeActivityName() {
        Activity activity = getHomeActivity();
        if (activity == null) {
            return null;
        }
        return activity.name;
    }

    ActivityTask getHomeTask() {
        ActivityStack homeStack = getStackById(HOME_STACK_ID);
        if (homeStack != null) {
            for (ActivityTask task : homeStack.mTasks) {
                if (task.mTaskType == HOME_ACTIVITY_TYPE) {
                    return task;
                }
            }
            return null;
        }
        return null;
    }

    ActivityTask getRecentsTask() {
        ActivityStack recentsStack = getStackById(RECENTS_STACK_ID);
        if (recentsStack != null) {
            for (ActivityTask task : recentsStack.mTasks) {
                if (task.mTaskType == RECENTS_ACTIVITY_TYPE) {
                    return task;
                }
            }
            return null;
        }
        return null;
    }

    private Activity getHomeActivity() {
        final ActivityTask homeTask = getHomeTask();
        return homeTask != null ? homeTask.mActivities.get(homeTask.mActivities.size() - 1) : null;
    }

    private Activity getRecentsActivity() {
        final ActivityTask recentsTask = getRecentsTask();
        return recentsTask != null ? recentsTask.mActivities.get(recentsTask.mActivities.size() - 1)
                : null;
    }

    ActivityTask getTaskByActivityName(String activityName) {
        return getTaskByActivityName(activityName, -1);
    }

    ActivityTask getTaskByActivityName(String activityName, int stackId) {
        String fullName = ActivityManagerTestBase.getActivityComponentName(activityName);
        for (ActivityStack stack : mStacks) {
            if (stackId == -1 || stackId == stack.mStackId) {
                for (ActivityTask task : stack.mTasks) {
                    for (Activity activity : task.mActivities) {
                        if (activity.name.equals(fullName)) {
                            return task;
                        }
                    }
                }
            }
        }
        return null;
    }

    static class ActivityStack extends ActivityContainer {

        private static final Pattern TASK_ID_PATTERN = Pattern.compile("Task id #(\\d+)");
        private static final Pattern RESUMED_ACTIVITY_PATTERN = Pattern.compile(
                "mResumedActivity\\: ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)\\}");
        private static final Pattern SLEEPING_PATTERN = Pattern.compile("isSleeping=(\\S+)");

        int mDisplayId;
        int mStackId;
        String mResumedActivity;
        Boolean mSleeping; // A Boolean to trigger an NPE if it's not initialized
        ArrayList<ActivityTask> mTasks = new ArrayList();

        private ActivityStack() {
        }

        static ActivityStack create(LinkedList<String> dump, Pattern stackIdPattern,
                                    Pattern[] exitPatterns, int displayId) {
            final String line = dump.peek().trim();

            final Matcher matcher = stackIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a stack.
                return null;
            }
            // For the stack Id line we just read.
            dump.pop();

            final ActivityStack stack = new ActivityStack();
            stack.mDisplayId = displayId;
            log(line);
            final String stackId = matcher.group(1);
            log(stackId);
            stack.mStackId = Integer.parseInt(stackId);
            stack.extract(dump, exitPatterns);
            return stack;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {

            final List<Pattern> taskExitPatterns = new ArrayList();
            Collections.addAll(taskExitPatterns, exitPatterns);
            taskExitPatterns.add(TASK_ID_PATTERN);
            taskExitPatterns.add(RESUMED_ACTIVITY_PATTERN);
            final Pattern[] taskExitPatternsArray =
                    taskExitPatterns.toArray(new Pattern[taskExitPatterns.size()]);

            while (!doneExtracting(dump, exitPatterns)) {
                final ActivityTask task =
                        ActivityTask.create(dump, TASK_ID_PATTERN, taskExitPatternsArray);

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

                Matcher matcher = RESUMED_ACTIVITY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    mResumedActivity = matcher.group(3);
                    log(mResumedActivity);
                    continue;
                }

                matcher = SLEEPING_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    mSleeping = "true".equals(matcher.group(1));
                    continue;
                }
            }
        }

        /**
         * @return the bottom task in the stack.
         */
        ActivityTask getBottomTask() {
            if (!mTasks.isEmpty()) {
                // NOTE: Unlike the ActivityManager internals, we dump the state from top to bottom,
                //       so the indices are inverted
                return mTasks.get(mTasks.size() - 1);
            }
            return null;
        }

        /**
         * @return the top task in the stack.
         */
        ActivityTask getTopTask() {
            if (!mTasks.isEmpty()) {
                // NOTE: Unlike the ActivityManager internals, we dump the state from top to bottom,
                //       so the indices are inverted
                return mTasks.get(0);
            }
            return null;
        }

        List<ActivityTask> getTasks() {
            return new ArrayList(mTasks);
        }

        ActivityTask getTask(int taskId) {
            for (ActivityTask task : mTasks) {
                if (taskId == task.mTaskId) {
                    return task;
                }
            }
            return null;
        }
    }

    static class ActivityTask extends ActivityContainer {
        private static final Pattern TASK_RECORD_PATTERN = Pattern.compile("\\* TaskRecord\\"
                + "{(\\S+) #(\\d+) (\\S+)=(\\S+) U=(\\d+) StackId=(\\d+) sz=(\\d+)\\}");

        private static final Pattern LAST_NON_FULLSCREEN_BOUNDS_PATTERN = Pattern.compile(
                "mLastNonFullscreenBounds=Rect\\((\\d+), (\\d+) - (\\d+), (\\d+)\\)");

        private static final Pattern ORIG_ACTIVITY_PATTERN = Pattern.compile("origActivity=(\\S+)");
        private static final Pattern REAL_ACTIVITY_PATTERN = Pattern.compile("realActivity=(\\S+)");

        private static final Pattern ACTIVITY_NAME_PATTERN = Pattern.compile(
                "\\* Hist #(\\d+)\\: ActivityRecord\\{(\\S+) u(\\d+) (\\S+) t(\\d+)\\}");

        private static final Pattern TASK_TYPE_PATTERN = Pattern.compile("autoRemoveRecents=(\\S+) "
                + "isPersistable=(\\S+) numFullscreen=(\\d+) taskType=(\\d+) "
                + "mTaskToReturnTo=(\\d+)");

        private static final Pattern RESIZABLE_PATTERN = Pattern.compile(
                ".*mResizeMode=([^\\s]+).*");

        int mTaskId;
        int mStackId;
        Rectangle mLastNonFullscreenBounds;
        String mRealActivity;
        String mOrigActivity;
        ArrayList<Activity> mActivities = new ArrayList();
        int mTaskType = -1;
        int mReturnToType = -1;
        private String mResizeMode;

        private ActivityTask() {
        }

        static ActivityTask create(
                LinkedList<String> dump, Pattern taskIdPattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = taskIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a task.
                return null;
            }
            // For the task Id line we just read.
            dump.pop();

            final ActivityTask task = new ActivityTask();
            log(line);
            final String taskId = matcher.group(1);
            log(taskId);
            task.mTaskId = Integer.parseInt(taskId);
            task.extract(dump, exitPatterns);
            return task;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {
            final List<Pattern> activityExitPatterns = new ArrayList();
            Collections.addAll(activityExitPatterns, exitPatterns);
            activityExitPatterns.add(ACTIVITY_NAME_PATTERN);
            final Pattern[] activityExitPatternsArray =
                    activityExitPatterns.toArray(new Pattern[activityExitPatterns.size()]);

            while (!doneExtracting(dump, exitPatterns)) {
                final Activity activity =
                        Activity.create(dump, ACTIVITY_NAME_PATTERN, activityExitPatternsArray);

                if (activity != null) {
                    mActivities.add(activity);
                    continue;
                }

                final String line = dump.pop().trim();

                if (extractFullscreen(line)) {
                    continue;
                }

                if (extractBounds(line)) {
                    continue;
                }

                if (extractMinimalSize(line)) {
                    continue;
                }

                Matcher matcher = TASK_RECORD_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    final String stackId = matcher.group(6);
                    mStackId = Integer.valueOf(stackId);
                    log(stackId);
                    continue;
                }

                matcher = LAST_NON_FULLSCREEN_BOUNDS_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    mLastNonFullscreenBounds = extractBounds(matcher);
                }

                matcher = REAL_ACTIVITY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    if (mRealActivity == null) {
                        log(line);
                        mRealActivity = matcher.group(1);
                        log(mRealActivity);
                    }
                    continue;
                }

                matcher = ORIG_ACTIVITY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    if (mOrigActivity == null) {
                        log(line);
                        mOrigActivity = matcher.group(1);
                        log(mOrigActivity);
                    }
                    continue;
                }

                matcher = TASK_TYPE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    mTaskType = Integer.valueOf(matcher.group(4));
                    mReturnToType = Integer.valueOf(matcher.group(5));
                    continue;
                }

                matcher = RESIZABLE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    mResizeMode = matcher.group(1);
                    log(mResizeMode);
                    continue;
                }
            }
        }

        public String getResizeMode() {
            return mResizeMode;
        }

        /**
         * @return whether this task contains the given activity.
         */
        public boolean containsActivity(String activityName) {
            for (Activity activity : mActivities) {
                if (activity.name.equals(activityName)) {
                    return true;
                }
            }
            return false;
        }
    }

    static class Activity {
        private static final Pattern STATE_PATTERN = Pattern.compile("state=(\\S+).*");
        private static final Pattern VISIBILITY_PATTERN = Pattern.compile("keysPaused=(\\S+) "
                + "inHistory=(\\S+) visible=(\\S+) sleeping=(\\S+) idle=(\\S+) "
                + "mStartingWindowState=(\\S+)");
        private static final Pattern FRONT_OF_TASK_PATTERN = Pattern.compile("frontOfTask=(\\S+) "
                + "task=TaskRecord\\{(\\S+) #(\\d+) A=(\\S+) U=(\\d+) StackId=(\\d+) sz=(\\d+)\\}");
        private static final Pattern PROCESS_RECORD_PATTERN = Pattern.compile(
                "app=ProcessRecord\\{(\\S+) (\\d+):(\\S+)/(.+)\\}");

        String name;
        String state;
        boolean visible;
        boolean frontOfTask;
        int procId = -1;

        private Activity() {
        }

        static Activity create(
                LinkedList<String> dump, Pattern activityNamePattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = activityNamePattern.matcher(line);
            if (!matcher.matches()) {
                // Not an activity.
                return null;
            }
            // For the activity name line we just read.
            dump.pop();

            final Activity activity = new Activity();
            log(line);
            activity.name = matcher.group(4);
            log(activity.name);
            activity.extract(dump, exitPatterns);
            return activity;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {

            while (!doneExtracting(dump, exitPatterns)) {
                final String line = dump.pop().trim();

                // Break the activity extraction once we hit an empty line
                if (line.isEmpty()) {
                    break;
                }

                Matcher matcher = VISIBILITY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    final String visibleString = matcher.group(3);
                    visible = Boolean.valueOf(visibleString);
                    log(visibleString);
                    continue;
                }

                matcher = STATE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    state = matcher.group(1);
                    log(state);
                    continue;
                }

                matcher = PROCESS_RECORD_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    final String procIdString = matcher.group(2);
                    procId = Integer.valueOf(procIdString);
                    log(procIdString);
                    continue;
                }

                matcher = FRONT_OF_TASK_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    final String frontOfTaskString = matcher.group(1);
                    frontOfTask = Boolean.valueOf(frontOfTaskString);
                    log(frontOfTaskString);
                    continue;
                }
            }
        }
    }

    static abstract class ActivityContainer {
        protected static final Pattern FULLSCREEN_PATTERN = Pattern.compile("mFullscreen=(\\S+)");
        protected static final Pattern BOUNDS_PATTERN =
                Pattern.compile("mBounds=Rect\\((\\d+), (\\d+) - (\\d+), (\\d+)\\)");
        protected static final Pattern MIN_WIDTH_PATTERN =
                Pattern.compile("mMinWidth=(\\d+)");
        protected static final Pattern MIN_HEIGHT_PATTERN =
                Pattern.compile("mMinHeight=(\\d+)");

        protected boolean mFullscreen;
        protected Rectangle mBounds;
        protected int mMinWidth = -1;
        protected int mMinHeight = -1;

        boolean extractFullscreen(String line) {
            final Matcher matcher = FULLSCREEN_PATTERN.matcher(line);
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
            final Matcher matcher = BOUNDS_PATTERN.matcher(line);
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

        boolean extractMinimalSize(String line) {
            final Matcher minWidthMatcher = MIN_WIDTH_PATTERN.matcher(line);
            final Matcher minHeightMatcher = MIN_HEIGHT_PATTERN.matcher(line);

            if (minWidthMatcher.matches()) {
                log(line);
                mMinWidth = Integer.valueOf(minWidthMatcher.group(1));
            } else if (minHeightMatcher.matches()) {
                log(line);
                mMinHeight = Integer.valueOf(minHeightMatcher.group(1));
            } else {
                return false;
            }
            return true;
        }

        Rectangle getBounds() {
            return mBounds;
        }

        boolean isFullscreen() {
            return mFullscreen;
        }

        int getMinWidth() {
            return mMinWidth;
        }

        int getMinHeight() {
            return mMinHeight;
        }
    }

    static class KeyguardControllerState {
        private static final Pattern NAME_PATTERN = Pattern.compile("KeyguardController:");
        private static final Pattern SHOWING_PATTERN = Pattern.compile("mKeyguardShowing=(\\S+)");
        private static final Pattern OCCLUDED_PATTERN = Pattern.compile("mOccluded=(\\S+)");

        boolean keyguardShowing;
        boolean keyguardOccluded;

        private KeyguardControllerState() {
        }

        static KeyguardControllerState create(LinkedList<String> dump, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = NAME_PATTERN.matcher(line);
            if (!matcher.matches()) {
                // Not KeyguardController
                return null;
            }

            // For the KeyguardController line we just read.
            dump.pop();

            final KeyguardControllerState controller = new KeyguardControllerState();
            controller.extract(dump, exitPatterns);
            return controller;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {

            while (!doneExtracting(dump, exitPatterns)) {
                final String line = dump.pop().trim();

                Matcher matcher = SHOWING_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    final String showingString = matcher.group(1);
                    keyguardShowing = Boolean.valueOf(showingString);
                    log(showingString);
                    continue;
                }

                matcher = OCCLUDED_PATTERN.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    final String occludedString = matcher.group(1);
                    keyguardOccluded = Boolean.valueOf(occludedString);
                    log(occludedString);
                    continue;
                }
            }
        }
    }

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
}
