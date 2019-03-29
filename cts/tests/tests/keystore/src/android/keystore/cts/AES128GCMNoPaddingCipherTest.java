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

public class AES128GCMNoPaddingCipherTest extends AESGCMNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode("ba76354f0aed6e8d91f45c4ff5a062db");
    private static final byte[] KAT_IV = HexEncoding.decode("b79437ae08ff355d7d8a4d0f");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "6d7596a8fd56ceaec61de7940984b7736fec44f572afc3c8952e4dc6541e2bc6a702c440a37610989543f6"
            + "3fedb047ca2173bc18581944");
    private static final byte[] KAT_CIPHERTEXT_WITHOUT_AAD = HexEncoding.decode(
            "b3f6799e8f9326f2df1e80fcd2cb16d78c9dc7cc14bb677862dc6c639b3a6338d24b312d3989e5920b5dbf"
            + "c976765efbfe57bb385940a7a43bdf05bddae3c9d6a2fbbdfcc0cba0");
    private static final byte[] KAT_AAD = HexEncoding.decode(
            "d3bc7458914f45d56d5fcfbb2eeff2dcc0e620c1229d90904e98930ea71aa43b6898f846f3244d");
    private static final byte[] KAT_CIPHERTEXT_WITH_AAD = HexEncoding.decode(
            "b3f6799e8f9326f2df1e80fcd2cb16d78c9dc7cc14bb677862dc6c639b3a6338d24b312d3989e5920b5dbf"
            + "c976765efbfe57bb385940a70c106264d81506f8daf9cd6a1c70988c");

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
