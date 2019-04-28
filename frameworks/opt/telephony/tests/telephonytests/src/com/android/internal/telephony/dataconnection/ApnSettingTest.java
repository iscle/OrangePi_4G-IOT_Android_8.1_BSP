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

import static com.android.internal.telephony.PhoneConstants.APN_TYPE_ALL;
import static com.android.internal.telephony.PhoneConstants.APN_TYPE_DEFAULT;
import static com.android.internal.telephony.PhoneConstants.APN_TYPE_HIPRI;
import static com.android.internal.telephony.PhoneConstants.APN_TYPE_IA;
import static com.android.internal.telephony.PhoneConstants.APN_TYPE_MMS;
import static com.android.internal.telephony.PhoneConstants.APN_TYPE_SUPL;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ApnSettingTest extends TelephonyTest {

    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    static ApnSetting createApnSetting(String[] apnTypes) {
        return createApnSettingInternal(apnTypes, true);
    }

    private static ApnSetting createDisabledApnSetting(String[] apnTypes) {
        return createApnSettingInternal(apnTypes, false);
    }

    private static ApnSetting createApnSettingInternal(String[] apnTypes, boolean carrierEnabled) {
        return new ApnSetting(
                2163,                   // id
                "44010",                // numeric
                "sp-mode",              // name
                "spmode.ne.jp",         // apn
                "",                     // proxy
                "",                     // port
                "",                     // mmsc
                "",                     // mmsproxy
                "",                     // mmsport
                "",                     // user
                "",                     // password
                -1,                     // authtype
                apnTypes,               // types
                "IP",                   // protocol
                "IP",                   // roaming_protocol
                carrierEnabled,         // carrier_enabled
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
    }

    private static void assertApnSettingsEqual(List<ApnSetting> a1, List<ApnSetting> a2) {
        assertEquals(a1.size(), a2.size());
        for (int i = 0; i < a1.size(); ++i) {
            assertApnSettingEqual(a1.get(i), a2.get(i));
        }
    }

    private static void assertApnSettingEqual(ApnSetting a1, ApnSetting a2) {
        assertEquals(a1.carrier, a2.carrier);
        assertEquals(a1.apn, a2.apn);
        assertEquals(a1.proxy, a2.proxy);
        assertEquals(a1.port, a2.port);
        assertEquals(a1.mmsc, a2.mmsc);
        assertEquals(a1.mmsProxy, a2.mmsProxy);
        assertEquals(a1.mmsPort, a2.mmsPort);
        assertEquals(a1.user, a2.user);
        assertEquals(a1.password, a2.password);
        assertEquals(a1.authType, a2.authType);
        assertEquals(a1.id, a2.id);
        assertEquals(a1.numeric, a2.numeric);
        assertEquals(a1.protocol, a2.protocol);
        assertEquals(a1.roamingProtocol, a2.roamingProtocol);
        assertEquals(a1.types.length, a2.types.length);
        int i;
        for (i = 0; i < a1.types.length; i++) {
            assertEquals(a1.types[i], a2.types[i]);
        }
        assertEquals(a1.carrierEnabled, a2.carrierEnabled);
        assertEquals(a1.bearerBitmask, a2.bearerBitmask);
        assertEquals(a1.profileId, a2.profileId);
        assertEquals(a1.modemCognitive, a2.modemCognitive);
        assertEquals(a1.maxConns, a2.maxConns);
        assertEquals(a1.waitTime, a2.waitTime);
        assertEquals(a1.maxConnsTime, a2.maxConnsTime);
        assertEquals(a1.mtu, a2.mtu);
        assertEquals(a1.mvnoType, a2.mvnoType);
        assertEquals(a1.mvnoMatchData, a2.mvnoMatchData);
    }

    @Test
    @SmallTest
    public void testFromString() throws Exception {
        String[] dunTypes = {"DUN"};
        String[] mmsTypes = {"mms", "*"};

        ApnSetting expectedApn;
        String testString;

        // A real-world v1 example string.
        testString = "Vodafone IT,web.omnitel.it,,,,,,,,,222,10,,DUN";
        expectedApn = new ApnSetting(
                -1, "22210", "Vodafone IT", "web.omnitel.it", "", "",
                "", "", "", "", "", 0, dunTypes, "IP", "IP", true, 0, 0,
                0, false, 0, 0, 0, 0, "", "");
        assertApnSettingEqual(expectedApn, ApnSetting.fromString(testString));

        // A v2 string.
        testString = "[ApnSettingV2] Name,apn,,,,,,,,,123,45,,mms|*,IPV6,IP,true,14";
        expectedApn = new ApnSetting(
                -1, "12345", "Name", "apn", "", "",
                "", "", "", "", "", 0, mmsTypes, "IPV6", "IP", true, 14, 0,
                0, false, 0, 0, 0, 0, "", "");
        assertApnSettingEqual(expectedApn, ApnSetting.fromString(testString));

        // A v2 string with spaces.
        testString = "[ApnSettingV2] Name,apn, ,,,,,,,,123,45,,mms|*,IPV6, IP,true,14";
        expectedApn = new ApnSetting(
                -1, "12345", "Name", "apn", "", "",
                "", "", "", "", "", 0, mmsTypes, "IPV6", "IP", true, 14, 0,
                0, false, 0, 0, 0, 0, "", "");
        assertApnSettingEqual(expectedApn, ApnSetting.fromString(testString));

        // A v3 string.
        testString = "[ApnSettingV3] Name,apn,,,,,,,,,123,45,,mms|*,IPV6,IP,true,14,,,,,,,spn,testspn";
        expectedApn = new ApnSetting(
                -1, "12345", "Name", "apn", "", "", "", "", "", "", "", 0, mmsTypes, "IPV6",
                "IP", true, 14, 0, 0, false, 0, 0, 0, 0, "spn", "testspn");
        assertApnSettingEqual(expectedApn, ApnSetting.fromString(testString));

        // Return no apn if insufficient fields given.
        testString = "[ApnSettingV3] Name,apn,,,,,,,,,123, 45,,mms|*";
        assertEquals(null, ApnSetting.fromString(testString));

        testString = "Name,apn,,,,,,,,,123, 45,";
        assertEquals(null, ApnSetting.fromString(testString));
    }

    @Test
    @SmallTest
    public void testArrayFromString() throws Exception {
        // Test a multiple v3 string.
        String testString =
                "[ApnSettingV3] Name,apn,,,,,,,,,123,45,,mms,IPV6,IP,true,14,,,,,,,spn,testspn";
        testString +=
                " ;[ApnSettingV3] Name1,apn1,,,,,,,,,123,46,,mms,IPV6,IP,true,12,,,,,,,gid,testGid";
        testString +=
                " ;[ApnSettingV3] Name1,apn2,,,,,,,,,123,46,,mms,IPV6,IP,true,12,,,,,,,,";
        List<ApnSetting> expectedApns = new ArrayList<ApnSetting>();
        expectedApns.add(new ApnSetting(
                -1, "12345", "Name", "apn", "", "", "", "", "", "", "", 0, new String[]{"mms"}, "IPV6",
                "IP", true, 14, 0, 0, false, 0, 0, 0, 0, "spn", "testspn"));
        expectedApns.add(new ApnSetting(
                -1, "12346", "Name1", "apn1", "", "", "", "", "", "", "", 0, new String[]{"mms"}, "IPV6",
                "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "gid", "testGid"));
        expectedApns.add(new ApnSetting(
                -1, "12346", "Name1", "apn2", "", "", "", "", "", "", "", 0, new String[]{"mms"}, "IPV6",
                "IP", true, 12, 0, 0, false, 0, 0, 0, 0, "", ""));
        assertApnSettingsEqual(expectedApns, ApnSetting.arrayFromString(testString));
    }

    @Test
    @SmallTest
    public void testToString() throws Exception {
        String[] types = {"default", "*"};
        ApnSetting apn = new ApnSetting(
                99, "12345", "Name", "apn", "proxy", "port",
                "mmsc", "mmsproxy", "mmsport", "user", "password", 0,
                types, "IPV6", "IP", true, 14, 0, 0, false, 0, 0, 0, 0, "", "");
        String expected = "[ApnSettingV3] Name, 99, 12345, apn, proxy, " +
                "mmsc, mmsproxy, mmsport, port, 0, default | *, " +
                "IPV6, IP, true, 14, 8192, 0, false, 0, 0, 0, 0, , , false";
        assertEquals(expected, apn.toString());
    }

    @Test
    @SmallTest
    public void testIsMetered() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(1).when(mPhone).getSubId();
        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_MMS}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_MMS, PhoneConstants.APN_TYPE_SUPL}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_DUN}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_SUPL}).
                isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IA, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DEFAULT, mPhone));
        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_MMS, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_SUPL, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_CBS, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DUN, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_FOTA, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_IA, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_HIPRI, mPhone));

        // Carrier config settings changes.
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});

        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DEFAULT, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_MMS, mPhone));
    }

    @Test
    @SmallTest
    public void testIsRoamingMetered() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(1).when(mPhone).getSubId();

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_MMS}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_MMS, PhoneConstants.APN_TYPE_SUPL}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_DUN}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_SUPL}).
                isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IA, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        // Carrier config settings changes.
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_FOTA});

        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DEFAULT, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_MMS, mPhone));
        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_FOTA, mPhone));
    }

    @Test
    @SmallTest
    public void testIsIwlanMetered() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_IWLAN_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS});
        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN).when(mServiceState)
                .getRilDataRadioTechnology();
        doReturn(1).when(mPhone).getSubId();

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_MMS}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_MMS, PhoneConstants.APN_TYPE_SUPL})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_DUN})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_SUPL})
                .isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IA, PhoneConstants.APN_TYPE_CBS})
                .isMetered(mPhone));

        // Carrier config settings changes.
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_IWLAN_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_FOTA});

        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DEFAULT, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_MMS, mPhone));
        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_FOTA, mPhone));
    }

    @Test
    @SmallTest
    public void testIsMeteredAnother() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_CBS});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(1).when(mPhone).getSubId();
        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_CBS}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_IA}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_IMS}).
                isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS}).isMetered(mPhone));
    }

    @Test
    @SmallTest
    public void testIsRoamingMeteredAnother() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_CBS});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(2).when(mPhone).getSubId();
        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_CBS}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_IA}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_IMS}).
                isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS}).isMetered(mPhone));

        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_SUPL, mPhone));
        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_CBS, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DEFAULT, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_MMS, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DUN, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_FOTA, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_IA, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_HIPRI, mPhone));
    }

    @Test
    @SmallTest
    public void testIsIwlanMeteredAnother() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_IWLAN_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_CBS});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN).when(mServiceState)
                .getRilDataRadioTechnology();
        doReturn(2).when(mPhone).getSubId();
        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_CBS})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_CBS}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_CBS})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_SUPL, PhoneConstants.APN_TYPE_IA})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_IMS})
                .isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS}).isMetered(mPhone));

        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_SUPL, mPhone));
        assertTrue(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_CBS, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DEFAULT, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_MMS, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_DUN, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_FOTA, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_IA, mPhone));
        assertFalse(ApnSetting.isMeteredApnType(PhoneConstants.APN_TYPE_HIPRI, mPhone));
    }

    @Test
    @SmallTest
    public void testIsMeteredNothingCharged() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(3).when(mPhone).getSubId();

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS, PhoneConstants.APN_TYPE_MMS})
                .isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_FOTA})
                .isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));
    }

    @Test
    @SmallTest
    public void testIsRoamingMeteredNothingCharged() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(3).when(mPhone).getSubId();

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS, PhoneConstants.APN_TYPE_MMS}).
                isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_FOTA}).
                isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).
                isMetered(mPhone));
    }

    @Test
    @SmallTest
    public void testIsIwlanMeteredNothingCharged() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_IWLAN_APN_TYPES_STRINGS,
                new String[]{});
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN).when(mServiceState)
                .getRilDataRadioTechnology();
        doReturn(3).when(mPhone).getSubId();

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS}).isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IMS, PhoneConstants.APN_TYPE_MMS})
                .isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_FOTA})
                .isMetered(mPhone));

        assertFalse(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));
    }

    @Test
    @SmallTest
    public void testIsMeteredNothingFree() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_ALL});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(4).when(mPhone).getSubId();

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IA, PhoneConstants.APN_TYPE_DUN}).
                isMetered(mPhone));

    }

    @Test
    @SmallTest
    public void testIsRoamingMeteredNothingFree() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_ALL});

        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(4).when(mPhone).getSubId();

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_CBS})
                .isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IA, PhoneConstants.APN_TYPE_DUN})
                .isMetered(mPhone));
    }

    @Test
    @SmallTest
    public void testIsIwlanMeteredNothingFree() throws Exception {
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_IWLAN_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_ALL});

        doReturn(false).when(mServiceState).getDataRoaming();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN).when(mServiceState)
                .getRilDataRadioTechnology();
        doReturn(4).when(mPhone).getSubId();

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_ALL}).isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_FOTA, PhoneConstants.APN_TYPE_CBS}).
                isMetered(mPhone));

        assertTrue(createApnSetting(
                new String[]{PhoneConstants.APN_TYPE_IA, PhoneConstants.APN_TYPE_DUN}).
                isMetered(mPhone));
    }

    @Test
    @SmallTest
    public void testCanHandleType() throws Exception {
        String types[] = {"mms"};

        // empty string replaced with ALL ('*') when loaded to db
        assertFalse(createApnSetting(new String[]{}).
                canHandleType(APN_TYPE_MMS));

        assertTrue(createApnSetting(new String[]{APN_TYPE_ALL}).
                canHandleType(APN_TYPE_MMS));

        assertFalse(createApnSetting(new String[]{APN_TYPE_DEFAULT}).
                canHandleType(APN_TYPE_MMS));

        assertTrue(createApnSetting(new String[]{"DEfAULT"}).
                canHandleType("defAult"));

        // Hipri is asymmetric
        assertTrue(createApnSetting(new String[]{APN_TYPE_DEFAULT}).
                canHandleType(APN_TYPE_HIPRI));
        assertFalse(createApnSetting(new String[]{APN_TYPE_HIPRI}).
                canHandleType(APN_TYPE_DEFAULT));


        assertTrue(createApnSetting(new String[]{APN_TYPE_DEFAULT, APN_TYPE_MMS}).
                canHandleType(APN_TYPE_DEFAULT));

        assertTrue(createApnSetting(new String[]{APN_TYPE_DEFAULT, APN_TYPE_MMS}).
                canHandleType(APN_TYPE_MMS));

        assertFalse(createApnSetting(new String[]{APN_TYPE_DEFAULT, APN_TYPE_MMS}).
                canHandleType(APN_TYPE_SUPL));

        // special IA case - doesn't match wildcards
        assertFalse(createApnSetting(new String[]{APN_TYPE_DEFAULT, APN_TYPE_MMS}).
                canHandleType(APN_TYPE_IA));
        assertFalse(createApnSetting(new String[]{APN_TYPE_ALL}).
                canHandleType(APN_TYPE_IA));
        assertFalse(createApnSetting(new String[]{APN_TYPE_ALL}).
                canHandleType("iA"));
        assertTrue(createApnSetting(new String[]{APN_TYPE_DEFAULT, APN_TYPE_MMS, APN_TYPE_IA}).
                canHandleType(APN_TYPE_IA));

        // check carrier disabled
        assertFalse(createDisabledApnSetting(new String[]{APN_TYPE_ALL}).
                canHandleType(APN_TYPE_MMS));
        assertFalse(createDisabledApnSetting(new String[]{"DEfAULT"}).
                canHandleType("defAult"));
        assertFalse(createDisabledApnSetting(new String[]{APN_TYPE_DEFAULT}).
                canHandleType(APN_TYPE_HIPRI));
        assertFalse(createDisabledApnSetting(new String[]{APN_TYPE_DEFAULT, APN_TYPE_MMS}).
                canHandleType(APN_TYPE_DEFAULT));
        assertFalse(createDisabledApnSetting(new String[]{APN_TYPE_DEFAULT, APN_TYPE_MMS}).
                canHandleType(APN_TYPE_MMS));
        assertFalse(createDisabledApnSetting(new String[]
                {APN_TYPE_DEFAULT, APN_TYPE_MMS, APN_TYPE_IA}).
                canHandleType(APN_TYPE_IA));
    }

    @Test
    @SmallTest
    public void testEquals() throws Exception {
        final int dummyInt = 1;
        final String dummyString = "dummy";
        final String[] dummyStringArr = new String[] {"dummy"};
        // base apn
        ApnSetting baseApn = createApnSetting(new String[] {"mms", "default"});
        Field[] fields = ApnSetting.class.getDeclaredFields();
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            f.setAccessible(true);
            ApnSetting testApn = null;
            if (int.class.equals(f.getType())) {
                testApn = new ApnSetting(baseApn);
                f.setInt(testApn, dummyInt + f.getInt(testApn));
            } else if (boolean.class.equals(f.getType())) {
                testApn = new ApnSetting(baseApn);
                f.setBoolean(testApn, !f.getBoolean(testApn));
            } else if (String.class.equals(f.getType())) {
                testApn = new ApnSetting(baseApn);
                f.set(testApn, dummyString);
            } else if (String[].class.equals(f.getType())) {
                testApn = new ApnSetting(baseApn);
                f.set(testApn, dummyStringArr);
            } else {
                fail("Unsupported field:" + f.getName());
            }
            if (testApn != null) {
                assertFalse(f.getName() + " is NOT checked", testApn.equals(baseApn));
            }
        }
    }

    @Test
    @SmallTest
    public void testEqualsRoamingProtocol() throws Exception {
        ApnSetting apn1 = new ApnSetting(
                1234,
                "310260",
                "",
                "ims",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                -1,
                 new String[]{"ims"},
                "IPV6",
                "",
                true,
                0,
                131071,
                0,
                false,
                0,
                0,
                0,
                1440,
                "",
                "");

        ApnSetting apn2 = new ApnSetting(
                1235,
                "310260",
                "",
                "ims",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                -1,
                new String[]{"ims"},
                "IPV6",
                "IPV6",
                true,
                0,
                131072,
                0,
                false,
                0,
                0,
                0,
                1440,
                "",
                "");

        assertTrue(apn1.equals(apn2, false));
        assertFalse(apn1.equals(apn2, true));
    }
}