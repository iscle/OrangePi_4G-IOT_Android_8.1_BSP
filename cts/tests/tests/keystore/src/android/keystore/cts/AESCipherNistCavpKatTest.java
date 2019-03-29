/*
 * Copyright 2015 The Android Open Source Project
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESCipherNistCavpKatTest extends AndroidTestCase {

    public void testECBVarKey128() throws Exception {
        runTestsForKatFile("ECBVarKey128.rsp");
    }

    public void testECBVarKey192() throws Exception {
        runTestsForKatFile("ECBVarKey192.rsp");
    }
    public void testECBVarKey256() throws Exception {
        runTestsForKatFile("ECBVarKey256.rsp");
    }

    public void testECBVarTxt128() throws Exception {
        runTestsForKatFile("ECBVarTxt128.rsp");
    }

    public void testECBVarTxt192() throws Exception {
        runTestsForKatFile("ECBVarTxt192.rsp");
    }

    public void testECBVarTxt256() throws Exception {
        runTestsForKatFile("ECBVarTxt256.rsp");
    }

    public void testECBGFSbox128() throws Exception {
        runTestsForKatFile("ECBGFSbox128.rsp");
    }

    public void testECBGFSbox192() throws Exception {
        runTestsForKatFile("ECBGFSbox192.rsp");
    }

    public void testECBGFSbox256() throws Exception {
        runTestsForKatFile("ECBGFSbox256.rsp");
    }

    public void testECBKeySbox128() throws Exception {
        runTestsForKatFile("ECBKeySbox128.rsp");
    }

    public void testECBKeySbox192() throws Exception {
        runTestsForKatFile("ECBKeySbox192.rsp");
    }

    public void testECBKeySbox256() throws Exception {
        runTestsForKatFile("ECBKeySbox256.rsp");
    }

    public void testCBCVarKey128() throws Exception {
        runTestsForKatFile("CBCVarKey128.rsp");
    }

    public void testCBCVarKey192() throws Exception {
        runTestsForKatFile("CBCVarKey192.rsp");
    }
    public void testCBCVarKey256() throws Exception {
        runTestsForKatFile("CBCVarKey256.rsp");
    }

    public void testCBCVarTxt128() throws Exception {
        runTestsForKatFile("CBCVarTxt128.rsp");
    }

    public void testCBCVarTxt192() throws Exception {
        runTestsForKatFile("CBCVarTxt192.rsp");
    }

    public void testCBCVarTxt256() throws Exception {
        runTestsForKatFile("CBCVarTxt256.rsp");
    }

    public void testCBCGFSbox128() throws Exception {
        runTestsForKatFile("CBCGFSbox128.rsp");
    }

    public void testCBCGFSbox192() throws Exception {
        runTestsForKatFile("CBCGFSbox192.rsp");
    }

    public void testCBCGFSbox256() throws Exception {
        runTestsForKatFile("CBCGFSbox256.rsp");
    }

    public void testCBCKeySbox128() throws Exception {
        runTestsForKatFile("CBCKeySbox128.rsp");
    }

    public void testCBCKeySbox192() throws Exception {
        runTestsForKatFile("CBCKeySbox192.rsp");
    }

    public void testCBCKeySbox256() throws Exception {
        runTestsForKatFile("CBCKeySbox256.rsp");
    }

    private void runTestsForKatFile(String fileName) throws Exception {
        try (ZipInputStream zipIn = new ZipInputStream(
                getContext().getResources().getAssets().open("nist_cavp_aes_kat.zip"))) {
            ZipEntry zipEntry;
            byte[] entryContents = null;
            while ((zipEntry = zipIn.getNextEntry()) != null) {
                String entryName = zipEntry.getName();

                // We have to read the contents of all entries because there's no way to skip an
                // entry without reading its contents.
                entryContents = new byte[(int) zipEntry.getSize()];
                readFully(zipIn, entryContents);

                if (fileName.equals(entryName)) {
                    break;
                }
            }

            if (entryContents == null) {
                fail(fileName + " not found");
                return;
            }

            String blockMode = fileName.substring(0, 3);
            if ("CFB".equals(blockMode)) {
                blockMode = fileName.substring(0, 4);
            }
            runTestsForKatFile(blockMode, entryContents);
        }
    }

    private void runTestsForKatFile(String blockMode, byte[] fileContents) throws Exception {
        BufferedReader in = null;
        int testNumber = 0;
        try {
            in = new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(fileContents), "ISO-8859-1"));
            String line;
            int lineNumber = 0;
            String section = null; // ENCRYPT or DECRYPT

            boolean insideTestDefinition = false;
            TestVector testVector = null;

            while ((line = in.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.startsWith("#")) {
                    // Ignore comment lines
                    continue;
                }

                if (!insideTestDefinition) {
                    // Outside of a test definition
                    if (line.length() == 0) {
                        // Ignore empty lines
                        continue;
                    }
                    if ((line.startsWith("[")) && (line.endsWith("]"))) {
                        section = line.substring(1, line.length() - 1);
                        if ((!"DECRYPT".equals(section)) && (!"ENCRYPT".equals(section))) {
                            throw new IOException(lineNumber + ": Unexpected section: " + section);
                        }
                        continue;
                    }

                    // Check whether this is a NAME = VALUE line
                    int delimiterIndex = line.indexOf('=');
                    if (delimiterIndex == -1) {
                        throw new IOException(lineNumber + ": Unexpected line outside of test"
                                + " definition: " + line);
                    }
                    String name = line.substring(0, delimiterIndex).trim();
                    String value = line.substring(delimiterIndex + 1).trim();

                    if ("COUNT".equals(name)) {
                        testNumber = Integer.parseInt(value);
                        insideTestDefinition = true;
                        testVector = new TestVector();
                    } else {
                        throw new IOException(lineNumber + ": Unexpected line outside of test"
                                + " definition: " + line);
                    }
                } else {
                    // Inside of a test definition
                    if (line.length() == 0) {
                        // End of test definition
                        boolean encrypt;
                        if ("ENCRYPT".equals(section)) {
                            encrypt = true;
                        } else if ("DECRYPT".equals(section)) {
                            encrypt = false;
                        } else {
                            throw new IOException("Unexpected test operation: " + section);
                        }
                        runKatTest(blockMode, encrypt, testVector);
                        insideTestDefinition = false;
                        testVector = null;
                    } else {
                        // Check whether this is a NAME = VALUE line
                        int delimiterIndex = line.indexOf('=');
                        if (delimiterIndex == -1) {
                            throw new IOException(lineNumber + ": Unexpected line inside test"
                                    + " definition: " + line);
                        }
                        String name = line.substring(0, delimiterIndex).trim();
                        String value = line.substring(delimiterIndex + 1).trim();

                        if ("KEY".equals(name)) {
                            testVector.key = HexEncoding.decode(value);
                        } else if ("IV".equals(name)) {
                            testVector.iv = HexEncoding.decode(value);
                        } else if ("PLAINTEXT".equals(name)) {
                            testVector.plaintext = HexEncoding.decode(value);
                        } else if ("CIPHERTEXT".equals(name)) {
                            testVector.ciphertext = HexEncoding.decode(value);
                        } else {
                            throw new IOException(lineNumber + ": Unexpected line inside test"
                                    + " definition: " + line);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("Test #" + testNumber + " failed", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void runKatTest(String mode, boolean encrypt, TestVector testVector) throws Exception {
        String keyAlias = AESCipherNistCavpKatTest.class.getName();
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.setEntry(keyAlias,
                new KeyStore.SecretKeyEntry(new SecretKeySpec(testVector.key, "AES")),
                new KeyProtection.Builder(
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(mode)
                        .setEncryptionPaddings("NoPadding")
                        .setRandomizedEncryptionRequired(false)
                        .build());
        try {
            SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);
            assertNotNull(key);
            Cipher cipher = Cipher.getInstance("AES/" + mode + "/NoPadding");

            int opmode = (encrypt) ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
            if (testVector.iv != null) {
                cipher.init(opmode, key, new IvParameterSpec(testVector.iv));
            } else {
                cipher.init(opmode, key);
            }

            byte[] input = (encrypt) ? testVector.plaintext : testVector.ciphertext;
            byte[] actualOutput = cipher.doFinal(input);
            byte[] expectedOutput = (encrypt) ? testVector.ciphertext : testVector.plaintext;
            if (!Arrays.equals(expectedOutput, actualOutput)) {
                fail("Expected: " + HexEncoding.encode(expectedOutput)
                        + ", actual: " + HexEncoding.encode(actualOutput));
            }
        } finally {
            keyStore.deleteEntry(keyAlias);
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0;
        int remaining = buf.length;
        while (remaining > 0) {
            int chunkSize = in.read(buf, offset, remaining);
            if (chunkSize == -1) {
                throw new EOFException("Premature EOF. Remaining: " + remaining);
            }
            offset += chunkSize;
            remaining -= chunkSize;
        }
    }

    private static class TestVector {
        public byte[] key;
        public byte[] iv;
        public byte[] plaintext;
        public byte[] ciphertext;
    }
}
