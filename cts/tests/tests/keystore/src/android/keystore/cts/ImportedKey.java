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

import java.security.Key;
import java.security.KeyPair;

import javax.crypto.SecretKey;

public class ImportedKey {
    private final boolean mSymmetric;
    private final String mAlias;
    private final KeyPair mOriginalKeyPair;
    private final KeyPair mKeystoreBackedKeyPair;
    private final SecretKey mOriginalSecretKey;
    private final SecretKey mKeystoreBackedSecretKey;

    public ImportedKey(String alias, KeyPair original, KeyPair keystoreBacked) {
        mAlias = alias;
        mSymmetric = false;
        mOriginalKeyPair = original;
        mKeystoreBackedKeyPair = keystoreBacked;
        mOriginalSecretKey = null;
        mKeystoreBackedSecretKey = null;
    }

    public ImportedKey(String alias, SecretKey original, SecretKey keystoreBacked) {
        mAlias = alias;
        mSymmetric = true;
        mOriginalKeyPair = null;
        mKeystoreBackedKeyPair = null;
        mOriginalSecretKey = original;
        mKeystoreBackedSecretKey = keystoreBacked;
    }

    public String getAlias() {
        return mAlias;
    }

    public Key getOriginalEncryptionKey() {
        if (mSymmetric) {
            return mOriginalSecretKey;
        } else {
            return mOriginalKeyPair.getPublic();
        }
    }

    public Key getOriginalDecryptionKey() {
        if (mSymmetric) {
            return mOriginalSecretKey;
        } else {
            return mOriginalKeyPair.getPrivate();
        }
    }

    public Key getOriginalSigningKey() {
        if (mSymmetric) {
            return mOriginalSecretKey;
        } else {
            return mOriginalKeyPair.getPrivate();
        }
    }

    public Key getOriginalVerificationKey() {
        if (mSymmetric) {
            return mOriginalSecretKey;
        } else {
            return mOriginalKeyPair.getPublic();
        }
    }

    public Key getKeystoreBackedEncryptionKey() {
        if (mSymmetric) {
            return mKeystoreBackedSecretKey;
        } else {
            return mKeystoreBackedKeyPair.getPublic();
        }
    }

    public Key getKeystoreBackedDecryptionKey() {
        if (mSymmetric) {
            return mKeystoreBackedSecretKey;
        } else {
            return mKeystoreBackedKeyPair.getPrivate();
        }
    }

    public Key getKeystoreBackedSigningKey() {
        if (mSymmetric) {
            return mKeystoreBackedSecretKey;
        } else {
            return mKeystoreBackedKeyPair.getPrivate();
        }
    }

    public Key getKeystoreBackedVerificationKey() {
        if (mSymmetric) {
            return mKeystoreBackedSecretKey;
        } else {
            return mKeystoreBackedKeyPair.getPublic();
        }
    }


    public KeyPair getOriginalKeyPair() {
        checkIsKeyPair();
        return mOriginalKeyPair;
    }

    public KeyPair getKeystoreBackedKeyPair() {
        checkIsKeyPair();
        return mKeystoreBackedKeyPair;
    }

    public SecretKey getOriginalSecretKey() {
        checkIsSecretKey();
        return mOriginalSecretKey;
    }

    public SecretKey getKeystoreBackedSecretKey() {
        checkIsSecretKey();
        return mKeystoreBackedSecretKey;
    }

    private void checkIsKeyPair() {
        if (mSymmetric) {
            throw new IllegalStateException("Not a KeyPair");
        }
    }

    private void checkIsSecretKey() {
        if (!mSymmetric) {
            throw new IllegalStateException("Not a SecretKey");
        }
    }
}
