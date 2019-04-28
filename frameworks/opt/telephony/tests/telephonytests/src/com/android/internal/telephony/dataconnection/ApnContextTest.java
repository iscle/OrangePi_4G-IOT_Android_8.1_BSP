/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkRequest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.LocalLog;

import com.android.internal.R;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ApnContextTest extends TelephonyTest {

    @Mock
    NetworkConfig mNetworkConfig;
    @Mock
    ApnSetting mApnSetting;

    private ApnContext mApnContext;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mNetworkConfig.dependencyMet = true;
        mApnContext = new ApnContext(mPhone, PhoneConstants.APN_TYPE_DEFAULT, TAG, mNetworkConfig,
                mDcTracker);
    }

    @After
    public void tearDown() throws Exception {
        mApnContext = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSetGetApnSetting() throws Exception {
        mApnContext.setApnSetting(mApnSetting);
        assertEquals(mApnSetting, mApnContext.getApnSetting());
    }

    @Test
    @SmallTest
    public void testGetApnType() throws Exception {
        assertEquals(PhoneConstants.APN_TYPE_DEFAULT, mApnContext.getApnType());
    }

    @Test
    @SmallTest
    public void testConnectionGeneration() throws Exception {
        for (int i = 0; i < 100; i++) {
            mApnContext.incAndGetConnectionGeneration();
            assertEquals(i + 1, mApnContext.getConnectionGeneration());
        }
    }

    @Test
    @SmallTest
    public void testDataCallAsyncChannel() throws Exception {

        DcAsyncChannel dcAc = mock(DcAsyncChannel.class);

        mApnContext.setDataConnectionAc(dcAc);
        assertEquals(dcAc, mApnContext.getDcAc());
        mApnContext.releaseDataConnection("");
        assertNull(mApnContext.getDcAc());
        assertEquals(DctConstants.State.IDLE, mApnContext.getState());
    }

    @Test
    @SmallTest
    public void testDependencyMet() throws Exception {
        assertTrue(mApnContext.getDependencyMet());
        mApnContext.setDependencyMet(false);
        assertFalse(mApnContext.getDependencyMet());
    }

    @Test
    @SmallTest
    public void testReason() throws Exception {
        mApnContext.setReason("dataEnabled");
        assertEquals("dataEnabled", mApnContext.getReason());
        mApnContext.setReason("simLoaded");
        assertEquals("simLoaded", mApnContext.getReason());
    }

    @Test
    @SmallTest
    public void testState() throws Exception {
        mApnContext.setState(DctConstants.State.DISCONNECTING);
        assertEquals(DctConstants.State.DISCONNECTING, mApnContext.getState());
        mApnContext.setEnabled(true);
        mApnContext.setDependencyMet(true);
        assertFalse(mApnContext.isConnectable());

        mApnContext.setState(DctConstants.State.SCANNING);
        assertTrue(mApnContext.isConnectable());
        assertTrue(mApnContext.isConnectedOrConnecting());

        mApnContext.setState(DctConstants.State.FAILED);
        assertTrue(mApnContext.isDisconnected());
        mApnContext.setState(DctConstants.State.IDLE);
        assertTrue(mApnContext.isDisconnected());
    }

    @Test
    @SmallTest
    public void testNetworkRequest() throws Exception {
        LocalLog log = new LocalLog(3);
        NetworkRequest nr = new NetworkRequest.Builder().build();
        mApnContext.requestNetwork(nr, log);

        verify(mDcTracker, times(1)).setEnabled(eq(DctConstants.APN_DEFAULT_ID), eq(true));
        mApnContext.requestNetwork(nr, log);
        verify(mDcTracker, times(1)).setEnabled(eq(DctConstants.APN_DEFAULT_ID), eq(true));

        mApnContext.releaseNetwork(nr, log);
        verify(mDcTracker, times(1)).setEnabled(eq(DctConstants.APN_DEFAULT_ID), eq(false));
        mApnContext.releaseNetwork(nr, log);
        verify(mDcTracker, times(1)).setEnabled(eq(DctConstants.APN_DEFAULT_ID), eq(false));
    }

    @Test
    @SmallTest
    public void testConcurrentVoiceAndDataAllowed() throws Exception {
        mApnContext.setConcurrentVoiceAndDataAllowed(true);
        assertTrue(mApnContext.isConcurrentVoiceAndDataAllowed());
        mApnContext.setConcurrentVoiceAndDataAllowed(false);
        assertFalse(mApnContext.isConcurrentVoiceAndDataAllowed());
    }

    @Test
    @SmallTest
    public void testEnableDisable() throws Exception {
        mApnContext.setEnabled(true);
        assertTrue(mApnContext.isEnabled());
        mApnContext.setEnabled(false);
        assertFalse(mApnContext.isEnabled());
    }

    @Test
    @SmallTest
    public void testProvisionApn() throws Exception {
        mContextFixture.putResource(R.string.mobile_provisioning_apn, "fake_apn");

        ApnSetting myApn = new ApnSetting(
                2163,                   // id
                "44010",                // numeric
                "sp-mode",              // name
                "fake_apn",             // apn
                "",                     // proxy
                "",                     // port
                "",                     // mmsc
                "",                     // mmsproxy
                "",                     // mmsport
                "",                     // user
                "",                     // password
                -1,                     // authtype
                new String[]{"default", "supl"},     // types
                "IP",                   // protocol
                "IP",                   // roaming_protocol
                true,                   // carrier_enabled
                0,                      // bearer
                0,                      // bearer_bitmask
                0,                      // profile_id
                false,                  // modem_cognitive
                0,                      // max_conns
                0,                      // wait_time
                0,                      // max_conns_time
                0,                      // mtu
                "",                     // mvno_type
                "");                    // mnvo_match_data

        mApnContext.setApnSetting(myApn);
        assertTrue(mApnContext.isProvisioningApn());
        mApnContext.setApnSetting(mApnSetting);
        assertFalse(mApnContext.isProvisioningApn());
    }

    @Test
    @SmallTest
    public void testIsReady() throws Exception {
        mApnContext.setEnabled(true);
        mApnContext.setDependencyMet(true);
        assertTrue(mApnContext.isReady());

        mApnContext.setEnabled(false);
        assertFalse(mApnContext.isReady());

        mApnContext.setDependencyMet(false);
        assertFalse(mApnContext.isReady());

        mApnContext.setEnabled(true);
        assertFalse(mApnContext.isReady());
    }

    @Test
    @SmallTest
    public void testErrorCodeRetry() throws Exception {
        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_cell_retries_per_error_code,
                new String[]{"36,2"});
        mApnContext.resetErrorCodeRetries();

        assertFalse(mApnContext.restartOnError(36));
        assertTrue(mApnContext.restartOnError(36));
        assertFalse(mApnContext.restartOnError(37));
    }
}
