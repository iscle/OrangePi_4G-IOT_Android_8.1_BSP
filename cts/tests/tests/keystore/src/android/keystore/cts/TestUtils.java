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

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.test.MoreAsserts;
import junit.framework.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

abstract class TestUtils extends Assert {

    static final String EXPECTED_CRYPTO_OP_PROVIDER_NAME = "AndroidKeyStoreBCWorkaround";
    static final String EXPECTED_PROVIDER_NAME = "AndroidKeyStore";

    static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;


    private TestUtils() {}

    /**
     * Asserts the the key algorithm and algorithm-specific parameters of the two keys in the
     * provided pair match.
     */
    static void assertKeyPairSelfConsistent(KeyPair keyPair) {
        assertKeyPairSelfConsistent(keyPair.getPublic(), keyPair.getPrivate());
    }

    /**
     * Asserts the the key algorithm and public algorithm-specific parameters of the two provided
     * keys match.
     */
    static void assertKeyPairSelfConsistent(PublicKey publicKey, PrivateKey privateKey) {
        assertNotNull(publicKey);
        assertNotNull(privateKey);
        assertEquals(publicKey.getAlgorithm(), privateKey.getAlgorithm());
        String keyAlgorithm = publicKey.getAlgorithm();
        if ("EC".equalsIgnoreCase(keyAlgorithm)) {
            assertTrue("EC public key must be instanceof ECKey: "
                    + publicKey.getClass().getName(),
                    publicKey instanceof ECKey);
            assertTrue("EC private key must be instanceof ECKey: "
                    + privateKey.getClass().getName(),
                    privateKey instanceof ECKey);
            assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                    "Private key must have the same EC parameters as public key",
                    ((ECKey) publicKey).getParams(), ((ECKey) privateKey).getParams());
        } else if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            assertTrue("RSA public key must be instance of RSAKey: "
                    + publicKey.getClass().getName(),
                    publicKey instanceof RSAKey);
            assertTrue("RSA private key must be instance of RSAKey: "
                    + privateKey.getClass().getName(),
                    privateKey instanceof RSAKey);
            assertEquals("Private and public key must have the same RSA modulus",
                    ((RSAKey) publicKey).getModulus(), ((RSAKey) privateKey).getModulus());
        } else {
            fail("Unsuported key algorithm: " + keyAlgorithm);
        }
    }

    static int getKeySizeBits(Key key) {
        if (key instanceof ECKey) {
            return ((ECKey) key).getParams().getCurve().getField().getFieldSize();
        } else if (key instanceof RSAKey) {
            return ((RSAKey) key).getModulus().bitLength();
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + key.getClass());
        }
    }

    static void assertKeySize(int expectedSizeBits, KeyPair keyPair) {
        assertEquals(expectedSizeBits, getKeySizeBits(keyPair.getPrivate()));
        assertEquals(expectedSizeBits, getKeySizeBits(keyPair.getPublic()));
    }

    /**
     * Asserts that the provided key pair is an Android Keystore key pair stored under the provided
     * alias.
     */
    static void assertKeyStoreKeyPair(KeyStore keyStore, String alias, KeyPair keyPair) {
        assertKeyMaterialExportable(keyPair.getPublic());
        assertKeyMaterialNotExportable(keyPair.getPrivate());
        assertTransparentKey(keyPair.getPublic());
        assertOpaqueKey(keyPair.getPrivate());

        KeyStore.Entry entry;
        Certificate cert;
        try {
            entry = keyStore.getEntry(alias, null);
            cert = keyStore.getCertificate(alias);
        } catch (KeyStoreException | UnrecoverableEntryException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to load entry: " + alias, e);
        }
        assertNotNull(entry);

        assertTrue(entry instanceof KeyStore.PrivateKeyEntry);
        KeyStore.PrivateKeyEntry privEntry = (KeyStore.PrivateKeyEntry) entry;
        assertEquals(cert, privEntry.getCertificate());
        assertTrue("Certificate must be an X.509 certificate: " + cert.getClass(),
                cert instanceof X509Certificate);
        final X509Certificate x509Cert = (X509Certificate) cert;

        PrivateKey keystorePrivateKey = privEntry.getPrivateKey();
        PublicKey keystorePublicKey = cert.getPublicKey();
        assertEquals(keyPair.getPrivate(), keystorePrivateKey);
        assertEquals(keyPair.getPublic(), keystorePublicKey);

        assertEquals(
                "Public key used to sign certificate should have the same algorithm as in KeyPair",
                keystorePublicKey.getAlgorithm(), x509Cert.getPublicKey().getAlgorithm());

        Certificate[] chain = privEntry.getCertificateChain();
        if (chain.length == 0) {
            fail("Empty certificate chain");
            return;
        }
        assertEquals(cert, chain[0]);
    }


    private static void assertKeyMaterialExportable(Key key) {
        if (key instanceof PublicKey) {
            assertEquals("X.509", key.getFormat());
        } else if (key instanceof PrivateKey) {
            assertEquals("PKCS#8", key.getFormat());
        } else if (key instanceof SecretKey) {
            assertEquals("RAW", key.getFormat());
        } else {
            fail("Unsupported key type: " + key.getClass().getName());
        }
        byte[] encodedForm = key.getEncoded();
        assertNotNull(encodedForm);
        if (encodedForm.length == 0) {
            fail("Empty encoded form");
        }
    }

    private static void assertKeyMaterialNotExportable(Key key) {
        assertEquals(null, key.getFormat());
        assertEquals(null, key.getEncoded());
    }

    private static void assertOpaqueKey(Key key) {
        assertFalse(key.getClass().getName() + " is a transparent key", isTransparentKey(key));
    }

    private static void assertTransparentKey(Key key) {
        assertTrue(key.getClass().getName() + " is not a transparent key", isTransparentKey(key));
    }

    private static boolean isTransparentKey(Key key) {
        if (key instanceof PrivateKey) {
            return (key instanceof ECPrivateKey) || (key instanceof RSAPrivateKey);
        } else if (key instanceof PublicKey) {
            return (key instanceof ECPublicKey) || (key instanceof RSAPublicKey);
        } else if (key instanceof SecretKey) {
            return (key instanceof SecretKeySpec);
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + key.getClass().getName());
        }
    }

    static void assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
            ECParameterSpec expected, ECParameterSpec actual) {
        assertECParameterSpecEqualsIgnoreSeedIfNotPresent(null, expected, actual);
    }

    static void assertECParameterSpecEqualsIgnoreSeedIfNotPresent(String message,
            ECParameterSpec expected, ECParameterSpec actual) {
        EllipticCurve expectedCurve = expected.getCurve();
        EllipticCurve actualCurve = actual.getCurve();
        String msgPrefix = (message != null) ? message + ": " : "";
        assertEquals(msgPrefix + "curve field", expectedCurve.getField(), actualCurve.getField());
        assertEquals(msgPrefix + "curve A", expectedCurve.getA(), actualCurve.getA());
        assertEquals(msgPrefix + "curve B", expectedCurve.getB(), actualCurve.getB());
        assertEquals(msgPrefix + "order", expected.getOrder(), actual.getOrder());
        assertEquals(msgPrefix + "generator",
                expected.getGenerator(), actual.getGenerator());
        assertEquals(msgPrefix + "cofactor", expected.getCofactor(), actual.getCofactor());

        // If present, the seed must be the same
        byte[] expectedSeed = expectedCurve.getSeed();
        byte[] actualSeed = expectedCurve.getSeed();
        if ((expectedSeed != null) && (actualSeed != null)) {
            MoreAsserts.assertEquals(expectedSeed, actualSeed);
        }
    }

    static KeyInfo getKeyInfo(Key key) throws InvalidKeySpecException, NoSuchAlgorithmException,
            NoSuchProviderException {
        if ((key instanceof PrivateKey) || (key instanceof PublicKey)) {
            return KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore")
                    .getKeySpec(key, KeyInfo.class);
        } else if (key instanceof SecretKey) {
            return (KeyInfo) SecretKeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore")
                    .getKeySpec((SecretKey) key, KeyInfo.class);
        } else {
            throw new IllegalArgumentException("Unexpected key type: " + key.getClass());
        }
    }

    static <T> void assertContentsInAnyOrder(Iterable<T> actual, T... expected) {
        assertContentsInAnyOrder(null, actual, expected);
    }

    static <T> void assertContentsInAnyOrder(String message, Iterable<T> actual, T... expected) {
        Map<T, Integer> actualFreq = getFrequencyTable(actual);
        Map<T, Integer> expectedFreq = getFrequencyTable(expected);
        if (actualFreq.equals(expectedFreq)) {
            return;
        }

        Map<T, Integer> extraneousFreq = new HashMap<T, Integer>();
        for (Map.Entry<T, Integer> actualEntry : actualFreq.entrySet()) {
            int actualCount = actualEntry.getValue();
            Integer expectedCount = expectedFreq.get(actualEntry.getKey());
            int diff = actualCount - ((expectedCount != null) ? expectedCount : 0);
            if (diff > 0) {
                extraneousFreq.put(actualEntry.getKey(), diff);
            }
        }

        Map<T, Integer> missingFreq = new HashMap<T, Integer>();
        for (Map.Entry<T, Integer> expectedEntry : expectedFreq.entrySet()) {
            int expectedCount = expectedEntry.getValue();
            Integer actualCount = actualFreq.get(expectedEntry.getKey());
            int diff = expectedCount - ((actualCount != null) ? actualCount : 0);
            if (diff > 0) {
                missingFreq.put(expectedEntry.getKey(), diff);
            }
        }

        List<T> extraneous = frequencyTableToValues(extraneousFreq);
        List<T> missing = frequencyTableToValues(missingFreq);
        StringBuilder result = new StringBuilder();
        String delimiter = "";
        if (message != null) {
            result.append(message).append(".");
            delimiter = " ";
        }
        if (!missing.isEmpty()) {
            result.append(delimiter).append("missing: " + missing);
            delimiter = ", ";
        }
        if (!extraneous.isEmpty()) {
            result.append(delimiter).append("extraneous: " + extraneous);
        }
        fail(result.toString());
    }

    private static <T> Map<T, Integer> getFrequencyTable(Iterable<T> values) {
        Map<T, Integer> result = new HashMap<T, Integer>();
        for (T value : values) {
            Integer count = result.get(value);
            if (count == null) {
                count = 1;
            } else {
                count++;
            }
            result.put(value, count);
        }
        return result;
    }

    private static <T> Map<T, Integer> getFrequencyTable(T... values) {
        Map<T, Integer> result = new HashMap<T, Integer>();
        for (T value : values) {
            Integer count = result.get(value);
            if (count == null) {
                count = 1;
            } else {
                count++;
            }
            result.put(value, count);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static <T> List<T> frequencyTableToValues(Map<T, Integer> table) {
        if (table.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<T>();
        boolean comparableValues = true;
        for (Map.Entry<T, Integer> entry : table.entrySet()) {
            T value = entry.getKey();
            if (!(value instanceof Comparable)) {
                comparableValues = false;
            }
            int frequency = entry.getValue();
            for (int i = 0; i < frequency; i++) {
                result.add(value);
            }
        }

        if (comparableValues) {
            sortAssumingComparable(result);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void sortAssumingComparable(List<?> values) {
        Collections.sort((List<Comparable>)values);
    }

    static String[] toLowerCase(String... values) {
        if (values == null) {
            return null;
        }
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            result[i] = (value != null) ? value.toLowerCase() : null;
        }
        return result;
    }

    static PrivateKey getRawResPrivateKey(Context context, int resId) throws Exception {
        byte[] pkcs8EncodedForm;
        try (InputStream in = context.getResources().openRawResource(resId)) {
            pkcs8EncodedForm = drain(in);
        }
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pkcs8EncodedForm);

        try {
            return KeyFactory.getInstance("EC").generatePrivate(privateKeySpec);
        } catch (InvalidKeySpecException e) {
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec);
            } catch (InvalidKeySpecException e2) {
                throw new InvalidKeySpecException("The key is neither EC nor RSA", e);
            }
        }
    }

    static X509Certificate getRawResX509Certificate(Context context, int resId) throws Exception {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        }
    }

    static KeyPair importIntoAndroidKeyStore(
            String alias,
            PrivateKey privateKey,
            Certificate certificate,
            KeyProtection keyProtection) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.setEntry(alias,
                new KeyStore.PrivateKeyEntry(privateKey, new Certificate[] {certificate}),
                keyProtection);
        return new KeyPair(
                keyStore.getCertificate(alias).getPublicKey(),
                (PrivateKey) keyStore.getKey(alias, null));
    }

    static ImportedKey importIntoAndroidKeyStore(
            String alias,
            SecretKey key,
            KeyProtection keyProtection) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.setEntry(alias,
                new KeyStore.SecretKeyEntry(key),
                keyProtection);
        return new ImportedKey(alias, key, (SecretKey) keyStore.getKey(alias, null));
    }

    static ImportedKey importIntoAndroidKeyStore(
            String alias, Context context, int privateResId, int certResId, KeyProtection params)
                    throws Exception {
        Certificate originalCert = TestUtils.getRawResX509Certificate(context, certResId);
        PublicKey originalPublicKey = originalCert.getPublicKey();
        PrivateKey originalPrivateKey = TestUtils.getRawResPrivateKey(context, privateResId);

        // Check that the domain parameters match between the private key and the public key. This
        // is to catch accidental errors where a test provides the wrong resource ID as one of the
        // parameters.
        if (!originalPublicKey.getAlgorithm().equalsIgnoreCase(originalPrivateKey.getAlgorithm())) {
            throw new IllegalArgumentException("Key algorithm mismatch."
                    + " Public: " + originalPublicKey.getAlgorithm()
                    + ", private: " + originalPrivateKey.getAlgorithm());
        }
        assertKeyPairSelfConsistent(originalPublicKey, originalPrivateKey);

        KeyPair keystoreBacked = TestUtils.importIntoAndroidKeyStore(
                alias, originalPrivateKey, originalCert,
                params);
        assertKeyPairSelfConsistent(keystoreBacked);
        assertKeyPairSelfConsistent(keystoreBacked.getPublic(), originalPrivateKey);
        return new ImportedKey(
                alias,
                new KeyPair(originalCert.getPublicKey(), originalPrivateKey),
                keystoreBacked);
    }

    static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int chunkSize;
        while ((chunkSize = in.read(buffer)) != -1) {
            result.write(buffer, 0, chunkSize);
        }
        return result.toByteArray();
    }

    static KeyProtection.Builder buildUpon(KeyProtection params) {
        return buildUponInternal(params, null);
    }

    static KeyProtection.Builder buildUpon(KeyProtection params, int newPurposes) {
        return buildUponInternal(params, newPurposes);
    }

    static KeyProtection.Builder buildUpon(
            KeyProtection.Builder builder) {
        return buildUponInternal(builder.build(), null);
    }

    static KeyProtection.Builder buildUpon(
            KeyProtection.Builder builder, int newPurposes) {
        return buildUponInternal(builder.build(), newPurposes);
    }

    private static KeyProtection.Builder buildUponInternal(
            KeyProtection spec, Integer newPurposes) {
        int purposes = (newPurposes == null) ? spec.getPurposes() : newPurposes;
        KeyProtection.Builder result = new KeyProtection.Builder(purposes);
        result.setBlockModes(spec.getBlockModes());
        if (spec.isDigestsSpecified()) {
            result.setDigests(spec.getDigests());
        }
        result.setEncryptionPaddings(spec.getEncryptionPaddings());
        result.setSignaturePaddings(spec.getSignaturePaddings());
        result.setKeyValidityStart(spec.getKeyValidityStart());
        result.setKeyValidityForOriginationEnd(spec.getKeyValidityForOriginationEnd());
        result.setKeyValidityForConsumptionEnd(spec.getKeyValidityForConsumptionEnd());
        result.setRandomizedEncryptionRequired(spec.isRandomizedEncryptionRequired());
        result.setUserAuthenticationRequired(spec.isUserAuthenticationRequired());
        result.setUserAuthenticationValidityDurationSeconds(
                spec.getUserAuthenticationValidityDurationSeconds());
        result.setBoundToSpecificSecureUserId(spec.getBoundToSpecificSecureUserId());
        return result;
    }

    static KeyGenParameterSpec.Builder buildUpon(KeyGenParameterSpec spec) {
        return buildUponInternal(spec, null);
    }

    static KeyGenParameterSpec.Builder buildUpon(KeyGenParameterSpec spec, int newPurposes) {
        return buildUponInternal(spec, newPurposes);
    }

    static KeyGenParameterSpec.Builder buildUpon(
            KeyGenParameterSpec.Builder builder) {
        return buildUponInternal(builder.build(), null);
    }

    static KeyGenParameterSpec.Builder buildUpon(
            KeyGenParameterSpec.Builder builder, int newPurposes) {
        return buildUponInternal(builder.build(), newPurposes);
    }

    private static KeyGenParameterSpec.Builder buildUponInternal(
            KeyGenParameterSpec spec, Integer newPurposes) {
        int purposes = (newPurposes == null) ? spec.getPurposes() : newPurposes;
        KeyGenParameterSpec.Builder result =
                new KeyGenParameterSpec.Builder(spec.getKeystoreAlias(), purposes);
        if (spec.getKeySize() >= 0) {
            result.setKeySize(spec.getKeySize());
        }
        if (spec.getAlgorithmParameterSpec() != null) {
            result.setAlgorithmParameterSpec(spec.getAlgorithmParameterSpec());
        }
        result.setCertificateNotBefore(spec.getCertificateNotBefore());
        result.setCertificateNotAfter(spec.getCertificateNotAfter());
        result.setCertificateSerialNumber(spec.getCertificateSerialNumber());
        result.setCertificateSubject(spec.getCertificateSubject());
        result.setBlockModes(spec.getBlockModes());
        if (spec.isDigestsSpecified()) {
            result.setDigests(spec.getDigests());
        }
        result.setEncryptionPaddings(spec.getEncryptionPaddings());
        result.setSignaturePaddings(spec.getSignaturePaddings());
        result.setKeyValidityStart(spec.getKeyValidityStart());
        result.setKeyValidityForOriginationEnd(spec.getKeyValidityForOriginationEnd());
        result.setKeyValidityForConsumptionEnd(spec.getKeyValidityForConsumptionEnd());
        result.setRandomizedEncryptionRequired(spec.isRandomizedEncryptionRequired());
        result.setUserAuthenticationRequired(spec.isUserAuthenticationRequired());
        result.setUserAuthenticationValidityDurationSeconds(
                spec.getUserAuthenticationValidityDurationSeconds());
        return result;
    }

    static KeyPair getKeyPairForKeyAlgorithm(String keyAlgorithm, Iterable<KeyPair> keyPairs) {
        for (KeyPair keyPair : keyPairs) {
            if (keyAlgorithm.equalsIgnoreCase(keyPair.getPublic().getAlgorithm())) {
                return keyPair;
            }
        }
        throw new IllegalArgumentException("No KeyPair for key algorithm " + keyAlgorithm);
    }

    static Key getKeyForKeyAlgorithm(String keyAlgorithm, Iterable<? extends Key> keys) {
        for (Key key : keys) {
            if (keyAlgorithm.equalsIgnoreCase(key.getAlgorithm())) {
                return key;
            }
        }
        throw new IllegalArgumentException("No Key for key algorithm " + keyAlgorithm);
    }

    static byte[] generateLargeKatMsg(byte[] seed, int msgSizeBytes) throws Exception {
        byte[] result = new byte[msgSizeBytes];
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        int resultOffset = 0;
        int resultRemaining = msgSizeBytes;
        while (resultRemaining > 0) {
            seed = digest.digest(seed);
            int chunkSize = Math.min(seed.length, resultRemaining);
            System.arraycopy(seed, 0, result, resultOffset, chunkSize);
            resultOffset += chunkSize;
            resultRemaining -= chunkSize;
        }
        return result;
    }

    static byte[] leftPadWithZeroBytes(byte[] array, int length) {
        if (array.length >= length) {
            return array;
        }
        byte[] result = new byte[length];
        System.arraycopy(array, 0, result, result.length - array.length, array.length);
        return result;
    }

    static boolean contains(int[] array, int value) {
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    static boolean isHmacAlgorithm(String algorithm) {
        return algorithm.toUpperCase(Locale.US).startsWith("HMAC");
    }

    static String getHmacAlgorithmDigest(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        if (!algorithmUpperCase.startsWith("HMAC")) {
            return null;
        }
        String result = algorithmUpperCase.substring("HMAC".length());
        if (result.startsWith("SHA")) {
            result = "SHA-" + result.substring("SHA".length());
        }
        return result;
    }

    static String getCipherKeyAlgorithm(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.startsWith("AES/")) {
            return KeyProperties.KEY_ALGORITHM_AES;
        } else if (transformationUpperCase.startsWith("RSA/")) {
            return KeyProperties.KEY_ALGORITHM_RSA;
        } else {
            throw new IllegalArgumentException("Unsupported transformation: " + transformation);
        }
    }

    static boolean isCipherSymmetric(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.startsWith("AES/")) {
            return true;
        } else if (transformationUpperCase.startsWith("RSA/")) {
            return false;
        } else {
            throw new IllegalArgumentException("Unsupported transformation: " + transformation);
        }
    }

    static String getCipherDigest(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.contains("/OAEP")) {
            if (transformationUpperCase.endsWith("/OAEPPADDING")) {
                return KeyProperties.DIGEST_SHA1;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-1ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA1;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-224ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA224;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-256ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA256;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-384ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA384;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-512ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA512;
            } else {
                throw new RuntimeException("Unsupported OAEP padding scheme: "
                        + transformation);
            }
        } else {
            return null;
        }
    }

    static String getCipherEncryptionPadding(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.endsWith("/NOPADDING")) {
            return KeyProperties.ENCRYPTION_PADDING_NONE;
        } else if (transformationUpperCase.endsWith("/PKCS7PADDING")) {
            return KeyProperties.ENCRYPTION_PADDING_PKCS7;
        } else if (transformationUpperCase.endsWith("/PKCS1PADDING")) {
            return KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;
        } else if (transformationUpperCase.split("/")[2].startsWith("OAEP")) {
            return KeyProperties.ENCRYPTION_PADDING_RSA_OAEP;
        } else {
            throw new IllegalArgumentException("Unsupported transformation: " + transformation);
        }
    }

    static String getCipherBlockMode(String transformation) {
        return transformation.split("/")[1].toUpperCase(Locale.US);
    }

    static String getSignatureAlgorithmDigest(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        int withIndex = algorithmUpperCase.indexOf("WITH");
        if (withIndex == -1) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
        String digest = algorithmUpperCase.substring(0, withIndex);
        if (digest.startsWith("SHA")) {
            digest = "SHA-" + digest.substring("SHA".length());
        }
        return digest;
    }

    static String getSignatureAlgorithmPadding(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        if (algorithmUpperCase.endsWith("WITHECDSA")) {
            return null;
        } else if (algorithmUpperCase.endsWith("WITHRSA")) {
            return KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
        } else if (algorithmUpperCase.endsWith("WITHRSA/PSS")) {
            return KeyProperties.SIGNATURE_PADDING_RSA_PSS;
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }

    static String getSignatureAlgorithmKeyAlgorithm(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        if (algorithmUpperCase.endsWith("WITHECDSA")) {
            return KeyProperties.KEY_ALGORITHM_EC;
        } else if ((algorithmUpperCase.endsWith("WITHRSA"))
                || (algorithmUpperCase.endsWith("WITHRSA/PSS"))) {
            return KeyProperties.KEY_ALGORITHM_RSA;
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }

    static boolean isKeyLongEnoughForSignatureAlgorithm(String algorithm, Key key) {
        String keyAlgorithm = key.getAlgorithm();
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            // No length restrictions for ECDSA
            return true;
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            // No length restrictions for RSA
            String digest = getSignatureAlgorithmDigest(algorithm);
            int digestOutputSizeBits = getDigestOutputSizeBits(digest);
            if (digestOutputSizeBits == -1) {
                // No digesting -- assume the key is long enough for the message
                return true;
            }
            String paddingScheme = getSignatureAlgorithmPadding(algorithm);
            int paddingOverheadBytes;
            if (KeyProperties.SIGNATURE_PADDING_RSA_PKCS1.equalsIgnoreCase(paddingScheme)) {
                paddingOverheadBytes = 30;
            } else if (KeyProperties.SIGNATURE_PADDING_RSA_PSS.equalsIgnoreCase(paddingScheme)) {
                int saltSizeBytes = (digestOutputSizeBits + 7) / 8;
                paddingOverheadBytes = saltSizeBytes + 1;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported signature padding scheme: " + paddingScheme);
            }
            int minKeySizeBytes = paddingOverheadBytes + (digestOutputSizeBits + 7) / 8 + 1;
            int keySizeBytes = ((RSAKey) key).getModulus().bitLength() / 8;
            return keySizeBytes >= minKeySizeBytes;
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    static int getMaxSupportedPlaintextInputSizeBytes(String transformation, Key key) {
        String keyAlgorithm = getCipherKeyAlgorithm(transformation);
        if (KeyProperties.KEY_ALGORITHM_AES.equalsIgnoreCase(keyAlgorithm)) {
            return Integer.MAX_VALUE;
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            String encryptionPadding = getCipherEncryptionPadding(transformation);
            int modulusSizeBytes = (getKeySizeBits(key) + 7) / 8;
            if (KeyProperties.ENCRYPTION_PADDING_NONE.equalsIgnoreCase(encryptionPadding)) {
                return modulusSizeBytes - 1;
            } else if (KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1.equalsIgnoreCase(
                    encryptionPadding)) {
                return modulusSizeBytes - 11;
            } else if (KeyProperties.ENCRYPTION_PADDING_RSA_OAEP.equalsIgnoreCase(
                    encryptionPadding)) {
                String digest = getCipherDigest(transformation);
                int digestOutputSizeBytes = (getDigestOutputSizeBits(digest) + 7) / 8;
                return modulusSizeBytes - 2 * digestOutputSizeBytes - 2;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported encryption padding scheme: " + encryptionPadding);
            }
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    static int getDigestOutputSizeBits(String digest) {
        if (KeyProperties.DIGEST_NONE.equals(digest)) {
            return -1;
        } else if (KeyProperties.DIGEST_MD5.equals(digest)) {
            return 128;
        } else if (KeyProperties.DIGEST_SHA1.equals(digest)) {
            return 160;
        } else if (KeyProperties.DIGEST_SHA224.equals(digest)) {
            return 224;
        } else if (KeyProperties.DIGEST_SHA256.equals(digest)) {
            return 256;
        } else if (KeyProperties.DIGEST_SHA384.equals(digest)) {
            return 384;
        } else if (KeyProperties.DIGEST_SHA512.equals(digest)) {
            return 512;
        } else {
            throw new IllegalArgumentException("Unsupported digest: " + digest);
        }
    }

    static byte[] concat(byte[] arr1, byte[] arr2) {
        return concat(arr1, 0, (arr1 != null) ? arr1.length : 0,
                arr2, 0, (arr2 != null) ? arr2.length : 0);
    }

    static byte[] concat(byte[] arr1, int offset1, int len1,
            byte[] arr2, int offset2, int len2) {
        if (len1 == 0) {
            return subarray(arr2, offset2, len2);
        } else if (len2 == 0) {
            return subarray(arr1, offset1, len1);
        }
        byte[] result = new byte[len1 + len2];
        if (len1 > 0) {
            System.arraycopy(arr1, offset1, result, 0, len1);
        }
        if (len2 > 0) {
            System.arraycopy(arr2, offset2, result, len1, len2);
        }
        return result;
    }

    static byte[] subarray(byte[] arr, int offset, int len) {
        if (len == 0) {
            return EmptyArray.BYTE;
        }
        if ((offset == 0) && (arr.length == len)) {
            return arr;
        }
        byte[] result = new byte[len];
        System.arraycopy(arr, offset, result, 0, len);
        return result;
    }

    static KeyProtection getMinimalWorkingImportParametersForSigningingWith(
            String signatureAlgorithm) {
        String keyAlgorithm = getSignatureAlgorithmKeyAlgorithm(signatureAlgorithm);
        String digest = getSignatureAlgorithmDigest(signatureAlgorithm);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            return new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                    .setDigests(digest)
                    .build();
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            String padding = getSignatureAlgorithmPadding(signatureAlgorithm);
            return new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                    .setDigests(digest)
                    .setSignaturePaddings(padding)
                    .build();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported signature algorithm: " + signatureAlgorithm);
        }
    }

    static KeyProtection getMinimalWorkingImportParametersForCipheringWith(
            String transformation, int purposes) {
        return getMinimalWorkingImportParametersForCipheringWith(transformation, purposes, false);
    }

    static KeyProtection getMinimalWorkingImportParametersForCipheringWith(
            String transformation, int purposes, boolean ivProvidedWhenEncrypting) {
        String keyAlgorithm = TestUtils.getCipherKeyAlgorithm(transformation);
        if (KeyProperties.KEY_ALGORITHM_AES.equalsIgnoreCase(keyAlgorithm)) {
            String encryptionPadding = TestUtils.getCipherEncryptionPadding(transformation);
            String blockMode = TestUtils.getCipherBlockMode(transformation);
            boolean randomizedEncryptionRequired = true;
            if (KeyProperties.BLOCK_MODE_ECB.equalsIgnoreCase(blockMode)) {
                randomizedEncryptionRequired = false;
            } else if ((ivProvidedWhenEncrypting)
                    && ((purposes & KeyProperties.PURPOSE_ENCRYPT) != 0)) {
                randomizedEncryptionRequired = false;
            }
            return new KeyProtection.Builder(
                    purposes)
                    .setBlockModes(blockMode)
                    .setEncryptionPaddings(encryptionPadding)
                    .setRandomizedEncryptionRequired(randomizedEncryptionRequired)
                    .build();
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            String digest = TestUtils.getCipherDigest(transformation);
            String encryptionPadding = TestUtils.getCipherEncryptionPadding(transformation);
            boolean randomizedEncryptionRequired =
                    !KeyProperties.ENCRYPTION_PADDING_NONE.equalsIgnoreCase(encryptionPadding);
            return new KeyProtection.Builder(
                    purposes)
                    .setDigests((digest != null) ? new String[] {digest} : EmptyArray.STRING)
                    .setEncryptionPaddings(encryptionPadding)
                    .setRandomizedEncryptionRequired(randomizedEncryptionRequired)
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    static byte[] getBigIntegerMagnitudeBytes(BigInteger value) {
        return removeLeadingZeroByteIfPresent(value.toByteArray());
    }

    private static byte[] removeLeadingZeroByteIfPresent(byte[] value) {
        if ((value.length < 1) || (value[0] != 0)) {
            return value;
        }
        return TestUtils.subarray(value, 1, value.length - 1);
    }
}
