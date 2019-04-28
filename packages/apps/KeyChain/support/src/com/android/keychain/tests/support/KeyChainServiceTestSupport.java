/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.keychain.tests.support;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.security.KeyChain;
import android.security.KeyStore;
import android.util.Log;

public class KeyChainServiceTestSupport extends Service {
    private static final String TAG = "KeyChainServiceTest";

    private final KeyStore mKeyStore = KeyStore.getInstance();

    private final IKeyChainServiceTestSupport.Stub mIKeyChainServiceTestSupport
            = new IKeyChainServiceTestSupport.Stub() {
        @Override public boolean keystoreReset() {
            Log.d(TAG, "keystoreReset");
            return mKeyStore.reset();
        }
        @Override public boolean keystoreSetPassword(String password) {
            Log.d(TAG, "keystoreSetPassword");
            return mKeyStore.onUserPasswordChanged(password);
        }
        @Override public boolean keystorePut(String key, byte[] value) {
            Log.d(TAG, "keystorePut");
            return mKeyStore.put(key, value, KeyStore.UID_SELF, KeyStore.FLAG_ENCRYPTED);
        }
        @Override public boolean keystoreImportKey(String key, byte[] value) {
            Log.d(TAG, "keystoreImport");
            return mKeyStore.importKey(key, value, KeyStore.UID_SELF, KeyStore.FLAG_ENCRYPTED);
        }

        @Override public void revokeAppPermission(final int uid, final String alias)
                throws RemoteException {
            Log.d(TAG, "revokeAppPermission");
            blockingSetGrantPermission(uid, alias, false);
        }

        @Override public void grantAppPermission(final int uid, final String alias)
                throws RemoteException {
            Log.d(TAG, "grantAppPermission");
            blockingSetGrantPermission(uid, alias, true);
        }

        /**
         * Binds to the KeyChainService and requests that permission for the sender to
         * access the specified alias is granted/revoked.
         * This method blocks so it must not be called from the UI thread.
         * @param senderUid
         * @param alias
         */
        private void blockingSetGrantPermission(int senderUid, String alias, boolean value)
                throws RemoteException {
            KeyChain.KeyChainConnection connection = null;
            try {
                connection = KeyChain.bind(KeyChainServiceTestSupport.this);
                connection.getService().setGrant(senderUid, alias, value);
            } catch (InterruptedException e) {
                // should never happen. if it does we will not grant the requested permission
                Log.e(TAG, "interrupted while granting access");
                Thread.currentThread().interrupt();
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }
    };

    @Override public IBinder onBind(Intent intent) {
        if (IKeyChainServiceTestSupport.class.getName().equals(intent.getAction())) {
            return mIKeyChainServiceTestSupport;
        }
        return null;
    }
}
