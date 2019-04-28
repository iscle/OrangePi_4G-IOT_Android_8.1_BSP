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

package com.android.internal.telephony;

import android.os.Parcel;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import junit.framework.TestCase;

import java.util.ArrayList;

public class ServiceStateTest extends TestCase {

    @SmallTest
    public void testRoaming() {
        ServiceState ss = new ServiceState();

        ss.setCdmaDefaultRoamingIndicator(1);
        assertEquals(1, ss.getCdmaDefaultRoamingIndicator());

        ss.setCdmaEriIconIndex(2);
        assertEquals(2, ss.getCdmaEriIconIndex());

        ss.setCdmaEriIconMode(3);
        assertEquals(3, ss.getCdmaEriIconMode());

        ss.setCdmaRoamingIndicator(4);
        assertEquals(4, ss.getCdmaRoamingIndicator());

        ss.setDataRoamingType(ServiceState.ROAMING_TYPE_DOMESTIC);
        assertTrue(ss.getDataRoaming());
        assertEquals(ServiceState.ROAMING_TYPE_DOMESTIC, ss.getDataRoamingType());

        ss.setDataRoamingFromRegistration(true);
        assertTrue(ss.getDataRoamingFromRegistration());

        ss.setVoiceRoamingType(ServiceState.ROAMING_TYPE_DOMESTIC);
        assertTrue(ss.getVoiceRoaming());
        assertEquals(ServiceState.ROAMING_TYPE_DOMESTIC, ss.getVoiceRoamingType());
    }

    @SmallTest
    public void testRegState() {
        ServiceState ss = new ServiceState();

        ss.setDataRegState(ServiceState.STATE_IN_SERVICE);
        assertEquals(ServiceState.STATE_IN_SERVICE, ss.getDataRegState());

        ss.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        assertEquals(ServiceState.STATE_IN_SERVICE, ss.getVoiceRegState());
    }

    @SmallTest
    public void testRAT() {
        ServiceState ss = new ServiceState();

        ss.setRilDataRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
        assertEquals(ServiceState.RIL_RADIO_TECHNOLOGY_LTE, ss.getRilDataRadioTechnology());
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, ss.getDataNetworkType());

        ss.setRilVoiceRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        assertEquals(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT, ss.getRilVoiceRadioTechnology());
        assertEquals(TelephonyManager.NETWORK_TYPE_1xRTT, ss.getVoiceNetworkType());

        ArrayList<Pair<Integer, Boolean>> rats = new ArrayList<Pair<Integer, Boolean>>();

        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_IS95A, true));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_IS95B, true));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT, true));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0, true));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A, true));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B, true));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD, true));

        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_GPRS, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_EDGE, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_UMTS, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_HSPA, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_LTE, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_GSM, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA, false));
        rats.add(new Pair<Integer, Boolean>(ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN, false));

        for (Pair<Integer, Boolean> rat : rats) {
            boolean isCdma = rat.second;

            if (isCdma) {
                assertTrue("RAT " + rat + " should be CDMA", ServiceState.isCdma(rat.first));
                assertFalse("RAT " + rat + " should not be GSM", ServiceState.isGsm(rat.first));
            } else {
                assertFalse("RAT " + rat + " should not be CDMA", ServiceState.isCdma(rat.first));
                assertTrue("RAT " + rat + " should be GSM", ServiceState.isGsm(rat.first));
            }
        }
    }

    @SmallTest
    public void testOperatorName() {
        ServiceState ss = new ServiceState();

        ss.setDataOperatorAlphaLong("abc");
        assertEquals("abc", ss.getDataOperatorAlphaLong());

        ss.setDataOperatorName("def", "xyz", "123456");
        assertEquals("xyz", ss.getDataOperatorAlphaShort());

        ss.setOperatorName("long", "short", "numeric");
        assertEquals("long", ss.getVoiceOperatorAlphaLong());
        assertEquals("short", ss.getVoiceOperatorAlphaShort());
        assertEquals("numeric", ss.getVoiceOperatorNumeric());
        assertEquals("long", ss.getDataOperatorAlphaLong());
        assertEquals("short", ss.getDataOperatorAlphaShort());
        assertEquals("numeric", ss.getDataOperatorNumeric());
        assertEquals("long", ss.getOperatorAlpha());

        ss.setOperatorName("", "short", "");
        assertEquals("short", ss.getOperatorAlpha());
    }

    @SmallTest
    public void testMisc() {
        ServiceState ss = new ServiceState();

        ss.setCssIndicator(100);
        assertEquals(1, ss.getCssIndicator());

        ss.setIsManualSelection(true);
        assertTrue(ss.getIsManualSelection());

        ss.setSystemAndNetworkId(123, 456);
        assertEquals(123, ss.getSystemId());
        assertEquals(456, ss.getNetworkId());

        ss.setEmergencyOnly(true);
        assertTrue(ss.isEmergencyOnly());
    }

    @SmallTest
    public void testParcel() {

        ServiceState ss = new ServiceState();
        ss.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        ss.setDataRegState(ServiceState.STATE_OUT_OF_SERVICE);
        ss.setVoiceRoamingType(ServiceState.ROAMING_TYPE_INTERNATIONAL);
        ss.setDataRoamingType(ServiceState.ROAMING_TYPE_UNKNOWN);
        ss.setOperatorName("long", "short", "numeric");
        ss.setIsManualSelection(true);
        ss.setRilVoiceRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        ss.setRilDataRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0);
        ss.setCssIndicator(1);
        ss.setSystemAndNetworkId(2, 3);
        ss.setCdmaRoamingIndicator(4);
        ss.setCdmaDefaultRoamingIndicator(5);
        ss.setCdmaEriIconIndex(6);
        ss.setCdmaEriIconMode(7);
        ss.setEmergencyOnly(true);
        ss.setDataRoamingFromRegistration(true);

        Parcel p = Parcel.obtain();
        ss.writeToParcel(p, 0);
        p.setDataPosition(0);

        ServiceState newSs = new ServiceState(p);
        assertEquals(ss, newSs);
    }
}