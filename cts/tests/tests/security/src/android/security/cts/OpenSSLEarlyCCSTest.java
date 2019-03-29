/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;
import android.security.cts.OpenSSLHeartbleedTest.AlertMessage;
import android.security.cts.OpenSSLHeartbleedTest.HandshakeMessage;
import android.security.cts.OpenSSLHeartbleedTest.HardcodedCertX509KeyManager;
import android.security.cts.OpenSSLHeartbleedTest.TlsProtocols;
import android.security.cts.OpenSSLHeartbleedTest.TlsRecord;
import android.security.cts.OpenSSLHeartbleedTest.TlsRecordReader;
import android.security.cts.OpenSSLHeartbleedTest.TrustAllX509TrustManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/**
 * Tests for the OpenSSL early ChangeCipherSpec (CCS) vulnerability (CVE-2014-0224).
 */
@SecurityTest
public class OpenSSLEarlyCCSTest extends InstrumentationTestCase {

    // IMPLEMENTATION NOTE: This test spawns an SSLSocket client, SSLServerSocket server, and a
    // Man-in-The-Middle (MiTM). The client connects to the MiTM which then connects to the server
    // and starts forwarding all TLS records between the client and the server. In tests that check
    // for the early CCS vulnerability, the MiTM also injects an early ChangeCipherSpec record into
    // the traffic to which a correctly implemented peer is supposed to reply by immediately
    // aborting the TLS handshake with an unexpected_message fatal alert.

    // IMPLEMENTATION NOTE: This test spawns several background threads that perform network I/O
    // on localhost. To ensure that these background threads are cleaned up at the end of the test,
    // tearDown() kills the sockets they may be using. To aid this behavior, all Socket and
    // ServerSocket instances are available as fields of this class. These fields should be accessed
    // via setters and getters to avoid memory visibility issues due to concurrency.

    private static final String TAG = OpenSSLEarlyCCSTest.class.getSimpleName();

    private SSLServerSocket mServerListeningSocket;
    private SSLSocket mServerSocket;
    private SSLSocket mClientSocket;
    private ServerSocket mMitmListeningSocket;
    private Socket mMitmServerSocket;
    private Socket mMitmClientSocket;
    private ExecutorService mExecutorService;

    private boolean mCCSWasInjected;
    private TlsRecord mFirstRecordReceivedAfterCCSWasInjected;
    private boolean mFirstRecordReceivedAfterCCSWasInjectedMayBeEncrypted;

    @Override
    protected void tearDown() throws Exception {
        Log.i(TAG, "Tearing down");
        try {
            if (mExecutorService != null) {
                mExecutorService.shutdownNow();
            }
            OpenSSLHeartbleedTest.closeQuietly(getServerListeningSocket());
            OpenSSLHeartbleedTest.closeQuietly(getServerSocket());
            OpenSSLHeartbleedTest.closeQuietly(getClientSocket());
            OpenSSLHeartbleedTest.closeQuietly(getMitmListeningSocket());
            OpenSSLHeartbleedTest.closeQuietly(getMitmServerSocket());
            OpenSSLHeartbleedTest.closeQuietly(getMitmClientSocket());
        } finally {
            super.tearDown();
            Log.i(TAG, "Tear down completed");
        }
    }

    /**
     * Tests that TLS handshake succeeds when the MiTM simply forwards all data without tampering
     * with it. This is to catch issues unrelated to early CCS.
     */
    public void testWithoutEarlyCCS() throws Exception {
        handshake(false, false);
    }

    /**
     * Tests whether client sockets are vulnerable to early CCS.
     */
    public void testEarlyCCSInjectedIntoClient() throws Exception {
        checkEarlyCCS(true);
    }

    /**
     * Tests whether server sockets are vulnerable to early CCS.
     */
    public void testEarlyCCSInjectedIntoServer() throws Exception {
        checkEarlyCCS(false);
    }

    /**
     * Tests for the early CCS vulnerability.
     *
     * @param client {@code true} to test the client, {@code false} to test the server.
     */
    private void checkEarlyCCS(boolean client) throws Exception {
        // IMPLEMENTATION NOTE: The MiTM is forwarding all TLS records between the client and the
        // server unmodified. Additionally, the MiTM transmits an early ChangeCipherSpec to server
        // (if "client" argument is false) right before client's ClientKeyExchange or to client (if
        // "client" argument is true) right before server's Certificate. The peer is expected to
        // to abort the handshake immediately with unexpected_message alert.
        try {
            handshake(true, client);
            // TLS handshake succeeded
            assertTrue("Early CCS injected", wasCCSInjected());
            fail("Handshake succeeded despite early CCS having been injected");
        } catch (ExecutionException e) {
            // TLS handshake failed
            assertTrue("Early CCS injected", wasCCSInjected());
            TlsRecord firstRecordReceivedAfterCCSWasInjected =
                    getFirstRecordReceivedAfterCCSWasInjected();
            assertNotNull(
                    "Nothing received after early CCS was injected",
                    firstRecordReceivedAfterCCSWasInjected);
            if (firstRecordReceivedAfterCCSWasInjected.protocol == TlsProtocols.ALERT) {
                AlertMessage alert = AlertMessage.tryParse(firstRecordReceivedAfterCCSWasInjected);
                if ((alert != null)
                        && (alert.level == AlertMessage.LEVEL_FATAL)
                        && (alert.description == AlertMessage.DESCRIPTION_UNEXPECTED_MESSAGE)) {
                    // Expected/correct response to an early CCS
                    return;
                }
            }
            fail("SSLSocket is vulnerable to early CCS in " + ((client) ? "client" : "server")
                    + " mode: unexpected record received after CCS was injected: "
                    + getRecordInfo(
                            getFirstRecordReceivedAfterCCSWasInjected(),
                            getFirstRecordReceivedAfterCCSWasInjectedMayBeEncrypted()));
        }
    }

    /**
     * Starts the client, server, and the MiTM. Makes the client and server perform a TLS handshake
     * and exchange application-level data. The MiTM injects a ChangeCipherSpec record if requested
     * by {@code earlyCCSInjected}. The direction of the injected message is specified by
     * {@code injectedIntoClient}.
     */
    private void handshake(
            final boolean earlyCCSInjected,
            final boolean injectedIntoClient) throws Exception {
        mExecutorService = Executors.newFixedThreadPool(4);
        setServerListeningSocket(serverBind());
        final SocketAddress serverAddress = getServerListeningSocket().getLocalSocketAddress();
        Log.i(TAG, "Server bound to " + serverAddress);

        setMitmListeningSocket(mitmBind());
        final SocketAddress mitmAddress = getMitmListeningSocket().getLocalSocketAddress();
        Log.i(TAG, "MiTM bound to " + mitmAddress);

        // Start the MiTM daemon in the background
        mExecutorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                mitmAcceptAndForward(serverAddress, earlyCCSInjected, injectedIntoClient);
                return null;
            }
        });
        // Start the server in the background
        Future<Void> serverFuture = mExecutorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                serverAcceptAndHandshake();
                return null;
            }
        });
        // Start the client in the background
        Future<Void> clientFuture = mExecutorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                clientConnectAndHandshake(mitmAddress);
                return null;
            }
        });

        // Wait for both client and server to terminate, to ensure that we observe all the traffic
        // exchanged between them. Throw an exception if one of them failed.
        Log.i(TAG, "Waiting for client");
        // Wait for the client, but don't yet throw an exception if it failed.
        Exception clientException = null;
        try {
            clientFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            clientException = e;
        }
        Log.i(TAG, "Waiting for server");
        // Wait for the server and throw an exception if it failed.
        serverFuture.get(5, TimeUnit.SECONDS);
        // Throw an exception if the client failed.
        if (clientException != null) {
            throw clientException;
        }
        Log.i(TAG, "Handshake completed and application data exchanged");
    }

    private void clientConnectAndHandshake(SocketAddress serverAddress) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                null,
                new TrustManager[] {new TrustAllX509TrustManager()},
                null);
        SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
        setClientSocket(socket);
        try {
            Log.i(TAG, "Client connecting to " + serverAddress);
            socket.connect(serverAddress);
            Log.i(TAG, "Client connected to server from " + socket.getLocalSocketAddress());
            // Ensure a TLS handshake is performed and an exception is thrown if it fails.
            socket.getOutputStream().write("client".getBytes());
            socket.getOutputStream().flush();
            Log.i(TAG, "Client sent request. Reading response");
            int b = socket.getInputStream().read();
            Log.i(TAG, "Client read response: " + b);
        } catch (Exception e) {
            // Make sure the test log includes exceptions from all parties involved.
            Log.w(TAG, "Client failed", e);
            throw e;
          } finally {
            socket.close();
        }
    }

    private SSLServerSocket serverBind() throws Exception {
        // Load the server's private key and cert chain
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                OpenSSLHeartbleedTest.readResource(
                        getInstrumentation().getContext(), R.raw.openssl_heartbleed_test_key)));
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate[] certChain =  new X509Certificate[] {
                (X509Certificate) certFactory.generateCertificate(
                        new ByteArrayInputStream(OpenSSLHeartbleedTest.readResource(
                                getInstrumentation().getContext(),
                                R.raw.openssl_heartbleed_test_cert)))
        };

        // Initialize TLS context to use the private key and cert chain for server sockets
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                new KeyManager[] {new HardcodedCertX509KeyManager(privateKey, certChain)},
                null,
                null);

        Log.i(TAG, "Server binding to local port");
        return (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(0);
    }

    private void serverAcceptAndHandshake() throws Exception {
        SSLSocket socket = null;
        SSLServerSocket serverSocket = getServerListeningSocket();
        try {
            Log.i(TAG, "Server listening for incoming connection");
            socket = (SSLSocket) serverSocket.accept();
            setServerSocket(socket);
            Log.i(TAG, "Server accepted connection from " + socket.getRemoteSocketAddress());
            // Ensure a TLS handshake is performed and an exception is thrown if it fails.
            socket.getOutputStream().write("server".getBytes());
            socket.getOutputStream().flush();
            Log.i(TAG, "Server sent reply. Reading response");
            int b = socket.getInputStream().read();
            Log.i(TAG, "Server read response: " + b);
        } catch (Exception e) {
            // Make sure the test log includes exceptions from all parties involved.
            Log.w(TAG, "Server failed", e);
            throw e;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private ServerSocket mitmBind() throws Exception {
        Log.i(TAG, "MiTM binding to local port");
        return ServerSocketFactory.getDefault().createServerSocket(0);
    }

    /**
     * Accepts the connection on the MiTM listening socket, forwards the TLS records between the
     * client and the server, and, if requested, injects an early {@code ChangeCipherSpec} record.
     *
     * @param injectEarlyCCS whether to inject an early {@code ChangeCipherSpec} record.
     * @param injectIntoClient when {@code injectEarlyCCS} is {@code true}, whether to inject the
     *        {@code ChangeCipherSpec} record into client or into server.
     */
    private void mitmAcceptAndForward(
            SocketAddress serverAddress,
            final boolean injectEarlyCCS,
            final boolean injectIntoClient) throws Exception {
        Socket clientSocket = null;
        Socket serverSocket = null;
        ServerSocket listeningSocket = getMitmListeningSocket();
        try {
            Log.i(TAG, "MiTM waiting for incoming connection");
            clientSocket = listeningSocket.accept();
            setMitmClientSocket(clientSocket);
            Log.i(TAG, "MiTM accepted connection from " + clientSocket.getRemoteSocketAddress());
            serverSocket = SocketFactory.getDefault().createSocket();
            setMitmServerSocket(serverSocket);
            Log.i(TAG, "MiTM connecting to server " + serverAddress);
            serverSocket.connect(serverAddress, 10000);
            Log.i(TAG, "MiTM connected to server from " + serverSocket.getLocalSocketAddress());
            final InputStream serverInputStream = serverSocket.getInputStream();
            final OutputStream clientOutputStream = clientSocket.getOutputStream();
            Future<Void> serverToClientTask = mExecutorService.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    // Inject early ChangeCipherSpec before Certificate, if requested
                    forwardTlsRecords(
                            "MiTM S->C",
                            serverInputStream,
                            clientOutputStream,
                            (injectEarlyCCS && injectIntoClient)
                                      ? HandshakeMessage.TYPE_CERTIFICATE : -1);
                    return null;
                }
            });
            // Inject early ChangeCipherSpec before ClientKeyExchange, if requested
            forwardTlsRecords(
                    "MiTM C->S",
                    clientSocket.getInputStream(),
                    serverSocket.getOutputStream(),
                    (injectEarlyCCS && !injectIntoClient)
                            ? HandshakeMessage.TYPE_CLIENT_KEY_EXCHANGE : -1);
            serverToClientTask.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Make sure the test log includes exceptions from all parties involved.
            Log.w(TAG, "MiTM failed", e);
            throw e;
        } finally {
            OpenSSLHeartbleedTest.closeQuietly(clientSocket);
            OpenSSLHeartbleedTest.closeQuietly(serverSocket);
        }
    }

    /**
     * Forwards TLS records from the provided {@code InputStream} to the provided
     * {@code OutputStream}. If requested, injects an early {@code ChangeCipherSpec}.
     */
    private void forwardTlsRecords(
            String logPrefix,
            InputStream in,
            OutputStream out,
            int handshakeMessageTypeBeforeWhichToInjectEarlyCCS) throws Exception {
        Log.i(TAG, logPrefix + ": record forwarding started");
        boolean interestingRecordsLogged =
                handshakeMessageTypeBeforeWhichToInjectEarlyCCS == -1;
        try {
            TlsRecordReader reader = new TlsRecordReader(in);
            byte[] recordBytes;
            // Fragments contained in records may be encrypted after a certain point in the
            // handshake. Once they are encrypted, this MiTM cannot inspect their plaintext which.
            boolean fragmentEncryptionMayBeEnabled = false;
            while ((recordBytes = reader.readRecord()) != null) {
                TlsRecord record = TlsRecord.parse(recordBytes);
                forwardTlsRecord(logPrefix,
                        recordBytes,
                        record,
                        fragmentEncryptionMayBeEnabled,
                        out,
                        interestingRecordsLogged,
                        handshakeMessageTypeBeforeWhichToInjectEarlyCCS);
                if (record.protocol == TlsProtocols.CHANGE_CIPHER_SPEC) {
                    fragmentEncryptionMayBeEnabled = true;
                }
            }
        } finally {
            Log.d(TAG, logPrefix + ": record forwarding finished");
        }
    }

    private void forwardTlsRecord(
            String logPrefix,
            byte[] recordBytes,
            TlsRecord record,
            boolean fragmentEncryptionMayBeEnabled,
            OutputStream out,
            boolean interestingRecordsLogged,
            int handshakeMessageTypeBeforeWhichToInjectCCS) throws IOException {
        // Save information about the record if it's of interest to this test
        if (interestingRecordsLogged) {
            if (wasCCSInjected()) {
                setFirstRecordReceivedAfterCCSWasInjected(record, fragmentEncryptionMayBeEnabled);
            }
        } else if ((record.protocol == TlsProtocols.CHANGE_CIPHER_SPEC)
                && (handshakeMessageTypeBeforeWhichToInjectCCS != -1)) {
            // Do not forward original ChangeCipherSpec record(s) if we're injecting such a record
            // ourselves. This is to make sure that the peer sees only one ChangeCipherSpec.
            Log.i(TAG, logPrefix + ": Dropping TLS record. "
                    + getRecordInfo(record, fragmentEncryptionMayBeEnabled));
            return;
        }

        // Inject a ChangeCipherSpec, if necessary, before the specified handshake message type
        if (handshakeMessageTypeBeforeWhichToInjectCCS != -1) {
            if ((!fragmentEncryptionMayBeEnabled) && (OpenSSLHeartbleedTest.isHandshakeMessageType(
                    record, handshakeMessageTypeBeforeWhichToInjectCCS))) {
                Log.i(TAG, logPrefix + ": Injecting ChangeCipherSpec");
                setCCSWasInjected();
                out.write(createChangeCipherSpecRecord(record.versionMajor, record.versionMinor));
                out.flush();
            }
        }

        Log.i(TAG, logPrefix + ": Forwarding TLS record. "
                + getRecordInfo(record, fragmentEncryptionMayBeEnabled));
        out.write(recordBytes);
        out.flush();
    }

    private static String getRecordInfo(TlsRecord record, boolean mayBeEncrypted) {
        StringBuilder result = new StringBuilder();
        result.append(getProtocolName(record.protocol))
                .append(", ")
                .append(getFragmentInfo(record, mayBeEncrypted));
        return result.toString();
    }

    private static String getProtocolName(int protocol) {
        switch (protocol) {
            case TlsProtocols.ALERT:
                return "alert";
            case TlsProtocols.APPLICATION_DATA:
                return "application data";
            case TlsProtocols.CHANGE_CIPHER_SPEC:
                return "change cipher spec";
            case TlsProtocols.HANDSHAKE:
                return "handshake";
            default:
                return String.valueOf(protocol);
        }
    }

    private static String getFragmentInfo(TlsRecord record, boolean mayBeEncrypted) {
        StringBuilder result = new StringBuilder();
        if (mayBeEncrypted) {
            result.append("encrypted?");
        } else {
            switch (record.protocol) {
                case TlsProtocols.ALERT:
                    result.append("level: " + ((record.fragment.length > 0)
                            ? String.valueOf(record.fragment[0] & 0xff) : "n/a")
                    + ", description: "
                    + ((record.fragment.length > 1)
                            ? String.valueOf(record.fragment[1] & 0xff) : "n/a"));
                    break;
                case TlsProtocols.APPLICATION_DATA:
                    break;
                case TlsProtocols.CHANGE_CIPHER_SPEC:
                    result.append("payload: " + ((record.fragment.length > 0)
                            ? String.valueOf(record.fragment[0] & 0xff) : "n/a"));
                    break;
                case TlsProtocols.HANDSHAKE:
                    result.append("type: " + ((record.fragment.length > 0)
                            ? String.valueOf(record.fragment[0] & 0xff) : "n/a"));
                    break;
            }
        }
        result.append(", ").append("fragment length: " + record.fragment.length);
        return result.toString();
    }

    private synchronized void setServerListeningSocket(SSLServerSocket socket) {
        mServerListeningSocket = socket;
    }

    private synchronized SSLServerSocket getServerListeningSocket() {
        return mServerListeningSocket;
    }

    private synchronized void setServerSocket(SSLSocket socket) {
        mServerSocket = socket;
    }

    private synchronized SSLSocket getServerSocket() {
        return mServerSocket;
    }

    private synchronized void setClientSocket(SSLSocket socket) {
        mClientSocket = socket;
    }

    private synchronized SSLSocket getClientSocket() {
        return mClientSocket;
    }

    private synchronized void setMitmListeningSocket(ServerSocket socket) {
        mMitmListeningSocket = socket;
    }

    private synchronized ServerSocket getMitmListeningSocket() {
        return mMitmListeningSocket;
    }

    private synchronized void setMitmServerSocket(Socket socket) {
        mMitmServerSocket = socket;
    }

    private synchronized Socket getMitmServerSocket() {
        return mMitmServerSocket;
    }

    private synchronized void setMitmClientSocket(Socket socket) {
        mMitmClientSocket = socket;
    }

    private synchronized Socket getMitmClientSocket() {
        return mMitmClientSocket;
    }

    private synchronized void setCCSWasInjected() {
        mCCSWasInjected = true;
    }

    private synchronized boolean wasCCSInjected() {
        return mCCSWasInjected;
    }

    private synchronized void setFirstRecordReceivedAfterCCSWasInjected(
            TlsRecord record, boolean mayBeEncrypted) {
        if (mFirstRecordReceivedAfterCCSWasInjected == null) {
            mFirstRecordReceivedAfterCCSWasInjected = record;
            mFirstRecordReceivedAfterCCSWasInjectedMayBeEncrypted = mayBeEncrypted;
        }
    }

    private synchronized TlsRecord getFirstRecordReceivedAfterCCSWasInjected() {
        return mFirstRecordReceivedAfterCCSWasInjected;
    }

    private synchronized boolean getFirstRecordReceivedAfterCCSWasInjectedMayBeEncrypted() {
        return mFirstRecordReceivedAfterCCSWasInjectedMayBeEncrypted;
    }

    private static byte[] createChangeCipherSpecRecord(int versionMajor, int versionMinor) {
        TlsRecord record = new TlsRecord();
        record.protocol = TlsProtocols.CHANGE_CIPHER_SPEC;
        record.versionMajor = versionMajor;
        record.versionMinor = versionMinor;
        record.fragment = new byte[] {1};
        return TlsRecord.unparse(record);
    }
}
