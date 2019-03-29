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

package android.server.cts.second;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Broadcast receiver that can launch activities. */
public class LaunchBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = LaunchBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();
        final Intent newIntent = new Intent();

        final String targetActivity = extras != null ? extras.getString("target_activity") : null;
        if (targetActivity != null) {
            String packageName = extras.getString("package_name");
            newIntent.setComponent(new ComponentName(packageName,
                    packageName + "." + targetActivity));
        } else {
            newIntent.setClass(context, SecondActivity.class);
        }

        ActivityOptions options = ActivityOptions.makeBasic();
        int displayId = extras.getInt("display_id", -1);
        if (displayId != -1) {
            options.setLaunchDisplayId(displayId);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        } else {
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(newIntent, options.toBundle());
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException launching activity");
        }
    }
}
