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

import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

abstract class AESGCMCipherTestBase extends BlockCipherTestBase {

    protected abstract byte[] getKatAad();
    protected abstract byte[] getKatCiphertextWhenKatAadPresent();

    @Override
    protected boolean isStreamCipher() {
        return true;
    }

    @Override
    protected boolean isAuthenticatedCipher() {
        return true;
    }

    @Override
    protected int getKatAuthenticationTagLengthBytes() {
        return getKatCiphertext().length - getKatPlaintext().length;
    }

    @Override
    protected int getBlockSize() {
        return 16;
    }

    @Override
    protected AlgorithmParameterSpec getKatAlgorithmParameterSpec() {
        return new GCMParameterSpec(getKatAuthenticationTagLengthBytes() * 8, getKatIv());
    }

    @Override
    protected byte[] getIv(AlgorithmParameters params) throws InvalidParameterSpecException {
        GCMParameterSpec spec = params.getParameterSpec(GCMParameterSpec.class);
        return spec.getIV();
    }

    public void testKatEncryptWithAadProvidedInOneGo() throws Exception {
        createCipher();
        assertKatTransformWithAadProvidedInOneGo(
                Cipher.ENCRYPT_MODE,
                getKatAad(),
                getKatPlaintext(),
                getKatCiphertextWhenKatAadPresent());
    }

    public void testKatDecryptWithAadProvidedInOneGo() throws Exception {
        createCipher();
        assertKatTransformWithAadProvidedInOneGo(
                Cipher.DECRYPT_MODE,
                getKatAad(),
                getKatCiphertextWhenKatAadPresent(),
                getKatPlaintext());
    }

    public void testKatEncryptWithAadProvidedInChunks() throws Exception {
        createCipher();
        assertKatTransformWithAadProvidedInChunks(
                Cipher.ENCRYPT_MODE,
                getKatAad(),
                getKatPlaintext(),
                getKatCiphertextWhenKatAadPresent(),
                1);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.ENCRYPT_MODE,
                getKatAad(),
                getKatPlaintext(),
                getKatCiphertextWhenKatAadPresent(),
                8);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.ENCRYPT_MODE,
                getKatAad(),
                getKatPlaintext(),
                getKatCiphertextWhenKatAadPresent(),
                3);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.ENCRYPT_MODE,
                getKatAad(),
                getKatPlaintext(),
                getKatCiphertextWhenKatAadPresent(),
                7);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.ENCRYPT_MODE,
                getKatAad(),
                getKatPlaintext(),
                getKatCiphertextWhenKatAadPresent(),
                23);
    }

    public void testKatDecryptWithAadProvidedInChunks() throws Exception {
        createCipher();
        assertKatTransformWithAadProvidedInChunks(
                Cipher.DECRYPT_MODE,
                getKatAad(),
                getKatCiphertextWhenKatAadPresent(),
                getKatPlaintext(),
                1);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.DECRYPT_MODE,
                getKatAad(),
                getKatCiphertextWhenKatAadPresent(),
                getKatPlaintext(),
                8);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.DECRYPT_MODE,
                getKatAad(),
                getKatCiphertextWhenKatAadPresent(),
                getKatPlaintext(),
                3);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.DECRYPT_MODE,
                getKatAad(),
                getKatCiphertextWhenKatAadPresent(),
                getKatPlaintext(),
                7);
        assertKatTransformWithAadProvidedInChunks(
                Cipher.DECRYPT_MODE,
                getKatAad(),
                getKatCiphertextWhenKatAadPresent(),
                getKatPlaintext(),
                23);
    }

    private void assertKatTransformWithAadProvidedInOneGo(int opmode,
            byte[] aad, byte[] input, byte[] expectedOutput) throws Exception {
        initKat(opmode);
        updateAAD(aad);
        assertEquals(expectedOutput, doFinal(input));

        initKat(opmode);
        updateAAD(aad, 0, aad.length);
        assertEquals(expectedOutput, doFinal(input));

        initKat(opmode);
        updateAAD(ByteBuffer.wrap(aad));
        assertEquals(expectedOutput, doFinal(input));
    }

    private void assertKatTransformWithAadProvidedInChunks(int opmode,
            byte[] aad, byte[] input, byte[] expectedOutput, int maxChunkSize) throws Exception {
        createCipher();
        initKat(opmode);
        int aadOffset = 0;
        while (aadOffset < aad.length) {
            int chunkSize = Math.min(aad.length - aadOffset, maxChunkSize);
            updateAAD(aad, aadOffset, chunkSize);
            aadOffset += chunkSize;
        }
        assertEquals(expectedOutput, doFinal(input));
    }

    public void testCiphertextBitflipDetectedWhenDecrypting() throws Exception {
        createCipher();
        Key key = importKey(getKatKey());
        byte[] ciphertext = getKatCiphertext();
        ciphertext[ciphertext.length / 2] ^= 0x40;
        init(Cipher.DECRYPT_MODE, key, getKatAlgorithmParameterSpec());
        try {
            doFinal(ciphertext);
            fail();
        } catch (AEADBadTagException expected) {}
    }

    public void testAadBitflipDetectedWhenDecrypting() throws Exception {
        createCipher();
        Key key = importKey(getKatKey());
        byte[] ciphertext = getKatCiphertextWhenKatAadPresent();
        byte[] aad = getKatCiphertext();
        aad[aad.length / 3] ^= 0x2;
        init(Cipher.DECRYPT_MODE, key, getKatAlgorithmParameterSpec());
        updateAAD(aad);
        try {
            doFinal(ciphertext);
            fail();
        } catch (AEADBadTagException expected) {}
    }

    public void testInitRejectsIvParameterSpec() throws Exception {
        assertInitRejectsIvParameterSpec(getKatIv());
    }
}
