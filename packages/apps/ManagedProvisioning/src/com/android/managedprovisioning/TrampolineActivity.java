/*
 * Copyright 2015, The Android Open Source Project
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

package com.android.managedprovisioning;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;


/**
 * A trampoline activity that starts a real activity in a new task. This is used so that we can call
 * startActivityForResult() to start the real activity, from a non-activity context. The target
 * activity can the check the caller by {@link Activity#getCallingPackage} or
 * {@link Activity#getCallingActivity}.
 */
public class TrampolineActivity extends Activity {
    private static final String EXTRA_INTENT = "intent";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent target = (Intent) getIntent().getParcelableExtra(EXTRA_INTENT);
        if (target != null) {
            // So that the target activity can get caller information via
            // {@link Activity#getCallingPackage}.
            startActivityForResult(target, 0);
        }
        finish();
    }

    public static void startActivity(Context context, Intent target) {
        context.startActivity(createIntent(context, target));
    }

    public static Intent createIntent(Context context, Intent target) {
        Intent intent = new Intent(context, TrampolineActivity.class);
        intent.putExtra(EXTRA_INTENT, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
