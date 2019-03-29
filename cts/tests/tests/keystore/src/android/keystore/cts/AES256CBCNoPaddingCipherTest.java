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

public class AES256CBCNoPaddingCipherTest extends AESCBCNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "dd2f20dc6b98c100bac919120ff95eb5d96003f8229987b283a1e777b0cd5c30");
    private static final byte[] KAT_IV = HexEncoding.decode("23b4d85239fb90db93b07a981e90a170");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "2fbe5d46dca5cea433e550d8b291740ab9551c2a2d37680d7fb7b993225f58494cb53caca353e4b637ba05"
            + "687be20f8d");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "5aba24fc316936c8369061ee8fe463e4faed04288e204456626b988c0e376b6047da1e4fd7c4e1cf265609"
            + "7f75ae8685");

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
