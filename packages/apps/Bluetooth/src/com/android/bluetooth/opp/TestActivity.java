/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import com.android.bluetooth.R;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import android.view.View.OnClickListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import javax.obex.Authenticator;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;
import javax.obex.ServerSession;

public class TestActivity extends Activity {

    public String currentInsert;

    public int mCurrentByte = 0;

    EditText mUpdateView;

    EditText mAckView;

    EditText mDeleteView;

    EditText mInsertView;

    EditText mAddressView;

    EditText mMediaView;

    TestTcpServer server;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        String action = intent.getAction();

        Context c = getBaseContext();

        if (Intent.ACTION_SEND.equals(action)) {
            /*
             * Other application is trying to share a file via Bluetooth,
             * probably Pictures, or vCard. The Intent should contain an
             * EXTRA_STREAM with the data to attach.
             */

            String type = intent.getType();
            Uri stream = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);

            if (stream != null && type != null) {
                /*
                 * if (MimeUtility.mimeTypeMatches(type,
                 * Email.ACCEPTABLE_ATTACHMENT_SEND_TYPES)) {
                 * addAttachment(stream);
                 */
                Log.v(Constants.TAG, " Get share intent with Uri " + stream + " mimetype is "
                        + type);
                // Log.v(Constants.TAG, " trying Uri function " +
                // stream.getAuthority() + " " + Uri.parse(stream));
                Cursor cursor = c.getContentResolver().query(stream, null, null, null, null);
                cursor.close();

            }
            /* start insert a record */
            /*
             * ContentValues values = new ContentValues();
             * values.put(BluetoothShare.URI, stream.toString());
             * values.put(BluetoothShare.DESTINATION, "FF:FF:FF:00:00:00");
             * values.put(BluetoothShare.DIRECTION,
             * BluetoothShare.DIRECTION_OUTBOUND); final Uri contentUri =
             * getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
             * Log.v(Constants.TAG, "insert contentUri: " + contentUri);
             */
        }
        /*
         * Context c = getBaseContext(); c.startService(new Intent(c,
         * BluetoothOppService.class));
         */

        setContentView(R.layout.testactivity_main);

        Button mInsertRecord = (Button)findViewById(R.id.insert_record);
        Button mDeleteRecord = (Button)findViewById(R.id.delete_record);
        Button mUpdateRecord = (Button)findViewById(R.id.update_record);

        Button mAckRecord = (Button)findViewById(R.id.ack_record);

        Button mDeleteAllRecord = (Button)findViewById(R.id.deleteAll_record);
        mUpdateView = (EditText)findViewById(R.id.update_text);
        mAckView = (EditText)findViewById(R.id.ack_text);
        mDeleteView = (EditText)findViewById(R.id.delete_text);
        mInsertView = (EditText)findViewById(R.id.insert_text);

        mAddressView = (EditText)findViewById(R.id.address_text);
        mMediaView = (EditText)findViewById(R.id.media_text);

        mInsertRecord.setOnClickListener(insertRecordListener);
        mDeleteRecord.setOnClickListener(deleteRecordListener);
        mUpdateRecord.setOnClickListener(updateRecordListener);
        mAckRecord.setOnClickListener(ackRecordListener);
        mDeleteAllRecord.setOnClickListener(deleteAllRecordListener);

        Button mStartTcpServer = (Button)findViewById(R.id.start_server);
        mStartTcpServer.setOnClickListener(startTcpServerListener);

        Button mNotifyTcpServer = (Button)findViewById(R.id.notify_server);
        mNotifyTcpServer.setOnClickListener(notifyTcpServerListener);
        /* parse insert result Uri */
        /*
         * String id = contentUri.getPathSegments().get(1); Log.v(Constants.TAG,
         * "insert record id is " + id); Uri contentUri1 =
         * Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
         */
        /* update a single column of a record */
        /*
         * ContentValues updateValues = new ContentValues();
         * updateValues.put(BluetoothShare.TOTAL_BYTES, 120000);
         * getContentResolver().update(contentUri1,updateValues,null,null);
         */
        /* query a single column of a record */
        /*
         * Cursor queryC = getContentResolver().query(contentUri1, null, null,
         * null, null); if (queryC != null) { if (queryC.moveToFirst()) { int
         * currentByteColumn =
         * queryC.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES); int
         * currentByte = queryC.getInt(currentByteColumn);
         */
        /* update a column of a record */
        /*
         * for(int i =0;i<100;i++){ currentByte ++;
         * updateValues.put(BluetoothShare.CURRENT_BYTES, currentByte);
         * getContentResolver().update(contentUri1,updateValues,null,null); } }
         * }
         */
        /* query whole data base */
        /*
         * Cursor c = managedQuery(contentUri1, new String [] {"_id",
         * BluetoothShare.URI, BluetoothShare.STATUS,
         * BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES,
         * BluetoothShare._DATA, BluetoothShare.DIRECTION,
         * BluetoothShare.MIMETYPE, BluetoothShare.DESTINATION,
         * BluetoothShare.VISIBILITY, BluetoothShare.USER_CONFIRMATION,
         * BluetoothShare.TIMESTAMP}, null, null, null); Log.v(Constants.TAG,
         * "query " + contentUri1 +" get " + c.getCount()+" records");
         */
        /* delete a record */
        /*
         * Uri contentUri2 = Uri.parse(BluetoothShare.CONTENT_URI + "/" + 1);
         * getContentResolver().delete(contentUri2, null, null);
         */

    }

    public OnClickListener insertRecordListener = new OnClickListener() {
        public void onClick(View view) {

            String address = null;
            if (mAddressView.getText().length() != 0) {
                address = mAddressView.getText().toString();
                Log.v(Constants.TAG, "Send to address  " + address);
            }
            if (address == null) {
                address = "00:17:83:58:5D:CC";
            }

            Integer media = null;
            if (mMediaView.getText().length() != 0) {
                media = Integer.parseInt(mMediaView.getText().toString().trim());
                Log.v(Constants.TAG, "Send media no.  " + media);
            }
            if (media == null) {
                media = 1;
            }
            ContentValues values = new ContentValues();
            values.put(BluetoothShare.URI, "content://media/external/images/media/" + media);
            // values.put(BluetoothShare.DESTINATION, "FF:FF:FF:00:00:00");
            // baibai Q9 test
            // values.put(BluetoothShare.DESTINATION, "12:34:56:78:9A:BC");
            // java's nokia
            // values.put(BluetoothShare.DESTINATION, "00:1B:33:F0:58:FB");
            // Assis phone
            // values.put(BluetoothShare.DESTINATION, "00:17:E5:5D:74:F3");
            // Jackson E6
            // values.put(BluetoothShare.DESTINATION, "00:1A:1B:7F:1E:F0");
            // Baibai V950
            // values.put(BluetoothShare.DESTINATION, "00:17:83:58:5D:CC");
            // Baibai NSC1173
            // values.put(BluetoothShare.DESTINATION, "00:16:41:49:5B:F3");

            values.put(BluetoothShare.DESTINATION, address);

            values.put(BluetoothShare.DIRECTION, BluetoothShare.DIRECTION_OUTBOUND);

            Long ts = System.currentTimeMillis();
            values.put(BluetoothShare.TIMESTAMP, ts);

            Integer records = null;
            if (mInsertView.getText().length() != 0) {
                records = Integer.parseInt(mInsertView.getText().toString().trim());
                Log.v(Constants.TAG, "parseInt  " + records);
            }
            if (records == null) {
                records = 1;
            }
            for (int i = 0; i < records; i++) {
                Uri contentUri = getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
                Log.v(Constants.TAG, "insert contentUri: " + contentUri);
                currentInsert = contentUri.getPathSegments().get(1);
                Log.v(Constants.TAG, "currentInsert = " + currentInsert);
            }

        }
    };

    public OnClickListener deleteRecordListener = new OnClickListener() {
        public void onClick(View view) {
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/"
                    + mDeleteView.getText().toString());
            getContentResolver().delete(contentUri, null, null);
        }
    };

    public OnClickListener updateRecordListener = new OnClickListener() {
        public void onClick(View view) {
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/"
                    + mUpdateView.getText().toString());
            ContentValues updateValues = new ContentValues();
            // mCurrentByte ++;
            // updateValues.put(BluetoothShare.TOTAL_BYTES, "120000");
            // updateValues.put(BluetoothShare.CURRENT_BYTES, mCurrentByte);
            // updateValues.put(BluetoothShare.VISIBILITY,
            // BluetoothShare.VISIBILITY_HIDDEN);
            updateValues.put(BluetoothShare.USER_CONFIRMATION,
                    BluetoothShare.USER_CONFIRMATION_CONFIRMED);
            getContentResolver().update(contentUri, updateValues, null, null);
        }
    };

    public OnClickListener ackRecordListener = new OnClickListener() {
        public void onClick(View view) {
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/"
                    + mAckView.getText().toString());
            ContentValues updateValues = new ContentValues();
            // mCurrentByte ++;
            // updateValues.put(BluetoothShare.TOTAL_BYTES, "120000");
            // updateValues.put(BluetoothShare.CURRENT_BYTES, mCurrentByte);
            updateValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
            // updateValues.put(BluetoothShare.USER_CONFIRMATION,
            // BluetoothShare.USER_CONFIRMATION_CONFIRMED);
            getContentResolver().update(contentUri, updateValues, null, null);
        }
    };

    public OnClickListener deleteAllRecordListener = new OnClickListener() {
        public void onClick(View view) {
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "");
            getContentResolver().delete(contentUri, null, null);
        }
    };

    public OnClickListener startTcpServerListener = new OnClickListener() {
        public void onClick(View view) {
            server = new TestTcpServer();
            Thread server_thread = new Thread(server);
            server_thread.start();

        }
    };

    public OnClickListener notifyTcpServerListener = new OnClickListener() {
        public void onClick(View view) {
            final Thread notifyThread = new Thread() {
                public void run() {
                    synchronized (server) {
                        server.a = true;
                        server.notify();
                    }
                }

            };
            notifyThread.start();
        }

    };
}

/**
 * This class listens on OPUSH channel for incoming connection
 */
class TestTcpListener {

    private static final String TAG = "BtOppRfcommListener";

    private static final boolean D = Constants.DEBUG;

    private static final boolean V = Constants.VERBOSE;

    private volatile boolean mInterrupted;

    private Thread mSocketAcceptThread;

    private Handler mCallback;

    private static final int ACCEPT_WAIT_TIMEOUT = 5000;

    public static final int DEFAULT_OPP_CHANNEL = 12;

    public static final int MSG_INCOMING_BTOPP_CONNECTION = 100;

    private int mBtOppRfcommChannel = -1;

    public TestTcpListener() {
        this(DEFAULT_OPP_CHANNEL);
    }

    public TestTcpListener(int channel) {
        mBtOppRfcommChannel = channel;
    }

    public synchronized boolean start(Handler callback) {
        if (mSocketAcceptThread == null) {
            mCallback = callback;
            mSocketAcceptThread = new Thread(TAG) {
                ServerSocket mServerSocket;

                public void run() {
                    if (D) Log.d(TAG, "RfcommSocket listen thread starting");
                    try {
                        if (V)
                            Log.v(TAG, "Create server RfcommSocket on channel"
                                    + mBtOppRfcommChannel);
                        mServerSocket = new ServerSocket(6500, 1);
                    } catch (IOException e) {
                        Log.e(TAG, "Error listing on channel" + mBtOppRfcommChannel);
                        mInterrupted = true;
                    }
                    while (!mInterrupted) {
                        try {
                            mServerSocket.setSoTimeout(ACCEPT_WAIT_TIMEOUT);
                            Socket clientSocket = mServerSocket.accept();
                            if (clientSocket == null) {
                                if (V) Log.v(TAG, "incomming connection time out");
                            } else {
                                if (D) Log.d(TAG, "RfcommSocket connected!");
                                Log.d(TAG, "remote addr is "
                                        + clientSocket.getRemoteSocketAddress());
                                TestTcpTransport transport = new TestTcpTransport(clientSocket);
                                Message msg = Message.obtain();
                                msg.setTarget(mCallback);
                                msg.what = MSG_INCOMING_BTOPP_CONNECTION;
                                msg.obj = transport;
                                msg.sendToTarget();
                            }
                        } catch (SocketException e) {
                            Log.e(TAG, "Error accept connection " + e);
                        } catch (IOException e) {
                            Log.e(TAG, "Error accept connection " + e);
                        }

                        if (mInterrupted) {
                            Log.e(TAG, "socketAcceptThread thread was interrupted (2), exiting");
                        }
                    }
                    if (D) Log.d(TAG, "RfcommSocket listen thread finished");
                }
            };
            mInterrupted = false;
            mSocketAcceptThread.start();

        }
        return true;

    }

    public synchronized void stop() {
        if (mSocketAcceptThread != null) {
            if (D) Log.d(TAG, "stopping Connect Thread");
            mInterrupted = true;
            try {
                mSocketAcceptThread.interrupt();
                if (V) Log.v(TAG, "waiting for thread to terminate");
                mSocketAcceptThread.join();
                mSocketAcceptThread = null;
                mCallback = null;
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted waiting for Accept Thread to join");
            }
        }
    }

}

class TestTcpServer extends ServerRequestHandler implements Runnable {
    private static final String TAG = "ServerRequestHandler";

    private static final boolean V = Constants.VERBOSE;

    static final int port = 6500;

    public boolean a = false;

    // TextView serverStatus = null;
    public void run() {
        try {
            updateStatus("[server:] listen on port " + port);
            TestTcpSessionNotifier rsn = new TestTcpSessionNotifier(port);

            updateStatus("[server:] Now waiting for a client to connect");
            rsn.acceptAndOpen(this);
            updateStatus("[server:] A client is now connected");
        } catch (Exception ex) {
            updateStatus("[server:] Caught the error: " + ex);
        }
    }

    public TestTcpServer() {
        updateStatus("enter construtor of TcpServer");
    }

    public int onConnect(HeaderSet request, HeaderSet reply) {

        updateStatus("[server:] The client has created an OBEX session");
        /* sleep for 2000 ms to wait for the batch contains all ShareInfos */
        synchronized (this) {
            try {
                while (!a) {
                    wait(500);
                }
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted waiting for markBatchFailed");
            }
        }
        updateStatus("[server:] we accpet the seesion");
        return ResponseCodes.OBEX_HTTP_OK;
    }

    public int onPut(Operation op) {
        FileOutputStream fos = null;
        try {
            java.io.InputStream is = op.openInputStream();

            updateStatus("Got data bytes " + is.available() + " name "
                    + op.getReceivedHeader().getHeader(HeaderSet.NAME) + " type " + op.getType());

            File f = new File((String)op.getReceivedHeader().getHeader(HeaderSet.NAME));
            fos = new FileOutputStream(f);
            byte b[] = new byte[1000];
            int len;

            while (is.available() > 0 && (len = is.read(b)) > 0) {
                fos.write(b, 0, len);
            }

            fos.close();
            is.close();
            updateStatus("[server:] Wrote data to " + f.getAbsolutePath());
        } catch (Exception e) {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            e.printStackTrace();
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    public void onDisconnect(HeaderSet req, HeaderSet resp) {
        updateStatus("[server:] The client has disconnected the OBEX session");
    }

    public void updateStatus(String message) {
        Log.v(TAG, "\n" + message);
    }

    public void onAuthenticationFailure(byte[] userName) {
    }

    public int onSetPath(HeaderSet request, HeaderSet reply, boolean backup, boolean create) {

        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

    public int onDelete(HeaderSet request, HeaderSet reply) {
        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

    public int onGet(Operation op) {
        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

}

class TestTcpSessionNotifier {
    /* implements SessionNotifier */

    ServerSocket server = null;

    Socket conn = null;

    private static final String TAG = "TestTcpSessionNotifier";

    public TestTcpSessionNotifier(int port) throws IOException {
        server = new ServerSocket(port);
    }

    public ServerSession acceptAndOpen(ServerRequestHandler handler, Authenticator auth)
            throws IOException {
        try {
            conn = server.accept();

        } catch (Exception ex) {
            Log.v(TAG, "ex");
        }

        TestTcpTransport tt = new TestTcpTransport(conn);

        return new ServerSession((ObexTransport)tt, handler, auth);

    }

    public ServerSession acceptAndOpen(ServerRequestHandler handler) throws IOException {

        return acceptAndOpen(handler, null);

    }

}

class TestTcpTransport implements ObexTransport {

    Socket s = null;

    public TestTcpTransport(Socket s) {
        super();
        this.s = s;
    }

    public void close() throws IOException {
        s.close();
    }

    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    public InputStream openInputStream() throws IOException {
        return s.getInputStream();
    }

    public OutputStream openOutputStream() throws IOException {
        return s.getOutputStream();
    }

    public void connect() throws IOException {
        // TODO Auto-generated method stub

    }

    public void create() throws IOException {
        // TODO Auto-generated method stub

    }

    public void disconnect() throws IOException {
        // TODO Auto-generated method stub

    }

    public void listen() throws IOException {
        // TODO Auto-generated method stub

    }

    public boolean isConnected() throws IOException {
        return s.isConnected();
    }

    @Override
    public int getMaxTransmitPacketSize() {
        return -1;
    }

    @Override
    public int getMaxReceivePacketSize() {
        return -1;
    }

    @Override
    public boolean isSrmSupported() {
        // TODO: It should be possible to use SRM in TCP connections
        return false;
    }
}
