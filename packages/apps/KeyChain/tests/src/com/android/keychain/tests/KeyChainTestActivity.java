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

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import libcore.java.security.TestKeyStore;
import libcore.javax.net.ssl.TestSSLContext;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

/**
 * Simple activity based test that exercises the KeyChain API
 */
public class KeyChainTestActivity extends Activity {

    private static final String TAG = "KeyChainTestActivity";

    private static final int REQUEST_CA_INSTALL = 1;

    private TextView mTextView;

    private TestKeyStore mTestKeyStore;

    private final Object mAliasLock = new Object();
    private String mAlias;

    private final Object mGrantedLock = new Object();
    private boolean mGranted;

    private void log(final String message) {
        Log.d(TAG, message);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                mTextView.append(message + "\n");
            }
        });
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                   .detectDiskReads()
                                   .detectDiskWrites()
                                   .detectAll()
                                   .penaltyLog()
                                   .penaltyDeath()
                                   .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                               .detectLeakedSqlLiteObjects()
                               .detectLeakedClosableObjects()
                               .penaltyLog()
                               .penaltyDeath()
                               .build());

        mTextView = new TextView(this);
        mTextView.setMovementMethod(new ScrollingMovementMethod());
        setContentView(mTextView);

        log("Starting test...");
        testKeyChainImproperUse();

        new SetupTestKeyStore().execute();
    }

    private void testKeyChainImproperUse() {
        try {
            KeyChain.getPrivateKey(null, null);
            throw new AssertionError();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (KeyChainException e) {
            throw new AssertionError(e);
        } catch (NullPointerException expected) {
            log("KeyChain failed as expected with null argument.");
        }

        try {
            KeyChain.getPrivateKey(this, null);
            throw new AssertionError();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (KeyChainException e) {
            throw new AssertionError(e);
        } catch (NullPointerException expected) {
            log("KeyChain failed as expected with null argument.");
        }

        try {
            KeyChain.getPrivateKey(null, "");
            throw new AssertionError();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (KeyChainException e) {
            throw new AssertionError(e);
        } catch (NullPointerException expected) {
            log("KeyChain failed as expected with null argument.");
        }

        try {
            KeyChain.getPrivateKey(this, "");
            throw new AssertionError();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (KeyChainException e) {
            throw new AssertionError(e);
        } catch (IllegalStateException expected) {
            log("KeyChain failed as expected on main thread.");
        }
    }

    private class SetupTestKeyStore extends AsyncTask<Void, Void, Void> {
        @Override protected Void doInBackground(Void... params) {
            mTestKeyStore = TestKeyStore.getServer();
            return null;
        }
        @Override protected void onPostExecute(Void result) {
            testCaInstall();
        }
    }

    private void testCaInstall() {
        try {
            log("Requesting install of server's CA...");
            X509Certificate ca = mTestKeyStore.getRootCertificate("RSA");
            Intent intent = KeyChain.createInstallIntent();
            intent.putExtra(KeyChain.EXTRA_NAME, TAG);
            intent.putExtra(KeyChain.EXTRA_CERTIFICATE, ca.getEncoded());
            startActivityForResult(intent, REQUEST_CA_INSTALL);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

    }

    private class TestHttpsRequest extends AsyncTask<Void, Void, Void> {
        @Override protected Void doInBackground(Void... params) {
            try {
                log("Starting web server...");
                URL url = startWebServer();
                log("Making https request to " + url);
                makeHttpsRequest(url);
                log("Tests succeeded.");

                return null;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        private URL startWebServer() throws Exception {
            KeyStore serverKeyStore = mTestKeyStore.keyStore;
            char[] serverKeyStorePassword = mTestKeyStore.storePassword;
            String kmfAlgoritm = KeyManagerFactory.getDefaultAlgorithm();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmfAlgoritm);
            kmf.init(serverKeyStore, serverKeyStorePassword);
            SSLContext serverContext = SSLContext.getInstance("SSL");
            serverContext.init(kmf.getKeyManagers(),
                               new TrustManager[] { new TrustAllTrustManager() },
                               null);
            SSLSocketFactory sf = serverContext.getSocketFactory();
            SSLSocketFactory needClientAuth = TestSSLContext.clientAuth(sf, false, true);
            MockWebServer server = new MockWebServer();
            server.useHttps(needClientAuth, false);
            server.enqueue(new MockResponse().setBody("this response comes via HTTPS"));
            server.play();
            return server.getUrl("/");
        }
        private void makeHttpsRequest(URL url) throws Exception {
            SSLContext clientContext = SSLContext.getInstance("SSL");
            clientContext.init(new KeyManager[] { new KeyChainKeyManager() }, null, null);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(clientContext.getSocketFactory());
            if (connection.getResponseCode() != 200) {
                throw new AssertionError();
            }
        }
    }

    private class KeyChainKeyManager extends X509ExtendedKeyManager {
        @Override public String chooseClientAlias(String[] keyTypes,
                                                  Principal[] issuers,
                                                  Socket socket) {
            log("KeyChainKeyManager chooseClientAlias...");

            KeyChain.choosePrivateKeyAlias(KeyChainTestActivity.this, new AliasResponse(),
                                           keyTypes, issuers,
                                           socket.getInetAddress().getHostName(), socket.getPort(),
                                           "My Test Certificate");
            String alias;
            synchronized (mAliasLock) {
                while (mAlias == null) {
                    try {
                        mAliasLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                alias = mAlias;
            }
            return alias;
        }
        @Override public String chooseServerAlias(String keyType,
                                                  Principal[] issuers,
                                                  Socket socket) {
            // not a client SSLSocket callback
            throw new UnsupportedOperationException();
        }
        @Override public X509Certificate[] getCertificateChain(String alias) {
            try {
                log("KeyChainKeyManager getCertificateChain...");
                X509Certificate[] certificateChain
                        = KeyChain.getCertificateChain(KeyChainTestActivity.this, alias);
                if (certificateChain == null) {
                    log("Null certificate chain!");
                    return null;
                }
                for (int i = 0; i < certificateChain.length; i++) {
                    log("certificate[" + i + "]=" + certificateChain[i]);
                }
                return certificateChain;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (KeyChainException e) {
                throw new RuntimeException(e);
            }
        }
        @Override public String[] getClientAliases(String keyType, Principal[] issuers) {
            // not a client SSLSocket callback
            throw new UnsupportedOperationException();
        }
        @Override public String[] getServerAliases(String keyType, Principal[] issuers) {
            // not a client SSLSocket callback
            throw new UnsupportedOperationException();
        }
        @Override public PrivateKey getPrivateKey(String alias) {
            try {
                log("KeyChainKeyManager getPrivateKey...");
                PrivateKey privateKey = KeyChain.getPrivateKey(KeyChainTestActivity.this,
                                                                         alias);
                log("privateKey=" + privateKey);
                return privateKey;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (KeyChainException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class AliasResponse implements KeyChainAliasCallback {
        @Override public void alias(String alias) {
            if (alias == null) {
                log("AliasResponse empty!");
                log("Do you need to install some client certs with:");
                log("    adb shell am startservice -n "
                    + "com.android.keychain.tests/.KeyChainServiceTest");
                return;
            }
            log("Alias choosen '" + alias + "'");
            synchronized (mAliasLock) {
                mAlias = alias;
                mAliasLock.notifyAll();
            }
        }
    }

    private static class TrustAllTrustManager implements X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }
        @Override public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CA_INSTALL: {
                log("onActivityResult REQUEST_CA_INSTALL...");
                if (resultCode != RESULT_OK) {
                    log("REQUEST_CA_INSTALL failed!");
                    return;
                }
                new TestHttpsRequest().execute();
                break;
            }
            default:
                throw new IllegalStateException("requestCode == " + requestCode);
        }
    }
}
