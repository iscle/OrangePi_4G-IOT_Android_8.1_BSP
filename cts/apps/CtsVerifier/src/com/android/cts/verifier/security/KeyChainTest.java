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

package com.android.cts.verifier.security;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

import libcore.java.security.TestKeyStore;
import libcore.javax.net.ssl.TestSSLContext;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Simple activity based test that exercises the KeyChain API
 */
public class KeyChainTest extends PassFailButtons.Activity implements View.OnClickListener {

    private static final String TAG = "KeyChainTest";

    private static final int REQUEST_KEY_INSTALL = 1;

    // Alias under which credentials are generated
    private static final String ALIAS = "alias";

    private static final String CREDENTIAL_NAME = TAG + " Keys";
    private static final String CACERT_NAME = TAG + " CA";

    private TextView mInstructionView;
    private TextView mLogView;
    private Button mResetButton;
    private Button mSkipButton;
    private Button mNextButton;

    private List<Step> mSteps;
    int mCurrentStep;

    private KeyStore mKeyStore;
    private TrustManagerFactory mTrustManagerFactory;
    private static final char[] EMPTY_PASSWORD = "".toCharArray();

    // How long to wait before giving up on the user selecting a key alias.
    private static final int KEYCHAIN_ALIAS_TIMEOUT_MS = (int) TimeUnit.MINUTES.toMillis(5L);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View root = getLayoutInflater().inflate(R.layout.keychain_main, null);
        setContentView(root);

        setInfoResources(R.string.keychain_test, R.string.keychain_info, -1);
        setPassFailButtonClickListeners();

        mInstructionView = (TextView) root.findViewById(R.id.test_instruction);
        mLogView = (TextView) root.findViewById(R.id.test_log);
        mLogView.setMovementMethod(new ScrollingMovementMethod());

        mNextButton = (Button) root.findViewById(R.id.action_next);
        mNextButton.setOnClickListener(this);

        mResetButton = (Button) root.findViewById(R.id.action_reset);
        mResetButton.setOnClickListener(this);

        mSkipButton = (Button) root.findViewById(R.id.action_skip);
        mSkipButton.setOnClickListener(this);

        resetProgress();
    }

    @Override
    public void onClick(View v) {
        Step step = mSteps.get(mCurrentStep);
        if (v == mNextButton) {
            switch (step.task.getStatus()) {
                case PENDING: {
                    step.task.execute();
                    break;
                }
                case FINISHED: {
                    if (mCurrentStep + 1 < mSteps.size()) {
                        mCurrentStep += 1;
                        updateUi();
                    } else {
                        mSkipButton.setVisibility(View.INVISIBLE);
                        mNextButton.setVisibility(View.INVISIBLE);
                    }
                    break;
                }
            }
        } else if (v == mSkipButton) {
            step.task.cancel(false);
            mCurrentStep += 1;
            updateUi();
        } else if (v == mResetButton) {
            resetProgress();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_KEY_INSTALL: {
                if (resultCode == RESULT_OK) {
                    log("Client keys installed successfully");
                } else {
                    log("REQUEST_KEY_INSTALL failed with result code: " + resultCode);
                }
                break;
            }
            default:
                throw new IllegalStateException("requestCode == " + requestCode);
        }
    }

    private void resetProgress() {
        getPassButton().setEnabled(false);
        mLogView.setText("");

        mSteps = new ArrayList<>();
        mSteps.add(new Step(R.string.keychain_setup_desc, false, new SetupTestKeyStoreTask()));
        mSteps.add(new Step(R.string.keychain_install_desc, true, new InstallCredentialsTask()));
        mSteps.add(new Step(R.string.keychain_https_desc, false, new TestHttpsRequestTask()));
        mSteps.add(new Step(R.string.keychain_reset_desc, true, new ClearCredentialsTask()));
        mCurrentStep = 0;

        updateUi();
    }

    private void updateUi() {
        mLogView.setText("");

        if (mCurrentStep >= mSteps.size()) {
            mSkipButton.setVisibility(View.INVISIBLE);
            mNextButton.setVisibility(View.INVISIBLE);
            getPassButton().setEnabled(true);
            return;
        }

        final Step step = mSteps.get(mCurrentStep);
        if (step.task.getStatus() == AsyncTask.Status.PENDING) {
            mInstructionView.setText(step.instructionTextId);
        }
        mSkipButton.setVisibility(step.skippable ? View.VISIBLE : View.INVISIBLE);
        mNextButton.setVisibility(View.VISIBLE);
    }

    private class SetupTestKeyStoreTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            final Certificate[] chain = new Certificate[2];
            final Key privKey;

            log("Reading resources");
            Resources res = getResources();
            ByteArrayOutputStream userKey = new ByteArrayOutputStream();
            try {
                InputStream is = res.openRawResource(R.raw.userkey);
                byte[] buffer = new byte[4096];
                for (int n; (n = is.read(buffer, 0, buffer.length)) != -1;) {
                    userKey.write(buffer, 0, n);
                }
            } catch (IOException e) {
                Log.e(TAG, "Reading private key failed", e);
                return null;
            }
            log("Private key length: " + userKey.size() + " bytes");

            log("Setting up KeyStore");
            try {
                KeyFactory keyFact = KeyFactory.getInstance("RSA");
                privKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(userKey.toByteArray()));

                final CertificateFactory f = CertificateFactory.getInstance("X.509");
                chain[0] = f.generateCertificate(res.openRawResource(R.raw.usercert));
                chain[1] = f.generateCertificate(res.openRawResource(R.raw.cacert));
            } catch (GeneralSecurityException gse) {
                Log.w(TAG, "Certificate generation failed", gse);
                return null;
            }

            try {
                // Create a PKCS12 keystore populated with key + certificate chain
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setKeyEntry(ALIAS, privKey, EMPTY_PASSWORD, chain);
                mKeyStore = ks;

                // Make a TrustManagerFactory backed by our new keystore.
                mTrustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                mTrustManagerFactory.init(mKeyStore);

                log("KeyStore initialized");
            } catch (Exception e) {
                log("KeyStore initialization failed");
                Log.e(TAG, "", e);
            }
            return null;
        }
    }

    private class InstallCredentialsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Intent intent = KeyChain.createInstallIntent();
                intent.putExtra(KeyChain.EXTRA_NAME, CREDENTIAL_NAME);

                // Write keystore to byte array for installation
                ByteArrayOutputStream pkcs12 = new ByteArrayOutputStream();
                mKeyStore.store(pkcs12, EMPTY_PASSWORD);
                if (pkcs12.size() == 0) {
                    log("ERROR: Credential archive is empty");
                    return null;
                }
                log("Requesting install of credentials");
                intent.putExtra(KeyChain.EXTRA_PKCS12, pkcs12.toByteArray());
                startActivityForResult(intent, REQUEST_KEY_INSTALL);
            } catch (Exception e) {
                log("Failed to install credentials: " + e);
            }
            return null;
        }
    }

    private class TestHttpsRequestTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                URL url = startWebServer();
                makeHttpsRequest(url);
            } catch (Exception e) {
                Log.e(TAG, "HTTPS request unsuccessful", e);
                log("Connection failed");
                return null;
            }

            runOnUiThread(new Runnable() {
                @Override public void run() {
                    getPassButton().setEnabled(true);
                }
            });
            return null;
        }

        /**
         * Create a mock web server.
         * The server authenticates itself to the client using the key pair and certificate from the
         * PKCS#12 keystore used in this test. Client authentication uses default trust management:
         * the server trusts only the certificates installed in the credential storage of this
         * user/profile.
         */
        private URL startWebServer() throws Exception {
            log("Starting web server");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(mKeyStore, EMPTY_PASSWORD);
            SSLContext serverContext = SSLContext.getInstance("TLS");
            serverContext.init(kmf.getKeyManagers(),
                    mTrustManagerFactory.getTrustManagers(),
                    null /* SecureRandom */);
            SSLSocketFactory sf = serverContext.getSocketFactory();
            SSLSocketFactory needsClientAuth = TestSSLContext.clientAuth(sf,
                    false /* Want client auth */,
                    true /* Need client auth */);
            MockWebServer server = new MockWebServer();
            server.useHttps(needsClientAuth, false /* tunnelProxy */);
            server.enqueue(new MockResponse().setBody("this response comes via HTTPS"));
            server.play();
            return server.getUrl("/");
        }

        /**
         * Open a new connection to the server.
         * The client authenticates itself to the server using a private key and certificate
         * supplied by KeyChain.
         * Server authentication only trusts the root certificate of the credentials generated
         * earlier during this test.
         */
        private void makeHttpsRequest(URL url) throws Exception {
            log("Making https request to " + url);
            SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(new KeyManager[] { new KeyChainKeyManager() },
                    mTrustManagerFactory.getTrustManagers(),
                    null /* SecureRandom */);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(clientContext.getSocketFactory());
            if (connection.getResponseCode() != 200) {
                log("Connection failed. Response code: " + connection.getResponseCode());
                throw new AssertionError();
            }
            log("Connection succeeded.");
        }
    }

    private class ClearCredentialsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            final Intent securitySettingsIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            startActivity(securitySettingsIntent);
            log("Started action: " + Settings.ACTION_SECURITY_SETTINGS);
            log("All tests complete!");
            return null;
        }
    }

    /**
     * Key manager which synchronously prompts for its aliases via KeyChain
     */
    private class KeyChainKeyManager extends X509ExtendedKeyManager {
        @Override
        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            log("KeyChainKeyManager chooseClientAlias");
            KeyChainAliasCallback aliasCallback = Mockito.mock(KeyChainAliasCallback.class);
            KeyChain.choosePrivateKeyAlias(KeyChainTest.this, aliasCallback,
                                           keyTypes, issuers,
                                           socket.getInetAddress().getHostName(), socket.getPort(),
                                           null);

            ArgumentCaptor<String> aliasCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(aliasCallback, Mockito.timeout((int) KEYCHAIN_ALIAS_TIMEOUT_MS))
                    .alias(aliasCaptor.capture());

            log("Certificate alias: \"" + aliasCaptor.getValue() + "\"");
            return aliasCaptor.getValue();
        }

        @Override
        public String chooseServerAlias(String keyType,
                                                  Principal[] issuers,
                                                  Socket socket) {
            // Not a client SSLSocket callback
            throw new UnsupportedOperationException();
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            try {
                log("KeyChainKeyManager getCertificateChain");
                X509Certificate[] certificateChain =
                        KeyChain.getCertificateChain(KeyChainTest.this, alias);
                if (certificateChain == null) {
                    log("Null certificate chain!");
                    return null;
                }
                log("Returned " + certificateChain.length + " certificates in chain");
                for (int i = 0; i < certificateChain.length; i++) {
                    Log.d(TAG, "certificate[" + i + "]=" + certificateChain[i]);
                }
                return certificateChain;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (KeyChainException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            // not a client SSLSocket callback
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            // not a client SSLSocket callback
            throw new UnsupportedOperationException();
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            try {
                log("KeyChainKeyManager.getPrivateKey(\"" + alias + "\")");
                PrivateKey privateKey = KeyChain.getPrivateKey(KeyChainTest.this, alias);
                Log.d(TAG, "privateKey = " + privateKey);
                return privateKey;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (KeyChainException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Write a message to the log, also to a visible TextView if available.
     */
    private void log(final String message) {
        Log.d(TAG, message);
        if (mLogView != null) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    mLogView.append(message + "\n");
                }
            });
        }
    }

    /**
     * Utility class to store one step per object.
     */
    private static class Step {
        // Instruction message to show before running
        int instructionTextId;

        // Whether to allow a 'skip' button for this step
        boolean skippable;

        // Set of commands to run when 'next' is pressed
        AsyncTask<Void, Void, Void> task;

        public Step(int instructionTextId, boolean skippable, AsyncTask<Void, Void, Void> task) {
            this.instructionTextId = instructionTextId;
            this.skippable = skippable;
            this.task = task;
        }
    }
}
