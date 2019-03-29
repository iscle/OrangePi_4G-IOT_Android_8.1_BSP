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

public class AES128ECBNoPaddingCipherTest extends AESECBNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode("7DA2467F068854B3CB36E5C333A16619");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "9A07C9575AD9CE209DF9F3953965CEBE8208587C7AE575A1904BF25048946D7B6168A9A27BCE554BEA94EF"
            + "26E6C742A0");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "8C47E49420FC92AC4CA2C601BC3F8AC31D01B260B7B849F2B8EEDFFFED8F36C31CBDA0D22F95C9C2A48C34"
            + "7E8C77AC82");

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
