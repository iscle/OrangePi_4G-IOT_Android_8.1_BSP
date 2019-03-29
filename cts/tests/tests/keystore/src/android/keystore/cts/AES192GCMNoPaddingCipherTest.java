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

public class AES192GCMNoPaddingCipherTest extends AESGCMNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "21339fc1d011abca65d50ce2365230603fd47d07e8830f6e");
    private static final byte[] KAT_IV = HexEncoding.decode("d5fb1469a8d81dd75286a418");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "cf776dedf53a828d51a0073db3ef0dd1ee19e2e9e243ce97e95841bb9ad4e3ff52");
    private static final byte[] KAT_CIPHERTEXT_WITHOUT_AAD = HexEncoding.decode(
            "3a0d48278111d3296bc663df8a5dbeb2474ea47fd85b608f8d9375d9dcf7de1413ad70fb0e1970669095ad"
            + "77ebb5974ae8");
    private static final byte[] KAT_AAD = HexEncoding.decode(
            "04cdc1d840c17dcfccf78b3d792463740ce0bfdc167b98a632e144cafe9663");
    private static final byte[] KAT_CIPHERTEXT_WITH_AAD = HexEncoding.decode(
            "3a0d48278111d3296bc663df8a5dbeb2474ea47fd85b608f8d9375d9dcf7de141380718b9f6c023fb091c7"
            + "6891a91683e2");

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
        return KAT_CIPHERTEXT_WITHOUT_AAD.clone();
    }

    @Override
    protected byte[] getKatAad() {
        return KAT_AAD.clone();
    }

    @Override
    protected byte[] getKatCiphertextWhenKatAadPresent() {
        return KAT_CIPHERTEXT_WITH_AAD.clone();
    }
}
