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


import com.android.cts.managedprofile.BaseManagedProfileTest.BasicAdminReceiver;

import org.junit.Ignore;

/**
 * Test wipeData() for use in managed profile. If called from a managed profile, wipeData() should
 * remove the current managed profile. Also, no erasing of external storage should be allowed.
 */
public class WipeDataTest extends BaseManagedProfileTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Make sure we are running in a managed profile, otherwise risk wiping the primary user's
        // data.
        assertTrue(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT));
        assertTrue(mDevicePolicyManager.isProfileOwnerApp(ADMIN_RECEIVER_COMPONENT.getPackageName()));
    }

    public void testWipeData() throws InterruptedException {
        mDevicePolicyManager.wipeData(0);
        // the test that the profile will indeed be removed is done in the host.
    }
}
