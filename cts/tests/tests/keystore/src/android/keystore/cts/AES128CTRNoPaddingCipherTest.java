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

public class AES128CTRNoPaddingCipherTest extends AESCTRNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode("4713a7b2f93efe809b42ecc45213ef9f");
    private static final byte[] KAT_IV = HexEncoding.decode("ebfa19b0ebf3d57feabd4c4bd04bea01");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "6d2c07e1fc86f99c6e2a8f6567828b4262a9c23d0f3ed8ab32482283c79796f0adba1bcd3736084996452a"
            +"917fae98005aebe61f9e91c3");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "345deb1d67b95e600e05cad4c32ec381aadb3e2c1ec7e0fb956dc38e6860cf0553535566e1b12fa9f87d29"
            + "266ca26df427233df035df28");

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
