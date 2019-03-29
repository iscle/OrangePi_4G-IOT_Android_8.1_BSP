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

package com.android.cts.managedprofile;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.PersistableBundle;
import android.test.MoreAsserts;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.cts.managedprofile.TrustAgentInfoTest.AssertConfigMode.*;

public class TrustAgentInfoTest extends BaseManagedProfileTest {
    private static final String BUNDLE_KEY = "testing";
    private static final String BUNDLE_PARENT_VALUE = "parent";
    private static final String BUNDLE_CHILD_VALUE = "child";
    private static final PersistableBundle CHILD_CONFIG = new PersistableBundle();
    private static final PersistableBundle PARENT_CONFIG = new PersistableBundle();
    private static final ComponentName TRUST_AGENT_COMPONENT =
            new ComponentName("com.trustagent", "com.trustagent.xxx");

    static {
        CHILD_CONFIG.putString(BUNDLE_KEY, BUNDLE_CHILD_VALUE);
        PARENT_CONFIG.putString(BUNDLE_KEY, BUNDLE_PARENT_VALUE);
    }

    enum AssertConfigMode {
        ASSERT_PARENT_CONFIG, ASSERT_CHILD_CONFIG, ASSERT_BOTH
    }

    @Override
    protected void tearDown() throws Exception {
        clearTrustAgentConfiguration(true /* isParent */);
        clearTrustAgentConfiguration(false /* isParent */);
        enableTrustAgents(true /* isParent */);
        enableTrustAgents(false /* isParent */);
        super.tearDown();
    }

    public void testSetAndGetTrustAgentConfiguration_child() {
        setAndGetTrustAgentConfigurationInternal(false /* isParent */);
    }

    public void testSetAndGetTrustAgentConfiguration_parent() {
        setAndGetTrustAgentConfigurationInternal(true /* isParent */);
    }

    public void testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndUnified() {
        // Set both trust agents.
        setTrustAgentConfiguration(false /* isParent */);
        setTrustAgentConfiguration(true /* isParent */);

        // Case when trust agents is not disabled
        List<PersistableBundle> parentConfig =
                mParentDevicePolicyManager.getTrustAgentConfiguration(
                        null, TRUST_AGENT_COMPONENT);
        List<PersistableBundle> childConfig = mDevicePolicyManager.getTrustAgentConfiguration(
                null, TRUST_AGENT_COMPONENT);
        assertNull(parentConfig);
        assertNull(childConfig);

        // Case when trust agents is disabled
        disableTrustAgents(false /* isParent */);
        disableTrustAgents(true /* isParent */);

        parentConfig = mParentDevicePolicyManager.getTrustAgentConfiguration(
                null, TRUST_AGENT_COMPONENT);
        childConfig = mDevicePolicyManager.getTrustAgentConfiguration(
                null, TRUST_AGENT_COMPONENT);
        assertPersistableBundleListEquals(ASSERT_BOTH, parentConfig);
        assertPersistableBundleListEquals(ASSERT_BOTH, childConfig);
    }

    public void testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndNonUnified() {
        // Precondition: separate challenge for the managed profile should have been enabled.

        // Set both trust agents.
        setTrustAgentConfiguration(false /* isParent */);
        setTrustAgentConfiguration(true /* isParent */);
        disableTrustAgents(false /* isParent */);
        disableTrustAgents(true /* isParent */);

        List<PersistableBundle> parentConfig =
                mParentDevicePolicyManager.getTrustAgentConfiguration(
                        null, TRUST_AGENT_COMPONENT);
        List<PersistableBundle> childConfig =
                mDevicePolicyManager.getTrustAgentConfiguration(
                        null, TRUST_AGENT_COMPONENT);
        // Separate credential in managed profile, should only get its own config.
        assertPersistableBundleListEquals(ASSERT_PARENT_CONFIG, parentConfig);
        assertPersistableBundleListEquals(ASSERT_CHILD_CONFIG, childConfig);
    }

    private void setAndGetTrustAgentConfigurationInternal(boolean isParent) {
        // Set the config
        setTrustAgentConfiguration(isParent);
        // Case when trust agents is not disabled
        List<PersistableBundle> configs = getDevicePolicyManager(isParent)
                .getTrustAgentConfiguration(null, TRUST_AGENT_COMPONENT);
        assertNull(configs);
        // Case when trust agents is disabled
        disableTrustAgents(isParent);
        configs = getDevicePolicyManager(isParent)
                .getTrustAgentConfiguration(null, TRUST_AGENT_COMPONENT);
        assertPersistableBundleListEquals(isParent ?
                ASSERT_PARENT_CONFIG : ASSERT_CHILD_CONFIG, configs);
    }

    private void disableTrustAgents(boolean isParent) {
        getDevicePolicyManager(isParent).setKeyguardDisabledFeatures(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS);
    }

    private void enableTrustAgents(boolean isParent) {
        getDevicePolicyManager(isParent).setKeyguardDisabledFeatures(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE);
    }

    private void clearTrustAgentConfiguration(boolean isParent) {
        getDevicePolicyManager(isParent).setTrustAgentConfiguration(ADMIN_RECEIVER_COMPONENT,
                TRUST_AGENT_COMPONENT, null);
        assertNull(getDevicePolicyManager(isParent).getTrustAgentConfiguration(
                ADMIN_RECEIVER_COMPONENT, TRUST_AGENT_COMPONENT));
    }

    private PersistableBundle setTrustAgentConfiguration(boolean isParent) {
        PersistableBundle expected = isParent ? PARENT_CONFIG : CHILD_CONFIG;
        getDevicePolicyManager(isParent).setTrustAgentConfiguration(ADMIN_RECEIVER_COMPONENT,
                TRUST_AGENT_COMPONENT, expected);
        List<PersistableBundle> configs =
                getDevicePolicyManager(isParent).getTrustAgentConfiguration(
                        ADMIN_RECEIVER_COMPONENT, TRUST_AGENT_COMPONENT);
        assertPersistableBundleListEquals(isParent ?
                ASSERT_PARENT_CONFIG :
                ASSERT_CHILD_CONFIG, configs);
        return expected;
    }

    private static void assertPersistableBundleListEquals(
            AssertConfigMode mode, List<PersistableBundle> actual) {
        Set<String> expectedValues = new HashSet<>();
        switch (mode) {
            case ASSERT_CHILD_CONFIG:
                expectedValues.add(BUNDLE_CHILD_VALUE);
                break;
            case ASSERT_PARENT_CONFIG:
                expectedValues.add(BUNDLE_PARENT_VALUE);
                break;
            case ASSERT_BOTH:
                expectedValues.add(BUNDLE_PARENT_VALUE);
                expectedValues.add(BUNDLE_CHILD_VALUE);
                break;
        }
        assertNotNull(actual);
        Set<String> actualValues = new HashSet<>();
        for (PersistableBundle bundle : actual) {
            actualValues.add(bundle.getString(BUNDLE_KEY));
        }
        MoreAsserts.assertEquals(expectedValues, actualValues);
    }

}
