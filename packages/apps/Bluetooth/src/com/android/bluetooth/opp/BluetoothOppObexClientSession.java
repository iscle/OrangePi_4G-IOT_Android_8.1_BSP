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

import javax.obex.ClientOperation;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.ResponseCodes;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread;

/**
 * This class runs as an OBEX client
 */
public class BluetoothOppObexClientSession implements BluetoothOppObexSession {

    private static final String TAG = "BtOppObexClient";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    private ClientThread mThread;

    private ObexTransport mTransport;

    private Context mContext;

    private volatile boolean mInterrupted;

    private volatile boolean mWaitingForRemote;

    private Handler mCallback;

    public BluetoothOppObexClientSession(Context context, ObexTransport transport) {
        if (transport == null) {
            throw new NullPointerException("transport is null");
        }
        mContext = context;
        mTransport = transport;
    }

    public void start(Handler handler, int numShares) {
        if (D) Log.d(TAG, "Start!");
        mCallback = handler;
        mThread = new ClientThread(mContext, mTransport, numShares);
        mThread.start();
    }

    public void stop() {
        if (D) Log.d(TAG, "Stop!");
        if (mThread != null) {
            mInterrupted = true;
            try {
                mThread.interrupt();
                if (V) Log.v(TAG, "waiting for thread to terminate");
                mThread.join();
                mThread = null;
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted waiting for thread to join");
            }
        }
        NotificationManager nm =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(BluetoothOppNotification.NOTIFICATION_ID_PROGRESS);

        mCallback = null;
    }

    public void addShare(BluetoothOppShareInfo share) {
        mThread.addShare(share);
    }

    private static int readFully(InputStream is, byte[] buffer, int size) throws IOException {
        int done = 0;
        while (done < size) {
            int got = is.read(buffer, done, size - done);
            if (got <= 0) break;
            done += got;
        }
        return done;
    }

    private class ClientThread extends Thread {

        private static final int sSleepTime = 500;

        private Context mContext1;

        private BluetoothOppShareInfo mInfo;

        private volatile boolean waitingForShare;

        private ObexTransport mTransport1;

        private ClientSession mCs;

        private WakeLock wakeLock;

        private BluetoothOppSendFileInfo mFileInfo = null;

        private boolean mConnected = false;

        private int mNumShares;

        public ClientThread(Context context, ObexTransport transport, int initialNumShares) {
            super("BtOpp ClientThread");
            mContext1 = context;
            mTransport1 = transport;
            waitingForShare = true;
            mWaitingForRemote = false;
            mNumShares = initialNumShares;
            PowerManager pm = (PowerManager)mContext1.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }

        public void addShare(BluetoothOppShareInfo info) {
            mInfo = info;
            mFileInfo = processShareInfo();
            waitingForShare = false;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            if (V) Log.v(TAG, "acquire partial WakeLock");
            wakeLock.acquire();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e1) {
                if (V) Log.v(TAG, "Client thread was interrupted (1), exiting");
                mInterrupted = true;
            }
            if (!mInterrupted) {
                connect(mNumShares);
            }

            while (!mInterrupted) {
                if (!waitingForShare) {
                    doSend();
                } else {
                    try {
                        if (D) Log.d(TAG, "Client thread waiting for next share, sleep for "
                                    + sSleepTime);
                        Thread.sleep(sSleepTime);
                    } catch (InterruptedException e) {

                    }
                }
            }
            disconnect();

            if (wakeLock.isHeld()) {
                if (V) Log.v(TAG, "release partial WakeLock");
                wakeLock.release();
            }
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothOppObexSession.MSG_SESSION_COMPLETE;
            msg.obj = mInfo;
            msg.sendToTarget();

        }

        private void disconnect() {
            try {
                if (mCs != null) {
                    mCs.disconnect(null);
                }
                mCs = null;
                if (D) Log.d(TAG, "OBEX session disconnected");
            } catch (IOException e) {
                Log.w(TAG, "OBEX session disconnect error" + e);
            }
            try {
                if (mCs != null) {
                    if (D) Log.d(TAG, "OBEX session close mCs");
                    mCs.close();
                    if (D) Log.d(TAG, "OBEX session closed");
                    }
            } catch (IOException e) {
                Log.w(TAG, "OBEX session close error" + e);
            }
            if (mTransport1 != null) {
                try {
                    mTransport1.close();
                } catch (IOException e) {
                    Log.e(TAG, "mTransport.close error");
                }

            }
        }

        private void connect(int numShares) {
            if (D) Log.d(TAG, "Create ClientSession with transport " + mTransport1.toString());
            try {
                mCs = new ClientSession(mTransport1);
                mConnected = true;
            } catch (IOException e1) {
                Log.e(TAG, "OBEX session create error");
            }
            if (mConnected) {
                mConnected = false;
                HeaderSet hs = new HeaderSet();
                hs.setHeader(HeaderSet.COUNT, (long) numShares);
                synchronized (this) {
                    mWaitingForRemote = true;
                }
                try {
                    mCs.connect(hs);
                    if (D) Log.d(TAG, "OBEX session created");
                    mConnected = true;
                } catch (IOException e) {
                    Log.e(TAG, "OBEX session connect error");
                }
            }
            synchronized (this) {
                mWaitingForRemote = false;
            }
        }

        private void doSend() {

            int status = BluetoothShare.STATUS_SUCCESS;

            /* connection is established too fast to get first mInfo */
            while (mFileInfo == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    status = BluetoothShare.STATUS_CANCELED;
                }
            }
            if (!mConnected) {
                // Obex connection error
                status = BluetoothShare.STATUS_CONNECTION_ERROR;
            }
            if (status == BluetoothShare.STATUS_SUCCESS) {
                /* do real send */
                if (mFileInfo.mFileName != null) {
                    status = sendFile(mFileInfo);
                } else {
                    /* this is invalid request */
                    status = mFileInfo.mStatus;
                }
                waitingForShare = true;
            } else {
                Constants.updateShareStatus(mContext1, mInfo.mId, status);
            }

            Message msg = Message.obtain(mCallback);
            msg.what = (status == BluetoothShare.STATUS_SUCCESS) ?
                        BluetoothOppObexSession.MSG_SHARE_COMPLETE :
                        BluetoothOppObexSession.MSG_SESSION_ERROR;
            mInfo.mStatus = status;
            msg.obj = mInfo;
            msg.sendToTarget();
        }

        /*
         * Validate this ShareInfo
         */
        private BluetoothOppSendFileInfo processShareInfo() {
            if (V) Log.v(TAG, "Client thread processShareInfo() " + mInfo.mId);

            BluetoothOppSendFileInfo fileInfo = BluetoothOppUtility.getSendFileInfo(mInfo.mUri);
            if (fileInfo.mFileName == null || fileInfo.mLength == 0) {
                if (V) Log.v(TAG, "BluetoothOppSendFileInfo get invalid file");
                    Constants.updateShareStatus(mContext1, mInfo.mId, fileInfo.mStatus);

            } else {
                if (V) {
                    Log.v(TAG, "Generate BluetoothOppSendFileInfo:");
                    Log.v(TAG, "filename  :" + fileInfo.mFileName);
                    Log.v(TAG, "length    :" + fileInfo.mLength);
                    Log.v(TAG, "mimetype  :" + fileInfo.mMimetype);
                }

                ContentValues updateValues = new ContentValues();
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mInfo.mId);

                updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                updateValues.put(BluetoothShare.TOTAL_BYTES, fileInfo.mLength);
                updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);

                mContext1.getContentResolver().update(contentUri, updateValues, null, null);

            }
            return fileInfo;
        }

        private int sendFile(BluetoothOppSendFileInfo fileInfo) {
            boolean error = false;
            int responseCode = -1;
            long position = 0;
            int status = BluetoothShare.STATUS_SUCCESS;
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mInfo.mId);
            ContentValues updateValues;
            HeaderSet request;
            request = new HeaderSet();
            request.setHeader(HeaderSet.NAME, fileInfo.mFileName);
            request.setHeader(HeaderSet.TYPE, fileInfo.mMimetype);

            applyRemoteDeviceQuirks(request, mInfo.mDestination, fileInfo.mFileName);

            Constants.updateShareStatus(mContext1, mInfo.mId, BluetoothShare.STATUS_RUNNING);

            request.setHeader(HeaderSet.LENGTH, fileInfo.mLength);
            ClientOperation putOperation = null;
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                synchronized (this) {
                    mWaitingForRemote = true;
                }
                try {
                    if (V) Log.v(TAG, "put headerset for " + fileInfo.mFileName);
                    putOperation = (ClientOperation)mCs.put(request);
                } catch (IOException e) {
                    status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
                    Constants.updateShareStatus(mContext1, mInfo.mId, status);

                    Log.e(TAG, "Error when put HeaderSet ");
                    error = true;
                }
                synchronized (this) {
                    mWaitingForRemote = false;
                }

                if (!error) {
                    try {
                        if (V) Log.v(TAG, "openOutputStream " + fileInfo.mFileName);
                        outputStream = putOperation.openOutputStream();
                        inputStream = putOperation.openInputStream();
                    } catch (IOException e) {
                        status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
                        Constants.updateShareStatus(mContext1, mInfo.mId, status);
                        Log.e(TAG, "Error when openOutputStream");
                        error = true;
                    }
                }
                if (!error) {
                    updateValues = new ContentValues();
                    updateValues.put(BluetoothShare.CURRENT_BYTES, 0);
                    updateValues.put(BluetoothShare.STATUS, BluetoothShare.STATUS_RUNNING);
                    mContext1.getContentResolver().update(contentUri, updateValues, null, null);
                }

                if (!error) {
                    int readLength = 0;
                    long percent = 0;
                    long prevPercent = 0;
                    boolean okToProceed = false;
                    long timestamp = 0;
                    int outputBufferSize = putOperation.getMaxPacketSize();
                    byte[] buffer = new byte[outputBufferSize];
                    BufferedInputStream a = new BufferedInputStream(fileInfo.mInputStream, 0x4000);

                    if (!mInterrupted && (position != fileInfo.mLength)) {
                        readLength = readFully(a, buffer, outputBufferSize);

                        mCallback.sendMessageDelayed(mCallback
                                .obtainMessage(BluetoothOppObexSession.MSG_CONNECT_TIMEOUT),
                                BluetoothOppObexSession.SESSION_TIMEOUT);
                        synchronized (this) {
                            mWaitingForRemote = true;
                        }

                        // first packet will block here
                        outputStream.write(buffer, 0, readLength);

                        position += readLength;

                        if (position == fileInfo.mLength) {
                            // if file length is smaller than buffer size, only one packet
                            // so block point is here
                            outputStream.close();
                            outputStream = null;
                        }

                        /* check remote accept or reject */
                        responseCode = putOperation.getResponseCode();

                        mCallback.removeMessages(BluetoothOppObexSession.MSG_CONNECT_TIMEOUT);
                        synchronized (this) {
                            mWaitingForRemote = false;
                        }

                        if (responseCode == ResponseCodes.OBEX_HTTP_CONTINUE
                                || responseCode == ResponseCodes.OBEX_HTTP_OK) {
                            if (V) Log.v(TAG, "Remote accept");
                            okToProceed = true;
                            updateValues = new ContentValues();
                            updateValues.put(BluetoothShare.CURRENT_BYTES, position);
                            mContext1.getContentResolver().update(contentUri, updateValues, null,
                                    null);
                        } else {
                            Log.i(TAG, "Remote reject, Response code is " + responseCode);
                        }
                    }

                    while (!mInterrupted && okToProceed && (position < fileInfo.mLength)) {
                        if (V) timestamp = System.currentTimeMillis();

                        readLength = a.read(buffer, 0, outputBufferSize);
                        outputStream.write(buffer, 0, readLength);

                        /* check remote abort */
                        responseCode = putOperation.getResponseCode();
                        if (V) Log.v(TAG, "Response code is " + responseCode);
                        if (responseCode != ResponseCodes.OBEX_HTTP_CONTINUE
                                && responseCode != ResponseCodes.OBEX_HTTP_OK) {
                            /* abort happens */
                            okToProceed = false;
                        } else {
                            position += readLength;
                            if (V) {
                                Log.v(TAG, "Sending file position = " + position
                                        + " readLength " + readLength + " bytes took "
                                        + (System.currentTimeMillis() - timestamp) + " ms");
                            }
                            // Update the Progress Bar only if there is change in percentage
                            percent = position * 100 / fileInfo.mLength;
                            if (percent > prevPercent) {
                                updateValues = new ContentValues();
                                updateValues.put(BluetoothShare.CURRENT_BYTES, position);
                                mContext1.getContentResolver().update(contentUri, updateValues,
                                        null, null);
                                prevPercent = percent;
                            }
                        }
                    }

                    if (responseCode == ResponseCodes.OBEX_HTTP_FORBIDDEN
                            || responseCode == ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE) {
                        Log.i(TAG, "Remote reject file " + fileInfo.mFileName + " length "
                                + fileInfo.mLength);
                        status = BluetoothShare.STATUS_FORBIDDEN;
                    } else if (responseCode == ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE) {
                        Log.i(TAG, "Remote reject file type " + fileInfo.mMimetype);
                        status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                    } else if (!mInterrupted && position == fileInfo.mLength) {
                        Log.i(TAG, "SendFile finished send out file " + fileInfo.mFileName
                                + " length " + fileInfo.mLength);
                    } else {
                        error = true;
                        status = BluetoothShare.STATUS_CANCELED;
                        putOperation.abort();
                        /* interrupted */
                        Log.i(TAG, "SendFile interrupted when send out file " + fileInfo.mFileName
                                + " at " + position + " of " + fileInfo.mLength);
                    }
                }
            } catch (IOException e) {
                handleSendException(e.toString());
            } catch (NullPointerException e) {
                handleSendException(e.toString());
            } catch (IndexOutOfBoundsException e) {
                handleSendException(e.toString());
            } finally {
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }

                    // Close InputStream and remove SendFileInfo from map
                    BluetoothOppUtility.closeSendFileInfo(mInfo.mUri);
                    if (!error) {
                        responseCode = putOperation.getResponseCode();
                        if (responseCode != -1) {
                            if (V) Log.v(TAG, "Get response code " + responseCode);
                            if (responseCode != ResponseCodes.OBEX_HTTP_OK) {
                                Log.i(TAG, "Response error code is " + responseCode);
                                status = BluetoothShare.STATUS_UNHANDLED_OBEX_CODE;
                                if (responseCode == ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE) {
                                    status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                                }
                                if (responseCode == ResponseCodes.OBEX_HTTP_FORBIDDEN
                                        || responseCode == ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE) {
                                    status = BluetoothShare.STATUS_FORBIDDEN;
                                }
                            }
                        } else {
                            // responseCode is -1, which means connection error
                            status = BluetoothShare.STATUS_CONNECTION_ERROR;
                        }
                    }

                    Constants.updateShareStatus(mContext1, mInfo.mId, status);

                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (putOperation != null) {
                        putOperation.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error when closing stream after send");

                    // Socket has been closed due to the response timeout in the framework,
                    // mark the transfer as failure.
                    if (position != fileInfo.mLength) {
                       status = BluetoothShare.STATUS_FORBIDDEN;
                       Constants.updateShareStatus(mContext1, mInfo.mId, status);
                    }
                }
            }
            return status;
        }

        private void handleSendException(String exception) {
            Log.e(TAG, "Error when sending file: " + exception);
            // Update interrupted outbound content resolver entry when
            // error during transfer.
            Constants.updateShareStatus(mContext1, mInfo.mId,
                BluetoothShare.STATUS_OBEX_DATA_ERROR);
            mCallback.removeMessages(BluetoothOppObexSession.MSG_CONNECT_TIMEOUT);
        }

        @Override
        public void interrupt() {
            super.interrupt();
            synchronized (this) {
                if (mWaitingForRemote) {
                    if (V) Log.v(TAG, "Interrupted when waitingForRemote");
                    try {
                        mTransport1.close();
                    } catch (IOException e) {
                        Log.e(TAG, "mTransport.close error");
                    }
                    Message msg = Message.obtain(mCallback);
                    msg.what = BluetoothOppObexSession.MSG_SHARE_INTERRUPTED;
                    if (mInfo != null) {
                        msg.obj = mInfo;
                    }
                    msg.sendToTarget();
                }
            }
        }
    }

    public static void applyRemoteDeviceQuirks(HeaderSet request, String address, String filename) {
        if (address == null) {
            return;
        }
        if (address.startsWith("00:04:48")) {
            // Poloroid Pogo
            // Rejects filenames with more than one '.'. Rename to '_'.
            // for example: 'a.b.jpg' -> 'a_b.jpg'
            //              'abc.jpg' NOT CHANGED
            char[] c = filename.toCharArray();
            boolean firstDot = true;
            boolean modified = false;
            for (int i = c.length - 1; i >= 0; i--) {
                if (c[i] == '.') {
                    if (!firstDot) {
                        modified = true;
                        c[i] = '_';
                    }
                    firstDot = false;
                }
            }

            if (modified) {
                String newFilename = new String(c);
                request.setHeader(HeaderSet.NAME, newFilename);
                Log.i(TAG, "Sending file \"" + filename + "\" as \"" + newFilename +
                        "\" to workaround Poloroid filename quirk");
            }
        }
    }

    public void unblock() {
        // Not used for client case
    }

}
