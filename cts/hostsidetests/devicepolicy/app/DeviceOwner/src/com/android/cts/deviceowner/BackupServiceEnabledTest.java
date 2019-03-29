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
package com.android.cts.deviceowner;

public class BackupServiceEnabledTest extends BaseDeviceOwnerTest {

    /**
     * Test: Test enabling backup service. This test should be executed after installing a device
     * owner so that we check that backup service is not enabled by default.
     * This test will keep backup service disabled after its execution.
     */
    public void testEnablingAndDisablingBackupService() {
        assertFalse(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
        mDevicePolicyManager.setBackupServiceEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
        mDevicePolicyManager.setBackupServiceEnabled(getWho(), false);
        assertFalse(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
    }
}
