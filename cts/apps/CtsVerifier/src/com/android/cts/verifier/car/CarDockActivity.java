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

package com.android.cts.verifier.car;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.android.cts.verifier.R;

/**
 * Dummy activity which notifies {@link CarDockTestActivity} that the CAR_DOCK application
 * successfully opened.
 */
public class CarDockActivity extends Activity {
    public static Runnable sOnHomePressedRunnable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.car_dock_main);
        Toast.makeText(this, "CAR_DOCK app started from entering car mode!",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    /**
     * Pressing the home button while already in this app will trigger this callback.
     */
    protected void onNewIntent(Intent intent) {
        if (sOnHomePressedRunnable != null) {
            sOnHomePressedRunnable.run();
            sOnHomePressedRunnable = null;
            Toast.makeText(this, "CAR_DOCK app started from home button press!",
                    Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
