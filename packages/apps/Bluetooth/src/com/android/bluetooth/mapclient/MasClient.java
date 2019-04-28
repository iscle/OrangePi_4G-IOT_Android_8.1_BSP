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
import android.bluetooth.BluetoothSocket;
import android.bluetooth.SdpMasRecord;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.BluetoothObexTransport;
import com.android.internal.util.StateMachine;

import java.io.IOException;
import java.lang.ref.WeakReference;

import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.ResponseCodes;
/* MasClient is a one time use connection to a server defined by the SDP record passed in at
 * construction.  After use shutdown() must be called to properly clean up.
 */
public class MasClient {
    private static final int CONNECT = 0;
    private static final int DISCONNECT = 1;
    private static final int REQUEST = 2;
    private static final String TAG = "MasClient";
    private static final boolean DBG = MapClientService.DBG;
    private static final boolean VDBG = MapClientService.VDBG;
    private static final byte[] BLUETOOTH_UUID_OBEX_MAS = new byte[]{
            (byte) 0xbb, 0x58, 0x2b, 0x40, 0x42, 0x0c, 0x11, (byte) 0xdb, (byte) 0xb0, (byte) 0xde,
            0x08, 0x00, 0x20, 0x0c, (byte) 0x9a, 0x66
    };
    private static final byte OAP_TAGID_MAP_SUPPORTED_FEATURES = 0x29;
    private static final int MAP_FEATURE_NOTIFICATION_REGISTRATION = 0x00000001;
    private static final int MAP_SUPPORTED_FEATURES = MAP_FEATURE_NOTIFICATION_REGISTRATION;

    private final StateMachine mCallback;
    private Handler mHandler;
    private BluetoothSocket mSocket;
    private BluetoothObexTransport mTransport;
    private BluetoothDevice mRemoteDevice;
    private ClientSession mSession;
    private HandlerThread thread;
    private boolean mConnected = false;
    SdpMasRecord mSdpMasRecord;

    public MasClient(BluetoothDevice remoteDevice,
            StateMachine callback, SdpMasRecord sdpMasRecord) {
        if (remoteDevice == null) {
            throw new NullPointerException("Obex transport is null");
        }
        mRemoteDevice = remoteDevice;
        mCallback = callback;
        mSdpMasRecord = sdpMasRecord;
        thread = new HandlerThread("Client");
        thread.start();
        /* This will block until the looper have started, hence it will be safe to use it,
           when the constructor completes */
        Looper looper = thread.getLooper();
        mHandler = new MasClientHandler(looper, this);

        mHandler.obtainMessage(CONNECT).sendToTarget();
    }

    private void connect() {
        try {
            if (DBG) {
                Log.d(TAG, "Connecting to OBEX on RFCOM channel "
                        + mSdpMasRecord.getRfcommCannelNumber());
            }
            mSocket = mRemoteDevice.createRfcommSocket(mSdpMasRecord.getRfcommCannelNumber());
            Log.d(TAG, mRemoteDevice.toString() + "Socket: " + mSocket.toString());
            mSocket.connect();
            mTransport = new BluetoothObexTransport(mSocket);

            mSession = new ClientSession(mTransport);
            HeaderSet headerset = new HeaderSet();
            headerset.setHeader(HeaderSet.TARGET, BLUETOOTH_UUID_OBEX_MAS);
            ObexAppParameters oap = new ObexAppParameters();

            oap.add(OAP_TAGID_MAP_SUPPORTED_FEATURES,
                    MAP_SUPPORTED_FEATURES);

            oap.addToHeaderSet(headerset);

            headerset = mSession.connect(headerset);
            Log.d(TAG, "Connection results" + headerset.getResponseCode());

            if (headerset.getResponseCode() == ResponseCodes.OBEX_HTTP_OK) {
                if (DBG) Log.d(TAG, "Connection Successful");
                mConnected = true;
                mCallback.obtainMessage(
                        MceStateMachine.MSG_MAS_CONNECTED).sendToTarget();
            } else {
                disconnect();
            }

        } catch (IOException e) {
            Log.e(TAG, "Caught an exception " + e.toString());
            disconnect();
        }
    }

    private void disconnect() {
        if (mSession != null) {
            try {
                mSession.disconnect(null);
            } catch (IOException e) {
                Log.e(TAG, "Caught an exception while disconnecting:" + e.toString());
            }

            try {
                mSession.close();
            } catch (IOException e) {
                Log.e(TAG, "Caught an exception while closing:" + e.toString());
            }
        }

        mConnected = false;
        mCallback.obtainMessage(MceStateMachine.MSG_MAS_DISCONNECTED).sendToTarget();
    }

    private void executeRequest(Request request) {
        try {
            request.execute(mSession);
            mCallback.obtainMessage(MceStateMachine.MSG_MAS_REQUEST_COMPLETED,
                    request).sendToTarget();
        } catch (IOException e) {
            if (DBG) Log.d(TAG, "Request failed: " + request);
            // Disconnect to cleanup.
            disconnect();
        }
    }

    public boolean makeRequest(Request request) {
        if (DBG) Log.d(TAG, "makeRequest called with: " + request);

        boolean status = mHandler.sendMessage(mHandler.obtainMessage(REQUEST, request));
        if (!status) {
            Log.e(TAG, "Adding messages failed, state: " + mConnected);
            return false;
        }
        return true;
    }

    public void shutdown() {
        mHandler.obtainMessage(DISCONNECT).sendToTarget();
        thread.quitSafely();
    }

    public enum CharsetType {
        NATIVE, UTF_8;
    }

    private static class MasClientHandler extends Handler {
        WeakReference<MasClient> mInst;

        MasClientHandler(Looper looper, MasClient inst) {
            super(looper);
            mInst = new WeakReference<>(inst);
        }

        @Override
        public void handleMessage(Message msg) {
            MasClient inst = mInst.get();
            if (!inst.mConnected && msg.what != CONNECT) {
                Log.w(TAG, "Cannot execute " + msg + " when not CONNECTED.");
                return;
            }

            switch (msg.what) {
                case CONNECT:
                    inst.connect();
                    break;

                case DISCONNECT:
                    inst.disconnect();
                    break;

                case REQUEST:
                    inst.executeRequest((Request) msg.obj);
                    break;
            }
        }
    }

}
