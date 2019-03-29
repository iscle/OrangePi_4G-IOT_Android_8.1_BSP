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

public class AES256CTRNoPaddingCipherTest extends AESCTRNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "928b380a8fed4b4b4cfeb56e0c66a4cb0f9ff58d61ac68bcfd0e3fbd910a684f");
    private static final byte[] KAT_IV = HexEncoding.decode("0b678a5249e6eeda461dfb4776b6c58e");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "f358de57543b297e997cba46fb9100553d6abd65377e55b9aac3006400ead11f6db3c884");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "a07a35fbd1776ad81462e1935f542337add60962bf289249476817b6ddd532a7be30d4c3");

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
