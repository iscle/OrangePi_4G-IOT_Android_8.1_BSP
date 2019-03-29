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

import android.os.SystemClock;
import android.util.Log;

/**
 * Base class for metered and non-metered tests on idle apps.
 */
abstract class AbstractAppIdleTestCase extends AbstractRestrictBackgroundNetworkTestCase {

    @Override
    protected final void setUp() throws Exception {
        super.setUp();

        if (!isSupported()) return;

        // Set initial state.
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
        setAppIdle(false);
        turnBatteryOff();

        registerBroadcastReceiver();
    }

    @Override
    protected final void tearDown() throws Exception {
        super.tearDown();

        if (!isSupported()) return;

        try {
            tearDownMeteredNetwork();
        } finally {
            turnBatteryOn();
            setAppIdle(false);
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
     * Resets the (non) metered network state.
     *
     * <p>By default is empty - it's up to subclasses to override.
     */
    protected void tearDownMeteredNetwork() throws Exception {
    }

    public void testBackgroundNetworkAccess_enabled() throws Exception {
        if (!isSupported()) return;

        setAppIdle(true);
        assertBackgroundNetworkAccess(false);

        assertsForegroundAlwaysHasNetworkAccess();
        setAppIdle(true);
        assertBackgroundNetworkAccess(false);

        // Make sure foreground app doesn't lose access upon enabling it.
        setAppIdle(true);
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
        finishActivity();
        assertAppIdle(false); // Sanity check - not idle anymore, since activity was launched...
        assertBackgroundNetworkAccess(true);
        setAppIdle(true);
        assertBackgroundNetworkAccess(false);

        // Same for foreground service.
        setAppIdle(true);
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_FOREGROUND_SERVICE);
        stopForegroundService();
        assertAppIdle(true);
        assertBackgroundNetworkAccess(false);
    }

    public void testBackgroundNetworkAccess_whitelisted() throws Exception {
        if (!isSupported()) return;

        setAppIdle(true);
        assertBackgroundNetworkAccess(false);

        addPowerSaveModeWhitelist(TEST_APP2_PKG);
        assertAppIdle(false); // Sanity check - not idle anymore, since whitelisted
        assertBackgroundNetworkAccess(true);

        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        assertAppIdle(true); // Sanity check - idle again, once whitelisted was removed
        assertBackgroundNetworkAccess(false);

        addPowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
        assertAppIdle(false); // Sanity check - not idle anymore, since whitelisted
        assertBackgroundNetworkAccess(true);

        removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
        assertAppIdle(true); // Sanity check - idle again, once whitelisted was removed
        assertBackgroundNetworkAccess(false);

        assertsForegroundAlwaysHasNetworkAccess();

        // Sanity check - no whitelist, no access!
        setAppIdle(true);
        assertBackgroundNetworkAccess(false);
    }

    public void testBackgroundNetworkAccess_disabled() throws Exception {
        if (!isSupported()) return;

        assertBackgroundNetworkAccess(true);

        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(true);
    }

    public void testAppIdleNetworkAccess_whenCharging() throws Exception {
        if (!isSupported()) return;

        // Check that app is paroled when charging
        setAppIdle(true);
        assertBackgroundNetworkAccess(false);
        turnBatteryOn();
        assertBackgroundNetworkAccess(true);
        turnBatteryOff();
        assertBackgroundNetworkAccess(false);

        // Check that app is restricted when not idle but power-save is on
        setAppIdle(false);
        assertBackgroundNetworkAccess(true);
        setBatterySaverMode(true);
        assertBackgroundNetworkAccess(false);
        // Use setBatterySaverMode API to leave power-save mode instead of plugging in charger
        setBatterySaverMode(false);
        turnBatteryOn();
        assertBackgroundNetworkAccess(true);

        // And when no longer charging, it still has network access, since it's not idle
        turnBatteryOff();
        assertBackgroundNetworkAccess(true);
    }

    public void testAppIdle_toast() throws Exception {
        if (!isSupported()) return;

        setAppIdle(true);
        assertAppIdle(true);
        assertEquals("Shown", showToast());
        assertAppIdle(true);
        // Wait for a couple of seconds for the toast to actually be shown
        SystemClock.sleep(2000);
        assertAppIdle(true);
    }
}
