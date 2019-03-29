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
package com.android.cts.net.hostside;

import android.util.Log;

/**
 * Test cases for the more complex scenarios where multiple restrictions (like Battery Saver Mode
 * and Data Saver Mode) are applied simultaneously.
 * <p>
 * <strong>NOTE: </strong>it might sound like the test methods on this class are testing too much,
 * which would make it harder to diagnose individual failures, but the assumption is that such
 * failure most likely will happen when the restriction is tested individually as well.
 */
public class MixedModesTest extends AbstractRestrictBackgroundNetworkTestCase {
    private static final String TAG = "MixedModesTest";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        if (!isSupported()) return;

        // Set initial state.
        removeRestrictBackgroundWhitelist(mUid);
        removeRestrictBackgroundBlacklist(mUid);
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);

        registerBroadcastReceiver();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (!isSupported()) return;

        try {
            setRestrictBackground(false);
        } finally {
            setBatterySaverMode(false);
        }
    }

    /**
     * Tests all DS ON and BS ON scenarios from network-policy-restrictions.md on metered networks.
     */
    public void testDataAndBatterySaverModes_meteredNetwork() throws Exception {
        if (!isSupported()) return;

        if (!isDozeModeEnabled()) {
            Log.w(TAG, "testDataAndBatterySaverModes_meteredNetwork() skipped because "
                    + "device does not support Doze Mode");
            return;
        }

        Log.i(TAG, "testDataAndBatterySaverModes_meteredNetwork() tests");
        if (!setMeteredNetwork()) {
            Log.w(TAG, "testDataAndBatterySaverModes_meteredNetwork() skipped because "
                    + "device cannot use a metered network");
            return;
        }

        try {
            setRestrictBackground(true);
            setBatterySaverMode(true);

            Log.v(TAG, "Not whitelisted for any.");
            assertBackgroundNetworkAccess(false);
            assertsForegroundAlwaysHasNetworkAccess();
            assertBackgroundNetworkAccess(false);

            Log.v(TAG, "Whitelisted for Data Saver but not for Battery Saver.");
            addRestrictBackgroundWhitelist(mUid);
            removePowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);
            assertsForegroundAlwaysHasNetworkAccess();
            assertBackgroundNetworkAccess(false);
            removeRestrictBackgroundWhitelist(mUid);

            Log.v(TAG, "Whitelisted for Battery Saver but not for Data Saver.");
            addPowerSaveModeWhitelist(TEST_APP2_PKG);
            removeRestrictBackgroundWhitelist(mUid);
            assertBackgroundNetworkAccess(false);
            assertsForegroundAlwaysHasNetworkAccess();
            assertBackgroundNetworkAccess(false);
            removePowerSaveModeWhitelist(TEST_APP2_PKG);

            Log.v(TAG, "Whitelisted for both.");
            addRestrictBackgroundWhitelist(mUid);
            addPowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(true);
            assertsForegroundAlwaysHasNetworkAccess();
            assertBackgroundNetworkAccess(true);
            removePowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);
            removeRestrictBackgroundWhitelist(mUid);

            Log.v(TAG, "Blacklisted for Data Saver, not whitelisted for Battery Saver.");
            addRestrictBackgroundBlacklist(mUid);
            removePowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);
            assertsForegroundAlwaysHasNetworkAccess();
            assertBackgroundNetworkAccess(false);
            removeRestrictBackgroundBlacklist(mUid);

            Log.v(TAG, "Blacklisted for Data Saver, whitelisted for Battery Saver.");
            addRestrictBackgroundBlacklist(mUid);
            addPowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);
            assertsForegroundAlwaysHasNetworkAccess();
            assertBackgroundNetworkAccess(false);
            removeRestrictBackgroundBlacklist(mUid);
            removePowerSaveModeWhitelist(TEST_APP2_PKG);
        } finally {
            resetMeteredNetwork();
        }
    }

    /**
     * Tests all DS ON and BS ON scenarios from network-policy-restrictions.md on non-metered
     * networks.
     */
    public void testDataAndBatterySaverModes_nonMeteredNetwork() throws Exception {
        if (!isSupported()) return;

        if (!isDozeModeEnabled()) {
            Log.w(TAG, "testDataAndBatterySaverModes_nonMeteredNetwork() skipped because "
                    + "device does not support Doze Mode");
            return;
        }

        if (!setUnmeteredNetwork()) {
            Log.w(TAG, "testDataAndBatterySaverModes_nonMeteredNetwork() skipped because network"
                    + " is metered");
            return;
        }
        Log.i(TAG, "testDataAndBatterySaverModes_nonMeteredNetwork() tests");
        setRestrictBackground(true);
        setBatterySaverMode(true);

        Log.v(TAG, "Not whitelisted for any.");
        assertBackgroundNetworkAccess(false);
        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(false);

        Log.v(TAG, "Whitelisted for Data Saver but not for Battery Saver.");
        addRestrictBackgroundWhitelist(mUid);
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(false);
        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(false);
        removeRestrictBackgroundWhitelist(mUid);

        Log.v(TAG, "Whitelisted for Battery Saver but not for Data Saver.");
        addPowerSaveModeWhitelist(TEST_APP2_PKG);
        removeRestrictBackgroundWhitelist(mUid);
        assertBackgroundNetworkAccess(true);
        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(true);
        removePowerSaveModeWhitelist(TEST_APP2_PKG);

        Log.v(TAG, "Whitelisted for both.");
        addRestrictBackgroundWhitelist(mUid);
        addPowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(true);
        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(true);
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(false);
        removeRestrictBackgroundWhitelist(mUid);

        Log.v(TAG, "Blacklisted for Data Saver, not whitelisted for Battery Saver.");
        addRestrictBackgroundBlacklist(mUid);
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(false);
        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(false);
        removeRestrictBackgroundBlacklist(mUid);

        Log.v(TAG, "Blacklisted for Data Saver, whitelisted for Battery Saver.");
        addRestrictBackgroundBlacklist(mUid);
        addPowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(true);
        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(true);
        removeRestrictBackgroundBlacklist(mUid);
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
    }

    /**
     * Tests that powersave whitelists works as expected when doze and battery saver modes
     * are enabled.
     */
    public void testDozeAndBatterySaverMode_powerSaveWhitelists() throws Exception {
        if (!isSupported()) {
            return;
        }
        if (!isDozeModeEnabled()) {
            Log.i(TAG, "Skipping " + getClass() + "." + getName()
                    + "() because device does not support Doze Mode");
            return;
        }

        setBatterySaverMode(true);
        setDozeMode(true);

        try {
            addPowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(true);

            removePowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);

            addPowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);

            removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);
        } finally {
            setBatterySaverMode(false);
            setDozeMode(false);
        }
    }

    /**
     * Tests that powersave whitelists works as expected when doze and appIdle modes
     * are enabled.
     */
    public void testDozeAndAppIdle_powerSaveWhitelists() throws Exception {
        if (!isSupported()) {
            return;
        }
        if (!isDozeModeEnabled()) {
            Log.i(TAG, "Skipping " + getClass() + "." + getName()
                    + "() because device does not support Doze Mode");
            return;
        }

        setDozeMode(true);
        setAppIdle(true);

        try {
            addPowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(true);

            removePowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);

            addPowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);

            removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(false);
        } finally {
            setAppIdle(false);
            setDozeMode(false);
        }
    }
}
