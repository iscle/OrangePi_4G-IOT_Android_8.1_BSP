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

package com.android.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * An activity to launch a new activity.
 *
 * <p>In the case when {@link MainActivity} starts a new activity using
 * {@link Activity#startActivity} or {@link Activity#startActivityForResult}, Live TV app is
 * terminated if the new activity crashes. That's because the {@link android.app.ActivityManager}
 * terminates the activity which is just below the crashed activity in the activity stack. To avoid
 * this, we need to locate an additional activity between these activities in the activity stack.
 */
public class LauncherActivity extends Activity {
    private static final String TAG = "LauncherActivity";

    public static final String ERROR_MESSAGE
            = "com.android.tv.LauncherActivity.ErrorMessage";

    private static final int REQUEST_CODE_DEFAULT = 0;
    private static final int REQUEST_START_ACTIVITY = 100;

    private static final String EXTRA_INTENT = "com.android.tv.LauncherActivity.INTENT";
    private static final String EXTRA_REQUEST_RESULT =
            "com.android.tv.LauncherActivity.REQUEST_RESULT";

    /**
     * Starts an activity by calling {@link Activity#startActivity}.
     */
    public static void startActivitySafe(Activity baseActivity, Intent intentToLaunch) {
        // To avoid the app termination when the new activity crashes, LauncherActivity should be
        // started by calling startActivityForResult().
        baseActivity.startActivityForResult(createIntent(baseActivity, intentToLaunch, false),
                REQUEST_CODE_DEFAULT);
    }

    /**
     * Starts an activity by calling {@link Activity#startActivityForResult}.
     *
     * <p>Note: {@code requestCode} should not be 0. The value is reserved for internal use.
     */
    public static void startActivityForResultSafe(Activity baseActivity, Intent intentToLaunch,
            int requestCode) {
        if (requestCode == REQUEST_CODE_DEFAULT) {
            throw new IllegalArgumentException("requestCode should not be 0.");
        }
        // To avoid the app termination when the new activity crashes, LauncherActivity should be
        // started by calling startActivityForResult().
        baseActivity.startActivityForResult(createIntent(baseActivity, intentToLaunch, true),
                requestCode);
    }

    private static Intent createIntent(Context context, Intent intentToLaunch,
            boolean requestResult) {
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.putExtra(EXTRA_INTENT, intentToLaunch);
        if (requestResult) {
            intent.putExtra(EXTRA_REQUEST_RESULT, true);
        }
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We should launch the new activity in onCreate rather than in onStart.
        // That's because it is not guaranteed that onStart is called only once.
        Intent intent = getIntent().getParcelableExtra(EXTRA_INTENT);
        boolean requestResult = getIntent().getBooleanExtra(EXTRA_REQUEST_RESULT, false);
        try {
            if (requestResult) {
                startActivityForResult(intent, REQUEST_START_ACTIVITY);
            } else {
                startActivity(intent);
                setResult(Activity.RESULT_OK);
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Activity not found for " + intent);
            intent.putExtra(ERROR_MESSAGE,
                    getResources().getString(R.string.msg_missing_app));
            setResult(Activity.RESULT_CANCELED, intent);
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        setResult(resultCode, data);
        finish();
    }
}
