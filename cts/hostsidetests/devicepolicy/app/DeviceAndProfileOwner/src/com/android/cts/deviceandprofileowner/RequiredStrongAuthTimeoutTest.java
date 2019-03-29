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

package com.android.cts.deviceandprofileowner;

import android.content.ComponentName;

import java.util.concurrent.TimeUnit;

public class RequiredStrongAuthTimeoutTest extends BaseDeviceAdminTest {

    private static final long DEFAULT_STRONG_AUTH_TIMEOUT_MS = TimeUnit.HOURS.toMillis(72);
    private static final long MINIMUM_STRONG_AUTH_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1);
    private static final long ONE_MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long MIN_PLUS_ONE_MINUTE = MINIMUM_STRONG_AUTH_TIMEOUT_MS + ONE_MINUTE;
    private static final long MAX_MINUS_ONE_MINUTE = DEFAULT_STRONG_AUTH_TIMEOUT_MS - ONE_MINUTE;

    private static final ComponentName ADMIN = ADMIN_RECEIVER_COMPONENT;

    public void testSetRequiredStrongAuthTimeout() throws Exception {
        // aggregation should be the default if unset by any admin
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(null),
                DEFAULT_STRONG_AUTH_TIMEOUT_MS);

        // admin not participating by default
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN), 0);

        //clamping from the top
        mDevicePolicyManager.setRequiredStrongAuthTimeout(ADMIN,
                DEFAULT_STRONG_AUTH_TIMEOUT_MS + ONE_MINUTE);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN),
                DEFAULT_STRONG_AUTH_TIMEOUT_MS);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(null),
                DEFAULT_STRONG_AUTH_TIMEOUT_MS);

        // 0 means the admin is not participating, so default should be returned
        mDevicePolicyManager.setRequiredStrongAuthTimeout(ADMIN, 0);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN), 0);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(null),
                DEFAULT_STRONG_AUTH_TIMEOUT_MS);

        // clamping from the bottom
        mDevicePolicyManager.setRequiredStrongAuthTimeout(ADMIN,
                MINIMUM_STRONG_AUTH_TIMEOUT_MS - ONE_MINUTE);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN),
                MINIMUM_STRONG_AUTH_TIMEOUT_MS);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(null),
                MINIMUM_STRONG_AUTH_TIMEOUT_MS);

        // values within range
        mDevicePolicyManager.setRequiredStrongAuthTimeout(ADMIN, MIN_PLUS_ONE_MINUTE);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN), MIN_PLUS_ONE_MINUTE);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(null), MIN_PLUS_ONE_MINUTE);

        mDevicePolicyManager.setRequiredStrongAuthTimeout(ADMIN, MAX_MINUS_ONE_MINUTE);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN),
                MAX_MINUS_ONE_MINUTE);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(null), MAX_MINUS_ONE_MINUTE);

        // reset to default
        mDevicePolicyManager.setRequiredStrongAuthTimeout(ADMIN, 0);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN), 0);
        assertEquals(mDevicePolicyManager.getRequiredStrongAuthTimeout(null),
                DEFAULT_STRONG_AUTH_TIMEOUT_MS);

        // negative value
        try {
            mDevicePolicyManager.setRequiredStrongAuthTimeout(ADMIN, -ONE_MINUTE);
            fail("Didn't throw IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
        }
    }
}
