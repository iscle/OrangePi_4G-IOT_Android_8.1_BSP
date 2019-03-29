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

package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.PersistableBundle;

import java.util.List;


public class TrustAgentInfoTest extends BaseDeviceAdminTest {
    private static final String BUNDLE_KEY = "testing";
    private static final String BUNDLE_VALUE = "value";
    private static final PersistableBundle CONFIG = new PersistableBundle();
    private static final ComponentName TRUST_AGENT_COMPONENT =
            new ComponentName("com.trustagent", "com.trustagent.xxx");
    private static final ComponentName NOT_CONFIGURED_TRUST_AGENT_COMPONENT =
            new ComponentName("com.trustagent.not_configured", "com.trustagent.xxx");

    static {
        CONFIG.putString(BUNDLE_KEY, BUNDLE_VALUE);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.setTrustAgentConfiguration(ADMIN_RECEIVER_COMPONENT,
                TRUST_AGENT_COMPONENT, null);
        assertNull(mDevicePolicyManager.getTrustAgentConfiguration(
                ADMIN_RECEIVER_COMPONENT, TRUST_AGENT_COMPONENT));
        mDevicePolicyManager.setKeyguardDisabledFeatures(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE);
        super.tearDown();
    }

    public void testSetAndGetTrustAgentConfiguration() {
        // Set the config
        mDevicePolicyManager.setTrustAgentConfiguration(ADMIN_RECEIVER_COMPONENT,
                TRUST_AGENT_COMPONENT, CONFIG);
        // Should able to get the config just set.
        List<PersistableBundle> configs =
                mDevicePolicyManager.getTrustAgentConfiguration(
                        ADMIN_RECEIVER_COMPONENT, TRUST_AGENT_COMPONENT);
        assertPersistableBundleList(configs);
        // Try to get the config of an trust agent that is not configured.
        configs = mDevicePolicyManager
                .getTrustAgentConfiguration(null, NOT_CONFIGURED_TRUST_AGENT_COMPONENT);
        assertNull(configs);
        // Try to get the aggregated list when trust agents is not disabled.
        configs = mDevicePolicyManager
                .getTrustAgentConfiguration(null, TRUST_AGENT_COMPONENT);
        assertNull(configs);
        // Try to get the aggregated list when trust agents is disabled.
        mDevicePolicyManager.setKeyguardDisabledFeatures(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS);
        // Try to get the aggregated list of a trust agent that is not configured.
        configs = mDevicePolicyManager
                .getTrustAgentConfiguration(null, NOT_CONFIGURED_TRUST_AGENT_COMPONENT);
        assertNull(configs);
        configs = mDevicePolicyManager
                .getTrustAgentConfiguration(null, TRUST_AGENT_COMPONENT);
        assertPersistableBundleList(configs);
    }

    private static void assertPersistableBundleList(List<PersistableBundle> actual) {
        assertNotNull(actual);
        assertEquals(1, actual.size());
        PersistableBundle bundle = actual.get(0);
        assertEquals(BUNDLE_VALUE, bundle.getString(BUNDLE_KEY));
    }

}
