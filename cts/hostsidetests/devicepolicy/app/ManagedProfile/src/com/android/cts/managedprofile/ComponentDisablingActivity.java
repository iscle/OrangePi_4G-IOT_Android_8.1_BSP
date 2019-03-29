/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.managedprofile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

/**
 * Class that disables a given component for the user it's running in.
 */
public class ComponentDisablingActivity extends Activity {

    private static final String TAG = ComponentDisablingActivity.class.getName();
    public static final String EXTRA_PACKAGE = "extra-package";
    public static final String EXTRA_CLASS_NAME = "extra-class-name";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String extraClassName = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        String extraPackage = getIntent().getStringExtra(EXTRA_PACKAGE);

        Log.i(TAG, "Disabling: " + extraPackage + "/" + extraClassName + " for user "
                + Process.myUserHandle());
        PackageManager packageManager = getPackageManager();
        packageManager.setComponentEnabledSetting(new ComponentName(extraPackage, extraClassName),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}