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

package com.android.server.wifi.hotspot2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.wifi.EAPConstants;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.wifi.IMSIParameter;
import com.android.server.wifi.hotspot2.anqp.CellularNetwork;
import com.android.server.wifi.hotspot2.anqp.DomainNameElement;
import com.android.server.wifi.hotspot2.anqp.NAIRealmData;
import com.android.server.wifi.hotspot2.anqp.NAIRealmElement;
import com.android.server.wifi.hotspot2.anqp.RoamingConsortiumElement;
import com.android.server.wifi.hotspot2.anqp.ThreeGPPNetworkElement;
import com.android.server.wifi.hotspot2.anqp.eap.AuthParam;
import com.android.server.wifi.hotspot2.anqp.eap.EAPMethod;
import com.android.server.wifi.hotspot2.anqp.eap.InnerAuthEAP;
import com.android.server.wifi.hotspot2.anqp.eap.NonEAPInnerAuth;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link com.android.server.wifi.hotspot2.ANQPMatcher}.
 */
@SmallTest
public class ANQPMatcherTest {
    /**
     * Verify that domain name match will fail when a null Domain Name ANQP element is provided.
     *
     * @throws Exception
     */
    @Test
    public void matchDomainNameWithNullElement() throws Exception {
        assertFalse(ANQPMatcher.matchDomainName(null, "test.com", null, null));
    }

    /**
     * Verify that domain name match will succeed when the specified FQDN matches a domain name
     * in the Domain Name ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchDomainNameUsingFQDN() throws Exception {
        String fqdn = "test.com";
        String[] domains = new String[] {fqdn};
        DomainNameElement element = new DomainNameElement(Arrays.asList(domains));
        assertTrue(ANQPMatcher.matchDomainName(element, fqdn, null, null));
    }

    /**
     * Verify that domain name match will succeed when the specified IMSI parameter and IMSI list
     * matches a 3GPP network domain in the Domain Name ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchDomainNameUsingIMSI() throws Exception {
        IMSIParameter imsiParam = new IMSIParameter("1234", true);
        List<String> simImsiList = Arrays.asList(new String[] {"123457890", "123498723"});
        // 3GPP network domain with MCC=123 and MNC=456.
        String[] domains = new String[] {"wlan.mnc457.mcc123.3gppnetwork.org"};
        DomainNameElement element = new DomainNameElement(Arrays.asList(domains));
        assertTrue(ANQPMatcher.matchDomainName(element, null, imsiParam, simImsiList));
    }

    /**
     * Verify that roaming consortium match will fail when a null Roaming Consortium ANQP
     * element is provided.
     *
     * @throws Exception
     */
    @Test
    public void matchRoamingConsortiumWithNullElement() throws Exception {
        assertFalse(ANQPMatcher.matchRoamingConsortium(null, new long[0]));
    }

    /**
     * Verify that a roaming consortium match will succeed when the specified OI matches
     * an OI in the Roaming Consortium ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchRoamingConsortium() throws Exception {
        long oi = 0x1234L;
        RoamingConsortiumElement element =
                new RoamingConsortiumElement(Arrays.asList(new Long[] {oi}));
        assertTrue(ANQPMatcher.matchRoamingConsortium(element, new long[] {oi}));
    }

    /**
     * Verify that an indeterminate match will be returned when matching a null NAI Realm
     * ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchNAIRealmWithNullElement() throws Exception {
        assertEquals(AuthMatch.INDETERMINATE, ANQPMatcher.matchNAIRealm(null, "test.com",
                EAPConstants.EAP_TLS, new InnerAuthEAP(EAPConstants.EAP_TTLS)));
    }

    /**
     * Verify that an indeterminate match will be returned when matching a NAI Realm
     * ANQP element contained no NAI realm data.
     *
     * @throws Exception
     */
    @Test
    public void matchNAIRealmWithEmtpyRealmData() throws Exception {
        NAIRealmElement element = new NAIRealmElement(new ArrayList<NAIRealmData>());
        assertEquals(AuthMatch.INDETERMINATE, ANQPMatcher.matchNAIRealm(element, "test.com",
                EAPConstants.EAP_TLS, null));
    }

    /**
     * Verify that a realm match will be returned when the specified realm matches a realm
     * in the NAI Realm ANQP element with no EAP methods.
     *
     * @throws Exception
     */
    @Test
    public void matchNAIRealmWithRealmMatch() throws Exception {
        String realm = "test.com";
        NAIRealmData realmData = new NAIRealmData(
                Arrays.asList(new String[] {realm}), new ArrayList<EAPMethod>());
        NAIRealmElement element = new NAIRealmElement(
                Arrays.asList(new NAIRealmData[] {realmData}));
        assertEquals(AuthMatch.REALM, ANQPMatcher.matchNAIRealm(element, realm,
                EAPConstants.EAP_TLS, null));
    }

    /**
     * Verify that a realm and method match will be returned when the specified realm and EAP
     * method matches a realm in the NAI Realm ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchNAIRealmWithRealmMethodMatch() throws Exception {
        // Test data.
        String realm = "test.com";
        int eapMethodID = EAPConstants.EAP_TLS;

        // Setup NAI Realm element.
        EAPMethod method = new EAPMethod(eapMethodID, new HashMap<Integer, Set<AuthParam>>());
        NAIRealmData realmData = new NAIRealmData(
                Arrays.asList(new String[] {realm}), Arrays.asList(new EAPMethod[] {method}));
        NAIRealmElement element = new NAIRealmElement(
                Arrays.asList(new NAIRealmData[] {realmData}));

        assertEquals(AuthMatch.REALM | AuthMatch.METHOD,
                ANQPMatcher.matchNAIRealm(element, realm, eapMethodID, null));
    }

    /**
     * Verify that an exact match will be returned when the specified realm, EAP
     * method, and the authentication parameter matches a realm with the associated EAP method and
     * authentication parameter in the NAI Realm ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchNAIRealmWithExactMatch() throws Exception {
        // Test data.
        String realm = "test.com";
        int eapMethodID = EAPConstants.EAP_TTLS;
        NonEAPInnerAuth authParam = new NonEAPInnerAuth(NonEAPInnerAuth.AUTH_TYPE_MSCHAP);
        Set<AuthParam> authSet = new HashSet<>();
        authSet.add(authParam);
        Map<Integer, Set<AuthParam>> authMap = new HashMap<>();
        authMap.put(authParam.getAuthTypeID(), authSet);

        // Setup NAI Realm element.
        EAPMethod method = new EAPMethod(eapMethodID, authMap);
        NAIRealmData realmData = new NAIRealmData(
                Arrays.asList(new String[] {realm}), Arrays.asList(new EAPMethod[] {method}));
        NAIRealmElement element = new NAIRealmElement(
                Arrays.asList(new NAIRealmData[] {realmData}));

        assertEquals(AuthMatch.EXACT,
                ANQPMatcher.matchNAIRealm(element, realm, eapMethodID, authParam));
    }

    /**
     * Verify that a mismatch (AuthMatch.NONE) will be returned when the specified EAP method
     * doesn't match with the corresponding EAP method in the NAI Realm ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchNAIRealmWithEAPMethodMismatch() throws Exception {
        // Test data.
        String realm = "test.com";
        int eapMethodID = EAPConstants.EAP_TTLS;
        NonEAPInnerAuth authParam = new NonEAPInnerAuth(NonEAPInnerAuth.AUTH_TYPE_MSCHAP);
        Set<AuthParam> authSet = new HashSet<>();
        authSet.add(authParam);
        Map<Integer, Set<AuthParam>> authMap = new HashMap<>();
        authMap.put(authParam.getAuthTypeID(), authSet);

        // Setup NAI Realm element.
        EAPMethod method = new EAPMethod(eapMethodID, authMap);
        NAIRealmData realmData = new NAIRealmData(
                Arrays.asList(new String[] {realm}), Arrays.asList(new EAPMethod[] {method}));
        NAIRealmElement element = new NAIRealmElement(
                Arrays.asList(new NAIRealmData[] {realmData}));

        assertEquals(AuthMatch.NONE,
                ANQPMatcher.matchNAIRealm(element, realm, EAPConstants.EAP_TLS, null));
    }

    /**
     * Verify that a mismatch (AuthMatch.NONE) will be returned when the specified authentication
     * parameter doesn't match with the corresponding authentication parameter in the NAI Realm
     * ANQP element.
     *
     * @throws Exception
     */
    @Test
    public void matchNAIRealmWithAuthTypeMismatch() throws Exception {
        // Test data.
        String realm = "test.com";
        int eapMethodID = EAPConstants.EAP_TTLS;
        NonEAPInnerAuth authParam = new NonEAPInnerAuth(NonEAPInnerAuth.AUTH_TYPE_MSCHAP);
        Set<AuthParam> authSet = new HashSet<>();
        authSet.add(authParam);
        Map<Integer, Set<AuthParam>> authMap = new HashMap<>();
        authMap.put(authParam.getAuthTypeID(), authSet);

        // Setup NAI Realm element.
        EAPMethod method = new EAPMethod(eapMethodID, authMap);
        NAIRealmData realmData = new NAIRealmData(
                Arrays.asList(new String[] {realm}), Arrays.asList(new EAPMethod[] {method}));
        NAIRealmElement element = new NAIRealmElement(
                Arrays.asList(new NAIRealmData[] {realmData}));

        // Mismatch in authentication type.
        assertEquals(AuthMatch.NONE,
                ANQPMatcher.matchNAIRealm(element, realm, EAPConstants.EAP_TTLS,
                        new NonEAPInnerAuth(NonEAPInnerAuth.AUTH_TYPE_PAP)));
    }

    /**
     * Verify that 3GPP Network match will fail when a null element is provided.
     *
     * @throws Exception
     */
    @Test
    public void matchThreeGPPNetworkWithNullElement() throws Exception {
        IMSIParameter imsiParam = new IMSIParameter("1234", true);
        List<String> simImsiList = Arrays.asList(new String[] {"123456789", "123498723"});
        assertFalse(ANQPMatcher.matchThreeGPPNetwork(null, imsiParam, simImsiList));
    }

    /**
     * Verify that 3GPP network will succeed when the given 3GPP Network ANQP element contained
     * a MCC-MNC that matches the both IMSI parameter and an IMSI from the IMSI list.
     *
     * @throws Exception
     */
    @Test
    public void matchThreeGPPNetwork() throws Exception {
        IMSIParameter imsiParam = new IMSIParameter("1234", true);
        List<String> simImsiList = Arrays.asList(new String[] {"123456789", "123498723"});

        CellularNetwork network = new CellularNetwork(Arrays.asList(new String[] {"123456"}));
        ThreeGPPNetworkElement element =
                new ThreeGPPNetworkElement(Arrays.asList(new CellularNetwork[] {network}));
        // The MCC-MNC provided in 3GPP Network ANQP element matches both IMSI parameter
        // and an IMSI from the installed SIM card.
        assertTrue(ANQPMatcher.matchThreeGPPNetwork(element, imsiParam, simImsiList));
    }

    /**
     * Verify that 3GPP network will failed when the given 3GPP Network ANQP element contained
     * a MCC-MNC that match the IMSI parameter but not the IMSI list.
     *
     * @throws Exception
     */
    @Test
    public void matchThreeGPPNetworkWithoutSimImsiMatch() throws Exception {
        IMSIParameter imsiParam = new IMSIParameter("1234", true);
        List<String> simImsiList = Arrays.asList(new String[] {"123457890", "123498723"});

        CellularNetwork network = new CellularNetwork(Arrays.asList(new String[] {"123456"}));
        ThreeGPPNetworkElement element =
                new ThreeGPPNetworkElement(Arrays.asList(new CellularNetwork[] {network}));
        // The MCC-MNC provided in 3GPP Network ANQP element doesn't match any of the IMSIs
        // from the installed SIM card.
        assertFalse(ANQPMatcher.matchThreeGPPNetwork(element, imsiParam, simImsiList));
    }

    /**
     * Verify that 3GPP network will failed when the given 3GPP Network ANQP element contained
     * a MCC-MNC that doesn't match with the IMSI parameter.
     *
     * @throws Exception
     */
    @Test
    public void matchThreeGPPNetworkWithImsiParamMismatch() throws Exception {
        IMSIParameter imsiParam = new IMSIParameter("1234", true);
        List<String> simImsiList = Arrays.asList(new String[] {"123457890", "123498723"});

        CellularNetwork network = new CellularNetwork(Arrays.asList(new String[] {"123356"}));
        ThreeGPPNetworkElement element =
                new ThreeGPPNetworkElement(Arrays.asList(new CellularNetwork[] {network}));
        // The MCC-MNC provided in 3GPP Network ANQP element doesn't match the IMSI parameter.
        assertFalse(ANQPMatcher.matchThreeGPPNetwork(element, imsiParam, simImsiList));
    }
}
