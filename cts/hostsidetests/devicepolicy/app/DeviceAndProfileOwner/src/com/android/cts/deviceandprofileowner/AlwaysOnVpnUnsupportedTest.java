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

package com.android.cts.deviceandprofileowner;

import static com.android.cts.deviceandprofileowner.vpn.VpnTestHelper.VPN_PACKAGE;

/**
 * Validates that a device owner or profile owner cannot enable the always-on feature for
 * unsupported VPN apps.
 *
 * A VPN app does not support the always-on feature if it
 * <ul>
 *     <li>has a target SDK version below {@link android.os.Build.VERSION_CODES#N}, or</li>
 *     <li>explicitly opts out of the feature through
 *         {@link android.net.VpnService#SERVICE_META_DATA_SUPPORTS_ALWAYS_ON}</li>
 * </ul>
 */
public class AlwaysOnVpnUnsupportedTest extends BaseDeviceAdminTest {

    public void testAssertNoAlwaysOnVpn() throws Exception {
        assertNull("Always-on VPN already exists",
                mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
    }

    public void testClearAlwaysOnVpn() throws Exception {
        mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, null, false);
        assertNull("Failed to clear always-on package",
                mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
    }

    public void testSetSupportedVpnAlwaysOn() throws Exception {
        testAssertNoAlwaysOnVpn();
        mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, VPN_PACKAGE, true);
        assertEquals("Failed to set always-on package",
                VPN_PACKAGE, mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
    }

    public void testSetUnsupportedVpnAlwaysOn() throws Exception {
        testAssertNoAlwaysOnVpn();
        try {
            mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, VPN_PACKAGE, true);
            fail("setAlwaysOnVpnPackage should not accept an unsupported vpn package");
        } catch (UnsupportedOperationException e) {
            // success
        }
        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
    }
}
