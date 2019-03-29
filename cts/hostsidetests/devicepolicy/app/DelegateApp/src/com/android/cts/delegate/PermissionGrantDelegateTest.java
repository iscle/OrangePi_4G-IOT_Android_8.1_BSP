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
package com.android.cts.delegate;

import static android.app.admin.DevicePolicyManager.DELEGATION_PERMISSION_GRANT;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
import static com.android.cts.delegate.DelegateTestUtils.assertExpectException;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;

import java.util.List;

/**
 * Test that an app given the {@link DevicePolicyManager#DELEGATION_PERMISSION_GRANT} scope via
 * {@link DevicePolicyManager#setDelegatedScopes} can grant permissions and check permission grant
 * state.
 */
public class PermissionGrantDelegateTest extends InstrumentationTestCase {

    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String TEST_PERMISSION = "android.permission.READ_CONTACTS";

    private DevicePolicyManager mDpm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Context context = getInstrumentation().getContext();
        mDpm = context.getSystemService(DevicePolicyManager.class);
    }

    public void testCannotAccessApis() {
        assertFalse("DelegateApp should not be a permisssion grant delegate",
            amIPermissionGrantDelegate());

        // Exercise setPermissionPolicy.
        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.setPermissionPolicy(null, PERMISSION_POLICY_AUTO_GRANT);
                });
        assertFalse("Permission policy should not have been set",
                PERMISSION_POLICY_AUTO_GRANT == mDpm.getPermissionPolicy(null));

        // Exercise setPermissionGrantState.
        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.setPermissionGrantState(null, TEST_APP_PKG, TEST_PERMISSION,
                            PERMISSION_GRANT_STATE_GRANTED);
                });

        // Exercise getPermissionGrantState.
        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.getPermissionGrantState(null, TEST_APP_PKG, TEST_PERMISSION);
                });
    }

    public void testCanAccessApis() {
        assertTrue("DelegateApp is not a permission grant delegate",
            amIPermissionGrantDelegate());

        // Exercise setPermissionPolicy.
        mDpm.setPermissionPolicy(null, PERMISSION_POLICY_AUTO_DENY);
        assertTrue("Permission policy was not set",
                PERMISSION_POLICY_AUTO_DENY == mDpm.getPermissionPolicy(null));

        // Exercise setPermissionGrantState.
        assertTrue("Permission grant state was not set successfully",
                mDpm.setPermissionGrantState(null, TEST_APP_PKG, TEST_PERMISSION,
                    PERMISSION_GRANT_STATE_DENIED));

        // Exercise getPermissionGrantState.
        assertEquals("Permission grant state is not denied", PERMISSION_GRANT_STATE_DENIED,
                mDpm.getPermissionGrantState(null, TEST_APP_PKG, TEST_PERMISSION));
    }

    private boolean amIPermissionGrantDelegate() {
        final String packageName = getInstrumentation().getContext().getPackageName();
        final List<String> scopes = mDpm.getDelegatedScopes(null, packageName);
        return scopes.contains(DELEGATION_PERMISSION_GRANT);
    }
}
