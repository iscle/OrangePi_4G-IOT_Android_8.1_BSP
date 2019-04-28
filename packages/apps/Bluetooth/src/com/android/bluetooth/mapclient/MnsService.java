/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.bluetooth.mapclient;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import com.android.bluetooth.BluetoothObexTransport;
import com.android.bluetooth.IObexConnectionHandler;
import com.android.bluetooth.ObexServerSockets;
import com.android.bluetooth.sdp.SdpManager;

import java.io.IOException;

import javax.obex.ServerSession;

class MnsService {
    static final int MSG_EVENT = 1;
    /* for Client */
    static final int EVENT_REPORT = 1001;
    private static final String TAG = "MnsService";
    private static final Boolean DBG = MapClientService.DBG;
    private static final Boolean VDBG = MapClientService.VDBG;
    /* MAP version 1.1 */
    private static final int MNS_VERSION = 0x0101;
    /* MNS features: Notification Feature */
    private static final int MNS_FEATURE_BITS = 0x0002;
    /* these are shared across instances */
    static private SocketAcceptor mAcceptThread = null;
    static private Handler mSessionHandler = null;
    static private BluetoothServerSocket mServerSocket = null;
    static private ObexServerSockets mServerSockets = null;

    static private MapClientService mContext;
    private volatile boolean mShutdown = false;         // Used to interrupt socket accept thread
    private int mSdpHandle = -1;

    MnsService(MapClientService context) {
        if (VDBG) Log.v(TAG, "MnsService()");
        mContext = context;
        mAcceptThread = new SocketAcceptor();
        mServerSockets = ObexServerSockets.create(mAcceptThread);
        SdpManager sdpManager = SdpManager.getDefaultManager();
        if (sdpManager == null) {
            Log.e(TAG, "SdpManager is null");
            return;
        }
        mSdpHandle = sdpManager.createMapMnsRecord("MAP Message Notification Service",
                mServerSockets.getRfcommChannel(), -1, MNS_VERSION, MNS_FEATURE_BITS);
    }

    void stop() {
        if (VDBG) Log.v(TAG, "stop()");
        mShutdown = true;
        cleanUpSdpRecord();
        if (mServerSockets != null) {
            mServerSockets.shutdown(false);
            mServerSockets = null;
        }
    }

    private void cleanUpSdpRecord() {
        if (mSdpHandle < 0) {
            Log.e(TAG, "cleanUpSdpRecord, SDP record never created");
            return;
        }
        int sdpHandle = mSdpHandle;
        mSdpHandle = -1;
        SdpManager sdpManager = SdpManager.getDefaultManager();
        if (sdpManager == null) {
            Log.e(TAG, "cleanUpSdpRecord failed, sdpManager is null, sdpHandle=" + sdpHandle);
            return;
        }
        Log.i(TAG, "cleanUpSdpRecord, mSdpHandle=" + sdpHandle);
        if (!sdpManager.removeSdpRecord(sdpHandle)) {
            Log.e(TAG, "cleanUpSdpRecord, removeSdpRecord failed, sdpHandle=" + sdpHandle);
        }
    }

    private class SocketAcceptor implements IObexConnectionHandler {

        private boolean mInterrupted = false;

        /**
         * Called when an unrecoverable error occurred in an accept thread.
         * Close down the server socket, and restart.
         * TODO: Change to message, to call start in correct context.
         */
        @Override
        public synchronized void onAcceptFailed() {
            Log.e(TAG, "OnAcceptFailed");
            mServerSockets = null; // Will cause a new to be created when calling start.
            if (mShutdown) {
                Log.e(TAG, "Failed to accept incomming connection - " + "shutdown");
            }
        }

        @Override
        public synchronized boolean onConnect(BluetoothDevice device, BluetoothSocket socket) {
            if (DBG) Log.d(TAG, "onConnect" + device + " SOCKET: " + socket);
            /* Signal to the service that we have received an incoming connection.*/
            MnsObexServer srv = new MnsObexServer(
                    mContext.mMceStateMachine, mServerSockets);
            BluetoothObexTransport transport = new BluetoothObexTransport(socket);
            try {
                new ServerSession(transport, srv, null);
                return true;
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                return false;
            }
        }
    }
}
