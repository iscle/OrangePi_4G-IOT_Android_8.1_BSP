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

package android.net.wifi.cts;

import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.omadm.PpsMoParser;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.test.AndroidTestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CTS tests for PPS MO (PerProviderSubscription Management Object) XML string parsing API.
 */
public class PpsMoParserTest extends AndroidTestCase {
    private static final String PPS_MO_XML_FILE = "assets/PerProviderSubscription.xml";

    /**
     * Read the content of the given resource file into a String.
     *
     * @param filename String name of the file
     * @return String
     * @throws IOException
     */
    private String loadResourceFile(String filename) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        return builder.toString();
    }

    /**
     * Generate a {@link PasspointConfiguration} that matches the configuration specified in the
     * XML file {@link #PPS_MO_XML_FILE}.
     *
     * @return {@link PasspointConfiguration}
     */
    private PasspointConfiguration generateConfigurationFromPPSMOTree() throws Exception {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        byte[] certFingerprint = new byte[32];
        Arrays.fill(certFingerprint, (byte) 0x1f);

        PasspointConfiguration config = new PasspointConfiguration();

        // HomeSP configuration.
        HomeSp homeSp = new HomeSp();
        homeSp.setFriendlyName("Century House");
        assertEquals("Century House", homeSp.getFriendlyName());
        homeSp.setFqdn("mi6.co.uk");
        assertEquals("mi6.co.uk", homeSp.getFqdn());
        homeSp.setRoamingConsortiumOis(new long[] {0x112233L, 0x445566L});
        assertTrue(Arrays.equals(new long[] {0x112233L, 0x445566L},
                homeSp.getRoamingConsortiumOis()));
        config.setHomeSp(homeSp);
        assertEquals(homeSp, config.getHomeSp());

        // Credential configuration.
        Credential credential = new Credential();
        credential.setRealm("shaken.stirred.com");
        assertEquals("shaken.stirred.com", credential.getRealm());
        Credential.UserCredential userCredential = new Credential.UserCredential();
        userCredential.setUsername("james");
        assertEquals("james", userCredential.getUsername());
        userCredential.setPassword("Ym9uZDAwNw==");
        assertEquals("Ym9uZDAwNw==", userCredential.getPassword());
        userCredential.setEapType(21);
        assertEquals(21, userCredential.getEapType());
        userCredential.setNonEapInnerMethod("MS-CHAP-V2");
        assertEquals("MS-CHAP-V2", userCredential.getNonEapInnerMethod());
        credential.setUserCredential(userCredential);
        assertEquals(userCredential, credential.getUserCredential());
        Credential.CertificateCredential certCredential = new Credential.CertificateCredential();
        certCredential.setCertType("x509v3");
        assertEquals("x509v3", certCredential.getCertType());
        certCredential.setCertSha256Fingerprint(certFingerprint);
        assertTrue(Arrays.equals(certFingerprint, certCredential.getCertSha256Fingerprint()));
        credential.setCertCredential(certCredential);
        assertEquals(certCredential, credential.getCertCredential());
        Credential.SimCredential simCredential = new Credential.SimCredential();
        simCredential.setImsi("imsi");
        assertEquals("imsi", simCredential.getImsi());
        simCredential.setEapType(24);
        assertEquals(24, simCredential.getEapType());
        credential.setSimCredential(simCredential);
        assertEquals(simCredential, credential.getSimCredential());
        config.setCredential(credential);
        assertEquals(credential, config.getCredential());
        return config;
    }

    /**
     * Parse and verify all supported fields under PPS MO tree.
     *
     * @throws Exception
     */
    public void testParsePPSMOTree() throws Exception {
        String ppsMoTree = loadResourceFile(PPS_MO_XML_FILE);
        PasspointConfiguration expectedConfig = generateConfigurationFromPPSMOTree();
        PasspointConfiguration actualConfig = PpsMoParser.parseMoText(ppsMoTree);
        assertTrue(actualConfig.equals(expectedConfig));
    }
}
