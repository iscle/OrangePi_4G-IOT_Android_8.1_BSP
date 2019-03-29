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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import junit.framework.TestCase;

public class DomainConfigTest extends TestCase {

    public void testDomainConfig() throws Exception {
        TestUtils.assertTlsConnectionSucceeds("android.com", 443);
    }

    public void testDefaultConfig() throws Exception {
        // The default config in this case has no trusted CAs, so all connections should fail.
        TestUtils.assertTlsConnectionFails("developer.android.com", 443);
        TestUtils.assertTlsConnectionFails("example.com", 443);
    }

    public void testHostnameAwareCheckServerTrustedRequired() throws Exception {
        X509TrustManager x509tm = TestUtils.getDefaultTrustManager();
        try {
            x509tm.checkServerTrusted(new X509Certificate[] {}, "RSA");
            fail("checkServerTrusted passed");
        } catch (CertificateException e) {
            if (!e.getMessage().contains("hostname aware checkServerTrusted")) {
                fail("Hostname aware checkServerTrusted not required with a domain config");
            }
        }
    }
}
