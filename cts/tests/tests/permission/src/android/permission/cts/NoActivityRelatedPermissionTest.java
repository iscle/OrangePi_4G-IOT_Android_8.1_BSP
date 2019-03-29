/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.permission.cts;


import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.Suppress;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;

import java.util.List;

/**
 * Verify the Activity related operations require specific permissions.
 */
public class NoActivityRelatedPermissionTest
        extends ActivityInstrumentationTestCase2<PermissionStubActivity> {

    private PermissionStubActivity mActivity;

    public NoActivityRelatedPermissionTest() {
        super("android.permission.cts", PermissionStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    /**
     * Verify that get task requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#GET_TASKS}
     */
    @MediumTest
    public void testGetTask() {
        ActivityManager manager = (ActivityManager) getActivity()
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTasks =  manager.getRunningTasks(10);
        // Current implementation should only return tasks for home and the caller.
        // We'll be done and task this to mean it shouldn't return more than 2.
        assertTrue("Found tasks: " + runningTasks,
                runningTasks == null || runningTasks.size() <= 2);

        List<ActivityManager.RecentTaskInfo> recentTasks = manager.getRecentTasks(10,
                ActivityManager.RECENT_WITH_EXCLUDED);
        // Current implementation should only return tasks for home and the caller. Since there can
        // be multiple home tasks, we remove them from the list and then check that there is one or
        // less task left in the list.
        removeHomeTasks(recentTasks);
        assertTrue("Found tasks: " + recentTasks, recentTasks == null || recentTasks.size() <= 1);
    }

    private void removeHomeTasks(List<ActivityManager.RecentTaskInfo> tasks) {
        for (int i = tasks.size() -1; i >= 0; i--) {
            ActivityManager.RecentTaskInfo task = tasks.get(i);
            if (task.baseIntent != null && isHomeIntent(task.baseIntent)) {
                tasks.remove(i);
            }
        }
    }

    private boolean isHomeIntent(Intent intent) {
        return Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME)
                && intent.getCategories().size() == 1
                && intent.getData() == null
                && intent.getType() == null;
    }
}
