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
package android.app.cts;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.WindowManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class ApplyOverrideConfigurationActivity extends Activity {
    public static final int OVERRIDE_SMALLEST_WIDTH = 99999;

    private CompletableFuture<Configuration> mOnConfigurationChangedFuture = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);

        final Configuration overrideConfig = new Configuration();
        overrideConfig.smallestScreenWidthDp = OVERRIDE_SMALLEST_WIDTH;
        applyOverrideConfiguration(overrideConfig);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        synchronized (this) {
            if (mOnConfigurationChangedFuture != null) {
                mOnConfigurationChangedFuture.complete(new Configuration(newConfig));
                mOnConfigurationChangedFuture = null;
            }
        }
    }

    /**
     * Hands back a Future that will be completed when onConfigurationChanged() is called.
     * It will only report a single call to onConfigurationChanged(). Subsequent calls can be
     * captured by calling this method again. Calling this method will cancel all past
     * Futures received from this method.
     * @return A Future that completes with the configuration passed in to onConfigurationChanged().
     */
    public synchronized Future<Configuration> watchForSingleOnConfigurationChangedCallback() {
        if (mOnConfigurationChangedFuture != null) {
            mOnConfigurationChangedFuture.cancel(true);
        }

        mOnConfigurationChangedFuture = new CompletableFuture<>();
        return mOnConfigurationChangedFuture;
    }
}
