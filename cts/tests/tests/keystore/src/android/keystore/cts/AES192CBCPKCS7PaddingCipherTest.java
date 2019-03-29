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

public class AES192CBCPKCS7PaddingCipherTest extends AESCBCPKCS7PaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "68969215ec41e4df7d23de0e806f458f52aff492bd7c5263");
    private static final byte[] KAT_IV = HexEncoding.decode("e61d13dfbf0533289f0e7950209da418");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "8d4c1cac27511ee2d82409a7f378e7e402b0eb189c1eaa5c506eb72a9074b170");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "e70bcd62c595dc1b2b8c197bb91a7447e1be2cbcf3fdc69e7e991faf0f57cf4e3884138ff403a41fd99818"
            + "708ada301c");

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
