/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.permission.cts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import java.net.InetAddress;

/**
 * Verify ConnectivityManager related methods without specific network state permissions.
 */
public class NoNetworkStatePermissionTest extends AndroidTestCase {
    private ConnectivityManager mConnectivityManager;
    private static final int TEST_NETWORK_TYPE = ConnectivityManager.TYPE_MOBILE;
    private static final String TEST_FEATURE = "enableHIPRI";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        assertNotNull(mConnectivityManager);
    }

    /**
     * Verify that ConnectivityManager#getActiveNetworkInfo() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_NETWORK_STATE}.
     */
    @SmallTest
    public void testGetActiveNetworkInfo() {
        try {
            mConnectivityManager.getActiveNetworkInfo();
            fail("ConnectivityManager.getActiveNetworkInfo didn't throw SecurityException as"
                    + " expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that ConnectivityManager#getNetworkInfo() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_NETWORK_STATE}.
     */
    @SmallTest
    public void testGetNetworkInfo() {
        try {
            mConnectivityManager.getNetworkInfo(TEST_NETWORK_TYPE);
            fail("ConnectivityManager.getNetworkInfo didn't throw SecurityException as"
                    + " expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that ConnectivityManager#getAllNetworkInfo() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_NETWORK_STATE}.
     */
    @SmallTest
    public void testGetAllNetworkInfo() {
        try {
            mConnectivityManager.getAllNetworkInfo();
            fail("ConnectivityManager.getAllNetworkInfo didn't throw SecurityException as"
                    + " expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    @SmallTest
    public void testSecurityExceptionFromDns() throws Exception {
        try {
            InetAddress.getByName("www.google.com");
            fail();
        } catch (SecurityException expected) {
        }
    }
}
