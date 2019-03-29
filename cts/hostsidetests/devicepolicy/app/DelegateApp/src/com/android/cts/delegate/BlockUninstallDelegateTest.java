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

import static android.app.admin.DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL;
import static com.android.cts.delegate.DelegateTestUtils.assertExpectException;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;

import java.util.List;

/**
 * Test that an app given the {@link DevicePolicyManager#DELEGATION_BLOCK_UNINSTALL} scope via
 * {@link DevicePolicyManager#setDelegatedScopes} can choose packages that are block uninstalled.
 */
public class BlockUninstallDelegateTest extends InstrumentationTestCase {

    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";

    private DevicePolicyManager mDpm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Context context = getInstrumentation().getContext();
        mDpm = context.getSystemService(DevicePolicyManager.class);
    }

    public void testCannotAccessApis() {
        assertFalse("DelegateApp should not be a block uninstall delegate",
            amIBlockUninstallDelegate());

        assertExpectException(SecurityException.class,
                "Caller with uid \\d+ is not a delegate of scope", () -> {
                    mDpm.setUninstallBlocked(null, TEST_APP_PKG, true);
                });
    }

    public void testCanAccessApis() {
        assertTrue("DelegateApp is not a block uninstall delegate",
            amIBlockUninstallDelegate());
        try {
            // Exercise setUninstallBlocked.
            mDpm.setUninstallBlocked(null, TEST_APP_PKG, true);
            assertTrue("App not uninstall blocked", mDpm.isUninstallBlocked(null, TEST_APP_PKG));
        } finally {
            mDpm.setUninstallBlocked(null, TEST_APP_PKG, false);
            assertFalse("App still uninstall blocked", mDpm.isUninstallBlocked(null, TEST_APP_PKG));
        }
    }

    private boolean amIBlockUninstallDelegate() {
        final String packageName = getInstrumentation().getContext().getPackageName();
        final List<String> scopes = mDpm.getDelegatedScopes(null, packageName);
        return scopes.contains(DELEGATION_BLOCK_UNINSTALL);
    }
}
