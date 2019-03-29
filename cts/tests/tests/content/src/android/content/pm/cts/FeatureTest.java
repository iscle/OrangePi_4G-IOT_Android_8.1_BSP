/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.content.pm.cts;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureGroupInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.Arrays;
import java.util.Comparator;

public class FeatureTest extends AndroidTestCase {

    private static final String TAG = "FeatureTest";

    private PackageManager mPackageManager;
    private ActivityManager mActivityManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPackageManager = getContext().getPackageManager();
        mActivityManager = (ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
    }

    public void testNoManagedUsersIfLowRamDevice() {
        if (mPackageManager == null || mActivityManager == null) {
            Log.w(TAG, "Skipping testNoManagedUsersIfLowRamDevice");
            return;
        }
        if (mActivityManager.isLowRamDevice()) {
            assertFalse(mPackageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS));
        }
    }
}
