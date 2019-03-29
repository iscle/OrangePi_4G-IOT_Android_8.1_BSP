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
 * Base class for metered and non-metered Battery Saver Mode tests.
 */
abstract class AbstractBatterySaverModeTestCase extends AbstractRestrictBackgroundNetworkTestCase {

    @Override
    protected final void setUp() throws Exception {
        super.setUp();

        if (!isSupported()) return;

        // Set initial state.
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
        setBatterySaverMode(false);

        registerBroadcastReceiver();
    }

    @Override
    protected final void tearDown() throws Exception {
        super.tearDown();

        if (!isSupported()) return;

        try {
            tearDownMeteredNetwork();
        } finally {
            setBatterySaverMode(false);
        }
    }

    @Override
    protected boolean isSupported() throws Exception {
        boolean supported = isDozeModeEnabled();
        if (!supported) {
            Log.i(TAG, "Skipping " + getClass() + "." + getName()
                    + "() because device does not support Doze Mode");
        }
        return supported;
    }

    /**
     * Sets the initial (non) metered network state.
     *
     * <p>By default is empty - it's up to subclasses to override.
     */
    protected void setUpMeteredNetwork() throws Exception {
    }

    /**
     * Resets the (non) metered network state.
     *
     * <p>By default is empty - it's up to subclasses to override.
     */
    protected void tearDownMeteredNetwork() throws Exception {
    }

    public void testBackgroundNetworkAccess_enabled() throws Exception {
        if (!isSupported()) return;

        setBatterySaverMode(true);
        assertBackgroundNetworkAccess(false);

        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(false);

        // Make sure foreground app doesn't lose access upon Battery Saver.
        setBatterySaverMode(false);
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
        setBatterySaverMode(true);
        assertForegroundNetworkAccess();

        // Although it should not have access while the screen is off.
        turnScreenOff();
        assertBackgroundNetworkAccess(false);
        turnScreenOn();
        assertForegroundNetworkAccess();

        // Goes back to background state.
        finishActivity();
        assertBackgroundNetworkAccess(false);

        // Make sure foreground service doesn't lose access upon enabling Battery Saver.
        setBatterySaverMode(false);
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_FOREGROUND_SERVICE);
        setBatterySaverMode(true);
        assertForegroundNetworkAccess();
        stopForegroundService();
        assertBackgroundNetworkAccess(false);
    }

    public void testBackgroundNetworkAccess_whitelisted() throws Exception {
        if (!isSupported()) return;

        setBatterySaverMode(true);
        assertBackgroundNetworkAccess(false);

        addPowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(true);

        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(false);

        addPowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(true);

        removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(false);

        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(false);
    }

    public void testBackgroundNetworkAccess_disabled() throws Exception {
        if (!isSupported()) return;

        assertBackgroundNetworkAccess(true);

        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(true);
    }
}
