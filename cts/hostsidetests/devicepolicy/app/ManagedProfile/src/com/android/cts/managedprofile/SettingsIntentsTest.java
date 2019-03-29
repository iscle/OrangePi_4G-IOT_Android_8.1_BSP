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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.test.ActivityInstrumentationTestCase2;
import android.provider.Settings;


/**
 * Tests that make sure that some core application intents as described in Compatibility Definition
 * Document are handled within a managed profile.
 * Note that OEMs can replace the Settings apps, so we we can at most check if the intent resolves.
 */
public class SettingsIntentsTest extends ActivityInstrumentationTestCase2<TestActivity> {

    private PackageManager mPackageManager;

    public SettingsIntentsTest() {
        super(TestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPackageManager = getActivity().getPackageManager();
    }

    public void testSettings() {
        assertNotNull(mPackageManager.resolveActivity(
                new Intent(Settings.ACTION_SETTINGS), 0 /* flags */));
    }

    public void testAccounts() {
        assertNotNull(mPackageManager.resolveActivity(
                new Intent(Settings.ACTION_SYNC_SETTINGS), 0 /* flags */));
    }

    public void testApps() {
        assertNotNull(mPackageManager.resolveActivity(
                new Intent(Settings.ACTION_APPLICATION_SETTINGS), 0 /* flags */));
    }

    public void testSecurity() {
        // This leads to device administrators, screenlock etc.
        assertNotNull(mPackageManager.resolveActivity(
                new Intent(Settings.ACTION_SECURITY_SETTINGS), 0 /* flags */));
    }

    public void testNfc() {
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            assertNotNull(mPackageManager.resolveActivity(
                    new Intent(Settings.ACTION_NFC_SETTINGS), 0 /* flags */));
        }
    }

    public void testLocation() {
        assertNotNull(mPackageManager.resolveActivity(
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0 /* flags */));
    }
}
