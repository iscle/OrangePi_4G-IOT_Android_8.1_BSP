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

package com.android.server.wifi.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;

import com.android.server.wifi.WifiConfigurationTestUtil;
import com.android.server.wifi.util.TelephonyUtil.SimAuthRequestData;
import com.android.server.wifi.util.TelephonyUtil.SimAuthResponseData;

import org.junit.Test;

/**
 * Unit tests for {@link com.android.server.wifi.util.TelephonyUtil}.
 */
@SmallTest
public class TelephonyUtilTest {
    @Test
    public void getSimIdentityEapSim() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getSubscriberId()).thenReturn("3214561234567890");
        when(tm.getSimState()).thenReturn(TelephonyManager.SIM_STATE_READY);
        when(tm.getSimOperator()).thenReturn("321456");
        assertEquals("13214561234567890@wlan.mnc456.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE)));
        assertEquals("13214561234567890@wlan.mnc456.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.SIM)));
    }

    @Test
    public void getSimIdentityEapAka() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getSubscriberId()).thenReturn("3214561234567890");
        when(tm.getSimState()).thenReturn(TelephonyManager.SIM_STATE_READY);
        when(tm.getSimOperator()).thenReturn("321456");
        assertEquals("03214561234567890@wlan.mnc456.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE)));
        assertEquals("03214561234567890@wlan.mnc456.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.AKA)));
    }

    @Test
    public void getSimIdentityEapAkaPrime() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getSubscriberId()).thenReturn("3214561234567890");
        when(tm.getSimState()).thenReturn(TelephonyManager.SIM_STATE_READY);
        when(tm.getSimOperator()).thenReturn("321456");
        assertEquals("63214561234567890@wlan.mnc456.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.AKA_PRIME, WifiEnterpriseConfig.Phase2.NONE)));
        assertEquals("63214561234567890@wlan.mnc456.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.AKA_PRIME)));
    }

    @Test
    public void getSimIdentity2DigitMnc() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getSubscriberId()).thenReturn("321560123456789");
        when(tm.getSimState()).thenReturn(TelephonyManager.SIM_STATE_READY);
        when(tm.getSimOperator()).thenReturn("32156");
        assertEquals("1321560123456789@wlan.mnc056.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE)));
    }

    @Test
    public void getSimIdentityUnknownMccMnc() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getSubscriberId()).thenReturn("3214560123456789");
        when(tm.getSimState()).thenReturn(TelephonyManager.SIM_STATE_UNKNOWN);
        when(tm.getSimOperator()).thenReturn(null);
        assertEquals("13214560123456789@wlan.mnc456.mcc321.3gppnetwork.org",
                TelephonyUtil.getSimIdentity(tm, WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE)));
    }

    @Test
    public void getSimIdentityWithNoTelephonyManager() {
        assertEquals(null, TelephonyUtil.getSimIdentity(null,
                WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE)));
    }

    @Test
    public void getSimIdentityNonTelephonyConfig() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getSubscriberId()).thenReturn("321560123456789");
        when(tm.getSimState()).thenReturn(TelephonyManager.SIM_STATE_READY);
        when(tm.getSimOperator()).thenReturn("32156");
        assertEquals(null, TelephonyUtil.getSimIdentity(tm,
                WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.TTLS, WifiEnterpriseConfig.Phase2.SIM)));
        assertEquals(null, TelephonyUtil.getSimIdentity(tm,
                WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.MSCHAPV2)));
        assertEquals(null, TelephonyUtil.getSimIdentity(tm,
                WifiConfigurationTestUtil.createEapNetwork(
                        WifiEnterpriseConfig.Eap.TLS, WifiEnterpriseConfig.Phase2.NONE)));
        assertEquals(null, TelephonyUtil.getSimIdentity(tm, new WifiConfiguration()));
    }

    @Test
    public void isSimConfig() {
        assertFalse(TelephonyUtil.isSimConfig(null));
        assertFalse(TelephonyUtil.isSimConfig(new WifiConfiguration()));
        assertFalse(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createOpenNetwork()));
        assertFalse(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createWepNetwork()));
        assertFalse(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createPskNetwork()));
        assertFalse(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TTLS, WifiEnterpriseConfig.Phase2.SIM)));
        assertFalse(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TLS, WifiEnterpriseConfig.Phase2.NONE)));
        assertFalse(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.MSCHAPV2)));
        assertTrue(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE)));
        assertTrue(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE)));
        assertTrue(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.AKA_PRIME, WifiEnterpriseConfig.Phase2.NONE)));
        assertTrue(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.SIM)));
        assertTrue(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.AKA)));
        assertTrue(TelephonyUtil.isSimConfig(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.AKA_PRIME)));
    }

    /**
     * Produce a base64 encoded length byte + data.
     */
    private static String createSimChallengeRequest(byte[] challengeValue) {
        byte[] challengeLengthAndValue = new byte[challengeValue.length + 1];
        challengeLengthAndValue[0] = (byte) challengeValue.length;
        for (int i = 0; i < challengeValue.length; ++i) {
            challengeLengthAndValue[i + 1] = challengeValue[i];
        }
        return Base64.encodeToString(challengeLengthAndValue, android.util.Base64.NO_WRAP);
    }

    /**
     * Produce a base64 encoded sres length byte + sres + kc length byte + kc.
     */
    private static String createGsmSimAuthResponse(byte[] sresValue, byte[] kcValue) {
        int overallLength = sresValue.length + kcValue.length + 2;
        byte[] result = new byte[sresValue.length + kcValue.length + 2];
        int idx = 0;
        result[idx++] = (byte) sresValue.length;
        for (int i = 0; i < sresValue.length; ++i) {
            result[idx++] = sresValue[i];
        }
        result[idx++] = (byte) kcValue.length;
        for (int i = 0; i < kcValue.length; ++i) {
            result[idx++] = kcValue[i];
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    @Test
    public void getGsmSimAuthResponseInvalidRequest() {
        TelephonyManager tm = mock(TelephonyManager.class);
        final String[] invalidRequests = { null, "", "XXXX" };
        assertEquals("", TelephonyUtil.getGsmSimAuthResponse(invalidRequests, tm));
    }

    @Test
    public void getGsmSimAuthResponseFailedSimResponse() {
        TelephonyManager tm = mock(TelephonyManager.class);
        final String[] failedRequests = { "5E5F" };
        when(tm.getIccAuthentication(anyInt(), anyInt(),
                eq(createSimChallengeRequest(new byte[] { 0x5e, 0x5f })))).thenReturn(null);

        assertEquals(null, TelephonyUtil.getGsmSimAuthResponse(failedRequests, tm));
    }

    @Test
    public void getGsmSimAuthResponseUsim() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x1b, 0x2b })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x1D, 0x2C },
                                new byte[] { 0x3B, 0x4A }));
        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x01, 0x22 })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x11, 0x11 },
                                new byte[] { 0x12, 0x34 }));

        assertEquals(":3b4a:1d2c:1234:1111", TelephonyUtil.getGsmSimAuthResponse(
                        new String[] { "1B2B", "0122" }, tm));
    }

    @Test
    public void getGsmSimAuthResponseSimpleSim() {
        TelephonyManager tm = mock(TelephonyManager.class);
        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x1a, 0x2b })))
                .thenReturn(null);
        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_SIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x1a, 0x2b })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x1D, 0x2C },
                                new byte[] { 0x3B, 0x4A }));
        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x01, 0x23 })))
                .thenReturn(null);
        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_SIM,
                        TelephonyManager.AUTHTYPE_EAP_SIM,
                        createSimChallengeRequest(new byte[] { 0x01, 0x23 })))
                .thenReturn(createGsmSimAuthResponse(new byte[] { 0x33, 0x22 },
                                new byte[] { 0x11, 0x00 }));

        assertEquals(":3b4a:1d2c:1100:3322", TelephonyUtil.getGsmSimAuthResponse(
                        new String[] { "1A2B", "0123" }, tm));
    }

    /**
     * Produce a base64 encoded tag + res length byte + res + ck length byte + ck + ik length byte +
     * ik.
     */
    private static String create3GSimAuthUmtsAuthResponse(byte[] res, byte[] ck, byte[] ik) {
        byte[] result = new byte[res.length + ck.length + ik.length + 4];
        int idx = 0;
        result[idx++] = (byte) 0xdb;
        result[idx++] = (byte) res.length;
        for (int i = 0; i < res.length; ++i) {
            result[idx++] = res[i];
        }
        result[idx++] = (byte) ck.length;
        for (int i = 0; i < ck.length; ++i) {
            result[idx++] = ck[i];
        }
        result[idx++] = (byte) ik.length;
        for (int i = 0; i < ik.length; ++i) {
            result[idx++] = ik[i];
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    private static String create3GSimAuthUmtsAutsResponse(byte[] auts) {
        byte[] result = new byte[auts.length + 2];
        int idx = 0;
        result[idx++] = (byte) 0xdc;
        result[idx++] = (byte) auts.length;
        for (int i = 0; i < auts.length; ++i) {
            result[idx++] = auts[i];
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    @Test
    public void get3GAuthResponseInvalidRequest() {
        TelephonyManager tm = mock(TelephonyManager.class);
        assertEquals(null, TelephonyUtil.get3GAuthResponse(
                        new SimAuthRequestData(0, 0, "SSID", new String[] {"0123"}), tm));
        assertEquals(null, TelephonyUtil.get3GAuthResponse(
                        new SimAuthRequestData(0, 0, "SSID", new String[] {"xyz2", "1234"}), tm));
        verifyNoMoreInteractions(tm);
    }

    @Test
    public void get3GAuthResponseNullIccAuthentication() {
        TelephonyManager tm = mock(TelephonyManager.class);

        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn")).thenReturn(null);

        SimAuthResponseData response = TelephonyUtil.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[] {"0123", "4567"}), tm);
        assertNull(response);
    }

    @Test
    public void get3GAuthResponseIccAuthenticationTooShort() {
        TelephonyManager tm = mock(TelephonyManager.class);

        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(Base64.encodeToString(new byte[] {(byte) 0xdc}, Base64.NO_WRAP));

        SimAuthResponseData response = TelephonyUtil.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[] {"0123", "4567"}), tm);
        assertNull(response);
    }

    @Test
    public void get3GAuthResponseBadTag() {
        TelephonyManager tm = mock(TelephonyManager.class);

        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(Base64.encodeToString(new byte[] {0x31, 0x1, 0x2, 0x3, 0x4},
                                Base64.NO_WRAP));

        SimAuthResponseData response = TelephonyUtil.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[] {"0123", "4567"}), tm);
        assertNull(response);
    }

    @Test
    public void get3GAuthResponseUmtsAuth() {
        TelephonyManager tm = mock(TelephonyManager.class);

        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(create3GSimAuthUmtsAuthResponse(new byte[] {0x11, 0x12},
                                new byte[] {0x21, 0x22, 0x23}, new byte[] {0x31}));

        SimAuthResponseData response = TelephonyUtil.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[] {"0123", "4567"}), tm);
        assertNotNull(response);
        assertEquals("UMTS-AUTH", response.type);
        assertEquals(":31:212223:1112", response.response);
    }

    @Test
    public void get3GAuthResponseUmtsAuts() {
        TelephonyManager tm = mock(TelephonyManager.class);

        when(tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "AgEjAkVn"))
                .thenReturn(create3GSimAuthUmtsAutsResponse(new byte[] {0x22, 0x33}));

        SimAuthResponseData response = TelephonyUtil.get3GAuthResponse(
                new SimAuthRequestData(0, 0, "SSID", new String[] {"0123", "4567"}), tm);
        assertNotNull(response);
        assertEquals("UMTS-AUTS", response.type);
        assertEquals(":2233", response.response);
    }
}
