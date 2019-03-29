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
package com.android.cts.deviceandprofileowner.userrestrictions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;

import com.android.cts.deviceandprofileowner.BaseDeviceAdminTest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class BaseUserRestrictionsTest extends BaseDeviceAdminTest {
    protected static final String[] ALL_USER_RESTRICTIONS = new String[]{
            UserManager.DISALLOW_CONFIG_WIFI,
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_SHARE_LOCATION,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_CONFIG_BLUETOOTH,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_CONFIG_CREDENTIALS,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_REMOVE_MANAGED_PROFILE,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_CONFIG_VPN,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_ADD_MANAGED_PROFILE,
            UserManager.ENSURE_VERIFY_APPS,
            UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_UNMUTE_MICROPHONE,
            UserManager.DISALLOW_ADJUST_VOLUME,
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_CREATE_WINDOWS,
            UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE,
            UserManager.DISALLOW_OUTGOING_BEAM,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.ALLOW_PARENT_PROFILE_APP_LINKING,
            UserManager.DISALLOW_DATA_ROAMING,
            UserManager.DISALLOW_SET_USER_ICON,
            UserManager.DISALLOW_BLUETOOTH,
            UserManager.DISALLOW_BLUETOOTH_SHARING
    };

    /**
     * Restrictions that affect all users when DO sets.
     */
    protected static final String[] DO_GLOBAL_RESTRICTIONS = new String[] {
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_CREATE_WINDOWS,
            UserManager.DISALLOW_BLUETOOTH,
            // UserManager.DISALLOW_DATA_ROAMING, // Not set during CTS

            // PO can set them too, but when DO sets them, they're global.
            UserManager.DISALLOW_ADJUST_VOLUME,
            UserManager.DISALLOW_UNMUTE_MICROPHONE
    };

    public static final String[] HIDDEN_AND_PROHIBITED = new String[] {
            "no_record_audio",
            "no_wallpaper",
            "no_oem_unlock"
    };

    protected void assertLayeredRestriction(String restriction, boolean expected) {
        assertEquals("Restriction " + restriction + ": expected=" + expected,
                expected, mUserManager.hasUserRestriction(restriction));
    }

    protected void assertOwnerRestriction(String restriction, boolean expected) {
        assertEquals("Restriction " + restriction + ": expected=" + expected,
                expected, mDevicePolicyManager.getUserRestrictions(ADMIN_RECEIVER_COMPONENT)
                        .getBoolean(restriction));
    }

    protected void assertRestrictions(Set<String> expected) {
        for (String r : ALL_USER_RESTRICTIONS) {
            assertLayeredRestriction(r, expected.contains(r));
        }
    }

    /**
     * Test that the given restriction can be set and cleared, then leave it set again.
     */
    protected void assertSetClearUserRestriction(String restriction) {
        final boolean hadRestriction = mUserManager.hasUserRestriction(restriction);

        assertOwnerRestriction(restriction, false);

        // Set.  Shouldn't throw.
        mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);

        assertOwnerRestriction(restriction, true);
        assertLayeredRestriction(restriction, true);

        // Then clear.
        assertClearUserRestriction(restriction);

        assertLayeredRestriction(restriction, hadRestriction);

        // Then set again.
        mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);
    }

    /**
     * Test that the given restriction can be cleared.  (and leave it cleared.)
     */
    protected void assertClearUserRestriction(String restriction) {
        mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);

        assertOwnerRestriction(restriction, false);
    }

    protected void assertClearDefaultRestrictions() {
        for (String restriction : getDefaultEnabledRestrictions()) {
            assertClearUserRestriction(restriction);
        }
    }

    /**
     * Test that the given restriction *cannot* be set (or clear).
     */
    protected void assertCannotSetUserRestriction(String restriction) {
        final boolean hadRestriction = mUserManager.hasUserRestriction(restriction);

        assertOwnerRestriction(restriction, false);

        // Set should fail.
        try {
            mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);
            fail("Restriction=" + restriction);
        } catch (SecurityException e) {
            assertTrue("Restriction=" + restriction + " Message was: " + e.getMessage(),
                    e.getMessage().contains("cannot set user restriction"));
        }

        // Shouldn't have changed.
        assertOwnerRestriction(restriction, false);
        assertLayeredRestriction(restriction, hadRestriction);

        // Clear should fail too.
        try {
            mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);
            fail("Restriction=" + restriction);
        } catch (SecurityException e) {
            assertTrue("Restriction=" + restriction + " Message was: " + e.getMessage(),
                    e.getMessage().contains("cannot set user restriction"));
        }

        // Shouldn't have changed.
        assertOwnerRestriction(restriction, false);
        assertLayeredRestriction(restriction, hadRestriction);
    }

    /** For {@link #testSetAllRestrictions} */
    protected abstract String[] getAllowedRestrictions();

    /** For {@link #testSetAllRestrictions} */
    protected abstract String[] getDisallowedRestrictions();

    /** For {@link #testDefaultRestrictions()} */
    protected abstract String[] getDefaultEnabledRestrictions();

    /**
     * Test restrictions that should be enabled by default
     */
    public void testDefaultRestrictions() {
        for (String restriction : getDefaultEnabledRestrictions()) {
            assertOwnerRestriction(restriction, true);
        }

        Set<String> offByDefaultRestrictions = new HashSet<>(Arrays.asList(ALL_USER_RESTRICTIONS));
        offByDefaultRestrictions.removeAll(
                new HashSet<>(Arrays.asList(getDefaultEnabledRestrictions())));
        for (String restriction : offByDefaultRestrictions) {
            assertOwnerRestriction(restriction, false);
        }
    }

    /**
     * Set only one restriction, and make sure only that's set, and then clear it.
     */
    public void testSetAllRestrictionsIndividually() {
        assertClearDefaultRestrictions();
        for (String r : getAllowedRestrictions()) {
            // Set it.
            assertSetClearUserRestriction(r);

            assertRestrictions(new HashSet<>(Arrays.asList(new String[]{r})));

            // Then clear it.
            assertClearUserRestriction(r);
        }
    }

    /**
     * Make sure all allowed restrictions can be set, and the others can't.
     */
    public void testSetAllRestrictions() {
        assertClearDefaultRestrictions();
        for (String r : getAllowedRestrictions()) {
            assertSetClearUserRestriction(r);
        }
        for (String r : getDisallowedRestrictions()) {
            assertCannotSetUserRestriction(r);
        }
        for (String r : HIDDEN_AND_PROHIBITED) {
            assertCannotSetUserRestriction(r);
        }
    }

    /**
     * Clear all allowed restrictions.
     */
    public void testClearAllRestrictions() {
        for (String r : getAllowedRestrictions()) {
            assertClearUserRestriction(r);
        }
    }

    public void testBroadcast() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final IntentFilter filter = new IntentFilter(UserManager.ACTION_USER_RESTRICTIONS_CHANGED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        }, filter);

        final String restriction = UserManager.DISALLOW_CONFIG_WIFI;
        mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);

        assertTrue("Didn't receive broadcast", latch.await(120, TimeUnit.SECONDS));

        mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);
    }
}
