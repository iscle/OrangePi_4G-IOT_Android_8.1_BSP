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

import javax.obex.ResponseCodes;
import javax.obex.ServerSession;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * Wraps multiple BluetoothServerSocket objects to make it possible to accept connections on
 * both a RFCOMM and L2CAP channel in parallel.<br>
 * Create an instance using {@link #create()}, which will block until the sockets have been created
 * and channel numbers have been assigned.<br>
 * Use {@link #getRfcommChannel()} and {@link #getL2capPsm()} to get the channel numbers to
 * put into the SDP record.<br>
 * Call {@link #shutdown(boolean)} to terminate the accept threads created by the call to
 * {@link #create(IObexConnectionHandler)}.<br>
 * A reference to an object of this type cannot be reused, and the {@link BluetoothServerSocket}
 * object references passed to this object will be closed by this object, hence cannot be reused
 * either (This is needed, as the only way to interrupt an accept call is to close the socket...)
 * <br>
 * When a connection is accepted,
 * {@link IObexConnectionHandler#onConnect(BluetoothDevice, BluetoothSocket)} will be called.<br>
 * If the an error occur while waiting for an incoming connection
 * {@link IObexConnectionHandler#onConnect(BluetoothDevice, BluetoothSocket)} will be called.<br>
 * In both cases the {@link ObexServerSockets} object have terminated, and a new must be created.
 */
public class ObexServerSockets {
    private final String TAG;
    private static final String STAG = "ObexServerSockets";
    private static final boolean D = true; // TODO: set to false!
    private static final int NUMBER_OF_SOCKET_TYPES = 2; // increment if LE will be supported

    private final IObexConnectionHandler mConHandler;
    /* The wrapped sockets */
    private final BluetoothServerSocket mRfcommSocket;
    private final BluetoothServerSocket mL2capSocket;
    /* Handles to the accept threads. Needed for shutdown. */
    private SocketAcceptThread mRfcommThread = null;
    private SocketAcceptThread mL2capThread = null;

    private volatile boolean mConAccepted = false;

    private static volatile int sInstanceCounter = 0;

    private ObexServerSockets(IObexConnectionHandler conHandler,
            BluetoothServerSocket rfcommSocket,
            BluetoothServerSocket l2capSocket) {
        mConHandler = conHandler;
        mRfcommSocket = rfcommSocket;
        mL2capSocket = l2capSocket;
        TAG = "ObexServerSockets" + sInstanceCounter++;
    }

    /**
     * Creates an RFCOMM {@link BluetoothServerSocket} and a L2CAP {@link BluetoothServerSocket}
     * @param validator a reference to the {@link IObexConnectionHandler} object to call
     *                  to validate an incoming connection.
     * @return a reference to a {@link ObexServerSockets} object instance.
     * @throws IOException if it occurs while creating the {@link BluetoothServerSocket}s.
     */
    public static ObexServerSockets create(IObexConnectionHandler validator) {
        return create(validator, BluetoothAdapter.SOCKET_CHANNEL_AUTO_STATIC_NO_SDP,
                BluetoothAdapter.SOCKET_CHANNEL_AUTO_STATIC_NO_SDP, true);
    }

    /**
     * Creates an Insecure RFCOMM {@link BluetoothServerSocket} and a L2CAP
         *                  {@link BluetoothServerSocket}
     * @param validator a reference to the {@link IObexConnectionHandler} object to call
     *                  to validate an incoming connection.
     * @return a reference to a {@link ObexServerSockets} object instance.
     * @throws IOException if it occurs while creating the {@link BluetoothServerSocket}s.
     */
    public static ObexServerSockets createInsecure(IObexConnectionHandler validator) {
        return create(validator, BluetoothAdapter.SOCKET_CHANNEL_AUTO_STATIC_NO_SDP,
                BluetoothAdapter.SOCKET_CHANNEL_AUTO_STATIC_NO_SDP, false);
    }

    /**
     * Creates an RFCOMM {@link BluetoothServerSocket} and a L2CAP {@link BluetoothServerSocket}
     * with specific l2cap and RFCOMM channel numbers. It is the responsibility of the caller to
     * ensure the numbers are free and can be used, e.g. by calling {@link #getL2capPsm()} and
     * {@link #getRfcommChannel()} in {@link ObexServerSockets}.
     * @param validator a reference to the {@link IObexConnectionHandler} object to call
     *                  to validate an incoming connection.
     * @param isSecure boolean flag to determine whther socket would be secured or inseucure.
     * @return a reference to a {@link ObexServerSockets} object instance.
     * @throws IOException if it occurs while creating the {@link BluetoothServerSocket}s.
     *
     * TODO: Make public when it becomes possible to determine that the listen-call
     *       failed due to channel-in-use.
     */
    private static ObexServerSockets create(
            IObexConnectionHandler validator, int rfcommChannel, int l2capPsm, boolean isSecure) {
        if(D) Log.d(STAG,"create(rfcomm = " +rfcommChannel + ", l2capPsm = " + l2capPsm +")");
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if(bt == null) {
            throw new RuntimeException("No bluetooth adapter...");
        }
        BluetoothServerSocket rfcommSocket = null;
        BluetoothServerSocket l2capSocket = null;
        boolean initSocketOK = false;
        final int CREATE_RETRY_TIME = 10;

        // It's possible that create will fail in some cases. retry for 10 times
        for (int i = 0; i < CREATE_RETRY_TIME; i++) {
            initSocketOK = true;
            try {
                if(rfcommSocket == null) {
                    if (isSecure) {
                        rfcommSocket = bt.listenUsingRfcommOn(rfcommChannel);
                    } else {
                        rfcommSocket = bt.listenUsingInsecureRfcommOn(rfcommChannel);
                    }
                }
                if(l2capSocket == null) {
                    if (isSecure) {
                        l2capSocket = bt.listenUsingL2capOn(l2capPsm);
                    } else {
                        l2capSocket = bt.listenUsingInsecureL2capOn(l2capPsm);
                    }
                }
            } catch (IOException e) {
                Log.e(STAG, "Error create ServerSockets ",e);
                initSocketOK = false;
            }
            if (!initSocketOK) {
                // Need to break out of this loop if BT is being turned off.
                int state = bt.getState();
                if ((state != BluetoothAdapter.STATE_TURNING_ON) &&
                    (state != BluetoothAdapter.STATE_ON)) {
                    Log.w(STAG, "initServerSockets failed as BT is (being) turned off");
                    break;
                }
                try {
                    if (D) Log.v(STAG, "waiting 300 ms...");
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Log.e(STAG, "create() was interrupted");
                }
            } else {
                break;
            }
        }

        if (initSocketOK) {
            if (D) Log.d(STAG, "Succeed to create listening sockets ");
            ObexServerSockets sockets = new ObexServerSockets(validator, rfcommSocket, l2capSocket);
            sockets.startAccept();
            return sockets;
        } else {
            Log.e(STAG, "Error to create listening socket after " + CREATE_RETRY_TIME + " try");
            return null;
        }
    }

    /**
     * Returns the channel number assigned to the RFCOMM socket. This will be a static value, that
     * should be reused for multiple connections.
     * @return the RFCOMM channel number
     */
    public int getRfcommChannel() {
        return mRfcommSocket.getChannel();
    }

    /**
     * Returns the channel number assigned to the L2CAP socket. This will be a static value, that
     * should be reused for multiple connections.
     * @return the L2CAP channel number
     */
    public int getL2capPsm() {
        return mL2capSocket.getChannel();
    }

    /**
     * Initiate the accept threads.
     * Will create a thread for each socket type. an incoming connection will be signaled to
     * the {@link IObexConnectionValidator#onConnect()}, at which point both threads will exit.
     */
    private void startAccept() {
        if(D) Log.d(TAG,"startAccept()");
        prepareForNewConnect();

        mRfcommThread = new SocketAcceptThread(mRfcommSocket);
        mRfcommThread.start();

        mL2capThread = new SocketAcceptThread(mL2capSocket);
        mL2capThread.start();
    }

    /**
     * Set state to accept new incoming connection. Will cause the next incoming connection to be
     * Signaled through {@link IObexConnectionValidator#onConnect()};
     */
    synchronized public void prepareForNewConnect() {
        if(D) Log.d(TAG, "prepareForNewConnect()");
        mConAccepted = false;
    }

    /**
     * Called from the AcceptThreads to signal an incoming connection.
     * This is the entry point that needs to synchronize between the accept
     * threads, and ensure only a single connection is accepted.
     * {@link mAcceptedSocket} is used a state variable.
     * @param device the connecting device.
     * @param conSocket the socket associated with the connection.
     * @return true if the connection is accepted, false otherwise.
     */
    synchronized private boolean onConnect(BluetoothDevice device, BluetoothSocket conSocket) {
        if(D) Log.d(TAG, "onConnect() socket: " + conSocket + " mConAccepted = " + mConAccepted);
        if(mConAccepted  == false && mConHandler.onConnect(device, conSocket) == true) {
            mConAccepted = true; // TODO: Reset this when ready to accept new connection
            /* Signal the remaining threads to stop.
            shutdown(false); */ // UPDATE: TODO: remove - redesigned to keep running...
            return true;
        }
        return false;
    }

    /**
     * Signal to the {@link IObexConnectionHandler} that an error have occurred.
     */
    synchronized private void onAcceptFailed() {
        shutdown(false);
        BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
        if ((mAdapter != null) && (mAdapter.getState() == BluetoothAdapter.STATE_ON)) {
            Log.d(TAG, "onAcceptFailed() calling shutdown...");
            mConHandler.onAcceptFailed();
        }
    }

    /**
     * Terminate any running accept threads
     * @param block Set true to block the calling thread until the AcceptThreads
     * has ended execution
     */
    synchronized public void shutdown(boolean block) {
        if(D) Log.d(TAG, "shutdown(block = " + block + ")");
        if(mRfcommThread != null) {
            mRfcommThread.shutdown();
        }
        if(mL2capThread != null){
            mL2capThread.shutdown();
        }
        if(block == true) {
            while(mRfcommThread != null || mL2capThread != null) {
                try {
                    if(mRfcommThread != null) {
                        mRfcommThread.join();
                        mRfcommThread = null;
                    }
                    if(mL2capThread != null) {
                        mL2capThread.join();
                        mL2capThread = null;
                    }
                } catch (InterruptedException e) {
                    Log.i(TAG, "shutdown() interrupted, continue waiting...", e);
                }
            }
        } else {
            mRfcommThread = null;
            mL2capThread = null;
        }
    }

    /**
     * A thread that runs in the background waiting for remote an incoming
     * connect. Once a remote socket connects, this thread will be
     * shutdown. When the remote disconnect, this thread shall be restarted to
     * accept a new connection.
     */
    private class SocketAcceptThread extends Thread {

        private boolean mStopped = false;
        private final BluetoothServerSocket mServerSocket;

        /**
         * Create a SocketAcceptThread
         * @param serverSocket shall never be null.
         * @param latch shall never be null.
         * @throws IllegalArgumentException
         */
        public SocketAcceptThread(BluetoothServerSocket serverSocket) {
            if(serverSocket == null) {
                throw new IllegalArgumentException("serverSocket cannot be null");
            }
            mServerSocket = serverSocket;
        }

        /**
         * Run until shutdown of BT.
         * Accept incoming connections and reject if needed. Keep accepting incoming connections.
         */
        @Override
        public void run() {
            try {
                while (!mStopped) {
                    BluetoothSocket connSocket;
                    BluetoothDevice device;

                    try {
                        if (D) Log.d(TAG, "Accepting socket connection...");

                        connSocket = mServerSocket.accept();
                        if (D) Log.d(TAG, "Accepted socket connection from: " + mServerSocket);

                       if (connSocket == null) {
                           // TODO: Do we need a max error count, to avoid spinning?
                            Log.w(TAG, "connSocket is null - reattempt accept");
                            continue;
                        }
                        device = connSocket.getRemoteDevice();

                        if (device == null) {
                            Log.i(TAG, "getRemoteDevice() = null - reattempt accept");
                            try{
                                connSocket.close();
                            } catch (IOException e) {
                                Log.w(TAG, "Error closing the socket. ignoring...",e );
                            }
                            continue;
                        }

                        /* Signal to the service that we have received an incoming connection.
                         */
                        boolean isValid = ObexServerSockets.this.onConnect(device, connSocket);

                        if(isValid == false) {
                            /* Close connection if we already have a connection with another device
                             * by responding to the OBEX connect request.
                             */
                            Log.i(TAG, "RemoteDevice is invalid - creating ObexRejectServer.");
                            BluetoothObexTransport obexTrans =
                                    new BluetoothObexTransport(connSocket);
                            // Create and detach a selfdestructing ServerSession to respond to any
                            // incoming OBEX signals.
                            new ServerSession(obexTrans,
                                    new ObexRejectServer(
                                            ResponseCodes.OBEX_HTTP_UNAVAILABLE,
                                            connSocket),
                                    null);
                            // now wait for a new connect
                        } else {
                            // now wait for a new connect
                        }
                    } catch (IOException ex) {
                        if(mStopped == true) {
                            // Expected exception because of shutdown.
                        } else {
                            Log.w(TAG, "Accept exception for " +
                                    mServerSocket, ex);
                            ObexServerSockets.this.onAcceptFailed();
                        }
                        mStopped=true;
                    }
                } // End while()
            } finally {
                if (D) Log.d(TAG, "AcceptThread ended for: " + mServerSocket);
            }
        }

        /**
         * Shuts down the accept threads, and closes the ServerSockets, causing all related
         * BluetoothSockets to disconnect, hence do not call until all all accepted connections
         * are ready to be disconnected.
         */
        public void shutdown() {
            if(mStopped == false) {
                mStopped = true;
                // TODO: According to the documentation, this should not close the accepted
                //       sockets - and that is true, but it closes the l2cap connections, and
                //       therefore it implicitly also closes the accepted sockets...
                try {
                     mServerSocket.close();
                } catch (IOException e) {
                    if(D) Log.d(TAG, "Exception while thread shutdown:", e);
                }
            }
            // If called from another thread, interrupt the thread
            if(!Thread.currentThread().equals(this)){
                // TODO: Will this interrupt the thread if it is blocked in synchronized?
                // Else: change to use InterruptableLock
                if(D) Log.d(TAG, "shutdown called from another thread - interrupt().");
                interrupt();
            }
        }
    }
}
