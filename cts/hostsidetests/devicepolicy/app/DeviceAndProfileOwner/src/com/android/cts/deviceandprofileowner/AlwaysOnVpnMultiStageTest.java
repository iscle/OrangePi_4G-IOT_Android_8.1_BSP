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

import android.content.pm.PackageManager;
import android.system.ErrnoException;
import android.system.OsConstants;

import com.android.cts.deviceandprofileowner.vpn.VpnTestHelper;

/**
 * Contains methods to test always-on VPN invoked by DeviceAndProfileOwnerTest
 */
public class AlwaysOnVpnMultiStageTest extends BaseDeviceAdminTest {

    public void testAlwaysOnSet() throws Exception {
        // Setup always-on vpn
        VpnTestHelper.waitForVpn(mContext, VPN_PACKAGE, /* usable */ true);
        assertTrue(VpnTestHelper.isNetworkVpn(mContext));
        VpnTestHelper.checkPing(TEST_ADDRESS);
    }

    public void testAlwaysOnSetAfterReboot() throws Exception {
        VpnTestHelper.waitForVpn(mContext, null, /* usable */ true);
        VpnTestHelper.checkPing(TEST_ADDRESS);
    }

    public void testNetworkBlocked() throws Exception {
        // After the vpn app being force-stop, expect that always-on package stays the same
        assertEquals(VPN_PACKAGE, mDevicePolicyManager.getAlwaysOnVpnPackage(
                ADMIN_RECEIVER_COMPONENT));
        assertFalse(VpnTestHelper.isNetworkVpn(mContext));
        // Expect the network is still locked down after the vpn app process is killed
        try {
            VpnTestHelper.tryPosixConnect(TEST_ADDRESS);
            fail("sendIcmpMessage doesn't throw Exception during network lockdown");
        } catch (ErrnoException e) {
            // Os.connect returns ENETUNREACH or EACCES errno after the vpn app process is killed
            assertTrue((e.errno == OsConstants.ENETUNREACH) ||
                       (e.errno == OsConstants.EACCES));
        }
    }

    public void testAlwaysOnVpnDisabled() throws Exception {
        // After the vpn app being uninstalled, check that always-on vpn is null
        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
        assertFalse(VpnTestHelper.isNetworkVpn(mContext));
    }

    public void testSetNonExistingPackage() throws Exception {
        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));

        // Verify it throws NameNotFoundException for non-existing package after uninstallation
        try {
            mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, VPN_PACKAGE,
                    true);
            fail("setAlwaysOnVpnPackage should not accept non-vpn package");
        } catch (PackageManager.NameNotFoundException e) {
            // success
        }

        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
    }

    public void testCleanup() throws Exception {
        mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, null, false);
    }
}
