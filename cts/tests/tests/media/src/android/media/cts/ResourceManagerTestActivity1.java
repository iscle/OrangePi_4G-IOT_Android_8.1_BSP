/*
 * Copyright 2015 The Android Open Source Project
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

package android.media.cts;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class ResourceManagerTestActivity1 extends ResourceManagerTestActivityBase {
    private static final int MAX_INSTANCES = 32;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TAG = "ResourceManagerTestActivity1";

        Log.d(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);
        moveTaskToBack(true);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mWaitForReclaim = extras.getBoolean("wait-for-reclaim", mWaitForReclaim);
        }

        if (allocateCodecs(MAX_INSTANCES) == MAX_INSTANCES) {
            // haven't reached the limit with MAX_INSTANCES, no need to wait for reclaim exception.
            mWaitForReclaim = false;
        }

        useCodecs();
    }
}
