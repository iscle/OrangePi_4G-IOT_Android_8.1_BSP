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

public class AES256GCMNoPaddingCipherTest extends AESGCMNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "7972140d831eedac75d5ea515c9a4c3bb124499a90b5f317ac1a685e88fae395");
    private static final byte[] KAT_IV = HexEncoding.decode("a66c5252808d823dd4151fed");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "c2b9dabf3a55adaa94e8c0d1e77a84a3435aee23b2c3c4abb587b09a9c2afbf0");
    private static final byte[] KAT_CIPHERTEXT_WITHOUT_AAD = HexEncoding.decode(
            "a960619314657b2afb96b93bebb372bffd09e19d53e351f17d1ba2611f9dc33c9c92d563e8fd381254ac26"
            + "2aa2a4ea0d");
    private static final byte[] KAT_AAD = HexEncoding.decode(
            "3727229db7a3ccda7283f628fb8a3cdf093ea1f4e8bd1bc40a830fc6df6fb0e249845dd7d449b2bc3b5ba4"
            + "2258fb92c7");
    private static final byte[] KAT_CIPHERTEXT_WITH_AAD = HexEncoding.decode(
            "a960619314657b2afb96b93bebb372bffd09e19d53e351f17d1ba2611f9dc33c1501caa6cca0a281f42bc3"
            + "10d1e4488f");

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
