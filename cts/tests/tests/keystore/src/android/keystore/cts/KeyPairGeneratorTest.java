/*
 * Copyright 2013 The Android Open Source Project
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

import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Provider.Service;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.text.DecimalFormatSymbols;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;

import libcore.java.security.TestKeyStore;
import libcore.javax.net.ssl.TestKeyManager;
import libcore.javax.net.ssl.TestSSLContext;

public class KeyPairGeneratorTest extends AndroidTestCase {
    private KeyStore mKeyStore;

    private CountingSecureRandom mRng;

    private static final String TEST_ALIAS_1 = "test1";

    private static final String TEST_ALIAS_2 = "test2";

    private static final String TEST_ALIAS_3 = "test3";

    private static final X500Principal TEST_DN_1 = new X500Principal("CN=test1");

    private static final X500Principal TEST_DN_2 = new X500Principal("CN=test2");

    private static final BigInteger TEST_SERIAL_1 = BigInteger.ONE;

    private static final BigInteger TEST_SERIAL_2 = BigInteger.valueOf(2L);

    private static final long NOW_MILLIS = System.currentTimeMillis();

    /* We have to round this off because X509v3 doesn't store milliseconds. */
    private static final Date NOW = new Date(NOW_MILLIS - (NOW_MILLIS % 1000L));

    @SuppressWarnings("deprecation")
    private static final Date NOW_PLUS_10_YEARS = new Date(NOW.getYear() + 10, 0, 1);

    private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;

    private static final X500Principal DEFAULT_CERT_SUBJECT = new X500Principal("CN=fake");
    private static final BigInteger DEFAULT_CERT_SERIAL_NUMBER = new BigInteger("1");
    private static final Date DEFAULT_CERT_NOT_BEFORE = new Date(0L); // Jan 1 1970
    private static final Date DEFAULT_CERT_NOT_AFTER = new Date(2461449600000L); // Jan 1 2048

    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_PROVIDER_NAME;

    private static final String[] EXPECTED_ALGORITHMS = {
        "EC",
        "RSA",
    };

    private static final Map<String, Integer> DEFAULT_KEY_SIZES =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        DEFAULT_KEY_SIZES.put("EC", 256);
        DEFAULT_KEY_SIZES.put("RSA", 2048);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRng = new CountingSecureRandom();
        mKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mKeyStore.load(null, null);
    }

    public void testAlgorithmList() {
        // Assert that Android Keystore Provider exposes exactly the expected KeyPairGenerator
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
            if ("KeyPairGenerator".equalsIgnoreCase(service.getType())) {
                String algLowerCase = service.getAlgorithm().toLowerCase(Locale.US);
                actualAlgsLowerCase.add(algLowerCase);
            }
        }

        TestUtils.assertContentsInAnyOrder(actualAlgsLowerCase,
                expectedAlgsLowerCase.toArray(new String[0]));
    }

    public void testInitialize_LegacySpec() throws Exception {
        @SuppressWarnings("deprecation")
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(TEST_ALIAS_1)
                .setSubject(TEST_DN_1)
                .setSerialNumber(TEST_SERIAL_1)
                .setStartDate(NOW)
                .setEndDate(NOW_PLUS_10_YEARS)
                .build();
        getRsaGenerator().initialize(spec);
        getRsaGenerator().initialize(spec, new SecureRandom());

        getEcGenerator().initialize(spec);
        getEcGenerator().initialize(spec, new SecureRandom());
    }

    public void testInitialize_ModernSpec() throws Exception {
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .build();
        getRsaGenerator().initialize(spec);
        getRsaGenerator().initialize(spec, new SecureRandom());

        getEcGenerator().initialize(spec);
        getEcGenerator().initialize(spec, new SecureRandom());
    }

    public void testInitialize_KeySizeOnly() throws Exception {
        try {
            getRsaGenerator().initialize(1024);
            fail("KeyPairGenerator should not support setting the key size");
        } catch (IllegalArgumentException success) {
        }

        try {
            getEcGenerator().initialize(256);
            fail("KeyPairGenerator should not support setting the key size");
        } catch (IllegalArgumentException success) {
        }
    }

    public void testInitialize_KeySizeAndSecureRandomOnly()
            throws Exception {
        try {
            getRsaGenerator().initialize(1024, new SecureRandom());
            fail("KeyPairGenerator should not support setting the key size");
        } catch (IllegalArgumentException success) {
        }

        try {
            getEcGenerator().initialize(1024, new SecureRandom());
            fail("KeyPairGenerator should not support setting the key size");
        } catch (IllegalArgumentException success) {
        }
    }

    public void testDefaultKeySize() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                int expectedSizeBits = DEFAULT_KEY_SIZES.get(algorithm);
                KeyPairGenerator generator = getGenerator(algorithm);
                generator.initialize(getWorkingSpec().build());
                KeyPair keyPair = generator.generateKeyPair();
                assertEquals(expectedSizeBits,
                        TestUtils.getKeyInfo(keyPair.getPrivate()).getKeySize());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithUnknownBlockModeFails() {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyPairGenerator generator = getGenerator(algorithm);
                try {
                    generator.initialize(getWorkingSpec().setBlockModes("weird").build());
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
                KeyPairGenerator generator = getGenerator(algorithm);
                try {
                    generator.initialize(getWorkingSpec().setEncryptionPaddings("weird").build());
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitWithUnknownSignaturePaddingFails() {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyPairGenerator generator = getGenerator(algorithm);
                try {
                    generator.initialize(getWorkingSpec().setSignaturePaddings("weird").build());
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
                KeyPairGenerator generator = getGenerator(algorithm);
                try {
                    generator.initialize(getWorkingSpec().setDigests("weird").build());
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
                KeyPairGenerator generator = getGenerator(algorithm);
                try {
                    generator.initialize(getWorkingSpec(
                            KeyProperties.PURPOSE_ENCRYPT)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
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
                        new String[] {KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1};
                String[] digests =
                        new String[] {KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1};
                int purposes = KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT;
                KeyPairGenerator generator = getGenerator(algorithm);
                generator.initialize(getWorkingSpec(purposes)
                        .setBlockModes(blockModes)
                        .setEncryptionPaddings(encryptionPaddings)
                        .setDigests(digests)
                        .setKeyValidityStart(keyValidityStart)
                        .setKeyValidityForOriginationEnd(keyValidityForOriginationEnd)
                        .setKeyValidityForConsumptionEnd(keyValidityForConsumptionEnd)
                        .build());
                KeyPair keyPair = generator.generateKeyPair();
                assertEquals(algorithm, keyPair.getPrivate().getAlgorithm());

                KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
                assertEquals(purposes, keyInfo.getPurposes());
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(keyInfo.getBlockModes()), blockModes);

                List<String> actualEncryptionPaddings =
                        new ArrayList<String>(Arrays.asList(keyInfo.getEncryptionPaddings()));
                // Keystore may have added ENCRYPTION_PADDING_NONE to allow software OAEP
                actualEncryptionPaddings.remove(KeyProperties.ENCRYPTION_PADDING_NONE);
                TestUtils.assertContentsInAnyOrder(
                        actualEncryptionPaddings, encryptionPaddings);

                List<String> actualDigests =
                        new ArrayList<String>(Arrays.asList(keyInfo.getDigests()));
                // Keystore may have added DIGEST_NONE, to allow software digesting.
                actualDigests.remove(KeyProperties.DIGEST_NONE);
                TestUtils.assertContentsInAnyOrder(actualDigests, digests);

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

    @SuppressWarnings("deprecation")
    public void testGenerate_EC_LegacySpec() throws Exception {
        // There are three legacy ways to generate an EC key pair using Android Keystore
        // KeyPairGenerator:
        // 1. Use an RSA KeyPairGenerator and specify EC as key type,
        // 2. Use an EC KeyPairGenerator and specify EC as key type,
        // 3. Use an EC KeyPairGenerator and leave the key type unspecified.
        //
        // We test all three.

        // 1. Use an RSA KeyPairGenerator and specify EC as key type.
        KeyPairGenerator generator = getRsaGenerator();
        generator.initialize(new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(TEST_ALIAS_1)
                .setKeyType("EC")
                .setSubject(TEST_DN_1)
                .setSerialNumber(TEST_SERIAL_1)
                .setStartDate(NOW)
                .setEndDate(NOW_PLUS_10_YEARS)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "EC",
                256,
                TEST_DN_1,
                TEST_SERIAL_1, NOW,
                NOW_PLUS_10_YEARS);
        assertSelfSignedCertificateSignatureVerifies(TEST_ALIAS_1);
        assertKeyPairAndCertificateUsableForTLSPeerAuthentication(TEST_ALIAS_1);
        TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                ECCurves.NIST_P_256_SPEC, ((ECPublicKey) keyPair.getPublic()).getParams());
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(256, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_1, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY,
                keyInfo.getPurposes());
        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(null, keyInfo.getKeyValidityStart());
        assertEquals(null, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(null, keyInfo.getKeyValidityForConsumptionEnd());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getDigests()),
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA224,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512);
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getEncryptionPaddings()));

        // 2. Use an EC KeyPairGenerator and specify EC as key type.
        generator = getEcGenerator();
        generator.initialize(new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(TEST_ALIAS_2)
                .setKeyType("EC")
                .setSubject(TEST_DN_1)
                .setSerialNumber(TEST_SERIAL_1)
                .setStartDate(NOW)
                .setEndDate(NOW_PLUS_10_YEARS)
                .build());
        keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_2,
                "EC",
                256,
                TEST_DN_1,
                TEST_SERIAL_1,
                NOW,
                NOW_PLUS_10_YEARS);
        assertSelfSignedCertificateSignatureVerifies(TEST_ALIAS_2);
        assertKeyPairAndCertificateUsableForTLSPeerAuthentication(TEST_ALIAS_2);
        TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                ECCurves.NIST_P_256_SPEC, ((ECPublicKey) keyPair.getPublic()).getParams());
        keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(256, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_2, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY,
                keyInfo.getPurposes());
        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(null, keyInfo.getKeyValidityStart());
        assertEquals(null, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(null, keyInfo.getKeyValidityForConsumptionEnd());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getDigests()),
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA224,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512);
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getEncryptionPaddings()));

        // 3. Use an EC KeyPairGenerator and leave the key type unspecified.
        generator = getEcGenerator();
        generator.initialize(new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(TEST_ALIAS_3)
                .setSubject(TEST_DN_1)
                .setSerialNumber(TEST_SERIAL_1)
                .setStartDate(NOW)
                .setEndDate(NOW_PLUS_10_YEARS)
                .build());
        keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_3,
                "EC",
                256,
                TEST_DN_1,
                TEST_SERIAL_1,
                NOW,
                NOW_PLUS_10_YEARS);
        assertSelfSignedCertificateSignatureVerifies(TEST_ALIAS_3);
        assertKeyPairAndCertificateUsableForTLSPeerAuthentication(TEST_ALIAS_3);
        TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                ECCurves.NIST_P_256_SPEC, ((ECPublicKey) keyPair.getPublic()).getParams());
        keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(256, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_3, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY,
                keyInfo.getPurposes());
        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(null, keyInfo.getKeyValidityStart());
        assertEquals(null, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(null, keyInfo.getKeyValidityForConsumptionEnd());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getDigests()),
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA224,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512);
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getEncryptionPaddings()));
    }

    public void testGenerate_RSA_LegacySpec() throws Exception {
        KeyPairGenerator generator = getRsaGenerator();
        generator.initialize(new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(TEST_ALIAS_1)
                .setSubject(TEST_DN_1)
                .setSerialNumber(TEST_SERIAL_1)
                .setStartDate(NOW)
                .setEndDate(NOW_PLUS_10_YEARS)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "RSA",
                2048,
                TEST_DN_1,
                TEST_SERIAL_1,
                NOW,
                NOW_PLUS_10_YEARS);
        assertSelfSignedCertificateSignatureVerifies(TEST_ALIAS_1);
        assertKeyPairAndCertificateUsableForTLSPeerAuthentication(TEST_ALIAS_1);
        assertEquals(RSAKeyGenParameterSpec.F4,
                ((RSAPublicKey) keyPair.getPublic()).getPublicExponent());
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(2048, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_1, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT,
                keyInfo.getPurposes());
        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(null, keyInfo.getKeyValidityStart());
        assertEquals(null, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(null, keyInfo.getKeyValidityForConsumptionEnd());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getDigests()),
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_MD5,
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA224,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512);
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getEncryptionPaddings()),
                KeyProperties.ENCRYPTION_PADDING_NONE,
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP);
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getSignaturePaddings()),
                KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);
    }

    public void testGenerate_ReplacesOldEntryWithSameAlias()
            throws Exception {
        // Generate the first key
        {
            KeyPairGenerator generator = getRsaGenerator();
            generator.initialize(new KeyGenParameterSpec.Builder(
                    TEST_ALIAS_1,
                    KeyProperties.PURPOSE_SIGN
                            | KeyProperties.PURPOSE_VERIFY
                            | KeyProperties.PURPOSE_ENCRYPT
                            | KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_NONE)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setCertificateSubject(TEST_DN_1)
                    .setCertificateSerialNumber(TEST_SERIAL_1)
                    .setCertificateNotBefore(NOW)
                    .setCertificateNotAfter(NOW_PLUS_10_YEARS)
                    .build());
            assertGeneratedKeyPairAndSelfSignedCertificate(
                    generator.generateKeyPair(),
                    TEST_ALIAS_1,
                    "RSA",
                    2048,
                    TEST_DN_1,
                    TEST_SERIAL_1,
                    NOW,
                    NOW_PLUS_10_YEARS);
        }

        // Replace the original key
        {
            KeyPairGenerator generator = getRsaGenerator();
            generator.initialize(new KeyGenParameterSpec.Builder(
                    TEST_ALIAS_1,
                    KeyProperties.PURPOSE_SIGN
                            | KeyProperties.PURPOSE_VERIFY
                            | KeyProperties.PURPOSE_ENCRYPT
                            | KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_NONE)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setCertificateSubject(TEST_DN_2)
                    .setCertificateSerialNumber(TEST_SERIAL_2)
                    .setCertificateNotBefore(NOW)
                    .setCertificateNotAfter(NOW_PLUS_10_YEARS)
                    .build());
            assertGeneratedKeyPairAndSelfSignedCertificate(
                    generator.generateKeyPair(),
                    TEST_ALIAS_1,
                    "RSA",
                    2048,
                    TEST_DN_2,
                    TEST_SERIAL_2,
                    NOW,
                    NOW_PLUS_10_YEARS);
        }
    }

    public void testGenerate_DoesNotReplaceOtherEntries()
            throws Exception {
        // Generate the first key
        KeyPairGenerator generator = getRsaGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN
                        | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_ENCRYPT
                        | KeyProperties.PURPOSE_DECRYPT)
                .setDigests(KeyProperties.DIGEST_NONE)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(TEST_DN_1)
                .setCertificateSerialNumber(TEST_SERIAL_1)
                .setCertificateNotBefore(NOW)
                .setCertificateNotAfter(NOW_PLUS_10_YEARS)
                .build());
        KeyPair keyPair1 = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair1,
                TEST_ALIAS_1,
                "RSA",
                2048,
                TEST_DN_1,
                TEST_SERIAL_1,
                NOW,
                NOW_PLUS_10_YEARS);

        // Generate the second key
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_2,
                KeyProperties.PURPOSE_SIGN
                        | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_ENCRYPT
                        | KeyProperties.PURPOSE_DECRYPT)
                .setDigests(KeyProperties.DIGEST_NONE)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(TEST_DN_2)
                .setCertificateSerialNumber(TEST_SERIAL_2)
                .setCertificateNotBefore(NOW)
                .setCertificateNotAfter(NOW_PLUS_10_YEARS)
                .build());
        KeyPair keyPair2 = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair2,
                TEST_ALIAS_2,
                "RSA",
                2048,
                TEST_DN_2,
                TEST_SERIAL_2,
                NOW,
                NOW_PLUS_10_YEARS);

        // Check the first key pair again
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair1,
                TEST_ALIAS_1,
                "RSA",
                2048,
                TEST_DN_1,
                TEST_SERIAL_1,
                NOW,
                NOW_PLUS_10_YEARS);
    }

    public void testGenerate_EC_ModernSpec_Defaults() throws Exception {
        KeyPairGenerator generator = getEcGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "EC",
                256,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                ECCurves.NIST_P_256_SPEC, ((ECKey) keyPair.getPrivate()).getParams());
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(256, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_1, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY, keyInfo.getPurposes());
        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(null, keyInfo.getKeyValidityStart());
        assertEquals(null, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(null, keyInfo.getKeyValidityForConsumptionEnd());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getDigests()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getEncryptionPaddings()));
    }

    public void testGenerate_RSA_ModernSpec_Defaults() throws Exception {
        KeyPairGenerator generator = getRsaGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "RSA",
                2048,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        assertEquals(RSAKeyGenParameterSpec.F4,
                ((RSAPublicKey) keyPair.getPublic()).getPublicExponent());
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(2048, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_1, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT,
                keyInfo.getPurposes());
        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(null, keyInfo.getKeyValidityStart());
        assertEquals(null, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(null, keyInfo.getKeyValidityForConsumptionEnd());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getDigests()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getEncryptionPaddings()));
    }

    public void testGenerate_EC_ModernSpec_AsCustomAsPossible() throws Exception {
        KeyPairGenerator generator = getEcGenerator();
        Date keyValidityStart = new Date(System.currentTimeMillis());
        Date keyValidityEndDateForOrigination = new Date(System.currentTimeMillis() + 1000000);
        Date keyValidityEndDateForConsumption = new Date(System.currentTimeMillis() + 10000000);

        Date certNotBefore = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 7);
        Date certNotAfter = new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7);
        BigInteger certSerialNumber = new BigInteger("12345678");
        X500Principal certSubject = new X500Principal("cn=hello");
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_ENCRYPT)
                .setKeySize(224)
                .setDigests(KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setKeyValidityStart(keyValidityStart)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setCertificateSerialNumber(certSerialNumber)
                .setCertificateSubject(certSubject)
                .setCertificateNotBefore(certNotBefore)
                .setCertificateNotAfter(certNotAfter)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "EC",
                224,
                certSubject,
                certSerialNumber,
                certNotBefore,
                certNotAfter);
        TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                ECCurves.NIST_P_224_SPEC, ((ECKey) keyPair.getPrivate()).getParams());
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(224, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_1, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_ENCRYPT,
                keyInfo.getPurposes());
        assertEquals(keyValidityStart, keyInfo.getKeyValidityStart());
        assertEquals(keyValidityEndDateForOrigination, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(keyValidityEndDateForConsumption, keyInfo.getKeyValidityForConsumptionEnd());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));

        List<String> actualDigests = new ArrayList<String>(Arrays.asList(keyInfo.getDigests()));
        // Keystore may have added DIGEST_NONE, to allow software digesting.
        actualDigests.remove(KeyProperties.DIGEST_NONE);
        TestUtils.assertContentsInAnyOrder(
                actualDigests, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512);

        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getEncryptionPaddings()));
        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(-1, keyInfo.getUserAuthenticationValidityDurationSeconds());
    }

    public void testGenerate_RSA_ModernSpec_AsCustomAsPossible() throws Exception {
        KeyPairGenerator generator = getRsaGenerator();
        Date keyValidityStart = new Date(System.currentTimeMillis());
        Date keyValidityEndDateForOrigination = new Date(System.currentTimeMillis() + 1000000);
        Date keyValidityEndDateForConsumption = new Date(System.currentTimeMillis() + 10000000);

        Date certNotBefore = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 210);
        Date certNotAfter = new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 210);
        BigInteger certSerialNumber = new BigInteger("1234567890");
        X500Principal certSubject = new X500Principal("cn=hello2");
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_ENCRYPT)
                .setAlgorithmParameterSpec(
                        new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F0))
                .setKeySize(3072)
                .setDigests(KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                        KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setKeyValidityStart(keyValidityStart)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setCertificateSerialNumber(certSerialNumber)
                .setCertificateSubject(certSubject)
                .setCertificateNotBefore(certNotBefore)
                .setCertificateNotAfter(certNotAfter)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "RSA",
                3072,
                certSubject,
                certSerialNumber,
                certNotBefore,
                certNotAfter);
        assertEquals(RSAKeyGenParameterSpec.F0,
                ((RSAPublicKey) keyPair.getPublic()).getPublicExponent());
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(3072, keyInfo.getKeySize());
        assertEquals(TEST_ALIAS_1, keyInfo.getKeystoreAlias());
        assertOneOf(keyInfo.getOrigin(),
                KeyProperties.ORIGIN_GENERATED, KeyProperties.ORIGIN_UNKNOWN);
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_ENCRYPT,
                keyInfo.getPurposes());
        assertEquals(keyValidityStart, keyInfo.getKeyValidityStart());
        assertEquals(keyValidityEndDateForOrigination, keyInfo.getKeyValidityForOriginationEnd());
        assertEquals(keyValidityEndDateForConsumption, keyInfo.getKeyValidityForConsumptionEnd());

        List<String> actualDigests =
	    new ArrayList<String>(Arrays.asList(keyInfo.getDigests()));
        // Keystore may have added DIGEST_NONE, to allow software digesting.
        actualDigests.remove(KeyProperties.DIGEST_NONE);
        TestUtils.assertContentsInAnyOrder(
                actualDigests, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512);

        TestUtils.assertContentsInAnyOrder(Arrays.asList(keyInfo.getSignaturePaddings()),
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS);
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getBlockModes()),
                KeyProperties.BLOCK_MODE_ECB);

        List<String> actualEncryptionPaddings =
                new ArrayList<String>(Arrays.asList(keyInfo.getEncryptionPaddings()));
        // Keystore may have added ENCRYPTION_PADDING_NONE, to allow software padding.
        actualEncryptionPaddings.remove(KeyProperties.ENCRYPTION_PADDING_NONE);
        TestUtils.assertContentsInAnyOrder(actualEncryptionPaddings,
                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1);

        assertFalse(keyInfo.isUserAuthenticationRequired());
        assertEquals(-1, keyInfo.getUserAuthenticationValidityDurationSeconds());
    }

    public void testGenerate_EC_ModernSpec_UsableForTLSPeerAuth() throws Exception {
        KeyPairGenerator generator = getEcGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA384)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "EC",
                256,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getDigests()),
                KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA384);
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getEncryptionPaddings()));
        assertSelfSignedCertificateSignatureVerifies(TEST_ALIAS_1);
        assertKeyPairAndCertificateUsableForTLSPeerAuthentication(TEST_ALIAS_1);
    }

    public void testGenerate_RSA_ModernSpec_UsableForTLSPeerAuth() throws Exception {
        KeyPairGenerator generator = getRsaGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN
                        | KeyProperties.PURPOSE_VERIFY
                        | KeyProperties.PURPOSE_DECRYPT)
                .setDigests(KeyProperties.DIGEST_NONE,
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE,
                        KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "RSA",
                2048,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getBlockModes()));
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getDigests()),
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512);
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getSignaturePaddings()),
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(keyInfo.getEncryptionPaddings()),
                KeyProperties.ENCRYPTION_PADDING_NONE,
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1);
        assertSelfSignedCertificateSignatureVerifies(TEST_ALIAS_1);
        assertKeyPairAndCertificateUsableForTLSPeerAuthentication(TEST_ALIAS_1);
    }

    // TODO: Test fingerprint-authorized and secure lock screen-authorized keys. These can't
    // currently be tested here because CTS does not require that secure lock screen is set up and
    // that at least one fingerprint is enrolled.

    public void testGenerate_EC_ModernSpec_KeyNotYetValid() throws Exception {
        KeyPairGenerator generator = getEcGenerator();
        Date validityStart = new Date(System.currentTimeMillis() + DAY_IN_MILLIS);
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(256)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setKeyValidityStart(validityStart)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "EC",
                256,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(validityStart, keyInfo.getKeyValidityStart());
    }

    public void testGenerate_RSA_ModernSpec_KeyExpiredForOrigination() throws Exception {
        KeyPairGenerator generator = getRsaGenerator();
        Date originationEnd = new Date(System.currentTimeMillis() - DAY_IN_MILLIS);
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(1024)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeyValidityForOriginationEnd(originationEnd)
                .build());
        KeyPair keyPair = generator.generateKeyPair();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "RSA",
                1024,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(originationEnd, keyInfo.getKeyValidityForOriginationEnd());
    }

    public void testGenerate_EC_ModernSpec_SupportedSizes() throws Exception {
        assertKeyGenUsingECSizeOnlyUsesCorrectCurve(224, ECCurves.NIST_P_224_SPEC);
        assertKeyGenUsingECSizeOnlyUsesCorrectCurve(256, ECCurves.NIST_P_256_SPEC);
        assertKeyGenUsingECSizeOnlyUsesCorrectCurve(384, ECCurves.NIST_P_384_SPEC);
        assertKeyGenUsingECSizeOnlyUsesCorrectCurve(521, ECCurves.NIST_P_521_SPEC);
    }

    public void testGenerate_EC_ModernSpec_UnsupportedSizesRejected() throws Exception {
        for (int keySizeBits = 0; keySizeBits <= 1024; keySizeBits++) {
            if ((keySizeBits == 224) || (keySizeBits == 256) || (keySizeBits == 384)
                    || (keySizeBits == 521)) {
                // Skip supported sizes
                continue;
            }
            KeyPairGenerator generator = getEcGenerator();

            try {
                generator.initialize(new KeyGenParameterSpec.Builder(
                        TEST_ALIAS_1,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(keySizeBits)
                        .build());
                fail("EC KeyPairGenerator initialized with unsupported key size: "
                        + keySizeBits + " bits");
            } catch (InvalidAlgorithmParameterException expected) {
            }
        }
    }

    public void testGenerate_EC_ModernSpec_SupportedNamedCurves() throws Exception {
        assertKeyGenUsingECNamedCurveSupported("P-224", ECCurves.NIST_P_224_SPEC);
        assertKeyGenUsingECNamedCurveSupported("p-224", ECCurves.NIST_P_224_SPEC);
        assertKeyGenUsingECNamedCurveSupported("secp224r1", ECCurves.NIST_P_224_SPEC);
        assertKeyGenUsingECNamedCurveSupported("SECP224R1", ECCurves.NIST_P_224_SPEC);

        assertKeyGenUsingECNamedCurveSupported("P-256", ECCurves.NIST_P_256_SPEC);
        assertKeyGenUsingECNamedCurveSupported("p-256", ECCurves.NIST_P_256_SPEC);
        assertKeyGenUsingECNamedCurveSupported("secp256r1", ECCurves.NIST_P_256_SPEC);
        assertKeyGenUsingECNamedCurveSupported("SECP256R1", ECCurves.NIST_P_256_SPEC);
        assertKeyGenUsingECNamedCurveSupported("prime256v1", ECCurves.NIST_P_256_SPEC);
        assertKeyGenUsingECNamedCurveSupported("PRIME256V1", ECCurves.NIST_P_256_SPEC);

        assertKeyGenUsingECNamedCurveSupported("P-384", ECCurves.NIST_P_384_SPEC);
        assertKeyGenUsingECNamedCurveSupported("p-384", ECCurves.NIST_P_384_SPEC);
        assertKeyGenUsingECNamedCurveSupported("secp384r1", ECCurves.NIST_P_384_SPEC);
        assertKeyGenUsingECNamedCurveSupported("SECP384R1", ECCurves.NIST_P_384_SPEC);

        assertKeyGenUsingECNamedCurveSupported("P-521", ECCurves.NIST_P_521_SPEC);
        assertKeyGenUsingECNamedCurveSupported("p-521", ECCurves.NIST_P_521_SPEC);
        assertKeyGenUsingECNamedCurveSupported("secp521r1", ECCurves.NIST_P_521_SPEC);
        assertKeyGenUsingECNamedCurveSupported("SECP521R1", ECCurves.NIST_P_521_SPEC);
    }

    public void testGenerate_RSA_ModernSpec_SupportedSizes() throws Exception {
        assertKeyGenUsingRSASizeOnlySupported(512);
        assertKeyGenUsingRSASizeOnlySupported(768);
        assertKeyGenUsingRSASizeOnlySupported(1024);
        assertKeyGenUsingRSASizeOnlySupported(2048);
        assertKeyGenUsingRSASizeOnlySupported(3072);
        assertKeyGenUsingRSASizeOnlySupported(4096);

        // The above use F4. Check that F0 is supported as well, just in case somebody is crazy
        // enough.
        assertKeyGenUsingRSAKeyGenParameterSpecSupported(new RSAKeyGenParameterSpec(
                2048, RSAKeyGenParameterSpec.F0));
    }

    public void testGenerate_RSA_IndCpaEnforced() throws Exception {
        KeyGenParameterSpec.Builder goodBuilder = new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1, KeyProperties.PURPOSE_ENCRYPT)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                        KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1);
        assertKeyGenInitSucceeds("RSA", goodBuilder.build());

        // Should be fine because IND-CPA restriction applies only to encryption keys
        assertKeyGenInitSucceeds("RSA",
                TestUtils.buildUpon(goodBuilder, KeyProperties.PURPOSE_DECRYPT)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());

        assertKeyGenInitThrowsInvalidAlgorithmParameterException("RSA",
                TestUtils.buildUpon(goodBuilder)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());

        assertKeyGenInitSucceeds("RSA",
                TestUtils.buildUpon(goodBuilder)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(false)
                        .build());

        // Should fail because PKCS#7 padding doesn't work with RSA
        assertKeyGenInitThrowsInvalidAlgorithmParameterException("RSA",
                TestUtils.buildUpon(goodBuilder)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build());
    }

    public void testGenerate_EC_IndCpaEnforced() throws Exception {
        KeyGenParameterSpec.Builder goodBuilder = new KeyGenParameterSpec.Builder(
                TEST_ALIAS_2, KeyProperties.PURPOSE_ENCRYPT);
        assertKeyGenInitSucceeds("EC", goodBuilder.build());

        // Should be fine because IND-CPA restriction applies only to encryption keys
        assertKeyGenInitSucceeds("EC",
                TestUtils.buildUpon(goodBuilder, KeyProperties.PURPOSE_DECRYPT)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());

        assertKeyGenInitThrowsInvalidAlgorithmParameterException("EC",
                TestUtils.buildUpon(goodBuilder)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());

        assertKeyGenInitSucceeds("EC",
                TestUtils.buildUpon(goodBuilder)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(false)
                        .build());
    }

    // http://b/28384942
    public void testGenerateWithFarsiLocale() throws Exception {
        Locale defaultLocale = Locale.getDefault();
        // Note that we use farsi here because its number formatter doesn't use
        // arabic digits.
        Locale fa_IR = Locale.forLanguageTag("fa-IR");
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(fa_IR);
        assertFalse('0' == dfs.getZeroDigit());

        Locale.setDefault(fa_IR);
        try {
            KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");

            keyGenerator.initialize(new KeyGenParameterSpec.Builder(
                   TEST_ALIAS_1, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                   .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                   .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                   .build());

            keyGenerator.generateKeyPair();
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private void assertKeyGenInitSucceeds(String algorithm, AlgorithmParameterSpec params)
            throws Exception {
        KeyPairGenerator generator = getGenerator(algorithm);
        generator.initialize(params);
    }

    private void assertKeyGenInitThrowsInvalidAlgorithmParameterException(
            String algorithm, AlgorithmParameterSpec params) throws Exception {
        KeyPairGenerator generator = getGenerator(algorithm);
        try {
            generator.initialize(params);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}
    }

    private void assertKeyGenUsingECSizeOnlyUsesCorrectCurve(
            int keySizeBits, ECParameterSpec expectedParams) throws Exception {
        KeyPairGenerator generator = getEcGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(keySizeBits)
                .build(),
                mRng);
        mRng.resetCounters();
        KeyPair keyPair = generator.generateKeyPair();
        long consumedEntropyAmountBytes = mRng.getOutputSizeBytes();
        int expectedKeySize = expectedParams.getCurve().getField().getFieldSize();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "EC",
                expectedKeySize,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(expectedKeySize, keyInfo.getKeySize());
        TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                expectedParams,
                ((ECKey) keyPair.getPublic()).getParams());
        assertEquals(((keySizeBits + 7) / 8) * 8, consumedEntropyAmountBytes * 8);
    }

    private void assertKeyGenUsingECNamedCurveSupported(
            String curveName, ECParameterSpec expectedParams) throws Exception {
        KeyPairGenerator generator = getEcGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec(curveName))
                .build(),
                mRng);
        mRng.resetCounters();
        KeyPair keyPair = generator.generateKeyPair();
        long consumedEntropyAmountBytes = mRng.getOutputSizeBytes();
        int expectedKeySize = expectedParams.getCurve().getField().getFieldSize();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "EC",
                expectedKeySize,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(expectedKeySize, keyInfo.getKeySize());
        TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                expectedParams,
                ((ECKey) keyPair.getPublic()).getParams());
        assertEquals(((expectedKeySize + 7) / 8) * 8, consumedEntropyAmountBytes * 8);
    }

    private void assertKeyGenUsingRSASizeOnlySupported(int keySizeBits) throws Exception {
        KeyPairGenerator generator = getRsaGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(keySizeBits)
                .build(),
                mRng);
        mRng.resetCounters();
        KeyPair keyPair = generator.generateKeyPair();
        long consumedEntropyAmountBytes = mRng.getOutputSizeBytes();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "RSA",
                keySizeBits,
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(keySizeBits, keyInfo.getKeySize());
        assertEquals(((keySizeBits + 7) / 8) * 8, consumedEntropyAmountBytes * 8);
    }

    private void assertKeyGenUsingRSAKeyGenParameterSpecSupported(
            RSAKeyGenParameterSpec spec) throws Exception {
        KeyPairGenerator generator = getRsaGenerator();
        generator.initialize(new KeyGenParameterSpec.Builder(
                TEST_ALIAS_1,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(spec)
                .build(),
                mRng);
        mRng.resetCounters();
        KeyPair keyPair = generator.generateKeyPair();
        long consumedEntropyAmountBytes = mRng.getOutputSizeBytes();
        assertGeneratedKeyPairAndSelfSignedCertificate(
                keyPair,
                TEST_ALIAS_1,
                "RSA",
                spec.getKeysize(),
                DEFAULT_CERT_SUBJECT,
                DEFAULT_CERT_SERIAL_NUMBER,
                DEFAULT_CERT_NOT_BEFORE,
                DEFAULT_CERT_NOT_AFTER);
        assertEquals(spec.getPublicExponent(),
                ((RSAPublicKey) keyPair.getPublic()).getPublicExponent());
        KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());
        assertEquals(spec.getKeysize(), keyInfo.getKeySize());
        assertEquals(((spec.getKeysize() + 7) / 8) * 8, consumedEntropyAmountBytes * 8);
    }

    private static void assertSelfSignedCertificateSignatureVerifies(Certificate certificate) {
        try {
            certificate.verify(certificate.getPublicKey());
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify self-signed certificate signature", e);
        }
    }

    private void assertGeneratedKeyPairAndSelfSignedCertificate(
            KeyPair keyPair, String alias,
            String expectedKeyAlgorithm,
            int expectedKeySize,
            X500Principal expectedCertSubject,
            BigInteger expectedCertSerialNumber,
            Date expectedCertNotBefore,
            Date expectedCertNotAfter)
            throws Exception {
        assertNotNull(keyPair);
        TestUtils.assertKeyPairSelfConsistent(keyPair);
        TestUtils.assertKeySize(expectedKeySize, keyPair);
        assertEquals(expectedKeyAlgorithm, keyPair.getPublic().getAlgorithm());
        TestUtils.assertKeyStoreKeyPair(mKeyStore, alias, keyPair);

        X509Certificate cert = (X509Certificate) mKeyStore.getCertificate(alias);
        assertEquals(keyPair.getPublic(), cert.getPublicKey());
        assertX509CertificateParameters(cert,
                expectedCertSubject,
                expectedCertSerialNumber,
                expectedCertNotBefore,
                expectedCertNotAfter);
        // Assert that the certificate chain consists only of the above certificate
        MoreAsserts.assertContentsInOrder(
                Arrays.asList(mKeyStore.getCertificateChain(alias)), cert);
    }

    private void assertSelfSignedCertificateSignatureVerifies(String alias) throws Exception {
        assertSelfSignedCertificateSignatureVerifies(mKeyStore.getCertificate(alias));
    }

    private void assertKeyPairAndCertificateUsableForTLSPeerAuthentication(String alias)
            throws Exception {
        assertUsableForTLSPeerAuthentication(
                (PrivateKey) mKeyStore.getKey(alias, null),
                mKeyStore.getCertificateChain(alias));
    }

    private static void assertX509CertificateParameters(
            X509Certificate actualCert,
            X500Principal expectedSubject, BigInteger expectedSerialNumber,
            Date expectedNotBefore, Date expectedNotAfter) {
        assertEquals(expectedSubject, actualCert.getSubjectDN());
        assertEquals(expectedSubject, actualCert.getIssuerDN());
        assertEquals(expectedSerialNumber, actualCert.getSerialNumber());
        assertDateEquals(expectedNotBefore, actualCert.getNotBefore());
        assertDateEquals(expectedNotAfter, actualCert.getNotAfter());
    }

    private static void assertUsableForTLSPeerAuthentication(
            PrivateKey privateKey,
            Certificate[] certificateChain) throws Exception {
        // Set up both client and server to use the same private key + cert, and to trust that cert
        // when it's presented by peer. This exercises the use of the private key both in client
        // and server scenarios.
        X509Certificate[] x509CertificateChain = new X509Certificate[certificateChain.length];
        for (int i = 0; i < certificateChain.length; i++) {
            x509CertificateChain[i] = (X509Certificate) certificateChain[i];
        }
        TestKeyStore serverKeyStore = TestKeyStore.getServer();
        // Make the peer trust the root certificate in the chain. As opposed to making the peer
        // trust the leaf certificate, this will ensure that the whole chain verifies.
        serverKeyStore.keyStore.setCertificateEntry(
                "trusted", certificateChain[certificateChain.length - 1]);
        SSLContext serverContext = TestSSLContext.createSSLContext("TLS",
                new KeyManager[] {
                    TestKeyManager.wrap(new MyKeyManager(privateKey, x509CertificateChain))
                },
                TestKeyStore.createTrustManagers(serverKeyStore.keyStore));
        SSLContext clientContext = serverContext;

        if ("EC".equalsIgnoreCase(privateKey.getAlgorithm())) {
            // As opposed to RSA (see below) EC keys are used in the same way in all cipher suites.
            // Assert that the key works with the default list of cipher suites.
            assertSSLConnectionWithClientAuth(
                    clientContext, serverContext, null, x509CertificateChain, x509CertificateChain);
        } else if ("RSA".equalsIgnoreCase(privateKey.getAlgorithm())) {
            // RSA keys are used differently between Forward Secure and non-Forward Secure cipher
            // suites. For example, RSA key exchange requires the server to decrypt using its RSA
            // private key, whereas ECDHE_RSA key exchange requires the server to sign usnig its
            // RSA private key. We thus assert that the key works with Forward Secure cipher suites
            // and that it works with non-Forward Secure cipher suites.
            List<String> fsCipherSuites = new ArrayList<String>();
            List<String> nonFsCipherSuites = new ArrayList<String>();
            for (String cipherSuite : clientContext.getDefaultSSLParameters().getCipherSuites()) {
                if (cipherSuite.contains("_ECDHE_RSA_") || cipherSuite.contains("_DHE_RSA_")) {
                    fsCipherSuites.add(cipherSuite);
                } else if (cipherSuite.contains("_RSA_WITH_")) {
                    nonFsCipherSuites.add(cipherSuite);
                }
            }
            assertFalse("No FS RSA cipher suites enabled by default", fsCipherSuites.isEmpty());
            assertFalse("No non-FS RSA cipher suites enabled", nonFsCipherSuites.isEmpty());

            // Assert that the key works with RSA Forward Secure cipher suites.
            assertSSLConnectionWithClientAuth(
                    clientContext, serverContext, fsCipherSuites.toArray(new String[0]),
                    x509CertificateChain, x509CertificateChain);
            // Assert that the key works with RSA non-Forward Secure cipher suites.
            assertSSLConnectionWithClientAuth(
                    clientContext, serverContext, nonFsCipherSuites.toArray(new String[0]),
                    x509CertificateChain, x509CertificateChain);
        } else {
            fail("Unsupported key algorithm: " + privateKey.getAlgorithm());
        }
    }

    private static void assertSSLConnectionWithClientAuth(
            SSLContext clientContext, SSLContext serverContext, String[] enabledCipherSuites,
            X509Certificate[] expectedClientCertChain, X509Certificate[] expectedServerCertChain)
            throws Exception {
        SSLServerSocket serverSocket = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0);
        InetAddress host = InetAddress.getLocalHost();
        int port = serverSocket.getLocalPort();
        SSLSocket client = (SSLSocket) clientContext.getSocketFactory().createSocket(host, port);

        final SSLSocket server = (SSLSocket) serverSocket.accept();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Certificate[]> future = executor.submit(new Callable<Certificate[]>() {
            @Override
            public Certificate[] call() throws Exception {
                server.setNeedClientAuth(true);
                server.setWantClientAuth(true);
                server.startHandshake();
                return server.getSession().getPeerCertificates();
            }
        });
        executor.shutdown();
        if (enabledCipherSuites != null) {
            client.setEnabledCipherSuites(enabledCipherSuites);
        }
        client.startHandshake();
        Certificate[] usedServerCerts = client.getSession().getPeerCertificates();
        Certificate[] usedClientCerts = future.get();
        client.close();
        server.close();

        assertNotNull(usedServerCerts);
        assertEquals(Arrays.asList(expectedServerCertChain), Arrays.asList(usedServerCerts));

        assertNotNull(usedClientCerts);
        assertEquals(Arrays.asList(expectedClientCertChain), Arrays.asList(usedClientCerts));
    }

    private static class MyKeyManager extends X509ExtendedKeyManager {
        private final PrivateKey key;
        private final X509Certificate[] chain;

        public MyKeyManager(PrivateKey key, X509Certificate[] certChain) {
            this.key = key;
            this.chain = certChain;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return "fake";
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return "fake";
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return chain;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[] { "fake" };
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[] { "fake" };
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return key;
        }
    }


    private static void assertDateEquals(Date date1, Date date2) {
        assertDateEquals(null, date1, date2);
    }

    private static void assertDateEquals(String message, Date date1, Date date2) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");

        String result1 = formatter.format(date1);
        String result2 = formatter.format(date2);

        assertEquals(message, result1, result2);
    }

    private KeyPairGenerator getRsaGenerator()
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return getGenerator("RSA");
    }

    private KeyPairGenerator getEcGenerator()
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return getGenerator("EC");
    }

    private KeyPairGenerator getGenerator(String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return KeyPairGenerator.getInstance(algorithm, "AndroidKeyStore");
    }

    private static void assertOneOf(int actual, int... expected) {
        assertOneOf(null, actual, expected);
    }

    private static void assertOneOf(String message, int actual, int... expected) {
        for (int expectedValue : expected) {
            if (actual == expectedValue) {
                return;
            }
        }
        fail(((message != null) ? message + ". " : "")
                + "Expected one of " + Arrays.asList(expected)
                + ", actual: <" + actual + ">");
    }

    private KeyGenParameterSpec.Builder getWorkingSpec() {
        return getWorkingSpec(0);
    }

    private KeyGenParameterSpec.Builder getWorkingSpec(int purposes) {
        return new KeyGenParameterSpec.Builder(TEST_ALIAS_1, purposes);
    }
}
