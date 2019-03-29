/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

@SecurityTest
public class ConscryptIntermediateVerificationTest extends AndroidTestCase {

    private X509Certificate[] loadCertificates(int resource) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> result = new ArrayList<X509Certificate>();
        InputStream is = null;
        Collection<? extends Certificate> certs;
        try {
            is = getContext().getResources().openRawResource(resource);
            certs = certFactory.generateCertificates(is);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception ignored) {
            }
        }
        for (Certificate cert : certs) {
            result.add((X509Certificate) cert);
        }
        return result.toArray(new X509Certificate[result.size()]);
    }

    private X509TrustManager getTrustManager() throws Exception {

        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(null);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate root = loadCertificates(R.raw.intermediate_test_root)[0];
        keystore.setEntry("root", new KeyStore.TrustedCertificateEntry(root), null);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(keystore);
        X509TrustManager trustManager = null;
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        fail("Unable to find X509TrustManager");
        return null;
    }

    public void testIntermediateVerification() throws Exception {
        X509TrustManager tm = getTrustManager();
        X509Certificate[] validChain = loadCertificates(R.raw.intermediate_test_valid);
        X509Certificate[] invalidChain = loadCertificates(R.raw.intermediate_test_invalid);

        // This test consists of two chains:
        // valid: L -> I -> R
        // invalid L' -> I -> R
        // Where R is the trusted root CA
        // I is an intermediate CA with name constraints disallowing android.com
        // L is a certificate issued by I for example.com
        // L' is a certificate issued by I for android.com
        // valid is a valid chain and should verify correctly
        // invalid should fail due to the violation of I's name constraints

        try {
            tm.checkServerTrusted(invalidChain, "RSA");
            fail("invalidChain incorrectly valid");
        } catch (CertificateException expected) {
        }
        tm.checkServerTrusted(validChain, "RSA");
        // Implementation note: conscrypt's TrustManagerImpl caches intermediates after successful
        // verifications, those cached intermediates should still be considered untrusted when used
        // again in a subsequent connection.
        try {
            tm.checkServerTrusted(invalidChain, "RSA");
            fail("invalidChain incorrectly valid after trusting validChain");
        } catch (CertificateException expected) {
        }
    }
}
