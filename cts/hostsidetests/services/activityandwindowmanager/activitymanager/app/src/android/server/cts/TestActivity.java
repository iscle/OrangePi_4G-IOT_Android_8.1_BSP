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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

public class TestActivity extends AbstractLifecycleLogActivity {

    private static final String TAG = TestActivity.class.getSimpleName();

    // Sets the fixed orientation (can be one of {@link ActivityInfo.ScreenOrientation}
    private static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";

    // Finishes the activity
    private static final String ACTION_FINISH_SELF = "android.server.cts.TestActivity.finish_self";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction().equals(ACTION_FINISH_SELF)) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set the fixed orientation if requested
        if (getIntent().hasExtra(EXTRA_FIXED_ORIENTATION)) {
            final int ori = Integer.parseInt(getIntent().getStringExtra(EXTRA_FIXED_ORIENTATION));
            setRequestedOrientation(ori);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mReceiver, new IntentFilter(ACTION_FINISH_SELF));
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Configuration config = getResources().getConfiguration();
        dumpDisplaySize(config);
        dumpConfiguration(config);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dumpDisplaySize(newConfig);
        dumpConfiguration(newConfig);
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}
