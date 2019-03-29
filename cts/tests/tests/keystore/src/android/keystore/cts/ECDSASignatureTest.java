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
import android.security.keystore.KeyProtection;
import android.test.AndroidTestCase;

import android.keystore.cts.R;

import java.security.KeyPair;
import java.security.Security;
import java.security.Signature;
import java.util.Arrays;
import java.util.Collection;

public class ECDSASignatureTest extends AndroidTestCase {

    public void testNONEwithECDSATruncatesInputToFieldSize() throws Exception {
        for (ImportedKey key : importKatKeyPairs("NONEwithECDSA")) {
            try {
                assertNONEwithECDSATruncatesInputToFieldSize(key.getKeystoreBackedKeyPair());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + key.getAlias(), e);
            }
        }
    }

    private void assertNONEwithECDSATruncatesInputToFieldSize(KeyPair keyPair)
            throws Exception {
        int keySizeBits = TestUtils.getKeySizeBits(keyPair.getPublic());
        byte[] message = new byte[(keySizeBits * 3) / 8];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (i + 1);
        }

        Signature signature = Signature.getInstance("NONEwithECDSA");
        signature.initSign(keyPair.getPrivate());
        assertSame(Security.getProvider(SignatureTest.EXPECTED_PROVIDER_NAME),
                signature.getProvider());
        signature.update(message);
        byte[] sigBytes = signature.sign();

        signature = Signature.getInstance(signature.getAlgorithm(), signature.getProvider());
        signature.initVerify(keyPair.getPublic());

        // Verify the full-length message
        signature.update(message);
        assertTrue(signature.verify(sigBytes));

        // Verify the message truncated to field size
        signature.update(message, 0, (keySizeBits + 7) / 8);
        assertTrue(signature.verify(sigBytes));

        // Verify message truncated to one byte shorter than field size -- this should fail
        signature.update(message, 0, (keySizeBits / 8) - 1);
        assertFalse(signature.verify(sigBytes));
    }

    public void testNONEwithECDSASupportsMessagesShorterThanFieldSize() throws Exception {
        for (ImportedKey key : importKatKeyPairs("NONEwithECDSA")) {
            try {
                assertNONEwithECDSASupportsMessagesShorterThanFieldSize(
                        key.getKeystoreBackedKeyPair());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + key.getAlias(), e);
            }
        }
    }

    private void assertNONEwithECDSASupportsMessagesShorterThanFieldSize(KeyPair keyPair)
            throws Exception {
        int keySizeBits = TestUtils.getKeySizeBits(keyPair.getPublic());
        byte[] message = new byte[(keySizeBits * 3 / 4) / 8];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (i + 1);
        }

        Signature signature = Signature.getInstance("NONEwithECDSA");
        signature.initSign(keyPair.getPrivate());
        assertSame(Security.getProvider(SignatureTest.EXPECTED_PROVIDER_NAME),
                signature.getProvider());
        signature.update(message);
        byte[] sigBytes = signature.sign();

        signature = Signature.getInstance(signature.getAlgorithm(), signature.getProvider());
        signature.initVerify(keyPair.getPublic());

        // Verify the message
        signature.update(message);
        assertTrue(signature.verify(sigBytes));

        // Assert that the message is left-padded with zero bits
        byte[] fullLengthMessage = TestUtils.leftPadWithZeroBytes(message, keySizeBits / 8);
        signature.update(fullLengthMessage);
        assertTrue(signature.verify(sigBytes));
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
                TestUtils.importIntoAndroidKeyStore("testECsecp224r1", context,
                        R.raw.ec_key3_secp224r1_pkcs8, R.raw.ec_key3_secp224r1_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testECsecp256r1", context,
                        R.raw.ec_key4_secp256r1_pkcs8, R.raw.ec_key4_secp256r1_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testECsecp384r1", context,
                        R.raw.ec_key5_secp384r1_pkcs8, R.raw.ec_key5_secp384r1_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testECsecp521r1", context,
                        R.raw.ec_key6_secp521r1_pkcs8, R.raw.ec_key6_secp521r1_cert, importParams),
                });
    }
}
