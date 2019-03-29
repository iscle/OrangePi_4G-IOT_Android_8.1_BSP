/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.server.cts.tools;

import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;

import android.app.ActivityOptions;
import android.content.Intent;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.server.cts.TestActivity;
import android.util.Log;

/** Utility class which contains common code for launching activities. */
public class ActivityLauncher {
    private static final String TAG = ActivityLauncher.class.getSimpleName();

    public static void launchActivityFromExtras(final Context context, Bundle extras) {
        if (extras == null || !extras.getBoolean("launch_activity")) {
            return;
        }

        Log.i(TAG, "launchActivityFromExtras: extras=" + extras);

        final Intent newIntent = new Intent();
        final String targetActivity = extras.getString("target_activity");
        if (targetActivity != null) {
            final String extraPackageName = extras.getString("package_name");
            final String packageName = extraPackageName != null ? extraPackageName
                    : context.getApplicationContext().getPackageName();
            newIntent.setComponent(new ComponentName(packageName,
                    packageName + "." + targetActivity));
        } else {
            newIntent.setClass(context, TestActivity.class);
        }

        if (extras.getBoolean("launch_to_the_side")) {
            newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT);
            if (extras.getBoolean("random_data")) {
                final Uri data = new Uri.Builder()
                        .path(String.valueOf(System.currentTimeMillis()))
                        .build();
                newIntent.setData(data);
            }
        }
        if (extras.getBoolean("multiple_task")) {
            newIntent.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        if (extras.getBoolean("new_task")) {
            newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        }

        if (extras.getBoolean("reorder_to_front")) {
            newIntent.addFlags(FLAG_ACTIVITY_REORDER_TO_FRONT);
        }

        ActivityOptions options = null;
        final int displayId = extras.getInt("display_id", -1);
        if (displayId != -1) {
            options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        }

        try {
            context.startActivity(newIntent, options != null ? options.toBundle() : null);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException launching activity");
        }
    }
}
