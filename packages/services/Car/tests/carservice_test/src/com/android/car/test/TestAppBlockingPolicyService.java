/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.test;

import android.car.content.pm.AppBlockingPackageInfo;
import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.CarAppBlockingPolicyService;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.util.Log;

public class TestAppBlockingPolicyService extends CarAppBlockingPolicyService {
    private static final String TAG = TestAppBlockingPolicyService.class.getSimpleName();

    private static TestAppBlockingPolicyService sInstance;
    private static boolean sSetPolicy = true;

    public static synchronized TestAppBlockingPolicyService getInstance() {
        return sInstance;
    }

    public static synchronized void controlPolicySettingFromService(boolean setPolicy) {
        Log.i(TAG, "controlPolicySettingFromService:" + setPolicy);
        sSetPolicy = setPolicy;
    }

    @Override
    protected CarAppBlockingPolicy getAppBlockingPolicy() {
        synchronized (TestAppBlockingPolicyService.class) {
            sInstance = this;
            if (sSetPolicy == false) {
                Log.i(TAG, "getAppBlockingPolicy returning null");
                return null;
            }
        }
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        Signature[] signatures;
        try {
            signatures = pm.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES).signatures;
        } catch (NameNotFoundException e) {
            return null;
        }
        AppBlockingPackageInfo selfInfo = new AppBlockingPackageInfo(packageName, 0, 0, 0,
                signatures, null);
        AppBlockingPackageInfo[] whitelists = new AppBlockingPackageInfo[] { selfInfo };
        CarAppBlockingPolicy policy = new CarAppBlockingPolicy(whitelists, null);
        Log.i(TAG, "getAppBlockingPolicy, passing policy:" + policy);
        return policy;
    }
}
