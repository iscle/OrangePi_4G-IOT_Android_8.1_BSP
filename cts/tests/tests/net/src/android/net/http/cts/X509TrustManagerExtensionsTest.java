/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.net.http.cts;

import android.net.http.X509TrustManagerExtensions;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import junit.framework.TestCase;

public class X509TrustManagerExtensionsTest extends TestCase {

    private static X509TrustManager getFirstX509TrustManager(TrustManagerFactory tmf)
            throws Exception {
        for (TrustManager trustManager : tmf.getTrustManagers()) {
             if (trustManager instanceof X509TrustManager) {
                 return (X509TrustManager) trustManager;
             }
        }
        fail("Unable to find X509TrustManager");
        return null;
    }

    public void testIsUserAddedCertificateDefaults() throws Exception {
        final TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        X509TrustManager tm = getFirstX509TrustManager(tmf);
        X509TrustManagerExtensions xtm = new X509TrustManagerExtensions(tm);
        // Verify that all the default system provided CAs are not marked as user added.
        for (Certificate cert : tm.getAcceptedIssuers()) {
            assertFalse(xtm.isUserAddedCertificate((X509Certificate) cert));
        }
    }
}
