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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.webkit.MimeTypeMap;

import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;
import javax.obex.ServerSession;

import com.android.bluetooth.BluetoothObexTransport;
import com.android.bluetooth.ObexServerSockets;

/**
 * This class runs as an OBEX server
 */
public class BluetoothOppObexServerSession extends ServerRequestHandler implements
        BluetoothOppObexSession {

    private static final String TAG = "BtOppObexServer";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    private ObexTransport mTransport;

    private Context mContext;

    private Handler mCallback = null;

    /* status when server is blocking for user/auto confirmation */
    private boolean mServerBlocking = true;

    /* the current transfer info */
    private BluetoothOppShareInfo mInfo;

    /* info id when we insert the record */
    private int mLocalShareInfoId;

    private int mAccepted = BluetoothShare.USER_CONFIRMATION_PENDING;

    private boolean mInterrupted = false;

    private ServerSession mSession;

    private long mTimestamp;

    private BluetoothOppReceiveFileInfo mFileInfo;

    private WakeLock mPartialWakeLock;

    boolean mTimeoutMsgSent = false;

    private ObexServerSockets mServerSocket;

    public BluetoothOppObexServerSession(
            Context context, ObexTransport transport, ObexServerSockets serverSocket) {
        mContext = context;
        mTransport = transport;
        mServerSocket = serverSocket;
        PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mPartialWakeLock.setReferenceCounted(false);
    }

    public void unblock() {
        mServerBlocking = false;
    }

    /**
     * Called when connection is accepted from remote, to retrieve the first
     * Header then wait for user confirmation
     */
    public void preStart() {
        try {
            if (D) Log.d(TAG, "Create ServerSession with transport " + mTransport.toString());
            mSession = new ServerSession(mTransport, this, null);
        } catch (IOException e) {
            Log.e(TAG, "Create server session error" + e);
        }
    }

    /**
     * Called from BluetoothOppTransfer to start the "Transfer"
     */
    public void start(Handler handler, int numShares) {
        if (D) Log.d(TAG, "Start!");
        mCallback = handler;

    }

    /**
     * Called from BluetoothOppTransfer to cancel the "Transfer" Otherwise,
     * server should end by itself.
     */
    public void stop() {
        /*
         * TODO now we implement in a tough way, just close the socket.
         * maybe need nice way
         */
        if (D) Log.d(TAG, "Stop!");
        mInterrupted = true;
        if (mSession != null) {
            try {
                mSession.close();
                mTransport.close();
            } catch (IOException e) {
                Log.e(TAG, "close mTransport error" + e);
            }
        }
        mCallback = null;
        mSession = null;
    }

    public void addShare(BluetoothOppShareInfo info) {
        if (D) Log.d(TAG, "addShare for id " + info.mId);
        mInfo = info;
        mFileInfo = processShareInfo();
    }

    @Override
    public int onPut(Operation op) {
        if (D) Log.d(TAG, "onPut " + op.toString());
        HeaderSet request;
        String name, mimeType;
        Long length;

        int obexResponse = ResponseCodes.OBEX_HTTP_OK;

        /**
         * For multiple objects, reject further objects after user deny the
         * first one
         */
        if (mAccepted == BluetoothShare.USER_CONFIRMATION_DENIED) {
            return ResponseCodes.OBEX_HTTP_FORBIDDEN;
        }

        String destination;
        if (mTransport instanceof BluetoothObexTransport) {
            destination = ((BluetoothObexTransport)mTransport).getRemoteAddress();
        } else {
            destination = "FF:FF:FF:00:00:00";
        }
        boolean isWhitelisted = BluetoothOppManager.getInstance(mContext).
                isWhitelisted(destination);

        try {
            boolean pre_reject = false;

            request = op.getReceivedHeader();
            if (V) Constants.logHeader(request);
            name = (String)request.getHeader(HeaderSet.NAME);
            length = (Long)request.getHeader(HeaderSet.LENGTH);
            mimeType = (String)request.getHeader(HeaderSet.TYPE);

            if (length == 0) {
                if (D) Log.w(TAG, "length is 0, reject the transfer");
                pre_reject = true;
                obexResponse = ResponseCodes.OBEX_HTTP_LENGTH_REQUIRED;
            }

            if (name == null || name.equals("")) {
                if (D) Log.w(TAG, "name is null or empty, reject the transfer");
                pre_reject = true;
                obexResponse = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }

            if (!pre_reject) {
                /* first we look for Mimetype in Android map */
                String extension, type;
                int dotIndex = name.lastIndexOf(".");
                if (dotIndex < 0 && mimeType == null) {
                    if (D) Log.w(TAG, "There is no file extension or mime type," +
                            "reject the transfer");
                    pre_reject = true;
                    obexResponse = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                } else {
                    extension = name.substring(dotIndex + 1).toLowerCase();
                    MimeTypeMap map = MimeTypeMap.getSingleton();
                    type = map.getMimeTypeFromExtension(extension);
                    if (V) Log.v(TAG, "Mimetype guessed from extension " + extension + " is " + type);
                    if (type != null) {
                        mimeType = type;

                    } else {
                        if (mimeType == null) {
                            if (D) Log.w(TAG, "Can't get mimetype, reject the transfer");
                            pre_reject = true;
                            obexResponse = ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE;
                        }
                    }
                    if (mimeType != null) {
                        mimeType = mimeType.toLowerCase();
                    }
                }
            }

            // Reject policy: anything outside the "white list" plus unspecified
            // MIME Types. Also reject everything in the "black list".
            if (!pre_reject
                    && (mimeType == null
                            || (!isWhitelisted && !Constants.mimeTypeMatches(mimeType,
                                    Constants.ACCEPTABLE_SHARE_INBOUND_TYPES))
                            || Constants.mimeTypeMatches(mimeType,
                                    Constants.UNACCEPTABLE_SHARE_INBOUND_TYPES))) {
                if (D) Log.w(TAG, "mimeType is null or in unacceptable list, reject the transfer");
                pre_reject = true;
                obexResponse = ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE;
            }

            if (pre_reject && obexResponse != ResponseCodes.OBEX_HTTP_OK) {
                // some bad implemented client won't send disconnect
                return obexResponse;
            }

        } catch (IOException e) {
            Log.e(TAG, "get getReceivedHeaders error " + e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        ContentValues values = new ContentValues();

        values.put(BluetoothShare.FILENAME_HINT, name);

        values.put(BluetoothShare.TOTAL_BYTES, length);

        values.put(BluetoothShare.MIMETYPE, mimeType);

        values.put(BluetoothShare.DESTINATION, destination);

        values.put(BluetoothShare.DIRECTION, BluetoothShare.DIRECTION_INBOUND);
        values.put(BluetoothShare.TIMESTAMP, mTimestamp);

        /** It's not first put if !serverBlocking, so we auto accept it */
        if (!mServerBlocking && (mAccepted == BluetoothShare.USER_CONFIRMATION_CONFIRMED ||
                mAccepted == BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED)) {
            values.put(BluetoothShare.USER_CONFIRMATION,
                    BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED);
        }

        if (isWhitelisted) {
            values.put(BluetoothShare.USER_CONFIRMATION,
                    BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED);

        }

        Uri contentUri = mContext.getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
        mLocalShareInfoId = Integer.parseInt(contentUri.getPathSegments().get(1));

        if (V) Log.v(TAG, "insert contentUri: " + contentUri);
        if (V) Log.v(TAG, "mLocalShareInfoId = " + mLocalShareInfoId);

        synchronized (this) {
            mPartialWakeLock.acquire();
            mServerBlocking = true;
            try {

                while (mServerBlocking) {
                    wait(1000);
                    if (mCallback != null && !mTimeoutMsgSent) {
                        mCallback.sendMessageDelayed(mCallback
                                .obtainMessage(BluetoothOppObexSession.MSG_CONNECT_TIMEOUT),
                                BluetoothOppObexSession.SESSION_TIMEOUT);
                        mTimeoutMsgSent = true;
                        if (V) Log.v(TAG, "MSG_CONNECT_TIMEOUT sent");
                    }
                }
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted in onPut blocking");
            }
        }
        if (D) Log.d(TAG, "Server unblocked ");
        synchronized (this) {
            if (mCallback != null && mTimeoutMsgSent) {
                mCallback.removeMessages(BluetoothOppObexSession.MSG_CONNECT_TIMEOUT);
            }
        }

        /* we should have mInfo now */

        /*
         * TODO check if this mInfo match the one that we insert before server
         * blocking? just to make sure no error happens
         */
        if (mInfo.mId != mLocalShareInfoId) {
            Log.e(TAG, "Unexpected error!");
        }
        mAccepted = mInfo.mConfirm;

        if (V) Log.v(TAG, "after confirm: userAccepted=" + mAccepted);
        int status = BluetoothShare.STATUS_SUCCESS;

        if (mAccepted == BluetoothShare.USER_CONFIRMATION_CONFIRMED
                || mAccepted == BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED
                || mAccepted == BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED) {
            /* Confirm or auto-confirm */

            if (mFileInfo.mFileName == null) {
                status = mFileInfo.mStatus;
                /* TODO need to check if this line is correct */
                mInfo.mStatus = mFileInfo.mStatus;
                Constants.updateShareStatus(mContext, mInfo.mId, status);
                obexResponse = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;

            }

            if (mFileInfo.mFileName != null) {

                ContentValues updateValues = new ContentValues();
                contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mInfo.mId);
                updateValues.put(BluetoothShare._DATA, mFileInfo.mFileName);
                updateValues.put(BluetoothShare.STATUS, BluetoothShare.STATUS_RUNNING);
                mContext.getContentResolver().update(contentUri, updateValues, null, null);

                status = receiveFile(mFileInfo, op);
                /*
                 * TODO map status to obex response code
                 */
                if (status != BluetoothShare.STATUS_SUCCESS) {
                    obexResponse = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                }
                Constants.updateShareStatus(mContext, mInfo.mId, status);
            }

            if (status == BluetoothShare.STATUS_SUCCESS) {
                Message msg = Message.obtain(mCallback, BluetoothOppObexSession.MSG_SHARE_COMPLETE);
                msg.obj = mInfo;
                msg.sendToTarget();
            } else {
                if (mCallback != null) {
                    Message msg = Message.obtain(mCallback,
                            BluetoothOppObexSession.MSG_SESSION_ERROR);
                    mInfo.mStatus = status;
                    msg.obj = mInfo;
                    msg.sendToTarget();
                }
                try {
                    if (mFileInfo != null && mFileInfo.mFileName != null) {
                        if (V) Log.d(TAG, "Deleting not completed file");
                        new File(mFileInfo.mFileName).delete();
                    }
                } catch (Exception e) {
                    if (V) {
                        e.printStackTrace();
                        Log.e(TAG, "exception while deleting file");
                    }
                }
            }
        } else if (mAccepted == BluetoothShare.USER_CONFIRMATION_DENIED
                || mAccepted == BluetoothShare.USER_CONFIRMATION_TIMEOUT) {
            /* user actively deny the inbound transfer */
            /*
             * Note There is a question: what's next if user deny the first obj?
             * Option 1 :continue prompt for next objects
             * Option 2 :reject next objects and finish the session
             * Now we take option 2:
             */

            Log.i(TAG, "Rejected incoming request");
            if (mFileInfo.mFileName != null) {
                try {
                    mFileInfo.mOutputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "error close file stream");
                }
                new File(mFileInfo.mFileName).delete();
            }
            // set status as local cancel
            status = BluetoothShare.STATUS_CANCELED;
            Constants.updateShareStatus(mContext, mInfo.mId, status);
            obexResponse = ResponseCodes.OBEX_HTTP_FORBIDDEN;

            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothOppObexSession.MSG_SHARE_INTERRUPTED;
            mInfo.mStatus = status;
            msg.obj = mInfo;
            msg.sendToTarget();
        }
        return obexResponse;
    }

    private int receiveFile(BluetoothOppReceiveFileInfo fileInfo, Operation op) {
        /*
         * implement receive file
         */
        int status = -1;
        BufferedOutputStream bos = null;

        InputStream is = null;
        boolean error = false;
        try {
            is = op.openInputStream();
        } catch (IOException e1) {
            Log.e(TAG, "Error when openInputStream");
            status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
            error = true;
        }

        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mInfo.mId);

        if (!error) {
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare._DATA, fileInfo.mFileName);
            mContext.getContentResolver().update(contentUri, updateValues, null, null);
        }

        long position = 0;
        long percent = 0;
        long prevPercent = 0;

        if (!error) {
            bos = new BufferedOutputStream(fileInfo.mOutputStream, 0x10000);
        }

        if (!error) {
            int outputBufferSize = op.getMaxPacketSize();
            byte[] b = new byte[outputBufferSize];
            int readLength = 0;
            long timestamp = 0;
            try {
                while ((!mInterrupted) && (position != fileInfo.mLength)) {

                    if (V) timestamp = System.currentTimeMillis();

                    readLength = is.read(b);

                    if (readLength == -1) {
                        if (D) Log.d(TAG, "Receive file reached stream end at position" + position);
                        break;
                    }

                    bos.write(b, 0, readLength);
                    position += readLength;
                    percent = position * 100 / fileInfo.mLength;

                    if (V) {
                        Log.v(TAG, "Receive file position = " + position + " readLength "
                                + readLength + " bytes took "
                                + (System.currentTimeMillis() - timestamp) + " ms");
                    }

                    // Update the Progress Bar only if there is change in percentage
                    if (percent > prevPercent) {
                        ContentValues updateValues = new ContentValues();
                        updateValues.put(BluetoothShare.CURRENT_BYTES, position);
                        mContext.getContentResolver().update(contentUri, updateValues, null, null);
                        prevPercent = percent;
                    }
                }
            } catch (IOException e1) {
                Log.e(TAG, "Error when receiving file: " + e1);
                /* OBEX Abort packet received from remote device */
                if ("Abort Received".equals(e1.getMessage())) {
                    status = BluetoothShare.STATUS_CANCELED;
                } else {
                    status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
                }
                error = true;
            }
        }

        if (mInterrupted) {
            if (D) Log.d(TAG, "receiving file interrupted by user.");
            status = BluetoothShare.STATUS_CANCELED;
        } else {
            if (position == fileInfo.mLength) {
                if (D) Log.d(TAG, "Receiving file completed for " + fileInfo.mFileName);
                status = BluetoothShare.STATUS_SUCCESS;
            } else {
                if (D) Log.d(TAG, "Reading file failed at " + position + " of " + fileInfo.mLength);
                if (status == -1) {
                    status = BluetoothShare.STATUS_UNKNOWN_ERROR;
                }
            }
        }

        if (bos != null) {
            try {
                bos.close();
            } catch (IOException e) {
                Log.e(TAG, "Error when closing stream after send");
            }
        }
        return status;
    }

    private BluetoothOppReceiveFileInfo processShareInfo() {
        if (D) Log.d(TAG, "processShareInfo() " + mInfo.mId);
        BluetoothOppReceiveFileInfo fileInfo = BluetoothOppReceiveFileInfo.generateFileInfo(
                mContext, mInfo.mId);
        if (V) {
            Log.v(TAG, "Generate BluetoothOppReceiveFileInfo:");
            Log.v(TAG, "filename  :" + fileInfo.mFileName);
            Log.v(TAG, "length    :" + fileInfo.mLength);
            Log.v(TAG, "status    :" + fileInfo.mStatus);
        }
        return fileInfo;
    }

    @Override
    public int onConnect(HeaderSet request, HeaderSet reply) {

        if (D) Log.d(TAG, "onConnect");
        if (V) Constants.logHeader(request);
        Long objectCount = null;
        try {
            byte[] uuid = (byte[])request.getHeader(HeaderSet.TARGET);
            if (V) Log.v(TAG, "onConnect(): uuid =" + Arrays.toString(uuid));
            if(uuid != null) {
                 return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }

            objectCount = (Long) request.getHeader(HeaderSet.COUNT);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        String destination;
        if (mTransport instanceof BluetoothObexTransport) {
            destination = ((BluetoothObexTransport)mTransport).getRemoteAddress();
        } else {
            destination = "FF:FF:FF:00:00:00";
        }
        boolean isHandover = BluetoothOppManager.getInstance(mContext).
                isWhitelisted(destination);
        if (isHandover) {
            // Notify the handover requester file transfer has started
            Intent intent = new Intent(Constants.ACTION_HANDOVER_STARTED);
            if (objectCount != null) {
                intent.putExtra(Constants.EXTRA_BT_OPP_OBJECT_COUNT, objectCount.intValue());
            } else {
                intent.putExtra(Constants.EXTRA_BT_OPP_OBJECT_COUNT,
                        Constants.COUNT_HEADER_UNAVAILABLE);
            }
            intent.putExtra(Constants.EXTRA_BT_OPP_ADDRESS, destination);
            mContext.sendBroadcast(intent, Constants.HANDOVER_STATUS_PERMISSION);
        }
        mTimestamp = System.currentTimeMillis();
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onDisconnect(HeaderSet req, HeaderSet resp) {
        if (D) Log.d(TAG, "onDisconnect");
        resp.responseCode = ResponseCodes.OBEX_HTTP_OK;
    }

    private synchronized void releaseWakeLocks() {
        if (mPartialWakeLock.isHeld()) {
            mPartialWakeLock.release();
        }
    }

    @Override
    public void onClose() {
        if (D) Log.d(TAG, "onClose");
        releaseWakeLocks();

        if (mServerSocket != null) {
            if (D) Log.d(TAG, "prepareForNewConnect");
            mServerSocket.prepareForNewConnect();
        }

        NotificationManager nm =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(BluetoothOppNotification.NOTIFICATION_ID_PROGRESS);

        /* onClose could happen even before start() where mCallback is set */
        if (mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothOppObexSession.MSG_SESSION_COMPLETE;
            msg.obj = mInfo;
            msg.sendToTarget();
        }
    }
}
