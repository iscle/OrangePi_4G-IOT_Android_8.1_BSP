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

public class AES192ECBPKCS7PaddingCipherTest extends AESECBPKCS7PaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "d57f4e5446f736c16476ec4db5decc7b1bf3936e4f7e4618");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "b115777f1ee7a43a07daa6401e59c46b7a98213a8747eabfbe3ca4ec93524de2c7");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "1e92cd20da08bb5fa174a7a69879d4fc25a155e6af06d75b26c5b450d273c8bb7e3a889dd4a9589098b44a"
            + "cf1056e7aa");

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
