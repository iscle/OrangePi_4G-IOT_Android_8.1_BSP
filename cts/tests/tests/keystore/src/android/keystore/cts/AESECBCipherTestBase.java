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

import java.security.AlgorithmParameters;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

abstract class AESECBCipherTestBase extends BlockCipherTestBase {

    @Override
    protected boolean isStreamCipher() {
        return false;
    }

    @Override
    protected boolean isAuthenticatedCipher() {
        return false;
    }

    @Override
    protected int getKatAuthenticationTagLengthBytes() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int getBlockSize() {
        return 16;
    }

    @Override
    protected AlgorithmParameterSpec getKatAlgorithmParameterSpec() {
        return null;
    }

    @Override
    protected byte[] getIv(AlgorithmParameters params) throws InvalidParameterSpecException {
        if (params != null) {
            fail("ECB does not use IV");
        }
        return null;
    }

    public void testInitRejectsIvParameterSpec() throws Exception {
        assertInitRejectsIvParameterSpec(new byte[getBlockSize()]);
    }
}
