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

package android.keystore.cts;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.test.MoreAsserts;

import junit.framework.TestCase;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.Provider.Service;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

public class SecretKeyFactoryTest extends TestCase {
    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_PROVIDER_NAME;

    private static final String[] EXPECTED_ALGORITHMS = {
        "AES",
        "HmacSHA1",
        "HmacSHA224",
        "HmacSHA256",
        "HmacSHA384",
        "HmacSHA512",
    };

    public void testAlgorithmList() {
        // Assert that Android Keystore Provider exposes exactly the expected SecretKeyFactory
        // algorithms. We don't care whether the algorithms are exposed via aliases, as long as
        // canonical names of algorithms are accepted. If the Provider exposes extraneous
        // algorithms, it'll be caught because it'll have to expose at least one Service for such an
        // algorithm, and this Service's algorithm will not be in the expected set.

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        Set<Service> services = provider.getServices();
        Set<String> actualAlgsLowerCase = new HashSet<String>();
        Set<String> expectedAlgsLowerCase = new HashSet<String>(
                Arrays.asList(TestUtils.toLowerCase(EXPECTED_ALGORITHMS)));
        for (Service service : services) {
            if ("SecretKeyFactory".equalsIgnoreCase(service.getType())) {
                String algLowerCase = service.getAlgorithm().toLowerCase(Locale.US);
                actualAlgsLowerCase.add(algLowerCase);
            }
        }

        TestUtils.assertContentsInAnyOrder(actualAlgsLowerCase,
                expectedAlgsLowerCase.toArray(new String[0]));
    }

    public void testGetKeySpecWithKeystoreKeyAndKeyInfoReflectsAllAuthorizations()
            throws Exception {
        Date keyValidityStart = new Date(System.currentTimeMillis() - TestUtils.DAY_IN_MILLIS);
        Date keyValidityForOriginationEnd =
                new Date(System.currentTimeMillis() + TestUtils.DAY_IN_MILLIS);
        Date keyValidityForConsumptionEnd =
                new Date(System.currentTimeMillis() + 3 * TestUtils.DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                String[] blockModes =
                        new String[] {KeyProperties.BLOCK_MODE_CTR, KeyProperties.BLOCK_MODE_ECB};
                String[] encryptionPaddings =
                        new String[] {KeyProperties.ENCRYPTION_PADDING_PKCS7,
                                KeyProperties.ENCRYPTION_PADDING_NONE};
                String[] digests;
                int purposes;
                if (TestUtils.isHmacAlgorithm(algorithm)) {
                    String digest = TestUtils.getHmacAlgorithmDigest(algorithm);
                    digests = new String[] {digest};
                    purposes = KeyProperties.PURPOSE_SIGN;
                } else {
                    digests = new String[] {KeyProperties.DIGEST_SHA384};
                    purposes = KeyProperties.PURPOSE_DECRYPT;
                }
                KeyGenerator keyGenerator =
                        KeyGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.init(new KeyGenParameterSpec.Builder("test1", purposes)
                        .setBlockModes(blockModes)
                        .setEncryptionPaddings(encryptionPaddings)
                        .setDigests(digests)
                        .setKeyValidityStart(keyValidityStart)
                        .setKeyValidityForOriginationEnd(keyValidityForOriginationEnd)
                        .setKeyValidityForConsumptionEnd(keyValidityForConsumptionEnd)
                        .build());
                SecretKey key = keyGenerator.generateKey();
                SecretKeyFactory keyFactory = getKeyFactory(algorithm);
                KeyInfo keyInfo = (KeyInfo) keyFactory.getKeySpec(key, KeyInfo.class);
                assertEquals("test1", keyInfo.getKeystoreAlias());
                assertEquals(purposes, keyInfo.getPurposes());
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(blockModes), keyInfo.getBlockModes());
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(encryptionPaddings), keyInfo.getEncryptionPaddings());
                TestUtils.assertContentsInAnyOrder(Arrays.asList(digests), keyInfo.getDigests());
                MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
                assertEquals(keyValidityStart, keyInfo.getKeyValidityStart());
                assertEquals(keyValidityForOriginationEnd,
                        keyInfo.getKeyValidityForOriginationEnd());
                assertEquals(keyValidityForConsumptionEnd,
                        keyInfo.getKeyValidityForConsumptionEnd());
                assertFalse(keyInfo.isUserAuthenticationRequired());
                assertFalse(keyInfo.isUserAuthenticationRequirementEnforcedBySecureHardware());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testTranslateKeyWithNullKeyThrowsInvalidKeyException() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.translateKey(null);
                    fail();
                } catch (InvalidKeyException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testTranslateKeyRejectsNonAndroidKeystoreKeys() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKey key = new SecretKeySpec(new byte[16], algorithm);
                SecretKeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.translateKey(key);
                    fail();
                } catch (InvalidKeyException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testTranslateKeyAcceptsAndroidKeystoreKeys() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator =
                        KeyGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                if (algorithm.equals("AES")) {
                    keyGenerator.init(new KeyGenParameterSpec.Builder("test1",
                                    KeyProperties.PURPOSE_ENCRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());
                } else {
                    keyGenerator.init(new KeyGenParameterSpec.Builder("test1",
                                    KeyProperties.PURPOSE_SIGN)
                            .build());
                }
                SecretKey key = keyGenerator.generateKey();

                SecretKeyFactory keyFactory = getKeyFactory(algorithm);
                assertSame(key, keyFactory.translateKey(key));
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testGenerateSecretWithNullSpecThrowsInvalidKeySpecException() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generateSecret(null);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testGenerateSecretRejectsSecretKeySpec() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generateSecret(new SecretKeySpec(new byte[16], algorithm));
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testGenerateSecretRejectsKeyInfo() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator =
                        KeyGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.init(new KeyGenParameterSpec.Builder("test1", 0).build());
                SecretKey keystoreKey = keyGenerator.generateKey();
                KeyInfo keyInfo = TestUtils.getKeyInfo(keystoreKey);

                SecretKeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generateSecret(keyInfo);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    private SecretKeyFactory getKeyFactory(String algorithm) throws NoSuchAlgorithmException,
            NoSuchProviderException {
        return SecretKeyFactory.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    }
}
