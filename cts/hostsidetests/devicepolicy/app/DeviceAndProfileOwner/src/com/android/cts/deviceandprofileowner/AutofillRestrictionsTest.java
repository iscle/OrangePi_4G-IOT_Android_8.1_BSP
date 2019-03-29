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

import static android.provider.Settings.Secure.AUTOFILL_SERVICE;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

import android.content.Intent;
import static android.os.UserManager.DISALLOW_AUTOFILL;

public class AutofillRestrictionsTest extends BaseDeviceAdminTest {

    private static final String SERVICE_NAME =
            "com.android.cts.devicepolicy.autofillapp/.SimpleAutofillService";
    private static final String AUTOFILL_PACKAGE_NAME = "com.android.cts.devicepolicy.autofillapp";
    private static final String AUTOFILL_ACTIVITY_NAME = AUTOFILL_PACKAGE_NAME + ".SimpleActivity";

    // Currently, autofill_service is a cloned service, so it's only set in the default user.
    // That might change, so we're using a guard to decide how to set it
    private final boolean USES_CLONED_SETTINGS = true;

    int mUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUserId = getInstrumentation().getContext().getUserId();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            disableService();
        } finally {
            mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, DISALLOW_AUTOFILL);
        }
        super.tearDown();
    }

    public void testDisallowAutofill_allowed() throws Exception {
        enableService();

        final boolean enabledBefore = launchActivityAndGetEnabled();
        assertTrue(enabledBefore);

        mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, DISALLOW_AUTOFILL);

        // Must try a couple times because it will be disabled asynchronously.
        for (int i = 1; i <= 5; i++) {
            final boolean disabledAfter = !launchActivityAndGetEnabled();
            if (disabledAfter) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Not disabled after 2.5s");
    }

    private boolean launchActivityAndGetEnabled() throws Exception {
        final Intent launchIntent = new Intent();
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launchIntent.setClassName(AUTOFILL_PACKAGE_NAME, AUTOFILL_ACTIVITY_NAME);
        final AutofillActivity activity = launchActivity("com.android.cts.deviceandprofileowner",
                AutofillActivity.class, null);
        return activity.isAutofillEnabled();
    }

    private void enableService() throws Exception {
        runShellCommand("settings put secure --user %d %s %s default", mUserId, USER_SETUP_COMPLETE,
                1);

        if (USES_CLONED_SETTINGS) {
            runShellCommand("settings put secure %s %s default", AUTOFILL_SERVICE, SERVICE_NAME);
        } else {
            runShellCommand("settings put --user %d secure %s %s default",
                    mUserId, AUTOFILL_SERVICE, SERVICE_NAME);
        }
        waitForServiceSettingSaved(SERVICE_NAME);
    }

    private void disableService() {
        if (USES_CLONED_SETTINGS) {
            runShellCommand("settings delete secure %s", AUTOFILL_SERVICE);
        } else {
            runShellCommand("settings delete --user %d secure %s", mUserId, AUTOFILL_SERVICE);
        }
    }

    private void waitForServiceSettingSaved(String expected) throws Exception {
        String actual = null;
        // Wait up to 0.5 seconds until setting is saved.
        for (int i = 0; i < 5; i++) {
            if (USES_CLONED_SETTINGS) {
                actual = runShellCommand("settings get secure %s", AUTOFILL_SERVICE);
            } else {
                actual = runShellCommand("settings get --user %d secure %s", mUserId,
                        AUTOFILL_SERVICE);
            }
            if (expected.equals(actual)) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Expected service status for user " + mUserId + ": " + expected
                + "; actual: " + actual + " after 0.5 seconds");
    }
}
