/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.performanceLaunch;

import android.annotation.Nullable;
import android.app.Activity;
import android.os.Bundle;
import android.os.Trace;

public class ManyConfigResourceActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "onCreate");
        super.onCreate(savedInstanceState);

        for (int i = 0; i < 1000; i++) {
            getResources().getStringArray(R.array.many_configs);
        }
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
    }
}
