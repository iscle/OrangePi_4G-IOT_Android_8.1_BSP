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

package com.android.keychain.tests;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.security.Credentials;
import android.security.IKeyChainService;
import android.security.KeyStore;
import android.util.Log;
import com.android.keychain.tests.support.IKeyChainServiceTestSupport;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.Certificate;
import java.util.Arrays;
import junit.framework.Assert;
import libcore.java.security.TestKeyStore;

public class KeyChainServiceTest extends Service {

    private static final String TAG = "KeyChainServiceTest";

    private final Object mSupportLock = new Object();
    private IKeyChainServiceTestSupport mSupport;
    private boolean mIsBoundSupport;

    private final Object mServiceLock = new Object();
    private IKeyChainService mService;
    private boolean mIsBoundService;

    private ServiceConnection mSupportConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mSupportLock) {
                mSupport = IKeyChainServiceTestSupport.Stub.asInterface(service);
                mSupportLock.notifyAll();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (mSupportLock) {
                mSupport = null;
            }
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mServiceLock) {
                mService = IKeyChainService.Stub.asInterface(service);
                mServiceLock.notifyAll();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (mServiceLock) {
                mService = null;
            }
        }
    };

    private void bindSupport() {
        mIsBoundSupport = bindService(new Intent(IKeyChainServiceTestSupport.class.getName()),
                                      mSupportConnection,
                                      Context.BIND_AUTO_CREATE);
    }

    private void bindService() {
        mIsBoundService = bindService(new Intent(IKeyChainService.class.getName()),
                                      mServiceConnection,
                                      Context.BIND_AUTO_CREATE);
    }

    private void unbindServices() {
        if (mIsBoundSupport) {
            unbindService(mSupportConnection);
            mIsBoundSupport = false;
        }
        if (mIsBoundService) {
            unbindService(mServiceConnection);
            mIsBoundService = false;
        }
    }

    @Override public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        new Thread(new Test(), TAG).start();
        return START_STICKY;
    }

    @Override public void onDestroy () {
        Log.d(TAG, "onDestroy");
        unbindServices();
    }

    private final class Test extends Assert implements Runnable {

        @Override public void run() {
            try {
                test_KeyChainService();
            } catch (RuntimeException e) {
                // rethrow RuntimeException without wrapping
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                stopSelf();
            }
        }

        public void test_KeyChainService() throws Exception {
            Log.d(TAG, "test_KeyChainService uid=" + getApplicationInfo().uid);

            Log.d(TAG, "test_KeyChainService bind support");
            bindSupport();
            assertTrue(mIsBoundSupport);
            synchronized (mSupportLock) {
                if (mSupport == null) {
                    mSupportLock.wait(10 * 1000);
                }
            }
            assertNotNull(mSupport);

            Log.d(TAG, "test_KeyChainService setup keystore");
            KeyStore keyStore = KeyStore.getInstance();
            assertTrue(mSupport.keystoreReset());
            assertTrue(mSupport.keystoreSetPassword("newpasswd"));

            String intermediate = "-intermediate";
            String root = "-root";

            String alias1 = "client";
            String alias1Intermediate = alias1 + intermediate;
            String alias1Root = alias1 + root;
            String alias1Pkey = (Credentials.USER_PRIVATE_KEY + alias1);
            String alias1Cert = (Credentials.USER_CERTIFICATE + alias1);
            String alias1ICert = (Credentials.CA_CERTIFICATE + alias1Intermediate);
            String alias1RCert = (Credentials.CA_CERTIFICATE + alias1Root);
            PrivateKeyEntry pke1 = TestKeyStore.getClientCertificate().getPrivateKey("RSA", "RSA");
            Certificate intermediate1 = pke1.getCertificateChain()[1];
            Certificate root1 = TestKeyStore.getClientCertificate().getRootCertificate("RSA");

            final String alias2 = "server";
            String alias2Intermediate = alias2 + intermediate;
            String alias2Root = alias2 + root;
            String alias2Pkey = (Credentials.USER_PRIVATE_KEY + alias2);
            String alias2Cert = (Credentials.USER_CERTIFICATE + alias2);
            String alias2ICert = (Credentials.CA_CERTIFICATE + alias2Intermediate);
            String alias2RCert = (Credentials.CA_CERTIFICATE + alias2Root);
            PrivateKeyEntry pke2 = TestKeyStore.getServer().getPrivateKey("RSA", "RSA");
            Certificate intermediate2 = pke2.getCertificateChain()[1];
            Certificate root2 = TestKeyStore.getServer().getRootCertificate("RSA");

            assertTrue(mSupport.keystoreImportKey(alias1Pkey,
                                           pke1.getPrivateKey().getEncoded()));
            assertTrue(mSupport.keystorePut(alias1Cert,
                                            Credentials.convertToPem(pke1.getCertificate())));
            assertTrue(mSupport.keystorePut(alias1ICert,
                                            Credentials.convertToPem(intermediate1)));
            assertTrue(mSupport.keystorePut(alias1RCert,
                                            Credentials.convertToPem(root1)));
            assertTrue(mSupport.keystoreImportKey(alias2Pkey,
                                            pke2.getPrivateKey().getEncoded()));
            assertTrue(mSupport.keystorePut(alias2Cert,
                                            Credentials.convertToPem(pke2.getCertificate())));
            assertTrue(mSupport.keystorePut(alias2ICert,
                                            Credentials.convertToPem(intermediate2)));
            assertTrue(mSupport.keystorePut(alias2RCert,
                                            Credentials.convertToPem(root2)));

            assertEquals(KeyStore.State.UNLOCKED, keyStore.state());

            Log.d(TAG, "test_KeyChainService bind service");
            bindService();
            assertTrue(mIsBoundService);
            synchronized (mServiceLock) {
                if (mService == null) {
                    mServiceLock.wait(10 * 1000);
                }
            }
            assertNotNull(mService);

            mSupport.grantAppPermission(getApplicationInfo().uid, alias1);
            // don't grant alias2, so it can be done manually with KeyChainTestActivity
            Log.d(TAG, "test_KeyChainService positive testing");
            assertNotNull("Requesting private key should succeed",
                    mService.requestPrivateKey(alias1));

            byte[] certificate = mService.getCertificate(alias1);
            assertNotNull(certificate);
            assertEquals(Arrays.toString(Credentials.convertToPem(pke1.getCertificate())),
                         Arrays.toString(certificate));

            Log.d(TAG, "test_KeyChainService negative testing");
            mSupport.revokeAppPermission(getApplicationInfo().uid, alias2);
            try {
                mService.requestPrivateKey(alias2);
                fail();
            } catch (IllegalStateException expected) {
            }

            try {
                mService.getCertificate(alias2);
                fail();
            } catch (IllegalStateException expected) {
            }

            Log.d(TAG, "test_KeyChainService unbind");
            unbindServices();
            assertFalse(mIsBoundSupport);
            assertFalse(mIsBoundService);

            Log.d(TAG, "test_KeyChainService end");
        }
    }
}
