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
package com.android.cts.deviceadminservice;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import android.util.Log;

public class ComponentController extends AndroidTestCase {
    private static final String TAG = "ComponentController";

    private void enableComponent(ComponentName cn, boolean enabled) {
        getContext().getPackageManager().setComponentEnabledSetting(cn,
                (enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                , PackageManager.DONT_KILL_APP);
        Log.i(TAG, "setComponentEnabledSetting: " + cn + " enabled=" + enabled);
    }

    public void testEnableService1() {
        enableComponent(new ComponentName(getContext(), MyService.class), true);
    }

    public void testDisableService1() {
        enableComponent(new ComponentName(getContext(), MyService.class), false);
    }

    public void testEnableService2() {
        enableComponent(new ComponentName(getContext(), MyService2.class), true);
    }

    public void testDisableService2() {
        enableComponent(new ComponentName(getContext(), MyService2.class), false);
    }
}
