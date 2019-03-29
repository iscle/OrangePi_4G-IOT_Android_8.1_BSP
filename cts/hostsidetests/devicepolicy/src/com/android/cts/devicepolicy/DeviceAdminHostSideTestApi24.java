/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.devicepolicy;

/**
 * BaseDeviceAdminHostSideTest for device admin targeting API level 24.
 */
public class DeviceAdminHostSideTestApi24 extends BaseDeviceAdminHostSideTest {
    @Override
    protected int getTargetApiVersion() {
        return 24;
    }

    /**
     * Device admin must be protected with BIND_DEVICE_ADMIN, if the target SDK >= 24.
     */
    public void testAdminWithNoProtection() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(getDeviceAdminApkFileName(), mUserId);
        setDeviceAdminExpectingFailure(getUnprotectedAdminReceiverComponent(), mUserId,
                "must be protected with android.permission.BIND_DEVICE_ADMIN");
    }
}
