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

package test.instant.cookie;

import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class CookieTest {
    @Test
    public void testCookieUpdateAndRetrieval() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // We should be an instant app
        assertTrue(pm.isInstantApp());

        // The max cookie size is greater than zero
        assertTrue(pm.getInstantAppCookieMaxBytes() > 0);

        // Initially there is no cookie
        byte[] cookie = pm.getInstantAppCookie();
        assertTrue(cookie != null && cookie.length == 0);

        // Setting a cookie below max size should work
        pm.updateInstantAppCookie("1".getBytes());

        // Setting a cookie above max size should not work
        try {
            pm.updateInstantAppCookie(
                    new byte[pm.getInstantAppCookieMaxBytes() + 1]);
            fail("Shouldn't be able to set a cookie larger than max size");
        } catch (IllegalArgumentException e) {
            /* expected */
        }

        // Ensure cookie not modified
        assertEquals("1", new String(pm.getInstantAppCookie()));
    }

    @Test
    public void testCookiePersistedAcrossInstantInstalls1() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // Set a cookie to later check when reinstalled as instant app
        pm.updateInstantAppCookie("2".getBytes());
    }

    @Test
    public void testCookiePersistedAcrossInstantInstalls2() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // After the upgrade the cookie should be the same
        assertEquals("2", new String(pm.getInstantAppCookie()));
    }

    @Test
    public void testCookiePersistedUpgradeFromInstant1() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // Make sure we are an instant app
        assertTrue(pm.isInstantApp());

        // Set a cookie to later check when upgrade to a normal app
        pm.updateInstantAppCookie("3".getBytes());
    }

    @Test
    public void testCookiePersistedUpgradeFromInstant2() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // Make sure we are not an instant app
        assertFalse(pm.isInstantApp());

        // The cookie survives the upgrade to a normal app
        assertEquals("3", new String(pm.getInstantAppCookie()));
    }

    @Test
    public void testCookieResetOnNonInstantReinstall1() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // Set a cookie to later check when reinstalled as normal app
        pm.updateInstantAppCookie("4".getBytes());
    }

    @Test
    public void testCookieResetOnNonInstantReinstall2() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // The cookie should have been wiped if non-instant app is uninstalled
        byte[] cookie = pm.getInstantAppCookie();
        assertTrue(cookie != null && cookie.length == 0);
    }
}
