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

public class AES128ECBPKCS7PaddingCipherTest extends AESECBPKCS7PaddingCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode("C3BE04BCCB3D99B85290F113FE7AF194");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "348C213FD8DF3F990C20C5ACBF07B34B6264AE245784A5A6176DBFB1C2E7DD27E52CC92B8EEE40614F05B5"
            + "07B355F6354A2705BD86");
    private static final byte[] KAT_CIPHERTEXT = HexEncoding.decode(
            "07CD05C41FEDEDDC5DB4B3E35E676153184A119AA4DFDDC290616F1FA600931DE6BEA9BDB90D1D73389994"
            + "6F8C8E5C0C4383F99F5D88E27F3EBC0C6E52759ED3");

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
