/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car;

import android.os.Message;
import android.util.Log;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.PrintWriter;

/**
 * BluetoothAutoConnectStateMachine is a simple state machine to manage automatic bluetooth
 * connection attempts.  It has 2 states Idle & Processing.
 * Idle is the starting state. Incoming 'CONNECT' message is honored and connection attempts are
 * triggered.  A Connection Timeout is also set before transitioning to Processing State
 * Processing state ignores any incoming 'CONNECT' requests from any of the vehicle signals,
 * since it is already in the middle of a connection attempt.  Processing moves back to Idle, when
 * either
 * 1. All the connections are made.
 * 2. All connection attempts failed and there is nothing else to try.
 */
public class BluetoothAutoConnectStateMachine extends StateMachine {
    private static final String TAG = "BTAutoConnStateMachine";
    private static final boolean DBG = false;
    private final BluetoothDeviceConnectionPolicy mPolicy;
    private final Idle mIdle;
    private final Processing mProcessing;
    // The messages handled by the States in the State Machine
    public static final int CONNECT = 101;
    public static final int DISCONNECT = 102;
    public static final int CONNECT_TIMEOUT = 103;
    public static final int DEVICE_CONNECTED = 104;
    public static final int DEVICE_DISCONNECTED = 105;
    public static final int CONNECTION_TIMEOUT_MS = 8000;


    BluetoothAutoConnectStateMachine(BluetoothDeviceConnectionPolicy policy) {
        super(TAG);
        mPolicy = policy;

        // Two supported states -
        // Idle when ready to accept connections
        // Processing when in the middle of a connection attempt.
        mIdle = new Idle();
        mProcessing = new Processing();

        addState(mIdle);
        addState(mProcessing);
        setInitialState(mIdle);
    }

    public static BluetoothAutoConnectStateMachine make(BluetoothDeviceConnectionPolicy policy) {
        BluetoothAutoConnectStateMachine mStateMachine = new BluetoothAutoConnectStateMachine(
                policy);
        mStateMachine.start();
        return mStateMachine;
    }

    public void doQuit() {
        quitNow();
    }

    /**
     * Idle State is the Initial State, when the system is accepting incoming 'CONNECT' requests.
     * Attempts a connection whenever the state transitions into Idle.
     * If the policy finds a device to connect on a profile, transitions to Processing.
     * If there is nothing to connect to, wait for the next 'CONNECT' message to try next.
     */
    private class Idle extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, "Enter Idle");
            }
            connectToBluetoothDevice();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) {
                Log.d(TAG, "Idle processMessage " + msg.what);
            }
            switch (msg.what) {
                case CONNECT: {
                    if (DBG) {
                        Log.d(TAG, "Idle->Connect:");
                    }
                    connectToBluetoothDevice();
                    break;
                }

                case DEVICE_CONNECTED: {
                    if (DBG) {
                        Log.d(TAG, "Idle->DeviceConnected: Ignored");
                    }
                    break;
                }

                default: {
                    if (DBG) {
                        Log.d(TAG, "Idle->Unhandled Msg; " + msg.what);
                    }
                    return false;
                }
            }
            return true;
        }

        /**
         * Instruct the policy to find and connect to a device on a connectable profile.
         * If the policy reports that there is nothing to connect to, stay in the Idle state.
         * If it found a {device, profile} combination to attempt a connection, move to
         * Processing state
         */
        private void connectToBluetoothDevice() {
            boolean deviceToConnectFound = mPolicy.findDeviceToConnect();
            if (deviceToConnectFound) {
                transitionTo(mProcessing);
            } else {
                // Stay in Idle State and wait for the next 'CONNECT' message.
                if (DBG) {
                    Log.d(TAG, "Idle->No device to connect");
                }
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Log.d(TAG, "Exit Idle");
            }
        }

    }

    /**
     * Processing state indicates the system is processing a auto connect trigger and will ignore
     * connection requests.
     */
    private class Processing extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, "Enter Processing");
            }

        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) {
                Log.d(TAG, "Processing processMessage " + msg.what);
            }
            BluetoothDeviceConnectionPolicy.ConnectionParams params;
            switch (msg.what) {
                case CONNECT_TIMEOUT: {
                    if (DBG) {
                        Log.d(TAG, "Connection Timeout");
                    }
                    params = (BluetoothDeviceConnectionPolicy.ConnectionParams) msg.obj;
                    mPolicy.updateDeviceConnectionStatus(params, false);
                    transitionTo(mIdle);
                    break;
                }

                case DEVICE_CONNECTED:
                    // fall through
                case DEVICE_DISCONNECTED: {
                    removeMessages(CONNECT_TIMEOUT);
                    transitionTo(mIdle);
                    break;
                }

                default:
                    if (DBG) {
                        Log.d(TAG, "Processing->Unhandled Msg: " + msg.what);
                    }
                    return false;
            }
            return true;
        }

        @Override
        public void exit() {
            if (DBG) {
                Log.d(TAG, "Exit Processing");
            }
        }
    }

    public void dump(PrintWriter writer) {
        writer.println("StateMachine: " + this.toString());
    }

}
