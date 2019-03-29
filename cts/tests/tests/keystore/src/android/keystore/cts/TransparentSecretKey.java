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

import javax.crypto.SecretKey;

/**
 * {@link SecretKey} which exposes its key material. The two reasons for the existence of this class
 * are: (1) to help test that classes under test don't assume that all transparent secret keys are
 * instances of {@link SecretKeySpec}, and (2) because {@code SecretKeySpec} rejects zero-length
 * key material which is needed in some tests.
 */
public class TransparentSecretKey implements SecretKey {
    private final String mAlgorithm;
    private final byte[] mKeyMaterial;

    public TransparentSecretKey(byte[] keyMaterial, String algorithm) {
        mAlgorithm = algorithm;
        mKeyMaterial = keyMaterial.clone();
    }

    @Override
    public String getAlgorithm() {
        return mAlgorithm;
    }

    @Override
    public byte[] getEncoded() {
        return mKeyMaterial;
    }

    @Override
    public String getFormat() {
        return "RAW";
    }
}
