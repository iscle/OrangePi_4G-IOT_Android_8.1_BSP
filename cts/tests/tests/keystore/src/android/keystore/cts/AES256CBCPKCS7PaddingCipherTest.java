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

public class AES256CBCPKCS7PaddingCipherTest extends AESCBCPKCS7PaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "03ab2510520f5cfebfab0a17a7f8324c9634911f6fc59e586f85346bb38ac88a");
    private static final byte[] KAT_IV = HexEncoding.decode("9af96967195bb0184f129beffa8241ae");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "2d6944653ac14988a772a2730b7c5bfa99a21732ae26f40cdc5b3a2874c7942545a82b73c48078b9dae622"
            + "61c65909");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "26b308f7e1668b55705a79c8b3ad10e244655f705f027f390a5c34e4536f519403a71987b95124073d69f2"
            + "a3cb95b0ab");

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
