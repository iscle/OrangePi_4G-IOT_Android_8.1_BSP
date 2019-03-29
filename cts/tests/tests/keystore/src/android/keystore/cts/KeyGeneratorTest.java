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

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class KeyGeneratorTest extends TestCase {
    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_PROVIDER_NAME;

    static final String[] EXPECTED_ALGORITHMS = {
        "AES",
        "HmacSHA1",
        "HmacSHA224",
        "HmacSHA256",
        "HmacSHA384",
        "HmacSHA512",
    };

    private static final Map<String, Integer> DEFAULT_KEY_SIZES =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        DEFAULT_KEY_SIZES.put("AES", 128);
        DEFAULT_KEY_SIZES.put("HmacSHA1", 160);
        DEFAULT_KEY_SIZES.put("HmacSHA224", 224);
        DEFAULT_KEY_SIZES.put("HmacSHA256", 256);
        DEFAULT_KEY_SIZES.put("HmacSHA384", 384);
        DEFAULT_KEY_SIZES.put("HmacSHA512", 512);
    }

    static final int[] AES_SUPPORTED_KEY_SIZES = new int[] {128, 192, 256};

    public void testAlgorithmList() {
        // Assert that Android Keystore Provider exposes exactly the expected KeyGenerator
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
            if ("KeyGenerator".equalsIgnoreCase(service.getType())) {
                String algLowerCase = service.getAlgorithm().toLowerCase(Locale.US);
                actualAlgsLowerCase.add(algLowerCase);
            }
        }

        TestUtils.assertContentsInAnyOrder(actualAlgsLowerCase,
                expectedAlgsLowerCase.toArray(new String[0]));
    }

    public void testGenerateWithoutInitThrowsIllegalStateException() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.generateKey();
                    fail();
                } catch (IllegalStateException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithKeySizeThrowsUnsupportedOperationException() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                int keySizeBits = DEFAULT_KEY_SIZES.get(algorithm);
                try {
                    keyGenerator.init(keySizeBits);
                    fail();
                } catch (UnsupportedOperationException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithKeySizeAndSecureRandomThrowsUnsupportedOperationException()
            throws Exception {
        SecureRandom rng = new SecureRandom();
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                int keySizeBits = DEFAULT_KEY_SIZES.get(algorithm);
                try {
                    keyGenerator.init(keySizeBits, rng);
                    fail();
                } catch (UnsupportedOperationException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithNullAlgParamsThrowsInvalidAlgorithmParameterException()
            throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init((AlgorithmParameterSpec) null);
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithNullAlgParamsAndSecureRandomThrowsInvalidAlgorithmParameterException()
            throws Exception {
        SecureRandom rng = new SecureRandom();
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init((AlgorithmParameterSpec) null, rng);
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithAlgParamsAndNullSecureRandom()
            throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                keyGenerator.init(getWorkingSpec().build(), (SecureRandom) null);
                // Check that generateKey doesn't fail either, just in case null SecureRandom
                // causes trouble there.
                keyGenerator.generateKey();
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithUnsupportedAlgParamsTypeThrowsInvalidAlgorithmParameterException()
            throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init(new ECGenParameterSpec("secp256r1"));
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testDefaultKeySize() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                int expectedSizeBits = DEFAULT_KEY_SIZES.get(algorithm);
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                keyGenerator.init(getWorkingSpec().build());
                SecretKey key = keyGenerator.generateKey();
                assertEquals(expectedSizeBits, TestUtils.getKeyInfo(key).getKeySize());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testAesKeySupportedSizes() throws Exception {
        KeyGenerator keyGenerator = getKeyGenerator("AES");
        KeyGenParameterSpec.Builder goodSpec = getWorkingSpec();
        CountingSecureRandom rng = new CountingSecureRandom();
        for (int i = -16; i <= 512; i++) {
            try {
                rng.resetCounters();
                KeyGenParameterSpec spec;
                if (i >= 0) {
                    spec = TestUtils.buildUpon(goodSpec.setKeySize(i)).build();
                } else {
                    try {
                        spec = TestUtils.buildUpon(goodSpec.setKeySize(i)).build();
                        fail();
                    } catch (IllegalArgumentException expected) {
                        continue;
                    }
                }
                rng.resetCounters();
                if (TestUtils.contains(AES_SUPPORTED_KEY_SIZES, i)) {
                    keyGenerator.init(spec, rng);
                    SecretKey key = keyGenerator.generateKey();
                    assertEquals(i, TestUtils.getKeyInfo(key).getKeySize());
                    assertEquals((i + 7) / 8, rng.getOutputSizeBytes());
                } else {
                    try {
                        keyGenerator.init(spec, rng);
                        fail();
                    } catch (InvalidAlgorithmParameterException expected) {}
                    assertEquals(0, rng.getOutputSizeBytes());
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key size " + i, e);
            }
        }
    }

    public void testHmacKeySupportedSizes() throws Exception {
        CountingSecureRandom rng = new CountingSecureRandom();
        for (String algorithm : EXPECTED_ALGORITHMS) {
            if (!TestUtils.isHmacAlgorithm(algorithm)) {
                continue;
            }

            for (int i = -16; i <= 1024; i++) {
                try {
                    rng.resetCounters();
                    KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                    KeyGenParameterSpec spec;
                    if (i >= 0) {
                        spec = getWorkingSpec().setKeySize(i).build();
                    } else {
                        try {
                            spec = getWorkingSpec().setKeySize(i).build();
                            fail();
                        } catch (IllegalArgumentException expected) {
                            continue;
                        }
                    }
                    if ((i >= 64) && ((i % 8 ) == 0)) {
                        keyGenerator.init(spec, rng);
                        SecretKey key = keyGenerator.generateKey();
                        assertEquals(i, TestUtils.getKeyInfo(key).getKeySize());
                        assertEquals((i + 7) / 8, rng.getOutputSizeBytes());
                    } else if (i >= 64) {
                        try {
                            keyGenerator.init(spec, rng);
                            fail();
                        } catch (InvalidAlgorithmParameterException expected) {}
                        assertEquals(0, rng.getOutputSizeBytes());
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + algorithm + " with key size " + i, e);
                }
            }
        }
    }

    public void testHmacKeyOnlyOneDigestCanBeAuthorized() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            if (!TestUtils.isHmacAlgorithm(algorithm)) {
                continue;
            }

            try {
                String digest = TestUtils.getHmacAlgorithmDigest(algorithm);
                assertNotNull(digest);

                KeyGenParameterSpec.Builder goodSpec = new KeyGenParameterSpec.Builder(
                        "test1", KeyProperties.PURPOSE_SIGN);

                KeyGenerator keyGenerator = getKeyGenerator(algorithm);

                // Digests authorization not specified in algorithm parameters
                assertFalse(goodSpec.build().isDigestsSpecified());
                keyGenerator.init(goodSpec.build());
                SecretKey key = keyGenerator.generateKey();
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

                // The same digest is specified in algorithm parameters
                keyGenerator.init(TestUtils.buildUpon(goodSpec).setDigests(digest).build());
                key = keyGenerator.generateKey();
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

                // No digests specified in algorithm parameters
                try {
                    keyGenerator.init(TestUtils.buildUpon(goodSpec).setDigests().build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}

                // A different digest specified in algorithm parameters
                String anotherDigest = "SHA-256".equalsIgnoreCase(digest) ? "SHA-384" : "SHA-256";
                try {
                    keyGenerator.init(TestUtils.buildUpon(goodSpec)
                            .setDigests(anotherDigest)
                            .build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
                try {
                    keyGenerator.init(TestUtils.buildUpon(goodSpec)
                            .setDigests(digest, anotherDigest)
                            .build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithUnknownBlockModeFails() {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init(getWorkingSpec().setBlockModes("weird").build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithUnknownEncryptionPaddingFails() {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init(getWorkingSpec().setEncryptionPaddings("weird").build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithSignaturePaddingFails() {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init(getWorkingSpec()
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                            .build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithUnknownDigestFails() {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    String[] digests;
                    if (TestUtils.isHmacAlgorithm(algorithm)) {
                        // The digest from HMAC key algorithm must be specified in the list of
                        // authorized digests (if the list if provided).
                        digests = new String[] {algorithm, "weird"};
                    } else {
                        digests = new String[] {"weird"};
                    }
                    keyGenerator.init(getWorkingSpec().setDigests(digests).build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithKeyAlgorithmDigestMissingFromAuthorizedDigestFails() {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            if (!TestUtils.isHmacAlgorithm(algorithm)) {
                continue;
            }
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);

                // Authorized for digest(s) none of which is the one implied by key algorithm.
                try {
                    String digest = TestUtils.getHmacAlgorithmDigest(algorithm);
                    String anotherDigest = KeyProperties.DIGEST_SHA256.equalsIgnoreCase(digest)
                            ? KeyProperties.DIGEST_SHA512 : KeyProperties.DIGEST_SHA256;
                    keyGenerator.init(getWorkingSpec().setDigests(anotherDigest).build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}

                // Authorized for empty set of digests
                try {
                    keyGenerator.init(getWorkingSpec().setDigests().build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitRandomizedEncryptionRequiredButViolatedFails() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init(getWorkingSpec(
                            KeyProperties.PURPOSE_ENCRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                            .build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testGenerateHonorsRequestedAuthorizations() throws Exception {
        Date keyValidityStart = new Date(System.currentTimeMillis() - TestUtils.DAY_IN_MILLIS);
        Date keyValidityForOriginationEnd =
                new Date(System.currentTimeMillis() + TestUtils.DAY_IN_MILLIS);
        Date keyValidityForConsumptionEnd =
                new Date(System.currentTimeMillis() + 3 * TestUtils.DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                String[] blockModes =
                        new String[] {KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CBC};
                String[] encryptionPaddings =
                        new String[] {KeyProperties.ENCRYPTION_PADDING_PKCS7,
                                KeyProperties.ENCRYPTION_PADDING_NONE};
                String[] digests;
                int purposes;
                if (TestUtils.isHmacAlgorithm(algorithm)) {
                    // HMAC key can only be authorized for one digest, the one implied by the key's
                    // JCA algorithm name.
                    digests = new String[] {TestUtils.getHmacAlgorithmDigest(algorithm)};
                    purposes = KeyProperties.PURPOSE_SIGN;
                } else {
                    digests = new String[] {KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA1};
                    purposes = KeyProperties.PURPOSE_DECRYPT;
                }
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                keyGenerator.init(getWorkingSpec(purposes)
                        .setBlockModes(blockModes)
                        .setEncryptionPaddings(encryptionPaddings)
                        .setDigests(digests)
                        .setKeyValidityStart(keyValidityStart)
                        .setKeyValidityForOriginationEnd(keyValidityForOriginationEnd)
                        .setKeyValidityForConsumptionEnd(keyValidityForConsumptionEnd)
                        .build());
                SecretKey key = keyGenerator.generateKey();
                assertEquals(algorithm, key.getAlgorithm());

                KeyInfo keyInfo = TestUtils.getKeyInfo(key);
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

    private static KeyGenParameterSpec.Builder getWorkingSpec() {
        return getWorkingSpec(0);
    }

    private static KeyGenParameterSpec.Builder getWorkingSpec(int purposes) {
        return new KeyGenParameterSpec.Builder("test1", purposes);
    }

    private static KeyGenerator getKeyGenerator(String algorithm) throws NoSuchAlgorithmException,
            NoSuchProviderException {
        return KeyGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    }
}
