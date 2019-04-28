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

package com.android.internal.telephony.dataconnection;

import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_ADDRESS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_DNS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_GATEWAY;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_IFNAME;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_PCSCF_ADDRESS;

import static org.junit.Assert.assertEquals;

import android.net.LinkProperties;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.DataCallResponse.SetupResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DataCallResponseTest extends TelephonyTest {

    DataCallResponse mDcResponse;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mDcResponse = new DataCallResponse(0, -1, 1, 2, "IP", FAKE_IFNAME, FAKE_ADDRESS,
                FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSetLinkProperties() throws Exception {
        LinkProperties linkProperties = new LinkProperties();
        assertEquals(SetupResult.SUCCESS,
                mDcResponse.setLinkProperties(linkProperties, true));
        logd(linkProperties.toString());
        assertEquals(mDcResponse.ifname, linkProperties.getInterfaceName());
        assertEquals(mDcResponse.addresses.length, linkProperties.getAddresses().size());
        for (int i = 0; i < mDcResponse.addresses.length; ++i) {
            assertEquals(mDcResponse.addresses[i],
                    linkProperties.getLinkAddresses().get(i).getAddress().getHostAddress());
        }

        assertEquals(mDcResponse.dnses.length, linkProperties.getDnsServers().size());
        for (int i = 0; i < mDcResponse.dnses.length; ++i) {
            assertEquals("i = " + i, mDcResponse.dnses[i],
                    linkProperties.getDnsServers().get(i).getHostAddress());
        }

        assertEquals(mDcResponse.gateways.length, linkProperties.getRoutes().size());
        for (int i = 0; i < mDcResponse.gateways.length; ++i) {
            assertEquals("i = " + i, mDcResponse.gateways[i],
                    linkProperties.getRoutes().get(i).getGateway().getHostAddress());
        }

        assertEquals(mDcResponse.mtu, linkProperties.getMtu());
    }

    @Test
    @SmallTest
    public void testSetLinkPropertiesInvalidAddress() throws Exception {

        // 224.224.224.224 is an invalid address.
        mDcResponse = new DataCallResponse(0, -1, 1, 2, "IP", FAKE_IFNAME, "224.224.224.224",
                FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);

        LinkProperties linkProperties = new LinkProperties();
        assertEquals(SetupResult.ERR_UnacceptableParameter,
                mDcResponse.setLinkProperties(linkProperties, true));
    }
}