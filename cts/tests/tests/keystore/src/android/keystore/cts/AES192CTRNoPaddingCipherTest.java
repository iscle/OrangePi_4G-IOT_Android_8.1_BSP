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

public class AES192CTRNoPaddingCipherTest extends AESCTRNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "5e2036e790d38815c90beb67a1c9e5aa0e167ef082927317");
    private static final byte[] KAT_IV = HexEncoding.decode("df0694959b89054156962d68a226965c");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "6ed2781c99e03e45314d6019932220c2c98130c53f9f67ad10ac519adf50e928091e09cdbbd3b42b");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "e427b6666502e05b82d0b20ae50e862b1936d71266fc49178ac984e71571f22ae0f90f0c19f42b4a");

    @Override
    protected byte[] getKatKey() {
        return KAT_KEY.clone();
    }

    @Override
    protected byte[] getKatIv() {
        return KAT_IV.clone();
    }

    @Override
    protected byte[] getKatPlaintext() {
        return KAT_PLAINTEXT.clone();
    }

    @Override
    protected byte[] getKatCiphertext() {
        return KAT_CIPHERTEXT.clone();
    }
}
