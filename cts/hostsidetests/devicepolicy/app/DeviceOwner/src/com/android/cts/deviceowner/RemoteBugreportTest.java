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
package com.android.cts.deviceowner;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.test.InstrumentationTestCase;

/**
 * Test class for remote bugreports.
 */
public class RemoteBugreportTest extends BaseDeviceOwnerTest {

    /**
     * Test: remote bugreport flow can only be started if there is only one user on the device or
     * all existing secondary users/profiles are affiliated.
     */
    public void testRequestBugreportThrowsSecurityException() {
        try {
            mDevicePolicyManager.requestBugreport(getWho());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

}
