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

public class AES256ECBPKCS7PaddingCipherTest extends AESECBPKCS7PaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "bf3f07c68467fead0ca8e2754500ab514258abf02eb7e615a493bcaaa45d5ee1");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "af0757e49018dad628f16998628a407db5f28291bef3bc2e4d8a5a31fb238e6f");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "21ec3011074bf1ef140643d47130326c5e183f61237c69bc77551ca207d71fc2b90cfac6c8d2d125e5cd9f"
            + "f353dee0df");

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
