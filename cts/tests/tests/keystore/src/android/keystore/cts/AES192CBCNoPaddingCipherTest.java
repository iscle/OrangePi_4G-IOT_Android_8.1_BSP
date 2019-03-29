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

public class AES192CBCNoPaddingCipherTest extends AESCBCNoPaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "be8cc4e25cce46e5d55725e2391f7d3cf59ed60062f5a43b");
    private static final byte[] KAT_IV = HexEncoding.decode("80a199aab0eee77e7762ddf3b3a32f40");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "064f9200e0df37d4711af4a69d11addf9e1c345d9d8195f9f1f715019ce96a167f2497c994bd496eb80bfb"
            + "2ba2c9d5af");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "859b90becaa85e95a71e104efbd7a3b723bcbf4eb39865544a05d9e90b6fe572c134552f3a138e726fbe49"
            + "3b3a839598");

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
