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

public class ResourceManagerTestActivity2 extends ResourceManagerTestActivityBase {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TAG = "ResourceManagerTestActivity2";

        Log.d(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);

        int result = (allocateCodecs(1 /* max */) == 1) ? RESULT_OK : RESULT_CANCELED;
        finishWithResult(result);
    }
}
