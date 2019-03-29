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

import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.test.AndroidTestCase;

import junit.framework.AssertionFailedError;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

abstract class BlockCipherTestBase extends AndroidTestCase {

    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_CRYPTO_OP_PROVIDER_NAME;

    private KeyStore mAndroidKeyStore;
    private int mNextKeyId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAndroidKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mAndroidKeyStore.load(null);
        for (Enumeration<String> e = mAndroidKeyStore.aliases(); e.hasMoreElements();) {
            mAndroidKeyStore.deleteEntry(e.nextElement());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            for (Enumeration<String> e = mAndroidKeyStore.aliases(); e.hasMoreElements();) {
                mAndroidKeyStore.deleteEntry(e.nextElement());
            }
        } finally {
            super.tearDown();
        }
    }

    protected abstract String getTransformation();
    protected abstract int getBlockSize();

    protected abstract byte[] getKatKey();
    protected abstract byte[] getKatIv();
    protected abstract AlgorithmParameterSpec getKatAlgorithmParameterSpec();
    protected abstract byte[] getKatPlaintext();
    protected abstract byte[] getKatCiphertext();
    protected abstract int getKatAuthenticationTagLengthBytes();
    protected abstract boolean isStreamCipher();
    protected abstract boolean isAuthenticatedCipher();

    protected abstract byte[] getIv(AlgorithmParameters params)
            throws InvalidParameterSpecException;

    private byte[] getKatInput(int opmode) {
        switch (opmode) {
            case Cipher.ENCRYPT_MODE:
                return getKatPlaintext();
            case Cipher.DECRYPT_MODE:
                return getKatCiphertext();
            default:
                throw new IllegalArgumentException("Invalid opmode: " + opmode);
        }
    }

    private byte[] getKatOutput(int opmode) {
        switch (opmode) {
            case Cipher.ENCRYPT_MODE:
                return getKatCiphertext();
            case Cipher.DECRYPT_MODE:
                return getKatPlaintext();
            default:
                throw new IllegalArgumentException("Invalid opmode: " + opmode);
        }
    }

    private Cipher mCipher;
    private int mOpmode;

    public void testGetAlgorithm() throws Exception {
        createCipher();
        assertEquals(getTransformation(), mCipher.getAlgorithm());
    }

    public void testGetProvider() throws Exception {
        createCipher();
        Provider expectedProvider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertSame(expectedProvider, mCipher.getProvider());
    }

    public void testGetBlockSize() throws Exception {
        createCipher();
        assertEquals(getBlockSize(), mCipher.getBlockSize());
    }

    public void testGetExemptionMechanism() throws Exception {
        createCipher();
        assertNull(mCipher.getExemptionMechanism());
    }

    public void testGetParameters() throws Exception {
        createCipher();
        assertAlgoritmParametersIv(null);

        initKat(Cipher.ENCRYPT_MODE);
        assertAlgoritmParametersIv(getKatIv());
        doFinal(getKatPlaintext());
        assertAlgoritmParametersIv(getKatIv());

        initKat(Cipher.DECRYPT_MODE);
        assertAlgoritmParametersIv(getKatIv());
        doFinal(getKatCiphertext());
        assertAlgoritmParametersIv(getKatIv());
    }

    private void assertAlgoritmParametersIv(byte[] expectedIv)
            throws InvalidParameterSpecException {
        AlgorithmParameters actualParameters = mCipher.getParameters();
        if (expectedIv == null) {
            assertNull(actualParameters);
        } else {
            byte[] actualIv = getIv(actualParameters);
            assertEquals(expectedIv, actualIv);
        }
    }

    public void testGetOutputSizeInEncryptionMode() throws Exception {
        int blockSize = getBlockSize();
        createCipher();
        try {
            mCipher.getOutputSize(blockSize);
            fail();
        } catch (IllegalStateException expected) {}

        initKat(Cipher.ENCRYPT_MODE);
        if (isAuthenticatedCipher()) {
            // Authenticated ciphers do not return any output when decrypting until doFinal where
            // ciphertext is authenticated.
            for (int input = 0; input <= blockSize * 2; input++) {
                int actualOutputSize = mCipher.getOutputSize(input);
                int expectedOutputSize = input + getKatAuthenticationTagLengthBytes();
                if (actualOutputSize < expectedOutputSize) {
                    fail("getOutputSize(" + expectedOutputSize + ") underestimated output size"
                            + ". min expected: <" + expectedOutputSize
                            + ">, actual: <" + actualOutputSize + ">");
                }
            }
            return;
        } else if (isStreamCipher()) {
            // Unauthenticated stream ciphers do not buffer input or output.
            for (int input = 0; input <= blockSize * 2; input++) {
                int actualOutputSize = mCipher.getOutputSize(input);
                if (actualOutputSize < input) {
                    fail("getOutputSize(" + input + ") underestimated output size. min expected: <"
                            + input + ">, actual: <" + actualOutputSize + ">");
                }
            }
            return;
        }
        // Not a stream cipher -- input may be buffered.

        for (int buffered = 0; buffered < blockSize; buffered++) {
            // Assert that the output of getOutputSize is not lower than the minimum expected.
            for (int input = 0; input <= blockSize * 2; input++) {
                int inputInclBuffered = buffered + input;
                // doFinal dominates the output size.
                // One full plaintext block results in one ciphertext block.
                int minExpectedOutputSize = inputInclBuffered - (inputInclBuffered % blockSize);
                if (isPaddingEnabled()) {
                    // Regardless of whether there is a partial input block, an additional block of
                    // ciphertext should be output.
                    minExpectedOutputSize += blockSize;
                } else {
                    // When no padding is enabled, any remaining partial block of plaintext will
                    // cause an error. Thus, there's no need to account for its ciphertext.
                }
                int actualOutputSize = mCipher.getOutputSize(input);
                if (actualOutputSize < minExpectedOutputSize) {
                    fail("getOutputSize(" + input + ") underestimated output size when buffered == "
                            + buffered + ". min expected: <"
                            + minExpectedOutputSize + ">, actual: <" + actualOutputSize + ">");
                }
            }

            if (buffered == blockSize - 1) {
                break;
            }
            // Buffer one more byte of input.
            assertNull("buffered: " + buffered, update(new byte[1]));
        }
    }

    public void testGetOutputSizeInDecryptionMode() throws Exception {
        int blockSize = getBlockSize();
        createCipher();
        try {
            mCipher.getOutputSize(blockSize);
            fail();
        } catch (IllegalStateException expected) {}

        initKat(Cipher.DECRYPT_MODE);
        if ((!isAuthenticatedCipher()) && (isStreamCipher())) {
            // Unauthenticated stream ciphers do not buffer input or output.
            for (int input = 0; input <= blockSize * 2; input++) {
                int actualOutputSize = mCipher.getOutputSize(input);
                int expectedOutputSize = input;
                if (actualOutputSize < expectedOutputSize) {
                    fail("getOutputSize(" + expectedOutputSize + ") underestimated output size"
                            + ". min expected: <" + expectedOutputSize
                            + ">, actual: <" + actualOutputSize + ">");
                }
            }
            return;
        }
        // Input may be buffered.

        for (int buffered = 0; buffered < blockSize; buffered++) {
            // Assert that the output of getOutputSize is not lower than the minimum expected.
            for (int input = 0; input <= blockSize * 2; input++) {
                int inputInclBuffered = buffered + input;
                // doFinal dominates the output size.
                int minExpectedOutputSize;
                if (isAuthenticatedCipher()) {
                    // Non-stream authenticated ciphers not supported
                    assertTrue(isStreamCipher());

                    // Authenticated stream cipher
                    minExpectedOutputSize =
                            inputInclBuffered - getKatAuthenticationTagLengthBytes();
                } else {
                    // Unauthenticated block cipher.

                    // One full ciphertext block results in one ciphertext block.
                    minExpectedOutputSize = inputInclBuffered - (inputInclBuffered % blockSize);
                    if (isPaddingEnabled()) {
                        if ((inputInclBuffered % blockSize) == 0) {
                            // No more ciphertext remaining. Thus, the last plaintext block is at
                            // most blockSize - 1 bytes long.
                            minExpectedOutputSize--;
                        } else {
                            // Partial ciphertext block cannot be decrypted. Thus, the last
                            // plaintext block would not have been output.
                            minExpectedOutputSize -= blockSize;
                        }
                    } else {
                        // When no padding is enabled, any remaining ciphertext will cause a error
                        // because only full blocks can be decrypted. Thus, there's no need to
                        // account for its plaintext.
                    }
                }
                if (minExpectedOutputSize < 0) {
                    minExpectedOutputSize = 0;
                }
                int actualOutputSize = mCipher.getOutputSize(input);
                if (actualOutputSize < minExpectedOutputSize) {
                    fail("getOutputSize(" + input + ") underestimated output size when buffered == "
                            + buffered + ". min expected: <"
                            + minExpectedOutputSize + ">, actual: <" + actualOutputSize + ">");
                }
            }

            if (buffered == blockSize - 1) {
                break;
            }
            // Buffer one more byte of input.
            assertNull("buffered: " + buffered, update(new byte[1]));
        }
    }

    public void testInitRequiresIvInDecryptMode() throws Exception {
        if (getKatIv() == null) {
            // IV not used in this transformation.
            return;
        }

        createCipher();
        try {
            init(Cipher.DECRYPT_MODE, getKey());
            fail();
        } catch (InvalidKeyException expected) {}

        createCipher();
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (SecureRandom) null);
            fail();
        } catch (InvalidKeyException expected) {}

        createCipher();
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameterSpec) null, null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        createCipher();
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameterSpec) null, null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        createCipher();
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameters) null, null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        createCipher();
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameters) null, null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}
    }

    public void testGetIV() throws Exception {
        createCipher();
        assertNull(mCipher.getIV());

        initKat(Cipher.ENCRYPT_MODE);
        assertEquals(getKatIv(), mCipher.getIV());

        byte[] ciphertext = doFinal(new byte[getBlockSize()]);
        assertEquals(getKatIv(), mCipher.getIV());

        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        assertEquals(getKatIv(), mCipher.getIV());

        doFinal(ciphertext);
        assertEquals(getKatIv(), mCipher.getIV());
    }

    public void testIvGeneratedAndUsedWhenEncryptingWithoutExplicitIv() throws Exception {
        createCipher();
        SecretKey key = getKey();
        init(Cipher.ENCRYPT_MODE, key);
        byte[] generatedIv = mCipher.getIV();
        AlgorithmParameters generatedParams = mCipher.getParameters();
        if (getKatIv() == null) {
            // IV not needed by this transformation -- shouldn't have been generated by Cipher.init
            assertNull(generatedIv);
            assertNull(generatedParams);
        } else {
            // IV is needed by this transformation -- should've been generated by Cipher.init
            assertNotNull(generatedIv);
            assertEquals(getKatIv().length, generatedIv.length);
            assertNotNull(generatedParams);
            assertEquals(generatedIv, getIv(generatedParams));
        }

        // Assert that encrypting then decrypting using the above IV (or null) results in the
        // original plaintext.
        byte[] plaintext = new byte[getBlockSize()];
        byte[] ciphertext = doFinal(plaintext);
        createCipher();
        init(Cipher.DECRYPT_MODE, key, generatedParams);
        byte[] decryptedPlaintext = mCipher.doFinal(ciphertext);
        assertEquals(plaintext, decryptedPlaintext);
    }

    public void testGeneratedIvSurvivesReset() throws Exception {
        if (getKatIv() == null) {
            // This transformation does not use an IV
            return;
        }

        createCipher();
        init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = mCipher.getIV();
        AlgorithmParameters generatedParams = mCipher.getParameters();
        byte[] ciphertext = mCipher.doFinal(getKatPlaintext());
        // Assert that the IV is still there
        assertEquals(iv, mCipher.getIV());
        assertAlgoritmParametersIv(iv);

        if (getKatIv() != null) {
            // We try to prevent IV reuse by not letting the Cipher be reused.
            return;
        }

        // Assert that encrypting the same input after the above reset produces the same ciphertext.
        assertEquals(ciphertext, mCipher.doFinal(getKatPlaintext()));

        assertEquals(iv, mCipher.getIV());
        assertAlgoritmParametersIv(iv);

        // Just in case, test with a new instance of Cipher with the same parameters
        createCipher();
        init(Cipher.ENCRYPT_MODE, getKey(), generatedParams);
        assertEquals(ciphertext, mCipher.doFinal(getKatPlaintext()));
    }

    public void testGeneratedIvDoesNotSurviveReinitialization() throws Exception {
        if (getKatIv() == null) {
            // This transformation does not use an IV
            return;
        }

        createCipher();
        init(Cipher.ENCRYPT_MODE, getKey());
        byte[] ivBeforeReinitialization = mCipher.getIV();

        init(Cipher.ENCRYPT_MODE, getKey());
        // A new IV should've been generated
        if (Arrays.equals(ivBeforeReinitialization, mCipher.getIV())) {
            fail("Same auto-generated IV after Cipher reinitialized."
                    + " Broken implementation or you're very unlucky (p: 2^{-"
                    + (ivBeforeReinitialization.length * 8) + "})");
        }
    }

    public void testExplicitlySetIvDoesNotSurviveReinitialization() throws Exception {
        if (getKatIv() == null) {
            // This transformation does not use an IV
            return;
        }

        createCipher();
        initKat(Cipher.ENCRYPT_MODE);
        init(Cipher.ENCRYPT_MODE, getKey());
        // A new IV should've been generated
        if (Arrays.equals(getKatIv(), mCipher.getIV())) {
            fail("Auto-generated IV after Cipher reinitialized is the same as previous IV."
                    + " Broken implementation or you're very unlucky (p: 2^{-"
                    + (getKatIv().length * 8) + "})");
        }
    }

    public void testReinitializingInDecryptModeDoesNotUsePreviouslyUsedIv() throws Exception {
        if (getKatIv() == null) {
            // This transformation does not use an IV
            return;
        }

        createCipher();
        // Initialize with explicitly provided IV
        init(Cipher.ENCRYPT_MODE, getKey(), getKatAlgorithmParameterSpec());
        // Make sure the IV has been used, just in case it's set/cached lazily.
        mCipher.update(new byte[getBlockSize() * 2]);

        // IV required but not provided
        try {
            init(Cipher.DECRYPT_MODE, getKey());
            fail();
        } catch (InvalidKeyException expected) {}

        createCipher();
        // Initialize with a generated IV
        init(Cipher.ENCRYPT_MODE, getKey());
        mCipher.doFinal(getKatPlaintext());

        // IV required but not provided
        try {
            init(Cipher.DECRYPT_MODE, getKey());
            fail();
        } catch (InvalidKeyException expected) {}

        // IV required but not provided
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (SecureRandom) null);
            fail();
        } catch (InvalidKeyException expected) {}

        // IV required but not provided
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameterSpec) null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        // IV required but not provided
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameterSpec) null, null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        // IV required but not provided
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameters) null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        // IV required but not provided
        try {
            init(Cipher.DECRYPT_MODE, getKey(), (AlgorithmParameters) null, null);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}
    }

    public void testKeyDoesNotSurviveReinitialization() throws Exception {
        assertKeyDoesNotSurviveReinitialization(Cipher.ENCRYPT_MODE);
        assertKeyDoesNotSurviveReinitialization(Cipher.DECRYPT_MODE);
    }

    private void assertKeyDoesNotSurviveReinitialization(int opmode) throws Exception {
        byte[] input = getKatInput(opmode);
        createCipher();
        byte[] katKeyBytes = getKatKey();
        SecretKey key1 = importKey(katKeyBytes);
        init(opmode, key1, getKatAlgorithmParameterSpec());
        byte[] output1 = doFinal(input);

        // Create a different key by flipping a bit in the KAT key.
        katKeyBytes[0] ^= 1;
        SecretKey key2 = importKey(katKeyBytes);

        init(opmode, key2, getKatAlgorithmParameterSpec());
        byte[] output2;
        try {
            output2 = doFinal(input);
        } catch (BadPaddingException expected) {
            // Padding doesn't decode probably because the new key is being used. This can only
            // occur is padding is used.
            return;
        }

        // Either padding wasn't used or the old key was used.
        if (Arrays.equals(output1, output2)) {
            fail("Same output when reinitialized with a different key. opmode: " + opmode);
        }
    }

    public void testDoFinalResets() throws Exception {
        assertDoFinalResetsCipher(Cipher.DECRYPT_MODE);
        assertDoFinalResetsCipher(Cipher.ENCRYPT_MODE);
    }

    private void assertDoFinalResetsCipher(int opmode) throws Exception {
        byte[] input = getKatInput(opmode);
        byte[] expectedOutput = getKatOutput(opmode);

        createCipher();
        initKat(opmode);
        assertEquals(expectedOutput, doFinal(input));

        if ((opmode == Cipher.ENCRYPT_MODE) && (getKatIv() != null)) {
            // Assert that this cipher cannot be reused (thus making IV reuse harder)
            try {
                doFinal(input);
                fail();
            } catch (IllegalStateException expected) {}
            return;
        }

        // Assert that the same output is produced after the above reset
        assertEquals(expectedOutput, doFinal(input));

        // Assert that the same output is produced after the above reset. This time, make update()
        // buffer half a block of input.
        if (input.length < getBlockSize() * 2) {
            fail("This test requires an input which is at least two blocks long");
        }
        assertEquals(expectedOutput, concat(
                update(subarray(input, 0, getBlockSize() * 3 / 2)),
                doFinal(subarray(input, getBlockSize() * 3 / 2, input.length))));

        // Assert that the same output is produced after the above reset, despite half of the block
        // having been buffered prior to the reset. This is in case the implementation does not
        // empty that buffer when resetting.
        assertEquals(expectedOutput, doFinal(input));

        // Assert that the IV with which the cipher was initialized is still there after the resets.
        assertEquals(getKatIv(), mCipher.getIV());
        assertAlgoritmParametersIv(getKatIv());
    }

    public void testUpdateWithEmptyInputReturnsCorrectValue() throws Exception {
        // Test encryption
        createCipher();
        initKat(Cipher.ENCRYPT_MODE);
        assertUpdateWithEmptyInputReturnsNull();

        // Test decryption
        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        assertUpdateWithEmptyInputReturnsNull();
    }

    private void assertUpdateWithEmptyInputReturnsNull() {
        assertEquals(null, update(new byte[0]));
        assertEquals(null, update(new byte[getBlockSize() * 2], getBlockSize(), 0));
        assertEquals(null, update(new byte[getBlockSize()], 0, 0));

        // Feed two blocks through the Cipher, so that it's in a state where a block of input
        // produces a block of output.
        // Two blocks are used instead of one because when decrypting with padding enabled, output
        // lags behind input by a block because the Cipher doesn't know whether the most recent
        // input block was supposed to contain padding.
        update(new byte[getBlockSize() * 2]);

        assertEquals(null, update(new byte[0]));
        assertEquals(null, update(new byte[getBlockSize() * 2], getBlockSize(), 0));
        assertEquals(null, update(new byte[getBlockSize()], 0, 0));
    }

    public void testUpdateDoesNotProduceOutputWhenInsufficientInput() throws Exception {
        if (isStreamCipher()) {
            // Stream ciphers always produce output for non-empty input.
            return;
        }

        // Test encryption
        createCipher();
        initKat(Cipher.ENCRYPT_MODE);
        assertUpdateDoesNotProduceOutputWhenInsufficientInput();

        // Test decryption
        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        assertUpdateDoesNotProduceOutputWhenInsufficientInput();
    }

    private void assertUpdateDoesNotProduceOutputWhenInsufficientInput() throws Exception {
        if (getBlockSize() < 8) {
            fail("This test isn't designed for small block size: " + getBlockSize());
        }

        assertEquals(null, update(new byte[1]));
        assertEquals(null, update(new byte[1], 0, 1));
        assertEquals(0, update(new byte[1], 0, 1, new byte[getBlockSize()]));
        assertEquals(0, update(new byte[1], 0, 1, new byte[getBlockSize()], 0));
        assertEquals(0, update(ByteBuffer.allocate(1), ByteBuffer.allocate(getBlockSize())));

        // Feed a block through the Cipher, so that it's potentially no longer in an initial state.
        byte[] output = update(new byte[getBlockSize()]);
        assertEquals(getBlockSize(), output.length);

        assertEquals(null, update(new byte[1]));
        assertEquals(null, update(new byte[1], 0, 1));
        assertEquals(0, update(new byte[1], 0, 1, new byte[getBlockSize()]));
        assertEquals(0, update(new byte[1], 0, 1, new byte[getBlockSize()], 0));
        assertEquals(0, update(ByteBuffer.allocate(1), ByteBuffer.allocate(getBlockSize())));
    }

    public void testKatOneShotEncryptUsingDoFinal() throws Exception {
        createCipher();
        assertKatOneShotTransformUsingDoFinal(
                Cipher.ENCRYPT_MODE, getKatPlaintext(), getKatCiphertext());
    }

    public void testKatOneShotDecryptUsingDoFinal() throws Exception {
        createCipher();
        assertKatOneShotTransformUsingDoFinal(
                Cipher.DECRYPT_MODE, getKatCiphertext(), getKatPlaintext());
    }

    private void assertKatOneShotTransformUsingDoFinal(
            int opmode, byte[] input, byte[] expectedOutput) throws Exception {
        int bufferWithInputInTheMiddleCleartextOffset = 5;
        byte[] bufferWithInputInTheMiddle = concat(
                new byte[bufferWithInputInTheMiddleCleartextOffset],
                input,
                new byte[4]);

        initKat(opmode);
        assertEquals(expectedOutput, doFinal(input));
        initKat(opmode);
        assertEquals(expectedOutput, doFinal(input, 0, input.length));
        initKat(opmode);
        assertEquals(expectedOutput,
                doFinal(bufferWithInputInTheMiddle,
                        bufferWithInputInTheMiddleCleartextOffset,
                        input.length));

        ByteBuffer inputBuffer = ByteBuffer.wrap(
                bufferWithInputInTheMiddle,
                bufferWithInputInTheMiddleCleartextOffset,
                input.length);
        ByteBuffer actualOutputBuffer = ByteBuffer.allocate(expectedOutput.length);
        initKat(opmode);
        assertEquals(expectedOutput.length, doFinal(inputBuffer, actualOutputBuffer));
        assertEquals(0, inputBuffer.remaining());
        assertByteBufferEquals(
                (ByteBuffer) ByteBuffer.wrap(expectedOutput).position(expectedOutput.length),
                actualOutputBuffer);
    }

    public void testKatEncryptOneByteAtATime() throws Exception {
        createCipher();
        initKat(Cipher.ENCRYPT_MODE);
        byte[] plaintext = getKatPlaintext();
        byte[] expectedCiphertext = getKatCiphertext();
        int blockSize = getBlockSize();
        if (isStreamCipher()) {
            // Stream cipher -- one byte in, one byte out
            for (int plaintextIndex = 0; plaintextIndex < plaintext.length; plaintextIndex++) {
                byte[] output = update(new byte[] {plaintext[plaintextIndex]});
                assertEquals("plaintext index: " + plaintextIndex, 1, output.length);
                assertEquals("plaintext index: " + plaintextIndex,
                        expectedCiphertext[plaintextIndex], output[0]);
            }
            byte[] finalOutput = doFinal();
            byte[] expectedFinalOutput;
            if (isAuthenticatedCipher()) {
                expectedFinalOutput =
                        subarray(expectedCiphertext, plaintext.length, expectedCiphertext.length);
            } else {
                expectedFinalOutput = EmptyArray.BYTE;
            }
            assertEquals(expectedFinalOutput, finalOutput);
        } else {
            // Not a stream cipher -- operates on full blocks only.

            // Assert that a block of output is produced once a full block of input is provided.
            // Every input block produces an output block.
            int ciphertextIndex = 0;
            for (int plaintextIndex = 0; plaintextIndex < plaintext.length; plaintextIndex++) {
                byte[] output = update(new byte[] {plaintext[plaintextIndex]});
                if ((plaintextIndex % blockSize) == blockSize - 1) {
                    // Cipher.update is expected to have output a new block
                    assertEquals(
                            "plaintext index: " + plaintextIndex,
                            subarray(
                                    expectedCiphertext,
                                    ciphertextIndex,
                                    ciphertextIndex + blockSize),
                            output);
                } else {
                    // Cipher.update is expected to have produced no output
                    assertEquals("plaintext index: " + plaintextIndex, null, output);
                }
                if (output != null) {
                    ciphertextIndex += output.length;
                }
            }

            byte[] actualFinalOutput = doFinal();
            byte[] expectedFinalOutput =
                    subarray(expectedCiphertext, ciphertextIndex, expectedCiphertext.length);
            assertEquals(expectedFinalOutput, actualFinalOutput);
        }
    }

    public void testKatDecryptOneByteAtATime() throws Exception {
        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        byte[] ciphertext = getKatCiphertext();
        int plaintextIndex = 0;
        int blockSize = getBlockSize();
        byte[] expectedPlaintext = getKatPlaintext();
        boolean paddingEnabled = isPaddingEnabled();
        if (isAuthenticatedCipher()) {
            // Authenticated cipher -- no output until doFinal where ciphertext is authenticated.
            for (int ciphertextIndex = 0; ciphertextIndex < ciphertext.length; ciphertextIndex++) {
                byte[] output = update(new byte[] {ciphertext[ciphertextIndex]});
                assertEquals("ciphertext index: " + ciphertextIndex,
                        0, (output != null) ? output.length : 0);
            }
            byte[] finalOutput = doFinal();
            assertEquals(expectedPlaintext, finalOutput);
        } else if (isStreamCipher()) {
            // Unauthenticated stream cipher -- one byte in, one byte out
            for (int ciphertextIndex = 0; ciphertextIndex < ciphertext.length; ciphertextIndex++) {
                byte[] output = update(new byte[] {ciphertext[ciphertextIndex]});
                assertEquals("ciphertext index: " + ciphertextIndex, 1, output.length);
                assertEquals("ciphertext index: " + ciphertextIndex,
                        expectedPlaintext[ciphertextIndex], output[0]);
            }
            byte[] finalOutput = doFinal();
            assertEquals(0, finalOutput.length);
        } else {
            // Unauthenticated block cipher -- operates in full blocks only

            // Assert that a block of output is produced once a full block of input is provided.
            // When padding is used, output is produced one input byte later: once the first byte of the
            // next input block is provided.
            for (int ciphertextIndex = 0; ciphertextIndex < ciphertext.length; ciphertextIndex++) {
                byte[] output = update(new byte[] {ciphertext[ciphertextIndex]});
                boolean outputExpected =
                        ((paddingEnabled)
                                && (ciphertextIndex > 0) && ((ciphertextIndex % blockSize) == 0))
                        || ((!paddingEnabled) && ((ciphertextIndex % blockSize) == blockSize - 1));

                if (outputExpected) {
                    assertEquals(
                            "ciphertext index: " + ciphertextIndex,
                            subarray(expectedPlaintext, plaintextIndex, plaintextIndex + blockSize),
                            output);
                } else {
                    assertEquals("ciphertext index: " + ciphertextIndex, null, output);
                }

                if (output != null) {
                    plaintextIndex += output.length;
                }
            }

            byte[] actualFinalOutput = doFinal();
            byte[] expectedFinalOutput =
                    subarray(expectedPlaintext, plaintextIndex, expectedPlaintext.length);
            assertEquals(expectedFinalOutput, actualFinalOutput);
        }
    }

    public void testUpdateAADNotSupported() throws Exception {
        if (isAuthenticatedCipher()) {
            // Not applicable to authenticated ciphers where updateAAD is supported.
            return;
        }

        createCipher();
        initKat(Cipher.ENCRYPT_MODE);
        assertUpdateAADNotSupported();

        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        assertUpdateAADNotSupported();
    }

    public void testUpdateAADSupported() throws Exception {
        if (!isAuthenticatedCipher()) {
            // Not applicable to unauthenticated ciphers where updateAAD is not supported.
            return;
        }

        createCipher();
        initKat(Cipher.ENCRYPT_MODE);
        assertUpdateAADSupported();

        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        assertUpdateAADSupported();
    }

    private void assertUpdateAADNotSupported() throws Exception {
        try {
            mCipher.updateAAD(new byte[getBlockSize()]);
            fail();
        } catch (UnsupportedOperationException expected) {
        } catch (IllegalStateException expected) {}

        try {
            mCipher.updateAAD(new byte[getBlockSize()], 0, getBlockSize());
            fail();
        } catch (UnsupportedOperationException expected) {
        } catch (IllegalStateException expected) {}

        try {
            mCipher.updateAAD(ByteBuffer.allocate(getBlockSize()));
            fail();
        } catch (UnsupportedOperationException expected) {
        } catch (IllegalStateException expected) {}
    }

    private void assertUpdateAADSupported() throws Exception {
        mCipher.updateAAD(new byte[getBlockSize()]);
        mCipher.updateAAD(new byte[getBlockSize()], 0, getBlockSize());
        mCipher.updateAAD(ByteBuffer.allocate(getBlockSize()));
    }

    // TODO: Add tests for WRAP and UNWRAP

    public void testUpdateAndDoFinalNotSupportedInWrapAndUnwrapModes() throws Exception {
        createCipher();
        assertUpdateAndDoFinalThrowIllegalStateExceprtion(
                Cipher.WRAP_MODE, getKey(), getKatAlgorithmParameterSpec());

        createCipher();
        assertUpdateAndDoFinalThrowIllegalStateExceprtion(
                Cipher.UNWRAP_MODE, getKey(), getKatAlgorithmParameterSpec());
    }

    private void assertUpdateAndDoFinalThrowIllegalStateExceprtion(
            int opmode, SecretKey key, AlgorithmParameterSpec paramSpec)
            throws Exception {
        try {
            init(opmode, key, paramSpec);
        } catch (UnsupportedOperationException e) {
            // Skip this test because wrap/unwrap is not supported by this Cipher
            return;
        }

        try {
            update(new byte[getBlockSize()]);
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            update(new byte[getBlockSize()], 0, getBlockSize());
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            update(new byte[getBlockSize()], 0, getBlockSize(), new byte[getBlockSize() * 2]);
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            update(new byte[getBlockSize()], 0, getBlockSize(), new byte[getBlockSize() * 2], 0);
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            update(ByteBuffer.allocate(getBlockSize()), ByteBuffer.allocate(getBlockSize() * 2));
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            doFinal();
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            doFinal(new byte[getBlockSize()]);
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            doFinal(new byte[getBlockSize()], 0, getBlockSize());
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            doFinal(new byte[getBlockSize() * 2], 0);
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            doFinal(new byte[getBlockSize()], 0, getBlockSize(), new byte[getBlockSize() * 2]);
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            doFinal(new byte[getBlockSize()], 0, getBlockSize(), new byte[getBlockSize() * 2], 0);
            fail();
        } catch (IllegalStateException expected) {}

        init(opmode, key, paramSpec);
        try {
            doFinal(ByteBuffer.allocate(getBlockSize()), ByteBuffer.allocate(getBlockSize() * 2));
            fail();
        } catch (IllegalStateException expected) {}
    }

    public void testGeneratedPadding() throws Exception {
        // Assert that the Cipher under test correctly handles plaintexts of various lengths.
        if (isStreamCipher()) {
            // Not applicable to stream ciphers
            return;
        }

        // Encryption of basePlaintext and additional data should result in baseCiphertext and some
        // data (some of which may be padding).
        int blockSize = getBlockSize();
        byte[] basePlaintext = subarray(getKatPlaintext(), 0, blockSize);
        byte[] baseCiphertext = subarray(getKatCiphertext(), 0, blockSize);
        boolean paddingEnabled = isPaddingEnabled();

        for (int lastInputBlockUnusedByteCount = 0;
                lastInputBlockUnusedByteCount < blockSize;
                lastInputBlockUnusedByteCount++) {
            byte[] plaintext = concat(basePlaintext, new byte[lastInputBlockUnusedByteCount]);
            createCipher();
            initKat(Cipher.ENCRYPT_MODE);

            if ((!paddingEnabled) && ((lastInputBlockUnusedByteCount % blockSize) != 0)) {
                // Without padding, plaintext which does not end with a full block should be
                // rejected.
                try {
                    doFinal(plaintext);
                    fail();
                } catch (IllegalBlockSizeException expected) {}
                continue;
            }
            byte[] ciphertext = doFinal(plaintext);

            assertEquals(
                    "lastInputBlockUnusedByteCount: " + lastInputBlockUnusedByteCount,
                    baseCiphertext,
                    subarray(ciphertext, 0, baseCiphertext.length));

            int expectedCiphertextLength = getExpectedCiphertextLength(plaintext.length);
            int expectedDecryptedPlaintextLength =
                    (paddingEnabled) ? plaintext.length : expectedCiphertextLength;
            assertEquals(
                    "lastInputBlockUnusedByteCount: " + lastInputBlockUnusedByteCount,
                    expectedCiphertextLength,
                    ciphertext.length);
            initKat(Cipher.DECRYPT_MODE);
            byte[] decryptedPlaintext = doFinal(ciphertext);
            assertEquals(
                    "lastInputBlockUnusedByteCount: " + lastInputBlockUnusedByteCount,
                    expectedDecryptedPlaintextLength,
                    decryptedPlaintext.length);
            assertEquals(
                    "lastInputBlockUnusedByteCount: " + lastInputBlockUnusedByteCount,
                    basePlaintext,
                    subarray(decryptedPlaintext, 0, basePlaintext.length));
        }
    }

    public void testDecryptWithMangledPadding() throws Exception {
        if (!isPaddingEnabled()) {
            // Test not applicable when padding not in use
            return;
        }

        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        byte[] ciphertext = getKatCiphertext();
        // Flip a bit in the last byte of ciphertext -- this should result in the last plaintext
        // block getting mangled. In turn, this should result in bad padding.
        ciphertext[ciphertext.length - 1] ^= 1;
        try {
            doFinal(ciphertext);
            fail();
        } catch (BadPaddingException expected) {}
    }

    public void testDecryptWithMissingPadding() throws Exception {
        if (!isPaddingEnabled()) {
            // Test not applicable when padding not in use
            return;
        }

        createCipher();
        initKat(Cipher.DECRYPT_MODE);
        byte[] ciphertext = subarray(getKatCiphertext(), 0, getBlockSize());
        try {
            doFinal(ciphertext);
            fail();
        } catch (BadPaddingException expected) {}
    }

    public void testUpdateCopySafe() throws Exception {
        // Assert that when input and output buffers passed to Cipher.update reference the same
        // byte array, then no input data is overwritten before it's consumed.
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, 0, 0);
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, 0, 1);
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, 1, 0);
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, 0, getBlockSize() - 1);
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, 0, getBlockSize());
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, 0, getBlockSize() + 1);
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, getBlockSize() * 2 - 1, 0);
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, getBlockSize() * 2, 0);
        assertUpdateCopySafe(Cipher.ENCRYPT_MODE, getBlockSize() * 2 + 1, 0);

        assertUpdateCopySafe(Cipher.DECRYPT_MODE, 0, 0);
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, 0, 1);
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, 1, 0);
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, 0, getBlockSize() - 1);
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, 0, getBlockSize());
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, 0, getBlockSize() + 1);
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, getBlockSize() * 2 - 1, 0);
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, getBlockSize() * 2, 0);
        assertUpdateCopySafe(Cipher.DECRYPT_MODE, getBlockSize() * 2 + 1, 0);
    }

    private void assertUpdateCopySafe(
            int opmode, int inputOffsetInBuffer, int outputOffsetInBuffer)
            throws Exception {
        int blockSize = getBlockSize();
        byte[] input;
        byte[] expectedOutput;
        switch (opmode) {
            case Cipher.ENCRYPT_MODE:
                input = getKatPlaintext();
                if (isStreamCipher()) {
                    if (isAuthenticatedCipher()) {
                        expectedOutput = subarray(getKatCiphertext(), 0, input.length);
                    } else {
                        expectedOutput = getKatCiphertext();
                    }
                } else {
                    // Update outputs exactly one block of ciphertext for one block of plaintext,
                    // excluding padding.
                    expectedOutput = subarray(
                            getKatCiphertext(), 0, (input.length / blockSize) * blockSize);
                }
                break;
            case Cipher.DECRYPT_MODE:
                input = getKatCiphertext();
                if (isAuthenticatedCipher()) {
                    expectedOutput = EmptyArray.BYTE;
                } else if (isStreamCipher()) {
                    expectedOutput = getKatPlaintext();
                } else {
                    expectedOutput = getKatPlaintext();
                    if (isPaddingEnabled()) {
                        // When padding is enabled, update will not output the last block of
                        // plaintext because it doesn't know whether more ciphertext will be
                        // provided.
                        expectedOutput = subarray(
                                expectedOutput, 0, ((input.length / blockSize) - 1) * blockSize);
                    } else {
                        // When no padding is used, one block of ciphertext results in one block of
                        // plaintext.
                        expectedOutput = subarray(
                                expectedOutput, 0, (input.length - (input.length % blockSize)));
                    }
                }
                break;
            default:
                throw new AssertionFailedError("Unsupported opmode: " + opmode);
        }

        int inputEndIndexInBuffer = inputOffsetInBuffer + input.length;
        int outputEndIndexInBuffer = outputOffsetInBuffer + expectedOutput.length;

        // Test the update(byte[], int, int, byte[], int) variant
        byte[] buffer = new byte[Math.max(inputEndIndexInBuffer, outputEndIndexInBuffer)];
        System.arraycopy(input, 0, buffer, inputOffsetInBuffer, input.length);
        createCipher();
        initKat(opmode);
        assertEquals(expectedOutput.length,
                update(buffer, inputOffsetInBuffer, input.length,
                        buffer, outputOffsetInBuffer));
        assertEquals(expectedOutput,
                subarray(buffer, outputOffsetInBuffer, outputEndIndexInBuffer));

        if (outputOffsetInBuffer == 0) {
            // We can use the update variant which assumes that output offset is 0.
            buffer = new byte[Math.max(inputEndIndexInBuffer, outputEndIndexInBuffer)];
            System.arraycopy(input, 0, buffer, inputOffsetInBuffer, input.length);
            createCipher();
            initKat(opmode);
            assertEquals(expectedOutput.length,
                    update(buffer, inputOffsetInBuffer, input.length, buffer));
            assertEquals(expectedOutput,
                    subarray(buffer, outputOffsetInBuffer, outputEndIndexInBuffer));
        }

        // Test the update(ByteBuffer, ByteBuffer) variant
        buffer = new byte[Math.max(inputEndIndexInBuffer, outputEndIndexInBuffer)];
        System.arraycopy(input, 0, buffer, inputOffsetInBuffer, input.length);
        ByteBuffer inputBuffer = ByteBuffer.wrap(buffer, inputOffsetInBuffer, input.length);
        ByteBuffer outputBuffer =
                ByteBuffer.wrap(buffer, outputOffsetInBuffer, expectedOutput.length);
        createCipher();
        initKat(opmode);
        assertEquals(expectedOutput.length, update(inputBuffer, outputBuffer));
        assertEquals(expectedOutput,
                subarray(buffer, outputOffsetInBuffer, outputEndIndexInBuffer));
    }

    public void testDoFinalCopySafe() throws Exception {
        // Assert that when input and output buffers passed to Cipher.doFinal reference the same
        // byte array, then no input data is overwritten before it's consumed.
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, 0, 0);
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, 0, 1);
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, 1, 0);
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, 0, getBlockSize() - 1);
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, 0, getBlockSize());
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, 0, getBlockSize() + 1);
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, getBlockSize() * 2 - 1, 0);
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, getBlockSize() * 2, 0);
        assertDoFinalCopySafe(Cipher.ENCRYPT_MODE, getBlockSize() * 2 + 1, 0);

        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, 0, 0);
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, 0, 1);
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, 1, 0);
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, 0, getBlockSize() - 1);
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, 0, getBlockSize());
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, 0, getBlockSize() + 1);
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, getBlockSize() * 2 - 1, 0);
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, getBlockSize() * 2, 0);
        assertDoFinalCopySafe(Cipher.DECRYPT_MODE, getBlockSize() * 2 + 1, 0);
    }

    private void assertDoFinalCopySafe(
            int opmode, int inputOffsetInBuffer, int outputOffsetInBuffer)
            throws Exception {
        byte[] input = getKatInput(opmode);
        byte[] expectedOutput = getKatOutput(opmode);

        int inputEndIndexInBuffer = inputOffsetInBuffer + input.length;
        int outputEndIndexInBuffer = outputOffsetInBuffer + expectedOutput.length;

        // Test the doFinal(byte[], int, int, byte[], int) variant
        byte[] buffer = new byte[Math.max(inputEndIndexInBuffer, outputEndIndexInBuffer)];
        System.arraycopy(input, 0, buffer, inputOffsetInBuffer, input.length);
        createCipher();
        initKat(opmode);
        assertEquals(expectedOutput.length,
                doFinal(buffer, inputOffsetInBuffer, input.length,
                        buffer, outputOffsetInBuffer));
        assertEquals(expectedOutput,
                subarray(buffer, outputOffsetInBuffer, outputEndIndexInBuffer));

        if (outputOffsetInBuffer == 0) {
            // We can use the doFinal variant which assumes that output offset is 0.
            buffer = new byte[Math.max(inputEndIndexInBuffer, outputEndIndexInBuffer)];
            System.arraycopy(input, 0, buffer, inputOffsetInBuffer, input.length);
            createCipher();
            initKat(opmode);
            assertEquals(expectedOutput.length,
                    doFinal(buffer, inputOffsetInBuffer, input.length, buffer));
            assertEquals(expectedOutput,
                    subarray(buffer, outputOffsetInBuffer, outputEndIndexInBuffer));
        }

        // Test the doFinal(ByteBuffer, ByteBuffer) variant
        buffer = new byte[Math.max(inputEndIndexInBuffer, outputEndIndexInBuffer)];
        System.arraycopy(input, 0, buffer, inputOffsetInBuffer, input.length);
        ByteBuffer inputBuffer = ByteBuffer.wrap(buffer, inputOffsetInBuffer, input.length);
        ByteBuffer outputBuffer =
                ByteBuffer.wrap(buffer, outputOffsetInBuffer, expectedOutput.length);
        createCipher();
        initKat(opmode);
        assertEquals(expectedOutput.length, doFinal(inputBuffer, outputBuffer));
        assertEquals(expectedOutput,
                subarray(buffer, outputOffsetInBuffer, outputEndIndexInBuffer));
    }

    protected void createCipher() throws NoSuchAlgorithmException,
            NoSuchPaddingException, NoSuchProviderException  {
        mCipher = Cipher.getInstance(getTransformation(), EXPECTED_PROVIDER_NAME);
    }

    private String getKeyAlgorithm() {
        String transformation = getTransformation();
        int delimiterIndex = transformation.indexOf('/');
        if (delimiterIndex == -1) {
            fail("Unexpected transformation: " + transformation);
        }
        return transformation.substring(0, delimiterIndex);
    }

    private String getBlockMode() {
        String transformation = getTransformation();
        int delimiterIndex = transformation.indexOf('/');
        if (delimiterIndex == -1) {
            fail("Unexpected transformation: " + transformation);
        }
        int nextDelimiterIndex = transformation.indexOf('/', delimiterIndex + 1);
        if (nextDelimiterIndex == -1) {
            fail("Unexpected transformation: " + transformation);
        }
        return transformation.substring(delimiterIndex + 1, nextDelimiterIndex);
    }

    private String getPadding() {
        String transformation = getTransformation();
        int delimiterIndex = transformation.indexOf('/');
        if (delimiterIndex == -1) {
            fail("Unexpected transformation: " + transformation);
        }
        int nextDelimiterIndex = transformation.indexOf('/', delimiterIndex + 1);
        if (nextDelimiterIndex == -1) {
            fail("Unexpected transformation: " + transformation);
        }
        return transformation.substring(nextDelimiterIndex + 1);
    }

    private SecretKey getKey() {
        return importKey(getKatKey());
    }

    protected SecretKey importKey(byte[] keyMaterial) {
        try {
            int keyId = mNextKeyId++;
            String keyAlias = "key" + keyId;
            mAndroidKeyStore.setEntry(
                    keyAlias,
                    new KeyStore.SecretKeyEntry(new SecretKeySpec(keyMaterial, getKeyAlgorithm())),
                    new KeyProtection.Builder(
                            KeyProperties.PURPOSE_ENCRYPT
                                    | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(getBlockMode())
                            .setEncryptionPaddings(getPadding())
                            .setRandomizedEncryptionRequired(false)
                            .build());
            return (SecretKey) mAndroidKeyStore.getKey(keyAlias, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to import key into AndroidKeyStore", e);
        }
    }

    private boolean isPaddingEnabled() {
        return !getTransformation().toLowerCase(Locale.US).endsWith("/nopadding");
    }

    private int getExpectedCiphertextLength(int plaintextLength) {
        int blockSize = getBlockSize();
        if (isStreamCipher()) {
            // Padding not supported for stream ciphers
            assertFalse(isPaddingEnabled());
            return plaintextLength;
        } else {
            if (isPaddingEnabled()) {
                return ((plaintextLength / blockSize) + 1) * blockSize;
            } else {
                return ((plaintextLength + blockSize - 1) / blockSize) * blockSize;
            }
        }
    }

    protected void initKat(int opmode)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        init(opmode, getKey(), getKatAlgorithmParameterSpec());
    }

    protected void init(int opmode, Key key, AlgorithmParameters spec)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        mCipher.init(opmode, key, spec);
        mOpmode = opmode;
    }

    protected void init(int opmode, Key key, AlgorithmParameters spec, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        mCipher.init(opmode, key, spec, random);
        mOpmode = opmode;
    }

    protected void init(int opmode, Key key, AlgorithmParameterSpec spec)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        mCipher.init(opmode, key, spec);
        mOpmode = opmode;
    }

    protected void init(int opmode, Key key, AlgorithmParameterSpec spec, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        mCipher.init(opmode, key, spec, random);
        mOpmode = opmode;
    }

    protected void init(int opmode, Key key) throws InvalidKeyException {
        mCipher.init(opmode, key);
        mOpmode = opmode;
    }

    protected void init(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        mCipher.init(opmode, key, random);
        mOpmode = opmode;
    }

    protected byte[] doFinal() throws IllegalBlockSizeException, BadPaddingException {
        return mCipher.doFinal();
    }

    protected byte[] doFinal(byte[] input) throws IllegalBlockSizeException, BadPaddingException {
        return mCipher.doFinal(input);
    }

    protected byte[] doFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        return mCipher.doFinal(input, inputOffset, inputLen);
    }

    protected int doFinal(byte[] input, int inputOffset, int inputLen, byte[] output)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        return mCipher.doFinal(input, inputOffset, inputLen, output);
    }

    protected int doFinal(byte[] input, int inputOffset, int inputLen, byte[] output,
            int outputOffset) throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        return mCipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
    }

    protected int doFinal(byte[] output, int outputOffset) throws IllegalBlockSizeException,
            ShortBufferException, BadPaddingException {
        return mCipher.doFinal(output, outputOffset);
    }

    protected int doFinal(ByteBuffer input, ByteBuffer output) throws ShortBufferException,
            IllegalBlockSizeException, BadPaddingException {
        return mCipher.doFinal(input, output);
    }

    private boolean isEncrypting() {
        return (mOpmode == Cipher.ENCRYPT_MODE) || (mOpmode == Cipher.WRAP_MODE);
    }

    private void assertUpdateOutputSize(int inputLength, int outputLength) {
        if ((isAuthenticatedCipher()) && (!isEncrypting())) {
            assertEquals("Output of update must be empty for authenticated cipher when decrypting",
                    0, outputLength);
            return;
        }

        if (isStreamCipher()) {
            if (outputLength != inputLength) {
                fail("Output of update (" + outputLength + ") not same size as input ("
                        + inputLength + ")");
            }
        } else {
            if ((outputLength % getBlockSize()) != 0) {
                fail("Output of update (" + outputLength + ") not a multiple of block size ("
                        + getBlockSize() + ")");
            }
        }
    }

    protected byte[] update(byte[] input) {
        byte[] output = mCipher.update(input);
        assertUpdateOutputSize(
                (input != null) ? input.length : 0, (output != null) ? output.length : 0);
        return output;
    }

    protected byte[] update(byte[] input, int offset, int len) {
        byte[] output = mCipher.update(input, offset, len);
        assertUpdateOutputSize(len, (output != null) ? output.length : 0);

        return output;
    }

    protected int update(byte[] input, int offset, int len, byte[] output)
            throws ShortBufferException {
        int outputLen = mCipher.update(input, offset, len, output);
        assertUpdateOutputSize(len, outputLen);

        return outputLen;
    }

    protected int update(byte[] input, int offset, int len, byte[] output, int outputOffset)
            throws ShortBufferException {
        int outputLen = mCipher.update(input, offset, len, output, outputOffset);
        assertUpdateOutputSize(len, outputLen);

        return outputLen;
    }

    protected int update(ByteBuffer input, ByteBuffer output) throws ShortBufferException {
        int inputLimitBefore = input.limit();
        int outputLimitBefore = output.limit();
        int inputLen = input.remaining();
        int outputPosBefore = output.position();

        int outputLen = mCipher.update(input, output);

        assertUpdateOutputSize(inputLen, outputLen);
        assertEquals(inputLimitBefore, input.limit());
        assertEquals(input.limit(), input.position());

        assertEquals(outputLimitBefore, output.limit());
        assertEquals(outputPosBefore + outputLen, output.position());

        return outputLen;
    }

    protected void updateAAD(byte[] input) {
        mCipher.updateAAD(input);
    }

    protected void updateAAD(byte[] input, int offset, int len) {
        mCipher.updateAAD(input, offset, len);
    }

    protected void updateAAD(ByteBuffer input) {
        mCipher.updateAAD(input);
    }

    @SuppressWarnings("unused")
    protected static void assertEquals(Buffer expected, Buffer actual) {
        throw new RuntimeException(
                "Comparing ByteBuffers using their .equals is probably not what you want"
                + " -- use assertByteBufferEquals instead.");
    }

    /**
     * Asserts that the position, limit, and capacity of the provided buffers are the same, and that
     * their contents (from position {@code 0} to capacity) are the same.
     */
    protected static void assertByteBufferEquals(ByteBuffer expected, ByteBuffer actual) {
        if (expected == null) {
            if (actual == null) {
                return;
            } else {
                fail("Expected: null, actual: " + bufferToString(actual));
            }
        } else {
            if (actual == null) {
                fail("Expected: " + bufferToString(expected) + ", actual: null");
            } else {
                if ((expected.capacity() != actual.capacity())
                        || (expected.position() != actual.position())
                        || (expected.limit() != actual.limit())
                        || (!equals(expected.array(), expected.arrayOffset(), expected.capacity(),
                                    actual.array(), actual.arrayOffset(), actual.capacity()))) {
                    fail("Expected: " + bufferToString(expected)
                            + ", actual: " + bufferToString(actual));
                }
            }
        }
    }

    private static String bufferToString(ByteBuffer buffer) {
        return "ByteBuffer[pos: " + buffer.position() + ", limit: " + buffer.limit()
                + ", capacity: " + buffer.capacity()
                + ", backing array: " + HexEncoding.encode(
                        buffer.array(), buffer.arrayOffset(), buffer.capacity()) + "]";
    }

    protected static boolean equals(byte[] arr1, int offset1, int len1, byte[] arr2, int offset2,
            int len2) {
        if (arr1 == null) {
            return (arr2 == null);
        } else if (arr2 == null) {
            return (arr1 == null);
        } else {
            if (len1 != len2) {
                return false;
            }
            for (int i = 0; i < len1; i++) {
                if (arr1[i + offset1] != arr2[i + offset2]) {
                    return false;
                }
            }
            return true;
        }
    }

    protected static byte[] subarray(byte[] array, int beginIndex, int endIndex) {
        byte[] result = new byte[endIndex - beginIndex];
        System.arraycopy(array, beginIndex, result, 0, result.length);
        return result;
    }

    protected static byte[] concat(byte[]... arrays) {
        int resultLength = 0;
        for (byte[] array : arrays) {
            resultLength += (array != null) ? array.length : 0;
        }

        byte[] result = new byte[resultLength];
        int resultOffset = 0;
        for (byte[] array : arrays) {
            if (array != null) {
                System.arraycopy(array, 0, result, resultOffset, array.length);
                resultOffset += array.length;
            }
        }
        return result;
    }

    protected static void assertEquals(byte[] expected, byte[] actual) {
        assertEquals(null, expected, actual);
    }

    protected static void assertEquals(String message, byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            StringBuilder detail = new StringBuilder();
            if (expected != null) {
                detail.append("Expected (" + expected.length + " bytes): <"
                        + HexEncoding.encode(expected) + ">");
            } else {
                detail.append("Expected: null");
            }
            if (actual != null) {
                detail.append(", actual (" + actual.length + " bytes): <"
                        + HexEncoding.encode(actual) + ">");
            } else {
                detail.append(", actual: null");
            }
            if (message != null) {
                fail(message + ": " + detail);
            } else {
                fail(detail.toString());
            }
        }
    }

    protected final void assertInitRejectsIvParameterSpec(byte[] iv) throws Exception {
        Key key = importKey(getKatKey());
        createCipher();
        IvParameterSpec spec = new IvParameterSpec(iv);
        try {
            init(Cipher.ENCRYPT_MODE, key, spec);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        try {
            init(Cipher.WRAP_MODE, key, spec);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        try {
            init(Cipher.DECRYPT_MODE, key, spec);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        try {
            init(Cipher.UNWRAP_MODE, key, spec);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        AlgorithmParameters param = AlgorithmParameters.getInstance("AES");
        param.init(new IvParameterSpec(iv));
        try {
            init(Cipher.ENCRYPT_MODE, key, param);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        try {
            init(Cipher.WRAP_MODE, key, param);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        try {
            init(Cipher.DECRYPT_MODE, key, param);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}

        try {
            init(Cipher.UNWRAP_MODE, key, param);
            fail();
        } catch (InvalidAlgorithmParameterException expected) {}
    }
}
