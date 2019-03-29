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


import android.content.cts.MockActivity;

import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.test.AndroidTestCase;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Test instant apps.
 */
public class InstantAppTest extends AndroidTestCase {
    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    private PackageManager mPackageManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPackageManager = getContext().getPackageManager();
    }

    /** Ensure only one resolver is defined */
    public void testInstantAppResolverQuery() {
        final Intent resolverIntent = new Intent(Intent.ACTION_RESOLVE_INSTANT_APP_PACKAGE);
        final int resolveFlags =
                MATCH_DIRECT_BOOT_AWARE
                | MATCH_DIRECT_BOOT_UNAWARE
                | MATCH_SYSTEM_ONLY;
        final List<ResolveInfo> matches =
                mPackageManager.queryIntentServices(resolverIntent, resolveFlags);
        assertTrue(matches == null || matches.size() <= 1);
    }

    /** Ensure only one resolver is defined */
    public void testInstantAppInstallerQuery() {
        final Intent intent = new Intent(Intent.ACTION_INSTALL_INSTANT_APP_PACKAGE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setDataAndType(Uri.fromFile(new File("foo.apk")), PACKAGE_MIME_TYPE);
        final int resolveFlags =
                MATCH_DIRECT_BOOT_AWARE
                | MATCH_DIRECT_BOOT_UNAWARE
                | MATCH_SYSTEM_ONLY;
        final List<ResolveInfo> matches =
                mPackageManager.queryIntentActivities(intent, resolveFlags);
        assertTrue(matches == null || matches.size() <= 1);
    }
}
