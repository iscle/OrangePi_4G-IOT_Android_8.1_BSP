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
package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Tests for {@link DevicePolicyManager#setScreenCaptureDisabled} and
 * {@link DevicePolicyManager#getScreenCaptureDisabled} APIs.
 */
public class ScreenCaptureDisabledTest extends BaseDeviceAdminTest {
    private final int MAX_ATTEMPTS_COUNT = 20;
    private final int WAIT_IN_MILLISECOND = 500;

    private static final String TAG = "ScreenCaptureDisabledTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testSetScreenCaptureDisabled_false() throws Exception {
        mDevicePolicyManager.setScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT, false);
        assertFalse(mDevicePolicyManager.getScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT));
        assertFalse(mDevicePolicyManager.getScreenCaptureDisabled(null /* any admin */));
    }

    public void testSetScreenCaptureDisabled_true() throws Exception {
        mDevicePolicyManager.setScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT, true);
        assertTrue(mDevicePolicyManager.getScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT));
        assertTrue(mDevicePolicyManager.getScreenCaptureDisabled(null /* any admin */));
    }

    public void testScreenCaptureImpossible() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS_COUNT; i++) {
            Log.d(TAG, "testScreenCaptureImpossible: " + i + " trials");
            Thread.sleep(WAIT_IN_MILLISECOND);
            if (getInstrumentation().getUiAutomation().takeScreenshot() == null) {
                break;
            }
        }
        assertNull(getInstrumentation().getUiAutomation().takeScreenshot());
    }

    public void testScreenCapturePossible() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS_COUNT; i++) {
            Log.d(TAG, "testScreenCapturePossible: " + i + " trials");
            Thread.sleep(WAIT_IN_MILLISECOND);
            if (getInstrumentation().getUiAutomation().takeScreenshot() != null) {
                break;
            }
        }
        assertNotNull(getInstrumentation().getUiAutomation().takeScreenshot());
    }
}
