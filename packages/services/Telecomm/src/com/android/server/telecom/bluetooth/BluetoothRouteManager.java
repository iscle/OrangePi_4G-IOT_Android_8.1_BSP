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
 * limitations under the License
 */

package com.android.server.telecom.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.telecom.BluetoothHeadsetProxy;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BluetoothRouteManager extends StateMachine {
    private static final String LOG_TAG = BluetoothRouteManager.class.getSimpleName();

    private static final SparseArray<String> MESSAGE_CODE_TO_NAME = new SparseArray<String>() {{
         put(NEW_DEVICE_CONNECTED, "NEW_DEVICE_CONNECTED");
         put(LOST_DEVICE, "LOST_DEVICE");
         put(CONNECT_HFP, "CONNECT_HFP");
         put(DISCONNECT_HFP, "DISCONNECT_HFP");
         put(RETRY_HFP_CONNECTION, "RETRY_HFP_CONNECTION");
         put(HFP_IS_ON, "HFP_IS_ON");
         put(HFP_LOST, "HFP_LOST");
         put(CONNECTION_TIMEOUT, "CONNECTION_TIMEOUT");
         put(RUN_RUNNABLE, "RUN_RUNNABLE");
    }};

    // Constants for compatiblity with current CARSM/CARPA
    // TODO: delete and replace with new direct interface to CARPA.
    public static final int BLUETOOTH_UNINITIALIZED = 0;
    public static final int BLUETOOTH_DISCONNECTED = 1;
    public static final int BLUETOOTH_DEVICE_CONNECTED = 2;
    public static final int BLUETOOTH_AUDIO_PENDING = 3;
    public static final int BLUETOOTH_AUDIO_CONNECTED = 4;

    public static final String AUDIO_OFF_STATE_NAME = "AudioOff";
    public static final String AUDIO_CONNECTING_STATE_NAME_PREFIX = "Connecting";
    public static final String AUDIO_CONNECTED_STATE_NAME_PREFIX = "Connected";

    public interface BluetoothStateListener {
        void onBluetoothStateChange(int oldState, int newState);
    }

    // Broadcast receiver to receive audio state change broadcasts from the BT stack
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("BRM.oR");
            try {
                String action = intent.getAction();

                if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
                    int bluetoothHeadsetAudioState =
                            intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                                    BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device == null) {
                        Log.w(BluetoothRouteManager.this, "Got null device from broadcast. " +
                                "Ignoring.");
                        return;
                    }

                    Log.i(BluetoothRouteManager.this, "Device %s transitioned to audio state %d",
                            device.getAddress(), bluetoothHeadsetAudioState);
                    Session session = Log.createSubsession();
                    SomeArgs args = SomeArgs.obtain();
                    args.arg1 = session;
                    args.arg2 = device.getAddress();
                    switch (bluetoothHeadsetAudioState) {
                        case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                            sendMessage(HFP_IS_ON, args);
                            break;
                        case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                            sendMessage(HFP_LOST, args);
                            break;
                    }
                }
            } finally {
                Log.endSession();
            }
        }
    };

    /**
     * Constants representing messages sent to the state machine.
     * Messages are expected to be sent with {@link SomeArgs} as the obj.
     * In all cases, arg1 will be the log session.
     */
    // arg2: Address of the new device
    public static final int NEW_DEVICE_CONNECTED = 1;
    // arg2: Address of the lost device
    public static final int LOST_DEVICE = 2;

    // arg2 (optional): the address of the specific device to connect to.
    public static final int CONNECT_HFP = 100;
    // No args.
    public static final int DISCONNECT_HFP = 101;
    // arg2: the address of the device to connect to.
    public static final int RETRY_HFP_CONNECTION = 102;

    // arg2: the address of the device that is on
    public static final int HFP_IS_ON = 200;
    // arg2: the address of the device that lost HFP
    public static final int HFP_LOST = 201;

    // No args; only used internally
    public static final int CONNECTION_TIMEOUT = 300;

    // arg2: Runnable
    public static final int RUN_RUNNABLE = 9001;

    private static final int MAX_CONNECTION_RETRIES = 2;

    // States
    private final class AudioOffState extends State {
        @Override
        public String getName() {
            return AUDIO_OFF_STATE_NAME;
        }

        @Override
        public void enter() {
            BluetoothDevice erroneouslyConnectedDevice = getBluetoothAudioConnectedDevice();
            if (erroneouslyConnectedDevice != null) {
                Log.w(LOG_TAG, "Entering AudioOff state but device %s appears to be connected. " +
                        "Disconnecting.", erroneouslyConnectedDevice);
                disconnectAudio();
            }
            cleanupStatesForDisconnectedDevices();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == RUN_RUNNABLE) {
                ((Runnable) msg.obj).run();
                return HANDLED;
            }

            SomeArgs args = (SomeArgs) msg.obj;
            try {
                switch (msg.what) {
                    case NEW_DEVICE_CONNECTED:
                        // If the device isn't new, don't bother passing it up.
                        if (addDevice((String) args.arg2)) {
                            // TODO: replace with new interface
                            if (mDeviceManager.getNumConnectedDevices() == 1) {
                                mListener.onBluetoothStateChange(
                                        BLUETOOTH_DISCONNECTED, BLUETOOTH_DEVICE_CONNECTED);
                            }
                        }
                        break;
                    case LOST_DEVICE:
                        // If the device has already been removed, don't bother passing it up.
                        if (removeDevice((String) args.arg2)) {
                            // TODO: replace with new interface
                            if (mDeviceManager.getNumConnectedDevices() == 0) {
                                mListener.onBluetoothStateChange(
                                        BLUETOOTH_DEVICE_CONNECTED, BLUETOOTH_DISCONNECTED);
                            }
                        }
                        break;
                    case CONNECT_HFP:
                        String actualAddress = connectHfpAudio((String) args.arg2);

                        if (actualAddress != null) {
                            mListener.onBluetoothStateChange(BLUETOOTH_DEVICE_CONNECTED,
                                    BLUETOOTH_AUDIO_PENDING);
                            transitionTo(getConnectingStateForAddress(actualAddress,
                                    "AudioOff/CONNECT_HFP"));
                        } else {
                            Log.w(LOG_TAG, "Tried to connect to %s but failed to connect to" +
                                    " any HFP device.", (String) args.arg2);
                        }
                        break;
                    case DISCONNECT_HFP:
                        // Ignore.
                        break;
                    case RETRY_HFP_CONNECTION:
                        Log.i(LOG_TAG, "Retrying HFP connection to %s", (String) args.arg2);
                        String retryAddress = connectHfpAudio((String) args.arg2, args.argi1);

                        if (retryAddress != null) {
                            mListener.onBluetoothStateChange(BLUETOOTH_DEVICE_CONNECTED,
                                    BLUETOOTH_AUDIO_PENDING);
                            transitionTo(getConnectingStateForAddress(retryAddress,
                                    "AudioOff/RETRY_HFP_CONNECTION"));
                        } else {
                            Log.i(LOG_TAG, "Retry failed.");
                        }
                        break;
                    case CONNECTION_TIMEOUT:
                        // Ignore.
                        break;
                    case HFP_IS_ON:
                        String address = (String) args.arg2;
                        Log.w(LOG_TAG, "HFP audio unexpectedly turned on from device %s", address);
                        mListener.onBluetoothStateChange(BLUETOOTH_DEVICE_CONNECTED,
                                BLUETOOTH_AUDIO_CONNECTED);
                        transitionTo(getConnectedStateForAddress(address, "AudioOff/HFP_IS_ON"));
                        break;
                    case HFP_LOST:
                        Log.i(LOG_TAG, "Received HFP off for device %s while HFP off.",
                                (String) args.arg2);
                        break;
                }
            } finally {
                args.recycle();
            }
            return HANDLED;
        }
    }

    private final class AudioConnectingState extends State {
        private final String mDeviceAddress;

        AudioConnectingState(String address) {
            mDeviceAddress = address;
        }

        @Override
        public String getName() {
            return AUDIO_CONNECTING_STATE_NAME_PREFIX + ":" + mDeviceAddress;
        }

        @Override
        public void enter() {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = Log.createSubsession();
            sendMessageDelayed(CONNECTION_TIMEOUT, args,
                    mTimeoutsAdapter.getBluetoothPendingTimeoutMillis(
                            mContext.getContentResolver()));
        }

        @Override
        public void exit() {
            removeMessages(CONNECTION_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == RUN_RUNNABLE) {
                ((Runnable) msg.obj).run();
                return HANDLED;
            }

            SomeArgs args = (SomeArgs) msg.obj;
            String address = (String) args.arg2;
            try {
                switch (msg.what) {
                    case NEW_DEVICE_CONNECTED:
                        // If the device isn't new, don't bother passing it up.
                        if (addDevice(address)) {
                            // TODO: replace with new interface
                            if (mDeviceManager.getNumConnectedDevices() == 1) {
                                Log.w(LOG_TAG, "Newly connected device is only device" +
                                        " while audio pending.");
                            }
                        }
                        break;
                    case LOST_DEVICE:
                        removeDevice((String) args.arg2);

                        if (Objects.equals(address, mDeviceAddress)) {
                            String newAddress = connectHfpAudio(null);
                            if (newAddress != null) {
                                mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_PENDING,
                                        BLUETOOTH_AUDIO_PENDING);
                                transitionTo(getConnectingStateForAddress(newAddress,
                                        "AudioConnecting/LOST_DEVICE"));
                            } else {
                                int numConnectedDevices = mDeviceManager.getNumConnectedDevices();
                                mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_PENDING,
                                        numConnectedDevices == 0 ? BLUETOOTH_DISCONNECTED :
                                                BLUETOOTH_DEVICE_CONNECTED);
                                transitionTo(mAudioOffState);
                            }
                        }
                        break;
                    case CONNECT_HFP:
                        if (Objects.equals(mDeviceAddress, address)) {
                            // Ignore repeated connection attempts to the same device
                            break;
                        }
                        String actualAddress = connectHfpAudio(address);

                        if (actualAddress != null) {
                            mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_PENDING,
                                    BLUETOOTH_AUDIO_PENDING);
                            transitionTo(getConnectingStateForAddress(actualAddress,
                                    "AudioConnecting/CONNECT_HFP"));
                        } else {
                            Log.w(LOG_TAG, "Tried to connect to %s but failed" +
                                    " to connect to any HFP device.", (String) args.arg2);
                        }
                        break;
                    case DISCONNECT_HFP:
                        disconnectAudio();
                        mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_PENDING,
                                BLUETOOTH_DEVICE_CONNECTED);
                        transitionTo(mAudioOffState);
                        break;
                    case RETRY_HFP_CONNECTION:
                        if (Objects.equals(address, mDeviceAddress)) {
                            Log.d(LOG_TAG, "Retry message came through while connecting.");
                        } else {
                            String retryAddress = connectHfpAudio(address, args.argi1);
                            if (retryAddress != null) {
                                transitionTo(getConnectingStateForAddress(retryAddress,
                                        "AudioConnecting/RETRY_HFP_CONNECTION"));
                            } else {
                                Log.i(LOG_TAG, "Retry failed.");
                            }
                        }
                        break;
                    case CONNECTION_TIMEOUT:
                        Log.i(LOG_TAG, "Connection with device %s timed out.",
                                mDeviceAddress);
                        transitionToActualState(BLUETOOTH_AUDIO_PENDING);
                        break;
                    case HFP_IS_ON:
                        if (Objects.equals(mDeviceAddress, address)) {
                            Log.i(LOG_TAG, "HFP connection success for device %s.", mDeviceAddress);
                            transitionTo(mAudioConnectedStates.get(mDeviceAddress));
                        } else {
                            Log.w(LOG_TAG, "In connecting state for device %s but %s" +
                                    " is now connected", mDeviceAddress, address);
                            transitionTo(getConnectedStateForAddress(address,
                                    "AudioConnecting/HFP_IS_ON"));
                        }
                        mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_PENDING,
                                BLUETOOTH_AUDIO_CONNECTED);
                        break;
                    case HFP_LOST:
                        if (Objects.equals(mDeviceAddress, address)) {
                            Log.i(LOG_TAG, "Connection with device %s failed.",
                                    mDeviceAddress);
                            transitionToActualState(BLUETOOTH_AUDIO_PENDING);
                        } else {
                            Log.w(LOG_TAG, "Got HFP lost message for device %s while" +
                                    " connecting to %s.", address, mDeviceAddress);
                        }
                        break;
                }
            } finally {
                args.recycle();
            }
            return HANDLED;
        }
    }

    private final class AudioConnectedState extends State {
        private final String mDeviceAddress;

        AudioConnectedState(String address) {
            mDeviceAddress = address;
        }

        @Override
        public String getName() {
            return AUDIO_CONNECTED_STATE_NAME_PREFIX + ":" + mDeviceAddress;
        }

        @Override
        public void enter() {
            // Remove any of the retries that are still in the queue once any device becomes
            // connected.
            removeMessages(RETRY_HFP_CONNECTION);
            // Remove and add to ensure that the device is at the top.
            mMostRecentlyUsedDevices.remove(mDeviceAddress);
            mMostRecentlyUsedDevices.add(mDeviceAddress);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == RUN_RUNNABLE) {
                ((Runnable) msg.obj).run();
                return HANDLED;
            }

            SomeArgs args = (SomeArgs) msg.obj;
            String address = (String) args.arg2;
            try {
                switch (msg.what) {
                    case NEW_DEVICE_CONNECTED:
                        // If the device isn't new, don't bother passing it up.
                        if (addDevice(address)) {
                            // TODO: Replace with new interface
                            if (mDeviceManager.getNumConnectedDevices() == 1) {
                                Log.w(LOG_TAG, "Newly connected device is only" +
                                        " device while audio connected.");
                            }
                        }
                        break;
                    case LOST_DEVICE:
                        removeDevice((String) args.arg2);

                        if (Objects.equals(address, mDeviceAddress)) {
                            String newAddress = connectHfpAudio(null);
                            if (newAddress != null) {
                                mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_CONNECTED,
                                        BLUETOOTH_AUDIO_PENDING);
                                transitionTo(getConnectingStateForAddress(newAddress,
                                        "AudioConnected/LOST_DEVICE"));
                            } else {
                                int numConnectedDevices = mDeviceManager.getNumConnectedDevices();
                                mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_CONNECTED,
                                        numConnectedDevices == 0 ? BLUETOOTH_DISCONNECTED :
                                                BLUETOOTH_DEVICE_CONNECTED);
                                transitionTo(mAudioOffState);
                            }
                        }
                        break;
                    case CONNECT_HFP:
                        if (Objects.equals(mDeviceAddress, address)) {
                            // Ignore connection to already connected device.
                            break;
                        }
                        String actualAddress = connectHfpAudio(address);

                        if (actualAddress != null) {
                            mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_CONNECTED,
                                    BLUETOOTH_AUDIO_PENDING);
                            transitionTo(getConnectingStateForAddress(address,
                                    "AudioConnected/CONNECT_HFP"));
                        } else {
                            Log.w(LOG_TAG, "Tried to connect to %s but failed" +
                                    " to connect to any HFP device.", (String) args.arg2);
                        }
                        break;
                    case DISCONNECT_HFP:
                        disconnectAudio();
                        mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_CONNECTED,
                                BLUETOOTH_DEVICE_CONNECTED);
                        transitionTo(mAudioOffState);
                        break;
                    case RETRY_HFP_CONNECTION:
                        if (Objects.equals(address, mDeviceAddress)) {
                            Log.d(LOG_TAG, "Retry message came through while connected.");
                        } else {
                            String retryAddress = connectHfpAudio(address, args.argi1);
                            if (retryAddress != null) {
                                mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_CONNECTED,
                                        BLUETOOTH_AUDIO_PENDING);
                                transitionTo(getConnectingStateForAddress(retryAddress,
                                        "AudioConnected/RETRY_HFP_CONNECTION"));
                            } else {
                                Log.i(LOG_TAG, "Retry failed.");
                            }
                        }
                        break;
                    case CONNECTION_TIMEOUT:
                        Log.w(LOG_TAG, "Received CONNECTION_TIMEOUT while connected.");
                        break;
                    case HFP_IS_ON:
                        if (Objects.equals(mDeviceAddress, address)) {
                            Log.i(LOG_TAG, "Received redundant HFP_IS_ON for %s", mDeviceAddress);
                        } else {
                            Log.w(LOG_TAG, "In connected state for device %s but %s" +
                                    " is now connected", mDeviceAddress, address);
                            transitionTo(getConnectedStateForAddress(address,
                                    "AudioConnected/HFP_IS_ON"));
                        }
                        break;
                    case HFP_LOST:
                        if (Objects.equals(mDeviceAddress, address)) {
                            Log.i(LOG_TAG, "HFP connection with device %s lost.", mDeviceAddress);
                            String nextAddress = connectHfpAudio(null, mDeviceAddress);
                            if (nextAddress == null) {
                                Log.i(LOG_TAG, "No suitable fallback device. Going to AUDIO_OFF.");
                                transitionToActualState(BLUETOOTH_AUDIO_CONNECTED);
                            } else {
                                mListener.onBluetoothStateChange(BLUETOOTH_AUDIO_CONNECTED,
                                        BLUETOOTH_AUDIO_PENDING);
                                transitionTo(getConnectingStateForAddress(nextAddress,
                                        "AudioConnected/HFP_LOST"));
                            }
                        } else {
                            Log.w(LOG_TAG, "Got HFP lost message for device %s while" +
                                    " connected to %s.", address, mDeviceAddress);
                        }
                        break;
                }
            } finally {
                args.recycle();
            }
            return HANDLED;
        }
    }

    private final State mAudioOffState;
    private final Map<String, AudioConnectingState> mAudioConnectingStates = new HashMap<>();
    private final Map<String, AudioConnectedState> mAudioConnectedStates = new HashMap<>();
    private final Set<State> statesToCleanUp = new HashSet<>();
    private final LinkedHashSet<String> mMostRecentlyUsedDevices = new LinkedHashSet<>();

    private final TelecomSystem.SyncRoot mLock;
    private final Context mContext;
    private final Timeouts.Adapter mTimeoutsAdapter;

    private BluetoothStateListener mListener;
    private BluetoothDeviceManager mDeviceManager;

    public BluetoothRouteManager(Context context, TelecomSystem.SyncRoot lock,
            BluetoothDeviceManager deviceManager, Timeouts.Adapter timeoutsAdapter) {
        super(BluetoothRouteManager.class.getSimpleName());
        mContext = context;
        mLock = lock;
        mDeviceManager = deviceManager;
        mDeviceManager.setBluetoothRouteManager(this);
        mTimeoutsAdapter = timeoutsAdapter;

        IntentFilter intentFilter = new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);

        mAudioOffState = new AudioOffState();
        addState(mAudioOffState);
        setInitialState(mAudioOffState);
        start();
    }

    @Override
    protected void onPreHandleMessage(Message msg) {
        if (msg.obj != null && msg.obj instanceof SomeArgs) {
            SomeArgs args = (SomeArgs) msg.obj;

            Log.continueSession(((Session) args.arg1), "BRM.pM_" + msg.what);
            Log.i(LOG_TAG, "Message received: %s.", MESSAGE_CODE_TO_NAME.get(msg.what));
        } else if (msg.what == RUN_RUNNABLE && msg.obj instanceof Runnable) {
            Log.i(LOG_TAG, "Running runnable for testing");
        } else {
            Log.w(LOG_TAG, "Message sent must be of type nonnull SomeArgs, but got " +
                    (msg.obj == null ? "null" : msg.obj.getClass().getSimpleName()));
            Log.w(LOG_TAG, "The message was of code %d = %s",
                    msg.what, MESSAGE_CODE_TO_NAME.get(msg.what));
        }
    }

    @Override
    protected void onPostHandleMessage(Message msg) {
        Log.endSession();
    }

    /**
     * Returns whether there is a HFP device available to route audio to.
     * @return true if there is a device, false otherwise.
     */
    public boolean isBluetoothAvailable() {
        return mDeviceManager.getNumConnectedDevices() > 0;
    }

    /**
     * This method needs be synchronized with the local looper because getCurrentState() depends
     * on the internal state of the state machine being consistent. Therefore, there may be a
     * delay when calling this method.
     * @return
     */
    public boolean isBluetoothAudioConnectedOrPending() {
        IState[] state = new IState[] {null};
        CountDownLatch latch = new CountDownLatch(1);
        Runnable r = () -> {
            state[0] = getCurrentState();
            latch.countDown();
        };
        sendMessage(RUN_RUNNABLE, r);
        try {
            latch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.w(LOG_TAG, "isBluetoothAudioConnectedOrPending -- interrupted getting state");
            return false;
        }
        return (state[0] != null) && (state[0] != mAudioOffState);
    }

    /**
     * Attempts to connect to Bluetooth audio. If the first connection attempt synchronously
     * fails, schedules a retry at a later time.
     * @param address The MAC address of the bluetooth device to connect to. If null, the most
     *                recently used device will be used.
     */
    public void connectBluetoothAudio(String address) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = address;
        sendMessage(CONNECT_HFP, args);
    }

    /**
     * Disconnects Bluetooth HFP audio.
     */
    public void disconnectBluetoothAudio() {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        sendMessage(DISCONNECT_HFP, args);
    }

    public void setListener(BluetoothStateListener listener) {
        mListener = listener;
    }

    public void onDeviceAdded(BluetoothDevice newDevice) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = newDevice.getAddress();
        sendMessage(NEW_DEVICE_CONNECTED, args);
    }

    public void onDeviceLost(BluetoothDevice lostDevice) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = lostDevice.getAddress();
        sendMessage(LOST_DEVICE, args);
    }

    private String connectHfpAudio(String address) {
        return connectHfpAudio(address, 0, null);
    }

    private String connectHfpAudio(String address, int retryCount) {
        return connectHfpAudio(address, retryCount, null);
    }

    private String connectHfpAudio(String address, String excludeAddress) {
        return connectHfpAudio(address, 0, excludeAddress);
    }

    /**
     * Initiates a HFP connection to the BT address specified.
     * Note: This method is not synchronized on the Telecom lock, so don't try and call back into
     * Telecom from within it.
     * @param address The address that should be tried first. May be null.
     * @param retryCount The number of times this connection attempt has been retried.
     * @param excludeAddress Don't connect to this address.
     * @return The address of the device that's actually being connected to, or null if no
     * connection was successful.
     */
    private String connectHfpAudio(String address, int retryCount, String excludeAddress) {
        BluetoothHeadsetProxy bluetoothHeadset = mDeviceManager.getHeadsetService();
        if (bluetoothHeadset == null) {
            Log.i(this, "connectHfpAudio: no headset service available.");
            return null;
        }
        List<BluetoothDevice> deviceList = bluetoothHeadset.getConnectedDevices();
        Optional<BluetoothDevice> matchingDevice = deviceList.stream()
                .filter(d -> Objects.equals(d.getAddress(), address))
                .findAny();

        String actualAddress = matchingDevice.isPresent() ?
                address : getPreferredDevice(excludeAddress);
        if (!matchingDevice.isPresent()) {
            Log.i(this, "No device with address %s available. Using %s instead.",
                    address, actualAddress);
        }
        if (actualAddress != null && !connectAudio(actualAddress)) {
            boolean shouldRetry = retryCount < MAX_CONNECTION_RETRIES;
            Log.w(LOG_TAG, "Could not connect to %s. Will %s", actualAddress,
                    shouldRetry ? "retry" : "not retry");
            if (shouldRetry) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = Log.createSubsession();
                args.arg2 = actualAddress;
                args.argi1 = retryCount + 1;
                sendMessageDelayed(RETRY_HFP_CONNECTION, args,
                        mTimeoutsAdapter.getRetryBluetoothConnectAudioBackoffMillis(
                                mContext.getContentResolver()));
            }
            return null;
        }

        return actualAddress;
    }

    private String getPreferredDevice(String excludeAddress) {
        String preferredDevice = null;
        for (String address : mMostRecentlyUsedDevices) {
            if (!Objects.equals(excludeAddress, address)) {
                preferredDevice = address;
            }
        }
        if (preferredDevice == null) {
            return mDeviceManager.getMostRecentlyConnectedDevice(excludeAddress);
        }
        return preferredDevice;
    }

    private void transitionToActualState(int currentBtState) {
        BluetoothDevice possiblyAlreadyConnectedDevice = getBluetoothAudioConnectedDevice();
        if (possiblyAlreadyConnectedDevice != null) {
            Log.i(LOG_TAG, "Device %s is already connected; going to AudioConnected.",
                    possiblyAlreadyConnectedDevice);
            transitionTo(getConnectedStateForAddress(
                    possiblyAlreadyConnectedDevice.getAddress(), "transitionToActualState"));
            // TODO: replace with new interface
            mListener.onBluetoothStateChange(currentBtState, BLUETOOTH_AUDIO_CONNECTED);
        } else {
            transitionTo(mAudioOffState);
            mListener.onBluetoothStateChange(currentBtState,
                    mDeviceManager.getNumConnectedDevices() > 0 ?
                            BLUETOOTH_DEVICE_CONNECTED : BLUETOOTH_DISCONNECTED);
        }
    }

    /**
     * @return The BluetoothDevice that is connected to BT audio, null if none are connected.
     */
    @VisibleForTesting
    public BluetoothDevice getBluetoothAudioConnectedDevice() {
        BluetoothHeadsetProxy bluetoothHeadset = mDeviceManager.getHeadsetService();
        if (bluetoothHeadset == null) {
            Log.i(this, "getBluetoothAudioConnectedDevice: no headset service available.");
            return null;
        }
        List<BluetoothDevice> deviceList = bluetoothHeadset.getConnectedDevices();

        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice device = deviceList.get(i);
            boolean isAudioOn = bluetoothHeadset.isAudioConnected(device);
            Log.v(this, "isBluetoothAudioConnected: ==> isAudioOn = " + isAudioOn
                    + "for headset: " + device);
            if (isAudioOn) {
                return device;
            }
        }
        return null;
    }

    private boolean connectAudio(String address) {
        BluetoothHeadsetProxy bluetoothHeadset = mDeviceManager.getHeadsetService();
        if (bluetoothHeadset == null) {
            Log.w(this, "Trying to connect audio but no headset service exists.");
            return false;
        }
        // TODO: update once connectAudio supports passing in a device.
        return bluetoothHeadset.connectAudio();
    }

    private void disconnectAudio() {
        BluetoothHeadsetProxy bluetoothHeadset = mDeviceManager.getHeadsetService();
        if (bluetoothHeadset == null) {
            Log.w(this, "Trying to disconnect audio but no headset service exists.");
        } else {
            bluetoothHeadset.disconnectAudio();
        }
    }

    private boolean addDevice(String address) {
        if (mAudioConnectingStates.containsKey(address)) {
            Log.i(this, "Attempting to add device %s twice.", address);
            return false;
        }
        AudioConnectedState audioConnectedState = new AudioConnectedState(address);
        AudioConnectingState audioConnectingState = new AudioConnectingState(address);
        mAudioConnectingStates.put(address, audioConnectingState);
        mAudioConnectedStates.put(address, audioConnectedState);
        addState(audioConnectedState);
        addState(audioConnectingState);
        return true;
    }

    private boolean removeDevice(String address) {
        if (!mAudioConnectingStates.containsKey(address)) {
            Log.i(this, "Attempting to remove already-removed device %s", address);
            return false;
        }
        statesToCleanUp.add(mAudioConnectingStates.remove(address));
        statesToCleanUp.add(mAudioConnectedStates.remove(address));
        mMostRecentlyUsedDevices.remove(address);
        return true;
    }

    private AudioConnectingState getConnectingStateForAddress(String address, String error) {
        if (!mAudioConnectingStates.containsKey(address)) {
            Log.w(LOG_TAG, "Device being connected to does not have a corresponding state: %s",
                    error);
            addDevice(address);
        }
        return mAudioConnectingStates.get(address);
    }

    private AudioConnectedState getConnectedStateForAddress(String address, String error) {
        if (!mAudioConnectedStates.containsKey(address)) {
            Log.w(LOG_TAG, "Device already connected to does" +
                    " not have a corresponding state: %s", error);
            addDevice(address);
        }
        return mAudioConnectedStates.get(address);
    }

    /**
     * Removes the states for disconnected devices from the state machine. Called when entering
     * AudioOff so that none of the states-to-be-removed are active.
     */
    private void cleanupStatesForDisconnectedDevices() {
        for (State state : statesToCleanUp) {
            if (state != null) {
                removeState(state);
            }
        }
        statesToCleanUp.clear();
    }

    @VisibleForTesting
    public void setInitialStateForTesting(String stateName, BluetoothDevice device) {
        switch (stateName) {
            case AUDIO_OFF_STATE_NAME:
                transitionTo(mAudioOffState);
                break;
            case AUDIO_CONNECTING_STATE_NAME_PREFIX:
                transitionTo(getConnectingStateForAddress(device.getAddress(),
                        "setInitialStateForTesting"));
                break;
            case AUDIO_CONNECTED_STATE_NAME_PREFIX:
                transitionTo(getConnectedStateForAddress(device.getAddress(),
                        "setInitialStateForTesting"));
                break;
        }
    }
}
