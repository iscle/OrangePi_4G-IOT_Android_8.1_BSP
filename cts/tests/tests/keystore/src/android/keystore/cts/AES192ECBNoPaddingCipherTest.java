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

public class AES192ECBNoPaddingCipherTest extends AESECBNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "3cab83fb338ba985fbfe74c5e9d2e900adb570b1d67faf92");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "2cc64c335a13fb838f3c6aad0a6b47297ca90bb886ddb059200f0b41740c44ab");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "9c5c825328f5ee0aa24947e374d3f9165f484b39dd808c790d7a129648102453");

    @Override
    protected byte[] getKatKey() {
        return KAT_KEY.clone();
    }

    @Override
    protected byte[] getKatIv() {
        return null;
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
