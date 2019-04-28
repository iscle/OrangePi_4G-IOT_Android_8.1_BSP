/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.testcommon;

import android.app.Activity;
import android.support.annotation.WorkerThread;
import android.support.test.runner.lifecycle.ActivityLifecycleCallback;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitor;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import android.support.test.runner.lifecycle.Stage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ActivityLifecycleWaiter implements ActivityLifecycleCallback {
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final Activity mActivity;
    private final Stage mStage;

    public ActivityLifecycleWaiter(Activity activity, Stage stage) {
        mActivity = activity;
        mStage = stage;
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(this);
    }

    @Override
    public void onActivityLifecycleChanged(Activity activity, Stage stage) {
        if (activity == mActivity && stage == mStage) {
            mLatch.countDown();
        }
    }

    @WorkerThread
    public void waitForStage() throws InterruptedException {
        waitForStage(5000L);
    }

    @WorkerThread
    public void waitForStage(long millis) throws InterruptedException {
        try {
            mLatch.await(millis, TimeUnit.MILLISECONDS);
        } finally {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(this);
        }
    }
}
