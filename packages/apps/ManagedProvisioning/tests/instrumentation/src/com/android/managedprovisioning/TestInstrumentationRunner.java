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

package com.android.managedprovisioning;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.test.runner.AndroidJUnitRunner;
import android.util.ArrayMap;

import android.view.WindowManager;
import java.util.Map;

public class TestInstrumentationRunner extends AndroidJUnitRunner {
    // hard-coded package name as context.getPackageName() provides ManagedProvisioning app name
    // instead of test package name
    public static final String TEST_PACKAGE_NAME = "com.android.managedprovisioning.tests";

    private static final String TAG = "TestInstrumentationRunner";
    private static final Map<String, OnActivityCreatedCallback> sReplacedActivityMap =
            new ArrayMap();

    public static void registerReplacedActivity(Class<?> oldActivity,
            OnActivityCreatedCallback onActivityCreatedCallback) {
        sReplacedActivityMap.put(oldActivity.getCanonicalName(), onActivityCreatedCallback);
    }

    public static void registerReplacedActivity(Class<?> oldActivity,
            Class<? extends Activity> newActivity) {
        registerReplacedActivity(oldActivity,
                (classLoader, className, intent) -> newActivity.newInstance());
    }

    public static void unregisterReplacedActivity(Class<?> oldActivity) {
        sReplacedActivityMap.remove(oldActivity.getCanonicalName());
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        OnActivityCreatedCallback callback = sReplacedActivityMap.get(className);
        if (callback != null) {
            return callback.createActivity(cl, className, intent);
        } else {
            return super.newActivity(cl, className, intent);
        }
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Application app = super.newApplication(cl, className, context);
        app.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {
                // Show activity on top of keyguard
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
                // Turn on screen to prevent activity being paused by system. See b/31262906
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {}

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
        return app;
    }

    public interface OnActivityCreatedCallback {
        Activity createActivity(ClassLoader cl, String className, Intent intent)
                throws IllegalAccessException, InstantiationException;
    }
}
