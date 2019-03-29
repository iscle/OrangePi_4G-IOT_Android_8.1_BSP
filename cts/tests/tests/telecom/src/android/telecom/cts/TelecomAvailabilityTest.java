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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.shouldTestTelecom;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for Telecom service. These tests only run on L+ devices because Telecom was
 * added in L.
 */
public class TelecomAvailabilityTest extends InstrumentationTestCase {
    private static final String TAG = TelecomAvailabilityTest.class.getSimpleName();
    private static final String TELECOM_PACKAGE_NAME = "com.android.server.telecom";
    private static final String TELEPHONY_PACKAGE_NAME = "com.android.phone";

    private PackageManager mPackageManager;
    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mPackageManager = getInstrumentation().getTargetContext().getPackageManager();
    }

    /**
     * Test that the Telecom APK is pre-installed and a system app (FLAG_SYSTEM).
     */
    public void testTelecomIsPreinstalledAndSystem() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        PackageInfo packageInfo = findOnlyTelecomPackageInfo(mPackageManager);
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        assertTrue("Telecom APK must be FLAG_SYSTEM",
                (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
        Log.d(TAG, String.format("Telecom APK is FLAG_SYSTEM %d", applicationInfo.flags));
    }

    /**
     * Test that the Telecom APK is registered to handle CALL intents, and that the Telephony APK
     * is not.
     */
    public void testTelecomHandlesCallIntents() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        final Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", "1234567", null));
        final List<ResolveInfo> activities =
                mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        boolean telecomMatches = false;
        boolean telephonyMatches = false;
        for (ResolveInfo resolveInfo : activities) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }
            if (!telecomMatches
                    && TELECOM_PACKAGE_NAME.equals(resolveInfo.activityInfo.packageName)) {
                telecomMatches = true;
            } else if (!telephonyMatches
                    && TELEPHONY_PACKAGE_NAME.equals(resolveInfo.activityInfo.packageName)) {
                telephonyMatches = true;
            }
        }

        assertTrue("Telecom APK must be registered to handle CALL intents", telecomMatches);
        assertFalse("Telephony APK must NOT be registered to handle CALL intents",
                telephonyMatches);
    }

    public void testTelecomCanManageBlockedNumbers() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        final TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
        final Intent intent = telecomManager.createManageBlockedNumbersIntent();
        assertNotNull(intent);

        final List<ResolveInfo> activities =
                mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        assertEquals(1, activities.size());
        for (ResolveInfo resolveInfo : activities) {
            assertNotNull(resolveInfo.activityInfo);
            assertEquals(TELECOM_PACKAGE_NAME, resolveInfo.activityInfo.packageName);
        }
    }

    /**
     * @return The {@link PackageInfo} of the only app named {@code PACKAGE_NAME}.
     */
    private static PackageInfo findOnlyTelecomPackageInfo(PackageManager packageManager) {
        List<PackageInfo> telecomPackages = findMatchingPackages(packageManager);
        assertEquals(String.format("There must be only one package named %s", TELECOM_PACKAGE_NAME),
                1, telecomPackages.size());
        return telecomPackages.get(0);
    }

    /**
     * Finds all packages that have {@code PACKAGE_NAME} name.
     *
     * @param pm the android package manager
     * @return a list of {@link PackageInfo} records
     */
    private static List<PackageInfo> findMatchingPackages(PackageManager pm) {
        List<PackageInfo> packageInfoList = new ArrayList<PackageInfo>();
        for (PackageInfo info : pm.getInstalledPackages(0)) {
            if (TELECOM_PACKAGE_NAME.equals(info.packageName)) {
                packageInfoList.add(info);
            }
        }
        return packageInfoList;
    }
}
