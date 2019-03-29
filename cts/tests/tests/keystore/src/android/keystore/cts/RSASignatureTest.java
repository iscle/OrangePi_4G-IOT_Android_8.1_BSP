/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.keystore.cts;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import android.keystore.cts.R;

import android.content.Context;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.test.AndroidTestCase;

public class RSASignatureTest extends AndroidTestCase {

    private static final String EXPECTED_PROVIDER_NAME = SignatureTest.EXPECTED_PROVIDER_NAME;

    private static final String[] SIGNATURE_ALGORITHMS;

    static {
        List<String> sigAlgs = new ArrayList<>();
        for (String algorithm : SignatureTest.EXPECTED_SIGNATURE_ALGORITHMS) {
            String keyAlgorithm = TestUtils.getSignatureAlgorithmKeyAlgorithm(algorithm);
            if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
                sigAlgs.add(algorithm);
            }
        }
        SIGNATURE_ALGORITHMS = sigAlgs.toArray(new String[sigAlgs.size()]);
    }

    public void testMaxMessageSizeWhenNoDigestUsed() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey keyPair : importKatKeyPairs("NONEwithRSA")) {
            PublicKey publicKey = keyPair.getKeystoreBackedKeyPair().getPublic();
            PrivateKey privateKey = keyPair.getKeystoreBackedKeyPair().getPrivate();
            int modulusSizeBits = ((RSAKey) publicKey).getModulus().bitLength();
            try {
                int modulusSizeBytes = (modulusSizeBits + 7) / 8;
                // PKCS#1 signature padding must be at least 11 bytes long (00 || 01 || PS || 00)
                // where PS must be at least 8 bytes long).
                int expectedMaxMessageSizeBytes = modulusSizeBytes - 11;
                byte[] msg = new byte[expectedMaxMessageSizeBytes + 1];
                Arrays.fill(msg, (byte) 0xf0);

                // Assert that a message of expected maximum length is accepted
                Signature signature = Signature.getInstance("NONEwithRSA", provider);
                signature.initSign(privateKey);
                signature.update(msg, 0, expectedMaxMessageSizeBytes);
                byte[] sigBytes = signature.sign();

                signature.initVerify(publicKey);
                signature.update(msg, 0, expectedMaxMessageSizeBytes);
                assertTrue(signature.verify(sigBytes));

                // Assert that a message longer than expected maximum length is rejected
                signature = Signature.getInstance(signature.getAlgorithm(), provider);
                signature.initSign(privateKey);
                try {
                    signature.update(msg, 0, expectedMaxMessageSizeBytes + 1);
                    signature.sign();
                    fail();
                } catch (SignatureException expected) {
                }

                signature.initVerify(publicKey);
                try {
                    signature.update(msg, 0, expectedMaxMessageSizeBytes + 1);
                    signature.verify(sigBytes);
                    fail();
                } catch (SignatureException expected) {
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + modulusSizeBits + " bit key", e);
            }
        }
    }

    public void testSmallKeyRejected() throws Exception {
        // Use a 512 bit key which should prevent the use of any digests larger than SHA-256
        // because the padded form of the digested message will be larger than modulus size.
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (String algorithm : SIGNATURE_ALGORITHMS) {
            try {
                String digest = TestUtils.getSignatureAlgorithmDigest(algorithm);
                if (KeyProperties.DIGEST_NONE.equalsIgnoreCase(digest)) {
                    // Ignore signature algorithms without digest -- this is tested in a separate
                    // test above.
                    continue;
                }
                int digestOutputSizeBits = TestUtils.getDigestOutputSizeBits(digest);
                if (digestOutputSizeBits <= 256) {
                    // 256-bit and shorter digests are short enough to work with a 512 bit key.
                    continue;
                }

                KeyPair keyPair = TestUtils.importIntoAndroidKeyStore("test1",
                        getContext(),
                        R.raw.rsa_key5_512_pkcs8,
                        R.raw.rsa_key5_512_cert,
                        TestUtils.getMinimalWorkingImportParametersForSigningingWith(algorithm))
                        .getKeystoreBackedKeyPair();
                assertEquals(512, ((RSAKey) keyPair.getPrivate()).getModulus().bitLength());
                assertEquals(512, ((RSAKey) keyPair.getPublic()).getModulus().bitLength());

                Signature signature = Signature.getInstance(algorithm, provider);
                // Assert that either initSign or sign fails. We don't expect all keymaster
                // implementations to fail early, during initSign.
                try {
                    signature.initSign(keyPair.getPrivate());
                    signature.update("A message".getBytes("UTF-8"));
                    byte[] sigBytes = signature.sign();
                    fail("Unexpectedly generated a signature (" + sigBytes.length + " bytes): "
                            + HexEncoding.encode(sigBytes));
                } catch (InvalidKeyException | SignatureException expected) {
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    private Collection<ImportedKey> importKatKeyPairs(String signatureAlgorithm)
            throws Exception {
        KeyProtection params =
                TestUtils.getMinimalWorkingImportParametersForSigningingWith(signatureAlgorithm);
        return importKatKeyPairs(getContext(), params);
    }

    static Collection<ImportedKey> importKatKeyPairs(
            Context context, KeyProtection importParams) throws Exception {
        return Arrays.asList(new ImportedKey[] {
                TestUtils.importIntoAndroidKeyStore("testRSA512", context,
                        R.raw.rsa_key5_512_pkcs8, R.raw.rsa_key5_512_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testRSA768", context,
                        R.raw.rsa_key6_768_pkcs8, R.raw.rsa_key6_768_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testRSA1024", context,
                        R.raw.rsa_key3_1024_pkcs8, R.raw.rsa_key3_1024_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testRSA2048", context,
                        R.raw.rsa_key8_2048_pkcs8, R.raw.rsa_key8_2048_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testRSA3072", context,
                        R.raw.rsa_key7_3072_pksc8, R.raw.rsa_key7_3072_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testRSA4096", context,
                        R.raw.rsa_key4_4096_pkcs8, R.raw.rsa_key4_4096_cert, importParams),
                });
    }
}
