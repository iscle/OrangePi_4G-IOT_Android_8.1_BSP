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

import com.android.org.conscrypt.OpenSSLProvider;
import com.google.security.wycheproof.AesGcmTest;
import com.google.security.wycheproof.BasicTest;
import com.google.security.wycheproof.CipherInputStreamTest;
import com.google.security.wycheproof.CipherOutputStreamTest;
import com.google.security.wycheproof.EcKeyTest;
import com.google.security.wycheproof.EcdhTest;
import com.google.security.wycheproof.EcdsaTest;
import com.google.security.wycheproof.RsaEncryptionTest;
import com.google.security.wycheproof.RsaKeyTest;
import com.google.security.wycheproof.RsaSignatureTest;
import com.google.security.wycheproof.TestUtil;
import com.google.security.wycheproof.WycheproofRunner;
import com.google.security.wycheproof.WycheproofRunner.Fast;
import com.google.security.wycheproof.WycheproofRunner.Provider;
import com.google.security.wycheproof.WycheproofRunner.ProviderType;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that our Conscrypt provider properly implements all its functionality.
 */
@RunWith(WycheproofRunner.class)
@SuiteClasses({
        AesGcmTest.class,
        BasicTest.class,
        CipherInputStreamTest.class,
        CipherOutputStreamTest.class,
        EcKeyTest.class,
        EcdhTest.class,
        EcdsaTest.class,
        RsaEncryptionTest.class,
        RsaKeyTest.class,
        RsaSignatureTest.class
})
@Provider(ProviderType.CONSCRYPT)
@Fast
public final class ConscryptTest {

    private static final List<java.security.Provider> previousProviders = new ArrayList<>();

    @BeforeClass
    public static void setUp() throws Exception {
        previousProviders.clear();
        previousProviders.addAll(Arrays.asList(Security.getProviders()));
        TestUtil.installOnlyThisProvider(new OpenSSLProvider());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        for (java.security.Provider p : Security.getProviders()) {
            Security.removeProvider(p.getName());
        }
        for (java.security.Provider p : previousProviders) {
            Security.addProvider(p);
        }
    }
}
