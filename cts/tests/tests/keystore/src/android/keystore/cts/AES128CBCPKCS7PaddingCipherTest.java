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

public class AES128CBCPKCS7PaddingCipherTest extends AESCBCPKCS7PaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode("F16E698472578E919D92806262C5169F");
    private static final byte[] KAT_IV = HexEncoding.decode("EF743540F8421ACA128A3247521F3E7D");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "5BEBF33569D90BF5E853814E12E7C7AA5758013F755773E29F4A25EC26EEB765F7F2DC251F7DC62AEFCA1E"
            + "8A5A11A1DCD44F0BD8FB593A5AE3");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "3197CF6DB9466188B5FED375329324EE7D6092A8C0E41DFAF49E3724271427896D56A6243C0D59D6639722"
            + "AF93CD53449BDDABF9C5F153EBDBFED9ED98C8CC37");

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
