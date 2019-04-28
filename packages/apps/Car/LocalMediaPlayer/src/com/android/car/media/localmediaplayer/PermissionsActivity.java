/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.media.localmediaplayer;

import android.app.Activity;
import android.os.Bundle;

public class PermissionsActivity extends Activity {
    private static final int REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check again, just in case.
        if (!Utils.hasRequiredPermissions(this)) {
            requestPermissions(Utils.PERMISSIONS, REQUEST_CODE);
        } else {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        // The media browser displays an error anyway if it doesn't have the required permissions
        // so we call finish irrespective of the grant result. This whole activity exists just
        // for the purpose of trampolining the permissions request anyway.
        finish();
    }
}
