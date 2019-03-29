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

public class CleartextPermittedTest extends TestCase {
    public void testDefaultAllowed() throws Exception {
        TestUtils.assertCleartextConnectionSucceeds("example.com", 80);
        TestUtils.assertTlsConnectionSucceeds("example.com", 443);
    }

    public void testCleartextBlocked() throws Exception {
        TestUtils.assertCleartextConnectionFails("android.com", 80);
        TestUtils.assertTlsConnectionSucceeds("android.com", 443);
        // subdomains of android.com are also disallowed.
        TestUtils.assertCleartextConnectionFails("www.android.com", 80);
        TestUtils.assertTlsConnectionSucceeds("www.android.com", 443);
    }

    public void testNestedCleartextPermitted() throws Exception {
        // developer.android.com is explicitly permitted.
        TestUtils.assertCleartextConnectionSucceeds("developer.android.com", 80);
        TestUtils.assertTlsConnectionSucceeds("developer.android.com", 443);
    }
}
