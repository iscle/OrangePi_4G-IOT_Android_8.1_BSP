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
 * limitations under the License.
 */

package com.android.cts.verifier.admin.tapjacking;

import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;

import com.android.cts.verifier.R;


public class OverlayingActivity extends Activity {

    private static final long ACTIVITY_TIMEOUT_ON_WATCH = 10_000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.da_tapjacking_overlay_activity);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.flags = FLAG_LAYOUT_NO_LIMITS | FLAG_NOT_TOUCH_MODAL | FLAG_NOT_TOUCHABLE
                | FLAG_KEEP_SCREEN_ON;
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            getWindow().getDecorView().postDelayed(() -> OverlayingActivity.this.finish(),
                    ACTIVITY_TIMEOUT_ON_WATCH);
        }
    }
}
