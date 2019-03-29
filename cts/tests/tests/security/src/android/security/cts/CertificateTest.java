/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.security.cts;

import java.io.IOException;
import android.platform.test.annotations.SecurityTest;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

@SecurityTest
public class CertificateTest extends TestCase {

    public void testNoRemovedCertificates() throws Exception {
        Set<String> expectedCertificates = new HashSet<String>(
                Arrays.asList(CertificateData.CERTIFICATE_DATA));
        Set<String> deviceCertificates = getDeviceCertificates();
        expectedCertificates.removeAll(deviceCertificates);
        assertEquals("Missing CA certificates", Collections.EMPTY_SET, expectedCertificates);
    }

    /**
     * If you fail CTS as a result of adding a root CA that is not part of the Android root CA
     * store, please see the following.
     *
     * <p>This test exists because adding root CAs to a device has a very significant security
     * impact. Whoever has access to the signing keys of that CA can compromise secure network
     * traffic from affected Android devices, putting users at risk.
     *
     * <p>If you have a CA certificate which needs to be trusted by a particular app/service,
     * ask the developer of the app/service to modify it to trust this CA (e.g., using Network
     * Security Config feature). This avoids compromising the security of network traffic of other
     * apps on the device.
     *
     * <p>If you have a CA certificate that you believe should be present on all Android devices,
     * please file a public bug at https://code.google.com/p/android/issues/entry.
     *
     * <p>For questions, comments, and code reviews please contact security@android.com.
     */
    public void testNoAddedCertificates() throws Exception {
        Set<String> expectedCertificates = new HashSet<String>(
                Arrays.asList(CertificateData.CERTIFICATE_DATA));
        Set<String> deviceCertificates = getDeviceCertificates();
        deviceCertificates.removeAll(expectedCertificates);
        assertEquals("Unknown CA certificates", Collections.EMPTY_SET, deviceCertificates);
    }

    public void testBlockCertificates() throws Exception {
        Set<String> blockCertificates = new HashSet<String>();
        blockCertificates.add("C0:60:ED:44:CB:D8:81:BD:0E:F8:6C:0B:A2:87:DD:CF:81:67:47:8C");

        Set<String> deviceCertificates = getDeviceCertificates();
        deviceCertificates.retainAll(blockCertificates);
        assertEquals("Blocked CA certificates", Collections.EMPTY_SET, deviceCertificates);
    }

    private Set<String> getDeviceCertificates() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
        keyStore.load(null, null);

        List<String> aliases = Collections.list(keyStore.aliases());
        assertFalse(aliases.isEmpty());

        Set<String> certificates = new HashSet<String>();
        for (String alias : aliases) {
            assertTrue(keyStore.isCertificateEntry(alias));
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            assertEquals(certificate.getSubjectUniqueID(), certificate.getIssuerUniqueID());
            assertNotNull(certificate.getSubjectDN());
            assertNotNull(certificate.getIssuerDN());
            String fingerprint = getFingerprint(certificate);
            certificates.add(fingerprint);
        }
        return certificates;
    }

    private String getFingerprint(X509Certificate certificate) throws CertificateEncodingException,
            NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
        messageDigest.update(certificate.getEncoded());
        byte[] sha1 = messageDigest.digest();
        return convertToHexFingerprint(sha1);
    }

    private String convertToHexFingerprint(byte[] sha1) {
        StringBuilder fingerprint = new StringBuilder();
        for (int i = 0; i < sha1.length; i++) {
            fingerprint.append(String.format("%02X", sha1[i]));
            if (i + 1 < sha1.length) {
                fingerprint.append(":");
            }
        }
        return fingerprint.toString();
    }
}
