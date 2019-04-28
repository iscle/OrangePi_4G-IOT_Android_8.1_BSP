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

package com.android.providers.tv;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.test.IsolatedContext;
import android.test.RenamingDelegatingContext;
import android.test.mock.MockContext;
import android.test.mock.MockPackageManager;

import java.util.Set;
import java.util.HashSet;

class MockTvProviderContext extends IsolatedContext {
    private final Context mBase;
    private final MockPackageManager mMockPackageManager = new MockPackageManager() {
        @Override
        public ServiceInfo getServiceInfo(ComponentName className, int flags) {
            return null;
        }
    };

    private final Set<String> rejectedPermissions = new HashSet<>();

    MockTvProviderContext(ContentResolver resolver, Context base) {
        super(resolver, new RenamingDelegatingContext(new MockContext(), base, "test."));
        mBase = base;
    }

    @Override
    public Resources getResources() {
        return mBase.getResources();
    }

    @Override
    public PackageManager getPackageManager() {
        return mMockPackageManager;
    }

    @Override
    public String getPackageName() {
        return mBase.getPackageName();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return mBase.getApplicationInfo();
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        return rejectedPermissions.contains(permission) ? PackageManager.PERMISSION_DENIED
                : PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return mBase.getSharedPreferences(name, mode);
    }

    public void grantOrRejectPermission(String permission, boolean granted) {
        if (granted) {
            rejectedPermissions.remove(permission);
        } else {
            rejectedPermissions.add(permission);
        }
    }
}
