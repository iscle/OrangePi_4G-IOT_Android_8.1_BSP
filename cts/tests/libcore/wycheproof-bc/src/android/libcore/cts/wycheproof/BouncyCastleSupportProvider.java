/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.libcore.cts.wycheproof;

import java.security.Provider;

/**
 * Provides a small number of exports to allow Bouncy Castle tests to function properly.
 * Our modified version of Bouncy Castle depends on Conscrypt for a few pieces of
 * functionality, but in tests we don't want to have Conscrypt installed so that we can test
 * Bouncy Castle properly.  We install this provider instead.
 */
public class BouncyCastleSupportProvider extends Provider {

    // The classes are jarjared, so this is the prefix in practice.
    private static final String PREFIX = "com.android.org.conscrypt.";

    public BouncyCastleSupportProvider() {
        // Our modified version of Bouncy Castle specifically expects certain algorithms
        // to be provided by a provider named "AndroidOpenSSL", so we use that name
        super("AndroidOpenSSL", 0.0,
                "Provides algorithms that Bouncy Castle needs to work in tests");

        // Conscrypt is the only SecureRandom implementation
        put("SecureRandom.SHA1PRNG", PREFIX + "OpenSSLRandom");

        // Bouncy Castle's MACs are backed by Conscrypt's MessageDigests
        put("MessageDigest.SHA-1", PREFIX + "OpenSSLMessageDigestJDK$SHA1");
        put("MessageDigest.SHA-224", PREFIX + "OpenSSLMessageDigestJDK$SHA224");
        put("MessageDigest.SHA-256", PREFIX + "OpenSSLMessageDigestJDK$SHA256");
        put("MessageDigest.SHA-384", PREFIX + "OpenSSLMessageDigestJDK$SHA384");
        put("MessageDigest.SHA-512", PREFIX + "OpenSSLMessageDigestJDK$SHA512");
    }
}
