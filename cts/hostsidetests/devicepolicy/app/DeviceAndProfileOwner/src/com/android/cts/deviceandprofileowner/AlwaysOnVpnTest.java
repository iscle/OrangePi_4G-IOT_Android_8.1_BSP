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

import static com.android.cts.deviceandprofileowner.vpn.VpnTestHelper.TEST_ADDRESS;
import static com.android.cts.deviceandprofileowner.vpn.VpnTestHelper.VPN_PACKAGE;

import android.os.Bundle;
import android.os.UserManager;

import com.android.cts.deviceandprofileowner.vpn.VpnTestHelper;

/**
 * Validates that a device owner or profile owner can set an always-on VPN without user action.
 *
 * A trivial VPN app is installed which reflects ping packets back to the sender. One ping packet is
 * sent, received after its round-trip, and compared to the original packet to make sure nothing
 * strange happened on the way through the VPN.
 *
 * All of the addresses in this test are fictional and any resemblance to real addresses is the
 * result of a misconfigured network.
 */
public class AlwaysOnVpnTest extends BaseDeviceAdminTest {
    /** @see com.android.cts.vpnfirewall.ReflectorVpnService */
    public static final String RESTRICTION_ADDRESSES = "vpn.addresses";
    public static final String RESTRICTION_ROUTES = "vpn.routes";
    public static final String RESTRICTION_ALLOWED = "vpn.allowed";
    public static final String RESTRICTION_DISALLOWED = "vpn.disallowed";

    private String mPackageName;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // always-on is null by default
        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
        mPackageName = mContext.getPackageName();
    }

    @Override
    public void tearDown() throws Exception {
        mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, null, false);
        mDevicePolicyManager.setApplicationRestrictions(ADMIN_RECEIVER_COMPONENT, VPN_PACKAGE,
                /* restrictions */ null);
        super.tearDown();
    }
    public void testAlwaysOnVpn() throws Exception {
        // test always-on is null by default
        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));

        VpnTestHelper.waitForVpn(mContext, VPN_PACKAGE, /* usable */ true);
        VpnTestHelper.checkPing(TEST_ADDRESS);
    }

    public void testDisallowConfigVpn() throws Exception {
        mDevicePolicyManager.addUserRestriction(
                ADMIN_RECEIVER_COMPONENT, UserManager.DISALLOW_CONFIG_VPN);
        try {
            testAlwaysOnVpn();
        } finally {
            // clear the user restriction
            mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT,
                    UserManager.DISALLOW_CONFIG_VPN);
        }
    }

    public void testAllowedApps() throws Exception {
        final Bundle restrictions = new Bundle();
        restrictions.putStringArray(RESTRICTION_ALLOWED, new String[] {mPackageName});
        mDevicePolicyManager.setApplicationRestrictions(ADMIN_RECEIVER_COMPONENT, VPN_PACKAGE,
                restrictions);
        VpnTestHelper.waitForVpn(mContext, VPN_PACKAGE, /* usable */ true);
        assertTrue(VpnTestHelper.isNetworkVpn(mContext));
    }

    public void testDisallowedApps() throws Exception {
        final Bundle restrictions = new Bundle();
        restrictions.putStringArray(RESTRICTION_DISALLOWED, new String[] {mPackageName});
        mDevicePolicyManager.setApplicationRestrictions(ADMIN_RECEIVER_COMPONENT, VPN_PACKAGE,
                restrictions);
        VpnTestHelper.waitForVpn(mContext, VPN_PACKAGE, /* usable */ false);
        assertFalse(VpnTestHelper.isNetworkVpn(mContext));
    }

    public void testSetNonVpnAlwaysOn() throws Exception {
        // Treat this CTS DPC as an non-vpn app, since it doesn't register
        // android.net.VpnService intent filter in AndroidManifest.xml.
        try {
            mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, mPackageName,
                    true);
            fail("setAlwaysOnVpnPackage should not accept non-vpn package");
        } catch (UnsupportedOperationException e) {
            // success
        }
        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
    }
}
