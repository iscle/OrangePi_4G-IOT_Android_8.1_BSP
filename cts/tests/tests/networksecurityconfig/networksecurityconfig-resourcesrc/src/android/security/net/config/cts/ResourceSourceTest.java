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

package android.security.net.config.cts;

import android.security.net.config.cts.CtsNetSecConfigResourcesSrcTestCases.R;

import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class ResourceSourceTest extends AndroidTestCase {

    public void testSingleDerPresent() throws Exception {
        Set<X509Certificate> trusted = getTrustedCertificates();
        Set<X509Certificate> singleDer = loadCertificates(R.raw.der_single);
        assertContainsAll(trusted, singleDer);
    }

    public void testSinglePemPresent() throws Exception {
        Set<X509Certificate> trusted = getTrustedCertificates();
        Set<X509Certificate> singlePem = loadCertificates(R.raw.pem_single);
        assertContainsAll(trusted, singlePem);
    }

    public void testMultipleDerPresent() throws Exception {
        Set<X509Certificate> trusted = getTrustedCertificates();
        Set<X509Certificate> multipleDer = loadCertificates(R.raw.der_multiple);
        assertEquals(2, multipleDer.size());
        assertContainsAll(trusted, multipleDer);
    }

    public void testMultiplePemPresent() throws Exception {
        Set<X509Certificate> trusted = getTrustedCertificates();
        Set<X509Certificate> multiplePem = loadCertificates(R.raw.pem_multiple);
        assertEquals(2, multiplePem.size());
        assertContainsAll(trusted, multiplePem);
    }

    public void testOnlyResourceCasPresent() throws Exception {
        Set<X509Certificate> trusted = getTrustedCertificates();
        Set<X509Certificate> certs = loadCertificates(R.raw.der_single);
        certs.addAll(loadCertificates(R.raw.der_multiple));
        certs.addAll(loadCertificates(R.raw.pem_single));
        certs.addAll(loadCertificates(R.raw.pem_multiple));
        MoreAsserts.assertEquals(certs, trusted);
    }

    private Set<X509Certificate> loadCertificates(int resId) throws Exception {
        Set<X509Certificate> result = new HashSet<X509Certificate>();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        InputStream is = null;
        try {
            is = getContext().getResources().openRawResource(resId);
            for (Certificate cert : factory.generateCertificates(is)) {
                result.add((X509Certificate)cert);
            }
            return result;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private static Set<X509Certificate> getTrustedCertificates() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init((KeyStore)null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                X509Certificate[] trustedCerts = ((X509TrustManager) tm).getAcceptedIssuers();
                Set<X509Certificate> result = new HashSet<X509Certificate>(trustedCerts.length);
                for (X509Certificate cert : trustedCerts) {
                    result.add(cert);
                }
                return result;
            }
        }
        fail("Unable to find X509TrustManager");
        return null;
    }

    private static void assertContainsAll(Set<? extends Object> set,
            Set<? extends Object> subset) throws Exception {
        for (Object o : subset) {
            if (!set.contains(o)) {
                fail("Set does not contain element " + o);
            }
        }
    }
}
