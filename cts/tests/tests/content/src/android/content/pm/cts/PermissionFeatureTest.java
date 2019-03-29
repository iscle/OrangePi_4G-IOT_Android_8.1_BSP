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

package android.content.pm.cts;

import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

public class PermissionFeatureTest extends AndroidTestCase {
    public void testPermissionRequiredFeatureDefined() {
        PackageManager pm = getContext().getPackageManager();
        assertEquals(PackageManager.PERMISSION_GRANTED,
                pm.checkPermission("android.content.cts.REQUIRED_FEATURE_DEFINED",
                        getContext().getPackageName()));
    }

    public void testPermissionRequiredFeatureUndefined() {
        PackageManager pm = getContext().getPackageManager();
        assertEquals(PackageManager.PERMISSION_DENIED,
                pm.checkPermission("android.content.cts.REQUIRED_FEATURE_UNDEFINED",
                        getContext().getPackageName()));
    }

    public void testPermissionRequiredNotFeatureDefined() {
        PackageManager pm = getContext().getPackageManager();
        assertEquals(PackageManager.PERMISSION_DENIED,
                pm.checkPermission("android.content.cts.REQUIRED_NOT_FEATURE_DEFINED",
                        getContext().getPackageName()));
    }

    public void testPermissionRequiredNotFeatureUndefined() {
        PackageManager pm = getContext().getPackageManager();
        assertEquals(PackageManager.PERMISSION_GRANTED,
                pm.checkPermission("android.content.cts.REQUIRED_NOT_FEATURE_UNDEFINED",
                        getContext().getPackageName()));
    }

    public void testPermissionRequiredMultiDeny() {
        PackageManager pm = getContext().getPackageManager();
        assertEquals(PackageManager.PERMISSION_DENIED,
                pm.checkPermission("android.content.cts.REQUIRED_MULTI_DENY",
                        getContext().getPackageName()));
    }

    public void testPermissionRequiredMultiGrant() {
        PackageManager pm = getContext().getPackageManager();
        assertEquals(PackageManager.PERMISSION_GRANTED,
                pm.checkPermission("android.content.cts.REQUIRED_MULTI_GRANT",
                        getContext().getPackageName()));
    }
}
