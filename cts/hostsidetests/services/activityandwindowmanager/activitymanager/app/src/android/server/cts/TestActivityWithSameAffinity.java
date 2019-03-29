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

package android.server.cts;

import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

public class TestActivityWithSameAffinity extends TestActivity {

    private static final String TAG = TestActivityWithSameAffinity.class.getSimpleName();

    // Calls enterPictureInPicture() on creation
    private static final String EXTRA_ENTER_PIP = "enter_pip";
    // Starts the activity (component name) provided by the value at the end of onCreate
    private static final String EXTRA_START_ACTIVITY = "start_activity";
    // Finishes the activity at the end of onResume (after EXTRA_START_ACTIVITY is handled)
    private static final String EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Enter picture in picture if requested
        if (getIntent().hasExtra(EXTRA_ENTER_PIP)) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        }

        // Launch a new activity if requested
        String launchActivityComponent = getIntent().getStringExtra(EXTRA_START_ACTIVITY);
        if (launchActivityComponent != null) {
            Intent launchIntent = new Intent();
            launchIntent.setComponent(ComponentName.unflattenFromString(launchActivityComponent));
            startActivity(launchIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Finish self if requested
        if (getIntent().hasExtra(EXTRA_FINISH_SELF_ON_RESUME)) {
            finish();
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}
