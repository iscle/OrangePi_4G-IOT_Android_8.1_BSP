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
package com.android.car;

import android.app.ActivityManager;
import android.app.ActivityManager.StackInfo;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service to monitor AMS for new Activity or Service launching.
 */
public class SystemActivityMonitoringService implements CarServiceBase {

    /**
     * Container to hold info on top task in an Activity stack
     */
    public static class TopTaskInfoContainer {
        public final ComponentName topActivity;
        public final int taskId;
        public final StackInfo stackInfo;

        private TopTaskInfoContainer(ComponentName topActivity, int taskId, StackInfo stackInfo) {
            this.topActivity = topActivity;
            this.taskId = taskId;
            this.stackInfo = stackInfo;
        }

        public boolean isMatching(TopTaskInfoContainer taskInfo) {
            return taskInfo != null
                    && Objects.equals(this.topActivity, taskInfo.topActivity)
                    && this.taskId == taskInfo.taskId
                    && this.stackInfo.userId == taskInfo.stackInfo.userId;
        }

        @Override
        public String toString() {
            return String.format(
                    "TaskInfoContainer [topActivity=%s, taskId=%d, stackId=%d, userId=%d",
                    topActivity, taskId, stackInfo.stackId, stackInfo.userId);
        }
    }

    public interface ActivityLaunchListener {
        /**
         * Notify launch of activity.
         * @param topTask Task information for what is currently launched.
         */
        void onActivityLaunch(TopTaskInfoContainer topTask);
    }

    private static final boolean DBG = false;

    private static final int NUM_MAX_TASK_TO_FETCH = 10;

    private final Context mContext;
    private final IActivityManager mAm;
    private final ProcessObserver mProcessObserver;
    private final TaskListener mTaskListener;

    private final HandlerThread mMonitorHandlerThread;
    private final ActivityMonitorHandler mHandler;

    /** K: stack id, V: top task */
    private final SparseArray<TopTaskInfoContainer> mTopTasks = new SparseArray<>();
    /** K: uid, V : list of pid */
    private final Map<Integer, Set<Integer>> mForegroundUidPids = new ArrayMap<>();
    private int mFocusedStackId = -1;

    /**
     * Temporary container to dispatch tasks for onActivityLaunch. Only used in handler thread.
     * can be accessed without lock. */
    private final List<TopTaskInfoContainer> mTasksToDispatch = new LinkedList<>();
    private ActivityLaunchListener mActivityLaunchListener;

    public SystemActivityMonitoringService(Context context) {
        mContext = context;
        mMonitorHandlerThread = new HandlerThread(CarLog.TAG_AM);
        mMonitorHandlerThread.start();
        mHandler = new ActivityMonitorHandler(mMonitorHandlerThread.getLooper());
        mProcessObserver = new ProcessObserver();
        mTaskListener = new TaskListener();
        mAm = ActivityManager.getService();
        // Monitoring both listeners are necessary as there are cases where one listener cannot
        // monitor activity change.
        try {
            mAm.registerProcessObserver(mProcessObserver);
            mAm.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_AM, "cannot register activity monitoring", e);
            throw new RuntimeException(e);
        }
        updateTasks();
    }

    @Override
    public void init() {
    }

    @Override
    public void release() {
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*SystemActivityMonitoringService*");
        writer.println(" Top Tasks:");
        synchronized (this) {
            for (int i = 0; i < mTopTasks.size(); i++) {
                TopTaskInfoContainer info = mTopTasks.valueAt(i);
                if (info != null) {
                    writer.println(info);
                }
            }
            writer.println(" Foregroud uid-pids:");
            for (Integer key : mForegroundUidPids.keySet()) {
                Set<Integer> pids = mForegroundUidPids.get(key);
                if (pids == null) {
                    continue;
                }
                writer.println("uid:" + key + ", pids:" + Arrays.toString(pids.toArray()));
            }
            writer.println(" focused stack:" + mFocusedStackId);
        }
    }

    /**
     * Block the current task: Launch new activity with given Intent and finish the current task.
     * @param currentTask task to finish
     * @param newActivityIntent Intent for new Activity
     */
    public void blockActivity(TopTaskInfoContainer currentTask, Intent newActivityIntent) {
        mHandler.requestBlockActivity(currentTask, newActivityIntent);
    }

    public List<TopTaskInfoContainer> getTopTasks() {
        LinkedList<TopTaskInfoContainer> tasks = new LinkedList<>();
        synchronized (this) {
            for (int i = 0; i < mTopTasks.size(); i++) {
                tasks.add(mTopTasks.valueAt(i));
            }
        }
        return tasks;
    }

    public boolean isInForeground(int pid, int uid) {
        synchronized (this) {
            Set<Integer> pids = mForegroundUidPids.get(uid);
            if (pids == null) {
                return false;
            }
            if (pids.contains(pid)) {
                return true;
            }
        }
        return false;
    }

    public void registerActivityLaunchListener(ActivityLaunchListener listener) {
        synchronized (this) {
            mActivityLaunchListener = listener;
        }
    }

    private void updateTasks() {
        List<StackInfo> infos;
        try {
            infos = mAm.getAllStackInfos();
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_AM, "cannot getTasks", e);
            return;
        }
        int focusedStackId = -1;
        try {
            focusedStackId = mAm.getFocusedStackId();
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_AM, "cannot getFocusedStackId", e);
            return;
        }
        mTasksToDispatch.clear();
        ActivityLaunchListener listener;
        synchronized (this) {
            listener = mActivityLaunchListener;
            for (StackInfo info : infos) {
                int stackId = info.stackId;
                if (info.taskNames.length == 0 || !info.visible) { // empty stack or not shown
                    mTopTasks.remove(stackId);
                    continue;
                }
                TopTaskInfoContainer newTopTaskInfo = new TopTaskInfoContainer(
                        info.topActivity, info.taskIds[info.taskIds.length - 1], info);
                TopTaskInfoContainer currentTopTaskInfo = mTopTasks.get(stackId);

                // if a new task is added to stack or focused stack changes, should notify
                if (currentTopTaskInfo == null ||
                        !currentTopTaskInfo.isMatching(newTopTaskInfo) ||
                        (focusedStackId == stackId && focusedStackId != mFocusedStackId)) {
                    mTopTasks.put(stackId, newTopTaskInfo);
                    mTasksToDispatch.add(newTopTaskInfo);
                    if (DBG) {
                        Log.i(CarLog.TAG_AM, "New top task: " + newTopTaskInfo);
                    }
                }
            }
            mFocusedStackId = focusedStackId;
        }
        if (listener != null) {
            for (TopTaskInfoContainer topTask : mTasksToDispatch) {
                if (DBG) {
                    Log.i(CarLog.TAG_AM, "activity launched:" + topTask.toString());
                }
                listener.onActivityLaunch(topTask);
            }
        }
    }

    public StackInfo getFocusedStackForTopActivity(ComponentName activity) {
        int focusedStackId = -1;
        try {
            focusedStackId = mAm.getFocusedStackId();
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_AM, "cannot getFocusedStackId", e);
            return null;
        }
        StackInfo focusedStack;
        try {
            focusedStack = mAm.getStackInfo(focusedStackId);
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_AM, "cannot getFocusedStackId", e);
            return null;
        }
        if (focusedStack.taskNames.length == 0) { // nothing in focused stack
            return null;
        }
        ComponentName topActivity = ComponentName.unflattenFromString(
                focusedStack.taskNames[focusedStack.taskNames.length - 1]);
        if (topActivity.equals(activity)) {
            return focusedStack;
        } else {
            return null;
        }
    }

    private void handleForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        synchronized (this) {
            if (foregroundActivities) {
                Set<Integer> pids = mForegroundUidPids.get(uid);
                if (pids == null) {
                    pids = new ArraySet<Integer>();
                    mForegroundUidPids.put(uid, pids);
                }
                pids.add(pid);
            } else {
                doHandlePidGoneLocked(pid, uid);
            }
        }
    }

    private void handleProcessDied(int pid, int uid) {
        synchronized (this) {
            doHandlePidGoneLocked(pid, uid);
        }
    }

    private void doHandlePidGoneLocked(int pid, int uid) {
        Set<Integer> pids = mForegroundUidPids.get(uid);
        if (pids != null) {
            pids.remove(pid);
            if (pids.isEmpty()) {
                mForegroundUidPids.remove(uid);
            }
        }
    }

    private void handleBlockActivity(TopTaskInfoContainer currentTask, Intent newActivityIntent) {
        Log.i(CarLog.TAG_AM, String.format("stopping activity %s with taskid:%d",
                currentTask.topActivity, currentTask.taskId));
        // Put launcher in the activity stack, so that we have something safe to show after the
        // block activity finishes.
        Intent launcherIntent = new Intent();
        launcherIntent.setComponent(ComponentName.unflattenFromString(
                mContext.getString(R.string.defaultHomeActivity)));
        mContext.startActivity(launcherIntent);

        newActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivityAsUser(newActivityIntent,
                new UserHandle(currentTask.stackInfo.userId));
        // now make stack with new activity focused.
        findTaskAndGrantFocus(newActivityIntent.getComponent());
        try {
            mAm.removeTask(currentTask.taskId);
        } catch (RemoteException e) {
            Log.w(CarLog.TAG_AM, "cannot remove task:" + currentTask.taskId, e);
        }
    }

    private void findTaskAndGrantFocus(ComponentName activity) {
        List<StackInfo> infos;
        try {
            infos = mAm.getAllStackInfos();
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_AM, "cannot getTasks", e);
            return;
        }
        for (StackInfo info : infos) {
            if (info.taskNames.length == 0) {
                continue;
            }
            ComponentName topActivity = ComponentName.unflattenFromString(
                    info.taskNames[info.taskNames.length - 1]);
            if (activity.equals(topActivity)) {
                try {
                    mAm.setFocusedStack(info.stackId);
                } catch (RemoteException e) {
                    Log.e(CarLog.TAG_AM, "cannot setFocusedStack to stack:" + info.stackId, e);
                }
                return;
            }
        }
        Log.i(CarLog.TAG_AM, "cannot give focus, cannot find Activity:" + activity);
    }

    private class ProcessObserver extends IProcessObserver.Stub {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            if (DBG) {
                Log.i(CarLog.TAG_AM,
                        String.format("onForegroundActivitiesChanged uid %d pid %d fg %b",
                    uid, pid, foregroundActivities));
            }
            mHandler.requestForegroundActivitiesChanged(pid, uid, foregroundActivities);
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            mHandler.requestProcessDied(pid, uid);
        }
    }

    private class TaskListener extends TaskStackListener {
        @Override
        public void onTaskStackChanged() {
            if (DBG) {
                Log.i(CarLog.TAG_AM, "onTaskStackChanged");
            }
            mHandler.requestUpdatingTask();
        }
    }

    private class ActivityMonitorHandler extends Handler {
        private static final int MSG_UPDATE_TASKS = 0;
        private static final int MSG_FOREGROUND_ACTIVITIES_CHANGED = 1;
        private static final int MSG_PROCESS_DIED = 2;
        private static final int MSG_BLOCK_ACTIVITY = 3;

        private ActivityMonitorHandler(Looper looper) {
            super(looper);
        }

        private void requestUpdatingTask() {
            Message msg = obtainMessage(MSG_UPDATE_TASKS);
            sendMessage(msg);
        }

        private void requestForegroundActivitiesChanged(int pid, int uid,
                boolean foregroundActivities) {
            Message msg = obtainMessage(MSG_FOREGROUND_ACTIVITIES_CHANGED, pid, uid,
                    Boolean.valueOf(foregroundActivities));
            sendMessage(msg);
        }

        private void requestProcessDied(int pid, int uid) {
            Message msg = obtainMessage(MSG_PROCESS_DIED, pid, uid);
            sendMessage(msg);
        }

        private void requestBlockActivity(TopTaskInfoContainer currentTask,
                Intent newActivityIntent) {
            Message msg = obtainMessage(MSG_BLOCK_ACTIVITY,
                    new Pair<TopTaskInfoContainer, Intent>(currentTask, newActivityIntent));
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_TASKS:
                    updateTasks();
                    break;
                case MSG_FOREGROUND_ACTIVITIES_CHANGED:
                    handleForegroundActivitiesChanged(msg.arg1, msg.arg2, (Boolean) msg.obj);
                    updateTasks();
                    break;
                case MSG_PROCESS_DIED:
                    handleProcessDied(msg.arg1, msg.arg2);
                    break;
                case MSG_BLOCK_ACTIVITY:
                    Pair<TopTaskInfoContainer, Intent> pair =
                        (Pair<TopTaskInfoContainer, Intent>) msg.obj;
                    handleBlockActivity(pair.first, pair.second);
                    break;
            }
        }
    }
}
