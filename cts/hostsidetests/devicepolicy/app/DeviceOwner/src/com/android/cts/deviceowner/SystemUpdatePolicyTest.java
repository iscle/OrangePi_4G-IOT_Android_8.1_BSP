/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.compatibility.common.util.ApiLevelUtil;

import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link SystemUpdatePolicy}, {@link DevicePolicyManager#setSystemUpdatePolicy} and
 * {@link DevicePolicyManager#getSystemUpdatePolicy}
 */
public class SystemUpdatePolicyTest extends BaseDeviceOwnerTest {

    private static final int TIMEOUT_MS = 20_000;

    private final Semaphore mPolicyChangedSemaphore = new Semaphore(0);
    private final BroadcastReceiver policyChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            mPolicyChangedSemaphore.release();
        }
    };
    private boolean mHasFeature;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHasFeature = ApiLevelUtil.isAfter(Build.VERSION_CODES.LOLLIPOP_MR1);

        if (mHasFeature) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(DevicePolicyManager.ACTION_SYSTEM_UPDATE_POLICY_CHANGED);
            mContext.registerReceiver(policyChangedReceiver, filter);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            mContext.unregisterReceiver(policyChangedReceiver);
            mDevicePolicyManager.setSystemUpdatePolicy(getWho(), null);
        }
        super.tearDown();
    }

    public void testSetEmptytInstallPolicy() {
        if (!mHasFeature) {
            return;
        }
        testPolicy(null);
    }

    public void testSetAutomaticInstallPolicy() {
        if (!mHasFeature) {
            return;
        }
        testPolicy(SystemUpdatePolicy.createAutomaticInstallPolicy());
    }

    public void testSetWindowedInstallPolicy() {
        if (!mHasFeature) {
            return;
        }
        testPolicy(SystemUpdatePolicy.createWindowedInstallPolicy(0, 720));
    }

    public void testSetPostponeInstallPolicy() {
        if (!mHasFeature) {
            return;
        }
        testPolicy(SystemUpdatePolicy.createPostponeInstallPolicy());
    }

    public void testShouldFailInvalidWindowPolicy() throws Exception {
        if (!mHasFeature) {
            return;
        }
        try {
            SystemUpdatePolicy.createWindowedInstallPolicy(24 * 60 + 1, 720);
            fail("Invalid window start should not be accepted.");
        } catch (IllegalArgumentException expected) { }
        try {
            SystemUpdatePolicy.createWindowedInstallPolicy(-1, 720);
            fail("Invalid window start should not be accepted.");
        } catch (IllegalArgumentException expected) { }
        try {
            SystemUpdatePolicy.createWindowedInstallPolicy(0, 24 * 60 + 1);
            fail("Invalid window end should not be accepted.");
        } catch (IllegalArgumentException expected) { }
        try {
            SystemUpdatePolicy.createWindowedInstallPolicy(0, -1);
            fail("Invalid window end should not be accepted.");
        } catch (IllegalArgumentException expected) { }
    }

    private void testPolicy(SystemUpdatePolicy policy) {
        mDevicePolicyManager.setSystemUpdatePolicy(getWho(), policy);
        waitForBroadcast();
        SystemUpdatePolicy newPolicy = mDevicePolicyManager.getSystemUpdatePolicy();
        if (policy == null) {
            assertNull(newPolicy);
        } else {
            assertNotNull(newPolicy);
            assertEquals(policy.toString(), newPolicy.toString());
            assertEquals(policy.getPolicyType(), newPolicy.getPolicyType());
            if (policy.getPolicyType() == SystemUpdatePolicy.TYPE_INSTALL_WINDOWED) {
                assertEquals(policy.getInstallWindowStart(), newPolicy.getInstallWindowStart());
                assertEquals(policy.getInstallWindowEnd(), newPolicy.getInstallWindowEnd());
            }
        }
    }
    private void waitForBroadcast() {
        try {
            assertTrue("Timeout while waiting for broadcast.",
                    mPolicyChangedSemaphore.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("Interrupted while waiting for broadcast.");
        }
    }
}
