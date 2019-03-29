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

public class AES256ECBNoPaddingCipherTest extends AESECBNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "fa4622d9cf6485075daedd33d2c4fffdf859e2edb7f7df4f04603f7e647fae90");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "96ccabbe0c68970d8cdee2b30ab43c2d61cc50ee68271e77571e72478d713a31a476d6806b8116089c6ec5"
            + "0bb543200f");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "0e81839e9dfbfe3b503d619e676abe5ac80fac3f245d8f09b9134b1b32a67dc83e377faf246288931136be"
            + "f2a07c0be4");

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
