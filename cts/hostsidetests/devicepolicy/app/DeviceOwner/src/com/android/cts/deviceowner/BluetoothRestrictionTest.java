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

package com.android.cts.deviceowner;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.os.UserManager;

/**
 * Test interaction between {@link UserManager#DISALLOW_BLUETOOTH} user restriction and the state
 * of Bluetooth.
 */
public class BluetoothRestrictionTest extends BaseDeviceOwnerTest {

    private static final int DISABLE_TIMEOUT_MS = 8000; // ms timeout for BT disable
    private static final int ENABLE_TIMEOUT_MS = 10000; // ms timeout for BT enable
    private static final int POLL_TIME_MS = 400;           // ms to poll BT state
    private static final int CHECK_WAIT_TIME_MS = 1000;    // ms to wait before enable/disable
    private static final int COMPONENT_STATE_TIMEOUT_MS = 10000;
    private static final ComponentName OPP_LAUNCHER_COMPONENT = new ComponentName(
            "com.android.bluetooth", "com.android.bluetooth.opp.BluetoothOppLauncherActivity");

    private BluetoothAdapter mBluetoothAdapter;
    private PackageManager mPackageManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mPackageManager = mContext.getPackageManager();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_BLUETOOTH);
        enable();
    }

    public void testEnableBluetoothFailsWhenDisallowed() throws Exception {
        if (mBluetoothAdapter == null) {
            return;
        }

        // Make sure Bluetooth is initially disabled.
        disable();

        // Add the user restriction disallowing Bluetooth.
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_BLUETOOTH);

        // Check that enabling Bluetooth fails.
        assertFalse(mBluetoothAdapter.enable());
    }

    public void testBluetoothGetsDisabledAfterRestrictionSet() throws Exception {
        if (mBluetoothAdapter == null) {
            return;
        }

        // Make sure Bluetooth is enabled first.
        enable();

        // Add the user restriction to disallow Bluetooth.
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_BLUETOOTH);

        // Check that Bluetooth gets disabled as a result.
        assertDisabledAfterTimeout();
    }

    public void testEnableBluetoothSucceedsAfterRestrictionRemoved() throws Exception {
        if (mBluetoothAdapter == null) {
            return;
        }

        // Add the user restriction.
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_BLUETOOTH);

        // Make sure Bluetooth is disabled.
        assertDisabledAfterTimeout();

        // Remove the user restriction.
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_BLUETOOTH);

        // Check that it is possible to enable Bluetooth again once the restriction has been
        // removed.
        enable();
    }

    /**
     * Tests that BluetoothOppLauncherActivity gets disabled when Bluetooth itself or Bluetooth
     * sharing is disallowed.
     *
     * <p> It also checks the state of the activity is set back to default if Bluetooth is not
     * disallowed anymore.
     */
    public void testOppDisabledWhenRestrictionSet() throws Exception {
        if (mBluetoothAdapter == null) {
            return;
        }

        // First verify DISALLOW_BLUETOOTH.
        testOppDisabledWhenRestrictionSet(UserManager.DISALLOW_BLUETOOTH);
        // Verify DISALLOW_BLUETOOTH_SHARING which leaves bluetooth workable but the sharing
        // component should be disabled.
        testOppDisabledWhenRestrictionSet(UserManager.DISALLOW_BLUETOOTH_SHARING);
    }

    /** Verifies that a given restriction disables the bluetooth sharing component. */
    private void testOppDisabledWhenRestrictionSet(String restriction) {
        // Add the user restriction.
        mDevicePolicyManager.addUserRestriction(getWho(), restriction);

        // The BluetoothOppLauncherActivity's component should be disabled.
        assertComponentStateAfterTimeout(
                OPP_LAUNCHER_COMPONENT, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        // Remove the user restriction.
        mDevicePolicyManager.clearUserRestriction(getWho(), restriction);

        // The BluetoothOppLauncherActivity's component should be in the default state.
        assertComponentStateAfterTimeout(
                OPP_LAUNCHER_COMPONENT, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
    }

    /** Helper to turn BT off.
     * This method will either fail on an assert, or return with BT turned off.
     * Behavior of getState() and isEnabled() are validated along the way.
     */
    private void disable() {
        // Can't disable a bluetooth adapter that does not exist.
        if (mBluetoothAdapter == null)
            return;

        sleep(CHECK_WAIT_TIME_MS);
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            assertFalse(mBluetoothAdapter.isEnabled());
            return;
        }

        assertEquals(BluetoothAdapter.STATE_ON, mBluetoothAdapter.getState());
        assertTrue(mBluetoothAdapter.isEnabled());
        mBluetoothAdapter.disable();
        assertDisabledAfterTimeout();
    }

    /**
     * Helper method which waits for Bluetooth to be disabled. Fails if it doesn't happen in a
     * given time.
     */
    private void assertDisabledAfterTimeout() {
        boolean turnOff = false;
        final long timeout = SystemClock.elapsedRealtime() + DISABLE_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < timeout) {
            int state = mBluetoothAdapter.getState();
            switch (state) {
            case BluetoothAdapter.STATE_OFF:
                assertFalse(mBluetoothAdapter.isEnabled());
                return;
            default:
                if (state != BluetoothAdapter.STATE_ON || turnOff) {
                    assertEquals(BluetoothAdapter.STATE_TURNING_OFF, state);
                    turnOff = true;
                }
                break;
            }
            sleep(POLL_TIME_MS);
        }
        fail("disable() timeout");
    }

    private void assertComponentStateAfterTimeout(ComponentName component, int expectedState) {
        final long timeout = SystemClock.elapsedRealtime() + COMPONENT_STATE_TIMEOUT_MS;
        int state = -1;
        while (SystemClock.elapsedRealtime() < timeout) {
            state = mPackageManager.getComponentEnabledSetting(component);
            if (expectedState == state) {
                // Success
                return;
            }
            sleep(POLL_TIME_MS);
        }
        fail("The state of " + component + " should have been " + expectedState + ", it but was "
                + state + " after timeout.");
    }

    /** Helper to turn BT on.
     * This method will either fail on an assert, or return with BT turned on.
     * Behavior of getState() and isEnabled() are validated along the way.
     */
    private void enable() {
        // Can't enable a bluetooth adapter that does not exist.
        if (mBluetoothAdapter == null)
            return;

        sleep(CHECK_WAIT_TIME_MS);
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            assertTrue(mBluetoothAdapter.isEnabled());
            return;
        }

        assertEquals(BluetoothAdapter.STATE_OFF, mBluetoothAdapter.getState());
        assertFalse(mBluetoothAdapter.isEnabled());
        mBluetoothAdapter.enable();
        assertEnabledAfterTimeout();
    }

    /**
     * Helper method which waits for Bluetooth to be enabled. Fails if it doesn't happen in a given
     * time.
     */
    private void assertEnabledAfterTimeout() {
        boolean turnOn = false;
        final long timeout = SystemClock.elapsedRealtime() + ENABLE_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < timeout) {
            int state = mBluetoothAdapter.getState();
            switch (state) {
            case BluetoothAdapter.STATE_ON:
                assertTrue(mBluetoothAdapter.isEnabled());
                return;
            default:
                if (state != BluetoothAdapter.STATE_OFF || turnOn) {
                    assertEquals(BluetoothAdapter.STATE_TURNING_ON, state);
                    turnOn = true;
                }
                break;
            }
            sleep(POLL_TIME_MS);
        }
        fail("enable() timeout");
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {}
    }

}
