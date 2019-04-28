/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.files;

import static com.android.documentsui.base.Shared.DEBUG;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.documentsui.R;

import java.util.List;

/**
 * Provides FilesActivity task grouping support. This allows multiple FilesActivities to be
 * launched (a behavior imparted by way of {@code documentLaunchMode="intoExisting"} and
 * our use of pseudo document {@link Uri}s. This also lets us move an existing task
 * to the foreground when a suitable task exists.
 *
 * Requires that {@code documentLaunchMode="intoExisting"} be set on target activity.
 *
 */
public class LauncherActivity extends Activity {

    public static final String TASK_LABEL_RES = "com.android.documentsui.taskLabel";
    public static final String TASK_ICON_RES = "com.android.documentsui.taskIcon";

    private static final String LAUNCH_CONTROL_AUTHORITY = "com.android.documentsui.launchControl";
    private static final String TAG = "LauncherActivity";

    // Array of boolean extras that should be copied when creating new launch intents.
    // Missing intents will be ignored.
    private static final String[] PERSISTENT_BOOLEAN_EXTRAS = {
        DocumentsContract.EXTRA_SHOW_ADVANCED
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        launch();

        finish();
    }

    private void launch() {
        ActivityManager activities = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        Intent intent = findTask(activities);
        if (intent != null) {
            if (restoreTask(intent)) {
                return;
            } else {
                // We failed to restore the task. It may happen when system was just updated and we
                // moved the location of the targeted activity. Chances is that the rest of tasks
                // can't be restored either, so clean those tasks and start a new one.
                clearTask(activities);
            }
        }

        startTask();
    }

    private @Nullable Intent findTask(ActivityManager activities) {
        List<AppTask> tasks = activities.getAppTasks();
        for (AppTask task : tasks) {
            Intent intent = task.getTaskInfo().baseIntent;
            Uri uri = intent.getData();
            if (isLaunchUri(uri)) {
                return intent;
            }
        }
        return null;
    }

    private void startTask() {
        Intent intent = createLaunchIntent(this);

        intent.putExtra(TASK_LABEL_RES, R.string.launcher_label);
        intent.putExtra(TASK_ICON_RES, R.drawable.launcher_icon);

        // Forward any flags from the original intent.
        intent.setFlags(getIntent().getFlags());
        if (DEBUG) Log.d(TAG, "Starting new task > " + intent.getData());
        startActivity(intent);
    }

    private boolean restoreTask(Intent intent) {
        if (DEBUG) Log.d(TAG, "Restoring existing task > " + intent.getData());
        try {
            // TODO: This doesn't appear to restore a task once it has stopped running.
            startActivity(intent);

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to restore task > " + intent.getData() +
                    ". Clear all existing tasks and start a new one.", e);
        }

        return false;
    }

    private void clearTask(ActivityManager activities) {
        List<AppTask> tasks = activities.getAppTasks();
        for (AppTask task : tasks) {
            task.finishAndRemoveTask();
        }
    }

    public static final Intent createLaunchIntent(Activity activity) {
        Intent intent = new Intent(activity, FilesActivity.class);
        intent.setData(buildLaunchUri());

        // Relay any config overrides bits present in the original intent.
        Intent original = activity.getIntent();
        if (original != null) {
            copyExtras(original, intent);
            if (original.hasExtra(Intent.EXTRA_TITLE)) {
                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        original.getStringExtra(Intent.EXTRA_TITLE));
            }
        }
        return intent;
    }

    private static void copyExtras(Intent src, Intent dest) {
        for (String extra : PERSISTENT_BOOLEAN_EXTRAS) {
            if (src.hasExtra(extra)) {
                dest.putExtra(extra, src.getBooleanExtra(extra, false));
            }
        }
    }

    private static Uri buildLaunchUri() {
        return new Uri.Builder()
                .authority(LAUNCH_CONTROL_AUTHORITY)
                .fragment(String.valueOf(System.currentTimeMillis()))
                .build();
    }

    public static boolean isLaunchUri(@Nullable Uri uri) {
        boolean result = uri != null && LAUNCH_CONTROL_AUTHORITY.equals(uri.getAuthority());
        return result;
    }
}
