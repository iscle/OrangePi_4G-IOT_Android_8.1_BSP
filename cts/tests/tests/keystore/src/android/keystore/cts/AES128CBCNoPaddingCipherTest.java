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

public class AES128CBCNoPaddingCipherTest extends AESCBCNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode("7E3D723C09A9852B24F584F9D916F6A8");
    private static final byte[] KAT_IV = HexEncoding.decode("944AE274D983892EADE422274858A96A");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "044E15899A080AADEB6778F64323B64D2CBCBADB338DF93B9AC459D4F41029809FFF37081C22EF278F896A"
            + "B213A2A631");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "B419293FCBD686F2913D1CF947E510D42FAFEDE5593C98AFD6AEE272596A56FE42C22F2A5E3B6A02BA9D8D"
            + "0DE1E9A810");

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
