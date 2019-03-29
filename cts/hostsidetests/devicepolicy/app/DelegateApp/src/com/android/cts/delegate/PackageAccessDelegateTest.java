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

import static android.app.admin.DevicePolicyManager.DELEGATION_PACKAGE_ACCESS;
import static com.android.cts.delegate.DelegateTestUtils.assertExpectException;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;

import java.util.Arrays;
import java.util.List;

/**
 * Test that an app given the {@link DevicePolicyManager#DELEGATION_PACKAGE_ACCESS} scope via
 * {@link DevicePolicyManager#setDelegatedScopes} can manage package hide and suspend status.
 */
public class PackageAccessDelegateTest extends InstrumentationTestCase {

    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";

    private DevicePolicyManager mDpm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Context context = getInstrumentation().getContext();
        mDpm = context.getSystemService(DevicePolicyManager.class);
    }

    public void testCannotAccessApis() throws NameNotFoundException {
        assertFalse("DelegateApp should not be a package access delegate",
            amIPackageAccessDelegate());

        // Exercise isApplicationHidden.
        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.isApplicationHidden(null, TEST_APP_PKG);
                });

        // Exercise setApplicationHidden.
        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.setApplicationHidden(null, TEST_APP_PKG, true /* hide */);
                });

        // Exercise isPackageSuspended.
        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.isPackageSuspended(null, TEST_APP_PKG);
                });

        // Exercise setPackagesSuspended.
        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.setPackagesSuspended(null, new String[] {TEST_APP_PKG}, true /* suspend */);
                });
    }

    public void testCanAccessApis() throws NameNotFoundException {
        assertTrue("DelegateApp is not a package access delegate", amIPackageAccessDelegate());

        // Exercise isApplicationHidden.
        assertFalse("Package should not be hidden", mDpm.isApplicationHidden(null, TEST_APP_PKG));

        // Exercise setApplicationHidden.
        assertTrue("Package not hidden successfully",
                mDpm.setApplicationHidden(null, TEST_APP_PKG, true /* hide */));
        assertTrue("Package should be hidden", mDpm.isApplicationHidden(null, TEST_APP_PKG));

        // Exercise isPackageSuspended.
        assertFalse("Package should not be suspended", mDpm.isPackageSuspended(null, TEST_APP_PKG));

        // Exercise setPackagesSuspended.
        String[] suspended = mDpm.setPackagesSuspended(null, new String[] {TEST_APP_PKG},
                true /* suspend */);
        assertTrue("Package not suspended successfully", suspended.length == 0);
        assertTrue("Package should be suspended", mDpm.isPackageSuspended(null, TEST_APP_PKG));
    }

    private boolean amIPackageAccessDelegate() {
        final String packageName = getInstrumentation().getContext().getPackageName();
        final List<String> scopes = mDpm.getDelegatedScopes(null, packageName);
        return scopes.contains(DELEGATION_PACKAGE_ACCESS);
    }
}
