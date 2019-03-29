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

import android.graphics.Color;

public class OrganizationInfoTest extends BaseManagedProfileTest {

    // needs to match DevicePolicyManagerService.ActiveAdmin.DEF_ORGANIZATION_COLOR
    private static final int DEFAULT_ORGANIZATION_COLOR = Color.parseColor("#00796B");

    public void testDefaultOrganizationColor() {
        int defaultColor = mDevicePolicyManager.getOrganizationColor(ADMIN_RECEIVER_COMPONENT);
        assertEquals("Default color returned: " + Integer.toHexString(defaultColor),
                DEFAULT_ORGANIZATION_COLOR, defaultColor);
    }

    public void testSetOrganizationColor() {
        int previousColor = mDevicePolicyManager.getOrganizationColor(ADMIN_RECEIVER_COMPONENT);

        try {
            final int[] colors = {
                Color.TRANSPARENT,
                Color.WHITE,
                Color.RED,
                Color.GREEN,
                Color.BLUE,
                0x7FFE5B35 /* HTML name: "Sunset orange". Opacity: 50%. */
            };

            for (int color : colors) {
                mDevicePolicyManager.setOrganizationColor(ADMIN_RECEIVER_COMPONENT, color);
                assertEquals(color | 0xFF000000 /* opacity always enforced to 100% */,
                        mDevicePolicyManager.getOrganizationColor(ADMIN_RECEIVER_COMPONENT));
            }
        } finally {
            // Put the organization color back how it was.
            mDevicePolicyManager.setOrganizationColor(ADMIN_RECEIVER_COMPONENT, previousColor);
        }
    }

    public void testSetOrGetOrganizationColorWithNullAdminFails() {
        try {
            mDevicePolicyManager.setOrganizationColor(null, Color.GRAY);
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }

        try {
            int color = mDevicePolicyManager.getOrganizationColor(null);
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }
    }

    public void testDefaultOrganizationNameIsNull() {
        CharSequence organizationName = mDevicePolicyManager.getOrganizationName(
                ADMIN_RECEIVER_COMPONENT);
        assertNull(organizationName);
    }

    public void testSetOrganizationName() {
        CharSequence previousOrganizationName = mDevicePolicyManager.getOrganizationName(
                ADMIN_RECEIVER_COMPONENT);

        try {
            final CharSequence name = "test-set-name";
            mDevicePolicyManager.setOrganizationName(ADMIN_RECEIVER_COMPONENT, name);
            CharSequence organizationName = mDevicePolicyManager.getOrganizationName(
                    ADMIN_RECEIVER_COMPONENT);
            assertEquals(name, organizationName);
        } finally {
            mDevicePolicyManager.setOrganizationName(ADMIN_RECEIVER_COMPONENT,
                    previousOrganizationName);
        }
    }

    public void testSetOrGetOrganizationNameWithNullAdminFails() {
        try {
            mDevicePolicyManager.setOrganizationName(null, "null-admin-fails");
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }

        try {
            mDevicePolicyManager.getOrganizationName(null);
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }
    }
}
