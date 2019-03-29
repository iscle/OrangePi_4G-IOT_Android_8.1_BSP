/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.tv.hostside.app2;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class TvViewMonitorActivity extends Activity {
    private static final String TAG = TvViewMonitorActivity.class.getSimpleName();
    private static final String TEST_STRING = "HOST_SIDE_TEST_TV_INTPUT_IS_UPDATED";

    private TvInputManager mManager;
    private LoggingCallback mCallback = new LoggingCallback();

    private boolean hasTvInputFramework() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_TV);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (hasTvInputFramework()) {
            mManager.registerCallback(mCallback, new Handler());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hasTvInputFramework()) {
            mManager.unregisterCallback(mCallback);
        }
    }

    private static class LoggingCallback extends TvInputManager.TvInputCallback {
        @Override
        public void onInputUpdated(String inputId) {
            Log.i(TAG, TEST_STRING);
        }
    }
}
