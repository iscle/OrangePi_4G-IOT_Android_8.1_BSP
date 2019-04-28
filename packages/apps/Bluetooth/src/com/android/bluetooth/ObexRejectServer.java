/*
* Copyright (C) 2015 Samsung System LSI
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

package com.android.bluetooth;

import java.io.IOException;

import javax.obex.HeaderSet;
import javax.obex.ServerRequestHandler;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * A simple ObexServer used to handle connection rejection in two cases:
 *  - A profile cannot handle a new connection, as it is already connected to another device.
 *  - The user rejected access to the resources needed by the profile.
 *
 * Will reject the OBEX connection, start a timer, and at timeout close the socket.
 */
public class ObexRejectServer extends ServerRequestHandler implements Callback {

    private static final String TAG = "ObexRejectServer";
    private static final boolean V = true;
    private final int mResult;
    private final HandlerThread mHandlerThread;
    private final Handler mMessageHandler;
    private final static int MSG_ID_TIMEOUT = 0x01;
    private final static int TIMEOUT_VALUE = 5*1000; // ms
    private final BluetoothSocket mSocket;

    /**
     * @param result the ResponseCodes.OBEX_HTTP_ code to respond to an incoming connect request.
     */
    public ObexRejectServer(int result, BluetoothSocket socket) {
        super();
        mResult = result;
        mSocket = socket;
        mHandlerThread = new HandlerThread("TestTimeoutHandler",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        Looper timeoutLooper = mHandlerThread.getLooper();
        mMessageHandler = new Handler(timeoutLooper, this);
        // Initiate self destruction.
        mMessageHandler.sendEmptyMessageDelayed(MSG_ID_TIMEOUT, TIMEOUT_VALUE);
    }

    // OBEX operation handlers
    @Override
    public int onConnect(HeaderSet request, HeaderSet reply) {
        if(V) Log.i(TAG,"onConnect() returning error");
        return mResult;
    }

    public void shutdown() {
      mMessageHandler.removeCallbacksAndMessages(null);
      mHandlerThread.quit();
      try {
          // This will cause an exception in the ServerSession, causing it to shut down
          mSocket.close();
      } catch (IOException e) {
          Log.w(TAG, "Unable to close socket - ignoring", e);
      }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if(V) Log.i(TAG,"Handling message ID: " + msg.what);
        switch(msg.what) {
        case MSG_ID_TIMEOUT:
            shutdown();
            break;
        default:
            // Message not handled
            return false;
        }
        return true; // Message handled
    }
}
