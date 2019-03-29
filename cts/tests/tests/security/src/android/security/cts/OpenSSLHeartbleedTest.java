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

import android.content.Context;
import android.platform.test.annotations.SecurityTest;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
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
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Tests for the OpenSSL Heartbleed vulnerability.
 */
@SecurityTest
public class OpenSSLHeartbleedTest extends InstrumentationTestCase {

    // IMPLEMENTATION NOTE: This test spawns an SSLSocket client, SSLServerSocket server, and a
    // Man-in-The-Middle (MiTM). The client connects to the MiTM which then connects to the server
    // and starts forwarding all TLS records between the client and the server. In tests that check
    // for the Heartbleed vulnerability, the MiTM also injects a HeartbeatRequest message into the
    // traffic.

    // IMPLEMENTATION NOTE: This test spawns several background threads that perform network I/O
    // on localhost. To ensure that these background threads are cleaned up at the end of the test
    // tearDown() kills the sockets they may be using. To aid this behavior, all Socket and
    // ServerSocket instances are available as fields of this class. These fields should be accessed
    // via setters and getters to avoid memory visibility issues due to concurrency.

    private static final String TAG = OpenSSLHeartbleedTest.class.getSimpleName();

    private SSLServerSocket mServerListeningSocket;
    private SSLSocket mServerSocket;
    private SSLSocket mClientSocket;
    private ServerSocket mMitmListeningSocket;
    private Socket mMitmServerSocket;
    private Socket mMitmClientSocket;
    private ExecutorService mExecutorService;

    private boolean mHeartbeatRequestWasInjected;
    private boolean mHeartbeatResponseWasDetetected;
    private int mFirstDetectedFatalAlertDescription = -1;

    @Override
    protected void tearDown() throws Exception {
        Log.i(TAG, "Tearing down");
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
        closeQuietly(getServerListeningSocket());
        closeQuietly(getServerSocket());
        closeQuietly(getClientSocket());
        closeQuietly(getMitmListeningSocket());
        closeQuietly(getMitmServerSocket());
        closeQuietly(getMitmClientSocket());
        super.tearDown();
        Log.i(TAG, "Tear down completed");
    }

    /**
     * Tests that TLS handshake succeeds when the MiTM simply forwards all data without tampering
     * with it. This is to catch issues unrelated to TLS heartbeats.
     */
    public void testWithoutHeartbeats() throws Exception {
        handshake(false, false);
    }

    /**
     * Tests whether client sockets are vulnerable to Heartbleed.
     */
    public void testClientHeartbleed() throws Exception {
        checkHeartbleed(true);
    }

    /**
     * Tests whether server sockets are vulnerable to Heartbleed.
     */
    public void testServerHeartbleed() throws Exception {
        checkHeartbleed(false);
    }

    /**
     * Tests for Heartbleed.
     *
     * @param client {@code true} to test the client, {@code false} to test the server.
     */
    private void checkHeartbleed(boolean client) throws Exception {
        // IMPLEMENTATION NOTE: The MiTM is forwarding all TLS records between the client and the
        // server unmodified. Additionally, the MiTM transmits a malformed HeartbeatRequest to
        // server (if "client" argument is false) right after client's ClientKeyExchange or to
        // client (if "client" argument is true) right after server's ServerHello. The peer is
        // expected to either ignore the HeartbeatRequest (if heartbeats are supported) or to abort
        // the handshake with unexpected_message alert (if heartbeats are not supported).
        try {
            handshake(true, client);
        } catch (ExecutionException e) {
            assertFalse(
                    "SSLSocket is vulnerable to Heartbleed in " + ((client) ? "client" : "server")
                            + " mode",
                    wasHeartbeatResponseDetected());
            if (e.getCause() instanceof SSLException) {
                // TLS handshake or data exchange failed. Check whether the error was caused by
                // fatal alert unexpected_message
                int alertDescription = getFirstDetectedFatalAlertDescription();
                if (alertDescription == -1) {
                    fail("Handshake failed without a fatal alert");
                }
                assertEquals(
                        "First fatal alert description received from server",
                        AlertMessage.DESCRIPTION_UNEXPECTED_MESSAGE,
                        alertDescription);
                return;
            } else {
                throw e;
            }
        }

        // TLS handshake succeeded
        assertFalse(
                "SSLSocket is vulnerable to Heartbleed in " + ((client) ? "client" : "server")
                        + " mode",
                wasHeartbeatResponseDetected());
        assertTrue("HeartbeatRequest not injected", wasHeartbeatRequestInjected());
    }

    /**
     * Starts the client, server, and the MiTM. Makes the client and server perform a TLS handshake
     * and exchange application-level data. The MiTM injects a HeartbeatRequest message if requested
     * by {@code heartbeatRequestInjected}. The direction of the injected message is specified by
     * {@code injectedIntoClient}.
     */
    private void handshake(
            final boolean heartbeatRequestInjected,
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
                mitmAcceptAndForward(
                        serverAddress,
                        heartbeatRequestInjected,
                        injectedIntoClient);
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
            Log.w(TAG, "Client failed", e);
            throw e;
          } finally {
            socket.close();
        }
    }

    public SSLServerSocket serverBind() throws Exception {
        // Load the server's private key and cert chain
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                readResource(
                        getInstrumentation().getContext(), R.raw.openssl_heartbleed_test_key)));
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate[] certChain =  new X509Certificate[] {
                (X509Certificate) certFactory.generateCertificate(
                        new ByteArrayInputStream(readResource(
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
     * client and the server, and, if requested, injects a {@code HeartbeatRequest}.
     *
     * @param injectHeartbeat whether to inject a {@code HeartbeatRequest} message.
     * @param injectIntoClient when {@code injectHeartbeat} is {@code true}, whether to inject the
     *        {@code HeartbeatRequest} message into client or into server.
     */
    private void mitmAcceptAndForward(
            SocketAddress serverAddress,
            final boolean injectHeartbeat,
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
                    // Inject HeatbeatRequest after ServerHello, if requested
                    forwardTlsRecords(
                            "MiTM S->C",
                            serverInputStream,
                            clientOutputStream,
                            (injectHeartbeat && injectIntoClient)
                                    ? HandshakeMessage.TYPE_SERVER_HELLO : -1);
                    return null;
                }
            });
            // Inject HeatbeatRequest after ClientKeyExchange, if requested
            forwardTlsRecords(
                    "MiTM C->S",
                    clientSocket.getInputStream(),
                    serverSocket.getOutputStream(),
                    (injectHeartbeat && !injectIntoClient)
                            ? HandshakeMessage.TYPE_CLIENT_KEY_EXCHANGE : -1);
            serverToClientTask.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.w(TAG, "MiTM failed", e);
            throw e;
          } finally {
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
        }
    }

    /**
     * Forwards TLS records from the provided {@code InputStream} to the provided
     * {@code OutputStream}. If requested, injects a {@code HeartbeatMessage}.
     */
    private void forwardTlsRecords(
            String logPrefix,
            InputStream in,
            OutputStream out,
            int handshakeMessageTypeAfterWhichToInjectHeartbeatRequest) throws Exception {
        Log.i(TAG, logPrefix + ": record forwarding started");
        boolean interestingRecordsLogged =
                handshakeMessageTypeAfterWhichToInjectHeartbeatRequest == -1;
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
                        handshakeMessageTypeAfterWhichToInjectHeartbeatRequest);
                if (record.protocol == TlsProtocols.CHANGE_CIPHER_SPEC) {
                    fragmentEncryptionMayBeEnabled = true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, logPrefix + ": failed", e);
            throw e;
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
            int handshakeMessageTypeAfterWhichToInjectHeartbeatRequest) throws IOException {
        // Save information about the records if its of interest to this test
        if (interestingRecordsLogged) {
            switch (record.protocol) {
                case TlsProtocols.ALERT:
                    if (!fragmentEncryptionMayBeEnabled) {
                        AlertMessage alert = AlertMessage.tryParse(record);
                        if ((alert != null) && (alert.level == AlertMessage.LEVEL_FATAL)) {
                            setFatalAlertDetected(alert.description);
                        }
                    }
                    break;
                case TlsProtocols.HEARTBEAT:
                    // When TLS records are encrypted, we cannot determine whether a
                    // heartbeat is a HeartbeatResponse. In our setup, the client and the
                    // server are not expected to sent HeartbeatRequests. Thus, we err on
                    // the side of caution and assume that any heartbeat message sent by
                    // client or server is a HeartbeatResponse.
                    Log.e(TAG, logPrefix
                            + ": heartbeat response detected -- vulnerable to Heartbleed");
                    setHeartbeatResponseWasDetected();
                    break;
            }
        }

        Log.i(TAG, logPrefix + ": Forwarding TLS record. "
                + getRecordInfo(record, fragmentEncryptionMayBeEnabled));
        out.write(recordBytes);
        out.flush();

        // Inject HeartbeatRequest, if necessary, after the specified handshake message type
        if (handshakeMessageTypeAfterWhichToInjectHeartbeatRequest != -1) {
            if ((!fragmentEncryptionMayBeEnabled) && (isHandshakeMessageType(
                    record, handshakeMessageTypeAfterWhichToInjectHeartbeatRequest))) {
                // The Heartbeat Request message below is malformed because its declared
                // length of payload one byte larger than the actual payload. The peer is
                // supposed to reject such messages.
                byte[] payload = "arbitrary".getBytes("US-ASCII");
                byte[] heartbeatRequestRecordBytes = createHeartbeatRequestRecord(
                        record.versionMajor,
                        record.versionMinor,
                        payload.length + 1,
                        payload);
                Log.i(TAG, logPrefix + ": Injecting malformed HeartbeatRequest: "
                        + getRecordInfo(
                                TlsRecord.parse(heartbeatRequestRecordBytes), false));
                setHeartbeatRequestWasInjected();
                out.write(heartbeatRequestRecordBytes);
                out.flush();
            }
        }
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
            case TlsProtocols.HEARTBEAT:
                return "heatbeat";
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
                case TlsProtocols.HEARTBEAT:
                    result.append("type: " + ((record.fragment.length > 0)
                            ? String.valueOf(record.fragment[0] & 0xff) : "n/a")
                            + ", payload length: "
                            + ((record.fragment.length >= 3)
                                    ? String.valueOf(
                                            getUnsignedShortBigEndian(record.fragment, 1))
                                    : "n/a"));
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

    private synchronized void setHeartbeatRequestWasInjected() {
        mHeartbeatRequestWasInjected = true;
    }

    private synchronized boolean wasHeartbeatRequestInjected() {
        return mHeartbeatRequestWasInjected;
    }

    private synchronized void setHeartbeatResponseWasDetected() {
        mHeartbeatResponseWasDetetected = true;
    }

    private synchronized boolean wasHeartbeatResponseDetected() {
        return mHeartbeatResponseWasDetetected;
    }

    private synchronized void setFatalAlertDetected(int description) {
        if (mFirstDetectedFatalAlertDescription == -1) {
            mFirstDetectedFatalAlertDescription = description;
        }
    }

    private synchronized int getFirstDetectedFatalAlertDescription() {
        return mFirstDetectedFatalAlertDescription;
    }

    public static abstract class TlsProtocols {
        public static final int CHANGE_CIPHER_SPEC = 20;
        public static final int ALERT = 21;
        public static final int HANDSHAKE = 22;
        public static final int APPLICATION_DATA = 23;
        public static final int HEARTBEAT = 24;
        private TlsProtocols() {}
    }

    public static class TlsRecord {
        public int protocol;
        public int versionMajor;
        public int versionMinor;
        public byte[] fragment;

        public static TlsRecord parse(byte[] record) throws IOException {
            TlsRecord result = new TlsRecord();
            if (record.length < TlsRecordReader.RECORD_HEADER_LENGTH) {
                throw new IOException("Record too short: " + record.length);
            }
            result.protocol = record[0] & 0xff;
            result.versionMajor = record[1] & 0xff;
            result.versionMinor = record[2] & 0xff;
            int fragmentLength = getUnsignedShortBigEndian(record, 3);
            int actualFragmentLength = record.length - TlsRecordReader.RECORD_HEADER_LENGTH;
            if (fragmentLength != actualFragmentLength) {
                throw new IOException("Fragment length mismatch. Expected: " + fragmentLength
                        + ", actual: " + actualFragmentLength);
            }
            result.fragment = new byte[fragmentLength];
            System.arraycopy(
                    record, TlsRecordReader.RECORD_HEADER_LENGTH,
                    result.fragment, 0,
                    fragmentLength);
            return result;
        }

        public static byte[] unparse(TlsRecord record) {
            byte[] result = new byte[TlsRecordReader.RECORD_HEADER_LENGTH + record.fragment.length];
            result[0] = (byte) record.protocol;
            result[1] = (byte) record.versionMajor;
            result[2] = (byte) record.versionMinor;
            putUnsignedShortBigEndian(result, 3, record.fragment.length);
            System.arraycopy(
                    record.fragment, 0,
                    result, TlsRecordReader.RECORD_HEADER_LENGTH,
                    record.fragment.length);
            return result;
        }
    }

    public static final boolean isHandshakeMessageType(TlsRecord record, int type) {
        HandshakeMessage handshake = HandshakeMessage.tryParse(record);
        if (handshake == null) {
            return false;
        }
        return handshake.type == type;
    }

    public static class HandshakeMessage {
        public static final int TYPE_SERVER_HELLO = 2;
        public static final int TYPE_CERTIFICATE = 11;
        public static final int TYPE_CLIENT_KEY_EXCHANGE = 16;

        public int type;

        /**
         * Parses the provided TLS record as a handshake message.
         *
         * @return alert message or {@code null} if the record does not contain a handshake message.
         */
        public static HandshakeMessage tryParse(TlsRecord record) {
            if (record.protocol != TlsProtocols.HANDSHAKE) {
                return null;
            }
            if (record.fragment.length < 1) {
                return null;
            }
            HandshakeMessage result = new HandshakeMessage();
            result.type = record.fragment[0] & 0xff;
            return result;
        }
    }

    public static class AlertMessage {
        public static final int LEVEL_FATAL = 2;
        public static final int DESCRIPTION_UNEXPECTED_MESSAGE = 10;

        public int level;
        public int description;

        /**
         * Parses the provided TLS record as an alert message.
         *
         * @return alert message or {@code null} if the record does not contain an alert message.
         */
        public static AlertMessage tryParse(TlsRecord record) {
            if (record.protocol != TlsProtocols.ALERT) {
                return null;
            }
            if (record.fragment.length < 2) {
                return null;
            }
            AlertMessage result = new AlertMessage();
            result.level = record.fragment[0] & 0xff;
            result.description = record.fragment[1] & 0xff;
            return result;
        }
    }

    private static abstract class HeartbeatProtocol {
        private HeartbeatProtocol() {}

        private static final int MESSAGE_TYPE_REQUEST = 1;
        @SuppressWarnings("unused")
        private static final int MESSAGE_TYPE_RESPONSE = 2;

        private static final int MESSAGE_HEADER_LENGTH = 3;
        private static final int MESSAGE_PADDING_LENGTH = 16;
    }

    private static byte[] createHeartbeatRequestRecord(
            int versionMajor, int versionMinor,
            int declaredPayloadLength, byte[] payload) {

        byte[] fragment = new byte[HeartbeatProtocol.MESSAGE_HEADER_LENGTH
                + payload.length + HeartbeatProtocol.MESSAGE_PADDING_LENGTH];
        fragment[0] = HeartbeatProtocol.MESSAGE_TYPE_REQUEST;
        putUnsignedShortBigEndian(fragment, 1, declaredPayloadLength); // payload_length
        TlsRecord record = new TlsRecord();
        record.protocol = TlsProtocols.HEARTBEAT;
        record.versionMajor = versionMajor;
        record.versionMinor = versionMinor;
        record.fragment = fragment;
        return TlsRecord.unparse(record);
    }

    /**
     * Reader of TLS records.
     */
    public static class TlsRecordReader {
        private static final int MAX_RECORD_LENGTH = 16384;
        public static final int RECORD_HEADER_LENGTH = 5;

        private final InputStream in;
        private final byte[] buffer;
        private int firstBufferedByteOffset;
        private int bufferedByteCount;

        public TlsRecordReader(InputStream in) {
            this.in = in;
            buffer = new byte[MAX_RECORD_LENGTH];
        }

        /**
         * Reads the next TLS record.
         *
         * @return TLS record or {@code null} if EOF was encountered before any bytes of a record
         *         could be read.
         */
        public byte[] readRecord() throws IOException {
            // Ensure that a TLS record header (or more) is in the buffer.
            if (bufferedByteCount < RECORD_HEADER_LENGTH) {
                boolean eofPermittedInstead = (bufferedByteCount == 0);
                boolean eofEncounteredInstead =
                        !readAtLeast(RECORD_HEADER_LENGTH, eofPermittedInstead);
                if (eofEncounteredInstead) {
                    // End of stream reached exactly before a TLS record start.
                    return null;
                }
            }

            // TLS record header (or more) is in the buffer.
            // Ensure that the rest of the record is in the buffer.
            int fragmentLength = getUnsignedShortBigEndian(buffer, firstBufferedByteOffset + 3);
            int recordLength = RECORD_HEADER_LENGTH + fragmentLength;
            if (recordLength > MAX_RECORD_LENGTH) {
                throw new IOException("TLS record too long: " + recordLength);
            }
            if (bufferedByteCount < recordLength) {
                readAtLeast(recordLength - bufferedByteCount, false);
            }

            // TLS record (or more) is in the buffer.
            byte[] record = new byte[recordLength];
            System.arraycopy(buffer, firstBufferedByteOffset, record, 0, recordLength);
            firstBufferedByteOffset += recordLength;
            bufferedByteCount -= recordLength;
            return record;
        }

        /**
         * Reads at least the specified number of bytes from the underlying {@code InputStream} into
         * the {@code buffer}.
         *
         * <p>Bytes buffered but not yet returned to the client in the {@code buffer} are relocated
         * to the start of the buffer to make space if necessary.
         *
         * @param eofPermittedInstead {@code true} if it's permitted for an EOF to be encountered
         *        without any bytes having been read.
         *
         * @return {@code true} if the requested number of bytes (or more) has been read,
         *         {@code false} if {@code eofPermittedInstead} was {@code true} and EOF was
         *         encountered when no bytes have yet been read.
         */
        private boolean readAtLeast(int size, boolean eofPermittedInstead) throws IOException {
            ensureRemainingBufferCapacityAtLeast(size);
            boolean firstAttempt = true;
            while (size > 0) {
                int chunkSize = in.read(
                        buffer,
                        firstBufferedByteOffset + bufferedByteCount,
                        buffer.length - (firstBufferedByteOffset + bufferedByteCount));
                if (chunkSize == -1) {
                    if ((firstAttempt) && (eofPermittedInstead)) {
                        return false;
                    } else {
                        throw new EOFException("Premature EOF");
                    }
                }
                firstAttempt = false;
                bufferedByteCount += chunkSize;
                size -= chunkSize;
            }
            return true;
        }

        /**
         * Ensures that there is enough capacity in the buffer to store the specified number of
         * bytes at the {@code firstBufferedByteOffset + bufferedByteCount} offset.
         */
        private void ensureRemainingBufferCapacityAtLeast(int size) throws IOException {
            int bufferCapacityRemaining =
                    buffer.length - (firstBufferedByteOffset + bufferedByteCount);
            if (bufferCapacityRemaining >= size) {
                return;
            }
            // Insufficient capacity at the end of the buffer.
            if (firstBufferedByteOffset > 0) {
                // Some of the bytes at the start of the buffer have already been returned to the
                // client of this reader. Check if moving the remaining buffered bytes to the start
                // of the buffer will make enough space at the end of the buffer.
                bufferCapacityRemaining += firstBufferedByteOffset;
                if (bufferCapacityRemaining >= size) {
                    System.arraycopy(buffer, firstBufferedByteOffset, buffer, 0, bufferedByteCount);
                    firstBufferedByteOffset = 0;
                    return;
                }
            }

            throw new IOException("Insuffucient remaining capacity in the buffer. Requested: "
                    + size + ", remaining: " + bufferCapacityRemaining);
        }
    }

    private static int getUnsignedShortBigEndian(byte[] buf, int offset) {
        return ((buf[offset] & 0xff) << 8) | (buf[offset + 1] & 0xff);
    }

    private static void putUnsignedShortBigEndian(byte[] buf, int offset, int value) {
        buf[offset] = (byte) ((value >>> 8) & 0xff);
        buf[offset + 1] = (byte) (value & 0xff);
    }

    // IMPLEMENTATION NOTE: We can't implement just one closeQueietly(Closeable) because on some
    // older Android platforms Socket did not implement these interfaces. To make this patch easy to
    // apply to these older platforms, we declare all the variants of closeQuietly that are needed
    // without relying on the Closeable interface.

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
    }

    public static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public static byte[] readResource(Context context, int resId) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        InputStream in = null;
        byte[] buf = new byte[16 * 1024];
        try {
            in = context.getResources().openRawResource(resId);
            int chunkSize;
            while ((chunkSize = in.read(buf)) != -1) {
                result.write(buf, 0, chunkSize);
            }
            return result.toByteArray();
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * {@link X509TrustManager} which trusts all certificate chains.
     */
    public static class TrustAllX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /**
     * {@link X509KeyManager} which uses the provided private key and cert chain for all sockets.
     */
    public static class HardcodedCertX509KeyManager implements X509KeyManager {

        private final PrivateKey mPrivateKey;
        private final X509Certificate[] mCertChain;

        HardcodedCertX509KeyManager(PrivateKey privateKey, X509Certificate[] certChain) {
            mPrivateKey = privateKey;
            mCertChain = certChain;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return "singleton";
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return mCertChain;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return mPrivateKey;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[] {"singleton"};
        }
    }
}
