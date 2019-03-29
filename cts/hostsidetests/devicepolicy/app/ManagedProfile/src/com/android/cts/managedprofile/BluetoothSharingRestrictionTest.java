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
package com.android.cts.managedprofile;


import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;
import android.os.UserManager;

import junit.framework.TestCase;

import java.util.List;


/**
 * Test {@link UserManager#DISALLOW_BLUETOOTH_SHARING} in profile owner.
 *
 * Shamelessly copied from BluetoothRestrictionTest, would be nice to extract common stuff to a lib.
 */
public class BluetoothSharingRestrictionTest extends BaseManagedProfileTest {
    /** How long should we wait for the component state to change. */
    private static final int COMPONENT_STATE_TIMEOUT_MS = 2000;
    /** How often to check component state. */
    private static final int POLL_TIME_MS = 400;
    /** Activity that handles Bluetooth sharing. */
    private static final ComponentName OPP_LAUNCHER_COMPONENT = new ComponentName(
            "com.android.bluetooth", "com.android.bluetooth.opp.BluetoothOppLauncherActivity");

    /**
     * Tests that Bluetooth sharing activity gets disabled when the restriction is enforced.
     */
    public void testOppDisabledWhenRestrictionSet() throws Exception {
        if (BluetoothAdapter.getDefaultAdapter() == null) {
            // No Bluetooth - nothing to test.
            return;
        }

        // The restriction is active by default for managed profiles.
        assertBluetoothSharingAvailable(mContext, false);

        // Remove the user restriction.
        mDevicePolicyManager.clearUserRestriction(
                ADMIN_RECEIVER_COMPONENT, UserManager.DISALLOW_BLUETOOTH_SHARING);
        // Bluetooth sharing should become available.
        assertBluetoothSharingAvailable(mContext, true);

        // Add the user restriction back (which is the default state).
        mDevicePolicyManager.addUserRestriction(
                ADMIN_RECEIVER_COMPONENT, UserManager.DISALLOW_BLUETOOTH_SHARING);
        // Bluetooth sharing should be disabled once again.
        assertBluetoothSharingAvailable(mContext, false);
    }

    /** Verifies restriction enforcement. */
    private static void assertRestrictionEnforced(Context context, boolean enforced) {
        final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        assertEquals("Invalid restriction enforcement status", enforced,
                um.getUserRestrictions().getBoolean(UserManager.DISALLOW_BLUETOOTH_SHARING, false));
    }

    /**
     * Builds an intent to share an image file. If Bluetooth sharing is allowed, it should be
     * handled by {@link #OPP_LAUNCHER_COMPONENT}.
     */
    private static Intent fileSharingIntent() {
        final Intent result = new Intent(Intent.ACTION_SEND);
        final Uri uri = Uri.parse("content://foo/bar");
        result.setDataAndType(uri, "image/*");
        return result;
    }

    /**
     * Verifies bluetooth sharing availability.
     */
    static void assertBluetoothSharingAvailable(Context context, boolean available)
            throws Exception {
        // Check restriction.
        assertRestrictionEnforced(context, !available);
        // Check component status.
        final int componentEnabledState = available
                ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        assertComponentStateAfterTimeout(context, OPP_LAUNCHER_COMPONENT, componentEnabledState);
        // Check whether sharing activity is offered.
        assertHandlerAvailable(context, fileSharingIntent(), OPP_LAUNCHER_COMPONENT, available);
    }

    /** Waits for package state to change to a desired one or fails. */
    private static void assertComponentStateAfterTimeout(Context context, ComponentName component,
            int expectedState)
            throws Exception {
        final long timeout = SystemClock.elapsedRealtime() + COMPONENT_STATE_TIMEOUT_MS;
        int state = -1;
        while (SystemClock.elapsedRealtime() < timeout) {
            state = context.getPackageManager().getComponentEnabledSetting(component);
            if (expectedState == state) {
                // Success
                return;
            }
            Thread.sleep(POLL_TIME_MS);
        }
        TestCase.fail("The state of " + component + " should have been " + expectedState
                + ", it but was " + state + " after timeout.");
    }

    /** Verifies that {@code component} is offered when handling {@code intent}. */
    private static void assertHandlerAvailable(Context context, Intent intent,
            ComponentName component,
            boolean shouldResolve) {
        final List<ResolveInfo> infos =
                context.getPackageManager().queryIntentActivities(intent, 0);
        for (final ResolveInfo info : infos) {
            final ComponentInfo componentInfo =
                    info.activityInfo != null ? info.activityInfo :
                            info.serviceInfo != null ? info.serviceInfo :
                                    info.providerInfo;
            final ComponentName resolvedComponent =
                    new ComponentName(componentInfo.packageName, componentInfo.name);

            if (resolvedComponent.equals(component)) {
                if (shouldResolve) {
                    // Found it, assertion passed.
                    return;
                } else {
                    TestCase.fail(component + " is available as a handler for " + intent);
                }
            }
        }
        // If we get to this point, there was no match.
        if (shouldResolve) {
            TestCase.fail(component + " isn't available as a handler for " + intent);
        }
    }
}
